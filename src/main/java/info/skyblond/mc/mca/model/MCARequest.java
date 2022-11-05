package info.skyblond.mc.mca.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;

public record MCARequest(
        @NotNull String action,
        @NotNull String payload // use "null" for null value
) {
    public static <T> MCARequest create(Gson gson, String action, T payload) {
        return new MCARequest(action, gson.toJson(payload));
    }

    public <T> T parseJsonPayload(Gson gson, Class<T> type) {
        return gson.fromJson(this.payload, type);
    }

    public <T> T parseJsonPayload(Gson gson, TypeToken<T> typeOfT) {
        return gson.fromJson(this.payload, typeOfT);
    }

    public String toJson(Gson gson) {
        return gson.toJson(this);
    }
}
