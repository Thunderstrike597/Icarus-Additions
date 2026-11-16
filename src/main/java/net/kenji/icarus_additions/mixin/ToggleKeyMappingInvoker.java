package net.kenji.icarus_additions.mixin;

import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ToggleKeyMapping.class)
public interface ToggleKeyMappingInvoker {
    @Invoker("reset")
    void icarusAdditions$reset();
}