package dev.local.copycatlight;

import net.minecraft.world.level.Level;

/** Gives a render region access to immutable material snapshots, not live BEs. */
public interface RenderLevelAccess {
    Level copycatlight$level();
}
