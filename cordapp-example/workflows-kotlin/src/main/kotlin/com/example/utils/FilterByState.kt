package com.example.utils

import net.corda.core.contracts.ContractState

inline fun <reified T> List<ContractState>.filterByState(): List<T> {
    val states = mutableListOf<T>()
    for (element in this) {
        if (element is T) {
            states.add(element)
        }
    }
    return states
}
