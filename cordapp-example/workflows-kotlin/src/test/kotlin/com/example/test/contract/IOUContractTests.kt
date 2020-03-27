package com.example.test.contract

import com.example.contract.IOUContract
import com.example.state.IOUState
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class IOUContractTests {
    private val ledgerServices = MockServices(listOf("com.example.contract", "com.example.flow"))
    private val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
    private val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "New York", "US"))
    private val iouValue = 1

    @Test
    fun `transaction must include Create command`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                fails()
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), IOUContract.Commands.Create())
                verifies()
            }
        }
    }

    @Test
    fun `Create - transaction must have no inputs`() {
        ledgerServices.ledger {
            transaction {
                input(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), IOUContract.Commands.Create())
                `fails with`("No IOUState inputs should be consumed when issuing an IOU.")
            }
        }
    }

    @Test
    fun `Create - transaction must have one output`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), IOUContract.Commands.Create())
                `fails with`("There should be only one IOUState output.")
            }
        }
    }

    @Test
    fun `Create - transaction required two signers`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                command(miniCorp.publicKey, IOUContract.Commands.Create())
                `fails with`("Command require two signers.")
            }
        }
    }

    @Test
    fun `Create - lender must sign transaction`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                command(listOf(miniCorp.publicKey, miniCorp.publicKey), IOUContract.Commands.Create())
                `fails with`("All of the participants must be signers.")
            }
        }
    }

    @Test
    fun `Create - borrower must sign transaction`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                command(listOf(megaCorp.publicKey, megaCorp.publicKey), IOUContract.Commands.Create())
                `fails with`("All of the participants must be signers.")
            }
        }
    }

    @Test
    fun `lender is not borrower`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, megaCorp.party, megaCorp.party))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), IOUContract.Commands.Create())
                `fails with`("The lender and the borrower cannot be the same entity.")
            }
        }
    }

    @Test
    fun `cannot create negative-value IOUs`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(-1, miniCorp.party, megaCorp.party))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), IOUContract.Commands.Create())
                `fails with`("The IOU's value must be non-negative.")
            }
        }
    }

    @Test
    fun `transaction must include Destroy command`() {
        ledgerServices.ledger {
            transaction {
                input(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                fails()
                command(listOf(miniCorp.publicKey), IOUContract.Commands.Destroy())
                verifies()
            }
        }
    }

    @Test
    fun `Destroy - transaction must have no IOUState outputs`() {
        ledgerServices.ledger {
            transaction {
                input(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                command(miniCorp.publicKey, IOUContract.Commands.Destroy())
                `fails with`("There should be no IOUState output.")
            }
        }
    }

    @Test
    fun `Destroy - transaction must have one IOUState input`() {
        ledgerServices.ledger {
            transaction {
                input(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                input(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                command(listOf(miniCorp.publicKey), IOUContract.Commands.Destroy())
                `fails with`("There should be only one IOUState input.")
            }
        }
    }

    @Test
    fun `Destroy - transaction expects one signer`() {
        ledgerServices.ledger {
            transaction {
                input(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                command(listOf(miniCorp.publicKey, megaCorp.publicKey), IOUContract.Commands.Destroy())
                `fails with`("Expect one signer.")
            }
        }
    }

    @Test
    fun `Destroy - lender must sign the transaction`() {
        ledgerServices.ledger {
            transaction {
                input(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                command(listOf(megaCorp.publicKey), IOUContract.Commands.Destroy())
                `fails with`("Lender must be a signer.")
            }
        }
    }

    @Test
    fun `Destroy - expect valid transaction`() {
        ledgerServices.ledger {
            transaction {
                input(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                command(listOf(miniCorp.publicKey), IOUContract.Commands.Destroy())
                verifies()
            }
        }
    }
}