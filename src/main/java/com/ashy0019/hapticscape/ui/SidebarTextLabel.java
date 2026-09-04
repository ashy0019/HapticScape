package com.ashy0019.hapticscape.ui;

import java.awt.Dimension;
import javax.swing.border.Border;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/** Plain-text label that wraps predictably inside RuneLite's narrow sidebar. */
final class SidebarTextLabel extends JTextArea
{
	// Leave room for card borders and RuneLite scrollbar gutters at the
	// narrowest supported sidebar width.
	private static final int TEXT_WIDTH = 180;
	private boolean initialized;

	SidebarTextLabel(String text)
	{
		setEditable(false);
		setFocusable(false);
		setLineWrap(true);
		setWrapStyleWord(true);
		setOpaque(false);
		setFont(UIManager.getFont("Label.font"));
		setForeground(UIManager.getColor("Label.foreground"));
		initialized = true;
		setPlainText(text);
	}

	void setPlainText(String text)
	{
		setPreferredSize(null);
		setMinimumSize(null);
		setMaximumSize(null);
		setText(text == null ? "" : text);
		setSize(new Dimension(TEXT_WIDTH, Short.MAX_VALUE));
		Dimension measured = super.getPreferredSize();
		Dimension preferred = new Dimension(TEXT_WIDTH, measured.height);
		setPreferredSize(preferred);
		setMinimumSize(new Dimension(0, preferred.height));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
		setCaretPosition(0);
	}

	@Override
	public void setBorder(Border border)
	{
		super.setBorder(border);
		if (initialized)
		{
			setPlainText(getText());
		}
	}
}
