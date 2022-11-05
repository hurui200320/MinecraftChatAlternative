package info.skyblond.mc.mca.model;

import org.jetbrains.annotations.NotNull;

public record MessagePayload(
        @NotNull String type,
        @NotNull String content // how can you send null in chat?
) {
}
