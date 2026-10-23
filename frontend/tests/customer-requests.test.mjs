import assert from "node:assert/strict";
import test from "node:test";
import { isCustomerRequests } from "../src/lib/customer-requests.ts";

const request = {
	id: 12,
	customerId: 1,
	helperId: null,
	category: "BUY",
	title: "Groceries",
	description: "Milk and bread",
	pickupAddress: "Demo shop",
	deliveryAddress: "Demo home",
	shoppingBudget: null,
	helperReward: "0.00",
	deadline: "2027-01-15T17:00:00Z",
	status: "OPEN",
	createdAt: "2026-10-21T07:00:00Z",
	updatedAt: "2026-10-21T07:00:00Z",
};

test("accepts every tracking status without filtering or reordering the response", () => {
	const requests = [
		"OPEN",
		"ACCEPTED",
		"PICKED_UP",
		"DELIVERING",
		"DELIVERED",
		"CANCELLED",
	].map((status, index) => ({
		...request,
		id: 6 - index,
		status,
		helperId: status === "OPEN" ? null : 2,
	}));
	const before = structuredClone(requests);
	assert.equal(isCustomerRequests(requests), true);
	assert.deepEqual(requests, before);
});
test("accepts all categories, assigned helpers, and no helper", () => {
	for (const category of ["PACKAGE", "BUY", "PICKUP"]) {
		for (const helperId of [null, 2, 3])
			assert.equal(
				isCustomerRequests([{ ...request, category, helperId }]),
				true,
			);
	}
});
test("only an empty array represents a customer with no requests", () => {
	assert.equal(isCustomerRequests([]), true);
	for (const data of [null, undefined, {}, { requests: [] }, "[]", [null]])
		assert.equal(isCustomerRequests(data), false);
});
test("rejects another customer's request even when customer 1 is its helper", () => {
	assert.equal(
		isCustomerRequests([
			{ ...request, customerId: 2, helperId: 1, status: "ACCEPTED" },
		]),
		false,
	);
	assert.equal(
		isCustomerRequests([request, { ...request, id: 13, customerId: 2 }]),
		false,
	);
});
test("rejects duplicate and unsafe request IDs before creating links", () => {
	assert.equal(isCustomerRequests([request, { ...request }]), false);
	for (const id of [0, -1, "12", "../new", Number.MAX_SAFE_INTEGER + 1])
		assert.equal(isCustomerRequests([{ ...request, id }]), false);
});
test("rejects invalid display data and unknown statuses", () => {
	for (const [field, value] of [
		["title", null],
		["category", "OTHER"],
		["status", "DONE"],
		["helperId", "2"],
		["deadline", "2027-01-15T17:00:00"],
	]) {
		assert.equal(isCustomerRequests([{ ...request, [field]: value }]), false);
	}
});
