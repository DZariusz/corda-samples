package com.example.contract

import com.example.state.CashState
import com.example.state.IOUState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

/**
 * A implementation of a basic smart contract in Corda.
 *
 * This contract enforces rules regarding the creation of a valid [IOUState], which in turn encapsulates an [IOUState].
 *
 * For a new [IOUState] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [IOUState].
 * - An Create() command with the public keys of both the lender and the borrower.
 *
 * All contracts must sub-class the [Contract] interface.
 */
class IOUContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.example.contract.IOUContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.Create -> requireThat {
                // Generic constraints around the IOU transaction.
                "No IOUState inputs should be consumed when issuing an IOU." using (tx.inputsOfType<IOUState>().isEmpty())
                "There should be only one IOUState output." using (tx.outputsOfType<IOUState>().size == 1)
                val out = tx.outputsOfType<IOUState>().single()
                "The lender and the borrower cannot be the same entity." using (out.lender != out.borrower)
                "Command require two signers." using (command.signers.size == 2)
                "All of the participants must be signers." using (command.signers.containsAll(out.participants.map { it.owningKey }))

                // IOU-specific constraints.
                "The IOU's value must be non-negative." using (out.value > 0)
            }
            is Commands.Destroy -> requireThat {
                "There should be two inputs in general." using (tx.inputs.size == 2)
                "There should be only one IOUState input." using (tx.inputsOfType<IOUState>().size == 1)
                "There should be only one CashState input." using (tx.inputsOfType<CashState>().size == 1)

                "There should be one output in general." using (tx.outputs.size == 1)
                "There should be one CashState output." using (tx.outputsOfType<CashState>().size == 1)

                val iouStateSigner = tx.inputsOfType<IOUState>().single().lender.owningKey

                "Expect one signer." using (command.signers.size == 1)
                "Lender must be a signer." using (command.signers.single() == iouStateSigner)
            }
            else -> throw IllegalArgumentException("Not supported command")
        }
    }

    interface Commands : CommandData {
        class Create : Commands
        class Destroy : Commands
    }
}
