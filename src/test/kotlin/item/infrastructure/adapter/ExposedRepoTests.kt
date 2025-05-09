package com.example.item.infrastructure.adapter

import com.example.item.infrastructure.database.*

import kotlin.test.Test
import kotlin.test.assertEquals

import com.example.item.infrastructure.database.SQLDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.assertFalse
import kotlin.test.assertTrue


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
            BrandTotalPrice.deleteAll()
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
            BrandTotalPrice.deleteAll()
            Brands.deleteAll()
        }
    }

    @Test
    fun testItemUpdateOnNewBrand() {

        SQLDatabase.connect()
        SQLDatabase.createTables()
        val brandName = "asdf"
        val price = 2323
        val categoryId = ItemCategory.PANTS.value
        val repo = ExposedPointRepository()


        runBlocking {
            newSuspendedTransaction(Dispatchers.IO) {
                val (isTotalUpdated, brandData) = repo.updateItem(
                    brandName,
                    categoryId,
                    price,
                )

                assertEquals(price, brandData[categoryId])
            }
        }
        transaction {
            val totalPrice = (BrandTotalPrice innerJoin  Brands).select(
                BrandTotalPrice.price
            ).where(
                Brands.name eq brandName
            ).first()[BrandTotalPrice.price]

            assertEquals(price, totalPrice)
        }

        transaction {
            Items.deleteAll()
            BrandTotalPrice.deleteAll()
            Brands.deleteAll()

        }
    }

    @Test
    fun testItemUpdateOnBrandTotal() {

        SQLDatabase.connect()
        SQLDatabase.createTables()
        val brandName = "dwasdf"

        val minPrices = ItemCategory.entries.associate {
            it.value to (30..190).random()
        }

        val expectedTotalPrice = minPrices.values.sum()

        transaction {
            Brands.insert { it[Brands.name] = brandName }
            val brandId = Brands.select(
                Brands.id
            ).where(Brands.name eq brandName).single()[Brands.id].value

            Items.batchInsert(minPrices.entries) {
                this[Items.categoryId] = it.key
                this[Items.brandId] = brandId
                this[Items.price] = it.value
            }

            BrandTotalPrice.insert {
                it[BrandTotalPrice.brandId] = brandId
                it[BrandTotalPrice.price] = expectedTotalPrice
            }
        }

        val repo = ExposedPointRepository()
        val categoryId = ItemCategory.OUTERWEAR.value
        var isTotalUpdated = false
        var brandData: Map<Int, Int> = mapOf()

        runBlocking {
            newSuspendedTransaction(Dispatchers.IO) {
                val ret = repo.updateItem(
                    brandName,
                    categoryId,
                    (minPrices[categoryId] ?: 0) + 100,
                )

                isTotalUpdated = ret.first
                brandData = ret.second
            }
        }

        assertFalse(isTotalUpdated)
        assertEquals(brandData.values.sum(), expectedTotalPrice)

        val priceChange =  5

        runBlocking {
            newSuspendedTransaction(Dispatchers.IO) {
                val ret = repo.updateItem(
                    brandName,
                    categoryId,
                    (minPrices[categoryId] ?: 0) - 5,
                )

                isTotalUpdated = ret.first
                brandData = ret.second
            }
        }

        assertTrue(isTotalUpdated)
        assertEquals(brandData.values.sum(), expectedTotalPrice - priceChange)

        var totalPrice = 0
        transaction {
            totalPrice = (BrandTotalPrice innerJoin  Brands).select(
                BrandTotalPrice.price
            ).where(
                Brands.name eq brandName
            ).first()[BrandTotalPrice.price]


        }

        assertEquals(expectedTotalPrice - priceChange, totalPrice)
        transaction {
            Items.deleteAll()
            BrandTotalPrice.deleteAll()
            Brands.deleteAll()

        }
    }
}
