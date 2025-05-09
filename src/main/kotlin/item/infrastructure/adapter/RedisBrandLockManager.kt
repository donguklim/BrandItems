package com.example.item.infrastructure.adapter

import kotlin.random.Random
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.math.min

import kotlin.properties.Delegates
import kotlin.time.DurationUnit

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisBrandLockManager(host: String, port: Int = 6379, private val numBaseLocks: Int = 50){
    class Latch (
        brandId: Long,
        pubSubConnection: StatefulRedisPubSubConnection<String, String>
    ){
        val mutex = Mutex()
        private val reactive = pubSubConnection.reactive()
        private val channelName = "brand_lock_channel:${brandId}"
        private var hasSubscribed = false

        suspend fun subscribe(){
            if (!hasSubscribed) {
                reactive.subscribe(channelName).awaitFirstOrNull()
                hasSubscribed = true
            }
        }

        suspend fun getMessage(): String {
            if (!hasSubscribed)
                throw Exception("Latch is not listening Redis channel yet")

            return reactive.observeChannels().asFlow().first().message
        }

        suspend fun unsubscribe() {
            reactive.unsubscribe(channelName).awaitFirstOrNull()
        }
    }
    private val redisClient: RedisClient
    val connection: StatefulRedisConnection<String, String>
    private val commands: RedisCoroutinesCommands<String, String>
    private val pubSubConnection: StatefulRedisPubSubConnection<String, String>
    private val pubsubCommands: RedisCoroutinesCommands<String, String>
    private val baseMutexes: List<Mutex> = List(numBaseLocks){Mutex()}
    private val brandIdCountsList: List<MutableMap<Long, Int>> = List(numBaseLocks){mutableMapOf()}
    private val brandIdLatchesList: List<MutableMap<Long, Latch>> = List(numBaseLocks){mutableMapOf()}

    init {
        redisClient =
            RedisClient.create(
                "redis://$host:$port",
            )
        connection = redisClient.connect()
        commands = connection.coroutines()

        pubSubConnection = redisClient.connectPubSub()
        pubsubCommands = pubSubConnection.coroutines()
    }

    fun close() {
        redisClient.shutdown()
    }

    private fun getKey(brandId: Long): String {
        return "brand_lock:${brandId}"
    }

    suspend fun tryLock(
        brandId: Long,
        lockId: Long,
        waitTimeMilliSeconds: Long = 500L,
        leaseTimeMilliSeconds: Long = 10000L,
        spinningTimeMilliSeconds: Long = 100L,
    ): Boolean {
        // Something to consider
        // 1. No timeout check is done for acquiring mutexes,
        // 2. No timeout check is done for redis commands
        val startAt = Clock.System.now()

        val key = getKey(brandId)

        val luaScript = """
            if (redis.call('set', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]) == nil) then
                return redis.call('pttl', KEYS[1]);
            end;
            if(redis.call('get', KEYS[1]) == ARGV[1]) then
                return nil;
            end;
            return redis.call('pttl', KEYS[1]);
            """.trimIndent()

        var lockTtl = commands.eval<Long?>(
            luaScript,
            io.lettuce.core.ScriptOutputType.INTEGER,
            arrayOf(key),
            "$lockId",
            "$leaseTimeMilliSeconds"
        )

        if (lockTtl == null) return true

        var latch: Latch by Delegates.notNull()
        baseMutexes[(brandId % numBaseLocks).toInt()].withLock {
            val brandIdLatches = brandIdLatchesList[(brandId % numBaseLocks).toInt()]
            val brandIdCounts = brandIdCountsList[(brandId % numBaseLocks).toInt()]
            latch = brandIdLatches.getOrPut(brandId) { Latch(brandId, pubSubConnection) }
            brandIdCounts[brandId] = brandIdCounts.getOrPut(brandId) { 0 } + 1
            latch.subscribe()
        }

        var remainingWaitTime = waitTimeMilliSeconds - (Clock.System.now() - startAt).toLong(DurationUnit.MILLISECONDS)

        // Assume Redis pub sub message can be lost, and repeatedly check the message with a period of time
        var messageWaitTime = min(remainingWaitTime, spinningTimeMilliSeconds)

        try {
            lockTtl = commands.eval<Long?>(
                luaScript,
                io.lettuce.core.ScriptOutputType.INTEGER,
                arrayOf(key),
                "$lockId",
                "$leaseTimeMilliSeconds"
            )

            if (lockTtl == null) return true

            while (remainingWaitTime > 0){
                latch.mutex.withLock {
                    try {
                        withTimeout(messageWaitTime) {
                            latch.getMessage()
                        }
                    } catch (_: TimeoutCancellationException) {
                    }

                    lockTtl = commands.eval<Long?>(
                        luaScript,
                        io.lettuce.core.ScriptOutputType.INTEGER,
                        arrayOf(key),
                        "$lockId",
                        "$leaseTimeMilliSeconds"
                    )
                }
                if (lockTtl == null) return true

                remainingWaitTime = waitTimeMilliSeconds - (Clock.System.now() - startAt).toLong(DurationUnit.MILLISECONDS)
                messageWaitTime = min(remainingWaitTime, spinningTimeMilliSeconds)
            }
        } finally {
            baseMutexes[(brandId % numBaseLocks).toInt()].withLock {
                val brandIdLatches = brandIdLatchesList[(brandId % numBaseLocks).toInt()]
                val brandIdCounts = brandIdCountsList[(brandId % numBaseLocks).toInt()]
                brandIdCounts[brandId] = brandIdCounts[brandId]!! - 1
                if (brandIdCounts[brandId] == 0){
                    brandIdCounts.remove(brandId)
                    brandIdLatches.remove(brandId)
                    latch.unsubscribe()
                }
            }
        }

        return false
    }

    suspend fun unlock(brandId:Long, lockId:Long){
        val key = getKey(brandId)

        val luaScript = """
            if (redis.call('get', KEYS[1]) == ARGV[1]) then
                redis.call('del', KEYS[1]);
                return 1;
            end;
            return 0;
            """.trimIndent()

        val res = commands.eval<Long>(
            luaScript,
            io.lettuce.core.ScriptOutputType.INTEGER,
            arrayOf(key),
            "$lockId",
        )

        if (res != null && res > 0){
            pubsubCommands.publish("brand_lock_channel:${brandId}", "released")
        }


    }
    suspend inline fun withLock(
        brandId: Long,
        waitTimeMilliSeconds: Long = 5000L,
        leaseTimeMilliSeconds: Long = 20000L,
        action: () -> Unit
    ): Boolean{
        val randomLockId = Random.nextLong()

        val isLocked = tryLock(brandId, randomLockId, waitTimeMilliSeconds, leaseTimeMilliSeconds)

        if (!isLocked) {
            return false
        }

        action()
        unlock(brandId, randomLockId)

        return true
    }
}
