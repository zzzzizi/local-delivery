import assert from "node:assert/strict";
import { afterEach, beforeEach, test } from "node:test";
import { ApiError } from "../src/lib/api.ts";
import {
	acceptJob as acceptJobAs,
	fetchRequest,
	isDeliveryRequest,
	isRequestId,
} from "../src/lib/request-details.ts";

const originalUrl = process.env.NEXT_PUBLIC_API_URL;
beforeEach(() => {
	process.env.NEXT_PUBLIC_API_URL = "https://delivery.example/api";
});
afterEach(() => {
	if (originalUrl === undefined) delete process.env.NEXT_PUBLIC_API_URL;
	else process.env.NEXT_PUBLIC_API_URL = originalUrl;
});
const open = {
	id: 12,
	customerId: 1,
	helperId: null,
	category: "BUY",
	title: "Groceries",
	description: "Milk and bread",
	pickupAddress: "Oslo shop",
	deliveryAddress: "Oslo home",
	shoppingBudget: "150.00",
	helperReward: "80.50",
	deadline: "2027-01-15T17:00:00Z",
	status: "OPEN",
	createdAt: "2026-10-21T10:00:00Z",
	updatedAt: "2026-10-21T10:00:00Z",
};
const accepted = {
	...open,
	helperId: 2,
	status: "ACCEPTED",
	updatedAt: "2026-10-21T10:01:00Z",
};
const conflict = () =>
	Response.json(
		{ message: "Delivery request has already been accepted." },
		{ status: 409 },
	);
const unavailable = () =>
	Response.json({ message: "Temporarily unavailable." }, { status: 503 });
function sequence(t, responses) {
	let index = 0;
	return t.mock.method(globalThis, "fetch", async () => {
		assert.ok(
			index < responses.length,
			"No unexpected retries or extra HTTP calls",
		);
		const response = responses[index++];
		if (response instanceof Error) throw response;
		return typeof response === "function"
			? response()
			: Response.json(response);
	});
}

