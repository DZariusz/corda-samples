package com.example.test.contract

import com.example.contract.IOUContract
import com.example.state.IOUState
import net.corda.core.identity.CordaX500Name
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class IOUContractTests {
    private val ledgerServices = MockServices(listOf(
            "com.example.contract", "com.example.flow", "net.corda.testing.contracts"
    ))
    private val borrower = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
    private val lender = TestIdentity(CordaX500Name("MiniCorp", "New York", "US"))
    private val dummyParty = TestIdentity(CordaX500Name("Dummy", "Dummy", "US"))
    private val iouValue = 1

    @Test
    fun `transaction must include Create command`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, lender.party, borrower.party))
                fails()
                command(listOf(borrower.publicKey, lender.publicKey), IOUContract.Commands.Create())
                verifies()
            }
        }
    }

    @Test
    fun `Create - transaction must have no inputs`() {
        ledgerServices.ledger {
            transaction {
                input(IOUContract.ID, IOUState(iouValue, lender.party, borrower.party))
                output(IOUContract.ID, IOUState(iouValue, lender.party, borrower.party))
                command(listOf(borrower.publicKey, lender.publicKey), IOUContract.Commands.Create())
                `fails with`("No IOUState inputs should be consumed when issuing an IOU.")
            }
        }
    }

    @Test
    fun `Create - transaction must have one output`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, lender.party, borrower.party))
                output(IOUContract.ID, IOUState(iouValue, lender.party, borrower.party))
                command(listOf(borrower.publicKey, lender.publicKey), IOUContract.Commands.Create())
                `fails with`("There should be only one IOUState output.")
            }
        }
    }

    @Test
    fun `Create - transaction required two signers`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, lender.party, borrower.party))
                command(lender.publicKey, IOUContract.Commands.Create())
                `fails with`("Command require two signers.")
            }
        }
    }

    @Test
    fun `Create - lender must sign transaction`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, lender.party, borrower.party))
                command(listOf(lender.publicKey, lender.publicKey), IOUContract.Commands.Create())
                `fails with`("All of the participants must be signers.")
            }
        }
    }

    @Test
    fun `Create - borrower must sign transaction`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, lender.party, borrower.party))
                command(listOf(borrower.publicKey, borrower.publicKey), IOUContract.Commands.Create())
                `fails with`("All of the participants must be signers.")
            }
        }
    }

    @Test
    fun `lender is not borrower`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, borrower.party, borrower.party))
                command(listOf(borrower.publicKey, lender.publicKey), IOUContract.Commands.Create())
                `fails with`("The lender and the borrower cannot be the same entity.")
            }
        }
    }

    @Test
    fun `cannot create negative-value IOUs`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(-1, lender.party, borrower.party))
                command(listOf(borrower.publicKey, lender.publicKey), IOUContract.Commands.Create())
                `fails with`("The IOU's value must be non-negative.")
            }
        }
    }

    @Test
    fun `Destroy - There should be one IOUState input`() {
        ledgerServices.ledger {
            transaction {
                command(lender.publicKey, IOUContract.Commands.Destroy())
                input(IOUContract.ID, IOUState(iouValue, lender.party, borrower.party))
                input(IOUContract.ID, IOUState(iouValue, lender.party, borrower.party))
                `fails with`("There should be one input.")
            }
        }
    }

    @Test
    fun `Destroy - There should be no IOUState output`() {
        ledgerServices.ledger {
            transaction {
                command(lender.publicKey, IOUContract.Commands.Destroy())
                input(IOUContract.ID, IOUState(iouValue, lender.party, borrower.party))
                output(IOUContract.ID, IOUState(iouValue, lender.party, borrower.party))
                `fails with`("There should be no output.")
            }
        }
    }

    @Test
    fun `Destroy - Lender must be a signer`() {
        ledgerServices.ledger {
            transaction {
                input(IOUContract.ID, IOUState(iouValue, lender.party, borrower.party))

                tweak {
                    command(listOf(lender.publicKey, borrower.publicKey), IOUContract.Commands.Destroy())
                    `fails with`("Expect one signer.")
                }

                tweak {
                    command(borrower.publicKey, IOUContract.Commands.Destroy())
                    `fails with`("Lender must be a signer.")
                }

                command(lender.publicKey, IOUContract.Commands.Destroy())
                verifies()
            }
        }
    }

    @Test
    fun `Destroy accepts other inputs and outputs`() {
        ledgerServices.ledger {
            transaction {
                command(lender.publicKey, IOUContract.Commands.Destroy())
                input(IOUContract.ID, IOUState(iouValue, lender.party, borrower.party))

                command(lender.publicKey,  DummyContract.Commands.Create())
                input(DummyContract.PROGRAM_ID, DummyState())

                verifies()
            }
        }
    }
}
