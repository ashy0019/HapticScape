package com.ashy0019.hapticscape.remote;

interface UnlockKeyProtector
{
	boolean isAvailable();

	String getUnavailableMessage();

	byte[] protect(byte[] plaintext);

	byte[] unprotect(byte[] ciphertext);
}
