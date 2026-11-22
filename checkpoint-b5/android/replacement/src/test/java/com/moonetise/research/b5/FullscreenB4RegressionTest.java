package com.moonetise.research.b5;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Source parity against committed B4. No advertising or SDK execution. */
public class FullscreenB4RegressionTest {
    private Path root() {
        Path p = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++, p = p.getParent()) {
            if (Files.isDirectory(p.resolve("checkpoint-b4")) && Files.isDirectory(p.resolve("checkpoint-b5"))) return p;
        }
        throw new AssertionError("Frozen B4 sibling required");
    }
    private String read(String checkpoint, String path) throws Exception {
        return new String(Files.readAllBytes(root().resolve(checkpoint
                + "/android/replacement/src/main/java/" + path)), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
    private String b4(String text) { return text.replace(".research.b5", ".research.b4").replace("B5", "B4"); }
    @Test public void fullscreenHelpersAndProviderSemanticsRemainUnchanged() throws Exception {
        for (String path : new String[] {
            "com/applovin/mediation/adapters/prebid/ParametersChecker.java",
            "com/applovin/mediation/adapters/prebid/ListenersCreator.java",
            "com/applovin/mediation/adapters/prebid/managers/MaxInterstitialManager.java",
            "com/applovin/mediation/adapters/prebid/managers/MaxRewardedManager.java",
            "com/moonetise/research/b4/DemandBridge.java",
            "com/moonetise/research/b4/RewardedDemandBridge.java",
            "com/moonetise/research/b4/PendingSlot.java",
            "com/moonetise/research/b4/ResponseParameters.java",
            "com/moonetise/research/b4/CycleGate.java",
            "com/moonetise/research/b4/RewardObservation.java"
        }) assertEquals(path, read("checkpoint-b4", path),
                b4(read("checkpoint-b5", path.replace("/b4/", "/b5/"))));
    }
    @Test public void fullscreenLoadShowAndRewardDelegationBodiesRemainIdentical() throws Exception {
        String path = "com/applovin/mediation/adapters/PrebidMaxMediationAdapter.java";
        String old = read("checkpoint-b4", path), now = b4(read("checkpoint-b5", path));
        for (String[] bounds : new String[][] {
            {"    @Override public void loadInterstitialAd(", "    @Override public void onDestroy()"},
            {"    @Override public void loadRewardedAd(", "    @Override public void loadNativeAd("}
        }) assertEquals(old.substring(old.indexOf(bounds[0]), old.indexOf(bounds[1])),
                now.substring(now.indexOf(bounds[0]), now.indexOf(bounds[1])));
        assertTrue(now.contains("if (manager != null) manager.destroy();"));
        assertTrue(now.contains("if (rewardedManager != null) rewardedManager.destroy();"));
    }
    @Test public void originalBannerViewDeliveryContractRemainsInManager() throws Exception {
        String manager = read("checkpoint-b5",
                "com/applovin/mediation/adapters/prebid/managers/MaxBannerManager.java");
        assertTrue(manager.contains("ListenersCreator.createBannerListener("));
        assertTrue(manager.contains("maxListener.onAdViewAdLoaded(adView)"));
        assertTrue(manager.contains("new MediationBannerView(activity, listener, adConfiguration, response)"));
        assertTrue(manager.contains("((PrebidDestroyable) adView).destroy()"));
        assertFalse(manager.contains("new WebView"));
    }
}
