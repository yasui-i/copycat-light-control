package dev.local.copycatlight.mixin;

import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import dev.local.copycatlight.MaterialLighting;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CopycatBlockEntity.class)
abstract class CreateMaterialLightUpdateMixin {
    @Inject(method = {"updateLight", "setMaterial", "read"}, at = @At("RETURN"), remap = false)
    private void copycatlight$refreshCreate(CallbackInfo ci) {
        MaterialLighting.refresh((BlockEntity) (Object) this);
    }
}
