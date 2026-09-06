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

The same Worker can host a user-installed Discord application that presents an
Accept or Deny Remote Play request in a one-to-one DM. Discord is only the
request surface. It does not replace the HapticScape session protocol, local
consent screen, encryption, or relay.

The available commands are:

| Command | Result |
| --- | --- |
| `/hapticscape link` | Creates a private, five-minute `HSL1` code used to link the caller's Discord account to one HapticScape installation. |
| `/hapticscape connect` | In a one-to-one Discord DM, sends the linked participant an Accept or Deny Remote Play request. |
| `/hapticscape status` | Privately reports whether the linked client is online. |
| `/hapticscape unlink` | Removes the server-side device link and disconnects the linked client. |

Both people link one HapticScape installation to their own Discord account.
When the intended participant selects **Accept**, the Worker redirects the
browser to a strict `hapticscape://` wake request. The packaged Windows launcher
opens or focuses HapticScape, and the participant must separately approve the
local Remote Play consent dialog. **Deny** closes the request without contacting
either session endpoint.

The wake request contains a short-lived opaque token, not an `HSP1` code or
session key. The participant client authenticates to its linked Durable Object
and generates an ephemeral RSA-2048 key pair before the controller is asked to
create an invitation. The controller encrypts the resulting `HSP1` code with
RSA-OAEP-SHA-256. The Worker relays only the participant public key and encrypted
code. The private key stays in participant memory, and the plaintext code is
never displayed in Discord or sent to the Worker.

The **Install Discord app** button in HapticScape opens `/discord/install` on
the configured relay. That endpoint redirects to the User Install page for the
Discord application identified by the relay's `DISCORD_APPLICATION_ID` value.

The Discord application uses HTTP interactions and a hibernatable Durable
Object WebSocket. It does not run a Discord Gateway process and does not request
privileged intents. `/hapticscape connect` is rejected in servers and group
DMs. Manual `HSP1` connection codes remain available in HapticScape as a
fallback.

### Discord data and credentials

- Discord interaction requests are verified with the application's Ed25519
  public key before any command is handled.
- A link ticket contains the Discord user ID and display name for up to five
  minutes and is deleted after one successful redemption.
- A linked-user Durable Object retains the Discord user ID, display name, and a
  SHA-256 hash of the random device credential until unlinked.
- The raw device credential is stored only on the linked Windows installation,
  protected for the current Windows account with DPAPI.
- A pending `/connect` request temporarily retains the controller and
  participant Discord IDs, their display names, the interaction token required
  to edit the response, request state, expiry, and a hash of the Accept token.
  It expires after two minutes.
- Discord and Cloudflare necessarily receive their ordinary network and
  account metadata. The opaque Accept token passes through Discord and the
  participant's browser, but it cannot authorize a join without the linked
  participant device credential.
- The `HSP1` bearer code is created only after linked-device authentication and
  encrypted to the participant's ephemeral RSA-2048 public key. The Worker sees
  the public key and RSA-OAEP-SHA-256 ciphertext, but not the participant's
  private key or plaintext code. The code is not posted to Discord or included
  in the browser deep link.
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

1. Install the Discord application for two test accounts.
2. Start the packaged Windows HapticScape client for each account and open
   **Remote Play**. Launching `HapticScape.exe` registers the per-user
   `hapticscape://` protocol handler.
3. On each account, run `/hapticscape link` in Discord.
4. Paste each private `HSL1` code into that account's HapticScape Discord
   section and select **Link Discord**.
5. Run `/hapticscape status` for both accounts and confirm both clients are
   online.
6. Open a one-to-one DM between the accounts and run
   `/hapticscape connect` from the controller account.
7. Select **Deny** once and confirm the request closes without starting a
   session.
8. Create another request and select **Accept** as the participant. Confirm the
   packaged client starts or focuses, opens Remote Play, and shows the local
   consent dialog.
9. Decline once and confirm no session begins. Repeat, approve locally, and
   confirm the encrypted session connects.

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
