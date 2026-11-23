package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnishingTable;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

/**
 * QA (2026-11-23) for ROADMAP 49.2, the Daub House (house_t1_b), with the pack's furniture mods. Written from the spec
 * (docs/wiki/daub-house.md, 36.23a-c, STYLE.md's owner furniture rules) rather than the code: "every piece is a
 * furniture spot, so a builder with the furniture mods builds the mods' chairs, table, beds, counters, lamps and
 * crockery in their places"; "the one standing lamp on its side table by the door"; "plates on the counters and the
 * shelf above them (none by the front door)" (the kitchen: tools/blueprints/js/house_t1_b/plan.mjs, walls x 8..11,
 * z 3..9); "Up a straight stair, the loft: four beds"; "moddedLibrary and the furniture switches apply as for every home".
 */
public class QaDaubFurnished1123bCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final ResourceLocation DAUB = AliveWorkplace.id("house_t1_b");
	private static final List<String> ORDER = List.of("handcrafted", "beautify");
	/** Where the house goes in the test area: its front (z = 0) row faces the barrels and the bench. */
	private static final BlockPos AT = new BlockPos(9, 2, 3);
	/** The kitchen's walls (plan.mjs, MASSES.kitchen), inclusive. */
	private static final int KITCHEN_X0 = 8, KITCHEN_X1 = 11, KITCHEN_Z0 = 3, KITCHEN_Z1 = 9;
	/** The front door (plan.mjs: frontDoor at [4, 1, 2]). */
	private static final BlockPos FRONT_DOOR = new BlockPos(4, 1, 2);

	// ---- 1. the furnished house built across a restart mid-DECORATION -------------------------------------------------

	/**
	 * A builder builds the Daub House with Handcrafted and Beautify from barrels holding exactly its material list; the
	 * server restarts (sites and builder saved and loaded) in the middle of DECORATION. It ends DONE, nothing skipped,
	 * every block as drawn, every modded piece standing in its spot; the plates only in the kitchen, one per crockery spot;
	 * the standing lamp on the side table's own piece; no modded piece over air.
	 */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"qaDaubFurnished1123b_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "qaDaubFurnished1123b_build")
	public void qaTheFurnishedDaubHouseIsBuiltAcrossARestartMidDecoration(GameTestHelper helper) {
		Run run = start(helper, null);
		ServerLevel level = helper.getLevel();
		UUID id = run.site().id();
		FurnitureChoice choice = run.site().furniture();
		checkChoice(helper, choice);
		Villager[] builder = {run.builder()};
		BuildSite[] current = {run.site()};
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			BuildSite now = BuildSiteManager.get(level).get(id);
			if (now == null || reloaded[0] || now.stage() != BuildPlan.Stage.DECORATION || now.cursor() < 3) {
				return;
			}
			reloaded[0] = true;
			BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			level.getDataStorage().set(BuildSiteManager.NAME, loaded);
			current[0] = loaded.get(id);
			helper.assertTrue(current[0] != null, "the Daub House site didn't come back from the save");
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(builder[0].save(tag), "the builder wasn't saved");
			builder[0].discard();
			Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
			helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
			level.addFreshEntity(back);
			builder[0] = (Villager) back;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never restarted mid-DECORATION (stage now " + current[0].stage() + ")");
			assertBuilt(helper, run, current[0]);
			helper.assertTrue(current[0].furniture().spots().equals(choice.spots()), "the furniture changed over the restart");
			assertPieces(helper, run.placement().origin(), current[0].furniture());
		});
	}

	// ---- 2. every bed walked to from the front door ----------------------------------------------------------------------

	/**
	 * The furnished house as the builder plans it (Handcrafted and Beautify), its doors open: a villager on the street
	 * before the front door finds a path to every one of the four beds. The furniture (fancy beds, nightstands, desk,
	 * chairs, tables, counters) doesn't block the way in, the stair or the loft.
	 */
	//$ gametest_ticks_batch HUGE_AREA '600' '"qaDaubFurnished1123b_beds"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 600, batch = "qaDaubFurnished1123b_beds")
	public void qaEveryFurnishedBedIsWalkedToFromTheFrontDoor(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(AT), Rotation.NONE, Mirror.NONE);
		BuildSite site;
		try {
			new WorkplaceConfig().apply();
			Furnishings.MODS = ORDER;
			site = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", DAUB, placement);
			site.plan(level);
		} finally {
			new WorkplaceConfig().apply();
		}
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no blueprint " + DAUB);
		FurnitureChoice choice = site.furniture();
		helper.assertTrue(choice.spots().values().stream().anyMatch(p -> p.kind() == FurnitureSpot.Kind.BED && p.mod().equals("handcrafted")),
			"(setup) no Handcrafted bed chosen for the Daub House: " + choice.spots().size() + " pieces");
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2 | 16);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2 | 16);
				}
			}
		}
		BlockPos origin = placement.origin();
		// Every wooden door open (the spec's walk: doors open)
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(12, 13, 12))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof DoorBlock && !s.is(Blocks.IRON_DOOR) && !s.getValue(DoorBlock.OPEN)) {
				level.setBlock(p, s.setValue(DoorBlock.OPEN, true), 2 | 16);
			}
		}
		helper.assertTrue(level.getBlockState(origin.offset(FRONT_DOOR)).getBlock() instanceof DoorBlock,
			"(setup) no door at the front door " + FRONT_DOOR.toShortString() + ": " + level.getBlockState(origin.offset(FRONT_DOOR)));
		// Each bed's head cell, by the planned pieces
		List<BlockPos> beds = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(12, 13, 12))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
				beds.add(p.immutable());
			}
		}
		helper.assertTrue(beds.size() == 4, "the furnished Daub House has " + beds.size() + " beds standing, not four: "
			+ beds.stream().map(b -> b.subtract(origin).toShortString() + "=" + BuiltInRegistries.BLOCK.getKey(level.getBlockState(b).getBlock())).toList());
		// A villager on the street, two blocks before the front door
		BlockPos street = origin.offset(FRONT_DOOR.getX(), 0, -1);
		Villager walker = EntityType.VILLAGER.create(level);
		walker.setNoAi(false);
		walker.moveTo(street.getX() + 0.5, street.getY(), street.getZ() + 0.5, 0f, 0f);
		level.addFreshEntity(walker);
		helper.runAfterDelay(5, () -> {
			List<String> unreached = new ArrayList<>();
			for (BlockPos head : beds) {
				Path path = walker.getNavigation().createPath(head, 1);
				if (path == null || !path.canReach()) {
					unreached.add(head.subtract(origin).toShortString() + " (" + BuiltInRegistries.BLOCK.getKey(level.getBlockState(head).getBlock())
						+ (path == null ? ", no path" : ", path ends at " + path.getTarget().subtract(origin).toShortString()) + ")");
				}
			}
			helper.assertTrue(unreached.isEmpty(), "beds a villager can't walk to from the front door (doors open): " + unreached);
			helper.succeed();
		});
	}

	// ---- 3. a furniture piece missing ------------------------------------------------------------------------------------

	/**
	 * The barrels hold the whole material list except Handcrafted's crockery: the builder builds the rest and then waits
	 * for the plates alone (no vanilla stand-in, no skip). Once the plates are brought he finishes: DONE, nothing
	 * skipped, a plate in every crockery spot, all in the kitchen.
	 */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"qaDaubFurnished1123b_missing"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "qaDaubFurnished1123b_missing")
	public void qaMissingPlatesAreWaitedForAndTheHouseFinishesOnceBrought(GameTestHelper helper) {
		Item crockery = crockeryItem(BlueprintFurniture.of(DAUB));
		Run run = start(helper, crockery);
		ServerLevel level = helper.getLevel();
		UUID id = run.site().id();
		int needed = run.plan().materials().getOrDefault(crockery, 0);
		helper.assertTrue(needed > 0, "(setup) the plan costs no " + BuiltInRegistries.ITEM.getKey(crockery) + ": " + run.plan().materials());
		boolean[] waited = {false};
		boolean[] brought = {false};
		helper.onEachTick(() -> {
			BuildSite now = BuildSiteManager.get(level).get(id);
			if (brought[0] || now == null) {
				return;
			}
			if (now.missing().keySet().equals(Set.of(crockery))) {
				waited[0] = true;
				brought[0] = true;
				BlockPos barrel = new BlockPos(1, 2, 7);
				helper.setBlock(barrel, Blocks.BARREL);
				Container box = helper.getBlockEntity(barrel);
				box.setItem(0, new ItemStack(crockery, needed));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(waited[0], "never waited for the plates alone: " + run.site().stage() + " " + run.site().status()
				+ " missing " + run.site().missing() + ", skipped " + run.site().skipped());
			assertBuilt(helper, run, run.site());
			assertPieces(helper, run.placement().origin(), run.site().furniture());
		});
	}

	// ---- 4. the switch off -----------------------------------------------------------------------------------------------

	/**
	 * "The furniture switches apply as for every home": with furnishedBuilds off a new Daub House plans every furniture
	 * spot as drawn (vanilla), and with the modded library off too it costs no furniture-mod item at all.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaDaubFurnished1123b_off"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaDaubFurnished1123b_off")
	public void qaWithFurnishedBuildsOffTheDaubHouseIsPlainVanilla(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)),
			Rotation.NONE, Mirror.NONE);
		try {
			WorkplaceConfig.parse("{\"furnishedBuilds\": false}").apply();
			helper.assertTrue(FurnitureChoice.forNewBuild(DAUB).isVanilla(), "the Daub House is furnished with furnishedBuilds off");
			BuildSite site = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", DAUB, placement);
			BuildPlan plan = site.plan(level);
			helper.assertTrue(plan != null, "no blueprint " + DAUB);
			Map<BlockPos, BlockState> targets = plan.targets();
			List<String> wrong = new ArrayList<>();
			for (FurnitureSpot spot : BlueprintFurniture.of(DAUB)) {
				for (FurnitureSpot.Cell cell : spot.blocks()) {
					BlockState there = targets.get(placement.origin().offset(spot.pos()).offset(cell.at()));
					if (cell.state().isAir() ? there != null && !there.isAir() : there == null || !there.is(cell.state().getBlock())) {
						wrong.add(spot.kind() + "@" + spot.pos().offset(cell.at()).toShortString() + " plans " + (there == null ? "nothing" : BuiltInRegistries.BLOCK.getKey(there.getBlock()))
							+ ", drawn " + BuiltInRegistries.BLOCK.getKey(cell.state().getBlock()));
					}
				}
			}
			helper.assertTrue(wrong.isEmpty(), "with furnishedBuilds off the spots aren't the drawn vanilla pieces: " + wrong);
			WorkplaceConfig.parse("{\"furnishedBuilds\": false, \"moddedLibrary\": false}").apply();
			BuildPlan plain = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", DAUB, placement).plan(level);
			List<String> modded = new ArrayList<>();
			plain.materials().keySet().forEach(item -> {
				String ns = BuiltInRegistries.ITEM.getKey(item).getNamespace();
				if (Set.of("handcrafted", "beautify", "cobblefurnies", "cozyhome").contains(ns)) {
					modded.add(BuiltInRegistries.ITEM.getKey(item).toString());
				}
			});
			helper.assertTrue(modded.isEmpty(), "the plain Daub House costs furniture-mod items: " + modded);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	// ---- shared ------------------------------------------------------------------------------------------------------------

	record Run(BuildSite site, BuildPlan plan, BlueprintData.Placement placement, Villager builder) {
	}

	/** The item Handcrafted's piece for the Daub House's crockery spots is made of. */
	private static Item crockeryItem(List<FurnitureSpot> spots) {
		FurnitureChoice choice = FurnitureChoice.choose(spots, ORDER, true);
		for (FurnitureChoice.Placed placed : choice.spots().values()) {
			if (placed.kind() == FurnitureSpot.Kind.CROCKERY && !placed.mod().equals("minecraft")) {
				for (BlockState s : placed.cells().values()) {
					if (!s.isAir()) {
						return s.getBlock().asItem();
					}
				}
			}
		}
		throw new GameTestAssertException("Handcrafted furnishes none of the Daub House's crockery spots");
	}

	/**
	 * The test area cleared, a bench and a builder at x 2-3, the Daub House started at {@link #AT} with Handcrafted and
	 * Beautify, barrels holding exactly its material list less {@code without}; rules put back after the test.
	 */
	private static Run start(GameTestHelper helper, Item without) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean wasFree = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(AT), Rotation.NONE, Mirror.NONE);
		QaRebuildCreate1117e3CompatTests.after(helper, () -> {
			new WorkplaceConfig().apply();
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(wasFree, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			BuildSiteManager.get(level).forgetFinished(placement);
		});
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		new WorkplaceConfig().apply();
		Furnishings.MODS = ORDER;
		BuildSite site = Builders.start(level, builder, null, DAUB, placement);
		BuildPlan plan = site.plan(level);
		// The choice is made at the first plan and saved on the site: the order goes back to the default now
		new WorkplaceConfig().apply();
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + DAUB);
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
		helper.assertTrue(stock.size() <= 6 * 4 * 27, "(setup) the Daub House needs " + stock.size() + " stacks");
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i % 6, 2 + i / 6, 5);
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		return new Run(site, plan, placement, builder);
	}

	/** Before the build: the pieces the spec names are modded ones in the right places. */
	private static void checkChoice(GameTestHelper helper, FurnitureChoice choice) {
		List<FurnitureSpot> spots = BlueprintFurniture.of(DAUB);
		Map<FurnitureSpot.Kind, Integer> moddedKinds = new HashMap<>();
		for (FurnitureChoice.Placed p : choice.spots().values()) {
			if (!p.mod().equals("minecraft")) {
				moddedKinds.merge(p.kind(), 1, Integer::sum);
			}
		}
		for (FurnitureSpot.Kind kind : List.of(FurnitureSpot.Kind.CHAIR, FurnitureSpot.Kind.TABLE, FurnitureSpot.Kind.BED, FurnitureSpot.Kind.COUNTER,
			FurnitureSpot.Kind.LAMP, FurnitureSpot.Kind.CROCKERY)) {
			helper.assertTrue(moddedKinds.getOrDefault(kind, 0) > 0, "the spec's modded " + kind + " isn't built with Handcrafted and Beautify: " + moddedKinds);
		}
		long beds = spots.stream().filter(s -> s.kind() == FurnitureSpot.Kind.BED).count();
		helper.assertTrue(beds == 4, "the Daub House has " + beds + " bed spots, not four");
	}

	/** The site is finished: DONE, nothing skipped, every block as drawn. */
	private static void assertBuilt(GameTestHelper helper, Run run, BuildSite site) {
		ServerLevel level = helper.getLevel();
		BuildSite now = BuildSiteManager.get(level).get(site.id());
		helper.assertTrue(now == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
		helper.assertTrue(site.stage() == BuildPlan.Stage.DONE, "the site ended in " + site.stage() + ", not DONE");
		helper.assertTrue(site.skipped() == 0, site.skipped() + " steps skipped");
		BlockPos origin = run.placement().origin();
		List<BlockPos> unfinished = run.plan().unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " blocks not as drawn, e.g. " + unfinished.stream().limit(4)
			.map(p -> p.subtract(origin).toShortString() + "=" + level.getBlockState(p) + " (drawn " + run.plan().targets().get(p) + ")").toList());
	}

	/**
	 * Each modded piece standing where it was drawn; the plates only in the kitchen and one in every crockery spot; the
	 * standing lamp on the side table's piece; no modded piece over air.
	 */
	private static void assertPieces(GameTestHelper helper, BlockPos origin, FurnitureChoice choice) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		Set<BlockPos> crockeryCells = new HashSet<>();
		Map<BlockPos, FurnitureChoice.Placed> sideTables = new HashMap<>();
		Set<ResourceLocation> crockeryBlocks = new HashSet<>();
		int crockerySpots = 0;
		for (FurnitureChoice.Placed p : choice.spots().values()) {
			if (p.mod().equals("minecraft")) {
				continue;
			}
			Set<BlockPos> own = new HashSet<>();
			p.cells().forEach((at, s) -> {
				if (!s.isAir()) {
					own.add(p.pos().offset(at));
				}
			});
			FurnitureSpot.Support needs = FurnishingTable.of(p.mod()).map(t -> t.pieces().get(p.kind())).map(FurnishingTable.Piece::needs).orElse(null);
			for (Map.Entry<BlockPos, BlockState> cell : p.cells().entrySet()) {
				if (cell.getValue().isAir()) {
					continue;
				}
				BlockPos rel = p.pos().offset(cell.getKey());
				BlockState there = level.getBlockState(origin.offset(rel));
				// crockery or a lamp drawn on a two-cell counter is set down onto the one-block modded counter (B1386)
				boolean setDown = (p.kind() == FurnitureSpot.Kind.CROCKERY || p.kind() == FurnitureSpot.Kind.LAMP)
					&& level.getBlockState(origin.offset(rel.below())).is(cell.getValue().getBlock());
				if (setDown) {
					rel = rel.below();
					there = level.getBlockState(origin.offset(rel));
				}
				if (!there.is(cell.getValue().getBlock())) {
					problems.add(p.kind() + " at " + rel.toShortString() + " is " + BuiltInRegistries.BLOCK.getKey(there.getBlock()) + ", not "
						+ BuiltInRegistries.BLOCK.getKey(cell.getValue().getBlock()));
				}
				if (p.kind() == FurnitureSpot.Kind.CROCKERY) {
					crockeryCells.add(rel);
					crockeryBlocks.add(BuiltInRegistries.BLOCK.getKey(cell.getValue().getBlock()));
				}
				if (p.kind() == FurnitureSpot.Kind.SIDE_TABLE || p.kind() == FurnitureSpot.Kind.TABLE) {
					sideTables.put(rel, p);
				}
				boolean hung = p.kind() == FurnitureSpot.Kind.SHELF || p.kind() == FurnitureSpot.Kind.PICTURE || p.kind() == FurnitureSpot.Kind.CLOCK;
				if (!hung && needs != FurnitureSpot.Support.WALL && needs != FurnitureSpot.Support.CEILING && !own.contains(rel.below())
					&& level.getBlockState(origin.offset(rel.below())).isAir()) {
					problems.add(p.mod() + " " + p.kind() + " at " + rel.toShortString() + " (" + BuiltInRegistries.BLOCK.getKey(cell.getValue().getBlock())
						+ ") floats over air");
				}
			}
			if (p.kind() == FurnitureSpot.Kind.CROCKERY) {
				crockerySpots++;
			}
		}
		// The plates: every crockery block standing in the house is in the kitchen or on a table with a chair at it (the
		// owner's rule as tools/blueprints/check.py reads it), and there is one per crockery spot
		Set<BlockPos> diningTops = new HashSet<>();
		Set<BlockPos> seats = new HashSet<>();
		for (FurnitureChoice.Placed p : choice.spots().values()) {
			if (p.kind() == FurnitureSpot.Kind.CHAIR) {
				seats.add(p.pos());
			}
		}
		for (FurnitureChoice.Placed p : choice.spots().values()) {
			if (p.kind() == FurnitureSpot.Kind.TABLE && (seats.contains(p.pos().east()) || seats.contains(p.pos().west())
				|| seats.contains(p.pos().north()) || seats.contains(p.pos().south()))) {
				for (int up = 0; up <= 2; up++) {
					diningTops.add(p.pos().above(up));
				}
			}
		}
		int standing = 0;
		int onDiningTables = 0;
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(12, 13, 12))) {
			ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock());
			if (crockeryBlocks.contains(id) || (!id.getNamespace().equals("minecraft") && id.getPath().contains("crockery"))) {
				standing++;
				BlockPos rel = p.subtract(origin);
				if (diningTops.contains(rel)) {
					onDiningTables++;
					continue;
				}
				if (rel.getX() < KITCHEN_X0 || rel.getX() > KITCHEN_X1 || rel.getZ() < KITCHEN_Z0 || rel.getZ() > KITCHEN_Z1) {
					problems.add("plates (" + id + ") outside the kitchen at " + rel.toShortString());
				}
			}
		}
		if (crockerySpots == 0 || standing - onDiningTables != crockeryCells.size()) {
			problems.add(standing + " crockery pieces standing for " + crockerySpots + " modded crockery spots (" + crockeryCells.size() + " cells)");
		}
		// The standing lamp: a modded lamp that isn't hanging stands on a table piece, the block under it that table's own
		int onTables = 0;
		for (FurnitureChoice.Placed p : choice.spots().values()) {
			if (p.kind() != FurnitureSpot.Kind.LAMP || p.mod().equals("minecraft")) {
				continue;
			}
			for (Map.Entry<BlockPos, BlockState> cell : p.cells().entrySet()) {
				if (cell.getValue().isAir()) {
					continue;
				}
				BlockPos rel = p.pos().offset(cell.getKey());
				BlockState under = level.getBlockState(origin.offset(rel.below()));
				FurnitureChoice.Placed table = sideTables.get(rel.below());
				if (table == null || !under.is(table.cells().values().stream().filter(s -> !s.isAir()).findFirst().orElseThrow().getBlock())) {
					problems.add("the " + p.mod() + " lamp at " + rel.toShortString() + " stands on " + BuiltInRegistries.BLOCK.getKey(under.getBlock())
						+ ", not on a modded table piece");
				} else {
					onTables++;
				}
			}
		}
		if (onTables == 0) {
			problems.add("no modded standing lamp stands on the side table");
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
	}
}
