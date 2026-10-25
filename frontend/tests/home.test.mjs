import assert from "node:assert/strict";
import test from "node:test";
import { existsSync } from "node:fs";
import { ApiError, apiRequest as sendApiRequest } from "../src/lib/api.ts";
import { formatOsloDate } from "../src/lib/create-request.ts";

if (existsSync(".env.local")) process.loadEnvFile(".env.local");
const baseUrl = process.env.FRONTEND_TEST_URL ?? "http://127.0.0.1:3000";
const customerCookie = process.env.FRONTEND_CUSTOMER_COOKIE;
const helperCookie = process.env.FRONTEND_HELPER_COOKIE;
const customerId = Number(process.env.FRONTEND_CUSTOMER_ID);
const helperId = Number(process.env.FRONTEND_HELPER_ID);
if (!customerCookie || !helperCookie || !customerId || !helperId) {
  throw new Error("Run node scripts/test-docker.mjs from the project root to supply isolated authenticated test sessions.");
}
function cookieFor(path) {
  return path.includes("deliveries") || /\/requests\/[^/]+$/.test(path) && !path.endsWith("/new") ? helperCookie : customerCookie;
}
async function fetch(url, init = {}) {
  const headers = new Headers(init.headers);
  headers.set("Cookie", cookieFor(new URL(url).pathname));
  if (init.method && init.method !== "GET") {
    const csrf = await globalThis.fetch(`${baseUrl}/backend-api/auth/csrf`, { headers });
    const token = await csrf.json();
    headers.set(token.headerName, token.token);
  }
  return globalThis.fetch(url, { ...init, headers });
}
function apiRequest(path, options = {}) {
  return sendApiRequest(path, { ...options, headers: { ...options.headers, Cookie: cookieFor(path) } });
}
const statusLabels = {
	OPEN: "Open",
	ACCEPTED: "Accepted",
	PICKED_UP: "Picked up",
	DELIVERING: "Delivering",
	DELIVERED: "Delivered",
	CANCELLED: "Cancelled",
};
const pages = [
	["/", "Home", "What do you need<br/>help with?"],
	["/requests/new", "Create Request", "Create Request"],
	["/jobs", "Available Jobs", "Available Jobs"],
	["/my-requests", "My Requests", "My Requests"],
	["/my-deliveries", "My Deliveries", "My Deliveries"],
];

for (const [path, label, heading] of pages) {
	test(`the running frontend serves ${label} with navigation and an active link`, async () => {
		const response = await fetch(`${baseUrl}${path}`, {
			signal: AbortSignal.timeout(15000),
		});
		assert.equal(response.status, 200);
		assert.match(response.headers.get("content-type"), /text\/html/);
		const html = await response.text();
		assert.ok(html.includes(`<h1>${heading}</h1>`));
		assert.ok(
			html.includes(
				`<title>${path === "/" ? "Local Delivery" : `${label} | Local Delivery`}</title>`,
			),
		);
		const navigation = html.match(
			/<nav aria-label="Main navigation">(.*?)<\/nav>/s,
		)?.[1];
		assert.ok(navigation);
		for (const [href, title] of pages) {
			assert.ok(navigation.includes(`href="${href}"`));
			assert.ok(navigation.includes(`>${title}</a>`));
		}
		assert.equal((navigation.match(/aria-current="page"/g) ?? []).length, 1);
		assert.ok(navigation.includes(`aria-current="page" href="${path}"`));
		assert.match(html, /href="#main-content"/);
		if (path === "/requests/new") {
			assert.match(html, /<form[\s>]/);
			assert.match(html, />Post request<\/button>/);
			for (const name of [
				"category",
				"title",
				"description",
				"pickupAddress",
				"deliveryAddress",
				"shoppingBudget",
				"helperReward",
				"deadline",
			]) {
				assert.ok(html.includes(`name="${name}"`));
				assert.ok(html.includes(`for="${name}"`));
			}
			assert.doesNotMatch(html, /Coming soon/);
		} else if (path === "/jobs") {
			assert.match(html, /aria-label="Available requests"/);
			assert.match(html, />Refresh jobs<\/button>/);
			assert.doesNotMatch(html, /Coming soon/);
		} else if (path === "/my-requests") {
			assert.match(html, /aria-label="Your requests"/);
			assert.match(html, />Refresh requests<\/button>/);
			assert.doesNotMatch(html, /Coming soon/);
		} else if (path === "/my-deliveries") {
			assert.match(html, /aria-label="Your deliveries"/);
			assert.match(html, />Refresh deliveries<\/button>/);
			assert.doesNotMatch(html, /Coming soon/);
		} else if (path !== "/") {
			assert.match(html, /Coming soon/);
			assert.doesNotMatch(html, /<form[\s>]/);
		}
	});
}

