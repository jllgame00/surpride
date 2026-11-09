package com.surpride.navhostpoc.map

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TileStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val key = TileKey(15, 16200, 16200)

    @Test fun repeatViewportReadUsesPrivateCacheForAtLeastSevenDaysWithoutNetwork() {
        var requests = 0
        val store = TileStore(temp.root, TileTransport { _, headers ->
            requests++
            assertTrue(headers.getValue("User-Agent").startsWith("SURPRIDE/0.1"))
            assertFalse(headers.containsKey("Cache-Control"))
            assertFalse(headers.containsKey("Pragma"))
            TileResponse(200, byteArrayOf(1, 2, 3), emptyMap())
        })
        assertArrayEquals(byteArrayOf(1, 2, 3), store.load(key, 1000).bytes)
        assertArrayEquals(byteArrayOf(1, 2, 3), store.load(key, 6 * 86400000L).bytes)
        assertEquals(1, requests)
    }

    @Test fun expiredTileIsConditionallyRevalidatedAndRetainedOnServerFailure() {
        var requests = 0
        val store = TileStore(temp.root, TileTransport { _, headers ->
            when (++requests) {
                1 -> TileResponse(200, byteArrayOf(4), mapOf("ETag" to "v1"))
                2 -> { assertEquals("v1", headers["If-None-Match"]); TileResponse(304, null, emptyMap()) }
                else -> throw java.io.IOException("unavailable")
            }
        })
        store.load(key, 1000)
        assertFalse(store.load(key, 8 * 86400000L).stale)
        val fallback = store.load(key, 16 * 86400000L)
        assertTrue(fallback.stale)
        assertArrayEquals(byteArrayOf(4), fallback.bytes)
        assertNotNull(fallback.failure)
    }

    @Test fun missingTileFailureIsExplicitAndNeverReturnsFabricatedMapData() {
        val store = TileStore(temp.root, TileTransport { _, _ -> TileResponse(503, null, emptyMap()) })
        val result = store.load(key, 0)
        assertNull(result.bytes)
        assertNotNull(result.failure)
    }

    @Test fun longServerFreshnessIsHonoredAndNoFreshTileIsEvicted() {
        var requests = 0
        val store = TileStore(temp.root, TileTransport { _, _ ->
            requests++; TileResponse(200, byteArrayOf(9), mapOf("Cache-Control" to "max-age=2592000"))
        })
        store.load(key, 1000)
        store.load(TileKey(15, 16201, 16200), 1000)
        assertArrayEquals(byteArrayOf(9), store.load(key, 20 * 86400000L).bytes)
        assertEquals(2, requests)
    }
}
