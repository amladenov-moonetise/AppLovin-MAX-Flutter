import 'dart:async';

import 'package:applovin_max/applovin_max.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('applovin_max');
  final calls = <MethodCall>[];

  setUp(() {
    final previousPlatform = debugDefaultTargetPlatformOverride;
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    addTearDown(() {
      debugDefaultTargetPlatformOverride = previousPlatform;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    });
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return call.method == 'reserveInterstitial' ? 'opaque-attempt' : null;
    });
  });

  test('reservation returns opaque handle without initializing or loading',
      () async {
    expect(await AppLovinMAX.reserveInterstitial('unit-a'), 'opaque-attempt');
    expect(calls.map((call) => call.method), ['reserveInterstitial']);
    expect(calls.single.arguments, {'ad_unit_id': 'unit-a'});
  });

  test('busy rejection is propagated unchanged', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      throw PlatformException(code: 'INTERSTITIAL_BUSY');
    });
    await expectLater(
      AppLovinMAX.reserveInterstitial('unit-a'),
      throwsA(isA<PlatformException>().having(
        (error) => error.code,
        'code',
        'INTERSTITIAL_BUSY',
      )),
    );
  });

  test('missing native handle is rejected', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async => null);
    await expectLater(
      AppLovinMAX.reserveInterstitial('unit-a'),
      throwsA(isA<PlatformException>().having(
        (error) => error.code,
        'code',
        'INVALID_RESERVATION_RESPONSE',
      )),
    );
  });

  test('unsupported reservation never invokes native channel', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
    await expectLater(
      AppLovinMAX.reserveInterstitial('unit-a'),
      throwsUnsupportedError,
    );
    expect(calls, isEmpty);
  });

  test('ordinary load and show retain original wire arguments', () async {
    await AppLovinMAX.loadInterstitial('unit-a');
    await AppLovinMAX.showInterstitial(
      'unit-a',
      placement: 'test-placement',
      customData: 'test-data',
    );
    expect(
      calls.map((call) => call.method),
      ['loadInterstitial', 'showInterstitial'],
    );
    expect(calls[0].arguments, {'ad_unit_id': 'unit-a'});
    expect(calls[1].arguments, {
      'ad_unit_id': 'unit-a',
      'placement': 'test-placement',
      'custom_data': 'test-data',
    });
  });

  test('ID-only rejection during coordination is awaitable', () async {
    var reserved = false;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      if (call.method == 'reserveInterstitial') {
        reserved = true;
        return 'opaque-attempt';
      }
      if (reserved && call.arguments['attempt_id'] == null) {
        throw PlatformException(code: 'ATTEMPT_REQUIRED');
      }
      return null;
    });
    await AppLovinMAX.reserveInterstitial('unit-a');
    for (final operation in [
      () => AppLovinMAX.loadInterstitial('unit-a'),
      () => AppLovinMAX.showInterstitial('unit-a'),
    ]) {
      await expectLater(
        operation(),
        throwsA(isA<PlatformException>().having(
          (error) => error.code,
          'code',
          'ATTEMPT_REQUIRED',
        )),
      );
    }
    expect(calls.length, 3); // No retry or fallback channel invocation.
  });

  test('explicit attempt is passed to load without initialization', () async {
    await AppLovinMAX.loadInterstitial('unit-a', attemptId: 'opaque-attempt');
    expect(calls.single.method, 'loadInterstitial');
    expect(calls.single.arguments, {
      'ad_unit_id': 'unit-a',
      'attempt_id': 'opaque-attempt',
    });
  });

  test('explicit show carries attempt and existing placement data', () async {
    await AppLovinMAX.showInterstitial(
      'unit-a',
      attemptId: 'opaque-attempt',
      placement: 'test-placement',
      customData: 'test-data',
    );
    expect(calls.single.method, 'showInterstitial');
    expect(calls.single.arguments, {
      'ad_unit_id': 'unit-a',
      'placement': 'test-placement',
      'custom_data': 'test-data',
      'attempt_id': 'opaque-attempt',
    });
  });

  for (final code in [
    'INTERSTITIAL_BUSY',
    'INTERSTITIAL_NOT_READY',
    'STALE_ATTEMPT',
    'ATTEMPT_EXPIRED',
    'ATTEMPT_INVALIDATED',
    'STALE_OBJECT',
    'ENGINE_DETACHED',
    'RESERVATION_UNSAFE',
  ]) {
    test('load and show propagate $code without retry', () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
        calls.add(call);
        throw PlatformException(code: code);
      });
      for (final operation in [
        () => AppLovinMAX.loadInterstitial('unit-a', attemptId: 'old'),
        () => AppLovinMAX.showInterstitial('unit-a', attemptId: 'old'),
      ]) {
        await expectLater(
          operation(),
          throwsA(isA<PlatformException>().having(
            (error) => error.code,
            'code',
            code,
          )),
        );
      }
      expect(calls.length, 2);
    });
  }

  test('coordinated operations on iOS never invoke native channel', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
    await expectLater(
      AppLovinMAX.loadInterstitial('unit-a', attemptId: 'attempt'),
      throwsUnsupportedError,
    );
    await expectLater(
      AppLovinMAX.showInterstitial('unit-a', attemptId: 'attempt'),
      throwsUnsupportedError,
    );
    expect(calls, isEmpty);
  });

  test('readiness reports the guarded native result without an attempt leak',
      () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return false;
    });
    expect(await AppLovinMAX.isInterstitialReady('unit-a'), false);
    expect(calls.single.method, 'isInterstitialReady');
    expect(calls.single.arguments, {'ad_unit_id': 'unit-a'});
  });

  test('ordinary iOS arguments remain unchanged', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
    await AppLovinMAX.loadInterstitial('unit-a');
    await AppLovinMAX.showInterstitial('unit-a');
    expect(calls[0].arguments, {'ad_unit_id': 'unit-a'});
    expect(calls[1].arguments, {
      'ad_unit_id': 'unit-a',
      'placement': null,
      'custom_data': null,
    });
  });

  test('retirement returns completed cleanup without loading or showing',
      () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return true;
    });
    expect(
      await AppLovinMAX.retireInterstitial('unit-a', attemptId: 'attempt'),
      true,
    );
    expect(calls.single.method, 'retireInterstitial');
    expect(calls.single.arguments, {
      'ad_unit_id': 'unit-a',
      'attempt_id': 'attempt',
    });
  });

  test('deferred retirement is explicit and does not automatically retry',
      () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return false;
    });
    expect(
      await AppLovinMAX.retireInterstitial('unit-a', attemptId: 'attempt'),
      false,
    );
    await Future<void>.delayed(Duration.zero);
    expect(calls.length, 1);
  });

  test('missing cleanup acknowledgement cannot be reported as success',
      () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async => null);
    await expectLater(
      AppLovinMAX.retireInterstitial('unit-a', attemptId: 'attempt'),
      throwsA(isA<PlatformException>().having(
        (error) => error.code,
        'code',
        'INVALID_RETIREMENT_RESPONSE',
      )),
    );
  });

  for (final code in [
    'STALE_ATTEMPT',
    'STALE_OBJECT',
    'ENGINE_DETACHED',
    'INTERSTITIAL_CLEANUP_FAILED',
    'INVALID_ARGUMENT',
  ]) {
    test('retirement propagates $code without retry or fallback', () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
        calls.add(call);
        throw PlatformException(code: code);
      });
      await expectLater(
        AppLovinMAX.retireInterstitial('unit-a', attemptId: 'attempt'),
        throwsA(isA<PlatformException>().having(
          (error) => error.code,
          'code',
          code,
        )),
      );
      expect(calls.length, 1);
    });
  }

  test('unsupported retirement never invokes the native channel', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
    await expectLater(
      AppLovinMAX.retireInterstitial('unit-a', attemptId: 'attempt'),
      throwsUnsupportedError,
    );
    expect(calls, isEmpty);
  });

  test('repeated retirement forwards only explicit requests', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return true;
    });
    expect(
      await AppLovinMAX.retireInterstitial('unit-a', attemptId: 'attempt'),
      true,
    );
    expect(
      await AppLovinMAX.retireInterstitial('unit-a', attemptId: 'attempt'),
      true,
    );
    expect(
      calls.map((call) => call.method),
      ['retireInterstitial', 'retireInterstitial'],
    );
  });

  test('operations wait for native acknowledgement and never auto-show',
      () async {
    final acknowledgement = Completer<void>();
    var completed = false;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      await acknowledgement.future;
      return null;
    });
    final dispatch = AppLovinMAX.loadInterstitial('unit-a', attemptId: 'attempt')
        .then((_) => completed = true);
    await Future<void>.delayed(Duration.zero);
    expect(completed, false);
    acknowledgement.complete();
    await dispatch;
    expect(completed, true);
    expect(calls.map((call) => call.method), ['loadInterstitial']);
  });
}
