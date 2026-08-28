package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.CustomPattern;
import com.ashy0019.hapticscape.CustomPatternLibrary;
import com.ashy0019.hapticscape.CustomPatternSlot;
import com.ashy0019.hapticscape.HapticScapeConfig;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import net.runelite.client.config.ConfigManager;

final class PatternForgePanel extends JPanel
{
	private static final int MAXIMUM_UNDO_STATES = 20;

	private final ConfigManager configManager;
	private final IntSupplier previewDurationMillis;
	private final Consumer<CustomPattern> previewAction;
	private final Consumer<CustomPatternLibrary> libraryChangeAction;
	private final JComboBox<CustomPatternSlot> slotComboBox =
		new JComboBox<>(CustomPatternSlot.values());
	private final JButton renameButton = new JButton("Rename");
	private final PatternCanvas canvas = new PatternCanvas();
	private final JButton undoButton = new JButton("Undo");
	private final JButton clearButton = new JButton("Clear");
	private final JButton previewButton = new JButton("Preview");
	private final JButton saveButton = new JButton("Save");
	private final JLabel saveStateLabel = new JLabel("Saved", SwingConstants.RIGHT);
	private final Deque<CustomPattern> undoStates = new ArrayDeque<>();

	private CustomPatternLibrary library;
	private CustomPatternSlot selectedSlot = CustomPatternSlot.I;
	private CustomPattern draft;
	private boolean loadingControls;
	private boolean dirty;
	private Timer playheadTimer;
	private long previewStartedAt;

