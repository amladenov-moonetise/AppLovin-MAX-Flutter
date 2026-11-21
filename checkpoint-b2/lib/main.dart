import 'dart:async';
import 'package:applovin_max/applovin_max.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(const MaterialApp(home: B2Screen()));

class B2Screen extends StatefulWidget {
  const B2Screen({super.key});
  @override
  State<B2Screen> createState() => _B2ScreenState();
}

class _B2ScreenState extends State<B2Screen> with WidgetsBindingObserver {
  static const channel = MethodChannel('moonetise/adapter_b2_research');
  static const unit = 'd236de754b83a168';
  static const adapter = 'com.applovin.mediation.adapters.PrebidMaxMediationAdapter';
  final keyInput = TextEditingController();
  final expiryInput = TextEditingController(text: '5000');
  bool authorized = false, used = false, closed = false, invalidated = false;
  bool terminal = false, ready = false, shown = false, hidden = false, diagnosticsFailed = false;
  String mode = 'NO_DEMAND', winner = 'UNKNOWN', status = 'No SDK initialization occurs on launch.';
  String report = '';
  Timer? deadline;

  @override
  void initState() { super.initState(); WidgetsBinding.instance.addObserver(this); }

  Future<void> event(String name, String result,
      {String category = 'NONE', String network = 'UNKNOWN', String state = 'UNKNOWN'}) async {
    try {
      await channel.invokeMethod<void>('event', {
        'event': name, 'result': result, 'category': category, 'network': network, 'state': state,
      });
    } catch (_) { diagnosticsFailed = true; }
  }

  String network(String name) {
    if (name == 'TestPBS' || name == 'Custom Network (SDK) - TestPBS') return 'TESTPBS';
    if (name == 'AppLovin' || name == 'AppLovin Exchange' || name == 'ALX') return 'APPLOVIN';
    return 'OTHER';
  }
  String category(MaxError error) {
    if (error.code == ErrorCode.noFill) return 'NO_FILL';
    if (error.code == ErrorCode.invalidAdUnitId) return 'CREDENTIAL_OR_CONFIGURATION';
    if (error.code == ErrorCode.networkError || error.code == ErrorCode.networkTimeout) return 'CONNECTIVITY';
    return 'SDK_OR_NETWORK_ERROR';
  }
  Future<void> waterfall(MaxAdWaterfallInfo? value) async {
    if (value == null) return;
    for (final response in value.networkResponses.take(24)) {
      String state = 'UNKNOWN';
      if (response.adLoadState == AdLoadState.adLoaded) state = 'AD_LOADED';
      if (response.adLoadState == AdLoadState.adFailedToLoad) state = 'AD_FAILED_TO_LOAD';
      if (response.adLoadState == AdLoadState.adLoadNotAttempted) state = 'AD_LOAD_NOT_ATTEMPTED';
      await event('HOST_WATERFALL',
          response.mediatedNetwork.adapterClassName == adapter ? 'CLASS_MATCH' : 'CLASS_MISMATCH',
          network: network(response.mediatedNetwork.name), state: state,
          category: response.error == null ? 'NONE' : category(response.error!));
    }
  }
  Future<void> discardUnused() async {
    try { await channel.invokeMethod<void>('terminal'); }
    catch (_) { diagnosticsFailed = true; }
  }
  Future<void> loaded(MaxAd ad) async {
    if (closed || terminal || ad.adUnitId != unit) return;
    terminal = true;
    await discardUnused();
    await event('HOST_LOAD_RESULT', 'LOADED');
    winner = network(ad.networkName);
    await event('HOST_SELECTED_NETWORK', 'LOADED', network: winner);
    await waterfall(ad.waterfall);
    if (mounted) setState(() {
      ready = !invalidated;
      status = 'MAX loaded: $winner. Show is a separate explicit action.';
    });
  }
  Future<void> failed(String id, MaxError error) async {
    if (closed || terminal || id != unit) return;
    terminal = true;
    await discardUnused();
    await event('HOST_LOAD_RESULT', 'FAILED', category: category(error));
    await waterfall(error.waterfall);
    if (mounted) setState(() => status = 'Genuine MAX load failure. Export the report; no retry.');
  }

