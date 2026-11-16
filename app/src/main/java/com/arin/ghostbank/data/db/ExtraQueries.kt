package com.arin.ghostbank.data.db

import androidx.room.Query

/** One-shot read used when an export must not wait for a Flow to settle. */
interface LegOnceQueries {
    @Query("SELECT * FROM harvest_legs WHERE runId = :runId ORDER BY ts ASC")
    suspend fun byRunOnce(runId: Long): List<HarvestLegEntity>
}
