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
                output(CashContract.ID, CashState(bank.party, owner.party, iouValue))
                fails()
                command(bank.publicKey, CashContract.Commands.Create())
                verifies()
            }
        }
    }

    @Test
    fun `Create - There should be no input`() {
        ledgerServices.ledger {
            transaction {
                output(CashContract.ID, CashState(bank.party, owner.party, iouValue))
                command(bank.publicKey, CashContract.Commands.Create())
                verifies()
                input(CashContract.ID, CashState(bank.party, owner.party, iouValue))
                `fails with`("There should be no input.")
            }
        }
    }

    @Test
    fun `Create - Cash value must be positive`() {
        ledgerServices.ledger {
            transaction {
                command(bank.publicKey, CashContract.Commands.Create())

                tweak {
                    output(CashContract.ID, CashState(bank.party, owner.party, -1L))
                    `fails with`("Cash value must be positive")
                }

                output(CashContract.ID, CashState(bank.party, owner.party, iouValue))
                verifies()
            }
        }
    }

    @Test
    fun `Create - Expect valir signature`() {
        ledgerServices.ledger {
            transaction {
                output(CashContract.ID, CashState(bank.party, owner.party, iouValue))

                tweak {
                    command(listOf(bank.publicKey, bank.publicKey), CashContract.Commands.Create())
                    `fails with`("Expect one signature")
                }

                tweak {
                    command(owner.publicKey, CashContract.Commands.Create())
                    `fails with`("Creator must sign the output")
                }

                command(bank.publicKey, CashContract.Commands.Create())
                verifies()
            }
        }
    }

    @Test
    fun `transaction must include Move command`() {
        ledgerServices.ledger {
            transaction {
                input(CashContract.ID, CashState(bank.party, owner.party, iouValue))
                output(CashContract.ID, CashState(bank.party, newOwner.party, iouValue))
                fails()
                command(listOf(owner.publicKey, bank.publicKey), CashContract.Commands.Move())
                verifies()
            }
        }
    }

    @Test
    fun `Move - There should be at least one input`() {
        ledgerServices.ledger {
            transaction {
                command(listOf(owner.publicKey, bank.publicKey), CashContract.Commands.Move())
                output(CashContract.ID, CashState(bank.party, newOwner.party, iouValue))
                `fails with`("There should be at least one input.")

                tweak {
                    input(CashContract.ID, CashState(bank.party, owner.party, iouValue))
                    verifies()
                }

                input(CashContract.ID, CashState(bank.party, owner.party, iouValue - 1))
                input(CashContract.ID, CashState(bank.party, owner.party, 1))
                verifies()
            }
        }
    }

    @Test
    fun `Move - fails when values overflow its limit`() {
        ledgerServices.ledger {
            transaction {
                command(listOf(owner.publicKey, bank.publicKey), CashContract.Commands.Move())
                input(CashContract.ID, CashState(bank.party, owner.party, iouValue))
                output(CashContract.ID, CashState(bank.party, newOwner.party, iouValue))

                tweak {
                    input(CashContract.ID, CashState(bank.party, owner.party, Long.MAX_VALUE))
                    fails()
                }

                verifies()
            }
        }
    }

    @Test
    fun `Move - There should be at least one output`() {
        ledgerServices.ledger {
            transaction {
                command(listOf(owner.publicKey, bank.publicKey), CashContract.Commands.Move())
                input(CashContract.ID, CashState(bank.party, owner.party, iouValue))
                `fails with`("There should be at least one output.")

                tweak {
                    output(CashContract.ID, CashState(bank.party, newOwner.party, iouValue))
                    verifies()
                }

                tweak {
                    output(CashContract.ID, CashState(bank.party, newOwner.party, 1))
                    output(CashContract.ID, CashState(bank.party, newOwner.party, iouValue - 1))
                    verifies()
                }

            }
        }
    }

    @Test
    fun `Move - in out value must match`() {
        ledgerServices.ledger {
            transaction {
                command(listOf(owner.publicKey, bank.publicKey), CashContract.Commands.Move())
                input(CashContract.ID, CashState(bank.party, owner.party, iouValue))

                tweak {
                    output(CashContract.ID, CashState(bank.party, owner.party, iouValue + 1))
                    `fails with`("in/out value must match")
                }

                output(CashContract.ID, CashState(bank.party, owner.party, iouValue))
                verifies()
            }
        }
    }

    @Test
    fun `Move - Previous owner must sign`() {
        ledgerServices.ledger {
            transaction {
                input(CashContract.ID, CashState(bank.party, owner.party, iouValue))
                output(CashContract.ID, CashState(bank.party, newOwner.party, iouValue))

                tweak {
                    command(listOf(newOwner.publicKey, bank.publicKey), CashContract.Commands.Move())
                    `fails with`("Previous cash owner must sign.")
                }

                command(listOf(owner.publicKey, bank.publicKey), CashContract.Commands.Move())
                verifies()
            }
        }
    }

    @Test
    fun `Move - Creator stays intact`() {
        ledgerServices.ledger {
            transaction {
                input(CashContract.ID, CashState(bank.party, owner.party, iouValue))

                tweak {
                    command(listOf(owner.publicKey, newOwner.publicKey), CashContract.Commands.Move())
                    output(CashContract.ID, CashState(owner.party, newOwner.party, iouValue))
                    `fails with`("Creator stays intact.")
                }

                command(listOf(owner.publicKey, bank.publicKey), CashContract.Commands.Move())
                output(CashContract.ID, CashState(bank.party, newOwner.party, iouValue))
                verifies()
            }
        }
    }
}
