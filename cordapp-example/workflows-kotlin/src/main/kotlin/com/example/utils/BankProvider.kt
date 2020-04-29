package com.example.utils

import com.example.settings.BANK_NAME
import net.corda.core.node.ServiceHub

/* val bankProvider: ServiceHub.() -> Party = {
    val x500Name = CordaX500Name.parse("O=Bank,L=Paris,C=FR")
    this.networkMapCache.getPeerByLegalName(x500Name) ?: throw IllegalArgumentException("Bank does not exist")
} */

fun ServiceHub.bankProvider() = networkMapCache
        .getPeerByLegalName(BANK_NAME)
        ?: throw IllegalArgumentException("Bank does not exist")