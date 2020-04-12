package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.IOUContract
import com.example.flow.PaybackFlow.Acceptor
import com.example.flow.PaybackFlow.Initiator
import com.example.state.CashState
import com.example.state.IOUState
import com.example.utils.bankProvider
import com.example.utils.iouStateFinder
import com.example.utils.moneyFinder
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.unwrap

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
    class Initiator(val iouStateLinearId: String, val paybackValue: Int) : FlowLogic<SignedTransaction>() {
        companion object {
            object CREATING_COMMAND : Step("Pull data from vault and create command.")
            object GENERATING_TRANSACTION : Step("Generating transaction based on IOU state.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object INITIALISE_OTHER_PARTY_FLOW : Step("Send the state to the counterparty, and receive it back with their signature")

            object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object PAYBACK_COMMUNICATION : Step("Sending payback value to lender.")

            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    CREATING_COMMAND,
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    INITIALISE_OTHER_PARTY_FLOW,
                    GATHERING_SIGS,
                    PAYBACK_COMMUNICATION,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = CREATING_COMMAND
            //val signedTransaction = serviceHub.validatedTransactions.getTransaction(loanTxId)
            // val signedTransaction = transactions.find { it.id == transactionHash } ?: throw IllegalArgumentException("Unknown transaction hash.")

            val iouStateAndRef = serviceHub.iouStateFinder(iouStateLinearId)
            val iouState = iouStateAndRef.state.data
            val cashStateAndRef = serviceHub.moneyFinder(iouState.borrower, iouState.value)!!

            require(iouState.value.compareTo(cashStateAndRef.state.data.value) == 0) { "Payback value differ from borrowed amount." }

            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val destroyIOUCommand = Command(IOUContract.Commands.Destroy(), iouState.lender.owningKey)
            val (moveCashCommand, cashOutputState) = cashStateAndRef.state.data.withNewOwner(iouState.lender)
            //val moveMoneyCommand = Command(CashContract.Commands.Move(), iouState.borrower.owningKey)
            //val moveMoneyCommand = Command(CashContract.Commands.Move(), iouState.borrower.owningKey)

            // Obtain a reference to the notary that was in use for input state
            val notary = cashStateAndRef.state.notary

            // build tx that will consume previous output
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(iouStateAndRef)
                    .addInputState(cashStateAndRef)
                    .addOutputState(cashOutputState, cashStateAndRef.state.contract, cashStateAndRef.state.notary)
                    .addCommand(destroyIOUCommand)
                    .addCommand(moveCashCommand, cashStateAndRef.state.data.owner.owningKey)

            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction (must be by a borrower)
            if (!iouState.borrower.equals(ourIdentity)) {
                throw IllegalArgumentException("Borrower must start the flow")
            }
            val signedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = INITIALISE_OTHER_PARTY_FLOW
            // Send the state to the counterparty, and receive it back with their signature.
            val lenderPartySession = initiateFlow(iouState.lender)

            // send/receive part is just for experiment
            val confirmPayback = lenderPartySession
                    .sendAndReceive<Boolean>("ready for receiving payback?")
                    .unwrap { it }

            require(confirmPayback) { "Oops, lender failed to be ready" }

            progressTracker.currentStep = PAYBACK_COMMUNICATION
            lenderPartySession.send(paybackValue)

            progressTracker.currentStep = GATHERING_SIGS
            val fullySignedTx = subFlow(CollectSignaturesFlow(
                    signedTx,
                    setOf(lenderPartySession),
                    GATHERING_SIGS.childProgressTracker())
            )

            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(FinalityFlow(fullySignedTx, setOf(lenderPartySession), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val borrowerPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            borrowerPartySession.receive<String>().unwrap { it }
            //send confirmation about being ready to receive payback
            borrowerPartySession.send(true)

            // just exercise
            val receivedPayback: Int = borrowerPartySession.receive<Int>().unwrap { it }

            val signTransactionFlow = object : SignTransactionFlow(borrowerPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val iouState = serviceHub.toStateAndRef<IOUState>(stx.tx.inputs.first()).state.data
                    val cashState = serviceHub.toStateAndRef<CashState>(stx.tx.inputs.last()).state.data

                    "Payback value differ from borrowed amount." using (cashState.value.compareTo(iouState.value) == 0)
                    "Lender should own IOU." using iouState.lender.owningKey.equals(ourIdentity.owningKey)
                    "Borrower should own payback cash." using iouState.borrower.owningKey.equals(cashState.owner.owningKey)
                    // below should be always true, because on cash creation we have condition
                    //to accept only bank as a creator
                    "Accept only money created by bank." using cashState.creator.equals(serviceHub.bankProvider())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(borrowerPartySession, expectedTxId = txId))
        }
    }
}
