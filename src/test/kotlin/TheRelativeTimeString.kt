import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.komputing.fauceth.util.toTimeString

class TheRelativeTimeString {

    @Test
    fun shouldDisplaySingleSeconds() {
        42L.toTimeString() `should be equal to` "42s"
    }

    @Test
    fun shouldDisplaySingleMinute() {
        60L.toTimeString() `should be equal to` "1m"
    }

    @Test
    fun shouldDisplayMinuteAndSecond() {
        61L.toTimeString() `should be equal to` "1m 1s"
    }

    @Test
    fun shouldDisplaySingleHour() {
        3600L.toTimeString() `should be equal to` "1h"
    }

    @Test
    fun shouldDisplayHourAndSecond() {
        3601L.toTimeString() `should be equal to` "1h 1s"
    }

    @Test
    fun shouldDisplay0s() {
        0L.toTimeString() `should be equal to` "0s"
    }
}