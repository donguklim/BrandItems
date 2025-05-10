package com.example

import com.example.item.infrastructure.adapter.getIpAddressByHostname
import com.example.item.service.Handler
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import kotlin.text.toInt

@Serializable
data class ItemData(
    val brandName: String,
    val categoryName: String,
    val price: Int,
)


fun Application.configureRouting() {
    val handler = Handler(
        // use the ip address instead of hostname, because Lettuce has connection error when both app and redis are docker containers.
        redisHost = getIpAddressByHostname(System.getenv("CACHE_REDIS_HOST") ?: "localhost"),
        redisPort =(System.getenv("REDIS_PORT") ?: "6379").toInt()
    )
    routing {
        get("/hello") {
            call.respondText("Hello World!")
        }
        get("/min-prices") {

            call.respond(handler.getMinPricePerCategory())
        }
        get("/brand-min-prices") {

            call.respond(handler.getBrandMinPrices())
        }
        get("/min-max-price") {
            val name = call.request.queryParameters["category"]
            if (name == null) {
                call.respond(HttpStatusCode.BadRequest, "Null category name")
                return@get
            }

            val categoryId = handler.getCategoryId(name)

            if (categoryId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Invalid category name. Valid values are: ${handler.getCategoryNames()}"
                )
                return@get
            }

            val ret = handler.getCategoryMinMaxPrices(categoryId)
            call.respond(ret)

        }

        post("/item") {
            val inputData = call.receive<ItemData>()

            val categoryId = handler.getCategoryId(inputData.categoryName)

            if (categoryId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Invalid category name. Valid values are: ${handler.getCategoryNames()}"
                )
                return@post
            }

            if (inputData.price <= 0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "price must be higher than zero"
                )
                return@post
            }

            handler.createItem(
                inputData.brandName,
                categoryId,
                inputData.price
            )

            call.respondText(
                "Received: Brand Name: $inputData",
                status = HttpStatusCode.Created
            )
        }

        delete("/item") {

            val inputData = call.receive<ItemData>()

            val categoryId = handler.getCategoryId(inputData.categoryName)

            if (categoryId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Invalid category name. Valid values are: ${handler.getCategoryNames()}"
                )
                return@delete
            }

            handler.deleteItem(
                inputData.brandName,
                categoryId,
                inputData.price,
            )

            call.respondText(
                "Deleted: Brand Name: $inputData",
                status = HttpStatusCode.OK
            )
        }
    }
}
