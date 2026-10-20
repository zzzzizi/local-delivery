import assert from "node:assert/strict";
import test from "node:test";
import { isAvailableJobs } from "../src/lib/available-jobs.ts";

const job = {
	id: 11,
	category: "BUY",
	title: "Buy groceries",
	pickupAddress: "KIWI Majorstuen, Oslo",
	deliveryAddress: "Frogner, Oslo",
	helperReward: "80.50",
	deadline: "2027-01-15T17:00:00Z",
	status: "OPEN",
};

test("accepts all categories in API order without altering returned data", () => {
	const jobs = [
		{ ...job, id: 3, category: "PACKAGE", helperReward: "0.00" },
		{ ...job, id: 2, category: "BUY" },
		{ ...job, id: 1, category: "PICKUP", helperReward: "99999999.99" },
	];
	const before = structuredClone(jobs);
	assert.equal(isAvailableJobs(jobs), true);
	assert.deepEqual(jobs, before);
});

test("only a real empty array represents no available jobs", () => {
	assert.equal(isAvailableJobs([]), true);
	for (const data of [
		undefined,
		null,
		{},
		{ requests: [] },
		"[]",
		[null],
		["job"],
	]) {
		assert.equal(isAvailableJobs(data), false);
	}
});

test("rejects invalid and duplicate identifiers before building detail links", () => {
	for (const id of [0, -1, 1.5, "11", Number.MAX_SAFE_INTEGER + 1, "../new"]) {
		assert.equal(isAvailableJobs([{ ...job, id }]), false);
	}
	assert.equal(isAvailableJobs([job, { ...job }]), false);
});

test("rejects unavailable statuses and unknown categories", () => {
	for (const status of [
		"ACCEPTED",
		"PICKED_UP",
		"DELIVERING",
		"DELIVERED",
		"CANCELLED",
		"open",
		undefined,
	]) {
		assert.equal(isAvailableJobs([{ ...job, status }]), false);
	}
	for (const category of ["buy", "INVALID", "toString", null]) {
		assert.equal(isAvailableJobs([{ ...job, category }]), false);
	}
});

test("requires displayable text for the title and both addresses", () => {
	for (const field of ["title", "pickupAddress", "deliveryAddress"]) {
		for (const value of [undefined, null, 42, {}]) {
			assert.equal(isAvailableJobs([{ ...job, [field]: value }]), false);
		}
	}
});

test("requires the backend's exact nonnegative decimal-string reward contract", () => {
	for (const helperReward of [
		80.5,
		"80.5",
		"-1.00",
		"NaN",
		"1e2",
		"100000000.00",
		null,
	]) {
		assert.equal(isAvailableJobs([{ ...job, helperReward }]), false);
	}
});

test("requires an explicit deadline offset so browser timezone cannot change its meaning", () => {
	for (const deadline of [
		"2027-01-15T17:00:00Z",
		"2027-01-15T18:00:00+01:00",
		"2027-01-15T17:00:00.123456Z",
	]) {
		assert.equal(isAvailableJobs([{ ...job, deadline }]), true);
	}
	for (const deadline of [
		"2027-01-15T18:00:00",
		"2027-01-15",
		"tomorrow",
		"2027-99-99T17:00:00Z",
		null,
	]) {
		assert.equal(isAvailableJobs([{ ...job, deadline }]), false);
	}
});
