package com.moonetise.research.b1;

import android.os.SystemClock;
import android.util.Log;
import com.applovin.sdk.AppLovinSdk;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

/** Research-only bounded, process-local evidence. Never accepts arbitrary log payloads. */
public final class B1Trace {
    public static final String TAG = "MoonetiseAdapterB1";
    public static final String ADAPTER = "com.applovin.mediation.adapters.PrebidMaxMediationAdapter";
    public static final long WINDOW_MILLIS = 60_000L;
    private static final int MAX_EVENTS = 128;

    public enum Event {
        RUN_STARTED,
        MOONETISE_B1_ADAPTER_CONSTRUCTED,
        MOONETISE_B1_MAX_INITIALIZE_CALLED,
        MOONETISE_B1_MAX_LOAD_CALLED,
        MOONETISE_B1_ADAPTER_DESTROYED,
        HOST_MAX_INITIALIZED, HOST_LOAD_REQUESTED, HOST_LOAD_RESULT,
        HOST_WATERFALL, HOST_SELECTED_NETWORK, HOST_UNEXPECTED_CALLBACK,
        HOST_ERROR, HOST_OBSERVATION_CLOSED, HOST_DETACHED
    }
    public enum Result {
        SINGLE_DEFINITION, SUCCESS, RESEARCH_STUB_READY, CONTROLLED_NO_FILL,
        AD_NOT_READY, LOADED, FAILED, TIMEOUT, CLASS_MATCH, CLASS_MISMATCH,
        UNKNOWN, REQUESTED, DESTROYED, IGNORED, CLOSED
    }
    public enum Category {
        NONE, CONTROLLED_STUB, NO_FILL, CONNECTIVITY, CREDENTIAL_OR_CONFIGURATION,
        SDK_OR_NETWORK_ERROR, HOST_EXCEPTION, UNEXPECTED_DISPLAY,
        UNEXPECTED_REWARD, DETACHED, DEADLINE, CAPTURE_LIMIT
    }
    public enum Network { UNKNOWN, TESTPBS, APPLOVIN, OTHER }
    public enum State { UNKNOWN, AD_LOAD_NOT_ATTEMPTED, AD_LOADED, AD_FAILED_TO_LOAD }

    private static final List<String> lines = new ArrayList<>();
    private static boolean used;
    private static boolean closed;
    private static long started;
    private static String runId;
    private static boolean overflow;

    private B1Trace() {}

    /** Called only after the host checks the installed APK. One run per process. */
    public static synchronized String begin() {
        if (used) throw new IllegalStateException("FRESH_PROCESS_REQUIRED");
        used = true;
        runId = UUID.randomUUID().toString();
        started = SystemClock.elapsedRealtime();
        append(Event.RUN_STARTED, Result.SINGLE_DEFINITION, Category.NONE,
                Network.UNKNOWN, State.UNKNOWN);
        return runId;
    }

    public static synchronized void record(Event event, Result result, Category category) {
        if (!used || closed || SystemClock.elapsedRealtime() - started > WINDOW_MILLIS) return;
        append(event, result, category, Network.UNKNOWN, State.UNKNOWN);
    }

    public static synchronized void host(String event, String result, String category,
            String network, String state) {
        Event parsed = Event.valueOf(event);
        if (!parsed.name().startsWith("HOST_")) throw new IllegalArgumentException("HOST_EVENT_REQUIRED");
        Result safeResult = Result.valueOf(result);
        Category safeCategory = Category.valueOf(category);
        Network safeNetwork = Network.valueOf(network);
        State safeState = State.valueOf(state);
        if (!used || closed || SystemClock.elapsedRealtime() - started > WINDOW_MILLIS) return;
        append(parsed, safeResult, safeCategory, safeNetwork, safeState);
    }

    public static synchronized void close(Category reason) {
        if (!used || closed) return;
        append(Event.HOST_OBSERVATION_CLOSED, Result.CLOSED, reason, Network.UNKNOWN, State.UNKNOWN);
        closed = true;
    }

    private static void append(Event event, Result result, Category category,
            Network network, State state) {
        if (lines.size() >= MAX_EVENTS - 1) {
            if (overflow) return;
            overflow = true;
            event = Event.HOST_ERROR;
            result = Result.FAILED;
            category = Category.CAPTURE_LIMIT;
            network = Network.UNKNOWN;
            state = State.UNKNOWN;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("runId", runId);
            json.put("timestamp", Instant.now().toString());
            json.put("event", event.name());
            json.put("adapterClass", ADAPTER);
            json.put("maxVersion", AppLovinSdk.VERSION);
            json.put("result", result.name());
            json.put("category", category.name());
            json.put("selectedNetwork", network.name());
            json.put("loadState", state.name());
            String line = json.toString();
            lines.add(line);
            Log.i(TAG, line);
        } catch (JSONException impossible) {
            throw new IllegalStateException("DIAGNOSTIC_SCHEMA_ERROR");
        }
    }

    public static synchronized String exportJsonLines() {
        return String.join("\n", lines);
    }
}
