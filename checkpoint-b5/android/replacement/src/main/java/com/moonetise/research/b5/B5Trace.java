package com.moonetise.research.b5;

import android.os.SystemClock;
import android.util.Log;
import com.applovin.sdk.AppLovinSdk;
import java.time.Instant;
import java.util.*;
import org.json.JSONException;
import org.json.JSONObject;

/** Fixed-schema bounded evidence. No request, response ID, SDK key, consent or raw errors. */
public final class B5Trace {
    public static final String TAG = "MoonetiseAdapterB5";
    public static final String ADAPTER = "com.applovin.mediation.adapters.PrebidMaxMediationAdapter";
    public static final long WINDOW_MILLIS = 180000;
    public enum Scenario { SYNTHETIC, NO_DEMAND, PREP_FAILURE, PREP_TIMEOUT, CANCEL, EXPIRE, WITHDRAW, TIER_CHECK }
    public enum Event {
        RUN_STARTED, INSTALLED_APK_CHECK, CYCLE_STARTED, CYCLE_COMPLETE, PREBID_INITIALIZED, INIT_STATUS,
        PREPARATION_STARTED, PREPARATION_RESULT, PREPARATION_REJECTED, CAPTURED, STALE_CALLBACK,
        BANNER_LOAD_CALLED, ADAPTER_FORMAT, FORMAT_SIZE_CHECK, BANNER_MANAGER_DESTROYED,
        HOST_VIEW_MOUNT_REQUESTED, HOST_VIEW_REMOVED, HOST_DISPOSAL_REVIEW, HOST_REVENUE,
        HOST_CLICKED, HOST_EXPANDED, HOST_COLLAPSED,
        SLOT_ARMED, SLOT_CLAIM, CACHE_POP, PROVIDER_MISS, ADAPTER_DELEGATED, ADAPTER_NO_FILL,
        CONTRIBUTION_CLEARED, OWNED_CACHE_CLEARED, SDK_TIER_RESULT,
        MOONETISE_B5_ADAPTER_CONSTRUCTED, MOONETISE_B5_MAX_INITIALIZE_CALLED,
        MOONETISE_B5_MAX_LOAD_CALLED, MOONETISE_B5_ADAPTER_DESTROYED,
        HOST_MAX_INITIALIZED, HOST_LOAD_REQUESTED, HOST_LOAD_RESULT, HOST_WATERFALL,
        HOST_SELECTED_NETWORK, HOST_OPERATOR_VISIBLE, HOST_DISPLAYED, HOST_HIDDEN,
        HOST_SHOW_REQUESTED, HOST_REWARD, HOST_ERROR
    }
    public enum Result {
        SUCCESS, FAILED, TIMEOUT, BID, NO_BID, REQUESTED, LOADED, MATCH, MISMATCH,
        MISSING, EXPIRED, INCOMPATIBLE, BANNER, LEADER, MREC, OTHER_FORMAT, CONSUMED, YES, NO, CLASS_MATCH, CLASS_MISMATCH, UNKNOWN,
        ADAPTER_READY, DESTROYED, CLOSED, FRESH, CANCELLED, WITHDRAWN, LATE_IGNORED,
        FORCED_FAILURE, FORCED_TIMEOUT, INITIALIZED_SUCCESS, INITIALIZED_UNKNOWN, INITIALIZED_FAILURE
    }
    public enum Category { NONE, PREPARATION, TARGETING, AUTHORIZATION, NO_FILL, CONNECTIVITY,
        CREDENTIAL_OR_CONFIGURATION, SDK_OR_NETWORK_ERROR, HOST_EXCEPTION, DETACHED, DEADLINE, CAPTURE_LIMIT }
    public enum Network { UNKNOWN, TESTPBS, APPLOVIN, OTHER }
    public enum State { UNKNOWN, AD_LOAD_NOT_ATTEMPTED, AD_LOADED, AD_FAILED_TO_LOAD }
    private static final List<String> lines = new ArrayList<>();
    private static final Map<Long, Scenario> scenarios = new LinkedHashMap<>();
    private static String runId;
    private static long current;
    private static boolean release, overflow;
    private B5Trace() {}
    public static synchronized String begin(boolean releaseBuild) {
        if (runId != null) throw new IllegalStateException("SESSION_ALREADY_OPEN");
        release = releaseBuild; runId = UUID.randomUUID().toString();
        emit(0, Event.RUN_STARTED, Result.SUCCESS, Category.NONE, Network.UNKNOWN, State.UNKNOWN);
        return runId;
    }
    public static synchronized void cycle(long id, Scenario scenario) {
        current = id; scenarios.put(id, scenario);
        emit(id, Event.CYCLE_STARTED, Result.SUCCESS, Category.NONE, Network.UNKNOWN, State.UNKNOWN);
    }
    public static synchronized void record(Event e, Result r, Category c) { recordFor(current, e, r, c); }
    public static synchronized void recordFor(long id, Event e, Result r, Category c) {
        if (runId != null) emit(id, e, r, c, Network.UNKNOWN, State.UNKNOWN);
    }
    public static synchronized void host(long id, String event, String result, String category,
            String network, String state) {
        Event e = Event.valueOf(event);
        if (!e.name().startsWith("HOST_")) throw new IllegalArgumentException("HOST_EVENT");
        if (id != 0 && !scenarios.containsKey(id)) throw new IllegalArgumentException("CYCLE");
        emit(id, e, Result.valueOf(result), Category.valueOf(category),
                Network.valueOf(network), State.valueOf(state));
    }
    private static void emit(long id, Event e, Result r, Category c, Network n, State s) {
        if (runId == null) return;
        if (lines.size() >= 640) { overflow = true; return; }
        try {
            JSONObject j = new JSONObject();
            j.put("runId", runId); j.put("cycle", id); j.put("timestamp", Instant.now().toString());
            j.put("elapsedMs", SystemClock.elapsedRealtime()); j.put("event", e.name());
            j.put("scenario", id == 0 ? "SESSION" : scenarios.get(id).name());
            j.put("build", release ? "RELEASE" : "DEBUG");
            j.put("format", "BANNER");
            j.put("unitSuffix", "a5bb");
            j.put("expectedSize", "320x50");
            j.put("adapterClass", ADAPTER); j.put("maxVersion", AppLovinSdk.VERSION);
            j.put("result", r.name()); j.put("category", c.name());
            j.put("network", n.name()); j.put("loadState", s.name());
            String line = j.toString(); lines.add(line); Log.i(TAG, line);
        } catch (JSONException impossible) { throw new IllegalStateException("SCHEMA"); }
    }
    private static boolean seen(long id, Event e, Result r, Network network, State state) {
        for (String line : lines) try {
            JSONObject j = new JSONObject(line);
            if (j.getLong("cycle") == id && e.name().equals(j.getString("event"))
                    && (r == null || r.name().equals(j.getString("result")))
                    && (network == null || network.name().equals(j.getString("network")))
                    && (state == null || state.name().equals(j.getString("loadState")))) return true;
        } catch (JSONException ignored) { return false; }
        return false;
    }
    private static boolean has(long id, Event e, Result r) { return seen(id, e, r, null, null); }
    private static int count(long id, Event e, Result r) {
        int total = 0;
        for (String line : lines) try {
            JSONObject j = new JSONObject(line);
            if (j.getLong("cycle") == id && e.name().equals(j.getString("event"))
                    && r.name().equals(j.getString("result"))) total++;
        } catch (JSONException ignored) {}
        return total;
    }
    private static void need(List<String> missing, boolean value, String name) { if (!value) missing.add(name); }
    public static synchronized Map<String, Object> report(long id) {
        if (!scenarios.containsKey(id)) throw new IllegalArgumentException("CYCLE");
        Scenario scenario = scenarios.get(id);
        List<String> missing = new ArrayList<>();
        boolean fail = has(id, Event.HOST_OPERATOR_VISIBLE, Result.NO)
                || has(id, Event.HOST_DISPOSAL_REVIEW, Result.NO)
                || count(id, Event.SLOT_CLAIM, Result.MATCH) > 1
                || count(id, Event.CACHE_POP, Result.CONSUMED) > 1
                || count(id, Event.HOST_LOAD_REQUESTED, Result.REQUESTED) > 1
                || has(id, Event.OWNED_CACHE_CLEARED, Result.FAILED);
        need(missing, !overflow, "CAPTURE_WITHIN_LIMIT");
        need(missing, has(0, Event.INSTALLED_APK_CHECK, Result.SUCCESS), "INSTALLED_BASE_SPLIT_UNIQUENESS");
        need(missing, has(0, Event.HOST_MAX_INITIALIZED, Result.SUCCESS), "MAX_INITIALIZED");
        need(missing, has(0, Event.MOONETISE_B5_ADAPTER_CONSTRUCTED, Result.SUCCESS)
                || has(id, Event.MOONETISE_B5_ADAPTER_CONSTRUCTED, Result.SUCCESS), "ADAPTER_CONSTRUCTED");
        need(missing, has(0, Event.MOONETISE_B5_MAX_INITIALIZE_CALLED, Result.ADAPTER_READY)
                || has(id, Event.MOONETISE_B5_MAX_INITIALIZE_CALLED, Result.ADAPTER_READY), "ADAPTER_INITIALIZED");
        need(missing, count(id, Event.HOST_LOAD_REQUESTED, Result.REQUESTED) == 1, "ONE_PUBLISHER_LOAD");
        need(missing, has(id, Event.HOST_VIEW_MOUNT_REQUESTED, Result.REQUESTED), "OFFICIAL_WIDGET_MOUNT_REQUESTED");
        need(missing, has(id, Event.BANNER_LOAD_CALLED, Result.REQUESTED), "BANNER_ADAPTER_INVOKED");
        need(missing, has(id, Event.FORMAT_SIZE_CHECK, Result.MATCH), "MAX_BANNER_320X50");
        need(missing, has(id, Event.HOST_LOAD_RESULT, Result.LOADED)
                || has(id, Event.HOST_LOAD_RESULT, Result.FAILED), "GENUINE_MAX_TERMINAL");
        need(missing, has(id, Event.HOST_VIEW_REMOVED, Result.SUCCESS), "PUBLISHER_WIDGET_REMOVED");
        need(missing, has(id, Event.HOST_DISPOSAL_REVIEW, Result.YES), "OPERATOR_DISPOSAL_REVIEW");
        if (scenario == Scenario.SYNTHETIC) {
            need(missing, has(id, Event.PREPARATION_RESULT, Result.BID), "PREPARATION_BID");
            need(missing, count(id, Event.CAPTURED, Result.FRESH) == 1, "ONE_320X50_BANNER_CAPTURE");
            need(missing, count(id, Event.SLOT_CLAIM, Result.MATCH) == 1, "ONE_TARGETING_MATCHED_CLAIM");
            need(missing, count(id, Event.CACHE_POP, Result.CONSUMED) == 1, "ONE_ORIGINAL_CACHE_POP");
            need(missing, has(id, Event.ADAPTER_DELEGATED, Result.SUCCESS), "ORIGINAL_MANAGER_DELEGATED");
            need(missing, has(id, Event.HOST_LOAD_RESULT, Result.LOADED), "GENUINE_PUBLISHER_LOADED");
            need(missing, seen(id, Event.HOST_SELECTED_NETWORK, Result.LOADED, Network.TESTPBS, null), "TESTPBS_SELECTED");
            need(missing, seen(id, Event.HOST_WATERFALL, Result.CLASS_MATCH, Network.TESTPBS, State.AD_LOADED), "TESTPBS_CLASS_MATCH_LOADED");
            need(missing, has(id, Event.HOST_OPERATOR_VISIBLE, Result.YES), "OPERATOR_SYNTHETIC_320X50_VISIBLE");
            need(missing, has(id, Event.BANNER_MANAGER_DESTROYED, Result.SUCCESS), "ORIGINAL_MANAGER_DESTROY_OBSERVED");
        } else {
            need(missing, has(id, Event.PREPARATION_RESULT, Result.NO_BID), "NO_AUCTION_PATH");
            need(missing, !has(id, Event.SLOT_CLAIM, Result.MATCH), "NO_PREBID_CLAIM");
            need(missing, has(id, Event.ADAPTER_NO_FILL, Result.MISSING), "PROMPT_PREBID_NO_FILL");
            boolean ordinary = seen(id, Event.HOST_SELECTED_NETWORK, Result.LOADED, Network.APPLOVIN, null)
                    || seen(id, Event.HOST_SELECTED_NETWORK, Result.LOADED, Network.OTHER, null)
                    || seen(id, Event.HOST_WATERFALL, null, Network.APPLOVIN, State.AD_FAILED_TO_LOAD)
                    || seen(id, Event.HOST_WATERFALL, null, Network.OTHER, State.AD_FAILED_TO_LOAD);
            need(missing, ordinary, "ORDINARY_MAX_MEDIATION");
        }
        if (count(id, Event.HOST_LOAD_RESULT, Result.LOADED) + count(id, Event.HOST_LOAD_RESULT, Result.FAILED) > 1)
            missing.add("UNEXPECTED_EXTRA_PUBLISHER_LOAD_CALLBACK");
        if (has(id, Event.HOST_ERROR, Result.TIMEOUT)) missing.add("UNRESOLVED_RESTART_REQUIRED");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", runId); result.put("cycle", id); result.put("scenario", scenario.name());
        result.put("format", "BANNER"); result.put("unitSuffix", "a5bb"); result.put("size", "320x50");
        result.put("classification", fail ? "FAIL" : missing.isEmpty() ? "PASS" : "INCONCLUSIVE");
        result.put("missingEvidence", missing);
        result.put("revenueCallback", has(id, Event.HOST_REVENUE, Result.SUCCESS) ? "OBSERVED" : "NOT_OBSERVED");
        result.put("cancellationObserved", has(id, Event.CONTRIBUTION_CLEARED, Result.CANCELLED));
        List<String> selected = new ArrayList<>();
        for (String line : lines) try {
            long cycle = new JSONObject(line).getLong("cycle");
            if (cycle == 0 || cycle == id) selected.add(line);
        } catch (JSONException ignored) {}
        result.put("jsonl", String.join("\n", selected));
        return result;
    }
}
