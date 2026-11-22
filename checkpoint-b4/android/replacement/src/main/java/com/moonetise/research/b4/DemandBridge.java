package com.moonetise.research.b4;

import android.os.Bundle;
import android.os.SystemClock;
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters;
import com.applovin.mediation.adapters.prebid.ParametersChecker;
import java.util.Collections;
import java.util.Map;
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse;
import org.prebid.mobile.rendering.bidding.display.BidResponseCache;

/** One optional owned response for the current cycle. No MAX ad object or listener ownership. */
public final class DemandBridge {
    public static final String UNIT = "d236de754b83a168";
    private static PendingSlot slot;
    private static long generation, windowMs;
    private static String captured;
    private static boolean retired = true;
    private DemandBridge() {}
    public static synchronized void begin(long id, boolean authorized, long eligibilityMs) {
        if (!retired || id <= generation || eligibilityMs < 1000 || eligibilityMs > 30000)
            throw new IllegalStateException("ACTIVE_OR_INVALID_CYCLE");
        generation = id; slot = new PendingSlot(UNIT);
        captured = null; retired = false; windowMs = eligibilityMs; slot.begin(authorized);
    }
    public static synchronized void capture(long id, BidResponse response) {
        if (response == null || response.getId() == null || response.getId().isEmpty()) return;
        String key = response.getId();
        if (id != generation || retired || !slot.preparing()) {
            // Do not remove a key currently owned/claimed by the newer cycle.
            if (!key.equals(captured)) BidResponseCache.getInstance().popBidResponse(key);
            B4Trace.recordFor(id, B4Trace.Event.STALE_CALLBACK, B4Trace.Result.LATE_IGNORED, B4Trace.Category.NONE);
            return;
        }
        if (key.equals(captured)) return;
        captured = key;
        boolean valid = response.isVideo() && "3.00".equals(response.getTargeting().get("hb_pb"))
                && response.getWinningBid() != null && response.getWinningBid().getPrice() == 3.0
                && BidResponseCache.getInstance().getKeywords(key) != null;
        long lifetime = windowMs;
        Integer seconds = response.getExpirationTimeSeconds();
        if (seconds != null) lifetime = Math.min(lifetime,
                seconds.longValue() * 1000 - Math.max(0, System.currentTimeMillis() - response.getCreationTime()));
        if (valid && lifetime > 0 && slot.complete(key, SystemClock.elapsedRealtime() + lifetime)) {
            B4Trace.recordFor(id, B4Trace.Event.CAPTURED, B4Trace.Result.FRESH, B4Trace.Category.NONE);
        } else BidResponseCache.getInstance().popBidResponse(key);
    }
    public static synchronized boolean preparationFinished(long id) {
        if (id != generation || retired) return false;
        if (slot.preparing()) slot.complete(null, 0);
        return slot.pending() != null;
    }
    public static synchronized void arm(long id) {
        if (id != generation) throw new IllegalStateException("STALE_CYCLE");
        if (!retired) slot.arm(); // Revoked contribution still permits the publisher's MAX-only load.
        B4Trace.recordFor(id, B4Trace.Event.SLOT_ARMED,
                retired ? B4Trace.Result.MISSING : B4Trace.Result.SUCCESS, B4Trace.Category.NONE);
    }
    public static synchronized MaxAdapterResponseParameters claim(MaxAdapterResponseParameters original) {
        if (slot == null || retired || original == null) { miss(B4Trace.Result.MISSING); return null; }
        String key = slot.pending();
        ResponseParameters wrapped = key == null ? null : new ResponseParameters(original, key);
        PendingSlot.Claim claim = slot.claim(original.getAdUnitId(), SystemClock.elapsedRealtime(),
                () -> wrapped != null && original.getCustomParameters() != null
                        && original.getCustomParameters().containsKey("hb_pb")
                        && ParametersChecker.getResponseIdAndCheckKeywords(wrapped, (code, ignored) -> {}) != null);
        if (claim.outcome != PendingSlot.Outcome.MATCH) {
            miss(claim.outcome == PendingSlot.Outcome.MISMATCH ? B4Trace.Result.MISMATCH
                    : claim.outcome == PendingSlot.Outcome.EXPIRED ? B4Trace.Result.EXPIRED : B4Trace.Result.MISSING);
            return null;
        }
        B4Trace.recordFor(generation, B4Trace.Event.SLOT_CLAIM, B4Trace.Result.MATCH, B4Trace.Category.NONE);
        return wrapped;
    }
    private static void miss(B4Trace.Result result) {
        B4Trace.recordFor(generation, B4Trace.Event.PROVIDER_MISS, result,
                result == B4Trace.Result.MISMATCH ? B4Trace.Category.TARGETING : B4Trace.Category.NO_FILL);
    }
    public static synchronized void finish(long id) {
        if (id != generation || slot == null || retired) return;
        retired = true;
        String unused = slot.close();
        if (unused != null) {
            BidResponseCache.getInstance().popBidResponse(unused);
            B4Trace.recordFor(id, B4Trace.Event.OWNED_CACHE_CLEARED,
                    BidResponseCache.getInstance().getKeywords(unused) == null
                            ? B4Trace.Result.SUCCESS : B4Trace.Result.FAILED, B4Trace.Category.NONE);
        }
        // Never destroy a claimed Prebid controller/loaded MAX creative.
    }
    public static synchronized void revoke(long id, B4Trace.Result reason) {
        if (id != generation) return;
        finish(id);
        B4Trace.recordFor(id, B4Trace.Event.CONTRIBUTION_CLEARED, reason, B4Trace.Category.AUTHORIZATION);
    }
    /** SDK-level probe only. Never edits MAX configuration or invokes a MAX load. */
    public static synchronized boolean tierProbe(long id, String existingTier) {
        if (id != generation || retired || existingTier == null
                || !existingTier.matches("(0|[1-9][0-9]{0,2})\\.[0-9]{2}")
                || "3.00".equals(existingTier)) return false;
        String key = slot.pending();
        if (key == null) return false;
        boolean mismatch = claim(new ProbeParameters(existingTier)) == null
                && key.equals(slot.pending()) && BidResponseCache.getInstance().getKeywords(key) != null;
        MaxAdapterResponseParameters matching = mismatch ? claim(new ProbeParameters("3.00")) : null;
        BidResponse response = matching == null ? null : ParametersChecker.getBidResponse(key, (code, ignored) -> {});
        if (response != null)
            B4Trace.recordFor(id, B4Trace.Event.CACHE_POP, B4Trace.Result.CONSUMED, B4Trace.Category.NONE);
        boolean once = response != null && claim(new ProbeParameters("3.00")) == null
                && BidResponseCache.getInstance().getKeywords(key) == null;
        return mismatch && once;
    }
    private static final class ProbeParameters implements MaxAdapterResponseParameters {
        private final Bundle custom = new Bundle();
        ProbeParameters(String price) { custom.putString("hb_pb", price); }
        public String getAdUnitId() { return UNIT; }
        public Map<String, Object> getLocalExtraParameters() { return Collections.emptyMap(); }
        public Bundle getCustomParameters() { return custom; }
        public Bundle getServerParameters() { return new Bundle(); }
        public String getThirdPartyAdPlacementId() { return null; }
        public String getBidResponse() { return null; }
        public long getBidExpirationMillis() { return 0; }
        public Boolean hasUserConsent() { return null; }
        public Boolean isAgeRestrictedUser() { return null; }
        public Boolean isDoNotSell() { return null; }
        public String getConsentString() { return null; }
        public boolean isTesting() { return false; }
    }
}
