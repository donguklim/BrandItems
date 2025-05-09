package com.example.item.infrastructure.adapter

import com.example.item.infrastructure.database.Brands
import com.example.item.infrastructure.database.ItemCategory
import com.example.item.infrastructure.database.Items

import kotlin.test.Test
import kotlin.test.assertEquals

import com.example.item.infrastructure.database.SQLDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction


class ExposedRepoTests {

    @Test
    fun testCategoryMin() {
        SQLDatabase.connect()
        SQLDatabase.createTables()

        val brands: List<String> = List(10) { "brand-$it" }


        var brandIds = mapOf<Long, String>()

        transaction {
            Brands.batchInsert(brands) { brand -> this[Brands.name] = brand }
            brandIds = Brands.select(Brands.id, Brands.name).associate { it[Brands.id].value to it[Brands.name] }
        }


        val minPrices = ItemCategory.entries.associate {
            it.value to (1..19).random()
        }

        val minPriceBrands = ItemCategory.entries.associate {
            it.value to brandIds.keys.random()
        }
        val nonMinPrices = minPrices.keys.associate {
            it to minPrices[it]!! + (1..100).random()
        }

        transaction {
            Items.batchInsert(nonMinPrices.entries) {
                this[Items.categoryId] = it.key
                this[Items.brandId] = brandIds.keys.random()
                this[Items.price] = it.value
            }

            Items.batchInsert(minPrices.entries) {
                this[Items.categoryId] = it.key
                this[Items.brandId] = minPriceBrands[it.key]!!
                this[Items.price] = it.value
            }
        }

        val repo = ExposedPointRepository()

        runBlocking {
            newSuspendedTransaction(Dispatchers.IO) {
                repo.getCategoryMinPrice().forEach {
                    assertEquals(minPrices[it.key], it.value.price)
                    assertEquals(brandIds[minPriceBrands[it.key]], it.value.brandName)
                }
            }

        }

        transaction {
            Items.deleteAll()
            Brands.deleteAll()
        }
    }

    @Test
    fun testCategoryMax() {
        SQLDatabase.connect()
        SQLDatabase.createTables()

        val brands: List<String> = List(10) { "brand-$it" }


        var brandIds = mapOf<Long, String>()

        transaction {
            Brands.batchInsert(brands) { brand -> this[Brands.name] = brand }
            brandIds = Brands.select(Brands.id, Brands.name).associate { it[Brands.id].value to it[Brands.name] }
        }

        val maxPrices = ItemCategory.entries.associate {
            it.value to (100..1923).random()
        }

        val maxPriceBrands = ItemCategory.entries.associate {
            it.value to brandIds.keys.random()
        }
        val nonMaxPrices = ItemCategory.entries.associate {
            it.value to (10..55).random()
        }

        transaction {
            Items.batchInsert(nonMaxPrices.entries) {
                this[Items.categoryId] = it.key
                this[Items.brandId] = brandIds.keys.random()
                this[Items.price] = it.value
            }

            Items.batchInsert(maxPrices.entries) {
                this[Items.categoryId] = it.key
                this[Items.brandId] = maxPriceBrands[it.key]!!
                this[Items.price] = it.value
            }
        }

        val repo = ExposedPointRepository()

        runBlocking {
            newSuspendedTransaction(Dispatchers.IO) {
                repo.getCategoryMaxPrice().forEach {
                    assertEquals(maxPrices[it.key], it.value.price)
                    assertEquals(brandIds[maxPriceBrands[it.key]], it.value.brandName)
                }
            }

        }

        transaction {
            Items.deleteAll()
            Brands.deleteAll()
        }
    }
}
