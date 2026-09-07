package com.ashy0019.hapticscape.desktop;

import java.awt.GraphicsEnvironment;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

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
		installSystemLookAndFeel();

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

	private static void installSystemLookAndFeel()
	{
		try
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception ignored)
		{
			// Swing's cross-platform look and feel is an acceptable fallback.
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
			+ "\n\nIf RuneLite-hosted HapticScape is currently running, close or disable it so port 41713 is free.";
		JOptionPane.showMessageDialog(
			null,
			finalMessage,
			"HapticScape startup failed",
			JOptionPane.ERROR_MESSAGE
		);
	}
}
