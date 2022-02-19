package org.komputing.fauceth.calls

import io.ktor.http.*
import org.kethereum.eip137.model.ENSName
import org.kethereum.model.Address
import org.komputing.fauceth.ADDRESS_KEY
import org.komputing.fauceth.CALLBACK_KEY
import org.komputing.fauceth.CHAIN_KEY
import org.komputing.fauceth.chains

class ReceiveParametersProcessor(receiveParameters: Parameters) {

    var addressString: String
    val chainString: String?

    init {
        var tempChainString = receiveParameters[CHAIN_KEY]
        var tempAddressString = receiveParameters[ADDRESS_KEY]

        if (tempAddressString?.contains(":") == true) {
            tempChainString = tempAddressString.split(":").first()
            tempAddressString = tempAddressString.substringAfter(":")
            if (tempChainString == "eip155") {
                tempChainString = tempAddressString.split(":").firstOrNull()
                tempAddressString = tempAddressString.substringAfter(":")
            }
        }
        addressString = tempAddressString ?: ""
        chainString = tempChainString
    }

    var address = Address(addressString)
    val callback = receiveParameters[CALLBACK_KEY]
    val ensName = ENSName(addressString)

    val chain = chains.findLast { it.staticChainInfo.shortName == chainString || it.staticChainInfo.chainId == chainString?.toLongOrNull() }
}