package com.applovin.applovin_max;

/** Exact-binding cleanup boundary. Never creates, loads, shows or initializes an SDK object. */
final class InterstitialRetirement
{
    interface NativeCleanup
    {
        void destroy(InterstitialReservationRegistry.Binding binding) throws Exception;
    }

    private final InterstitialReservationRegistry registry;
    private final NativeCleanup nativeCleanup;

    InterstitialRetirement(InterstitialReservationRegistry registry, NativeCleanup nativeCleanup)
    {
        this.registry = registry;
        this.nativeCleanup = nativeCleanup;
    }

    boolean retire(String adUnitId, String attemptId)
    {
        return cleanup(registry.requestRetirement(adUnitId, attemptId));
    }

    boolean cleanup(InterstitialReservationRegistry.Binding binding)
    {
        switch (registry.beginCleanup(binding))
        {
            case RETIRED: return true;
            case DEFERRED: return false;
            case FAILED: throw new InterstitialReservationRegistry.Rejected("INTERSTITIAL_CLEANUP_FAILED");
            case READY: break;
            default: throw new IllegalStateException("Unknown cleanup state");
        }
        try
        {
            nativeCleanup.destroy(binding); // Outside registry synchronization.
        }
        catch (Exception ignored)
        {
            registry.cleanupFinished(binding, false);
            throw new InterstitialReservationRegistry.Rejected("INTERSTITIAL_CLEANUP_FAILED");
        }
        registry.cleanupFinished(binding, true);
        return true;
    }
}
