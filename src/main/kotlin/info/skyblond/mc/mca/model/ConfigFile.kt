package info.skyblond.mc.mca.model

import info.skyblond.i2p.p2p.chat.core.PeerSession
import info.skyblond.mc.mca.i2p.MCAContext
import info.skyblond.mc.mca.json.MCAConfig
import info.skyblond.mc.mca.json.PeerRepoEntry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import kotlin.io.path.readText
import kotlin.io.path.writeText

// use default instance for now, replace with custom one if needed
private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

sealed class ConfigFile<T>(
    private val filename: String,
    private val serializer: KSerializer<T>,
    defaultValue: T,
) {
    object ModConfig : ConfigFile<MCAConfig>(
        filename = "minecraft-chat-alternative/config.json",
        serializer = MCAConfig.serializer(),
        defaultValue = MCAConfig()
    )

    @Suppress("OPT_IN_USAGE")
    object PeerRepo : ConfigFile<MutableMap<String, PeerRepoEntry>>(
        filename = "minecraft-chat-alternative/peer_repo.json",
        serializer = object : KSerializer<MutableMap<String, PeerRepoEntry>> {
            val delegate = MapSerializer(String.serializer(), PeerRepoEntry.serializer())

            override val descriptor: SerialDescriptor = SerialDescriptor(
                "MutableMap<String, PeerRepoEntry>", delegate.descriptor
            )

            override fun deserialize(decoder: Decoder): MutableMap<String, PeerRepoEntry> {
                return decoder.decodeSerializableValue(delegate).toMutableMap()
            }

            override fun serialize(encoder: Encoder, value: MutableMap<String, PeerRepoEntry>) {
                encoder.encodeSerializableValue(delegate, value)
            }
        },
        defaultValue = mutableMapOf()
    ) {
        /**
         * Update from session list.
         * */
        fun updateLastSeenFromSessions(sessions: List<PeerSession<MCAContext>>) {
            sessions.forEach { s ->
                if (s.isClosed()) return@forEach
                val username = s.useContextSync { username } ?: return@forEach
                this.use {
                    if (containsKey(username))
                        get(username)!!.seen(s.getPeerDestination())
                    else
                        put(username, PeerRepoEntry(s.getPeerDestination(), ZonedDateTime.now()))
                }
            }
        }
    }

    /**
     * Current value. It use default value unless we overwrite it when [loadOrCreate]
     * */
    private var value: T = defaultValue

    /**
     * The actual [Path] for this config file.
     * */
    private val configPath = FabricLoader.getInstance().configDir.resolve(this.filename)

    init {
        reload()
        // write the latest
        save()
    }

    /**
     * Load from disk, create with default value if not exist.
     * */
    private fun loadOrCreate(): T {
        // ensure we don't read and write against the same config at the same time
        synchronized(this) {
            return if (Files.exists(configPath)) {
                // read it
                json.decodeFromString(serializer, configPath.readText())
            } else {
                //create it
                @Suppress("BlockingMethodInNonBlockingContext")
                Files.createDirectories(configPath.parent)
                configPath.writeText(json.encodeToString(serializer, value))
                value
            }
        }
    }

    /**
     * Use the config sync.
     * */
    fun <R> use(block: T.() -> R): R {
        synchronized(this) {
            return block(value)
        }
    }

    /**
     * Discard changes and reload latest from disk.
     * Same as [loadOrCreate]
     * */
    fun reload() {
        value = loadOrCreate()
    }

    /**
     * Save the current value info disk.
     * */
    fun save() {
        synchronized(this) {
            configPath.writeText(json.encodeToString(serializer, value))
        }
    }
}
