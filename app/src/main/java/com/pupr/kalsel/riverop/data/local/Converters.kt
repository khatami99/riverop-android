package com.pupr.kalsel.riverop.data.local

import androidx.room.TypeConverter
import com.pupr.kalsel.riverop.models.SyncStatus

class Converters {
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}