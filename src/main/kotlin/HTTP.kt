package com.example

import com.ucasoft.ktor.simpleCache.SimpleCache
import com.ucasoft.ktor.simpleCache.cacheOutput
import com.ucasoft.ktor.simpleRedisCache.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.exposed.sql.*

fun Application.configureHTTP() {


    install(SimpleCache) {
        redisCache {
            invalidateAt = 10.seconds
            host = System.getenv("CACHE_REDIS_HOST") ?: "localhost"
            port = (System.getenv("REDIS_PORT") ?: "6379").toInt()
        }
    }
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowNonSimpleContentTypes = true
        anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
    }
    install(ContentNegotiation) {
        json()  // Enable JSON serialization
    }
    routing {
        swaggerUI(path = "openapi")
    }
    routing {
        cacheOutput(2.seconds) {
            get("/short") {
                call.respond(kotlin.random.Random.nextInt().toString())
            }
        }
        cacheOutput {
            get("/default") {
                call.respond(Random.nextInt().toString())
            }
        }
    }
}
