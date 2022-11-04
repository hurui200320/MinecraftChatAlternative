package info.skyblond.mc.mca.model;

import info.skyblond.mc.mca.MCAUtils;
import info.skyblond.mc.mca.MinecraftChatAlternative;
import net.i2p.data.DataFormatException;
import net.i2p.data.Destination;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.encryption.PlayerPublicKey;
import net.minecraft.network.encryption.Signer;

import java.util.Base64;
import java.util.UUID;

import static info.skyblond.mc.mca.MinecraftChatAlternative.LOGGER;

public record AuthPayload(
        UUID profileUUID,
        PlayerPublicKey.PublicKeyData publicKeyData,
        String destinationBase64,
        String signatureOfDestinationBase64
) {
    public static AuthPayload sign(Destination dest) {
        var minecraft = MinecraftClient.getInstance();
        var uuid = MCAUtils.getCurrentPlayerUUID();
        if (uuid == null) {
            LOGGER.warn("Profile UUID not found");
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
        var payload = dest.toByteArray();
        byte[] signature = signer.sign(payload);
        return new AuthPayload(
                uuid, profileKeys.getPublicKey().get().data(),
                dest.toBase64(), Base64.getEncoder().encodeToString(signature)
        );
    }

    public boolean verify() throws Exception {
        var minecraft = MinecraftClient.getInstance();
        try {
            var pubKey = PlayerPublicKey.verifyAndDecode(
                    minecraft.getServicesSignatureVerifier(),
                    this.profileUUID, this.publicKeyData,
                    PlayerPublicKey.EXPIRATION_GRACE_PERIOD);

            byte[] payload = new Destination(this.destinationBase64).toByteArray();
            byte[] signature = Base64.getDecoder().decode(this.signatureOfDestinationBase64);
            var verifier = pubKey.createSignatureInstance();
            return verifier.validate(payload, signature);
        } catch (PlayerPublicKey.PublicKeyException e) {
            throw new Exception("Failed to verify public key: " + e.getMessage());
        }
    }

    public Destination parseDestination() {
        try {
            return new Destination(this.destinationBase64);
        } catch (DataFormatException e) {
            return null;
        }
    }
}
