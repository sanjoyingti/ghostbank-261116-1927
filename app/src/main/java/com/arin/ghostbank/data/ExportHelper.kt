=== ABOUT: Writes CSV files and hands them to the Android share sheet ===
package com.arin.ghostbank.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ExportHelper(private val context: Context) {

    suspend fun writeCsv(name: String, header: String, rows: List<String>): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, name)
        file.writeText(buildString {
            appendLine(header)
            rows.forEach { appendLine(it) }
        })
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun share(uri: Uri, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Writes text to a plain file in cache and returns a shareable uri (used by the raw console). */
    suspend fun dump(name: String, text: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, name)
        file.writeText(text)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
