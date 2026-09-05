package com.ashy0019.hapticscape.remote;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns the complete post-session settings-lock protocol conversation. */
final class RemoteLockCoordinator
{
	private static final Logger LOG = Logger.getLogger(RemoteLockCoordinator.class.getName());
	private static final long PROPOSAL_RETRY_NANOS = 2_000_000_000L;

	private final Gson gson;
	private final SettingsLockService settingsLockService;
	private final SavedUnlockKeyStore savedUnlockKeyStore;
	private final RemoteMessageSender sender;
	private final Consumer<RemoteLockSnapshot> snapshotPublisher;
	private final Consumer<SettingsLockProposal> proposalPublisher;

	private volatile RemoteLockSnapshot snapshot = RemoteLockSnapshot.inactive();
	private SettingsLockProposal controllerProposal;
	private char[] pendingControllerUnlockKey;
	private SettingsLockProposal participantProposal;
	private String participantArmedLockId;
	private String participantDeclinedLockId;
	private long lastProposalNanos;

	RemoteLockCoordinator(
		Gson gson,
		SettingsLockService settingsLockService,
		SavedUnlockKeyStore savedUnlockKeyStore,
		RemoteMessageSender sender,
		Consumer<RemoteLockSnapshot> snapshotPublisher,
		Consumer<SettingsLockProposal> proposalPublisher)
	{
		this.gson = Objects.requireNonNull(gson, "gson");
		this.settingsLockService = Objects.requireNonNull(
			settingsLockService,
			"settingsLockService"
		);
		this.savedUnlockKeyStore = Objects.requireNonNull(
			savedUnlockKeyStore,
			"savedUnlockKeyStore"
		);
		this.sender = Objects.requireNonNull(sender, "sender");
		this.snapshotPublisher = Objects.requireNonNull(
			snapshotPublisher,
			"snapshotPublisher"
		);
		this.proposalPublisher = Objects.requireNonNull(
			proposalPublisher,
			"proposalPublisher"
		);
	}

	RemoteLockSnapshot getSnapshot()
	{
		return snapshot;
	}

	char[] generateUnlockKey()
	{
		return settingsLockService.generateUnlockKey();
	}

	List<SavedUnlockKey> getSavedUnlockKeys()
	{
		return savedUnlockKeyStore.list();
	}

	boolean isSavedUnlockKeyVaultAvailable()
	{
		return savedUnlockKeyStore.isAvailable();
	}

	String getSavedUnlockKeyVaultMessage()
	{
		return savedUnlockKeyStore.getUnavailableMessage();
	}

	char[] revealSavedUnlockKey(String id)
	{
		return savedUnlockKeyStore.reveal(id);
	}

	SavedUnlockKey updateSavedUnlockKey(String id, String label, String note)
	{
		return savedUnlockKeyStore.updateDetails(id, label, note);
	}

	boolean forgetSavedUnlockKey(String id)
	{
		return savedUnlockKeyStore.forget(id);
	}

	void propose(RemoteRole role, RemoteSessionState state, char[] password)
	{
		if (role != RemoteRole.CONTROLLER
			|| (state != RemoteSessionState.ACTIVE
				&& state != RemoteSessionState.PEER_EMERGENCY_PAUSED))
		{
			throw new IllegalStateException("A participant must be connected first");
		}
		if (snapshot.getState() == RemoteLockState.AWAITING_APPROVAL
			|| snapshot.getState() == RemoteLockState.ARMED)
		{
			throw new IllegalStateException(
				"Cancel the current post-session lock before creating another"
			);
		}

		SettingsLockProposal proposal = settingsLockService.createProposal(password);
		char[] pendingKey = Arrays.copyOf(password, password.length);
		clearPendingControllerUnlockKey();
		controllerProposal = proposal;
		pendingControllerUnlockKey = pendingKey;
		lastProposalNanos = 0;
		publish(RemoteLockState.AWAITING_APPROVAL, "Waiting for participant approval");
		sendControllerProposal();
	}

	void cancel(RemoteRole role)
	{
		if (role != RemoteRole.CONTROLLER || controllerProposal == null)
		{
			return;
		}
		if (snapshot.getState() == RemoteLockState.DECLINED)
		{
			clearPendingControllerUnlockKey();
			controllerProposal = null;
			publish(RemoteLockState.INACTIVE, "No post-session lock requested");
			return;
		}
		sender.send(
			RemoteMessageType.LOCK_CANCEL_REQUEST,
			0,
			controllerProposal.getProposalId()
		);
	}

