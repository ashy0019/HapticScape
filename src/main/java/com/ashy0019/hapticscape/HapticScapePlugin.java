package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.integration.runelite.RuneLiteGameplayBridge;
import com.ashy0019.hapticscape.protocol.LocalhostGameplayEventTransport;
import com.ashy0019.hapticscape.protocol.LocalhostTransportEndpoint;
import com.ashy0019.hapticscape.protocol.TransportWireCodec;
import com.google.gson.Gson;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientUI;

/**
 * Minimal RuneLite-side HapticScape gameplay bridge.
 *
 * <p>The standalone HapticScape application owns all device, UI, settings,
 * persistence, remote-control, Discord, audio, and safety behavior. This
 * plugin only observes RuneLite gameplay facts and publishes neutral events to
 * the standalone runtime over the loopback transport.</p>
 */
@Slf4j
@PluginDescriptor(
    name = "HapticScape Bridge",
    description = "Publishes neutral Old School RuneScape events to the local HapticScape app",
    tags = {"haptics", "feedback", "xp", "accessibility", "bridge"}
)
public class HapticScapePlugin extends Plugin
{
    private RuneLiteGameplayBridge gameplayBridge;
    private LocalhostGameplayEventTransport gameplayTransport;

    @Inject
    private Client client;

    @Inject
    private ClientUI clientUI;

    @Inject
    private ItemManager itemManager;

    @Inject
    private Gson gson;

    @Override
    protected void startUp()
    {
        startGameplayBridge(LocalhostTransportEndpoint.DEFAULT_PORT);
        log.info("HapticScape RuneLite bridge connected to standalone runtime");
    }

    private void startGameplayBridge(int port)
    {
        TransportWireCodec codec = new TransportWireCodec(gson);
        LocalhostGameplayEventTransport transport = new LocalhostGameplayEventTransport(
            "runelite",
            codec,
            RuneLiteGameplayBridge.CAPABILITIES,
            port
        );
        RuneLiteGameplayBridge bridge = new RuneLiteGameplayBridge(client, itemManager, transport);
        try
        {
            bridge.start();
        }
        catch (RuntimeException failure)
        {
            transport.close();
            throw failure;
        }
        gameplayTransport = transport;
        gameplayBridge = bridge;
    }

    @Override
    protected void shutDown()
    {
        gameplayBridge = null;
        if (gameplayTransport != null)
        {
            gameplayTransport.close();
            gameplayTransport = null;
        }
        log.info("HapticScape RuneLite bridge stopped");
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        RuneLiteGameplayBridge bridge = gameplayBridge;
        if (bridge != null)
        {
            bridge.onGameStateChanged(event.getGameState());
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        RuneLiteGameplayBridge bridge = gameplayBridge;
        if (bridge != null)
        {
            bridge.onStatChanged(event);
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        RuneLiteGameplayBridge bridge = gameplayBridge;
        if (bridge != null)
        {
            bridge.onChatMessage(event);
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        RuneLiteGameplayBridge bridge = gameplayBridge;
        if (bridge != null)
        {
            bridge.onItemContainerChanged(event);
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        RuneLiteGameplayBridge bridge = gameplayBridge;
        if (bridge != null)
        {
            bridge.onVarbitChanged(event);
        }
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event)
    {
        RuneLiteGameplayBridge bridge = gameplayBridge;
        if (bridge != null)
        {
            bridge.onLootReceived(event.getItems());
        }
    }

    @Subscribe
    public void onPlayerLootReceived(PlayerLootReceived event)
    {
        RuneLiteGameplayBridge bridge = gameplayBridge;
        if (bridge != null)
        {
            bridge.onLootReceived(event.getItems());
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event)
    {
        RuneLiteGameplayBridge bridge = gameplayBridge;
        if (bridge != null)
        {
            bridge.onActorDeath(event);
        }
    }

    @Subscribe
    public void onNotificationFired(NotificationFired event)
    {
        RuneLiteGameplayBridge bridge = gameplayBridge;
        if (bridge != null)
        {
            bridge.onNotificationFired(event, clientUI.isFocused());
        }
    }
}
