package org.deejdev.rxaction

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ActionTest {
    @Test
    fun executesExactlyOncePerInvocation() {
        var numberOfExecutions = 0
        val completable = Completable.create {
            numberOfExecutions++
            it.onComplete()
        }
        val action = Action.fromCompletable<Unit>(execute = { completable })

        assertEquals("numberOfExecutions != 0", 0, numberOfExecutions)
        action()
        assertEquals("numberOfExecutions != 1", 1, numberOfExecutions)
        action()
        assertEquals("numberOfExecutions != 2", 2, numberOfExecutions)
    }

    @Test
    fun onlyExecutesWhenEnabled() {
        val enabled = BehaviorSubject.create<Boolean>()
        val single = Single.create<Unit> {
            assertTrue(enabled.value!!)
            it.onSuccess(Unit)
        }
        val action = Action.fromSingle<Unit, Unit>(enabled) { single }
        val valuesObserver = action.values.test()

        enabled.onNext(false)
        action()
        enabled.onNext(true)
        action()
        enabled.onNext(false)
        action()

        valuesObserver.assertValueCount(1)
    }

    @Test
    fun executionResultsForwardedToValues() {
        val result = Any()
        val action = Action.fromSingle<Unit, Any> { Single.just(result) }
        val valuesObserver = action.values.test()

        action()
        valuesObserver.assertValuesOnly(result)
    }

    @Test
    fun executionErrorsForwardedToErrors() {
        val error = RuntimeException()
        val action = Action.fromSingle<Unit, Any> { Single.error(error) }
        val errorsObserver = action.errors.test()

        action()
        errorsObserver.assertValuesOnly(error)
    }

    @Test
    fun executionErrorsDoNotEndValues() {
        val error = RuntimeException()
        val action = Action.fromSingle<Unit, Any> { Single.error(error) }
        val valuesObserver = action.values.test()

        action()
        valuesObserver.assertNotTerminated()
    }

    @Test
    fun disabledActionOnlyEmitsDisabledErrors() {
        val enabled = Observable.just(false)
        val action = Action.fromSingle<Unit, Any>(enabled) { Single.just(Unit) }
        val valuesObserver = action.values.test()
        val errorsObserver = action.errors.test()
        val disabledErrorsObserver = action.disabledErrors.test()

        action()
        disabledErrorsObserver.assertValueCount(1)
        action()
        disabledErrorsObserver.assertValueCount(2)

        valuesObserver.assertNoValues()
        errorsObserver.assertNoValues()
    }

    @Test
    fun isExecuting() {
        val completable = Completable.complete()
            .delay(100, TimeUnit.MILLISECONDS)
        val action = Action.fromCompletable<Unit> { completable }

        action.assertExecuting(false)

        action()
        action.assertExecuting(true)

        action.completions.test().awaitCount(1).assertValueCount(1)
        action.assertExecuting(false)
    }

    @Test
    fun disabledWhileExecuting() {
        val completable = Completable.complete()
            .delay(100, TimeUnit.MILLISECONDS)
        val action = Action.fromCompletable<Unit> { completable }

        action.assertEnabled(true)

        action()
        action.assertEnabled(false)

        action.completions.test().awaitCount(1).assertValueCount(1)
        action.assertEnabled(true)
    }

    @Test
    fun wontExecuteInParallel() {
        val completable = Completable.complete()
            .delay(100, TimeUnit.MILLISECONDS)
        val action = Action.fromCompletable<Unit> { completable }
        val completionsObserver = action.completions.test()
        val disabledErrorsObserver = action.disabledErrors.test()

        action()
        action()
        disabledErrorsObserver.assertValueCount(1)
        completionsObserver.assertValueCount(0)

        Thread.sleep(200)

        completionsObserver.assertValueCount(1)
    }

    @Test
    fun multipleValuesPerExecution() {
        val observable = Observable.just(3, 2, 1)
        val action = Action.fromObservable<Unit, Int> { observable }
        val valuesObserver = action.values.test()

        action()
        valuesObserver.assertValuesOnly(3, 2, 1)
    }
}
