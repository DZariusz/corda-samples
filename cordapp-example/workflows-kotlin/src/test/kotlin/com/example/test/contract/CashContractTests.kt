package com.example.test.contract

import com.example.contract.CashContract
import com.example.state.CashState
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class CashContractTests {
    private val ledgerServices = MockServices(listOf("com.example.contract", "com.example.flow"))
    private val bank = TestIdentity(CordaX500Name("Bank", "Sun", "SS"))
    private val owner = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
    private val newOwner = TestIdentity(CordaX500Name("MiniCorp", "New York", "US"))
    private val iouValue = 9L

    @Test
    fun `transaction must include Create command`() {
        ledgerServices.ledger {
            transaction {
                output(CashContract.ID, CashState(iouValue, bank.party, owner.party))
                fails()
                command(owner.publicKey, CashContract.Commands.Create())
                verifies()
            }
        }
    }

    @Test
    fun `Create - There should be no input`() {
        ledgerServices.ledger {
            transaction {
                input(CashContract.ID, CashState(iouValue, bank.party, owner.party))
                output(CashContract.ID, CashState(iouValue, bank.party, owner.party))
                command(owner.publicKey, CashContract.Commands.Create())
                `fails with`("There should be no input.")
            }
        }
    }

    @Test
    fun `Create - Cash value must be positive`() {
        ledgerServices.ledger {
            transaction {
                output(CashContract.ID, CashState(-1L, bank.party, owner.party))
                command(owner.publicKey, CashContract.Commands.Create())
                `fails with`("Cash value must be positive")
            }
        }
    }

    @Test
    fun `Create - Expect one signature`() {
        ledgerServices.ledger {
            transaction {
                output(CashContract.ID, CashState(iouValue, bank.party, owner.party))
                command(listOf(owner.publicKey, owner.publicKey), CashContract.Commands.Create())
                `fails with`("Expect one signature")
            }
        }
    }

    @Test
    fun `transaction must include Move command`() {
        ledgerServices.ledger {
            transaction {
                input(CashContract.ID, CashState(iouValue, bank.party, owner.party))
                output(CashContract.ID, CashState(iouValue, bank.party, newOwner.party))
                fails()
                command(owner.publicKey, CashContract.Commands.Move())
                verifies()
            }
        }
    }

    @Test
    fun `Move - There should be one input`() {
        ledgerServices.ledger {
            transaction {
                input(CashContract.ID, CashState(iouValue, bank.party, owner.party))
                input(CashContract.ID, CashState(iouValue, bank.party, owner.party))
                command(owner.publicKey, CashContract.Commands.Move())
                `fails with`("There should be one input.")
            }
        }
    }

    @Test
    fun `Move - There should be one output`() {
        ledgerServices.ledger {
            transaction {
                command(owner.publicKey, CashContract.Commands.Move())
                input(CashContract.ID, CashState(iouValue, bank.party, owner.party))
                `fails with`("There should be one output.")

                tweak {
                    output(CashContract.ID, CashState(iouValue, bank.party, owner.party))
                    output(CashContract.ID, CashState(iouValue, bank.party, owner.party))
                    `fails with`("There should be one output.")
                }
            }
        }
    }

    @Test
    fun `Move - in out value must match`() {
        ledgerServices.ledger {
            transaction {
                command(owner.publicKey, CashContract.Commands.Move())
                input(CashContract.ID, CashState(iouValue, bank.party, owner.party))
                output(CashContract.ID, CashState(iouValue + 1, bank.party, owner.party))
                `fails with`("in/out value must match")
            }
        }
    }

    @Test
    fun `Move - Can't move to the same party`() {
        ledgerServices.ledger {
            transaction {
                command(owner.publicKey, CashContract.Commands.Move())
                input(CashContract.ID, CashState(iouValue, bank.party, owner.party))
                output(CashContract.ID, CashState(iouValue, bank.party, owner.party))
                `fails with`("Can't move to the same party")
            }
        }
    }

    @Test
    fun `Move - Expect one signature`() {
        ledgerServices.ledger {
            transaction {
                command(listOf(owner.publicKey, owner.publicKey), CashContract.Commands.Move())
                input(CashContract.ID, CashState(iouValue, bank.party, owner.party))
                output(CashContract.ID, CashState(iouValue, bank.party, newOwner.party))
                `fails with`("Expect one signature")
            }
        }
    }

    @Test
    fun `Move - Previous owner must sign`() {
        ledgerServices.ledger {
            transaction {
                command(newOwner.publicKey, CashContract.Commands.Move())
                input(CashContract.ID, CashState(iouValue, bank.party, owner.party))
                output(CashContract.ID, CashState(iouValue, bank.party, newOwner.party))
                `fails with`("Previous owner must sign.")
            }
        }
    }

    @Test
    fun `Move - Creator stays intact`() {
        ledgerServices.ledger {
            transaction {
                input(CashContract.ID, CashState(iouValue, bank.party, owner.party))
                output(CashContract.ID, CashState(iouValue, newOwner.party, newOwner.party))
                command(owner.publicKey, CashContract.Commands.Move())
                `fails with`("Creator stays intact.")
            }
        }
    }
}
