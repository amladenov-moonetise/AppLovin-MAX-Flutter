package com.moonetise.research.checkpointb1;

import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.moonetise.research.b1.B1Trace;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Diagnostics only. MAX initialization/load/show stay exclusively in official Flutter MAX. */
public final class MainActivity extends FlutterActivity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService scanner = Executors.newSingleThreadExecutor();
    private MethodChannel diagnostics;
    private boolean starting;
    private boolean destroyed;
    private final Runnable deadline = () -> B1Trace.close(B1Trace.Category.DEADLINE);

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine engine) {
        super.configureFlutterEngine(engine);
        diagnostics = new MethodChannel(engine.getDartExecutor().getBinaryMessenger(),
                "moonetise/adapter_b1_research");
        diagnostics.setMethodCallHandler((call, reply) -> {
            switch (call.method) {
                case "begin":
                    if (starting || destroyed ||
                            (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
                        reply.error("RUN_NOT_ALLOWED", "Use a fresh debug process.", null);
                        return;
                    }
                    starting = true;
                    scanner.execute(() -> {
                        int count;
                        try { count = InstalledApkCheck.count(getApplicationContext()); }
                        catch (Exception ignored) { count = -1; }
                        final int definitions = count;
                        main.post(() -> {
                            if (destroyed) return;
                            if (definitions < 0) {
                                reply.error("APK_SCAN_FAILED", "Installed APK could not be checked.", null);
                                return;
                            }
                            if (definitions != 1) {
                                reply.error("APK_CLASS_CHECK_FAILED",
                                        "Installed APK must contain exactly one adapter definition.", null);
                                return;
                            }
                            try {
                                String run = B1Trace.begin();
                                main.postDelayed(deadline, B1Trace.WINDOW_MILLIS);
                                reply.success(run);
                            } catch (IllegalStateException used) {
                                reply.error("FRESH_PROCESS_REQUIRED", "Force-stop before another run.", null);
                            }
                        });
                    });
                    break;
                case "event":
                    try {
                        B1Trace.host(call.argument("event"), call.argument("result"),
                                call.argument("category"), call.argument("network"), call.argument("state"));
                        reply.success(null);
                    } catch (RuntimeException invalid) {
                        reply.error("INVALID_DIAGNOSTIC_EVENT", "Only fixed diagnostic enums are accepted.", null);
                    }
                    break;
                case "export":
                    reply.success(B1Trace.exportJsonLines());
                    break;
                case "close":
                    B1Trace.close(B1Trace.Category.DEADLINE);
                    reply.success(null);
                    break;
                default:
                    reply.notImplemented();
            }
        });
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        B1Trace.record(B1Trace.Event.HOST_DETACHED, B1Trace.Result.CLOSED, B1Trace.Category.DETACHED);
        B1Trace.close(B1Trace.Category.DETACHED);
        main.removeCallbacks(deadline);
        if (diagnostics != null) diagnostics.setMethodCallHandler(null);
        scanner.shutdown();
        super.onDestroy();
    }
}
