package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Rolls one of Cobblemon Raid Dens' own loot tables ({@code cobblemonraiddens:raid/tier/<tier>}, 38.4). Its pools have
 * conditions that read the raid mod's own loot context (the Raid Pouch being opened), so the table can't be rolled with a
 * plain chest context: {@code compat/raiddens} fills this in when Raid Dens is installed. Without it there's no loot.
 */
public interface RaidDenLoot {
	Extension<RaidDenLoot> EXTENSION = new Extension<>("the raid dens' loot");

	/** The items {@code table} gives as if a Raid Pouch of it were opened, no bonus loot. */
	List<ItemStack> roll(ServerLevel level, ResourceKey<LootTable> table, RandomSource random);
}
