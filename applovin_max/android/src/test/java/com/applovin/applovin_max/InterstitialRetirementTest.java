package com.applovin.applovin_max;

import java.util.ArrayList;
import java.util.List;
import static com.applovin.applovin_max.InterstitialReservationRegistry.Event.*;

/** Pure JVM fake cleanup boundary; no SDK, Android, initialization, advertising or network. */
public final class InterstitialRetirementTest
{
    private static final class Fixture
    {
        long now = 100;
        int destroys;
        int loads;
        int shows;
        int failStep;
        Runnable duringCleanup;
        final List<String> events = new ArrayList<>();
        final Object object = new Object();
        final InterstitialReservationRegistry registry = new InterstitialReservationRegistry(
                () -> now, new InterstitialReservationRegistry.IdentityDomain());
        final InterstitialReservationRegistry.Binding binding;
        final InterstitialReservationRegistry.Attempt attempt;
        final InterstitialRetirement retirement;

        Fixture()
        {
            registry.attach();
            registry.created("unit-a", object);
            binding = registry.binding("unit-a", object);
            attempt = registry.reserve("unit-a", object, false);
            retirement = new InterstitialRetirement(registry, entry -> {
                check(!registry.isCurrent("unit-a", object, attempt.id, attempt.generation, attempt.epoch));
                events.add("DETACH_LISTENERS");
                if (failStep == 1) throw new Exception("Fake listener failure");
                destroys++;
                events.add("DESTROY");
                if (duringCleanup != null) duringCleanup.run();
                if (failStep == 2) throw new Exception("Fake uncertain destroy");
            });
        }

        void load()
        {
            registry.beforeLoad("unit-a", object, attempt.id);
            loads++;
        }

        void loaded() { load(); check(event(LOADED)); }

        void showing()
        {
            loaded();
            registry.beforeShow("unit-a", object, attempt.id, true);
            shows++;
            check(event(DISPLAYED));
        }

        boolean event(InterstitialReservationRegistry.Event event)
        {
            boolean accepted = registry.acceptCallback(binding, event);
            if (accepted) events.add("FORWARDED_" + event.name());
            return accepted;
        }

        boolean retire() { return retirement.retire("unit-a", attempt.id); }
    }

    private static void check(boolean value)
    {
        if (!value) throw new AssertionError("Retirement invariant failed");
    }

    private static void rejects(String code, Runnable call)
    {
        try { call.run(); }
        catch (InterstitialReservationRegistry.Rejected error)
        {
            if (!code.equals(error.code)) throw new AssertionError("Expected " + code + ", got " + error.code);
            return;
        }
        throw new AssertionError("Expected rejection: " + code);
    }

    private static void idleRetiresBeforeCleanupAndReleasesOnlyAfterReturn()
    {
        Fixture f = new Fixture();
        f.duringCleanup = () -> check(f.registry.isCoordinated("unit-a"));
        check(f.retire());
        check(!f.registry.isCoordinated("unit-a") && f.binding.object == null);
        check(f.destroys == 1 && f.loads == 0 && f.shows == 0);
    }

    private static void expiredPreparationCannotLoadButCanRetire()
    {
        Fixture f = new Fixture();
        f.now = f.attempt.deadlineMs;
        rejects("ATTEMPT_EXPIRED", f::load);
        check(f.registry.isCoordinated("unit-a"));
        check(f.retire() && f.destroys == 1 && f.loads == 0);
    }

    private static void slowLoadAndDelayedShowDoNotUsePreparationDeadline()
    {
        Fixture f = new Fixture();
        f.load();
        f.now += 120_000;
        check(f.event(LOADED));
        check(f.registry.canReportReady("unit-a", f.object, true));
        f.now += 120_000;
        f.registry.beforeShow("unit-a", f.object, f.attempt.id, true);
        f.shows++;
        check(f.event(DISPLAYED) && f.shows == 1 && f.destroys == 0);
    }

    private static void invalidationDuringLoadRetiresAndDropsLateSuccess()
    {
        Fixture f = new Fixture();
        f.load();
        check(f.retire());
        check(!f.event(LOADED) && !f.event(LOAD_FAILED));
        check(!f.registry.isCurrent("unit-a", f.object, f.attempt.id, f.attempt.generation, f.attempt.epoch));
    }

