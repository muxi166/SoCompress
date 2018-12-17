package com.hangman.plugin

import groovy.io.FileType
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.impldep.org.apache.commons.compress.compressors.CompressorStreamFactory

class SoCompressTask extends DefaultTask {
    private final static
    def SUPPORT_ALGORITHM = [CompressorStreamFactory.BZIP2, CompressorStreamFactory.BROTLI, CompressorStreamFactory.GZIP, CompressorStreamFactory.PACK200,
                             CompressorStreamFactory.XZ, CompressorStreamFactory.LZMA, CompressorStreamFactory.Z, CompressorStreamFactory.LZ4_BLOCK, CompressorStreamFactory.LZ4_FRAMED]
    SoCompressConfig config
    String taskVariantName
    Set<File> inputFileDir
    Set<File> outputFileDir
    def map = new HashMap<String, CompressInfo>()
    def abiFilterSet

    @TaskAction
    void taskAction() {
        def printLog = config.printLog
        if (printLog) {
            println "current variant name is ${taskVariantName}"
        }
        if (inputFileDir == null || outputFileDir == null) {
            if (printLog) {
                print """|inputFileDir $inputFileDir
                         |outputFileDir $outputFileDir""".stripMargin()
            }
            return
        }
        if (printLog) {
            println "taskName ${this.name}"
            println "$config"
        }
        if (!SUPPORT_ALGORITHM.contains(config.algorithm)) {
            throw new IllegalArgumentException("only support one of ${Arrays.asList(SUPPORT_ALGORITHM).toString()}")
        }
        // 找到输入输出目录
        def libInputFileDir = null
        def libOutputFileDir = null

        inputFileDir.each { file ->
            if (file.getAbsolutePath().contains('transforms/mergeJniLibs')) {
                libInputFileDir = file
            }
        }
        outputFileDir.forEach { file ->
            if (file.getAbsolutePath().contains('intermediates/assets')) {
                libOutputFileDir = file
            }
        }
        if (libInputFileDir == null) {
            throw new IllegalStateException('libInputFileDir is null')
        }
        if (libOutputFileDir == null) {
            throw new IllegalStateException('libOutputFileDir is null')
        }
        if (printLog) {
            println "libInputFileDir ${libInputFileDir}"
            println "libOutputFileDir ${libOutputFileDir}"
        }
        String[] tarFileArray = config.tarFileNameArray
        String[] compressFileArray = config.compressFileNameArray

        tarFileArray.each { fileName ->
            if (compressFileArray.contains(fileName)) {
                throw new IllegalArgumentException("${fileName} both in tarFileNameArray & compressFileNameArray")
            }
        }

        if (tarFileArray.length != 0) {
            tarFileArray.sort()
            compressTar(tarFileArray, libInputFileDir, libOutputFileDir, printLog)
        }

        if (compressFileArray.length != 0) {
            compressFileArray.sort()
            compressSoFileArray(compressFileArray, libInputFileDir, libOutputFileDir, printLog)
        }
    }

    def compressTar(String[] fileArray, File libInputFileDir, File libOutputFileDir, boolean printLog) {
        def fileNameArray = new ArrayList<String>()
        fileArray.each { fileName ->
            if (!fileNameArray.contains(fileName)) {
                fileNameArray.add(fileName)
            }
        }
        if (printLog) {
            println "compress tar file list ${fileNameArray}"
        }
        def soCompressDir = new File(libOutputFileDir, CompressConstant.SO_COMPRESSED)
        if (!soCompressDir.exists()) {
            soCompressDir.mkdirs()
        }
        if (printLog) {
            println "compressTar -> compress dir ${soCompressDir.getAbsolutePath()}"
        }
        abiFilterSet.each { value ->
            def fileList = new ArrayList<File>()
            def tempMap = new HashMap<String, File>()
            libInputFileDir.eachFileRecurse(FileType.FILES) { file ->
                if (fileNameArray.contains(file.name) && file.getAbsolutePath().contains(value)) {
                    fileList.add(file)
                    tempMap[file.name] = file
                }
            }
            if (fileNameArray.size() != fileList.size()) {
                def notExistFileList = new ArrayList<String>()
                fileNameArray.each { fileName ->
                    if (tempMap.get(fileName) == null) {
                        notExistFileList.add(fileName)
                    }
                }
                throw new IllegalStateException("compressTar -> current abiFilter is ${value}, but these file ${notExistFileList} not found in inputDir $libInputFileDir")
            } else {
                tempMap.clear()
                tempMap = null
            }
            fileList.sort()
            // 压缩文件列表为一个tar包
            def info = CompressionUtil.tarFileList(fileList, soCompressDir, value, config)
            map.put(info.fileName, info)
        }

    }

    def compressSoFileArray(String[] fileArray, File libInputFileDir, File libOutputFileDir, boolean printLog) {
        def fileNameList = new ArrayList<String>()
        fileArray.each { fileName ->
            if (!fileNameList.contains(fileName)) {
                fileNameList.add(fileName)
            }
        }
        if (printLog) {
            println "compress file list ${fileNameList}"
        }
        def soCompressDir = new File(libOutputFileDir, 'so_compressed')
        if (!soCompressDir.exists()) {
            soCompressDir.mkdirs()
        }
        if (printLog) {
            println "compressSoFileArray compress dir ${soCompressDir.getAbsolutePath()}"
        }
        abiFilterSet.each { value ->
            def fileList = new ArrayList<File>()
            def tempMap = new HashMap<String, File>()
            libInputFileDir.eachFileRecurse(FileType.FILES) { file ->
                if (fileNameList.contains(file.name) && file.getAbsolutePath().contains(value)) {
                    fileList.add(file)
                    tempMap[file.name] = file
                }
            }
            if (fileNameList.size() != fileList.size()) {
                def notExistFileList = new ArrayList<String>()
                fileNameArray.each { fileName ->
                    if (tempMap.get(fileName) == null) {
                        notExistFileList.add(fileName)
                    }
                }
                throw new IllegalStateException("compressSoFileArray -> current abiFilter is ${value}, but these file ${notExistFileList} not found in inputDir $libInputFileDir")
            } else {
                tempMap.clear()
                tempMap = null
            }

            fileList.each {
                def abiDir = new File(soCompressDir, value)
                if (!abiDir.exists()) {
                    abiDir.mkdir()
                }
                def info = CompressionUtil.compressSoFile(file, abiDir, config.algorithm, config.verify, config.printLog)
                map.put(file.name, info)
            }
            fileList.clear()
        }
    }

    def getInfoMap() {
        return map
    }
}