package com.example.item.infrastructure.adapter

import com.example.item.infrastructure.database.*

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.innerJoin


@Serializable
data class BrandData(val brandName: String, val price: Int)

@Serializable
data class CategoryBrandData(val category: String, val brandName: String, val price: Int)


class ExposedPointRepository {

    suspend fun getCheapestInEachCategory(): Map<Int, BrandData> {
        val categoryGroupedMap: Map<Int, BrandData> = transaction {
            val minPrice = Items.price.min().alias("min_price")
            val mimPricePerCategory = Items.select(
                Items.categoryId,
                minPrice
            ).groupBy(Items.categoryId).alias("category_min_price")

            mimPricePerCategory.join(
                Items,
                JoinType.INNER,
                additionalConstraint = {
                    (mimPricePerCategory[Items.categoryId] eq Items.categoryId) and (minPrice eq Items.price)
                }
            ).select(
                Items.categoryId,
                Items.price,
                Items.brandId,
            ).alias("category_min_brand").join(
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
        }
        return categoryGroupedMap
    }
}