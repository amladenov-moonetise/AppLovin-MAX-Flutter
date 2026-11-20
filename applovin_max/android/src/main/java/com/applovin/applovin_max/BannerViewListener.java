package com.applovin.applovin_max;

import com.applovin.mediation.*;
import com.applovin.mediation.ads.MaxAdView;

/** Overlay listener retains the exact view identity while preserving publisher callback forwarding. */
final class BannerViewListener implements MaxAdViewAdListener, MaxAdRevenueListener {
    private final AppLovinMAX plugin;
    private final MaxAdView view;
    private final boolean coordinated;
    BannerViewListener(AppLovinMAX plugin, MaxAdView view, boolean coordinated) {
        this.plugin = plugin; this.view = view; this.coordinated = coordinated;
    }
    private boolean current() { return plugin.bannerOperations.callbackAllowed(view, coordinated); }
    public void onAdLoaded(MaxAd ad) {
        if (!current()) return;
        try { plugin.bannerOperations.complete(view, ad); }
        finally { plugin.onAdLoaded(ad); }
    }
    public void onAdLoadFailed(String id, MaxError error) {
        if (!current()) return;
        try { plugin.bannerOperations.complete(view, null); }
        finally { plugin.onAdLoadFailed(id, error); }
    }
    public void onAdClicked(MaxAd ad) { if (current()) plugin.onAdClicked(ad); }
    public void onAdExpanded(MaxAd ad) { if (current()) plugin.onAdExpanded(ad); }
    public void onAdCollapsed(MaxAd ad) { if (current()) plugin.onAdCollapsed(ad); }
    public void onAdRevenuePaid(MaxAd ad) { if (current()) plugin.onAdRevenuePaid(ad); }
    public void onAdDisplayed(MaxAd ad) { if (current()) plugin.onAdDisplayed(ad); }
    public void onAdDisplayFailed(MaxAd ad, MaxError error) { if (current()) plugin.onAdDisplayFailed(ad, error); }
    public void onAdHidden(MaxAd ad) { if (current()) plugin.onAdHidden(ad); }
}
