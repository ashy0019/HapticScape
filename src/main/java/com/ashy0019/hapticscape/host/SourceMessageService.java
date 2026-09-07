package com.ashy0019.hapticscape.host;

/** Posts informational messages back to the active source application's local UI. */
public interface SourceMessageService
{
	void post(String message);

	void postColored(String message, int rgb);
}
