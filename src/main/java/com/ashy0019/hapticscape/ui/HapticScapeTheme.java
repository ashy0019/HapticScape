package com.ashy0019.hapticscape.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.Color;
import java.awt.Insets;
import javax.swing.UIManager;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.InsetsUIResource;

/** Installs the compact, low-motion visual foundation for the desktop app. */
public final class HapticScapeTheme
{
	public static final Color CANVAS = new Color(25, 25, 25);
	public static final Color SIDEBAR = new Color(31, 31, 31);
	public static final Color SURFACE = new Color(35, 35, 35);
	public static final Color RAISED_SURFACE = new Color(43, 43, 43);
	public static final Color BORDER = new Color(61, 61, 61);
	public static final Color BORDER_HOVER = new Color(86, 86, 86);
	public static final Color TEXT = new Color(242, 242, 242);
	public static final Color MUTED_TEXT = new Color(169, 176, 186);
	public static final Color DISABLED_TEXT = new Color(116, 116, 116);
	public static final Color ACCENT = new Color(240, 160, 0);
	public static final Color ACCENT_HOVER = new Color(255, 177, 28);
	public static final Color DANGER = new Color(219, 90, 82);
	public static final Color SUCCESS = new Color(101, 200, 121);
	public static final Color SELECTION = new Color(55, 55, 55);

	private HapticScapeTheme()
	{
	}

	public static void install()
	{
		try
		{
			UIManager.setLookAndFeel(new FlatDarkLaf());
			applyDefaults();
		}
		catch (Exception ignored)
		{
			installFallback();
		}
	}

