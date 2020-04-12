package com.example.test.flow

import com.example.flow.ExampleFlow
import com.example.flow.PaybackFlow
import com.example.state.CashState
import com.example.state.IOUState
import net.corda.core.concurrent.CordaFuture
import net.corda.core.identity.CordaX500Name
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

    private lateinit var validFlow: PaybackFlow.Initiator
    private lateinit var validFuture: CordaFuture<SignedTransaction>

    // create input IOU - we need input for flow test
    private fun issueIOU(): IOUState {
        val flow = ExampleFlow.Initiator(9, borrower.info.singleIdentity())
        val future = lender.startFlow(flow)
        network.runNetwork()

        val stx: SignedTransaction? = future.get()
        return stx?.tx?.outputs?.get(0)?.data as IOUState
    }

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.example.contract"),
                TestCordapp.findCordapp("com.example.flow")
        )))

        val x500Name = CordaX500Name.parse("O=Bank,L=Paris,C=FR")
        bank = network.createPartyNode(legalName = x500Name)
        lender = network.createPartyNode()
        borrower = network.createPartyNode()

        // For real nodes this happens automatically, but we have to manually register the flow for tests.
        listOf(lender, borrower).forEach {
            it.registerInitiatedFlow(ExampleFlow.Acceptor::class.java)
            it.registerInitiatedFlow(PaybackFlow.Acceptor::class.java)
        }

        network.runNetwork()

        iouInput = issueIOU()

        validFlow = PaybackFlow.Initiator(iouInput.linearId.toString(), iouInput.value)
        validFuture = borrower.startFlow(validFlow)
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `throw when invalid UUID string`() {
        val flow = PaybackFlow.Initiator("123", iouInput.value)
        val future = lender.startFlow(flow)
        network.runNetwork()

        assertFailsWith<IllegalArgumentException> { future.getOrThrow() }
    }

    @Test
    fun `throw when value doesn't match IOU`() {
        val flow = PaybackFlow.Initiator(iouInput.linearId.toString(), iouInput.value - 1)
        val future = lender.startFlow(flow)
        network.runNetwork()

        assertFailsWith<KotlinNullPointerException> { future.getOrThrow() }
    }

    @Test
    fun `SignedTransaction returned by the flow is signed by both sides`() {
        network.runNetwork()

        val signedTx = validFuture.getOrThrow()
        signedTx.verifySignaturesExcept(borrower.info.singleIdentity().owningKey)
        signedTx.verifySignaturesExcept(lender.info.singleIdentity().owningKey)
    }

    @Test
    fun `flow records a transaction in both parties' transaction storages`() {
        network.runNetwork()
        val signedTx = validFuture.getOrThrow()

        // We check the recorded transaction in both transaction storages.
        for (node in listOf(borrower, lender)) {
            assertEquals(signedTx, node.services.validatedTransactions.getTransaction(signedTx.id))
        }
    }

    @Test
    fun `recorded transaction has 2 inputs and 2 outputs`() {
        network.runNetwork()
        val signedTx = validFuture.getOrThrow()

        // We check the recorded transaction in both vaults.
        for (node in listOf(borrower, lender)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)
            val txInputs = recordedTx!!.tx.inputs
            val txOutputs = recordedTx.tx.outputs
            assert(txInputs.size == 2)
            assert(txOutputs.size == 1)

            val iouInputState = node.services.toStateAndRef<IOUState>(recordedTx.tx.inputs.get(0)).state.data
            val cashInputState = node.services.toStateAndRef<CashState>(recordedTx.tx.inputs.get(1)).state.data
            val cashOutputState = recordedTx.tx.outputsOfType<CashState>().first()

            assertTrue(iouInputState.lender.equals(cashOutputState.owner), "lender owns money")
            assertEquals(cashInputState.owner, borrower.info.singleIdentity())
            assertEquals(cashOutputState.owner, lender.info.singleIdentity())

            assertEquals(iouInputState.value.toLong(), cashInputState.value, "iou value must match cash input")
            assertEquals(cashInputState.value, cashOutputState.value, "cash amount must be the same")
        }
    }
}
