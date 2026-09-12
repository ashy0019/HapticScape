package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.remote.RemoteActivityEvent;
import com.ashy0019.hapticscape.remote.RemoteActivityType;
import com.ashy0019.hapticscape.remote.RemotePermissions;
import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RemoteActivityFeedPanelTest
{
	@Test
	public void feedIsBoundedAndClearsWhenPermissionIsRevoked()
	{
		RemoteActivityFeedPanel panel = new RemoteActivityFeedPanel();
		RemotePermissions allowed = RemotePermissions.defaults()
			.withActivitySharingAllowed(true);
		panel.apply(
			new RemoteSessionSnapshot(
				RemoteRole.CONTROLLER,
				RemoteSessionState.ACTIVE,
				"active",
				0
			),
			allowed
		);

		for (int index = 0; index < 60; index++)
		{
			panel.addActivity(new RemoteActivityEvent(
				RemoteActivityType.XP_GAIN,
				"Fishing",
				"+" + index + " XP",
				index
			));
		}
		assertEquals(50, panel.entryCount());

		panel.apply(
			new RemoteSessionSnapshot(
				RemoteRole.CONTROLLER,
				RemoteSessionState.ACTIVE,
				"active",
				0
			),
			RemotePermissions.defaults()
		);
		assertEquals(0, panel.entryCount());
	}
}
