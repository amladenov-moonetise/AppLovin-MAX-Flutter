package com.applovin.applovin_max;

/** One exact native view, one preparation per explicit load. No Android/SDK dependencies. */
final class BannerCycle {
    private final Object view;
    private final long epoch;
    private long cycle;
    private boolean alive = true;
    private boolean loading;
    private boolean consumed;
    private boolean preparing;
    private boolean invalid;
    BannerCycle(Object view, long epoch) { this.view = view; this.epoch = epoch; }
    long begin(Object candidate, long currentEpoch) {
        requireView(candidate, currentEpoch);
        if (loading || preparing) throw new IllegalStateException("BANNER_BUSY");
        invalid = false; consumed = false; preparing = true;
        return ++cycle;
    }
    boolean current(Object candidate, long currentEpoch, long token) {
        return alive && candidate == view && currentEpoch == epoch && token == cycle && cycle > 0 && !invalid;
    }
    boolean writable(Object candidate, long currentEpoch, long token) {
        return current(candidate, currentEpoch, token) && preparing && !consumed;
    }
    void consume(Object candidate, long currentEpoch, long token) {
        if (!writable(candidate, currentEpoch, token)) throw new IllegalStateException("BANNER_STALE_CYCLE");
        preparing = false; consumed = true; loading = true;
    }
    boolean complete(Object candidate, long currentEpoch) {
        if (!alive || candidate != view || currentEpoch != epoch || !loading) return false;
        loading = false; return true;
    }
    void invalidate() { invalid = true; preparing = false; }
    void destroy() { invalidate(); alive = false; }
    private void requireView(Object candidate, long currentEpoch) {
        if (!alive || candidate != view || currentEpoch != epoch)
            throw new IllegalStateException("BANNER_STALE_VIEW");
    }
}
