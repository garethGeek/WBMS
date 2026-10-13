package com.tanroads.weighbridge.profilebuilder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.tanroads.weighbridge.profilebuilder.engine.CaptureStatistics;
import com.tanroads.weighbridge.profilebuilder.engine.NormalizedWeightReading;
import com.tanroads.weighbridge.profilebuilder.model.ConnectionType;
import com.tanroads.weighbridge.profilebuilder.model.ExtractionRule;
import com.tanroads.weighbridge.profilebuilder.model.FramingMode;
import com.tanroads.weighbridge.profilebuilder.model.IndicatorProfile;
import com.tanroads.weighbridge.profilebuilder.model.ProfileApprovalStatus;
import com.tanroads.weighbridge.profilebuilder.model.ReadingStability;

class EscapesAndLifecycleTest {

    private static final String BS = String.valueOf((char) 92);
    private static final String CRLF = "" + (char) 13 + (char) 10;

    @Test
    void unescapeTurnsUiTextIntoRealControlCharacters() {
        assertEquals(CRLF, Escapes.unescape(BS + "r" + BS + "n"));
        assertEquals("" + (char) 2 + "W" + (char) 13, Escapes.unescape(BS + "x02W" + BS + "r"));
        assertEquals("plain", Escapes.unescape("plain"));
        assertNull(Escapes.unescape(null));
        assertEquals(BS, Escapes.unescape(BS + BS));
        assertEquals(BS + "q", Escapes.unescape(BS + "q"));
    }

    @Test
    void escapeRoundTrips() {
        String real = "" + (char) 2 + "ST," + (char) 9 + "1" + CRLF;
        String shown = Escapes.escape(real);
        assertEquals(BS + "x02ST," + BS + "t1" + BS + "r" + BS + "n", shown);
        assertEquals(real, Escapes.unescape(shown));
    }

    @Test
    void visibleAsciiAndHex() {
        byte[] data = ("ST" + CRLF).getBytes(StandardCharsets.US_ASCII);
        assertEquals("ST" + BS + "r" + BS + "n", Escapes.visibleAscii(data, data.length));
        assertEquals("53 54 0D 0A", Escapes.hex(data, data.length));
    }

    @Test
    void lifecycleMovesForwardOneStepAndDemotesToDraft() {
        ProfileLifecycleService lifecycle = new ProfileLifecycleService();
        assertEquals(ProfileApprovalStatus.TESTING, lifecycle.nextStatus(ProfileApprovalStatus.DRAFT));
        assertEquals(ProfileApprovalStatus.VALIDATED, lifecycle.nextStatus(ProfileApprovalStatus.TESTING));
        assertEquals(ProfileApprovalStatus.APPROVED, lifecycle.nextStatus(ProfileApprovalStatus.VALIDATED));
        assertEquals(ProfileApprovalStatus.DEPLOYED, lifecycle.nextStatus(ProfileApprovalStatus.APPROVED));
        assertFalse(lifecycle.canAdvance(ProfileApprovalStatus.DEPLOYED));
        assertThrows(IllegalStateException.class, () -> lifecycle.nextStatus(ProfileApprovalStatus.DEPLOYED));
        assertEquals(ProfileApprovalStatus.DRAFT, lifecycle.demoteToDraft());
        assertTrue(lifecycle.isEditableInPlace(ProfileApprovalStatus.VALIDATED));
        assertFalse(lifecycle.isEditableInPlace(ProfileApprovalStatus.APPROVED));
    }

    @Test
    void validatedRequiresEvidenceAndConfirmation() {
        ProfileLifecycleService lifecycle = new ProfileLifecycleService();
        CaptureStatistics few = new CaptureStatistics();
        assertThrows(IllegalStateException.class,
                () -> lifecycle.requireSufficientEvidence(ProfileApprovalStatus.VALIDATED, few, true));
        CaptureStatistics enough = new CaptureStatistics();
        for (int i = 0; i < ProfileLifecycleService.MINIMUM_MESSAGES_FOR_VALIDATION; i++) {
            enough.record(new NormalizedWeightReading(java.math.BigDecimal.TEN, null, "kg", ReadingStability.STABLE,
                    java.time.Instant.now(), "t", "10", null, null));
        }
        assertThrows(IllegalStateException.class,
                () -> lifecycle.requireSufficientEvidence(ProfileApprovalStatus.VALIDATED, enough, false));
        lifecycle.requireSufficientEvidence(ProfileApprovalStatus.VALIDATED, enough, true);
        lifecycle.requireSufficientEvidence(ProfileApprovalStatus.TESTING, few, false);
    }

    @Test
    void newVersionCarriesConfigurationNotIdentity() {
        IndicatorProfile previous = new IndicatorProfile().setId(7L).setName("X").setConnectionType(ConnectionType.TCP)
                .setTcpHost("h").setTcpPort(1).setFramingMode(FramingMode.DELIMITER).setMessageDelimiter(CRLF)
                .setExtractionRule(ExtractionRule.FIRST_NUMBER).setProfileVersion(3)
                .setApprovalStatus(ProfileApprovalStatus.APPROVED).setValidatedByName("someone");
        IndicatorProfile next = new ProfileLifecycleService().createNewVersion(previous);
        assertNull(next.getId());
        assertEquals(4, next.getProfileVersion());
        assertEquals(7L, next.getSupersedesProfileNo());
        assertEquals(ProfileApprovalStatus.DRAFT, next.getApprovalStatus());
        assertEquals("h", next.getTcpHost());
        assertEquals(CRLF, next.getMessageDelimiter());
        assertNull(next.getValidatedByName());
    }
}