test("detail IDs are positive canonical Java Long values", () => {
	for (const id of ["1", "12", "9223372036854775807"])
		assert.equal(isRequestId(id), true);
	for (const id of [
		"",
		"0",
		"-1",
		"01",
		"1.5",
		"1e2",
		"../new",
		"12?x=1",
		"9223372036854775808",
	]) {
		assert.equal(isRequestId(id), false);
	}
});
test("invalid detail and acceptance IDs never issue HTTP requests", async (t) => {
	const fetch = sequence(t, []);
	await assert.rejects(fetchRequest("../new"), { status: 404 });
	await assert.rejects(acceptJob("0"), { status: 404 });
	assert.equal(fetch.mock.callCount(), 0);
});
test("complete details allow every status, category, optional budget and helper", () => {
	for (const status of [
		"OPEN",
		"ACCEPTED",
		"PICKED_UP",
		"DELIVERING",
		"DELIVERED",
		"CANCELLED",
	]) {
		for (const category of ["BUY", "PACKAGE", "PICKUP"]) {
			assert.equal(isDeliveryRequest({ ...accepted, status, category }), true);
		}
	}
	assert.equal(
		isDeliveryRequest({ ...open, shoppingBudget: null, helperReward: "0.00" }),
		true,
	);
});
test("malformed detail fields cannot be rendered as valid request data", () => {
	for (const value of [null, undefined, [], {}, "request"])
		assert.equal(isDeliveryRequest(value), false);
	for (const [field, value] of [
		["id", Number.MAX_SAFE_INTEGER + 1],
		["id", "12"],
		["customerId", 0],
		["helperId", -1],
		["category", ["BUY"]],
		["category", "toString"],
		["status", ["OPEN"]],
		["status", "INVALID"],
		["description", null],
		["title", 1],
		["pickupAddress", {}],
		["deliveryAddress", undefined],
		["shoppingBudget", 150],
		["helperReward", "-1.00"],
		["helperReward", "80.5"],
		["deadline", "2027-01-15T17:00:00"],
		["createdAt", "invalid"],
		["updatedAt", null],
	])
		assert.equal(isDeliveryRequest({ ...open, [field]: value }), false, field);
});
test("fetch details verifies the returned ID and bypasses caches", async (t) => {
	const fetch = sequence(t, [open, { ...open, id: 13 }, {}]);
	assert.deepEqual(await fetchRequest("12"), open);
	assert.equal(
		fetch.mock.calls[0].arguments[0],
		"https://delivery.example/api/requests/12",
	);
	assert.equal(fetch.mock.calls[0].arguments[1].cache, "no-store");
	await assert.rejects(fetchRequest("12"), { status: 502 });
	await assert.rejects(fetchRequest("12"), { status: 502 });
});
test("missing detail preserves the backend 404", async (t) => {
	sequence(t, [
		() =>
			Response.json(
				{ message: "Delivery request not found." },
				{ status: 404 },
			),
	]);
	await assert.rejects(fetchRequest("12"), {
		status: 404,
		message: "Delivery request not found.",
	});
});
test("acceptance posts helper 2 once, then displays the refreshed GET result", async (t) => {
	const latest = {
		...accepted,
		status: "PICKED_UP",
		updatedAt: "2026-10-21T10:02:00Z",
	};
	const fetch = sequence(t, [accepted, latest]);
	const result = await acceptJob("12");
	assert.equal(result.kind, "success");
	assert.equal(result.requiresRefresh, false);
	assert.deepEqual(result.request, latest);
	const calls = fetch.mock.calls;
	assert.equal(calls.length, 2);
	assert.equal(
		calls[0].arguments[0],
		"https://delivery.example/api/requests/12/accept",
	);
	assert.equal(calls[0].arguments[1].method, "POST");
	assert.deepEqual(JSON.parse(calls[0].arguments[1].body), {});
	assert.equal(
		calls[1].arguments[0],
		"https://delivery.example/api/requests/12",
	);
	assert.equal(calls[1].arguments[1].method, undefined);
});
test("successful acceptance survives a failed refresh using the saved response", async (t) => {
	sequence(t, [accepted, unavailable]);
	const result = await acceptJob("12");
	assert.equal(result.kind, "success");
	assert.deepEqual(result.request, accepted);
	assert.match(result.message, /couldn’t reload/);
});
test("already-accepted conflict preserves the error and refreshes the winning helper", async (t) => {
	const winner = { ...accepted, helperId: 3 };
	sequence(t, [conflict, winner]);
	const result = await acceptJob("12");
	assert.equal(result.kind, "error");
	assert.match(result.message, /already been accepted/);
	assert.deepEqual(result.request, winner);
	assert.equal(result.requiresRefresh, false);
});
for (const response of [open, unavailable]) {
	test(`conflict blocks repeated acceptance when refresh ${typeof response === "function" ? "fails" : "still says OPEN"}`, async (t) => {
		const fetch = sequence(t, [conflict, response]);
		const result = await acceptJob("12");
		assert.equal(result.kind, "error");
		assert.equal(result.requiresRefresh, true);
		assert.equal(fetch.mock.callCount(), 2);
	});
}
for (const failure of [
	new TypeError("Connection lost"),
	new DOMException("Timed out", "TimeoutError"),
	unavailable,
]) {
	test(`uncertain POST (${typeof failure === "function" ? "503" : failure.name}) reconciles saved acceptance without a second POST`, async (t) => {
		const fetch = sequence(t, [failure, accepted]);
		const result = await acceptJob("12");
		assert.equal(result.kind, "success");
		assert.deepEqual(result.request, accepted);
		assert.match(result.message, /assigned helper/);
		assert.equal(fetch.mock.callCount(), 2);
	});
}
for (const response of [
	open,
	{ ...accepted, helperId: 3 },
	{ ...accepted, status: "CANCELLED" },
	unavailable,
]) {
	test(`uncertain acceptance requires manual refresh for ${typeof response === "function" ? "unavailable details" : `${response.status}, helper ${response.helperId}`}`, async (t) => {
		sequence(t, [new TypeError("Connection lost"), response]);
		const result = await acceptJob("12");
		assert.equal(result.kind, "error");
		assert.equal(result.requiresRefresh, true);
		assert.match(result.message, /couldn’t confirm acceptance/);
	});
}
for (const status of [400, 404, 422]) {
	test(`known business error ${status} is shown without an acceptance retry`, async (t) => {
		const fetch = sequence(t, [
			() =>
				Response.json({ message: "Business validation failed." }, { status }),
		]);
		await assert.rejects(
			acceptJob("12"),
			(error) => error instanceof ApiError && error.status === status,
		);
		assert.equal(fetch.mock.callCount(), 1);
	});
}
test("invalid acceptance response is reconciled instead of claiming success", async (t) => {
	sequence(t, [{ ...accepted, id: 13 }, open]);
	const result = await acceptJob("12");
	assert.equal(result.kind, "error");
	assert.equal(result.requiresRefresh, true);
	assert.deepEqual(result.request, open);
});

function acceptJob(id) { return acceptJobAs(id, 2); }
