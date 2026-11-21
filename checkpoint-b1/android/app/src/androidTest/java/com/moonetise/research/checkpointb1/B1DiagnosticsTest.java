package com.moonetise.research.checkpointb1;

import static org.junit.Assert.*;
import androidx.test.platform.app.InstrumentationRegistry;
import com.moonetise.research.b1.B1Trace;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Test;

/** Offline preflight only; never constructs an adapter or initializes/loads MAX. */
public final class B1DiagnosticsTest {
    @Test
    public void installedApkIsUniqueAndExportRejectsArbitraryPayloads() throws Exception {
        assertEquals(1, InstalledApkCheck.count(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));
        String run = B1Trace.begin();
        assertEquals(run, UUID.fromString(run).toString());
        String forbidden = "DO_NOT_EXPORT_ARBITRARY_PAYLOAD";
        try {
            B1Trace.host(forbidden, "SUCCESS", "NONE", "UNKNOWN", "UNKNOWN");
            fail("Arbitrary diagnostic event must be rejected");
        } catch (IllegalArgumentException expected) {
            // Do not log or stringify the exception.
        }
        B1Trace.host("HOST_ERROR", "FAILED", "HOST_EXCEPTION",
                "UNKNOWN", "UNKNOWN");
        B1Trace.close(B1Trace.Category.NONE);
        String report = B1Trace.exportJsonLines();
        assertFalse(report.contains(forbidden));
        assertFalse(report.contains("sdkKey"));
        assertFalse(report.contains("adUnitId"));
        Set<String> allowed = new HashSet<>(Arrays.asList(
                "runId", "timestamp", "event", "adapterClass", "maxVersion",
                "result", "category", "selectedNetwork", "loadState"));
        for (String line : report.split("\n")) {
            JSONObject json = new JSONObject(line);
            assertEquals(run, json.getString("runId"));
            assertEquals("13.3.1", json.getString("maxVersion"));
            assertEquals(B1Trace.ADAPTER, json.getString("adapterClass"));
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) assertTrue(allowed.contains(keys.next()));
        }
        assertFalse(report.contains("MOONETISE_B1_ADAPTER_CONSTRUCTED"));
        assertFalse(report.contains("MOONETISE_B1_MAX_INITIALIZE_CALLED"));
    }
}
