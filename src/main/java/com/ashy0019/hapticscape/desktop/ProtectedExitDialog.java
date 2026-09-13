package com.ashy0019.hapticscape.desktop;

import com.ashy0019.hapticscape.remote.SettingsLockCatalog;
import com.ashy0019.hapticscape.remote.SettingsLockService;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.Arrays;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.Timer;

/** Consent-preserving exit prompt shown while application exit is locked. */
final class ProtectedExitDialog extends JDialog
{
	static final int BYPASS_DELAY_SECONDS = 10;

	private final SettingsLockService lockService;
	private final Runnable authorizedExit;
	private final Runnable unauthorizedExit;
	private final Runnable emergencyOff;
	private final JPasswordField password = new JPasswordField(22);
	private final JLabel status = new JLabel(" ");
	private final JButton bypass = new JButton();
	private final long openedAtMillis = System.currentTimeMillis();
	private final Timer timer;

	ProtectedExitDialog(
		Window owner,
		SettingsLockService lockService,
		Runnable authorizedExit,
		Runnable unauthorizedExit,
		Runnable emergencyOff)
	{
		super(owner, "Protected exit", Dialog.ModalityType.APPLICATION_MODAL);
		this.lockService = Objects.requireNonNull(lockService, "lockService");
		this.authorizedExit = Objects.requireNonNull(authorizedExit, "authorizedExit");
		this.unauthorizedExit = Objects.requireNonNull(unauthorizedExit, "unauthorizedExit");
		this.emergencyOff = Objects.requireNonNull(emergencyOff, "emergencyOff");

		JPanel content = new JPanel(new BorderLayout(0, 10));
		content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		content.add(new JLabel(
			"Application exit is protected. The controller password releases this lock profile."
		), BorderLayout.NORTH);

		JPanel passwordRow = new JPanel(new BorderLayout(8, 0));
		passwordRow.add(new JLabel("Password"), BorderLayout.WEST);
		passwordRow.add(password, BorderLayout.CENTER);
		content.add(passwordRow, BorderLayout.CENTER);

		JPanel footer = new JPanel(new BorderLayout(0, 8));
		footer.add(status, BorderLayout.NORTH);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		JButton cancel = new JButton("Cancel");
		JButton emergency = new JButton("Emergency Off");
		JButton authorized = new JButton("Unlock settings and exit");
		buttons.add(emergency);
		buttons.add(cancel);
		buttons.add(bypass);
		buttons.add(authorized);
		footer.add(buttons, BorderLayout.SOUTH);
		content.add(footer, BorderLayout.SOUTH);
		setContentPane(content);

		authorized.addActionListener(event -> authorize());
		password.addActionListener(event -> authorize());
		cancel.addActionListener(event -> dispose());
		emergency.addActionListener(event -> emergencyOff.run());
		bypass.addActionListener(event ->
		{
			if (remainingSeconds(System.currentTimeMillis() - openedAtMillis) == 0)
			{
				finish(unauthorizedExit);
			}
		});

		timer = new Timer(200, event -> refreshBypass());
		timer.start();
		refreshBypass();
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
		setResizable(false);
		setLocationRelativeTo(owner);
	}

	private void authorize()
	{
		char[] entered = password.getPassword();
		try
		{
			if (lockService.unlock(SettingsLockCatalog.PROTECTED_EXIT, entered))
			{
				finish(authorizedExit);
				return;
			}
			status.setText("Password not accepted.");
			password.selectAll();
		}
		catch (RuntimeException failure)
		{
			status.setText("Could not release the settings lock.");
			password.selectAll();
		}
		finally
		{
			Arrays.fill(entered, '\0');
		}
	}

	private void refreshBypass()
	{
		int remaining = remainingSeconds(System.currentTimeMillis() - openedAtMillis);
		bypass.setEnabled(remaining == 0);
		bypass.setText(remaining == 0
			? "Exit without unlocking"
			: "Exit without unlocking (" + remaining + "s)");
	}

	private void finish(Runnable action)
	{
		timer.stop();
		dispose();
		action.run();
	}

	static int remainingSeconds(long elapsedMillis)
	{
		long elapsedSeconds = Math.max(0L, elapsedMillis) / 1_000L;
		return (int) Math.max(0L, BYPASS_DELAY_SECONDS - elapsedSeconds);
	}

	@Override
	public void dispose()
	{
		if (timer != null)
		{
			timer.stop();
		}
		super.dispose();
	}
}
