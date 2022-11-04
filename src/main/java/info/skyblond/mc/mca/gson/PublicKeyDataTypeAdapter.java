package info.skyblond.mc.mca.gson;

import com.google.gson.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.encryption.PlayerPublicKey;

import java.lang.reflect.Type;
import java.util.Base64;

public class PublicKeyDataTypeAdapter implements JsonSerializer<PlayerPublicKey.PublicKeyData>, JsonDeserializer<PlayerPublicKey.PublicKeyData> {
    @Override
    public JsonElement serialize(PlayerPublicKey.PublicKeyData src, Type typeOfSrc, JsonSerializationContext context) {
        var byteBuf = Unpooled.buffer();
        src.write(new PacketByteBuf(byteBuf));
        var bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);
        return new JsonPrimitive(Base64.getEncoder().encodeToString(bytes));
    }

    @Override
    public PlayerPublicKey.PublicKeyData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        var bytes = Base64.getDecoder().decode(json.getAsString());
        var byteBuf = Unpooled.buffer(bytes.length);
        byteBuf.writeBytes(bytes);
        return new PlayerPublicKey.PublicKeyData(new PacketByteBuf(byteBuf));
    }

}
