package com.example.item.service

import com.example.item.infrastructure.adapter.BrandData
import com.example.item.infrastructure.adapter.ExposedPointRepository
import com.example.item.infrastructure.adapter.RedisBrandLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction


@Serializable
data class BrandPriceData(
    val brand: String,
    val price: Int,

)

@Serializable
data class CategoryPrices(
    val categoryPrices: Map<String, BrandPriceData>,
    val totalPrice: Int,
)

@Serializable
data class CategoryPriceData(
    val category: String,
    val price: Int,
)


@Serializable
data class BrandCategoryPrices(
    val brand: String,
    val categories: List<CategoryPriceData>,
    val totalPrice: Int,
)

@Serializable
data class CategoryMinMaxPriceData(
    val category: String,
    val minPrice: BrandPriceData,
    val maxPrice: BrandPriceData,
)


class Handler(redisHost: String, redisPort: Int) {
    private val repo = ExposedPointRepository()
    private val redisLock = RedisBrandLockManager(
        host=redisHost,
        port=redisPort,
    )

    private val categoryNames = mapOf<Int, String>(
        1 to "상의",
        2 to "아우터",
        3 to "바지",
        4 to "스니커즈",
        5 to "가방",
        6 to "모자",
        7 to "양말",
        8 to "액세서리",
    )
    private val categoryIds = categoryNames.entries.associate { it.value to it.key }

    private fun getCategoryName(categoryId: Int): String? {
        return categoryNames[categoryId]
    }

    fun getCategoryId(category: String): Int? {
        return categoryIds[category]
    }

    suspend fun getMinPricePerCategory(): CategoryPrices{
        val minPrices = newSuspendedTransaction(Dispatchers.IO) {
            repo.getCategoryMinPrice()

        }
        val categoryData: Map<String, BrandPriceData> = minPrices.filter {
            getCategoryName(it.key) != null
        }.entries.associate {
            getCategoryName(it.key)!! to BrandPriceData(it.value.brandName, it.value.price)
        }

        return CategoryPrices(
            categoryData,
            categoryData.values.fold(0) { acc, brandData -> acc + brandData.price}
        )
    }

    suspend fun getBrandMinPrices() : BrandCategoryPrices {
        return newSuspendedTransaction(Dispatchers.IO) {
            val (brandId, brandName, totalPrice) = repo.getCheapestBrand()
            val brandData = repo.getBrandData(brandId)

            val categories = brandData.entries.filter {
                getCategoryName(it.key) != null
            }.map{
                CategoryPriceData(getCategoryName(it.key)!!,  it.value)
            }
            BrandCategoryPrices(
                brandName,
                categories,
                categories.fold(0) { acc, categoryData -> acc + categoryData.price}
            )
        }
    }

    suspend fun getCategoryMinMaxPrices(categoryId: Int) {
        newSuspendedTransaction(Dispatchers.IO) {
            val minBrand = repo.getCategoryMinPrice()[categoryId]
            val maxBrand = repo.getCategoryMaxPrice()[categoryId]

            CategoryMinMaxPriceData(
                getCategoryName(categoryId)!!,
                BrandPriceData(minBrand?.brandName ?: "", minBrand?.price ?: 0),
                BrandPriceData(maxBrand?.brandName ?: "", maxBrand?.price ?: 0),
            )
        }
    }

    suspend fun createItem(brandName: String, categoryId: Int, price: Int){
        val brandId = newSuspendedTransaction(Dispatchers.IO) {
            repo.getBrandId(brandName)
        }

        val isLocked = redisLock.withLock(brandId) {
            newSuspendedTransaction(Dispatchers.IO) {
                repo.createItem(brandId, categoryId, price)
            }
        }
    }


    suspend fun deleteItem(brandName: String, categoryId: Int, price: Int){
        val brandId = newSuspendedTransaction(Dispatchers.IO) {
            repo.getExistingBrandId(brandName)
        }

        if(brandId == null){
            return
        }
        val isLocked = redisLock.withLock(brandId) {
            newSuspendedTransaction(Dispatchers.IO) {
                repo.deleteItem(brandId, categoryId, price)
            }
        }
    }
}