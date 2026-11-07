import assert from "node:assert/strict";
import { afterEach, beforeEach, test } from "node:test";
import { decideApplication, fetchApplications, isApplication, requestJob } from "../src/lib/job-applications.ts";
import { deliveryAction } from "../src/lib/helper-deliveries.ts";
const originalUrl = process.env.NEXT_PUBLIC_API_URL;
beforeEach(() => { process.env.NEXT_PUBLIC_API_URL = "https://delivery.example/api"; });
afterEach(() => { if (originalUrl === undefined) delete process.env.NEXT_PUBLIC_API_URL; else process.env.NEXT_PUBLIC_API_URL = originalUrl; });
const pending = { id: 8, requestId: 12, requestTitle: "Parcel", requestStatus: "OPEN", customerId: 1, helperId: 2, helperName: "Helper", status: "PENDING", createdAt: "2026-11-02T12:00:00Z", updatedAt: "2026-11-02T12:00:00Z" };
const approved = { id: 12, customerId: 1, helperId: 2, status: "ACCEPTED", category: "PACKAGE", title: "Parcel", description: "Small parcel", pickupAddress: "Shop", deliveryAddress: "Home", shoppingBudget: null, helperReward: "50.00", deadline: "2030-01-01T12:00:00Z", createdAt: pending.createdAt, updatedAt: pending.updatedAt };

test("helper requests approval using session identity and never sets an assignment", async t => {
    t.mock.method(globalThis, "fetch", async (url, init) => {
        assert.equal(url, "https://delivery.example/api/requests/12/applications");
        assert.equal(init.method, "POST"); assert.deepEqual(JSON.parse(init.body), {});
        return Response.json(pending);
    });
    assert.deepEqual(await requestJob(12, 2), pending);
    assert.equal(deliveryAction({ ...approved, status: "OPEN", helperId: null }, 2), null);
});
test("application validation permits every outcome and rejects malformed identities", () => {
    for (const status of ["PENDING", "APPROVED", "DECLINED", "WITHDRAWN", "NOT_SELECTED", "CANCELLED"]) assert.equal(isApplication({ ...pending, status }), true);
    for (const bad of [null, {}, { ...pending, helperId: "2" }, { ...pending, status: "ACCEPTED" }, { ...pending, status: "toString" }, { ...pending, updatedAt: "bad" }]) assert.equal(isApplication(bad), false);
});
test("submission rejects a different helper, job or unexpected outcome", async t => {
    let body;
    t.mock.method(globalThis, "fetch", async () => Response.json(body));
    for (body of [{ ...pending, helperId: 3 }, { ...pending, requestId: 13 }, { ...pending, status: "DECLINED" }]) await assert.rejects(requestJob(12, 2), { status: 502 });
});
test("customer list permits multiple helpers but rejects another customer's data", async t => {
    let rows = [pending, { ...pending, id: 9, helperId: 3 }];
    t.mock.method(globalThis, "fetch", async () => Response.json({ items: rows, hasMore: false }));
    assert.equal((await fetchApplications(1, 0, 12)).items.length, 2);
    await assert.rejects(fetchApplications(4, 0, 12), { status: 502 });
    rows = [pending, pending]; await assert.rejects(fetchApplications(1, 0, 12), { status: 502 });
});
test("My Deliveries request history is restricted to the current helper", async t => {
    t.mock.method(globalThis, "fetch", async url => {
        assert.match(url, /\/me\/applications\?page=1$/);
        return Response.json({ items: [pending], hasMore: false });
    });
    assert.equal((await fetchApplications(2, 1)).items.length, 1);
    await assert.rejects(fetchApplications(1, 1), { status: 502 });
});
test("approval uses the exact application and validates the customer's assignment", async t => {
    t.mock.method(globalThis, "fetch", async (url, init) => {
        assert.equal(url, "https://delivery.example/api/requests/12/applications/8/approve");
        assert.equal(init.method, "POST"); assert.deepEqual(JSON.parse(init.body), {});
        return Response.json(approved);
    });
    assert.deepEqual(await decideApplication(pending, "approve"), approved);
});
for (const patch of [{ helperId: 3 }, { customerId: 3 }, { id: 13 }, { status: "OPEN" }]) {
    test(`approval rejects mismatched response ${JSON.stringify(patch)}`, async t => {
        t.mock.method(globalThis, "fetch", async () => Response.json({ ...approved, ...patch }));
        await assert.rejects(decideApplication(pending, "approve"), { status: 502 });
    });
}
for (const action of ["decline", "withdraw"]) {
    test(`${action} validates the persisted outcome`, async t => {
        const saved = { ...pending, status: action === "decline" ? "DECLINED" : "WITHDRAWN" };
        t.mock.method(globalThis, "fetch", async (url, init) => {
            assert.ok(url.endsWith(`/${action}`)); assert.equal(init.method, "POST");
            return Response.json(saved);
        });
        assert.deepEqual(await decideApplication(pending, action), saved);
    });
}
for (const status of [401, 403, 409, 429, 503]) {
    test(`failed approval ${status} is preserved without automatic mutation retries`, async t => {
        const fetch = t.mock.method(globalThis, "fetch", async () => Response.json({ message: "Refresh and try again." }, { status }));
        await assert.rejects(decideApplication(pending, "approve"), { status });
        assert.equal(fetch.mock.callCount(), 1);
    });
}
test("uncertain submissions are not silently retried", async t => {
    const fetch = t.mock.method(globalThis, "fetch", async () => { throw new TypeError("Connection lost"); });
    await assert.rejects(requestJob(12, 2), /Connection lost/);
    assert.equal(fetch.mock.callCount(), 1);
});