  Future<void> run() async {
    final expiry = int.tryParse(expiryInput.text);
    if (used || !authorized || keyInput.text.trim().isEmpty ||
        expiry == null || expiry < 1000 || expiry > 30000) return;
    final sdkKey = keyInput.text.trim();
    keyInput.clear();
    FocusScope.of(context).unfocus();
    setState(() { used = true; status = 'Installed APK uniqueness check...'; });
    try {
      await channel.invokeMethod<String>('begin', {
        'authorized': authorized, 'mode': mode, 'eligibilityMs': expiry,
      }).timeout(const Duration(seconds: 15));
    } catch (_) {
      if (mounted) setState(() => status = 'Preflight rejected or timed out. Do not run MAX. Fresh process required.');
      return;
    }
    deadline = Timer(const Duration(seconds: 175), () async {
      closed = true;
      try { await channel.invokeMethod<void>('close'); } catch (_) { diagnosticsFailed = true; }
      if (mounted) setState(() { ready = false; status = 'Observation closed. Export, then force-stop.'; });
    });
    AppLovinMAX.setInterstitialListener(InterstitialListener(
      onAdLoadedCallback: (ad) { loaded(ad); },
      onAdLoadFailedCallback: (id, error) { failed(id, error); },
      onAdDisplayedCallback: (_) { event('HOST_DISPLAYED', 'SUCCESS'); },
      onAdHiddenCallback: (_) {
        event('HOST_HIDDEN', 'SUCCESS');
        if (mounted) setState(() { hidden = true; ready = false; status = 'Dismissed. Confirm visibility only if you saw the expected synthetic creative.'; });
      },
      onAdDisplayFailedCallback: (_, __) {
        event('HOST_ERROR', 'FAILED', category: 'SDK_OR_NETWORK_ERROR');
        if (mounted) setState(() { ready = false; status = 'MAX display failed: inspect redacted report.'; });
      },
      onAdClickedCallback: (_) {},
      onAdRevenuePaidCallback: (_) {}, // Publisher-owned; never export amounts or infer revenue.
    ));
    try {
      if (mounted) setState(() => status = 'Initializing pinned Prebid and official MAX...');
      try {
        await channel.invokeMethod<bool>('initializePrebid').timeout(const Duration(seconds: 10));
      } catch (_) {
        await event('HOST_ERROR', 'FAILED', category: 'PREPARATION');
        // Optional Prebid initialization failure does not prevent official MAX initialization.
      }
      if (closed || invalidated || !mounted) return;
      AppLovinMAX.setVerboseLogging(false);
      final config = await AppLovinMAX.initialize(sdkKey).timeout(const Duration(seconds: 20));
      final initialized = await AppLovinMAX.isInitialized().timeout(const Duration(seconds: 3));
      if (closed || invalidated || !mounted) return;
      if (config == null || initialized != true) {
        await event('HOST_MAX_INITIALIZED', 'FAILED', category: 'SDK_OR_NETWORK_ERROR');
        setState(() => status = 'MAX initialization failed: INCONCLUSIVE. No load requested.');
        return;
      }
      await event('HOST_MAX_INITIALIZED', 'SUCCESS');
      String preparation = 'FAILED';
      try {
        preparation = await channel.invokeMethod<String>('prepare')
            .timeout(const Duration(seconds: 4)) ?? 'FAILED';
      } catch (_) {
        await discardUnused();
        await event('HOST_ERROR', 'FAILED', category: 'PREPARATION');
      }
      if (closed || invalidated || !mounted) return;
      try { await channel.invokeMethod<void>('arm'); }
      catch (_) {
        // Asterise failure must not block this independently authorized MAX load.
        await discardUnused();
      }
      if (closed || invalidated || !mounted) return;
      await event('HOST_LOAD_REQUESTED', 'REQUESTED');
      setState(() => status = 'Preparation: $preparation. Requesting one official MAX interstitial load.');
      AppLovinMAX.loadInterstitial(unit);
    } catch (_) {
      await event('HOST_ERROR', 'FAILED', category: 'HOST_EXCEPTION');
      if (mounted) setState(() => status = 'Initialization/channel failure: INCONCLUSIVE. No automatic retry.');
    }
  }

