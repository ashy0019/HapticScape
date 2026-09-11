package com.ashy0019.localeventbridge.runelite;

import com.ashy0019.localeventbridge.event.ChatMessageEvent;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;

/** Translates RuneLite chat messages into source-neutral local events. */
public final class RuneLiteChatAdapter
{
	private static final String SOURCE = RuneLiteEventBridge.SOURCE_ID;
	private static final Pattern JAGEX_TAG = Pattern.compile("<([^>]*)>");

	public ChatMessageEvent adapt(ChatMessage event)
	{
		Objects.requireNonNull(event, "event");
		return adapt(event.getType(), event.getMessage());
	}

	ChatMessageEvent adapt(ChatMessageType type, String rawMessage)
	{
		Objects.requireNonNull(type, "type");
		String normalizedMessage = normalizeMessage(rawMessage);
		ChatMessageEvent.Kind kind;
		switch (type)
		{
			case PRIVATECHAT:
			case MODPRIVATECHAT:
				kind = ChatMessageEvent.Kind.DIRECT_MESSAGE;
				break;
			case TRADEREQ:
				kind = rawMessage != null
					&& rawMessage.contains("wishes to trade with you.")
					? ChatMessageEvent.Kind.TRADE_REQUEST
					: ChatMessageEvent.Kind.OTHER;
				break;
			default:
				kind = ChatMessageEvent.Kind.OTHER;
				break;
		}
		return new ChatMessageEvent(SOURCE, kind, rawMessage, normalizedMessage);
	}

	/**
	 * Normalize the small Jagex text vocabulary used by chat without depending
	 * on RuneLite client utility classes. Keeping this in the RuneLite adapter
	 * makes the future bridge self-contained while the emitted event stays plain
	 * Java data.
	 */
	static String normalizeMessage(String rawMessage)
	{
		if (rawMessage == null)
		{
			return "";
		}

		Matcher matcher = JAGEX_TAG.matcher(rawMessage);
		StringBuffer output = new StringBuffer(rawMessage.length());
		while (matcher.find())
		{
			String replacement;
			switch (matcher.group(1))
			{
				case "br":
				case "n":
					replacement = "\n";
					break;
				case "lt":
					replacement = "<";
					break;
				case "gt":
					replacement = ">";
					break;
				case "at":
					replacement = "@";
					break;
				case "nbh":
					replacement = "-";
					break;
				default:
					replacement = "";
					break;
			}
			matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(output);

		return output.toString()
			.replace('\u00A0', ' ')
			.trim();
	}
}
