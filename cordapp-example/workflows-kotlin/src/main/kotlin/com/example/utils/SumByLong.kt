package com.example.utils

inline fun <T> Iterable<T>.sumByLongSecure(selector: (T) -> Long): Long {
    var sum = 0L
    for (element in this) {
        sum = Math.addExact(sum, selector(element))
    }
    return sum
}
