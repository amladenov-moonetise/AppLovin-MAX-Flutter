package com.applovin.mediation.adapters;

import android.app.Activity;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.adapter.MaxAdViewAdapter;
import com.applovin.mediation.adapter.MaxAdapterError;
import com.applovin.mediation.adapter.MaxInterstitialAdapter;
import com.applovin.mediation.adapter.MaxNativeAdAdapter;
import com.applovin.mediation.adapter.MaxRewardedAdapter;
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener;
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener;
import com.applovin.mediation.adapter.listeners.MaxNativeAdAdapterListener;
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener;
import com.applovin.mediation.adapter.parameters.MaxAdapterInitializationParameters;
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters;
import com.applovin.sdk.AppLovinSdk;
import org.prebid.mobile.PrebidMobile;

/**
 * Moonetise Checkpoint A TEST DOUBLE, NOT the enhanced Prebid adapter.
 *
 * Preserves the pinned adapter's constructor, superclass, interfaces and public
 * entry-point signatures solely for packaging/class-loading verification.
 * Contains no managers, renderer calls, demand lookup, PBS, MAX ad objects,
 * consent changes, rewards, or successful ad callbacks. Never ship this fixture.
 */
public class PrebidMaxMediationAdapter extends MediationAdapterBase
        implements MaxAdViewAdapter, MaxInterstitialAdapter, MaxRewardedAdapter,
        MaxNativeAdAdapter {

    public static final String TAG = "PrebidMaxMediationAdapter";
    public static final String EXTRA_RESPONSE_ID = TAG + "ExtraResponseId";
    public static final String EXTRA_KEYWORDS_ID = TAG + "ExtraKeywordsId";
    public static final String CHECKPOINT_A_MARKER = "MOONETISE_PACKAGING_STUB_ONLY";

    public PrebidMaxMediationAdapter(AppLovinSdk sdk) {
        super(sdk);
    }

    @Override
    public void initialize(MaxAdapterInitializationParameters parameters,
            Activity activity, OnCompletionListener completion) {
        completion.onCompletion(InitializationStatus.INITIALIZED_FAILURE,
                CHECKPOINT_A_MARKER);
    }

    @Override
    public String getAdapterVersion() {
        return "0.0.1-checkpoint-a-NOT-PREBID";
    }

    @Override
    public String getSdkVersion() {
        return PrebidMobile.SDK_VERSION;
    }

    @Override
    public void loadInterstitialAd(MaxAdapterResponseParameters parameters,
            Activity activity, MaxInterstitialAdapterListener listener) {
        listener.onInterstitialAdLoadFailed(MaxAdapterError.NO_FILL);
    }

    @Override
    public void showInterstitialAd(MaxAdapterResponseParameters parameters,
            Activity activity, MaxInterstitialAdapterListener listener) {
        listener.onInterstitialAdDisplayFailed(MaxAdapterError.AD_NOT_READY);
    }

    @Override
    public void loadRewardedAd(MaxAdapterResponseParameters parameters,
            Activity activity, MaxRewardedAdapterListener listener) {
        listener.onRewardedAdLoadFailed(MaxAdapterError.NO_FILL);
    }

    @Override
    public void showRewardedAd(MaxAdapterResponseParameters parameters,
            Activity activity, MaxRewardedAdapterListener listener) {
        listener.onRewardedAdDisplayFailed(MaxAdapterError.AD_NOT_READY);
    }

    @Override
    public void loadAdViewAd(MaxAdapterResponseParameters parameters,
            MaxAdFormat format, Activity activity, MaxAdViewAdapterListener listener) {
        listener.onAdViewAdLoadFailed(MaxAdapterError.NO_FILL);
    }

    @Override
    public void loadNativeAd(MaxAdapterResponseParameters parameters,
            Activity activity, MaxNativeAdAdapterListener listener) {
        listener.onNativeAdLoadFailed(MaxAdapterError.NO_FILL);
    }

    @Override
    public void onDestroy() {
        // No resources are acquired by this test double.
    }
}
