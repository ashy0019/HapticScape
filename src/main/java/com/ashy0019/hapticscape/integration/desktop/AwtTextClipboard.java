package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.host.TextClipboard;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.Objects;

/** AWT system-clipboard implementation for plain text. */
public final class AwtTextClipboard implements TextClipboard
{
	@Override
	public void copyText(String value)
	{
		Objects.requireNonNull(value, "value");
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
				new StringSelection(value),
				null
			);
		}
		catch (RuntimeException exception)
		{
			throw new IllegalStateException("Clipboard is unavailable", exception);
		}
	}

	@Override
	public String readText()
	{
		try
		{
			Object value = Toolkit.getDefaultToolkit().getSystemClipboard()
				.getData(DataFlavor.stringFlavor);
			return value instanceof String ? (String) value : null;
		}
		catch (Exception exception)
		{
			throw new IllegalStateException("Clipboard is unavailable", exception);
		}
	}
}
