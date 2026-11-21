package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.WellKeeperWork;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * QA lane B (2026-11-21), the shops rebuilt in 50.11 (Flower Shop I-II, Supply Shop I-IV, Market Stall I-III) and the
 * water builds rebuilt in 52.1 (Well I-II, Well House, Fountain, Lily Pond, Footbridge), written from their blind briefs
 * ({@code tools/blueprints/briefs/<family>.md}), not from the designers' recipes.
 *
 * <p>The walk is a villager's, as in {@code QaRebuiltWorkshops1121bGameTests}: wooden doors open, a fence gate stays shut,
 * nobody stands on a fence or wall or in water, and it starts in the street in front (the z = 0 side). A job block counts
 * as reached when a villager can stand right beside it; a store when a player stands within reach of it (2 across, 1 up or
 * down). Water is checked twice: in the drawing (every water cell a source, held in on every side and below), and in the
 * world (each build placed on flat ground, its water ticked: every drawn source still a source, no water anywhere else).
 */
public class QaRebuiltShopsWater1121bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";

	private static final List<StarterBlueprints.Entry> FLOWER = List.of(StarterBlueprints.FLOWER_SHOP, StarterBlueprints.FLOWER_SHOP_2);
	private static final List<StarterBlueprints.Entry> SUPPLY = List.of(StarterBlueprints.SUPPLY_SHOP, StarterBlueprints.SUPPLY_SHOP_2,
		StarterBlueprints.SUPPLY_SHOP_3, StarterBlueprints.SUPPLY_SHOP_4);
	private static final List<StarterBlueprints.Entry> STALL = List.of(StarterBlueprints.MARKET_STALL, StarterBlueprints.MARKET_STALL_2,
		StarterBlueprints.MARKET_STALL_3);
	private static final List<StarterBlueprints.Entry> WATER = List.of(StarterBlueprints.WELL, StarterBlueprints.WELL_2, StarterBlueprints.WELL_HOUSE,
		StarterBlueprints.FOUNTAIN, StarterBlueprints.LILY_POND, StarterBlueprints.FOOTBRIDGE);

	// ---- drawings -----------------------------------------------------------------------------------------------------

	private static Blueprint blueprint(GameTestHelper helper, StarterBlueprints.Entry entry) {
		return BlueprintLibrary.get(helper.getLevel(), entry.id()).orElseThrow(() -> new AssertionError("missing " + entry.id()));
	}

	/** The drawing's cells, its basement (if any) below y = 0, so y = 0 is the ground floor in every level. */
	private static Map<BlockPos, BlockState> cells(Blueprint blueprint) {
		return QaBasementGameTests.blocks(blueprint);
	}

	/** A drawn cell; outside the drawing, air above the ground and dirt below it. */
	private static BlockState at(Map<BlockPos, BlockState> cells, BlockPos pos) {
		return QaBasementGameTests.at(cells, pos);
	}

	private static String n(StarterBlueprints.Entry entry) {
		return entry.id().getPath();
	}

	private static String name(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
	}

	private static List<BlockPos> where(Map<BlockPos, BlockState> cells, Block block) {
		return cells.entrySet().stream().filter(e -> e.getValue().is(block)).map(Map.Entry::getKey).sorted().toList();
	}

	/** Every block whose block entity holds items (chests, barrels, pots, chiseled shelves, furnaces...), the counter aside. */
	private static List<BlockPos> stores(Map<BlockPos, BlockState> cells) {
		List<BlockPos> out = new ArrayList<>();
		cells.forEach((p, s) -> {
			if (!s.is(ModBlocks.SHOP_COUNTER) && holdsItems(s)) {
				out.add(p);
			}
		});
		out.sort(null);
		return out;
	}

	private static boolean holdsItems(BlockState s) {
		if (!(s.getBlock() instanceof EntityBlock eb)) {
			return false;
		}
		BlockEntity be = eb.newBlockEntity(BlockPos.ZERO, s);
		return be instanceof Container;
	}

	/** Every job site a villager is drawn to, the bell included (not a bed, lightning rod, portal, lodestone or hive). */
	private static Map<BlockPos, BlockState> jobSites(Map<BlockPos, BlockState> cells) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		cells.forEach((p, s) -> {
			if (PoiTypes.forState(s).filter(h -> !h.is(PoiTypes.HOME) && !h.is(PoiTypes.LIGHTNING_ROD) && !h.is(PoiTypes.NETHER_PORTAL)
				&& !h.is(PoiTypes.LODESTONE) && !h.is(PoiTypes.BEEHIVE) && !h.is(PoiTypes.BEE_NEST)).isPresent()) {
				out.put(p, s);
			}
		});
		return out;
	}

	private static String shortList(Map<BlockPos, BlockState> m) {
		List<String> out = new ArrayList<>();
		m.forEach((p, s) -> out.add(name(s) + " " + p.toShortString()));
		out.sort(null);
		return out.toString();
	}

	static boolean within(BlockPos a, BlockPos b, int across, int vertical) {
		return Math.abs(a.getX() - b.getX()) <= across && Math.abs(a.getZ() - b.getZ()) <= across && Math.abs(a.getY() - b.getY()) <= vertical;
	}

	private static boolean water(BlockState s) {
		return s.getFluidState().is(FluidTags.WATER);
	}

	// ---- a villager's walk ------------------------------------------------------------------------------------------

	private static boolean open(BlockState state, boolean player) {
		if (state.getBlock() instanceof DoorBlock && (player || !state.is(Blocks.IRON_DOOR))) {
			return true;
		}
		if (player && (state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock)) {
			return true;
		}
		if (state.is(Blocks.LADDER)) {
			return true;
		}
		var shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.2;
	}

	private static boolean noFloor(BlockState state) {
		return state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock || state.getBlock() instanceof FenceGateBlock;
	}

	private static boolean standable(Map<BlockPos, BlockState> cells, BlockPos feet, boolean player) {
		BlockState here = at(cells, feet);
		if (!open(here, player) || !open(at(cells, feet.above()), player) || !here.getFluidState().isEmpty()) {
			return false;
		}
		BlockState below = at(cells, feet.below());
		return here.is(Blocks.LADDER) || below.is(Blocks.LADDER) || (!open(below, false) && !noFloor(below) && below.getFluidState().isEmpty());
	}

	/** Every spot reached on foot from the street in front: level, a step up, up to three down, ladders. */
	private static Set<BlockPos> walk(Blueprint blueprint, Map<BlockPos, BlockState> cells, boolean player) {
		return walk(blueprint, cells, player, -2, blueprint.size().getX() + 1);
	}

	/** The same walk, kept between columns {@code minX} and {@code maxX} (a crossing that may not go round the build). */
	private static Set<BlockPos> walk(Blueprint blueprint, Map<BlockPos, BlockState> cells, boolean player, int minX, int maxX) {
		int sz = blueprint.size().getZ();
		int top = blueprint.size().getY();
		Set<BlockPos> reached = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (int x = minX; x <= maxX; x++) {
			for (int z = -2; z <= -1; z++) {
				BlockPos s = new BlockPos(x, 0, z);
				if (standable(cells, s, player) && reached.add(s)) {
					queue.add(s);
				}
			}
		}
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			List<BlockPos> next = new ArrayList<>();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos side = pos.relative(d);
				next.add(side);
				if (open(at(cells, pos.above(2)), player)) {
					next.add(side.above());
				}
				for (int drop = 1; drop <= 3; drop++) {
					if (!open(at(cells, side.below(drop - 1)), player) || !open(at(cells, side.below(drop - 1).above()), player)) {
						break;
					}
					next.add(side.below(drop));
				}
			}
			if (at(cells, pos).is(Blocks.LADDER) || at(cells, pos.above()).is(Blocks.LADDER)) {
				next.add(pos.above());
			}
			if (at(cells, pos.below()).is(Blocks.LADDER)) {
				next.add(pos.below());
			}
			for (BlockPos nx : next) {
				if (nx.getX() < minX || nx.getX() > maxX || nx.getZ() < -2 || nx.getZ() > sz + 1 || nx.getY() < -8 || nx.getY() > top) {
					continue;
				}
				if (!reached.contains(nx) && standable(cells, nx, player)) {
					reached.add(nx);
					queue.add(nx);
				}
			}
		}
		return reached;
	}

	private static boolean beside(Set<BlockPos> reached, BlockPos target) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (reached.contains(target.relative(d)) || reached.contains(target.relative(d).below())) {
				return true;
			}
		}
		return false;
	}

	private static boolean near(Set<BlockPos> reached, BlockPos target, int across) {
		for (BlockPos p : BlockPos.betweenClosed(target.offset(-across, -1, -across), target.offset(across, 1, across))) {
			if (reached.contains(p)) {
				return true;
			}
		}
		return false;
	}

	/** A walked spot from which a villager's eyes are within the Walker's reach (3.0, less its 0.3 margin) of {@code water}. */
	private static boolean dipReach(Set<BlockPos> reached, BlockPos water) {
		Vec3 c = Vec3.atCenterOf(water);
		for (BlockPos p : reached) {
			if (new Vec3(p.getX() + 0.5, p.getY() + 1.62, p.getZ() + 0.5).distanceTo(c) <= 2.7) {
				return true;
			}
		}
		return false;
	}

	// ---- the shops, from the briefs ---------------------------------------------------------------------------------

	/**
	 * flower_shop.md, supply_shop.md, market_stall.md, "Job block": Flower Shop I and II exactly one composter and no other
	 * job block of any kind (bell included); Supply Shop I-IV exactly one Shop Counter each; Market Stall I none, II and III
	 * exactly one Shop Counter. Each of those job blocks has a spot right beside it a villager walks to from the street in
	 * front (wooden doors open, gates shut). And no Market Stall level has a bed (the stall's residents: none, at any level).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryShopLevelHasItsJobBlockAndAVillagerWalksUpToIt(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int checked = 0;
		Map<StarterBlueprints.Entry, Block> job = new LinkedHashMap<>();
		Map<StarterBlueprints.Entry, Integer> count = new LinkedHashMap<>();
		FLOWER.forEach(e -> {
			job.put(e, Blocks.COMPOSTER);
			count.put(e, 1);
		});
		SUPPLY.forEach(e -> {
			job.put(e, ModBlocks.SHOP_COUNTER);
			count.put(e, 1);
		});
		for (int i = 0; i < STALL.size(); i++) {
			job.put(STALL.get(i), ModBlocks.SHOP_COUNTER);
			count.put(STALL.get(i), i == 0 ? 0 : 1);
		}
		for (Map.Entry<StarterBlueprints.Entry, Block> e : job.entrySet()) {
			Blueprint blueprint = blueprint(helper, e.getKey());
			Map<BlockPos, BlockState> cells = cells(blueprint);
			String n = n(e.getKey());
			List<BlockPos> found = where(cells, e.getValue());
			if (found.size() != count.get(e.getKey())) {
				problems.add(n + ": " + found.size() + " " + name(e.getValue().defaultBlockState()) + " " + found + ", the brief says " + count.get(e.getKey()));
			}
			Set<BlockPos> reached = walk(blueprint, cells, false);
			for (BlockPos p : found) {
				checked++;
				if (!beside(reached, p)) {
					problems.add(n + ": the " + name(e.getValue().defaultBlockState()) + " at " + p.toShortString()
						+ " has no spot beside it a villager walks to from the street (doors open, gates shut)");
				}
			}
			if (FLOWER.contains(e.getKey())) {
				Map<BlockPos, BlockState> others = jobSites(cells);
				others.values().removeIf(s -> s.is(Blocks.COMPOSTER));
				if (!others.isEmpty()) {
					problems.add(n + ": other job blocks " + shortList(others));
				}
			}
			if (STALL.contains(e.getKey())) {
				long beds = cells.values().stream().filter(s -> s.getBlock() instanceof BedBlock).count();
				if (beds > 0) {
					problems.add(n + ": " + beds + " bed blocks, the stall has no residents");
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.assertTrue(checked == 8, "(setup) " + checked + " job blocks checked, want 8");
		helper.succeed();
	}

	/**
	 * The briefs' "Storage": Flower Shop I and II a chest within 8 across and 4 up or down of the composter (or the Florist
	 * does nothing) and no barrel; Supply Shop I exactly 8 item-holding blocks besides the counter, every one within 8 / 4 of
	 * it; Market Stall II at least one store and III more than II, every one within 8 / 4 of the counter, and nothing solid
	 * on a chest's lid. In every level of the three families every chest and barrel can be walked up to from the street (a
	 * player's walk, every door, trapdoor and gate opened).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryShopsStoresAreInTheCountersReachAndCanBeWalkedTo(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (StarterBlueprints.Entry e : FLOWER) {
			Map<BlockPos, BlockState> cells = cells(blueprint(helper, e));
			List<BlockPos> chests = cells.entrySet().stream().filter(c -> c.getValue().getBlock() instanceof ChestBlock).map(Map.Entry::getKey).toList();
			for (BlockPos composter : where(cells, Blocks.COMPOSTER)) {
				if (chests.stream().noneMatch(c -> within(c, composter, 8, 4))) {
					problems.add(n(e) + ": no chest within 8 / 4 of the composter " + composter.toShortString() + " (chests " + chests + ")");
				}
			}
			if (!where(cells, Blocks.BARREL).isEmpty()) {
				problems.add(n(e) + ": barrels (a Fisherman's job block) " + where(cells, Blocks.BARREL));
			}
		}
		Map<BlockPos, BlockState> s1 = cells(blueprint(helper, StarterBlueprints.SUPPLY_SHOP));
		List<BlockPos> stock = stores(s1);
		List<BlockPos> counters = where(s1, ModBlocks.SHOP_COUNTER);
		if (stock.size() != 8) {
			problems.add("supply_shop: " + stock.size() + " item-holding blocks besides the counter " + shortList(only(s1, stock)) + ", the brief says exactly 8");
		}
		if (counters.size() == 1) {
			List<BlockPos> far = stock.stream().filter(c -> !within(c, counters.get(0), 8, 4)).toList();
			if (!far.isEmpty()) {
				problems.add("supply_shop: stock out of the counter's reach (8 / 4 of " + counters.get(0).toShortString() + ") " + far);
			}
		}
		int before = 0;
		for (int i = 1; i < STALL.size(); i++) {
			StarterBlueprints.Entry e = STALL.get(i);
			Map<BlockPos, BlockState> cells = cells(blueprint(helper, e));
			List<BlockPos> st = stores(cells);
			List<BlockPos> c = where(cells, ModBlocks.SHOP_COUNTER);
			int distinct = (int) st.stream().filter(p -> !(at(cells, p).getBlock() instanceof ChestBlock)
				|| at(cells, p).getValue(ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.RIGHT).count();
			if (distinct < 1 || distinct <= before) {
				problems.add(n(e) + ": " + distinct + " stores (a double chest one), want at least 1 and more than the level below's " + before);
			}
			before = distinct;
			if (c.size() == 1) {
				List<BlockPos> far = st.stream().filter(p -> !within(p, c.get(0), 8, 4)).toList();
				if (!far.isEmpty()) {
					problems.add(n(e) + ": stores out of the counter's reach (8 / 4 of " + c.get(0).toShortString() + ") " + far);
				}
			}
			for (BlockPos p : st) {
				BlockState lid = at(cells, p.above());
				if (at(cells, p).getBlock() instanceof ChestBlock && lid.isRedstoneConductor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
					problems.add(n(e) + ": the chest at " + p.toShortString() + " has " + name(lid) + " on its lid (it won't open)");
				}
			}
		}
		for (List<StarterBlueprints.Entry> family : List.of(FLOWER, SUPPLY, STALL)) {
			for (StarterBlueprints.Entry e : family) {
				Blueprint blueprint = blueprint(helper, e);
				Map<BlockPos, BlockState> cells = cells(blueprint);
				Set<BlockPos> reached = walk(blueprint, cells, true);
				List<String> cut = cells.entrySet().stream().filter(c -> c.getValue().getBlock() instanceof ChestBlock || c.getValue().getBlock() instanceof BarrelBlock)
					.map(Map.Entry::getKey).filter(c -> !near(reached, c, 2)).sorted().map(BlockPos::toShortString).toList();
				if (!cut.isEmpty()) {
					problems.add(n(e) + ": chests or barrels nobody walks up to from the street " + cut);
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	private static Map<BlockPos, BlockState> only(Map<BlockPos, BlockState> cells, List<BlockPos> keys) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		keys.forEach(k -> out.put(k, cells.get(k)));
		return out;
	}

	/**
	 * The briefs' "Kept cells": every upgrade (Flower Shop I to II, Supply Shop I to IV, Market Stall I to III) keeps the
	 * level below's job blocks and item-holding blocks in the same cell as the same block (a builder never breaks them, so a
	 * moved one would stay behind and the player's stock would stand in the wrong place), and is at least the level below's
	 * size. Supply Shop IV is III with a basement dug under it: nothing of III above the ground floor's floor moved (only
	 * cells opened for the way down may differ, at most three).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryShopUpgradeKeepsItsCounterAndStoresWhereTheyStood(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int steps = 0;
		for (List<StarterBlueprints.Entry> family : List.of(FLOWER, SUPPLY, STALL)) {
			for (int i = 1; i < family.size(); i++) {
				steps++;
				StarterBlueprints.Entry lowE = family.get(i - 1);
				StarterBlueprints.Entry upE = family.get(i);
				Blueprint low = blueprint(helper, lowE);
				Blueprint up = blueprint(helper, upE);
				Map<BlockPos, BlockState> lower = cells(low);
				Map<BlockPos, BlockState> upper = cells(up);
				List<String> lost = new ArrayList<>();
				lower.forEach((p, s) -> {
					boolean kept = holdsItems(s) || s.is(ModBlocks.SHOP_COUNTER) || PoiTypes.forState(s).isPresent();
					if (kept && !at(upper, p).is(s.getBlock())) {
						lost.add(name(s) + " " + p.toShortString() + " -> " + name(at(upper, p)));
					}
				});
				if (!lost.isEmpty()) {
					lost.sort(null);
					problems.add(n(lowE) + " -> " + n(upE) + ": " + lost);
				}
				Vec3i a = low.size();
				Vec3i b = up.size();
				if (b.getX() < a.getX() || b.getY() < a.getY() || b.getZ() < a.getZ()) {
					problems.add(n(upE) + " " + b.toShortString() + " is smaller than " + n(lowE) + " " + a.toShortString());
				}
				if (upE == StarterBlueprints.SUPPLY_SHOP_4) {
					List<String> moved = new ArrayList<>();
					int opened = 0;
					for (Map.Entry<BlockPos, BlockState> c : lower.entrySet()) {
						if (c.getKey().getY() < 0 || c.getValue().equals(at(upper, c.getKey()))) {
							continue;
						}
						if (open(at(upper, c.getKey()), true) && opened < 3) {
							opened++;
							continue;
						}
						moved.add(c.getKey().toShortString() + " " + name(c.getValue()) + " -> " + name(at(upper, c.getKey())));
					}
					for (Map.Entry<BlockPos, BlockState> c : upper.entrySet()) {
						if (c.getKey().getY() >= 0 && !c.getValue().isAir() && !lower.containsKey(c.getKey())) {
							moved.add(c.getKey().toShortString() + " (air) -> " + name(c.getValue()));
						}
					}
					if (!moved.isEmpty()) {
						moved.sort(null);
						problems.add("supply_shop_4 changes III above the ground (" + moved.size() + "): " + moved.subList(0, Math.min(8, moved.size())));
					}
				}
			}
		}
		helper.assertTrue(steps == 6, "(setup) " + steps + " upgrade steps, want 6");
		helper.assertTrue(problems.isEmpty(), "an upgrade moves or drops what the level below had: " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The briefs' "Fits": each drawing equals its declared size; Flower Shop I and Supply Shop I at most 13 x 16 x 13; Supply
	 * Shop II-IV at most 23 x 18 x 28, IV's basement exactly 4 deep and I-III none; Market Stall I at most 12 wide, 6 deep, 16
	 * tall, width and depth even and at least 4, at least 4 tall, II at most 21 x 16 x 9, no stall level with a cellar;
	 * Well I and II exactly 7 x 7, at most 10 tall, II not lower than I; Well House at most 18 x 28 x 22; Fountain at most
	 * 13 x 16 x 13; Lily Pond at most 9 x 8 x 7; Footbridge at most 9 x 8 x 9 and at least 4 wide and 5 deep.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryShopAndWaterBuildFitsThePlotItsBriefGives(GameTestHelper helper) {
		List<String> p = new ArrayList<>();
		Map<StarterBlueprints.Entry, int[]> max = new LinkedHashMap<>(); // w, h, d
		max.put(StarterBlueprints.FLOWER_SHOP, new int[] {13, 16, 13});
		max.put(StarterBlueprints.FLOWER_SHOP_2, new int[] {999, 999, 999});
		max.put(StarterBlueprints.SUPPLY_SHOP, new int[] {13, 16, 13});
		max.put(StarterBlueprints.SUPPLY_SHOP_2, new int[] {23, 18, 28});
		max.put(StarterBlueprints.SUPPLY_SHOP_3, new int[] {23, 18, 28});
		max.put(StarterBlueprints.SUPPLY_SHOP_4, new int[] {23, 18, 28});
		max.put(StarterBlueprints.MARKET_STALL, new int[] {12, 16, 6});
		max.put(StarterBlueprints.MARKET_STALL_2, new int[] {21, 16, 9});
		max.put(StarterBlueprints.MARKET_STALL_3, new int[] {999, 999, 999});
		max.put(StarterBlueprints.WELL, new int[] {7, 10, 7});
		max.put(StarterBlueprints.WELL_2, new int[] {7, 10, 7});
		max.put(StarterBlueprints.WELL_HOUSE, new int[] {18, 28, 22});
		max.put(StarterBlueprints.FOUNTAIN, new int[] {13, 16, 13});
		max.put(StarterBlueprints.LILY_POND, new int[] {9, 8, 7});
		max.put(StarterBlueprints.FOOTBRIDGE, new int[] {9, 8, 9});
		for (Map.Entry<StarterBlueprints.Entry, int[]> e : max.entrySet()) {
			Blueprint b = blueprint(helper, e.getKey());
			Vec3i size = b.size();
			String n = n(e.getKey());
			if (!size.equals(e.getKey().size())) {
				p.add(n + ": drawn " + size.toShortString() + ", declared " + e.getKey().size().toShortString());
			}
			int[] m = e.getValue();
			if (size.getX() > m[0] || size.getY() > m[1] || size.getZ() > m[2]) {
				p.add(n + ": " + size.toShortString() + " (w h d) is over the brief's " + m[0] + ", " + m[1] + ", " + m[2]);
			}
			int want = e.getKey() == StarterBlueprints.SUPPLY_SHOP_4 ? 4 : 0;
			if (b.basement() != want) {
				p.add(n + ": basement " + b.basement() + " deep, want " + want);
			}
		}
		Vec3i stall = blueprint(helper, StarterBlueprints.MARKET_STALL).size();
		if (stall.getX() % 2 != 0 || stall.getZ() % 2 != 0 || stall.getX() < 4 || stall.getZ() < 4 || stall.getY() < 4) {
			p.add("market_stall: " + stall.toShortString() + ", want width and depth even and at least 4, height at least 4");
		}
		for (StarterBlueprints.Entry w : List.of(StarterBlueprints.WELL, StarterBlueprints.WELL_2)) {
			Vec3i s = blueprint(helper, w).size();
			if (s.getX() != 7 || s.getZ() != 7) {
				p.add(n(w) + ": footprint " + s.getX() + " x " + s.getZ() + ", the brief says exactly 7 x 7");
			}
		}
		if (blueprint(helper, StarterBlueprints.WELL_2).size().getY() < blueprint(helper, StarterBlueprints.WELL).size().getY()) {
			p.add("well_2 is lower than well");
		}
		Vec3i fb = blueprint(helper, StarterBlueprints.FOOTBRIDGE).size();
		if (fb.getX() < 4 || fb.getZ() < 5) {
			p.add("footbridge: " + fb.toShortString() + ", the pinned water at x 3, z 2-4 needs at least 4 wide and 5 deep");
		}
		helper.assertTrue(p.isEmpty(), String.join("; ", p));
		helper.succeed();
	}

	/**
	 * market_stall.md, "At least two flower pots on Level I ... standing on a planned solid block that is not a container or
	 * other block entity"; and flower_shop.md, "nothing small stands on air, pots included": every
	 * flower pot in both Flower Shop levels stands on a block that holds it or hangs from a chain (nothing small on air).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheStallsPotsStandOnSolidBlocksAndTheFlowerShopsPotsStandOnSomething(GameTestHelper helper) {
		List<String> p = new ArrayList<>();
		Map<BlockPos, BlockState> stall = cells(blueprint(helper, StarterBlueprints.MARKET_STALL));
		int good = 0;
		for (Map.Entry<BlockPos, BlockState> c : stall.entrySet()) {
			if (c.getValue().getBlock() instanceof FlowerPotBlock) {
				BlockState below = at(stall, c.getKey().below());
				if (!below.isAir() && !below.hasBlockEntity() && below.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP, SupportType.CENTER)) {
					good++;
				}
			}
		}
		if (good < 2) {
			p.add("market_stall: " + good + " flower pots standing on a solid block that isn't a container, the brief says at least 2");
		}
		for (StarterBlueprints.Entry e : FLOWER) {
			Map<BlockPos, BlockState> cells = cells(blueprint(helper, e));
			for (Map.Entry<BlockPos, BlockState> c : cells.entrySet()) {
				if (c.getValue().getBlock() instanceof FlowerPotBlock) {
					BlockState below = at(cells, c.getKey().below());
					boolean hung = at(cells, c.getKey().above()).is(Blocks.CHAIN);
					if (!hung && !below.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP, SupportType.CENTER)) {
						p.add(n(e) + ": the pot at " + c.getKey().toShortString() + " stands on " + name(below) + " and hangs from nothing");
					}
				}
			}
		}
		helper.assertTrue(p.isEmpty(), String.join("; ", p));
		helper.succeed();
	}

	// ---- the water builds, from the briefs ---------------------------------------------------------------------------

	/** Water can't run from a water cell into {@code n}: more water, a block it can't enter or wash away. */
	private static boolean holdsWater(BlockState n) {
		if (water(n)) {
			return true;
		}
		if (n.isAir()) {
			return false;
		}
		Block b = n.getBlock();
		if (b instanceof LiquidBlockContainer || b instanceof DoorBlock || b instanceof SignBlock || b instanceof LadderBlock
			|| b instanceof SugarCaneBlock) {
			return true;
		}
		return n.blocksMotion();
	}

	/**
	 * The water briefs' "Water" in the drawings: Well I and II, Well House, Fountain, Lily Pond and Footbridge each pour water,
	 * every water cell a source (no flowing water), held in on all four sides and below by more water or a block water can't
	 * enter (the street round the build is air); no water in the Lily Pond's bottom layer and at least 10 sources; the
	 * Footbridge exactly 15 water cells, three of them the sources at (3, 0, 2..4); no job block, bell or bed in the Well,
	 * Fountain, Lily Pond or Footbridge, and no container in the Lily Pond or Footbridge (a take-down leaves containers).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryWaterBuildsWaterIsSourcesHeldInOnEverySide(GameTestHelper helper) {
		List<String> p = new ArrayList<>();
		for (StarterBlueprints.Entry e : WATER) {
			Map<BlockPos, BlockState> cells = cells(blueprint(helper, e));
			String n = n(e);
			List<BlockPos> wet = cells.entrySet().stream().filter(c -> water(c.getValue())).map(Map.Entry::getKey).sorted().toList();
			if (wet.isEmpty()) {
				p.add(n + ": no water");
			}
			for (BlockPos w : wet) {
				FluidState f = at(cells, w).getFluidState();
				if (!f.isSource()) {
					p.add(n + ": flowing water at " + w.toShortString());
				}
				List<String> leaks = new ArrayList<>();
				for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
					BlockState side = at(cells, w.relative(d));
					if (!holdsWater(side)) {
						leaks.add(d.getName() + "=" + name(side));
					}
				}
				if (!leaks.isEmpty()) {
					p.add(n + ": the water at " + w.toShortString() + " runs out " + leaks);
				}
			}
			if (e == StarterBlueprints.LILY_POND) {
				long sources = wet.stream().filter(w -> at(cells, w).getFluidState().isSource()).count();
				if (sources < 10) {
					p.add(n + ": " + sources + " sources, want at least 10");
				}
				if (wet.stream().anyMatch(w -> w.getY() == 0)) {
					p.add(n + ": water in the bottom layer " + wet.stream().filter(w -> w.getY() == 0).toList());
				}
				if (!stores(cells).isEmpty()) {
					p.add(n + ": containers " + stores(cells));
				}
			}
			if (e == StarterBlueprints.FOOTBRIDGE) {
				if (wet.size() != 15) {
					p.add(n + ": " + wet.size() + " water cells, the brief says exactly 15");
				}
				for (int z = 2; z <= 4; z++) {
					BlockState s = at(cells, new BlockPos(3, 0, z));
					if (!s.is(Blocks.WATER) || !s.getFluidState().isSource()) {
						p.add(n + ": (3, 0, " + z + ") is " + name(s) + ", the brief pins a water source there");
					}
				}
				if (!stores(cells).isEmpty()) {
					p.add(n + ": containers " + stores(cells));
				}
			}
			if (e != StarterBlueprints.WELL_HOUSE) {
				Map<BlockPos, BlockState> jobs = jobSites(cells);
				cells.forEach((pos, s) -> {
					if (s.getBlock() instanceof BedBlock) {
						jobs.put(pos, s);
					}
				});
				if (!jobs.isEmpty()) {
					p.add(n + ": job blocks or beds " + shortList(jobs));
				}
			}
		}
		helper.assertTrue(p.isEmpty(), String.join("; ", p));
		helper.succeed();
	}

	/**
	 * well.md, "At least one source must have a non-water block above it and a spot a villager can stand on within 3 blocks
	 * of it" (what a Well Keeper dips his bucket in), taken as a villager does it: a spot he walks to from the street in
	 * front, his eyes within the Walker's reach of the water (Well I and II, and the Fountain, which the brief says a Well
	 * Keeper can use the same way). And footbridge.md, "the build is crossed from the front (z = 0) towards the back": a
	 * villager who starts in front of the bridge and never steps outside its sides walks to the ground behind it, standing over
	 * its water on the way (rows z = 2..4, where the stream runs).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAVillagerDipsABucketInTheWellsAndCrossesTheFootbridge(GameTestHelper helper) {
		List<String> p = new ArrayList<>();
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.WELL, StarterBlueprints.WELL_2, StarterBlueprints.FOUNTAIN)) {
			Blueprint b = blueprint(helper, e);
			Map<BlockPos, BlockState> cells = cells(b);
			Set<BlockPos> reached = walk(b, cells, false);
			List<BlockPos> dips = cells.entrySet().stream().filter(c -> water(c.getValue()) && c.getValue().getFluidState().isSource()
				&& !water(at(cells, c.getKey().above()))).map(Map.Entry::getKey).toList();
			if (dips.stream().noneMatch(w -> dipReach(reached, w))) {
				p.add(n(e) + ": none of its " + dips.size() + " sources with no water above can be reached from a spot walked to from the street");
			}
		}
		Blueprint fb = blueprint(helper, StarterBlueprints.FOOTBRIDGE);
		Map<BlockPos, BlockState> cells = cells(fb);
		int sx = fb.size().getX();
		int sz = fb.size().getZ();
		Set<BlockPos> crossing = walk(fb, cells, false, 0, sx - 1);
		boolean back = crossing.stream().anyMatch(c -> c.getZ() >= sz);
		boolean over = crossing.stream().anyMatch(c -> c.getZ() >= 2 && c.getZ() <= 4
			&& java.util.stream.IntStream.range(0, c.getY()).anyMatch(y -> water(at(cells, new BlockPos(c.getX(), y, c.getZ())))));
		if (!back || !over) {
			p.add("footbridge: walked from the front within its sides, reached the back " + back + ", stood over its water (rows z 2-4) " + over
				+ " (" + crossing.size() + " spots reached)");
		}
		helper.assertTrue(p.isEmpty(), String.join("; ", p));
		helper.succeed();
	}

	// ---- in the world -------------------------------------------------------------------------------------------------

	/**
	 * Places {@code entry}'s drawing at {@code origin} (helper coordinates), ticks its water, and after 60 ticks
	 * checks: every drawn water cell is still a water source; no water anywhere else in the footprint and one block round
	 * it (none flowed out; a drawn waterloggable block holding a source is still water held in); every drawn block can
	 * still stand where it is ({@code canSurvive}: a lantern hangs from something, a pot or lily pad stands on something).
	 */
	private static void placeAndWatch(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos origin, List<String> problems, Runnable then) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(origin);
		level.getStructureManager().get(entry.id()).orElseThrow()
			.placeInWorld(level, abs, abs, new StructurePlaceSettings().setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING),
				net.minecraft.util.RandomSource.create(1121L), 2);
		Vec3i size = entry.size();
		for (BlockPos pos : BlockPos.betweenClosed(abs, abs.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			FluidState f = level.getFluidState(pos);
			if (!f.isEmpty()) {
				level.scheduleTick(pos.immutable(), f.getType(), 1);
			}
		}
		helper.runAfterDelay(60, () -> {
			Map<BlockPos, BlockState> cells = cells(blueprint(helper, entry));
			String n = n(entry);
			for (BlockPos pos : BlockPos.betweenClosed(abs.offset(-1, 0, -1), abs.offset(size.getX(), size.getY() - 1, size.getZ()))) {
				BlockPos rel = pos.subtract(abs);
				BlockState drawn = at(cells, rel);
				FluidState f = level.getFluidState(pos);
				if (water(drawn) && drawn.getFluidState().isSource()) {
					if (!f.is(FluidTags.WATER) || !f.isSource()) {
						problems.add(n + ": the drawn source at " + rel.toShortString() + " is " + level.getBlockState(pos) + " in the world");
					}
				} else if (!f.isEmpty() && !(f.isSource() && drawn.hasProperty(BlockStateProperties.WATERLOGGED) && cells.containsKey(rel))) {
					// (A drawn slab or stair between two of its sources may take water, as vanilla's infinite water does: it stays in.)
					problems.add(n + ": " + (f.isSource() ? "water" : "flowing water") + " at " + rel.toShortString() + " that the drawing doesn't have there (" + level.getBlockState(pos) + ")");
				}
				BlockState now = level.getBlockState(pos);
				if (cells.containsKey(rel) && !drawn.isAir() && !now.canSurvive(level, pos)) {
					problems.add(n + ": " + name(now) + " at " + rel.toShortString() + " can't stay where it is (nothing holds it)");
				}
			}
			then.run();
		});
	}

	/**
	 * well.md, fountain.md, lily_pond.md, footbridge.md in the world: Well I, Well II, the Fountain, the Lily Pond and the
	 * Footbridge placed on flat ground side by side, their water ticked for 60 ticks: every source the drawing pours is still a
	 * source, no water ran out of any of them onto the ground round it, and nothing hangs on or stands on a block that can't
	 * hold it.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaShopsWater1121bPlaced"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaShopsWater1121bPlaced")
	public void qaTheWellsFountainPondAndFootbridgeHoldTheirWaterOnFlatGround(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<String> problems = new ArrayList<>();
		int[] done = {0};
		Runnable one = () -> done[0]++;
		placeAndWatch(helper, StarterBlueprints.WELL, new BlockPos(1, 2, 1), problems, one);
		placeAndWatch(helper, StarterBlueprints.WELL_2, new BlockPos(10, 2, 1), problems, one);
		placeAndWatch(helper, StarterBlueprints.FOUNTAIN, new BlockPos(19, 2, 1), problems, one);
		placeAndWatch(helper, StarterBlueprints.LILY_POND, new BlockPos(1, 2, 13), problems, one);
		placeAndWatch(helper, StarterBlueprints.FOOTBRIDGE, new BlockPos(13, 2, 13), problems, one);
		helper.succeedWhen(() -> {
			helper.assertTrue(done[0] == 5, "waiting");
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		});
	}

	/**
	 * well_house.md in the world: "He fills at the water source nearest his cauldron ... whose block above isn't water. He then
	 * has to get his eyes within 3.0 blocks of that water block's centre, standing on a spot he can walk to." The Well House
	 * placed on flat ground; the Well Keeper's own search from its cauldron ({@code WellKeeperWork.findWater}) picks a source,
	 * and a villager walks from the street to a spot his eyes reach it from. Its water holds as in the test above, and its one
	 * cauldron has a spot beside it walked to from the street.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaShopsWater1121bWellHouse"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaShopsWater1121bWellHouse")
	public void qaTheWellKeepersNearestWaterInTheWellHouseIsOneHeCanReach(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		BlockPos origin = new BlockPos(6, 2, 6);
		BlockPos abs = helper.absolutePos(origin);
		boolean[] done = {false};
		placeAndWatch(helper, StarterBlueprints.WELL_HOUSE, origin, problems, () -> {
			Blueprint b = blueprint(helper, StarterBlueprints.WELL_HOUSE);
			Map<BlockPos, BlockState> cells = cells(b);
			List<BlockPos> cauldrons = where(cells, Blocks.CAULDRON);
			Set<BlockPos> reached = walk(b, cells, false);
			if (cauldrons.size() != 1) {
				problems.add("well_house: " + cauldrons.size() + " cauldrons");
			} else {
				BlockPos cauldron = cauldrons.get(0);
				if (!beside(reached, cauldron)) {
					problems.add("well_house: the cauldron at " + cauldron.toShortString() + " has no spot beside it walked to from the street");
				}
				Optional<BlockPos> water = WellKeeperWork.findWater(level, abs.offset(cauldron));
				if (water.isEmpty()) {
					problems.add("well_house: the Well Keeper finds no water from his cauldron");
				} else {
					BlockPos rel = water.get().subtract(abs);
					if (!dipReach(reached, rel)) {
						problems.add("well_house: the water nearest the cauldron, at " + rel.toShortString()
							+ ", has no spot walked to from the street that a villager's eyes reach it from (within 2.7)");
					}
				}
			}
			done[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(done[0], "waiting");
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		});
	}

	// ---- live builds --------------------------------------------------------------------------------------------------

	private static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the villager wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the villager didn't load back: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}

	private static final BlockPos[] BARRELS = {new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4)};
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static void stock(GameTestHelper helper, List<ItemStack> stock) {
		helper.assertTrue(stock.size() <= BARRELS.length * 27, "(setup) the test needs more barrels: " + stock.size() + " stacks");
		for (int i = 0; i < BARRELS.length; i++) {
			helper.setBlock(BARRELS[i], Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(BARRELS[i]);
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
	}

	private static void rules(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
	}

	static List<BlockPos> built(ServerLevel level, BlockPos origin, Vec3i size, Block block) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			if (level.getBlockState(p).is(block)) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	private static List<ItemStack> stacks(Map<Item, Integer> needs) {
		List<ItemStack> stock = new ArrayList<>();
		needs.forEach((item, count) -> {
			for (int left = count; left > 0; ) {
				int k = Math.min(left, item.getDefaultMaxStackSize());
				stock.add(new ItemStack(item, k));
				left -= k;
			}
		});
		return stock;
	}

	/**
	 * A real Builder builds {@code entry} at {@code origin} from exactly its material list, the site and the builder saved
	 * and loaded once when {@code reloadNow} first holds; the build ends DONE, every block right, nothing of the list left
	 * over; {@code settle} ticks later {@code after} checks the finished build.
	 */
	private static void buildAcrossAReload(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos origin,
		java.util.function.BiPredicate<BuildSite, BlueprintData.Placement> reloadNow, int settle, Consumer<BlueprintData.Placement> after) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		rules(helper);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		BuildPlan plan = BuildPlan.create(blueprint(helper, entry), placement);
		Map<Item, Integer> needs = new LinkedHashMap<>(plan.materials());
		stock(helper, stacks(needs));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, entry.id(), placement);
		helper.assertTrue(site != null, n(entry) + " didn't start");
		UUID id = site.id();
		Villager[] who = {builder};
		boolean[] reloaded = {false};
		long[] doneAt = {-1};
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 11000) {
				helper.setDayTime(2000);
			}
			BuildSite s = BuildSiteManager.get(level).get(id);
			if (s == null) {
				if (doneAt[0] < 0) {
					doneAt[0] = level.getGameTime();
				}
				return;
			}
			if (!reloaded[0] && reloadNow.test(s, placement)) {
				reloaded[0] = true;
				who[0] = reload(helper, level, who[0]);
				helper.assertTrue(BuildSiteManager.get(level).get(id) != null, "the site didn't come back from its save");
			}
		});
		helper.succeedWhen(() -> {
			BuildSite s = BuildSiteManager.get(level).get(id);
			helper.assertTrue(s == null, s == null ? "" : "still building: " + s.stage() + " " + s.status() + ", " + s.placed() + " placed, missing "
				+ s.missing() + ", builder at " + helper.relativePos(who[0].blockPosition()) + (who[0].isAlive() ? "" : " (dead)"));
			helper.assertTrue(reloaded[0], "never saved and loaded mid-build");
			helper.assertTrue(doneAt[0] >= 0 && level.getGameTime() - doneAt[0] >= settle, "settling");
			List<BlockPos> wrong = plan.unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) wrong after the build, e.g. "
				+ wrong.stream().limit(5).map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			Map<Item, Integer> left = new LinkedHashMap<>();
			List<Container> containers = new ArrayList<>();
			for (BlockPos b : BARRELS) {
				containers.add(helper.getBlockEntity(b));
			}
			containers.add(helper.getBlockEntity(CHEST));
			containers.add(who[0].getInventory());
			for (Container c : containers) {
				for (int slot = 0; slot < c.getContainerSize(); slot++) {
					ItemStack st = c.getItem(slot);
					if (!st.isEmpty() && needs.containsKey(st.getItem())) {
						left.merge(st.getItem(), st.getCount(), Integer::sum);
					}
				}
			}
			helper.assertTrue(left.isEmpty(), "materials left over after the build (it should use exactly what it needs): " + left);
			after.accept(placement);
		});
	}

	/** Water sources standing in the footprint right now. */
	private static int sourcesIn(ServerLevel level, BlockPos origin, Vec3i size) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			FluidState f = level.getFluidState(p);
			if (f.is(FluidTags.WATER) && f.isSource()) {
				n++;
			}
		}
		return n;
	}

	/**
	 * fountain.md in the world, the brief's "Every source must be held in so no water ever flows out of its cell, in the
	 * world, after it is poured": a Builder builds the Fountain from bare ground from exactly its materials, saved and loaded
	 * (site and builder) while he is pouring its water (DECORATION, at least one source down); DONE, every block right,
	 * nothing of the list left over; 100 ticks later every source of the plan is a source where drawn (no dry basin) and
	 * there is no other water in the whole test area (none spilled onto the street, none left flowing).
	 */
	//$ gametest_ticks_batch BIG '20000' '"qaShopsWater1121bFountainBuild"'
	@GameTest(template = BIG, timeoutTicks = 20000, batch = "qaShopsWater1121bFountainBuild")
	public void qaABuilderBuildsTheFountainAcrossAReloadMidPourAndItsBasinHoldsItsWater(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3i size = StarterBlueprints.FOUNTAIN.size();
		buildAcrossAReload(helper, StarterBlueprints.FOUNTAIN, new BlockPos(9, 2, 9),
			(s, pl) -> s.stage() == BuildPlan.Stage.DECORATION && sourcesIn(level, pl.origin(), size) >= 1, 100, placement -> {
				Map<BlockPos, BlockState> cells = cells(blueprint(helper, StarterBlueprints.FOUNTAIN));
				List<String> bad = new ArrayList<>();
				int planned = 0;
				for (Map.Entry<BlockPos, BlockState> c : cells.entrySet()) {
					if (water(c.getValue())) {
						planned++;
						FluidState f = level.getFluidState(placement.origin().offset(c.getKey()));
						if (!f.is(FluidTags.WATER) || !f.isSource()) {
							bad.add("dry or flowing at " + c.getKey().toShortString());
						}
					}
				}
				BlockPos lo = helper.absolutePos(new BlockPos(0, 1, 0));
				BlockPos hi = helper.absolutePos(new BlockPos(21, 17, 21));
				for (BlockPos p : BlockPos.betweenClosed(lo, hi)) {
					FluidState f = level.getFluidState(p);
					if (f.isEmpty()) {
						continue;
					}
					BlockPos rel = p.subtract(placement.origin());
					if (!water(at(cells, rel)) || !f.isSource()) {
						bad.add((f.isSource() ? "water" : "flowing water") + " at " + rel.toShortString() + " (from the fountain's origin)");
					}
				}
				helper.assertTrue(planned >= 1, "(setup) the fountain pours no water");
				helper.assertTrue(bad.isEmpty(), "the built fountain's water after 100 ticks: " + bad.subList(0, Math.min(10, bad.size())));
			});
	}

	/**
	 * flower_shop.md in the world: a Builder builds Flower Shop I from bare ground from exactly its materials, saved and
	 * loaded (site and builder) once 40 blocks of the walls are up; DONE, every block right, nothing of the list left over;
	 * the built shop has exactly one composter, a job site, with a built chest within 8 across and 4 up or down of it.
	 */
	//$ gametest_ticks_batch HUGE '40000' '"qaShopsWater1121bFlowerBuild"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "qaShopsWater1121bFlowerBuild")
	public void qaABuilderBuildsFlowerShopIAcrossAReloadFromExactlyItsMaterials(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		buildAcrossAReload(helper, StarterBlueprints.FLOWER_SHOP, new BlockPos(9, 2, 9),
			(s, pl) -> s.stage() == BuildPlan.Stage.STRUCTURE && s.placed() >= 40, 1, placement -> {
				Vec3i size = StarterBlueprints.FLOWER_SHOP.size();
				List<BlockPos> composters = built(level, placement.origin(), size, Blocks.COMPOSTER);
				helper.assertTrue(composters.size() == 1, "the built shop has " + composters.size() + " composters");
				helper.assertTrue(level.getPoiManager().getType(composters.get(0)).isPresent(), "the built composter is no job site");
				List<BlockPos> chests = built(level, placement.origin(), size, Blocks.CHEST);
				helper.assertTrue(chests.stream().anyMatch(c -> within(c, composters.get(0), 8, 4)), "no built chest within 8 / 4 of the composter");
			});
	}

	/**
	 * supply_shop.md, "The Shop Counter, every chest and barrel and every other block-entity block stay in the same cell as
	 * the same block in every later level", in the world: Supply Shop I stands finished, a Shopkeeper holds its Shop Counter
	 * (the POI ticket taken, his JOB_SITE on it) and a player's stock (emeralds and bread) sits in each of its 8 stores. A
	 * Builder stocked with exactly what Level II still needs builds it over I. DONE: every block of II right; the counter is
	 * still there and still taken (never broken and re-placed), the Shopkeeper still holding it; every store of Level I still
	 * there with the player's stock in it.
	 */
	//$ gametest_ticks_batch HUGE '40000' '"qaShopsWater1121bSupplyUpgrade"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "qaShopsWater1121bSupplyUpgrade")
	public void qaUpgradingASupplyShopToIIKeepsTheClaimedCounterAndThePlayersStock(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		rules(helper);
		BlockPos origin = helper.absolutePos(new BlockPos(9, 2, 9));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		level.getStructureManager().get(StarterBlueprints.SUPPLY_SHOP.id()).orElseThrow()
			.placeInWorld(level, origin, origin, new StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1121L), 2);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.SUPPLY_SHOP.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		Vec3i size1 = StarterBlueprints.SUPPLY_SHOP.size();
		List<BlockPos> counters = built(level, origin, size1, ModBlocks.SHOP_COUNTER);
		List<BlockPos> stores = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size1.getX() - 1, size1.getY() - 1, size1.getZ() - 1))) {
			if (!level.getBlockState(p).is(ModBlocks.SHOP_COUNTER) && level.getBlockEntity(p) instanceof Container) {
				stores.add(p.immutable());
			}
		}
		helper.assertTrue(counters.size() == 1 && !stores.isEmpty(), "(setup) Supply Shop I placed with " + counters.size() + " counters, "
			+ stores.size() + " stores");
		BlockPos counter = counters.get(0);
		helper.assertTrue(level.getPoiManager().take(h -> h.is(ModVillagers.SHOP_COUNTER_POI), (h, p) -> p.equals(counter), counter, 1).isPresent(),
			"(setup) the Shop Counter couldn't be claimed");
		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		keeper.setNoAi(true);
		keeper.setVillagerData(keeper.getVillagerData().setProfession(ModVillagers.SHOPKEEPER));
		keeper.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), counter));
		Map<BlockPos, Integer> put = new LinkedHashMap<>();
		int k = 0;
		for (BlockPos p : stores) {
			k++;
			Container box = (Container) level.getBlockEntity(p);
			// A double chest is two block entities: each half gets its own stack.
			box.setItem(0, new ItemStack(k % 2 == 0 ? Items.EMERALD : Items.BREAD, 10 + k));
			put.put(p, 10 + k);
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		var report = BlueprintSupplies.check(level, new BlueprintData(StarterBlueprints.SUPPLY_SHOP_2.id(), Optional.of(StarterBlueprints.SUPPLY_SHOP_2.size()),
			Optional.of(placement))).orElseThrow(() -> new AssertionError("no supply report for the upgrade"));
		List<ItemStack> stock = new ArrayList<>();
		for (var m : report.missing()) {
			for (int left = m.count(); left > 0; left -= m.item().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(m.item(), Math.min(left, m.item().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(!stock.isEmpty(), "(setup) the upgrade needs nothing: " + report);
		stock(helper, stock);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, StarterBlueprints.SUPPLY_SHOP_2.id(), placement);
		helper.assertTrue(site != null, "Supply Shop II didn't start over the finished Level I");
		UUID id = site.id();
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 11000) {
				helper.setDayTime(2000);
			}
		});
		helper.succeedWhen(() -> {
			BuildSite s = BuildSiteManager.get(level).get(id);
			helper.assertTrue(s == null, s == null ? "" : "still building: " + s.stage() + " " + s.status() + ", " + s.placed() + " placed, missing " + s.missing());
			BuildPlan plan = BuildPlan.create(blueprint(helper, StarterBlueprints.SUPPLY_SHOP_2), placement);
			List<BlockPos> wrong = plan.unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of Supply Shop II wrong, e.g. "
				+ wrong.stream().limit(5).map(p -> p.subtract(origin).toShortString() + "=" + level.getBlockState(p)).toList());
			String c = counter.subtract(origin).toShortString();
			helper.assertTrue(level.getBlockState(counter).is(ModBlocks.SHOP_COUNTER), "the claimed Shop Counter at " + c + " is gone after the upgrade");
			helper.assertTrue(level.getPoiManager().getFreeTickets(counter) == 0, "the claimed Shop Counter at " + c
				+ " was freed by the upgrade (broken and re-placed): the Shopkeeper lost it");
			helper.assertTrue(keeper.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(counter::equals).isPresent(),
				"the Shopkeeper lost his job site: " + keeper.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.assertTrue(keeper.getVillagerData().getProfession() == ModVillagers.SHOPKEEPER, "the Shopkeeper lost his job");
			List<String> lost = new ArrayList<>();
			for (Map.Entry<BlockPos, Integer> e : put.entrySet()) {
				ItemStack kept = level.getBlockEntity(e.getKey()) instanceof Container box ? box.getItem(0) : ItemStack.EMPTY;
				if (kept.getCount() != e.getValue()) {
					lost.add(e.getKey().subtract(origin).toShortString() + " " + name(level.getBlockState(e.getKey())) + " holds " + kept);
				}
			}
			helper.assertTrue(lost.isEmpty(), "the player's stock was lost in the upgrade: " + lost);
			keeper.discard();
		});
	}
}
