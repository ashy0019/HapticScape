# Local Event Protocol v1

Local Event Bridge publishes a fixed allowlist of RuneLite observations to a
local companion application over TCP loopback.

The protocol is intentionally one-way for gameplay behavior. A source publishes
facts, snapshots, and reset signals. The consumer may only return a handshake
acknowledgement or protocol error. There is no message for clicking, typing,
moving, interacting, invoking menus, or otherwise controlling RuneLite.

## Endpoint

The default endpoint is TCP `127.0.0.1:41713`.

Frames are newline-delimited UTF-8 JSON and are bounded to 524,288 bytes.

## Stable wire identifiers

Version 1 uses generic wire protocol identifiers:

- transport: `local-event-bridge`
- events: `local-event-bridge-events`

These strings identify the wire contract rather than any particular consumer.
Any local application can implement the documented protocol.

## Transport envelope

Every transport frame is a JSON object:

```json
{
  "protocol": "local-event-bridge",
  "version": 1,
  "kind": "hello",
  "payload": {}
}
```

Source-to-consumer message kinds are:

- `hello`
- `event`
- `state`
- `reset`

Consumer-to-source message kinds are:

- `hello_ack`
- `error`

## Handshake

The first source message is `hello`:

```json
{
  "protocol": "local-event-bridge",
  "version": 1,
  "kind": "hello",
  "payload": {
    "source": "runelite",
    "eventProtocol": "local-event-bridge-events",
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

Capabilities are an allowlist. The consumer echoes the accepted capability set
in `hello_ack`.

## Event envelope

Events use a nested source-neutral envelope:

```json
{
  "protocol": "local-event-bridge-events",
  "version": 1,
  "source": "runelite",
  "type": "experience.changed",
  "payload": {}
}
```

Version 1 event types are:

- `experience.changed`
- `chat.message`
- `resource.changed`
- `inventory.occupancy`
- `status.changed`
- `loot.received`
- `actor.death`
- `notification.emitted`

## Event versus state

`event` is a newly observed occurrence. Transient events are best-effort and
at-most-once.

`state` is a current snapshot. Resource, inventory-occupancy, and status state
is retained and coalesced while disconnected.

During an established session, a live resource, inventory, or status change is
sent as `event` so consumers can evaluate transitions, while its newest value is
also retained for recovery. Explicit seeds and reconnect replay use `state`.

After reconnect, the bridge sends `reset` and then the latest retained state
before new transient events resume. Replayed state does not represent a newly
observed transition.

## Privacy surface

The v1 bridge does not publish player names, account identifiers, positions,
bank contents, equipment, raw inventory item IDs, NPC targets, menu entries, or
world-state snapshots.

Chat events include the source message text because phrase matching and direct
message classification require it. No data is sent to an Internet endpoint by
this plugin; the transport is hard-bound to IPv4 loopback.
