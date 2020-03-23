package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.IOUContract
import com.example.flow.PaybackFlow.Acceptor
import com.example.flow.PaybackFlow.Initiator
import com.example.state.IOUState
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step


/**
 * This flow allows two parties (the [Initiator] and the [Acceptor]) to come to an agreement about the IOU encapsulated
 * within an [IOUState].
 *
 * In our simple example, the [Acceptor] always accepts a valid IOU.
 *
 * These flows have deliberately been implemented by using only the call() method for ease of understanding. In
 * practice we would recommend splitting up the various stages of the flow into sub-routines.
 *
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */
object PaybackFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val loanTxId: String) : FlowLogic<SignedTransaction>() {
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on IOU state.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")

            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val txCommand = Command(IOUContract.Commands.Destroy(), ourIdentity.owningKey)

            /* val criteria = QueryCriteria.VaultQueryCriteria(
                    contractStateTypes = setOf(IOUState::class.java),
                    status = Vault.StateStatus.UNCONSUMED,
                    exactParticipants = listOf(ourIdentity, otherParty)
            ) // */

            //val signedTransaction = serviceHub.validatedTransactions.getTransaction(loanTxId)
            // val signedTransaction = transactions.find { it.id == transactionHash } ?: throw IllegalArgumentException("Unknown transaction hash.")

            val ourStateRef = StateRef(SecureHash.parse(loanTxId), 0)
            // val (state, ref) = serviceHub.toStateAndRef<ContractState>(ourStateRef)
            val inputStateAndRef = serviceHub.toStateAndRef<ContractState>(ourStateRef)
            val inputIouState = inputStateAndRef.state.data as IOUState;

            // build tx that will consume previous output
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(inputStateAndRef)
                    .addCommand(txCommand)

            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val signedTx = serviceHub.signInitialTransaction(txBuilder, inputIouState.lender.owningKey)

            /**
             * I don't think lender sig is required here,
             * we should be able to payback without it - but idk how to do `FinalityFlow` without it
             * also, will it update lender vault and spend IUO if lender will nobe be included in flow?
             */

            // Send the state to the counterparty, and receive it back with their signature.
            val otherPartySession = initiateFlow(inputIouState.lender)
            //val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, setOf(otherPartySession), ExampleFlow.Initiator.Companion.GATHERING_SIGS.childProgressTracker()))

            // Stage 4.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(FinalityFlow(signedTx, setOf(otherPartySession), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    // The transaction involves an IOUState - this ensures that IOUContract will be run to verify the transaction
                    val output = stx.tx.outputs.single().data
                    "This must be an IOU transaction." using (output is IOUState)
                    val iou = output as IOUState
                    "I won't accept IOUs with a value over 100." using (iou.value <= 100)
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}
