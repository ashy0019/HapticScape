package com.ashy0019.hapticscape.host;

/** Plain-text clipboard access supplied by the current host platform. */
public interface TextClipboard
{
	void copyText(String value);

	String readText();
}
