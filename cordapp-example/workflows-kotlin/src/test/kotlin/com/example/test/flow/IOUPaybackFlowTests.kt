package com.example.test.flow

import com.example.flow.CreateMoneyFlow
import com.example.flow.ExampleFlow
import com.example.flow.PaybackFlow
import com.example.settings.BANK_NAME
import com.example.state.CashState
import com.example.state.IOUState
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
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
import kotlin.test.assertTrue

class IOUPaybackFlowTests {
    private lateinit var network: MockNetwork
    private lateinit var bank: StartedMockNode
    private lateinit var lender: StartedMockNode
    private lateinit var borrower: StartedMockNode
    private lateinit var iouInput: IOUState
    private var cashValue: Long = 9

    private fun party2cashMap(party: StartedMockNode, values: List<Long>) = hashMapOf(party.info.singleIdentity() to values)

    private fun validPaybackFlow() = PaybackFlow.Initiator(iouInput.linearId.toString())
    private fun validPaybackFuture() = borrower.startFlow(validPaybackFlow())

    // create input IOU - we need input for flow test
    private fun issueIOU(borrower: Party): IOUState {
        val flow = ExampleFlow.Initiator(cashValue.toInt(), borrower)
        val future = lender.startFlow(flow)
        network.runNetwork()

        val stx: SignedTransaction? = future.get()
        return stx?.tx?.outputs?.get(0)?.data as IOUState
    }

    private fun createMoney(cashValues: List<Long>, owner: StartedMockNode) {
        val flow = CreateMoneyFlow.Initiator(party2cashMap(owner, cashValues))
        val future = bank.startFlow(flow)
        network.runNetwork()
        future.get()
    }

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.example.contract"),
                TestCordapp.findCordapp("com.example.flow")
        )))

        bank = network.createPartyNode(legalName = BANK_NAME)
        lender = network.createPartyNode()
        borrower = network.createPartyNode()

        // For real nodes this happens automatically, but we have to manually register the flow for tests.
        listOf(lender, borrower, bank).forEach {
            it.registerInitiatedFlow(ExampleFlow.Acceptor::class.java)
            it.registerInitiatedFlow(PaybackFlow.Acceptor::class.java)
            it.registerInitiatedFlow(CreateMoneyFlow.Acceptor::class.java)
        }

        network.runNetwork()

        iouInput = issueIOU(borrower.info.singleIdentity())
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `throw when invalid UUID string`() {
        val flow = PaybackFlow.Initiator("123")
        val future = lender.startFlow(flow)
        network.runNetwork()

        assertFailsWith<IllegalArgumentException> { future.getOrThrow() }
    }

    @Test
    fun `throw when no money`() {
        // money not created but we starting a flow
        val future = validPaybackFuture()
        network.runNetwork()

        assertFailsWith<NoSuchElementException> { future.getOrThrow() }
    }

    @Test
    fun `throw when not enough money`() {
        createMoney(listOf(cashValue - 1), borrower)
        val future = validPaybackFuture()
        network.runNetwork()

        assertFailsWith<NoSuchElementException> { future.getOrThrow() }
    }

    @Test
    fun `Throw when money is not created by bank`() {
        val flow = CreateMoneyFlow.Initiator(party2cashMap(borrower, listOf(cashValue)))
        lender.startFlow(flow)
        network.runNetwork()

        val future = validPaybackFuture()
        network.runNetwork()

        assertFailsWith<NoSuchElementException> { future.getOrThrow() }
    }

    @Test
    fun `SignedTransaction returned by the flow is signed by both sides`() {
        createMoney(listOf(cashValue), borrower)
        val future = validPaybackFuture()
        network.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifySignaturesExcept(borrower.info.singleIdentity().owningKey)
        signedTx.verifySignaturesExcept(lender.info.singleIdentity().owningKey)
        signedTx.verifySignaturesExcept(bank.info.singleIdentity().owningKey)
    }

    @Test
    fun `flow records a transaction in all parties' transaction storages`() {
        createMoney(listOf(cashValue), borrower)
        val future = validPaybackFuture()
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both transaction storages.
        for (node in listOf(borrower, lender, bank)) {
            assertEquals(signedTx, node.services.validatedTransactions.getTransaction(signedTx.id))
        }
    }

    @Test
    fun `recorded transaction has valid number of inputs and outputs`() {
        createMoney(listOf(cashValue - 1, 2), borrower)
        val future = validPaybackFuture()
        network.runNetwork()

        val signedTx = future.getOrThrow()

        for (node in listOf(borrower, lender, bank)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)
            val txInputs = recordedTx!!.tx.inputs
            val txOutputs = recordedTx.tx.outputs

            assert(txInputs.size == 3) { "should be 2x cash + iou" }
            assert(txOutputs.size == 2) { "should be 2x cash (payback + change)" }

            val cashOutputStates = recordedTx.tx.outputsOfType<CashState>()

            val lenderCashOut = cashOutputStates.first()
            val borrowerCashOut = cashOutputStates.last()

            assertTrue(lenderCashOut.owner == lender.info.singleIdentity(), "lender owns payback money")
            assertTrue(lenderCashOut.value == cashValue, "invalid payback money")
            assertTrue(borrowerCashOut.owner == borrower.info.singleIdentity(), "borower owns change")
            assertTrue(borrowerCashOut.value == 1L, "invalid change")

            assertTrue(cashOutputStates.all { it.creator.equals(bank.info.singleIdentity()) }, "Expect bank to create all the money")
        }
    }
}
