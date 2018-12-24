package org.deejdev.rxaction

import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject

class Action<Input, Output> private constructor(
    isUserEnabled: Observable<Boolean>?,
    private val execute: (Input) -> Single<Output>
) {
    companion object {
        @JvmStatic
        fun <Input, Output> fromSingle(isUserEnabled: Observable<Boolean>, execute: (Input) -> Single<Output>) = Action(isUserEnabled, execute)

        @JvmStatic
        fun <Input, Output> fromSingle(execute: (Input) -> Single<Output>) = Action(null, execute)
    }

    private val _values = PublishSubject.create<Output>()
    private val _errors = PublishSubject.create<Throwable>()
    private val _disabledErrors = PublishSubject.create<Any>()
    private val _isExecuting = BehaviorSubject.createDefault(false)
    private val _isEnabled = BehaviorSubject.create<Boolean>().also {
        if (isUserEnabled == null) {
            _isExecuting
                .map(Boolean::not)
                .subscribe(it)
        } else {
            val combiner = BiFunction<Boolean, Boolean, Boolean> { userEnabled, executing -> userEnabled && !executing }
            Observable.combineLatest(isUserEnabled, _isExecuting, combiner)
                .subscribe(it)
        }
    }

    val values: Observable<Output> = _values
    val errors: Observable<Throwable> = _errors
    val disabledErrors: Observable<Any> = _disabledErrors
    val isExecuting: Observable<Boolean> = _isExecuting
    val isEnabled: Observable<Boolean> = _isEnabled

    operator fun invoke(input: Input) {
        if (_isEnabled.value == false) {
            _disabledErrors.onNext(Unit)
            return
        }

        execute(input)
            .doOnSubscribe { _isExecuting.onNext(true) }
            .doFinally { _isExecuting.onNext(false) }
            .subscribe(_values::onNext, _errors::onNext)
    }
}

operator fun <Output> Action<Unit, Output>.invoke() = invoke(Unit)
