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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateMoneyFlowTests {
    private lateinit var network: MockNetwork
    private lateinit var bank: StartedMockNode
    private lateinit var owner1: StartedMockNode
    private lateinit var owner2: StartedMockNode
    private var cashValue: Long = 30

    private fun party2cash() = hashMapOf(
            owner1.info.singleIdentity() to listOf(cashValue),
            owner2.info.singleIdentity() to listOf(1L, 2L)
    )

    private fun validFlow() = CreateMoneyFlow.Initiator(party2cash())
    private fun validFuture() = bank.startFlow(validFlow())

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.example.contract"),
                TestCordapp.findCordapp("com.example.flow")
        )))

        val x500Name = CordaX500Name.parse("O=Bank,L=Paris,C=FR")
        bank = network.createPartyNode(legalName = x500Name)
        owner1 = network.createPartyNode()
        owner2 = network.createPartyNode()

        // For real nodes this happens automatically, but we have to manually register the flow for tests.
        owner1.registerInitiatedFlow(CreateMoneyFlow.Acceptor::class.java)
        owner2.registerInitiatedFlow(CreateMoneyFlow.Acceptor::class.java)

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `NOT throw when creator is not a bank`() {
        val party2cash = hashMapOf(owner1.info.singleIdentity() to listOf(cashValue))
        val flow = CreateMoneyFlow.Initiator(party2cash)
        val future = owner2.startFlow(flow)
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
    fun `flow records a transaction in all parties' transaction storages`() {
        val future = validFuture()
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both transaction storages.
        for (node in listOf(bank, owner1, owner2)) {
            assertEquals(signedTx, node.services.validatedTransactions.getTransaction(signedTx.id))
        }
    }

    @Test
    fun `recorded transaction has 0 inputs and 3 outputs`() {
        val future = validFuture()
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both vaults.
        for (node in listOf(bank, owner1, owner2)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)!!
            assert(recordedTx.tx.inputs.isEmpty()) { "expect no inputs" }

            val txOutputs = recordedTx.tx.outputs
            assert(txOutputs.size == 3) { "expect one output "}

            val owner2cash = hashMapOf(
                owner2.info.singleIdentity() to mutableSetOf(1L, 2L),
                owner1.info.singleIdentity() to mutableSetOf(cashValue)
            )

            recordedTx.tx.outputsOfType<CashState>().forEach {
                assertTrue(owner2cash.get(it.owner)!!.contains(it.value), "Expect to contain ${it.value} as cash value for owner1")
                owner2cash.get(it.owner)!!.remove(it.value)
            }

            FlowLogic.contextLogger().info(node.info.singleIdentity().nameOrNull().toString() + " done.")
        }
    }
}
