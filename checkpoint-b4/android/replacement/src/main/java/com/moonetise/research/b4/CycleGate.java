package com.moonetise.research.b4;

/** Three explicit, non-overlapping cycles. This token is internal, NOT a MAX request ID. */
public final class CycleGate {
    public enum State { IDLE, PREPARING, LOADING, LOADED, SHOWING, COMPLETE, UNRESOLVED }
    private long generation;
    private State state = State.IDLE;
    public synchronized long begin() {
        if (!canBegin()) throw new IllegalStateException("CYCLE_ACTIVE_OR_RESTART_REQUIRED");
        generation++; state = State.PREPARING; return generation;
    }
    public synchronized boolean canBegin() {
        return generation < 3 && (state == State.IDLE || state == State.COMPLETE);
    }
    public synchronized boolean current(long id) {
        return id == generation && id > 0 && state != State.UNRESOLVED;
    }
    public synchronized boolean preparing(long id) { return current(id) && state == State.PREPARING; }
    public synchronized boolean loading(long id) { return current(id) && state == State.LOADING; }
    public synchronized boolean loaded(long id) { return current(id) && state == State.LOADED; }
    public synchronized boolean showing(long id) { return current(id) && state == State.SHOWING; }
    public synchronized boolean requestLoad(long id) {
        if (!preparing(id)) return false;
        state = State.LOADING; return true;
    }
    public synchronized boolean terminal(long id, boolean success) {
        if (!loading(id)) return false;
        state = success ? State.LOADED : State.COMPLETE; return true;
    }
    public synchronized boolean show(long id) {
        if (!loaded(id)) return false;
        state = State.SHOWING; return true;
    }
    public synchronized boolean hidden(long id) {
        if (!showing(id)) return false;
        state = State.COMPLETE; return true;
    }
    public synchronized boolean probeComplete(long id) {
        if (!preparing(id)) return false;
        state = State.COMPLETE; return true;
    }
    public synchronized void unresolved(long id) {
        if (id == generation && state != State.COMPLETE) state = State.UNRESOLVED;
    }
    public synchronized State state() { return state; }
    public synchronized long generation() { return generation; }
}
