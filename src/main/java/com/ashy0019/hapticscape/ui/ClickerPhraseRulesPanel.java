package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.clicker.ClickerPhraseMatchMode;
import com.ashy0019.hapticscape.clicker.ClickerPhraseRule;
import com.ashy0019.hapticscape.clicker.ClickerPhraseRules;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import net.runelite.client.config.ConfigManager;

final class ClickerPhraseRulesPanel extends JPanel
{
	private final ConfigManager configManager;
	private final DefaultListModel<ClickerPhraseRule> ruleModel =
		new DefaultListModel<>();
	private final JList<ClickerPhraseRule> ruleList =
		new JList<>(ruleModel);
	private final JButton addButton = new JButton("Add");
	private final JButton editButton = new JButton("Edit");
	private final JButton deleteButton = new JButton("Delete");

	private volatile ClickerPhraseRules rules;
	private boolean clickerEnabled;
	private boolean remoteReadOnly;

	ClickerPhraseRulesPanel(
		HapticScapeConfig config,
		ConfigManager configManager)
	{
		this.configManager = configManager;
		rules = ClickerPhraseRules.fromConfigValue(
			config.clickerPhraseRules()
		);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createTitledBorder("Phrase clicks"));

		JLabel description = new JLabel(
			"Click when a RuneLite chat message matches a local rule."
		);
		description.setToolTipText(
			"Contains and Exact ignore case. Regex uses Java regular expressions."
		);
		PanelUi.addVerticalComponent(this, description);

		ruleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JScrollPane scrollPane = new JScrollPane(ruleList);
		scrollPane.setPreferredSize(new Dimension(0, 115));
		scrollPane.setToolTipText(
			"Regex uses find(). Use ^ and $ for a whole-message match, "
				+ "or (?i) for case-insensitive regex."
		);
		PanelUi.addVerticalComponent(this, scrollPane);

		JPanel buttons = new JPanel(new GridLayout(1, 3, 4, 0));
		buttons.add(addButton);
		buttons.add(editButton);
		buttons.add(deleteButton);
		PanelUi.addVerticalComponent(this, buttons);

		ruleList.addListSelectionListener(event ->
		{
			if (!event.getValueIsAdjusting())
			{
				refreshEnabledState();
			}
		});
		addButton.addActionListener(event -> addRule());
		editButton.addActionListener(event -> editSelectedRule());
		deleteButton.addActionListener(event -> deleteSelectedRule());

