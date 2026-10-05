import assert from "node:assert/strict";
import test from "node:test";

test("the running frontend serves the setup page", async () => {
  const response = await fetch("http://localhost:3000", {
    signal: AbortSignal.timeout(15000),
  });

  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type"), /text\/html/);

  const html = await response.text();
  assert.match(html, /<h1>Local Delivery<\/h1>/);
  assert.match(html, /<title>Local Delivery<\/title>/);
});
