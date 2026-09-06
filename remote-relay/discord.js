const LINK_TTL_MILLIS = 5 * 60 * 1000;
const REQUEST_TTL_MILLIS = 2 * 60 * 1000;
const LINK_LOCATOR_PATTERN = /^[A-Za-z0-9_-]{16}$/;
const DISCORD_USER_ID_PATTERN = /^\d{15,22}$/;
const DEVICE_SECRET_PATTERN = /^[A-Za-z0-9_-]{43}$/;
const PAIRING_CODE_PATTERN = /^HSP1\.[A-Za-z0-9_-]{43}$/;
const REQUEST_ID_PATTERN = /^[A-Za-z0-9_-]{16}$/;
const EPHEMERAL_FLAG = 64;
const MAXIMUM_SIGNATURE_AGE_SECONDS = 5 * 60;

export class DiscordLinkTicket {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
  }

  async fetch(request) {
    if (request.method === "PUT") {
      return this.create(request);
    }
    if (request.method === "POST") {
      return this.redeem(request);
    }
    return new Response("Method not allowed", { status: 405 });
  }

  async create(request) {
    const current = await this.readCurrent();
    if (current) {
      return new Response("Link ticket already exists", { status: 409 });
    }
    const body = await readJson(request);
    if (!body || !DISCORD_USER_ID_PATTERN.test(body.userId ?? "")) {
      return new Response("Invalid Discord user", { status: 400 });
    }
    const ticket = {
      userId: body.userId,
      displayName: safeDisplayName(body.displayName),
      expiresAt: Date.now() + LINK_TTL_MILLIS,
    };
    await this.ctx.storage.put("ticket", ticket);
    await this.ctx.storage.setAlarm(ticket.expiresAt);
    return new Response(null, { status: 201 });
  }

  async redeem(request) {
    const ticket = await this.readCurrent();
    if (!ticket) {
      return jsonResponse({ error: "This Discord link code expired or was already used" }, 404);
    }
    const secret = bearerToken(request);
    if (!DEVICE_SECRET_PATTERN.test(secret)) {
      return jsonResponse({ error: "Invalid HapticScape device credential" }, 401);
    }

    const credentialHash = await sha256Base64Url(secret);
    const userId = this.env.DISCORD_USERS.idFromName(ticket.userId);
    const response = await this.env.DISCORD_USERS.get(userId).fetch(new Request(
      "https://discord-user.internal/link",
      {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          credentialHash,
          displayName: ticket.displayName,
        }),
      },
    ));
    if (!response.ok) {
      return jsonResponse({ error: "Unable to link the HapticScape client" }, 502);
    }

    await this.ctx.storage.delete("ticket");
    return jsonResponse({
      userId: ticket.userId,
      displayName: ticket.displayName,
    });
  }

  async readCurrent() {
    const ticket = await this.ctx.storage.get("ticket");
    if (!ticket) {
      return null;
    }
    if (ticket.expiresAt <= Date.now()) {
      await this.ctx.storage.delete("ticket");
      return null;
    }
    return ticket;
  }

  async alarm() {
    await this.ctx.storage.delete("ticket");
  }
}

