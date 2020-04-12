package com.example.state

import com.example.contract.CashContract
import com.example.schema.CashSchemaV1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandAndState
import net.corda.core.contracts.OwnableState
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState

@BelongsToContract(CashContract::class)
data class CashState(val value: Long, val creator: AbstractParty, override val owner: AbstractParty): OwnableState, QueryableState {
    override val participants: List<AbstractParty> get() = listOf(owner)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is CashSchemaV1 -> CashSchemaV1.PersistentCash(
                    this.creator.nameOrNull().toString(),
                    this.owner.nameOrNull().toString(),
                    this.value
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(CashSchemaV1)

    override fun withNewOwner(newOwner: AbstractParty): CommandAndState {
        return CommandAndState(CashContract.Commands.Move(), copy(owner = newOwner))
    }
}
