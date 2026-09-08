package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.CustomPattern;
import com.ashy0019.hapticscape.CustomPatternEntry;
import com.ashy0019.hapticscape.CustomPatternLibrary;
import com.ashy0019.hapticscape.HapticScapeSettingKeys;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
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
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

final class PatternForgePanel extends JPanel
{
	private static final int MAXIMUM_UNDO_STATES = 20;
	private static final int WIDE_BREAKPOINT = 1120;
	private static final int MEDIUM_BREAKPOINT = 720;

	private final SettingsChangeSink settingsSink;
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
	private final JLabel beatSummaryLabel = new JLabel("", SwingConstants.RIGHT);
	private final JLabel saveStateLabel = new JLabel("Saved", SwingConstants.RIGHT);
	private final PatternTimeline outputTimeline = new PatternTimeline();
	private final Deque<CustomPattern> undoStates = new ArrayDeque<>();
	private final JPanel layoutPanel = new JPanel(new GridBagLayout());

	private CustomPatternLibrary library;
	private int selectedPatternId = -1;
	private CustomPattern draft;
	private boolean loadingControls;
	private boolean dirty;
	private boolean connected;
	private boolean remoteReadOnly;
	private boolean previewAllowed = true;
	private Timer playheadTimer;
	private long previewStartedAt;
	private int layoutMode = -1;

