package org.example

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient as JavaHttpClient
import java.net.http.HttpRequest as JavaHttpRequest
import java.net.http.HttpResponse

/**
 * Integration tests demonstrating Ktor Java engine Content-Length: 0 bug on GET requests.
 *
 * Root cause: Ktor's JavaHttpRequest.kt uses `method("GET", BodyPublishers.noBody())` instead of
 * `.GET()`. The JDK HttpClient sends `Content-Length: 0` when using `method()` with any body
 * publisher (even noBody()), but `.GET()` sends no Content-Length header at all.
 * Some servers (e.g. Newcastle Permanent's WAF) reject GET requests with Content-Length: 0.
 *
 * Bug location: ktor-client-java/jvm/src/io/ktor/client/engine/java/JavaHttpRequest.kt line 54
 * Fix: Use `.GET()` / `.HEAD()` / `.DELETE()` for NoContent bodies instead of `.method(name, noBody())`
 *
 * Remarks: These can intermittently fail if a financial institution's CDR API is down.
 */
class NoContentTests {

    private val url = "https://openbank.newcastlepermanent.com.au/cds-au/v1/banking/products?product-category=RESIDENTIAL_MORTGAGES&page-size=50"
    private var json = Json { ignoreUnknownKeys = true }


    @Test
    fun `java http client works directly`() {
        val client = JavaHttpClient.newHttpClient()
        val request = JavaHttpRequest.newBuilder(URI.create(url))
            .header("x-v", "5")
            .header("x-min-v", "3")
            .GET()
            .build()

        println("Java HttpClient request headers: ${request.headers().map()}")

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        println("Response status: ${response.statusCode()}")
        println("Response body (first 500 chars): ${response.body().take(500)}")

        assert(response.statusCode() == 200) { "Expected 200 but got ${response.statusCode()}" }

        val parsed = json.decodeFromString<JsonObject>(response.body())
        assert(parsed.containsKey("data")) { "Response should contain 'data' key" }
    }

    @Test
    fun `java http client with method GET and noBody sends content-length 0`() {
        val client = JavaHttpClient.newHttpClient()
        // This is what Ktor does under the hood: method("GET", noBody()) instead of .GET()
        val request = JavaHttpRequest.newBuilder(URI.create(url))
            .header("x-v", "5")
            .header("x-min-v", "3")
            .method("GET", JavaHttpRequest.BodyPublishers.noBody())
            .build()

        println("Java method(GET, noBody()) request headers: ${request.headers().map()}")

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        println("Response status: ${response.statusCode()}")
        println("Response body (first 500 chars): ${response.body().take(500)}")

        // Documents the JDK bug: method("GET", noBody()) adds Content-Length: 0 on the wire,
        // which this server rejects with 400. When this starts returning 200, the JDK or
        // server behaviour has changed and this test should be updated.
        assert(response.statusCode() == 400) {
            "Expected 400 (known JDK Content-Length:0 bug) but got ${response.statusCode()} - has the JDK behaviour changed?"
        }
    }

    @Test
    fun `ktor http client fails with 400 due to content-length 0 bug`() = runTest {
        val clientConfig = HttpClientConfig()
        val client = clientConfig.clientIgnoreKeys()

        val response = client.get(url) {
            header("x-v", "5")
            header("x-min-v", "3")
        }
        val body = response.bodyAsText()

        println("Ktor response status: ${response.status}")
        println("Ktor response headers: ${response.headers.entries()}")
        println("Ktor response body (first 500 chars): ${body.take(500)}")

        // Documents the Ktor bug: its Java engine uses method("GET", noBody()) internally,
        // causing Content-Length: 0 to be sent. When Ktor fixes this (using .GET() instead),
        // this test will start returning 200 and should be updated to assert OK.
        assert(response.status == HttpStatusCode.BadRequest) {
            "Expected 400 (known Ktor Content-Length:0 bug) but got ${response.status} - has Ktor been fixed?"
        }
    }
}
