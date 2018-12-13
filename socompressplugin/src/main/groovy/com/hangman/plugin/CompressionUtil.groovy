package com.hangman.plugin

import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.CompressorException
import org.apache.commons.compress.compressors.CompressorInputStream
import org.apache.commons.compress.compressors.CompressorOutputStream
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.utils.IOUtils

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class CompressionUtil {

    static def tarFileList(Set<File> fileSet, File outputDir, SoCompressConfig config) {
        def originTotalSize = 0
        def time = System.currentTimeMillis()
        def sb = new StringBuilder()
        fileSet.eachWithIndex { file, index ->
            originTotalSize += file.length()
            String MD5 = getMD5(file)
            if (config.printLog) {
                println "index = ${index} file = ${file} MD5 = ${MD5}"
            }
            sb.append(getMD5(file))
        }
        if (config.printLog) {
            println "total file: ${fileSet.size()}, total size:${originTotalSize}"
        }
        def tarFileName = getMD5(sb.toString())

        TarArchiveOutputStream tarArchiveOutputStream = null
        File tarFile = new File(outputDir, "${tarFileName}.tar")
        try {
            tarArchiveOutputStream = (TarArchiveOutputStream) new ArchiveStreamFactory().createArchiveOutputStream(ArchiveStreamFactory.TAR, new FileOutputStream(tarFile))
            fileSet.forEach() { file ->
                InputStream is = null
                try {
                    is = new FileInputStream(file)
                    TarArchiveEntry entry = new TarArchiveEntry(file, file.getName())
                    tarArchiveOutputStream.putArchiveEntry(entry)
                    IOUtils.copy(is, tarArchiveOutputStream)
                    tarArchiveOutputStream.closeArchiveEntry()
                } finally {
                    if (is != null) {
                        try {
                            is.close()
                        } catch (Exception e) {
                        }
                    }
                }
            }
            tarArchiveOutputStream.finish()
            tarArchiveOutputStream.close()
        } catch (Exception e) {
        }
        // 默认为lzma格式压缩这个tar包
        def info = compressSoFile(tarFile, outputDir, config.algorithm, config.verify, config.printLog)
        if (config.printLog) {
            println "tar file and compress cost: ${System.currentTimeMillis() - time}"
        }
        fileSet.forEach { file ->
            file.delete()
        }
        return info
    }

    // 压缩单个问件到指定输出目录
    static CompressInfo compressSoFile(File file, File outputDir, String algorithm, boolean verify, boolean printLog) {
        def info = new CompressInfo()
        info.setFileName(file.name)
        info.setFilePath(file.absolutePath)
        info.setOriginFileLength(file.length())
        def MD5 = getMD5(file)
        info.setOriginMd5String(MD5)

        // 开始压缩so文件
        long time = System.currentTimeMillis()
        File compressFile = new File(outputDir, "${file.name}^_^${MD5}")
        compressFile.delete()
        try {
            InputStream is = new FileInputStream(file)
            OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(compressFile))
            CompressorOutputStream compressorOutputStream = new CompressorStreamFactory().createCompressorOutputStream(algorithm, outputStream)
            IOUtils.copy(new BufferedInputStream(is), compressorOutputStream)
            compressorOutputStream.close()
            is.close()
            long costTime = System.currentTimeMillis() - time
            info.setCompressTime(costTime)

            long compressFileLength = compressFile.length()
            info.setCompressFileLength(compressFileLength)
            info.setCompressMd5String(getMD5(compressFile))

            // 验证压缩的so文件是否有效
            if (verify) {
                time = System.currentTimeMillis()
                File uncompressFile = new File(outputDir, "${file.name}.uncompress")
                uncompressFile.delete()
                final OutputStream anotherSourceFileOutputStream = new FileOutputStream(uncompressFile)
                final InputStream compressInputStream = new BufferedInputStream(new FileInputStream(compressFile))
                CompressorInputStream compressorInputStream =
                        new CompressorStreamFactory().createCompressorInputStream(algorithm, compressInputStream)
                IOUtils.copy(compressorInputStream, anotherSourceFileOutputStream)
                compressInputStream.close()
                anotherSourceFileOutputStream.close()
                long uncompressCostTime = System.currentTimeMillis() - time
                info.setUncompressTime(uncompressCostTime)
                long uncompressFileLength = uncompressFile.length()
                info.setUncompressFileLength(uncompressFileLength)
                def uncompressMD5 = getMD5(uncompressFile)
                info.setUncompressMd5String(uncompressMD5)

                if (uncompressMD5 != MD5) {
                    throw new IllegalStateException("file verify failed ：$info")
                } else {
                    if (printLog) {
                        println "compress info：$info"
                    }
                    uncompressFile.delete()
                }
            }
            // 删除原文件
            file.delete()
        } catch (FileNotFoundException e) {
            if (printLog) {
                println "FileNotFoundException $e"
            }
        } catch (CompressorException e) {
            if (printLog) {
                println "CompressorException $e"
            }
        } catch (IOException e) {
            if (printLog) {
                println "IOException $e"
            }
        }
        return info
    }

    static def getMD5(File file) {
        def MD5 = ''
        def is
        try {
            is = new FileInputStream(file)
            // 生成一个MD5加密计算摘要
            MessageDigest md = MessageDigest.getInstance("MD5")
            // 计算md5函数
            byte[] data = new byte[1024]
            int number
            while ((number = is.read(data)) != -1) {
                md.update(data, 0, number)
            }
            byte[] hash = md.digest()
            StringBuilder result = new StringBuilder()
            for (int i = 0; i < hash.length; i++) {
                result.append(Integer.toString((hash[i] & 0xff) + 0x100, 16).substring(1))
            }
            MD5 = result.toString()
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace()
        } catch (IOException e) {
            e.printStackTrace()
        } finally {
            try {
                if (is != null) {
                    is.close()
                }
            } catch (Exception e) {
            }
        }
        return MD5
    }

    static def getMD5(String string) {
        def MD5 = ''
        try {
            MessageDigest md = MessageDigest.getInstance("MD5")
            byte[] data = string.getBytes()
            md.update(data)
            byte[] hash = md.digest()
            StringBuilder result = new StringBuilder()
            for (int i = 0; i < hash.length; i++) {
                result.append(Integer.toString((hash[i] & 0xff) + 0x100, 16).substring(1))
            }
            MD5 = result.toString()
        } catch (Exception e) {
        }
        return MD5
    }


}