package com.moonetise.research.b5;

import android.os.Bundle;
import android.os.SystemClock;
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters;
import com.applovin.mediation.adapters.prebid.ParametersChecker;
import java.util.Collections;
import java.util.Map;
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse;
import org.prebid.mobile.rendering.bidding.display.BidResponseCache;

/** Rewarded-only provider entry point and fixed rewarded MAX unit. Separate from interstitial. No MAX ad object or listener ownership. */
public final class RewardedDemandBridge {
    public static final String UNIT = "691ff46cc460ce8e";
    private static PendingSlot slot;
    private static long generation, windowMs;
    private static String captured;
    private static boolean retired = true;
    private RewardedDemandBridge() {}
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
            B5Trace.recordFor(id, B5Trace.Event.STALE_CALLBACK, B5Trace.Result.LATE_IGNORED, B5Trace.Category.NONE);
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
            B5Trace.recordFor(id, B5Trace.Event.CAPTURED, B5Trace.Result.FRESH, B5Trace.Category.NONE);
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
        B5Trace.recordFor(id, B5Trace.Event.SLOT_ARMED,
                retired ? B5Trace.Result.MISSING : B5Trace.Result.SUCCESS, B5Trace.Category.NONE);
    }
    public static synchronized MaxAdapterResponseParameters claim(MaxAdapterResponseParameters original) {
        if (slot == null || retired || original == null) { miss(B5Trace.Result.MISSING); return null; }
        String key = slot.pending();
        ResponseParameters wrapped = key == null ? null : new ResponseParameters(original, key);
        PendingSlot.Claim claim = slot.claim(original.getAdUnitId(), SystemClock.elapsedRealtime(),
                () -> wrapped != null && original.getCustomParameters() != null
                        && original.getCustomParameters().containsKey("hb_pb")
                        && ParametersChecker.getResponseIdAndCheckKeywords(wrapped, (code, ignored) -> {}) != null);
        if (claim.outcome != PendingSlot.Outcome.MATCH) {
            miss(claim.outcome == PendingSlot.Outcome.MISMATCH ? B5Trace.Result.MISMATCH
                    : claim.outcome == PendingSlot.Outcome.EXPIRED ? B5Trace.Result.EXPIRED : B5Trace.Result.MISSING);
            return null;
        }
        B5Trace.recordFor(generation, B5Trace.Event.SLOT_CLAIM, B5Trace.Result.MATCH, B5Trace.Category.NONE);
        return wrapped;
    }
    private static void miss(B5Trace.Result result) {
        B5Trace.recordFor(generation, B5Trace.Event.PROVIDER_MISS, result,
                result == B5Trace.Result.MISMATCH ? B5Trace.Category.TARGETING : B5Trace.Category.NO_FILL);
    }
    public static synchronized void finish(long id) {
        if (id != generation || slot == null || retired) return;
        retired = true;
        String unused = slot.close();
        if (unused != null) {
            BidResponseCache.getInstance().popBidResponse(unused);
            B5Trace.recordFor(id, B5Trace.Event.OWNED_CACHE_CLEARED,
                    BidResponseCache.getInstance().getKeywords(unused) == null
                            ? B5Trace.Result.SUCCESS : B5Trace.Result.FAILED, B5Trace.Category.NONE);
        }
        // Never destroy a claimed Prebid controller/loaded MAX creative.
    }
    public static synchronized void revoke(long id, B5Trace.Result reason) {
        if (id != generation) return;
        finish(id);
        B5Trace.recordFor(id, B5Trace.Event.CONTRIBUTION_CLEARED, reason, B5Trace.Category.AUTHORIZATION);
    }
}
