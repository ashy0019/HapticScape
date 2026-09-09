package com.ashy0019.hapticscape.desktop;

import com.ashy0019.hapticscape.ui.HapticScapeTheme;
import java.awt.GraphicsEnvironment;
import javax.swing.JOptionPane;

/** Standalone desktop entry point for HapticScape. */
public final class HapticScapeDesktopMain
{
	private HapticScapeDesktopMain()
	{
	}

	public static void main(String[] args)
	{
		if (GraphicsEnvironment.isHeadless())
		{
			throw new IllegalStateException("HapticScape desktop mode requires a graphical desktop");
		}
		HapticScapeTheme.install();

		HapticScapeDesktopApplication application = new HapticScapeDesktopApplication();
		Runtime.getRuntime().addShutdownHook(new Thread(application::close, "hapticscape-desktop-shutdown"));
		try
		{
			application.start();
		}
		catch (RuntimeException failure)
		{
			showStartupError(failure);
			application.close();
			System.exit(1);
		}
	}

	private static void showStartupError(RuntimeException failure)
	{
		String message = failure.getMessage();
		if (message == null || message.trim().isEmpty())
		{
			message = failure.getClass().getSimpleName();
		}
		String finalMessage = "HapticScape could not start.\n\n" + message
			+ "\n\nAnother HapticScape instance may already be running, or port 41713 may be unavailable.";
		JOptionPane.showMessageDialog(
			null,
			finalMessage,
			"HapticScape startup failed",
			JOptionPane.ERROR_MESSAGE
		);
	}
}
