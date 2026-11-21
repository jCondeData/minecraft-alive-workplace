package io.github.jcondedata.aliveworkplace.compat.toms.mixin;

import io.github.jcondedata.aliveworkplace.compat.toms.TomsBlockAccess;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/**
 * B1025: lets {@code TomsMerge} ask a Tom's Simple Storage block reader for the storage it reads. By name, without
 * Tom's classes on the compile path (the mod is optional; {@link TomsMixinPlugin} applies this only with it installed).
 */
@Pseudo
@Mixin(targets = "com.tom.storagemod.inventory.PlatformInventoryAccess$BlockInventoryAccess", remap = false)
public abstract class TomsBlockAccessMixin implements TomsBlockAccess {
	@Shadow
	public abstract Storage<ItemVariant> get();

	@Override
	public Storage<ItemVariant> aliveworkplace$storage() {
		return get();
	}
}
