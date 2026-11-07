import assert from "node:assert/strict";
import { afterEach, beforeEach, test } from "node:test";
import { ApiError } from "../src/lib/api.ts";
import {
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
test("invalid detail IDs never issue HTTP requests", async (t) => {
	const fetch = sequence(t, []);
	await assert.rejects(fetchRequest("../new"), { status: 404 });
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
