package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.host.ExternalLinkOpener;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;

/** AWT desktop implementation for opening external links. */
public final class AwtExternalLinkOpener implements ExternalLinkOpener
{
	@Override
	public void open(String url)
	{
		Objects.requireNonNull(url, "url");
		if (!Desktop.isDesktopSupported()
			|| !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
		{
			throw new IllegalStateException("This computer cannot open web links");
		}
		try
		{
			Desktop.getDesktop().browse(URI.create(url));
		}
		catch (IOException | IllegalArgumentException exception)
		{
			throw new IllegalStateException("Could not open web link", exception);
		}
	}
}
