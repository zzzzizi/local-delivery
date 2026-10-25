import assert from "node:assert/strict";
import test from "node:test";
import {
	emptyRequest,
	formatOsloDate,
	prepareRequest,
} from "../src/lib/create-request.ts";

const valid = {
	category: "BUY",
	title: "  Buy groceries  ",
	description: "  Milk, eggs and bread.  ",
	pickupAddress: "  KIWI Majorstuen, Oslo  ",
	deliveryAddress: "  Frogner, Oslo  ",
	shoppingBudget: "300.25",
	helperReward: "80.50",
	deadline: "2027-01-15T18:00",
};

test("prepares the exact Java create contract, trims text, and keeps the Oslo wall time", () => {
	assert.deepEqual(prepareRequest(valid), {
		payload: {
			category: "BUY",
			title: "Buy groceries",
			description: "Milk, eggs and bread.",
			pickupAddress: "KIWI Majorstuen, Oslo",
			deliveryAddress: "Frogner, Oslo",
			shoppingBudget: 300.25,
			helperReward: 80.5,
			deadline: "2027-01-15T18:00",
		},
	});
});

test("reports every missing required field and allows an omitted budget", () => {
	const result = prepareRequest(emptyRequest);
	assert.equal(result.payload, undefined);
	assert.deepEqual(Object.keys(result.errors).sort(), [
		"category",
		"deadline",
		"deliveryAddress",
		"description",
		"helperReward",
		"pickupAddress",
		"title",
	]);
});

for (const category of ["PACKAGE", "BUY", "PICKUP"]) {
	test(`accepts ${category}, zero reward, and a blank optional budget`, () => {
		const { payload, errors } = prepareRequest({
			...valid,
			category,
			helperReward: "0",
			shoppingBudget: "  ",
		});
		assert.equal(errors, undefined);
		assert.equal(payload.category, category);
		assert.equal(payload.helperReward, 0);
		assert.equal(payload.shoppingBudget, null);
	});
}

for (const category of ["", "buy", "INVALID", "toString", "__proto__"]) {
	test(`rejects unsupported category ${JSON.stringify(category)}`, () => {
		assert.ok(prepareRequest({ ...valid, category }).errors.category);
	});
}

for (const key of [
	"title",
	"description",
	"pickupAddress",
	"deliveryAddress",
]) {
	test(`rejects whitespace-only ${key}`, () => {
		assert.ok(prepareRequest({ ...valid, [key]: " \n\t " }).errors[key]);
	});
}

for (const [key, max] of [
	["title", 200],
	["pickupAddress", 500],
	["deliveryAddress", 500],
]) {
	test(`enforces the Java length limit for ${key}`, () => {
		assert.equal(
			prepareRequest({ ...valid, [key]: "x".repeat(max) }).errors,
			undefined,
		);
		assert.ok(
			prepareRequest({ ...valid, [key]: "x".repeat(max + 1) }).errors[key],
		);
	});
}

for (const key of ["shoppingBudget", "helperReward"]) {
	test(`validates ${key} without rounding invalid money or accepting numeric coercions`, () => {
		for (const value of [
			"-1",
			"0.001",
			"100000000",
			"Infinity",
			"NaN",
			"1e2",
			"0x10",
			"12,50",
			"abc",
		]) {
			const result = prepareRequest({ ...valid, [key]: value });
			assert.ok(result.errors[key], value);
			assert.equal(result.payload, undefined);
		}
		for (const value of ["0", "0.00", "12.5", "99.99", "99999999.99"]) {
			assert.equal(
				prepareRequest({ ...valid, [key]: value }).payload[key],
				Number(value),
			);
		}
	});
}

test("rejects missing/malformed deadlines and leaves future/DST validation to Java", () => {
	for (const deadline of ["", "tomorrow", "2027-01-15", "2027-01-15T18:00Z"]) {
		assert.ok(prepareRequest({ ...valid, deadline }).errors.deadline);
	}
	assert.equal(
		prepareRequest({ ...valid, deadline: "2020-01-01T12:00" }).errors,
		undefined,
	);
});

test("formats saved deadlines in Oslo across winter and summer regardless of browser timezone", () => {
	assert.equal(formatOsloDate("2027-01-15T17:00:00Z"), "15 Jan 2027, 18:00");
	assert.equal(formatOsloDate("2027-07-15T16:00:00Z"), "15 Jul 2027, 18:00");
});
