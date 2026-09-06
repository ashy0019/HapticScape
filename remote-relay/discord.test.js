import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import test from "node:test";
import {
  DiscordLinkTicket,
  DiscordUser,
  handleDiscordInteraction,
  verifyDiscordSignature,
} from "./discord.js";

test("Discord interaction signatures are verified against the raw body", async () => {
  const keys = await webcrypto.subtle.generateKey("Ed25519", true, ["sign", "verify"]);
  const publicKey = bytesHex(new Uint8Array(
    await webcrypto.subtle.exportKey("raw", keys.publicKey),
  ));
  const body = JSON.stringify({ type: 1 });
  const timestamp = String(Math.floor(Date.now() / 1000));
  const signature = bytesHex(new Uint8Array(await webcrypto.subtle.sign(
    "Ed25519",
    keys.privateKey,
    new TextEncoder().encode(timestamp + body),
  )));

  assert.equal(
    await verifyDiscordSignature(body, signature, timestamp, publicKey),
    true,
  );
  assert.equal(
    await verifyDiscordSignature(body + " ", signature, timestamp, publicKey),
    false,
  );
  assert.equal(
    await verifyDiscordSignature(
      body,
      signature,
      String(Number(timestamp) - 301),
      publicKey,
    ),
    false,
  );

  const response = await handleDiscordInteraction(
    new Request("https://relay.example/discord/interactions", {
      method: "POST",
      headers: {
        "X-Signature-Ed25519": signature,
        "X-Signature-Timestamp": timestamp,
      },
      body,
    }),
    { DISCORD_PUBLIC_KEY: publicKey },
    {},
  );
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { type: 1 });
});

test("Discord link tickets bind one device credential and expire after use", async () => {
  const context = fakeDurableContext();
  let linked = null;
  const userObject = {
    async fetch(request) {
      linked = await request.json();
      return new Response(null, { status: 204 });
    },
  };
  const ticket = new DiscordLinkTicket(context, {
    DISCORD_USERS: fakeNamespace(() => userObject),
  });
  const secret = "s".repeat(43);

  const created = await ticket.fetch(jsonRequest("PUT", "/ticket", {
    userId: "123456789012345678",
    displayName: "Test User",
  }));
  const redeemed = await ticket.fetch(new Request("https://internal/ticket", {
    method: "POST",
    headers: { Authorization: `Bearer ${secret}` },
  }));
  const replayed = await ticket.fetch(new Request("https://internal/ticket", {
    method: "POST",
    headers: { Authorization: `Bearer ${secret}` },
  }));

  assert.equal(created.status, 201);
  assert.equal(redeemed.status, 200);
  assert.equal(replayed.status, 404);
  assert.equal(linked.displayName, "Test User");
  assert.equal(linked.credentialHash.length, 43);
  assert.notEqual(linked.credentialHash, secret);
  assert.deepEqual(await redeemed.json(), {
    userId: "123456789012345678",
    displayName: "Test User",
  });
});

test("linked Discord client receives a request and completes the deferred response", async () => {
  const socket = fakeSocket();
  const context = fakeDurableContext([socket]);
  const user = new DiscordUser(context, {
    DISCORD_APPLICATION_ID: "987654321098765432",
    DISCORD_API_ORIGIN: "https://discord.test",
  });
  const secret = "d".repeat(43);
  const credentialHash = await sha256Base64Url(secret);
  await user.fetch(jsonRequest("PUT", "/link", {
    credentialHash,
    displayName: "Controller",
  }));

  const status = await user.fetch(new Request("https://internal/status"));
  assert.deepEqual(await status.json(), {
    linked: true,
    online: true,
    displayName: "Controller",
  });

  const requested = await user.fetch(jsonRequest("POST", "/request", {
    requestId: "abcdefghijklmnop",
    interactionToken: "interaction-token-long-enough",
  }));
  assert.equal(requested.status, 202);
  assert.deepEqual(JSON.parse(socket.sent[0]), {
    type: "PAIR_REQUEST",
    requestId: "abcdefghijklmnop",
  });

  const originalFetch = globalThis.fetch;
  let delivery = null;
  globalThis.fetch = async (url, options) => {
    delivery = { url, options };
    return new Response(null, { status: 200 });
  };
  try {
    await user.webSocketMessage(socket, JSON.stringify({
      type: "PAIR_RESPONSE",
      requestId: "abcdefghijklmnop",
      code: "HSP1." + "c".repeat(43),
    }));
  } finally {
    globalThis.fetch = originalFetch;
  }

  assert.equal(
    delivery.url,
    "https://discord.test/api/v10/webhooks/987654321098765432/"
      + "interaction-token-long-enough/messages/@original",
  );
  assert.match(JSON.parse(delivery.options.body).content, /HSP1\.c{43}/);
  assert.equal(await context.storage.get("pending"), undefined);
});

test("device unlink requires the locally held bearer credential", async () => {
  const context = fakeDurableContext();
  const user = new DiscordUser(context, {});
  const secret = "z".repeat(43);
  await user.fetch(jsonRequest("PUT", "/link", {
    credentialHash: await sha256Base64Url(secret),
    displayName: "Controller",
  }));

  const rejected = await user.fetch(new Request("https://internal/device", {
    method: "DELETE",
    headers: { Authorization: `Bearer ${"x".repeat(43)}` },
  }));
  const removed = await user.fetch(new Request("https://internal/device", {
    method: "DELETE",
    headers: { Authorization: `Bearer ${secret}` },
  }));

  assert.equal(rejected.status, 401);
  assert.equal(removed.status, 204);
  assert.equal((await (await user.fetch(new Request("https://internal/status"))).json()).linked, false);
});

