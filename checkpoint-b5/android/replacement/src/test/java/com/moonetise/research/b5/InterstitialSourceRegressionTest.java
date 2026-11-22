package com.moonetise.research.b5;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Frozen B3 source parity, not a substitute for a device interstitial regression run. */
public class InterstitialSourceRegressionTest {
    private Path root() {
        Path p = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++, p = p.getParent()) {
            if (Files.isDirectory(p.resolve("checkpoint-b3")) && Files.isDirectory(p.resolve("checkpoint-b5"))) return p;
        }
        throw new AssertionError("Run inside the research repository with frozen B3 present");
    }
    private String source(String checkpoint, String relative) throws Exception {
        return new String(Files.readAllBytes(root().resolve(checkpoint + "/android/replacement/src/main/java/" + relative)),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
    private String normalized(String b4) {
        return b4.replace(".research.b5", ".research.b3").replace("B5", "B3");
    }
    @Test public void originalInterstitialHelpersAndClaimSemanticsRemainIdentical() throws Exception {
        for (String file : new String[] {
            "com/applovin/mediation/adapters/prebid/ParametersChecker.java",
            "com/applovin/mediation/adapters/prebid/ListenersCreator.java",
            "com/applovin/mediation/adapters/prebid/managers/MaxInterstitialManager.java",
            "com/moonetise/research/b3/DemandBridge.java",
            "com/moonetise/research/b3/PendingSlot.java",
            "com/moonetise/research/b3/CycleGate.java",
            "com/moonetise/research/b3/ResponseParameters.java"
        }) {
            assertEquals(file, source("checkpoint-b3", file),
                    normalized(source("checkpoint-b5", file.replace("/b3/", "/b5/"))));
        }
    }
    @Test public void interstitialLoadAndShowBodiesRemainIdentical() throws Exception {
        String path = "com/applovin/mediation/adapters/PrebidMaxMediationAdapter.java";
        String b3 = source("checkpoint-b3", path);
        String b4 = normalized(source("checkpoint-b5", path));
        String start = "    @Override public void loadInterstitialAd(";
        String end = "    @Override public void onDestroy()";
        assertEquals(b3.substring(b3.indexOf(start), b3.indexOf(end)),
                b4.substring(b4.indexOf(start), b4.indexOf(end)));
    }
}
