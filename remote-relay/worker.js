const MAX_MESSAGE_BYTES = 128 * 1024;
const MAX_PAIRING_ENVELOPE_BYTES = 4 * 1024;
const PAIRING_TTL_MILLIS = 5 * 60 * 1000;
const PAIRING_LOCATOR_PATTERN = /^[A-Za-z0-9_-]{12}$/;
const PAIRING_PROOF_PATTERN = /^[A-Za-z0-9_-]{43}$/;

export class SessionRoom {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
  }

  async fetch(request) {
    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
      return new Response("WebSocket upgrade required", { status: 426 });
    }

    const url = new URL(request.url);
    const role = url.searchParams.get("role");
    if (role !== "controller" && role !== "participant") {
      return new Response("role must be controller or participant", { status: 400 });
    }

    if (this.ctx.getWebSockets(role).length > 0) {
      return new Response(`${role} is already connected`, { status: 409 });
    }

    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];
    this.ctx.acceptWebSocket(server, [role]);

    return new Response(null, {
      status: 101,
      webSocket: client,
    });
  }

  webSocketMessage(webSocket, message) {
    if (typeof message !== "string") {
      webSocket.close(1003, "Text messages only");
      return;
    }

    if (new TextEncoder().encode(message).length > MAX_MESSAGE_BYTES) {
      webSocket.close(1009, "Message too large");
      return;
    }

    for (const peer of this.ctx.getWebSockets()) {
      if (peer !== webSocket) {
        peer.send(message);
      }
    }
  }

  webSocketClose(webSocket, code, reason, wasClean) {
    // The runtime has already observed the close. No server-side action is needed.
  }

  webSocketError(webSocket) {
    try {
      webSocket.close(1011, "Relay socket error");
    } catch (_) {
      // Socket may already be gone.
    }
  }
}

export class PairingMailbox {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
  }

  async fetch(request) {
    switch (request.method) {
      case "PUT":
        return this.create(request);
      case "GET":
        return this.redeem(request);
      case "DELETE":
        return this.cancel(request);
      default:
        return new Response("Method not allowed", {
          status: 405,
          headers: { Allow: "PUT, GET, DELETE" },
        });
    }
  }

  async create(request) {
    const redeemProof = request.headers.get("X-HapticScape-Redeem-Proof");
    const cancelProof = request.headers.get("X-HapticScape-Cancel-Proof");
    if (!PAIRING_PROOF_PATTERN.test(redeemProof ?? "")
      || !PAIRING_PROOF_PATTERN.test(cancelProof ?? "")) {
      return new Response("Invalid pairing proof", { status: 400 });
    }

    const envelope = await request.text();
    if (envelope.length === 0
      || new TextEncoder().encode(envelope).length > MAX_PAIRING_ENVELOPE_BYTES) {
      return new Response("Invalid pairing envelope", { status: 400 });
    }

    const now = Date.now();
    const existing = await this.ctx.storage.get("entry");
    if (existing && existing.expiresAt > now) {
      return new Response("Pairing mailbox already exists", { status: 409 });
    }

    const entry = {
      envelope,
      redeemProof,
      cancelProof,
      expiresAt: now + PAIRING_TTL_MILLIS,
    };
    await this.ctx.storage.put("entry", entry);
    await this.ctx.storage.setAlarm(entry.expiresAt);
    return new Response(null, {
      status: 201,
      headers: { "Cache-Control": "no-store" },
    });
  }

  async redeem(request) {
    const entry = await this.readCurrentEntry();
    if (!entry) {
      return new Response("Pairing code not found or expired", { status: 404 });
    }
    if (!constantTimeEqual(
      request.headers.get("X-HapticScape-Redeem-Proof") ?? "",
      entry.redeemProof,
    )) {
      return new Response("Pairing code was rejected", { status: 403 });
    }

    await this.ctx.storage.delete("entry");
    return new Response(entry.envelope, {
      status: 200,
      headers: {
        "Content-Type": "text/plain; charset=utf-8",
        "Cache-Control": "no-store",
      },
    });
  }

  async cancel(request) {
    const entry = await this.readCurrentEntry();
    if (!entry) {
      return new Response(null, { status: 404 });
    }
    if (!constantTimeEqual(
      request.headers.get("X-HapticScape-Cancel-Proof") ?? "",
      entry.cancelProof,
    )) {
      return new Response("Pairing cancellation was rejected", { status: 403 });
    }

    await this.ctx.storage.delete("entry");
    return new Response(null, { status: 204 });
  }

  async readCurrentEntry() {
    const entry = await this.ctx.storage.get("entry");
    if (!entry) {
      return null;
    }
    if (entry.expiresAt <= Date.now()) {
      await this.ctx.storage.delete("entry");
      return null;
    }
    return entry;
  }

  async alarm() {
    await this.ctx.storage.delete("entry");
  }
}

function constantTimeEqual(first, second) {
  if (first.length !== second.length) {
    return false;
  }
  let difference = 0;
  for (let index = 0; index < first.length; index++) {
    difference |= first.charCodeAt(index) ^ second.charCodeAt(index);
  }
  return difference === 0;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname.startsWith("/pairing/")) {
      const pairingMatch = url.pathname.match(/^\/pairing\/([A-Za-z0-9_-]+)$/);
      if (!pairingMatch) {
        return new Response("Invalid pairing code", { status: 400 });
      }
      const locator = pairingMatch[1];
      if (!PAIRING_LOCATOR_PATTERN.test(locator)) {
        return new Response("Invalid pairing code", { status: 400 });
      }
      const id = env.PAIRING_MAILBOXES.idFromName(locator);
      return env.PAIRING_MAILBOXES.get(id).fetch(request);
    }
    if (url.pathname !== "/relay") {
      return new Response("HapticScape remote relay", { status: 200 });
    }

    const room = url.searchParams.get("room");
    if (!room || !/^[A-Za-z0-9_-]{8,64}$/.test(room)) {
      return new Response("Invalid room", { status: 400 });
    }

    const id = env.SESSION_ROOMS.idFromName(room);
    return env.SESSION_ROOMS.get(id).fetch(request);
  },
};
