package com.example.test.flow

import com.example.flow.ExampleFlow
import com.example.flow.PaybackFlow
import com.example.state.IOUState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class IOUPaybackFlowTests {
    private lateinit var network: MockNetwork
    private lateinit var lender: StartedMockNode
    private lateinit var borrower: StartedMockNode
    private lateinit var iou: IOUState

    fun prepareIOUinput(): IOUState {
        val iouValue = 1
        val flow = ExampleFlow.Initiator(iouValue, borrower.info.singleIdentity())
        val future = lender.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        val recordedTx = lender.services.validatedTransactions.getTransaction(signedTx.id)
        val txOutputs = recordedTx!!.tx.outputs

        return txOutputs[0].data as IOUState
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

        iou = prepareIOUinput()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `flow rejects invalid UUID string`() {
        val flow = PaybackFlow.Initiator("123", iou.value)
        val future = lender.startFlow(flow)
        network.runNetwork()

        assertFailsWith<IllegalArgumentException> { future.getOrThrow() }
    }
}