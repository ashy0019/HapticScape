const LINK_TTL_MILLIS = 5 * 60 * 1000;
const REQUEST_TTL_MILLIS = 2 * 60 * 1000;
const LINK_LOCATOR_PATTERN = /^[A-Za-z0-9_-]{16}$/;
const DISCORD_USER_ID_PATTERN = /^\d{15,22}$/;
const DEVICE_SECRET_PATTERN = /^[A-Za-z0-9_-]{43}$/;
const REQUEST_ID_PATTERN = /^[A-Za-z0-9_-]{16}$/;
const ACCEPT_TOKEN_PATTERN = /^[A-Za-z0-9_-]{43}$/;
const PARTICIPANT_PUBLIC_KEY_PATTERN = /^[A-Za-z0-9_-]{300,600}$/;
const ENCRYPTED_PAIRING_CODE_PATTERN = /^[A-Za-z0-9_-]{342}$/;
const EPHEMERAL_FLAG = 64;
const MAXIMUM_SIGNATURE_AGE_SECONDS = 5 * 60;

export function discordInstallRedirect(request, env) {
  if (request.method !== "GET") {
    return new Response("Method not allowed", {
      status: 405,
      headers: { Allow: "GET" },
    });
  }
  const applicationId = env.DISCORD_APPLICATION_ID ?? "";
  if (!DISCORD_USER_ID_PATTERN.test(applicationId)) {
    return new Response("Discord integration is not configured", { status: 503 });
  }
  const installUrl = "https://discord.com/oauth2/authorize?client_id="
    + encodeURIComponent(applicationId)
    + "&integration_type=1&scope=applications.commands";
  return new Response(null, {
    status: 302,
    headers: {
      Location: installUrl,
      "Cache-Control": "no-store",
    },
  });
}

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
          userId: ticket.userId,
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
    if (path === "/accept-link" && request.method === "POST") {
      return this.validateAcceptLink(request);
    }
    if (path === "/accept" && request.method === "POST") {
      return this.acceptFromDevice(request);
    }
    if (path === "/accept-from-participant" && request.method === "POST") {
      return this.acceptFromParticipant(request);
    }
    if (path === "/deliver" && request.method === "POST") {
      return this.deliverPairing(request);
    }
    if (path === "/participant-result" && request.method === "POST") {
      return this.participantResult(request);
    }
    if (path === "/deny" && request.method === "POST") {
      return this.denyPairing(request);
    }
    if (path === "/cancel-incoming" && request.method === "POST") {
      return this.cancelIncoming(request);
    }
    return new Response("Not found", { status: 404 });
  }

  async link(request) {
    const body = await readJson(request);
    if (!body
      || !DISCORD_USER_ID_PATTERN.test(body.userId ?? "")
      || !DEVICE_SECRET_PATTERN.test(body.credentialHash ?? "")
      || typeof body.displayName !== "string") {
      return new Response("Invalid device link", { status: 400 });
    }
    await this.closeDeviceSockets(4003, "Discord link replaced");
    await this.ctx.storage.put("credential", {
      userId: body.userId,
      hash: body.credentialHash,
      displayName: safeDisplayName(body.displayName),
    });
    await this.clearPending(true);
    await this.clearIncoming(true);
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
    await this.clearIncoming(true);
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
      || !DISCORD_USER_ID_PATTERN.test(body.controllerId ?? "")
      || !DISCORD_USER_ID_PATTERN.test(body.participantId ?? "")
      || typeof body.participantName !== "string"
      || !ACCEPT_TOKEN_PATTERN.test(body.acceptTokenHash ?? "")
      || typeof body.interactionToken !== "string"
      || body.interactionToken.length < 20
      || body.interactionToken.length > 256) {
      return jsonResponse({ error: "Invalid Discord connection request" }, 400);
    }
    const pending = {
      requestId: body.requestId,
      controllerId: body.controllerId,
      participantId: body.participantId,
      participantName: safeDisplayName(body.participantName),
      acceptTokenHash: body.acceptTokenHash,
      interactionToken: body.interactionToken,
      state: "pending",
      expiresAt: Date.now() + REQUEST_TTL_MILLIS,
    };
    await this.ctx.storage.put("pending", pending);
    await this.ctx.storage.setAlarm(pending.expiresAt);
    return new Response(null, { status: 202 });
  }

  async validateAcceptLink(request) {
    const body = await readJson(request);
    const pending = await this.readPending();
    if (!pending
      || pending.state !== "pending"
      || body?.requestId !== pending.requestId
      || !ACCEPT_TOKEN_PATTERN.test(body?.acceptToken ?? "")
      || !constantTimeEqual(
        await sha256Base64Url(body.acceptToken),
        pending.acceptTokenHash,
      )) {
      return new Response("This connection request expired or was already handled", { status: 404 });
    }
    return new Response(null, { status: 204 });
  }

  async acceptFromDevice(request) {
    if (!await this.authorized(request)) {
      return jsonResponse({ error: "Invalid HapticScape device credential" }, 401);
    }
    const body = await readJson(request);
    const credential = await this.ctx.storage.get("credential");
    const participantId = request.headers.get("X-HapticScape-User")
      || credential?.userId
      || "";
    if (!body
      || !credential
      || !DISCORD_USER_ID_PATTERN.test(participantId)
      || !DISCORD_USER_ID_PATTERN.test(body.controllerId ?? "")
      || !REQUEST_ID_PATTERN.test(body.requestId ?? "")
      || !ACCEPT_TOKEN_PATTERN.test(body.acceptToken ?? "")
      || !PARTICIPANT_PUBLIC_KEY_PATTERN.test(body.participantPublicKey ?? "")) {
      return jsonResponse({ error: "Invalid Discord connection request" }, 400);
    }
    if (body.controllerId === participantId) {
      return jsonResponse({ error: "You cannot accept your own connection request" }, 403);
    }
    const existing = await this.readIncoming();
    if (existing) {
      return jsonResponse({ error: "Another incoming connection is already pending" }, 409);
    }

    const incoming = {
      controllerId: body.controllerId,
      participantId,
      requestId: body.requestId,
      participantPublicKey: body.participantPublicKey,
      expiresAt: Date.now() + REQUEST_TTL_MILLIS,
    };
    await this.ctx.storage.put("incoming", incoming);
    await this.updateAlarm();
    const controller = this.env.DISCORD_USERS.get(
      this.env.DISCORD_USERS.idFromName(body.controllerId),
    );
    const response = await controller.fetch(jsonRequest(
      "https://discord-user.internal/accept-from-participant",
      {
        participantId,
        requestId: body.requestId,
        acceptToken: body.acceptToken,
        participantPublicKey: body.participantPublicKey,
      },
    ));
    if (response.status !== 202) {
      await this.ctx.storage.delete("incoming");
      await this.updateAlarm();
      let message = "This connection request expired or was already handled";
      try {
        message = (await response.json()).error ?? message;
      } catch (_) {
        // Use the safe generic message.
      }
      return jsonResponse({ error: message }, response.status);
    }
    return new Response(null, { status: 202 });
  }

  async acceptFromParticipant(request) {
    const body = await readJson(request);
    const pending = await this.readPending();
    if (!pending
      || pending.state !== "pending"
      || body?.participantId !== pending.participantId
      || body?.requestId !== pending.requestId
      || !ACCEPT_TOKEN_PATTERN.test(body?.acceptToken ?? "")
      || !PARTICIPANT_PUBLIC_KEY_PATTERN.test(body?.participantPublicKey ?? "")
      || !constantTimeEqual(
        await sha256Base64Url(body.acceptToken),
        pending.acceptTokenHash,
      )) {
      return jsonResponse({ error: "This connection request expired or was already handled" }, 404);
    }
    const sockets = this.ctx.getWebSockets("device");
    if (sockets.length === 0) {
      return jsonResponse({ error: "The controller's HapticScape client is offline" }, 409);
    }
    pending.state = "accepted";
    await this.ctx.storage.put("pending", pending);
    try {
      sockets[0].send(JSON.stringify({
        type: "PAIR_REQUEST",
        requestId: pending.requestId,
        participantPublicKey: body.participantPublicKey,
      }));
    } catch (_) {
      pending.state = "pending";
      await this.ctx.storage.put("pending", pending);
      return jsonResponse({ error: "The controller's HapticScape client disconnected" }, 409);
    }
    await patchInteractionResponse(
      this.env,
      pending.interactionToken,
      "**Connection accepted in Discord**\n\n"
        + "Opening HapticScape for **" + pending.participantName + "**. "
        + "The session will begin only if they also approve the local consent prompt.",
      [],
    );
    return new Response(null, { status: 202 });
  }

  async deliverPairing(request) {
    const body = await readJson(request);
    const incoming = await this.readIncoming();
    if (!incoming
      || body?.controllerId !== incoming.controllerId
      || body?.requestId !== incoming.requestId
      || !ENCRYPTED_PAIRING_CODE_PATTERN.test(body?.encryptedCode ?? "")
      || typeof body.controllerName !== "string"
      || typeof body.relayUrl !== "string") {
      return jsonResponse({ error: "The incoming connection request is no longer valid" }, 404);
    }
    const sockets = this.ctx.getWebSockets("device");
    if (sockets.length === 0) {
      return jsonResponse({ error: "The participant's HapticScape client is offline" }, 409);
    }
    try {
      sockets[0].send(JSON.stringify({
        type: "JOIN_REQUEST",
        requestId: incoming.requestId,
        controllerId: incoming.controllerId,
        controllerName: safeDisplayName(body.controllerName),
        relayUrl: body.relayUrl,
        encryptedCode: body.encryptedCode,
      }));
    } catch (_) {
      return jsonResponse({ error: "The participant's HapticScape client disconnected" }, 409);
    }
    return new Response(null, { status: 202 });
  }

  async participantResult(request) {
    const body = await readJson(request);
    const pending = await this.readPending();
    if (!pending
      || body?.requestId !== pending.requestId
      || body?.participantId !== pending.participantId
      || !["JOINING", "DENIED", "FAILED"].includes(body?.result)) {
      return new Response("Connection request not found", { status: 404 });
    }
    await this.ctx.storage.delete("pending");
    await this.updateAlarm();
    let content;
    if (body.result === "JOINING") {
      content = "**HapticScape connection approved**\n\n"
        + pending.participantName + " approved the local consent prompt. The encrypted session is connecting.";
    } else if (body.result === "DENIED") {
      content = "**HapticScape connection declined**\n\n"
        + pending.participantName + " declined the local consent prompt.";
      this.cancelControllerPairing(pending.requestId);
    } else {
      content = "**HapticScape connection failed**\n\n"
        + safeError(body.message);
      this.cancelControllerPairing(pending.requestId);
    }
    await patchInteractionResponse(this.env, pending.interactionToken, content, []);
    return new Response(null, { status: 204 });
  }

  async denyPairing(request) {
    const body = await readJson(request);
    const pending = await this.readPending();
    if (!pending
      || pending.state !== "pending"
      || body?.participantId !== pending.participantId
      || body?.requestId !== pending.requestId) {
      return jsonResponse({ error: "Only the invited partner can deny this request" }, 403);
    }
    await this.ctx.storage.delete("pending");
    await this.updateAlarm();
    return new Response(null, { status: 204 });
  }

  async cancelIncoming(request) {
    const body = await readJson(request);
    const incoming = await this.readIncoming();
    if (!incoming
      || body?.controllerId !== incoming.controllerId
      || body?.requestId !== incoming.requestId) {
      return new Response(null, { status: 404 });
    }
    await this.ctx.storage.delete("incoming");
    await this.updateAlarm();
    return new Response(null, { status: 204 });
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
    if (pending && body.requestId === pending.requestId) {
      if (body.type === "PAIR_RESPONSE"
        && pending.state === "accepted"
        && ENCRYPTED_PAIRING_CODE_PATTERN.test(body.encryptedCode ?? "")
        && typeof body.relayUrl === "string") {
        pending.state = "waiting-local-consent";
        await this.ctx.storage.put("pending", pending);
        const participant = this.env.DISCORD_USERS.get(
          this.env.DISCORD_USERS.idFromName(pending.participantId),
        );
        const delivered = await participant.fetch(jsonRequest(
          "https://discord-user.internal/deliver",
          {
            controllerId: pending.controllerId,
            controllerName: (await this.ctx.storage.get("credential"))?.displayName,
            requestId: pending.requestId,
            encryptedCode: body.encryptedCode,
            relayUrl: body.relayUrl,
          },
        ));
        if (delivered.status !== 202) {
          await this.ctx.storage.delete("pending");
          this.cancelControllerPairing(pending.requestId);
          await patchInteractionResponse(
            this.env,
            pending.interactionToken,
            "HapticScape could not deliver the request to the participant's client.",
            [],
          );
        }
        return;
      }
      if (body.type === "PAIR_ERROR") {
        await this.ctx.storage.delete("pending");
        await patchInteractionResponse(
          this.env,
          pending.interactionToken,
          "HapticScape could not create the connection: " + safeError(body.message),
          [],
        );
        return;
      }
    }

    const incoming = await this.readIncoming();
    if (incoming && body.requestId === incoming.requestId && body.type === "JOIN_RESULT") {
      if (!["JOINING", "DENIED", "FAILED"].includes(body.result)) {
        return;
      }
      await this.ctx.storage.delete("incoming");
      await this.updateAlarm();
      const controller = this.env.DISCORD_USERS.get(
        this.env.DISCORD_USERS.idFromName(incoming.controllerId),
      );
      await controller.fetch(jsonRequest(
        "https://discord-user.internal/participant-result",
        {
          participantId: incoming.participantId,
          requestId: incoming.requestId,
          result: body.result,
          message: safeError(body.message),
        },
      ));
    }
  }

  async webSocketClose() {
    if (this.ctx.getWebSockets("device").length === 0) {
      await this.clearPending(true);
      await this.clearIncoming(true);
    }
  }

  async webSocketError(webSocket) {
    const activeSockets = this.ctx.getWebSockets("device");
    if (activeSockets.length === 0
      || (activeSockets.length === 1 && activeSockets[0] === webSocket)) {
      await this.clearPending(true);
      await this.clearIncoming(true);
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
    const incoming = await this.ctx.storage.get("incoming");
    if (incoming && incoming.expiresAt <= Date.now()) {
      await this.clearIncoming(true);
    }
    await this.updateAlarm();
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

  async readIncoming() {
    const incoming = await this.ctx.storage.get("incoming");
    if (!incoming) {
      return null;
    }
    if (incoming.expiresAt <= Date.now()) {
      await this.ctx.storage.delete("incoming");
      return null;
    }
    return incoming;
  }

  async clearPending(notify) {
    const pending = await this.ctx.storage.get("pending");
    await this.ctx.storage.delete("pending");
    if (notify && pending) {
      this.cancelControllerPairing(pending.requestId);
      if (pending.state !== "pending" && pending.participantId) {
        const participant = this.env.DISCORD_USERS.get(
          this.env.DISCORD_USERS.idFromName(pending.participantId),
        );
        await participant.fetch(jsonRequest(
          "https://discord-user.internal/cancel-incoming",
          { controllerId: pending.controllerId, requestId: pending.requestId },
        ));
      }
      await patchInteractionResponse(
        this.env,
        pending.interactionToken,
        "The HapticScape connection request was cancelled or timed out.",
        [],
      );
    }
    await this.updateAlarm();
  }

  async clearIncoming(notify) {
    const incoming = await this.ctx.storage.get("incoming");
    await this.ctx.storage.delete("incoming");
    if (notify && incoming) {
      const controller = this.env.DISCORD_USERS.get(
        this.env.DISCORD_USERS.idFromName(incoming.controllerId),
      );
      await controller.fetch(jsonRequest(
        "https://discord-user.internal/participant-result",
        {
          participantId: incoming.participantId,
          requestId: incoming.requestId,
          result: "FAILED",
          message: "The participant's HapticScape client disconnected or timed out",
        },
      ));
    }
    await this.updateAlarm();
  }

  cancelControllerPairing(requestId) {
    for (const socket of this.ctx.getWebSockets("device")) {
      try {
        socket.send(JSON.stringify({ type: "PAIR_CANCELLED", requestId }));
      } catch (_) {
        // Socket cleanup will end the controller session.
      }
    }
  }

  async updateAlarm() {
    const pending = await this.ctx.storage.get("pending");
    const incoming = await this.ctx.storage.get("incoming");
    const expirations = [pending?.expiresAt, incoming?.expiresAt]
      .filter(value => Number.isFinite(value));
    if (expirations.length > 0) {
      await this.ctx.storage.setAlarm(Math.min(...expirations));
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
  if (interaction.type === 3) {
    return handleDiscordComponent(interaction, env);
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
    const recipients = Array.isArray(interaction.channel.recipients)
      ? interaction.channel.recipients.filter(
        recipient => DISCORD_USER_ID_PATTERN.test(recipient?.id ?? "")
          && recipient.id !== user.id,
      )
      : [];
    if (recipients.length !== 1) {
      return interactionMessage(
        "Discord did not identify exactly one partner in this DM. Close and reopen the one-to-one DM, then try again.",
        true,
      );
    }
    const participant = recipients[0];
    const participantObject = env.DISCORD_USERS.get(
      env.DISCORD_USERS.idFromName(participant.id),
    );
    const [controllerStatusResponse, participantStatusResponse] = await Promise.all([
      userObject.fetch("https://discord-user.internal/status"),
      participantObject.fetch("https://discord-user.internal/status"),
    ]);
    const controllerStatus = await controllerStatusResponse.json();
    const participantStatus = await participantStatusResponse.json();
    if (!controllerStatus.linked || !controllerStatus.online) {
      return interactionMessage(
        "Your linked HapticScape client must be running before you create a request.",
        true,
      );
    }
    if (!participantStatus.linked) {
      return interactionMessage(
        "Your partner must link HapticScape with `/hapticscape link` before you can invite them.",
        true,
      );
    }

    const requestId = randomBase64Url(12);
    const acceptToken = randomBase64Url(32);
    const dispatched = await dispatchPairingRequest(userObject, {
      requestId,
      interactionToken: interaction.token,
      participantId: participant.id,
      participantName: discordDisplayName(participant),
      acceptTokenHash: await sha256Base64Url(acceptToken),
      controllerId: user.id,
    });
    if (!dispatched.ok) {
      return interactionMessage(dispatched.error, true);
    }
    const acceptUrl = new URL(request.url).origin
      + "/discord/accept/" + encodeURIComponent(user.id)
      + "/" + encodeURIComponent(requestId)
      + "/" + encodeURIComponent(acceptToken);
    return interactionMessageWithComponents(
      "**HapticScape Remote Play request**\n\n"
        + "**" + discordDisplayName(user) + "** wants to start an encrypted Remote Play session with "
        + "**" + discordDisplayName(participant) + "**.\n\n"
        + "Accept opens HapticScape. Control is granted only after the participant also approves the local consent prompt.",
      [
        {
          type: 1,
          components: [
            { type: 2, style: 5, label: "Accept", url: acceptUrl },
            {
              type: 2,
              style: 4,
              label: "Deny",
              custom_id: "hsc_deny:" + user.id + ":" + requestId,
            },
          ],
        },
      ],
    );
  }
  return interactionMessage("Unknown HapticScape command", true);
}

async function handleDiscordComponent(interaction, env) {
  const user = interaction.member?.user ?? interaction.user;
  const match = String(interaction.data?.custom_id ?? "").match(
    /^hsc_deny:(\d{15,22}):([A-Za-z0-9_-]{16})$/,
  );
  if (!user || !DISCORD_USER_ID_PATTERN.test(user.id ?? "") || !match) {
    return interactionMessage("This HapticScape action is invalid or expired.", true);
  }
  const controller = env.DISCORD_USERS.get(env.DISCORD_USERS.idFromName(match[1]));
  const response = await controller.fetch(jsonRequest(
    "https://discord-user.internal/deny",
    { participantId: user.id, requestId: match[2] },
  ));
  if (!response.ok) {
    let message = "This request expired or belongs to the other person in the DM.";
    try {
      message = (await response.json()).error ?? message;
    } catch (_) {
      // Use the safe generic message.
    }
    return interactionMessage(message, true);
  }
  return jsonResponse({
    type: 7,
    data: {
      content: "**HapticScape connection declined**\n\nThe participant denied the Remote Play request.",
      components: [],
      allowed_mentions: { parse: [] },
    },
  });
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
  const headers = new Headers(request.headers);
  headers.set("X-HapticScape-User", userId);
  return env.DISCORD_USERS.get(id).fetch(new Request(target, {
    method: request.method,
    headers,
  }));
}

export async function handleDiscordDeviceAccept(request, env) {
  if (request.method !== "POST") {
    return new Response("Method not allowed", {
      status: 405,
      headers: { Allow: "POST" },
    });
  }
  const url = new URL(request.url);
  const userId = url.searchParams.get("user") ?? "";
  if (!DISCORD_USER_ID_PATTERN.test(userId)) {
    return jsonResponse({ error: "Invalid Discord user" }, 400);
  }
  const id = env.DISCORD_USERS.idFromName(userId);
  const target = new URL(request.url);
  target.protocol = "https:";
  target.hostname = "discord-user.internal";
  target.port = "";
  target.pathname = "/accept";
  const headers = new Headers(request.headers);
  headers.set("X-HapticScape-User", userId);
  return env.DISCORD_USERS.get(id).fetch(new Request(target, {
    method: request.method,
    headers,
    body: await request.arrayBuffer(),
  }));
}

export async function openDiscordAccept(request, env, controllerId, requestId, acceptToken) {
  if (request.method !== "GET"
    || !DISCORD_USER_ID_PATTERN.test(controllerId)
    || !REQUEST_ID_PATTERN.test(requestId)
    || !ACCEPT_TOKEN_PATTERN.test(acceptToken)) {
    return new Response("Invalid HapticScape connection request", { status: 400 });
  }
  const controller = env.DISCORD_USERS.get(env.DISCORD_USERS.idFromName(controllerId));
  const valid = await controller.fetch(jsonRequest(
    "https://discord-user.internal/accept-link",
    { requestId, acceptToken },
  ));
  if (!valid.ok) {
    return new Response("This HapticScape connection request expired or was already handled", {
      status: 410,
      headers: { "Cache-Control": "no-store" },
    });
  }
  const deepLink = "hapticscape://discord/accept?controller="
    + encodeURIComponent(controllerId)
    + "&request=" + encodeURIComponent(requestId)
    + "&token=" + encodeURIComponent(acceptToken);
  return new Response(null, {
    status: 302,
    headers: {
      Location: deepLink,
      "Cache-Control": "no-store",
      "Referrer-Policy": "no-referrer",
    },
  });
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

async function dispatchPairingRequest(userObject, payload) {
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
    return { ok: false, error: message };
  }
  return { ok: true };
}

async function patchInteractionResponse(env, interactionToken, content, components) {
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
          ...(components === undefined ? {} : { components }),
          allowed_mentions: { parse: [] },
        }),
      },
    );
    return response.ok;
  } catch (_) {
    return false;
  }
}

function interactionMessageWithComponents(content, components) {
  return jsonResponse({
    type: 4,
    data: {
      content,
      components,
      allowed_mentions: { parse: [] },
    },
  });
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

function jsonRequest(url, body) {
  return new Request(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
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
