package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Digging the cellar of a build finished before its blueprint had one (ROADMAP 36.7). The Smithy, the Storehouse and the
 * Kitchen got their cellars in 26.15; the ones standing from before have ground where the cellar now goes. A finished
 * build whose blueprint has a basement, but whose basement is still mostly ground (more than half of the spots the
 * drawing leaves open down there are solid), is offered "Dig the cellar" where its upgrade is offered: its village's
 * builders sell the job (a blueprint of the build itself, lined up over it) and the Village Hall's advice says so. Given
 * to a builder, that blueprint starts a site that works only the cellar ({@link BuildSite#isCellarOnly}): it digs and
 * fits out the basement and the way down into it, and leaves the build above as it stands. Upgrading such a build digs
 * its cellar too, as the upgrade's plan clears its whole basement.
 */
public final class Cellars {
	/** Emeralds a builder asks for digging a cellar. */
	public static final int PRICE = 4;
	/** Most times the job can be bought before the builder restocks. */
	static final int MAX_USES = 2;

	/**
	 * Whether the finished {@code structure} at {@code placement} has a cellar still to dig: its blueprint reaches under the
	 * ground, and more than half of the open spots of its basement are solid in the world (unloaded: no).
	 */
	public static boolean needsDigging(ServerLevel level, ResourceLocation structure, BlueprintData.Placement placement) {
		Optional<Blueprint> blueprint = BlueprintLibrary.get(level, structure);
		if (blueprint.isEmpty() || blueprint.get().basement() == 0 || !placement.dimension().equals(Ids.of(level.dimension()))) {
			return false;
		}
		int open = 0;
		int solid = 0;
		for (Blueprint.Entry e : blueprint.get().blocks()) {
			if (e.pos().getY() >= 0 || !e.state().isAir()) {
				continue;
			}
			BlockPos world = world(placement, e.pos());
			if (!level.isLoaded(world)) {
				return false;
			}
			open++;
			BlockState there = level.getBlockState(world);
			if (!there.isAir() && !there.canBeReplaced()) {
				solid++;
			}
		}
		return open > 0 && solid * 2 > open;
	}

	/** {@link #needsDigging(ServerLevel, ResourceLocation, BlueprintData.Placement)} for a finished building. */
	public static boolean needsDigging(ServerLevel level, BuildSiteManager.Finished building) {
		return needsDigging(level, building.structure(), building.placement());
	}

	/** The finished buildings within {@code radius} of {@code pos} whose cellar is still to dig, none under construction. */
	public static List<BuildSiteManager.Finished> toDig(ServerLevel level, BlockPos pos, int radius) {
		BuildSiteManager sites = BuildSiteManager.get(level);
		List<BuildSiteManager.Finished> out = new ArrayList<>();
		for (BuildSiteManager.Finished f : sites.finishedNear(level, pos, radius)) {
			boolean building = sites.all().stream().anyMatch(s -> s.placement().equals(f.placement()));
			if (!building && needsDigging(level, f)) {
				out.add(f);
			}
		}
		return out;
	}

	/**
	 * Whether a site of {@code structure} at {@code placement} is a cellar dig: the same build stands finished on exactly
	 * that spot, its cellar still to dig (a blueprint of the build lined up over it, given to a builder).
	 */
	public static boolean isCellarDig(ServerLevel level, ResourceLocation structure, BlueprintData.Placement placement) {
		return BuildSiteManager.get(level).finishedIn(level).stream()
			.anyMatch(f -> f.structure().equals(structure) && f.placement().equals(placement))
			&& needsDigging(level, structure, placement);
	}

	/**
	 * {@code blueprint} with only its cellar: every block under its y = 0, and the way down through the ground floor (the
	 * hatch or the stair's top step and the opening over the steps, at y = 0, and a ladder that runs on up to y = 1) in
	 * the columns the basement lies under. Same size and basement, so it lines up as the whole build does.
	 */
	public static Blueprint cellarOnly(Blueprint blueprint) {
		Set<Long> columns = new HashSet<>();
		for (Blueprint.Entry e : blueprint.blocks()) {
			if (e.pos().getY() < 0) {
				columns.add(column(e.pos()));
			}
		}
		List<Blueprint.Entry> kept = new ArrayList<>();
		for (Blueprint.Entry e : blueprint.blocks()) {
			int y = e.pos().getY();
			BlockState s = e.state();
			boolean wayDown = columns.contains(column(e.pos())) && (y == 0
				&& (s.isAir() || s.is(Blocks.LADDER) || s.is(BlockTags.TRAPDOORS) || s.is(BlockTags.STAIRS))
				|| y == 1 && s.is(Blocks.LADDER));
			if (y < 0 || wayDown) {
				kept.add(e);
			}
		}
		return new Blueprint(blueprint.id(), blueprint.size(), List.copyOf(kept), List.of());
	}

	private static long column(BlockPos pos) {
		return BlockPos.asLong(pos.getX(), 0, pos.getZ());
	}

	private static BlockPos world(BlueprintData.Placement placement, BlockPos rel) {
		return placement.origin().offset(StructureTemplate.transform(rel, placement.mirror(), placement.rotation(), BlockPos.ZERO));
	}

	/**
	 * The builder's "Dig the cellar" trades: one for each finished building of the village round them (within the hall
	 * radius) whose cellar is still to dig, unless they sell it already. Returns the buildings a trade was added for.
	 * Called when they level up and when a player opens their trades, with the grander homes ({@code ModTrades}).
	 */
	public static List<BuildSiteManager.Finished> offer(ServerLevel level, Villager builder) {
		List<BuildSiteManager.Finished> added = new ArrayList<>();
		for (BuildSiteManager.Finished f : toDig(level, builder.blockPosition(), io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS)) {
			if (!sells(builder, f)) {
				builder.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, PRICE), job(level, f), MAX_USES, 5, 0.05f));
				added.add(f);
			}
		}
		return added;
	}

	/** Whether {@code villager} sells digging the cellar of {@code building} already. */
	public static boolean sells(Villager villager, BuildSiteManager.Finished building) {
		return villager.getOffers().stream().anyMatch(o -> BlueprintItem.data(o.getResult())
			.map(d -> d.structure().equals(building.structure()) && d.placement().equals(Optional.of(building.placement())))
			.orElse(false));
	}

	/** The job as a blueprint: the build itself, lined up over it, named "Dig the cellar: <build>". */
	public static ItemStack job(ServerLevel level, BuildSiteManager.Finished building) {
		ItemStack stack = BlueprintItem.create(building.structure(),
			BlueprintLibrary.get(level, building.structure()).map(Blueprint::size).orElse(null));
		BlueprintItem.data(stack).ifPresent(d -> stack.set(ModComponents.BLUEPRINT, d.withPlacement(Optional.of(building.placement()))));
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.aliveworkplace.blueprint.cellar",
			Blueprints.displayName(building.structure())).withStyle(s -> s.withItalic(false)));
		return stack;
	}

	private Cellars() {
	}
}
