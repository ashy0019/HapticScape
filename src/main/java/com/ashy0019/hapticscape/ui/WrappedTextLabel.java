package com.ashy0019.hapticscape.ui;

import java.awt.Dimension;
import javax.swing.border.Border;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Lightweight plain-text label whose height follows its current desktop width. */
final class WrappedTextLabel extends JTextArea
{
	private static final int FALLBACK_WIDTH = 180;
	private boolean initialized;
	private boolean refreshQueued;
	private int measuredWidth = -1;

	WrappedTextLabel(String text)
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
		setText(text == null ? "" : text);
		measuredWidth = -1;
		refreshPreferredHeight(measurementWidth());
		setCaretPosition(0);
	}

	@Override
	public void setBounds(int x, int y, int width, int height)
	{
		boolean widthChanged = initialized && width > 0 && width != getWidth();
		super.setBounds(x, y, width, height);
		if (widthChanged)
		{
			queuePreferredHeightRefresh();
		}
	}

	@Override
	public void setFont(java.awt.Font font)
	{
		super.setFont(font);
		if (initialized)
		{
			measuredWidth = -1;
			queuePreferredHeightRefresh();
		}
	}

	@Override
	public void setBorder(Border border)
	{
		super.setBorder(border);
		if (initialized)
		{
			measuredWidth = -1;
			queuePreferredHeightRefresh();
		}
	}

	private int measurementWidth()
	{
		if (getWidth() > 0)
		{
			return getWidth();
		}
		if (getParent() != null && getParent().getWidth() > 0)
		{
			return getParent().getWidth();
		}
		return FALLBACK_WIDTH;
	}

	private void queuePreferredHeightRefresh()
	{
		if (refreshQueued)
		{
			return;
		}
		refreshQueued = true;
		SwingUtilities.invokeLater(() ->
		{
			refreshQueued = false;
			if (initialized)
			{
				refreshPreferredHeight(measurementWidth());
				revalidate();
				repaint();
			}
		});
	}

	private void refreshPreferredHeight(int width)
	{
		int safeWidth = Math.max(1, width);
		if (safeWidth == measuredWidth)
		{
			return;
		}
		JTextArea probe = new JTextArea(getText());
		probe.setEditable(false);
		probe.setLineWrap(true);
		probe.setWrapStyleWord(true);
		probe.setFont(getFont());
		probe.setBorder(getBorder());
		probe.setMargin(getMargin());
		probe.setSize(new Dimension(safeWidth, Short.MAX_VALUE));
		Dimension measured = probe.getPreferredSize();
		Dimension preferred = new Dimension(FALLBACK_WIDTH, measured.height);
		setPreferredSize(preferred);
		setMinimumSize(new Dimension(0, preferred.height));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
		measuredWidth = safeWidth;
	}
}
