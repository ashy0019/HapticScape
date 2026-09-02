package com.ashy0019.hapticscape.clicker;

import com.ashy0019.hapticscape.AlertCategory;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Immutable opt-in selection of semantic alerts which produce a click.
 */
public final class ClickerAlertSettings
{
	private final Set<AlertCategory> enabledCategories;

	private ClickerAlertSettings(Set<AlertCategory> enabledCategories)
	{
		EnumSet<AlertCategory> copy = EnumSet.noneOf(AlertCategory.class);
		copy.addAll(enabledCategories);
		this.enabledCategories = Collections.unmodifiableSet(copy);
	}

	public static ClickerAlertSettings noneEnabled()
	{
		return new ClickerAlertSettings(Collections.emptySet());
	}

	public static ClickerAlertSettings fromConfigValue(String configuredValue)
	{
		if (configuredValue == null || configuredValue.trim().isEmpty())
		{
			return noneEnabled();
		}

		EnumSet<AlertCategory> enabled = EnumSet.noneOf(AlertCategory.class);
		for (String token : configuredValue.split(","))
		{
			try
			{
				enabled.add(AlertCategory.valueOf(token.trim().toUpperCase(Locale.ROOT)));
			}
			catch (IllegalArgumentException ignored)
			{
				// Ignore unknown categories so future or malformed values are harmless.
			}
		}
		return new ClickerAlertSettings(enabled);
	}

	public boolean isEnabled(AlertCategory category)
	{
		return enabledCategories.contains(Objects.requireNonNull(category, "category"));
	}

	public ClickerAlertSettings withEnabled(AlertCategory category, boolean enabled)
	{
		Objects.requireNonNull(category, "category");
		EnumSet<AlertCategory> updated = EnumSet.noneOf(AlertCategory.class);
		updated.addAll(enabledCategories);
		if (enabled)
		{
			updated.add(category);
		}
		else
		{
			updated.remove(category);
		}
		return updated.equals(enabledCategories) ? this : new ClickerAlertSettings(updated);
	}

	public String toConfigValue()
	{
		StringJoiner result = new StringJoiner(",");
		for (AlertCategory category : AlertCategory.values())
		{
			if (enabledCategories.contains(category))
			{
				result.add(category.name());
			}
		}
		return result.toString();
	}
}
