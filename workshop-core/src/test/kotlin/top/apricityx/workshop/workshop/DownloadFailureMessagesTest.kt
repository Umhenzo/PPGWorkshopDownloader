package top.apricityx.workshop.workshop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.apricityx.workshop.steam.protocol.SteamAuthenticationException
import top.apricityx.workshop.steam.protocol.SteamProtocolException
import top.apricityx.workshop.steam.protocol.SteamServiceMethodException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DownloadFailureMessagesTest {

    @Test
    fun `formats EResult 15 Access Denied with helpful Portuguese message`() {
        val exception = SteamServiceMethodException(
            methodName = "ContentServerDirectory.GetManifestRequestCode#1",
            resultCode = 15,
            steamMessage = "Access Denied",
        )
        val outer = WorkshopDownloadException("Unable to download UGC manifest", exception)

        val message = outer.userVisibleDownloadFailureMessage()

        assertTrue("Expected EResult=15 message, got: $message", message.contains("Acesso negado pela Steam"))
        assertTrue("Expected explanation of private item, got: $message", message.contains("EResult=15"))
    }

    @Test
    fun `formats EResult 3 No Connection message`() {
        val exception = SteamServiceMethodException(
            methodName = "ContentServerDirectory.GetCDNAuthToken#1",
            resultCode = 3,
            steamMessage = "No Connection",
        )

        val message = exception.userVisibleDownloadFailureMessage()

        assertEquals("Sem conexão com os servidores da Steam (EResult=3).", message)
    }

    @Test
    fun `formats SocketTimeoutException with user friendly network message`() {
        val exception = WorkshopDownloadException(
            "Steam CDN request exhausted retries",
            SocketTimeoutException("Read timed out"),
        )

        val message = exception.userVisibleDownloadFailureMessage()

        assertEquals("Tempo limite de conexão excedido com os servidores da Steam.", message)
    }

    @Test
    fun `formats UnknownHostException with connection error message`() {
        val exception = UnknownHostException("cm1.steampowered.com")

        val message = exception.userVisibleDownloadFailureMessage()

        assertEquals(
            "Não foi possível resolver o endereço dos servidores da Steam. Verifique sua conexão com a internet.",
            message,
        )
    }

    @Test
    fun `formats SteamAuthenticationException correctly`() {
        val exception = SteamAuthenticationException(
            resultCode = 5,
            message = "Steam session logon failed: Invalid Password",
        )

        val message = exception.userVisibleDownloadFailureMessage()

        assertEquals("Steam session logon failed: Invalid Password", message)
    }

    @Test
    fun `formats Session Protocol Exception correctly`() {
        val exception = SteamProtocolException("Falha na conexão com os servidores da Steam CM: Connection refused")

        val message = exception.userVisibleDownloadFailureMessage()

        assertEquals("Falha na conexão com os servidores da Steam CM: Connection refused", message)
    }

    @Test
    fun `does not collapse root cause when 401 is present in exception message`() {
        val cause = SteamServiceMethodException(
            methodName = "ContentServerDirectory.GetCDNAuthToken#1",
            resultCode = 15,
            steamMessage = "Access Denied",
        )
        val cdnError = WorkshopDownloadException("Steam CDN request failed: 401", cause)
        val wrapper = WorkshopDownloadException("Steam CDN request exhausted retries: Steam CDN request failed: 401", cdnError)

        val message = wrapper.userVisibleDownloadFailureMessage()

        assertTrue("Expected diagnostic message instead of generic 401, got: $message", message.contains("Acesso negado pela Steam"))
    }
}
