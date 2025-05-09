package com.example.item.infrastructure.adapter

import com.example.item.infrastructure.database.*

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.transactions.transaction


@Serializable
data class BrandData(val brandName: String, val price: Int)

@Serializable
data class CategoryBrandData(val category: String, val brandName: String, val price: Int)


class ExposedPointRepository {

    suspend fun getCategoryMinPrice(): Map<Int, BrandData> {
        val minPrice = Items.price.min().alias("min_price")
        val minPricePerCategory = Items.select(
            Items.categoryId,
            minPrice
        ).groupBy(Items.categoryId).alias("category_min_price")

        val categoryGroupedMap: Map<Int, BrandData> = minPricePerCategory.join(
            Items,
            JoinType.INNER,
            additionalConstraint = {
                (minPricePerCategory[Items.categoryId] eq Items.categoryId) and (minPricePerCategory[minPrice] eq Items.price)
            }
        ).join(
            Brands,
            JoinType.INNER,
            onColumn=Items.brandId,
            otherColumn=Brands.id
        ).select(
            Items.categoryId,
            Items.price,
            Brands.name
        ).associateBy(
            keySelector = { it[Items.categoryId] },
            valueTransform = { BrandData(it[Brands.name], it[Items.price]) }
        )

        return categoryGroupedMap
    }

    suspend fun getCategoryMaxPrice(): Map<Int, BrandData> {
        val maxPrice = Items.price.max().alias("max_price")
        val maxPricePerCategory = Items.select(
            Items.categoryId,
            maxPrice
        ).groupBy(Items.categoryId).alias("category_max_price")

        val categoryGroupedMap: Map<Int, BrandData> = maxPricePerCategory.join(
            Items,
            JoinType.INNER,
            additionalConstraint = {
                (maxPricePerCategory[Items.categoryId] eq Items.categoryId) and (maxPricePerCategory[maxPrice] eq Items.price)
            }
        ).join(
            Brands,
            JoinType.INNER,
            onColumn=Items.brandId,
            otherColumn=Brands.id
        ).select(
            Items.categoryId,
            Items.price,
            Brands.name
        ).associateBy(
            keySelector = { it[Items.categoryId] },
            valueTransform = { BrandData(it[Brands.name], it[Items.price]) }
        )

        return categoryGroupedMap
    }

    suspend fun getBrandData(brandName: String): Map<Int, Int> {
        val minPrice = Items.price.min().alias("min_price")

        val minPricePerCategory = (Items innerJoin Brands).select(
            Items.categoryId,
            minPrice
        ).where(
            Brands.name eq brandName
        ).groupBy(Items.categoryId)

        val categoryGroupedMap: Map<Int, Int> = minPricePerCategory.associateBy(
            keySelector = { it[Items.categoryId] },
            valueTransform = { it[minPrice]!!  }
        )

        return categoryGroupedMap
    }
}
