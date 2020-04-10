package com.example.test.flow

import com.example.flow.ExampleFlow
import com.example.flow.PaybackFlow
import com.example.state.IOUState
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertFailsWith

class IOUPaybackFlowTests {
    private lateinit var network: MockNetwork
    private lateinit var lender: StartedMockNode
    private lateinit var borrower: StartedMockNode
    private lateinit var iouInput: IOUState

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

        lender = network.createPartyNode()
        borrower = network.createPartyNode()

        // For real nodes this happens automatically, but we have to manually register the flow for tests.
        listOf(lender, borrower).forEach {
            it.registerInitiatedFlow(ExampleFlow.Acceptor::class.java)
            it.registerInitiatedFlow(PaybackFlow.Acceptor::class.java)
        }

        network.runNetwork()

        iouInput = issueIOU();
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    @Ignore
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

        assertFailsWith<IllegalArgumentException> { future.getOrThrow() }
    }


    @Test
    @Ignore
    fun `SignedTransaction returned by the flow is signed by both sides`() {
        val flow = PaybackFlow.Initiator(iouInput.linearId.toString(), iouInput.value)
        val future = borrower.startFlow(flow)
        network.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifySignaturesExcept(borrower.info.singleIdentity().owningKey)
        signedTx.verifySignaturesExcept(lender.info.singleIdentity().owningKey)
    }

}