package com.moonetise.research.b5;
import org.junit.Test;
import static org.junit.Assert.*;

public class BannerScopeTest {
    private PendingSlot ready() {
        PendingSlot slot = new PendingSlot(BannerScope.UNIT);
        slot.begin(true); assertTrue(slot.complete("owned-banner", 100)); slot.arm(); return slot;
    }
    @Test public void unitFormatAndSizeMismatchLeaveDemandUntouched() {
        PendingSlot slot = ready();
        java.util.function.BooleanSupplier mustNotRun = () -> { fail("Incompatible scope reached targeting"); return true; };
        assertNull(BannerScope.claim(slot, DemandBridge.UNIT, "BANNER", 320, 50, 1, mustNotRun));
        assertNull(BannerScope.claim(slot, RewardedDemandBridge.UNIT, "BANNER", 320, 50, 1, mustNotRun));
        assertNull(BannerScope.claim(slot, BannerScope.UNIT, "LEADER", 728, 90, 1, mustNotRun));
        assertNull(BannerScope.claim(slot, BannerScope.UNIT, "MREC", 300, 250, 1, mustNotRun));
        assertNull(BannerScope.claim(slot, BannerScope.UNIT, "REWARDED", 320, 50, 1, mustNotRun));
        assertNull(BannerScope.claim(slot, BannerScope.UNIT, "BANNER", 320, 90, 1, mustNotRun));
        assertEquals("owned-banner", slot.pending());
        assertEquals(PendingSlot.Outcome.MATCH,
                BannerScope.claim(slot, BannerScope.UNIT, "BANNER", 320, 50, 1, () -> true).outcome);
    }
    @Test public void targetingMismatchDoesNotConsumeAndMatchingClaimIsSingleUse() {
        PendingSlot slot = ready();
        assertEquals(PendingSlot.Outcome.MISMATCH,
                BannerScope.claim(slot, BannerScope.UNIT, "BANNER", 320, 50, 1, () -> false).outcome);
        assertEquals("owned-banner", slot.pending());
        assertEquals("owned-banner",
                BannerScope.claim(slot, BannerScope.UNIT, "BANNER", 320, 50, 1, () -> true).responseId);
        assertEquals(PendingSlot.Outcome.MISSING,
                BannerScope.claim(slot, BannerScope.UNIT, "BANNER", 320, 50, 1, () -> true).outcome);
        assertNull(slot.close()); // Cleanup must not reclaim a successfully claimed creative.
    }
    @Test public void expiryAndCancellationDenyClaimAndReturnOnlyUnusedOwnedId() {
        PendingSlot expired = ready();
        assertEquals(PendingSlot.Outcome.EXPIRED,
                BannerScope.claim(expired, BannerScope.UNIT, "BANNER", 320, 50, 100, () -> true).outcome);
        assertEquals("owned-banner", expired.close()); assertNull(expired.close());
        PendingSlot cancelled = ready();
        assertEquals("owned-banner", cancelled.close());
        assertEquals(PendingSlot.Outcome.UNAUTHORIZED,
                BannerScope.claim(cancelled, BannerScope.UNIT, "BANNER", 320, 50, 1, () -> true).outcome);
        assertFalse(cancelled.complete("late", 200));
    }
    @Test public void onlyExactBannerCreativeDimensionsAreEligible() {
        assertTrue(BannerScope.creative("banner", 320, 50));
        assertFalse(BannerScope.creative("video", 320, 50));
        assertFalse(BannerScope.creative("banner", 728, 90));
        assertFalse(BannerScope.creative(null, 320, 50));
    }
}
