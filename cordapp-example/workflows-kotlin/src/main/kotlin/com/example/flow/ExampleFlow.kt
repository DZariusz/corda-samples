package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.CashContract
import com.example.contract.IOUContract
import com.example.flow.ExampleFlow.Acceptor
import com.example.flow.ExampleFlow.Initiator
import com.example.state.CashState
import com.example.state.IOUState
import com.example.utils.bankProvider
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
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
object ExampleFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val iouValue: Int,
                    val borrower: Party) : FlowLogic<SignedTransaction>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on new IOU.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
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
            val bank = serviceHub.bankProvider()
            
            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val iouState = IOUState(iouValue, serviceHub.myInfo.legalIdentities.first(), borrower)

            // just printing some money, so I can payback later
            // this is not how it should be done, but just for this module 3 I will leave it that way
            val cashState = CashState(iouState.value.toLong(), bank, iouState.borrower);

            val iouCommand = Command(IOUContract.Commands.Create(), iouState.participants.map { it.owningKey })
            val cashCommand = Command(CashContract.Commands.Create(), bank.owningKey)

            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(iouState, IOUContract.ID)
                    .addOutputState(cashState, CashContract.ID)
                    .addCommand(iouCommand)
                    .addCommand(cashCommand)

            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS
            // Send the state to the counterparty, and receive it back with their signature.
            val otherPartySession = initiateFlow(borrower)
            val bankSession = initiateFlow(bank)
            val fullySignedTx = subFlow(CollectSignaturesFlow(
                    partSignedTx, setOf(otherPartySession, bankSession), GATHERING_SIGS.childProgressTracker()
            ))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(FinalityFlow(
                    fullySignedTx, setOf(otherPartySession, bankSession), FINALISING_TRANSACTION.childProgressTracker()
            ))
        }
    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    // The transaction involves an IOUState - this ensures that IOUContract will be run to verify the transaction
                    "This must be an IOU." using (stx.tx.outputsOfType<IOUState>().size == 1)
                    val iou = stx.tx.outputsOfType<IOUState>().single()
                    "I won't accept IOUs with a value over 100." using (iou.value <= 100)
                    "Expect one output CashState." using (stx.tx.outputsOfType<CashState>().size == 1)
                    val cash = stx.tx.outputsOfType<CashState>().single()
                    "Expect bank to be cash creator" using cash.creator.equals(serviceHub.bankProvider())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}
