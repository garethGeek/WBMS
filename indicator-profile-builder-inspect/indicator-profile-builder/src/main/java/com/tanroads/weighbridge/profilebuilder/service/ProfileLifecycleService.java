package com.tanroads.weighbridge.profilebuilder.service;

import java.util.EnumMap;
import java.util.Map;

import com.tanroads.weighbridge.profilebuilder.engine.CaptureStatistics;
import com.tanroads.weighbridge.profilebuilder.model.IndicatorProfile;
import com.tanroads.weighbridge.profilebuilder.model.ProfileApprovalStatus;

/**
 * DRAFT -> TESTING -> VALIDATED -> APPROVED -> DEPLOYED, strictly forward one step at a
 * time; demotion back to DRAFT allowed from anywhere. Port of the main system service,
 * including its evidence requirement for reaching VALIDATED.
 */
public class ProfileLifecycleService {

    private static final Map<ProfileApprovalStatus, ProfileApprovalStatus> NEXT_STEP = new EnumMap<>(ProfileApprovalStatus.class);

    static {
        NEXT_STEP.put(ProfileApprovalStatus.DRAFT, ProfileApprovalStatus.TESTING);
        NEXT_STEP.put(ProfileApprovalStatus.TESTING, ProfileApprovalStatus.VALIDATED);
        NEXT_STEP.put(ProfileApprovalStatus.VALIDATED, ProfileApprovalStatus.APPROVED);
        NEXT_STEP.put(ProfileApprovalStatus.APPROVED, ProfileApprovalStatus.DEPLOYED);
    }

    /** Same pragmatic minimum as the main system: not a statutory number. */
    public static final long MINIMUM_MESSAGES_FOR_VALIDATION = 20;

    public boolean canAdvance(ProfileApprovalStatus current) {
        return NEXT_STEP.containsKey(current);
    }

    public ProfileApprovalStatus nextStatus(ProfileApprovalStatus current) {
        ProfileApprovalStatus next = NEXT_STEP.get(current);
        if (next == null) {
            throw new IllegalStateException("No forward transition from " + current);
        }
        return next;
    }

    public void requireSufficientEvidence(ProfileApprovalStatus targetStatus, CaptureStatistics statistics,
                                          boolean technicianConfirmedCorrect) {
        if (targetStatus != ProfileApprovalStatus.VALIDATED) {
            return;
        }
        long received = statistics != null ? statistics.getMessagesReceived() : 0;
        if (received < MINIMUM_MESSAGES_FOR_VALIDATION) {
            throw new IllegalStateException("At least " + MINIMUM_MESSAGES_FOR_VALIDATION
                    + " captured messages are required in the current capture session before a profile can be marked VALIDATED (received so far: "
                    + received + ")");
        }
        if (statistics.getValidCount() == 0) {
            throw new IllegalStateException("No valid readings were captured -- cannot validate this profile");
        }
        if (!technicianConfirmedCorrect) {
            throw new IllegalStateException("Tick the confirmation box first: the technician must explicitly confirm the parsed result is correct before VALIDATED");
        }
    }

    public ProfileApprovalStatus demoteToDraft() {
        return ProfileApprovalStatus.DRAFT;
    }

    public boolean isEditableInPlace(ProfileApprovalStatus status) {
        return status == ProfileApprovalStatus.DRAFT
                || status == ProfileApprovalStatus.TESTING
                || status == ProfileApprovalStatus.VALIDATED;
    }

    public boolean isDeletable(ProfileApprovalStatus status) {
        return isEditableInPlace(status);
    }

    /** New DRAFT carrying every configuration field forward, profileVersion + 1,
     *  supersedesProfileNo recorded; the previous profile is never touched. */
    public IndicatorProfile createNewVersion(IndicatorProfile previous) {
        IndicatorProfile next = previous.copyConfigurationTo(new IndicatorProfile());
        next.setProfileVersion(previous.getProfileVersion() != null ? previous.getProfileVersion() + 1 : 2)
                .setSupersedesProfileNo(previous.getId())
                .setApprovalStatus(ProfileApprovalStatus.DRAFT);
        return next;
    }
}