	private static void installFallback()
	{
		try
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception ignored)
		{
			// Swing's cross-platform look and feel remains the final fallback.
		}
	}

	private static void applyDefaults()
	{
		ColorUIResource canvas = color(CANVAS);
		ColorUIResource surface = color(SURFACE);
		ColorUIResource raised = color(RAISED_SURFACE);
		ColorUIResource border = color(BORDER);
		ColorUIResource borderHover = color(BORDER_HOVER);
		ColorUIResource text = color(TEXT);
		ColorUIResource muted = color(MUTED_TEXT);
		ColorUIResource disabled = color(DISABLED_TEXT);
		ColorUIResource accent = color(ACCENT);
		ColorUIResource accentHover = color(ACCENT_HOVER);
		ColorUIResource danger = color(DANGER);
		ColorUIResource selection = color(SELECTION);

		put("Panel.background", canvas);
		put("Viewport.background", canvas);
		put("Label.foreground", text);
		put("Label.disabledForeground", disabled);
		put("Separator.foreground", border);

		put("Component.arc", 0);
		put("Component.focusWidth", 1);
		put("Component.innerFocusWidth", 0);
		put("Component.focusColor", accent);
		put("Component.borderColor", border);
		put("Component.disabledBorderColor", border);
		put("Component.error.borderColor", danger);
		put("Component.minimumWidth", 32);
		put("Component.minimumHeight", 24);
		put("Component.arrowType", "triangle");

		put("Button.arc", 0);
		put("Button.background", raised);
		put("Button.foreground", text);
		put("Button.disabledBackground", surface);
		put("Button.disabledText", disabled);
		put("Button.borderColor", border);
		put("Button.hoverBackground", selection);
		put("Button.hoverBorderColor", borderHover);
		put("Button.pressedBackground", surface);
		put("Button.focusedBorderColor", accent);
		put("Button.default.background", raised);
		put("Button.default.foreground", text);
		put("Button.default.focusedBorderColor", accent);
		put("Button.margin", insets(2, 8, 2, 8));
		put("Button.minimumHeight", 24);

		put("ToggleButton.arc", 0);
		put("ToggleButton.background", surface);
		put("ToggleButton.foreground", text);
		put("ToggleButton.selectedBackground", selection);
		put("ToggleButton.selectedForeground", text);
		put("ToggleButton.hoverBackground", raised);
		put("ToggleButton.pressedBackground", selection);
		put("ToggleButton.toolbar.selectedBackground", selection);

		put("CheckBox.foreground", text);
		put("CheckBox.disabledText", disabled);
		put("CheckBox.icon.background", surface);
		put("CheckBox.icon.borderColor", borderHover);
		put("CheckBox.icon.selectedBackground", accent);
		put("CheckBox.icon.selectedBorderColor", accent);
		put("CheckBox.icon.checkmarkColor", color(CANVAS));
		put("CheckBox.icon.focusedBorderColor", accentHover);

		put("TextField.arc", 0);
		put("FormattedTextField.arc", 0);
		put("PasswordField.arc", 0);
		put("TextArea.background", surface);
		put("TextArea.foreground", text);
		put("TextArea.caretForeground", text);
		put("TextArea.selectionBackground", selection);
		put("TextArea.selectionForeground", text);
		put("TextField.background", surface);
		put("TextField.foreground", text);
		put("TextField.inactiveBackground", canvas);
		put("TextField.inactiveForeground", muted);
		put("TextField.caretForeground", text);
		put("TextField.selectionBackground", selection);
		put("TextField.selectionForeground", text);
		put("TextField.margin", insets(2, 5, 2, 5));

		put("ComboBox.background", surface);
		put("ComboBox.foreground", text);
		put("ComboBox.buttonBackground", surface);
		put("ComboBox.buttonArrowColor", muted);
		put("ComboBox.buttonHoverArrowColor", text);
		put("ComboBox.selectionBackground", selection);
		put("ComboBox.selectionForeground", text);
		put("ComboBox.padding", insets(2, 5, 2, 5));
		put("Spinner.background", surface);
		put("Spinner.foreground", text);
		put("Spinner.buttonBackground", surface);
		put("Spinner.buttonArrowColor", muted);
		put("Spinner.buttonHoverArrowColor", text);
		put("Spinner.padding", insets(2, 5, 2, 5));

		put("TabbedPane.background", canvas);
		put("TabbedPane.foreground", muted);
		put("TabbedPane.selectedBackground", canvas);
		put("TabbedPane.selectedForeground", text);
		put("TabbedPane.hoverColor", surface);
		put("TabbedPane.focusColor", surface);
		put("TabbedPane.underlineColor", accent);
		put("TabbedPane.inactiveUnderlineColor", border);
		put("TabbedPane.contentAreaColor", border);
		put("TabbedPane.showTabSeparators", true);
		put("TabbedPane.tabSeparatorsFullHeight", true);
		put("TabbedPane.tabHeight", 28);
		put("TabbedPane.tabInsets", insets(0, 10, 0, 10));
		put("TabbedPane.contentSeparatorHeight", 1);

		put("List.background", canvas);
		put("List.foreground", text);
		put("List.selectionBackground", selection);
		put("List.selectionForeground", text);
		put("List.selectionInactiveBackground", raised);
		put("List.selectionInactiveForeground", text);
		put("Table.background", canvas);
		put("Table.foreground", text);
		put("Table.gridColor", border);
		put("Table.selectionBackground", selection);
		put("Table.selectionForeground", text);
		put("TableHeader.background", surface);
		put("TableHeader.foreground", muted);

		put("ScrollPane.background", canvas);
		put("ScrollPane.border", new BorderUIResource.LineBorderUIResource(border));
		put("ScrollBar.track", canvas);
		put("ScrollBar.thumb", raised);
		put("ScrollBar.hoverThumbColor", borderHover);
		put("ScrollBar.pressedThumbColor", muted);
		put("ScrollBar.width", 10);
		put("ScrollBar.thumbArc", 0);
		put("ScrollBar.trackArc", 0);
		put("ScrollBar.showButtons", false);
		put("ScrollBar.thumbInsets", insets(1, 1, 1, 1));

		put("Slider.trackColor", border);
		put("Slider.thumbColor", accent);
		put("Slider.hoverThumbColor", accentHover);
		put("Slider.pressedThumbColor", accent);
		put("ProgressBar.background", surface);
		put("ProgressBar.foreground", accent);
		put("ProgressBar.arc", 0);

		put("ToolTip.background", raised);
		put("ToolTip.foreground", text);
		put("ToolTip.border", new BorderUIResource.LineBorderUIResource(borderHover));
		put("TitledBorder.titleColor", muted);
		put("TitledBorder.border", new BorderUIResource.LineBorderUIResource(border));
		put("OptionPane.background", canvas);
		put("OptionPane.messageForeground", text);
		put("PopupMenu.border", new BorderUIResource.LineBorderUIResource(border));
		put("MenuItem.selectionBackground", selection);
		put("MenuItem.selectionForeground", text);
	}

	private static void put(String key, Object value)
	{
		UIManager.put(key, value);
	}

	private static ColorUIResource color(Color color)
	{
		return new ColorUIResource(color);
	}

	private static InsetsUIResource insets(int top, int left, int bottom, int right)
	{
		Insets value = new Insets(top, left, bottom, right);
		return new InsetsUIResource(value.top, value.left, value.bottom, value.right);
	}
}
