package com.example

import com.example.item.infrastructure.database.*

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabases() {
    SQLDatabase.connect()
    SQLDatabase.createTables()

    // insert default data
    val brandNamesOrder = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I")

    val categoryIdsOrder = (1..8).toList()

    val prices = listOf(
        11200,5500,4200,9000,2000,1700,1800,2300,
        10500,5900,3800,9100,2100,2000,2000,2200,
        10000,6200,3300,9200,2200,1900,2200,2100,
        10100,5100,3000,9500,2500,1500,2400,2000,
        10700,5000,3800,9900,2300,1800,2100,2100,
        11200,7200,4000,9300,2100,1600,2300,1900,
        10500,5800,3900,9000,2200,1700,2100,2000,
        10800,6300,3100,9700,2100,1600,2000,2000,
        11400,6700,3200,9500,2400,1700,1700,2400,
    )

    transaction {
        Brands.batchInsert(brandNamesOrder){
            brand -> this[Brands.name] = brand
        }
        val brandIds = Brands.selectAll().orderBy(Brands.name, SortOrder.ASC).associate {
            it[Brands.name] to it[Brands.id].value }

        val brandIdsOrder = brandNamesOrder.map{name -> brandIds[name]!!}

        val items : MutableList<Triple<Long, Int, Int>> = mutableListOf()
        val brandTotalPrice = brandIdsOrder.associate {it to 0}.toMutableMap()
        brandIdsOrder.forEachIndexed { index, brandId ->
            val start = index * 8
            categoryIdsOrder.forEachIndexed { categoryIdx, categoryId ->
                items.add(
                    Triple(brandId, categoryId, prices[start + categoryIdx])
                )
                brandTotalPrice[brandId] = brandTotalPrice[brandId]!! +  prices[start + categoryIdx]
            }
        }

        BrandTotalPrice.batchInsert(brandTotalPrice.entries) { item ->
            this[BrandTotalPrice.brandId] = item.key
            this[BrandTotalPrice.price] = item.value
        }

        Items.batchInsert(items) { item ->
            this[Items.brandId] = item.first
            this[Items.categoryId] = item.second
            this[Items.price] = item.third
        }
    }

}
