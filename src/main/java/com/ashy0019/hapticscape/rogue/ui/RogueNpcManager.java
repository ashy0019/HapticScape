package com.ashy0019.hapticscape.rogue.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Lightweight ambient population simulator for the Rogue's Den table.
 *
 * <p>The NPCs are deliberately cosmetic: they never touch the blackjack
 * engine, shoe, player bankroll, or turn order. Each seat independently moves
 * through EMPTY -> ARRIVING -> SEATED -> LEAVING and occasionally performs a
 * tiny idle action while seated. This keeps the room feeling alive without
 * coupling atmosphere to game rules.</p>
 */
final class RogueNpcManager
{
	enum Phase
	{
		EMPTY,
		ARRIVING,
		SEATED,
		LEAVING
	}

	enum IdleAction
	{
		NONE,
		SIP,
		FIDDLE_CHIPS,
		LOOK_LEFT,
		LOOK_RIGHT
	}

	static final class SeatSnapshot
	{
		private final int seatIndex;
		private final Phase phase;
		private final String name;
		private final Color skin;
		private final Color hair;
		private final Color outfit;
		private final Color accent;
		private final Color chipColor;
		private final int style;
		private final int chipCount;
		private final long wager;
		private final float presence;
		private final IdleAction idleAction;
		private final float actionProgress;
		private final String speechLine;
		private final float speechAlpha;

		private SeatSnapshot(
			int seatIndex,
			Phase phase,
			String name,
			Color skin,
			Color hair,
			Color outfit,
			Color accent,
			Color chipColor,
			int style,
			int chipCount,
			long wager,
			float presence,
			IdleAction idleAction,
			float actionProgress,
			String speechLine,
			float speechAlpha)
		{
			this.seatIndex = seatIndex;
			this.phase = phase;
			this.name = name;
			this.skin = skin;
			this.hair = hair;
			this.outfit = outfit;
			this.accent = accent;
			this.chipColor = chipColor;
			this.style = style;
			this.chipCount = chipCount;
			this.wager = wager;
			this.presence = presence;
			this.idleAction = idleAction;
			this.actionProgress = actionProgress;
			this.speechLine = speechLine;
			this.speechAlpha = speechAlpha;
		}

		int getSeatIndex()
		{
			return seatIndex;
		}

		Phase getPhase()
		{
			return phase;
		}

		String getName()
		{
			return name;
		}

		Color getSkin()
		{
			return skin;
		}

		Color getHair()
		{
			return hair;
		}

		Color getOutfit()
		{
			return outfit;
		}

		Color getAccent()
		{
			return accent;
		}

		Color getChipColor()
		{
			return chipColor;
		}

		int getStyle()
		{
			return style;
		}

		int getChipCount()
		{
			return chipCount;
		}

		long getWager()
		{
			return wager;
		}

		float getPresence()
		{
			return presence;
		}

		IdleAction getIdleAction()
		{
			return idleAction;
		}

		float getActionProgress()
		{
			return actionProgress;
		}

		String getSpeechLine()
		{
			return speechLine;
		}

		float getSpeechAlpha()
		{
			return speechAlpha;
		}
	}

	private static final long ARRIVE_NANOS = 2_600_000_000L;
	private static final long LEAVE_NANOS = 2_300_000_000L;
	private static final long ACTION_NANOS = 1_850_000_000L;
	private static final long MIN_DWELL_NANOS = 45_000_000_000L;
	private static final long MAX_DWELL_NANOS = 150_000_000_000L;
	private static final long MIN_EMPTY_NANOS = 8_000_000_000L;
	private static final long MAX_EMPTY_NANOS = 38_000_000_000L;
	private static final long MIN_IDLE_NANOS = 6_000_000_000L;
	private static final long MAX_IDLE_NANOS = 16_000_000_000L;
	private static final long MIN_SPEECH_NANOS = 2_600_000_000L;
	private static final long MAX_SPEECH_NANOS = 4_100_000_000L;
	private static final long MIN_SPEECH_GAP_NANOS = 9_000_000_000L;
	private static final long MAX_SPEECH_GAP_NANOS = 24_000_000_000L;
	private static final long SPEECH_FADE_NANOS = 320_000_000L;
	private static final long[] WAGERS = {10L, 25L, 50L, 100L, 250L};

