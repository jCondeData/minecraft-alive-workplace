//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.CobblemonItemComponents;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.item.components.BaitEffectsComponent;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// The Habitat Keeper's Poké Snacks and wild Pokémon (ROADMAP 28.10). A snack is set out the way a player places one
// (the block's own setPlacedBy copies the snack's seasonings into its block entity), and is gone once the Pokémon have
// eaten it up. Wild Pokémon are found by an entity query; Alphas (Cobblemon 1.8) by their "alpha" aspect. Only touched
// when Cobblemon is installed.
public final class CobblemonHabitat implements HabitatKeepers.Snacks {
	@Override
	public boolean isSnack(ItemStack stack) {
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(HabitatKeepers.POKE_SNACK);
	}

	@Override
	public boolean isSnackBlock(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(HabitatKeepers.POKE_SNACK);
	}

	@Override
	public boolean setOut(ServerLevel level, BlockPos pos, ItemStack stack, Direction facing, LivingEntity placer) {
		Block block = BuiltInRegistries.BLOCK.getOptional(HabitatKeepers.POKE_SNACK).orElse(null);
		if (block == null || !isSnack(stack) || !level.getBlockState(pos).canBeReplaced()) {
			return false;
		}
		BlockState state = block.defaultBlockState();
		if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
			state = state.setValue(HorizontalDirectionalBlock.FACING, facing);
		}
		if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
			return false;
		}
		try {
			block.setPlacedBy(level, pos, state, placer, stack);
		} catch (RuntimeException | LinkageError e) {
			AliveWorkplace.LOG.warn("Alive Workplace: couldn't set out a Poké Snack at {}: {}", pos, e.toString());
		}
		return isSnackBlock(level.getBlockState(pos));
	}

	@Override
	public Set<ResourceLocation> seasonings(ItemStack stack) {
		return seasoningsOf(stack);
	}

	static Set<ResourceLocation> seasoningsOf(ItemStack stack) {
		try {
			BaitEffectsComponent effects = stack.get(CobblemonItemComponents.BAIT_EFFECTS);
			return effects == null ? Set.of() : new LinkedHashSet<>(effects.getEffects());
		} catch (LinkageError e) {
			return Set.of();
		}
	}

	@Override
	public List<HabitatKeepers.Wild> wild(ServerLevel level, AABB box) {
		List<HabitatKeepers.Wild> out = new ArrayList<>();
		for (PokemonEntity entity : level.getEntitiesOfClass(PokemonEntity.class, box, e -> e.isAlive() && !e.isBattling())) {
			Pokemon pokemon = entity.getPokemon();
			if (!pokemon.isWild()) {
				continue;
			}
			out.add(new HabitatKeepers.Wild(entity.getUUID(), pokemon.getSpecies().getResourceIdentifier(), pokemon.getSpecies().getTranslatedName(),
				pokemon.getShiny(), pokemon.getAspects().contains("alpha"), entity.blockPosition()));
		}
		return out;
	}
}
//?}
