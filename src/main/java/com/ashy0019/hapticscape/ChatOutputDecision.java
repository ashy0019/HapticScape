package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.clicker.ClickerPhraseRules;
import com.ashy0019.hapticscape.event.ChatEvent;
import java.util.Objects;

/** Source-neutral policy decision for a chat observation. */
final class ChatOutputDecision
{
	private final AlertCategory specificAlert;
	private final boolean click;

	private ChatOutputDecision(AlertCategory specificAlert, boolean click)
	{
		this.specificAlert = specificAlert;
		this.click = click;
	}

	static ChatOutputDecision classify(
		ChatEvent event,
		ClickerPhraseRules phraseRules)
	{
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(phraseRules, "phraseRules");

		String message = event.getNormalizedMessage();
		boolean click = !message.isEmpty() && phraseRules.matches(message);

		AlertCategory specificAlert;
		switch (event.getKind())
		{
			case DIRECT_MESSAGE:
				specificAlert = AlertCategory.DIRECT_MESSAGE;
				break;
			case TRADE_REQUEST:
				specificAlert = AlertCategory.TRADE_REQUEST;
				break;
			case OTHER:
			default:
				specificAlert = null;
				break;
		}

		return new ChatOutputDecision(specificAlert, click);
	}

	boolean hasSpecificAlert()
	{
		return specificAlert != null;
	}

	AlertCategory getSpecificAlert()
	{
		if (specificAlert == null)
		{
			throw new IllegalStateException("No specific alert for this chat event");
		}
		return specificAlert;
	}

	boolean shouldClick()
	{
		return click;
	}
}
