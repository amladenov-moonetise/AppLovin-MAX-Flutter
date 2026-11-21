package com.moonetise.research.checkpointb3;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import java.util.EnumSet;
import java.util.HashMap;
import com.moonetise.research.b3.DemandBridge;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.api.data.AdUnitFormat;
import org.prebid.mobile.api.mediation.MediationInterstitialAdUnit;
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse;
import org.prebid.mobile.rendering.bidding.display.PrebidMediationDelegate;

/** Debug-only fixed approved synthetic fixture. Absent from the release source set. */
final class ResearchPreparation {
    interface Callback { void complete(String result); }
    private final Context context;
    private final long generation;
    private final Callback callback;
    private MediationInterstitialAdUnit unit;
    private volatile boolean stopped;
    ResearchPreparation(Context context, long generation, Callback callback) {
        this.context = context; this.generation = generation; this.callback = callback;
    }
    void start() {
        if (!"com.moonetise.asterise.sample".equals(context.getPackageName())
                || (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0)
            throw new IllegalStateException("DEBUG_HOST_REQUIRED");
        PrebidMobile.setStoredAuctionResponse("moonetise-test-video-interstitial-3usd");
        unit = new MediationInterstitialAdUnit(context, "moonetise-mobile-interstitial-test",
                EnumSet.of(AdUnitFormat.BANNER, AdUnitFormat.VIDEO), new PrebidMediationDelegate() {
            public void handleKeywordsUpdate(HashMap<String, String> keywords) {}
            public boolean canPerformRefresh() { return false; }
            public void setResponseToLocalExtras(BidResponse response) {
                DemandBridge.capture(generation, response);
            }
        });
        unit.fetchDemand(result -> {
            if (!stopped) callback.complete(result.name());
        });
    }
    void stop() {
        stopped = true;
        MediationInterstitialAdUnit old = unit; unit = null;
        if (old != null) try { old.destroy(); } catch (RuntimeException ignored) {}
        clearOverride();
    }
    static void clearOverride() { PrebidMobile.setStoredAuctionResponse(null); }
}