	PatternForgePanel(
		CustomPatternLibrary library,
		SettingsChangeSink settingsSink,
		Consumer<CustomPatternEntry> previewAction,
		Consumer<CustomPatternLibrary> libraryChangeAction)
	{
		this.library = Objects.requireNonNull(library, "library");
		this.settingsSink = Objects.requireNonNull(settingsSink, "settingsSink");
		this.previewAction = Objects.requireNonNull(previewAction, "previewAction");
		this.libraryChangeAction = Objects.requireNonNull(
			libraryChangeAction,
			"libraryChangeAction"
		);

		setName("patternComposer");
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

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

		JPanel libraryPanel = new JPanel();
		libraryPanel.setName("patternLibrary");
		libraryPanel.setLayout(new BoxLayout(libraryPanel, BoxLayout.Y_AXIS));
		libraryPanel.setBorder(BorderFactory.createTitledBorder("Pattern library"));
		patternComboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE,
			patternComboBox.getPreferredSize().height));
		patternComboBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		libraryPanel.add(patternComboBox);
		JPanel patternButtons = new JPanel(new GridLayout(1, 3, 4, 0));
		configureCompactButton(addButton);
		configureCompactButton(renameButton);
		configureCompactButton(deleteButton);
		patternButtons.add(addButton);
		patternButtons.add(renameButton);
		patternButtons.add(deleteButton);
		patternButtons.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		patternButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE,
			patternButtons.getPreferredSize().height));
		patternButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
		libraryPanel.add(patternButtons);
		JLabel libraryHint = new JLabel("Select a reusable pattern to edit.");
		libraryHint.setBorder(BorderFactory.createEmptyBorder(5, 1, 0, 1));
		libraryHint.setAlignmentX(Component.LEFT_ALIGNMENT);
		libraryPanel.add(libraryHint);

		JPanel shapePanel = new JPanel(new BorderLayout(0, 5));
		shapePanel.setName("patternShapeEditor");
		shapePanel.setBorder(BorderFactory.createTitledBorder("Shape editor"));
		JPanel shapeHeading = new JPanel(new BorderLayout(8, 0));
		JLabel instructions = new JLabel("Draw intensity over one beat.");
		instructions.setToolTipText("Left to right is time; height is intensity");
		shapeHeading.add(instructions, BorderLayout.CENTER);
		beatSummaryLabel.setName("patternBeatSummary");
		shapeHeading.add(beatSummaryLabel, BorderLayout.EAST);
		shapePanel.add(shapeHeading, BorderLayout.NORTH);
		canvas.setName("patternCanvas");
		shapePanel.add(canvas, BorderLayout.CENTER);
		JPanel shapeFooter = new JPanel(new BorderLayout(0, 5));
		outputTimeline.setName("patternOutputTimeline");
		outputTimeline.setToolTipText(
			"Complete output timeline; repeated beats are grouped when the count is high"
		);
		shapeFooter.add(outputTimeline, BorderLayout.NORTH);
		JPanel drawingButtons = new JPanel(new GridLayout(1, 2, 4, 0));
		drawingButtons.add(undoButton);
		drawingButtons.add(clearButton);
		shapeFooter.add(drawingButtons, BorderLayout.SOUTH);
		shapePanel.add(shapeFooter, BorderLayout.SOUTH);

		configureCompactSpinner(beatDurationSpinner);
		configureCompactSpinner(beatCountSpinner);
		beatDurationSpinner.setName("patternBeatDuration");
		beatCountSpinner.setName("patternBeatCount");
		beatDurationSpinner.setToolTipText(
			"Length of one drawn beat, from 50 ms to 10 seconds"
		);
		beatCountSpinner.setToolTipText(
			"Number of times to repeat the drawn beat, from 1 to 72"
		);
		JPanel playbackPanel = new JPanel();
		playbackPanel.setName("patternPlayback");
		playbackPanel.setLayout(new BoxLayout(playbackPanel, BoxLayout.Y_AXIS));
		playbackPanel.setBorder(BorderFactory.createTitledBorder("Playback"));
		JPanel beatDurationRow = new JPanel(new BorderLayout(8, 0));
		beatDurationRow.add(new JLabel("Beat length (ms)"), BorderLayout.CENTER);
		beatDurationRow.add(beatDurationSpinner, BorderLayout.EAST);
		PanelUi.addVerticalComponent(playbackPanel, beatDurationRow);

		JPanel beatCountRow = new JPanel(new BorderLayout(8, 0));
		beatCountRow.add(new JLabel("Repeat"), BorderLayout.CENTER);
		beatCountRow.add(beatCountSpinner, BorderLayout.EAST);
		PanelUi.addVerticalComponent(playbackPanel, beatCountRow);

		JPanel saveStateRow = new JPanel(new BorderLayout());
		playbackSummaryLabel.setToolTipText(
			"Custom patterns save their own intensity, timing, and repetitions"
		);
		saveStateRow.add(playbackSummaryLabel, BorderLayout.WEST);
		saveStateRow.add(saveStateLabel, BorderLayout.EAST);
		saveStateRow.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
		PanelUi.addVerticalComponent(playbackPanel, saveStateRow);

		JPanel actionButtons = new JPanel(new GridLayout(1, 2, 4, 0));
		actionButtons.add(previewButton);
		actionButtons.add(saveButton);
		PanelUi.addVerticalComponent(playbackPanel, actionButtons);

		JPanel libraryHost = host(libraryPanel, 270);
		JPanel shapeHost = host(shapePanel, 540);
		JPanel playbackHost = host(playbackPanel, 270);
		add(layoutPanel, BorderLayout.NORTH);
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent event)
			{
				reflow(libraryHost, shapeHost, playbackHost);
			}
		});
		reflow(libraryHost, shapeHost, playbackHost);

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

	void setPreviewAllowed(boolean previewAllowed)
	{
		this.previewAllowed = previewAllowed;
		if (!previewAllowed)
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
		settingsSink.set(
			HapticScapeSettingKeys.CUSTOM_PATTERNS,
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
		previewButton.setEnabled(editable && connected && previewAllowed);
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
		outputTimeline.setPlayheadProgress(0.0);
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
				outputTimeline.setPlayheadProgress(
					(double) elapsedMillis / totalDurationMillis
				);
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
		outputTimeline.setPlayheadProgress(-1.0);
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
		int beatDurationMillis = getBeatDurationMillis();
		int beatCount = getBeatCount();
		long totalMillis = (long) beatDurationMillis * beatCount;
		beatSummaryLabel.setText("One beat · " + formatDuration(beatDurationMillis));
		playbackSummaryLabel.setText("Total " + formatDuration(totalMillis));
		canvas.setBeatDurationMillis(beatDurationMillis);
		outputTimeline.setPlayback(beatDurationMillis, beatCount);
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

	static String formatTimelineOffset(int durationMillis)
	{
		if (durationMillis <= 0)
		{
			return "0";
		}
		if (durationMillis < 1_000)
		{
			return durationMillis + " ms";
		}
		return BigDecimal.valueOf(durationMillis, 3)
			.stripTrailingZeros()
			.toPlainString() + " s";
	}

	private void setDirty(boolean dirty)
	{
		this.dirty = dirty;
		saveStateLabel.setText(dirty ? "Unsaved" : "Saved");
		refreshEditorState();
	}

	private void reflow(Component library, Component shape, Component playback)
	{
		int desired = layoutModeForWidth(getWidth());
		if (desired == layoutMode)
		{
			return;
		}
		layoutMode = desired;
		layoutPanel.removeAll();
		if (layoutMode == 3)
		{
			addSection(library, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.HORIZONTAL);
			addSection(shape, 1, 0, 1, 1, 1.0, 1.0, GridBagConstraints.BOTH);
			addSection(playback, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.HORIZONTAL);
		}
		else if (layoutMode == 2)
		{
			addSection(shape, 0, 0, 2, 1, 1.0, 1.0, GridBagConstraints.BOTH);
			addSection(library, 0, 1, 1, 1, 0.5, 0.0, GridBagConstraints.HORIZONTAL);
			addSection(playback, 1, 1, 1, 1, 0.5, 0.0, GridBagConstraints.HORIZONTAL);
		}
		else
		{
			addSection(library, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.HORIZONTAL);
			addSection(shape, 0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.BOTH);
			addSection(playback, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.HORIZONTAL);
		}
		layoutPanel.revalidate();
		layoutPanel.repaint();
	}

	static int layoutModeForWidth(int width)
	{
		return width >= WIDE_BREAKPOINT ? 3 : width >= MEDIUM_BREAKPOINT ? 2 : 1;
	}

	private void addSection(
		Component component,
		int x,
		int y,
		int width,
		int height,
		double weightX,
		double weightY,
		int fill)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = x;
		constraints.gridy = y;
		constraints.gridwidth = width;
		constraints.gridheight = height;
		constraints.weightx = weightX;
		constraints.weighty = weightY;
		constraints.fill = fill;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		constraints.insets = new Insets(0, 0, 7, 7);
		layoutPanel.add(component, constraints);
	}

	private static JPanel host(Component component, int preferredWidth)
	{
		JPanel host = new WidthHintPanel(preferredWidth);
		host.add(component, BorderLayout.CENTER);
		return host;
	}

	/** Supplies orderly desktop columns without fixing the editor's height. */
	private static final class WidthHintPanel extends JPanel
	{
		private final int preferredWidth;

		private WidthHintPanel(int preferredWidth)
		{
			super(new BorderLayout());
			this.preferredWidth = preferredWidth;
		}

		@Override
		public Dimension getPreferredSize()
		{
			Dimension preferred = super.getPreferredSize();
			return new Dimension(Math.max(preferredWidth, preferred.width), preferred.height);
		}
	}

	/** Compact overview of every repeated beat in the complete output. */
	private static final class PatternTimeline extends JPanel
	{
		private static final Color BACKGROUND = new Color(32, 34, 37);
		private static final Color GRID = new Color(91, 94, 98);
		private static final Color TEXT = new Color(190, 190, 190);
		private static final Color PLAYHEAD = new Color(255, 220, 90);
		private static final int MAXIMUM_VISIBLE_SEGMENTS = 12;

		private int beatDurationMillis = CustomPatternEntry.DEFAULT_BEAT_DURATION_MILLIS;
		private int beatCount = CustomPatternEntry.DEFAULT_BEAT_COUNT;
		private double playheadProgress = -1.0;

		private PatternTimeline()
		{
			setPreferredSize(new Dimension(0, 30));
			setMinimumSize(new Dimension(80, 30));
		}

		private void setPlayback(int durationMillis, int repeats)
		{
			beatDurationMillis = durationMillis;
			beatCount = Math.max(1, repeats);
			repaint();
		}

		private void setPlayheadProgress(double progress)
		{
			playheadProgress = progress < 0.0
				? -1.0
				: Math.max(0.0, Math.min(1.0, progress));
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D graphics2D = (Graphics2D) graphics.create();
			try
			{
				int left = 2;
				int top = 3;
				int width = Math.max(1, getWidth() - 4);
				int height = Math.max(1, getHeight() - 7);
				graphics2D.setColor(BACKGROUND);
				graphics2D.fillRect(left, top, width, height);
				graphics2D.setColor(GRID);
				graphics2D.drawRect(left, top, width - 1, height - 1);

				int visibleSegments = Math.min(beatCount, MAXIMUM_VISIBLE_SEGMENTS);
				for (int segment = 1; segment < visibleSegments; segment++)
				{
					int x = left + width * segment / visibleSegments;
					graphics2D.drawLine(x, top, x, top + height - 1);
				}

				graphics2D.setFont(getFont().deriveFont(10f));
				graphics2D.setColor(TEXT);
				String label = beatCount == 1
					? "1 beat · " + formatDuration(beatDurationMillis)
					: beatCount + " beats · "
						+ formatDuration((long) beatDurationMillis * beatCount);
				int labelWidth = graphics2D.getFontMetrics().stringWidth(label);
				graphics2D.drawString(
					label,
					Math.max(left + 4, left + (width - labelWidth) / 2),
					top + height - 6
				);

				if (playheadProgress >= 0.0)
				{
					int x = left + (int) Math.round((width - 1) * playheadProgress);
					graphics2D.setColor(PLAYHEAD);
					graphics2D.setStroke(new BasicStroke(2f));
					graphics2D.drawLine(x, top, x, top + height - 1);
				}
			}
			finally
			{
				graphics2D.dispose();
			}
		}
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
		private static final int BOTTOM = 25;

		private int[] samples = new int[CustomPattern.EDITOR_SAMPLE_COUNT];
		private Runnable gestureStartAction = () -> { };
		private Consumer<CustomPattern> patternChangeAction = ignored -> { };
		private double playheadProgress = -1.0;
		private int previousSampleIndex = -1;
		private int previousIntensity;
		private boolean editable = true;
		private int beatDurationMillis = CustomPatternEntry.DEFAULT_BEAT_DURATION_MILLIS;

		private PatternCanvas()
		{
			setPreferredSize(new Dimension(540, 280));
			setMinimumSize(new Dimension(180, 180));
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

		private void setBeatDurationMillis(int durationMillis)
		{
			beatDurationMillis = Math.max(1, durationMillis);
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
				int timeDivisions = getWidth() < 360 ? 2 : 4;
				for (int division = 0; division <= timeDivisions; division++)
				{
					int x = LEFT + graphWidth * division / timeDivisions;
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
				for (int division = 0; division <= timeDivisions; division++)
				{
					int offsetMillis = (int) Math.round(
						(double) beatDurationMillis * division / timeDivisions
					);
					String label = formatTimelineOffset(offsetMillis);
					int labelWidth = graphics2D.getFontMetrics().stringWidth(label);
					int tickX = LEFT + graphWidth * division / timeDivisions;
					int labelX = division == 0
						? tickX
						: division == timeDivisions
							? tickX - labelWidth
							: tickX - labelWidth / 2;
					graphics2D.drawString(label, labelX, getHeight() - 4);
				}

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
