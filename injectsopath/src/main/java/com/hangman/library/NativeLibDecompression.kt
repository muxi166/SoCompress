package com.hangman.library

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
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

class NativeLibDecompression(private val context: Context, private val algorithm: String, private val printLog: Boolean) {

    private var tarDecompression: Long = 0
    private var soDecompression: Long = 0
    private val threadPool = Executors.newSingleThreadExecutor()

    companion object {
        const val SO_DECOMPRESSION = "so_decompressed"
        const val SO_COMPRESSED = "so_compressed"
        const val TAR = "tar"
        const val TAG = "NativeLibDecompression"
        const val FILE_INTERVAL = "^_^"
    }

    private lateinit var spInterface: SpInterface
    private var decompressionCallback: DecompressionCallback? = null
    private var logInterface: LogInterface? = null
    private var abiNameList = arrayListOf<String>()

    fun decompression(async: Boolean, spInterface: SpInterface, logInterface: LogInterface?, decompressionCallback: DecompressionCallback?) {
        if (isMainProcess(context.applicationContext.packageName)) {
            if (printLog) {
                logInterface?.logE(TAG, "======= decompression function invoke ======")
            }
            val time = System.currentTimeMillis()
            this.spInterface = spInterface
            this.logInterface = logInterface
            this.decompressionCallback = decompressionCallback

            val runnable = Runnable {
                val decompressed = shouldDecompression()
                val cost = System.currentTimeMillis() - time
                logInterface?.logV(TAG, "NativeLibDecompression shouldDecompression cost $cost")

                val time1 = System.currentTimeMillis()
                injectExtraSoFilePath()
                val cost1 = System.currentTimeMillis() - time1
                logInterface?.logV(TAG, "NativeLibDecompression injectExtraSoFilePath cost $cost1 ")
                decompressionCallback?.decompression(true, decompressed)
            }
            if (async) {
                threadPool.execute(runnable)
            } else {
                runnable.run()
            }
        }
    }

    private fun shouldDecompression(): Boolean {
        val pathList = context.applicationContext.assets.list(SO_COMPRESSED)
        var decompressed = false
        pathList?.forEach { abiName ->
            abiNameList.add(abiName)
            val fileNameArray = context.applicationContext.assets.list("$SO_COMPRESSED/$abiName")
            if (printLog) {
                logInterface?.logV(TAG, "methodName = shouldDecompression pathName = $SO_COMPRESSED/$abiName")
            }
            fileNameArray?.forEach {
                if (printLog) {
                    logInterface?.logV(TAG, "fileName = $it")
                }
                val namePieces = it.split(FILE_INTERVAL)
                val fileName = namePieces[0]
                val originMD5 = namePieces[1]
                when (fileName.contains(TAR)) {
                    true -> {
                        val value = spInterface.getString(fileName)
                        if (!TextUtils.equals(value, originMD5)) {
                            decompressed = true
                            tarDecompression("$SO_COMPRESSED/$abiName/$it", abiName, it)
                            if (printLog) {
                                logInterface?.logV(TAG, "methodName = shouldDecompression tarDecompression $abiName")
                            }
                        }
                    }
                    false -> {
                        val value = spInterface.getString(fileName)
                        if (!TextUtils.equals(value, originMD5)) {
                            decompressed = true
                            soDecompression("$SO_COMPRESSED/$abiName/$it", abiName, it)
                            if (printLog) {
                                logInterface?.logV(TAG, "methodName = shouldDecompression soDecompression $abiName")
                            }
                        }
                    }
                }
            }
        }
        return decompressed
    }

