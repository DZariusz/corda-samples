package com.example.utils

import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServiceHub

/* val bankProvider: ServiceHub.() -> Party = {
    val x500Name = CordaX500Name.parse("O=Bank,L=Paris,C=FR")
    this.networkMapCache.getPeerByLegalName(x500Name) ?: throw IllegalArgumentException("Bank does not exist")
} */

fun ServiceHub.bankProvider() = networkMapCache
        .getPeerByLegalName(CordaX500Name.parse("O=Bank,L=Paris,C=FR"))
        ?: throw IllegalArgumentException("Bank does not exist")