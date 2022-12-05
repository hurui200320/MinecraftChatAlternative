package info.skyblond.mc.mca.helper

import net.minecraft.client.MinecraftClient
import net.minecraft.util.WinNativeModuleUtil
import net.minecraft.util.crash.CrashReport

/**
 * Useful things for minecraft related things.
 * */
object MinecraftHelper {
    /**
     * Get current username. Return null if unavailable.
     * */
    @JvmStatic
    fun getCurrentUsername(): String? =
        MinecraftClient.getInstance()?.session?.username

    /**
     * Get player list.
     * @return list of player names.
     * */
    @JvmStatic
    fun getPlayerList(): List<String> =
        MinecraftClient.getInstance()?.networkHandler?.let { n ->
            n.playerList.map { it.profile.name }
        } ?: emptyList()

    /**
     * Sleep for a while.
     * */
    fun sleep(millis: Long) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < millis) {
            try {
                Thread.sleep(start + millis - System.currentTimeMillis())
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Run later in normal thread.
     * If you use I2P resources, use [info.skyblond.i2p.p2p.chat.I2PHelper.runThread]
     * */
    @JvmStatic
    fun runLater(r: Runnable) {
        val t = Thread(r)
        t.isDaemon = true
        t.start()
    }

    /**
     * Crash the minecraft
     * */
    @JvmStatic
    fun crash(cause: Throwable) {
        val crashReport = CrashReport("MCA triggered crash", cause)
        val crashReportSection = crashReport.addElement("MCA crash details")
        WinNativeModuleUtil.addDetailTo(crashReportSection)
        MinecraftClient.printCrashReport(crashReport)
    }
}
