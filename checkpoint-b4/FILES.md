# B4 added source inventory

All paths are repository-relative. Only checkpoint-b4 is added; no prior fixture is edited.

- checkpoint-b4/.gitignore
- checkpoint-b4/FILES.md
- checkpoint-b4/LICENSE-Prebid-Apache-2.0.txt
- checkpoint-b4/README.md
- checkpoint-b4/REPORT_FORMAT.md
- checkpoint-b4/SOURCE_PROVENANCE.md
- checkpoint-b4/THIRD_PARTY_NOTICES.md
- checkpoint-b4/android/app/build.gradle
- checkpoint-b4/android/app/src/debug/java/com/moonetise/research/checkpointb4/ResearchPreparation.java
- checkpoint-b4/android/app/src/main/AndroidManifest.xml
- checkpoint-b4/android/app/src/main/java/com/moonetise/research/checkpointb4/InstalledApkCheck.java
- checkpoint-b4/android/app/src/main/java/com/moonetise/research/checkpointb4/MainActivity.java
- checkpoint-b4/android/app/src/release/java/com/moonetise/research/checkpointb4/ResearchPreparation.java
- checkpoint-b4/android/build.gradle
- checkpoint-b4/android/gradle.properties
- checkpoint-b4/android/replacement/build.gradle
- checkpoint-b4/android/replacement/consumer-rules.pro
- checkpoint-b4/android/replacement/src/main/AndroidManifest.xml
- checkpoint-b4/android/replacement/src/main/java/com/applovin/mediation/adapters/PrebidMaxMediationAdapter.java
- checkpoint-b4/android/replacement/src/main/java/com/applovin/mediation/adapters/prebid/ListenersCreator.java
- checkpoint-b4/android/replacement/src/main/java/com/applovin/mediation/adapters/prebid/ParametersChecker.java
- checkpoint-b4/android/replacement/src/main/java/com/applovin/mediation/adapters/prebid/managers/MaxInterstitialManager.java
- checkpoint-b4/android/replacement/src/main/java/com/applovin/mediation/adapters/prebid/managers/MaxRewardedManager.java
- checkpoint-b4/android/replacement/src/main/java/com/moonetise/research/b4/B4Trace.java
- checkpoint-b4/android/replacement/src/main/java/com/moonetise/research/b4/CycleGate.java
- checkpoint-b4/android/replacement/src/main/java/com/moonetise/research/b4/DemandBridge.java
- checkpoint-b4/android/replacement/src/main/java/com/moonetise/research/b4/PendingSlot.java
- checkpoint-b4/android/replacement/src/main/java/com/moonetise/research/b4/ResponseParameters.java
- checkpoint-b4/android/replacement/src/main/java/com/moonetise/research/b4/RewardObservation.java
- checkpoint-b4/android/replacement/src/main/java/com/moonetise/research/b4/RewardedDemandBridge.java
- checkpoint-b4/android/replacement/src/test/java/com/moonetise/research/b4/CycleGateTest.java
- checkpoint-b4/android/replacement/src/test/java/com/moonetise/research/b4/FormatScopeTest.java
- checkpoint-b4/android/replacement/src/test/java/com/moonetise/research/b4/InterstitialSourceRegressionTest.java
- checkpoint-b4/android/replacement/src/test/java/com/moonetise/research/b4/PendingSlotTest.java
- checkpoint-b4/android/replacement/src/test/java/com/moonetise/research/b4/RewardObservationTest.java
- checkpoint-b4/android/settings.gradle
- checkpoint-b4/lib/main.dart
- checkpoint-b4/pubspec.lock
- checkpoint-b4/pubspec.yaml

The same-name adapter and rewarded manager contain the rewarded mediation changes.
RewardedDemandBridge scopes the B3 slot mechanism to rewarded. MainActivity,
ResearchPreparation, B4Trace and lib/main.dart are research scaffolding, not production APIs.
Existing B3 interstitial helper/provider logic is retained after naming normalization.
Five focused JVM test classes are supplied. Existing operator-run reports show 19 passing
tests, including frozen-source regression and the Java 8 readAllBytes compatibility fix.
Codex did not rerun tests/builds. README.md records the separate operator-reviewed B4 PASS,
inspected debug/release build markers, limitations and operator-only reproduction commands.
