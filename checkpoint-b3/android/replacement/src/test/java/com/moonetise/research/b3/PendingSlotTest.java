package com.moonetise.research.b3;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PendingSlotTest {
    private PendingSlot ready() {
        PendingSlot s = new PendingSlot("unit");
        s.begin(true); assertTrue(s.complete("response", 100)); s.arm(); return s;
    }
    @Test public void mismatchDoesNotConsume() {
        PendingSlot s = ready();
        assertEquals(PendingSlot.Outcome.MISMATCH, s.claim("unit", 1, () -> false).outcome);
        assertEquals("response", s.pending());
        assertEquals("response", s.claim("unit", 1, () -> true).responseId);
        assertEquals(PendingSlot.Outcome.MISSING, s.claim("unit", 1, () -> true).outcome);
    }
    @Test public void wrongUnitAndExpiryCannotClaim() {
        PendingSlot s = ready();
        assertEquals(PendingSlot.Outcome.MISSING, s.claim("other", 1, () -> true).outcome);
        assertEquals(PendingSlot.Outcome.EXPIRED, s.claim("unit", 100, () -> true).outcome);
        assertEquals("response", s.close());
    }
    @Test public void closeRejectsLatePreparationAndClaim() {
        PendingSlot s = new PendingSlot("unit");
        s.begin(true); s.close();
        assertFalse(s.complete("late", 100));
        assertEquals(PendingSlot.Outcome.UNAUTHORIZED, s.claim("unit", 1, () -> true).outcome);
    }
    @Test public void claimedResponseIsNotCleanedByFinish() {
        PendingSlot s = ready(); s.claim("unit", 1, () -> true);
        assertNull(s.close());
    }
    @Test public void noBidStillTracksOneLoadingCycle() {
        PendingSlot s = new PendingSlot("unit");
        s.begin(true); s.complete(null, 0); s.arm();
        assertEquals(PendingSlot.Outcome.MISSING, s.claim("unit", 1, () -> true).outcome);
        assertThrows(IllegalStateException.class, () -> s.begin(true));
        s.close();
        assertThrows(IllegalStateException.class, () -> s.begin(true));
    }
    @Test public void concurrentMatchingTiersHaveExactlyOneWinner() throws Exception {
        PendingSlot s = ready();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        try {
            Callable<Void> claim = () -> {
                start.await();
                if (s.claim("unit", 1, () -> true).responseId != null) winners.incrementAndGet();
                return null;
            };
            Future<Void> a = pool.submit(claim), b = pool.submit(claim);
            start.countDown(); a.get(2, TimeUnit.SECONDS); b.get(2, TimeUnit.SECONDS);
            assertEquals(1, winners.get());
        } finally { pool.shutdownNow(); }
    }
}