    private static void loadedInvalidationNeverShowsRetiredCreative()
    {
        Fixture f = new Fixture();
        f.loaded();
        check(f.retire());
        rejects("RESERVATION_UNSAFE",
                () -> f.registry.beforeShow("unit-a", f.object, f.attempt.id, true));
        check(f.shows == 0 && f.destroys == 1);
    }

    private static void showingRetirementIsDeferredAndCallbacksSurvive()
    {
        Fixture f = new Fixture();
        f.showing();
        check(!f.retire() && f.destroys == 0);
        rejects("ATTEMPT_INVALIDATED",
                () -> f.registry.beforeShow("unit-a", f.object, f.attempt.id, true));
        check(f.event(CLICKED) && f.event(HIDDEN));
        check(f.event(REVENUE));
        check(f.retirement.cleanup(f.binding) && f.destroys == 1);
    }

    private static void hiddenBeforeRevenueDoesNotDestroyEarly()
    {
        Fixture f = new Fixture();
        f.showing();
        check(f.event(HIDDEN));
        check(!f.retirement.cleanup(f.binding) && f.destroys == 0);
        check(f.event(REVENUE));
        check(f.retirement.cleanup(f.binding) && f.destroys == 1);
    }

    private static void revenueBeforeHiddenStillWaitsForHidden()
    {
        Fixture f = new Fixture();
        f.showing();
        check(f.event(REVENUE));
        check(!f.retire() && f.destroys == 0);
        check(f.event(HIDDEN));
        check(f.retire() && f.destroys == 1);
    }

    private static void missingRevenueCannotBeInventedByTimeout()
    {
        Fixture f = new Fixture();
        f.showing();
        f.event(HIDDEN);
        f.now += 86_400_000;
        check(!f.retire() && f.registry.isCoordinated("unit-a") && f.destroys == 0);
    }

    private static void repeatedRetirementNeverDestroysTwice()
    {
        Fixture f = new Fixture();
        check(f.retire() && f.retire());
        check(f.retirement.cleanup(f.binding) && f.destroys == 1);
    }

    private static void wrongAttemptCannotInvalidateOrCleanCurrentObject()
    {
        Fixture f = new Fixture();
        rejects("STALE_ATTEMPT", () -> f.retirement.retire("unit-a", "wrong"));
        check(f.registry.isCurrent("unit-a", f.object, f.attempt.id, f.attempt.generation, f.attempt.epoch));
        check(f.destroys == 0 && !f.registry.retirementPending(f.binding));
    }

    private static void staleRetirementCannotDestroyReplacement()
    {
        Fixture f = new Fixture();
        check(f.retire());
        Object next = new Object();
        f.registry.created("unit-a", next);
        InterstitialReservationRegistry.Attempt nextAttempt = f.registry.reserve("unit-a", next, false);
        rejects("STALE_ATTEMPT", f::retire);
        check(f.registry.isCurrent("unit-a", next, nextAttempt.id, nextAttempt.generation, nextAttempt.epoch));
        check(f.destroys == 1);
    }

    private static void uncertainDestroyQuarantinesWithoutRetry()
    {
        Fixture f = new Fixture();
        f.loaded();
        f.failStep = 2;
        rejects("INTERSTITIAL_CLEANUP_FAILED", f::retire);
        rejects("INTERSTITIAL_CLEANUP_FAILED", f::retire);
        rejects("ATTEMPT_REQUIRED", () -> f.registry.beforeLoad("unit-a", f.object, null));
        check(f.registry.isCoordinated("unit-a") && f.destroys == 1);
        check(!f.registry.canReportReady("unit-a", f.object, true));
    }

    private static void reentrantCleanupCannotReleaseBeforeSdkReturns()
    {
        Fixture f = new Fixture();
        f.duringCleanup = () -> {
            check(!f.retire());
            check(f.registry.isCoordinated("unit-a") && f.binding.object == f.object);
        };
        check(f.retire() && f.destroys == 1);
        check(!f.registry.isCoordinated("unit-a"));
    }

