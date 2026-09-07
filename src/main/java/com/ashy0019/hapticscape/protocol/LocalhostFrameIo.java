package com.ashy0019.hapticscape.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Bounded newline-delimited UTF-8 framing for the localhost transport. */
final class LocalhostFrameIo
{
	private LocalhostFrameIo()
	{
	}

	static String read(InputStream input) throws IOException
	{
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		while (true)
		{
			int value = input.read();
			if (value < 0)
			{
				return buffer.size() == 0
					? null
					: decode(buffer);
			}
			if (value == '\n')
			{
				return decode(buffer);
			}
			if (buffer.size() >= LocalhostTransportEndpoint.MAX_FRAME_BYTES)
			{
				throw new IOException("Local transport frame exceeds maximum size");
			}
			buffer.write(value);
		}
	}

	static void write(OutputStream output, String frame) throws IOException
	{
		if (frame == null)
		{
			throw new IllegalArgumentException("frame must not be null");
		}
		byte[] bytes = frame.getBytes(StandardCharsets.UTF_8);
		if (bytes.length > LocalhostTransportEndpoint.MAX_FRAME_BYTES)
		{
			throw new IOException("Local transport frame exceeds maximum size");
		}
		output.write(bytes);
		output.write('\n');
		output.flush();
	}

	private static String decode(ByteArrayOutputStream buffer)
	{
		return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
	}
}