	private static final String[][] SPEECH_LINES = {
		{"Keep it quiet.", "Eyes down.", "No peeking.", "Bad odds.", "Deal again."},
		{"Hit me.", "Again.", "Easy coin.", "One more.", "Dealer's sharp."},
		{"Double it.", "Twenty-five.", "Coin talks.", "Good price.", "Keep dealing."},
		{"Bad vein.", "Need more ale.", "One more.", "Bah.", "Cold table."},
		{"How quaint.", "Deal.", "Double it.", "Charming.", "Again, dealer."},
		{"Not my night.", "Seen worse.", "One last hand.", "My luck...", "Good ale."}
	};

	private static final String[] NAMES = {
		"GARRICK", "MIRA", "BRAM", "NELL", "VEX", "SABLE",
		"HOB", "RUFUS", "WYNN", "TAMSIN", "CROWE", "MAGS",
		"DORAN", "IVY", "PIKE", "OLD TOM"
	};

	private static final Color[] SKINS = {
		new Color(224, 177, 137),
		new Color(199, 145, 105),
		new Color(160, 108, 78),
		new Color(231, 191, 154),
		new Color(126, 84, 64)
	};

	private static final Color[] HAIRS = {
		new Color(65, 40, 29),
		new Color(126, 70, 35),
		new Color(48, 43, 39),
		new Color(164, 129, 74),
		new Color(99, 47, 34)
	};

	private static final Color[] OUTFITS = {
		new Color(72, 61, 95),
		new Color(112, 50, 42),
		new Color(49, 84, 63),
		new Color(102, 77, 45),
		new Color(55, 67, 105),
		new Color(82, 50, 71),
		new Color(67, 73, 55)
	};

	private static final Color[] ACCENTS = {
		new Color(193, 151, 65),
		new Color(181, 184, 166),
		new Color(142, 55, 48),
		new Color(58, 106, 132),
		new Color(201, 182, 128)
	};

	private static final Color[] CHIP_COLORS = {
		new Color(158, 45, 43),
		new Color(52, 77, 143),
		new Color(47, 118, 70),
		new Color(115, 54, 133),
		new Color(42, 42, 42),
		new Color(201, 164, 54)
	};

	private static final class Seat
	{
		private Phase phase = Phase.EMPTY;
		private Appearance appearance;
		private long phaseStartedNanos;
		private long phaseDeadlineNanos;
		private long nextIdleNanos;
		private long idleStartedNanos;
		private IdleAction idleAction = IdleAction.NONE;
		private int chipCount;
		private long wager;
		private long nextSpeechNanos;
		private long speechStartedNanos;
		private long speechDeadlineNanos;
		private String speechLine;
	}

	private static final class Appearance
	{
		private String name;
		private Color skin;
		private Color hair;
		private Color outfit;
		private Color accent;
		private Color chipColor;
		private int style;
	}

	private final Random random;
	private final Seat[] seats = {new Seat(), new Seat(), new Seat(), new Seat()};
	private boolean initialized;

	RogueNpcManager()
	{
		this(new Random());
	}

	RogueNpcManager(Random random)
	{
		this.random = random == null ? new Random() : random;
	}

	void update(long nowNanos)
	{
		if (!initialized)
		{
			initialize(nowNanos);
		}

		for (int index = 0; index < seats.length; index++)
		{
			Seat seat = seats[index];
			switch (seat.phase)
			{
				case EMPTY:
					if (nowNanos >= seat.phaseDeadlineNanos)
					{
						startArrival(seat, nowNanos);
					}
					break;
				case ARRIVING:
					if (nowNanos >= seat.phaseDeadlineNanos)
					{
						seat.phase = Phase.SEATED;
						seat.phaseStartedNanos = nowNanos;
						seat.phaseDeadlineNanos = nowNanos + randomBetween(MIN_DWELL_NANOS, MAX_DWELL_NANOS);
						seat.nextIdleNanos = nowNanos + randomBetween(MIN_IDLE_NANOS, MAX_IDLE_NANOS);
						seat.nextSpeechNanos = nowNanos + randomBetween(3_000_000_000L, 8_000_000_000L);
					}
					break;
				case SEATED:
					updateIdleAction(seat, nowNanos);
					updateSpeech(seat, nowNanos);
					if (nowNanos >= seat.phaseDeadlineNanos)
					{
						seat.idleAction = IdleAction.NONE;
						seat.speechLine = null;
						seat.phase = Phase.LEAVING;
						seat.phaseStartedNanos = nowNanos;
						seat.phaseDeadlineNanos = nowNanos + LEAVE_NANOS;
					}
					break;
				case LEAVING:
					if (nowNanos >= seat.phaseDeadlineNanos)
					{
						seat.phase = Phase.EMPTY;
						seat.appearance = null;
						seat.idleAction = IdleAction.NONE;
						seat.speechLine = null;
						seat.phaseStartedNanos = nowNanos;
						seat.phaseDeadlineNanos = nowNanos + randomBetween(MIN_EMPTY_NANOS, MAX_EMPTY_NANOS);
					}
					break;
				default:
					break;
			}
		}
	}

