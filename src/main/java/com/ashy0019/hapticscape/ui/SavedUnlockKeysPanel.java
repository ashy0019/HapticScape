package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import com.ashy0019.hapticscape.remote.SavedUnlockKey;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
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
	private final SidebarTextLabel statusText = new SidebarTextLabel("");
	private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern(
		"MMM d, yyyy h:mm a"
	).withZone(ZoneId.systemDefault());

	SavedUnlockKeysPanel(RemoteSessionManager sessionManager)
	{
		this.sessionManager = sessionManager;
		setLayout(new BorderLayout(0, 0));
		setBorder(BorderFactory.createTitledBorder("Saved Unlock Keys"));
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

		SidebarTextLabel explanation = new SidebarTextLabel(
			"Accepted unlock keys are protected by Windows for this account. "
				+ "Invitations are never saved."
		);
		explanation.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 2));
		PanelUi.addVerticalComponent(header, explanation);

		statusText.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 2));
		PanelUi.addVerticalComponent(header, statusText);
		add(header, BorderLayout.NORTH);
		entriesPanel.setLayout(new GridBagLayout());
		entriesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		entriesPanel.setMinimumSize(new Dimension(0, 0));
		entriesPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		add(entriesPanel, BorderLayout.CENTER);
	}

	void refresh()
	{
		entriesPanel.removeAll();
		if (!sessionManager.isSavedUnlockKeyVaultAvailable())
		{
			statusText.setPlainText(sessionManager.getSavedUnlockKeyVaultMessage());
		}
		else
		{
			List<SavedUnlockKey> entries = sessionManager.getSavedUnlockKeys();
			if (entries.isEmpty())
			{
				statusText.setPlainText("No accepted unlock keys saved");
			}
			else
			{
				statusText.setPlainText(entries.size() + (entries.size() == 1
					? " key saved"
					: " keys saved"));
				for (int index = 0; index < entries.size(); index++)
				{
					GridBagConstraints constraints = new GridBagConstraints();
					constraints.gridx = 0;
					constraints.gridy = index;
					constraints.weightx = 1.0;
					constraints.fill = GridBagConstraints.HORIZONTAL;
					constraints.anchor = GridBagConstraints.NORTHWEST;
					constraints.insets = new Insets(index == 0 ? 0 : 6, 0, 0, 0);
					entriesPanel.add(entryRow(entries.get(index)), constraints);
				}
			}
		}
		updateDynamicSizeConstraints();
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
		SidebarTextLabel label = new SidebarTextLabel(entry.getLabel());
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		label.setPlainText(entry.getLabel());
		label.setToolTipText(entry.getNote().isEmpty() ? null : entry.getNote());
		allowHorizontalShrink(label);
		PanelUi.addVerticalComponent(row, label);
		JLabel created = metadataLabel(
			"Created " + dateFormat.format(entry.getCreatedAt())
		);
		JLabel lastUsed = metadataLabel(entry.getLastUsedAt() == null
			? "Not copied yet"
			: "Copied " + dateFormat.format(entry.getLastUsedAt()));
		PanelUi.addVerticalComponent(row, created);
		PanelUi.addVerticalComponent(row, lastUsed);

		JButton copy = new JButton("Copy key");
		JButton edit = new JButton("Edit");
		JButton forget = new JButton("Forget");
		configureCompactButton(copy);
		configureCompactButton(edit);
		configureCompactButton(forget);
		copy.addActionListener(event -> copy(entry));
		edit.addActionListener(event -> edit(entry));
		forget.addActionListener(event -> forget(entry));
		PanelUi.addVerticalComponent(row, copy);
		JPanel secondaryActions = new JPanel(new GridLayout(1, 2, 4, 0));
		secondaryActions.setMinimumSize(new Dimension(0, edit.getPreferredSize().height));
		secondaryActions.add(edit);
		secondaryActions.add(forget);
		PanelUi.addVerticalComponent(row, secondaryActions);
		Dimension preferred = row.getPreferredSize();
		row.setMinimumSize(new Dimension(0, preferred.height));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
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
			statusText.setPlainText("Unlock key copied");
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

	private void updateDynamicSizeConstraints()
	{
		Dimension entriesSize = entriesPanel.getPreferredSize();
		entriesPanel.setMinimumSize(new Dimension(0, entriesSize.height));
		entriesPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, entriesSize.height));
		Dimension panelSize = getPreferredSize();
		setMinimumSize(new Dimension(0, panelSize.height));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, panelSize.height));
	}

	private static JLabel metadataLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(Font.PLAIN, 10f));
		allowHorizontalShrink(label);
		return label;
	}

	private static void configureCompactButton(JButton button)
	{
		button.setMargin(new Insets(2, 6, 2, 6));
		button.setFocusable(false);
		allowHorizontalShrink(button);
	}

	private static void allowHorizontalShrink(JComponent component)
	{
		Dimension preferred = component.getPreferredSize();
		component.setMinimumSize(new Dimension(0, preferred.height));
	}
}
