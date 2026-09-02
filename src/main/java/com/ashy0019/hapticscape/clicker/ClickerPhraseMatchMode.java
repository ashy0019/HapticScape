package com.ashy0019.hapticscape.clicker;

public enum ClickerPhraseMatchMode
{
	CONTAINS("Contains"),
	EXACT("Exact"),
	REGEX("Regex");

	private final String displayName;

	ClickerPhraseMatchMode(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}