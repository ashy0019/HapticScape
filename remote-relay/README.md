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

## Optional Discord companion

The same Worker can host a user-installed Discord application that asks a
linked HapticScape client to create an ordinary one-use `HSP1` connection code.
Discord is only the request and delivery surface. It does not replace the
HapticScape session protocol, consent screen, encryption, or relay.

The available commands are:

| Command | Result |
| --- | --- |
| `/hapticscape link` | Creates a private, five-minute `HSL1` code used to link the caller's Discord account to one HapticScape installation. |
| `/hapticscape connect` | In a one-to-one Discord DM, asks the linked client to create a one-use `HSP1` code and posts it into that DM. |
| `/hapticscape status` | Privately reports whether the linked client is online. |
| `/hapticscape unlink` | Removes the server-side device link and disconnects the linked client. |

The controller links once. A participant does not need to link Discord. They
copy the `HSP1` code from the DM, select **Paste & join** in HapticScape, and
accept the normal Remote Play confirmation.

The **Install Discord app** button in HapticScape opens `/discord/install` on
the configured relay. That endpoint redirects to the User Install page for the
Discord application identified by the relay's `DISCORD_APPLICATION_ID` value.

The Discord application uses HTTP interactions and a hibernatable Durable
Object WebSocket. It does not run a Discord Gateway process and does not request
privileged intents. `/hapticscape connect` is rejected in servers and group
DMs so a connection code is not posted into a larger channel by mistake.

### Discord data and credentials

- Discord interaction requests are verified with the application's Ed25519
  public key before any command is handled.
- A link ticket contains the Discord user ID and display name for up to five
  minutes and is deleted after one successful redemption.
- A linked-user Durable Object retains the Discord user ID, display name, and a
  SHA-256 hash of the random device credential until unlinked.
- The raw device credential is stored only on the linked Windows installation,
  protected for the current Windows account with DPAPI.
- A pending `/connect` request temporarily retains the Discord interaction
  token required to edit its response. It expires after two minutes.
- Discord and Cloudflare necessarily receive their ordinary network and
  account metadata. The `HSP1` bearer code is visible in the one-to-one DM and
  briefly passes through the Discord delivery path.
- Remote session payloads continue to use HapticScape's end-to-end encrypted
  WebSocket protocol. They are not sent through Discord.

## Deploy the relay

1. Install Wrangler and authenticate with Cloudflare.
2. Copy `wrangler.toml.example` to `wrangler.toml`.
3. Run `npx wrangler deploy`.
4. In HapticScape, use the deployed WebSocket endpoint, for example:

   `wss://hapticscape-remote-relay.<account>.workers.dev/relay`

Controller invitations include that relay URL plus a random room ID and 256-bit session key. Compact `HSP1` connection codes retrieve those invitations through an encrypted temporary mailbox. Share either form privately because possession of it allows joining that session.

## Configure the Discord application

These steps are for the relay operator. End users never need the bot token,
application public key, Wrangler, or a Cloudflare account.

### 1. Create and configure the application

1. Create an application in the
   [Discord Developer Portal](https://discord.com/developers/applications).
2. On **Installation**, enable **User Install**.
3. Under the default User Install settings, add the
   `applications.commands` scope.
4. Copy the **Application ID** and **Public Key** from **General Information**.
5. On **Bot**, reset and copy the bot token. This token is used only by the
   local registration command. It is not deployed to Cloudflare.

### 2. Add the Worker secrets and deploy

From `remote-relay`:

```powershell
npm install
npx wrangler secret put DISCORD_PUBLIC_KEY
npx wrangler secret put DISCORD_APPLICATION_ID
npx wrangler deploy
```

Paste the matching value at each prompt. Do not put these values in
`wrangler.toml`. The public key and application ID are not authentication
secrets, but storing them as Worker secrets keeps deployment-specific values
out of the repository.

### 3. Set the interaction endpoint

In **General Information**, set **Interactions Endpoint URL** to:

```text
https://YOUR-WORKER.workers.dev/discord/interactions
```

Discord sends a signed validation request when this field is saved. The Worker
must already be deployed with the correct public key.

### 4. Register the commands

Set temporary environment variables in the current PowerShell window, then run
the registration script:

```powershell
$env:DISCORD_APPLICATION_ID = "YOUR_APPLICATION_ID"
$env:DISCORD_TOKEN = "YOUR_BOT_TOKEN"
npm run register-discord
Remove-Item Env:DISCORD_TOKEN
```

Never commit the bot token, put it in `wrangler.toml`, send it to an end user,
or add it as a Worker secret. Reset it immediately in the Developer Portal if
it is exposed.

### 5. Install and test the application

1. Copy the Discord-provided installation link from **Installation**.
2. Open the link and install the application for your user account.
3. Start HapticScape and open **Remote Play**.
4. Run `/hapticscape link` in Discord.
5. Paste the private `HSL1` code into the Discord section in HapticScape and
   select **Link Discord**.
6. Run `/hapticscape status` and confirm the client is online.
7. Open a one-to-one DM with a test partner and run
   `/hapticscape connect`.
8. On the participant client, copy the returned `HSP1` code, select
   **Paste & join**, and accept the session.

End users can instead select **Install Discord app** inside HapticScape. The
manual installation link remains useful for testing the Discord configuration
before a new client build is distributed.

Global application-command updates can take time to appear in Discord. The
registration script replaces this application's global commands with the four
subcommands listed above.

Discord's current setup references are its documentation for
[user-installed applications](https://docs.discord.com/developers/tutorials/developing-a-user-installable-app),
[interaction endpoints](https://docs.discord.com/developers/interactions/overview#configuring-an-interactions-endpoint-url),
and [application command contexts](https://docs.discord.com/developers/interactions/application-commands#contexts).
