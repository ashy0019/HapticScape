package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import com.ashy0019.hapticscape.remote.SavedUnlockKey;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Controller-owned UI for DPAPI-protected post-session unlock keys. */
final class SavedUnlockKeysPanel extends JPanel
{
	private final RemoteSessionManager sessionManager;
	private final JPanel entriesPanel = new JPanel();
	private final JLabel statusLabel = new JLabel();
	private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern(
		"MMM d, yyyy h:mm a"
	).withZone(ZoneId.systemDefault());

	SavedUnlockKeysPanel(RemoteSessionManager sessionManager)
	{
		this.sessionManager = sessionManager;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createTitledBorder("Saved Unlock Keys"));

		JTextArea explanation = new JTextArea(
			"Accepted post-session unlock keys are encrypted by Windows for your "
				+ "account. Invitation and session encryption keys are never saved.",
			3,
			24
		);
		explanation.setEditable(false);
		explanation.setOpaque(false);
		explanation.setFocusable(false);
		explanation.setLineWrap(true);
		explanation.setWrapStyleWord(true);
		PanelUi.addVerticalComponent(this, explanation);

		entriesPanel.setLayout(new BoxLayout(entriesPanel, BoxLayout.Y_AXIS));
		PanelUi.addVerticalComponent(this, entriesPanel);
		PanelUi.addVerticalComponent(this, statusLabel);
	}

	void refresh()
	{
		entriesPanel.removeAll();
		if (!sessionManager.isSavedUnlockKeyVaultAvailable())
		{
			statusLabel.setText(
				"<html>" + sessionManager.getSavedUnlockKeyVaultMessage() + "</html>"
			);
		}
		else
		{
			List<SavedUnlockKey> entries = sessionManager.getSavedUnlockKeys();
			if (entries.isEmpty())
			{
				statusLabel.setText("No accepted unlock keys saved");
			}
			else
			{
				statusLabel.setText(entries.size() + (entries.size() == 1
					? " saved key"
					: " saved keys"));
				for (SavedUnlockKey entry : entries)
				{
					PanelUi.addVerticalComponent(entriesPanel, entryRow(entry));
				}
			}
		}
		entriesPanel.revalidate();
		entriesPanel.repaint();
		revalidate();
		repaint();
	}

	private JPanel entryRow(SavedUnlockKey entry)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createEtchedBorder(),
			BorderFactory.createEmptyBorder(4, 4, 4, 4)
		));
		JLabel label = new JLabel(entry.getLabel());
		label.setToolTipText(entry.getNote().isEmpty() ? null : entry.getNote());
		PanelUi.addVerticalComponent(row, label);
		PanelUi.addVerticalComponent(
			row,
			new JLabel("Created " + dateFormat.format(entry.getCreatedAt()))
		);
		PanelUi.addVerticalComponent(row, new JLabel(entry.getLastUsedAt() == null
			? "Never copied"
			: "Last copied " + dateFormat.format(entry.getLastUsedAt())));

		JPanel buttons = new JPanel(new GridLayout(1, 3, 4, 0));
		JButton copy = new JButton("Copy");
		JButton edit = new JButton("Edit");
		JButton forget = new JButton("Forget");
		copy.addActionListener(event -> copy(entry));
		edit.addActionListener(event -> edit(entry));
		forget.addActionListener(event -> forget(entry));
		buttons.add(copy);
		buttons.add(edit);
		buttons.add(forget);
		PanelUi.addVerticalComponent(row, buttons);
		return row;
	}

	private void copy(SavedUnlockKey entry)
	{
		char[] key = null;
		try
		{
			key = sessionManager.revealSavedUnlockKey(entry.getId());
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
				new StringSelection(new String(key)),
				null
			);
			refresh();
			statusLabel.setText("Unlock key copied");
		}
		catch (RuntimeException e)
		{
			showError(e.getMessage());
		}
		finally
		{
			if (key != null)
			{
				Arrays.fill(key, '\0');
			}
		}
	}

	private void edit(SavedUnlockKey entry)
	{
		JTextField label = new JTextField(entry.getLabel());
		JTextArea note = new JTextArea(entry.getNote(), 3, 24);
		note.setLineWrap(true);
		note.setWrapStyleWord(true);
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		PanelUi.addVerticalComponent(content, labeledRow("Label", label));
		PanelUi.addVerticalComponent(content, new JLabel("Note (optional)"));
		PanelUi.addVerticalComponent(content, new JScrollPane(note));
		int choice = JOptionPane.showConfirmDialog(
			this,
			content,
			"Edit saved unlock key",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE
		);
		if (choice != JOptionPane.OK_OPTION)
		{
			return;
		}
		try
		{
			sessionManager.updateSavedUnlockKey(
				entry.getId(),
				label.getText(),
				note.getText()
			);
			refresh();
		}
		catch (RuntimeException e)
		{
			showError(e.getMessage());
		}
	}

	private void forget(SavedUnlockKey entry)
	{
		int choice = JOptionPane.showConfirmDialog(
			this,
			"Forget the saved unlock key for \"" + entry.getLabel() + "\"?",
			"Forget unlock key",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE
		);
		if (choice != JOptionPane.YES_OPTION)
		{
			return;
		}
		try
		{
			sessionManager.forgetSavedUnlockKey(entry.getId());
			refresh();
		}
		catch (RuntimeException e)
		{
			showError(e.getMessage());
		}
	}

	private void showError(String message)
	{
		JOptionPane.showMessageDialog(
			this,
			message == null ? "Saved Unlock Keys operation failed" : message,
			"Saved Unlock Keys",
			JOptionPane.ERROR_MESSAGE
		);
	}

	private static JPanel labeledRow(String name, java.awt.Component control)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.add(new JLabel(name), BorderLayout.WEST);
		row.add(control, BorderLayout.CENTER);
		return row;
	}
}
