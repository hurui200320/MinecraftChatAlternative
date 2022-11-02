package info.skyblond.mc.mca;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

/**
 * When handling commands, the Java type system fucked up,
 * and failed to work out the S in LiteralArgumentBuilder<S> should
 * be CommandSourceStack, rather than Object.
 * TODO this is a workaround.
 * */
public class MyArgumentBuilder extends LiteralArgumentBuilder<CommandSourceStack> {
    protected MyArgumentBuilder(String literal) {
        super(literal);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> literal(final String name) {
        return new MyArgumentBuilder(name);
    }
}
