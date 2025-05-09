package com.example.item.infrastructure.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object SQLDatabase {
    fun connect() {
        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
            user = "root",
            driver = "org.h2.Driver",
            password = "",
        )
    }

    fun createTables() {
        transaction {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(Brands, BrandTotalPrice, Items)
        }
    }
}