package com.applovin.applovin_max;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.MaxReward;
import com.applovin.mediation.MaxRewardedAdListener;
import com.applovin.mediation.ads.MaxRewardedAd;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Plugin-owned rewarded objects. No Prebid dependency or auction/load/show initiated by callbacks. */
final class RewardedOperations {
    private static final Map<String, java.lang.ref.WeakReference<MaxRewardedAd>> retiredWrappers =
            java.util.Collections.synchronizedMap(new HashMap<>());
    private final AppLovinMAX plugin;
    private final Map<String, MaxRewardedAd> objects;
    private final RewardedReservationRegistry registry =
            new RewardedReservationRegistry(android.os.SystemClock::elapsedRealtime);
    private final Map<RewardedReservationRegistry.Binding, RewardedPreparation> preparations = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int callbackDepth;
    private int sdkDepth;

    RewardedOperations(AppLovinMAX plugin, Map<String, MaxRewardedAd> objects) {
        this.plugin = plugin;
        this.objects = objects;
    }
    void attach() { registry.attach(); }
    void detach() { registry.detach(); cleanupRequested(); }
    void hostUnavailable() { registry.hostUnavailable(); cleanupRequested(); }

    MaxRewardedAd retrieve(String id) {
        MaxRewardedAd ad = objects.get(id);
        if (ad == null) {
            // The process guard is checked BEFORE getInstance or replacing any singleton listener.
            if (registry.isCoordinated(id)) throw new IllegalStateException("REWARDED_OWNER_CONFLICT");
            ad = plugin.createRewardedObject(id);
            java.lang.ref.WeakReference<MaxRewardedAd> previous = retiredWrappers.get(id);
            if (previous != null && previous.get() == ad)
                throw new IllegalStateException("REWARDED_RETIREMENT_UNVERIFIED");
            retiredWrappers.remove(id);
            objects.put(id, ad);
            registry.created(id, ad);
            RewardedReservationRegistry.Binding binding = registry.binding(id, ad);
            Callbacks callbacks = new Callbacks(binding);
            ad.setListener(callbacks);
            ad.setRevenueListener(callbacks);
            ad.setExpirationListener((expired, replacement) -> {
                registry.markUnsafe(binding);
                handler.post(this::cleanupRequested);
            });
        }
        return ad;
    }
    void requireUncoordinated(String id) {
        if (registry.isCoordinated(id)) throw new IllegalStateException("REWARDED_RESERVED");
    }
    String reserve(String id) {
        plugin.requireRewardedHost();
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}"))
            throw new IllegalArgumentException("INVALID_ARGUMENT");
        MaxRewardedAd ad = retrieve(id);
        return registry.reserve(id, ad, readReady(id, ad)).id;
    }
    private boolean readReady(String id, MaxRewardedAd ad) {
        try { return ad.isReady(); }
        catch (Exception error) { registry.markUnsafe(registry.binding(id, ad)); throw error; }
    }
    Access prepare(String id, String attempt, RewardedPreparation preparation) {
        plugin.requireRewardedHost();
        MaxRewardedAd ad = existing(id, attempt);
        registry.validateLoad(id, ad, attempt);
        RewardedReservationRegistry.Binding binding = registry.binding(id, ad);
        if (preparation == null || preparations.containsKey(binding) || !preparation.isAuthorized())
            throw new IllegalStateException("REWARDED_PREPARATION_REJECTED");
        preparations.put(binding, preparation);
        return new Access(binding, attempt, preparation);
    }
    private MaxRewardedAd existing(String id, @Nullable String attempt) {
        if (attempt == null && !registry.isCoordinated(id)) return retrieve(id);
        MaxRewardedAd ad = objects.get(id);
        if (ad == null) throw new IllegalStateException("STALE_ATTEMPT");
        return ad;
    }
    private void host(String id, @Nullable String attempt) {
        if (attempt != null || registry.isCoordinated(id)) plugin.requireRewardedHost();
    }
    private void prepared(RewardedReservationRegistry.Binding binding) {
        if (!registry.isCoordinated(binding.adUnitId)) return;
        RewardedPreparation preparation = preparations.get(binding);
        boolean allowed = false;
        try { allowed = preparation != null && preparation.isAuthorized() && preparation.isReady(); }
        catch (Exception ignored) {}
        if (!allowed) {
            registry.markUnsafe(binding);
            throw new IllegalStateException("REWARDED_PREPARATION_INVALIDATED");
        }
    }
    private void event(RewardedReservationRegistry.Binding binding, String event, @Nullable MaxAd ad) {
        RewardedPreparation preparation = preparations.get(binding);
        if (preparation != null) {
            try { preparation.onEvent(event, ad); }
            catch (Exception ignored) { registry.markUnsafe(binding); }
        }
    }
    void load(String id, @Nullable String attempt) {
        try {
            host(id, attempt);
            MaxRewardedAd ad = existing(id, attempt);
            RewardedReservationRegistry.Binding binding = registry.binding(id, ad);
            registry.validateLoad(id, ad, attempt);
            if (registry.isCoordinated(id) && readReady(id, ad)) {
                registry.markUnsafe(binding);
                throw new IllegalStateException("REWARDED_BUSY");
            }
            prepared(binding);
            event(binding, "LOAD_REQUESTED", null);
            prepared(binding);
            registry.beforeLoad(id, ad, attempt);
            sdkDepth++;
            try { ad.loadAd(); event(binding, "LOAD_DISPATCHED", null); }
            catch (Exception error) { registry.markUnsafe(binding); throw error; }
            finally { sdkDepth--; }
        } finally { cleanupRequested(); }
    }
    boolean isReady(String id) {
        try {
            host(id, null);
            MaxRewardedAd ad = existing(id, null);
            RewardedReservationRegistry.Binding binding = registry.binding(id, ad);
            if (registry.isCoordinated(id)) {
                RewardedPreparation preparation = preparations.get(binding);
                if (preparation == null) return false; // A readiness query cannot cancel pending preparation.
                try {
                    if (!preparation.isAuthorized()) { registry.markUnsafe(binding); return false; }
                    if (!preparation.isReady()) return false;
                } catch (Exception ignored) { registry.markUnsafe(binding); return false; }
            }
            return registry.canReportReady(id, ad, readReady(id, ad));
        } finally { cleanupRequested(); }
    }
    void show(String id, String placement, String customData, @Nullable String attempt) {
        try {
            host(id, attempt);
            MaxRewardedAd ad = existing(id, attempt);
            RewardedReservationRegistry.Binding binding = registry.binding(id, ad);
            boolean ready = !registry.isCoordinated(id) || readReady(id, ad);
            prepared(binding);
            event(binding, "SHOW_REQUESTED", null);
            prepared(binding);
            registry.beforeShow(id, ad, attempt, ready);
            sdkDepth++;
            try { ad.showAd(placement, customData); event(binding, "SHOW_DISPATCHED", null); }
            catch (Exception error) { registry.markUnsafe(binding); throw error; }
            finally { sdkDepth--; }
        } finally { cleanupRequested(); }
    }
    boolean retire(String id, String attempt) {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Platform thread required");
        return retireBinding(registry.requestRetirement(id, attempt));
    }
    private boolean invalidate(String id, String attempt) {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Platform thread required");
        return retireBinding(registry.requestInvalidation(id, attempt));
    }
    private boolean retireBinding(RewardedReservationRegistry.Binding binding) {
        if (callbackDepth != 0 || sdkDepth != 0) { handler.post(this::cleanupRequested); return false; }
        return cleanup(binding);
    }
    private void cleanupRequested() {
        if (Looper.myLooper() != Looper.getMainLooper() || callbackDepth != 0 || sdkDepth != 0) {
            handler.post(this::cleanupRequested);
            return;
        }
        for (RewardedReservationRegistry.Binding binding : registry.pendingCleanup()) {
            try { cleanup(binding); } catch (Exception ignored) { /* Quarantined, never retried. */ }
        }
    }
    private boolean cleanup(RewardedReservationRegistry.Binding binding) {
        switch (registry.beginCleanup(binding)) {
            case RETIRED: return true;
            case DEFERRED: return false;
            case FAILED: throw new IllegalStateException("REWARDED_CLEANUP_FAILED");
            case READY: break;
            default: throw new IllegalStateException("Unknown cleanup state");
        }
        try {
            MaxRewardedAd ad = (MaxRewardedAd) binding.object;
            RewardedPreparation preparation = preparations.get(binding);
            if (preparation != null) preparation.clear(ad);
            ad.setListener(null);
            ad.setRevenueListener(null);
            ad.setExpirationListener(null);
            ad.destroy(); // Public API retires the cached wrapper; never acquire a second one here.
            retiredWrappers.put(binding.adUnitId, new java.lang.ref.WeakReference<>(ad));
            if (objects.get(binding.adUnitId) == ad) objects.remove(binding.adUnitId);
            preparations.remove(binding);
            registry.cleanupFinished(binding, true);
            return true;
        } catch (Exception ignored) {
            registry.cleanupFinished(binding, false);
            throw new IllegalStateException("REWARDED_CLEANUP_FAILED");
        }
    }
    final class Access {
        private final RewardedReservationRegistry.Binding binding;
        private final String attempt;
        private final RewardedPreparation preparation;
        Access(RewardedReservationRegistry.Binding binding, String attempt, RewardedPreparation preparation) {
            this.binding = binding; this.attempt = attempt; this.preparation = preparation;
        }
        boolean isCurrent() {
            return Looper.myLooper() == Looper.getMainLooper() && preparations.get(binding) == preparation &&
                    registry.isCurrent(binding.adUnitId, binding.object, attempt, binding.generation, binding.epoch);
        }
        boolean withCurrentObject(Consumer<MaxRewardedAd> write) {
            if (!isCurrent() || !preparation.isAuthorized()) return false;
            plugin.requireRewardedHost();
            registry.validateLoad(binding.adUnitId, binding.object, attempt);
            write.accept((MaxRewardedAd) binding.object);
            return isCurrent();
        }
        boolean retire() { return RewardedOperations.this.retire(binding.adUnitId, attempt); }
        boolean invalidate() { return RewardedOperations.this.invalidate(binding.adUnitId, attempt); }
    }
    private final class Callbacks implements MaxRewardedAdListener, MaxAdRevenueListener {
        private final RewardedReservationRegistry.Binding binding;
        Callbacks(RewardedReservationRegistry.Binding binding) { this.binding = binding; }
        private void deliver(String id, @Nullable MaxAd ad, RewardedReservationRegistry.Event kind, Runnable forward) {
            Runnable delivery = () -> {
                if (!binding.adUnitId.equals(id) || ad != null && ad.getFormat() != MaxAdFormat.REWARDED) return;
                boolean accepted = registry.acceptCallback(binding, kind);
                callbackDepth++;
                try {
                    if (accepted) event(binding, kind.name(), ad);
                    if (accepted && registry.canForwardToEngine(binding)) forward.run();
                } finally {
                    callbackDepth--;
                    handler.post(RewardedOperations.this::cleanupRequested);
                }
            };
            if (Looper.myLooper() == Looper.getMainLooper()) delivery.run(); else handler.post(delivery);
        }
        @Override public void onAdLoaded(MaxAd ad) {
            deliver(ad.getAdUnitId(), ad, RewardedReservationRegistry.Event.LOADED, () -> plugin.onAdLoaded(ad));
        }
        @Override public void onAdLoadFailed(String id, MaxError error) {
            deliver(id, null, RewardedReservationRegistry.Event.LOAD_FAILED, () -> plugin.onAdLoadFailed(id, error));
        }
        @Override public void onAdDisplayed(MaxAd ad) {
            deliver(ad.getAdUnitId(), ad, RewardedReservationRegistry.Event.DISPLAYED, () -> plugin.onAdDisplayed(ad));
        }
        @Override public void onAdDisplayFailed(MaxAd ad, MaxError error) {
            deliver(ad.getAdUnitId(), ad, RewardedReservationRegistry.Event.DISPLAY_FAILED,
                    () -> plugin.onAdDisplayFailed(ad, error));
        }
        @Override public void onAdHidden(MaxAd ad) {
            deliver(ad.getAdUnitId(), ad, RewardedReservationRegistry.Event.HIDDEN, () -> plugin.onAdHidden(ad));
        }
        @Override public void onAdClicked(MaxAd ad) {
            deliver(ad.getAdUnitId(), ad, RewardedReservationRegistry.Event.CLICKED, () -> plugin.onAdClicked(ad));
        }
        @Override public void onAdRevenuePaid(MaxAd ad) {
            deliver(ad.getAdUnitId(), ad, RewardedReservationRegistry.Event.REVENUE, () -> plugin.onAdRevenuePaid(ad));
        }
        @Override public void onUserRewarded(MaxAd ad, MaxReward reward) {
            // The ONLY reward source. Forward original MAX metadata once; never grant currency.
            deliver(ad.getAdUnitId(), ad, RewardedReservationRegistry.Event.REWARD, () -> plugin.onUserRewarded(ad, reward));
        }
    }
}
