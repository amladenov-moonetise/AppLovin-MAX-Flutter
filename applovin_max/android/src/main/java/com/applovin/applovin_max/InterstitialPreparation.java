package com.applovin.applovin_max;

import androidx.annotation.Nullable;
import com.applovin.mediation.MaxAd;
import com.applovin.mediation.ads.MaxInterstitialAd;

/**
 * Trusted native integration only. Never initialize MAX, replace listeners, or load/show here.
 * All methods run on the platform thread. clear must seal callbacks before removing owned data.
 */
public interface InterstitialPreparation {
    boolean isAuthorized();
    boolean isReady();
    void clear(MaxInterstitialAd exactObject);
    default void onEvent(String event, @Nullable MaxAd ad) {}
    /** Diagnostic observation only: rejection is not a publisher display callback. */
    default void onCallbackObserved(String event, @Nullable MaxAd ad,
                                    boolean accepted, boolean engineForwardable) {}
}
