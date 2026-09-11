package dev.auralis.desktop.playback

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HttpStreamingResilienceTest {

    private var server: HttpServer? = null
    private var port: Int = 0

    @BeforeTest
    fun setUp() {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        port = s.address.port

        // Infinite loop: /loop -> /loop
        s.createContext("/loop") { exchange ->
            exchange.responseHeaders.set("Location", "http://127.0.0.1:$port/loop")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }

        // 2-hop redirect: /start -> /middle -> /destination
        s.createContext("/start") { exchange ->
            exchange.responseHeaders.set("Location", "http://127.0.0.1:$port/middle")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }

        s.createContext("/middle") { exchange ->
            exchange.responseHeaders.set("Location", "http://127.0.0.1:$port/destination")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }

        s.createContext("/destination") { exchange ->
            val body = "STREAM_OK".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.write(body)
            exchange.close()
        }

        // Unsupported scheme redirect: /bad-redirect -> file:///C:/secret.txt
        s.createContext("/bad-redirect") { exchange ->
            exchange.responseHeaders.set("Location", "file:///C:/secret.txt")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }

        s.start()
        server = s
    }

    @AfterTest
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun testSchemeSafetyRejectsNonHttpSchemes() {
        val nonHttpUrls = listOf(
            "file:///C:/Windows/system32/cmd.exe",
            "ftp://ftp.example.com/audio.mp3",
            "jar:file:/archive.zip!/audio.mp3",
            "javascript:alert(1)",
        )

        for (url in nonHttpUrls) {
            val ex = assertFailsWith<IllegalArgumentException> {
                DesktopPlaybackEngine.openHttpConnectionWithRedirects(url)
            }
            assertTrue(ex.message?.contains("Unsupported streaming protocol") == true)
        }
    }

    @Test
    fun testRedirectLoopBoundingThrowsAfterMaxRedirects() {
        val loopUrl = "http://127.0.0.1:$port/loop"
        val ex = assertFailsWith<IOException> {
            DesktopPlaybackEngine.openHttpConnectionWithRedirects(loopUrl)
        }
        assertTrue(
            ex.message?.contains("Too many HTTP redirects") == true,
            "Expected redirect ceiling error, got: ${ex.message}",
        )
    }

    @Test
    fun testSuccessfulRedirectHops() {
        val startUrl = "http://127.0.0.1:$port/start"
        val (conn, effectiveUrl) = DesktopPlaybackEngine.openHttpConnectionWithRedirects(startUrl)
        try {
            assertEquals(200, conn.responseCode)
            assertEquals("http://127.0.0.1:$port/destination", effectiveUrl)
        } finally {
            conn.disconnect()
        }
    }

    @Test
    fun testRedirectToUnsupportedSchemeIsRejected() {
        val badRedirectUrl = "http://127.0.0.1:$port/bad-redirect"
        val ex = assertFailsWith<IllegalArgumentException> {
            DesktopPlaybackEngine.openHttpConnectionWithRedirects(badRedirectUrl)
        }
        assertTrue(ex.message?.contains("Unsupported streaming protocol") == true)
    }
}
