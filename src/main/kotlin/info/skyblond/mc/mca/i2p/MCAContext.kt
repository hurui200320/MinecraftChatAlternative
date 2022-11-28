package info.skyblond.mc.mca.i2p

import info.skyblond.i2p.p2p.chat.core.PeerInfo
import info.skyblond.i2p.p2p.chat.core.SessionContext
import info.skyblond.i2p.p2p.chat.core.SessionSource

class MCAContext(
    override val sessionSource: SessionSource
) : SessionContext {
    /**
     * Peer's username
     * */
    var username: String? = null
        private set

    override val nickname: String?
        get() = username

    override var peerInfo: PeerInfo? = null
        set(value) {
            username = value?.getUsername()
            field = value
        }

    private var authAccepted: Boolean = false

    override fun onAuthAccepted() {
        authAccepted = true
    }

    override fun isAccepted(): Boolean = authAccepted

}
