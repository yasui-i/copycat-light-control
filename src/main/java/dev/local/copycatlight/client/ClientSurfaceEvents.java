package dev.local.copycatlight.client;

import dev.local.copycatlight.RenderInvalidation;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.minecraft.world.level.Level;

@EventBusSubscriber(modid = "copycat_light_control", value = Dist.CLIENT)
public final class ClientSurfaceEvents {
    private static final boolean HAS_SABLE = LoadingModList.get().getModFileById("sable") != null;
    static {
        if (HAS_SABLE) RenderInvalidation.listener = SableSurfaceLighting::changed;
    }
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (HAS_SABLE) SableSurfaceLighting.tick(Minecraft.getInstance().level);
    }
    @SubscribeEvent
    public static void loaded(ChunkEvent.Load event) { chunkChanged(event); }
    @SubscribeEvent
    public static void unloaded(ChunkEvent.Unload event) { chunkChanged(event); }
    private static void chunkChanged(ChunkEvent event) {
        if (HAS_SABLE && event.getLevel() instanceof Level level && level.isClientSide)
            SableSurfaceLighting.chunkChanged(level, event.getChunk().getPos());
    }
}