test("the shared API helper reaches the configured Java backend", async () => {
	assert.deepEqual(await apiRequest("/health"), { status: "ok" });
	await assert.rejects(
		apiRequest("/requests/9223372036854775807"),
		(error) =>
			error instanceof ApiError &&
			error.status === 404 &&
			error.message === "Delivery request not found.",
	);
});

test("available-job cards match the live API fields, order, and detail links", async () => {
	const jobs = await apiRequest("/requests");
	const response = await fetch(`${baseUrl}/jobs`, {
		signal: AbortSignal.timeout(15000),
	});
	assert.equal(response.status, 200);
	const html = (await response.text()).replaceAll(/<!--.*?-->/gs, "");
	const cards =
		html.match(/<a\b[^>]*class="job-card"[^>]*>[\s\S]*?<\/a>/g) ?? [];
	assert.equal(cards.length, jobs.length);
	if (!jobs.length) assert.match(html, /No jobs available yet/);
	const labels = {
		PACKAGE: "Package delivery",
		BUY: "Shopping",
		PICKUP: "Pickup",
	};
	const escape = (value) =>
		String(value)
			.replaceAll("&", "&amp;")
			.replaceAll("<", "&lt;")
			.replaceAll(">", "&gt;")
			.replaceAll('"', "&quot;")
			.replaceAll("'", "&#x27;");
	for (const [index, job] of jobs.entries()) {
		assert.equal(job.status, "OPEN");
		const card = cards[index];
		assert.ok(card.includes(`href="/requests/${job.id}"`));
		assert.ok(card.includes(`data-status="${job.status}"`));
		assert.ok(card.includes(`</span>${statusLabels[job.status]}</span>`));
		for (const text of [
			labels[job.category],
			job.title,
			job.pickupAddress,
			job.deliveryAddress,
			`${job.helperReward} NOK`,
			formatOsloDate(job.deadline),
			"OPEN",
		]) {
			assert.ok(
				card.includes(escape(text)),
				`Missing ${text} on request ${job.id}`,
			);
		}
		assert.equal(
			card.match(/<time\b[^>]*datetime="([^"]+)"/i)?.[1],
			job.deadline,
		);
	}
});

test("the same-origin proxy reaches backend health", async () => {
	const response = await fetch(`${baseUrl}/backend-api/health`, {
		signal: AbortSignal.timeout(15000),
	});
	assert.equal(response.status, 200);
	assert.deepEqual(await response.json(), { status: "ok" });
});

test("the proxy forwards method and body and preserves validation errors without creating data", async () => {
	const response = await fetch(`${baseUrl}/backend-api/requests`, {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: "{}",
		signal: AbortSignal.timeout(15000),
	});
	assert.equal(response.status, 422);
	const error = await response.json();
	assert.equal(error.message, "Validation failed.");
	assert.equal(error.status, 422);
	assert.equal(typeof error.fields.title, "string");
});

