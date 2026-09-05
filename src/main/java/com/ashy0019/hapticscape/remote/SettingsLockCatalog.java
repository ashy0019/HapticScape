package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.AlertCategory;
import com.ashy0019.hapticscape.SkillSelection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.runelite.api.Skill;

/** Authoritative registry of setting identifiers allowed in lock proposals. */
public final class SettingsLockCatalog
{
	private static final Map<String, SettingsLockTarget> TARGETS = new LinkedHashMap<>();

	public static final SettingsLockTarget LEVEL_UP_HAPTICS = register(
		"feature.level-ups.haptics",
		"Feedback",
		"Level-up haptics"
	);
	public static final SettingsLockTarget MILESTONE_HAPTICS = register(
		"feature.milestones.haptics",
		"Feedback",
		"Milestone haptics"
	);
	public static final SettingsLockTarget LEVEL_99_HAPTICS = register(
		"feature.level-99.haptics",
		"Feedback",
		"Level 99 celebration"
	);
	public static final SettingsLockTarget CLICKER_ENABLED = register(
		"clicker.enabled",
		"Clicker",
		"Clicker enabled"
	);
	public static final SettingsLockTarget CLICKER_LEVEL_UP = register(
		"clicker.level-ups",
		"Clicker",
		"Level-up clicks"
	);
	public static final SettingsLockTarget CLICKER_MILESTONE = register(
		"clicker.milestones",
		"Clicker",
		"Milestone clicks"
	);
	public static final SettingsLockTarget CLICKER_LEVEL_99 = register(
		"clicker.level-99",
		"Clicker",
		"Level 99 clicks"
	);
	public static final SettingsLockTarget GENERIC_NOTIFICATION_HAPTICS = register(
		"notifications.generic.haptics",
		"Notifications",
		"Generic notification haptics"
	);
	public static final SettingsLockTarget GENERIC_NOTIFICATION_CLICKS = register(
		"notifications.generic.clicks",
		"Notifications",
		"Generic notification clicks"
	);
	public static final SettingsLockTarget NOTIFICATION_RESPECT_FOCUS = register(
		"notifications.respect-focus",
		"Notifications",
		"Respect RuneLite focus"
	);

	private static final Map<Skill, SettingsLockTarget> HAPTIC_SKILLS =
		new LinkedHashMap<>();
	private static final Map<Skill, SettingsLockTarget> CLICK_SKILLS =
		new LinkedHashMap<>();
	private static final Map<Skill, SettingsLockTarget> PROFILE_OVERRIDES =
		new LinkedHashMap<>();
	private static final Map<AlertCategory, SettingsLockTarget> ALERT_CLICKS =
		new LinkedHashMap<>();

	static
	{
		for (Skill skill : SkillSelection.getSelectableSkills())
		{
			String slug = skill.name().toLowerCase(Locale.ROOT).replace('_', '-');
			HAPTIC_SKILLS.put(skill, register(
				"skill." + slug + ".haptics",
				"Skill haptics",
				skill.getName() + " haptics"
			));
			CLICK_SKILLS.put(skill, register(
				"skill." + slug + ".clicks",
				"Skill clicks",
				skill.getName() + " clicks"
			));
			PROFILE_OVERRIDES.put(skill, register(
				"profile." + slug + ".use-global",
				"Skill profiles",
				skill.getName() + " uses global XP settings"
			));
		}
		for (AlertCategory category : AlertCategory.values())
		{
			String slug = category.name().toLowerCase(Locale.ROOT).replace('_', '-');
			ALERT_CLICKS.put(category, register(
				"alert." + slug + ".clicks",
				"Alert clicks",
				category.getDisplayName() + " clicks"
			));
		}
	}

	private SettingsLockCatalog()
	{
	}

	public static SettingsLockTarget skillHaptics(Skill skill)
	{
		return Objects.requireNonNull(HAPTIC_SKILLS.get(skill), "Unsupported skill");
	}

	public static SettingsLockTarget skillClicks(Skill skill)
	{
		return Objects.requireNonNull(CLICK_SKILLS.get(skill), "Unsupported skill");
	}

	public static SettingsLockTarget profileUsesGlobal(Skill skill)
	{
		return Objects.requireNonNull(PROFILE_OVERRIDES.get(skill), "Unsupported skill");
	}

	public static SettingsLockTarget alertClicks(AlertCategory category)
	{
		return Objects.requireNonNull(ALERT_CLICKS.get(category), "Unsupported alert");
	}

	public static SettingsLockTarget require(String id)
	{
		SettingsLockTarget target = TARGETS.get(id);
		if (target == null)
		{
			throw new IllegalArgumentException("Unknown settings-lock target: " + id);
		}
		return target;
	}

	public static Set<SettingsLockTarget> resolve(Collection<String> ids)
	{
		Objects.requireNonNull(ids, "ids");
		Set<SettingsLockTarget> resolved = new LinkedHashSet<>();
		for (String id : ids)
		{
			SettingsLockTarget target = require(Objects.requireNonNull(id, "target ID"));
			if (!resolved.add(target))
			{
				throw new IllegalArgumentException("Duplicate settings-lock target: " + id);
			}
		}
		return Collections.unmodifiableSet(resolved);
	}

	public static List<String> ids(Collection<SettingsLockTarget> targets)
	{
		Objects.requireNonNull(targets, "targets");
		List<String> ids = new ArrayList<>();
		for (SettingsLockTarget target : targets)
		{
			SettingsLockTarget canonical = require(
				Objects.requireNonNull(target, "target").getId()
			);
			if (!ids.contains(canonical.getId()))
			{
				ids.add(canonical.getId());
			}
		}
		return Collections.unmodifiableList(ids);
	}

	public static Set<SettingsLockTarget> allTargets()
	{
		return Collections.unmodifiableSet(new LinkedHashSet<>(TARGETS.values()));
	}

	private static SettingsLockTarget register(String id, String group, String displayName)
	{
		SettingsLockTarget target = new SettingsLockTarget(id, group, displayName);
		if (TARGETS.put(id, target) != null)
		{
			throw new IllegalStateException("Duplicate settings-lock target: " + id);
		}
		return target;
	}
}
