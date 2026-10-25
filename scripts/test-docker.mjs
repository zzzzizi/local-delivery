import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { randomBytes } from "node:crypto";
import { fileURLToPath } from "node:url";

// A separate project, random host ports, and a disposable volume protect local data.
const cwd = fileURLToPath(new URL("../", import.meta.url));
const project = `local-delivery-test-${process.pid}-${randomBytes(4).toString("hex")}`;
const env = {
  ...process.env,
  DB_NAME: "delivery_test",
  DB_USERNAME: "delivery_test",
  DB_PASSWORD: randomBytes(32).toString("hex"),
  DB_PORT: "0",
  BACKEND_PORT: "0",
  FRONTEND_PORT: "0",
};
const composeArgs = ["compose", "-f", "docker-compose.yml", "-p", project];
function compose(args, capture = false) {
  const result = spawnSync("docker", [...composeArgs, ...args], {
    cwd, env, encoding: "utf8", stdio: capture ? "pipe" : "inherit",
  });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`Compose ${args[0]} failed (${result.status})`);
  return result.stdout?.trim();
}
function origin(service, port) {
  return `http://${compose(["port", service, String(port)], true)}`;
}
let frontend;
let backend;
const actors = { customer: { cookie: "" }, helper: { cookie: "" } };
const password = randomBytes(24).toString("hex");
let customerId;
let helperId;
async function api(path, { method = "GET", body, status = 200, direct = false, actor = path.includes("deliveries") || path.endsWith("/accept") || path.endsWith("/status") ? "helper" : "customer" } = {}) {
  const headers = { "Content-Type": "application/json", Cookie: actors[actor].cookie };
  if (!["GET", "HEAD"].includes(method)) {
    const csrf = await api("/auth/csrf", { actor });
    headers.Cookie = actors[actor].cookie;
    headers[csrf.headerName] = csrf.token;
  }
  const response = await fetch(`${direct ? `${backend}/api` : `${frontend}/backend-api`}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(15000),
  });
  for (const cookie of response.headers.getSetCookie()) {
    if (cookie.startsWith("DELIVERY_SESSION=")) {
      const sessionCookie = cookie.split(";")[0];
      if (sessionCookie !== "DELIVERY_SESSION=") assert.match(cookie, /HttpOnly/i);
      actors[actor].cookie = sessionCookie === "DELIVERY_SESSION=" ? "" : sessionCookie;
    }
  }
  const text = await response.text();
  const value = text ? JSON.parse(text) : undefined;
  assert.equal(response.status, status, `${method} ${path}: ${JSON.stringify(value)}`);
  return value;
}
async function create(category) {
  return api("/requests", {
    method: "POST", status: 201,
    body: {
      category, title: `Docker verification ${category}`,
      description: "Disposable Compose test data", pickupAddress: "Demo shop, Oslo",
      deliveryAddress: "Demo home, Oslo", helperReward: 80.50,
      shoppingBudget: category === "BUY" ? 325.75 : null,
      deadline: new Date(Date.now() + 172800000).toISOString(),
    },
  });
}

async function login(actor) {
  return api("/auth/login", { method: "POST", actor, body: { email: `${actor}@docker.example`, password } });
}

try {
  compose(["up", "--build", "-d", "--wait", "--wait-timeout", "180"]);
  frontend = origin("frontend", 3000);
  backend = origin("backend", 8080);
  assert.deepEqual(await api("/health"), { status: "ok" });
  assert.deepEqual(await api("/health", { direct: true }), { status: "ok" });
  await api("/requests", { status: 401 });
  for (const actor of ["customer", "helper"]) {
    const user = await api("/auth/register", { method: "POST", actor, status: 201,
      body: { name: `Docker ${actor}`, email: `${actor}@docker.example`, password } });
    if (actor === "customer") customerId = user.id; else helperId = user.id;
    const signedIn = await login(actor);
    assert.equal(signedIn.id, user.id);
  }
  assert.deepEqual(await api("/requests"), []);
  const csrfRejected = await fetch(`${frontend}/backend-api/requests`, { method: "POST", headers: { Cookie: actors.customer.cookie, "Content-Type": "application/json" }, body: "{}" });
  assert.equal(csrfRejected.status, 403);
  await api(`/users/${customerId}/requests`, { actor: "helper", status: 403 });
  console.log("PASS: registration, login, HttpOnly cookies, CSRF, anonymous denial, and private tracking");
  for (const service of ["backend", "frontend"]) {
    assert.notEqual(compose(["exec", "-T", service, "id", "-u"], true), "0");
    compose(["exec", "-T", service, "sh", "-c", "test ! -f /app/.env && test ! -f /app/.env.local"]);
  }
  console.log("PASS: healthy services, fresh migrations, non-root app processes, no local env files");

  await api("/requests", { method: "POST", body: {}, status: 422 });
  await api("/requests/999999", { status: 404 });
  const completed = [];
  for (const category of ["BUY", "PICKUP", "PACKAGE"]) {
    let current = await create(category);
    const path = `/requests/${current.id}`;
    assert.equal(current.status, "OPEN");
    assert.ok((await api("/requests")).some(row => row.id === current.id));
    current = await api(`${path}/accept`, { method: "POST", body: {} });
    assert.equal(current.status, "ACCEPTED");
    assert.equal(current.helperId, helperId);
    assert.equal(current.customerId, customerId);
    await api(`${path}/accept`, { method: "POST", body: {}, status: 409 });
    await api(`${path}/status`, { method: "PATCH", body: { status: "DELIVERED" }, status: 409 });
    for (const status of ["PICKED_UP", "DELIVERING", "DELIVERED"]) {
      current = await api(`${path}/status`, { method: "PATCH", body: { status } });
      assert.equal(current.status, status);
    }
    assert.deepEqual(await api(path, { direct: true }), current);
    assert.ok(!(await api("/requests")).some(row => row.id === current.id));
    for (const listing of ["/me/requests", "/me/deliveries"]) {
      assert.deepEqual((await api(listing)).find(row => row.id === current.id), current);
    }
    completed.push(current);
    console.log(`PASS: ${category} through create, accept, pickup, delivery, completion, and both tracking views`);
  }
  // Keep a waiting and an assigned request to exercise populated pages in the live suite.
  const waiting = await create("BUY");
  const assigned = await create("PACKAGE");
  await api(`/requests/${assigned.id}/accept`, { method: "POST", body: {} });
  for (const path of ["/", "/jobs", "/requests/new", "/my-requests", "/my-deliveries", `/requests/${waiting.id}`]) {
    const response = await fetch(`${frontend}${path}`, { headers: { Cookie: actors[path === "/my-deliveries" ? "helper" : "customer"].cookie }, signal: AbortSignal.timeout(15000) });
    assert.equal(response.status, 200, path);
    const html = await response.text();
    assert.match(html, /<h1>/);
    if (path === "/") {
      const assets = [...html.matchAll(/(?:src|href)="([^"?]+\.(?:js|css))(?:\?[^\"]*)?"/g)];
      assert.ok(assets.length > 0, "Standalone output must include JavaScript and styles");
      for (const [, asset] of assets) {
        const assetResponse = await fetch(new URL(asset, frontend), { signal: AbortSignal.timeout(15000) });
        assert.equal(assetResponse.status, 200, asset);
        await assetResponse.arrayBuffer();
      }
    }
  }
  console.log("PASS: all pages and production JavaScript/CSS are served");

  const before = await api("/me/requests");
  // Removing containers (not the volume) proves persistence across full replacement.
  compose(["down"]);
  compose(["up", "-d", "--wait", "--wait-timeout", "180"]);
  frontend = origin("frontend", 3000);
  backend = origin("backend", 8080);
  await api("/auth/me", { status: 401 });
  await login("customer");
  await login("helper");
  assert.deepEqual(await api("/me/requests"), before);
  for (const request of completed) {
    assert.deepEqual(await api(`/requests/${request.id}`), request);
  }
  console.log("PASS: accounts and requests survive container replacement; sessions require a fresh login");

  const tests = spawnSync("npm", ["test"], {
    cwd: fileURLToPath(new URL("../frontend/", import.meta.url)),
    env: { ...process.env, FRONTEND_TEST_URL: frontend, NEXT_PUBLIC_API_URL: `${backend}/api`, FRONTEND_CUSTOMER_COOKIE: actors.customer.cookie, FRONTEND_HELPER_COOKIE: actors.helper.cookie, FRONTEND_CUSTOMER_ID: String(customerId), FRONTEND_HELPER_ID: String(helperId) },
    stdio: "inherit",
  });
  if (tests.error) throw tests.error;
  assert.equal(tests.status, 0, "Frontend unit and live tests against Docker");
  await api("/auth/logout", { method: "POST", status: 204 });
  await api("/auth/me", { status: 401 });
  console.log("PASS: logout invalidates authentication");
  console.log("Docker verification passed.");
} finally {
  // Only this uniquely named test project's containers and volume are removed.
  compose(["down", "--volumes", "--remove-orphans"]);
}
