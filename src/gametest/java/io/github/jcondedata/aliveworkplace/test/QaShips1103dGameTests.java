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
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Ships;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (tests/ships-1103d): the pirate ship's raft, its sinking's end, its sailing away and the beach's lowest reach
 * (ROADMAP 32.8, 32.21), written from what a player sees. On {@link PirateGameTests}'s coast: a beach strip along the
 * north (z 0 to 3, top at y 7) with the hall, a pool six deep to the south; the ship lies from x 2 to 28, z 10 to 18, its
 * bow west, so its raft goes off its north side at (15, 7, 7), or off its south side at (15, 7, 21) when the north's
 * water is taken.
 */
public class QaShips1103dGameTests {
	private static final String AREA = "aliveworkplace_test:tall_area";
	private static final BlockPos HALL = new BlockPos(1, 8, 1);
	private static final BlockPos SHIP = new BlockPos(15, 7, 14);
	/** The raft's middle log off the north side, and off the south side. */
	private static final BlockPos NORTH_RAFT = new BlockPos(15, 7, 7);
	private static final BlockPos SOUTH_RAFT = new BlockPos(15, 7, 21);
	private static final long DAY = 24000;
	private static final ResourceLocation PIRATES = AliveWorkplace.id("drowned_pirates");

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/** {@link PirateGameTests}'s coast: the beach and the sea, a Town hall in a beach biome, radius 16, four villagers. */
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

