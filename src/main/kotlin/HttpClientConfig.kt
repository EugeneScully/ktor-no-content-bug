package org.example

import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class HttpClientConfig {
    fun clientIgnoreKeys(): HttpClient {
        return HttpClient(Java) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }
    }
}

