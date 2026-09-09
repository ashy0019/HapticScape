package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.host.GlobalUiHooks;
import com.ashy0019.hapticscape.remote.RemotePermissions;
import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/** Controller-only temporal drawing surface for continuous remote haptics. */
final class RemoteLiveForgePanel extends JPanel
{
	static final int SAMPLE_INTERVAL_MILLIS = 50;
	static final int RELEASE_DECAY_MILLIS = 300;
	private static final int WIDE_BREAKPOINT = 800;
	private static final int WARNING_HEIGHT = 44;

	private final LiveDispatcher dispatcher;
	private final JLabel permissionLabel = new JLabel("Not allowed", SwingConstants.RIGHT);
	private final LiveCanvas canvas = new LiveCanvas();
	private final JLabel limitLabel = new JLabel();
	private final JProgressBar limitBar = new JProgressBar(0, 100);
	private final JButton stopButton = new JButton("Stop Output");
	private final WrappedTextLabel warning = new WrappedTextLabel("");
	private final Timer sampleTimer = new Timer(SAMPLE_INTERVAL_MILLIS, event -> sample());
	private final JPanel layoutPanel = new JPanel(new GridBagLayout());

	private RemoteSessionSnapshot session = RemoteSessionSnapshot.local();
	private RemotePermissions permissions = RemotePermissions.defaults();
	private boolean streaming;
	private boolean workspaceActive;
	private int requestedIntensity;
	private long gestureStartedAtMillis;
	private int layoutMode = -1;

	private final GlobalUiHooks.Registration gestureSafetyHook;

	RemoteLiveForgePanel(RemoteSessionManager sessionManager, GlobalUiHooks globalUiHooks)
	{
		this(new LiveDispatcher()
		{
			@Override
			public void begin(int intensityPercent)
			{
				sessionManager.beginRemoteLiveHaptic(intensityPercent);
			}

			@Override
			public void update(int intensityPercent)
			{
				sessionManager.updateRemoteLiveHaptic(intensityPercent);
			}

			@Override
			public void end()
			{
				sessionManager.endRemoteLiveHaptic();
			}

			@Override
			public void stop()
			{
				sessionManager.stopRemoteOutput();
			}
		}, globalUiHooks);
		apply(sessionManager.getSnapshot(), sessionManager.getPeerPermissions());
	}

	RemoteLiveForgePanel(LiveDispatcher dispatcher)
	{
		this(dispatcher, GlobalUiHooks.noop());
	}