  Future<void> show() async {
    if (!ready || shown || closed || invalidated) return;
    shown = true;
    setState(() => ready = false);
    await event('HOST_SHOW_REQUESTED', 'REQUESTED');
    try {
      if (await AppLovinMAX.isInterstitialReady(unit) == true && !closed && !invalidated) {
        AppLovinMAX.showInterstitial(unit);
      } else {
        await event('HOST_ERROR', 'FAILED', category: 'SDK_OR_NETWORK_ERROR');
      }
    } catch (_) { await event('HOST_ERROR', 'FAILED', category: 'HOST_EXCEPTION'); }
  }
  Future<void> export() async {
    try {
      final value = await channel.invokeMapMethod<String, String>('export');
      final classification = diagnosticsFailed ? 'INCONCLUSIVE' : value?['classification'] ?? 'INCONCLUSIVE';
      final output = '{"classification":"$classification"}\n${value?['jsonl'] ?? ''}';
      await Clipboard.setData(ClipboardData(text: output));
      if (mounted) setState(() => report = output);
    } catch (_) {
      if (mounted) setState(() => status = 'Export failed: INCONCLUSIVE.');
    }
  }
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // A fullscreen show itself can background the host; do not destroy its creative.
    if (used && !shown && state == AppLifecycleState.paused) {
      invalidated = true;
      channel.invokeMethod<void>('invalidate');
      if (mounted) setState(() { ready = false; status = 'Backgrounded before Show: fresh process required.'; });
    }
  }
  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    deadline?.cancel();
    if (used) channel.invokeMethod<void>('invalidate');
    AppLovinMAX.setInterstitialListener(null);
    keyInput.dispose(); expiryInput.dispose();
    super.dispose();
  }
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Moonetise B2 — interstitial research')),
    body: SafeArea(child: ListView(padding: const EdgeInsets.all(16), children: [
      const Text('One run per fresh process. App ID com.moonetise.asterise.sample replaces the existing sample if installed. No automatic show or retry.'),
      TextField(controller: keyInput, enabled: !used, obscureText: true, autocorrect: false,
        enableSuggestions: false, decoration: const InputDecoration(labelText: 'Authorized development MAX SDK key — memory only')),
      DropdownButton<String>(value: mode, items: const [
        DropdownMenuItem(value: 'NO_DEMAND', child: Text('A — NO PREBID DEMAND')),
        DropdownMenuItem(value: 'SYNTHETIC', child: Text(r'B — APPROVED SYNTHETIC $3.00')),
      ], onChanged: used ? null : (value) => setState(() => mode = value!)),
      TextField(controller: expiryInput, enabled: !used, keyboardType: TextInputType.number,
        decoration: const InputDecoration(labelText: 'Response eligibility ms: 1000–30000; default 5000')),
      CheckboxListTile(value: authorized,
        title: const Text('Publisher privacy/CMP and development network authorization are already satisfied. This does not set consent.'),
        onChanged: used ? null : (value) => setState(() => authorized = value ?? false)),
      ElevatedButton(onPressed: used || !authorized ? null : run, child: const Text('RUN INTERSTITIAL TEST')),
      ElevatedButton(onPressed: ready && !shown && !closed ? show : null, child: const Text('SHOW LOADED INTERSTITIAL')),
      Text(status),
      if (mode == 'SYNTHETIC' && hidden && winner == 'TESTPBS' && !closed) ...[
        TextButton(onPressed: () => event('HOST_OPERATOR_VISIBLE', 'YES'),
          child: const Text('I visibly confirmed the expected synthetic interstitial')),
        TextButton(onPressed: () => event('HOST_OPERATOR_VISIBLE', 'NO'),
          child: const Text('The expected synthetic creative was NOT visible')),
      ],
      TextButton(onPressed: used ? export : null, child: const Text('COPY REDACTED ACCEPTANCE REPORT')),
      if (report.isNotEmpty) SelectableText(report),
    ])),
  );
}
