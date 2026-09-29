package io.github.jcondedata.aliveworkplace.fossil;

import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Reviving fossils into Pokémon, for the Fossil Scientist. Filled in by {@code compat/cobblemon}. */
public interface FossilLab {
	Extension<FossilLab> EXTENSION = new Extension<>("fossil revival");

	boolean isFossil(ItemStack stack);

	/** The fossil these items make up, or null. */
	@Nullable
	ResourceLocation fossil(List<ItemStack> items);

	/** Whether these items are part of a fossil that needs more pieces. */
	boolean partOfOne(List<ItemStack> items);

	/** Revives {@code fossil} for {@code player}; the Pokémon's name, or null if that fossil is gone from the mod's data. */
	@Nullable
	Component revive(ServerPlayer player, ResourceLocation fossil);
}
