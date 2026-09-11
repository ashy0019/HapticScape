package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.clicker.ClickSequence;
import java.awt.Component;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;

/** Small Swing helpers for the intentionally constrained click vocabulary. */
final class ClickSequenceControls
{
	private ClickSequenceControls()
	{
	}

	static JComboBox<ClickSequence> enabledOnly()
	{
		return create(
			new ClickSequence[] {
				ClickSequence.ONE,
				ClickSequence.TWO,
				ClickSequence.THREE
			},
			null
		);
	}

	static JComboBox<ClickSequence> optional()
	{
		return create(ClickSequence.values(), "Off");
	}

	static JComboBox<ClickSequence> override()
	{
		return create(ClickSequence.values(), "Inherit");
	}

	static String describe(ClickSequence sequence)
	{
		if (sequence == null || sequence == ClickSequence.NONE)
		{
			return "Off";
		}
		return sequence.getClickCount() + (sequence == ClickSequence.ONE ? " click" : " clicks");
	}

	private static JComboBox<ClickSequence> create(
		ClickSequence[] values,
		String noneLabel)
	{
		JComboBox<ClickSequence> comboBox = new JComboBox<>(values);
		comboBox.setRenderer(new SequenceRenderer(noneLabel));
		PanelUi.setFixedWidth(comboBox, PanelUi.SELECTOR_CONTROL_WIDTH);
		return comboBox;
	}

	private static final class SequenceRenderer extends DefaultListCellRenderer
	{
		private final String noneLabel;

		private SequenceRenderer(String noneLabel)
		{
			this.noneLabel = noneLabel;
		}

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
			if (value instanceof ClickSequence)
			{
				ClickSequence sequence = (ClickSequence) value;
				setText(sequence == ClickSequence.NONE && noneLabel != null
					? noneLabel
					: describe(sequence));
			}
			return this;
		}
	}
}
