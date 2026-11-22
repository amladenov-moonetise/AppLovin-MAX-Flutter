package com.moonetise.research.checkpointb5;

import android.content.Context;

/** Non-debuggable release: no synthetic setter, fixture ID, auction, or activation path. */
final class ResearchPreparation {
    interface Callback { void complete(String result); }
    ResearchPreparation(Context context, long generation, Callback callback) {}
    void start() { throw new IllegalStateException("SYNTHETIC_DISABLED_IN_RELEASE"); }
    void stop() {}
    static void clearOverride() {}
}
