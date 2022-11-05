package info.skyblond.mc.mca.model;

import org.jetbrains.annotations.NotNull;

public record MCAPlatform(
        @NotNull String platformDisplayName,
        @NotNull String platformCodeName
) {
    public static MCAPlatform MCA = new MCAPlatform("MCA", "mca");
    public static MCAPlatform I2P = new MCAPlatform("I2P", "i2p");
    public static MCAPlatform MC = new MCAPlatform("MC", "mc");
    // TODO: Some interface for more flexibility?
}
