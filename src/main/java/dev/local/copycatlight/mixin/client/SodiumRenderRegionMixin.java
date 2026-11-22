package dev.local.copycatlight.mixin.client;

import dev.local.copycatlight.RenderLevelAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Read immutable published materials, rather than a live BE on Sodium workers. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
abstract class SodiumRenderRegionMixin implements RenderLevelAccess {
    @Shadow(remap = false) @Final private ClientLevel level;
    public Level copycatlight$level() { return level; }
}
