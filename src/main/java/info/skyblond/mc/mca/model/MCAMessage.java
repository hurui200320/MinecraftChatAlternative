package info.skyblond.mc.mca.model;

import com.google.gson.Gson;

import java.util.Arrays;

public record MCAMessage(
        String action,
        String payload
) {
    public enum Action {
        BROADCAST("broadcast"), WHISPER("whisper"), PEER_EXCHANGE("peer-exchange"),
        UNKNOWN(null);

        public final String name;

        Action(String name) {
            this.name = name;
        }
    }

    public Action parseAction() {
        return Arrays.stream(Action.values())
                .filter(it -> it.name.equals(action))
                .findAny()
                .orElse(Action.UNKNOWN);
    }

    public <T> T parseJsonPayload(Gson gson, Class<T> type) {
        return gson.fromJson(this.payload, type);
    }
}
