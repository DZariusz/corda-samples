package com.example.contract

import com.example.state.CashState
import com.example.utils.sumByLongSecure
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
                "Creator must sign the output" using cashStates.all { command.signers.contains(it.creator.owningKey) }
            }
            is Commands.Move -> requireThat {
                val cashInputs = tx.inputsOfType<CashState>()
                val cashOutputs = tx.outputsOfType<CashState>()

                "There should be at least one input." using (cashInputs.size >= 1)
                "There should be at least one output." using (cashOutputs.size >= 1)

                val cashInSum = cashInputs.sumByLongSecure { it.value }
                val cashOutSum = cashOutputs.sumByLongSecure { it.value }

                "in/out value must match" using (cashInSum == cashOutSum)

                "Previous cash owner must sign." using command.signers.contains(cashInputs.first().owner.owningKey)
                "Creator must sign output cash." using cashOutputs.all { command.signers.contains(it.creator.owningKey) }

                val inputCreatorsSame = cashInputs.all { it.creator == cashInputs.first().creator }
                val outputCreatorsSame = cashOutputs.all { it.creator == cashOutputs.first().creator }
                val allCreatorsSame = inputCreatorsSame && outputCreatorsSame
                "Creator stays intact." using (allCreatorsSame && cashInputs.first().creator == cashOutputs.first().creator)
            }
            else -> throw IllegalArgumentException("Not supported command")
        }
    }

    interface Commands : CommandData {
        class Create : Commands
        class Move : Commands
    }
}
