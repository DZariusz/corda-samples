package com.example.utils

inline fun <T> Iterable<T>.sumByLongSecure(selector: (T) -> Long): Long {
    var sum = 0L
    for (element in this) {
        sum += selector(element)
        if (sum < 0) {
            throw RuntimeException()
        }
    }
    return sum
}