export class DiscordUser {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
  }

  async fetch(request) {
    const path = new URL(request.url).pathname;
    if (path === "/link" && request.method === "PUT") {
      return this.link(request);
    }
    if (path === "/status" && request.method === "GET") {
      return this.status();
    }
    if (path === "/unlink-by-discord" && request.method === "DELETE") {
      return this.unlink();
    }
    if (path === "/device" && request.method === "DELETE") {
      return this.unlinkFromDevice(request);
    }
    if (path === "/device" && request.method === "GET") {
      return this.connectDevice(request);
    }
    if (path === "/request" && request.method === "POST") {
      return this.requestPairing(request);
    }
    return new Response("Not found", { status: 404 });
  }

  async link(request) {
    const body = await readJson(request);
    if (!body
      || !DEVICE_SECRET_PATTERN.test(body.credentialHash ?? "")
      || typeof body.displayName !== "string") {
      return new Response("Invalid device link", { status: 400 });
    }
    await this.closeDeviceSockets(4003, "Discord link replaced");
    await this.ctx.storage.put("credential", {
      hash: body.credentialHash,
      displayName: safeDisplayName(body.displayName),
    });
    await this.clearPending(true);
    return new Response(null, { status: 204 });
  }

  async status() {
    const credential = await this.ctx.storage.get("credential");
    return jsonResponse({
      linked: Boolean(credential),
      online: Boolean(credential) && this.ctx.getWebSockets("device").length > 0,
      displayName: credential?.displayName ?? "",
    });
  }

  async unlinkFromDevice(request) {
    if (!await this.authorized(request)) {
      return new Response("Invalid device credential", { status: 401 });
    }
    return this.unlink();
  }

  async unlink() {
    await this.closeDeviceSockets(4003, "Discord link removed");
    await this.ctx.storage.delete("credential");
    await this.clearPending(true);
    return new Response(null, { status: 204 });
  }

  async connectDevice(request) {
    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
      return new Response("WebSocket upgrade required", { status: 426 });
    }
    if (!await this.authorized(request)) {
      return new Response("Invalid device credential", { status: 401 });
    }
    if (this.ctx.getWebSockets("device").length > 0) {
      return new Response("A linked HapticScape client is already connected", { status: 409 });
    }

    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];
    this.ctx.acceptWebSocket(server, ["device"]);
    return new Response(null, { status: 101, webSocket: client });
  }

  async requestPairing(request) {
    const credential = await this.ctx.storage.get("credential");
    if (!credential) {
      return jsonResponse({ error: "Link HapticScape with /hapticscape link first" }, 404);
    }
    const sockets = this.ctx.getWebSockets("device");
    if (sockets.length === 0) {
      return jsonResponse({ error: "Your linked HapticScape client is offline" }, 409);
    }
    const existing = await this.readPending();
    if (existing) {
      return jsonResponse({ error: "Another HapticScape connection request is already pending" }, 409);
    }

    const body = await readJson(request);
    if (!body
      || !REQUEST_ID_PATTERN.test(body.requestId ?? "")
      || typeof body.interactionToken !== "string"
      || body.interactionToken.length < 20
      || body.interactionToken.length > 256) {
      return jsonResponse({ error: "Invalid Discord connection request" }, 400);
    }
    const pending = {
      requestId: body.requestId,
      interactionToken: body.interactionToken,
      expiresAt: Date.now() + REQUEST_TTL_MILLIS,
    };
    await this.ctx.storage.put("pending", pending);
    await this.ctx.storage.setAlarm(pending.expiresAt);
    try {
      sockets[0].send(JSON.stringify({
        type: "PAIR_REQUEST",
        requestId: pending.requestId,
      }));
    } catch (_) {
      await this.ctx.storage.delete("pending");
      return jsonResponse({ error: "Your linked HapticScape client disconnected" }, 409);
    }
    return new Response(null, { status: 202 });
  }

  async webSocketMessage(webSocket, message) {
    if (typeof message !== "string" || message.length > 1024) {
      webSocket.close(1003, "Invalid device response");
      return;
    }
    let body;
    try {
      body = JSON.parse(message);
    } catch (_) {
      return;
    }
    const pending = await this.readPending();
    if (!pending || body.requestId !== pending.requestId) {
      return;
    }

    if (body.type === "PAIR_RESPONSE" && PAIRING_CODE_PATTERN.test(body.code ?? "")) {
      await this.ctx.storage.delete("pending");
      const delivered = await patchInteractionResponse(
        this.env,
        pending.interactionToken,
        "**HapticScape session ready**\n\n"
          + "The controller is waiting. Copy this temporary code into "
          + "**HapticScape → Remote Play → Paste & join**.\n\n"
          + "```text\n" + body.code + "\n```\n"
          + "This code expires after five minutes and can be used once.",
      );
      if (!delivered) {
        try {
          webSocket.send(JSON.stringify({
            type: "PAIR_DELIVERY_FAILED",
            requestId: pending.requestId,
          }));
        } catch (_) {
          // The client will clean up when its device channel disconnects.
        }
      }
      return;
    }
    if (body.type === "PAIR_ERROR") {
      await this.ctx.storage.delete("pending");
      await patchInteractionResponse(
        this.env,
        pending.interactionToken,
        "HapticScape could not create the connection: " + safeError(body.message),
      );
    }
  }

  async webSocketClose() {
    if (this.ctx.getWebSockets("device").length === 0) {
      await this.clearPending(true);
    }
  }

  async webSocketError(webSocket) {
    const activeSockets = this.ctx.getWebSockets("device");
    if (activeSockets.length === 0
      || (activeSockets.length === 1 && activeSockets[0] === webSocket)) {
      await this.clearPending(true);
    }
    try {
      webSocket.close(1011, "Discord device channel error");
    } catch (_) {
      // Socket may already be closed.
    }
  }

  async alarm() {
    const pending = await this.ctx.storage.get("pending");
    if (pending && pending.expiresAt <= Date.now()) {
      await this.clearPending(true);
    }
  }

  async authorized(request) {
    const credential = await this.ctx.storage.get("credential");
    const secret = bearerToken(request);
    if (!credential || !DEVICE_SECRET_PATTERN.test(secret)) {
      return false;
    }
    return constantTimeEqual(await sha256Base64Url(secret), credential.hash);
  }

  async readPending() {
    const pending = await this.ctx.storage.get("pending");
    if (!pending) {
      return null;
    }
    if (pending.expiresAt <= Date.now()) {
      await this.clearPending(true);
      return null;
    }
    return pending;
  }

  async clearPending(notify) {
    const pending = await this.ctx.storage.get("pending");
    await this.ctx.storage.delete("pending");
    if (notify && pending) {
      await patchInteractionResponse(
        this.env,
        pending.interactionToken,
        "The HapticScape connection request was cancelled or timed out.",
      );
    }
  }

  async closeDeviceSockets(code, reason) {
    for (const socket of this.ctx.getWebSockets("device")) {
      try {
        socket.close(code, reason);
      } catch (_) {
        // Socket may already be closed.
      }
    }
  }
}

