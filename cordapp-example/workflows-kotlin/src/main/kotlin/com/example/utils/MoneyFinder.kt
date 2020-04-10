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

// just for this exercise and make it simple `ExampleFlow` creates a money, so on payback I can use
// "the same" money (same owner, same value). This class finds it.
object MoneyFinder {
    fun call(serviceHub: ServiceHub, cashOwner: Party, amount: Int): StateAndRef<CashState> {
        val pageSpec = PageSpecification(pageNumber = 0, pageSize = 1)
        val criteria = moneyQueryCriteria(cashOwner, amount.toLong())
        val results = serviceHub.vaultService.queryBy(CashState::class.java, criteria = criteria, paging = pageSpec)

        return results.states.first()
    }

    private fun moneyQueryCriteria(cashOwner: Party, amount: Long): QueryCriteria {
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

            val customCriteria1 = QueryCriteria.VaultCustomQueryCriteria(currencyIndex)
            val customCriteria2 = QueryCriteria.VaultCustomQueryCriteria(quantityIndex)

            generalCriteria.and(customCriteria1.and(customCriteria2))
        };
    }
}