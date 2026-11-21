package io.github.jcondedata.aliveworkplace.compat.toms;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import org.jetbrains.annotations.Nullable;

/**
 * One block's inventory as a Tom's Simple Storage network reads it (its {@code BlockInventoryAccess}, given this
 * interface by {@code TomsBlockAccessMixin}): the Fabric item storage of the block face it looks at.
 */
public interface TomsBlockAccess {
	/** The storage Tom's reads there now; null when the block has none. */
	@Nullable
	Storage<ItemVariant> aliveworkplace$storage();
}
