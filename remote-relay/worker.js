const MAX_MESSAGE_BYTES = 128 * 1024;

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

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
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