	List<SeatSnapshot> snapshot(long nowNanos)
	{
		update(nowNanos);
		List<SeatSnapshot> result = new ArrayList<>(seats.length);
		for (int index = 0; index < seats.length; index++)
		{
			Seat seat = seats[index];
			Appearance appearance = seat.appearance;
			float presence = presence(seat, nowNanos);
			float actionProgress = actionProgress(seat, nowNanos);
			result.add(new SeatSnapshot(
				index,
				seat.phase,
				appearance == null ? null : appearance.name,
				appearance == null ? SKINS[0] : appearance.skin,
				appearance == null ? HAIRS[0] : appearance.hair,
				appearance == null ? OUTFITS[0] : appearance.outfit,
				appearance == null ? ACCENTS[0] : appearance.accent,
				appearance == null ? CHIP_COLORS[0] : appearance.chipColor,
				appearance == null ? 0 : appearance.style,
				seat.chipCount,
				seat.wager,
				presence,
				seat.idleAction,
				actionProgress,
				seat.speechLine,
				speechAlpha(seat, nowNanos)
			));
		}
		return Collections.unmodifiableList(result);
	}

	private void initialize(long nowNanos)
	{
		initialized = true;

		// Start with one resident rogue so the feature is visible immediately.
		// The remaining seats enter on staggered timers rather than popping in as
		// a group the moment the panel opens.
		int first = random.nextInt(seats.length);
		Seat seated = seats[first];
		seated.appearance = randomUniqueAppearance();
		seated.chipCount = randomBetweenInt(3, 8);
		seated.wager = randomWager();
		seated.phase = Phase.SEATED;
		seated.phaseStartedNanos = nowNanos;
		seated.phaseDeadlineNanos = nowNanos + randomBetween(55_000_000_000L, 130_000_000_000L);
		seated.nextIdleNanos = nowNanos + randomBetween(4_000_000_000L, 10_000_000_000L);
		startSpeech(seated, nowNanos);

		for (int index = 0; index < seats.length; index++)
		{
			if (index == first)
			{
				continue;
			}
			Seat seat = seats[index];
			seat.phase = Phase.EMPTY;
			seat.phaseStartedNanos = nowNanos;
			seat.phaseDeadlineNanos = nowNanos + randomBetween(5_000_000_000L, 28_000_000_000L);
		}
	}

	private void startArrival(Seat seat, long nowNanos)
	{
		seat.appearance = randomUniqueAppearance();
		seat.chipCount = randomBetweenInt(2, 8);
		seat.wager = randomWager();
		seat.idleAction = IdleAction.NONE;
		seat.speechLine = null;
		seat.nextSpeechNanos = Long.MAX_VALUE;
		seat.phase = Phase.ARRIVING;
		seat.phaseStartedNanos = nowNanos;
		seat.phaseDeadlineNanos = nowNanos + ARRIVE_NANOS;
	}

	private void updateIdleAction(Seat seat, long nowNanos)
	{
		if (seat.idleAction != IdleAction.NONE)
		{
			if (nowNanos - seat.idleStartedNanos >= ACTION_NANOS)
			{
				seat.idleAction = IdleAction.NONE;
				seat.nextIdleNanos = nowNanos + randomBetween(MIN_IDLE_NANOS, MAX_IDLE_NANOS);
			}
			return;
		}

		if (nowNanos < seat.nextIdleNanos)
		{
			return;
		}

		int pick = random.nextInt(5);
		switch (pick)
		{
			case 0:
				seat.idleAction = IdleAction.SIP;
				break;
			case 1:
				seat.idleAction = IdleAction.FIDDLE_CHIPS;
				seat.chipCount = Math.max(1, Math.min(9, seat.chipCount + (random.nextBoolean() ? 1 : -1)));
				if (random.nextInt(3) == 0)
				{
					seat.wager = randomWager();
				}
				break;
			case 2:
				seat.idleAction = IdleAction.LOOK_LEFT;
				break;
			case 3:
				seat.idleAction = IdleAction.LOOK_RIGHT;
				break;
			default:
				seat.idleAction = IdleAction.NONE;
				seat.nextIdleNanos = nowNanos + randomBetween(3_000_000_000L, 8_000_000_000L);
				return;
		}
		seat.idleStartedNanos = nowNanos;
	}

