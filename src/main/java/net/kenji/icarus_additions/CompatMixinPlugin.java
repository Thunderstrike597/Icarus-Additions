package net.kenji.icarus_additions;

import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CompatMixinPlugin implements IMixinConfigPlugin {

    /**
     * Checked here directly instead of via IcarusAdditions.EFM_LOADED: mixin plugins run during early bootstrap,
     * and touching the @Mod class this early would load it (and its imports) far too soon.
     */
    private static final boolean EPIC_FIGHT_LOADED = FMLLoader.getLoadingModList().getModFileById("epicfight") != null;


    @Override
    public List<String> getMixins() {
        List<String> mixins = new ArrayList<>();



        return mixins;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!EPIC_FIGHT_LOADED) {
            if (mixinClassName.endsWith("AbstractClientPlayerPatchMixin"))
                return false;
            if (mixinClassName.endsWith("PlayerPatchMixin"))
                return false;
        }
        return true;
    }

    // unused methods:
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> a, Set<String> b) {}
    @Override
    public void preApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {}
    @Override
    public void postApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {}
}