		refreshModel();
	}

	ClickerPhraseRules getRules()
	{
		return rules;
	}

	void applyDisplayedRules(ClickerPhraseRules displayedRules)
	{
		rules = displayedRules;
		refreshModel();
	}

	void setClickerEnabled(boolean enabled)
	{
		clickerEnabled = enabled;
		refreshEnabledState();
	}

	void setRemoteReadOnly(boolean remoteReadOnly)
	{
		this.remoteReadOnly = remoteReadOnly;
		refreshEnabledState();
	}

	private void addRule()
	{
		if (rules.getRules().size() >= ClickerPhraseRules.MAXIMUM_RULES)
		{
			JOptionPane.showMessageDialog(
				this,
				"Maximum of "
					+ ClickerPhraseRules.MAXIMUM_RULES
					+ " phrase rules reached.",
				"Phrase rules",
				JOptionPane.WARNING_MESSAGE
			);
			return;
		}

		ClickerPhraseRule rule = showRuleEditor(null);
		if (rule == null)
		{
			return;
		}

		rules = rules.withAdded(rule);
		persist();
		refreshModel();
		ruleList.setSelectedIndex(ruleModel.size() - 1);
	}

	private void editSelectedRule()
	{
		int index = ruleList.getSelectedIndex();
		if (index < 0)
		{
			return;
		}

		ClickerPhraseRule updated = showRuleEditor(
			rules.getRules().get(index)
		);
		if (updated == null)
		{
			return;
		}

		rules = rules.withReplaced(index, updated);
		persist();
		refreshModel();
		ruleList.setSelectedIndex(index);
	}

	private void deleteSelectedRule()
	{
		int index = ruleList.getSelectedIndex();
		if (index < 0)
		{
			return;
		}

		rules = rules.withRemoved(index);
		persist();
		refreshModel();
	}

	private ClickerPhraseRule showRuleEditor(ClickerPhraseRule existing)
	{
		JCheckBox enabled = new JCheckBox(
			"Enabled",
			existing == null || existing.isEnabled()
		);

		JComboBox<ClickerPhraseMatchMode> mode =
			new JComboBox<>(ClickerPhraseMatchMode.values());
		if (existing != null)
		{
			mode.setSelectedItem(existing.getMode());
		}

		JTextArea expression = new JTextArea(4, 24);
		expression.setLineWrap(false);
		if (existing != null)
		{
			expression.setText(existing.getExpression());
		}

		JScrollPane expressionScroll = new JScrollPane(expression);
		expressionScroll.setPreferredSize(new Dimension(280, 85));

		JLabel hint = new JLabel(
			"Contains/Exact ignore case; Regex uses Java syntax."
		);
		hint.setToolTipText(
			"Regex uses find(). Add ^...$ for whole-message matching "
				+ "or (?i) for case-insensitive matching."
		);

		JPanel editor = new JPanel();
		editor.setLayout(new BoxLayout(editor, BoxLayout.Y_AXIS));
		PanelUi.addVerticalComponent(editor, enabled);
		PanelUi.addVerticalComponent(editor, row("Match", mode));
		PanelUi.addVerticalComponent(editor, new JLabel("Phrase / regex"));
		PanelUi.addVerticalComponent(editor, expressionScroll);
		PanelUi.addVerticalComponent(editor, hint);

		while (true)
		{
			int result = JOptionPane.showConfirmDialog(
				this,
				editor,
				existing == null
					? "Add phrase rule"
					: "Edit phrase rule",
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE
			);

			if (result != JOptionPane.OK_OPTION)
			{
				return null;
			}

			try
			{
				return new ClickerPhraseRule(
					enabled.isSelected(),
					(ClickerPhraseMatchMode) mode.getSelectedItem(),
					expression.getText()
				);
			}
			catch (IllegalArgumentException exception)
			{
				JOptionPane.showMessageDialog(
					this,
					exception.getMessage(),
					"Invalid phrase rule",
					JOptionPane.ERROR_MESSAGE
				);
			}
		}
	}

	private void refreshModel()
	{
		int selectedIndex = ruleList.getSelectedIndex();
		ruleModel.clear();

		for (ClickerPhraseRule rule : rules.getRules())
		{
			ruleModel.addElement(rule);
		}

		if (!ruleModel.isEmpty())
		{
			ruleList.setSelectedIndex(
				Math.min(
					Math.max(selectedIndex, 0),
					ruleModel.size() - 1
				)
			);
		}

		refreshEnabledState();
	}

	private void refreshEnabledState()
	{
		boolean selected = ruleList.getSelectedIndex() >= 0;
		// The list remains browsable while remote-controlled; only mutations lock.
		ruleList.setEnabled(remoteReadOnly || clickerEnabled);
		addButton.setEnabled(
			!remoteReadOnly
				&& clickerEnabled
				&& rules.getRules().size()
					< ClickerPhraseRules.MAXIMUM_RULES
		);
		editButton.setEnabled(!remoteReadOnly && clickerEnabled && selected);
		deleteButton.setEnabled(!remoteReadOnly && clickerEnabled && selected);
	}

	private void persist()
	{
		configManager.setConfiguration(
			HapticScapeConfig.GROUP,
			HapticScapeConfig.CLICKER_PHRASE_RULES_KEY,
			rules.toConfigValue()
		);
	}

	private static JPanel row(String name, java.awt.Component control)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.add(new JLabel(name), BorderLayout.CENTER);
		row.add(control, BorderLayout.EAST);
		return row;
	}
}