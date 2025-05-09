package com.example

import com.example.item.service.Handler
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import kotlin.text.toInt

fun Application.configureRouting() {
    val handler = Handler(
        redisHost = System.getenv("CACHE_REDIS_HOST") ?: "localhost",
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
            val postParameters = call.receiveParameters()
            val brandName = postParameters["brand_name"]
            val price = postParameters["price"]?.toIntOrNull()
            val categoryName = postParameters["category_name"]

            val nullParameters: MutableList<String> = mutableListOf<String>()

            if(brandName==null) {
                nullParameters.add("brand_name")
            }

            if(price==null) {
                nullParameters.add("price")
            }

            if(categoryName==null) {
                nullParameters.add("category_name")
            }

            if(nullParameters.size > 0){
                call.respond(HttpStatusCode.BadRequest, "Null parameters: $nullParameters")
                return@post
            }

            val categoryId = handler.getCategoryId(categoryName!!)

            if (categoryId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Invalid category name. Valid values are: ${handler.getCategoryNames()}"
                )
                return@post
            }

            handler.createItem(
                brandName!!,
                categoryId,
                price!!
            )

            call.respondText(
                "Received: Brand Name: $brandName, category: $categoryName, price: $price",
                status = HttpStatusCode.OK
            )
        }

        delete("/item") {
            val postParameters = call.receiveParameters()
            val brandName = postParameters["brand_name"]
            val price = postParameters["price"]?.toIntOrNull()
            val categoryName = postParameters["category_name"]

            val nullParameters: MutableList<String> = mutableListOf<String>()

            if(brandName==null) {
                nullParameters.add("brand_name")
            }

            if(price==null) {
                nullParameters.add("price")
            }

            if(categoryName==null) {
                nullParameters.add("category_name")
            }

            if(nullParameters.size > 0){
                call.respond(HttpStatusCode.BadRequest, "Null parameters: $nullParameters")
                return@delete
            }

            val categoryId = handler.getCategoryId(categoryName!!)

            if (categoryId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Invalid category name. Valid values are: ${handler.getCategoryNames()}"
                )
                return@delete
            }

            handler.deleteItem(
                brandName!!,
                categoryId,
                price!!
            )

            call.respondText(
                "Deleted: Brand Name: $brandName, category: $categoryName, price: $price",
                status = HttpStatusCode.OK
            )
        }
    }
}