    private static void oldCallbacksCannotRestoreReadinessAfterReuse()
    {
        Fixture f = new Fixture();
        f.load();
        check(f.retire());
        Object next = new Object();
        f.registry.created("unit-a", next);
        InterstitialReservationRegistry.Attempt current = f.registry.reserve("unit-a", next, false);
        check(!f.event(LOADED) && !f.event(LOAD_FAILED));
        check(f.registry.isCurrent("unit-a", next, current.id, current.generation, current.epoch));
        check(!f.registry.canReportReady("unit-a", next, true)); // New generation has not loaded.
        check(f.loads == 1 && f.shows == 0);
    }

    private static void partialListenerCleanupFailureDoesNotRelease()
    {
        Fixture f = new Fixture();
        f.failStep = 1;
        rejects("INTERSTITIAL_CLEANUP_FAILED", f::retire);
        rejects("INTERSTITIAL_CLEANUP_FAILED", f::retire);
        check(f.destroys == 0 && f.registry.isCoordinated("unit-a") && f.binding.object == f.object);
    }

    private static void engineDetachRetiresPendingExactObjectInternally()
    {
        Fixture f = new Fixture();
        f.load();
        f.registry.detach();
        rejects("ENGINE_DETACHED", f::retire);
        check(f.retirement.cleanup(f.binding) && f.destroys == 1);
        f.registry.attach();
        Object next = new Object();
        f.registry.created("unit-a", next);
        check(f.registry.reserve("unit-a", next, false).epoch != f.attempt.epoch);
        check(!f.event(LOADED));
    }

    private static void engineDetachDoesNotInterruptPresentation()
    {
        Fixture f = new Fixture();
        f.showing();
        f.registry.detach();
        check(!f.retirement.cleanup(f.binding) && f.destroys == 0);
        check(f.event(HIDDEN) && f.event(REVENUE));
        check(!f.registry.canForwardToEngine(f.binding));
        check(f.retirement.cleanup(f.binding) && f.destroys == 1);
    }

    private static void ordinaryUncoordinatedObjectsAreNeverCleaned()
    {
        InterstitialReservationRegistry registry = new InterstitialReservationRegistry(
                () -> 100, new InterstitialReservationRegistry.IdentityDomain());
        registry.attach();
        Object object = new Object();
        registry.created("ordinary", object);
        InterstitialReservationRegistry.Binding binding = registry.binding("ordinary", object);
        InterstitialRetirement cleanup = new InterstitialRetirement(registry, entry -> {
            throw new AssertionError("Ordinary object cleanup is forbidden");
        });
        registry.beforeLoad("ordinary", object, null);
        check(registry.acceptCallback(binding, LOADED));
        registry.beforeShow("ordinary", object, null, true);
        check(registry.acceptCallback(binding, HIDDEN));
        registry.hostUnavailable();
        check(!cleanup.cleanup(binding) && registry.pendingCleanup().isEmpty());
        rejects("STALE_ATTEMPT", () -> cleanup.retire("ordinary", "unknown"));
    }

    private static void maxLoadFailureRequestsCleanupAfterCallback()
    {
        Fixture f = new Fixture();
        f.load();
        check(f.event(LOAD_FAILED) && f.registry.retirementPending(f.binding));
        check(f.retirement.cleanup(f.binding));
        check(f.events.indexOf("FORWARDED_LOAD_FAILED") < f.events.indexOf("DESTROY"));
    }

    private static void displayFailureAllowsTerminalCleanup()
    {
        Fixture f = new Fixture();
        f.loaded();
        f.registry.beforeShow("unit-a", f.object, f.attempt.id, true);
        check(f.event(DISPLAY_FAILED));
        check(f.retirement.cleanup(f.binding) && f.destroys == 1);
        check(!f.event(HIDDEN) && !f.event(DISPLAY_FAILED));
    }

    private static void activityLossDuringLoadSealsAndRetires()
    {
        Fixture f = new Fixture();
        f.load();
        f.registry.hostUnavailable();
        check(f.retirement.cleanup(f.binding));
        check(!f.event(LOADED) && f.destroys == 1);
    }

    private static void oldCleanupCannotReleaseNewerGeneration()
    {
        Fixture f = new Fixture();
        Object next = new Object();
        f.duringCleanup = () -> f.registry.created("unit-a", next);
        check(f.retire());
        check(f.registry.isCoordinated("unit-a")); // Old completion cannot clear newer guard.
        rejects("ATTEMPT_REQUIRED", () -> f.registry.beforeLoad("unit-a", next, null));
        check(f.registry.binding("unit-a", next).object == next);
    }

