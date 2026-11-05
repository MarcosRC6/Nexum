package com.example.testario.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.testario.data.local.TestarioLocalStore
import com.example.testario.data.model.DuplicateTopicAction
import com.example.testario.data.model.ImportPreview
import com.example.testario.data.model.Topic
import com.example.testario.domain.ExportManager
import com.example.testario.domain.ImportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class ImportExportViewModel(application: Application) : AndroidViewModel(application) {
    private val localStore = TestarioLocalStore.get(application)
    private val importManager = ImportManager(application, localStore)
    private val exportManager = ExportManager(application, localStore)

    suspend fun exportCompleteZip(outputStream: OutputStream, topicIds: List<String>? = null) = withContext(Dispatchers.IO) {
        exportManager.exportCompleteZip(outputStream, topicIds)
    }

    suspend fun exportBackupZip(outputStream: OutputStream) = withContext(Dispatchers.IO) {
        exportManager.exportBackupZip(outputStream)
    }

    suspend fun exportSimpleJson(topicIds: List<String>? = null): String = withContext(Dispatchers.IO) {
        exportManager.exportSimpleJson(topicIds)
    }

    suspend fun extractPayload(inputStream: InputStream, fileName: String?): String = withContext(Dispatchers.IO) {
        importManager.extractPayload(inputStream, fileName)
    }

    suspend fun restoreBackup(rawJson: String) = withContext(Dispatchers.IO) {
        importManager.restoreBackup(rawJson)
    }

    suspend fun previewImport(rawText: String, fileName: String? = null): ImportPreview = withContext(Dispatchers.IO) {
        importManager.previewImport(rawText, fileName)
    }

    suspend fun getTopicsFromRawText(rawText: String, fileName: String? = null): List<Topic> = withContext(Dispatchers.IO) {
        importManager.getTopicsFromRawText(rawText, fileName)
    }

    suspend fun importSingleTopicProcessed(topic: Topic, action: DuplicateTopicAction, customName: String? = null) = withContext(Dispatchers.IO) {
        importManager.importSingleTopicProcessed(topic, action, customName)
    }

    // NUEVO: Función para inyectar el archivo del colega en el motor de sincronización
    suspend fun syncWithExternalFile(inputStream: InputStream, fileName: String?): Int = withContext(Dispatchers.IO) {
        val rawJson = importManager.extractPayload(inputStream, fileName)
        importManager.syncWithExternalDatabase(rawJson)
    }
}