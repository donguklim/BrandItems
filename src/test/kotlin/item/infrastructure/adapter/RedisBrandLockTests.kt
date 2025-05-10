package com.example.item.infrastructure.adapter

import kotlin.test.*

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection

class RedisBrandLockTests {
    private val redisHost = getIpAddressByHostname(
        System.getenv("CACHE_REDIS_HOST") ?: "localhost"
    )
    private val redisPort = (System.getenv("REDIS_PORT") ?: "6379").toInt()

    private fun getConnection(): StatefulRedisConnection<String, String> {
        return RedisClient.create(
            "redis://$redisHost:$redisPort",
        ).connect()
    }

    private fun getManager(): RedisBrandLockManager {
        return RedisBrandLockManager(redisHost, redisPort)
    }

    @Test
    fun testLocking() {
        val brandId = (1..300L).random()

        val manager = getManager()

        val lockingId = 1232L
        var isLocked = false
        var isAnotherLocked = false
        val connection = getConnection()

        assertNull(connection.sync().get("brand_lock:${brandId}"))

        runBlocking {
            isLocked = manager.tryLock(brandId, lockingId, 100, 10021)
            isAnotherLocked = manager.tryLock(brandId, lockingId + 1, 100, 100000)
        }
        assertTrue(isLocked)
        assertFalse(isAnotherLocked)

        assertEquals(lockingId, manager.connection.sync().get("brand_lock:${brandId}")?.toLong() ?: -1L)

        runBlocking {
            manager.unlock(brandId, lockingId)
        }
        assertNull(connection.sync().get("brand_lock:${brandId}"))

        val command = connection.sync()
        command.flushdb()
        connection.close()
    }

    @Test
    fun testSequentialLockRelease() {
        val brandId = (1..300L).random()

        val manager = getManager()

        val lockingId = 1232L
        var isLocked = false
        var isAnotherLocked = false
        val connection = getConnection()

        assertNull(connection.sync().get("brand_lock:${brandId}"))

        runBlocking {
            isLocked = manager.tryLock(brandId, lockingId, 100, 10021)
            manager.unlock(brandId, lockingId)
            isAnotherLocked = manager.tryLock(brandId, lockingId + 1, 100, 100000)
            manager.unlock(brandId, lockingId + 1)
        }
        assertTrue(isLocked)
        assertTrue(isAnotherLocked)

        val command = connection.sync()
        command.flushdb()
        connection.close()
    }

    @Test
    fun testSimultaneousAccesses() {
        val brandId = (1..300L).random()

        val manager = getManager()
        val numCoroutines = 10
        var counter = 0
        val lockResults = MutableList(numCoroutines) { false }
        runBlocking {

            val jobs = List(numCoroutines) { index ->
                launch {
                    lockResults[index] = manager.tryLock(brandId, index.toLong(), 10000, 10021)
                    val counterBefore = counter
                    delay(10)
                    counter = counterBefore + 1
                    manager.unlock(brandId, index.toLong())
                }
            }
            jobs.forEach { it.join() }
        }

        assertEquals(numCoroutines, lockResults.count { it })

        assertEquals(counter, numCoroutines)

        val connection = getConnection()
        val command = connection.sync()
        command.flushdb()
        connection.close()
    }
}