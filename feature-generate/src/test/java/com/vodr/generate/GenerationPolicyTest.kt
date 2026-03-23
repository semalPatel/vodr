package com.vodr.generate

import org.junit.Assert.assertEquals
import org.junit.Test

class GenerationPolicyTest {

    private val policy = GenerationPolicy()

    @Test
    fun qualityModeSchedulesAllChunksInOneWave() {
        val schedule = policy.schedule(
            mode = GenerationMode.QUALITY,
            chunkCount = 5,
        )

        assertEquals(listOf(listOf(0, 1, 2, 3, 4)), schedule)
    }

    @Test
    fun balancedModeFrontLoadsTwoChunksBeforeContinuingSequentially() {
        val schedule = policy.schedule(
            mode = GenerationMode.BALANCED,
            chunkCount = 5,
        )

        assertEquals(
            listOf(
                listOf(0, 1),
                listOf(2, 3, 4),
            ),
            schedule,
        )
    }

    @Test
    fun fastStartModeFrontLoadsTheFirstChunkBeforeContinuingSequentially() {
        val schedule = policy.schedule(
            mode = GenerationMode.FAST_START,
            chunkCount = 5,
        )

        assertEquals(
            listOf(
                listOf(0),
                listOf(1, 2, 3, 4),
            ),
            schedule,
        )
    }
}
