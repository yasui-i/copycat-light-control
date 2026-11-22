package dev.local.copycatlight.mixin;

import dev.local.copycatlight.LightCacheAccess;
import dev.local.copycatlight.MaterialLighting;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Mixin(Level.class)
abstract class LevelLightCacheMixin implements LightCacheAccess {
    @Unique
    private final ConcurrentMap<Long, ConcurrentMap<Long, MaterialLighting.Profile>> copycatlight$cache = new ConcurrentHashMap<>();

    @Override
    public ConcurrentMap<Long, ConcurrentMap<Long, MaterialLighting.Profile>> copycatlight$profiles() {
        return copycatlight$cache;
    }
}
