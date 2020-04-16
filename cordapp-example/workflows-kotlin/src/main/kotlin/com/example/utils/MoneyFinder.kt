package com.example.utils

import com.example.schema.CashSchemaV1
import com.example.state.CashState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.*
import java.util.*

fun ServiceHub.moneyFinder(cashOwner: Party, amount: Int): MutableList<StateAndRef<CashState>> {
    val money = mutableListOf<StateAndRef<CashState>>()
    val criteria = moneyQueryCriteria(this.bankProvider(), cashOwner, amount.toLong())
    var pageNumber = DEFAULT_PAGE_NUM;
    var sum = 0

    do {
        val pageSpec = PageSpecification(pageNumber = pageNumber, pageSize = 5)
        val results = this.vaultService.queryBy(CashState::class.java, criteria = criteria, paging = pageSpec)
        money.addAll(results.states)
        sum += results.states.sumBy { it.state.data.value.toInt() }
        pageNumber++
    } while (sum < amount && (pageSpec.pageSize * (pageNumber - 1)) <= results.totalStatesAvailable)

    return if (sum < amount) Collections.emptyList() else money
}

fun moneyQueryCriteria(creator: Party, cashOwner: Party, amount: Long): QueryCriteria {
    val generalCriteria = QueryCriteria.VaultQueryCriteria(
            contractStateTypes = setOf(CashState::class.java),
            status = Vault.StateStatus.UNCONSUMED,
            exactParticipants = listOf(cashOwner)
    )

    return builder {
        val currencyIndex = CashSchemaV1.PersistentCash::currency.equal(CashSchemaV1.CURRENCY)
        // val quantityIndex = CashSchemaV1.PersistentCash::value.equal(amount)
        val creatorIndex = CashSchemaV1.PersistentCash::creator.equal(creator.nameOrNull().toString())

        val customCriteria1 = QueryCriteria.VaultCustomQueryCriteria(currencyIndex)
        //val customCriteria2 = QueryCriteria.VaultCustomQueryCriteria(quantityIndex)
        val customCriteria3 = QueryCriteria.VaultCustomQueryCriteria(creatorIndex)

        generalCriteria.and(customCriteria1.and(customCriteria3))
    }
}
