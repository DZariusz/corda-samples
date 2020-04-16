package com.example.utils

import com.example.state.IOUState
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.vault.QueryCriteria
import java.util.*

/* val iouStateFinder: ServiceHub.(String) -> StateAndRef<IOUState> = { iouStateLinearId ->
    val uuid: UUID = UUID.fromString(iouStateLinearId)
    val queryCriteria: QueryCriteria = QueryCriteria.LinearStateQueryCriteria().withUuid(listOf(uuid))

    val results = this.vaultService.queryBy(IOUState::class.java, queryCriteria)
    // val ourStateRef = StateRef(SecureHash.parse(loanTxId), 0)
    // val (state, ref) = serviceHub.toStateAndRef<ContractState>(ourStateRef)
    this.toStateAndRef<IOUState>(results.states.single().ref)
    //return inputStateAndRef.state.data
} // */

fun ServiceHub.iouStateFinder(uuid: UUID) =
        QueryCriteria.LinearStateQueryCriteria().withUuid(listOf(uuid))
                .let { criteria -> vaultService.queryBy(IOUState::class.java, criteria) }
                .let { results -> toStateAndRef<IOUState>(results.states.single().ref) }