	void accept(RemoteRole role)
	{
		if (role != RemoteRole.PARTICIPANT || participantProposal == null)
		{
			return;
		}
		SettingsLockProposal proposal = participantProposal;
		try
		{
			participantArmedLockId = proposal.getProposalId();
			settingsLockService.arm(proposal);
			participantProposal = null;
			participantDeclinedLockId = null;
			publish(RemoteLockState.ARMED, "Post-session settings lock armed");
			sender.send(RemoteMessageType.LOCK_ACCEPTED, 0, participantArmedLockId);
		}
		catch (RuntimeException e)
		{
			participantArmedLockId = null;
			participantProposal = null;
			participantDeclinedLockId = proposal.getProposalId();
			publish(RemoteLockState.DECLINED, "Settings lock could not be saved");
			sender.send(RemoteMessageType.LOCK_DECLINED, 0, participantDeclinedLockId);
			LOG.log(Level.WARNING, "Unable to arm participant settings lock", e);
		}
	}

	void decline(RemoteRole role)
	{
		if (role != RemoteRole.PARTICIPANT || participantProposal == null)
		{
			return;
		}
		participantDeclinedLockId = participantProposal.getProposalId();
		participantProposal = null;
		publish(RemoteLockState.DECLINED, "Post-session lock declined");
		sender.send(RemoteMessageType.LOCK_DECLINED, 0, participantDeclinedLockId);
	}

	void handle(RemoteRole role, RemoteProtocolMessage message)
	{
		switch (message.getType())
		{
			case LOCK_PROPOSAL:
				handleProposal(role, message);
				break;
			case LOCK_ACCEPTED:
				handleAccepted(role, message);
				break;
			case LOCK_DECLINED:
				handleDeclined(role, message);
				break;
			case LOCK_CANCEL_REQUEST:
				handleCancelRequest(role, message);
				break;
			case LOCK_CANCELLED:
				handleCancelled(role, message);
				break;
			default:
				throw new IllegalArgumentException("Not a settings-lock message: " + message.getType());
		}
	}

	void tick(RemoteRole role, RemoteSessionState state, long nowNanos)
	{
		if (role == RemoteRole.CONTROLLER
			&& controllerProposal != null
			&& state != RemoteSessionState.LOCAL
			&& snapshot.getState() == RemoteLockState.AWAITING_APPROVAL
			&& nowNanos - lastProposalNanos >= PROPOSAL_RETRY_NANOS)
		{
			sendControllerProposal();
		}
	}

	void handleLocalSettingsLockChanged(RemoteRole role, boolean locked)
	{
		if (locked || role != RemoteRole.PARTICIPANT || participantArmedLockId == null)
		{
			return;
		}
		String clearedId = participantArmedLockId;
		participantArmedLockId = null;
		publish(RemoteLockState.INACTIVE, "Settings lock cleared locally");
		sender.send(RemoteMessageType.LOCK_CANCELLED, 0, clearedId);
	}

	void handleControllerConnectionLost()
	{
		clearPendingControllerUnlockKey();
		controllerProposal = null;
		publish(RemoteLockState.INACTIVE, "Pending unlock key discarded");
	}

	void reset()
	{
		controllerProposal = null;
		clearPendingControllerUnlockKey();
		participantProposal = null;
		participantArmedLockId = null;
		participantDeclinedLockId = null;
		lastProposalNanos = 0;
		snapshot = RemoteLockSnapshot.inactive();
	}

