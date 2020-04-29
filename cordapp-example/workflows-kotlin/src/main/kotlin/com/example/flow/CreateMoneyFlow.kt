package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.CashContract
import com.example.state.CashState
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

object CreateMoneyFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val cashOwners: HashMap<Party, List<Long>>) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            val cashCommand = Command(CashContract.Commands.Create(), ourIdentity.owningKey)
            val txBuilder = TransactionBuilder(notary).addCommand(cashCommand)

            cashOwners.forEach { owner, cashValues ->
                cashValues.forEach {
                    val cashState = CashState(it, creator = ourIdentity, owner = owner)
                    txBuilder.addOutputState(cashState, CashContract.ID)
                }
            }

            txBuilder.verify(serviceHub)
            val signedTx = serviceHub.signInitialTransaction(txBuilder)
            val setOfSessions: Set<FlowSession> = cashOwners.map { it.key }.map { initiateFlow(it) }.toSet()

            return subFlow(FinalityFlow(signedTx, setOfSessions))
        }
    }

    @InitiatedBy(CreateMoneyFlow.Initiator::class)
    class Acceptor(private val creatorSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            return subFlow(ReceiveFinalityFlow(creatorSession))
        }
    }
}
