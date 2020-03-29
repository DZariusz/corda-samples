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
                // everyone can print/crate cash... because of covi... crisis
                val cashState = tx.inputsOfType<CashState>().single()
                "Cash value must be positive" using (cashState.balance > 0)
            }
            is Commands.Move -> requireThat {
                // yet another dummy verification
                val cashState = tx.inputsOfType<CashState>().first()
                "Cash value must be positive" using (cashState.balance > 0)
            }
            else -> throw IllegalArgumentException("Not supported command")
        }
    }

    interface Commands : CommandData {
        class Create : Commands
        class Move : Commands
    }
}
