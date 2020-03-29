package com.example.test.contract

import com.example.contract.CashContract
import com.example.contract.IOUContract
import com.example.state.CashState
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
    fun `Destroy - transaction must have two inputs`() {
        ledgerServices.ledger {
            transaction {
                input(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                command(miniCorp.publicKey, IOUContract.Commands.Destroy())
                `fails with`("There should be two inputs in general.")
            }
        }
    }

    @Test
    fun `Destroy - There should be one IOUState input`() {
        ledgerServices.ledger {
            transaction {
                command(miniCorp.publicKey, IOUContract.Commands.Destroy())

                input(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                input(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                `fails with`("There should be only one IOUState input.")
            }
        }
    }

    /*
        case `There should be only one CashState input` is impossible to test because of
        two preceding conditions:
            "There should be two inputs in general." using (tx.inputs.size == 2)
            "There should be only one IOUState input." using (tx.inputsOfType<IOUState>().size == 1)
            "There should be only one CashState input." using (tx.inputsOfType<CashState>().size == 1)

     */

    @Test
    fun `Destroy - testing outputs`() {
        ledgerServices.ledger {
            transaction {
                command(miniCorp.publicKey, IOUContract.Commands.Destroy())
                command(megaCorp.publicKey, CashContract.Commands.Move())

                input(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                input(CashContract.ID, CashState(iouValue.toLong(), megaCorp.party))

                `fails with`("There should be one output in general.")

                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                `fails with`("There should be one CashState output.")
            }
        }
    }

    @Test
    fun `Destroy - signatures`() {
        ledgerServices.ledger {
            transaction {
                command(megaCorp.publicKey, CashContract.Commands.Move())

                input(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                input(CashContract.ID, CashState(iouValue.toLong(), megaCorp.party))
                output(CashContract.ID, CashState(iouValue.toLong(), miniCorp.party))

                tweak {
                    command(listOf(miniCorp.publicKey, megaCorp.publicKey), IOUContract.Commands.Destroy())
                    `fails with`("Expect one signer.")
                }

                tweak {
                    command(megaCorp.publicKey, IOUContract.Commands.Destroy())
                    `fails with`("Lender must be a signer.")
                }
            }
        }
    }

    @Test
    fun `Destroy - expect valid transaction`() {
        ledgerServices.ledger {
            transaction {
                command(miniCorp.publicKey, IOUContract.Commands.Destroy())
                command(megaCorp.publicKey, CashContract.Commands.Move())

                input(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                input(CashContract.ID, CashState(iouValue.toLong(), megaCorp.party))

                output(CashContract.ID, CashState(iouValue.toLong(), miniCorp.party))

                verifies()
            }
        }
    }
}
