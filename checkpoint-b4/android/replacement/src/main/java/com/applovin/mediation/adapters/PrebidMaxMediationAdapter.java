/*
 * Based on Prebid Mobile Android MAX adapter 3.4.1, Apache-2.0.
 * Moonetise changes: bounded initialization and prepared-response providers for interstitial and rewarded.
 * Research only. Ad-view/native interfaces remain present but return no-fill.
 */
package com.applovin.mediation.adapters;

import android.app.Activity;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.adapter.*;
import com.applovin.mediation.adapter.listeners.*;
import com.applovin.mediation.adapter.parameters.*;
import com.applovin.mediation.adapters.prebid.managers.MaxInterstitialManager;
import com.applovin.sdk.AppLovinSdk;
import com.moonetise.research.b4.*;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.TargetingParams;
import org.prebid.mobile.rendering.bidding.display.BidResponseCache;

public class PrebidMaxMediationAdapter extends MediationAdapterBase
        implements MaxAdViewAdapter, MaxInterstitialAdapter, MaxRewardedAdapter, MaxNativeAdAdapter {
    public static final String TAG = "PrebidMaxMediationAdapter";
    public static final String EXTRA_RESPONSE_ID = TAG + "ExtraResponseId";
    public static final String EXTRA_KEYWORDS_ID = TAG + "ExtraKeywordsId";
    private MaxInterstitialManager manager;
    private com.applovin.mediation.adapters.prebid.managers.MaxRewardedManager rewardedManager;
    public PrebidMaxMediationAdapter(AppLovinSdk sdk) {
        super(sdk);
        B4Trace.record(B4Trace.Event.MOONETISE_B4_ADAPTER_CONSTRUCTED,
                B4Trace.Result.SUCCESS, B4Trace.Category.NONE);
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
        B4Trace.record(B4Trace.Event.MOONETISE_B4_MAX_INITIALIZE_CALLED,
                status == InitializationStatus.INITIALIZED_FAILURE ? B4Trace.Result.FAILED
                        : B4Trace.Result.ADAPTER_READY, B4Trace.Category.NONE);
        B4Trace.record(B4Trace.Event.INIT_STATUS, B4Trace.Result.valueOf(status.name()), B4Trace.Category.NONE);
        completion.onCompletion(status, null);
    }
    @Override public String getAdapterVersion() { return "3.4.1-moonetise-b4-research"; }
    @Override public String getSdkVersion() { return PrebidMobile.SDK_VERSION; }

    @Override public void loadInterstitialAd(MaxAdapterResponseParameters parameters, Activity activity,
            MaxInterstitialAdapterListener listener) {
        B4Trace.record(B4Trace.Event.MOONETISE_B4_MAX_LOAD_CALLED,
                B4Trace.Result.REQUESTED, B4Trace.Category.NONE);
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
            B4Trace.record(B4Trace.Event.ADAPTER_DELEGATED, B4Trace.Result.SUCCESS, B4Trace.Category.NONE);
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
        B4Trace.record(B4Trace.Event.MOONETISE_B4_ADAPTER_DESTROYED,
                B4Trace.Result.DESTROYED, B4Trace.Category.NONE);
    }
    @Override public void loadAdViewAd(MaxAdapterResponseParameters p, MaxAdFormat f, Activity a,
            MaxAdViewAdapterListener l) { l.onAdViewAdLoadFailed(MaxAdapterError.NO_FILL); }
    @Override public void loadRewardedAd(MaxAdapterResponseParameters parameters, Activity activity,
            MaxRewardedAdapterListener listener) {
        B4Trace.record(B4Trace.Event.MOONETISE_B4_MAX_LOAD_CALLED,
                B4Trace.Result.REQUESTED, B4Trace.Category.NONE);
        MaxAdapterResponseParameters prepared;
        try { prepared = RewardedDemandBridge.claim(parameters); }
        catch (RuntimeException unavailable) {
            listener.onRewardedAdLoadFailed(MaxAdapterError.NO_FILL); return;
        }
        if (prepared == null || activity == null || activity.isFinishing()
                || activity.isDestroyed() || !PrebidMobile.isSdkInitialized()) {
            if (prepared != null) BidResponseCache.getInstance().popBidResponse(
                    (String) prepared.getLocalExtraParameters().get(EXTRA_RESPONSE_ID));
            B4Trace.record(B4Trace.Event.ADAPTER_NO_FILL, B4Trace.Result.MISSING, B4Trace.Category.NO_FILL);
            listener.onRewardedAdLoadFailed(MaxAdapterError.NO_FILL); return;
        }
        rewardedManager = new com.applovin.mediation.adapters.prebid.managers.MaxRewardedManager();
        try {
            rewardedManager.loadAd(prepared, activity, listener);
            B4Trace.record(B4Trace.Event.ADAPTER_DELEGATED, B4Trace.Result.SUCCESS, B4Trace.Category.NONE);
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
