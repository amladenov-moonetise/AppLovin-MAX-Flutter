package com.moonetise.research.checkpointb5;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import java.util.HashMap;
import com.moonetise.research.b5.BannerDemandBridge;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.AdSize;
import org.prebid.mobile.api.mediation.MediationBannerAdUnit;
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse;
import org.prebid.mobile.rendering.bidding.display.PrebidMediationDelegate;

/** Debug-only fixed approved synthetic fixture. Absent from the release source set. */
final class ResearchPreparation {
    interface Callback { void complete(String result); }
    private final Context context;
    private final long generation;
    private final Callback callback;
    private MediationBannerAdUnit unit;
    private volatile boolean stopped;
    ResearchPreparation(Context context, long generation, Callback callback) {
        this.context = context; this.generation = generation; this.callback = callback;
    }
    void start() {
        if (!"com.moonetise.asterise.sample".equals(context.getPackageName())
                || (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0)
            throw new IllegalStateException("DEBUG_HOST_REQUIRED");
        PrebidMobile.setStoredAuctionResponse("moonetise-test-display-banner-3usd");
        unit = new MediationBannerAdUnit(context, "moonetise-mobile-banner-test", new AdSize(320, 50),
                new PrebidMediationDelegate() {
            public void handleKeywordsUpdate(HashMap<String, String> keywords) {}
            public boolean canPerformRefresh() { return false; }
            public void setResponseToLocalExtras(BidResponse response) {
                BannerDemandBridge.capture(generation, response);
            }
        });
        unit.setRefreshInterval(0); unit.stopRefresh();
        unit.fetchDemand(result -> {
            if (unit != null) unit.stopRefresh();
            if (!stopped) callback.complete(result.name());
        });
        if (unit != null) unit.stopRefresh();
    }
    void stop() {
        stopped = true;
        MediationBannerAdUnit old = unit; unit = null;
        if (old != null) try { old.stopRefresh(); old.destroy(); } catch (RuntimeException ignored) {}
        clearOverride();
    }
    static void clearOverride() { PrebidMobile.setStoredAuctionResponse(null); }
}
