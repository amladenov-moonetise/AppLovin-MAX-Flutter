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
import com.moonetise.research.b1.B1Trace;
import org.prebid.mobile.PrebidMobile;

/**
 * B1 invocation research STUB. NOT a functional Prebid adapter.
 * No bidding, cache access, renderer, MAX ad ownership, consent writes or rewards.
 */
public class PrebidMaxMediationAdapter extends MediationAdapterBase
        implements MaxAdViewAdapter, MaxInterstitialAdapter, MaxRewardedAdapter, MaxNativeAdAdapter {
    public static final String TAG = "PrebidMaxMediationAdapter";
    public static final String EXTRA_RESPONSE_ID = TAG + "ExtraResponseId";
    public static final String EXTRA_KEYWORDS_ID = TAG + "ExtraKeywordsId";
    public static final String B1_MARKER = "MOONETISE_B1_RESEARCH_STUB_ONLY";

    public PrebidMaxMediationAdapter(AppLovinSdk sdk) {
        super(sdk);
        B1Trace.record(B1Trace.Event.MOONETISE_B1_ADAPTER_CONSTRUCTED,
                B1Trace.Result.SUCCESS, B1Trace.Category.NONE);
    }

    @Override
    public void initialize(MaxAdapterInitializationParameters parameters, Activity activity,
            OnCompletionListener completion) {
        // This stub itself is ready to return no-fill. This does NOT initialize
        // Prebid or assert consent, auction or rendering readiness.
        B1Trace.record(B1Trace.Event.MOONETISE_B1_MAX_INITIALIZE_CALLED,
                B1Trace.Result.RESEARCH_STUB_READY, B1Trace.Category.CONTROLLED_STUB);
        completion.onCompletion(InitializationStatus.INITIALIZED_SUCCESS, null);
    }

    @Override public String getAdapterVersion() { return "0.0.1"; }
    @Override public String getSdkVersion() { return PrebidMobile.SDK_VERSION; }

    private void loadCalled() {
        B1Trace.record(B1Trace.Event.MOONETISE_B1_MAX_LOAD_CALLED,
                B1Trace.Result.CONTROLLED_NO_FILL, B1Trace.Category.CONTROLLED_STUB);
    }

    @Override
    public void loadInterstitialAd(MaxAdapterResponseParameters parameters, Activity activity,
            MaxInterstitialAdapterListener listener) {
        loadCalled();
        listener.onInterstitialAdLoadFailed(MaxAdapterError.NO_FILL);
    }

    @Override
    public void loadRewardedAd(MaxAdapterResponseParameters parameters, Activity activity,
            MaxRewardedAdapterListener listener) {
        loadCalled();
        listener.onRewardedAdLoadFailed(MaxAdapterError.NO_FILL);
    }

    @Override
    public void showInterstitialAd(MaxAdapterResponseParameters parameters, Activity activity,
            MaxInterstitialAdapterListener listener) {
        B1Trace.record(B1Trace.Event.HOST_UNEXPECTED_CALLBACK,
                B1Trace.Result.AD_NOT_READY, B1Trace.Category.UNEXPECTED_DISPLAY);
        listener.onInterstitialAdDisplayFailed(MaxAdapterError.AD_NOT_READY);
    }

    @Override
    public void showRewardedAd(MaxAdapterResponseParameters parameters, Activity activity,
            MaxRewardedAdapterListener listener) {
        B1Trace.record(B1Trace.Event.HOST_UNEXPECTED_CALLBACK,
                B1Trace.Result.AD_NOT_READY, B1Trace.Category.UNEXPECTED_DISPLAY);
        listener.onRewardedAdDisplayFailed(MaxAdapterError.AD_NOT_READY);
    }

    @Override
    public void loadAdViewAd(MaxAdapterResponseParameters parameters, MaxAdFormat format,
            Activity activity, MaxAdViewAdapterListener listener) {
        loadCalled();
        listener.onAdViewAdLoadFailed(MaxAdapterError.NO_FILL);
    }

    @Override
    public void loadNativeAd(MaxAdapterResponseParameters parameters, Activity activity,
            MaxNativeAdAdapterListener listener) {
        loadCalled();
        listener.onNativeAdLoadFailed(MaxAdapterError.NO_FILL);
    }

    @Override
    public void onDestroy() {
        B1Trace.record(B1Trace.Event.MOONETISE_B1_ADAPTER_DESTROYED,
                B1Trace.Result.DESTROYED, B1Trace.Category.NONE);
    }
}
