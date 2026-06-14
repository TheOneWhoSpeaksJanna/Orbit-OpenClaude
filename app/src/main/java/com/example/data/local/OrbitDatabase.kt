package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.local.dao.OrbitDao
import com.example.data.local.entity.*

@Database(
    entities = [
        ProjectEntity::class, 
        SessionEntity::class, 
        MessageEntity::class, 
        AgentEntity::class, 
        TermuxLogEntity::class
    ], 
    version = 1, 
    exportSchema = false
)
abstract class OrbitDatabase : RoomDatabase() {
    abstract fun dao(): OrbitDao
}
