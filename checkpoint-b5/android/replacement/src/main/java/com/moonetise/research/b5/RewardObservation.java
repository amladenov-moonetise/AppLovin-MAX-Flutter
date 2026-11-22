package com.moonetise.research.b5;

/** Observation only. Does not create SDK callbacks or entitlements; permits reward-after-hidden. */
public final class RewardObservation {
    private boolean showing, hidden;
    private int callbacks;
    public synchronized void show() {
        if (showing) throw new IllegalStateException("ONE_SHOW_ONLY");
        showing = true;
    }
    /** Called ONLY by the fixture channel from official Flutter MAX's reward listener. */
    public synchronized boolean callback() {
        if (!showing) return false;
        callbacks++;
        return true; // Preserve duplicate callback evidence; report treats duplicates as FAIL.
    }
    public synchronized void hidden() { hidden = true; }
    public synchronized int callbacks() { return callbacks; }
    public synchronized boolean hiddenObserved() { return hidden; }
}
