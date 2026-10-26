package com.example.ppgworkshop

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.apricityx.workshop.workshop.DownloadEvent
import top.apricityx.workshop.workshop.DownloadState
import top.apricityx.workshop.workshop.DownloadedFileInfo
import top.apricityx.workshop.workshop.WorkshopDownloadEngine
import top.apricityx.workshop.workshop.WorkshopDownloadRequest
import java.io.File
import java.io.IOException
import java.util.Locale

object WorkshopDownloaderFacade {
    interface Listener {
        fun onStatus(message: String)
        fun onCompleted(message: String)
        fun onError(message: String)
    }

    @JvmStatic
    fun start(context: Context, publishedFileId: String, listener: Listener): Job {
        val itemId = publishedFileId.toULongOrNull()
            ?: throw IllegalArgumentException("ID de addon inválido.")
        val appContext = context.applicationContext
        return CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val workDir = File(appContext.cacheDir, "ppg-workshop/$itemId")
            try {
                workDir.deleteRecursively()
                if (!workDir.mkdirs() && !workDir.isDirectory) {
                    throw IOException("Não foi possível preparar a pasta temporária do download.")
                }

                var failureMessage: String? = null
                var completedFiles: List<DownloadedFileInfo>? = null
                val engine = WorkshopDownloadEngine.createDefault()
                val request = WorkshopDownloadRequest(
                    appId = PEOPLE_PLAYGROUND_APP_ID,
                    publishedFileId = itemId,
                    outputDir = workDir,
                )

                withContext(Dispatchers.Main) {
                    listener.onStatus("Conectando anonimamente à Steam…")
                }

                engine.download(request).collect { event ->
                    when (event) {
                        is DownloadEvent.StateChanged -> withContext(Dispatchers.Main) {
                            listener.onStatus(stateMessage(event.state))
                        }
                        is DownloadEvent.Progress -> withContext(Dispatchers.Main) {
                            listener.onStatus(progressMessage(event))
                        }
                        is DownloadEvent.Failed -> failureMessage = event.message
                        is DownloadEvent.Completed -> completedFiles = event.files
                        else -> Unit
                    }
                }

                failureMessage?.let { throw IOException(it) }
                val files = completedFiles
                    ?: throw IOException("A Steam terminou sem confirmar os arquivos baixados.")

                val title = readWorkshopTitle(workDir, itemId)
                val folderName = sanitizeSegment("$title ($itemId)", "addon-$itemId")
                val exportedCount = exportFiles(appContext, workDir, files, folderName)
                if (exportedCount == 0) {
                    throw IOException("O download terminou, mas nenhum arquivo pôde ser exportado.")
                }

                withContext(Dispatchers.Main) {
                    listener.onCompleted(
                        "Download concluído!\\n$exportedCount arquivo(s) salvos em:\\nDownloads/ppg/$folderName"
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val message = error.message?.takeIf { it.isNotBlank() }
                    ?: "Erro inesperado durante o download."
                withContext(Dispatchers.Main) {
                    listener.onError(message)
                }
            }
        }
    }

    private fun stateMessage(state: DownloadState): String = when (state) {
        DownloadState.Idle -> "Preparando download…"
        DownloadState.Resolving -> "Consultando os dados do addon…"
        DownloadState.Connecting -> "Conectando anonimamente à Steam…"
        DownloadState.Downloading -> "Baixando arquivos do addon…"
        DownloadState.Paused -> "Download pausado."
        DownloadState.Success -> "Finalizando arquivos…"
        DownloadState.Failed -> "A Steam não conseguiu concluir o download."
    }

    private fun progressMessage(event: DownloadEvent.Progress): String {
        val total = event.totalBytes
        return if (total != null && total > 0L) {
            "Baixando… ${formatBytes(event.writtenBytes)} / ${formatBytes(total)}"
        } else {
            "Baixando arquivos do addon…"
        }
    }

    private fun formatBytes(value: Long): String {
        if (value < 1024L) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var amount = value.toDouble()
        var unit = -1
        do {
            amount /= 1024.0
            unit++
        } while (amount >= 1024.0 && unit < units.lastIndex)
        return String.format(Locale.getDefault(), "%.1f %s", amount, units[unit])
    }

    private fun readWorkshopTitle(workDir: File, itemId: ULong): String {
        return try {
            val root = JSONObject(File(workDir, "metadata.json").readText())
            val details = root.optJSONObject("response")
                ?.optJSONArray("publishedfiledetails")
                ?.optJSONObject(0)
            details?.optString("title")?.trim()?.takeIf { it.isNotBlank() } ?: "addon-$itemId"
        } catch (_: Exception) {
            "addon-$itemId"
        }
    }

    private fun exportFiles(
        context: Context,
        workDir: File,
        files: List<DownloadedFileInfo>,
        folderName: String,
    ): Int {
        val root = workDir.canonicalFile
        var exported = 0

        for (info in files) {
            val originalRelativePath = info.relativePath.replace("\\", "/")
            if (originalRelativePath.isBlank() ||
                originalRelativePath.startsWith("/") ||
                originalRelativePath.split('/').any { it == ".." } ||
                originalRelativePath == "metadata.json" ||
                originalRelativePath == "download.log" ||
                originalRelativePath.startsWith(".chunks/")
            ) {
                continue
            }

            val source = File(root, originalRelativePath).canonicalFile
            if (!source.isFile || !source.path.startsWith(root.path + File.separator)) continue

            val safeParts = originalRelativePath.split('/')
                .filter { it.isNotBlank() && it != "." }
                .map { sanitizeSegment(it, "file") }
            if (safeParts.isEmpty()) continue

            val parentPath = safeParts.dropLast(1).joinToString("/")
            val relativeDirectory = buildString {
                append(Environment.DIRECTORY_DOWNLOADS)
                append("/ppg/")
                append(folderName)
                if (parentPath.isNotBlank()) {
                    append("/")
                    append(parentPath)
                }
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeParts.last())
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, relativeDirectory)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("O Android não conseguiu criar um arquivo em Downloads/ppg.")

            var published = false
            try {
                source.inputStream().buffered().use { input ->
                    val output = resolver.openOutputStream(uri, "w")
                        ?: throw IOException("Não foi possível gravar um arquivo baixado.")
                    output.buffered().use { stream -> input.copyTo(stream) }
                }
                val ready = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(uri, ready, null, null)
                published = true
                exported++
            } finally {
                if (!published) resolver.delete(uri, null, null)
            }
        }
        return exported
    }

    private fun sanitizeSegment(value: String, fallback: String): String {
        val clean = value
            .replace("\\", "_")
            .replace(Regex("""[/:*?"<>|]"""), "_")
            .filterNot { it.isISOControl() }
            .trim()
            .trim('.')
            .take(100)
        return clean.ifBlank { fallback }
    }

    private const val PEOPLE_PLAYGROUND_APP_ID: UInt = 1118200u
}
