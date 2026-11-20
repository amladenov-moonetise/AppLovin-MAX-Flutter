package com.moonetise.research.checkpointa;

import static org.junit.Assert.*;
import android.app.Activity;
import androidx.test.platform.app.InstrumentationRegistry;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.adapter.MaxAdViewAdapter;
import com.applovin.mediation.adapter.MaxAdapter;
import com.applovin.mediation.adapter.MaxInterstitialAdapter;
import com.applovin.mediation.adapter.MaxNativeAdAdapter;
import com.applovin.mediation.adapter.MaxRewardedAdapter;
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener;
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener;
import com.applovin.mediation.adapter.listeners.MaxNativeAdAdapterListener;
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener;
import com.applovin.mediation.adapter.parameters.MaxAdapterInitializationParameters;
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters;
import com.applovin.mediation.adapters.MediationAdapterBase;
import com.applovin.sdk.AppLovinSdk;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * Uses the public class/constructor contract observed in MAX 13.3.1.
 * Does NOT invoke MAX's private loader or prove a server-configured waterfall.
 */
public class AdapterClassLoadingTest {
    private static final String NAME =
            "com.applovin.mediation.adapters.PrebidMaxMediationAdapter";

    @Test
    public void packagedAdapterLoadsAndConstructsAgainstPinnedMax() throws Exception {
        ClassLoader loader = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getClassLoader();
        Class<?> adapter = Class.forName(NAME, true, loader);
        assertTrue(Modifier.isPublic(adapter.getModifiers()));
        assertEquals(MediationAdapterBase.class, adapter.getSuperclass());
        assertTrue(MaxAdapter.class.isAssignableFrom(adapter));
        assertTrue(MaxInterstitialAdapter.class.isAssignableFrom(adapter));
        assertTrue(MaxRewardedAdapter.class.isAssignableFrom(adapter));
        assertTrue(MaxAdViewAdapter.class.isAssignableFrom(adapter));
        assertTrue(MaxNativeAdAdapter.class.isAssignableFrom(adapter));
        assertEquals("MOONETISE_PACKAGING_STUB_ONLY",
                adapter.getField("CHECKPOINT_A_MARKER").get(null));

        Constructor<?> constructor = adapter.getConstructor(AppLovinSdk.class);
        AtomicReference<MaxAdapter> instance = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                // Obtain SDK handle only. No initialize(), MAX fullscreen object,
                // network load, listener ownership or publisher settings changes.
                AppLovinSdk sdk = AppLovinSdk.getInstance(
                        InstrumentationRegistry.getInstrumentation().getTargetContext());
                assertFalse("Fixture must not initialize MAX", sdk.isInitialized());
                instance.set((MaxAdapter) constructor.newInstance(sdk));
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) throw new AssertionError("Adapter construction failed", failure.get());
        assertNotNull(instance.get());
        assertEquals("13.3.1", AppLovinSdk.VERSION);
        assertEquals("3.4.1", instance.get().getSdkVersion());
        assertEquals("0.0.1-checkpoint-a-NOT-PREBID", instance.get().getAdapterVersion());
        instance.get().onDestroy();

        // Linkability only: never invoke Prebid bidding or rendering.
        Class.forName("org.prebid.mobile.PrebidMobile", false, loader);
        Class.forName("org.prebid.mobile.rendering.bidding.display.PluginRendererFactory", false, loader);
        Class.forName("com.applovin.applovin_max.AppLovinMAX", false, loader);
        assertEquals("3.4.1", org.prebid.mobile.PrebidMobile.SDK_VERSION);
    }

    @Test
    public void pinnedAdapterEntryPointsRemainPublic() throws Exception {
        Class<?> adapter = Class.forName(NAME);
        assertVoid(adapter, "initialize", MaxAdapterInitializationParameters.class,
                Activity.class, MaxAdapter.OnCompletionListener.class);
        assertVoid(adapter, "loadInterstitialAd", MaxAdapterResponseParameters.class,
                Activity.class, MaxInterstitialAdapterListener.class);
        assertVoid(adapter, "showInterstitialAd", MaxAdapterResponseParameters.class,
                Activity.class, MaxInterstitialAdapterListener.class);
        assertVoid(adapter, "loadRewardedAd", MaxAdapterResponseParameters.class,
                Activity.class, MaxRewardedAdapterListener.class);
        assertVoid(adapter, "showRewardedAd", MaxAdapterResponseParameters.class,
                Activity.class, MaxRewardedAdapterListener.class);
        assertVoid(adapter, "loadAdViewAd", MaxAdapterResponseParameters.class,
                MaxAdFormat.class, Activity.class, MaxAdViewAdapterListener.class);
        assertVoid(adapter, "loadNativeAd", MaxAdapterResponseParameters.class,
                Activity.class, MaxNativeAdAdapterListener.class);
        assertVoid(adapter, "onDestroy");
    }

    private static void assertVoid(Class<?> type, String name, Class<?>... parameters)
            throws Exception {
        assertEquals(void.class, type.getMethod(name, parameters).getReturnType());
    }
}
