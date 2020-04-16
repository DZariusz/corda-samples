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
        val ID = CashContract::class.java.canonicalName
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.Create -> requireThat {
                "There should be no input." using tx.inputsOfType<CashState>().isEmpty()
                val cashStates = tx.outputsOfType<CashState>()
                "Cash value must be positive" using cashStates.all { it.value > 0 }
                "Expect one signature" using (command.signers.size == 1)
                // I can't think about a way of checking if this command is sign by bank
                // probably must be done in a flow
                "Issuer and owner must differ" using cashStates.all { it.creator != it.owner }
            }
            is Commands.Move -> requireThat {
                "There should be at least one input." using (tx.inputsOfType<CashState>().size >= 1)
                "There should be at least one output." using (tx.outputsOfType<CashState>().size >= 1)

                val cashInputs = tx.inputsOfType<CashState>()
                val cashOutputs = tx.outputsOfType<CashState>()

                val cashInSum = cashInputs.map { it.value }.sum()
                val cashOutSum = cashOutputs.map { it.value }.sum()

                "in/out value must match" using (cashInSum == cashOutSum)
                "All inputs must belong to the same party" using (cashInputs.all { it.owner == cashInputs[0].owner })
                "All outputs must belong to the same party" using (cashOutputs.all { it.owner == cashOutputs[0].owner })
                "Can't move to the same party" using !cashInputs[0].owner.equals(cashOutputs[0].owner)

                "Expect one signature" using (command.signers.size == 1)
                "Previous owner must sign." using (command.signers.single() == cashInputs[0].owner.owningKey)

                val inputCreatorsSame = cashInputs.all { it.creator == cashInputs[0].creator }
                val outputCreatorsSame = cashInputs.all { it.creator == cashInputs[0].creator }
                val allCreatorsSame = inputCreatorsSame && outputCreatorsSame
                "Creator stays intact." using (allCreatorsSame && cashInputs[0].creator == cashOutputs[0].creator)
            }
            else -> throw IllegalArgumentException("Not supported command")
        }
    }

    interface Commands : CommandData {
        class Create : Commands
        class Move : Commands
    }
}
