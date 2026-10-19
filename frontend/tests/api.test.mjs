import assert from "node:assert/strict";
import { afterEach, beforeEach, test } from "node:test";
import { ApiError, apiRequest } from "../src/lib/api.ts";
import { getApiBaseUrl } from "../src/lib/api-config.mjs";
import nextConfig from "../next.config.mjs";

const originalUrl = process.env.NEXT_PUBLIC_API_URL;
beforeEach(() => {
	process.env.NEXT_PUBLIC_API_URL = "https://delivery.example/api/";
});
afterEach(() => {
	if (originalUrl === undefined) delete process.env.NEXT_PUBLIC_API_URL;
	else process.env.NEXT_PUBLIC_API_URL = originalUrl;
	delete globalThis.window;
});

test("configured URL is normalized and used by the browser proxy", () => {
	assert.equal(getApiBaseUrl(), "https://delivery.example/api");
	assert.deepEqual(nextConfig.rewrites(), [
		{
			source: "/backend-api/:path*",
			destination: "https://delivery.example/api/:path*",
		},
	]);
	process.env.NEXT_PUBLIC_API_URL = "https://another.example/v2/";
	assert.equal(
		nextConfig.rewrites()[0].destination,
		"https://another.example/v2/:path*",
	);
});

for (const invalid of [
	undefined,
	"",
	"not-a-url",
	"/api",
	"ftp://example.com/api",
	"https://user:secret@example.com/api",
	"https://example.com/api?token=secret",
	"https://example.com/api#section",
]) {
	test(`invalid API configuration fails clearly: ${invalid ?? "missing"}`, () => {
		if (invalid === undefined) delete process.env.NEXT_PUBLIC_API_URL;
		else process.env.NEXT_PUBLIC_API_URL = invalid;
		assert.throws(getApiBaseUrl, /NEXT_PUBLIC_API_URL/);
	});
}

test("GET uses the configured base, preserves query parameters, and returns JSON", async (t) => {
	const fetch = t.mock.method(globalThis, "fetch", async (url, options) => {
		assert.equal(url, "https://delivery.example/api/requests?category=BUY");
		assert.equal(options.headers.get("Accept"), "application/json");
		assert.equal(options.headers.has("Content-Type"), false);
		assert.equal(options.cache, "no-store");
		assert.ok(options.signal instanceof AbortSignal);
		return Response.json([{ id: 1, status: "OPEN" }]);
	});
	assert.deepEqual(await apiRequest("/requests?category=BUY"), [
		{ id: 1, status: "OPEN" },
	]);
	assert.equal(fetch.mock.callCount(), 1);
});

test("browser requests use the same-origin proxy", async (t) => {
	globalThis.window = {};
	t.mock.method(globalThis, "fetch", async (url) => {
		assert.equal(url, "/backend-api/health");
		return Response.json({ status: "ok" });
	});
	assert.deepEqual(await apiRequest("/health"), { status: "ok" });
});

test("JSON mutations preserve the method, custom headers, and caller abort signal", async (t) => {
	const controller = new AbortController();
	t.mock.method(globalThis, "fetch", async (url, options) => {
		assert.equal(url, "https://delivery.example/api/requests/4/status");
		assert.equal(options.method, "PATCH");
		assert.equal(options.body, '{"status":"PICKED_UP"}');
		assert.equal(options.headers.get("Content-Type"), "application/json");
		assert.equal(options.headers.get("X-Test"), "kept");
		assert.equal(options.signal, controller.signal);
		return Response.json({ status: "PICKED_UP" });
	});
	assert.deepEqual(
		await apiRequest("/requests/4/status", {
			method: "PATCH",
			body: { status: "PICKED_UP" },
			headers: { "X-Test": "kept" },
			signal: controller.signal,
		}),
		{ status: "PICKED_UP" },
	);
});

for (const status of [404, 409, 422, 503]) {
	test(`HTTP ${status} preserves backend messages and field errors`, async (t) => {
		t.mock.method(globalThis, "fetch", async () =>
			Response.json(
				{
					message: "Validation failed.",
					fields: { title: "must not be blank", ignored: 42 },
				},
				{ status },
			),
		);
		await assert.rejects(apiRequest("/requests"), (error) => {
			assert.ok(error instanceof ApiError);
			assert.equal(error.status, status);
			assert.equal(error.message, "Validation failed.");
			assert.deepEqual(error.fields, { title: "must not be blank" });
			return true;
		});
	});
}

test("non-JSON proxy failures produce a safe HTTP error", async (t) => {
	t.mock.method(
		globalThis,
		"fetch",
		async () => new Response("<html>Internal details</html>", { status: 502 }),
	);
	await assert.rejects(apiRequest("/health"), (error) => {
		assert.ok(error instanceof ApiError);
		assert.equal(error.status, 502);
		assert.equal(error.message, "Request failed (502). Please try again.");
		return true;
	});
});

test("empty successful responses return undefined", async (t) => {
	t.mock.method(
		globalThis,
		"fetch",
		async () => new Response(null, { status: 204 }),
	);
	assert.equal(await apiRequest("/requests/1"), undefined);
});

test("invalid JSON in a successful response is rejected", async (t) => {
	t.mock.method(globalThis, "fetch", async () => new Response("not JSON"));
	await assert.rejects(apiRequest("/health"), /invalid JSON response/);
});

for (const failure of [
	new TypeError("Network unavailable"),
	new DOMException("Cancelled", "AbortError"),
]) {
	test(`transport failure is preserved: ${failure.name}`, async (t) => {
		t.mock.method(globalThis, "fetch", async () => {
			throw failure;
		});
		await assert.rejects(apiRequest("/health"), (error) => error === failure);
	});
}

for (const path of [
	"requests",
	"https://other.example/requests",
	"//other.example/requests",
]) {
	test(`absolute or malformed API path is rejected: ${path}`, async (t) => {
		const fetch = t.mock.method(globalThis, "fetch");
		await assert.rejects(apiRequest(path), /single slash/);
		assert.equal(fetch.mock.callCount(), 0);
	});
}
