package info.skyblond.mc.mca

import info.skyblond.i2p.p2p.chat.I2PHelper
import info.skyblond.i2p.p2p.chat.core.Peer
import info.skyblond.i2p.p2p.chat.message.getSerializersModule
import info.skyblond.mc.mca.helper.MinecraftHelper
import info.skyblond.mc.mca.i2p.MCAContext
import info.skyblond.mc.mca.i2p.createPeer
import info.skyblond.mc.mca.json.MCAConfig
import info.skyblond.mc.mca.model.ConfigFile
import info.skyblond.mc.mca.model.Lang
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import java.time.Duration

@Environment(EnvType.CLIENT)
object ClientModEntry : ClientModInitializer {
    private val logger = KotlinLogging.logger { }
    private val json = Json {
        ignoreUnknownKeys = true
        serializersModule = getSerializersModule()
    }
    private val threadPool = I2PHelper.createThreadPool(Runtime.getRuntime().availableProcessors())
    private var peer: Peer<MCAContext>? = null

    @JvmStatic
    fun getPeer(): Peer<MCAContext> = peer ?: error("Peer not initialized")

    override fun onInitializeClient() {
        logger.info { "Hello!" }

        logger.info { "Creating peer..." }
        peer = createPeer(
            json = json, username = MinecraftHelper.getCurrentUsername()!!,
            protocolName = "minecraft-chat-alternative-protocol-20221121",
            destinationKey = ConfigFile.ModConfig.use { i2pDestinationKey },
            threadPool = threadPool, pexInterval = ConfigFile.ModConfig.use { pexInterval }
        )
        logger.info { "Peer created!" }

        // start doing things with that peer
        // update last seen
        runTask({ lastSeenUpdateInterval }) { peer ->
            ConfigFile.PeerRepo.updateLastSeenFromSessions(peer.dumpSessions())
            ConfigFile.PeerRepo.save()
        }
        // remove invalid session
        runTask({ sessionRemoverInterval }) { peer ->
            peer.disconnect("not in same server") {
                val username = it.useContextSync { username } ?: return@disconnect false
                username !in MinecraftHelper.getPlayerList()
            }
        }
        // connect from peer repo
        runTask({ autoConnectInterval }) { peer ->
            val alreadyConnected = peer.dumpSessions().mapNotNull { it.useContextSync { username } }
            ConfigFile.PeerRepo.use {
                entries.filter {
                    it.key in MinecraftHelper.getPlayerList()
                            && it.key !in alreadyConnected
                }.forEach { entry ->
                    I2PHelper.runThread {
                        try {
                            peer.connect(entry.value.destination)
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
        }
    }

    private fun runTask(duration: MCAConfig.() -> Duration, block: (Peer<MCAContext>) -> Unit) {
        I2PHelper.runThread {
            val peer = getPeer()
            val interval = ConfigFile.ModConfig.use { duration() }.toMillis()
            while (!peer.isClosed()) {
                MinecraftHelper.sleep(interval)
                block(peer)
            }
            logger.warn { "Peer stopped! Trigger crash" }
            MinecraftHelper.crash(IllegalStateException(Lang.TextMessage.SystemMessage.Error.PeerStopped.translate().string))
        }
    }
}
