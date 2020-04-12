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
                val cashState = tx.outputsOfType<CashState>().single()
                "Cash value must be positive" using (cashState.value > 0)
                "Expect one signature" using (command.signers.size == 1)
                // I can't think about a way of checking if this command is sign by bank
                // probably must be done in a flow
                //"Bank must be a signer" using (command.signers.single() == bankProvider())
            }
            is Commands.Move -> requireThat {
                "There should be one input." using (tx.inputsOfType<CashState>().size == 1)
                "There should be one output." using (tx.outputsOfType<CashState>().size == 1)

                val cashStateIn = tx.inputsOfType<CashState>().single()
                val cashStateOut = tx.outputsOfType<CashState>().single()

                "in/out value must match" using (cashStateIn.value.equals(cashStateOut.value))
                "Can't move to the same party" using (!cashStateIn.owner.equals(cashStateOut.owner))

                "Expect one signature" using (command.signers.size == 1)
                "Previous owner must sign." using (command.signers.single() == cashStateIn.owner.owningKey)

                "Creator stays intact." using (cashStateIn.creator.equals(cashStateOut.creator))
            }
            else -> throw IllegalArgumentException("Not supported command")
        }
    }

    interface Commands : CommandData {
        class Create : Commands
        class Move : Commands
    }
}
