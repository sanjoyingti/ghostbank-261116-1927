package com.arin.ghostbank.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** One-shot read of the settings, for screens that need a value outside a flow. */
suspend fun Flow<Settings>.flowSnapshot(): Settings = first()
