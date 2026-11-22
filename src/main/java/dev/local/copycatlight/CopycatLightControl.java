package dev.local.copycatlight;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.minecraft.world.level.ChunkPos;

@Mod("copycat_light_control")
public final class CopycatLightControl {
    public CopycatLightControl() {
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Unload event) -> {
            if (event.getLevel() instanceof LightCacheAccess cache) {
                ChunkPos pos = event.getChunk().getPos();
                cache.copycatlight$profiles().remove(pos.toLong());
            }
        });
    }
}
