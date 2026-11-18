package com.applovin.applovin_max;

import static com.applovin.applovin_max.InterstitialReservationRegistry.Event.*;

/** Explicit dependency-free JVM runner; no Android/MAX SDK, network or Gradle/JUnit discovery. */
public final class InterstitialReservationRegistryTest
{
    private static final class Fixture
    {
        long now = 100;
        int loads;
        int shows;
        final Object object = new Object();
        final InterstitialReservationRegistry registry = new InterstitialReservationRegistry(
                () -> now, new InterstitialReservationRegistry.IdentityDomain());
        final long generation;
        final InterstitialReservationRegistry.Binding binding;

        Fixture()
        {
            registry.attach();
            generation = registry.created("unit-a", object);
            binding = registry.binding("unit-a", object);
        }

        InterstitialReservationRegistry.Attempt reserve() { return registry.reserve("unit-a", object, false); }
        boolean current(InterstitialReservationRegistry.Attempt a)
        {
            return registry.isCurrent("unit-a", object, a.id, a.generation, a.epoch);
        }
        void load(String attempt)
        {
            registry.beforeLoad("unit-a", object, attempt);
            loads++; // Fake native boundary, reached only after authorization.
        }
        void show(String attempt)
        {
            registry.beforeShow("unit-a", object, attempt, true);
            shows++;
        }
        boolean event(InterstitialReservationRegistry.Event event) { return registry.acceptCallback(binding, event); }
    }

    private static void check(boolean condition)
    {
        if (!condition) throw new AssertionError("Interstitial invariant failed");
    }

    private static void rejects(String code, Runnable action)
    {
        try { action.run(); }
        catch (InterstitialReservationRegistry.Rejected error)
        {
            if (!code.equals(error.code)) throw new AssertionError("Expected " + code + ", got " + error.code);
            return;
        }
        throw new AssertionError("Expected rejection: " + code);
    }