export async function handleDiscordInteraction(request, env, executionCtx) {
  if (request.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }
  const bodyText = await request.text();
  const valid = await verifyDiscordSignature(
    bodyText,
    request.headers.get("X-Signature-Ed25519"),
    request.headers.get("X-Signature-Timestamp"),
    env.DISCORD_PUBLIC_KEY,
  );
  if (!valid) {
    return new Response("Invalid Discord signature", { status: 401 });
  }

  let interaction;
  try {
    interaction = JSON.parse(bodyText);
  } catch (_) {
    return new Response("Invalid JSON", { status: 400 });
  }
  if (interaction.type === 1) {
    return jsonResponse({ type: 1 });
  }
  if (interaction.type !== 2 || interaction.data?.name !== "hapticscape") {
    return interactionMessage("Unknown HapticScape command", true);
  }

  const user = interaction.member?.user ?? interaction.user;
  if (!user || !DISCORD_USER_ID_PATTERN.test(user.id ?? "")) {
    return interactionMessage("Discord did not provide a valid user identity", true);
  }
  const subcommand = interaction.data.options?.[0]?.name;
  const userObjectId = env.DISCORD_USERS.idFromName(user.id);
  const userObject = env.DISCORD_USERS.get(userObjectId);

  if (subcommand === "link") {
    const code = await createLinkTicket(env, user);
    return interactionMessage(
      "Paste this one-time code into **HapticScape → Remote Play → Discord**:\n\n"
        + "```text\n" + code + "\n```\n"
        + "It expires after five minutes.",
      true,
    );
  }
  if (subcommand === "status") {
    const status = await userObject.fetch("https://discord-user.internal/status");
    const state = await status.json();
    return interactionMessage(
      !state.linked
        ? "No HapticScape client is linked to your Discord account."
        : state.online
          ? "HapticScape is linked and online as **" + state.displayName + "**."
          : "HapticScape is linked as **" + state.displayName + "**, but the client is offline.",
      true,
    );
  }
  if (subcommand === "unlink") {
    await userObject.fetch("https://discord-user.internal/unlink-by-discord", {
      method: "DELETE",
    });
    return interactionMessage("The linked HapticScape client has been removed.", true);
  }
  if (subcommand === "connect") {
    if (interaction.context !== 2 || interaction.channel?.type !== 1) {
      return interactionMessage(
        "Run `/hapticscape connect` inside a one-to-one Discord DM with your partner.",
        true,
      );
    }
    const requestId = randomBase64Url(12);
    const work = dispatchPairingRequest(userObject, env, {
      requestId,
      interactionToken: interaction.token,
    });
    if (executionCtx?.waitUntil) {
      executionCtx.waitUntil(work);
    } else {
      await work;
    }
    return jsonResponse({ type: 5, data: {} });
  }
  return interactionMessage("Unknown HapticScape command", true);
}

export async function redeemDiscordLink(request, env, locator) {
  if (!LINK_LOCATOR_PATTERN.test(locator)) {
    return jsonResponse({ error: "Invalid Discord link code" }, 400);
  }
  const id = env.DISCORD_LINK_TICKETS.idFromName(locator);
  return env.DISCORD_LINK_TICKETS.get(id).fetch(request);
}

export async function handleDiscordDevice(request, env) {
  const url = new URL(request.url);
  const userId = url.searchParams.get("user") ?? "";
  if (!DISCORD_USER_ID_PATTERN.test(userId)) {
    return new Response("Invalid Discord user", { status: 400 });
  }
  const id = env.DISCORD_USERS.idFromName(userId);
  const target = new URL(request.url);
  target.protocol = "https:";
  target.hostname = "discord-user.internal";
  target.port = "";
  target.pathname = "/device";
  return env.DISCORD_USERS.get(id).fetch(new Request(target, request));
}

