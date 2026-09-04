package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.CustomPattern;
import com.ashy0019.hapticscape.CustomPatternEntry;
import com.ashy0019.hapticscape.CustomPatternLibrary;
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
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import net.runelite.client.config.ConfigManager;

final class PatternForgePanel extends JPanel
{
	private static final int MAXIMUM_UNDO_STATES = 20;

	private final ConfigManager configManager;
	private final Consumer<CustomPatternEntry> previewAction;
	private final Consumer<CustomPatternLibrary> libraryChangeAction;
	private final JComboBox<CustomPatternEntry> patternComboBox = new JComboBox<>();
	private final JButton addButton = new JButton("Add");
	private final JButton renameButton = new JButton("Rename");
	private final JButton deleteButton = new JButton("Delete");
	private final PatternCanvas canvas = new PatternCanvas();
	private final JButton undoButton = new JButton("Undo");
	private final JButton clearButton = new JButton("Clear");
	private final JButton previewButton = new JButton("Preview");
	private final JButton saveButton = new JButton("Save");
	private final JSpinner beatDurationSpinner = new JSpinner(new SpinnerNumberModel(
		CustomPatternEntry.DEFAULT_BEAT_DURATION_MILLIS,
		CustomPatternEntry.MINIMUM_BEAT_DURATION_MILLIS,
		CustomPatternEntry.MAXIMUM_BEAT_DURATION_MILLIS,
		50
	));
	private final JSpinner beatCountSpinner = new JSpinner(new SpinnerNumberModel(
		CustomPatternEntry.DEFAULT_BEAT_COUNT,
		CustomPatternEntry.MINIMUM_BEAT_COUNT,
		CustomPatternEntry.MAXIMUM_BEAT_COUNT,
		1
	));
	private final JLabel playbackSummaryLabel = new JLabel();
	private final JLabel saveStateLabel = new JLabel("Saved", SwingConstants.RIGHT);
	private final Deque<CustomPattern> undoStates = new ArrayDeque<>();

	private CustomPatternLibrary library;
	private int selectedPatternId = -1;
	private CustomPattern draft;
	private boolean loadingControls;
	private boolean dirty;
	private boolean connected;
	private boolean remoteReadOnly;
	private Timer playheadTimer;
	private long previewStartedAt;