    private fun injectExtraSoFilePath() {
        val nativeLibraryElementArray = arrayOf<Any>(abiNameList.size)
        val nativeLibraryDirectories = arrayOfNulls<File?>(abiNameList.size)
        val index = 0
        abiNameList.forEach {
            val fileDecompressedDir = File(context.applicationContext.filesDir, "$SO_DECOMPRESSION/$it")
            nativeLibraryElementArray[index] = NativeLibraryPathIncrementUtils.makeNativeLibraryElement(fileDecompressedDir)
            nativeLibraryDirectories[index] = fileDecompressedDir
            index.inc()
        }
        if (printLog) {
            logInterface?.logV(TAG, "nativeLibraryElementArray $nativeLibraryElementArray")
        }
        val classLoader = context.applicationContext.classLoader
        if (printLog) {
            logInterface?.logV(TAG, "injectExtraSoFilePath classLoader $classLoader")
        }
        try {
            val dexPathListField = NativeLibraryPathIncrementUtils.findField(classLoader, "pathList")
            dexPathListField.isAccessible = true
            val dexPathListInstance = dexPathListField.get(classLoader)
            NativeLibraryPathIncrementUtils.expandFieldArray(dexPathListInstance, "nativeLibraryPathElements", nativeLibraryElementArray)
            NativeLibraryPathIncrementUtils.expandFieldList(dexPathListInstance, "nativeLibraryDirectories", nativeLibraryDirectories as Array<Any>)
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

    private fun tarDecompression(pathName: String, abiName: String, fileName: String) {
        try {
            // decompression
            var time = System.currentTimeMillis()
            val file = fileDecompression(pathName, abiName, fileName)
                    ?: throw IllegalStateException("tar decompression failed $pathName")
            var cost = System.currentTimeMillis() - time
            if (printLog) {
                logInterface?.logV(TAG, "tarDecompression1 $pathName cost : $cost")
            }
            soDecompression += cost

            // tar decompression
            time = System.currentTimeMillis()
            untar(file.absolutePath, file.parent)
            file.delete()
            cost = System.currentTimeMillis() - time
            tarDecompression += cost
            if (printLog) {
                logInterface?.logV(TAG, "tarDecompression2 $pathName cost : $cost")
            }

            val splits = fileName.split(FILE_INTERVAL)
            val name = splits[0]
            val originMD5 = splits[1]
            spInterface.saveString(name, originMD5)
        } catch (e: Exception) {
            decompressionCallback?.decompression(false, true)
        }

    }

    private fun soDecompression(pathName: String, abiName: String, fileName: String) {
        val time = System.currentTimeMillis()
        val file = fileDecompression(pathName, abiName, fileName)
                ?: throw IllegalStateException("soDecompression failed $pathName")
        val cost = System.currentTimeMillis() - time
        if (printLog) {
            logInterface?.logV(TAG, "soDecompression $pathName fileName ${file.name}  cost $cost")
        }
        soDecompression += cost
        val splits = fileName.split(FILE_INTERVAL)
        val name = splits[0]
        val originMD5 = splits[1]
        spInterface.saveString(name, originMD5)
    }

    private fun fileDecompression(pathName: String, abiName: String, fileName: String): File? {
        var file: File? = null
        try {
            val inputStream = context.applicationContext.assets.open(pathName)
            val fileDecompressedDir = File(context.applicationContext.filesDir, "$SO_DECOMPRESSION/$abiName")
            if (!fileDecompressedDir.exists()) {
                fileDecompressedDir.mkdirs()
            }
            val name = fileName.split(FILE_INTERVAL)[0]
            val decompressedFile = File(fileDecompressedDir, name)
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
            decompressionCallback?.decompression(false, true)
        }
        return file
    }

    private fun untar(tarPath: String, unTarPath: String) {
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
    fun isMainProcess(packageName: String): Boolean {
        val result = packageName == getCurrentProcessName()
        if (printLog && result) {
            logInterface?.logV(TAG, "processName $packageName")
        }
        return result
    }

    private fun getCurrentProcessName(): String? {
        val myPid = android.os.Process.myPid()
        val activityManager = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val taskList = activityManager.runningAppProcesses
        var processName: String? = null
        taskList.forEach { info ->
            if (info.pid == myPid) {
                processName = info.processName
                return processName
            }
        }
        return processName
    }
}