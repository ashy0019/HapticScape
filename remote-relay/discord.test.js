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

test("linked clients complete Discord acceptance without exposing the pairing code", async () => {
  const controllerSocket = fakeSocket();
  const participantSocket = fakeSocket();
  const controllerContext = fakeDurableContext([controllerSocket]);
  const participantContext = fakeDurableContext([participantSocket]);
  const objects = new Map();
  const env = {
    DISCORD_APPLICATION_ID: "987654321098765432",
    DISCORD_API_ORIGIN: "https://discord.test",
    DISCORD_USERS: fakeNamespace(id => objects.get(id)),
  };
  const controller = new DiscordUser(controllerContext, env);
  const participant = new DiscordUser(participantContext, env);
  objects.set("123456789012345678", controller);
  objects.set("223456789012345678", participant);
  const controllerSecret = "d".repeat(43);
  const participantSecret = "p".repeat(43);
  await linkUser(controller, "123456789012345678", "Controller", controllerSecret);
  await linkUser(participant, "223456789012345678", "Participant", participantSecret);
  const acceptToken = "a".repeat(43);

  const requested = await controller.fetch(jsonRequest("POST", "/request", {
    requestId: "abcdefghijklmnop",
    controllerId: "123456789012345678",
    participantId: "223456789012345678",
    participantName: "Participant",
    acceptTokenHash: await sha256Base64Url(acceptToken),
    interactionToken: "interaction-token-long-enough",
  }));
  assert.equal(requested.status, 202);
  assert.equal(controllerSocket.sent.length, 0);

  const originalFetch = globalThis.fetch;
  const discordUpdates = [];
  globalThis.fetch = async (url, options) => {
    discordUpdates.push({ url, body: JSON.parse(options.body) });
    return new Response(null, { status: 200 });
  };
  try {
    const accepted = await participant.fetch(new Request("https://internal/accept", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${participantSecret}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        controllerId: "123456789012345678",
        requestId: "abcdefghijklmnop",
        acceptToken,
        participantPublicKey: "p".repeat(392),
      }),
    }));
    assert.equal(accepted.status, 202);
    assert.deepEqual(JSON.parse(controllerSocket.sent[0]), {
      type: "PAIR_REQUEST",
      requestId: "abcdefghijklmnop",
      participantPublicKey: "p".repeat(392),
    });

    await controller.webSocketMessage(controllerSocket, JSON.stringify({
      type: "PAIR_RESPONSE",
      requestId: "abcdefghijklmnop",
      encryptedCode: "e".repeat(342),
      relayUrl: "wss://relay.example/relay",
    }));
    const join = JSON.parse(participantSocket.sent[0]);
    assert.equal(join.type, "JOIN_REQUEST");
    assert.equal(join.encryptedCode, "e".repeat(342));
    assert.equal(join.controllerName, "Controller");

    await participant.webSocketMessage(participantSocket, JSON.stringify({
      type: "JOIN_RESULT",
      requestId: "abcdefghijklmnop",
      result: "JOINING",
    }));
  } finally {
    globalThis.fetch = originalFetch;
  }

  assert.equal(await controllerContext.storage.get("pending"), undefined);
  assert.equal(await participantContext.storage.get("incoming"), undefined);
  assert.match(discordUpdates.at(-1).body.content, /approved the local consent prompt/);
  assert.doesNotMatch(
    [
      ...discordUpdates.map(update => JSON.stringify(update.body)),
      ...controllerSocket.sent,
      ...participantSocket.sent,
    ].join("\n"),
    /HSP1\./,
  );
});

test("an immediate participant result cannot recreate a completed request", async () => {
  const controllerSocket = fakeSocket();
  const controllerContext = fakeDurableContext([controllerSocket]);
  let controller;
  const participant = {
    async fetch(request) {
      const delivered = await request.json();
      await controller.fetch(jsonRequest("POST", "/participant-result", {
        participantId: "223456789012345678",
        requestId: delivered.requestId,
        result: "JOINING",
      }));
      return new Response(null, { status: 202 });
    },
  };
  const env = {
    DISCORD_APPLICATION_ID: "987654321098765432",
    DISCORD_API_ORIGIN: "https://discord.test",
    DISCORD_USERS: fakeNamespace(id => id === "123456789012345678"
      ? controller
      : participant),
  };
  controller = new DiscordUser(controllerContext, env);
  await linkUser(controller, "123456789012345678", "Controller", "c".repeat(43));
  await controllerContext.storage.put("pending", {
    requestId: "abcdefghijklmnop",
    controllerId: "123456789012345678",
    participantId: "223456789012345678",
    participantName: "Participant",
    interactionToken: "interaction-token-long-enough",
    state: "accepted",
    expiresAt: Date.now() + 60_000,
  });

  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(null, { status: 200 });
  try {
    await controller.webSocketMessage(controllerSocket, JSON.stringify({
      type: "PAIR_RESPONSE",
      requestId: "abcdefghijklmnop",
      encryptedCode: "e".repeat(342),
      relayUrl: "wss://relay.example/relay",
    }));
  } finally {
    globalThis.fetch = originalFetch;
  }

  assert.equal(await controllerContext.storage.get("pending"), undefined);
});

