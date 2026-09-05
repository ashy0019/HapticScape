package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.clicker.ClickerPhraseMatchMode;
import com.ashy0019.hapticscape.clicker.ClickerPhraseRule;
import com.ashy0019.hapticscape.clicker.ClickerPhraseRules;
import com.ashy0019.hapticscape.remote.RemoteLockSnapshot;
import com.ashy0019.hapticscape.remote.RemoteLockState;
import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import com.ashy0019.hapticscape.remote.SettingsLockCatalog;
import com.ashy0019.hapticscape.remote.SettingsLockService;
import com.ashy0019.hapticscape.remote.SettingsLockTarget;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
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

final class ClickerPhraseRulesPanel extends JPanel
{
	private final SettingsChangeSink settingsSink;
	private final DefaultListModel<ClickerPhraseRule> ruleModel =
		new DefaultListModel<>();
	private final JList<ClickerPhraseRule> ruleList =
		new JList<>(ruleModel);
	private final JButton addButton = new JButton("Add");
	private final JButton editButton = new JButton("Edit");
	private final JButton deleteButton = new JButton("Delete");
	private final SettingsLockDraft lockDraft;
	private final SettingsLockService lockService;
	private final RemoteSessionManager sessionManager;
	private final BooleanSupplier editingRemoteSubject;
	private final BooleanSupplier lockSelectionEnabled;

	private volatile ClickerPhraseRules rules;
	private boolean remoteReadOnly;