test("request detail route renders live request fields and acceptance controls", async (t) => {
	const jobs = await apiRequest("/requests");
	if (!jobs.length)
		return t.skip("No OPEN requests available in this database.");
	const request = await apiRequest(`/requests/${jobs[0].id}`);
	const response = await fetch(`${baseUrl}/requests/${request.id}`);
	assert.equal(response.status, 200);
	const html = (await response.text()).replaceAll(/<!--.*?-->/gs, "");
	const escape = (value) =>
		String(value)
			.replaceAll("&", "&amp;")
			.replaceAll("<", "&lt;")
			.replaceAll(">", "&gt;")
			.replaceAll('"', "&quot;")
			.replaceAll("'", "&#x27;");
	assert.match(html, /<h1>Request Details<\/h1>/);
	assert.match(html, /aria-label="Request details"/);
	for (const value of [
		request.title,
		request.description,
		request.pickupAddress,
		request.deliveryAddress,
		`${request.helperReward} NOK`,
		formatOsloDate(request.deadline),
		"No helper assigned",
		"OPEN",
	]) {
		assert.ok(html.includes(escape(value)), `Missing detail: ${value}`);
	}
	assert.match(html, />Accept Job<\/button>/);
	assert.match(html, />Refresh request<\/button>/);
	assert.match(html, /href="\/jobs"/);
});

for (const id of ["9223372036854775807", "invalid-id"]) {
	test(`missing or invalid request ${id} has a useful not-found page`, async () => {
		const response = await fetch(`${baseUrl}/requests/${id}`);
		const html = await response.text();
		assert.match(html, /Request not found/);
		assert.match(html, /name="robots" content="noindex"/);
		assert.doesNotMatch(html, />Accept Job<\/button>/);
		assert.match(html, /href="\/jobs"/);
	});
}

test("acceptance proxy preserves helper validation without mutating a request", async () => {
	const response = await fetch(`${baseUrl}/backend-api/requests/1/accept`, {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: '{"helperId":0}',
	});
	assert.equal(response.status, 422);
	const error = await response.json();
	assert.equal(error.message, "Validation failed.");
	assert.equal(typeof error.fields.helperId, "string");
});

test("customer tracking cards match every live API request in order", async () => {
	const requests = await apiRequest("/me/requests");
	const response = await fetch(`${baseUrl}/my-requests`, {
		signal: AbortSignal.timeout(15000),
	});
	assert.equal(response.status, 200);
	const html = (await response.text()).replaceAll(/<!--.*?-->/gs, "");
	const cards =
		html.match(/<a\b[^>]*class="job-card"[^>]*>[\s\S]*?<\/a>/g) ?? [];
	assert.equal(cards.length, requests.length);
	if (!requests.length) assert.match(html, /No requests yet/);
	const labels = {
		PACKAGE: "Package delivery",
		BUY: "Shopping",
		PICKUP: "Pickup",
	};
	const escape = (value) =>
		String(value)
			.replaceAll("&", "&amp;")
			.replaceAll("<", "&lt;")
			.replaceAll(">", "&gt;")
			.replaceAll('"', "&quot;")
			.replaceAll("'", "&#x27;");
	for (const [index, request] of requests.entries()) {
		assert.equal(request.customerId, customerId);
		const card = cards[index];
		assert.ok(card.includes(`href="/requests/${request.id}"`));
		assert.ok(card.includes(`data-status="${request.status}"`));
		assert.ok(card.includes(`</span>${statusLabels[request.status]}</span>`));
		for (const value of [
			request.title,
			labels[request.category],
			request.status,
			request.helperId === null
				? "No helper assigned"
				: `Helper #${request.helperId}`,
			formatOsloDate(request.deadline),
		])
			assert.ok(
				card.includes(escape(value)),
				`Missing ${value} for request ${request.id}`,
			);
		assert.equal(
			card.match(/<time\b[^>]*datetime="([^"]+)"/i)?.[1],
			request.deadline,
		);
	}
	assert.doesNotMatch(html, />Accept Job<\/button>|>Mark Delivered<\/button>/);
});

