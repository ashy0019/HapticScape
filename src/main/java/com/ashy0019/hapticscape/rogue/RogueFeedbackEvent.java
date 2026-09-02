package com.ashy0019.hapticscape.rogue;

/**
 * Semantic events emitted by Rogue Mode. Device-specific output is mapped
 * elsewhere so the blackjack engine and UI never need to know about Intiface.
 */
public enum RogueFeedbackEvent
{
	UNLOCK(true),
	DEAL(true),
	HIT(true),
	STAND(false),
	DOUBLE(true),
	WIN(true),
	LOSS(false),
	BUST(true),
	PUSH(false),
	BLACKJACK(true);

	private final boolean click;

	RogueFeedbackEvent(boolean click)
	{
		this.click = click;
	}

	public boolean shouldClick()
	{
		return click;
	}
}
