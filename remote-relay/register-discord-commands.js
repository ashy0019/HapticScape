const token = process.env.DISCORD_TOKEN;
const applicationId = process.env.DISCORD_APPLICATION_ID;

if (!token || !applicationId) {
  throw new Error(
    "Set DISCORD_TOKEN and DISCORD_APPLICATION_ID before registering commands",
  );
}

const command = {
  name: "hapticscape",
  description: "Connect HapticScape Remote Play through Discord",
  type: 1,
  integration_types: [1],
  contexts: [0, 1, 2],
  options: [
    {
      type: 1,
      name: "link",
      description: "Link your Discord account to this HapticScape client",
    },
    {
      type: 1,
      name: "connect",
      description: "Create a Remote Play connection in this private DM",
    },
    {
      type: 1,
      name: "status",
      description: "Check whether your HapticScape client is linked and online",
    },
    {
      type: 1,
      name: "unlink",
      description: "Remove the HapticScape client linked to your Discord account",
    },
  ],
};

const response = await fetch(
  `https://discord.com/api/v10/applications/${applicationId}/commands`,
  {
    method: "PUT",
    headers: {
      Authorization: `Bot ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify([command]),
  },
);

if (!response.ok) {
  throw new Error(
    `Discord command registration failed (${response.status}): ${await response.text()}`,
  );
}

console.log("Registered /hapticscape commands globally");
