import io.ktor.http.*
import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.komputing.fauceth.ADDRESS_KEY
import org.komputing.fauceth.CHAIN_KEY
import org.komputing.fauceth.calls.ReceiveParametersProcessor

class TheParameterProcessor {

    @Test
    fun shouldProcessCAIP10() {
        val params = ReceiveParametersProcessor(Parameters.build { appendAll(parametersOf(ADDRESS_KEY, "eip155:5:0x123")) })
        params.addressString `should be equal to` "0x123"
        params.chainString `should be equal to` "5"
    }

    @Test
    fun shouldProcessEIP3770() {
        val params = ReceiveParametersProcessor(Parameters.build { appendAll(parametersOf(ADDRESS_KEY, "gor:0x235")) })
        params.addressString `should be equal to` "0x235"
        params.chainString `should be equal to` "gor"
    }

    @Test
    fun shouldProcessNormalAddresses() {
        val params = ReceiveParametersProcessor(Parameters.build {
            append(ADDRESS_KEY, "0x237")
            append(CHAIN_KEY, "4")
        })
        params.addressString `should be equal to` "0x237"
        params.chainString `should be equal to` "4"
    }
}