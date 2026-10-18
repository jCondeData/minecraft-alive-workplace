package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.craft.CuisineDishes;
import io.github.jcondedata.aliveworkplace.craft.LuxuryWork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Partner treats (ROADMAP 38.10, Cobblecuisine): once a game day, each Pokémon helping a villager at work (as
 * {@link Partners#countDay} counts it) is fed one {@link #TREATS} item, a Poké Puff or a shake, from the village store
 * ({@link LuxuryWork#store}: its hall's kitchens and storehouses, or without a hall the storehouses near the
 * workstation). That is its treat for the day: {@link #FRIENDSHIP} more friendship, with hearts, the same as the
 * Rancher's berry treat. With nothing in the store it gets none. The day is kept in the Pokémon's own saved data
 * ({@code aliveworkplace_treat_day}, absent: never). The tag lists Cobblecuisine's items as optional entries, so
 * without the mod it is empty and nothing is fed. Switch {@code cuisineDishes} (behind M38's gate).
 */
public final class PartnerTreats {
	/** Cobblecuisine's Poké Puffs and shakes (optional entries). */
	public static final TagKey<Item> TREATS = TagKey.create(Registries.ITEM, AliveWorkplace.id("partner_treats"));
	/** Friendship a treat adds (as the Rancher's berry treat, {@code RancherWork.TREAT}). */
	public static final int FRIENDSHIP = 6;

	/** Whether {@code stack} is a partner treat. */
	public static boolean isTreat(ItemStack stack) {
		return !stack.isEmpty() && stack.is(TREATS);
	}

	/**
	 * Feeds each of up to {@code max} Pokémon of {@code types} helping at {@code site} not yet fed on {@code day} one
	 * treat from the store; how many were fed.
	 */
	public static int feed(ServerLevel level, BlockPos site, Set<String> types, int max, long day) {
		if (!CuisineDishes.ENABLED) {
			return 0;
		}
		List<Entity> helping = PokemonPartners.EXTENSION.call(p -> p.helping(level, site, Partners.RADIUS, types, max), List.<Entity>of());
		List<Entity> hungry = helping.stream().filter(e -> PokemonPartners.EXTENSION.call(p -> p.treatDay(e), -1L) < day).toList();
		if (hungry.isEmpty()) {
			return 0;
		}
		List<BlockPos> store = LuxuryWork.store(level, site);
		int fed = 0;
		for (Entity pokemon : hungry) {
			ItemStack treat = SupplyContainers.takeOne(level, store, PartnerTreats::isTreat);
			if (treat.isEmpty()) {
				break; // the store has none left: no treat today
			}
			PokemonPartners.EXTENSION.call(p -> p.befriend(level, pokemon, FRIENDSHIP), false);
			PokemonPartners.EXTENSION.run(p -> p.setTreatDay(pokemon, day));
			fed++;
		}
		return fed;
	}

	private PartnerTreats() {
	}
}
