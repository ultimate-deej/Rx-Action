package org.deejdev.rxaction3

import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject

class Action<Input, Output> private constructor(
    private val scheduler: Scheduler?,
    isUserEnabled: Observable<Boolean>?,
    private val execute: (Input) -> Observable<Output>
) {
    companion object {
        @JvmStatic
        fun <Input, Output> fromObservable(isUserEnabled: Observable<Boolean>, execute: (Input) -> Observable<Output>): Action<Input, Output> =
            Action(null, isUserEnabled, execute)

        @JvmStatic
        fun <Input, Output> fromObservable(execute: (Input) -> Observable<Output>): Action<Input, Output> =
            Action(null, null, execute)

        @JvmStatic
        fun <Input, Output> fromObservable(scheduler: Scheduler?, isUserEnabled: Observable<Boolean>, execute: (Input) -> Observable<Output>): Action<Input, Output> =
            Action(scheduler, isUserEnabled, execute)

        @JvmStatic
        fun <Input, Output> fromObservable(scheduler: Scheduler?, execute: (Input) -> Observable<Output>): Action<Input, Output> =
            Action(scheduler, null, execute)

        @JvmStatic
        fun <Input, Output> fromFlowable(isUserEnabled: Observable<Boolean>, execute: (Input) -> Flowable<Output>): Action<Input, Output> =
            Action(null, isUserEnabled) { execute(it).toObservable() }

        @JvmStatic
        fun <Input, Output> fromFlowable(execute: (Input) -> Flowable<Output>): Action<Input, Output> =
            Action(null, null) { execute(it).toObservable() }

        @JvmStatic
        fun <Input, Output> fromFlowable(scheduler: Scheduler?, isUserEnabled: Observable<Boolean>, execute: (Input) -> Flowable<Output>): Action<Input, Output> =
            Action(scheduler, isUserEnabled) { execute(it).toObservable() }

        @JvmStatic
        fun <Input, Output> fromFlowable(scheduler: Scheduler?, execute: (Input) -> Flowable<Output>): Action<Input, Output> =
            Action(scheduler, null) { execute(it).toObservable() }

        @JvmStatic
        fun <Input, Output> fromSingle(isUserEnabled: Observable<Boolean>, execute: (Input) -> Single<Output>): Action<Input, Output> =
            Action(null, isUserEnabled) { execute(it).toObservable() }

        @JvmStatic
        fun <Input, Output> fromSingle(execute: (Input) -> Single<Output>): Action<Input, Output> =
            Action(null, null) { execute(it).toObservable() }

        @JvmStatic
        fun <Input, Output> fromSingle(scheduler: Scheduler?, isUserEnabled: Observable<Boolean>, execute: (Input) -> Single<Output>): Action<Input, Output> =
            Action(scheduler, isUserEnabled) { execute(it).toObservable() }

        @JvmStatic
        fun <Input, Output> fromSingle(scheduler: Scheduler?, execute: (Input) -> Single<Output>): Action<Input, Output> =
            Action(scheduler, null) { execute(it).toObservable() }

        @JvmStatic
        fun <Input> fromCompletable(isUserEnabled: Observable<Boolean>, execute: (Input) -> Completable): Action<Input, Nothing> =
            Action(null, isUserEnabled) { execute(it).toObservable() }

        @JvmStatic
        fun <Input> fromCompletable(execute: (Input) -> Completable): Action<Input, Nothing> =
            Action(null, null) { execute(it).toObservable() }

        @JvmStatic
        fun <Input> fromCompletable(scheduler: Scheduler?, isUserEnabled: Observable<Boolean>, execute: (Input) -> Completable): Action<Input, Nothing> =
            Action(scheduler, isUserEnabled) { execute(it).toObservable() }

        @JvmStatic
        fun <Input> fromCompletable(scheduler: Scheduler?, execute: (Input) -> Completable): Action<Input, Nothing> =
            Action(scheduler, null) { execute(it).toObservable() }
    }

    private val _values = PublishSubject.create<Output>()
    private val _errors = PublishSubject.create<Throwable>()
    private val _completions = PublishSubject.create<Any>()
    private val _disabledErrors = PublishSubject.create<Any>()
    private val _isExecuting = BehaviorSubject.createDefault(false)
    private val _isEnabled = BehaviorSubject.create<Boolean>().also {
        if (isUserEnabled == null) {
            _isExecuting
                .map(Boolean::not)
                .subscribe(it)
        } else {
            val combiner = BiFunction<Boolean, Boolean, Boolean> { userEnabled, executing -> userEnabled && !executing }
            Observable.combineLatest(isUserEnabled.startWithItem(false), _isExecuting, combiner)
                .subscribe(it)
        }
    }

    val values: Observable<Output> = _values
    val errors: Observable<Throwable> = _errors
    val completions: Observable<Any> = _completions
    val disabledErrors: Observable<Any> = _disabledErrors
        .run { scheduler?.let(::observeOn) ?: this }
    val isExecuting: Observable<Boolean> = _isExecuting
        .run { scheduler?.let(::observeOn) ?: this }
    val isEnabled: Observable<Boolean> = _isEnabled
        .run { scheduler?.let(::observeOn) ?: this }

    val isExecutingValue: Boolean get() = _isExecuting.value!!
    val isEnabledValue: Boolean get() = _isEnabled.value!!

    operator fun invoke(input: Input) {
        if (_isEnabled.value == false) {
            _disabledErrors.onNext(Unit)
            return
        }

        @Suppress("MoveLambdaOutsideParentheses")
        execute(input)
            .run { scheduler?.let(::observeOn) ?: this }
            .doOnSubscribe { _isExecuting.onNext(true) }
            .doFinally { _isExecuting.onNext(false) }
            .subscribe(_values::onNext, _errors::onNext, { _completions.onNext(Unit) })
    }
}

operator fun <Output> Action<Unit, Output>.invoke() = invoke(Unit)
