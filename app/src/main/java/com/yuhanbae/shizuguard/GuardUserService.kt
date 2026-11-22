package com.yuhanbae.shizuguard

import kotlin.system.exitProcess

class GuardUserService : IGuardService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun runCommand(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val out = process.inputStream.bufferedReader().readText()
            val err = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (out.isNotBlank()) out.trim() else err.trim()
        } catch (e: Exception) {
            "ERR: ${e.message}"
        }
    }

    override fun setPackageEnabled(packageName: String, enabled: Boolean): Boolean {
        val action = if (enabled) "enable" else "disable-user --user 0"
        val res = runCommand("pm $action $packageName")
        return !res.startsWith("ERR:") && !res.contains("Exception")
    }

    override fun forceStopPackage(packageName: String): Boolean {
        val res = runCommand("am force-stop $packageName")
        return !res.startsWith("ERR:")
    }

    override fun clampBackground(packageName: String, restrict: Boolean): Boolean {
        val mode = if (restrict) "ignore" else "allow"
        val r1 = runCommand("cmd appops set $packageName RUN_IN_BACKGROUND $mode")
        val r2 = runCommand("cmd appops set $packageName RUN_ANY_IN_BACKGROUND $mode")
        val r3 = runCommand("cmd appops set $packageName WAKE_LOCK $mode")
        return !r1.startsWith("ERR:") && !r2.startsWith("ERR:") && !r3.startsWith("ERR:")
    }

    override fun batchSetEnabled(packages: List<String>, enabled: Boolean): Int {
        var success = 0
        for (pkg in packages) {
            if (!enabled) {
                forceStopPackage(pkg)
            }
            if (setPackageEnabled(pkg, enabled)) {
                success++
            }
        }
        return success
    }

    override fun getPackageRamKb(packageName: String): Long {
        val out = runCommand("dumpsys meminfo $packageName | grep -m 1 'TOTAL PSS:'")
        val match = Regex("TOTAL PSS:\s+(\d+)").find(out)
        return match?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
    }

    override fun getMemorySummary(): String {
        val out = runCommand("dumpsys meminfo | grep -E 'Total RAM:|Free RAM:|Used RAM:'")
        return if (out.isBlank()) "Telemetry unavailable" else out
    }

    override fun getTopWakelocks(): String {
        val out = runCommand("dumpsys power | grep -A 12 'Wake Locks:'")
        return if (out.isBlank()) "No active wake locks" else out
    }
}
