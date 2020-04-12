package com.example.utils

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub

fun bankProvider(serviceHub: ServiceHub) : Party {
    val x500Name = CordaX500Name.parse("O=Bank,L=Paris,C=FR")
    return serviceHub.networkMapCache.getPeerByLegalName(x500Name)
            ?: throw IllegalArgumentException("Bank does not exist")
}
