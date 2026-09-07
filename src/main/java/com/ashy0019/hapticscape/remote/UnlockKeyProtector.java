package com.ashy0019.hapticscape.remote;

public interface UnlockKeyProtector
{
	boolean isAvailable();

	String getUnavailableMessage();

	byte[] protect(byte[] plaintext);

	byte[] unprotect(byte[] ciphertext);
}
