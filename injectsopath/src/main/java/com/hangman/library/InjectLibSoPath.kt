package com.hangman.library

import android.app.ActivityManager
import android.content.Context
import android.text.TextUtils
import org.apache.commons.compress.archivers.ArchiveException
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.utils.IOUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.Executors

class InjectLibSoPath(private val context: Context, private val algorithm: String, private val printLog: Boolean) {

    var tarDecompression: Long = 0
    var soDecompression: Long = 0
    var totalCost: Long = 0
    private val threadPool = Executors.newSingleThreadExecutor()

    companion object {
        const val SO_DECOMPRESSION = "so_decompressed"
        const val SO_COMPRESSED = "so_compressed"
        const val TAR = "tar"
        const val TAG = "InjectLibSoPath"
    }

    private lateinit var spInterface: SpInterface
    private var decompressionCallback: DecompressionCallback? = null
    private var logInterface: LogInterface? = null

    fun inject(spInterface: SpInterface, logInterface: LogInterface?, decompressionCallback: DecompressionCallback?) {
        val time = System.currentTimeMillis()
        this.spInterface = spInterface
        this.logInterface = logInterface
        this.decompressionCallback = decompressionCallback
        threadPool.execute {
            shouldDecompression()
        }
        val cost = System.currentTimeMillis() - time
        logInterface?.logV(TAG, "InjectLibSoPath after shouldDecompression cost $cost")
        if (isMainProcess()) {
            injectExtraSoFilePath()
        }
        totalCost = System.currentTimeMillis() - time
        logInterface?.logV(TAG, "InjectLibSoPath after shouldDecompression totalCost $totalCost")
    }

    private fun shouldDecompression() {
        val pathList = context.applicationContext.assets.list(SO_COMPRESSED)
        pathList?.forEach { pathName ->
            if (printLog) {
                logInterface?.logV(TAG, "pathName $pathName")
            }
            val namePieces = pathName.split("-")
            when (namePieces.contains(TAR)) {
                true -> {
                    val fileName = namePieces[0]
                    val originMD5 = namePieces[1]
                    val value = spInterface.getString(fileName)
                    if (!TextUtils.equals(value, originMD5)) {
                        if (printLog) {
                            logInterface?.logV(TAG, "shouldDecompression tarDecompression $pathName")
                        }
                        tarDecompression(pathName)
                    }
                }
                false -> {
                    val fileName = namePieces[0]
                    val originMD5 = namePieces[1]
                    val value = spInterface.getString(fileName)
                    if (!TextUtils.equals(value, originMD5)) {
                        if (printLog) {
                            logInterface?.logV(TAG, "shouldDecompression soDecompression $pathName")
                        }
                        soDecompression(pathName)
                    }
                }
            }
        }
        decompressionCallback?.decompression(true)
    }

    private fun injectExtraSoFilePath() {
        val fileDecompressedDir = File(context.applicationContext.filesDir, SO_DECOMPRESSION)
        val classLoader = context.applicationContext.classLoader
        if (printLog) {
            logInterface?.logV(TAG, "injectExtraSoFilePath classLoader $classLoader")
        }
        try {
            val dexPathListField = NativeLibraryPathIncrementUtils.findField(classLoader, "pathList")
            dexPathListField.isAccessible = true
            val dexPathListInstance = dexPathListField.get(classLoader)
            NativeLibraryPathIncrementUtils.expandFieldArray(dexPathListInstance, "nativeLibraryPathElements",
                    arrayOf(NativeLibraryPathIncrementUtils.makeNativeLibraryElement(fileDecompressedDir.absoluteFile)))
            NativeLibraryPathIncrementUtils.expandFieldList(dexPathListInstance, "nativeLibraryDirectories", fileDecompressedDir)
            dexPathListField.set(classLoader, dexPathListInstance)
        } catch (e: NoSuchFieldException) {
            logInterface?.logV(TAG, "NoSuchFieldException $e")
        } catch (e: IllegalAccessException) {
            logInterface?.logV(TAG, "IllegalAccessException $e")
        } catch (e: IOException) {
            logInterface?.logV(TAG, "IOException $e")
        } catch (e: java.lang.Exception) {
            logInterface?.logV(TAG, "Exception $e")
        }
        if (printLog) {
            logInterface?.logV(TAG, "injectExtraSoFilePath classLoader $classLoader")
        }

    }

