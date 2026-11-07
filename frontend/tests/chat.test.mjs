import assert from "node:assert/strict";
import { beforeEach, test } from "node:test";
import { fetchConversation, fetchConversations, fetchMessages, mergeMessages, sendMessage, startConversation, chatError } from "../src/lib/chat.ts";
import { ApiError } from "../src/lib/api.ts";

const conversation = { id: 7, requestId: 4, requestTitle: "Parcel", status: "OPEN", customerId: 1, customerName: "Customer", helperId: 2, helperName: "Helper", canSend: true, canViewRequest: true, updatedAt: "2026-11-01T12:00:00Z" };
const message = { id: 1, conversationId: 7, senderId: 2, clientMessageId: "e6888e73-6120-451f-a572-d97d13ec3981", body: "Can I collect at 5?", createdAt: "2026-11-01T12:00:00Z" };
beforeEach(() => { process.env.NEXT_PUBLIC_API_URL = "https://delivery.example/api"; });
test("starting a chat uses the request and no caller-selected recipient", async t => {
    t.mock.method(globalThis, "fetch", async (url, options) => {
        assert.equal(url, "https://delivery.example/api/requests/4/conversation");
        assert.equal(options.method, "POST"); assert.equal(options.body, undefined);
        return Response.json(conversation);
    });
    assert.deepEqual(await startConversation(4, 2), conversation);
    await assert.rejects(startConversation(4, 3), { status: 502 });
});
test("conversation details reject a different thread or unrelated user", async t => {
    t.mock.method(globalThis, "fetch", async () => Response.json(conversation));
    assert.deepEqual(await fetchConversation(7, 1), conversation);
    await assert.rejects(fetchConversation(8, 1), { status: 502 });
    await assert.rejects(fetchConversation(7, 3), { status: 502 });
});
test("filtered inbox checks ownership and request identity", async t => {
    t.mock.method(globalThis, "fetch", async url => {
        assert.match(url, /conversations\?page=1&requestId=4$/);
        return Response.json({ items: [conversation], hasMore: false });
    });
    assert.equal((await fetchConversations(1, 1, 4)).items.length, 1);
    await assert.rejects(fetchConversations(3, 1, 4), { status: 502 });
});
test("message history rejects foreign conversations, senders and invalid ordering", async t => {
    let rows = [message];
    t.mock.method(globalThis, "fetch", async () => Response.json({ items: rows, hasMore: false }));
    assert.deepEqual((await fetchMessages(conversation)).items, [message]);
    for (const invalid of [[{ ...message, conversationId: 8 }], [{ ...message, senderId: 3 }], [message, message], [{ ...message, createdAt: "bad" }]]) {
        rows = invalid; await assert.rejects(fetchMessages(conversation), { status: 502 });
    }
});
test("cursor checks prevent gaps from malformed server responses", async t => {
    t.mock.method(globalThis, "fetch", async url => {
        assert.match(url, /messages\?(before|after)=1$/);
        return Response.json({ items: [message], hasMore: false });
    });
    await assert.rejects(fetchMessages(conversation, { before: 1 }), { status: 502 });
    await assert.rejects(fetchMessages(conversation, { after: 1 }), { status: 502 });
});
test("overlapping sends, polls and older pages merge without losing messages", () => {
    const second = { ...message, id: 2, senderId: 1 };
    const third = { ...message, id: 3 };
    assert.deepEqual(mergeMessages([second, third], [message, second]), [message, second, third]);
    assert.deepEqual(mergeMessages([message, second], [second, third]), [message, second, third]);
});
test("send retries reuse the same id and never supply a sender or recipient", async t => {
    const bodies = [];
    t.mock.method(globalThis, "fetch", async (url, options) => {
        assert.match(url, /conversations\/7\/messages$/);
        bodies.push(JSON.parse(options.body));
        if (bodies.length === 1) throw new Error("Connection lost after server saved");
        return Response.json(message);
    });
    await assert.rejects(sendMessage(conversation, 2, message.body, message.clientMessageId));
    assert.deepEqual(await sendMessage(conversation, 2, message.body, message.clientMessageId), message);
    assert.deepEqual(bodies[0], bodies[1]);
    assert.deepEqual(Object.keys(bodies[0]).sort(), ["body", "clientMessageId"]);
});
test("send validates saved text, sender and idempotency key", async t => {
    t.mock.method(globalThis, "fetch", async () => Response.json(message));
    for (const [user, text, key] of [[1, message.body, message.clientMessageId], [2, "Different", message.clientMessageId], [2, message.body, "different-key"]]) {
        await assert.rejects(sendMessage(conversation, user, text, key), { status: 502 });
    }
});
test("malformed or oversized pages do not enter state", async t => {
    let body;
    t.mock.method(globalThis, "fetch", async () => Response.json(body));
    for (body of [{ items: [], hasMore: "yes" }, { items: Array(51).fill(message), hasMore: false }, null]) {
        await assert.rejects(fetchMessages(conversation), { status: 502 });
    }
});
test("session and connectivity failures are actionable", () => {
    assert.match(chatError(new ApiError(401, "Unauthorized")), /Log in/);
    assert.match(chatError(new TypeError("fetch failed")), /connection/);
    assert.equal(chatError(new ApiError(409, "Read-only")), "Read-only");
});
