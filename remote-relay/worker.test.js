import assert from "node:assert/strict";
import test from "node:test";
import worker, { SessionRoom } from "./worker.js";

test("health endpoint does not allocate a room", async () => {
  const response = await worker.fetch(
    new Request("https://relay.example/"),
    {},
  );

  assert.equal(response.status, 200);
  assert.equal(await response.text(), "HapticScape remote relay");
});

test("relay endpoint rejects invalid room names before durable object lookup", async () => {
  const response = await worker.fetch(
    new Request("https://relay.example/relay?room=bad"),
    {},
  );

  assert.equal(response.status, 400);
});

test("session room requires a websocket upgrade and a valid role", async () => {
  const room = new SessionRoom(fakeContext(), {});
  const ordinary = await room.fetch({
    headers: new Headers(),
    url: "https://relay.example/relay?role=controller",
  });
  const invalidRole = await room.fetch({
    headers: new Headers({ Upgrade: "websocket" }),
    url: "https://relay.example/relay?role=observer",
  });

  assert.equal(ordinary.status, 426);
  assert.equal(invalidRole.status, 400);
});

test("session room forwards opaque text only to the other peer", () => {
  const sender = fakeSocket();
  const peer = fakeSocket();
  const room = new SessionRoom(fakeContext([sender, peer]), {});

  room.webSocketMessage(sender, "encrypted-payload");

  assert.deepEqual(sender.sent, []);
  assert.deepEqual(peer.sent, ["encrypted-payload"]);
});

test("session room closes binary and oversized messages", () => {
  const binarySender = fakeSocket();
  const largeSender = fakeSocket();
  const room = new SessionRoom(fakeContext([binarySender, largeSender]), {});

  room.webSocketMessage(binarySender, new Uint8Array([1, 2, 3]));
  room.webSocketMessage(largeSender, "x".repeat(128 * 1024 + 1));

  assert.deepEqual(binarySender.closed, [1003, "Text messages only"]);
  assert.deepEqual(largeSender.closed, [1009, "Message too large"]);
});

function fakeContext(sockets = []) {
  return {
    getWebSockets() {
      return sockets;
    },
  };
}

function fakeSocket() {
  return {
    sent: [],
    closed: null,
    send(message) {
      this.sent.push(message);
    },
    close(code, reason) {
      this.closed = [code, reason];
    },
  };
}
