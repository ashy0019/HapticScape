# HapticScape localhost gameplay transport v1

The gameplay bridge uses a loopback-only TCP connection to carry the JSON transport protocol defined by `hapticscape-transport` v1.

## Endpoint

- Host: `127.0.0.1`
- Default port: `41713`
- The receiver binds only to IPv4 loopback. It does not listen on LAN or wildcard interfaces.
- Tests may request port `0` to let the operating system allocate an ephemeral loopback port.

## Framing

Each transport message is compact UTF-8 JSON followed by a single LF byte (`\n`). JSON string newlines remain JSON-escaped and do not split frames.

A frame is rejected if it exceeds 524,288 UTF-8 bytes. The existing transport and nested event codecs independently enforce their character limits as well.

## Connection lifecycle

1. The source opens a TCP connection to the loopback endpoint.
2. The source sends `hello` as the first frame.
3. The receiver validates the transport/event protocol versions and replies with `hello_ack` or `error`.
4. After acknowledgement, the source may send `event` and `reset` frames in order.
5. Every new TCP connection creates a fresh transport session and must negotiate `hello` again.

The client retries a detected broken socket once by reconnecting, renegotiating `hello`, and resending the frame. TCP preserves ordering within a connection; the transport does not claim exactly-once delivery across a connection failure.

## Trust boundary

Loopback binding prevents remote-network peers from connecting directly. It does not authenticate other processes running as the same local user. Local peer authentication can be layered onto the handshake independently of gameplay event semantics if required.
