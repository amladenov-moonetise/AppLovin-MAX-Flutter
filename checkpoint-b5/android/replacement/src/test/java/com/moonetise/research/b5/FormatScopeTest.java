package com.moonetise.research.b5;
import org.junit.Test;
import static org.junit.Assert.*;

public class FormatScopeTest {
    @Test public void rewardedAndInterstitialSlotsCannotCrossClaim() {
        // Constants inline without loading Android-dependent providers in this JVM test.
        PendingSlot rewarded = new PendingSlot(RewardedDemandBridge.UNIT);
        PendingSlot interstitial = new PendingSlot(DemandBridge.UNIT);
        rewarded.begin(true); rewarded.complete("rewarded", 100); rewarded.arm();
        interstitial.begin(true); interstitial.complete("interstitial", 100); interstitial.arm();
        assertEquals(PendingSlot.Outcome.MISSING,
                rewarded.claim(DemandBridge.UNIT, 1, () -> { fail("Wrong unit must not evaluate targeting"); return true; }).outcome);
        assertEquals(PendingSlot.Outcome.MISSING,
                interstitial.claim(RewardedDemandBridge.UNIT, 1, () -> true).outcome);
        assertEquals("rewarded", rewarded.claim(RewardedDemandBridge.UNIT, 1, () -> true).responseId);
        assertEquals("interstitial", interstitial.claim(DemandBridge.UNIT, 1, () -> true).responseId);
        assertNull(rewarded.close()); assertNull(interstitial.close());
    }
}
