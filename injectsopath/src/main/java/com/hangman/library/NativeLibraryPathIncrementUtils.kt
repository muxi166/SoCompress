package com.hangman.library

import android.os.Build
import android.util.Log
import dalvik.system.DexFile
import java.io.File
import java.io.IOException
import java.lang.ClassCastException
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.util.*


// 本部分反射内容借鉴自atlas
object NativeLibraryPathIncrementUtils {

    private val constructorArgs = arrayOf<Class<*>>(File::class.java)
    private val constructorArgs1 = arrayOf<Class<*>>(File::class.java, Boolean::class.java, File::class.java, DexFile::class.java)


    @Throws(NoSuchFieldException::class)
    @JvmStatic
    fun findField(instance: Any, name: String): Field {
        var clazz: Class<*>? = instance.javaClass
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField(name)
                field.isAccessible = true
                return field
            } catch (e: NoSuchFieldException) {
                // ignore and search next
            }

            clazz = clazz.superclass
        }
        throw NoSuchFieldException("Field " + name + " not found in " + instance.javaClass)
    }


    @Throws(NoSuchFieldException::class, IllegalArgumentException::class, IllegalAccessException::class, ClassCastException::class)
    @JvmStatic
    fun expandFieldArray(instance: Any, fieldName: String, extraElement: Array<Any>) {
        val jlrField = findField(instance, fieldName)
        val original = jlrField.get(instance) as Array<Any>
        val combined = java.lang.reflect.Array.newInstance(
                original.javaClass.componentType, original.size + extraElement.size) as Array<Any>
        for (i in extraElement.indices) {
            combined[i] = extraElement[i]
        }
        System.arraycopy(original, 0, combined, extraElement.size, original.size)
        jlrField.set(instance, combined)
    }

    @Throws(NoSuchFieldException::class, IllegalArgumentException::class, IllegalAccessException::class)
    @JvmStatic
    fun expandFieldList(instance: Any, fieldName: String, extraElement: Any) {
        val jlrField = findField(instance, fieldName)
        val original = jlrField.get(instance) as java.util.List<Any>
        original.add(0, extraElement)
    }


    @Throws(IOException::class)
    @JvmStatic
    fun makeNativeLibraryElement(dir: File): Any {
        when (Build.VERSION.SDK_INT >= 25 && Build.VERSION.PREVIEW_SDK_INT > 0) {
            true -> {
                try {
                    val nativeLibraryElement = Class.forName("dalvik.system.DexPathList\$NativeLibraryElement")
                    val constructor = nativeLibraryElement.getDeclaredConstructor(*constructorArgs)
                    constructor.isAccessible = true
                    return constructor.newInstance(dir)
                } catch (e: Exception) {
                    throw IOException("make nativeElement failed ${e.message}", e)
                }
            }
            false -> {
                try {
                    val element = Class.forName("dalvik.system.DexPathList\$Element")
                    val constructor = getElementConstructor(element, *constructorArgs1)
                    if (constructor != null) {
                        constructor.isAccessible = true
                        return constructor.newInstance(dir, true, null, null)
                    } else {
                        throw IOException("make nativeElement fail | error constructor")
                    }
                } catch (e: Exception) {
                    throw IOException("make nativeElement fail ", e)
                }
            }
        }
    }

    @JvmStatic
    private fun getElementConstructor(element: Class<*>, vararg args: Class<*>): Constructor<*>? {
        try {
            return element.getDeclaredConstructor(*args)
        } catch (e: Throwable) {
            Log.e("KernalBundleImpl", "can not create element by args$args")
        }
        return null
    }

}