package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 37.4: a builder builds the furnished Starter Cottage and Inn with the pack's furniture mods, from chests
 * holding exactly what the plan costs. Every block ends up right, nothing is skipped, and every furniture spot of the
 * furnished rooms (dining sets, nightstands, couches, shelves of crockery, lamps, pictures, blinds) is the piece of the
 * mod the order picks for its kind: Handcrafted first (Beautify for what Handcrafted doesn't make), or CobbleFurnies
 * first. Lucky's Cozy Home can't load here, so clocks stay vanilla (the pack test checks Cozy Home).
 */
public class FurnishedHomesCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final ResourceLocation COTTAGE = AliveWorkplace.id("starter_cottage");
	private static final ResourceLocation INN = AliveWorkplace.id("inn");

	/** Each kind's mod with Handcrafted and Beautify (a pack with those two): Handcrafted's pieces, Beautify's decorations. */
	private static Map<FurnitureSpot.Kind, String> handcraftedFirst() {
		Map<FurnitureSpot.Kind, String> out = new EnumMap<>(FurnitureSpot.Kind.class);
		for (FurnitureSpot.Kind kind : List.of(FurnitureSpot.Kind.CHAIR, FurnitureSpot.Kind.TABLE, FurnitureSpot.Kind.BED,
			FurnitureSpot.Kind.NIGHTSTAND, FurnitureSpot.Kind.COUCH, FurnitureSpot.Kind.SHELF, FurnitureSpot.Kind.CROCKERY)) {
			out.put(kind, "handcrafted");
		}
		for (FurnitureSpot.Kind kind : List.of(FurnitureSpot.Kind.LAMP, FurnitureSpot.Kind.PICTURE, FurnitureSpot.Kind.BLINDS)) {
			out.put(kind, "beautify");
		}
		out.put(FurnitureSpot.Kind.CLOCK, "minecraft");
		return out;
	}

	/** With CobbleFurnies first: its seats, tables, nightstands, lamps and curtains; Handcrafted's beds and shelves. */
	private static Map<FurnitureSpot.Kind, String> cobbleFurniesFirst() {
		Map<FurnitureSpot.Kind, String> out = handcraftedFirst();
		for (FurnitureSpot.Kind kind : List.of(FurnitureSpot.Kind.CHAIR, FurnitureSpot.Kind.TABLE, FurnitureSpot.Kind.NIGHTSTAND,
			FurnitureSpot.Kind.COUCH, FurnitureSpot.Kind.LAMP, FurnitureSpot.Kind.BLINDS)) {
			out.put(kind, "cobblefurnies");
		}
		return out;
	}

	//$ gametest_ticks_batch HUGE_AREA '30000' '"furnished_cottage_handcrafted"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "furnished_cottage_handcrafted")
	public void aBuilderBuildsTheCottageWithHandcrafted(GameTestHelper helper) {
		build(helper, COTTAGE, List.of("handcrafted", "beautify"), handcraftedFirst());
	}

	//$ gametest_ticks_batch HUGE_AREA '30000' '"furnished_cottage_cobblefurnies"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "furnished_cottage_cobblefurnies")
	public void aBuilderBuildsTheCottageWithCobbleFurnies(GameTestHelper helper) {
		build(helper, COTTAGE, List.of("cobblefurnies", "handcrafted", "beautify"), cobbleFurniesFirst());
	}

	//$ gametest_ticks_batch HUGE_AREA '40000' '"furnished_inn_handcrafted"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "furnished_inn_handcrafted")
	public void aBuilderBuildsTheInnWithHandcrafted(GameTestHelper helper) {
		build(helper, INN, List.of("handcrafted", "beautify"), handcraftedFirst());
	}

	//$ gametest_ticks_batch HUGE_AREA '40000' '"furnished_inn_cobblefurnies"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "furnished_inn_cobblefurnies")
	public void aBuilderBuildsTheInnWithCobbleFurnies(GameTestHelper helper) {
		build(helper, INN, List.of("cobblefurnies", "handcrafted", "beautify"), cobbleFurniesFirst());
	}

	/** Without the Handcrafted couches in the chests the builder builds the rest and waits for them, by their own item; no vanilla stand-in. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"furnished_cottage_missing"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "furnished_cottage_missing")
	public void theBuilderWaitsForAMissingCouch(GameTestHelper helper) {
		Item couch = BuiltInRegistries.ITEM.get(ResourceLocation.parse("handcrafted:spruce_couch"));
		BuildSite site = start(helper, COTTAGE, List.of("handcrafted", "beautify"), couch);
		// Everything else is in the chests: once he has taken it all, the couch is the one thing he still waits for
		helper.succeedWhen(() -> helper.assertTrue(site.missing().keySet().equals(java.util.Set.of(couch)),
			"not waiting for the couch alone: " + site.stage() + " " + site.status() + " missing " + site.missing()));
	}

	/** A furnished cottage's site saved and loaded keeps every piece it chose, whatever the order says after the load. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"furnished_reload"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "furnished_reload")
	public void aSavedFurnishedSiteKeepsEveryPiece(GameTestHelper helper) {
		try {
			new WorkplaceConfig().apply();
			BuildSite site = new BuildSite(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "test", INN,
				new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)), Rotation.NONE, Mirror.NONE));
			site.plan(helper.getLevel());
			site.advance();
			net.minecraft.nbt.CompoundTag saved = site.save();
			Furnishings.MODS = List.of("cobblefurnies");
			BuildSite loaded = BuildSite.load(saved);
			loaded.plan(helper.getLevel());
			helper.assertTrue(loaded.furniture().equals(site.furniture()), "the Inn's furniture changed over a reload");
			helper.assertTrue(site.furniture().spots().size() >= 30, "too few furnished spots in the Inn: " + site.furniture().spots().size());
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/** With furnishedBuilds off the furnished rooms are vanilla, every spot: the cottage and the Inn plan no modded piece. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"furnished_reload"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "furnished_reload")
	public void switchedOffTheRoomsAreVanilla(GameTestHelper helper) {
		try {
			WorkplaceConfig.parse("{\"furnishedBuilds\": false}").apply();
			for (ResourceLocation id : List.of(COTTAGE, INN)) {
				helper.assertTrue(FurnitureChoice.forNewBuild(id).isVanilla(), id + " is furnished with the switch off");
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/**
	 * Builds {@code id} at (9, 2, 3) with the furniture mods tried in {@code order}; when it's done every block is right,
	 * nothing was skipped, and each spot's piece is from the mod {@code expected} names for its kind.
	 */
	private static void build(GameTestHelper helper, ResourceLocation id, List<String> order, Map<FurnitureSpot.Kind, String> expected) {
		BuildSite site = start(helper, id, order, null);
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = site.placement();
		BuildPlan plan = site.plan(level);
		List<FurnitureSpot> spots = BlueprintFurniture.of(id);
		FurnitureChoice choice = site.furniture();
		Map<String, String> wrong = new TreeMap<>();
		for (int i = 0; i < spots.size(); i++) {
			FurnitureSpot spot = spots.get(i);
			FurnitureChoice.Placed placed = choice.spots().get(i);
			String mod = placed == null ? "minecraft" : placed.mod();
			String want = expected.getOrDefault(spot.kind(), "minecraft");
			if (!mod.equals(want)) {
				wrong.put(spot.kind() + "@" + spot.pos().toShortString(), mod + " (expected " + want + ")");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "spots not furnished as the order says: " + wrong);
		helper.assertTrue(spots.stream().map(FurnitureSpot::kind).distinct().count() >= 10, "the furnished rooms have too few kinds of piece: " + spots.size());
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(3)
				.map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(site.skipped() == 0, site.skipped() + " skipped");
			for (FurnitureChoice.Placed placed : choice.spots().values()) {
				for (Map.Entry<BlockPos, BlockState> cell : placed.cells().entrySet()) {
					BlockState there = level.getBlockState(placement.origin().offset(placed.pos()).offset(cell.getKey()));
					if (!cell.getValue().isAir()) {
						helper.assertTrue(there.is(cell.getValue().getBlock()), placed.kind() + " at " + placed.pos().offset(cell.getKey()).toShortString() + ": "
							+ BuiltInRegistries.BLOCK.getKey(there.getBlock()) + ", not " + BuiltInRegistries.BLOCK.getKey(cell.getValue().getBlock()));
					}
				}
			}
		});
	}

	/**
	 * Starts a builder on {@code id} at (9, 2, 3) with the furniture mods tried in {@code order}, his chests holding
	 * exactly what the plan costs, less every {@code without} (null: nothing held back).
	 */
	private static BuildSite start(GameTestHelper helper, ResourceLocation id, List<String> order, Item without) {
		ServerLevel level = helper.getLevel();
		new WorkplaceConfig().apply();
		Furnishings.MODS = order;
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
		// The choice is made at the first plan and saved on the site: the order can go back to the default now
		new WorkplaceConfig().apply();
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + id);
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			if (e.getKey() == without) {
				continue;
			}
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