	private void updateSpeech(Seat seat, long nowNanos)
	{
		if (seat.speechLine != null)
		{
			if (nowNanos >= seat.speechDeadlineNanos)
			{
				seat.speechLine = null;
				seat.nextSpeechNanos = nowNanos + randomBetween(
					MIN_SPEECH_GAP_NANOS, MAX_SPEECH_GAP_NANOS);
			}
			return;
		}

		if (nowNanos < seat.nextSpeechNanos || anotherSeatIsSpeaking(seat))
		{
			return;
		}

		startSpeech(seat, nowNanos);
	}

	private void startSpeech(Seat seat, long nowNanos)
	{
		if (seat.appearance == null)
		{
			return;
		}
		String[] lines = SPEECH_LINES[Math.max(0, Math.min(SPEECH_LINES.length - 1, seat.appearance.style))];
		seat.speechLine = lines[random.nextInt(lines.length)];
		seat.speechStartedNanos = nowNanos;
		seat.speechDeadlineNanos = nowNanos + randomBetween(MIN_SPEECH_NANOS, MAX_SPEECH_NANOS);
	}

	private boolean anotherSeatIsSpeaking(Seat seat)
	{
		for (Seat candidate : seats)
		{
			if (candidate != seat && candidate.speechLine != null)
			{
				return true;
			}
		}
		return false;
	}

	private Appearance randomAppearance()
	{
		Appearance appearance = new Appearance();
		appearance.name = NAMES[random.nextInt(NAMES.length)];
		appearance.skin = SKINS[random.nextInt(SKINS.length)];
		appearance.hair = HAIRS[random.nextInt(HAIRS.length)];
		appearance.outfit = OUTFITS[random.nextInt(OUTFITS.length)];
		appearance.accent = ACCENTS[random.nextInt(ACCENTS.length)];
		appearance.chipColor = CHIP_COLORS[random.nextInt(CHIP_COLORS.length)];
		appearance.style = random.nextInt(6);
		return appearance;
	}

	private Appearance randomUniqueAppearance()
	{
		Appearance fallback = null;
		for (int attempt = 0; attempt < 12; attempt++)
		{
			Appearance candidate = randomAppearance();
			fallback = candidate;
			boolean duplicate = false;
			for (Seat seat : seats)
			{
				if (seat.appearance != null && candidate.name.equals(seat.appearance.name))
				{
					duplicate = true;
					break;
				}
			}
			if (!duplicate)
			{
				return candidate;
			}
		}
		return fallback == null ? randomAppearance() : fallback;
	}

	private long randomWager()
	{
		return WAGERS[random.nextInt(WAGERS.length)];
	}

	private long randomBetween(long minInclusive, long maxInclusive)
	{
		if (maxInclusive <= minInclusive)
		{
			return minInclusive;
		}
		double fraction = random.nextDouble();
		return minInclusive + (long) (fraction * (maxInclusive - minInclusive));
	}

	private int randomBetweenInt(int minInclusive, int maxInclusive)
	{
		return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
	}

	private static float presence(Seat seat, long nowNanos)
	{
		switch (seat.phase)
		{
			case ARRIVING:
				return clamp01((nowNanos - seat.phaseStartedNanos) / (float) ARRIVE_NANOS);
			case SEATED:
				return 1.0f;
			case LEAVING:
				return 1.0f - clamp01((nowNanos - seat.phaseStartedNanos) / (float) LEAVE_NANOS);
			case EMPTY:
			default:
				return 0.0f;
		}
	}

	private static float actionProgress(Seat seat, long nowNanos)
	{
		if (seat.idleAction == IdleAction.NONE)
		{
			return 0.0f;
		}
		float linear = clamp01((nowNanos - seat.idleStartedNanos) / (float) ACTION_NANOS);
		return 1.0f - Math.abs(linear * 2.0f - 1.0f);
	}

	private static float speechAlpha(Seat seat, long nowNanos)
	{
		if (seat.speechLine == null)
		{
			return 0.0f;
		}

		long age = Math.max(0L, nowNanos - seat.speechStartedNanos);
		long remaining = Math.max(0L, seat.speechDeadlineNanos - nowNanos);
		float fadeIn = clamp01(age / (float) SPEECH_FADE_NANOS);
		float fadeOut = clamp01(remaining / (float) SPEECH_FADE_NANOS);
		return Math.min(fadeIn, fadeOut);
	}

	private static float clamp01(float value)
	{
		return Math.max(0.0f, Math.min(1.0f, value));
	}
}
