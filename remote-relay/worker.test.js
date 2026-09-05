import assert from "node:assert/strict";
import test from "node:test";
import worker, { PairingMailbox, SessionRoom } from "./worker.js";

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

test("pairing route validates the locator before durable object lookup", async () => {
  const response = await worker.fetch(
    new Request("https://relay.example/pairing/short"),
    {},
  );

  assert.equal(response.status, 400);
});

test("pairing route selects the mailbox from its opaque locator", async () => {
  let selectedName = null;
  const mailbox = { fetch: async () => new Response("mailbox", { status: 202 }) };
  const response = await worker.fetch(
    new Request("https://relay.example/pairing/AbCdEf0123_-"),
    {
      PAIRING_MAILBOXES: {
        idFromName(name) {
          selectedName = name;
          return "mailbox-id";
        },
        get(id) {
          assert.equal(id, "mailbox-id");
          return mailbox;
        },
      },
    },
  );

  assert.equal(response.status, 202);
  assert.equal(selectedName, "AbCdEf0123_-");
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

test("pairing mailbox stores and redeems an envelope only once", async () => {
  const context = fakePairingContext();
  const mailbox = new PairingMailbox(context, {});
  const redeemProof = "r".repeat(43);
  const cancelProof = "c".repeat(43);

  const created = await mailbox.fetch(pairingRequest("PUT", {
    "X-HapticScape-Redeem-Proof": redeemProof,
    "X-HapticScape-Cancel-Proof": cancelProof,
  }, "encrypted-envelope"));
  const duplicate = await mailbox.fetch(pairingRequest("PUT", {
    "X-HapticScape-Redeem-Proof": redeemProof,
    "X-HapticScape-Cancel-Proof": cancelProof,
  }, "replacement-envelope"));
  const rejected = await mailbox.fetch(pairingRequest("GET", {
    "X-HapticScape-Redeem-Proof": "x".repeat(43),
  }));
  const redeemed = await mailbox.fetch(pairingRequest("GET", {
    "X-HapticScape-Redeem-Proof": redeemProof,
  }));
  const replayed = await mailbox.fetch(pairingRequest("GET", {
    "X-HapticScape-Redeem-Proof": redeemProof,
  }));

  assert.equal(created.status, 201);
  assert.equal(duplicate.status, 409);
  assert.equal(rejected.status, 403);
  assert.equal(redeemed.status, 200);
  assert.equal(await redeemed.text(), "encrypted-envelope");
  assert.equal(replayed.status, 404);
  assert.equal(context.alarms.length, 1);
});

test("pairing mailbox alarm removes an abandoned envelope", async () => {
  const context = fakePairingContext();
  const mailbox = new PairingMailbox(context, {});
  const redeemProof = "r".repeat(43);
  await mailbox.fetch(pairingRequest("PUT", {
    "X-HapticScape-Redeem-Proof": redeemProof,
    "X-HapticScape-Cancel-Proof": "c".repeat(43),
  }, "encrypted-envelope"));

  await mailbox.alarm();
  const missing = await mailbox.fetch(pairingRequest("GET", {
    "X-HapticScape-Redeem-Proof": redeemProof,
  }));

  assert.equal(missing.status, 404);
});

test("pairing mailbox cancellation requires its separate proof", async () => {
  const context = fakePairingContext();
  const mailbox = new PairingMailbox(context, {});
  const redeemProof = "r".repeat(43);
  const cancelProof = "c".repeat(43);
  await mailbox.fetch(pairingRequest("PUT", {
    "X-HapticScape-Redeem-Proof": redeemProof,
    "X-HapticScape-Cancel-Proof": cancelProof,
  }, "encrypted-envelope"));

  const rejected = await mailbox.fetch(pairingRequest("DELETE", {
    "X-HapticScape-Cancel-Proof": "x".repeat(43),
  }));
  const cancelled = await mailbox.fetch(pairingRequest("DELETE", {
    "X-HapticScape-Cancel-Proof": cancelProof,
  }));
  const missing = await mailbox.fetch(pairingRequest("GET", {
    "X-HapticScape-Redeem-Proof": redeemProof,
  }));

  assert.equal(rejected.status, 403);
  assert.equal(cancelled.status, 204);
  assert.equal(missing.status, 404);
});

test("pairing mailbox rejects malformed metadata and oversized envelopes", async () => {
  const mailbox = new PairingMailbox(fakePairingContext(), {});
  const malformed = await mailbox.fetch(pairingRequest("PUT", {}, "payload"));
  const oversized = await mailbox.fetch(pairingRequest("PUT", {
    "X-HapticScape-Redeem-Proof": "r".repeat(43),
    "X-HapticScape-Cancel-Proof": "c".repeat(43),
  }, "x".repeat(4 * 1024 + 1)));

  assert.equal(malformed.status, 400);
  assert.equal(oversized.status, 400);
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

function fakePairingContext() {
  const values = new Map();
  return {
    alarms: [],
    storage: {
      async get(key) {
        return values.get(key);
      },
      async put(key, value) {
        values.set(key, value);
      },
      async delete(key) {
        return values.delete(key);
      },
      async setAlarm(timestamp) {
        this.alarms = timestamp;
      },
    },
    get alarms() {
      return this.storage.alarms == null ? [] : [this.storage.alarms];
    },
  };
}

function pairingRequest(method, headers = {}, body = undefined) {
  return new Request("https://relay.example/pairing/AbCdEf0123_-", {
    method,
    headers,
    body,
  });
}
