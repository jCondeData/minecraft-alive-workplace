package io.github.jcondedata.aliveworkplace.inn;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.craft.CuisineDishes;
import io.github.jcondedata.aliveworkplace.people.Diet;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The inn's drinks (ROADMAP 38.8): with Cobblecuisine, an Innkeeper serves each guest one of the drinks on the dish lists
 * ({@code "inn": true}: Bouncy Brionne Soda, Teddiursa Iced Coffee, Fruit Punch), once a stay: from the chests by her
 * counter, or else the village's store. The guest drinks it there, and it counts toward their diet as a drink.
 * Nothing new is saved: a guest whose last meals hold one of the drinks has had theirs.
 */
public final class InnDrinks {
	private InnDrinks() {
	}

	/** Serves the first guest at {@code counter} who hasn't had a drink yet; the drink served, or empty. */
	public static ItemStack serve(ServerLevel level, Villager innkeeper, BlockPos counter) {
		List<Item> drinks = CuisineDishes.drinks();
		if (drinks.isEmpty()) {
			return ItemStack.EMPTY;
		}
		Set<Item> wanted = Set.copyOf(drinks);
		for (Villager guest : Innkeepers.guests(level, counter)) {
			if (hadOne(guest, wanted)) {
				continue;
			}
			ItemStack drink = take(level, counter, wanted);
			if (drink.isEmpty()) {
				return ItemStack.EMPTY; // none in the chests or the store
			}
			Diet.ate(guest, drink);
			guest.heal(2f);
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, drink), guest.getX(), guest.getEyeY() - 0.2, guest.getZ(),
				6, 0.15, 0.1, 0.15, 0.05);
			level.playSound(null, guest.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.NEUTRAL, 0.7f, 1f);
			BuilderLevels.addXp(level, innkeeper, 1, null);
			return drink;
		}
		return ItemStack.EMPTY;
	}

	/** Whether {@code guest}'s last meals hold one of the inn's drinks. */
	static boolean hadOne(Villager guest, Set<Item> drinks) {
		for (ResourceLocation id : ModAttachments.RECENT_MEALS.getOrElse(guest, List.of())) {
			if (drinks.contains(BuiltInRegistries.ITEM.get(id))) {
				return true;
			}
		}
		return false;
	}

	/** One of the drinks from the chests by the counter, or else from the village's store. */
	private static ItemStack take(ServerLevel level, BlockPos counter, Set<Item> drinks) {
		ItemStack drink = SupplyContainers.takeOne(level, SupplyContainers.find(level, counter, null), s -> drinks.contains(s.getItem()));
		if (!drink.isEmpty()) {
			return drink;
		}
		BlockPos hall = io.github.jcondedata.aliveworkplace.hall.VillageHalls.nearest(level, counter).orElse(null);
		if (hall == null) {
			return ItemStack.EMPTY;
		}
		List<BlockPos> store = new ArrayList<>(io.github.jcondedata.aliveworkplace.hall.VillageNeeds.store(level, hall));
		return SupplyContainers.takeOne(level, store, s -> drinks.contains(s.getItem()));
	}
}
