package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Conditions;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Ships;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane D, 2026-11-15, a mutation pass on {@link Ships}): the pirate ship's anchorage and its sinking (ROADMAP 32.8),
 * written from the spec. "Anchored on water at least 5 deep, 40-70 blocks out" (tested with a small sea, the same rule
 * scaled down, since the test areas hold no 70-block sea); "when the captain falls the ship burns and sinks: its own
 * blocks go, top down, over 10 s"; a restart mid-sinking carries on (the ships' saved data); nothing in the ship's
 * barrels and gunports spills onto the water; the crew wades in from up to three blocks out; the anchoring and sinking
 * lines a player reads. Kills the mutants Ships:173/174 (the far edges of the hull unchecked), 178 (a shallow side
 * fits), 156 (33 for 32), 295/297 (wading), 463 (the sinking ends a tick late), 485 (barrels spill), 470, 439/443
 * (tick), 592/595/596 (a save and load adds, loses or restarts a sinking).
 */
public class QaPirateShips1115dGameTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:tall_area";
	private static final BlockPos HALL = new BlockPos(1, 8, 1);
	private static final BlockPos SHIP = new BlockPos(15, 7, 14);
	private static final long DAY = 24000;
	private static final ResourceLocation PIRATES = AliveWorkplace.id("drowned_pirates");

	// The anchorage.

	/**
	 * A walled sea over the whole huge area: water from y 2 to 6 (five deep) over a stone floor, open air above. The chunks
	 * round the area are loaded, as a player nearby keeps them (the anchorage looks 16 blocks round each spot it tries).
	 */
	private static void sea(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos middle = helper.absolutePos(new BlockPos(15, 0, 15));
		for (int cx = (middle.getX() >> 4) - 3; cx <= (middle.getX() >> 4) + 3; cx++) {
			for (int cz = (middle.getZ() >> 4) - 3; cz <= (middle.getZ() >> 4) + 3; cz++) {
				level.getChunk(cx, cz);
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				boolean wall = x == 0 || x == 29 || z == 0 || z == 29;
				level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 2);
				for (int y = 2; y <= 6; y++) {
					level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), (wall ? Blocks.GLASS : Blocks.WATER).defaultBlockState(), 2);
				}
				for (int y = 7; y <= 40; y++) {
					level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 2); // (and the test's barrier roof: open sky)
				}
			}
		}
	}

	/** A 3 x 2 x 3 boat (the anchorage's rules don't depend on what it is made of). */
	private static StructureTemplate boat(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1115d_boat");
		StructureTemplate template = level.getStructureManager().getOrCreate(id);
		template.fillFromWorld(level, helper.absolutePos(new BlockPos(2, 20, 2)), new Vec3i(3, 2, 3), false, Blocks.STRUCTURE_VOID);
		return template;
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** 40 throws' anchorages for a hall at {@code hall}, from one fixed random (null where there is none). */
	private static List<BlockPos> sites(ServerLevel level, BlockPos hall, Culture.Sea sea, StructureTemplate template) {
		List<BlockPos> out = new ArrayList<>();
		RandomSource random = RandomSource.create(1115L);
		for (int i = 0; i < 40; i++) {
			out.add(Ships.site(level, hall, sea, template, random));
		}
		return out;
	}

	/**
	 * Near to far out, and never nearer or farther: with a sea 6 to 11 out every anchorage lies on the sea's top between
	 * them (6 less the rounding of a block's corner), and over 40 throws the ships anchor both near (under 8.5) and far
	 * (over 9.5) out, so the whole span is used, not just its near end.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qa1115dShipSpan"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa1115dShipSpan")
	public void aShipAnchorsAnywhereFromNearToFarOutAndNowhereElse(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		sea(helper);
		helper.runAfterDelay(2, () -> {
			StructureTemplate template = boat(helper);
			Culture.Sea sea = new Culture.Sea(1, 6, 11, 5, true);
			BlockPos hall = helper.absolutePos(new BlockPos(15, 8, 15));
			int top = helper.absolutePos(new BlockPos(0, 6, 0)).getY();
			List<BlockPos> sites = sites(level, hall, sea, template);
			double min = Double.MAX_VALUE;
			double max = 0;
			for (int i = 0; i < sites.size(); i++) {
				BlockPos site = sites.get(i);
				helper.assertTrue(site != null, "throw " + i + ": no anchorage on an open sea five deep all round the village");
				helper.assertTrue(site.getY() == top && level.getFluidState(site).is(FluidTags.WATER), "throw " + i + ": the anchorage " + site
					+ " isn't the sea's top (y " + top + "): " + level.getBlockState(site));
				double far = horizontal(site, hall);
				helper.assertTrue(far >= sea.near() - 1.5 && far <= sea.far() + 0.001, "throw " + i + ": the ship anchors " + far + " out, not "
					+ sea.near() + " to " + sea.far());
				min = Math.min(min, far);
				max = Math.max(max, far);
			}
			helper.assertTrue(max > sea.far() - 1.5, "over 40 throws no ship anchored far out (farthest " + max + ", far is " + sea.far() + ")");
			helper.assertTrue(min < sea.near() + 2.5, "over 40 throws no ship anchored near (nearest " + min + ", near is " + sea.near() + ")");
			AliveWorkplace.LOG.info("QA 1115d: 40 anchorages from {} to {} out (near {}, far {})", min, max, sea.near(), sea.far());
			helper.succeed();
		});
	}

	/** Water four deep under a ship that needs five: there is nowhere to anchor, whichever way the dice fall. */
	//$ gametest_ticks_batch HUGE '60' '"qa1115dShipShallow"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa1115dShipShallow")
	public void aSeaFourDeepHasNoAnchorageForAShipThatNeedsFive(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		sea(helper);
		for (int x = 1; x < 29; x++) {
			for (int z = 1; z < 29; z++) {
				level.setBlock(helper.absolutePos(new BlockPos(x, 2, z)), Blocks.STONE.defaultBlockState(), 2);
			}
		}
		helper.runAfterDelay(2, () -> {
			StructureTemplate template = boat(helper);
			Culture.Sea sea = new Culture.Sea(1, 6, 11, 5, true);
			BlockPos hall = helper.absolutePos(new BlockPos(15, 8, 15));
			helper.assertTrue(Ships.depth(level, helper.absolutePos(new BlockPos(15, 6, 15))) == 4, "the sea isn't four deep");
			List<BlockPos> sites = sites(level, hall, sea, template);
			helper.assertTrue(sites.stream().allMatch(s -> s == null), "a ship that needs water five deep anchored on water four deep: " + sites);
			helper.assertTrue(sites(level, hall, new Culture.Sea(1, 6, 11, 4, true), template).stream().allMatch(s -> s != null),
				"a ship that needs four deep found no anchorage on water four deep");
			helper.succeed();
		});
	}

	/**
	 * The sea may lie up to 32 blocks below the hall, no more: a hall 31 or 32 above the sea's top has its anchorages, one
	 * 33 or 34 above has none. (Below the sea is under the bottom of the world in the test areas: not tested.)
	 */
	//$ gametest_ticks_batch HUGE '60' '"qa1115dShipHeight"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa1115dShipHeight")
	public void aSeaThirtyTwoBelowTheHallIsItsSeaButNotThirtyThree(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		sea(helper);
		helper.runAfterDelay(2, () -> {
			StructureTemplate template = boat(helper);
			Culture.Sea sea = new Culture.Sea(1, 6, 11, 5, true);
			BlockPos top = helper.absolutePos(new BlockPos(15, 6, 15));
			for (int dy : new int[] {31, 32}) {
				List<BlockPos> sites = sites(level, top.above(dy), sea, template);
				helper.assertTrue(sites.stream().allMatch(s -> s != null && s.getY() == top.getY()), "a hall " + dy + " from the sea's top has no anchorage: " + sites);
			}
			for (int dy : new int[] {33, 34}) {
				List<BlockPos> sites = sites(level, top.above(dy), sea, template);
				helper.assertTrue(sites.stream().allMatch(s -> s == null), "a hall " + dy + " from the sea's top has an anchorage: " + sites);
			}
			helper.succeed();
		});
	}

	/**
	 * Five deep under all of it, not just under its middle: a sea whose top is level everywhere but which is five deep only
	 * in every third column (four deep between) has no anchorage for a ship three wide, whichever way the dice fall,
	 * though the middle of every throw may be five deep.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qa1115dShipShelf"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa1115dShipShelf")
	public void aShipNeedsWaterFiveDeepUnderAllOfItNotJustItsMiddle(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		sea(helper);
		for (int x = 1; x < 29; x++) {
			if (x % 3 != 0) {
				for (int z = 1; z < 29; z++) {
					level.setBlock(helper.absolutePos(new BlockPos(x, 2, z)), Blocks.STONE.defaultBlockState(), 2);
				}
			}
		}
		helper.runAfterDelay(2, () -> {
			StructureTemplate template = boat(helper);
			Culture.Sea sea = new Culture.Sea(1, 6, 11, 5, true);
			BlockPos hall = helper.absolutePos(new BlockPos(15, 8, 15));
			helper.assertTrue(Ships.depth(level, helper.absolutePos(new BlockPos(15, 6, 15))) == 5 && Ships.depth(level, helper.absolutePos(new BlockPos(16, 6, 15))) == 4,
				"the sea isn't five deep in every third column and four between");
			helper.assertFalse(Ships.fits(level, helper.absolutePos(new BlockPos(15, 6, 15)), hall, sea, template),
				"a ship three wide fits with its middle on water five deep and its sides on water four deep");
			List<BlockPos> sites = sites(level, hall, sea, template);
			helper.assertTrue(sites.stream().allMatch(s -> s == null), "a ship anchored with water four deep under its sides: " + sites);
			helper.succeed();
		});
	}

	/**
	 * The far edges count too: a sea five deep but for every third column (x, then z), four deep there, so that every ship
	 * three wide whose middle and west (north) side lie on water five deep has its east (south) side on water four deep.
	 * No ship anchors either way.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qa1115dShipEdge"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa1115dShipEdge")
	public void aShipNeedsWaterFiveDeepUnderItsFarEdgesToo(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		sea(helper);
		helper.runAfterDelay(2, () -> {
			StructureTemplate template = boat(helper);
			Culture.Sea sea = new Culture.Sea(1, 6, 11, 5, true);
			BlockPos hall = helper.absolutePos(new BlockPos(15, 8, 15));
			for (boolean alongX : new boolean[] {true, false}) {
				for (int x = 1; x < 29; x++) {
					for (int z = 1; z < 29; z++) {
						int i = alongX ? helper.absolutePos(new BlockPos(x, 2, z)).getX() : helper.absolutePos(new BlockPos(x, 2, z)).getZ();
						level.setBlock(helper.absolutePos(new BlockPos(x, 2, z)), (Math.floorMod(i, 3) == 1 ? Blocks.STONE : Blocks.WATER).defaultBlockState(), 2);
					}
				}
				String way = alongX ? "east" : "south";
				List<BlockPos> sites = sites(level, hall, sea, template);
				helper.assertTrue(sites.stream().allMatch(s -> s == null), "a ship anchored with water four deep under its " + way + " side: " + sites);
			}
			helper.succeed();
		});
	}

	/**
	 * Another village's water isn't this village's anchorage: with a second village's hall standing on a pillar in the sea
	 * inside the span, no ship anchors within the near distance (6) of that hall, though without it some of
	 * the same throws do.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qa1115dShipNeighbour"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa1115dShipNeighbour")
	public void aShipNeverAnchorsInAnotherVillagesWater(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		sea(helper);
		BlockPos other = new BlockPos(23, 7, 15);
		helper.runAfterDelay(2, () -> {
			StructureTemplate template = boat(helper);
			Culture.Sea sea = new Culture.Sea(1, 6, 11, 5, true);
			BlockPos hall = helper.absolutePos(new BlockPos(15, 8, 15));
			BlockPos neighbour = helper.absolutePos(other);
			List<BlockPos> open = sites(level, hall, sea, template);
			long close = open.stream().filter(s -> s != null && horizontal(s, neighbour) <= sea.near()).count();
			helper.assertTrue(close > 0, "even with no village there no throw lands within " + sea.near() + " of the second hall's spot: the test proves nothing");
			for (int y = 2; y <= 6; y++) {
				helper.setBlock(new BlockPos(other.getX(), y, other.getZ()), Blocks.STONE);
			}
			helper.setBlock(other, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(1, () -> {
				helper.assertTrue(VillageHalls.nearest(level, neighbour.below()).map(neighbour::equals).orElse(false), "the second hall isn't a village hall: "
					+ VillageHalls.nearest(level, neighbour.below()));
				List<BlockPos> sites = sites(level, hall, sea, template);
				List<BlockPos> theirs = sites.stream().filter(s -> s != null && horizontal(s, neighbour) <= sea.near()).toList();
				helper.assertTrue(theirs.isEmpty(), theirs.size() + " ships anchored within " + sea.near() + " of another village's hall: " + theirs);
				helper.assertTrue(sites.stream().anyMatch(s -> s != null), "with a second village by it the sea has no anchorage at all");
				helper.succeed();
			});
		});
	}

	// Coming ashore.

	/**
	 * The crew wades in "from the water just off the beach", up to three blocks out towards the ship: on a straight shore
	 * with the ship out to sea, they start three blocks out, each of those blocks open water; with a block lying on the
	 * water two out, they start one out, never past it; with the ship inland, at the beach itself.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qa1115dShipWading"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa1115dShipWading")
	public void theCrewWadesInFromUpToThreeBlocksOutOnOpenWater(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		// Land (z 0 to 9) and sea (z 10 to 28) with the same top, y 5; a glass wall round the sea.
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				boolean wall = x == 0 || x == 29 || z == 29;
				level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 2);
				for (int y = 2; y <= 5; y++) {
					BlockState block = z <= 9 ? Blocks.STONE.defaultBlockState() : wall ? Blocks.GLASS.defaultBlockState() : Blocks.WATER.defaultBlockState();
					level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), block, 2);
				}
				for (int y = 6; y <= 10; y++) {
					level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		helper.runAfterDelay(2, () -> {
			BlockPos beach = helper.absolutePos(new BlockPos(15, 6, 9));
			BlockPos ship = helper.absolutePos(new BlockPos(15, 6, 26));
			BlockPos wade = Ships.wading(level, beach, ship);
			BlockPos expected = helper.absolutePos(new BlockPos(15, 6, 12));
			helper.assertTrue(wade.equals(expected), "with open water off the beach the crew wades in at " + helper.relativePos(wade) + ", not three out at "
				+ helper.relativePos(expected));
			helper.assertTrue(level.getFluidState(wade.below()).is(FluidTags.WATER) && level.getBlockState(wade).isAir(), "the crew's wading spot isn't on open water: "
				+ level.getBlockState(wade.below()) + " under " + level.getBlockState(wade));
			BlockPos inland = helper.absolutePos(new BlockPos(15, 6, 1));
			helper.assertTrue(Ships.wading(level, beach, inland).equals(beach), "with no water towards the ship the crew doesn't start at the beach: "
				+ helper.relativePos(Ships.wading(level, beach, inland)));
			helper.setBlock(new BlockPos(15, 6, 11), Blocks.OAK_PLANKS);
			BlockPos blocked = Ships.wading(level, beach, ship);
			helper.assertTrue(blocked.equals(helper.absolutePos(new BlockPos(15, 6, 10))), "with a block on the water two out the crew wades in at "
				+ helper.relativePos(blocked) + ", not one out");
			helper.succeed();
		});
	}

	// The sinking.

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/** {@link QaShips1103dGameTests}'s coast: a beach strip along the north with the hall, a pool six deep to the south. */
	private static BlockPos coast(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		Set<String> off = Threats.DISABLED;
		boolean open = Expansions.openForTests;
		boolean lairs = Lairs.ENABLED;
		Lairs.ENABLED = true;
		VillageHalls.RADIUS = 16;
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				for (int y = 2; y <= 7; y++) {
					BlockState block = z <= 3 ? (y == 7 ? Blocks.SAND : Blocks.STONE).defaultBlockState()
						: x == 0 || x == 29 || z == 29 ? Blocks.GLASS.defaultBlockState() : Blocks.WATER.defaultBlockState();
					level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), block, 2);
				}
				for (int y = 8; y <= 9; y++) {
					level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		String old = level.getBiome(hall).unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
		fill(level, hall, "minecraft:beach");
		Conditions.forgetCoasts();
		ThreatData.get(level).forgetPast(hall);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(64), m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(VillageRaids.TAG))
				.forEach(Mob::discard);
			VillageRaids.forget();
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			Threats.DISABLED = off;
			Expansions.openForTests = open;
			Lairs.ENABLED = lairs;
			level.setDayTime(time);
			fill(level, hall, old);
			Conditions.forgetCoasts();
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(3 + i, 8, 1));
		}
		return hall;
	}

	private static void fill(ServerLevel level, BlockPos at, String biome) {
		String cmd = "fillbiome " + (at.getX() - 6) + " " + (at.getY() - 4) + " " + (at.getZ() - 6) + " " + (at.getX() + 6) + " " + (at.getY() + 4)
			+ " " + (at.getZ() + 6) + " " + biome;
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
	}

	/**
	 * What a player at the hall reads (the spec and the guide: "Black sails off Thornholm: Captain Silas Brine has dropped
	 * anchor 52 blocks south with his drowned crew..."): the ship anchoring, with the village's name, how far out the ship
	 * lies and which way, and the captain's name; then, when he falls, the ship burning and sinking, his name, and his chest
	 * on a raft. Every line once, whole, with no placeholder or key left in it.
	 */
	//$ gametest_ticks_batch AREA '280' '"qa1115dShipChat"'
	@GameTest(template = AREA, timeoutTicks = 280, batch = "qa1115dShipChat")
	public void aPlayerAtTheHallReadsTheShipAnchorAndSinkWithRealNames(GameTestHelper helper) {
		BlockPos hall = coast(helper);
		ServerLevel level = helper.getLevel();
		List<net.minecraft.network.chat.Component> heard = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			net.minecraft.server.level.ServerPlayer player = QaServiceJobs1031GameTests.listener(helper, heard);
			player.teleportTo(hall.getX() + 0.5, hall.getY() + 1, hall.getZ() + 0.5);
			((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
			at(level, 150, 13000);
			heard.clear();
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(SHIP), PIRATES);
			helper.assertTrue(lair != null && Ships.of(level, hall).isPresent(), "no pirate ship anchored: " + lair);
			String village = VillageHalls.name(level, hall).getString();
			double dx = lair.pos().getX() - hall.getX();
			double dz = lair.pos().getZ() - hall.getZ();
			long out = Math.round(Math.sqrt(dx * dx + dz * dz));
			List<String> anchored = heard.stream().map(net.minecraft.network.chat.Component::getString).filter(s -> s.contains("anchor")).toList();
			helper.assertTrue(anchored.size() == 1, "the player read the ship anchoring " + anchored.size() + " times: " + anchored);
			String line = anchored.get(0);
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("Black sails off (.+): Captain Silas Brine has dropped anchor (\\d+) blocks south-east with his drowned crew\\. "
				+ "Until Captain Silas Brine falls, they will come ashore by night\\.").matcher(line);
			helper.assertTrue(m.matches(), "the anchoring line reads: \"" + line + "\"");
			helper.assertTrue(m.group(1).equals(village) && !village.isBlank(), "the anchoring line names the village \"" + m.group(1) + "\", not \"" + village + "\"");
			helper.assertTrue(Math.abs(Long.parseLong(m.group(2)) - out) <= 1, "the anchoring line puts the ship " + m.group(2) + " blocks out, not " + out);
			heard.clear();
			((Mob) level.getEntity(lair.captain())).hurt(level.damageSources().fellOutOfWorld(), 1000f);
			List<String> sunk = heard.stream().map(net.minecraft.network.chat.Component::getString).filter(s -> s.contains("sinks")).toList();
			String expected = "The pirate ship off " + village + " burns and sinks: Captain Silas Brine has fallen. His chest is left floating on a raft.";
			helper.assertTrue(sunk.equals(List.of(expected)), "the sinking line reads " + sunk + ", not [" + expected + "]");
			for (String s : heard.stream().map(net.minecraft.network.chat.Component::getString).toList()) {
				helper.assertFalse(s.contains("%") || s.contains("aliveworkplace."), "a line with a placeholder or a key left in it: " + s);
			}
			AliveWorkplace.LOG.info("QA 1115d: \"{}\" / \"{}\"", line, sunk.get(0));
			// (the ship is let go down whole, so its sinking can't take blocks of the next test standing here)
			helper.runAfterDelay(Ships.SINK_TICKS + 5, () -> {
				helper.assertTrue(Ships.sinking(level).isEmpty(), "the ship is still sinking: " + Ships.sinking(level));
				helper.succeed();
			});
		});
	}

	/**
	 * Captain Silas Brine falls with a diamond in every barrel and dispenser of his ship. Halfway through its ten seconds
	 * the ship has gone from the top down: nothing of its upper 40% is left and all of its lower 40% still stands. The
	 * world is saved and loaded right then (the ships' data read back as a restart does): it is still sinking from the same
	 * moment, and it is gone by the end of its ten seconds, the sea where its hull lay below the waterline and air above.
	 * No diamond floats on the water: nothing in its barrels and gunports spilled.
	 */
	//$ gametest_ticks_batch AREA '320' '"qa1115dShipSinksTopDown"'
	@GameTest(template = AREA, timeoutTicks = 320, batch = "qa1115dShipSinksTopDown")
	public void theShipSinksTopDownCarriesOnAfterARestartAndSpillsNothing(GameTestHelper helper) {
		BlockPos hall = coast(helper);
		ServerLevel level = helper.getLevel();
		Ships.Placed[] ship = {null};
		long[] began = {-1};
		boolean[] halfway = {false};
		long[] ended = {-1};
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		int[] span = {0, 0}; // the ship's lowest and highest block
		helper.runAfterDelay(2, () -> {
			((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
			at(level, 140, 13000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(SHIP), PIRATES);
			helper.assertTrue(lair != null && Ships.of(level, hall).isPresent(), "no pirate ship anchored: " + lair);
			ship[0] = Ships.of(level, hall).orElseThrow();
			int stocked = 0;
			span[0] = Integer.MAX_VALUE;
			span[1] = Integer.MIN_VALUE;
			for (Map.Entry<BlockPos, BlockState> e : Ships.blocks(level, ship[0]).entrySet()) {
				if (e.getValue().isAir() || e.getValue().getBlock() instanceof ChestBlock) {
					continue; // (the loot chest rides the raft)
				}
				blocks.put(e.getKey(), e.getValue());
				span[0] = Math.min(span[0], e.getKey().getY());
				span[1] = Math.max(span[1], e.getKey().getY());
				if ((e.getValue().is(Blocks.BARREL) || e.getValue().is(Blocks.DISPENSER)) && level.getBlockEntity(e.getKey()) instanceof Container c) {
					c.setItem(0, new ItemStack(Items.DIAMOND));
					stocked++;
				}
			}
			helper.assertTrue(stocked > 0, "the ship has no barrel or dispenser to stock");
			((Mob) level.getEntity(lair.captain())).hurt(level.damageSources().fellOutOfWorld(), 1000f);
			began[0] = level.getGameTime();
			helper.assertTrue(Ships.of(level, hall).isEmpty() && Ships.sinking(level).stream().anyMatch(s -> s.ship().equals(ship[0])),
				"the captain fell but the ship isn't sinking: " + Ships.sinking(level));
		});
		for (int t = 3; t <= 2 + Ships.SINK_TICKS + 5; t++) {
			helper.runAfterDelay(t, () -> {
				if (ship[0] == null) {
					return;
				}
				long elapsed = level.getGameTime() - began[0];
				if (!halfway[0] && elapsed >= Ships.SINK_TICKS / 2) {
					halfway[0] = true;
					int height = span[1] - span[0] + 1;
					int gone = 0;
					for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
						int y = e.getKey().getY();
						boolean stands = MaterialRules.matches(level.getBlockState(e.getKey()), e.getValue());
						if (y > span[1] - height * 0.4) {
							helper.assertFalse(stands, elapsed + " ticks into the sinking a block in the ship's upper 40% still stands at " + e.getKey() + ": "
								+ e.getValue() + " (the ship from y " + span[0] + " to " + span[1] + ")");
							gone++;
						} else if (y < span[0] + height * 0.4) {
							helper.assertTrue(stands, elapsed + " ticks into the sinking a block in the ship's lower 40% is gone at " + e.getKey() + ": "
								+ level.getBlockState(e.getKey()) + " (the ship from y " + span[0] + " to " + span[1] + ")");
						}
					}
					helper.assertTrue(gone > 0, "the ship's upper 40% had no blocks");
					int going = Ships.sinking(level).size();
					Ships.Data.get(level).roundTrip(level.registryAccess());
					helper.assertTrue(Ships.sinking(level).size() == going, "a save and load mid-sinking turned " + going + " sinking ships into "
						+ Ships.sinking(level).size() + ": " + Ships.sinking(level));
					List<Ships.Sinking> mine = Ships.sinking(level).stream().filter(s -> s.ship().equals(ship[0])).toList();
					helper.assertTrue(mine.size() == 1 && mine.get(0).began() == began[0], "after a save and load mid-sinking the ship's sinking: " + mine
						+ " (it began at " + began[0] + ")");
					helper.assertTrue(Ships.of(level, hall).isEmpty(), "after a save and load the sinking ship is back at anchor");
					return;
				}
				if (halfway[0] && ended[0] < 0 && Ships.sinking(level).stream().noneMatch(s -> s.ship().equals(ship[0]))) {
					ended[0] = elapsed;
				}
			});
		}
		helper.runAfterDelay(2 + Ships.SINK_TICKS + 8, () -> {
			helper.assertTrue(halfway[0], "the halfway check never ran");
			helper.assertTrue(ended[0] == Ships.SINK_TICKS, "the ship sank in " + ended[0] + " ticks, not "
				+ Ships.SINK_TICKS + " (a save and load halfway)");
			for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
				BlockPos p = e.getKey();
				BlockState now = level.getBlockState(p);
				if (p.getY() <= ship[0].waterTop()) {
					helper.assertTrue(level.getFluidState(p).is(FluidTags.WATER), "where the hull lay below the waterline at " + p + " there is " + now + ", not the sea");
				} else {
					helper.assertTrue(now.isAir(), "where the ship stood above the waterline at " + p + " there is " + now + ", not air");
				}
			}
			AABB around = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).inflate(48);
			List<ItemEntity> diamonds = level.getEntitiesOfClass(ItemEntity.class, around, i -> i.getItem().is(Items.DIAMOND));
			helper.assertTrue(diamonds.isEmpty(), "the sunk ship's barrels and gunports spilled " + diamonds.size() + " diamonds onto the water");
			AliveWorkplace.LOG.info("QA 1115d: the ship ({} blocks, y {} to {}) sank top down in {} ticks across a save and load", blocks.size(), span[0], span[1], ended[0]);
			helper.succeed();
		});
	}
}