    private static void idleReservationSucceeds()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        check(f.current(a) && a.generation == f.generation);
        check(a.createdAtMs == 100 && a.deadlineMs == 5_100);
    }

    private static void duplicateReservationRejected()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        rejects("ALREADY_RESERVED", f::reserve);
        check(f.current(a));
    }

    private static void loadingRejected()
    {
        Fixture f = new Fixture();
        f.load(null);
        rejects("INTERSTITIAL_BUSY", f::reserve);
    }

    private static void loadedRejected()
    {
        Fixture f = new Fixture();
        f.load(null);
        check(f.event(LOADED));
        rejects("INTERSTITIAL_BUSY", f::reserve);
    }

    private static void showingRejected()
    {
        Fixture f = new Fixture();
        f.load(null); f.event(LOADED); f.show(null); f.event(DISPLAYED);
        rejects("INTERSTITIAL_BUSY", f::reserve);
    }

    private static void invalidAttemptRejected()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        check(!f.registry.isCurrent("unit-a", f.object, null, a.generation, a.epoch));
        check(!f.registry.isCurrent("unit-a", f.object, "invalid", a.generation, a.epoch));
    }

    private static void generationMismatchRejected()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        check(!f.registry.isCurrent("unit-a", f.object, a.id, a.generation + 1, a.epoch));
        check(!f.registry.isCurrent("unit-a", new Object(), a.id, a.generation, a.epoch));
        Object next = new Object();
        check(f.registry.created("unit-a", next) != a.generation && !f.current(a));
        rejects("RESERVATION_UNSAFE", f::reserve);
    }

    private static void epochMismatchRejected()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        check(!f.registry.isCurrent("unit-a", f.object, a.id, a.generation, a.epoch + 1));
    }

    private static void unitsAreIsolated()
    {
        Fixture f = new Fixture();
        Object other = new Object();
        f.registry.created("unit-b", other);
        InterstitialReservationRegistry.Attempt a = f.reserve();
        InterstitialReservationRegistry.Attempt b = f.registry.reserve("unit-b", other, false);
        check(!a.id.equals(b.id) && a.generation != b.generation);
        f.load(a.id);
        check(f.registry.isCurrent("unit-b", other, b.id, b.generation, b.epoch));
    }

    private static void detachInvalidatesAndReattachCannotRevive()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        f.registry.detach();
        check(!f.current(a) && !f.registry.isAttached());
        rejects("ENGINE_DETACHED", f::reserve);
        f.registry.attach();
        check(!f.current(a));
        rejects("STALE_OBJECT", f::reserve);
        Object fresh = new Object();
        f.registry.created("unit-b", fresh);
        check(f.registry.reserve("unit-b", fresh, false).epoch != a.epoch);
    }

    private static void duplicateAttachPreservesEpoch()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        f.registry.attach();
        check(f.current(a));
        f.load(a.id);
        f.registry.attach();
        check(f.event(LOADED) && f.registry.canForwardToEngine(f.binding));
    }

    private static void expiryCannotReleaseCoordinatedObject()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        f.now = a.deadlineMs - 1;
        check(f.current(a));
        f.now++;
        check(!f.current(a));
        rejects("ATTEMPT_EXPIRED", f::reserve);
        rejects("ATTEMPT_REQUIRED", () -> f.load(null));
        check(f.loads == 0);
    }

    private static void idOnlyDispatchCannotInvalidateValidReservation()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        rejects("ATTEMPT_REQUIRED", () -> f.load(null));
        rejects("ATTEMPT_REQUIRED", () -> f.show(null));
        check(f.current(a) && f.loads == 0 && f.shows == 0);
    }

    private static void failedOrHiddenObjectIsNotAssumedIdle()
    {
        Fixture f = new Fixture();
        f.load(null); f.event(LOAD_FAILED);
        rejects("RESERVATION_UNSAFE", f::reserve);
        f.load(null);
        rejects("INTERSTITIAL_BUSY", f::reserve);
        check(f.loads == 2);
    }

    private static void unexpectedCallbackCannotCreateIdle()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        check(!f.event(LOADED) && !f.current(a));
        check(!f.event(LOAD_FAILED));
        rejects("ATTEMPT_INVALIDATED", f::reserve);
    }

    private static void nativeReadinessCorroborationRejects()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        rejects("INTERSTITIAL_BUSY", () -> f.registry.reserve("unit-a", f.object, true));
        check(!f.current(a));
    }

    private static void overlappingOrdinaryDispatchBecomesUnsafe()
    {
        Fixture f = new Fixture();
        f.load(null); f.load(null);
        check(f.event(LOADED)); // Ordinary payload still forwards.
        rejects("RESERVATION_UNSAFE", f::reserve);
        check(f.loads == 2);
    }

    private static void unknownObjectRejected()
    {
        Fixture f = new Fixture();
        rejects("RESERVATION_UNSAFE", () -> f.registry.reserve("missing", new Object(), false));
    }

    private static void competingEnginesCannotReserveSameId()
    {
        InterstitialReservationRegistry.IdentityDomain domain = new InterstitialReservationRegistry.IdentityDomain();
        InterstitialReservationRegistry first = new InterstitialReservationRegistry(() -> 100, domain);
        InterstitialReservationRegistry second = new InterstitialReservationRegistry(() -> 100, domain);
        first.attach(); second.attach();
        Object one = new Object(); Object two = new Object();
        first.created("shared", one);
        InterstitialReservationRegistry.Attempt a = first.reserve("shared", one, false);
        second.created("shared", two);
        check(!first.isCurrent("shared", one, a.id, a.generation, a.epoch));
        rejects("STALE_OBJECT", () -> first.beforeLoad("shared", one, a.id));
        rejects("ATTEMPT_REQUIRED", () -> second.beforeLoad("shared", two, null));
        rejects("STALE_ATTEMPT", () -> second.beforeLoad("shared", two, a.id));
    }

    private static void hostLossInvalidatesWithoutDetachingEngine()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        f.registry.hostUnavailable();
        check(f.registry.isAttached() && !f.current(a));
        rejects("ATTEMPT_INVALIDATED", f::reserve);
    }

    private static void validAttemptLoadsAndShowsExactlyOnce()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        f.load(a.id);
        check(f.event(LOADED));
        check(f.registry.canReportReady("unit-a", f.object, true));
        f.show(a.id);
        check(f.event(DISPLAYED) && f.event(HIDDEN));
        check(f.loads == 1 && f.shows == 1);
    }

    private static void duplicateLoadAndShowRejected()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        f.load(a.id);
        rejects("INTERSTITIAL_BUSY", () -> f.load(a.id));
        f.event(LOADED);
        rejects("INTERSTITIAL_BUSY", () -> f.load(a.id));
        f.show(a.id);
        rejects("INTERSTITIAL_NOT_READY", () -> f.show(a.id));
        check(f.loads == 1 && f.shows == 1);
    }

    private static void wrongAttemptDoesNotDamageCurrentAttempt()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        rejects("STALE_ATTEMPT", () -> f.load("other"));
        rejects("STALE_ATTEMPT", () -> f.show("other"));
        check(f.current(a) && f.loads == 0 && f.shows == 0);
    }

    private static void initialDeadlineDoesNotExpireLoadedAd()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        f.load(a.id);
        f.now = a.deadlineMs + 60_000;
        check(f.event(LOADED));
        check(f.registry.canReportReady("unit-a", f.object, true));
        rejects("ATTEMPT_REQUIRED", () -> f.show(null));
        f.show(a.id);
        check(f.shows == 1);
    }

    private static void invalidatedObjectCannotBecomeOrdinary()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        f.registry.markUnsafe(f.binding);
        rejects("ATTEMPT_INVALIDATED", () -> f.load(a.id));
        rejects("ATTEMPT_REQUIRED", () -> f.load(null));
        check(!f.event(LOADED) && f.loads == 0);
    }

    private static void obsoleteCallbacksCannotUpdateReplacement()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        f.load(a.id);
        Object next = new Object();
        f.registry.created("unit-a", next);
        check(!f.event(LOADED) && !f.event(LOAD_FAILED));
        check(!f.registry.canReportReady("unit-a", next, true));
        rejects("ATTEMPT_REQUIRED", () -> f.registry.beforeLoad("unit-a", next, null));
    }

    private static void duplicateCoordinatedCallbacksAreSuppressed()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        f.load(a.id);
        check(f.event(LOADED) && !f.event(LOADED));
        f.show(a.id);
        check(f.event(DISPLAYED) && !f.event(DISPLAYED));
        check(f.event(HIDDEN) && !f.event(HIDDEN));
        check(f.event(REVENUE) && !f.event(REVENUE));
        check(!f.event(LOADED));
    }

    private static void dispatchedPresentationSurvivesHostLossAndExpiry()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        f.load(a.id); f.event(LOADED); f.show(a.id);
        f.registry.hostUnavailable();
        f.now = a.deadlineMs + 1;
        check(f.event(DISPLAYED) && f.event(CLICKED) && f.event(HIDDEN) && f.event(REVENUE));
        check(f.registry.canForwardToEngine(f.binding));
        check(!f.current(a));
    }

    private static void showingOldGenerationKeepsOnlyItsPresentationReceipts()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        f.load(a.id); f.event(LOADED); f.show(a.id);
        Object next = new Object();
        f.registry.created("unit-a", next);
        check(f.event(DISPLAYED) && f.event(HIDDEN) && f.event(REVENUE));
        check(!f.event(LOADED) && !f.registry.canReportReady("unit-a", next, true));
    }

    private static void detachedEngineCannotForwardIntoNewEngine()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        f.load(a.id); f.event(LOADED); f.show(a.id);
        f.registry.detach();
        check(f.event(DISPLAYED) && !f.registry.canForwardToEngine(f.binding));
        f.registry.attach();
        check(f.event(HIDDEN) && f.event(REVENUE));
        check(!f.registry.canForwardToEngine(f.binding));
        rejects("STALE_OBJECT", () -> f.load(a.id));
    }

    private static void nativeNotReadyBlocksShowPermanently()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        f.load(a.id); f.event(LOADED);
        rejects("INTERSTITIAL_NOT_READY", () -> f.registry.beforeShow("unit-a", f.object, a.id, false));
        rejects("ATTEMPT_INVALIDATED", () -> f.show(a.id));
        check(f.shows == 0);
    }

    private static void staleAttemptCannotClaimOrdinaryObject()
    {
        Fixture f = new Fixture();
        rejects("STALE_ATTEMPT", () -> f.load("old"));
        rejects("STALE_ATTEMPT", () -> f.show("old"));
        check(f.loads == 0 && f.shows == 0);
        f.load(null);
        check(f.loads == 1);
    }

    private static void terminalCoordinatedObjectCannotReload()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        f.load(a.id); check(f.event(LOAD_FAILED));
        check(!f.event(LOAD_FAILED) && !f.event(LOADED));
        rejects("ATTEMPT_INVALIDATED", () -> f.load(a.id));
        rejects("ATTEMPT_REQUIRED", () -> f.load(null));
        check(f.loads == 1);
    }

    private static void sdkReloadInvalidationCannotShowReplacementCreative()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        f.load(a.id); f.event(LOADED);
        f.registry.markUnsafe(f.binding);
        check(!f.event(LOADED) && !f.registry.canReportReady("unit-a", f.object, true));
        rejects("ATTEMPT_INVALIDATED", () -> f.show(a.id));
    }

    private static void oldGenerationInvalidationCannotDamageDifferentUnit()
    {
        Fixture f = new Fixture();
        InterstitialReservationRegistry.Attempt a = f.reserve();
        Object other = new Object();
        f.registry.created("unit-b", other);
        InterstitialReservationRegistry.Attempt b = f.registry.reserve("unit-b", other, false);
        f.registry.markUnsafe(f.binding);
        check(!f.current(a));
        check(f.registry.isCurrent("unit-b", other, b.id, b.generation, b.epoch));
    }

    private static void independentOrdinaryEnginesRetainTheirCallbacks()
    {
        InterstitialReservationRegistry.IdentityDomain domain = new InterstitialReservationRegistry.IdentityDomain();
        InterstitialReservationRegistry first = new InterstitialReservationRegistry(() -> 100, domain);
        InterstitialReservationRegistry second = new InterstitialReservationRegistry(() -> 100, domain);
        first.attach(); second.attach();
        Object one = new Object(); Object two = new Object();
        first.created("ordinary", one);
        InterstitialReservationRegistry.Binding old = first.binding("ordinary", one);
        first.beforeLoad("ordinary", one, null);
        second.created("ordinary", two);
        second.beforeLoad("ordinary", two, null);
        check(first.acceptCallback(old, LOADED));
        first.beforeShow("ordinary", one, null, true);
        check(first.acceptCallback(old, DISPLAYED));
        check(first.acceptCallback(old, HIDDEN) && first.acceptCallback(old, REVENUE));
        check(first.canForwardToEngine(old));
    }

    public static void main(String[] args)
    {
        idleReservationSucceeds();
        duplicateReservationRejected();
        loadingRejected();
        loadedRejected();
        showingRejected();
        invalidAttemptRejected();
        generationMismatchRejected();
        epochMismatchRejected();
        unitsAreIsolated();
        detachInvalidatesAndReattachCannotRevive();
        duplicateAttachPreservesEpoch();
        expiryCannotReleaseCoordinatedObject();
        idOnlyDispatchCannotInvalidateValidReservation();
        failedOrHiddenObjectIsNotAssumedIdle();
        unexpectedCallbackCannotCreateIdle();
        nativeReadinessCorroborationRejects();
        overlappingOrdinaryDispatchBecomesUnsafe();
        unknownObjectRejected();
        competingEnginesCannotReserveSameId();
        hostLossInvalidatesWithoutDetachingEngine();
        validAttemptLoadsAndShowsExactlyOnce();
        duplicateLoadAndShowRejected();
        wrongAttemptDoesNotDamageCurrentAttempt();
        initialDeadlineDoesNotExpireLoadedAd();
        invalidatedObjectCannotBecomeOrdinary();
        obsoleteCallbacksCannotUpdateReplacement();
        duplicateCoordinatedCallbacksAreSuppressed();
        dispatchedPresentationSurvivesHostLossAndExpiry();
        showingOldGenerationKeepsOnlyItsPresentationReceipts();
        detachedEngineCannotForwardIntoNewEngine();
        nativeNotReadyBlocksShowPermanently();
        staleAttemptCannotClaimOrdinaryObject();
        terminalCoordinatedObjectCannotReload();
        sdkReloadInvalidationCannotShowReplacementCreative();
        oldGenerationInvalidationCannotDamageDifferentUnit();
        independentOrdinaryEnginesRetainTheirCallbacks();
        System.out.println("36 interstitial reservation/dispatch checks passed.");
    }
}
