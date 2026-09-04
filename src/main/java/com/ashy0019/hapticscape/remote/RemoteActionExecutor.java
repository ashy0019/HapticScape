package com.ashy0019.hapticscape.remote;

/** Local effects available to the validated remote-action layer. */
public interface RemoteActionExecutor
{
	void playHaptic(String patternSelection, int intensityPercent, int durationMillis);

	void setRemoteLiveIntensity(int intensityPercent);

	void releaseRemoteLiveHaptic();

	void stopRemoteLiveHaptic();

	void playClick();

	void showMessage(String message, boolean desktopNotification, boolean localChatboxMessage);

	void stopRemoteOutput();

	RemoteActionExecutor NO_OP = new RemoteActionExecutor()
	{
		@Override
		public void playHaptic(String patternSelection, int intensityPercent, int durationMillis) { }

		@Override
		public void setRemoteLiveIntensity(int intensityPercent) { }

		@Override
		public void releaseRemoteLiveHaptic() { }

		@Override
		public void stopRemoteLiveHaptic() { }

		@Override
		public void playClick() { }

		@Override
		public void showMessage(String message, boolean desktopNotification, boolean localChatboxMessage) { }

		@Override
		public void stopRemoteOutput() { }
	};
}