test("device unlink requires the locally held bearer credential", async () => {
  const context = fakeDurableContext();
  const user = new DiscordUser(context, {});
  const secret = "z".repeat(43);
  await user.fetch(jsonRequest("PUT", "/link", {
    userId: "123456789012345678",
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

test("an intercepted accept token cannot be used by a different linked account", async () => {
  const controllerContext = fakeDurableContext([fakeSocket()]);
  const attackerContext = fakeDurableContext([fakeSocket()]);
  const objects = new Map();
  const env = { DISCORD_USERS: fakeNamespace(id => objects.get(id)) };
  const controller = new DiscordUser(controllerContext, env);
  const attacker = new DiscordUser(attackerContext, env);
  objects.set("123456789012345678", controller);
  objects.set("323456789012345678", attacker);
  await linkUser(controller, "123456789012345678", "Controller", "c".repeat(43));
  await linkUser(attacker, "323456789012345678", "Attacker", "x".repeat(43));
  const token = "t".repeat(43);
  await controller.fetch(jsonRequest("POST", "/request", {
    requestId: "ponmlkjihgfedcba",
    controllerId: "123456789012345678",
    participantId: "223456789012345678",
    participantName: "Participant",
    acceptTokenHash: await sha256Base64Url(token),
    interactionToken: "interaction-token-long-enough",
  }));

  const response = await attacker.fetch(new Request("https://internal/accept", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${"x".repeat(43)}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      controllerId: "123456789012345678",
      requestId: "ponmlkjihgfedcba",
      acceptToken: token,
      participantPublicKey: "p".repeat(392),
    }),
  }));
  assert.equal(response.status, 404);
  assert.equal(controllerContext.getWebSockets()[0].sent.length, 0);
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

test("Discord connect creates partner-bound Accept and Deny controls", async () => {
  let dispatched = null;
  const controllerObject = {
    async fetch(request) {
      const url = typeof request === "string" ? request : request.url;
      if (new URL(url).pathname === "/status") {
        return jsonResponseForTest({ linked: true, online: true, displayName: "Controller" });
      }
      dispatched = await request.json();
      return new Response(null, { status: 202 });
    },
  };
  const participantObject = {
    async fetch() {
      return jsonResponseForTest({ linked: true, online: false, displayName: "Participant" });
    },
  };
  const command = {
    type: 2,
    token: "interaction-token-long-enough",
    context: 2,
    channel: {
      type: 1,
      recipients: [{ id: "223456789012345678", username: "Participant" }],
    },
    user: { id: "123456789012345678", username: "Controller" },
    data: {
      name: "hapticscape",
      options: [{ type: 1, name: "connect" }],
    },
  };
  const signed = await signedInteraction(command);
  const response = await handleDiscordInteraction(
    signed.request,
    {
      DISCORD_PUBLIC_KEY: signed.publicKey,
      DISCORD_USERS: fakeNamespace(id => id === "123456789012345678"
        ? controllerObject
        : participantObject),
    },
  );
  const responseBody = await response.json();
  assert.equal(responseBody.type, 4);
  assert.match(responseBody.data.content, /local consent prompt/);
  assert.equal(responseBody.data.components[0].components[0].label, "Accept");
  assert.match(responseBody.data.components[0].components[0].url, /\/discord\/accept\//);
  assert.equal(responseBody.data.components[0].components[1].label, "Deny");
  assert.equal(dispatched.interactionToken, "interaction-token-long-enough");
  assert.equal(dispatched.participantId, "223456789012345678");
  assert.match(dispatched.requestId, /^[A-Za-z0-9_-]{16}$/);
  assert.match(dispatched.acceptTokenHash, /^[A-Za-z0-9_-]{43}$/);

  command.context = 0;
  command.channel.type = 0;
  dispatched = null;
  const rejected = await signedInteraction(command);
  const rejectedResponse = await handleDiscordInteraction(
    rejected.request,
    {
      DISCORD_PUBLIC_KEY: rejected.publicKey,
      DISCORD_USERS: fakeNamespace(() => controllerObject),
    },
    {},
  );
  const rejectedBody = await rejectedResponse.json();
  assert.equal(rejectedBody.type, 4);
  assert.match(rejectedBody.data.content, /one-to-one Discord DM/);
  assert.equal(dispatched, null);
});

test("only the intended participant can deny a Discord request", async () => {
  const controllerId = "123456789012345678";
  const participantId = "223456789012345678";
  let denied = null;
  const controller = {
    async fetch(request) {
      denied = await request.json();
      return denied.participantId === participantId
        ? new Response(null, { status: 204 })
        : jsonResponseForTest({ error: "Only the invited partner can deny this request" }, 403);
    },
  };
  const interaction = {
    type: 3,
    user: { id: participantId, username: "Participant" },
    data: { custom_id: `hsc_deny:${controllerId}:abcdefghijklmnop` },
  };
  const signed = await signedInteraction(interaction);
  const response = await handleDiscordInteraction(signed.request, {
    DISCORD_PUBLIC_KEY: signed.publicKey,
    DISCORD_USERS: fakeNamespace(() => controller),
  });
  const body = await response.json();

  assert.equal(body.type, 7);
  assert.deepEqual(body.data.components, []);
  assert.deepEqual(denied, {
    participantId,
    requestId: "abcdefghijklmnop",
  });
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

async function linkUser(user, userId, displayName, secret) {
  await user.fetch(jsonRequest("PUT", "/link", {
    userId,
    credentialHash: await sha256Base64Url(secret),
    displayName,
  }));
}

function jsonResponseForTest(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
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
