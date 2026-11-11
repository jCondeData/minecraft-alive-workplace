package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-11-11, an independent tester) of B1156 and B1170 from the player's side: a crafter (the Glassblower, Mason,
 * Tinkerer, Librarian, Leatherworker) uses the makings kept in the chests by his OWN workstation when a builder asks him
 * for something (guide crafter.md section 6, glassblower.md). Written from the spec, not the fix: each happy-path test
 * counts every watched item in the whole test area (chests, bags, inventories, the ground, built blocks, hung frames)
 * every second, so nothing may be doubled, and at the end the makings used are exactly one craft's batch. The edges: makings
 * split between his chest and the builder's; too few makings (the builder's line names the right numbers and nothing is
 * taken); a chest one block inside and one block outside his workstation's reach ({@code SupplyContainers.RADIUS} = 8
 * blocks each way); another crafter's chest; the Carpenter, whose own chest stays out on purpose (B778); and the crafter
 * saved and loaded mid-job. Not covered here: the Furnicrafter (needs CobbleFurnies, only in the compat suite). Each test
 * has its own batch: the village and the crafters' claims are shared state.
 */
public class QaCrafterOwnChest1111cGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos OWN = STATION.east();
	private static final BlockPos ROW = new BlockPos(6, 2, 8);
	private static final BlockPos FRAME = ROW.south();
	/** A chest at the edge of the crafter's reach: 8 blocks south of his workstation (SupplyContainers.RADIUS). */
	private static final BlockPos INSIDE = STATION.south(8);
	/** One block further: out of his reach (and 12 blocks from the builder's table, out of the builder's). */
	private static final BlockPos OUTSIDE = STATION.south(9);
	/** How long after the build is done the crafter is watched for a second batch (he looks every 100 ticks). */
	private static final int SETTLE = 400;
	/** When the "short of" tests read the line and check nothing was taken. */
	private static final int LOOK = 1000;

	private record Asker(Villager builder, BuildSite site, BuildPlan plan) {
	}

	/** A quantity that crafting must keep: the weighted sum of these items (a stone brick is two slabs). */
	private record Law(String name, Map<Item, Integer> weights) {
		static Law of(String name, Object... pairs) {
			Map<Item, Integer> w = new LinkedHashMap<>();
			for (int i = 0; i < pairs.length; i += 2) {
				w.put((Item) pairs[i], (Integer) pairs[i + 1]);
			}
			return new Law(name, w);
		}

		int of(Map<Item, Integer> tally) {
			int sum = 0;
			for (Map.Entry<Item, Integer> e : weights.entrySet()) {
				sum += e.getValue() * tally.getOrDefault(e.getKey(), 0);
			}
			return sum;
		}
	}

	// ------------------------------------------------------------------------------------------ (a) only his own chest

	/** 8 sand and 2 coal in the Glassblower's own chest, 4 glass asked: exactly 4 sand and 1 coal are fired, 4 glass built. */
	//$ gametest_ticks_batch AREA '4000' '"qaOwn1111cGlass"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwn1111cGlass")
	public void theGlassblowerFiresExactlyTheAskedGlassFromHisOwnChest(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.GLASS, 4, 1, "glass"));
		Villager blower = glassblower(helper, OWN, new ItemStack(Items.SAND, 8), new ItemStack(Items.COAL, 2));
		watch(helper, a, List.of(blower.getUUID()), List.of(Law.of("sand and glass", Items.SAND, 1, Items.GLASS, 1)), (start, end) -> {
			used(helper, start, end, Items.SAND, 4);
			used(helper, start, end, Items.COAL, 1);
			exactly(helper, end, Items.GLASS, 4);
			helper.assertTrue(count(helper, OWN, Items.SAND) == 4 && count(helper, OWN, Items.COAL) == 1, "his chest should keep 4 sand and 1 coal: "
				+ contents(helper, OWN));
		});
	}

	/** 10 stone bricks in the Mason's own chest, 4 slabs asked: 2 bricks are cut (a stonecutter makes 2 slabs of one). */
	//$ gametest_ticks_batch AREA '4000' '"qaOwn1111cMason"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwn1111cMason")
	public void theMasonCutsExactlyTheAskedSlabsFromHisOwnChest(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.STONE_BRICK_SLAB, 4, 1, "mason"));
		Villager mason = mason(helper, STATION, OWN, new ItemStack(Items.STONE_BRICKS, 10));
		watch(helper, a, List.of(mason.getUUID()), List.of(Law.of("stone in half-bricks", Items.STONE_BRICKS, 2, Items.STONE_BRICK_SLAB, 1)), (start, end) -> {
			used(helper, start, end, Items.STONE_BRICKS, 2);
			exactly(helper, end, Items.STONE_BRICK_SLAB, 4);
			helper.assertTrue(count(helper, OWN, Items.STONE_BRICKS) == 8, "his chest should keep 8 stone bricks: " + contents(helper, OWN));
		});
	}

	/** 12 iron ingots in the Tinkerer's own chest, 3 bars asked: one craft, 6 ingots into 16 bars, the other 6 stay. */
	//$ gametest_ticks_batch AREA '4000' '"qaOwn1111cTinker"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwn1111cTinker")
	public void theTinkererUsesOneBatchOfHisOwnIronForTheBars(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.IRON_BARS, 3, 1, "tinker"));
		Villager tinkerer = crafter(helper, ModBlocks.TINKERS_BENCH, ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER, new ItemStack(Items.IRON_INGOT, 12));
		watch(helper, a, List.of(tinkerer.getUUID()), List.of(Law.of("iron (an ingot 8, a bar 3)", Items.IRON_INGOT, 8, Items.IRON_BARS, 3)), (start, end) -> {
			used(helper, start, end, Items.IRON_INGOT, 6);
			exactly(helper, end, Items.IRON_BARS, 16);
		});
	}

	/** 9 planks and 5 books in the Librarian's own chest, a bookshelf asked: exactly 6 planks and 3 books go into it. */
	//$ gametest_ticks_batch AREA '4000' '"qaOwn1111cLibrarian"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwn1111cLibrarian")
	public void theLibrarianUsesExactlyOneShelfsWorthOfHisOwnBooks(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.BOOKSHELF, 1, 1, "librarian"));
		Villager librarian = crafter(helper, Blocks.LECTERN, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN, new ItemStack(Items.OAK_PLANKS, 9),
			new ItemStack(Items.BOOK, 5));
		watch(helper, a, List.of(librarian.getUUID()), List.of(Law.of("planks", Items.OAK_PLANKS, 1, Items.BOOKSHELF, 6),
			Law.of("books", Items.BOOK, 1, Items.BOOKSHELF, 3)), (start, end) -> {
				used(helper, start, end, Items.OAK_PLANKS, 6);
				used(helper, start, end, Items.BOOK, 3);
				exactly(helper, end, Items.BOOKSHELF, 1);
			});
	}

	/** 3 leather and 16 sticks in the Leatherworker's own chest, a plank in the builder's: one frame (1 leather, 8 sticks), hung. */
	//$ gametest_ticks_batch AREA '4000' '"qaOwn1111cLeather"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwn1111cLeather")
	public void theLeatherworkerUsesOneFramesWorthOfHisOwnLeather(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, framed(helper), new ItemStack(Items.OAK_PLANKS, 4));
		Villager leatherworker = crafter(helper, Blocks.CAULDRON, PoiTypes.LEATHERWORKER, VillagerProfession.LEATHERWORKER, new ItemStack(Items.LEATHER, 3),
			new ItemStack(Items.STICK, 16));
		watch(helper, a, List.of(leatherworker.getUUID()), List.of(Law.of("leather", Items.LEATHER, 1, Items.ITEM_FRAME, 1),
			Law.of("sticks", Items.STICK, 1, Items.ITEM_FRAME, 8), Law.of("planks", Items.OAK_PLANKS, 1)), (start, end) -> {
				used(helper, start, end, Items.LEATHER, 1);
				used(helper, start, end, Items.STICK, 8);
				exactly(helper, end, Items.ITEM_FRAME, 1);
				helper.assertTrue(frames(helper) == 1, frames(helper) + " item frames hang on the plank");
			});
	}

	// ------------------------------------------------------------------------- (b) split between his chest and the builder's

	/** 5 sand in the Glassblower's own chest, 3 sand and the coal in the builder's, 8 glass asked: all 8 sand fired, once. */
	//$ gametest_ticks_batch AREA '4000' '"qaOwn1111cGlassSplit"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwn1111cGlassSplit")
	public void sandSplitBetweenHisChestAndTheBuildersAddsUpToTheGlass(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.GLASS, 4, 2, "glasssplit"), new ItemStack(Items.SAND, 3), new ItemStack(Items.COAL, 1));
		Villager blower = glassblower(helper, OWN, new ItemStack(Items.SAND, 5));
		watch(helper, a, List.of(blower.getUUID()), List.of(Law.of("sand and glass", Items.SAND, 1, Items.GLASS, 1)), (start, end) -> {
			used(helper, start, end, Items.SAND, 8);
			used(helper, start, end, Items.COAL, 1);
			exactly(helper, end, Items.GLASS, 8);
		});
	}

	/** 1 stone brick in the builder's chest and 5 in the Mason's, 8 slabs asked: 4 bricks cut in all, 2 left, 8 slabs. */
	//$ gametest_ticks_batch AREA '4000' '"qaOwn1111cMasonSplit"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwn1111cMasonSplit")
	public void bricksSplitBetweenHisChestAndTheBuildersAddUpToTheSlabs(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.STONE_BRICK_SLAB, 4, 2, "masonsplit"), new ItemStack(Items.STONE_BRICKS, 1));
		Villager mason = mason(helper, STATION, OWN, new ItemStack(Items.STONE_BRICKS, 5));
		watch(helper, a, List.of(mason.getUUID()), List.of(Law.of("stone in half-bricks", Items.STONE_BRICKS, 2, Items.STONE_BRICK_SLAB, 1)), (start, end) -> {
			used(helper, start, end, Items.STONE_BRICKS, 4);
			exactly(helper, end, Items.STONE_BRICK_SLAB, 8);
		});
	}

	// ------------------------------------------------------------------------------------- (c) too little in his chest

	/** 3 ingots in the Tinkerer's chest, 2 in the builder's, 3 bars (6 ingots) asked: "short of 1 Iron Ingot", nothing taken. */
	//$ gametest_ticks_batch AREA '1400' '"qaOwn1111cTinkerShort"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaOwn1111cTinkerShort")
	public void fiveIngotsOfSixReadShortOfOneAndNothingIsTaken(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.IRON_BARS, 3, 1, "tinkershort"), new ItemStack(Items.IRON_INGOT, 2));
		Villager tinkerer = crafter(helper, ModBlocks.TINKERS_BENCH, ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER, new ItemStack(Items.IRON_INGOT, 3));
		untouched(helper, a, tinkerer, Map.of(OWN, Map.of(Items.IRON_INGOT, 3), CHEST, Map.of(Items.IRON_INGOT, 2)),
			"Can't be made yet: short of 1 Iron Ingot to make 3 Iron Bars");
	}

	/**
	 * 2 sand in the Glassblower's own chest and 2 in the builder's, no fuel anywhere, 4 glass asked: the line counts both
	 * chests' sand (only the coal is short: "short of 1 Coal to make 4 Glass", glassblower.md) and no sand is taken.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaOwn1111cGlassShort"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaOwn1111cGlassShort")
	public void sandInBothChestsButNoCoalReadsShortOfOneCoalAndNothingIsTaken(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.GLASS, 4, 1, "glassshort"), new ItemStack(Items.SAND, 2));
		Villager blower = glassblower(helper, OWN, new ItemStack(Items.SAND, 2));
		untouched(helper, a, blower, Map.of(OWN, Map.of(Items.SAND, 2), CHEST, Map.of(Items.SAND, 2)), "Can't be made yet: short of 1 Coal to make 4 Glass");
	}

	/** 6 planks and 2 books in the Librarian's own chest, a bookshelf asked: "short of 1 Book to make 1 Bookshelf", nothing taken. */
	//$ gametest_ticks_batch AREA '1400' '"qaOwn1111cLibrarianShort"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaOwn1111cLibrarianShort")
	public void twoBooksOfThreeReadShortOfOneBookAndNothingIsTaken(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.BOOKSHELF, 1, 1, "libshort"));
		Villager librarian = crafter(helper, Blocks.LECTERN, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN, new ItemStack(Items.OAK_PLANKS, 6),
			new ItemStack(Items.BOOK, 2));
		untouched(helper, a, librarian, Map.of(OWN, Map.of(Items.OAK_PLANKS, 6, Items.BOOK, 2)), "Can't be made yet: short of 1 Book to make 1 Bookshelf");
	}

	// ------------------------------------------------------------------------------------ (d) whose chest it is: the reach

	/** No chest by the Mason's cutter, one 8 blocks south of it (the edge of his reach) with 2 bricks: it's his, the slabs come. */
	//$ gametest_ticks_batch AREA '4000' '"qaOwn1111cMasonInside"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwn1111cMasonInside")
	public void aChestAtTheEdgeOfTheMasonsReachIsHis(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.STONE_BRICK_SLAB, 4, 1, "masoninside"));
		Villager mason = mason(helper, STATION, INSIDE, new ItemStack(Items.STONE_BRICKS, 2));
		watch(helper, a, List.of(mason.getUUID()), List.of(Law.of("stone in half-bricks", Items.STONE_BRICKS, 2, Items.STONE_BRICK_SLAB, 1)), (start, end) -> {
			used(helper, start, end, Items.STONE_BRICKS, 2);
			exactly(helper, end, Items.STONE_BRICK_SLAB, 4);
		});
	}

	/** The same chest one block further (9 south): not his and not the builder's, so it isn't raided; the line names the bricks. */
	//$ gametest_ticks_batch AREA '1400' '"qaOwn1111cMasonOutside"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaOwn1111cMasonOutside")
	public void aChestOneBlockPastTheMasonsReachIsNotRaided(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.STONE_BRICK_SLAB, 4, 1, "masonoutside"));
		Villager mason = mason(helper, STATION, OUTSIDE, new ItemStack(Items.STONE_BRICKS, 2));
		// the stonecutter cuts these slabs from stone or from stone bricks alike (2 of either), so the line may name either
		untouched(helper, a, mason, Map.of(OUTSIDE, Map.of(Items.STONE_BRICKS, 2)), "Can't be made yet: short of 2 Stone Bricks to make 4 Stone Brick Slabs",
			"Can't be made yet: short of 2 Stone to make 4 Stone Brick Slabs");
	}

	/** The Glassblower's sand and coal in a chest 8 blocks south of his kiln (the edge of his reach): his, the glass comes. */
	//$ gametest_ticks_batch AREA '4000' '"qaOwn1111cGlassInside"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwn1111cGlassInside")
	public void aChestAtTheEdgeOfTheGlassblowersReachIsHis(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.GLASS, 4, 1, "glassinside"));
		Villager blower = glassblower(helper, INSIDE, new ItemStack(Items.SAND, 4), new ItemStack(Items.COAL, 1));
		watch(helper, a, List.of(blower.getUUID()), List.of(Law.of("sand and glass", Items.SAND, 1, Items.GLASS, 1)), (start, end) -> {
			used(helper, start, end, Items.SAND, 4);
			used(helper, start, end, Items.COAL, 1);
			exactly(helper, end, Items.GLASS, 4);
		});
	}

	/** One block further (9 south): not his, not raided; the builder's line still says what's short, as B1156's report read. */
	//$ gametest_ticks_batch AREA '1400' '"qaOwn1111cGlassOutside"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaOwn1111cGlassOutside")
	public void aChestOneBlockPastTheGlassblowersReachIsNotRaided(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.GLASS, 4, 1, "glassoutside"));
		Villager blower = glassblower(helper, OUTSIDE, new ItemStack(Items.SAND, 4), new ItemStack(Items.COAL, 1));
		untouched(helper, a, blower, Map.of(OUTSIDE, Map.of(Items.SAND, 4, Items.COAL, 1)), "Can't be made yet: short of 4 Sand, 1 Coal to make 4 Glass");
	}

	/**
	 * Another crafter's chest: a Mason works a cutter 11 blocks south of the Tinkerer's bench, with 6 iron ingots in the chest
	 * by his cutter. The Tinkerer (an empty chest of his own) never takes the Mason's iron for the builder's bars.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaOwn1111cOtherCrafter"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaOwn1111cOtherCrafter")
	public void anotherCraftersChestIsNotTheTinkerersToRaid(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.IRON_BARS, 3, 1, "othercrafter"));
		Villager tinkerer = crafter(helper, ModBlocks.TINKERS_BENCH, ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER);
		BlockPos cutter = STATION.south(11);
		BlockPos masons = cutter.east();
		mason(helper, cutter, masons, new ItemStack(Items.IRON_INGOT, 6));
		helper.onEachTick(() -> {
			helper.assertTrue(count(helper, masons, Items.IRON_INGOT) == 6, "the Mason's chest was raided: " + contents(helper, masons));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(tinkerer, 0) == 0, "the Tinkerer made bars with iron that wasn't his");
		});
		helper.runAfterDelay(LOOK, () -> {
			String text = line(helper, a);
			helper.assertTrue(text != null && text.startsWith("Can't be made yet: short of") && text.contains("Iron") && text.endsWith("to make 3 Iron Bars"),
				"the builder's line should say the iron for the bars is short: " + text);
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(tinkerer).isEmpty(), "the Tinkerer carries " + ModAttachments.BUILDER_BAG.getOrCreate(tinkerer).stacks());
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------------------------------- (e) the Carpenter stays out

	/**
	 * crafter.md section 6 (B778, B1170): the Carpenter's own chest isn't counted for makings. 4 oak logs by his bench, 4 oak
	 * planks asked, nothing in the builder's chest: the logs stay his and the builder's line reads "short of 1 Oak Log".
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaOwn1111cCarpenter"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaOwn1111cCarpenter")
	public void theCarpentersOwnLogsAreNotSawnForTheBuilder(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.OAK_PLANKS, 4, 1, "carpenter"));
		Villager carpenter = crafter(helper, ModBlocks.CARPENTERS_BENCH, ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER,
			new ItemStack(Items.OAK_LOG, 4));
		untouched(helper, a, carpenter, Map.of(OWN, Map.of(Items.OAK_LOG, 4)), "Can't be made yet: short of 1 Oak Log to make 4 Oak Planks");
	}

	// ---------------------------------------------------------------------------------------------- (f) save and reload

	/** 6 bricks in the Mason's own chest, 8 slabs asked; he is saved and loaded while cutting and again while carrying: 4 bricks used. */
	//$ gametest_ticks_batch AREA '4000' '"qaOwn1111cMasonReload"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwn1111cMasonReload")
	public void theMasonReloadedMidJobStillUsesExactlyHisFourBricks(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.STONE_BRICK_SLAB, 4, 2, "masonreload"));
		Villager mason = mason(helper, STATION, OWN, new ItemStack(Items.STONE_BRICKS, 6));
		Map<String, AtomicBoolean> seen = reloads(helper, mason.getUUID());
		watch(helper, a, List.of(mason.getUUID()), List.of(Law.of("stone in half-bricks", Items.STONE_BRICKS, 2, Items.STONE_BRICK_SLAB, 1)), (start, end) -> {
			used(helper, start, end, Items.STONE_BRICKS, 4);
			exactly(helper, end, Items.STONE_BRICK_SLAB, 8);
			helper.assertTrue(count(helper, OWN, Items.STONE_BRICKS) == 2, "his chest should keep 2 bricks: " + contents(helper, OWN));
			seen.forEach((what, happened) -> helper.assertTrue(happened.get(), "never happened: " + what + ", so the reload wasn't tested"));
		});
	}

	/** 8 sand and 2 coal in the Glassblower's own chest, 8 glass asked; reloaded while firing and while carrying: 8 sand, 1 coal used. */
	//$ gametest_ticks_batch AREA '4000' '"qaOwn1111cGlassReload"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwn1111cGlassReload")
	public void theGlassblowerReloadedMidJobStillFiresExactlyTheAskedGlass(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.GLASS, 4, 2, "glassreload"));
		Villager blower = glassblower(helper, OWN, new ItemStack(Items.SAND, 8), new ItemStack(Items.COAL, 2));
		Map<String, AtomicBoolean> seen = reloads(helper, blower.getUUID());
		watch(helper, a, List.of(blower.getUUID()), List.of(Law.of("sand and glass", Items.SAND, 1, Items.GLASS, 1)), (start, end) -> {
			used(helper, start, end, Items.SAND, 8);
			used(helper, start, end, Items.COAL, 1);
			exactly(helper, end, Items.GLASS, 8);
			seen.forEach((what, happened) -> helper.assertTrue(happened.get(), "never happened: " + what + ", so the reload wasn't tested"));
		});
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	private static void prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
	}

	/** A blueprint of {@code dx} x {@code dz} {@code block}s on the floor at ROW. */
	private static ResourceLocation area(GameTestHelper helper, Block block, int dx, int dz, String what) {
		for (int x = 0; x < dx; x++) {
			for (int z = 0; z < dz; z++) {
				helper.setBlock(ROW.offset(x, 0, z), block);
			}
		}
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qaown1111c_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(dx, 1, dz), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < dx; x++) {
			for (int z = 0; z < dz; z++) {
				helper.setBlock(ROW.offset(x, 0, z), Blocks.AIR);
			}
		}
		return id;
	}

	/** A blueprint of one oak plank with an empty item frame on its south face. */
	private static ResourceLocation framed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(ROW, Blocks.OAK_PLANKS);
		ItemFrame frame = new ItemFrame(level, helper.absolutePos(FRAME), Direction.SOUTH);
		helper.assertTrue(frame.survives(), "setup: the frame can't hang on the plank");
		level.addFreshEntity(frame);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qaown1111c_frame_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(1, 1, 2), true, Blocks.STRUCTURE_VOID);
		frame.discard();
		helper.setBlock(ROW, Blocks.AIR);
		return id;
	}

	/** A builder at the Builder's Bench at BENCH, its chest at CHEST holding {@code items}, building the blueprint {@code id} at ROW. */
	private static Asker asker(GameTestHelper helper, ResourceLocation id, ItemStack... items) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container container = helper.getBlockEntity(CHEST);
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site == null ? null : site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("setup: blueprint not found: " + id);
		}
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		return new Asker(builder, site, plan);
	}

	private static void fill(GameTestHelper helper, BlockPos chest, ItemStack... items) {
		helper.setBlock(chest, Blocks.CHEST);
		Container c = helper.getBlockEntity(chest);
		for (int i = 0; i < items.length; i++) {
			c.setItem(i, items[i].copy());
		}
	}

	/** A crafter at {@code station} (STATION), with a chest by it (OWN) holding {@code own}. */
	private static Villager crafter(GameTestHelper helper, Block station, ResourceKey<PoiType> poi, VillagerProfession job, ItemStack... own) {
		helper.setBlock(STATION, station);
		fill(helper, OWN, own);
		Villager v = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), poi, job);
		return v;
	}

	/** A Mason at a stonecutter at {@code cutter}, with a chest at {@code chest} holding {@code own} (no other chest by him). */
	private static Villager mason(GameTestHelper helper, BlockPos cutter, BlockPos chest, ItemStack... own) {
		helper.setBlock(cutter, Blocks.STONECUTTER);
		fill(helper, chest, own);
		Villager v = helper.spawn(EntityType.VILLAGER, cutter.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(cutter), PoiTypes.MASON, VillagerProfession.MASON);
		return v;
	}

	/** A Glassblower at his Glass Kiln at STATION, with a chest at {@code chest} holding {@code own} (no other chest by him). */
	private static Villager glassblower(GameTestHelper helper, BlockPos chest, ItemStack... own) {
		helper.setBlock(STATION, ModBlocks.GLASS_KILN);
		fill(helper, chest, own);
		Villager v = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), ModVillagers.GLASS_KILN_POI, ModVillagers.GLASSBLOWER);
		return v;
	}

	private static String line(GameTestHelper helper, Asker a) {
		BuildSite live = BuildSiteManager.get(helper.getLevel()).get(a.site().id());
		if (live == null) {
			return "(the build is done)";
		}
		Component line = JobShortages.line(helper.getLevel(), live.id(), live.missing(), helper.absolutePos(BENCH));
		return line == null ? null : line.getString();
	}

	/**
	 * The "short of" checks: every tick the chests in {@code stocked} hold just what they were stocked with and the crafter
	 * has made nothing (no partial batch is fetched), and after {@link #LOOK} ticks the builder's line reads {@code expected},
	 * the crafter carries nothing and holds no job.
	 */
	private static void untouched(GameTestHelper helper, Asker a, Villager crafter, Map<BlockPos, Map<Item, Integer>> stocked, String... expected) {
		helper.onEachTick(() -> {
			stocked.forEach((chest, items) -> items.forEach((item, n) -> {
				int there = count(helper, chest, item);
				if (there != n) {
					throw new GameTestAssertException("the chest at " + chest.toShortString() + " should keep " + n + " " + item + ", it holds " + contents(helper, chest)
						+ "; the crafter carries " + ModAttachments.BUILDER_BAG.getOrCreate(crafter).stacks() + "; the builder's line: " + line(helper, a));
				}
			}));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0) == 0, "the crafter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0)
				+ " from too little");
		});
		helper.runAfterDelay(LOOK, () -> {
			String text = line(helper, a);
			helper.assertTrue(List.of(expected).contains(text), "the builder's line: '" + text + "', the spec's: '" + String.join("' or '", expected) + "'");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(crafter).isEmpty(), "the crafter carries " + ModAttachments.BUILDER_BAG.getOrCreate(crafter).stacks());
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(crafter) == null, "the crafter holds a job he can't finish: " + ModAttachments.CRAFTER_JOB.get(crafter));
			helper.succeed();
		});
	}

	/** Saves and loads the crafter the first time he is at his workstation making, and the first time he carries what he made. */
	private static Map<String, AtomicBoolean> reloads(GameTestHelper helper, UUID crafter) {
		Map<String, AtomicBoolean> seen = new LinkedHashMap<>();
		AtomicBoolean crafting = new AtomicBoolean();
		AtomicBoolean delivering = new AtomicBoolean();
		seen.put("the crafter reloaded while making", crafting);
		seen.put("the crafter reloaded while carrying", delivering);
		ServerLevel level = helper.getLevel();
		helper.onEachTick(() -> {
			if (!(level.getEntity(crafter) instanceof Villager v)) {
				return;
			}
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(v);
			if (job == null) {
				return;
			}
			if (!crafting.get() && job.phase() == CrafterJob.Phase.CRAFTING) {
				crafting.set(true);
				QaCarpenterStorehouse1101GameTests.reload(level, v);
			} else if (!delivering.get() && job.phase() == CrafterJob.Phase.DELIVERING) {
				delivering.set(true);
				QaCarpenterStorehouse1101GameTests.reload(level, v);
			}
		});
		return seen;
	}

	/**
	 * Watches the whole area: every 20 ticks no {@code laws} quantity is above its start (nothing doubled). When the build is
	 * done, {@link #SETTLE} more ticks with no crafter making anything more, then every law holds exactly (nothing lost) and
	 * {@code end} checks what was used.
	 */
	private static void watch(GameTestHelper helper, Asker a, List<UUID> crafters, List<Law> laws, BiConsumer<Map<Item, Integer>, Map<Item, Integer>> end) {
		ServerLevel level = helper.getLevel();
		Set<Item> items = new LinkedHashSet<>(List.of(Items.COAL, Items.CHARCOAL));
		laws.forEach(l -> items.addAll(l.weights().keySet()));
		Map<Item, Integer> start = tally(helper, items);
		long t0 = level.getGameTime();
		AtomicLong doneAt = new AtomicLong(-1);
		Map<UUID, Integer> craftedAtDone = new HashMap<>();
		helper.onEachTick(() -> {
			long t = level.getGameTime() - t0;
			if (t % 20 != 0) {
				return;
			}
			Map<Item, Integer> now = tally(helper, items);
			for (Law law : laws) {
				if (law.of(now) > law.of(start)) {
					throw new GameTestAssertException("at tick " + t + " the " + law.name() + " add up to " + law.of(now) + ", more than the " + law.of(start)
						+ " at the start: something was doubled; " + state(helper, a, crafters, start, now));
				}
			}
			if (BuildSiteManager.get(level).get(a.site().id()) != null) {
				return;
			}
			if (doneAt.get() < 0) {
				doneAt.set(t);
				crafters.forEach(id -> craftedAtDone.put(id, crafted(level, id)));
				return;
			}
			for (UUID id : crafters) {
				helper.assertTrue(crafted(level, id) == craftedAtDone.get(id), "at tick " + t + ", " + (t - doneAt.get()) + " ticks after the build was done, the "
					+ "crafter made more (" + crafted(level, id) + ", was " + craftedAtDone.get(id) + "); " + state(helper, a, crafters, start, now));
			}
			if (t - doneAt.get() < SETTLE) {
				return;
			}
			List<BlockPos> unfinished = a.plan().unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build; " + state(helper, a, crafters, start, now));
			for (Law law : laws) {
				helper.assertTrue(law.of(now) == law.of(start), "the " + law.name() + " add up to " + law.of(now) + ", not " + law.of(start) + ": some was lost; "
					+ state(helper, a, crafters, start, now));
			}
			for (UUID id : crafters) {
				helper.assertTrue(crafted(level, id) >= 1, "the crafter's count says he made nothing; " + state(helper, a, crafters, start, now));
			}
			try {
				end.accept(start, now);
			} catch (GameTestAssertException e) {
				throw new GameTestAssertException(e.getMessage() + "; " + state(helper, a, crafters, start, now));
			}
			helper.succeed();
		});
	}

	private static int crafted(ServerLevel level, UUID id) {
		return level.getEntity(id) instanceof Villager v ? ModAttachments.ITEMS_CRAFTED.getOrElse(v, 0) : -1;
	}

	private static void used(GameTestHelper helper, Map<Item, Integer> start, Map<Item, Integer> end, Item item, int expected) {
		int used = start.getOrDefault(item, 0) - end.getOrDefault(item, 0);
		helper.assertTrue(used == expected, used + " " + item + " were used, the spec's one batch for the asked amount is " + expected);
	}

	private static void exactly(GameTestHelper helper, Map<Item, Integer> end, Item item, int expected) {
		int n = end.getOrDefault(item, 0);
		helper.assertTrue(n == expected, n + " " + item + " exist in the area, expected " + expected);
	}

	/** Every one of {@code items} in the test area: built blocks above the floor, containers, villagers' bags and inventories, the ground, hung frames. */
	private static Map<Item, Integer> tally(GameTestHelper helper, Set<Item> items) {
		Map<Item, Integer> out = new HashMap<>();
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds().inflate(3);
		int floor = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
		for (BlockPos p : BlockPos.betweenClosed((int) box.minX, (int) box.minY, (int) box.minZ, (int) box.maxX, (int) box.maxY, (int) box.maxZ)) {
			BlockState state = level.getBlockState(p);
			if (p.getY() >= floor && !state.isAir() && items.contains(state.getBlock().asItem())) {
				out.merge(state.getBlock().asItem(), 1, Integer::sum);
			}
			if (state.hasBlockEntity()) {
				BlockEntity be = level.getBlockEntity(p);
				if (be instanceof Container c) {
					for (int i = 0; i < c.getContainerSize(); i++) {
						ItemStack s = c.getItem(i);
						if (items.contains(s.getItem())) {
							out.merge(s.getItem(), s.getCount(), Integer::sum);
						}
					}
				}
			}
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, box.inflate(16))) {
			for (Item item : items) {
				int n = ModAttachments.BUILDER_BAG.getOrCreate(v).count(item) + v.getInventory().countItem(item);
				if (n > 0) {
					out.merge(item, n, Integer::sum);
				}
			}
		}
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box.inflate(16))) {
			if (items.contains(e.getItem().getItem())) {
				out.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
			}
		}
		if (items.contains(Items.ITEM_FRAME)) {
			for (ItemFrame f : level.getEntitiesOfClass(ItemFrame.class, box, ItemFrame::isAlive)) {
				if (f.getType() == EntityType.ITEM_FRAME) {
					out.merge(Items.ITEM_FRAME, 1, Integer::sum);
				}
			}
		}
		return out;
	}

	private static int frames(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemFrame.class, new AABB(helper.absolutePos(FRAME)).inflate(0.5), ItemFrame::isAlive).size();
	}

	private static int count(GameTestHelper helper, BlockPos pos, Item item) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof Container c ? c.countItem(item) : 0;
	}

	private static String contents(GameTestHelper helper, BlockPos pos) {
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof Container c)) {
			return "(no chest)";
		}
		Map<Item, Integer> m = new LinkedHashMap<>();
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (!s.isEmpty()) {
				m.merge(s.getItem(), s.getCount(), Integer::sum);
			}
		}
		return m.toString();
	}

	private static String state(GameTestHelper helper, Asker a, List<UUID> crafters, Map<Item, Integer> start, Map<Item, Integer> now) {
		ServerLevel level = helper.getLevel();
		BuildSite live = BuildSiteManager.get(level).get(a.site().id());
		List<String> out = new ArrayList<>();
		out.add("site " + (live == null ? "done" : live.stage() + "/" + live.status() + " missing " + live.missing()));
		out.add("line " + line(helper, a));
		for (UUID id : crafters) {
			if (level.getEntity(id) instanceof Villager v) {
				out.add(v.getVillagerData().getProfession() + " made " + ModAttachments.ITEMS_CRAFTED.getOrElse(v, 0) + " job " + ModAttachments.CRAFTER_JOB.get(v)
					+ " bag " + ModAttachments.BUILDER_BAG.getOrCreate(v).stacks());
			} else {
				out.add("crafter gone");
			}
		}
		out.add("builder's chest " + contents(helper, CHEST));
		out.add("own " + contents(helper, OWN));
		out.add("start " + start + " now " + now);
		return String.join("; ", out);
	}
}
