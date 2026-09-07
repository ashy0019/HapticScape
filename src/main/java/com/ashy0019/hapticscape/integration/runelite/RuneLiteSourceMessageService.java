package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.host.SourceMessageService;
import java.awt.Color;
import java.util.Objects;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.util.ColorUtil;

/** RuneLite-hosted source-message implementation backed by the local console chatbox. */
public final class RuneLiteSourceMessageService implements SourceMessageService
{
	private final ChatMessageManager chatMessageManager;

	public RuneLiteSourceMessageService(ChatMessageManager chatMessageManager)
	{
		this.chatMessageManager = Objects.requireNonNull(
			chatMessageManager,
			"chatMessageManager"
		);
	}

	@Override
	public void post(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.value(message)
			.build());
	}

	@Override
	public void postColored(String message, int rgb)
	{
		String coloredMessage = ColorUtil.wrapWithColorTag(
			message,
			new Color(rgb)
		);
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(coloredMessage)
			.build());
	}
}
