package com.example.schema

import net.corda.core.contracts.Amount
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object CashSchema

object CashSchemaV1 : MappedSchema(
        schemaFamily = CashSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentCash::class.java)) {
    const val CURRENCY = "PLN"

    @Entity
    @Table(name = "coin_states")
    class PersistentCash(
            @Column(name = "owner")
            var owner: String,

            @Column(name = "balance")
            var balance: Amount<Currency>,

            @Column(name = "linear_id")
            var linearId: UUID
    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this("", Amount(0, Currency.getInstance(CURRENCY)),  UUID.randomUUID())
    }
}