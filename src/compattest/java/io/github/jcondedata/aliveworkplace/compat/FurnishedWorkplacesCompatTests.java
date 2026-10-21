package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot.Kind;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * ROADMAP 37.5: a builder builds three furnished workplaces (the Supply Shop, the Library and the Print Shop) with
 * Handcrafted and Beautify, from chests holding exactly what the plan costs. Every block ends up right, nothing is
 * skipped, every spot is the piece of the mod the order picks (Handcrafted's counters, desks, chairs, shelves and
 * crockery; Beautify's pictures, trellises and hanging pots), and each job still finds its block (the Shop Counter, the
 * lectern, the press, each still a job site) and exactly the chests and barrels the vanilla build has: no furniture
 * piece is counted as storage. And the owner's Poké Ball desks in the Healing Center and the Schoolhouse.
 */
public class FurnishedWorkplacesCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final List<String> ORDER = List.of("handcrafted", "beautify");

	private static Map<Kind, String> expected() {
		Map<Kind, String> out = new EnumMap<>(Kind.class);
		for (Kind kind : List.of(Kind.COUNTER, Kind.DESK, Kind.CHAIR, Kind.BENCH, Kind.TABLE, Kind.SHELF, Kind.CROCKERY)) {
			out.put(kind, "handcrafted");
		}
		for (Kind kind : List.of(Kind.PICTURE, Kind.TRELLIS, Kind.HANGING_POT, Kind.LAMP, Kind.BLINDS)) {
			out.put(kind, "beautify");
		}
		return out;
	}

	//$ gametest_ticks_batch HUGE_AREA '30000' '"furnished_shop_handcrafted"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "furnished_shop_handcrafted")
	public void theSupplyShopWithHandcraftedAndBeautify(GameTestHelper helper) {
		build(helper, AliveWorkplace.id("supply_shop"), ModBlocks.SHOP_COUNTER.defaultBlockState(), 8);
	}

	//$ gametest_ticks_batch HUGE_AREA '30000' '"furnished_library_handcrafted"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "furnished_library_handcrafted")
	public void theLibraryWithHandcraftedAndBeautify(GameTestHelper helper) {
		build(helper, AliveWorkplace.id("library"), Blocks.LECTERN.defaultBlockState(), 0);
	}

	//$ gametest_ticks_batch HUGE_AREA '30000' '"furnished_print_shop_handcrafted"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "furnished_print_shop_handcrafted")
	public void thePrintShopWithHandcraftedAndBeautify(GameTestHelper helper) {
		build(helper, AliveWorkplace.id("print_shop"), Blocks.CARTOGRAPHY_TABLE.defaultBlockState(), 1);
	}

	/**
	 * The owner's answer of 2026-10-18: with CobbleFurnies in the list, even after Handcrafted, the Healing Center's and the
	 * Schoolhouse's desks are Poké Ball desks; the Library's are Handcrafted's, and the other pieces follow the order.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theHealingCenterAndSchoolhouseGetPokeBallDesks(GameTestHelper helper) {
		try {
			new WorkplaceConfig().apply();
			Furnishings.MODS = List.of("handcrafted", "cobblefurnies", "beautify");
			for (String name : List.of("healing_center", "healing_center_2", "schoolhouse", "schoolhouse_2", "library")) {
				ResourceLocation id = AliveWorkplace.id(name);
				List<FurnitureSpot> spots = BlueprintFurniture.of(id);
				FurnitureChoice choice = FurnitureChoice.forNewBuild(id);
				int desks = 0;
				for (int i = 0; i < spots.size(); i++) {
					FurnitureChoice.Placed placed = choice.spots().get(i);
					if (placed == null && FurnishedHomesCompatTests.standing(spots.get(i), "beautify").equals("minecraft")
						&& spots.get(i).kind() == Kind.PICTURE) {
						continue; // B260: a picture on a wall stays a banner (Beautify's frame stands on a table)
					}
					helper.assertTrue(placed != null, name + ": " + spots.get(i).kind() + " at " + spots.get(i).pos().toShortString() + " stayed vanilla");
					if (spots.get(i).kind() == Kind.DESK) {
						desks++;
						String block = BuiltInRegistries.BLOCK.getKey(placed.cells().get(BlockPos.ZERO).getBlock()).toString();
						String want = name.equals("library") ? "handcrafted:dark_oak_desk" : "cobblefurnies:poke_ball_desk";
						helper.assertTrue(block.equals(want), name + "'s desk is " + block + ", not " + want);
					} else if (spots.get(i).kind() == Kind.BENCH) {
						helper.assertTrue(placed.mod().equals("handcrafted"), name + "'s bench is from " + placed.mod());
					}
				}
				helper.assertTrue(desks > 0, name + " has no desk");
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

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
	 * Builds {@code id} at (9, 2, 3) with Handcrafted and Beautify; when it's done every block is right, nothing was
	 * skipped, each spot holds its mod's piece, the job block {@code job} still stands where the template has it and is a
	 * job site, and the storage inside the building is exactly the template's {@code chests} containers.
	 */
	private static void build(GameTestHelper helper, ResourceLocation id, BlockState job, int chests) {
		ServerLevel level = helper.getLevel();
		BuildSite site = start(helper, id);
		BlueprintData.Placement placement = site.placement();
		BuildPlan plan = site.plan(level);
		List<FurnitureSpot> spots = BlueprintFurniture.of(id);
		FurnitureChoice choice = site.furniture();
		Map<String, String> wrong = new TreeMap<>();
		for (int i = 0; i < spots.size(); i++) {
			FurnitureSpot spot = spots.get(i);
			FurnitureChoice.Placed placed = choice.spots().get(i);
			String mod = placed == null ? "minecraft" : placed.mod();
			String want = FurnishedHomesCompatTests.standing(spot, expected().getOrDefault(spot.kind(), "minecraft"));
			if (!mod.equals(want)) {
				wrong.put(spot.kind() + "@" + spot.pos().toShortString(), mod + " (expected " + want + ")");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "spots not furnished as the order says: " + wrong);
		Blueprint template = BlueprintLibrary.get(level, id).orElseThrow();
		BlockPos origin = placement.origin();
		Set<BlockPos> jobs = new HashSet<>();
		Set<BlockPos> stores = new HashSet<>();
		for (Blueprint.Entry e : template.blocks()) {
			if (e.state().is(job.getBlock())) {
				jobs.add(origin.offset(e.pos()));
			}
			if (container(e.state()) && !e.state().is(job.getBlock())) { // the Shop Counter keeps its own ledger
				stores.add(origin.offset(e.pos()));
			}
		}
		helper.assertTrue(!jobs.isEmpty(), id + " has no " + job);
		helper.assertTrue(stores.size() == chests, id + " has " + stores.size() + " containers, not " + chests);
		BoundingBox box = BoundingBox.fromCorners(origin, origin.offset(template.size()).offset(-1, -1, -1));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(3)
				.map(p -> p.subtract(origin).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(site.skipped() == 0, site.skipped() + " skipped");
			for (FurnitureChoice.Placed placed : choice.spots().values()) {
				for (Map.Entry<BlockPos, BlockState> cell : placed.cells().entrySet()) {
					BlockState there = level.getBlockState(origin.offset(placed.pos()).offset(cell.getKey()));
					if (!cell.getValue().isAir()) {
						helper.assertTrue(there.is(cell.getValue().getBlock()), placed.kind() + " at " + placed.pos().offset(cell.getKey()).toShortString() + ": "
							+ BuiltInRegistries.BLOCK.getKey(there.getBlock()) + ", not " + BuiltInRegistries.BLOCK.getKey(cell.getValue().getBlock()));
					}
				}
			}
			for (BlockPos at : jobs) {
				helper.assertTrue(level.getBlockState(at).is(job.getBlock()), "the job block at " + at.subtract(origin).toShortString() + " is "
					+ level.getBlockState(at));
				helper.assertTrue(PoiTypes.hasPoi(level.getBlockState(at)) && level.getPoiManager().getType(at).isPresent(),
					"the job block at " + at.subtract(origin).toShortString() + " is no job site");
			}
			Set<BlockPos> found = new HashSet<>(SupplyContainers.inside(level, box));
			found.removeAll(jobs);
			helper.assertTrue(found.equals(stores), "the building's storage is " + found.stream().map(p -> p.subtract(origin).toShortString()
				+ "=" + BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock())).toList() + ", not the template's " + stores.size());
			for (BlockPos at : jobs) {
				Set<BlockPos> near = new HashSet<>(SupplyContainers.find(level, at, null));
				for (BlockPos store : stores) {
					helper.assertTrue(near.contains(store), "the job at " + at.subtract(origin).toShortString() + " doesn't find the chest at "
						+ store.subtract(origin).toShortString());
				}
			}
		});
	}

	/** A builder on {@code id} at (9, 2, 3) with Handcrafted and Beautify, his chests holding exactly what the plan costs. */
	private static BuildSite start(GameTestHelper helper, ResourceLocation id) {
		ServerLevel level = helper.getLevel();
		new WorkplaceConfig().apply();
		Furnishings.MODS = ORDER;
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
		new WorkplaceConfig().apply(); // the choice is made at the first plan and saved on the site
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
