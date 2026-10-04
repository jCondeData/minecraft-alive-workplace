//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.api.berry.Berries;
import com.cobblemon.mod.common.api.berry.Berry;
import io.github.jcondedata.aliveworkplace.berry.BerryChains;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

// Cobblemon's berry data for the Berry Breeder (ROADMAP 28.9): every berry and its mutations, read from the loaded
// berry registry, so a data pack's berries come too. Only touched when Cobblemon is installed.
public final class CobblemonBerries implements BerryChains.BerryData {
	@Override
	public List<ResourceLocation> berries() {
		List<ResourceLocation> ids = new ArrayList<>();
		for (Berry berry : Berries.all()) {
			ids.add(berry.getIdentifier());
		}
		return ids;
	}

	@Override
	public List<BerryChains.Mutation> mutations() {
		List<BerryChains.Mutation> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (Berry berry : Berries.all()) {
			for (Map.Entry<ResourceLocation, ResourceLocation> e : berry.getMutations().entrySet()) {
				ResourceLocation a = berry.getIdentifier();
				ResourceLocation b = e.getKey();
				// each pair once, whichever berry lists it
				String key = a.compareTo(b) <= 0 ? a + "+" + b : b + "+" + a;
				if (seen.add(key)) {
					out.add(a.compareTo(b) <= 0 ? new BerryChains.Mutation(a, b, e.getValue()) : new BerryChains.Mutation(b, a, e.getValue()));
				}
			}
		}
		return out;
	}

	@Override
	public ResourceLocation berryOf(net.minecraft.world.item.ItemStack stack) {
		return stack.getItem() instanceof com.cobblemon.mod.common.item.berry.BerryItem item ? item.berry().getIdentifier() : null;
	}

	@Override
	public net.minecraft.world.item.ItemStack item(ResourceLocation id) {
		Berry berry = Berries.getByIdentifier(id);
		return berry == null ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(berry.item());
	}

	@Override
	public ResourceLocation plantOf(net.minecraft.world.level.block.state.BlockState state) {
		return state.getBlock() instanceof com.cobblemon.mod.common.block.BerryBlock block && block.berry() != null
			? block.berry().getIdentifier() : null;
	}

	@Override
	public boolean ripe(net.minecraft.world.level.block.state.BlockState state) {
		return state.getBlock() instanceof com.cobblemon.mod.common.block.BerryBlock
			&& state.getValue(com.cobblemon.mod.common.block.BerryBlock.Companion.getAGE()) >= com.cobblemon.mod.common.block.BerryBlock.FRUIT_AGE;
	}

	@Override
	public List<net.minecraft.world.item.ItemStack> pick(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
		net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
		if (ripe(state) && level.getBlockEntity(pos) instanceof com.cobblemon.mod.common.block.entity.BerryBlockEntity plant) {
			return new ArrayList<>(plant.harvest(level, state, pos));
		}
		return List.of();
	}

	// Growth Mulch (quicker growth) and Surprise Mulch (mutations four times as likely): the two the breeder uses.
	private static com.cobblemon.mod.common.api.mulch.MulchVariant variant(net.minecraft.world.item.ItemStack stack) {
		if (stack.getItem() instanceof com.cobblemon.mod.common.item.MulchItem mulch) {
			com.cobblemon.mod.common.api.mulch.MulchVariant v = mulch.getVariant();
			if (v == com.cobblemon.mod.common.api.mulch.MulchVariant.GROWTH || v == com.cobblemon.mod.common.api.mulch.MulchVariant.SURPRISE) {
				return v;
			}
		}
		return null;
	}

	@Override
	public boolean isMulch(net.minecraft.world.item.ItemStack stack) {
		return variant(stack) != null;
	}

	@Override
	public boolean mulch(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.item.ItemStack stack) {
		com.cobblemon.mod.common.api.mulch.MulchVariant v = variant(stack);
		net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
		if (v == null || !(state.getBlock() instanceof com.cobblemon.mod.common.block.BerryBlock block) || !block.canHaveMulchApplied(level, pos, state, v)) {
			return false;
		}
		block.applyMulch(level, level.random, pos, state, v);
		stack.shrink(1);
		return true;
	}

	@Override
	public boolean mulched(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof com.cobblemon.mod.common.block.entity.BerryBlockEntity plant) {
			return plant.getMulchVariant() != com.cobblemon.mod.common.api.mulch.MulchVariant.NONE;
		}
		return false;
	}

	@Override
	public void ripen(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.util.RandomSource random) {
		for (int i = 0; i < 8; i++) {
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof com.cobblemon.mod.common.block.BerryBlock block) || ripe(state)) {
				return;
			}
			block.growHelper(level, random, pos, state, false);
		}
	}
}
//?}
