package com.hangman.plugin

class CompressInfo {
    private String fileName
    private String filePath
    private long compressTime
    private String originMd5String
    private String uncompressMd5String
    private String compressMd5String
    private long uncompressTime
    private long originFileLength
    private long compressFileLength
    private long uncompressFileLength

    String getFileName() {
        return fileName
    }

    void setFileName(String fileName) {
        this.fileName = fileName
    }

    String getFilePath() {
        return filePath
    }

    void setFilePath(String filePath) {
        this.filePath = filePath
    }

    long getCompressTime() {
        return compressTime
    }

    void setCompressTime(long compressTime) {
        this.compressTime = compressTime
    }

    String getOriginMd5String() {
        return originMd5String
    }

    void setOriginMd5String(String originMd5String) {
        this.originMd5String = originMd5String
    }

    String getUncompressMd5String() {
        return uncompressMd5String
    }

    void setUncompressMd5String(String uncompressMd5String) {
        this.uncompressMd5String = uncompressMd5String
    }

    String getCompressMd5String() {
        return compressMd5String
    }

    void setCompressMd5String(String compressMd5String) {
        this.compressMd5String = compressMd5String
    }

    long getUncompressTime() {
        return uncompressTime
    }

    void setUncompressTime(long uncompressTime) {
        this.uncompressTime = uncompressTime
    }

    long getOriginFileLength() {
        return originFileLength
    }

    void setOriginFileLength(long originFileLength) {
        this.originFileLength = originFileLength
    }

    long getCompressFileLength() {
        return compressFileLength
    }

    void setCompressFileLength(long compressFileLength) {
        this.compressFileLength = compressFileLength
    }

    long getUncompressFileLength() {
        return uncompressFileLength
    }

    void setUncompressFileLength(long uncompressFileLength) {
        this.uncompressFileLength = uncompressFileLength
    }

    @Override
    String toString() {
        return """|fileName ${fileName}
                  |filePath ${filePath} 
                  |originFileMD5 ${originMd5String}
                  |originFileLength ${originFileLength}
                  |compressFileLength ${compressFileLength}   
                  |compressFileMD5 ${compressMd5String} 
                  |compressTime  ${compressTime}
                  |unCompressFileLength ${uncompressFileLength}
                  |unCompressFileMD5  ${uncompressMd5String}
                  |unCompressTime ${uncompressTime} 
               """.stripMargin()
    }
}