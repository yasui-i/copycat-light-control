package dev.local.copycatlight.mixin.client;

import dev.local.copycatlight.RenderLevelAccess;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RenderChunkRegion.class)
abstract class SurfaceRenderRegionMixin implements RenderLevelAccess {
    @Shadow @Final protected Level level;
    public Level copycatlight$level() { return level; }
}
