package org.deejdev.rxaction

fun Action<*, *>.assertExecuting(expected: Boolean) {
    isExecuting.test().assertValue(expected)
}

fun Action<*, *>.assertEnabled(expected: Boolean) {
    isEnabled.test().assertValue(expected)
}
