package com.example.utils

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.node.ServiceHub

fun Iterable<StateRef>.getStxStates(serviceHub: ServiceHub): List<ContractState> {
    val states = mutableListOf<ContractState>()

    for (element in this) {
        val stateAndRef = serviceHub.toStateAndRef<ContractState>(element)
        states.add(stateAndRef.state.data)
    }

    return states
}
