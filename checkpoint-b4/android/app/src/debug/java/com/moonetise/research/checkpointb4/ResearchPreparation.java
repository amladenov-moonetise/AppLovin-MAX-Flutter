package com.moonetise.research.checkpointb4;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import java.util.HashMap;
import com.moonetise.research.b4.RewardedDemandBridge;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.api.mediation.MediationRewardedVideoAdUnit;
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse;
import org.prebid.mobile.rendering.bidding.display.PrebidMediationDelegate;

/** Debug-only fixed approved synthetic fixture. Absent from the release source set. */
final class ResearchPreparation {
    interface Callback { void complete(String result); }
    private final Context context;
    private final long generation;
    private final Callback callback;
    private MediationRewardedVideoAdUnit unit;
    private volatile boolean stopped;
    ResearchPreparation(Context context, long generation, Callback callback) {
        this.context = context; this.generation = generation; this.callback = callback;
    }
    void start() {
        if (!"com.moonetise.asterise.sample".equals(context.getPackageName())
                || (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0)
            throw new IllegalStateException("DEBUG_HOST_REQUIRED");
        PrebidMobile.setStoredAuctionResponse("moonetise-test-video-rewarded-3usd");
        unit = new MediationRewardedVideoAdUnit(context, "moonetise-mobile-rewarded-test",
                new PrebidMediationDelegate() {
            public void handleKeywordsUpdate(HashMap<String, String> keywords) {}
            public boolean canPerformRefresh() { return false; }
            public void setResponseToLocalExtras(BidResponse response) {
                RewardedDemandBridge.capture(generation, response);
            }
        });
        unit.fetchDemand(result -> {
            if (!stopped) callback.complete(result.name());
        });
    }
    void stop() {
        stopped = true;
        MediationRewardedVideoAdUnit old = unit; unit = null;
        if (old != null) try { old.destroy(); } catch (RuntimeException ignored) {}
        clearOverride();
    }
    static void clearOverride() { PrebidMobile.setStoredAuctionResponse(null); }
}
