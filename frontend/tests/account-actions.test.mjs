import assert from "node:assert/strict";
import test from "node:test";
import { accountTokenFromHash, accountActionBody } from "../src/lib/account-actions.ts";

const token = "a".repeat(43);
test("mail tokens are accepted only once and with the expected shape in the fragment", () => {
    assert.equal(accountTokenFromHash(`#token=${token}`), token);
    for (const hash of ["", "#token=short", `#token=${token}&token=${token}`, `#token=${"a".repeat(44)}`, `#token=${"!".repeat(43)}`]) assert.equal(accountTokenFromHash(hash), "");
});
test("email actions contain no caller-selected identity or reset token", () => {
    for (const action of ["forgot-password", "resend-verification"]) {
        assert.deepEqual(accountActionBody(action, { email: " trial@example.com ", token, password: "ignored" }), { email: "trial@example.com" });
        assert.throws(() => accountActionBody(action, { email: " " }), /Enter your email/);
    }
});
test("verification requires a valid token and sends no password", () => {
    assert.deepEqual(accountActionBody("verify-email", { token, password: "ignored" }), { token });
    assert.throws(() => accountActionBody("verify-email", {}), /missing or invalid/);
});
test("reset preserves password whitespace and requires matching confirmation", () => {
    const password = "  a strong passphrase  ";
    assert.deepEqual(accountActionBody("reset-password", { token, password, confirmation: password }), { token, password });
    assert.throws(() => accountActionBody("reset-password", { token, password, confirmation: "different" }), /do not match/);
});
test("reset enforces password length and rejects malformed tokens before submission", () => {
    for (const password of ["short", "x".repeat(129)]) assert.throws(() => accountActionBody("reset-password", { token, password, confirmation: password }), /12 and 128/);
    assert.throws(() => accountActionBody("reset-password", { token: "bad", password: "test-password-21", confirmation: "test-password-21" }), /missing or invalid/);
});
