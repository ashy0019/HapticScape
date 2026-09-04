package com.ashy0019.hapticscape.remote;

final class TestUnlockKeyProtector implements UnlockKeyProtector
{
	private static final byte MASK = (byte) 0xa5;
	private int protectCount;

	@Override
	public boolean isAvailable()
	{
		return true;
	}

	@Override
	public String getUnavailableMessage()
	{
		return "";
	}

	@Override
	public byte[] protect(byte[] plaintext)
	{
		protectCount++;
		return transform(plaintext);
	}

	@Override
	public byte[] unprotect(byte[] ciphertext)
	{
		return transform(ciphertext);
	}

	int getProtectCount()
	{
		return protectCount;
	}

	private static byte[] transform(byte[] input)
	{
		byte[] output = new byte[input.length];
		for (int index = 0; index < input.length; index++)
		{
			output[index] = (byte) (input[index] ^ MASK);
		}
		return output;
	}
}
