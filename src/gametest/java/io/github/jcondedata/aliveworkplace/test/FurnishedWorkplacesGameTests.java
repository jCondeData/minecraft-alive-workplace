package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot.Kind;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 37.5 without any furniture mod: the workplaces (the Supply Shop, the Library, the Healing Center, the
 * Schoolhouse, the Village Hall, the Kitchen and the Print Shop) have furniture spots (desks, counters, benches, shelves
 * and crockery, pictures, trellises and hanging pots); no spot in any of our builds covers a job block, a bed or a
 * container, and the workplaces' beds (the beds villagers there need) have no spot at all; and a builder builds the
 * furnished Supply Shop in plain vanilla with nothing skipped, its hanging pots and trellises included.
 */
public class FurnishedWorkplacesGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation SHOP = AliveWorkplace.id("supply_shop");

	/** Each workplace, every level of it, and the kinds of piece it must have. */
	private static Map<String, Set<Kind>> workplaces() {
		Map<String, Set<Kind>> out = new LinkedHashMap<>();
		for (String tier : List.of("", "_2", "_3", "_4")) {
			out.put("supply_shop" + tier, EnumSet.of(Kind.COUNTER, Kind.SHELF, Kind.CROCKERY, Kind.PICTURE, Kind.HANGING_POT, Kind.TRELLIS));
			out.put("library" + tier, EnumSet.of(Kind.DESK, Kind.CHAIR, Kind.PICTURE, Kind.HANGING_POT));
		}
		for (String tier : List.of("", "_2", "_3")) {
			out.put("healing_center" + tier, EnumSet.of(Kind.BENCH, Kind.PICTURE, Kind.DESK));
		}
		out.put("healing_center_4", EnumSet.of(Kind.BENCH, Kind.PICTURE)); // its cellar stair takes the desk's corner
		for (String tier : List.of("", "_2")) {
			out.put("schoolhouse" + tier, EnumSet.of(Kind.BENCH, Kind.DESK, Kind.PICTURE));
			out.put("print_shop" + tier, EnumSet.of(Kind.DESK, Kind.COUNTER));
		}
		out.put("village_hall", EnumSet.of(Kind.BENCH, Kind.HANGING_POT));
		for (String tier : List.of("_2", "_3")) {
			out.put("village_hall" + tier, EnumSet.of(Kind.BENCH, Kind.HANGING_POT, Kind.TABLE, Kind.CHAIR));
		}
		out.put("kitchen", EnumSet.of(Kind.CHAIR, Kind.TABLE, Kind.CROCKERY));
		return out;
	}

	/** Whether {@code state} holds items (a chest, a barrel, a furnace...). */
	private static boolean container(BlockState state) {
		if (!(state.getBlock() instanceof EntityBlock block)) {
			return false;
		}
		try {
			return block.newBlockEntity(BlockPos.ZERO, state) instanceof Container;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/**
	 * The item's check: in every shipped spots file no spot's cell is a job block (any villager point of interest: a
	 * workstation, a bell, our job blocks), a container, or a bed that isn't the spot's own bed; and the workplaces,
	 * whose beds are the beds their villagers need, have no bed spot at all.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noSpotCoversAJobBlockABedOrAContainer(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int cells = 0;
		Set<String> workplaces = workplaces().keySet();
		for (Map.Entry<ResourceLocation, List<FurnitureSpot>> e : BlueprintFurniture.all().entrySet()) {
			Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), e.getKey()).orElse(null);
			if (blueprint == null) {
				continue; // everyShippedSpotMatchesItsTemplate (37.2) reports a file without its blueprint
			}
			Map<BlockPos, BlockState> template = new HashMap<>();
			blueprint.blocks().forEach(entry -> template.put(entry.pos(), entry.state()));
			boolean workplace = e.getKey().getNamespace().equals(AliveWorkplace.MOD_ID) && workplaces.contains(e.getKey().getPath());
			for (FurnitureSpot spot : e.getValue()) {
				if (workplace && spot.kind() == Kind.BED) {
					problems.add(e.getKey() + ": a bed spot at " + spot.pos().toShortString() + " (a workplace's beds stay vanilla)");
				}
				for (FurnitureSpot.Cell cell : spot.blocks()) {
					cells++;
					BlockPos at = spot.pos().offset(cell.at());
					BlockState there = template.getOrDefault(at, cell.state());
					String what = there.getBlock() instanceof BedBlock ? (spot.kind() == Kind.BED ? null : "a bed")
						: PoiTypes.hasPoi(there) ? "a job block" : container(there) ? "a container" : null;
					if (what != null) {
						problems.add(e.getKey() + " " + spot.kind().id() + "@" + spot.pos().toShortString() + " covers " + what + ": "
							+ BuiltInRegistries.BLOCK.getKey(there.getBlock()) + " at " + at.toShortString());
					}
				}
			}
		}
		helper.assertTrue(cells > 1000, "only " + cells + " spot cells checked");
		helper.assertTrue(problems.isEmpty(), problems.size() + " problems, e.g. " + problems.subList(0, Math.min(5, problems.size())));
		helper.succeed();
	}

	/** Every workplace at every level has its pieces: counters and decorations in the shop, desks in the Library... */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyWorkplaceIsFurnished(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		workplaces().forEach((name, kinds) -> {
			ResourceLocation id = AliveWorkplace.id(name);
			Set<Kind> has = EnumSet.noneOf(Kind.class);
			BlueprintFurniture.of(id).forEach(s -> has.add(s.kind()));
			if (!has.containsAll(kinds)) {
				Set<Kind> missing = EnumSet.copyOf(kinds);
				missing.removeAll(has);
				problems.add(name + " lacks " + missing);
			}
		});
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Without CobbleFurnies the Healing Center's and the Schoolhouse's desks (the owner's Poké Ball desks) plan as the order
	 * says, here vanilla, and a Library's desks are never Poké Ball desks.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withoutCobbleFurniesTheDesksFollowTheOrder(GameTestHelper helper) {
		helper.assertTrue(FurnitureChoice.hasPokeBallDesks(AliveWorkplace.id("healing_center")), "the Healing Center has no Poké Ball desks");
		helper.assertTrue(FurnitureChoice.hasPokeBallDesks(AliveWorkplace.id("schoolhouse_2")), "Schoolhouse II has no Poké Ball desks");
		helper.assertTrue(!FurnitureChoice.hasPokeBallDesks(AliveWorkplace.id("library")), "the Library has Poké Ball desks");
		for (String name : List.of("healing_center", "schoolhouse")) {
			List<FurnitureSpot> spots = BlueprintFurniture.of(AliveWorkplace.id(name));
			helper.assertTrue(FurnitureChoice.choose(spots, List.of("cobblefurnies"), true, false, true).isVanilla(),
				name + " planned a piece without CobbleFurnies installed");
		}
		helper.succeed();
	}

	/** The furnished Supply Shop built in vanilla from chests of exactly its materials: every block right, nothing skipped. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"furnished_shop_vanilla"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "furnished_shop_vanilla")
	public void aBuilderBuildsTheFurnishedSupplyShopInVanilla(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuildSite site = start(helper, SHOP);
		ServerLevel level = helper.getLevel();
		BuildPlan plan = site.plan(level);
		BlueprintData.Placement placement = site.placement();
		List<FurnitureSpot> spots = BlueprintFurniture.of(SHOP);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(3)
				.map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(site.skipped() == 0, site.skipped() + " skipped");
			for (FurnitureSpot spot : spots) {
				for (FurnitureSpot.Cell cell : spot.blocks()) {
					BlockPos at = placement.origin().offset(spot.pos()).offset(cell.at());
					BlockState there = level.getBlockState(at);
					helper.assertTrue(there.is(cell.state().getBlock()), spot.kind() + " at " + spot.pos().offset(cell.at()).toShortString()
						+ ": " + BuiltInRegistries.BLOCK.getKey(there.getBlock()) + ", not " + BuiltInRegistries.BLOCK.getKey(cell.state().getBlock()));
				}
			}
			helper.assertTrue(level.getBlockState(placement.origin().offset(5, 1, 6)).is(ModBlocks.SHOP_COUNTER), "the Shop Counter isn't there");
		});
	}

	/** A builder on {@code id} at (9, 2, 3), his chests holding exactly what the plan costs. */
	private static BuildSite start(GameTestHelper helper, ResourceLocation id) {
		ServerLevel level = helper.getLevel();
		new WorkplaceConfig().apply();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 3)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + id);
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i % 6, 2 + i / 6, 5);
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		return site;
	}
}
