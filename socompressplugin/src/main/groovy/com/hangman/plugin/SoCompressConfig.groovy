package com.hangman.plugin

class SoCompressConfig {
    public String[] tarFileNameArray
    public String[] compressFileNameArray
    public String algorithm
    public boolean debugModeEnable
    public boolean printLog
    public boolean verify

    SoCompressConfig() {
        tarFileNameArray = []
        compressFileNameArray = []
        algorithm = 'lzma'
        debugModeEnable = false
        printLog = false
        verify = true
    }

    String[] getTarFileNameArray() {
        return tarFileNameArray
    }

    void setTarFileNameArray(String[] tarFileNameArray) {
        this.tarFileNameArray = tarFileNameArray
    }

    String[] getCompressFileNameArray() {
        return compressFileNameArray
    }

    void setCompressFileNameArray(String[] compressFileNameArray) {
        this.compressFileNameArray = compressFileNameArray
    }

    String getAlgorithm() {
        return algorithm
    }

    void setAlgorithm(String algorithm) {
        this.algorithm = algorithm
    }

    boolean getDebugModeEnable() {
        return debugModeEnable
    }

    void setDebugModeEnable(boolean debugModeEnable) {
        this.debugModeEnable = debugModeEnable
    }

    boolean getPrintLog() {
        return printLog
    }

    void setPrintLog(boolean printLog) {
        this.printLog = printLog
    }

    boolean getVerify() {
        return verify
    }

    void setVerify(boolean verify) {
        this.verify = verify
    }

    @Override
    String toString() {
        """| tarFileName = ${tarFileNameArray}
           | compressFileName = ${compressFileNameArray}
           | algorithm = ${algorithm}
           | debugModeEnable = ${debugModeEnable}
           | printLog = ${printLog}
           | verify = ${verify} 
        """.stripMargin()
    }
}