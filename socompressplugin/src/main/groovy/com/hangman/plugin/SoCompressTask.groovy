package com.hangman.plugin

import groovy.io.FileType
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class SoCompressTask extends DefaultTask {
    private final static def SUPPORT_ALGORITHM = ['bz2', 'gz', 'lzma', 'xz', 'pack200', 'Z']
    SoCompressConfig config
    String taskVariantName
    Set<File> inputFileDir
    Set<File> outputFileDir
    def map = new HashMap<String, CompressInfo>()

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
            if (printLog) {
                println 'libInputFileDir is null'
            }
            return
        }
        if (libOutputFileDir == null) {
            if (printLog) {
                println 'libOutputFileDir is null'
            }
            return
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
            compressTar(tarFileArray, libInputFileDir, libOutputFileDir, printLog)
        }

        if (compressFileArray.length != 0) {
            compressSoFileArray(compressFileArray, libInputFileDir, libOutputFileDir, printLog)
        }
    }

    def compressTar(String[] fileArray, File libInputFileDir, File libOutputFileDir, boolean printLog) {
        def fileNameSet = new HashSet<String>()
        fileArray.each { fileName ->
            fileNameSet.add(fileName)
        }
        if (printLog) {
            println "compress tar file list ${fileNameSet}"
        }
        def soCompressDir = new File(libOutputFileDir, 'so_compressed')
        if (!soCompressDir.exists()) {
            soCompressDir.mkdirs()
        }
        if (printLog) {
            println "compressTar compress dir ${soCompressDir.getAbsolutePath()}"
        }
        def fileMap = new HashMap<String, File>()
        def tarFileArray = new ArrayList<File>()
        libInputFileDir.eachFileRecurse(FileType.FILES) { file ->
            if (fileNameSet.contains(file.name)) {
                fileMap[file.name] = file
                tarFileArray.add(file)
            }
        }
        if (fileMap.size() != fileNameSet.size()) {
            def notExistFileList = new ArrayList<String>()
            fileNameSet.each { it ->
                if (fileMap.get(it) == null) {
                    notExistFileList.add(it)
                }
            }
            throw new IllegalArgumentException("compressTar, these file (${notExistFileList}) not exist in lib dir ")
        }
        // 压缩文件列表为一个tar包
        def info = CompressionUtil.tarFileList(tarFileArray, soCompressDir, config)
        map.put(info.fileName, info)
    }

    def compressSoFileArray(String[] fileArray, File libInputFileDir, File libOutputFileDir, boolean printLog) {
        def fileNameSet = new HashSet<String>()
        fileArray.each { fileName ->
            fileNameSet.add(fileName)
        }
        if (printLog) {
            println "compress file list ${fileNameSet}"
        }
        def soCompressDir = new File(libOutputFileDir, 'so_compressed')
        if (!soCompressDir.exists()) {
            soCompressDir.mkdirs()
        }
        if (printLog) {
            println "compressSoFileArray compress dir ${soCompressDir.getAbsolutePath()}"
        }
        def fileMap = new HashMap<String, File>()
        def fileList = new ArrayList<File>()
        libInputFileDir.eachFileRecurse(FileType.FILES) { file ->
            if (fileNameSet.contains(file.name)) {
                fileMap[file.name] = file
                fileList.add(file)
            }
        }
        if (fileMap.size() != fileNameSet.size()) {
            def notExistFileList = new ArrayList<String>()
            fileNameSet.each { fileName ->
                if (fileMap.get(fileName) == null) {
                    notExistFileList.add(fileName)
                }
            }
            throw new IllegalArgumentException("compressSoFileArray, but these file (${notExistFileList}) not exist in lib dir ")
        }
        fileList.each { file ->
            def info = CompressionUtil.compressSoFile(file, soCompressDir, config.algorithm, config.verify, config.printLog)
            map.put(file.name, info)
        }
    }

    def getInfoMap() {
        return map
    }
}