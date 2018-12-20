package org.deejdev.rxaction

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
        val single = Single.create<Unit> {
            numberOfExecutions++
            it.onSuccess(Unit)
        }
        val action = Action<Unit, Unit>(execute = { single })

        assertEquals("numberOfExecutions != 0", 0, numberOfExecutions)
        action(Unit)
        assertEquals("numberOfExecutions != 1", 1, numberOfExecutions)
        action(Unit)
        assertEquals("numberOfExecutions != 2", 2, numberOfExecutions)
    }

    @Test
    fun onlyExecutesWhenEnabled() {
        val enabled = BehaviorSubject.create<Boolean>()
        val single = Single.create<Unit> {
            assertTrue(enabled.value!!)
            it.onSuccess(Unit)
        }
        val action = Action<Unit, Unit>(enabled) { single }
        val valuesObserver = action.values.test()

        enabled.onNext(false)
        action(Unit)
        enabled.onNext(true)
        action(Unit)
        enabled.onNext(false)
        action(Unit)

        valuesObserver.assertValueCount(1)
    }

    @Test
    fun executionResultsForwardedToValues() {
        val result = Any()
        val action = Action<Unit, Any> { Single.just(result) }
        val valuesObserver = action.values.test()

        action(Unit)
        valuesObserver.assertValuesOnly(result)
    }

    @Test
    fun executionErrorsForwardedToErrors() {
        val error = RuntimeException()
        val action = Action<Unit, Any> { Single.error(error) }
        val errorsObserver = action.errors.test()

        action(Unit)
        errorsObserver.assertValuesOnly(error)
    }

    @Test
    fun executionErrorsDoNotEndValues() {
        val error = RuntimeException()
        val action = Action<Unit, Any> { Single.error(error) }
        val valuesObserver = action.values.test()

        action(Unit)
        valuesObserver.assertNotTerminated()
    }

    @Test
    fun disabledActionOnlyEmitsDisabledErrors() {
        val enabled = Observable.just(false)
        val action = Action<Unit, Any>(enabled) { Single.just(Unit) }
        val valuesObserver = action.values.test()
        val errorsObserver = action.errors.test()
        val disabledErrorsObserver = action.disabledErrors.test()

        action(Unit)
        disabledErrorsObserver.assertValueCount(1)
        action(Unit)
        disabledErrorsObserver.assertValueCount(2)

        valuesObserver.assertNoValues()
        errorsObserver.assertNoValues()
    }

    @Test
    fun isExecuting() {
        val single = Single.just(Unit)
            .delay(100, TimeUnit.MILLISECONDS)
        val action = Action<Unit, Unit> { single }

        action.assertExecuting(false)

        action(Unit)
        action.assertExecuting(true)

        action.values.test().awaitCount(1)
        action.assertExecuting(false)
    }

    @Test
    fun disabledWhileExecuting() {
        val single = Single.just(Unit)
            .delay(100, TimeUnit.MILLISECONDS)
        val action = Action<Unit, Unit> { single }

        action.assertEnabled(true)

        action(Unit)
        action.assertEnabled(false)

        action.values.test().awaitCount(1)
        action.assertEnabled(true)
    }

    @Test
    fun wontExecuteInParallel() {
        val single = Single.just(Unit)
            .delay(100, TimeUnit.MILLISECONDS)
        val action = Action<Unit, Unit> { single }
        val valuesObserver = action.values.test()
        val disabledErrorsObserver = action.disabledErrors.test()

        action(Unit)
        action(Unit)
        disabledErrorsObserver.assertValueCount(1)
        valuesObserver.assertValueCount(0)

        Thread.sleep(200)

        valuesObserver.assertValueCount(1)
    }
}