test("a failed Discord response delivery tells the client to cancel its session", async () => {
  const socket = fakeSocket();
  const context = fakeDurableContext([socket]);
  const user = new DiscordUser(context, {
    DISCORD_APPLICATION_ID: "987654321098765432",
    DISCORD_API_ORIGIN: "https://discord.test",
  });
  const secret = "q".repeat(43);
  await user.fetch(jsonRequest("PUT", "/link", {
    credentialHash: await sha256Base64Url(secret),
    displayName: "Controller",
  }));
  await user.fetch(jsonRequest("POST", "/request", {
    requestId: "ponmlkjihgfedcba",
    interactionToken: "interaction-token-long-enough",
  }));

  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(null, { status: 500 });
  try {
    await user.webSocketMessage(socket, JSON.stringify({
      type: "PAIR_RESPONSE",
      requestId: "ponmlkjihgfedcba",
      code: "HSP1." + "r".repeat(43),
    }));
  } finally {
    globalThis.fetch = originalFetch;
  }

  assert.deepEqual(JSON.parse(socket.sent[1]), {
    type: "PAIR_DELIVERY_FAILED",
    requestId: "ponmlkjihgfedcba",
  });
  assert.equal(await context.storage.get("pending"), undefined);
});

test("an expired Discord request edits the deferred response", async () => {
  const context = fakeDurableContext();
  const user = new DiscordUser(context, {
    DISCORD_APPLICATION_ID: "987654321098765432",
    DISCORD_API_ORIGIN: "https://discord.test",
  });
  await context.storage.put("pending", {
    requestId: "abcdefghijklmnop",
    interactionToken: "interaction-token-long-enough",
    expiresAt: Date.now() - 1,
  });

  const originalFetch = globalThis.fetch;
  let content = null;
  globalThis.fetch = async (_url, options) => {
    content = JSON.parse(options.body).content;
    return new Response(null, { status: 200 });
  };
  try {
    await user.alarm();
  } finally {
    globalThis.fetch = originalFetch;
  }

  assert.match(content, /cancelled or timed out/);
  assert.equal(await context.storage.get("pending"), undefined);
});

test("Discord connect accepts only a one-to-one DM and defers code delivery", async () => {
  let dispatched = null;
  const userObject = {
    async fetch(request) {
      dispatched = await request.json();
      return new Response(null, { status: 202 });
    },
  };
  const command = {
    type: 2,
    token: "interaction-token-long-enough",
    context: 2,
    channel: { type: 1 },
    user: { id: "123456789012345678", username: "Controller" },
    data: {
      name: "hapticscape",
      options: [{ type: 1, name: "connect" }],
    },
  };
  const signed = await signedInteraction(command);
  let backgroundWork = null;
  const response = await handleDiscordInteraction(
    signed.request,
    {
      DISCORD_PUBLIC_KEY: signed.publicKey,
      DISCORD_USERS: fakeNamespace(() => userObject),
    },
    {
      waitUntil(work) {
        backgroundWork = work;
      },
    },
  );
  await backgroundWork;

  assert.deepEqual(await response.json(), { type: 5, data: {} });
  assert.equal(dispatched.interactionToken, "interaction-token-long-enough");
  assert.match(dispatched.requestId, /^[A-Za-z0-9_-]{16}$/);

  command.context = 0;
  command.channel.type = 0;
  dispatched = null;
  const rejected = await signedInteraction(command);
  const rejectedResponse = await handleDiscordInteraction(
    rejected.request,
    {
      DISCORD_PUBLIC_KEY: rejected.publicKey,
      DISCORD_USERS: fakeNamespace(() => userObject),
    },
    {},
  );
  const rejectedBody = await rejectedResponse.json();
  assert.equal(rejectedBody.type, 4);
  assert.match(rejectedBody.data.content, /one-to-one Discord DM/);
  assert.equal(dispatched, null);
});

function fakeDurableContext(sockets = []) {
  const values = new Map();
  return {
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
        values.set("alarm", timestamp);
      },
    },
    getWebSockets() {
      return sockets;
    },
    acceptWebSocket() {},
  };
}

function fakeNamespace(resolve) {
  return {
    idFromName(name) {
      return name;
    },
    get(id) {
      return resolve(id);
    },
  };
}

function fakeSocket() {
  return {
    sent: [],
    send(message) {
      this.sent.push(message);
    },
    close() {},
  };
}

function jsonRequest(method, path, body) {
  return new Request(`https://internal${path}`, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

async function sha256Base64Url(value) {
  const digest = new Uint8Array(await webcrypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(value),
  ));
  return Buffer.from(digest).toString("base64url");
}

async function signedInteraction(body) {
  const keys = await webcrypto.subtle.generateKey("Ed25519", true, ["sign", "verify"]);
  const publicKey = bytesHex(new Uint8Array(
    await webcrypto.subtle.exportKey("raw", keys.publicKey),
  ));
  const text = JSON.stringify(body);
  const timestamp = String(Math.floor(Date.now() / 1000));
  const signature = bytesHex(new Uint8Array(await webcrypto.subtle.sign(
    "Ed25519",
    keys.privateKey,
    new TextEncoder().encode(timestamp + text),
  )));
  return {
    publicKey,
    request: new Request("https://relay.example/discord/interactions", {
      method: "POST",
      headers: {
        "X-Signature-Ed25519": signature,
        "X-Signature-Timestamp": timestamp,
      },
      body: text,
    }),
  };
}

function bytesHex(bytes) {
  return Array.from(bytes, value => value.toString(16).padStart(2, "0")).join("");
}
