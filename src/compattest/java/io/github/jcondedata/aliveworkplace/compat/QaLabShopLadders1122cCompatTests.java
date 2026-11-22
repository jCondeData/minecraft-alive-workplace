package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane C, 2026-11-22, lane-c-1122-0653): three landed fixes checked from the player's side, with the pack's mods.
 *
 * <p>B1468 (Research Lab I-III with Cobblemon; the Prep Room's worktop at (8, 1, 5) was a crafting table, the Carpenter's
 * job site): jobless villagers left standing in the Prep Room of each level, and of each modded copy, for a working
 * morning take no job whose site is in the lab but the Fossil Analyzer (at most one there), and then, stood back by the
 * worktop and sneak-clicked with planks (the Carpenter's item), still none (the old crafting table made them Carpenters:
 * a crafting table is not in {@code #acquirable_job_site}, so no villager took it by himself, the item was the way in);
 * a lab built by a builder from exactly its materials has the counter at (8, 1, 5), no job site but the analyzer, and
 * keeps jobless villagers jobless the same way. B1450 (Supply Shop, Handcrafted): no plates on any shop's modded copy, nor on or over the Supply Shop's display
 * tables as a server with Handcrafted furnishes and builds it. B1453 (the five village Ball Workshops): every loft ladder
 * stays through block updates all round it.
 */
public class QaLabShopLadders1122cCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final ResourceLocation FOSSIL_ANALYZER = ModVillagers.FOSSIL_ANALYZER_BLOCK;
	/** The Prep Room's worktop cell (game cells: y 0 on the ground, a basement below it). */
	private static final BlockPos PREP = new BlockPos(8, 1, 5);
	/** Where a placed lab's origin goes in the test area (room for III's 4-deep cellar under it). */
	private static final BlockPos LAB_ORIGIN = new BlockPos(3, 7, 3);
	private static final int IDLE_TICKS = 1500;

	// ---- B1468: jobless villagers in the Prep Room of each placed lab ---------------------------------------------------

	/** B1468: Research Lab I placed; three jobless villagers in its Prep Room for a working morning, reloaded half way. */
	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_lsl1122c_lab1"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_lsl1122c_lab1")
	public void b1468JoblessVillagersInResearchLabITakeNoJobButTheAnalyzer(GameTestHelper helper) {
		idleInLab(helper, "research_lab", true);
	}

	/** B1468: Research Lab II placed, three jobless villagers in its Prep Room. */
	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_lsl1122c_lab2"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_lsl1122c_lab2")
	public void b1468JoblessVillagersInResearchLabIITakeNoJobButTheAnalyzer(GameTestHelper helper) {
		idleInLab(helper, "research_lab_2", false);
	}

	/** B1468: Research Lab III placed (its cellar under it), three jobless villagers in its Prep Room. */
	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_lsl1122c_lab3"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_lsl1122c_lab3")
	public void b1468JoblessVillagersInResearchLabIIITakeNoJobButTheAnalyzer(GameTestHelper helper) {
		idleInLab(helper, "research_lab_3", false);
	}

	/** B1468: the modded copy of Research Lab I (what a server with the pack builds), reloaded half way. */
	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_lsl1122c_mlab1"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_lsl1122c_mlab1")
	public void b1468JoblessVillagersInModdedResearchLabITakeNoJobButTheAnalyzer(GameTestHelper helper) {
		idleInLab(helper, "modded/research_lab", true);
	}

	/** B1468: the modded copy of Research Lab II. */
	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_lsl1122c_mlab2"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_lsl1122c_mlab2")
	public void b1468JoblessVillagersInModdedResearchLabIITakeNoJobButTheAnalyzer(GameTestHelper helper) {
		idleInLab(helper, "modded/research_lab_2", false);
	}

	/** B1468: the modded copy of Research Lab III. */
	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_lsl1122c_mlab3"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_lsl1122c_mlab3")
	public void b1468JoblessVillagersInModdedResearchLabIIITakeNoJobButTheAnalyzer(GameTestHelper helper) {
		idleInLab(helper, "modded/research_lab_3", false);
	}

	/**
	 * {@code name} placed on dirt at {@link #LAB_ORIGIN}; no job site in its box but the analyzer in the live POI records;
	 * three jobless villagers on the open cells nearest the Prep Room's worktop, the morning fixed. For
	 * {@link #IDLE_TICKS}: none of them claims or works at a job site in the lab but the analyzer, at most one at the
	 * analyzer, and everyone else still jobless at the end (a job whose site is outside the lab, a leftover of another
	 * batch, is no fault of the lab and is let be); then each jobless one, stood back on its cell, sneak-clicked by a player
	 * with planks, does not become a Carpenter or take any block of the lab. With {@code reload}, each villager is saved and
	 * loaded back half way.
	 */
	private static void idleInLab(GameTestHelper helper, String name, boolean reload) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearAirAbove(helper, 2, 4);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(1000);
		// a modded copy as the game builds it: under the vanilla build's id, with its basement (loaded by its own file name
		// it has none, and III's would stand 4 blocks too high)
		Blueprint blueprint = (name.startsWith("modded/") ? ModdedLibrary.copy(AliveWorkplace.id(name.substring("modded/".length())))
			: BlueprintLibrary.get(level, AliveWorkplace.id(name))).orElseThrow(() -> new GameTestAssertException("(setup) no drawing " + name));
		BlockPos origin = helper.absolutePos(LAB_ORIGIN);
		var size = blueprint.size();
		int low = 0;
		for (Blueprint.Entry e : blueprint.blocks()) {
			low = Math.min(low, e.pos().getY());
		}
		// the ground: dirt from the area's floor up to the lab's ground, a margin round it
		BlockPos floor = helper.absolutePos(new BlockPos(0, 2, 0));
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(origin.getX() - 2, floor.getY(), origin.getZ() - 2),
			new BlockPos(origin.getX() + size.getX() + 1, origin.getY() - 1, origin.getZ() + size.getZ() + 1))) {
			level.setBlock(p, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		helper.assertTrue(origin.getY() + low > floor.getY() - 1, "(setup) " + name + "'s cellar goes below the test floor");
		BlockPos analyzer = null;
		for (Blueprint.Entry e : blueprint.blocks()) {
			level.setBlock(origin.offset(e.pos()), e.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			if (id(e.state()).equals(FOSSIL_ANALYZER)) {
				helper.assertTrue(analyzer == null, name + " draws a second Fossil Analyzer at " + e.pos().toShortString());
				analyzer = origin.offset(e.pos());
			}
		}
		helper.assertTrue(analyzer != null, name + " has no Fossil Analyzer (is Cobblemon in this run?)");
		BoundingBox box = new BoundingBox(origin.getX(), origin.getY() + low, origin.getZ(), origin.getX() + size.getX() - 1,
			origin.getY() + size.getY() - 1, origin.getZ() + size.getZ() - 1);
		List<String> stray = jobSitesIn(level, box, origin, analyzer);
		helper.assertTrue(stray.isEmpty(), name + " placed: job sites in the lab but the analyzer: " + stray);
		helper.assertTrue(!level.getBlockState(origin.offset(PREP)).is(Blocks.CRAFTING_TABLE), name + " still has a crafting table at "
			+ PREP.toShortString());
		List<BlockPos> stands = standsNear(level, origin.offset(PREP), 3);
		helper.assertTrue(stands.size() >= 2, "(setup) " + name + ": only " + stands.size() + " open cells in the Prep Room by " + PREP.toShortString());
		List<Villager> villagers = new ArrayList<>();
		for (BlockPos stand : stands) {
			villagers.add(jobless(level, stand));
		}
		helper.onEachTick(watcher(helper, name, villagers, stands, box, origin, analyzer, reload, IDLE_TICKS));
	}

	/**
	 * A ticker, run once a tick from the test's own tick callback (registering callbacks from inside one crashes the test
	 * runner): no villager of {@code villagers} remembers a job site or a potential one inside {@code box} but
	 * {@code analyzer}; with {@code reload} they are saved and loaded back at half time; {@code ticks} after the first run
	 * at most one works at the analyzer and the rest are jobless (or work outside the box), and the test succeeds.
	 */
	private static Runnable watcher(GameTestHelper helper, String name, List<Villager> villagers, List<BlockPos> stands, BoundingBox box, BlockPos origin,
		BlockPos analyzer, boolean reload, int ticks) {
		ServerLevel level = helper.getLevel();
		long[] start = {-1};
		boolean[] reloaded = {!reload};
		boolean[] done = {false};
		return () -> {
			if (done[0]) {
				return;
			}
			if (start[0] < 0) {
				start[0] = helper.getTick();
			}
			long now = helper.getTick() - start[0];
			if (level.getDayTime() % 24000 > 9000) {
				helper.setDayTime(1000); // keep it a working morning (no bed here: they would stand about at night)
			}
			if (!reloaded[0] && now >= ticks / 2) {
				reloaded[0] = true;
				for (int i = 0; i < villagers.size(); i++) {
					CompoundTag tag = new CompoundTag();
					helper.assertTrue(villagers.get(i).save(tag), "(setup) a villager wasn't saved");
					villagers.get(i).discard();
					Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
					helper.assertTrue(back instanceof Villager, "(setup) the villager didn't load back: " + back);
					level.addFreshEntity(back);
					villagers.set(i, (Villager) back);
				}
			}
			for (Villager v : villagers) {
				for (MemoryModuleType<GlobalPos> memory : List.of(MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE)) {
					Optional<BlockPos> site = v.getBrain().getMemory(memory).map(GlobalPos::pos);
					if (site.isPresent() && box.isInside(site.get()) && !site.get().equals(analyzer)) {
						throw new GameTestAssertException(name + ": a villager (" + profession(v) + ") took " + memory + " at "
							+ site.get().subtract(origin).toShortString() + " (lab cells), a "
							+ id(level.getBlockState(site.get())) + ", after " + now + " ticks");
					}
				}
			}
			if (now < ticks) {
				return;
			}
			done[0] = true;
			int atAnalyzer = 0;
			List<String> jobs = new ArrayList<>();
			for (Villager v : villagers) {
				helper.assertTrue(v.isAlive(), "(setup) " + name + ": a villager died (" + v.getRemovalReason() + ")");
				Optional<BlockPos> site = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
				if (site.isPresent() && site.get().equals(analyzer)) {
					atAnalyzer++;
				} else if (v.getVillagerData().getProfession() != VillagerProfession.NONE && (site.isEmpty() || box.isInside(site.get()))) {
					jobs.add(profession(v) + " at " + site.map(BlockPos::toShortString).orElse("no job site"));
				}
			}
			helper.assertTrue(atAnalyzer <= 1, name + ": " + atAnalyzer + " villagers work at the one Fossil Analyzer");
			helper.assertTrue(jobs.isEmpty(), name + ": villagers left in the Prep Room took jobs: " + jobs);
			// a player then sneak-clicks each jobless one with planks, the Carpenter's item: no block in the lab gives that job
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			try {
				player.setGameMode(GameType.SURVIVAL);
				for (int i = 0; i < villagers.size(); i++) {
					Villager v = villagers.get(i);
					if (v.getVillagerData().getProfession() != VillagerProfession.NONE) {
						continue;
					}
					v.getNavigation().stop();
					v.teleportTo(stands.get(i).getX() + 0.5, stands.get(i).getY(), stands.get(i).getZ() + 0.5); // stood back by the worktop, as a player does
					player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 4));
					player.moveTo(v.getX() + 1.0, v.getY(), v.getZ(), 90f, 0f);
					player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(v, true, InteractionHand.MAIN_HAND,
						new Vec3(0, v.getBbHeight() / 2, 0)));
					player.setShiftKeyDown(false);
					Optional<BlockPos> site = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
					helper.assertTrue(v.getVillagerData().getProfession() != ModVillagers.CARPENTER
						&& !(site.isPresent() && box.isInside(site.get()) && !site.get().equals(analyzer)),
						name + ": a jobless villager in the Prep Room, sneak-clicked with planks, became " + profession(v) + " at "
						+ site.map(p -> p.subtract(origin).toShortString() + " (lab cells), a "
						+ id(level.getBlockState(p))).orElse("no job site"));
				}
			} finally {
				level.getServer().getPlayerList().remove(player);
			}
			helper.succeed();
		};
	}

	// ---- B1468: a lab built by a builder from exactly its materials -----------------------------------------------------

	/**
	 * B1468: Research Lab I from bare ground, one builder, barrels holding exactly its materials (the modded copy and the
	 * pack's furniture as this server picks them): done, every block as planned; at (8, 1, 5) the Prep Room's counter (dark
	 * oak with a smooth stone top, or the furniture piece of the counter spot there), no crafting table; no job site in the
	 * built lab but the analyzer; then two jobless villagers in the Prep Room take no job in it but the analyzer.
	 */
	//$ gametest_ticks_batch HUGE_AREA '24000' '"qa_lsl1122c_lab_built"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 24000, batch = "qa_lsl1122c_lab_built")
	public void b1468AResearchLabBuiltFromItsMaterialsHasTheCounterAndKeepsVillagersJobless(GameTestHelper helper) {
		Stage s = stage(helper, new BlockPos(9, 2, 7), false);
		ServerLevel level = s.level();
		BuildSite site = Builders.start(level, s.builder(), null, StarterBlueprints.RESEARCH_LAB.id(), s.placement());
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "(setup) no plan for research_lab");
		stock(helper, plan);
		BlockPos origin = s.placement().origin();
		var size = StarterBlueprints.RESEARCH_LAB.size();
		BoundingBox box = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + size.getX() - 1, origin.getY() + size.getY() - 1,
			origin.getZ() + size.getZ() - 1);
		FurnitureChoice choice = site.furniture();
		Runnable[] watching = {null};
		helper.onEachTick(() -> {
			if (watching[0] != null) {
				watching[0].run();
				return;
			}
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				return;
			}
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), "research_lab: " + unfinished.size() + " blocks not as drawn, e.g. " + unfinished.stream().limit(4)
				.map(p -> p.subtract(origin).toShortString() + "=" + level.getBlockState(p)).toList());
			BlockPos prep = origin.offset(PREP);
			BlockState worktop = level.getBlockState(prep);
			BlockState top = level.getBlockState(prep.above());
			helper.assertTrue(!worktop.is(Blocks.CRAFTING_TABLE) && net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(worktop).isEmpty(),
				"research_lab built: at " + PREP.toShortString() + " stands " + id(worktop) + ", a job site");
			FurnitureChoice.Placed piece = choice.spots().values().stream().filter(p -> p.pos().equals(PREP)).findFirst().orElse(null);
			if (piece == null) {
				helper.assertTrue(worktop.is(Blocks.DARK_OAK_PLANKS) && top.is(Blocks.SMOOTH_STONE_SLAB), "research_lab built: the Prep Room's counter at "
					+ PREP.toShortString() + " is " + id(worktop) + " under " + id(top) + ", not dark oak planks under a smooth stone slab");
			} else {
				helper.assertTrue(piece.kind() == FurnitureSpot.Kind.COUNTER, "research_lab: the spot at " + PREP.toShortString() + " is a " + piece.kind()
					+ ", not the counter");
				for (Map.Entry<BlockPos, BlockState> cell : piece.cells().entrySet()) {
					BlockState there = level.getBlockState(prep.offset(cell.getKey()));
					helper.assertTrue(there.is(cell.getValue().getBlock()), "research_lab built: the counter piece's cell " + PREP.offset(cell.getKey()).toShortString()
						+ " is " + id(there) + ", not " + id(cell.getValue()));
				}
			}
			BlockPos analyzer = null;
			for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				if (id(level.getBlockState(p)).equals(FOSSIL_ANALYZER)) {
					helper.assertTrue(analyzer == null, "research_lab built: a second analyzer at " + p.subtract(origin).toShortString());
					analyzer = p.immutable();
				}
			}
			helper.assertTrue(analyzer != null, "research_lab built: no Fossil Analyzer");
			List<String> stray = jobSitesIn(level, box, origin, analyzer);
			helper.assertTrue(stray.isEmpty(), "research_lab built: job sites but the analyzer: " + stray);
			List<BlockPos> stands = standsNear(level, prep, 2);
			helper.assertTrue(stands.size() >= 2, "(setup) only " + stands.size() + " open cells in the built Prep Room");
			List<Villager> villagers = new ArrayList<>();
			for (BlockPos stand : stands) {
				villagers.add(jobless(level, stand));
			}
			watching[0] = watcher(helper, "research_lab built", villagers, stands, box, origin, analyzer, false, 1000);
		});
	}

	// ---- B1450: no plates in the Supply Shop (or any shop) with Handcrafted ---------------------------------------------

	private static final List<String> SHOPS = List.of("supply_shop", "supply_shop_2", "supply_shop_3", "supply_shop_4", "market_stall", "market_stall_2",
		"market_stall_3", "flower_shop", "flower_shop_2");
	private static final List<String> SUPPLY_SHOPS = List.of("supply_shop", "supply_shop_2", "supply_shop_3", "supply_shop_4");

	/** A plate, cup, mug, bowl, teapot or set of crockery of another mod (Handcrafted's): never a cupboard or a pressure plate. */
	static boolean crockery(BlockState state) {
		ResourceLocation id = id(state);
		if (id.getNamespace().equals("minecraft")) {
			return false;
		}
		String path = id.getPath();
		if (path.contains("cupboard") || path.contains("pressure_plate")) {
			return false;
		}
		return path.contains("crockery") || path.contains("plate") || path.contains("_cup") || path.startsWith("cup") || path.contains("mug")
			|| path.contains("teapot") || path.contains("bowl");
	}

	/**
	 * B1450: no shop's modded copy (the Supply Shop I-IV, the Market Stall I-III, the Flower Shop I-II: shops with no
	 * kitchen and no table with a seat) has a plate or any crockery anywhere, as the library loads it with Handcrafted.
	 * The Trade Hall is left out: its tables have chairs, so rule 3 lets its plates be.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void b1450NoShopsModdedCopyHasCrockery(GameTestHelper helper) {
		List<String> found = new ArrayList<>();
		for (String name : SHOPS) {
			Blueprint copy = BlueprintLibrary.get(helper.getLevel(), AliveWorkplace.id("modded/" + name))
				.orElseThrow(() -> new GameTestAssertException("(setup) no modded/" + name));
			helper.assertTrue(copy.blocks().stream().anyMatch(e -> !id(e.state()).getNamespace().equals("minecraft")),
				"(setup) modded/" + name + " has no modded block: are the pack's mods in this run?");
			for (Blueprint.Entry e : copy.blocks()) {
				if (crockery(e.state())) {
					found.add("modded/" + name + " " + e.pos().toShortString() + " " + id(e.state()));
				}
			}
		}
		helper.assertTrue(found.isEmpty(), "crockery in a shop: " + found);
		helper.succeed();
	}

	/**
	 * B1450 (the showcase's furnished_workplaces check): the Supply Shop I-IV as a server with Handcrafted and Beautify
	 * furnishes a new build (the modded copy, the pieces the order picks): on and over each display table (the table
	 * spots, three cells up from the spot) no plate or crockery, and every cell of every piece is what the build sets there
	 * (a table's carpet cell left air stays air: no crockery hanging over a one-block Handcrafted table).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void b1450TheFurnishedSupplyShopsDisplayTablesHoldNoPlates(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		try {
			new WorkplaceConfig().apply();
			Furnishings.MODS = List.of("handcrafted", "beautify");
			Furnishings.ENABLED = true;
			for (String name : SUPPLY_SHOPS) {
				ResourceLocation id = AliveWorkplace.id(name);
				helper.assertTrue(ModdedLibrary.use(id), "(setup) the modded copy of " + name + " isn't used in this run");
				Blueprint base = BlueprintLibrary.get(helper.getLevel(), id).orElseThrow(() -> new GameTestAssertException("(setup) no " + name));
				FurnitureChoice choice = FurnitureChoice.forNewBuild(id);
				helper.assertTrue(choice.modded(), "(setup) " + name + " isn't built from the modded library");
				Map<BlockPos, BlockState> built = new HashMap<>();
				for (Blueprint.Entry e : choice.apply(base, id).blocks()) {
					built.put(e.pos(), e.state());
				}
				int tables = 0;
				for (FurnitureSpot spot : BlueprintFurniture.of(id)) {
					if (spot.kind() != FurnitureSpot.Kind.TABLE) {
						continue;
					}
					tables++;
					for (int up = 0; up <= 2; up++) {
						BlockState s = built.getOrDefault(spot.pos().above(up), Blocks.AIR.defaultBlockState());
						if (crockery(s)) {
							wrong.add(name + " table " + spot.pos().toShortString() + ": " + id(s) + " at " + spot.pos().above(up).toShortString());
						}
					}
				}
				helper.assertTrue(tables >= 2, "(setup) " + name + " has " + tables + " table spots, the brief's two display tables expected");
				for (FurnitureChoice.Placed placed : choice.spots().values()) {
					for (Map.Entry<BlockPos, BlockState> cell : placed.cells().entrySet()) {
						BlockPos at = placed.pos().offset(cell.getKey());
						BlockState there = built.getOrDefault(at, Blocks.AIR.defaultBlockState());
						if (!there.is(cell.getValue().getBlock())) {
							wrong.add(name + " " + placed.kind() + " at " + at.toShortString() + " is " + id(there) + ", not " + id(cell.getValue()));
						}
					}
				}
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * B1450: a builder on this server builds the Supply Shop (free materials, Handcrafted and Beautify): done, every block
	 * as planned, and on and over its two display tables no plate or crockery stands in the world.
	 */
	//$ gametest_ticks_batch HUGE_AREA '20000' '"qa_lsl1122c_shop_built"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 20000, batch = "qa_lsl1122c_shop_built")
	public void b1450ASupplyShopBuiltWithHandcraftedHasNoPlatesOnItsTables(GameTestHelper helper) {
		new WorkplaceConfig().apply();
		Furnishings.MODS = List.of("handcrafted", "beautify");
		Furnishings.ENABLED = true;
		Stage s;
		BuildSite site;
		BuildPlan plan;
		try {
			s = stage(helper, new BlockPos(9, 2, 7), true);
			site = Builders.start(s.level(), s.builder(), null, StarterBlueprints.SUPPLY_SHOP.id(), s.placement());
			plan = site.plan(s.level());
		} finally {
			new WorkplaceConfig().apply(); // the choice is made at the first plan and saved on the site
		}
		ServerLevel level = s.level();
		helper.assertTrue(plan != null, "(setup) no plan for supply_shop");
		helper.assertTrue(site.furniture().modded(), "(setup) the Supply Shop isn't built from the modded library");
		BlockPos origin = s.placement().origin();
		helper.succeedWhen(() -> {
			BuildSite now = BuildSiteManager.get(level).get(site.id());
			helper.assertTrue(now == null, "supply_shop still building: " + (now == null ? "" : now.stage() + " " + now.status()));
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), "supply_shop: " + unfinished.size() + " blocks not as drawn, e.g. " + unfinished.stream().limit(4)
				.map(p -> p.subtract(origin).toShortString() + "=" + level.getBlockState(p)).toList());
			List<String> wrong = new ArrayList<>();
			int tables = 0;
			for (FurnitureSpot spot : BlueprintFurniture.of(StarterBlueprints.SUPPLY_SHOP.id())) {
				if (spot.kind() != FurnitureSpot.Kind.TABLE) {
					continue;
				}
				tables++;
				for (int up = 0; up <= 2; up++) {
					BlockState there = level.getBlockState(origin.offset(spot.pos()).above(up));
					if (crockery(there)) {
						wrong.add("table " + spot.pos().toShortString() + ": " + id(there) + " at " + spot.pos().above(up).toShortString());
					}
				}
			}
			helper.assertTrue(tables >= 2, "(setup) supply_shop has " + tables + " table spots");
			helper.assertTrue(wrong.isEmpty(), "plates on the Supply Shop's display tables: " + wrong);
		});
	}

	// ---- B1453: village Ball Workshop ladders through block updates ------------------------------------------------------

	/**
	 * B1453: each of the five village Ball Workshops placed as a village places it; then round every loft ladder the world
	 * changes as a player's building does: the block it hangs on sends its neighbours a shape update (as when it is placed
	 * or changes), a block is set and cleared in every open cell beside the ladder and the cells above and below it
	 * (neighbour and shape updates on), and the ladder's neighbours get a neighbour update. Every ladder the file draws is
	 * still a ladder after all of it, hanging on a block that holds it.
	 */
	//$ gametest_batch HUGE_AREA '"qa_lsl1122c_ladders"'
	@GameTest(template = HUGE_AREA, batch = "qa_lsl1122c_ladders")
	public void b1453EveryVillageBallWorkshopLadderStaysThroughBlockUpdatesAroundIt(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(5, 2, 5));
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (String style : List.of("desert", "plains", "savanna", "snowy", "taiga")) {
			ResourceLocation file = AliveWorkplace.id("village/" + style + "_ball_workshop");
			StructureTemplate template = level.getStructureManager().get(file).orElseThrow(() -> new GameTestAssertException("(setup) no " + file));
			var size = template.getSize();
			for (BlockPos p : BlockPos.betweenClosed(origin.offset(-2, -1, -3), origin.offset(size.getX() + 1, size.getY() + 1, size.getZ() + 1))) {
				level.setBlock(p, p.getY() < origin.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2 | 16);
			}
			template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setIgnoreEntities(true), RandomSource.create(1453L), 2 | 16);
			for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size.getX(), size.getY(), size.getZ()))) {
				if (level.getBlockState(p).is(Blocks.JIGSAW)) {
					level.setBlock(p, p.getY() == origin.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2 | 16);
				}
			}
			List<StructureTemplate.StructureBlockInfo> drawn = template.filterBlocks(origin, new StructurePlaceSettings(), Blocks.LADDER);
			if (drawn.isEmpty()) {
				problems.add(style + ": no ladder drawn");
			}
			for (StructureTemplate.StructureBlockInfo info : drawn) {
				BlockPos p = info.pos().immutable();
				Direction facing = info.state().getValue(LadderBlock.FACING);
				BlockPos support = p.relative(facing.getOpposite());
				if (!level.getBlockState(p).is(Blocks.LADDER)) {
					problems.add(style + ": the ladder " + p.subtract(origin).toShortString() + " wasn't placed (on " + id(level.getBlockState(support)) + ")");
					checked++;
					continue;
				}
				// the support re-sends its shape to its neighbours, as when it is placed or changes
				level.getBlockState(support).updateNeighbourShapes(level, support, Block.UPDATE_ALL);
				// a block set and cleared in every open cell around the ladder (the support aside)
				for (Direction d : Direction.values()) {
					BlockPos side = p.relative(d);
					if (side.equals(support) || !level.getBlockState(side).isAir()) {
						continue;
					}
					level.setBlock(side, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
					level.setBlock(side, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
				level.updateNeighborsAt(p, Blocks.STONE);
				level.updateNeighborsAt(support, Blocks.STONE);
				checked++;
			}
			// every rung checked after all the updates (one rung's updates reach the next one too)
			for (StructureTemplate.StructureBlockInfo info : drawn) {
				BlockPos p = info.pos();
				BlockState now = level.getBlockState(p);
				BlockPos support = p.relative(info.state().getValue(LadderBlock.FACING).getOpposite());
				if (!now.is(Blocks.LADDER)) {
					problems.add(style + ": the ladder " + p.subtract(origin).toShortString() + " is gone after block updates (it hung on "
						+ id(level.getBlockState(support)) + ")");
				} else if (!now.canSurvive(level, p)) {
					problems.add(style + ": the ladder " + p.subtract(origin).toShortString() + " can't hold on " + id(level.getBlockState(support)));
				}
			}
		}
		helper.assertTrue(checked >= 5, "(setup) only " + checked + " ladders checked");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// ---- shared --------------------------------------------------------------------------------------------------------

	record Stage(ServerLevel level, BlueprintData.Placement placement, Villager builder) {
	}

	/** Leftovers cleared, the morning fixed, a bench and a builder at x 2-3, rules set for the test and put back after it. */
	private static Stage stage(GameTestHelper helper, BlockPos at, boolean free) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean wasFree = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(at), Rotation.NONE, Mirror.NONE);
		QaRebuildCreate1117e3CompatTests.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(wasFree, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			BuildSiteManager.get(level).forgetFinished(placement);
		});
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(new BlockPos(1, 2, 2), Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		return new Stage(level, placement, builder);
	}

	/** Barrels at x 1-6 of row z 5 holding exactly what {@code plan} costs. */
	private static void stock(GameTestHelper helper, BuildPlan plan) {
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(stock.size() <= 108, "(setup) the plan needs " + stock.size() + " stacks, more than four barrels");
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i % 6, 2 + i / 6, 5);
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
	}

	/** The job sites (any profession's, by its job-site test) the live POI records hold inside {@code box}, but {@code analyzer}. */
	private static List<String> jobSitesIn(ServerLevel level, BoundingBox box, BlockPos origin, BlockPos analyzer) {
		List<String> out = new ArrayList<>();
		BlockPos centre = box.getCenter();
		int radius = Math.max(box.getXSpan(), Math.max(box.getYSpan(), box.getZSpan()));
		level.getPoiManager().ensureLoadedAndValid(level, centre, radius);
		level.getPoiManager().getInRange(h -> true, centre, radius, PoiManager.Occupancy.ANY).forEach((PoiRecord r) -> {
			BlockPos p = r.getPos();
			if (!box.isInside(p) || p.equals(analyzer)) {
				return;
			}
			Holder<PoiType> type = r.getPoiType();
			List<ResourceLocation> jobs = ResearchLabJobSiteB1468CompatTests.jobsAt(type);
			if (!jobs.isEmpty()) {
				out.add(p.subtract(origin).toShortString() + " " + id(level.getBlockState(p)) + " (job site of " + jobs + ")");
			}
		});
		return out;
	}

	/** Up to {@code most} open cells a villager stands in (feet and head clear, a sturdy floor) on the level of {@code job} or one off, nearest first. */
	private static List<BlockPos> standsNear(ServerLevel level, BlockPos job, int most) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(job.offset(-2, -1, -2), job.offset(2, 1, 2))) {
			if (p.equals(job)) {
				continue;
			}
			BlockState feet = level.getBlockState(p);
			BlockState head = level.getBlockState(p.above());
			BlockState below = level.getBlockState(p.below());
			if (feet.getCollisionShape(level, p).isEmpty() && head.getCollisionShape(level, p.above()).isEmpty() && feet.getFluidState().isEmpty()
				&& below.isFaceSturdy(level, p.below(), Direction.UP)) {
				out.add(p.immutable());
			}
		}
		out.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(job)));
		return out.subList(0, Math.min(most, out.size()));
	}

	/** A grown jobless villager standing at {@code at}. */
	private static Villager jobless(ServerLevel level, BlockPos at) {
		Villager villager = EntityType.VILLAGER.create(level);
		villager.moveTo(Vec3.atBottomCenterOf(at));
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
		villager.setAge(0);
		level.addFreshEntity(villager);
		return villager;
	}

	private static String profession(Villager v) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).toString();
	}

	private static ResourceLocation id(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock());
	}
}
