package com.moonetise.research.b4;
import org.junit.Test;
import static org.junit.Assert.*;

public class RewardObservationTest {
    @Test public void preparationAndHiddenNeverGrantReward() {
        RewardObservation r = new RewardObservation();
        assertFalse(r.callback());
        r.show(); r.hidden();
        assertEquals(0, r.callbacks());
    }
    @Test public void actualCallbackMayPrecedeOrFollowHidden() {
        RewardObservation first = new RewardObservation(); first.show();
        assertTrue(first.callback()); first.hidden(); assertEquals(1, first.callbacks());
        RewardObservation late = new RewardObservation(); late.show(); late.hidden();
        assertTrue(late.callback()); assertEquals(1, late.callbacks());
    }
    @Test public void duplicatesRemainEvidenceAndSecondShowIsRejected() {
        RewardObservation r = new RewardObservation(); r.show();
        r.callback(); r.callback(); assertEquals(2, r.callbacks());
        assertThrows(IllegalStateException.class, r::show);
    }
}
