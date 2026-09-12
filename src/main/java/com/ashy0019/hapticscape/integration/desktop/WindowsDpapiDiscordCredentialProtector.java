package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.remote.UnlockKeyProtector;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Crypt32Util;
import java.util.Arrays;
import java.util.Objects;

/** Protects the Discord device credential for the current Windows account. */
final class WindowsDpapiDiscordCredentialProtector implements UnlockKeyProtector
{
	@Override
	public boolean isAvailable()
	{
		return Platform.isWindows();
	}

	@Override
	public String getUnavailableMessage()
	{
		return isAvailable()
			? ""
			: "Discord linking requires the Windows client";
	}

	@Override
	public byte[] protect(byte[] plaintext)
	{
		requireAvailable();
		byte[] copy = Arrays.copyOf(
			Objects.requireNonNull(plaintext, "plaintext"),
			plaintext.length
		);
		try
		{
			return Crypt32Util.cryptProtectData(copy);
		}
		catch (RuntimeException exception)
		{
			throw new IllegalStateException(
				"Windows could not protect the Discord device credential",
				exception
			);
		}
		finally
		{
			Arrays.fill(copy, (byte) 0);
		}
	}

	@Override
	public byte[] unprotect(byte[] ciphertext)
	{
		requireAvailable();
		byte[] copy = Arrays.copyOf(
			Objects.requireNonNull(ciphertext, "ciphertext"),
			ciphertext.length
		);
		try
		{
			return Crypt32Util.cryptUnprotectData(copy);
		}
		catch (RuntimeException exception)
		{
			throw new IllegalStateException(
				"Windows could not open the Discord device credential for this user",
				exception
			);
		}
		finally
		{
			Arrays.fill(copy, (byte) 0);
		}
	}

	private void requireAvailable()
	{
		if (!isAvailable())
		{
			throw new IllegalStateException(getUnavailableMessage());
		}
	}
}
