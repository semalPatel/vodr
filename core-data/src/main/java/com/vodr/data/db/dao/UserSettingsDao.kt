package com.vodr.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vodr.data.db.entity.UserSettingsEntity

@Dao
interface UserSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(settings: UserSettingsEntity): Long

    @Query("SELECT * FROM user_settings WHERE settingsId = :settingsId")
    fun getById(settingsId: Long = UserSettingsEntity.DEFAULT_SETTINGS_ID): UserSettingsEntity?
}
