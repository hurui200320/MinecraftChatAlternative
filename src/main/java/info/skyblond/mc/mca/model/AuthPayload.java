package info.skyblond.mc.mca.model;

import info.skyblond.mc.mca.MCAUtils;
import net.i2p.data.DataFormatException;
import net.i2p.data.Destination;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.encryption.PlayerPublicKey;
import net.minecraft.network.encryption.Signer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static info.skyblond.mc.mca.MinecraftChatAlternative.LOGGER;

public record AuthPayload(
        @NotNull UUID profileUUID,
        @NotNull PlayerPublicKey.PublicKeyData publicKeyData,
        @NotNull String username,
        @NotNull String destinationBase64,
        // Signature of username + dest
        @NotNull String signature
) {
    public static @Nullable AuthPayload sign(Destination dest) {
        var minecraft = MinecraftClient.getInstance();

        var session = minecraft.getSession();
        if (session == null) {
            LOGGER.warn("Empty session");
            return null;
        }
        var uuid = session.getUuidOrNull();
        if (uuid == null) {
            LOGGER.warn("Profile UUID not found");
            return null;
        }
        var username = MCAUtils.getCurrentUsername();
        if (username == null) {
            LOGGER.warn("Profile username not found");
            return null;
        }
        var profileKeys = minecraft.getProfileKeys();
        if (profileKeys == null) {
            LOGGER.warn("Profile keys not found");
            return null;
        }
        if (profileKeys.getPublicKey().isEmpty()) {
            LOGGER.warn("Profile public key is empty");
            return null;
        }
        Signer signer = profileKeys.getSigner();
        if (signer == null) {
            LOGGER.warn("Profile signer not found");
            return null;
        }
        var destBase64 = dest.toBase64();
        var payload = (username + destBase64).getBytes(StandardCharsets.UTF_8);
        byte[] signature = signer.sign(payload);
        return new AuthPayload(
                uuid, profileKeys.getPublicKey().get().data(),
                username, destBase64,
                Base64.getEncoder().encodeToString(signature));
    }

    /**
     * return true if verify failed.
     */
    public boolean verifyFailed() throws Exception {
        var minecraft = MinecraftClient.getInstance();
        try {
            var pubKey = PlayerPublicKey.verifyAndDecode(
                    minecraft.getServicesSignatureVerifier(),
                    this.profileUUID, this.publicKeyData,
                    PlayerPublicKey.EXPIRATION_GRACE_PERIOD);

            byte[] payload = (this.username + this.destinationBase64).getBytes(StandardCharsets.UTF_8);
            byte[] signature = Base64.getDecoder().decode(this.signature);
            var verifier = pubKey.createSignatureInstance();
            return !verifier.validate(payload, signature);
        } catch (PlayerPublicKey.PublicKeyException e) {
            throw new Exception("Failed to verify public key: " + e.getMessage());
        }
    }

    public @Nullable Destination parseDestination() {
        try {
            return new Destination(this.destinationBase64);
        } catch (DataFormatException e) {
            return null;
        }
    }
}