export async function verifyDiscordSignature(body, signature, timestamp, publicKey) {
  if (typeof body !== "string"
    || !/^[0-9a-fA-F]{128}$/.test(signature ?? "")
    || !/^\d{10,16}$/.test(timestamp ?? "")
    || !/^[0-9a-fA-F]{64}$/.test(publicKey ?? "")) {
    return false;
  }
  const signedAtSeconds = Number(timestamp);
  const nowSeconds = Math.floor(Date.now() / 1000);
  if (!Number.isSafeInteger(signedAtSeconds)
    || Math.abs(nowSeconds - signedAtSeconds) > MAXIMUM_SIGNATURE_AGE_SECONDS) {
    return false;
  }
  try {
    const key = await crypto.subtle.importKey(
      "raw",
      hexBytes(publicKey),
      { name: "Ed25519" },
      false,
      ["verify"],
    );
    return crypto.subtle.verify(
      { name: "Ed25519" },
      key,
      hexBytes(signature),
      new TextEncoder().encode(timestamp + body),
    );
  } catch (_) {
    return false;
  }
}

async function createLinkTicket(env, user) {
  for (let attempt = 0; attempt < 4; attempt++) {
    const locator = randomBase64Url(12);
    const id = env.DISCORD_LINK_TICKETS.idFromName(locator);
    const response = await env.DISCORD_LINK_TICKETS.get(id).fetch(new Request(
      "https://discord-link.internal/ticket",
      {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          userId: user.id,
          displayName: discordDisplayName(user),
        }),
      },
    ));
    if (response.status === 201) {
      return "HSL1." + locator;
    }
  }
  throw new Error("Unable to allocate a Discord link code");
}

async function dispatchPairingRequest(userObject, env, payload) {
  const response = await userObject.fetch(new Request(
    "https://discord-user.internal/request",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
  ));
  if (response.status !== 202) {
    let message = "Unable to contact the linked HapticScape client";
    try {
      message = (await response.json()).error ?? message;
    } catch (_) {
      // Use the generic failure.
    }
    await patchInteractionResponse(env, payload.interactionToken, message);
  }
}

async function patchInteractionResponse(env, interactionToken, content) {
  if (!env.DISCORD_APPLICATION_ID || !interactionToken) {
    return false;
  }
  const origin = env.DISCORD_API_ORIGIN ?? "https://discord.com";
  try {
    const response = await fetch(
      origin + "/api/v10/webhooks/"
        + encodeURIComponent(env.DISCORD_APPLICATION_ID) + "/"
        + encodeURIComponent(interactionToken) + "/messages/@original",
      {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          content,
          allowed_mentions: { parse: [] },
        }),
      },
    );
    return response.ok;
  } catch (_) {
    return false;
  }
}

function interactionMessage(content, ephemeral) {
  return jsonResponse({
    type: 4,
    data: {
      content,
      flags: ephemeral ? EPHEMERAL_FLAG : 0,
      allowed_mentions: { parse: [] },
    },
  });
}

function discordDisplayName(user) {
  return safeDisplayName(user.global_name || user.username || "Discord user");
}

function safeDisplayName(value) {
  const result = String(value ?? "Discord user")
    .replace(/[\u0000-\u001f\u007f]/g, "")
    .trim()
    .slice(0, 80);
  return result || "Discord user";
}

function safeError(value) {
  const result = String(value ?? "Unknown error")
    .replace(/[\u0000-\u001f\u007f]/g, " ")
    .replace(/[`*_~|>@]/g, "")
    .trim()
    .slice(0, 160);
  return result || "Unknown error";
}

function bearerToken(request) {
  const authorization = request.headers.get("Authorization") ?? "";
  return authorization.startsWith("Bearer ")
    ? authorization.slice("Bearer ".length).trim()
    : "";
}

async function readJson(request) {
  try {
    return await request.json();
  } catch (_) {
    return null;
  }
}

async function sha256Base64Url(value) {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(value),
  );
  return bytesBase64Url(new Uint8Array(digest));
}

function randomBase64Url(bytes) {
  const value = new Uint8Array(bytes);
  crypto.getRandomValues(value);
  return bytesBase64Url(value);
}

function bytesBase64Url(bytes) {
  let binary = "";
  for (const value of bytes) {
    binary += String.fromCharCode(value);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function hexBytes(value) {
  const bytes = new Uint8Array(value.length / 2);
  for (let index = 0; index < bytes.length; index++) {
    bytes[index] = Number.parseInt(value.slice(index * 2, index * 2 + 2), 16);
  }
  return bytes;
}

function constantTimeEqual(first, second) {
  if (typeof first !== "string" || typeof second !== "string" || first.length !== second.length) {
    return false;
  }
  let difference = 0;
  for (let index = 0; index < first.length; index++) {
    difference |= first.charCodeAt(index) ^ second.charCodeAt(index);
  }
  return difference === 0;
}

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}
