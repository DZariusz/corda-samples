package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.CashContract
import com.example.contract.IOUContract
import com.example.flow.PaybackFlow.Acceptor
import com.example.flow.PaybackFlow.Initiator
import com.example.state.CashState
import com.example.state.IOUState
import com.example.utils.*
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.unwrap
import java.util.*
import kotlin.streams.toList

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
    class Initiator(val iouStateLinearId: String) : FlowLogic<SignedTransaction>() {
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
            // val signedTransaction = serviceHub.validatedTransactions.getTransaction(loanTxId)
            // val signedTransaction = transactions.find { it.id == transactionHash } ?: throw IllegalArgumentException("Unknown transaction hash.")

            val bank = serviceHub.bankProvider()
            val iouStateAndRef = serviceHub.iouStateFinder(UUID.fromString(iouStateLinearId))
            val iouState = iouStateAndRef.state.data
            val cashStatesAndRefs = serviceHub.moneyFinder(bank, iouState.borrower, iouState.value)
            val cashStateSample = cashStatesAndRefs.first().state.data
            val sum = cashStatesAndRefs.sumByLongSecure { it.state.data.value }

            if (!iouState.borrower.equals(ourIdentity)) {
                throw IllegalArgumentException("Borrower must start the flow")
            }

            require(!cashStatesAndRefs.isEmpty()) { "No money" }
            require(sum >= iouState.value) { "Not enough money to send payback, have: $sum needed: ${iouState.value}" }
            require(cashStatesAndRefs.all { it.state.data.creator == bank }) { "cash must be created by a bank" }

            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val destroyIOUCommand = Command(IOUContract.Commands.Destroy(), iouState.lender.owningKey)
            val paybackCashCommand = Command(CashContract.Commands.Move(), listOf(cashStateSample.owner.owningKey, bank.owningKey))
            //val moveMoneyCommand = Command(CashContract.Commands.Move(), iouState.borrower.owningKey)
            //val moveMoneyCommand = Command(CashContract.Commands.Move(), iouState.borrower.owningKey)

            // Obtain a reference to the notary that was in use for input state
            // I assume for this exercise, that all states have same notary
            val notary = cashStatesAndRefs.first().state.notary

            val paybackCashOutput = CashState(iouState.value.toLong(), bank, iouState.lender)

            // build tx that will consume previous output
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(iouStateAndRef)
                    .addCommand(destroyIOUCommand)
                    .addCommand(paybackCashCommand)
                    .addOutputState(paybackCashOutput)

            cashStatesAndRefs.forEach { txBuilder.addInputState(it) }

            if (sum > iouState.value) {
                val changeCashOutput = CashState(sum - iouState.value, bank, iouState.borrower)
                txBuilder.addOutputState(changeCashOutput)
            }

            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val signedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = INITIALISE_OTHER_PARTY_FLOW
            // Send the state to the counterparty, and receive it back with their signature.
            val lenderSession = initiateFlow(iouState.lender)
            val bankSession = initiateFlow(bank)

            // send/receive part is just for experiment
            val confirmPayback = lenderSession
                    .sendAndReceive<Boolean>("ready for receiving payback?")
                    .unwrap { it }

            require(confirmPayback) { "Oops, lender failed to be ready" }

            progressTracker.currentStep = PAYBACK_COMMUNICATION
            lenderSession.send(iouState.value)

            progressTracker.currentStep = GATHERING_SIGS
            val fullySignedTx = subFlow(CollectSignaturesFlow(
                    signedTx,
                    setOf(lenderSession, bankSession),
                    GATHERING_SIGS.childProgressTracker())
            )

            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(FinalityFlow(fullySignedTx, setOf(lenderSession, bankSession), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val borrowerPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val bank = serviceHub.bankProvider()

            if (!ourIdentity.equals(bank)) {
                borrowerPartySession.receive<String>().unwrap { it }
                //send confirmation about being ready to receive payback
                borrowerPartySession.send(true)

                // just exercise
                val receivedPayback: Int = borrowerPartySession.receive<Int>().unwrap { it }
            }

            val signTransactionFlow = object : SignTransactionFlow(borrowerPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val allInputStates = stx.tx.inputs.getStxStates(serviceHub)

                    val inIOUState = allInputStates.filterByState<IOUState>().single()
                    val inCashStates = allInputStates.filterByState<CashState>()

                    val outCashStates = stx.tx.outputsOfType<CashState>()
                    val lenderPaybackCash = outCashStates
                            .stream().filter { it.owner.equals(inIOUState.lender) }
                            .toList()

                    val inputCashSum = inCashStates.sumByLongSecure { it.value }
                    val lenderPaybackSum = lenderPaybackCash.sumByLongSecure { it.value }

                    "Expect exact payback value. $lenderPaybackSum != ${inIOUState.value}" using (lenderPaybackSum == inIOUState.value.toLong())

                    val borrowerChange = outCashStates
                            .stream().filter { it.owner.equals(inIOUState.borrower) }
                            .toList()
                            .sumByLongSecure { it.value }
                    "Expect change for borrower." using (inputCashSum - lenderPaybackSum == borrowerChange)

                    "Accept input money created by bank." using inCashStates.all { it.creator == bank }
                    "Accept output money created by bank" using outCashStates.all { it.creator == bank }
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(borrowerPartySession, expectedTxId = txId))
        }
    }
}
