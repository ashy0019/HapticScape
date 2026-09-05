# HapticScape Remote Relay

This Cloudflare Worker/Durable Object is a deliberately dumb relay for HapticScape Remote Control.

## Hosted endpoint

HapticScape uses this deployed relay by default:

`wss://hapticscape-remote-relay.hapticscape.workers.dev/relay`

Using the hosted endpoint is optional. Users can replace the relay URL in the
HapticScape Remote panel with a compatible self-hosted deployment.

- The two HapticScape clients never connect directly to one another.
- Cloudflare necessarily sees each client's source IP at the network edge.
- The relay sees the random room ID and each connection's role.
- HapticScape encrypts settings and control messages end-to-end before they reach the relay.
- The Durable Object does not persist messages or settings.
- There is no direct-connect fallback.

## Temporary connection codes

The optional `/pairing/<locator>` endpoint stores one encrypted invitation for
up to five minutes. HapticScape derives the locator, redemption proof,
cancellation proof, and AES-256 envelope key from a random connection-code
secret using separate domains.

- The connection-code secret is never sent to the relay.
- The stored invitation is encrypted before upload.
- A mailbox can be redeemed only once.
- Incorrect redemption and cancellation proofs are rejected.
- Redemption and cancellation delete the stored envelope immediately.
- Durable Object alarms remove abandoned envelopes after five minutes.
- Existing `HSR1` direct invitations remain supported by the client.

## Deploy

1. Install Wrangler and authenticate with Cloudflare.
2. Copy `wrangler.toml.example` to `wrangler.toml`.
3. Run `npx wrangler deploy`.
4. In HapticScape, use the deployed WebSocket endpoint, for example:

   `wss://hapticscape-remote-relay.<account>.workers.dev/relay`

Controller invitations include that relay URL plus a random room ID and 256-bit session key. Compact `HSP1` connection codes retrieve those invitations through an encrypted temporary mailbox. Share either form privately because possession of it allows joining that session.
