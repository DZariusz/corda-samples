package com.example.contract

import com.example.state.CashState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class CashContract : Contract {
    companion object {
        @JvmStatic
        val ID = CashContract::class.java.canonicalName // qualifiedName returns String?
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.Create -> requireThat {
                "There should be no input." using tx.inputsOfType<CashState>().isEmpty()
                val cashStates = tx.outputsOfType<CashState>()
                "Cash value must be positive" using cashStates.all { it.value > 0 }
                "Expect one signature" using (command.signers.size == 1)
                "Creator must sign the output" using cashStates.all { command.signers.single() == it.creator.owningKey }
            }
            is Commands.Move -> requireThat {
                val cashInputs = tx.inputsOfType<CashState>()
                val cashOutputs = tx.outputsOfType<CashState>()

                "There should be at least one input." using (cashInputs.size >= 1)
                "There should be at least one output." using (cashOutputs.size >= 1)

                val cashInSum = cashInputs.map { it.value }.fold(0L, Math::addExact)
                val cashOutSum = cashOutputs.fold(0L) { sum, cash -> Math.addExact(sum, cash.value) }

                "in/out value must match" using (cashInSum == cashOutSum)

                "Previous cash owner must sign." using command.signers.contains(cashInputs.first().owner.owningKey)
                "Creator stays intact." using (cashInputs.plus(cashOutputs).map { it.creator }.toSet().size == 1)
            }
            else -> throw IllegalArgumentException("Not supported command")
        }
    }

    interface Commands : CommandData {
        class Create : Commands
        class Move : Commands
    }
}