    private static void duplicateReservationCannotInvalidateLoadedAttempt()
    {
        Fixture f = new Fixture();
        f.loaded();
        rejects("ALREADY_RESERVED", () -> f.registry.reserve("unit-a", f.object, true));
        check(f.registry.canReportReady("unit-a", f.object, true) && f.destroys == 0);
    }

    private static void terminalAndRevenueAreForwardedBeforeCleanup()
    {
        Fixture f = new Fixture();
        f.showing();
        check(f.event(HIDDEN) && f.event(REVENUE));
        check(f.retirement.cleanup(f.binding));
        check(f.events.indexOf("FORWARDED_HIDDEN") < f.events.indexOf("DESTROY"));
        check(f.events.indexOf("FORWARDED_REVENUE") < f.events.indexOf("DESTROY"));
        check(!f.event(HIDDEN) && !f.event(REVENUE));
    }

    private static void cleanupFailureDoesNotQuarantineAnotherId()
    {
        Fixture f = new Fixture();
        f.failStep = 2;
        rejects("INTERSTITIAL_CLEANUP_FAILED", f::retire);
        Object other = new Object();
        f.registry.created("unit-b", other);
        InterstitialReservationRegistry.Attempt current = f.registry.reserve("unit-b", other, false);
        check(f.registry.isCurrent("unit-b", other, current.id, current.generation, current.epoch));
        check(f.registry.isCoordinated("unit-a"));
    }

    private static void readinessQueryCannotExtendPreloadDeadline()
    {
        Fixture f = new Fixture();
        f.registry.validateLoad("unit-a", f.object, f.attempt.id);
        f.now = f.attempt.deadlineMs;
        rejects("ATTEMPT_EXPIRED", f::load);
        check(f.loads == 0 && f.retire());
    }

    private static void reentrantInvalidationBeforeDispatchPreventsLoad()
    {
        Fixture f = new Fixture();
        f.registry.validateLoad("unit-a", f.object, f.attempt.id);
        f.registry.markUnsafe(f.binding);
        rejects("ATTEMPT_INVALIDATED", f::load);
        check(f.loads == 0 && f.retire());
    }

    public static void main(String[] args)
    {
        idleRetiresBeforeCleanupAndReleasesOnlyAfterReturn();
        expiredPreparationCannotLoadButCanRetire();
        slowLoadAndDelayedShowDoNotUsePreparationDeadline();
        invalidationDuringLoadRetiresAndDropsLateSuccess();
        loadedInvalidationNeverShowsRetiredCreative();
        showingRetirementIsDeferredAndCallbacksSurvive();
        hiddenBeforeRevenueDoesNotDestroyEarly();
        revenueBeforeHiddenStillWaitsForHidden();
        missingRevenueCannotBeInventedByTimeout();
        repeatedRetirementNeverDestroysTwice();
        wrongAttemptCannotInvalidateOrCleanCurrentObject();
        staleRetirementCannotDestroyReplacement();
        uncertainDestroyQuarantinesWithoutRetry();
        reentrantCleanupCannotReleaseBeforeSdkReturns();
        oldCallbacksCannotRestoreReadinessAfterReuse();
        partialListenerCleanupFailureDoesNotRelease();
        engineDetachRetiresPendingExactObjectInternally();
        engineDetachDoesNotInterruptPresentation();
        ordinaryUncoordinatedObjectsAreNeverCleaned();
        maxLoadFailureRequestsCleanupAfterCallback();
        displayFailureAllowsTerminalCleanup();
        activityLossDuringLoadSealsAndRetires();
        oldCleanupCannotReleaseNewerGeneration();
        duplicateReservationCannotInvalidateLoadedAttempt();
        terminalAndRevenueAreForwardedBeforeCleanup();
        cleanupFailureDoesNotQuarantineAnotherId();
        readinessQueryCannotExtendPreloadDeadline();
        reentrantInvalidationBeforeDispatchPreventsLoad();
        System.out.println("28 interstitial retirement/deadline checks passed.");
    }
}