	ClickerPhraseRulesPanel(
		HapticScapeConfig config,
		SettingsChangeSink settingsSink,
		RemoteSessionManager sessionManager,
		SettingsLockService lockService,
		SettingsLockDraft lockDraft,
		BooleanSupplier editingRemoteSubject,
		BooleanSupplier lockSelectionEnabled)
	{
		this.settingsSink = settingsSink;
		this.sessionManager = sessionManager;
		this.lockService = lockService;
		this.lockDraft = lockDraft;
		this.editingRemoteSubject = editingRemoteSubject;
		this.lockSelectionEnabled = lockSelectionEnabled;
		String configuredRules = config.clickerPhraseRules();
		rules = ClickerPhraseRules.fromConfigValue(configuredRules);
		if (ClickerPhraseRules.requiresMigration(configuredRules))
		{
			settingsSink.set(
				HapticScapeConfig.CLICKER_PHRASE_RULES_KEY,
				rules.toConfigValue()
			);
		}

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
		ruleList.setCellRenderer(new PhraseRuleRenderer());
		JScrollPane scrollPane = new JScrollPane(ruleList);
		scrollPane.setPreferredSize(new Dimension(0, 115));
		scrollPane.setToolTipText(
			"Regex uses find(). Use ^ and $ for a whole-message match, "
				+ "or (?i) for case-insensitive regex. During Remote Play, "
				+ "Shift-click a rule to select it for post-session locking."
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
		ruleList.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				handleRuleShiftClick(event);
			}
		});
		addButton.addActionListener(event -> addRule());
		editButton.addActionListener(event -> editSelectedRule());
		deleteButton.addActionListener(event -> deleteSelectedRule());
		lockDraft.addListener(this::refreshLockState);

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
		persist(null);
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
		ClickerPhraseRule existing = rules.getRules().get(index);
		SettingsLockTarget target = target(existing);
		if (isPersistentlyLocked(target))
		{
			return;
		}

		ClickerPhraseRule updated = showRuleEditor(
			existing
		);
		if (updated == null)
		{
			return;
		}

		rules = rules.withReplaced(index, updated);
		persist(target);
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
		SettingsLockTarget target = target(rules.getRules().get(index));
		if (isPersistentlyLocked(target))
		{
			return;
		}

		rules = rules.withRemoved(index);
		persist(target);
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
				ClickerPhraseMatchMode selectedMode =
					(ClickerPhraseMatchMode) mode.getSelectedItem();
				return existing == null
					? new ClickerPhraseRule(
						enabled.isSelected(),
						selectedMode,
						expression.getText()
					)
					: existing.withValues(
						enabled.isSelected(),
						selectedMode,
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
		ClickerPhraseRule selectedRule = ruleList.getSelectedValue();
		String selectedId = selectedRule == null ? null : selectedRule.getId();
		ruleModel.clear();

		for (ClickerPhraseRule rule : rules.getRules())
		{
			ruleModel.addElement(rule);
		}

		if (!ruleModel.isEmpty())
		{
			int selectedIndex = findRuleIndex(selectedId);
			ruleList.setSelectedIndex(selectedIndex < 0 ? 0 : selectedIndex);
		}

		refreshEnabledState();
	}

	private void refreshEnabledState()
	{
		boolean selected = ruleList.getSelectedIndex() >= 0;
		SettingsLockTarget selectedTarget = selected
			? target(ruleList.getSelectedValue())
			: null;
		boolean selectedLocked = selectedTarget != null
			&& isPersistentlyLocked(selectedTarget);
		// Phrase rules remain independently configurable even while the clicker is off.
		// A Click settings block lock must not become an indirect phrase-rule lock.
		ruleList.setEnabled(true);
		addButton.setEnabled(
			!remoteReadOnly
				&& rules.getRules().size()
					< ClickerPhraseRules.MAXIMUM_RULES
		);
		editButton.setEnabled(
			!remoteReadOnly && selected && !selectedLocked
		);
		deleteButton.setEnabled(
			!remoteReadOnly && selected && !selectedLocked
		);
	}

	private void persist(SettingsLockTarget target)
	{
		settingsSink.set(
			target,
			HapticScapeConfig.CLICKER_PHRASE_RULES_KEY,
			rules.toConfigValue()
		);
	}

	private void handleRuleShiftClick(MouseEvent event)
	{
		if ((event.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) == 0
			|| !editingRemoteSubject.getAsBoolean()
			|| !lockSelectionEnabled.getAsBoolean())
		{
			return;
		}
		int index = ruleList.locationToIndex(event.getPoint());
		if (index < 0)
		{
			return;
		}
		Rectangle bounds = ruleList.getCellBounds(index, index);
		if (bounds == null || !bounds.contains(event.getPoint()))
		{
			return;
		}
		ruleList.setSelectedIndex(index);
		lockDraft.toggle(target(ruleModel.get(index)));
		refreshLockState();
		event.consume();
	}

	private void refreshLockState()
	{
		ruleList.repaint();
		refreshEnabledState();
	}

	private int findRuleIndex(String id)
	{
		if (id == null)
		{
			return -1;
		}
		for (int index = 0; index < ruleModel.size(); index++)
		{
			if (id.equals(ruleModel.get(index).getId()))
			{
				return index;
			}
		}
		return -1;
	}

	private SettingsLockTarget target(ClickerPhraseRule rule)
	{
		return SettingsLockCatalog.phraseRule(rule.getId());
	}

	private boolean isPersistentlyLocked(SettingsLockTarget target)
	{
		return !editingRemoteSubject.getAsBoolean() && lockService.isLocked(target);
	}

	private LockState lockState(ClickerPhraseRule rule)
	{
		SettingsLockTarget target = target(rule);
		if (editingRemoteSubject.getAsBoolean())
		{
			RemoteLockSnapshot remote = sessionManager.getLockSnapshot();
			if (remote.getTargets().contains(target)
				&& remote.getState() == RemoteLockState.ARMED)
			{
				return LockState.ARMED;
			}
			if (remote.getTargets().contains(target) || lockDraft.contains(target))
			{
				return LockState.PROPOSED;
			}
			return LockState.NONE;
		}
		return lockService.isLocked(target) ? LockState.PERSISTENT : LockState.NONE;
	}

	private enum LockState
	{
		NONE,
		PROPOSED,
		ARMED,
		PERSISTENT
	}

	private final class PhraseRuleRenderer extends DefaultListCellRenderer
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
			ClickerPhraseRule rule = value instanceof ClickerPhraseRule
				? (ClickerPhraseRule) value
				: null;
			setIcon(new PhraseLockIcon(rule));
			return this;
		}
	}

	private final class PhraseLockIcon implements Icon
	{
		private final ClickerPhraseRule rule;

		private PhraseLockIcon(ClickerPhraseRule rule)
		{
			this.rule = rule;
		}

		@Override
		public int getIconWidth()
		{
			return 11;
		}

		@Override
		public int getIconHeight()
		{
			return 11;
		}

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			if (rule == null)
			{
				return;
			}
			LockState state = lockState(rule);
			if (state == LockState.NONE)
			{
				return;
			}
			graphics.setColor(state == LockState.PERSISTENT
				? new Color(170, 170, 170)
				: new Color(255, 174, 0));
			graphics.drawArc(x + 2, y, 5, 6, 0, 180);
			if (state == LockState.ARMED || state == LockState.PERSISTENT)
			{
				graphics.fillRect(x + 1, y + 5, 8, 6);
			}
			else
			{
				graphics.drawRect(x + 1, y + 5, 7, 5);
			}
		}
	}

	private static JPanel row(String name, java.awt.Component control)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.add(new JLabel(name), BorderLayout.CENTER);
		row.add(control, BorderLayout.EAST);
		return row;
	}
}
