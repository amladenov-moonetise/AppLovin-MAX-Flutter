package com.applovin.applovin_max;

import android.os.Looper;
import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.ads.MaxAdView;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Opt-in hooks shared by existing overlay/widget owners. Never constructs or loads advertisements. */
final class BannerOperations {
    private final Map<String, BannerPreparation> providers = new HashMap<>();
    private final Map<MaxAdView, Binding> views = new IdentityHashMap<>();
    private final java.util.Set<MaxAdView> retired = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private long epoch;
    private boolean attached;
    void attach() { main(); attached = true; epoch++; }
    void detach() {
        main(); attached = false; epoch++;
        for (Binding b : views.values().toArray(new Binding[0])) {
            try { release(b.view); } catch (RuntimeException ignored) { /* Each old binding is already sealed. */ }
        }
        providers.clear();
    }
    void register(String id, BannerPreparation provider) {
        main();
        if (!attached || provider == null || providers.containsKey(id))
            throw new IllegalStateException("BANNER_OWNER_CONFLICT");
        providers.put(id, provider);
    }
    void unregister(String id, BannerPreparation provider) {
        main();
        for (Binding b : views.values()) if (b.id.equals(id)) throw new IllegalStateException("BANNER_VIEW_STILL_OWNED");
        if (providers.get(id) == provider) providers.remove(id);
    }
    void abandon(String id, BannerPreparation provider) {
        main();
        if (providers.get(id) != provider) return;
        for (Binding b : views.values().toArray(new Binding[0])) if (b.id.equals(id)) release(b.view);
        providers.remove(id);
    }
    boolean managed(String id) { return providers.containsKey(id); }
    void beforeCreate(String id) {
        main();
        if (!managed(id)) return;
        if (!attached) throw new IllegalStateException("BANNER_ENGINE_DETACHED");
        for (Binding b : views.values()) if (b.id.equals(id)) throw new IllegalStateException("BANNER_VIEW_ALREADY_EXISTS");
    }
    void bind(String id, MaxAdView view, MaxAdFormat format, boolean adaptive) {
        main();
        BannerPreparation provider = providers.get(id);
        if (provider == null || views.containsKey(view)) return;
        if (!format.isBannerOrLeaderAd()) throw new IllegalStateException("BANNER_FORMAT_REQUIRED");
        beforeCreate(id);
        Binding binding = new Binding(id, view, format, adaptive, provider);
        views.put(view, binding);
        binding.watch();
        view.stopAutoRefresh();
        try { provider.onViewCreated(binding); }
        catch (RuntimeException error) { binding.invalidate(); throw error; }
    }
    boolean deferInitialLoad(MaxAdView view) { main(); return views.containsKey(view); }
    boolean callbackAllowed(MaxAdView view, boolean coordinated) {
        main(); return attached && (!coordinated || views.containsKey(view));
    }
    void beforeLoad(MaxAdView view, String handle) {
        main();
        if (!attached || retired.contains(view)) throw new IllegalStateException("BANNER_STALE_VIEW");
        Binding b = views.get(view);
        if (b == null) return;
        if (handle == null || !handle.equals(b.handle)) throw new IllegalStateException("BANNER_STALE_CYCLE");
        if (!b.provider.isAuthorized() || !b.provider.isReady()) throw new IllegalStateException("BANNER_NOT_PREPARED");
        b.guard.consume(view, epoch, b.token); // Seal writes before any SDK call/reentrant callback.
        view.stopAutoRefresh();
        b.provider.onEvent("MAX_LOAD_REQUESTED", null);
    }
    void beforeShow(MaxAdView view) {
        main();
        if (!attached || retired.contains(view)) throw new IllegalStateException("BANNER_STALE_VIEW");
        Binding b = views.get(view);
        if (b != null && (!b.guard.current(view, epoch, b.token) || !b.provider.isDisplayAuthorized()))
            throw new IllegalStateException("BANNER_NOT_AUTHORIZED");
    }
    void afterLoad(MaxAdView view) {
        main();
        Binding b = views.get(view);
        if (b != null) b.provider.onEvent("MAX_LOAD_DISPATCHED", null);
    }
    void invalidateView(MaxAdView view) {
        main();
        Binding b = views.get(view);
        if (b != null) b.invalidate();
    }
    void complete(MaxAdView view, MaxAd ad) {
        main();
        Binding b = views.get(view);
        if (b != null && b.guard.complete(view, epoch)) {
            view.stopAutoRefresh();
            // The waterfall has finished; response is no longer needed for this cycle.
            try {
                b.provider.clear(view);
                b.provider.onEvent(ad == null ? "MAX_LOAD_FAILED" : "MAX_LOADED", ad);
            } catch (RuntimeException ignored) {
                b.guard.invalidate(); // Quarantine; never suppress the real publisher MAX callback.
            }
        }
    }
    void release(MaxAdView view) {
        main();
        Binding b = views.remove(view);
        if (b == null) return;
        retired.add(view);
        b.unwatch();
        b.guard.destroy(); // Seal callbacks before clearing attempt-owned extras/cache.
        view.stopAutoRefresh();
        b.provider.clear(view);
        b.provider.onEvent("VIEW_DISPOSED", null);
    }
    private final class Binding implements BannerPreparation.Access {
        final String id;
        final MaxAdView view;
        final MaxAdFormat format;
        final BannerPreparation provider;
        final boolean adaptive;
        final BannerCycle guard;
        long token;
        String handle;
        final android.graphics.Rect visibleRect = new android.graphics.Rect();
        Boolean lastEligible;
        android.view.ViewTreeObserver watchedTree;
        final android.view.ViewTreeObserver.OnPreDrawListener draw = () -> { notifyEligibility(); return true; };
        final android.view.ViewTreeObserver.OnWindowFocusChangeListener focus = focused -> notifyEligibility();
        final android.view.View.OnAttachStateChangeListener attachment = new android.view.View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(android.view.View v) { watchTree(); notifyEligibility(); }
            public void onViewDetachedFromWindow(android.view.View v) { notifyEligibility(); }
        };
        Binding(String id, MaxAdView view, MaxAdFormat format, boolean adaptive, BannerPreparation provider) {
            this.id = id; this.view = view; this.format = format; this.adaptive = adaptive; this.provider = provider;
            guard = new BannerCycle(view, epoch);
        }
        void watch() { view.addOnAttachStateChangeListener(attachment); watchTree(); }
        void watchTree() {
            unwatchTree();
            watchedTree = view.getViewTreeObserver();
            watchedTree.addOnPreDrawListener(draw);
            watchedTree.addOnWindowFocusChangeListener(focus);
        }
        void unwatchTree() {
            if (watchedTree != null && watchedTree.isAlive()) {
                watchedTree.removeOnPreDrawListener(draw);
                watchedTree.removeOnWindowFocusChangeListener(focus);
            }
            watchedTree = null;
        }
        void unwatch() { view.removeOnAttachStateChangeListener(attachment); unwatchTree(); }
        void notifyEligibility() {
            boolean eligible = isRefreshEligible();
            if (lastEligible != null && lastEligible == eligible) return;
            lastEligible = eligible;
            try { provider.onEvent("VIEW_ELIGIBILITY_CHANGED", null); }
            catch (RuntimeException ignored) { guard.invalidate(); }
        }
        public boolean isRefreshEligible() {
            main();
            if (!attached || views.get(view) != this || !view.isAttachedToWindow() ||
                !view.isShown() || !view.hasWindowFocus() || view.getWindowVisibility() != android.view.View.VISIBLE ||
                view.getWidth() <= 0 || view.getHeight() <= 0) return false;
            android.view.View ancestor = view;
            while (ancestor != null) {
                if (ancestor.getAlpha() <= 0) return false;
                android.view.ViewParent parent = ancestor.getParent();
                ancestor = parent instanceof android.view.View ? (android.view.View) parent : null;
            }
            return view.getGlobalVisibleRect(visibleRect) &&
                visibleRect.width() >= view.getWidth() && visibleRect.height() >= view.getHeight();
        }
        public int getWidthDp() {
            main();
            if (!adaptive) return format.getSize().getWidth();
            return Math.round(view.getWidth() / view.getResources().getDisplayMetrics().density);
        }
        public int getHeightDp() {
            main();
            int width = getWidthDp();
            return adaptive && width > 0 ? format.getAdaptiveSize(width, view.getContext()).getHeight()
                : format.getSize().getHeight();
        }
        public String getLoadHandle() {
            main();
            return isCurrent(token) && guard.writable(view, epoch, token) ? handle : null;
        }
        public long begin() {
            main();
            if (!provider.isAuthorized()) throw new IllegalStateException("BANNER_NOT_AUTHORIZED");
            long next = guard.begin(view, epoch);
            try { provider.clear(view); }
            catch (RuntimeException error) { guard.invalidate(); throw error; }
            token = next;
            handle = java.util.UUID.randomUUID().toString();
            return token;
        }
        public boolean isCurrent(long cycle) {
            main();
            return attached && views.get(view) == this && guard.current(view, epoch, cycle);
        }
        public boolean withCurrentObject(long cycle, java.util.function.Consumer<MaxAdView> write) {
            main();
            if (!isCurrent(cycle) || !guard.writable(view, epoch, cycle) || !provider.isAuthorized()) return false;
            write.accept(view);
            return true;
        }
        public void invalidate() {
            main();
            guard.invalidate();
            view.stopAutoRefresh();
            provider.clear(view);
            provider.onEvent("CYCLE_INVALIDATED", null);
        }
    }
    private static void main() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("BANNER_WRONG_THREAD");
    }
}
