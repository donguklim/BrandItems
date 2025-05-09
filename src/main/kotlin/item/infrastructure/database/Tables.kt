package com.example.item.infrastructure.database

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

enum class ItemCategory(val value: Int) {
    TOP(1),
    OUTERWEAR(2),
    PANTS(3),
    SNEAKERS(4),
    BAG(5),
    Hat(6),
    SOCKS(7),
    ACCESSORY(8),
}

object Brands : LongIdTable("brands") {
    val name = varchar("name", 127).uniqueIndex()
}


object BrandTotalPrice : LongIdTable("brandTotalPrice") {
    val brandId = reference("brand_id", Brands.id, onDelete = ReferenceOption.CASCADE)
    val price = integer("price").index()
}


object Items : LongIdTable("items") {
    val brandId = reference("brand_id", Brands.id, onDelete = ReferenceOption.CASCADE)
    val categoryId = integer("category_id")
    val price = integer("price")

    val categoryPrice =
        index(
            "category_price",
            false,
            *arrayOf(categoryId, price),
        )
    val brandCategoryPrice =
        index(
            "brand_category_price",
            false,
            *arrayOf(brandId, categoryId, price),
        )
}