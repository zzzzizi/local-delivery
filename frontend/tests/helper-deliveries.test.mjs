import assert from "node:assert/strict";
import { afterEach, beforeEach, test } from "node:test";
import {
	advanceDelivery,
	deliveryAction,
	fetchDeliveries,
	isHelperDeliveries,
} from "../src/lib/helper-deliveries.ts";

const originalUrl = process.env.NEXT_PUBLIC_API_URL;
beforeEach(() => {
	process.env.NEXT_PUBLIC_API_URL = "https://delivery.example/api";
});
afterEach(() => {
	if (originalUrl === undefined) delete process.env.NEXT_PUBLIC_API_URL;
	else process.env.NEXT_PUBLIC_API_URL = originalUrl;
});
const request = {
	id: 13,
	customerId: 1,
	helperId: 2,
	category: "PACKAGE",
	title: "Demo delivery",
	description: "Test only",
	pickupAddress: "Demo shop",
	deliveryAddress: "Demo home",
	shoppingBudget: null,
	helperReward: "0.00",
	deadline: "2027-01-15T17:00:00Z",
	status: "ACCEPTED",
	createdAt: "2026-10-22T07:00:00Z",
	updatedAt: "2026-10-22T07:00:00Z",
};
const pickedUp = {
	...request,
	status: "PICKED_UP",
	updatedAt: "2026-10-22T07:01:00Z",
};
const unavailable = () =>
	Response.json({ message: "Service unavailable." }, { status: 503 });
const conflict = () =>
	Response.json(
		{ message: "Cannot change delivery status from PICKED_UP to PICKED_UP." },
		{ status: 409 },
	);
function sequence(t, responses) {
	let index = 0;
	return t.mock.method(globalThis, "fetch", async () => {
		assert.ok(index < responses.length, "No unexpected mutation retries");
		const response = responses[index++];
		if (response instanceof Error) throw response;
		return typeof response === "function"
			? response()
			: Response.json(response);
	});
}

test("helper list preserves assigned jobs, including terminal states and API order", () => {
	const data = [
		"ACCEPTED",
		"PICKED_UP",
		"DELIVERING",
		"DELIVERED",
		"CANCELLED",
	].map((status, i) => ({ ...request, id: 5 - i, status }));
	const before = structuredClone(data);
	assert.equal(isHelperDeliveries(data), true);
	assert.deepEqual(data, before);
});
test("only a real empty array represents no deliveries", () => {
	assert.equal(isHelperDeliveries([]), true);
	for (const data of [null, undefined, {}, { deliveries: [] }, [null]])
		assert.equal(isHelperDeliveries(data), false);
});
test("unassigned jobs and other helpers' jobs cannot appear on helper 2's list", () => {
	for (const helperId of [null, 1, "2"])
		assert.equal(isHelperDeliveries([{ ...request, helperId }]), false);
	assert.equal(
		isHelperDeliveries([{ ...request, customerId: 2, helperId: 1 }]),
		false,
	);
});
test("malformed and duplicate delivery records are rejected", () => {
	assert.equal(isHelperDeliveries([request, request]), false);
	for (const [field, value] of [
		["id", "../new"],
		["status", "DONE"],
		["title", null],
		["deadline", "2027-01-15"],
		["helperReward", 0],
	]) {
		assert.equal(isHelperDeliveries([{ ...request, [field]: value }]), false);
	}
});

