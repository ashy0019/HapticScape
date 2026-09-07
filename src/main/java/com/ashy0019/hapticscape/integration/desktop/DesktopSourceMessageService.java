package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.host.SourceMessageService;
import java.util.Objects;
import java.util.function.Consumer;

/** Standalone source-message host which can feed informational text into the desktop window. */
public final class DesktopSourceMessageService implements SourceMessageService
{
	private volatile Consumer<Message> listener;

	public void setListener(Consumer<Message> listener)
	{
		this.listener = listener;
	}

	@Override
	public void post(String message)
	{
		publish(new Message(message, null));
	}

	@Override
	public void postColored(String message, int rgb)
	{
		publish(new Message(message, rgb & 0xFFFFFF));
	}

	private void publish(Message message)
	{
		Consumer<Message> current = listener;
		if (current != null)
		{
			current.accept(message);
		}
		else
		{
			System.out.println("HapticScape: " + message.getText());
		}
	}

	public static final class Message
	{
		private final String text;
		private final Integer rgb;

		private Message(String text, Integer rgb)
		{
			this.text = Objects.requireNonNull(text, "text");
			this.rgb = rgb;
		}

		public String getText()
		{
			return text;
		}

		public Integer getRgb()
		{
			return rgb;
		}
	}
}
