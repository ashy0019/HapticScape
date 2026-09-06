package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.event.ChatEvent;
import net.runelite.api.ChatMessageType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RuneLiteChatEventAdapterTest
{
	private final RuneLiteChatEventAdapter adapter = new RuneLiteChatEventAdapter();

	@Test
	public void classifiesPrivateMessages()
	{
		assertEquals(
			ChatEvent.Kind.DIRECT_MESSAGE,
			adapter.adapt(ChatMessageType.PRIVATECHAT, "hello").getKind()
		);
		assertEquals(
			ChatEvent.Kind.DIRECT_MESSAGE,
			adapter.adapt(ChatMessageType.MODPRIVATECHAT, "hello").getKind()
		);
	}

	@Test
	public void classifiesOnlyActualTradeRequests()
	{
		assertEquals(
			ChatEvent.Kind.TRADE_REQUEST,
			adapter.adapt(
				ChatMessageType.TRADEREQ,
				"Someone wishes to trade with you."
			).getKind()
		);
		assertEquals(
			ChatEvent.Kind.OTHER,
			adapter.adapt(ChatMessageType.TRADEREQ, "Trade message changed").getKind()
		);
	}

	@Test
	public void preservesRawTextAndNormalizesForCorePolicy()
	{
		String raw = "  <col=ffffff>hello<at>world<nbh>x</col>\u00A0  ";
		ChatEvent event = adapter.adapt(ChatMessageType.GAMEMESSAGE, raw);

		assertEquals("runelite", event.getSource());
		assertEquals(ChatEvent.TYPE, event.getType());
		assertEquals(ChatEvent.Kind.OTHER, event.getKind());
		assertEquals(raw, event.getRawMessage());
		assertEquals("hello@world-x", event.getNormalizedMessage());
	}

	@Test
	public void normalizerHandlesJagexPrintableAndFormattingTags()
	{
		assertEquals(
			"hello <world> @me-x\nnext",
			RuneLiteChatEventAdapter.normalizeMessage(
				"<col=ff0000>hello <lt>world<gt> <at>me<nbh>x<br>next</col>"
			)
		);
	}
}