	PatternForgePanel(
		CustomPatternLibrary library,
		ConfigManager configManager,
		IntSupplier previewDurationMillis,
		Consumer<CustomPattern> previewAction,
		Consumer<CustomPatternLibrary> libraryChangeAction)
	{
		this.library = Objects.requireNonNull(library, "library");
		this.configManager = Objects.requireNonNull(configManager, "configManager");
		this.previewDurationMillis = Objects.requireNonNull(
			previewDurationMillis,
			"previewDurationMillis"
		);
		this.previewAction = Objects.requireNonNull(previewAction, "previewAction");
		this.libraryChangeAction = Objects.requireNonNull(
			libraryChangeAction,
			"libraryChangeAction"
		);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		slotComboBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(
				JList<?> list,
				Object value,
				int index,
				boolean isSelected,
				boolean cellHasFocus)
			{
				super.getListCellRendererComponent(
					list,
					value,
					index,
					isSelected,
					cellHasFocus
				);
				setText(value instanceof CustomPatternSlot
					? PatternForgePanel.this.library.getName((CustomPatternSlot) value)
					: "");
				return this;
			}
		});

		JPanel patternChoice = new JPanel(new BorderLayout(4, 0));
		renameButton.setMargin(new Insets(2, 5, 2, 5));
		patternChoice.add(slotComboBox, BorderLayout.CENTER);
		patternChoice.add(renameButton, BorderLayout.EAST);
		JPanel patternRow = new JPanel(new BorderLayout(8, 0));
		patternRow.add(new JLabel("Pattern"), BorderLayout.WEST);
		patternRow.add(patternChoice, BorderLayout.CENTER);
		add(patternRow);

		JLabel instructions = new JLabel(
			"<html>Draw directly on the curve below.<br>Left to right is time; height is intensity.</html>"
		);
		instructions.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
		add(instructions);
		add(canvas);

		JPanel saveStateRow = new JPanel(new BorderLayout());
		saveStateRow.add(new JLabel("Uses the active intensity and duration"), BorderLayout.WEST);
		saveStateRow.add(saveStateLabel, BorderLayout.EAST);
		add(saveStateRow);

		JPanel actionButtons = new JPanel(new GridLayout(2, 2, 4, 4));
		actionButtons.add(undoButton);
		actionButtons.add(clearButton);
		actionButtons.add(previewButton);
		actionButtons.add(saveButton);
		add(actionButtons);

		canvas.setGestureStartAction(this::rememberUndoState);
		canvas.setPatternChangeAction(pattern -> changeDraft(pattern, false));
		slotComboBox.addActionListener(event -> selectSlotFromControls());
		renameButton.addActionListener(event -> renameSelectedPattern());
		undoButton.addActionListener(event -> undo());
		clearButton.addActionListener(event -> changeDraft(CustomPattern.silent(), true));
		previewButton.addActionListener(event -> preview());
		saveButton.addActionListener(event -> saveDraft());

		loadSlot(selectedSlot);
	}

	CustomPatternLibrary getLibrary()
	{
		return library;
	}

	void setConnected(boolean connected)
	{
		previewButton.setEnabled(connected);
		if (!connected)
		{
			stopAnimation();
		}
	}

	void stopPreview()
	{
		stopAnimation();
	}

	void close()
	{
		stopAnimation();
	}

	private void selectSlotFromControls()
	{
		if (loadingControls)
		{
			return;
		}

		CustomPatternSlot selected = (CustomPatternSlot) slotComboBox.getSelectedItem();
		if (selected == null || selected == selectedSlot)
		{
			return;
		}

		if (dirty)
		{
			saveDraft();
		}
		selectedSlot = selected;
		loadSlot(selectedSlot);
	}

	private void loadSlot(CustomPatternSlot slot)
	{
		stopAnimation();
		loadingControls = true;
		try
		{
			selectedSlot = slot;
			slotComboBox.setSelectedItem(slot);
			draft = library.get(slot);
			canvas.setPattern(draft);
			undoStates.clear();
			setDirty(false);
		}
		finally
		{
			loadingControls = false;
		}
	}

	private void rememberUndoState()
	{
		rememberUndoState(draft);
	}

	private void rememberUndoState(CustomPattern pattern)
	{
		if (pattern == null)
		{
			return;
		}
		while (undoStates.size() >= MAXIMUM_UNDO_STATES)
		{
			undoStates.removeLast();
		}
		undoStates.push(pattern);
		undoButton.setEnabled(true);
	}

	private void changeDraft(CustomPattern pattern, boolean rememberCurrent)
	{
		stopAnimation();
		if (rememberCurrent)
		{
			rememberUndoState();
		}
		draft = Objects.requireNonNull(pattern, "pattern");
		canvas.setPattern(draft);
		setDirty(true);
	}

	private void undo()
	{
		if (undoStates.isEmpty())
		{
			return;
		}
		draft = undoStates.pop();
		canvas.setPattern(draft);
		undoButton.setEnabled(!undoStates.isEmpty());
		setDirty(true);
	}

	private void saveDraft()
	{
		library = library.withPattern(selectedSlot, draft);
		persistLibrary();
		setDirty(false);
	}

	private void renameSelectedPattern()
	{
		String updatedName = (String) JOptionPane.showInputDialog(
			this,
			"Choose a name for this custom pattern:",
			"Rename pattern",
			JOptionPane.PLAIN_MESSAGE,
			null,
			null,
			library.getName(selectedSlot)
		);
		if (updatedName == null)
		{
			return;
		}

		library = library.withName(selectedSlot, updatedName);
		persistLibrary();
	}

	private void persistLibrary()
	{
		configManager.setConfiguration(
			HapticScapeConfig.GROUP,
			HapticScapeConfig.CUSTOM_PATTERNS_KEY,
			library.toConfigValue()
		);
		slotComboBox.repaint();
		libraryChangeAction.accept(library);
	}

	private void preview()
	{
		startAnimation();
		previewAction.accept(draft);
	}

	private void startAnimation()
	{
		stopAnimation();
		int durationMillis = Math.max(1, previewDurationMillis.getAsInt());
		previewStartedAt = System.currentTimeMillis();
		canvas.setPlayheadProgress(0.0);
		playheadTimer = new Timer(30, event ->
		{
			double progress = (double) (System.currentTimeMillis() - previewStartedAt)
				/ durationMillis;
			if (progress >= 1.0)
			{
				stopAnimation();
			}
			else
			{
				canvas.setPlayheadProgress(progress);
			}
		});
		playheadTimer.start();
	}

	private void stopAnimation()
	{
		if (playheadTimer != null)
		{
			playheadTimer.stop();
			playheadTimer = null;
		}
		canvas.setPlayheadProgress(-1.0);
	}

	private void setDirty(boolean dirty)
	{
		this.dirty = dirty;
		saveButton.setEnabled(dirty);
		saveStateLabel.setText(dirty ? "Unsaved" : "Saved");
		undoButton.setEnabled(!undoStates.isEmpty());
	}

	private static final class PatternCanvas extends JPanel
	{
		private static final Color BACKGROUND = new Color(32, 34, 37);
		private static final Color GRID = new Color(72, 75, 79);
		private static final Color CURVE = new Color(255, 152, 31);
		private static final Color FILL = new Color(30, 135, 125, 95);
		private static final Color PLAYHEAD = new Color(255, 220, 90);
		private static final int LEFT = 28;
		private static final int RIGHT = 8;
		private static final int TOP = 10;
		private static final int BOTTOM = 18;

		private int[] samples = new int[CustomPattern.EDITOR_SAMPLE_COUNT];
		private Runnable gestureStartAction = () -> { };
		private Consumer<CustomPattern> patternChangeAction = ignored -> { };
		private double playheadProgress = -1.0;
		private int previousSampleIndex = -1;
		private int previousIntensity;

		private PatternCanvas()
		{
			setPreferredSize(new Dimension(0, 180));
			setMinimumSize(new Dimension(100, 150));
			setToolTipText("Click and drag to draw intensity over time");
			setBorder(BorderFactory.createLineBorder(new Color(91, 74, 49)));
			MouseAdapter drawingHandler = new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent event)
				{
					gestureStartAction.run();
					previousSampleIndex = -1;
					updateFromMouse(event);
				}

				@Override
				public void mouseDragged(MouseEvent event)
				{
					updateFromMouse(event);
				}

				@Override
				public void mouseReleased(MouseEvent event)
				{
					previousSampleIndex = -1;
				}
			};
			addMouseListener(drawingHandler);
			addMouseMotionListener(drawingHandler);
		}

		private void setGestureStartAction(Runnable action)
		{
			gestureStartAction = Objects.requireNonNull(action, "action");
		}

		private void setPatternChangeAction(Consumer<CustomPattern> action)
		{
			patternChangeAction = Objects.requireNonNull(action, "action");
		}

		private void setPattern(CustomPattern pattern)
		{
			CustomPattern displayed = pattern.resampled(CustomPattern.EDITOR_SAMPLE_COUNT);
			for (int index = 0; index < samples.length; index++)
			{
				samples[index] = displayed.getIntensityPercent(index);
			}
			repaint();
		}

		private void setPlayheadProgress(double progress)
		{
			playheadProgress = progress;
			repaint();
		}

		private void updateFromMouse(MouseEvent event)
		{
			int graphWidth = Math.max(1, getWidth() - LEFT - RIGHT);
			int graphHeight = Math.max(1, getHeight() - TOP - BOTTOM);
			int sampleIndex = (int) Math.round(
				(double) (event.getX() - LEFT) * (samples.length - 1) / graphWidth
			);
			sampleIndex = Math.max(0, Math.min(samples.length - 1, sampleIndex));
			int intensity = (int) Math.round(
				100.0 * (TOP + graphHeight - event.getY()) / graphHeight
			);
			intensity = Math.max(0, Math.min(100, intensity));

			if (previousSampleIndex < 0)
			{
				samples[sampleIndex] = intensity;
			}
			else
			{
				int from = Math.min(previousSampleIndex, sampleIndex);
				int to = Math.max(previousSampleIndex, sampleIndex);
				for (int index = from; index <= to; index++)
				{
					double fraction = from == to
						? 1.0
						: (double) (index - previousSampleIndex)
							/ (sampleIndex - previousSampleIndex);
					samples[index] = (int) Math.round(
						previousIntensity + (intensity - previousIntensity) * fraction
					);
				}
			}
			previousSampleIndex = sampleIndex;
			previousIntensity = intensity;
			patternChangeAction.accept(new CustomPattern(samples));
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D graphics2D = (Graphics2D) graphics.create();
			try
			{
				graphics2D.setRenderingHint(
					RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON
				);
				graphics2D.setColor(BACKGROUND);
				graphics2D.fillRect(0, 0, getWidth(), getHeight());

				int graphWidth = Math.max(1, getWidth() - LEFT - RIGHT);
				int graphHeight = Math.max(1, getHeight() - TOP - BOTTOM);
				graphics2D.setColor(GRID);
				graphics2D.setStroke(new BasicStroke(1f));
				for (int division = 0; division <= 4; division++)
				{
					int x = LEFT + graphWidth * division / 4;
					graphics2D.drawLine(x, TOP, x, TOP + graphHeight);
				}
				for (int division = 0; division <= 2; division++)
				{
					int y = TOP + graphHeight * division / 2;
					graphics2D.drawLine(LEFT, y, LEFT + graphWidth, y);
				}

				graphics2D.setColor(new Color(180, 180, 180));
				graphics2D.drawString("100", 2, TOP + 5);
				graphics2D.drawString("50", 8, TOP + graphHeight / 2 + 5);
				graphics2D.drawString("0", 14, TOP + graphHeight + 5);

				int[] xPoints = new int[samples.length + 2];
				int[] yPoints = new int[samples.length + 2];
				xPoints[0] = LEFT;
				yPoints[0] = TOP + graphHeight;
				for (int index = 0; index < samples.length; index++)
				{
					xPoints[index + 1] = LEFT
						+ graphWidth * index / (samples.length - 1);
					yPoints[index + 1] = TOP
						+ graphHeight * (100 - samples[index]) / 100;
				}
				xPoints[xPoints.length - 1] = LEFT + graphWidth;
				yPoints[yPoints.length - 1] = TOP + graphHeight;
				graphics2D.setColor(FILL);
				graphics2D.fill(new Polygon(xPoints, yPoints, xPoints.length));

				graphics2D.setColor(CURVE);
				graphics2D.setStroke(new BasicStroke(2.5f));
				for (int index = 1; index < samples.length; index++)
				{
					graphics2D.drawLine(
						xPoints[index],
						yPoints[index],
						xPoints[index + 1],
						yPoints[index + 1]
					);
				}

				if (playheadProgress >= 0.0)
				{
					int playheadX = LEFT + (int) Math.round(graphWidth * playheadProgress);
					graphics2D.setColor(PLAYHEAD);
					graphics2D.setStroke(new BasicStroke(2f));
					graphics2D.drawLine(playheadX, TOP, playheadX, TOP + graphHeight);
				}
			}
			finally
			{
				graphics2D.dispose();
			}
		}
	}
}
