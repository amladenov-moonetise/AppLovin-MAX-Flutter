package com.moonetise.research.b4;

import org.junit.Test;
import static org.junit.Assert.*;

public class CycleGateTest {
    @Test public void threeSequentialCyclesDoNotOverlap() {
        CycleGate g = new CycleGate();
        for (int i = 1; i <= 3; i++) {
            long id = g.begin(); assertEquals(i, id);
            assertThrows(IllegalStateException.class, () -> g.begin());
            assertTrue(g.requestLoad(id)); assertTrue(g.terminal(id, true));
            assertThrows(IllegalStateException.class, () -> g.begin()); // Loaded but unshown.
            assertTrue(g.show(id)); assertTrue(g.hidden(id));
        }
        assertFalse(g.canBegin());
        assertThrows(IllegalStateException.class, () -> g.begin());
    }
    @Test public void genuineLoadFailureAllowsNextCycle() {
        CycleGate g = new CycleGate(); long a = g.begin();
        g.requestLoad(a); assertTrue(g.terminal(a, false));
        assertEquals(2, g.begin());
    }
    @Test public void stalePreparationAndTerminalCannotAffectNextCycle() {
        CycleGate g = new CycleGate(); long a = g.begin();
        g.requestLoad(a); g.terminal(a, false); long b = g.begin();
        assertFalse(g.preparing(a)); assertFalse(g.terminal(a, true));
        g.unresolved(a);
        assertTrue(g.preparing(b));
    }
    @Test public void localTimeoutNeverAuthorizesReuse() {
        CycleGate g = new CycleGate(); long a = g.begin(); g.requestLoad(a);
        g.unresolved(a);
        assertFalse(g.terminal(a, true)); assertFalse(g.canBegin());
        assertThrows(IllegalStateException.class, () -> g.begin());
    }
    @Test public void displayFailureDoesNotPretendPresentationFinished() {
        CycleGate g = new CycleGate(); long a = g.begin();
        g.requestLoad(a); g.terminal(a, true); g.show(a); g.unresolved(a);
        assertFalse(g.hidden(a)); assertFalse(g.canBegin());
    }
    @Test public void previousClosedSlotCannotCompleteOrClaimNewCycle() {
        PendingSlot old = new PendingSlot("unit"); old.begin(true); old.close();
        PendingSlot next = new PendingSlot("unit"); next.begin(true);
        assertFalse(old.complete("old", 100));
        next.complete("new", 100); next.arm();
        assertEquals("new", next.claim("unit", 1, () -> true).responseId);
    }
    @Test public void SDKProbeCompletionDoesNotFakeMAXLoading() {
        CycleGate g = new CycleGate(); long a = g.begin();
        assertTrue(g.probeComplete(a)); assertFalse(g.terminal(a, true));
        assertTrue(g.canBegin());
    }
}
