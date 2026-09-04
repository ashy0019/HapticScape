package com.ashy0019.hapticscape.remote;

final class RemoteTextSanitizer
{
	private RemoteTextSanitizer()
	{
	}

	static String sanitize(String value)
	{
		String withoutTags = value.replaceAll("<[^>]*>", "");
		StringBuilder cleaned = new StringBuilder();
		boolean previousWhitespace = false;
		for (int index = 0; index < withoutTags.length(); index++)
		{
			char character = withoutTags.charAt(index);
			if (Character.isISOControl(character)
				|| Character.getType(character) == Character.FORMAT
				|| Character.isWhitespace(character))
			{
				if (!previousWhitespace && cleaned.length() > 0)
				{
					cleaned.append(' ');
				}
				previousWhitespace = true;
				continue;
			}
			previousWhitespace = false;
			if (character == '<' || character == '>')
			{
				continue;
			}
			cleaned.append(character);
		}
		String result = cleaned.toString().trim();
		result = result.replaceAll("(?i)https://", "https[:]//")
			.replaceAll("(?i)http://", "http[:]//")
			.replaceAll("(?i)www\\.", "www[.]");
		if (result.length() > RemoteAction.MAXIMUM_MESSAGE_LENGTH)
		{
			result = result.substring(0, RemoteAction.MAXIMUM_MESSAGE_LENGTH);
		}
		return result;
	}
}
