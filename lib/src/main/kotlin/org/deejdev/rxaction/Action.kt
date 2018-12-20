package org.deejdev.rxaction

import io.reactivex.Observable
import io.reactivex.Single

class Action<Input, Output>(
    isUserEnabled: Observable<Boolean>,
    execute: (Input) -> Single<Output>
) {
    constructor(execute: (Input) -> Single<Output>) : this(Observable.just(true), execute)

    val values: Observable<Output> = TODO()
    val errors: Observable<Throwable> = TODO()
    val disabledErrors: Observable<Any> = TODO()
    val isExecuting: Observable<Boolean> = TODO()
    val isEnabled: Observable<Boolean> = TODO()

    operator fun invoke(input: Input) {
        TODO()
    }
}
