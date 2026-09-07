package com.ashy0019.hapticscape.host;

import java.awt.Component;
import java.util.function.Consumer;
import javax.swing.JScrollPane;

/** Host-owned process-wide UI hooks used by the desktop HapticScape surface. */
public interface GlobalUiHooks
{
	Registration installSidebarScrollRouting(JScrollPane pageScrollPane, Component eventRoot);

	Registration onGestureEnd(Runnable listener);

	Registration onScopedKeyPress(Component scope, Consumer<ScopedKeyPress> listener);

	interface Registration extends AutoCloseable
	{
		@Override
		void close();
	}

	final class ScopedKeyPress
	{
		private final int keyCode;
		private final boolean textInputFocused;

		public ScopedKeyPress(int keyCode, boolean textInputFocused)
		{
			this.keyCode = keyCode;
			this.textInputFocused = textInputFocused;
		}

		public int getKeyCode()
		{
			return keyCode;
		}

		public boolean isTextInputFocused()
		{
			return textInputFocused;
		}
	}

	static GlobalUiHooks noop()
	{
		return new GlobalUiHooks()
		{
			private final Registration registration = () -> { };

			@Override
			public Registration installSidebarScrollRouting(
				JScrollPane pageScrollPane,
				Component eventRoot)
			{
				return registration;
			}

			@Override
			public Registration onGestureEnd(Runnable listener)
			{
				return registration;
			}

			@Override
			public Registration onScopedKeyPress(
				Component scope,
				Consumer<ScopedKeyPress> listener)
			{
				return registration;
			}
		};
	}
}