test("customer tracking is available through the same-origin proxy", async () => {
	const requests = await apiRequest("/me/requests");
	const response = await fetch(`${baseUrl}/backend-api/me/requests`);
	assert.equal(response.status, 200);
	assert.deepEqual(await response.json(), requests);
});

for (const [id, status] of [
	["0", 422],
	["9223372036854775807", 403],
]) {
	test(`customer tracking preserves ${status} errors through the proxy`, async () => {
		const response = await fetch(`${baseUrl}/backend-api/users/${id}/requests`);
		assert.equal(response.status, status);
		const error = await response.json();
		assert.equal(error.status, status);
		assert.equal(typeof error.message, "string");
		if (status === 404) assert.equal(error.message, "Customer not found.");
	});
}

test("helper delivery cards match assigned jobs and expose only the appropriate next step", async () => {
	const deliveries = await apiRequest("/me/deliveries");
	const response = await fetch(`${baseUrl}/my-deliveries`, {
		signal: AbortSignal.timeout(15000),
	});
	assert.equal(response.status, 200);
	const html = (await response.text()).replaceAll(/<!--.*?-->/gs, "");
	const cards =
		html.match(
			/<article\b[^>]*data-request-id="\d+"[^>]*>[\s\S]*?<\/article>/g,
		) ?? [];
	assert.equal(cards.length, deliveries.length);
	if (!deliveries.length) assert.match(html, /No deliveries yet/);
	const labels = {
		PACKAGE: "Package delivery",
		BUY: "Shopping",
		PICKUP: "Pickup",
	};
	const actions = {
		ACCEPTED: "Mark Picked Up",
		PICKED_UP: "Start Delivery",
		DELIVERING: "Mark Delivered",
	};
	const escape = (value) =>
		String(value)
			.replaceAll("&", "&amp;")
			.replaceAll("<", "&lt;")
			.replaceAll(">", "&gt;")
			.replaceAll('"', "&quot;")
			.replaceAll("'", "&#x27;");
	for (const [index, request] of deliveries.entries()) {
		assert.equal(request.helperId, helperId);
		const card = cards[index];
		assert.ok(card.includes(`data-request-id="${request.id}"`));
		assert.ok(card.includes(`href="/requests/${request.id}"`));
		assert.ok(card.includes(`data-status="${request.status}"`));
		assert.ok(card.includes(`</span>${statusLabels[request.status]}</span>`));
		for (const value of [
			request.title,
			labels[request.category],
			request.status,
			request.pickupAddress,
			request.deliveryAddress,
			`${request.helperReward} NOK`,
			formatOsloDate(request.deadline),
		]) {
			assert.ok(
				card.includes(escape(value)),
				`Missing ${value} for delivery ${request.id}`,
			);
		}
		const buttons = [...card.matchAll(/<button\b[^>]*>([^<]*)<\/button>/g)].map(
			(x) => x[1],
		);
		assert.deepEqual(
			buttons,
			actions[request.status] ? [actions[request.status]] : [],
		);
	}
});

test("helper delivery list is available through the same-origin proxy", async () => {
	const deliveries = await apiRequest("/me/deliveries");
	const response = await fetch(`${baseUrl}/backend-api/me/deliveries`);
	assert.equal(response.status, 200);
	assert.deepEqual(await response.json(), deliveries);
});

for (const [id, status] of [
	["0", 422],
	["9223372036854775807", 403],
]) {
	test(`helper delivery list preserves ${status} errors through the proxy`, async () => {
		const response = await fetch(
			`${baseUrl}/backend-api/users/${id}/deliveries`,
		);
		assert.equal(response.status, status);
		const error = await response.json();
		assert.equal(error.status, status);
		if (status === 404) assert.equal(error.message, "Helper not found.");
	});
}