	/** A Town by the sea at night, with the pirate ship anchored off it. */
	private static Lairs.Lair anchored(GameTestHelper helper, BlockPos hall, long day) {
		ServerLevel level = helper.getLevel();
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		at(level, day, 13000);
		Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(SHIP), PIRATES);
		helper.assertTrue(lair != null && lair.culture().equals(PIRATES) && Ships.of(level, hall).isPresent(), "no pirate ship anchored: " + lair);
		return lair;
	}

	private static void captainFalls(ServerLevel level, Lairs.Lair lair) {
		((Mob) level.getEntity(lair.captain())).hurt(level.damageSources().fellOutOfWorld(), 1000f);
	}

	/**
	 * The raft round {@code middle} (its middle log, relative): a chest with the pirates' loot on top, exactly nine spruce
	 * logs in a 3 x 3 at the waterline, each with the sea under it, and not one log more within two blocks of it.
	 */
	private static void assertRaft(GameTestHelper helper, BlockPos middle) {
		ServerLevel level = helper.getLevel();
		BlockPos chest = helper.absolutePos(middle.above());
		helper.assertTrue(level.getBlockEntity(chest) instanceof ChestBlockEntity c && c.getLootTable() != null
			&& c.getLootTable().location().equals(AliveWorkplace.id("chests/pirate_ship")), "no loot chest on the raft at " + chest + ": " + level.getBlockState(chest));
		List<BlockPos> logs = new ArrayList<>();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos p = helper.absolutePos(middle.offset(dx, dy, dz));
					if (level.getBlockState(p).is(Blocks.SPRUCE_LOG)) {
						logs.add(middle.offset(dx, dy, dz));
					}
				}
			}
		}
		helper.assertTrue(logs.size() == 9, "the raft isn't nine logs (3 x 3) but " + logs.size() + ": " + logs);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos log = middle.offset(dx, 0, dz);
				helper.assertTrue(logs.contains(log), "no raft log at " + log + " (the raft's logs: " + logs + ")");
				helper.assertTrue(level.getFluidState(helper.absolutePos(log.below())).is(FluidTags.WATER),
					"the raft's log at " + log + " isn't on the water: " + level.getBlockState(helper.absolutePos(log.below())));
			}
		}
	}

	/**
	 * Captain Silas Brine falls: his chest is on a raft of exactly 3 x 3 spruce logs on the water off the ship's north side
	 * (no log beyond it). The ship goes down over exactly {@link Ships#SINK_TICKS} ticks: one tick short it is still
	 * sinking, and the tick it reaches them it is gone, every block of it, and no longer sinking.
	 */
	//$ gametest_ticks_batch AREA '320' '"qaShipsRaftSink"'
	@GameTest(template = AREA, timeoutTicks = 320, batch = "qaShipsRaftSink")
	public void theRaftIsThreeByThreeAndTheShipIsGoneExactlyWhenItsSinkingEnds(GameTestHelper helper) {
		BlockPos hall = coast(helper);
		ServerLevel level = helper.getLevel();
		Ships.Placed[] ship = {null};
		long[] began = {-1};
		long[] ended = {-1};
		List<Map<BlockPos, BlockState>> blocks = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = anchored(helper, hall, 110);
			ship[0] = Ships.of(level, hall).orElseThrow();
			blocks.add(Ships.blocks(level, ship[0]));
			captainFalls(level, lair);
			began[0] = level.getGameTime();
			List<Ships.Sinking> mine = Ships.sinking(level).stream().filter(s -> s.ship().equals(ship[0])).toList();
			helper.assertTrue(mine.size() == 1 && mine.get(0).began() == began[0], "the ship isn't sinking from now: " + Ships.sinking(level));
			assertRaft(helper, NORTH_RAFT);
		});
		// Every tick after: the first tick it is no longer sinking, how long it took, and nothing of it left.
		for (int t = 3; t <= 2 + Ships.SINK_TICKS + 5; t++) {
			helper.runAfterDelay(t, () -> {
				if (ship[0] == null || ended[0] >= 0 || Ships.sinking(level).stream().anyMatch(s -> s.ship().equals(ship[0]))) {
					return;
				}
				ended[0] = level.getGameTime() - began[0];
				for (Map.Entry<BlockPos, BlockState> e : blocks.get(0).entrySet()) {
					helper.assertFalse(!e.getValue().isAir() && MaterialRules.matches(level.getBlockState(e.getKey()), e.getValue()),
						"the sinking ended " + ended[0] + " ticks in with a ship block still at " + e.getKey() + ": " + level.getBlockState(e.getKey()));
				}
			});
		}
		helper.runAfterDelay(2 + Ships.SINK_TICKS + 8, () -> {
			helper.assertTrue(ended[0] == Ships.SINK_TICKS, "the ship's sinking ended " + ended[0] + " ticks after the captain fell, not " + Ships.SINK_TICKS);
			helper.assertTrue(level.getBlockEntity(helper.absolutePos(NORTH_RAFT.above())) instanceof ChestBlockEntity, "the raft's chest went with the ship");
			helper.succeed();
		});
	}

	/**
	 * The water off the ship's north side is open but for one block standing on it in the raft's east column: the raft is
	 * never built under it, but off the south side, all nine logs on the water; the block stays where it was.
	 */
	//$ gametest_ticks_batch AREA '320' '"qaShipsRaftTaken"'
	@GameTest(template = AREA, timeoutTicks = 320, batch = "qaShipsRaftTaken")
	public void theRaftGoesWhereAllNineLogsLieOnOpenWater(GameTestHelper helper) {
		BlockPos hall = coast(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = anchored(helper, hall, 120);
			Ships.Placed ship = Ships.of(level, hall).orElseThrow();
			BlockPos taken = NORTH_RAFT.offset(1, 1, 0); // on the water, over the north raft's east-middle log
			helper.setBlock(taken, Blocks.OAK_PLANKS);
			captainFalls(level, lair);
			helper.assertTrue(Ships.of(level, hall).isEmpty(), "the ship didn't go when its captain fell");
			helper.assertFalse(level.getBlockState(helper.absolutePos(NORTH_RAFT.above())).is(Blocks.CHEST)
				|| level.getBlockState(helper.absolutePos(NORTH_RAFT)).is(Blocks.SPRUCE_LOG),
				"the raft was built off the north side, under the block standing on the water at " + taken);
			helper.assertTrue(level.getBlockState(helper.absolutePos(taken)).is(Blocks.OAK_PLANKS)
				&& level.getFluidState(helper.absolutePos(taken.below())).is(FluidTags.WATER), "the block on the water or the water under it changed: "
				+ level.getBlockState(helper.absolutePos(taken)) + " over " + level.getBlockState(helper.absolutePos(taken.below())));
			assertRaft(helper, SOUTH_RAFT);
			helper.runAfterDelay(Ships.SINK_TICKS + 10, () -> {
				helper.assertTrue(Ships.sinking(level).stream().noneMatch(s -> s.ship().equals(ship)), "the ship is still sinking");
				helper.succeed();
			});
		});
	}

	/**
	 * The lair leaves the village in peace (32.21): the ship weighs anchor and sails off, and that is marked to be saved, so
	 * a restart right after doesn't bring it back to anchor; after a save and load it is still on its way, and then gone.
	 */
	//$ gametest_ticks_batch AREA '320' '"qaShipsSailAway"'
	@GameTest(template = AREA, timeoutTicks = 320, batch = "qaShipsSailAway")
	public void aShipThatSailsAwayIsSavedAsGone(GameTestHelper helper) {
		BlockPos hall = coast(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = anchored(helper, hall, 130);
			Ships.Placed ship = Ships.of(level, hall).orElseThrow();
			Ships.Data data = Ships.Data.get(level);
			data.setDirty(false); // as just after an autosave
			Lairs.leave(level, lair);
			helper.assertTrue(Ships.of(level, hall).isEmpty() && Ships.sinking(level).stream().anyMatch(s -> s.ship().equals(ship)), "the ship didn't sail off");
			helper.assertTrue(data.isDirty(), "the ship sailed off but the ships' data isn't marked to be saved: after a restart it's back at anchor");
			helper.assertFalse(level.getBlockState(helper.absolutePos(NORTH_RAFT.above())).is(Blocks.CHEST), "a ship that sailed off left a raft");
			data.roundTrip(level.registryAccess());
			helper.assertTrue(Ships.of(level, hall).isEmpty() && Ships.sinking(level).stream().anyMatch(s -> s.ship().equals(ship)),
				"after a save and load the ship that sailed off: anchored " + Ships.of(level, hall) + ", going " + Ships.sinking(level));
			helper.runAfterDelay(Ships.SINK_TICKS + 10, () -> {
				helper.assertTrue(Ships.sinking(level).stream().noneMatch(s -> s.ship().equals(ship)) && Ships.of(level, hall).isEmpty(), "the ship is still going");
				helper.succeed();
			});
		});
	}

	/**
	 * A village's beach may lie as far as 24 blocks below its hall, no further: ground at the water's edge 24 below the
	 * hall is its beach, 25 below it isn't.
	 */
	//$ gametest_ticks_batch '"aliveworkplace_test:huge_area"' '40' '"qaShipsBeachLow"'
	@GameTest(template = "aliveworkplace_test:huge_area", timeoutTicks = 40, batch = "qaShipsBeachLow")
	public void aBeachTwentyFourBelowTheHallIsStillItsBeach(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 2;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		// Open air over the village's columns, a pool of one water block at y 2 with dry ground round it (its top at y 2).
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int y = 3; y <= 40; y++) {
					level.setBlock(helper.absolutePos(new BlockPos(10 + dx, y, 10 + dz)), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		BlockPos water = new BlockPos(12, 2, 10);
		helper.setBlock(water.below(), Blocks.STONE);
		helper.setBlock(water, Blocks.WATER);
		for (BlockPos ground : List.of(water.west(), water.east(), water.north(), water.south())) {
			helper.setBlock(ground, Blocks.STONE);
		}
		BlockPos from = helper.absolutePos(water);
		int standY = helper.absolutePos(new BlockPos(0, 3, 0)).getY();
		helper.runAfterDelay(1, () -> {
			helper.assertTrue(level.getFluidState(from).is(FluidTags.WATER), "the pool's water ran off");
			BlockPos hall = helper.absolutePos(new BlockPos(10, 3 + 24, 10));
			BlockPos beach = Ships.beach(level, hall, from);
			helper.assertTrue(beach != null && beach.getY() == standY && beach.getY() == hall.getY() - 24,
				"ground at the water's edge 24 below the hall isn't its beach: " + beach + " (hall " + hall + ")");
			BlockPos higher = hall.above();
			BlockPos none = Ships.beach(level, higher, from);
			helper.assertTrue(none == null, "ground 25 below the hall is its beach: " + none + " (hall " + higher + ")");
			helper.succeed();
		});
	}
}
