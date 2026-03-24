package com.vodr.library.settings

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vodr.data.db.VodrDatabase
import com.vodr.data.db.entity.UserSettingsEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsPersistenceTest {

    private lateinit var database: VodrDatabase
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VodrDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        repository = SettingsRepository(database.userSettingsDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun updatedSettingsPersistAndShapeNewGenerationRequests() {
        runBlocking {
        val updatedSettings = UserSettingsEntity(
            voice = "narrator",
            speechRate = 1.25f,
            style = "warm",
        )

        repository.save(updatedSettings)

        val readBack = repository.load()
        assertEquals(updatedSettings, readBack)

        val requestPayload = readBack.toGenerationRequestPayload()
        assertEquals("narrator", requestPayload.voice)
        assertEquals(1.25f, requestPayload.speechRate, 0.0f)
        assertEquals("warm", requestPayload.style)
        }
    }
}