	PatternForgePanel(
		CustomPatternLibrary library,
		ConfigManager configManager,
		Consumer<CustomPatternEntry> previewAction,
		Consumer<CustomPatternLibrary> libraryChangeAction)
	{
		this.library = Objects.requireNonNull(library, "library");
		this.configManager = Objects.requireNonNull(configManager, "configManager");
		this.previewAction = Objects.requireNonNull(previewAction, "previewAction");
		this.libraryChangeAction = Objects.requireNonNull(
			libraryChangeAction,
			"libraryChangeAction"
		);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		patternComboBox.setRenderer(new DefaultListCellRenderer()
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
				setText(value instanceof CustomPatternEntry
					? ((CustomPatternEntry) value).getName()
					: "");
				return this;
			}
		});

		JPanel patternChoice = new JPanel(new BorderLayout());
		patternChoice.add(patternComboBox, BorderLayout.CENTER);
		JPanel patternButtons = new JPanel(new GridLayout(1, 3, 4, 0));
		configureCompactButton(addButton);
		configureCompactButton(renameButton);
		configureCompactButton(deleteButton);
		patternButtons.add(addButton);
		patternButtons.add(renameButton);
		patternButtons.add(deleteButton);
		patternButtons.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		patternChoice.add(patternButtons, BorderLayout.SOUTH);
		JPanel patternRow = new JPanel(new BorderLayout(8, 0));
		patternRow.add(new JLabel("Pattern"), BorderLayout.WEST);
		patternRow.add(patternChoice, BorderLayout.CENTER);
		addVerticalComponent(this, patternRow);

		JLabel instructions = new JLabel(
			"<html>Draw one beat on the curve below.<br>Left to right is time; height is intensity.</html>"
		);
		instructions.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
		addVerticalComponent(this, instructions);
		addVerticalComponent(this, canvas);

		configureCompactSpinner(beatDurationSpinner);
		configureCompactSpinner(beatCountSpinner);
		beatDurationSpinner.setToolTipText(
			"Length of one drawn beat, from 50 ms to 10 seconds"
		);
		beatCountSpinner.setToolTipText(
			"Number of times to repeat the drawn beat, from 1 to 72"
		);
		JPanel beatDurationRow = new JPanel(new BorderLayout(8, 0));
		beatDurationRow.add(new JLabel("Beat length (ms)"), BorderLayout.CENTER);
		beatDurationRow.add(beatDurationSpinner, BorderLayout.EAST);
		addVerticalComponent(this, beatDurationRow);

		JPanel beatCountRow = new JPanel(new BorderLayout(8, 0));
		beatCountRow.add(new JLabel("Beats"), BorderLayout.CENTER);
		beatCountRow.add(beatCountSpinner, BorderLayout.EAST);
		addVerticalComponent(this, beatCountRow);

		JPanel saveStateRow = new JPanel(new BorderLayout());
		playbackSummaryLabel.setToolTipText(
			"Custom patterns save their own intensity, timing, and repetitions"
		);
		saveStateRow.add(playbackSummaryLabel, BorderLayout.WEST);
		saveStateRow.add(saveStateLabel, BorderLayout.EAST);
		addVerticalComponent(this, saveStateRow);

		JPanel actionButtons = new JPanel(new GridLayout(2, 2, 4, 4));
		actionButtons.add(undoButton);
		actionButtons.add(clearButton);
		actionButtons.add(previewButton);
		actionButtons.add(saveButton);
		addVerticalComponent(this, actionButtons);

		canvas.setGestureStartAction(this::rememberUndoState);
		canvas.setPatternChangeAction(pattern -> changeDraft(pattern, false));
		patternComboBox.addActionListener(event -> selectPatternFromControls());
		addButton.addActionListener(event -> addPattern());
		renameButton.addActionListener(event -> renameSelectedPattern());
		deleteButton.addActionListener(event -> deleteSelectedPattern());
		undoButton.addActionListener(event -> undo());
		clearButton.addActionListener(event -> changeDraft(CustomPattern.silent(), true));
		previewButton.addActionListener(event -> preview());
		saveButton.addActionListener(event -> saveDraft());
		beatDurationSpinner.addChangeListener(event -> changePlaybackSettings());
		beatCountSpinner.addChangeListener(event -> changePlaybackSettings());

		int initialId = library.getPatterns().get(0).getId();
		refreshPatternChoices(initialId);
		loadPattern(initialId);
	}

	private static void configureCompactButton(JButton button)
	{
		button.setMargin(new Insets(2, 3, 2, 3));
		button.putClientProperty("JButton.minimumWidth", 0);
	}

	private static void configureCompactSpinner(JSpinner spinner)
	{
		Dimension preferred = spinner.getPreferredSize();
		spinner.setPreferredSize(new Dimension(82, preferred.height));
		spinner.setMaximumSize(new Dimension(82, preferred.height));
	}

	private static void addVerticalComponent(JPanel panel, JComponent component)
	{
		Dimension preferredSize = component.getPreferredSize();
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferredSize.height));
		panel.add(component);
	}

	CustomPatternLibrary getLibrary()
	{
		return library;
	}

	void applyDisplayedLibrary(CustomPatternLibrary displayedLibrary)
	{
		library = Objects.requireNonNull(displayedLibrary, "displayedLibrary");
		int selectedId = library.findById(selectedPatternId).isPresent()
			? selectedPatternId
			: library.getPatterns().get(0).getId();
		refreshPatternChoices(selectedId);
		loadPattern(selectedId);
	}

	void setRemoteReadOnly(boolean remoteReadOnly)
	{
		this.remoteReadOnly = remoteReadOnly;
		if (remoteReadOnly)
		{
			stopAnimation();
		}
		refreshEditorState();
	}

	void setConnected(boolean connected)
	{
		this.connected = connected;
		if (!connected)
		{
			stopAnimation();
		}
		refreshEditorState();
	}

	void stopPreview()
	{
		stopAnimation();
	}

	void close()
	{
		stopAnimation();
	}

	private void selectPatternFromControls()
	{
		if (loadingControls)
		{
			return;
		}

		CustomPatternEntry selected = (CustomPatternEntry) patternComboBox.getSelectedItem();
		if (selected == null || selected.getId() == selectedPatternId)
		{
			return;
		}

		if (dirty && !remoteReadOnly)
		{
			saveDraft();
		}
		loadPattern(selected.getId());
	}

	private void loadPattern(int patternId)
	{
		CustomPatternEntry entry = library.findById(patternId)
			.orElse(library.getPatterns().get(0));
		stopAnimation();
		loadingControls = true;
		try
		{
			selectedPatternId = entry.getId();
			patternComboBox.setSelectedItem(entry);
			draft = entry.getPattern();
			canvas.setPattern(draft);
			beatDurationSpinner.setValue(entry.getBeatDurationMillis());
			beatCountSpinner.setValue(entry.getBeatCount());
			updatePlaybackSummary();
			undoStates.clear();
			setDirty(false);
			updateLibraryButtons();
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
		if (remoteReadOnly)
		{
			return;
		}
		stopAnimation();
		if (rememberCurrent)
		{
			rememberUndoState();
		}
		draft = Objects.requireNonNull(pattern, "pattern");
		canvas.setPattern(draft);
		setDirty(true);
	}

	private void changePlaybackSettings()
	{
		if (remoteReadOnly || loadingControls)
		{
			return;
		}
		stopAnimation();
		updatePlaybackSummary();
		setDirty(true);
	}

	private void undo()
	{
		if (remoteReadOnly)
		{
			return;
		}
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
		if (remoteReadOnly)
		{
			return;
		}
		library = library.withPattern(
			selectedPatternId,
			draft,
			getBeatDurationMillis(),
			getBeatCount()
		);
		persistLibrary();
		setDirty(false);
	}

	private void addPattern()
	{
		if (remoteReadOnly)
		{
			return;
		}
		if (!library.canAddPattern())
		{
			return;
		}
		if (dirty)
		{
			saveDraft();
		}

		int addedId = library.getNextPatternId();
		library = library.addBlankPattern();
		persistLibrary();
		refreshPatternChoices(addedId);
		loadPattern(addedId);
	}

	private void renameSelectedPattern()
	{
		if (remoteReadOnly)
		{
			return;
		}
		String updatedName = (String) JOptionPane.showInputDialog(
			this,
			"Choose a name for this custom pattern:",
			"Rename pattern",
			JOptionPane.PLAIN_MESSAGE,
			null,
			null,
			library.findById(selectedPatternId)
				.map(CustomPatternEntry::getName)
				.orElse("")
		);
		if (updatedName == null)
		{
			return;
		}

		library = library.withName(selectedPatternId, updatedName);
		persistLibrary();
		refreshPatternChoices(selectedPatternId);
	}

	private void deleteSelectedPattern()
	{
		if (remoteReadOnly)
		{
			return;
		}
		if (library.size() == 1)
		{
			return;
		}

		CustomPatternEntry selected = library.findById(selectedPatternId).orElse(null);
		if (selected == null)
		{
			return;
		}
		int answer = JOptionPane.showConfirmDialog(
			this,
			"Delete \"" + selected.getName() + "\"?\n"
				+ "Any settings using it will return to Single pulse.",
			"Delete custom pattern",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE
		);
		if (answer != JOptionPane.YES_OPTION)
		{
			return;
		}

		int currentIndex = library.getPatterns().indexOf(selected);
		library = library.withoutPattern(selectedPatternId);
		int replacementIndex = Math.min(currentIndex, library.size() - 1);
		int replacementId = library.getPatterns().get(replacementIndex).getId();
		persistLibrary();
		refreshPatternChoices(replacementId);
		loadPattern(replacementId);
	}

	private void persistLibrary()
	{
		configManager.setConfiguration(
			HapticScapeConfig.GROUP,
			HapticScapeConfig.CUSTOM_PATTERNS_KEY,
			library.toConfigValue()
		);
		patternComboBox.repaint();
		libraryChangeAction.accept(library);
	}

	private void refreshPatternChoices(int selectedId)
	{
		loadingControls = true;
		try
		{
			DefaultComboBoxModel<CustomPatternEntry> model = new DefaultComboBoxModel<>();
			for (CustomPatternEntry entry : library.getPatterns())
			{
				model.addElement(entry);
			}
			patternComboBox.setModel(model);
			library.findById(selectedId).ifPresent(patternComboBox::setSelectedItem);
		}
		finally
		{
			loadingControls = false;
		}
		updateLibraryButtons();
	}

	private void updateLibraryButtons()
	{
		boolean editable = !remoteReadOnly;
		addButton.setEnabled(editable && library.canAddPattern());
		renameButton.setEnabled(editable);
		deleteButton.setEnabled(editable && library.size() > 1);
		addButton.setToolTipText(library.canAddPattern()
			? "Create a blank custom pattern (" + library.size() + "/"
				+ CustomPatternLibrary.MAXIMUM_PATTERN_COUNT + ")"
			: "Maximum of " + CustomPatternLibrary.MAXIMUM_PATTERN_COUNT
				+ " custom patterns reached");
	}

	private void refreshEditorState()
	{
		boolean editable = !remoteReadOnly;
		// Pattern selection is navigation only and remains available while locked.
		patternComboBox.setEnabled(true);
		beatDurationSpinner.setEnabled(editable);
		beatCountSpinner.setEnabled(editable);
		canvas.setEditable(editable);
		clearButton.setEnabled(editable);
		undoButton.setEnabled(editable && !undoStates.isEmpty());
		saveButton.setEnabled(editable && dirty);
		previewButton.setEnabled(editable && connected);
		updateLibraryButtons();
	}

	private void preview()
	{
		if (remoteReadOnly)
		{
			return;
		}
		startAnimation();
		CustomPatternEntry previewEntry = library.withPattern(
			selectedPatternId,
			draft,
			getBeatDurationMillis(),
			getBeatCount()
		).findById(selectedPatternId).orElse(null);
		if (previewEntry != null)
		{
			previewAction.accept(previewEntry);
		}
	}

	private void startAnimation()
	{
		stopAnimation();
		int beatDurationMillis = getBeatDurationMillis();
		long totalDurationMillis = (long) beatDurationMillis * getBeatCount();
		previewStartedAt = System.currentTimeMillis();
		canvas.setPlayheadProgress(0.0);
		playheadTimer = new Timer(30, event ->
		{
			long elapsedMillis = System.currentTimeMillis() - previewStartedAt;
			if (elapsedMillis >= totalDurationMillis)
			{
				stopAnimation();
			}
			else
			{
				double beatProgress = (double) (elapsedMillis % beatDurationMillis)
					/ beatDurationMillis;
				canvas.setPlayheadProgress(beatProgress);
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

	private int getBeatDurationMillis()
	{
		return ((Number) beatDurationSpinner.getValue()).intValue();
	}

	private int getBeatCount()
	{
		return ((Number) beatCountSpinner.getValue()).intValue();
	}

	private void updatePlaybackSummary()
	{
		long totalMillis = (long) getBeatDurationMillis() * getBeatCount();
		playbackSummaryLabel.setText("Total " + formatDuration(totalMillis));
	}

	private static String formatDuration(long durationMillis)
	{
		if (durationMillis < 1_000)
		{
			return durationMillis + " ms";
		}
		if (durationMillis % 1_000 == 0)
		{
			return (durationMillis / 1_000) + " s";
		}
		return String.format(Locale.ROOT, "%.1f s", durationMillis / 1_000.0);
	}

	private void setDirty(boolean dirty)
	{
		this.dirty = dirty;
		saveStateLabel.setText(dirty ? "Unsaved" : "Saved");
		refreshEditorState();
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
		private boolean editable = true;

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
					if (!editable)
					{
						return;
					}
					gestureStartAction.run();
					previousSampleIndex = -1;
					updateFromMouse(event);
				}

				@Override
				public void mouseDragged(MouseEvent event)
				{
					if (editable)
					{
						updateFromMouse(event);
					}
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

		private void setEditable(boolean editable)
		{
			this.editable = editable;
			setToolTipText(editable
				? "Click and drag to draw intensity over time"
				: "Remote-controlled pattern: view only");
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
