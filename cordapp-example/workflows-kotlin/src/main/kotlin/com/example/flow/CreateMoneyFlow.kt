package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.CashContract
import com.example.state.CashState
import com.example.utils.bankProvider
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

object CreateMoneyFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val cashValue: Long, val moneyOwner: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val bank = serviceHub.bankProvider()

            require(ourIdentity.owningKey == bank.owningKey) { "only bank can create money" }

            val cashState = CashState(cashValue, bank, moneyOwner);
            val cashCommand = Command(CashContract.Commands.Create(), bank.owningKey)

            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(cashState, CashContract.ID)
                    .addCommand(cashCommand)

            txBuilder.verify(serviceHub)
            val signedTx = serviceHub.signInitialTransaction(txBuilder)
            val ownerSession = initiateFlow(moneyOwner)

            //return subFlow(FinalityFlow(signedTx, Collections.emptyList()))
            return subFlow(FinalityFlow(signedTx, setOf(ownerSession)))
        }
    }

    @InitiatedBy(CreateMoneyFlow.Initiator::class)
    class Acceptor(val bankSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(bankSession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val cash = stx.tx.outputsOfType<CashState>().single()
                    "Expect bank to be cash creator" using cash.creator.equals(serviceHub.bankProvider())
                }
            }

            val txId = subFlow(signTransactionFlow).id
            return subFlow(ReceiveFinalityFlow(bankSession, expectedTxId = txId))
        }
    }
}
