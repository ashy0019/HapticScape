package com.ashy0019.localeventbridge;

import com.ashy0019.localeventbridge.runelite.RuneLiteEventBridge;
import com.ashy0019.localeventbridge.protocol.LocalEventPublisher;
import com.ashy0019.localeventbridge.protocol.TransportWireCodec;
import com.google.gson.Gson;
import javax.inject.Inject;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal RuneLite-side local event bridge.
 *
 * <p>This plugin observes a fixed allowlist of RuneLite gameplay facts and
 * publishes source-neutral events to a local companion application over a
 * loopback-only transport. It does not expose a control path back into RuneLite.</p>
 */
@PluginDescriptor(
    name = "Local Event Bridge",
    description = "Publishes a fixed allowlist of gameplay observations to local companion apps over IPv4 loopback only",
    tags = {"integration", "local", "events"}
)
public class LocalEventBridgePlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(LocalEventBridgePlugin.class);

    private RuneLiteEventBridge gameplayBridge;
    private LocalEventPublisher gameplayTransport;

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
        startGameplayBridge();
        log.info("Local Event Bridge started; loopback transport will connect when available");
    }

    private void startGameplayBridge()
    {
        TransportWireCodec codec = new TransportWireCodec(gson);
        LocalEventPublisher transport = new LocalEventPublisher(
            RuneLiteEventBridge.SOURCE_ID,
            codec,
            RuneLiteEventBridge.CAPABILITIES
        );
        RuneLiteEventBridge bridge = new RuneLiteEventBridge(client, itemManager, transport);
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
        log.info("Local Event Bridge stopped");
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        RuneLiteEventBridge bridge = gameplayBridge;
        if (bridge != null)
        {
            bridge.onGameStateChanged(event.getGameState());
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        RuneLiteEventBridge bridge = gameplayBridge;
        if (bridge != null)
        {
            bridge.onStatChanged(event);
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        RuneLiteEventBridge bridge = gameplayBridge;
        if (bridge != null)
        {
            bridge.onChatMessage(event);
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        RuneLiteEventBridge bridge = gameplayBridge;
        if (bridge != null)
        {
            bridge.onItemContainerChanged(event);
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        RuneLiteEventBridge bridge = gameplayBridge;
        if (bridge != null)
        {
            bridge.onVarbitChanged(event);
        }
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event)
    {
        RuneLiteEventBridge bridge = gameplayBridge;
        if (bridge != null)
        {
            bridge.onLootReceived(event.getItems());
        }
    }

    @Subscribe
    public void onPlayerLootReceived(PlayerLootReceived event)
    {
        RuneLiteEventBridge bridge = gameplayBridge;
        if (bridge != null)
        {
            bridge.onLootReceived(event.getItems());
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event)
    {
        RuneLiteEventBridge bridge = gameplayBridge;
        if (bridge != null)
        {
            bridge.onActorDeath(event);
        }
    }

    @Subscribe
    public void onNotificationFired(NotificationFired event)
    {
        RuneLiteEventBridge bridge = gameplayBridge;
        if (bridge != null)
        {
            bridge.onNotificationFired(event, clientUI.isFocused());
        }
    }
}
