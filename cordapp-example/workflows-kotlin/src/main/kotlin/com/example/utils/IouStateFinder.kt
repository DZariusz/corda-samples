package com.example.utils

import com.example.state.IOUState
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.vault.QueryCriteria
import java.util.*

fun iouStateFinder(serviceHub: ServiceHub, iouStateLinearId: String): StateAndRef<IOUState> {
    val uuid: UUID = UUID.fromString(iouStateLinearId)
    val queryCriteria: QueryCriteria = QueryCriteria.LinearStateQueryCriteria().withUuid(listOf(uuid))

    val results = serviceHub.vaultService.queryBy(IOUState::class.java, queryCriteria)
    // val ourStateRef = StateRef(SecureHash.parse(loanTxId), 0)
    // val (state, ref) = serviceHub.toStateAndRef<ContractState>(ourStateRef)
    return serviceHub.toStateAndRef<IOUState>(results.states.single().ref)
    //return inputStateAndRef.state.data
}
