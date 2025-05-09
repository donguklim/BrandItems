package com.example.item.infrastructure.adapter

import com.example.item.infrastructure.database.*

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq


@Serializable
data class BrandData(val id: Long, val brandName: String, val price: Int)

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
            Brands.name,
            Brands.id
        ).associateBy(
            keySelector = { it[Items.categoryId] },
            valueTransform = { BrandData(it[Brands.id].value, it[Brands.name], it[Items.price]) }
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
            Brands.name,
            Brands.id
        ).associateBy(
            keySelector = { it[Items.categoryId] },
            valueTransform = { BrandData(it[Brands.id].value, it[Brands.name], it[Items.price]) }
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

    suspend fun getBrandData(brandId: Long): Map<Int, Int> {
        val minPrice = Items.price.min().alias("min_price")

        val minPricePerCategory = (Items innerJoin Brands).select(
            Items.categoryId,
            minPrice
        ).where(
            Brands.id eq brandId
        ).groupBy(Items.categoryId)

        val categoryGroupedMap: Map<Int, Int> = minPricePerCategory.associateBy(
            keySelector = { it[Items.categoryId] },
            valueTransform = { it[minPrice]!!  }
        )

        return categoryGroupedMap
    }

    suspend fun createItem(brandName: String, categoryId: Int, price: Int): Pair<Boolean, Map<Int, Int>>{

        Brands.insertIgnore { it[Brands.name] = brandName }

        val brandId = Brands.select(
            Brands.id
        ).where(Brands.name eq brandName).single()[Brands.id].value

        val brandData = getBrandData(brandId)
        Items.insertIgnore{
            it[Items.brandId] = brandId
            it[Items.price] = price
            it[Items.categoryId] = categoryId
        }
        brandData[categoryId]?.let {
            if(price >= it){
                return Pair(false,brandData)
            }
        }

        val modifiedData = brandData.toMutableMap()
        modifiedData[categoryId] = price

        val priceSum = modifiedData.values.sum()

        BrandTotalPrice.insertIgnore {
            it[BrandTotalPrice.brandId] = brandId
            it[BrandTotalPrice.price] = priceSum
        }

        BrandTotalPrice.update({BrandTotalPrice.brandId eq brandId}) {
            it[BrandTotalPrice.price] = priceSum
        }

        return Pair(true, modifiedData)
    }

    suspend fun deleteItem(brandName: String, categoryId: Int, price: Int): Pair<Boolean, Map<Int, Int>>{

        val brandId = Brands.select(
            Brands.id
        ).where(Brands.name eq brandName).singleOrNull()?.get(Brands.id)?.value

        if(brandId == null){
            return Pair(false,mapOf())
        }

        Items.deleteWhere{
            (Items.brandId eq brandId) and (Items.price eq price) and (Items.categoryId eq categoryId)
        }
        val brandData = getBrandData(brandId)

        brandData[categoryId]?.let {
            if(price >= it){
                return Pair(false,brandData)
            }
        }

        val priceSum = brandData.values.sum()

        BrandTotalPrice.insertIgnore {
            it[BrandTotalPrice.brandId] = brandId
            it[BrandTotalPrice.price] = priceSum
        }

        BrandTotalPrice.update({BrandTotalPrice.brandId eq brandId}) {
            it[BrandTotalPrice.price] = priceSum
        }

        return Pair(true, brandData)
    }

    suspend fun getCheapestBrand(): Triple<Long, String, Int>{
        (BrandTotalPrice innerJoin Brands).select(
            Brands.id,
            Brands.name,
            BrandTotalPrice.price
        ).orderBy(BrandTotalPrice.price).first().let {
            return Triple(it[Brands.id].value, it[Brands.name], it[BrandTotalPrice.price])
        }

    }

}
