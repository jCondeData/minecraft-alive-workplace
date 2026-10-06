package io.github.jcondedata.aliveworkplace.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Item frames, paintings and leash knots in a structure template keep the block they hang on in TileX/TileY/TileZ, saved
 * where the template was made (vanilla) or relative to it (our generated ones, tools/blueprints). Placing a template moves
 * only their "Pos", so loading them logs "Block-attached entity at invalid position" whenever that block is more than 16
 * blocks away (B68: every traveller's camp). This points the saved block at where it lands, the same transformed block
 * vanilla already uses for the entity's bounding-box check, so the entity loads cleanly where it would have ended up.
 */
@Mixin(StructureTemplate.class)
public abstract class StructureTemplateMixin {
	@ModifyArg(method = "placeEntities", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;createEntityIgnoreException(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/Optional;"),
		index = 1)
	private CompoundTag aliveworkplace$attachWhereItLands(CompoundTag tag, @Local(ordinal = 2) BlockPos landed) {
		if (tag.contains("TileX") && tag.contains("TileY") && tag.contains("TileZ")) {
			tag.putInt("TileX", landed.getX());
			tag.putInt("TileY", landed.getY());
			tag.putInt("TileZ", landed.getZ());
		}
		return tag;
	}
}