	RemoteLiveForgePanel(LiveDispatcher dispatcher, GlobalUiHooks globalUiHooks)
	{
		this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
		Objects.requireNonNull(globalUiHooks, "globalUiHooks");
		setName("remoteLiveForgePanel");
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		JPanel sessionPanel = new JPanel();
		sessionPanel.setName("remoteLiveSession");
		sessionPanel.setLayout(new BoxLayout(sessionPanel, BoxLayout.Y_AXIS));
		sessionPanel.setBorder(PanelUi.createSectionBorder("Session limits"));
		JPanel heading = new JPanel(new BorderLayout(6, 0));
		heading.add(new JLabel("Permission"), BorderLayout.WEST);
		permissionLabel.setName("remoteLivePermission");
		permissionLabel.setPreferredSize(new Dimension(82, permissionLabel.getPreferredSize().height));
		heading.add(permissionLabel, BorderLayout.EAST);
		allowHorizontalShrink(heading);
		PanelUi.addPreferredHeightComponent(sessionPanel, heading);

		JPanel controlPanel = new JPanel(new BorderLayout(0, 5));
		controlPanel.setName("remoteLiveControl");
		controlPanel.setBorder(PanelUi.createSectionBorder("Live control"));
		WrappedTextLabel explanation = new WrappedTextLabel(
			"Hold and move inside the graph. Height controls intensity while time moves continuously."
		);
		explanation.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
		controlPanel.add(explanation, BorderLayout.NORTH);

		canvas.setName("remoteLiveCanvas");
		canvas.setPreferredSize(new Dimension(680, 300));
		canvas.setMinimumSize(new Dimension(180, 180));
		canvas.setBorder(BorderFactory.createLineBorder(new Color(91, 74, 49)));
		controlPanel.add(canvas, BorderLayout.CENTER);

		WrappedTextLabel releaseHelp = new WrappedTextLabel(
			"Release to fade smoothly to zero. Leaving Live ends the gesture."
		);
		releaseHelp.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
		controlPanel.add(releaseHelp, BorderLayout.SOUTH);

		JPanel limitPanel = new JPanel(new BorderLayout(0, 4));
		limitPanel.setName("remoteLiveLimitPanel");
		limitLabel.setName("remoteLiveLimit");
		limitLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		limitPanel.add(limitLabel, BorderLayout.CENTER);
		limitBar.setName("remoteLiveLimitBar");
		limitBar.setStringPainted(false);
		limitBar.setPreferredSize(new Dimension(0, 6));
		limitBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
		limitPanel.add(limitBar, BorderLayout.SOUTH);
		int limitPanelHeight = Math.max(30, limitPanel.getPreferredSize().height);
		limitPanel.setPreferredSize(new Dimension(0, limitPanelHeight));
		limitPanel.setMinimumSize(new Dimension(0, limitPanelHeight));
		limitPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, limitPanelHeight));
		PanelUi.addPreferredHeightComponent(sessionPanel, limitPanel);

		stopButton.setName("remoteLiveStop");
		stopButton.setText("Stop output");
		stopButton.setToolTipText("Stop all remote haptic output immediately");
		stopButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		stopButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, stopButton.getPreferredSize().height));
		PanelUi.addPreferredHeightComponent(sessionPanel, stopButton);

		warning.setName("remoteLiveWarning");
		warning.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
		warning.setPreferredSize(new Dimension(180, WARNING_HEIGHT));
		warning.setMinimumSize(new Dimension(0, WARNING_HEIGHT));
		warning.setMaximumSize(new Dimension(Integer.MAX_VALUE, WARNING_HEIGHT));
		PanelUi.addPreferredHeightComponent(sessionPanel, warning);

		JPanel controlHost = host(controlPanel, 620);
		JPanel sessionHost = host(sessionPanel, 270);
		add(layoutPanel, BorderLayout.NORTH);
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent event)
			{
				reflow(controlHost, sessionHost);
			}
		});
		reflow(controlHost, sessionHost);

		MouseAdapter drawing = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				beginGesture(event);
			}

			@Override
			public void mouseDragged(MouseEvent event)
			{
				if (streaming)
				{
					requestedIntensity = intensityFrom(event);
					canvas.setHeldIntensity(requestedIntensity);
				}
			}

			@Override
			public void mouseReleased(MouseEvent event)
			{
				endGesture();
			}
		};
		canvas.addMouseListener(drawing);
		canvas.addMouseMotionListener(drawing);
		stopButton.addActionListener(event -> stopImmediately());
		sampleTimer.setCoalesce(true);
		gestureSafetyHook = globalUiHooks.onGestureEnd(this::endGesture);
		apply(RemoteSessionSnapshot.local(), RemotePermissions.defaults());
	}

	void apply(RemoteSessionSnapshot updatedSession, RemotePermissions updatedPermissions)
	{
		session = Objects.requireNonNull(updatedSession, "updatedSession");
		permissions = Objects.requireNonNull(updatedPermissions, "updatedPermissions");
		boolean controller = session.getRole() == RemoteRole.CONTROLLER
			&& session.getState() != RemoteSessionState.LOCAL;
		boolean connected = session.getState() == RemoteSessionState.ACTIVE
			|| session.getState() == RemoteSessionState.PEER_EMERGENCY_PAUSED;
		boolean allowed = permissions.isLiveHapticsAllowed()
			&& permissions.getMaximumIntensityPercent() > 0;
		boolean active = controller
			&& session.getState() == RemoteSessionState.ACTIVE
			&& allowed;
		permissionLabel.setText(allowed ? "Allowed" : "Not allowed");
		canvas.setInputEnabled(active && workspaceActive);
		stopButton.setEnabled(controller && connected);
		limitBar.setValue(permissions.getMaximumIntensityPercent());
		limitLabel.setText(
			"Limit: " + permissions.getMaximumIntensityPercent()
				+ "% / " + formatSeconds(permissions.getMaximumLiveDurationMillis())
		);
		canvas.setMaximumIntensity(permissions.getMaximumIntensityPercent());

		if (!active)
		{
			endGesture();
			sampleTimer.stop();
			canvas.stopImmediately();
		}
		if (session.getState() == RemoteSessionState.PEER_EMERGENCY_PAUSED)
		{
			setWarning("Emergency Off is active");
		}
		else if (!permissions.isLiveHapticsAllowed() && controller && connected)
		{
			setWarning("Participant has not enabled Live Forge control");
		}
		else if (permissions.getMaximumIntensityPercent() == 0 && controller && connected)
		{
			setWarning("Participant maximum intensity is 0%");
		}
		else
		{
			setWarning("");
		}

		if (!controller || !connected || !workspaceActive)
		{
			sampleTimer.stop();
			canvas.clear();
		}
		revalidate();
		repaint();
	}

	void close()
	{
		setWorkspaceActive(false);
		gestureSafetyHook.close();
	}

	void endGestureForNavigation()
	{
		endGesture();
	}

	void setWorkspaceActive(boolean active)
	{
		workspaceActive = active;
		boolean inputAvailable = active
			&& session.getRole() == RemoteRole.CONTROLLER
			&& session.getState() == RemoteSessionState.ACTIVE
			&& permissions.isLiveHapticsAllowed()
			&& permissions.getMaximumIntensityPercent() > 0;
		canvas.setInputEnabled(inputAvailable);
		if (!active)
		{
			endGesture();
			sampleTimer.stop();
			canvas.clear();
		}
	}

	private void beginGesture(MouseEvent event)
	{
		if (!canvas.isInputEnabled() || streaming)
		{
			return;
		}
		requestedIntensity = intensityFrom(event);
		try
		{
			dispatcher.begin(requestedIntensity);
			streaming = true;
			gestureStartedAtMillis = System.currentTimeMillis();
			canvas.setHeldIntensity(requestedIntensity);
			if (!sampleTimer.isRunning())
			{
				sampleTimer.start();
			}
			setWarning("");
		}
		catch (RuntimeException failure)
		{
			streaming = false;
			canvas.release();
			setWarning(messageFor(failure));
		}
	}

	private void sample()
	{
		long now = System.currentTimeMillis();
		if (streaming)
		{
			if (now - gestureStartedAtMillis >= permissions.getMaximumLiveDurationMillis())
			{
				endGesture();
			}
			else
			{
				try
				{
					dispatcher.update(requestedIntensity);
				}
				catch (RuntimeException failure)
				{
					streaming = false;
					canvas.release();
					setWarning(messageFor(failure));
				}
			}
		}
		canvas.advance(now);
		if (!streaming && !canvas.needsAnimation())
		{
			sampleTimer.stop();
		}
	}

	private void endGesture()
	{
		if (!streaming)
		{
			return;
		}
		streaming = false;
		try
		{
			dispatcher.end();
		}
		catch (RuntimeException failure)
		{
			setWarning(messageFor(failure));
		}
		canvas.release();
	}

	private void stopImmediately()
	{
		streaming = false;
		sampleTimer.stop();
		canvas.stopImmediately();
		try
		{
			dispatcher.stop();
			if (session.getState() == RemoteSessionState.ACTIVE)
			{
				setWarning("");
			}
		}
		catch (RuntimeException failure)
		{
			setWarning(messageFor(failure));
		}
	}

	boolean isSampling()
	{
		return sampleTimer.isRunning();
	}

	private int intensityFrom(MouseEvent event)
	{
		int graphHeight = Math.max(1, canvas.getHeight());
		int requested = (int) Math.round(
			100.0 * (graphHeight - event.getY()) / graphHeight
		);
		return Math.max(
			0,
			Math.min(requested, permissions.getMaximumIntensityPercent())
		);
	}

	private void setWarning(String text)
	{
		warning.setPlainText(text == null ? "" : text);
		warning.setPreferredSize(new Dimension(180, WARNING_HEIGHT));
		warning.setMinimumSize(new Dimension(0, WARNING_HEIGHT));
		warning.setMaximumSize(new Dimension(Integer.MAX_VALUE, WARNING_HEIGHT));
	}

	private static String messageFor(RuntimeException failure)
	{
		String message = failure.getMessage();
		return message == null || message.trim().isEmpty()
			? "Live haptic control failed"
			: message;
	}

	private static String formatSeconds(int millis)
	{
		return Math.max(1, millis / 1_000) + " s";
	}

	private static void allowHorizontalShrink(javax.swing.JComponent component)
	{
		Dimension preferred = component.getPreferredSize();
		component.setMinimumSize(new Dimension(0, preferred.height));
	}

	private void reflow(Component control, Component sessionControls)
	{
		int desired = layoutModeForWidth(getWidth());
		if (desired == layoutMode)
		{
			return;
		}
		layoutMode = desired;
		layoutPanel.removeAll();
		if (layoutMode == 2)
		{
			addSection(control, 0, 0, 1.0, 1.0, GridBagConstraints.BOTH);
			addSection(sessionControls, 1, 0, 0.0, 0.0, GridBagConstraints.HORIZONTAL);
		}
		else
		{
			addSection(control, 0, 0, 1.0, 1.0, GridBagConstraints.BOTH);
			addSection(sessionControls, 0, 1, 1.0, 0.0, GridBagConstraints.HORIZONTAL);
		}
		layoutPanel.revalidate();
		layoutPanel.repaint();
	}

	static int layoutModeForWidth(int width)
	{
		return width >= WIDE_BREAKPOINT ? 2 : 1;
	}

	private void addSection(
		Component component,
		int x,
		int y,
		double weightX,
		double weightY,
		int fill)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = x;
		constraints.gridy = y;
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

	interface LiveDispatcher
	{
		void begin(int intensityPercent);

		void update(int intensityPercent);

		void end();

		void stop();
	}

	private static final class LiveCanvas extends JPanel
	{
		private static final Color BACKGROUND = new Color(32, 34, 37);
		private static final Color GRID = new Color(72, 75, 79);
		private static final Color CURVE = new Color(255, 152, 31);
		private static final Color FILL = new Color(30, 135, 125, 95);
		private static final Color CAP = new Color(190, 132, 52);
		private static final Color TEXT = new Color(190, 190, 190);
		private static final long HISTORY_MILLIS = 4_000;

		private final Deque<Sample> samples = new ArrayDeque<>();
		private boolean inputEnabled;
		private boolean held;
		private int maximumIntensity = 100;
		private double displayedIntensity;
		private double releaseStartingIntensity;
		private long releaseStartedAtMillis;

		private LiveCanvas()
		{
			setToolTipText("Hold and drag vertically to control live intensity");
		}

		private boolean isInputEnabled()
		{
			return inputEnabled;
		}

		private void setInputEnabled(boolean enabled)
		{
			inputEnabled = enabled;
			setEnabled(enabled);
			setToolTipText(enabled
				? "Hold and drag vertically to control live intensity"
				: "Live Forge is unavailable for this session");
		}

		private void setMaximumIntensity(int maximum)
		{
			maximumIntensity = Math.max(0, Math.min(100, maximum));
			displayedIntensity = Math.min(displayedIntensity, maximumIntensity);
			repaint();
		}

		private void setHeldIntensity(int intensity)
		{
			held = true;
			displayedIntensity = Math.max(0, Math.min(maximumIntensity, intensity));
			repaint();
		}

		private void release()
		{
			held = false;
			releaseStartingIntensity = displayedIntensity;
			releaseStartedAtMillis = System.currentTimeMillis();
		}

		private void stopImmediately()
		{
			held = false;
			displayedIntensity = 0;
			releaseStartingIntensity = 0;
			releaseStartedAtMillis = 0;
			repaint();
		}

		private void advance(long now)
		{
			if (!held && displayedIntensity > 0 && releaseStartedAtMillis > 0)
			{
				double progress = Math.min(
					1.0,
					(double) (now - releaseStartedAtMillis) / RELEASE_DECAY_MILLIS
				);
				displayedIntensity = releaseStartingIntensity * (1.0 - progress);
			}
			samples.addLast(new Sample(now, displayedIntensity));
			while (!samples.isEmpty() && now - samples.peekFirst().timeMillis > HISTORY_MILLIS)
			{
				samples.removeFirst();
			}
			repaint();
		}

		private boolean needsAnimation()
		{
			return held || displayedIntensity > 0;
		}

		private void clear()
		{
			samples.clear();
			stopImmediately();
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
				paintGrid(graphics2D);
				paintCap(graphics2D);
				paintTrace(graphics2D);
				paintLabels(graphics2D);
			}
			finally
			{
				graphics2D.dispose();
			}
		}

		private void paintGrid(Graphics2D graphics)
		{
			graphics.setColor(GRID);
			graphics.setStroke(new BasicStroke(1f));
			for (int division = 1; division < 4; division++)
			{
				int x = getWidth() * division / 4;
				graphics.drawLine(x, 0, x, getHeight());
			}
			for (int division = 1; division < 3; division++)
			{
				int y = getHeight() * division / 2;
				graphics.drawLine(0, y, getWidth(), y);
			}
		}

		private void paintCap(Graphics2D graphics)
		{
			int y = yFor(maximumIntensity);
			graphics.setColor(CAP);
			graphics.setStroke(new BasicStroke(1f));
			graphics.drawLine(0, y, getWidth(), y);
		}

		private void paintTrace(Graphics2D graphics)
		{
			if (samples.isEmpty())
			{
				return;
			}
			long now = samples.peekLast().timeMillis;
			int count = samples.size();
			int[] x = new int[count + 2];
			int[] y = new int[count + 2];
			x[0] = 0;
			y[0] = getHeight();
			int index = 1;
			for (Sample sample : samples)
			{
				x[index] = getWidth() - (int) Math.round(
					(double) (now - sample.timeMillis) * getWidth() / HISTORY_MILLIS
				);
				y[index] = yFor(sample.intensity);
				index++;
			}
			x[index] = getWidth();
			y[index] = getHeight();
			graphics.setColor(FILL);
			graphics.fill(new Polygon(x, y, x.length));
			graphics.setColor(CURVE);
			graphics.setStroke(new BasicStroke(2.5f));
			for (int point = 1; point < count; point++)
			{
				graphics.drawLine(x[point], y[point], x[point + 1], y[point + 1]);
			}
		}

		private void paintLabels(Graphics2D graphics)
		{
			graphics.setFont(getFont().deriveFont(Font.PLAIN, 11f));
			graphics.setColor(TEXT);
			graphics.drawString("100", 4, 13);
			graphics.drawString("50", 7, getHeight() / 2 + 4);
			graphics.drawString("0", 12, getHeight() - 5);
			String intensity = String.format("%3d%%", (int) Math.round(displayedIntensity));
			int width = graphics.getFontMetrics().stringWidth(intensity);
			graphics.setColor(held ? new Color(255, 209, 102) : TEXT);
			graphics.drawString(intensity, getWidth() - width - 7, 13);
		}

		private int yFor(double intensity)
		{
			return (int) Math.round(getHeight() * (1.0 - intensity / 100.0));
		}

		private static final class Sample
		{
			private final long timeMillis;
			private final double intensity;

			private Sample(long timeMillis, double intensity)
			{
				this.timeMillis = timeMillis;
				this.intensity = intensity;
			}
		}
	}
}
