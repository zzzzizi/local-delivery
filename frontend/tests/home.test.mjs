import assert from "node:assert/strict";
import test from "node:test";
import { existsSync } from "node:fs";
import { ApiError, apiRequest } from "../src/lib/api.ts";
import { formatOsloDate } from "../src/lib/create-request.ts";

if (existsSync(".env.local")) process.loadEnvFile(".env.local");
const baseUrl = process.env.FRONTEND_TEST_URL ?? "http://127.0.0.1:3000";
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
		body: "{}",
	});
	assert.equal(response.status, 422);
	const error = await response.json();
	assert.equal(error.message, "Validation failed.");
	assert.equal(typeof error.fields.helperId, "string");
});
