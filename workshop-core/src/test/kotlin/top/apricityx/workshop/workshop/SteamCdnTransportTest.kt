package top.apricityx.workshop.workshop

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import top.apricityx.workshop.steam.protocol.CdnServer
import top.apricityx.workshop.steam.protocol.SteamServiceMethodException

class SteamCdnTransportTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var transport: SteamCdnTransport

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
        transport = SteamCdnTransport(client)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun mockCdnServer(): CdnServer {
        return CdnServer(
            type = "CDN",
            sourceId = 1,
            cellId = 1,
            load = 0,
            weightedLoad = 0f,
            numEntriesInClientList = 1,
            steamChinaOnly = false,
            host = "${server.hostName}:${server.port}",
            vHost = server.hostName,
            useAsProxy = false,
            proxyRequestPathTemplate = null,
            httpsSupport = "http",
            allowedAppIds = emptyList(),
            priorityClass = 1u,
        )
    }

    @Test
    fun `requests bytes successfully on first attempt`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("test payload"))

        val cdnServer = mockCdnServer()
        val bytes = transport.requestBytes(
            server = cdnServer,
            path = "depot/1118200/manifest/12345/5",
            query = null,
            proxyServer = null,
            resolveAuthToken = null,
        )

        assertEquals("test payload", String(bytes))
    }

    @Test
    fun `fetches auth token on 401 response and retries successfully`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody("authorized payload"))

        val cdnServer = mockCdnServer()
        var tokenResolved = false

        val bytes = transport.requestBytes(
            server = cdnServer,
            path = "depot/1118200/manifest/12345/5",
            query = null,
            proxyServer = null,
            resolveAuthToken = { host ->
                tokenResolved = true
                "token=abc1234"
            },
        )

        assertEquals(true, tokenResolved)
        assertEquals("authorized payload", String(bytes))

        val req1 = server.takeRequest()
        val req2 = server.takeRequest()
        assertEquals(null, req1.requestUrl?.query)
        assertEquals("token=abc1234", req2.requestUrl?.query)
    }

    @Test
    fun `preserves cause when resolveAuthToken throws exception`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        val cdnServer = mockCdnServer()
        val causeException = SteamServiceMethodException(
            methodName = "ContentServerDirectory.GetCDNAuthToken#1",
            resultCode = 15,
            steamMessage = "Access Denied",
        )

        try {
            transport.requestBytes(
                server = cdnServer,
                path = "depot/1118200/manifest/12345/5",
                query = null,
                proxyServer = null,
                resolveAuthToken = {
                    throw causeException
                },
            )
            fail("Expected exception was not thrown")
        } catch (e: WorkshopDownloadException) {
            assertNotNull(e.cause)
            val foundInChain = generateSequence<Throwable>(e) { it.cause }.any { it == causeException }
            assertEquals(true, foundInChain)
        }
    }
}
