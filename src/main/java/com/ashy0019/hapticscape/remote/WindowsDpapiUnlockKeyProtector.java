package com.ashy0019.hapticscape.remote;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Crypt32Util;
import java.util.Arrays;
import java.util.Objects;

/** Protects vault secrets with Windows DPAPI for the current Windows user. */
final class WindowsDpapiUnlockKeyProtector implements UnlockKeyProtector
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
			: "Saved Unlock Keys require the Windows client";
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
		catch (RuntimeException e)
		{
			throw new IllegalStateException(
				"Windows could not protect the saved unlock key",
				e
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
		catch (RuntimeException e)
		{
			throw new IllegalStateException(
				"Windows could not open the saved unlock key for this user",
				e
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
