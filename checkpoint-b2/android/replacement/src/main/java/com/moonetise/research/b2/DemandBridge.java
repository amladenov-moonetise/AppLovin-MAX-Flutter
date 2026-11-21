package com.moonetise.research.b2;

import android.os.SystemClock;
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters;
import com.applovin.mediation.adapters.prebid.ParametersChecker;
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse;
import org.prebid.mobile.rendering.bidding.display.BidResponseCache;

/** Research-only Asterise-owned provider. One unit and one run, no native MAX objects. */
public final class DemandBridge {
    public static final String UNIT = "d236de754b83a168";
    private static PendingSlot slot;
    private static long windowMs;
    private static String captured;
    private DemandBridge() {}
    public static synchronized void begin(boolean authorized, long eligibilityMs) {
        if (slot != null || eligibilityMs < 1000 || eligibilityMs > 30000)
            throw new IllegalStateException("FRESH_PROCESS_REQUIRED");
        slot = new PendingSlot(UNIT); windowMs = eligibilityMs; slot.begin(authorized);
    }
    /** Called by the public PrebidMediationDelegate after Prebid has cached the response. */
    public static synchronized void capture(BidResponse response) {
        if (response == null || response.getId() == null || response.getId().isEmpty()) return;
        String id = response.getId();
        if (id.equals(captured)) return;
        captured = id;
        boolean valid = response.isVideo() && "3.00".equals(response.getTargeting().get("hb_pb"))
                && response.getWinningBid() != null && response.getWinningBid().getPrice() == 3.0
                && BidResponseCache.getInstance().getKeywords(id) != null;
        long lifetime = windowMs;
        Integer seconds = response.getExpirationTimeSeconds();
        if (seconds != null) lifetime = Math.min(lifetime,
                seconds.longValue() * 1000L - Math.max(0, System.currentTimeMillis() - response.getCreationTime()));
        if (!valid || lifetime <= 0 || slot == null
                || !slot.complete(id, SystemClock.elapsedRealtime() + lifetime)) {
            BidResponseCache.getInstance().popBidResponse(id); // Only this delegate's unused response.
        }
    }
    public static synchronized boolean preparationFinished() {
        if (slot == null) throw new IllegalStateException("NO_RUN");
        if (slot.preparing()) slot.complete(null, 0);
        return slot.pending() != null;
    }
    public static synchronized void arm() {
        if (slot == null) throw new IllegalStateException("NO_RUN");
        slot.arm();
        B2Trace.record(B2Trace.Event.SLOT_ARMED, B2Trace.Result.SUCCESS, B2Trace.Category.NONE);
    }
    public static synchronized MaxAdapterResponseParameters claim(MaxAdapterResponseParameters original) {
        if (slot == null || original == null) {
            miss(B2Trace.Result.MISSING); return null;
        }
        String id = slot.pending();
        ResponseParameters wrapped = id == null ? null : new ResponseParameters(original, id);
        PendingSlot.Claim claim = slot.claim(original.getAdUnitId(), SystemClock.elapsedRealtime(),
                () -> wrapped != null && original.getCustomParameters() != null
                        && original.getCustomParameters().containsKey("hb_pb")
                        && ParametersChecker.getResponseIdAndCheckKeywords(wrapped,
                        (code, ignored) -> {}) != null);
        if (claim.outcome != PendingSlot.Outcome.MATCH) {
            B2Trace.Result result = claim.outcome == PendingSlot.Outcome.MISMATCH ? B2Trace.Result.MISMATCH
                    : claim.outcome == PendingSlot.Outcome.EXPIRED ? B2Trace.Result.EXPIRED : B2Trace.Result.MISSING;
            miss(result); return null;
        }
        B2Trace.record(B2Trace.Event.SLOT_CLAIM, B2Trace.Result.MATCH, B2Trace.Category.NONE);
        return wrapped;
    }
    private static void miss(B2Trace.Result result) {
        B2Trace.record(B2Trace.Event.PROVIDER_MISS, result,
                result == B2Trace.Result.MISMATCH ? B2Trace.Category.TARGETING : B2Trace.Category.NO_FILL);
    }
    public static synchronized void finish() {
        if (slot == null) return;
        String unused = slot.close();
        if (unused != null) BidResponseCache.getInstance().popBidResponse(unused);
        // A claimed response/controller belongs to Prebid/MAX; never destroy it here.
    }
}