for (const [current, next, label] of [
	["ACCEPTED", "PICKED_UP", "Mark Picked Up"],
	["PICKED_UP", "DELIVERING", "Start Delivery"],
	["DELIVERING", "DELIVERED", "Mark Delivered"],
]) {
	test(`${current} exposes only ${label} and PATCHes ${next} once before refreshing`, async (t) => {
		const start = { ...request, status: current },
			saved = { ...request, status: next };
		assert.deepEqual(deliveryAction(start), { status: next, label });
		const fetch = sequence(t, [saved, [saved]]);
		const result = await advanceDelivery(start);
		assert.equal(result.kind, "success");
		assert.equal(result.requiresRefresh, false);
		assert.deepEqual(result.deliveries, [saved]);
		assert.match(result.message, new RegExp(next));
		assert.equal(fetch.mock.callCount(), 2);
		const [patch, get] = fetch.mock.calls.map((x) => x.arguments);
		assert.equal(patch[0], "https://delivery.example/api/requests/13/status");
		assert.equal(patch[1].method, "PATCH");
		assert.deepEqual(JSON.parse(patch[1].body), { status: next });
		assert.equal(get[0], "https://delivery.example/api/users/2/deliveries");
		assert.equal(get[1].method, undefined);
		assert.equal(get[1].cache, "no-store");
	});
}
test("terminal, unassigned, foreign and invalid deliveries cannot trigger a status mutation", async (t) => {
	const fetch = sequence(t, []);
	for (const data of [
		{ ...request, status: "OPEN" },
		{ ...request, status: "DELIVERED" },
		{ ...request, status: "CANCELLED" },
		{ ...request, helperId: 1 },
		{ ...request, helperId: null },
	]) {
		assert.equal(deliveryAction(data), null);
		await assert.rejects(advanceDelivery(data), { status: 409 });
	}
	await assert.rejects(advanceDelivery({ ...request, id: "../new" }), {
		status: 409,
	});
	assert.equal(fetch.mock.callCount(), 0);
});
test("fetch validates the list and preserves HTTP errors", async (t) => {
	sequence(t, [
		[request],
		[{ ...request, helperId: 1 }],
		() => Response.json({ message: "Helper not found." }, { status: 404 }),
	]);
	assert.deepEqual(await fetchDeliveries(), [request]);
	await assert.rejects(fetchDeliveries(), { status: 502 });
	await assert.rejects(fetchDeliveries(), {
		status: 404,
		message: "Helper not found.",
	});
});
test("conflicts display the backend error and the refreshed next step without repeating PATCH", async (t) => {
	const fetch = sequence(t, [conflict, [pickedUp]]);
	const result = await advanceDelivery(request);
	assert.equal(result.kind, "error");
	assert.equal(result.requiresRefresh, false);
	assert.deepEqual(result.deliveries, [pickedUp]);
	assert.match(result.message, /Cannot change delivery status/);
	assert.equal(fetch.mock.callCount(), 2);
});
for (const fresh of [[request], unavailable]) {
	test(`conflict blocks another update when refresh ${Array.isArray(fresh) ? "has not advanced" : "fails"}`, async (t) => {
		sequence(t, [conflict, fresh]);
		const result = await advanceDelivery(request);
		assert.equal(result.kind, "error");
		assert.equal(result.requiresRefresh, true);
	});
}
for (const failure of [
	new TypeError("Disconnected"),
	new DOMException("Timed out", "TimeoutError"),
	unavailable,
]) {
	test(`uncertain ${typeof failure === "function" ? "503" : failure.name} response reconciles the committed status`, async (t) => {
		const fetch = sequence(t, [failure, [pickedUp]]);
		const result = await advanceDelivery(request);
		assert.equal(result.kind, "success");
		assert.equal(result.requiresRefresh, false);
		assert.deepEqual(result.deliveries, [pickedUp]);
		assert.match(result.message, /latest saved status is PICKED_UP/);
		assert.equal(fetch.mock.callCount(), 2);
	});
}
test("lost response can reconcile a later status without claiming this client performed it", async (t) => {
	sequence(t, [
		new TypeError("Disconnected"),
		[{ ...request, status: "DELIVERED" }],
	]);
	const result = await advanceDelivery(request);
	assert.equal(result.kind, "success");
	assert.match(result.message, /latest saved status is DELIVERED/);
});
for (const fresh of [[request], unavailable]) {
	test(`uncertain result requires manual refresh if ${Array.isArray(fresh) ? "unchanged" : "unavailable"}`, async (t) => {
		sequence(t, [new TypeError("Disconnected"), fresh]);
		const result = await advanceDelivery(request);
		assert.equal(result.kind, "error");
		assert.equal(result.requiresRefresh, true);
	});
}
test("saved PATCH result remains visible and further actions block if refresh fails", async (t) => {
	sequence(t, [pickedUp, unavailable]);
	const result = await advanceDelivery(request);
	assert.equal(result.kind, "success");
	assert.deepEqual(result.saved, pickedUp);
	assert.equal(result.requiresRefresh, true);
	assert.match(result.message, /couldn’t reload/);
});
test("an older GET cannot roll back a successfully saved status", async (t) => {
	sequence(t, [pickedUp, [request]]);
	const result = await advanceDelivery(request);
	assert.deepEqual(result.saved, pickedUp);
	assert.equal(result.requiresRefresh, true);
});
for (const status of [404, 422]) {
	test(`business error ${status} preserves its message and refreshes without retrying PATCH`, async (t) => {
		sequence(t, [
			() =>
				Response.json({ message: "Cannot update this delivery." }, { status }),
			[request],
		]);
		const result = await advanceDelivery(request);
		assert.equal(result.kind, "error");
		assert.match(result.message, /Cannot update this delivery/);
	});
}
test("removed assignments disappear after reconciliation", async (t) => {
	sequence(t, [conflict, []]);
	const result = await advanceDelivery(request);
	assert.deepEqual(result.deliveries, []);
	assert.equal(result.kind, "error");
	assert.equal(result.requiresRefresh, false);
});
test("cancelled deliveries are not reported as successful updates after a lost response", async (t) => {
	sequence(t, [
		new TypeError("Disconnected"),
		[{ ...request, status: "CANCELLED" }],
	]);
	const result = await advanceDelivery(request);
	assert.equal(result.kind, "error");
	assert.equal(deliveryAction(result.deliveries[0]), null);
});
test("invalid mutation response is checked against saved data before showing success", async (t) => {
	sequence(t, [{ ...pickedUp, id: 99 }, [request]]);
	const result = await advanceDelivery(request);
	assert.equal(result.kind, "error");
	assert.equal(result.requiresRefresh, true);
	assert.deepEqual(result.deliveries, [request]);
});
