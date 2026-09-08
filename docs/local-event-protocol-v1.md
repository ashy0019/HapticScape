# HapticScape Local Event Protocol v1

This document defines the game-agnostic contract between a local event source
and the standalone HapticScape application.

The protocol is intentionally one-way for source behavior: a source publishes
facts, snapshots, and reset signals to HapticScape. HapticScape may only return
handshake acknowledgements or protocol errors. The contract does not define any
message that clicks, types, moves, interacts, invokes menus, or otherwise
controls the source application.

## Endpoint

The default endpoint is TCP `127.0.0.1:41713`. Implementations must bind or
connect through loopback only.

Frames use the existing length-prefixed local framing implemented by
`LocalhostFrameIo`.

## Transport envelope

Every transport frame is a JSON object:

```json
{
  "protocol": "hapticscape-local-source",
  "version": 1,
  "kind": "hello",
  "payload": {}
}
```

Source-to-HapticScape message kinds are:

- `hello`
- `event`
- `state`
- `reset`

HapticScape-to-source message kinds are:

- `hello_ack`
- `error`

## Handshake and capabilities

The first source message must be `hello`:

```json
{
  "protocol": "hapticscape-local-source",
  "version": 1,
  "kind": "hello",
  "payload": {
    "source": "example-source",
    "eventProtocol": "hapticscape-local-events",
    "eventVersion": 1,
    "capabilities": [
      "experience",
      "chat",
      "resources",
      "inventory.occupancy",
      "status",
      "loot",
      "actor.death",
      "notification"
    ]
  }
}
```

Capabilities are an allowlist. A source must not publish an event family it did
not advertise. HapticScape echoes the accepted capability set in `hello_ack`.

The v1 capability identifiers are:

| Capability | Event family |
| --- | --- |
| `experience` | experience/level progress |
| `chat` | classified chat observations |
| `resources` | bounded gameplay resources |
| `inventory.occupancy` | filled/capacity inventory state |
| `status` | gameplay status state |
| `loot` | aggregate loot observations |
| `actor.death` | local-actor death observations |
| `notification` | source notification observations |

## Event protocol

Events use a nested, source-neutral envelope:

```json
{
  "protocol": "hapticscape-local-events",
  "version": 1,
  "source": "example-source",
  "type": "experience.changed",
  "payload": {}
}
```

The v1 event types are:

- `experience.changed`
- `chat.message`
- `resource.changed`
- `inventory.occupancy`
- `status.changed`
- `loot.received`
- `actor.death`
- `notification.emitted`

The `source` field must match the source negotiated by `hello`.

## Event versus state

`event` means a newly observed occurrence and may trigger edge-based HapticScape
behavior.

```json
{
  "kind": "event",
  "payload": {
    "event": { "...": "hapticscape-local-events envelope" }
  }
}
```

`state` is a current snapshot used to seed HapticScape state without pretending
that a new gameplay occurrence just happened.

```json
{
  "kind": "state",
  "payload": {
    "event": { "...": "hapticscape-local-events envelope" }
  }
}
```

In v1, state seeding is supported for resource, inventory-occupancy, and status
events.

## Reset

A source may invalidate its seeded state when the underlying game/session is no
longer valid:

```json
{
  "protocol": "hapticscape-local-source",
  "version": 1,
  "kind": "reset",
  "payload": {
    "source": "example-source"
  }
}
```

## Directionality invariant

The v1 protocol intentionally contains no HapticScape-to-source gameplay action
message. Adding source-control behavior requires a separate protocol and is not
an extension of this event contract.

## Delivery and reconnect semantics

Source implementations must keep network connection and framing work off the
source application's event/callback threads.

The v1 delivery model intentionally distinguishes transient events from state:

- `event` is best-effort and at-most-once. It is not queued while disconnected
  and is not replayed after reconnect.
- current values for state-capable event families are retained as the latest
  snapshot for each state key. Repeated updates may be coalesced while
  disconnected; the transient `event` edge itself is still dropped.
- after every successful reconnect, the source sends `reset` before replaying
  its retained current `state` snapshots.
- a bounded transient-event queue must be used while connected. Queue pressure
  drops transient events rather than blocking the source application.

Because transient events are never retried or replayed, v1 does not require
message sequence numbers or delivery acknowledgements. A TCP connection is the
transport session boundary; reconnect creates a fresh negotiated session and
state is re-established with `reset` plus current snapshots.
