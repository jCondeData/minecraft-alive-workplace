package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.fossil.Fossil;
import com.cobblemon.mod.common.api.fossil.Fossils;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Cobblemon's fossils for the Fossil Scientist: which items make which fossil, and bringing one back to life. */
public final class CobblemonFossils {
	/** Whether {@code stack} is (part of) a fossil. */
	public static boolean isFossil(ItemStack stack) {
		return !stack.isEmpty() && Fossils.isFossilIngredient(stack);
	}

	/** The fossil these items make, or null (a Galar fossil takes two items). */
	@Nullable
	public static ResourceLocation fossil(List<ItemStack> items) {
		Fossil fossil = Fossils.getFossilByItemStacks(items);
		return fossil == null ? null : fossil.getIdentifier();
	}

	/** Whether these items are part of a fossil that needs more (half of a Galar fossil). */
	public static boolean partOfOne(List<ItemStack> items) {
		return Fossils.getSubFossilByItemStacks(items) != null;
	}

	/**
	 * Brings the fossil back to life the way Cobblemon's own machine does (its Pokémon properties, the revival event) and
	 * puts the Pokémon in the player's party, or their PC if the party is full. Returns its name, or null if there's no
	 * such fossil any more.
	 */
	@Nullable
	public static Component revive(ServerPlayer player, ResourceLocation id) {
		Fossil fossil = Fossils.getByIdentifier(id);
		if (fossil == null) {
			return null;
		}
		Pokemon pokemon = fossil.getResult().create(player);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		if (!party.add(pokemon)) {
			Cobblemon.INSTANCE.getStorage().getPC(player).add(pokemon);
		}
		com.cobblemon.mod.common.api.events.CobblemonEvents.FOSSIL_REVIVED.post(
			new com.cobblemon.mod.common.api.events.pokemon.FossilRevivedEvent(pokemon, player));
		return pokemon.getDisplayName(false);
	}

	private CobblemonFossils() {
	}
}
