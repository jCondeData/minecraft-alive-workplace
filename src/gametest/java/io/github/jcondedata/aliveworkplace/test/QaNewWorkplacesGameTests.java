package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.hall.Decorations;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-c-1025-2153) for ROADMAP 27.13 and 27.14, written from the spec, not the code: the fifteen new workplaces
 * hold what the spec promises (an anvil, campfire and quench water at the Smithy, a second blast furnace and an ore store
 * at Smithy II, the second stonecutter and fletching table, a tall Map Room, a Farmstead with one bed and a 9 x 5 field
 * round a water channel, a barrel at the Fisher's Hut and a smoker at its II, a loom and fenced pen at the Weaver's
 * Cottage and a dye garden at its II, an eight-sided open Bandstand with a jukebox); their names read as the spec spells
 * them; each {@code workplace_*} rule holds for its own trades only, and not for a worker who still has his block; a
 * Bandstand alone counts for beauty; a Fisher's Hut II, like the first, wants a shore with shallow water under its jetty.
 */
public class QaNewWorkplacesGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** The eight new rules. */
	private static final List<String> RULES = List.of("workplace_smithy", "workplace_masons_yard", "workplace_fletchers_lodge",
		"workplace_map_room", "workplace_farmstead", "workplace_fishers_hut", "workplace_weavers_cottage", "workplace_bandstand");

	/** A trade, the block it works at, and the one new rule (if any) a worker of it without a block asks for. */
	record Trade(String job, Block block, Optional<String> rule) {
	}

	private static final List<Trade> TRADES = List.of(
		new Trade("minecraft:armorer", Blocks.BLAST_FURNACE, Optional.of("workplace_smithy")),
		new Trade("aliveworkplace:miner", Blocks.BLAST_FURNACE, Optional.of("workplace_smithy")),
		new Trade("minecraft:mason", Blocks.STONECUTTER, Optional.of("workplace_masons_yard")),
		new Trade("minecraft:fletcher", Blocks.FLETCHING_TABLE, Optional.of("workplace_fletchers_lodge")),
		new Trade("aliveworkplace:lumberjack", Blocks.FLETCHING_TABLE, Optional.of("workplace_fletchers_lodge")),
		new Trade("minecraft:cartographer", Blocks.CARTOGRAPHY_TABLE, Optional.of("workplace_map_room")),
		new Trade("minecraft:farmer", Blocks.COMPOSTER, Optional.of("workplace_farmstead")),
		new Trade("minecraft:fisherman", Blocks.BARREL, Optional.of("workplace_fishers_hut")),
		new Trade("minecraft:shepherd", Blocks.LOOM, Optional.of("workplace_weavers_cottage")),
		new Trade("aliveworkplace:bard", Blocks.JUKEBOX, Optional.of("workplace_bandstand")),
		// Trades the eight aren't for: none of them asks for one.
		new Trade("minecraft:toolsmith", Blocks.SMITHING_TABLE, Optional.empty()),
		new Trade("minecraft:weaponsmith", Blocks.GRINDSTONE, Optional.empty()),
		new Trade("minecraft:librarian", Blocks.LECTERN, Optional.empty()),
		new Trade("minecraft:butcher", Blocks.SMOKER, Optional.empty()));

	private static StewardRules.Rule ruleOf(String id) {
		return StewardRules.all().stream().filter(r -> r.id().equals(AliveWorkplace.id(id))).findFirst()
			.orElseThrow(() -> new GameTestAssertException("rule " + id + " didn't load"));
	}

	private static Set<String> held(ServerLevel level, BlockPos hall) {
		long day = StewardWishes.day(level);
		Set<String> out = new TreeSet<>();
		for (String r : RULES) {
			if (StewardWishes.judge(ruleOf(r), StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, day).status() == StewardWishes.Status.HELD) {
				out.add(r);
			}
		}
		return out;
	}

	/**
	 * Per trade: a worker at his own block makes none of the eight rules hold; the same worker with his block gone makes
	 * exactly his trade's rule hold (and, for a trade they aren't for, still none). Both sides of the rule.
	 */
	@GameTestGenerator
	public Collection<TestFunction> rulesForTheirTradesOnly() {
		List<TestFunction> out = new ArrayList<>();
		for (Trade t : TRADES) {
			String name = "qaNewWorkplaceRules_" + t.job().substring(t.job().indexOf(':') + 1);
			out.add(new TestFunction(name, name, AREA, 60, 0, true, helper -> rules(helper, t)));
		}
		return out;
	}

	private static void rules(GameTestHelper helper, Trade t) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> level.removeBlock(hall, false));
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.HAMLET);
		helper.assertTrue(held(level, hall).isEmpty(), "with nobody: " + held(level, hall));
		BlockPos block = new BlockPos(6, 2, 20);
		helper.setBlock(block, t.block());
		Villager worker = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 20));
		worker.setNoAi(true);
		VillagerProfession job = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(t.job()));
		helper.assertTrue(job != null && BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).toString().equals(t.job()), "no profession " + t.job());
		worker.setVillagerData(worker.getVillagerData().setProfession(job));
		worker.refreshBrain(level);
		worker.setVillagerXp(1);
		worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(block)));
		Set<String> atBlock = held(level, hall);
		helper.assertTrue(atBlock.isEmpty(), "a " + t.job() + " at his " + t.block() + " asks for " + atBlock);
		worker.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
		Set<String> lost = held(level, hall);
		Set<String> want = t.rule().map(Set::of).orElse(Set.of());
		helper.assertTrue(lost.equals(want), "a " + t.job() + " without his block asks for " + lost + ", the spec says " + want);
		helper.succeed();
	}

	// --- what each build holds ----------------------------------------------------------------------------

	private static Blueprint blueprint(GameTestHelper helper, StarterBlueprints.Entry e) {
		return BlueprintLibrary.get(helper.getLevel(), e.id()).orElseThrow(() -> new GameTestAssertException("missing " + e.id()));
	}

	private static long count(Blueprint b, Predicate<BlockState> p) {
		return b.blocks().stream().filter(x -> p.test(x.state())).count();
	}

	private static long count(Blueprint b, Block block) {
		return count(b, s -> s.is(block));
	}

	private static long beds(Blueprint b) {
		return count(b, s -> s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD);
	}

	private static void has(GameTestHelper helper, StarterBlueprints.Entry e, String what, long n, long min) {
		helper.assertTrue(n >= min, e.id().getPath() + " has " + n + " " + what + ", the spec wants at least " + min);
	}

	private static void exactly(GameTestHelper helper, StarterBlueprints.Entry e, String what, long n, long want) {
		helper.assertTrue(n == want, e.id().getPath() + " has " + n + " " + what + ", the spec says " + want);
	}

	/** 27.13: the Smithy's forge, open front pieces and chimney; Smithy II's ore store and second blast furnace. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaSmithyHoldsItsForge(GameTestHelper helper) {
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.SMITHY, StarterBlueprints.SMITHY_2)) {
			Blueprint b = blueprint(helper, e);
			has(helper, e, "anvils", count(b, s -> s.is(BlockTags.ANVIL)), 1);
			has(helper, e, "campfires (chimney smoke)", count(b, s -> s.is(BlockTags.CAMPFIRES)), 1);
			has(helper, e, "water (quench trough)", count(b, Blocks.WATER) + count(b, Blocks.WATER_CAULDRON), 1);
			exactly(helper, e, "smithing tables", count(b, Blocks.SMITHING_TABLE), 1);
			exactly(helper, e, "grindstones", count(b, Blocks.GRINDSTONE), 1);
			exactly(helper, e, "blast furnaces", count(b, Blocks.BLAST_FURNACE), e == StarterBlueprints.SMITHY ? 1 : 2);
		}
		// Smithy II's coal and ore store: more coal and ore than the Smithy, in blocks or chests.
		Predicate<BlockState> ore = s -> s.is(Blocks.COAL_BLOCK) || s.is(Blocks.RAW_IRON_BLOCK) || s.is(Blocks.RAW_COPPER_BLOCK)
			|| s.is(Blocks.RAW_GOLD_BLOCK) || s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(Blocks.CHEST) || s.is(Blocks.BARREL);
		long one = count(blueprint(helper, StarterBlueprints.SMITHY), ore);
		long two = count(blueprint(helper, StarterBlueprints.SMITHY_2), ore);
		helper.assertTrue(two > one, "Smithy II has no more coal and ore store than the Smithy: " + two + " vs " + one);
		helper.succeed();
	}

	/** 27.13: the Mason's Yard is fenced with one stonecutter, its II two; the Fletcher's Lodge is of logs with a target, its II two tables; the Map Room a tall tower. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaYardLodgeAndMapRoomHoldTheirBlocks(GameTestHelper helper) {
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.MASONS_YARD, StarterBlueprints.MASONS_YARD_2)) {
			Blueprint b = blueprint(helper, e);
			exactly(helper, e, "stonecutters", count(b, Blocks.STONECUTTER), e == StarterBlueprints.MASONS_YARD ? 1 : 2);
			has(helper, e, "fences or walls (the yard's fence)", count(b, s -> s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS)), 8);
		}
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.FLETCHERS_LODGE, StarterBlueprints.FLETCHERS_LODGE_2)) {
			Blueprint b = blueprint(helper, e);
			exactly(helper, e, "fletching tables", count(b, Blocks.FLETCHING_TABLE), e == StarterBlueprints.FLETCHERS_LODGE ? 1 : 2);
			has(helper, e, "logs (a log cabin and pile)", count(b, s -> s.is(BlockTags.LOGS)), 20);
			has(helper, e, "targets or hay (the straw target)", count(b, s -> s.is(Blocks.TARGET) || s.is(Blocks.HAY_BLOCK)), 1);
		}
		StarterBlueprints.Entry room = StarterBlueprints.MAP_ROOM;
		Blueprint b = blueprint(helper, room);
		exactly(helper, room, "cartography tables", count(b, Blocks.CARTOGRAPHY_TABLE), 1);
		int wide = Math.max(b.size().getX(), b.size().getZ());
		helper.assertTrue(b.size().getY() >= wide + wide / 2, "the Map Room isn't a narrow tower: " + b.size());
		// The lookout at the top: something to stand on in the top quarter of the tower, and a way up.
		int top = b.size().getY() - b.basement();
		long high = b.blocks().stream().filter(x -> x.pos().getY() >= top * 3 / 4 && !x.state().isAir()).count();
		has(helper, room, "blocks in its top quarter (the lookout)", high, 1);
		has(helper, room, "ladders or stairs up", count(b, s -> s.is(Blocks.LADDER) || s.is(BlockTags.STAIRS)), 1);
		helper.succeed();
	}

	/** 27.14: the Farmstead's one bed, composter, scarecrow, chest and 9 x 5 field round a water channel; its II two fields. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaFarmsteadHoldsItsFieldAndBed(GameTestHelper helper) {
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.FARMSTEAD, StarterBlueprints.FARMSTEAD_2)) {
			Blueprint b = blueprint(helper, e);
			boolean one = e == StarterBlueprints.FARMSTEAD;
			if (one) {
				exactly(helper, e, "beds", beds(b), 1);
			}
			exactly(helper, e, "composters", count(b, Blocks.COMPOSTER), one ? 1 : 2);
			has(helper, e, "chests", count(b, Blocks.CHEST), 1);
			has(helper, e, "carved pumpkins (the scarecrow's head)", count(b, s -> s.is(Blocks.CARVED_PUMPKIN) || s.is(Blocks.JACK_O_LANTERN)), 1);
			List<BlockPos> farmland = b.blocks().stream().filter(x -> x.state().is(Blocks.FARMLAND)).map(Blueprint.Entry::pos).toList();
			helper.assertTrue(!farmland.isEmpty(), e.id() + " has no farmland");
			BoundingBox field = BoundingBox.encapsulatingPositions(farmland).orElseThrow();
			int a = field.getXSpan();
			int c = field.getZSpan();
			helper.assertTrue(field.getYSpan() == 1, e.id() + "'s field isn't level: " + field);
			if (one) {
				helper.assertTrue(Math.min(a, c) == 5 && Math.max(a, c) == 9, "the Farmstead's field is " + a + " x " + c + ", not 9 x 5");
			} else {
				helper.assertTrue(farmland.size() >= 2 * 36, "Farmstead II has " + farmland.size() + " farmland, not two fields");
			}
			// The water channel runs through the field, on its level.
			long channel = b.blocks().stream().filter(x -> x.state().is(Blocks.WATER) && field.isInside(x.pos())).count();
			has(helper, e, "water blocks in the field (its channel)", channel, 4);
			// Every farmland block within 4 of water (or it dries out and won't stay farmland).
			List<BlockPos> water = b.blocks().stream().filter(x -> x.state().is(Blocks.WATER)).map(Blueprint.Entry::pos).toList();
			for (BlockPos f : farmland) {
				helper.assertTrue(water.stream().anyMatch(w -> Math.abs(w.getX() - f.getX()) <= 4 && Math.abs(w.getZ() - f.getZ()) <= 4
					&& w.getY() >= f.getY() && w.getY() <= f.getY() + 1), e.id() + ": farmland at " + f + " has no water within 4");
			}
		}
		helper.succeed();
	}

	/** 27.14: the Fisher's Hut's barrel and its II's smoker; the Weaver's Cottage's loom and pen, its II's dye garden; the Bandstand. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaHutCottageAndBandstandHoldTheirBlocks(GameTestHelper helper) {
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.FISHERS_HUT, StarterBlueprints.FISHERS_HUT_2)) {
			Blueprint b = blueprint(helper, e);
			exactly(helper, e, "barrels", count(b, Blocks.BARREL), 1);
			exactly(helper, e, "smokers", count(b, Blocks.SMOKER), e == StarterBlueprints.FISHERS_HUT ? 0 : 1);
			has(helper, e, "logs (the jetty's posts)", count(b, s -> s.is(BlockTags.LOGS)), 4);
		}
		long flowersOne = 0;
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.WEAVERS_COTTAGE, StarterBlueprints.WEAVERS_COTTAGE_2)) {
			Blueprint b = blueprint(helper, e);
			exactly(helper, e, "looms", count(b, Blocks.LOOM), 1);
			has(helper, e, "fences (the sheep pen)", count(b, s -> s.is(BlockTags.FENCES)), 8);
			has(helper, e, "fence gates (into the pen)", count(b, s -> s.is(BlockTags.FENCE_GATES)), 1);
			long flowers = count(b, s -> s.is(BlockTags.SMALL_FLOWERS) || s.is(BlockTags.TALL_FLOWERS));
			if (e == StarterBlueprints.WEAVERS_COTTAGE) {
				flowersOne = flowers;
			} else {
				helper.assertTrue(flowers >= flowersOne + 8, "Weaver's Cottage II's dye garden: " + flowers + " flowers vs " + flowersOne);
			}
		}
		StarterBlueprints.Entry e = StarterBlueprints.BANDSTAND;
		Blueprint b = blueprint(helper, e);
		exactly(helper, e, "jukeboxes", count(b, Blocks.JUKEBOX), 1);
		exactly(helper, e, "beds", beds(b), 0);
		// Eight-sided: the footprint the same mirrored either way and turned a quarter, and not a full square.
		Set<Long> foot = new HashSet<>();
		int minY = b.blocks().stream().mapToInt(x -> x.pos().getY()).min().orElse(0);
		for (Blueprint.Entry x : b.blocks()) {
			if (x.pos().getY() == minY && !x.state().isAir()) {
				foot.add(key(x.pos().getX(), x.pos().getZ()));
			}
		}
		int sx = b.size().getX();
		int sz = b.size().getZ();
		helper.assertTrue(sx == sz, "the Bandstand isn't square-sized: " + b.size());
		for (Blueprint.Entry x : b.blocks()) {
			if (x.pos().getY() != minY || x.state().isAir()) {
				continue;
			}
			int px = x.pos().getX();
			int pz = x.pos().getZ();
			helper.assertTrue(foot.contains(key(sx - 1 - px, pz)) && foot.contains(key(px, sz - 1 - pz)) && foot.contains(key(pz, px)),
				"the Bandstand's base isn't eight-sided at " + px + "," + pz);
		}
		helper.assertTrue(!foot.contains(key(0, 0)) && foot.size() < sx * sz, "the Bandstand's base is a square");
		// Open: between floor and roof, a ring of posts, no walls.
		int floor = minY + 1;
		int roof = b.blocks().stream().filter(x -> !x.state().isAir()).mapToInt(x -> x.pos().getY()).max().orElse(0);
		boolean open = false;
		for (int y = floor + 1; y < roof; y++) {
			final int yy = y;
			long solid = b.blocks().stream().filter(x -> x.pos().getY() == yy && !x.state().isAir()).count();
			open |= solid <= 12;
		}
		helper.assertTrue(open, "the Bandstand has no open level between floor and roof (walls, not posts)");
		helper.succeed();
	}

	private static long key(int x, int z) {
		return ((long) x << 32) | (z & 0xffffffffL);
	}

	/** Their names in the Blueprint Table as the spec spells them; Map Room and Bandstand have no II; every rule's reason has text. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaNewWorkplaceNamesAndText(GameTestHelper helper) {
		Map<StarterBlueprints.Entry, String> names = Map.ofEntries(
			Map.entry(StarterBlueprints.SMITHY, "Smithy"), Map.entry(StarterBlueprints.SMITHY_2, "Smithy II"),
			Map.entry(StarterBlueprints.MASONS_YARD, "Mason's Yard"), Map.entry(StarterBlueprints.MASONS_YARD_2, "Mason's Yard II"),
			Map.entry(StarterBlueprints.FLETCHERS_LODGE, "Fletcher's Lodge"), Map.entry(StarterBlueprints.FLETCHERS_LODGE_2, "Fletcher's Lodge II"),
			Map.entry(StarterBlueprints.MAP_ROOM, "Map Room"),
			Map.entry(StarterBlueprints.FARMSTEAD, "Farmstead"), Map.entry(StarterBlueprints.FARMSTEAD_2, "Farmstead II"),
			Map.entry(StarterBlueprints.FISHERS_HUT, "Fisher's Hut"), Map.entry(StarterBlueprints.FISHERS_HUT_2, "Fisher's Hut II"),
			Map.entry(StarterBlueprints.WEAVERS_COTTAGE, "Weaver's Cottage"), Map.entry(StarterBlueprints.WEAVERS_COTTAGE_2, "Weaver's Cottage II"),
			Map.entry(StarterBlueprints.BANDSTAND, "Bandstand"));
		Language english = Language.getInstance();
		names.forEach((e, name) -> {
			String key = "blueprint.aliveworkplace." + e.id().getPath();
			helper.assertTrue(english.has(key) && english.getOrDefault(key).equals(name), key + " reads \"" + english.getOrDefault(key) + "\", not \"" + name + "\"");
		});
		List<ResourceLocation> library = BlueprintLibrary.list(helper.getLevel().getServer(), true);
		for (String one : List.of("map_room_2", "bandstand_2")) {
			helper.assertTrue(!library.contains(AliveWorkplace.id(one)), one + " is in the library, but the spec gives it no upgrade");
		}
		for (String r : RULES) {
			StewardRules.Rule rule = ruleOf(r);
			helper.assertTrue(english.has(rule.why()), r + "'s reason " + rule.why() + " has no text");
			helper.assertTrue(english.getOrDefault(rule.why()).contains("%s"), r + "'s reason doesn't show the count");
		}
		helper.succeed();
	}

	/** 27.14: the Bandstand counts 3 for beauty, in any of its styles; none of the other new workplaces counts for any. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaOnlyTheBandstandIsADecoration(GameTestHelper helper) {
		helper.assertTrue(Decorations.points(StarterBlueprints.BANDSTAND.id()) == 3, "Bandstand: " + Decorations.points(StarterBlueprints.BANDSTAND.id()));
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.SMITHY, StarterBlueprints.SMITHY_2, StarterBlueprints.MASONS_YARD,
			StarterBlueprints.MASONS_YARD_2, StarterBlueprints.FLETCHERS_LODGE, StarterBlueprints.FLETCHERS_LODGE_2, StarterBlueprints.MAP_ROOM,
			StarterBlueprints.FARMSTEAD, StarterBlueprints.FARMSTEAD_2, StarterBlueprints.FISHERS_HUT, StarterBlueprints.FISHERS_HUT_2,
			StarterBlueprints.WEAVERS_COTTAGE, StarterBlueprints.WEAVERS_COTTAGE_2)) {
			helper.assertTrue(Decorations.points(e.id()) == 0, e.id() + " counts " + Decorations.points(e.id()) + " for beauty");
		}
		helper.succeed();
	}

	// --- the Fisher's Hut II on a shore ------------------------------------------------------------------

	private static final BlockPos PLOT_HALL = new BlockPos(2, 2, 14);
	private static final BlockPos PLOT_TABLE = new BlockPos(2, 2, 10);

	/** Flat grass over dirt with stone under, the hall and a Blueprint Table; no builds left from earlier batches nearby. */
	private static void ground(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 40, level.getMinBuildHeight(), a.getZ() - 40, a.getX() + 70, level.getMaxBuildHeight(), a.getZ() + 70);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			if (near.isInside(site.placement().origin())) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 16; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(PLOT_HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(PLOT_TABLE, ModBlocks.BLUEPRINT_TABLE);
	}

	/**
	 * A Fisher's Hut II, like the first, goes only on a shore: refused on dry land, refused with water 4 deep under its
	 * jetty, and placed where shallow water runs under the whole jetty. Turned to face west; its jetty is the 5 rows
	 * nearest the hall, which the plot's box tells.
	 */
	//$ gametest_ticks_batch HUGE_AREA '20' '"qaJetty2"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 20, batch = "qaJetty2")
	public void qaAFishersHutTwoWantsAShore(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		BitSet cells = new BitSet();
		BlockPos hallAt = helper.absolutePos(PLOT_HALL);
		for (int x = 4; x <= 29; x++) {
			for (int z = 0; z <= 29; z++) {
				cells.set(CityPlan.cellAt(hallAt, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		CityPlan plan = CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells);
		ResourceLocation hut = StarterBlueprints.FISHERS_HUT_2.id();
		helper.assertTrue(Plots.SHORE_BUILDINGS.contains(hut) && Plots.JETTIES.getOrDefault(hut, 0) == 5, "the Fisher's Hut II isn't a shore build with a 5-row jetty");
		BlockPos spot = new BlockPos(17, 2, 14);
		java.util.function.Supplier<Plots.Verdict> at = () -> Plots.check(level, hallAt, plan, "homes", hut, helper.absolutePos(spot),
			Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
		java.util.Map<BlockPos, BlockState> under = new java.util.HashMap<>();
		for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, -4, 0)), helper.absolutePos(new BlockPos(29, -1, 29)))) {
			under.put(p.immutable(), level.getBlockState(p));
		}
		Leftovers.after(helper, () -> under.forEach((p, s) -> level.setBlock(p, s, 2)));
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, -4, 0), new BlockPos(29, -1, 29))) {
			helper.setBlock(p, Blocks.STONE);
		}
		Plots.Verdict dry = at.get();
		helper.assertTrue(dry.reason().equals(Optional.of(Plots.Reason.SHORE)), "on dry land: " + dry);
		// Turned to face west and centred on the spot, its 19 rows run x 7/8..25/26: the jetty is the westmost 5 (x 7/8..11/12).
		// Water 2 deep at x 4..11, past the jetty's end and under it, but never under the hut.
		for (int x = 4; x <= 11; x++) {
			for (int z = 2; z <= 26; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
				helper.setBlock(new BlockPos(x, 0, z), Blocks.WATER);
			}
		}
		Plots.Verdict shallow = at.get();
		helper.assertTrue(shallow.plot().isPresent(), "water 2 deep under the jetty: " + shallow);
		int west = shallow.plot().get().box().minX() - helper.absolutePos(BlockPos.ZERO).getX();
		// A hole 4 deep under the jetty's middle.
		for (int z = 2; z <= 26; z++) {
			helper.setBlock(new BlockPos(9, -1, z), Blocks.WATER);
			helper.setBlock(new BlockPos(9, -2, z), Blocks.WATER);
		}
		Plots.Verdict deep = at.get();
		helper.assertTrue(deep.reason().equals(Optional.of(Plots.Reason.DEEP)), "water 4 deep under the jetty (the box from x " + west + "): " + deep);
		helper.succeed();
	}
}
