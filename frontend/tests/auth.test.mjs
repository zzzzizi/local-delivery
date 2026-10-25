import assert from "node:assert/strict";
import { afterEach, beforeEach, test } from "node:test";
import { apiRequest } from "../src/lib/api.ts";
import { isCustomerRequests } from "../src/lib/customer-requests.ts";
import { isHelperDeliveries, deliveryAction } from "../src/lib/helper-deliveries.ts";
import { acceptJob } from "../src/lib/request-details.ts";

const originalUrl = process.env.NEXT_PUBLIC_API_URL;
beforeEach(() => { process.env.NEXT_PUBLIC_API_URL = "https://example.test/api"; });
afterEach(() => {
  delete globalThis.window;
  if (originalUrl === undefined) delete process.env.NEXT_PUBLIC_API_URL;
  else process.env.NEXT_PUBLIC_API_URL = originalUrl;
});
const request = {
  id: 37, customerId: 41, helperId: 83, category: "PACKAGE", status: "ACCEPTED",
  title: "Test", description: "Test", pickupAddress: "Shop", deliveryAddress: "Home",
  shoppingBudget: null, helperReward: "80.50", deadline: "2030-01-01T12:00:00Z",
  createdAt: "2026-10-24T12:00:00Z", updatedAt: "2026-10-24T12:00:00Z",
};

test("tracking validates the actual customer's identity", () => {
  assert.equal(isCustomerRequests([request], 41), true);
  assert.equal(isCustomerRequests([request], 83), false);
});
test("delivery ownership and actions use the actual helper's identity", () => {
  assert.equal(isHelperDeliveries([request], 83), true);
  assert.equal(isHelperDeliveries([request], 41), false);
  assert.equal(deliveryAction(request, 41), null);
  assert.equal(deliveryAction(request, 83).status, "PICKED_UP");
});
test("acceptance uses the session without sending a helper ID", async t => {
  t.mock.method(globalThis, "fetch", async (_url, options) => {
    if (options.method === "POST") assert.deepEqual(JSON.parse(options.body), {});
    return Response.json(request);
  });
  assert.equal((await acceptJob("37", 83)).kind, "success");
});
test("browser mutations fetch a CSRF token and preserve JSON", async t => {
  globalThis.window = {};
  const calls = [];
  t.mock.method(globalThis, "fetch", async (url, options) => {
    calls.push(url);
    if (url === "/backend-api/auth/csrf") return Response.json({ token: "test-token", headerName: "X-CSRF-TOKEN" });
    assert.equal(options.headers.get("X-CSRF-TOKEN"), "test-token");
    assert.equal(options.body, '{"status":"CANCELLED"}');
    return Response.json({ status: "CANCELLED" });
  });
  await apiRequest("/requests/37/status", { method: "PATCH", body: { status: "CANCELLED" } });
  assert.deepEqual(calls, ["/backend-api/auth/csrf", "/backend-api/requests/37/status"]);
});
test("invalid CSRF data prevents a mutation", async t => {
  globalThis.window = {};
  const mock = t.mock.method(globalThis, "fetch", async () => Response.json({}));
  await assert.rejects(apiRequest("/auth/login", { method: "POST", body: {} }), { status: 403 });
  assert.equal(mock.mock.callCount(), 1);
});
test("login and logout fetch fresh tokens each time", async t => {
  globalThis.window = {};
  let issued = 0;
  t.mock.method(globalThis, "fetch", async (url, options) => {
    if (url.endsWith("/csrf")) return Response.json({ token: `token-${++issued}`, headerName: "X-CSRF-TOKEN" });
    assert.equal(options.headers.get("X-CSRF-TOKEN"), `token-${issued}`);
    return Response.json({ id: 83 });
  });
  await apiRequest("/auth/login", { method: "POST", body: {} });
  await apiRequest("/auth/logout", { method: "POST" });
  assert.equal(issued, 2);
});
