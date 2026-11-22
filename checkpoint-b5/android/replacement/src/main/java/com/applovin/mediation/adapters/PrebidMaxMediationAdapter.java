/*
 * Based on Prebid Mobile Android MAX adapter 3.4.1, Apache-2.0.
 * Moonetise changes: bounded initialization and prepared-response providers for interstitial, rewarded and fixed-size banner.
 * Research only. Native-ad interface remains present but returns no-fill.
 */
package com.applovin.mediation.adapters;

import android.app.Activity;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.adapter.*;
import com.applovin.mediation.adapter.listeners.*;
import com.applovin.mediation.adapter.parameters.*;
import com.applovin.mediation.adapters.prebid.managers.MaxInterstitialManager;
import com.applovin.sdk.AppLovinSdk;
import com.moonetise.research.b5.*;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.TargetingParams;
import org.prebid.mobile.rendering.bidding.display.BidResponseCache;

public class PrebidMaxMediationAdapter extends MediationAdapterBase
        implements MaxAdViewAdapter, MaxInterstitialAdapter, MaxRewardedAdapter, MaxNativeAdAdapter {
    public static final String TAG = "PrebidMaxMediationAdapter";
    public static final String EXTRA_RESPONSE_ID = TAG + "ExtraResponseId";
    public static final String EXTRA_KEYWORDS_ID = TAG + "ExtraKeywordsId";
    private MaxInterstitialManager manager;
    private com.applovin.mediation.adapters.prebid.managers.MaxBannerManager bannerManager;
    private com.applovin.mediation.adapters.prebid.managers.MaxRewardedManager rewardedManager;
    public PrebidMaxMediationAdapter(AppLovinSdk sdk) {
        super(sdk);
        B5Trace.record(B5Trace.Event.MOONETISE_B5_ADAPTER_CONSTRUCTED,
                B5Trace.Result.SUCCESS, B5Trace.Category.NONE);
    }
    @Override public void initialize(MaxAdapterInitializationParameters parameters, Activity activity,
            OnCompletionListener completion) {
        InitializationStatus status = InitializationStatus.INITIALIZED_UNKNOWN;
        try {
            // Preserve original 3.4.1 COPPA propagation. Never fabricate consent.
            if (parameters != null && parameters.isAgeRestrictedUser() != null)
                TargetingParams.setSubjectToCOPPA(parameters.isAgeRestrictedUser());
            if (PrebidMobile.isSdkInitialized()) status = InitializationStatus.INITIALIZED_SUCCESS;
        } catch (RuntimeException failure) {
            status = InitializationStatus.INITIALIZED_FAILURE;
        }
        B5Trace.record(B5Trace.Event.MOONETISE_B5_MAX_INITIALIZE_CALLED,
                status == InitializationStatus.INITIALIZED_FAILURE ? B5Trace.Result.FAILED
                        : B5Trace.Result.ADAPTER_READY, B5Trace.Category.NONE);
        B5Trace.record(B5Trace.Event.INIT_STATUS, B5Trace.Result.valueOf(status.name()), B5Trace.Category.NONE);
        completion.onCompletion(status, null);
    }
    @Override public String getAdapterVersion() { return "3.4.1-moonetise-b5-research"; }
    @Override public String getSdkVersion() { return PrebidMobile.SDK_VERSION; }

    @Override public void loadInterstitialAd(MaxAdapterResponseParameters parameters, Activity activity,
            MaxInterstitialAdapterListener listener) {
        B5Trace.record(B5Trace.Event.MOONETISE_B5_MAX_LOAD_CALLED,
                B5Trace.Result.REQUESTED, B5Trace.Category.NONE);
        MaxAdapterResponseParameters prepared;
        try {
            prepared = DemandBridge.claim(parameters);
        } catch (RuntimeException unavailable) {
            listener.onInterstitialAdLoadFailed(MaxAdapterError.NO_FILL);
            return;
        }
        if (prepared == null || activity == null || activity.isFinishing()
                || activity.isDestroyed() || !PrebidMobile.isSdkInitialized()) {
            if (prepared != null) BidResponseCache.getInstance().popBidResponse(
                    (String) prepared.getLocalExtraParameters().get(EXTRA_RESPONSE_ID));
            listener.onInterstitialAdLoadFailed(MaxAdapterError.NO_FILL); return;
        }
        manager = new MaxInterstitialManager();
        try {
            manager.loadAd(prepared, activity, listener);
            B5Trace.record(B5Trace.Event.ADAPTER_DELEGATED, B5Trace.Result.SUCCESS, B5Trace.Category.NONE);
        } catch (RuntimeException failure) {
            listener.onInterstitialAdLoadFailed(MaxAdapterError.NO_FILL);
        }
    }
    @Override public void showInterstitialAd(MaxAdapterResponseParameters parameters, Activity activity,
            MaxInterstitialAdapterListener listener) {
        if (manager == null || activity == null) {
            listener.onInterstitialAdDisplayFailed(MaxAdapterError.AD_NOT_READY); return;
        }
        activity.runOnUiThread(() -> manager.showAd());
    }
    @Override public void onDestroy() {
        if (manager != null) manager.destroy();
        if (rewardedManager != null) rewardedManager.destroy();
        if (bannerManager != null) bannerManager.destroy();
        B5Trace.record(B5Trace.Event.MOONETISE_B5_ADAPTER_DESTROYED,
                B5Trace.Result.DESTROYED, B5Trace.Category.NONE);
    }
    @Override public void loadAdViewAd(MaxAdapterResponseParameters parameters, MaxAdFormat format,
            Activity activity, MaxAdViewAdapterListener listener) {
        B5Trace.record(B5Trace.Event.BANNER_LOAD_CALLED, B5Trace.Result.REQUESTED, B5Trace.Category.NONE);
        MaxAdapterResponseParameters prepared;
        try { prepared = BannerDemandBridge.claim(parameters, format); }
        catch (RuntimeException unavailable) {
            listener.onAdViewAdLoadFailed(MaxAdapterError.NO_FILL); return;
        }
        if (prepared == null || activity == null || activity.isFinishing()
                || activity.isDestroyed() || !PrebidMobile.isSdkInitialized()) {
            if (prepared != null) BidResponseCache.getInstance().popBidResponse(
                    (String) prepared.getLocalExtraParameters().get(EXTRA_RESPONSE_ID));
            B5Trace.record(B5Trace.Event.ADAPTER_NO_FILL, B5Trace.Result.MISSING, B5Trace.Category.NO_FILL);
            listener.onAdViewAdLoadFailed(MaxAdapterError.NO_FILL); return;
        }
        bannerManager = new com.applovin.mediation.adapters.prebid.managers.MaxBannerManager();
        try {
            bannerManager.loadAd(prepared, format, activity, listener);
            B5Trace.record(B5Trace.Event.ADAPTER_DELEGATED, B5Trace.Result.SUCCESS, B5Trace.Category.NONE);
        } catch (RuntimeException unavailable) {
            BidResponseCache.getInstance().popBidResponse(
                    (String) prepared.getLocalExtraParameters().get(EXTRA_RESPONSE_ID));
            listener.onAdViewAdLoadFailed(MaxAdapterError.NO_FILL);
        }
    }
    @Override public void loadRewardedAd(MaxAdapterResponseParameters parameters, Activity activity,
            MaxRewardedAdapterListener listener) {
        B5Trace.record(B5Trace.Event.MOONETISE_B5_MAX_LOAD_CALLED,
                B5Trace.Result.REQUESTED, B5Trace.Category.NONE);
        MaxAdapterResponseParameters prepared;
        try { prepared = RewardedDemandBridge.claim(parameters); }
        catch (RuntimeException unavailable) {
            listener.onRewardedAdLoadFailed(MaxAdapterError.NO_FILL); return;
        }
        if (prepared == null || activity == null || activity.isFinishing()
                || activity.isDestroyed() || !PrebidMobile.isSdkInitialized()) {
            if (prepared != null) BidResponseCache.getInstance().popBidResponse(
                    (String) prepared.getLocalExtraParameters().get(EXTRA_RESPONSE_ID));
            B5Trace.record(B5Trace.Event.ADAPTER_NO_FILL, B5Trace.Result.MISSING, B5Trace.Category.NO_FILL);
            listener.onRewardedAdLoadFailed(MaxAdapterError.NO_FILL); return;
        }
        rewardedManager = new com.applovin.mediation.adapters.prebid.managers.MaxRewardedManager();
        try {
            rewardedManager.loadAd(prepared, activity, listener);
            B5Trace.record(B5Trace.Event.ADAPTER_DELEGATED, B5Trace.Result.SUCCESS, B5Trace.Category.NONE);
        } catch (RuntimeException failure) {
            // The provider claimed once; remove only its still-unconsumed cache entry.
            BidResponseCache.getInstance().popBidResponse(
                    (String) prepared.getLocalExtraParameters().get(EXTRA_RESPONSE_ID));
            listener.onRewardedAdLoadFailed(MaxAdapterError.NO_FILL);
        }
    }
    @Override public void showRewardedAd(MaxAdapterResponseParameters parameters, Activity activity,
            MaxRewardedAdapterListener listener) {
        if (rewardedManager == null || activity == null || activity.isFinishing() || activity.isDestroyed()) {
            listener.onRewardedAdDisplayFailed(MaxAdapterError.AD_NOT_READY); return;
        }
        activity.runOnUiThread(() -> rewardedManager.showAd());
    }
    @Override public void loadNativeAd(MaxAdapterResponseParameters p, Activity a,
            MaxNativeAdAdapterListener l) { l.onNativeAdLoadFailed(MaxAdapterError.NO_FILL); }
}
