package com.example.utils

import com.example.schema.CashSchemaV1
import com.example.state.CashState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import java.lang.Exception

// just for this exercise and make it simple `ExampleFlow` creates a money, so on payback I can use
// "the same" money (same owner, same value). This class finds it.
val moneyFinder: ServiceHub.(cashOwner: Party, amount: Int) -> StateAndRef<CashState>? = { cashOwner: Party, amount: Int ->
    val pageSpec = PageSpecification(pageNumber = 1, pageSize = 1)
    val criteria = moneyQueryCriteria(this.bankProvider(), cashOwner, amount.toLong())
    val results = this.vaultService.queryBy(CashState::class.java, criteria = criteria, paging = pageSpec)

    if (results.states.isEmpty()) null else results.states.first()
}

private fun moneyQueryCriteria(creator: Party, cashOwner: Party, amount: Long): QueryCriteria {
    // looking for money that should be printed on ExampleFlow
    // this is not the best way, but I use this way to exercise, I will do better in module 4
    val generalCriteria = QueryCriteria.VaultQueryCriteria(
            contractStateTypes = setOf(CashState::class.java),
            status = Vault.StateStatus.UNCONSUMED,
            exactParticipants = listOf(cashOwner)
    )

    return builder {
        val currencyIndex = CashSchemaV1.PersistentCash::currency.equal(CashSchemaV1.CURRENCY)
        val quantityIndex = CashSchemaV1.PersistentCash::value.equal(amount)
        val creatorIndex = CashSchemaV1.PersistentCash::creator.equal(creator.nameOrNull().toString())

        val customCriteria1 = QueryCriteria.VaultCustomQueryCriteria(currencyIndex)
        val customCriteria2 = QueryCriteria.VaultCustomQueryCriteria(quantityIndex)
        val customCriteria3 = QueryCriteria.VaultCustomQueryCriteria(creatorIndex)

        generalCriteria.and(customCriteria1.and(customCriteria2.and(customCriteria3)))
    };
}
