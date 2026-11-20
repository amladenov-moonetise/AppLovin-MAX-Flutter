package com.applovin.applovin_max;

/** Focused standalone runner: exact view/epoch, one preparation per load, stale refresh exclusion. */
public final class BannerCycleTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Banner cycle check " + checks);
    }
    private static void rejects(String code, Runnable operation) {
        try { operation.run(); throw new AssertionError("Expected " + code); }
        catch (IllegalStateException error) { check(code.equals(error.getMessage())); }
    }
    public static void main(String[] args) {
        Object view = new Object();
        BannerCycle cycle = new BannerCycle(view, 7);
        rejects("BANNER_STALE_CYCLE", () -> cycle.consume(view, 7, 0));
        long first = cycle.begin(view, 7);
        check(cycle.writable(view, 7, first));
        check(!cycle.current(new Object(), 7, first));
        check(!cycle.current(view, 8, first));
        rejects("BANNER_BUSY", () -> cycle.begin(view, 7));
        cycle.consume(view, 7, first);
        check(!cycle.writable(view, 7, first));
        rejects("BANNER_STALE_CYCLE", () -> cycle.consume(view, 7, first));
        rejects("BANNER_BUSY", () -> cycle.begin(view, 7));
        check(!cycle.complete(new Object(), 7));
        check(cycle.complete(view, 7));
        long refresh = cycle.begin(view, 7);
        check(refresh != first);
        check(!cycle.writable(view, 7, first));
        check(cycle.writable(view, 7, refresh));
        cycle.invalidate();
        check(!cycle.current(view, 7, refresh));
        rejects("BANNER_STALE_CYCLE", () -> cycle.consume(view, 7, refresh));
        long fallback = cycle.begin(view, 7);
        cycle.consume(view, 7, fallback);
        cycle.invalidate(); // Authorization withdrawal cannot enable another load while MAX is in flight.
        rejects("BANNER_BUSY", () -> cycle.begin(view, 7));
        check(cycle.complete(view, 7));
        long replacementCycle = cycle.begin(view, 7);
        cycle.destroy();
        check(!cycle.current(view, 7, replacementCycle));
        rejects("BANNER_STALE_VIEW", () -> cycle.begin(view, 7));
        rejects("BANNER_STALE_VIEW", () -> cycle.begin(new Object(), 8));
        System.out.println("Banner cycle checks passed: " + checks);
    }
}