    private fun tarDecompression(pathName: String) {
        try {
            // decompression
            var time = System.currentTimeMillis()
            val file = fileDecompression(pathName)
                    ?: throw IllegalStateException("tar decompression failed $pathName")
            var cost = System.currentTimeMillis() - time
            if (printLog) {
                logInterface?.logV(TAG, "tarDecompression1 $pathName cost : $cost")
            }
            soDecompression += cost

            // tar decompression
            time = System.currentTimeMillis()
            tarDecompression(file.absolutePath, file.parent)
            cost = System.currentTimeMillis() - time
            tarDecompression += cost
            if (printLog) {
                logInterface?.logV(TAG, "tarDecompression2 $pathName cost : $cost")
            }

            val splits = pathName.split("-")
            val fileName = splits[0]
            val originMD5 = splits[1]
            spInterface.saveString(fileName, originMD5)
        } catch (e: Exception) {
            decompressionCallback?.decompression(false)
        }

    }

    private fun soDecompression(pathName: String) {
        val time = System.currentTimeMillis()
        val file = fileDecompression(pathName)
                ?: throw IllegalStateException("soDecompression failed $pathName")
        val cost = System.currentTimeMillis() - time
        if (printLog) {
            logInterface?.logV(TAG, "soDecompression $pathName fileName ${file.name}  cost $cost")
        }
        soDecompression += cost
        val splits = pathName.split("-")
        val fileName = splits[0]
        val originMD5 = splits[1]
        spInterface.saveString(fileName, originMD5)
    }

    private fun fileDecompression(pathName: String): File? {
        var file: File? = null
        try {
            val inputStream = context.applicationContext.assets.open(SO_COMPRESSED + File.separatorChar + pathName)
            val fileDecompressedDir = File(context.applicationContext.filesDir, SO_DECOMPRESSION)
            if (!fileDecompressedDir.exists()) {
                fileDecompressedDir.mkdir()
            }
            val fileName = pathName.split("-")[0]
            val decompressedFile = File(fileDecompressedDir, fileName)
            if (decompressedFile.exists()) {
                decompressedFile.delete()
            }
            val outputStream = decompressedFile.outputStream()
            val compressInputStream = inputStream.buffered()
            val compressorInputStream = CompressorStreamFactory().createCompressorInputStream(algorithm, compressInputStream)
            IOUtils.copy(compressorInputStream, outputStream)
            compressorInputStream.close()
            compressInputStream.close()
            inputStream.close()
            outputStream.close()
            file = decompressedFile
        } catch (e: Exception) {
            if (printLog) {
                logInterface?.logE(TAG, "fileDecompression error: $e")
            }
            decompressionCallback?.decompression(false)
        }
        return file
    }

    private fun tarDecompression(tarPath: String, unTarPath: String) {
        val time = System.currentTimeMillis()
        val tarFile = File(tarPath)
        try {
            val bufferedInputStream = tarFile.inputStream().buffered()
            val archiveInputStream = ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.TAR, bufferedInputStream)
            while (true) {
                val entry = archiveInputStream.nextEntry ?: break
                val tarArchiveEntry = entry as TarArchiveEntry
                if (tarArchiveEntry.isDirectory) {
                    if (printLog) {
                        logInterface?.logV(TAG, "tarArchiveEntry directory ${tarArchiveEntry.name}")
                    }
                    File(unTarPath, tarArchiveEntry.name).mkdir()
                } else {
                    if (printLog) {
                        logInterface?.logV(TAG, "tarArchiveEntry file ${tarArchiveEntry.name}")
                    }
                    val file = File(unTarPath, tarArchiveEntry.name)
                    val out = file.outputStream()
                    IOUtils.copy(archiveInputStream, out)
                    out.close()
                }
            }
            archiveInputStream.close()
            bufferedInputStream.close()
            if (printLog) {
                logInterface?.logE(TAG, "tarDecompression $tarPath  $unTarPath cost: ${(System.currentTimeMillis() - time)}")
            }
        } catch (e: FileNotFoundException) {
            if (printLog) {
                logInterface?.logE(TAG, "FileNotFoundException $e")
            }
        } catch (e: ArchiveException) {
            if (printLog) {
                logInterface?.logE(TAG, "ArchiveException $e")
            }
        } catch (e: IOException) {
            if (printLog) {
                logInterface?.logE(TAG, "IOException $e")
            }
        }
    }

    @SuppressWarnings("all")
    fun isMainProcess(): Boolean {
        val packageName = context.applicationContext.packageName
        return packageName == getCurrentProcessName()
    }

    private fun getCurrentProcessName(): String? {
        val myPid = android.os.Process.myPid()
        val activityManager = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val taskList = activityManager.runningAppProcesses
        var processName: String? = null
        taskList.forEach { info ->
            if (info.pid == myPid) {
                processName = info.processName
                if (printLog) {
                    logInterface?.logV(TAG, "processName $processName")
                }
                return processName
            }
        }
        return processName
    }
}