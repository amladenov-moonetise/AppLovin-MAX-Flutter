package com.moonetise.research.b2;

import java.util.function.BooleanSupplier;

/** One cycle per fresh process. No SDK objects, callbacks or threads retained. */
public final class PendingSlot {
    public enum Phase { IDLE, PREPARING, READY, LOADING, CLOSED }
    public enum Outcome { MATCH, MISMATCH, MISSING, EXPIRED, UNAUTHORIZED }
    public static final class Claim {
        public final Outcome outcome;
        public final String responseId;
        private Claim(Outcome outcome, String id) { this.outcome = outcome; responseId = id; }
    }
    private final String unit;
    private Phase phase = Phase.IDLE;
    private String responseId;
    private long expires;
    private boolean authorized;
    public PendingSlot(String unit) { this.unit = unit; }
    public synchronized void begin(boolean allowed) {
        if (phase != Phase.IDLE || !allowed) throw new IllegalStateException("RUN_NOT_ALLOWED");
        authorized = true; phase = Phase.PREPARING;
    }
    public synchronized boolean complete(String id, long deadline) {
        if (phase != Phase.PREPARING || !authorized) return false;
        responseId = id; expires = deadline; phase = Phase.READY; return true;
    }
    public synchronized void arm() {
        if (phase != Phase.READY) throw new IllegalStateException("NOT_READY");
        phase = Phase.LOADING;
    }
    /** Matcher must be bounded and non-destructive; never call a MAX listener under this lock. */
    public synchronized Claim claim(String id, long now, BooleanSupplier matcher) {
        if (!authorized) return new Claim(Outcome.UNAUTHORIZED, null);
        if (!unit.equals(id) || phase != Phase.LOADING || responseId == null)
            return new Claim(Outcome.MISSING, null);
        if (now >= expires) return new Claim(Outcome.EXPIRED, null);
        if (!matcher.getAsBoolean()) return new Claim(Outcome.MISMATCH, null);
        String claimed = responseId; responseId = null;
        return new Claim(Outcome.MATCH, claimed);
    }
    public synchronized String pending() { return responseId; }
    public synchronized boolean preparing() { return phase == Phase.PREPARING; }
    /** No reuse after timeout/finish; a fresh process is the experiment's recovery policy. */
    public synchronized String close() {
        authorized = false; phase = Phase.CLOSED;
        String unused = responseId; responseId = null; return unused;
    }
}
