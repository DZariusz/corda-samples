package com.example.test.flow

import com.example.flow.CreateMoneyFlow
import com.example.state.CashState
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CreateMoneyFlowTests {
    private lateinit var network: MockNetwork
    private lateinit var bank: StartedMockNode
    private lateinit var owner: StartedMockNode
    private var cashValue: Long = 30

    private fun validFlow() = CreateMoneyFlow.Initiator(cashValue, owner.info.singleIdentity())
    private fun validFuture() = bank.startFlow(validFlow())

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.example.contract"),
                TestCordapp.findCordapp("com.example.flow")
        )))

        val x500Name = CordaX500Name.parse("O=Bank,L=Paris,C=FR")
        bank = network.createPartyNode(legalName = x500Name)
        owner = network.createPartyNode()

        // For real nodes this happens automatically, but we have to manually register the flow for tests.
        owner.registerInitiatedFlow(CreateMoneyFlow.Acceptor::class.java)

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `NOT throw when creator is not a bank`() {
        val flow = CreateMoneyFlow.Initiator(cashValue, bank.info.singleIdentity())
        val future = owner.startFlow(flow)
        network.runNetwork()

        future.get()
    }

    @Test
    fun `SignedTransaction returned by the flow is signed by bank`() {
        val future = validFuture()
        network.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifySignaturesExcept(bank.info.singleIdentity().owningKey)
    }

    @Test
    fun `flow records a transaction in both parties' transaction storages`() {
        val future = validFuture()
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both transaction storages.
        for (node in listOf(bank, owner)) {
            assertEquals(signedTx, node.services.validatedTransactions.getTransaction(signedTx.id))
        }
    }

    @Test
    fun `recorded transaction has 0 inputs and 1 output`() {
        val future = validFuture()
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both vaults.
        for (node in listOf(bank, owner)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)!!
            assert(recordedTx.tx.inputs.isEmpty()) { "expect no inputs" }

            val txOutputs = recordedTx.tx.outputs
            assert(txOutputs.size == 1) { "expect one output "}

            val cashOutputState = recordedTx.tx.outputsOfType<CashState>().single()
            assertEquals(cashValue, cashOutputState.value, "iou value must match cash output")

            FlowLogic.contextLogger().info(node.info.singleIdentity().nameOrNull().toString() + " done.")
        }
    }
}