	private void handleProposal(RemoteRole role, RemoteProtocolMessage message)
	{
		if (role != RemoteRole.PARTICIPANT)
		{
			return;
		}
		try
		{
			SettingsLockProposal proposal = gson.fromJson(
				message.getPayload(),
				SettingsLockProposal.class
			);
			if (proposal == null)
			{
				return;
			}
			proposal.validate();
			String proposalId = proposal.getProposalId();
			if (proposalId.equals(participantArmedLockId))
			{
				sender.send(RemoteMessageType.LOCK_ACCEPTED, 0, proposalId);
				return;
			}
			if (proposalId.equals(participantDeclinedLockId))
			{
				sender.send(RemoteMessageType.LOCK_DECLINED, 0, proposalId);
				return;
			}
			if (participantProposal != null
				&& proposalId.equals(participantProposal.getProposalId()))
			{
				return;
			}
			if (settingsLockService.isLocked())
			{
				participantDeclinedLockId = proposalId;
				publish(RemoteLockState.DECLINED, "Settings are already locked");
				sender.send(RemoteMessageType.LOCK_DECLINED, 0, proposalId);
				return;
			}
			participantProposal = proposal;
			publish(
				RemoteLockState.APPROVAL_REQUIRED,
				"Controller requests a post-session settings lock"
			);
			proposalPublisher.accept(proposal);
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.WARNING, "Rejected invalid settings-lock proposal", e);
		}
	}

	private void handleAccepted(RemoteRole role, RemoteProtocolMessage message)
	{
		if (role != RemoteRole.CONTROLLER || !matchesControllerProposal(message.getPayload()))
		{
			return;
		}
		String status = "Participant accepted; settings lock armed";
		try
		{
			if (pendingControllerUnlockKey == null)
			{
				status = savedUnlockKeyStore.findByLockId(message.getPayload()).isPresent()
					? "Participant accepted; unlock key saved"
					: "Participant accepted; unlock key is not available";
			}
			else if (savedUnlockKeyStore.isAvailable())
			{
				savedUnlockKeyStore.saveAcceptedKey(
					message.getPayload(),
					pendingControllerUnlockKey
				);
				status = "Participant accepted; unlock key saved";
			}
			else
			{
				status = "Participant accepted; secure key vault unavailable";
			}
		}
		catch (RuntimeException e)
		{
			status = "Participant accepted; unlock key could not be saved";
			LOG.log(Level.WARNING, "Unable to save accepted unlock key", e);
		}
		finally
		{
			clearPendingControllerUnlockKey();
		}
		publish(RemoteLockState.ARMED, status);
	}

	private void handleDeclined(RemoteRole role, RemoteProtocolMessage message)
	{
		if (role == RemoteRole.CONTROLLER && matchesControllerProposal(message.getPayload()))
		{
			clearPendingControllerUnlockKey();
			publish(RemoteLockState.DECLINED, "Participant declined the settings lock");
		}
	}

	private void handleCancelRequest(RemoteRole role, RemoteProtocolMessage message)
	{
		if (role != RemoteRole.PARTICIPANT)
		{
			return;
		}
		String proposalId = message.getPayload();
		boolean pendingMatch = participantProposal != null
			&& participantProposal.getProposalId().equals(proposalId);
		boolean armedMatch = proposalId != null && proposalId.equals(participantArmedLockId);
		if (!pendingMatch && !armedMatch)
		{
			return;
		}
		participantProposal = null;
		participantDeclinedLockId = null;
		if (armedMatch)
		{
			participantArmedLockId = null;
			settingsLockService.clearAllLocks();
		}
		publish(RemoteLockState.INACTIVE, "Post-session settings lock cancelled");
		sender.send(RemoteMessageType.LOCK_CANCELLED, 0, proposalId);
	}

	private void handleCancelled(RemoteRole role, RemoteProtocolMessage message)
	{
		if (role != RemoteRole.CONTROLLER || !matchesControllerProposal(message.getPayload()))
		{
			return;
		}
		String lockId = controllerProposal.getProposalId();
		clearPendingControllerUnlockKey();
		controllerProposal = null;
		String status = "Post-session settings lock cancelled";
		try
		{
			savedUnlockKeyStore.forgetByLockId(lockId);
		}
		catch (RuntimeException e)
		{
			status = "Settings lock cancelled; saved key could not be removed";
			LOG.log(Level.WARNING, "Unable to remove cancelled unlock key", e);
		}
		publish(RemoteLockState.INACTIVE, status);
	}

	private boolean matchesControllerProposal(String proposalId)
	{
		return controllerProposal != null
			&& controllerProposal.getProposalId().equals(proposalId);
	}

	private void sendControllerProposal()
	{
		SettingsLockProposal proposal = controllerProposal;
		if (proposal == null)
		{
			return;
		}
		lastProposalNanos = System.nanoTime();
		sender.send(RemoteMessageType.LOCK_PROPOSAL, 0, gson.toJson(proposal));
	}

	private void publish(RemoteLockState state, String message)
	{
		RemoteLockSnapshot next = new RemoteLockSnapshot(state, message);
		snapshot = next;
		snapshotPublisher.accept(next);
	}

	private void clearPendingControllerUnlockKey()
	{
		if (pendingControllerUnlockKey != null)
		{
			Arrays.fill(pendingControllerUnlockKey, '\0');
			pendingControllerUnlockKey = null;
		}
	}
}
