import assert from "node:assert/strict";
import test from "node:test";
import { existsSync } from "node:fs";
import { ApiError, apiRequest } from "../src/lib/api.ts";

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
		if (path !== "/") {
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
