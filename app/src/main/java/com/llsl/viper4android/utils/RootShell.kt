package com.llsl.viper4android.utils

import java.io.File
import java.util.concurrent.TimeUnit

object RootShell {
    private const val TAG = "RootShell"
    private const val SU_TIMEOUT_SEC = 10L

    @Volatile
    private var cachedSuPath: String? = null

    @Volatile
    private var suDetected = false

    /** LibreHU fork: the detected su is the old su of AOSP (`su UID COMMAND`), which has no -c. */
    @Volatile
    private var uidForm = false

    fun exec(
        command: String,
        timeoutSec: Long = SU_TIMEOUT_SEC,
    ): Process {
        val su = getSuPath()
        val args = if (uidForm) arrayOf(su, "0", "sh", "-c", command) else arrayOf(su, "-c", command)
        val process = Runtime.getRuntime().exec(args)
        if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw RuntimeException("su command timed out after ${timeoutSec}s: $command")
        }
        return process
    }

    fun getSuPath(): String {
        cachedSuPath?.let { return it }
        synchronized(this) {
            cachedSuPath?.let { return it }
            val path = detectSu()
            cachedSuPath = path
            suDetected = true
            return path
        }
    }

    fun isRootAvailable(): Boolean =
        try {
            getSuPath()
            true
        } catch (_: Exception) {
            false
        }

    private fun detectSu(): String {
        if (testSu("su")) {
            FileLogger.i(TAG, "su found via PATH")
            return "su"
        }

        val candidates =
            arrayOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/system/bin/kp",
                "/sbin/su",
                "/debug_ramdisk/su",
                "/su/bin/su",
                "/data/adb/ksud",
                "/data/adb/ksu/bin/ksud",
                "/data/adb/apd",
                "/sbin/magisk",
                "/debug_ramdisk/magisk",
            )
        for (path in candidates) {
            if (File(path).exists() && testSu(path)) {
                FileLogger.i(TAG, "su found at $path")
                return path
            }
        }

        FileLogger.e(TAG, "No working su binary found")
        throw RuntimeException("Root not available: no working su binary found")
    }

    private fun testSu(path: String): Boolean {
        val (ok, output) = runId(arrayOf(path, "-c", "id"))
        if (ok) return true
        // LibreHU fork: the old su of AOSP that some head unit ROMs ship (Jancar's /system/xbin/su) reads -c as a user
        // and stops with "invalid uid/gid" before running anything; it takes `su 0 sh -c COMMAND`.
        if (!output.contains("invalid uid/gid", ignoreCase = true)) return false
        if (!runId(arrayOf(path, "0", "sh", "-c", "id")).first) return false
        uidForm = true
        return true
    }

    /** True when [args] printed uid 0, with what they printed (output and errors). */
    private fun runId(args: Array<String>): Pair<Boolean, String> =
        try {
            val process = Runtime.getRuntime().exec(args)
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false to ""
            } else {
                val output = process.inputStream.bufferedReader().readText() + process.errorStream.bufferedReader().readText()
                (process.exitValue() == 0 && output.contains("uid=0")) to output
            }
        } catch (_: Exception) {
            false to ""
        }
}
