package com.hangman.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.tasks.TaskState

class SoCompressPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        noteApply()
        def extension = project.extensions.create('soCompressConfig', SoCompressConfig)
        project.afterEvaluate {
            project.android.applicationVariants.all { variant ->
                addTaskDependencies(project, variant.name, extension)
            }
            project.gradle.taskGraph.addTaskExecutionListener(new TaskExecutionListener() {

                def time = 0

                @Override
                void beforeExecute(Task task) {
                    time = System.currentTimeMillis()
                }

                @Override
                void afterExecute(Task task, TaskState taskState) {
                    if (task instanceof SoCompressTask) {
                        def map = task.infoMap
                        def compressTotalTime = 0
                        def uncompressTotalTime = 0
                        if (!map.isEmpty()) {
                            map.each {
                                compressTotalTime += it.value.compressTime
                                uncompressTotalTime += it.value.uncompressTime
                            }
                        }
                        println "task ${task.name} cost ${System.currentTimeMillis() - time}  [compress cost ${compressTotalTime} , uncompress cost ${uncompressTotalTime}]"
                    }
                }
            })
        }
    }

    private static def noteApply() {
        println 'apply so compress plugin'
    }

    private static def uppercaseFirstLetter(String originString) {
        char[] cs = originString.toCharArray()
        cs[0] -= 32
        return String.valueOf(cs)
    }

    def addTaskDependencies(Project project, String variantName, SoCompressConfig extension) {
        def uppercaseFirstLetterName = uppercaseFirstLetter(variantName)
        def preTask = project.tasks.getByName("transformNativeLibsWithMergeJniLibsFor${uppercaseFirstLetterName}")
        def followTask = project.tasks.getByName("package${uppercaseFirstLetterName}")
        def printLog = extension.printLog
        def debugModeEnable = extension.debugModeEnable
        if (preTask == null || followTask == null) {
            return
        }
        if (debugModeEnable || (!variantName.endsWith('Debug') && !variantName.endsWith('debug'))) {
            if (printLog) {
                println "add task for variant $variantName"
            }
            def abiFilters = project.android.defaultConfig.ndk.abiFilters
            SoCompressTask task = project.tasks.create("soCompressFor$uppercaseFirstLetterName", SoCompressTask) {
                abiFilterSet = abiFilters
                taskVariantName = variantName
                config = extension
                inputFileDir = preTask.outputs.files.files
                outputFileDir = followTask.inputs.files.files
            }
            task.dependsOn preTask
            if (printLog) {
                println '==========================================='
                println "${task.name} dependsOn ${preTask.name}"
            }
            followTask.dependsOn task
            if (printLog) {
                println "${followTask.name} dependsOn ${task.name}"
                println '==========================================='
            }
        }
    }

}