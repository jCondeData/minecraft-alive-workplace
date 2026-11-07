package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Conditions;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Risings;
import io.github.jcondedata.aliveworkplace.threat.Ships;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane D, 2026-11-06): a mutation-driven gap hunt in {@link Ships} and {@link Risings}, written from pirates.md
 * ("the chest is put on a 3 x 3 raft of spruce logs on the water ... past the bow, or off a side if the bow's water is
 * taken") and desert.md ("while he lives, a husk that falls within 24 blocks of him gets up again ... the same mob as it
 * was (its gear, its raid, its role)").
 * <ul>
 * <li>A block on the water over either outer row of the north raft spot (the row nearest the ship and the row
 *     farthest) keeps the raft off it: all nine logs go off the south side, on the water, and the block stays. Kills
 *     the survivor in {@code Ships.raftFits} that checked only two of the three rows. A block one row past the raft
 *     doesn't move it.</li>
 * <li>The crew's beach at the village's edge (exactly the hall radius out) counts, one block beyond doesn't.</li>
 * <li>Only a marked Pharaoh raises: a husk killed beside an ordinary husk stays down, and the Pharaoh himself, killed,
 *     doesn't rise.</li>
 * <li>A risen husk is the same mob: its name over its head and its tags (its role) come back with it.</li>
 * </ul>
 * The coast is {@link QaShips1103dGameTests}'s: a beach strip along the north (z 0 to 3, top at y 7) with the hall, a
 * pool six deep to the south; the ship lies from x 2 to 28, z 10 to 18, its bow west, so its raft goes off its north
 * side at (15, 7, 7), or off its south side at (15, 7, 21) when the north's water is taken.
 */
public class QaShipsRisings1106dGameTests {
	private static final String AREA = "aliveworkplace_test:tall_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 8, 1);
	private static final BlockPos SHIP = new BlockPos(15, 7, 14);
	private static final BlockPos NORTH_RAFT = new BlockPos(15, 7, 7);
	private static final BlockPos SOUTH_RAFT = new BlockPos(15, 7, 21);
	private static final long DAY = 24000;
	private static final ResourceLocation PIRATES = AliveWorkplace.id("drowned_pirates");
	private static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");

	// The pirates' coast (as QaShips1103dGameTests).

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

	private static Lairs.Lair anchored(GameTestHelper helper, BlockPos hall, long day) {
		ServerLevel level = helper.getLevel();
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		level.setDayTime((day - 1) * DAY + 13000);
		Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(SHIP), PIRATES);
		helper.assertTrue(lair != null && lair.culture().equals(PIRATES) && Ships.of(level, hall).isPresent(), "setup: no pirate ship anchored: " + lair);
		return lair;
	}

	/** The raft round {@code middle}: the pirates' loot chest on top, exactly nine spruce logs in a 3 x 3, each on the water. */
	private static void assertRaft(GameTestHelper helper, BlockPos middle) {
		ServerLevel level = helper.getLevel();
		BlockPos chest = helper.absolutePos(middle.above());
		helper.assertTrue(level.getBlockEntity(chest) instanceof ChestBlockEntity c && c.getLootTable() != null
			&& c.getLootTable().location().equals(AliveWorkplace.id("chests/pirate_ship")), "expected the loot chest on the raft at " + middle.above()
			+ ", found " + level.getBlockState(chest));
		List<BlockPos> logs = new ArrayList<>();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = -1; dy <= 1; dy++) {
					if (level.getBlockState(helper.absolutePos(middle.offset(dx, dy, dz))).is(Blocks.SPRUCE_LOG)) {
						logs.add(middle.offset(dx, dy, dz));
					}
				}
			}
		}
		helper.assertTrue(logs.size() == 9, "expected the raft 9 logs (3 x 3), found " + logs.size() + ": " + logs);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos log = middle.offset(dx, 0, dz);
				helper.assertTrue(logs.contains(log), "expected a raft log at " + log + ", the raft's logs: " + logs);
				helper.assertTrue(level.getFluidState(helper.absolutePos(log.below())).is(FluidTags.WATER),
					"expected the raft's log at " + log + " on the water, found under it " + level.getBlockState(helper.absolutePos(log.below())));
			}
		}
	}

	/**
	 * A player's block stands on the water over one log of the north raft spot's row {@code row} (-1: the row farthest
	 * from the ship, +1: the row nearest it). The captain falls: the north spot's water is taken, so the raft goes off the
	 * south side, all nine logs on the water, and the player's block and the sea under it are untouched.
	 */
	private static void raftAvoidsABlockOnRow(GameTestHelper helper, int row) {
		BlockPos hall = coast(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = anchored(helper, hall, 120);
			BlockPos taken = NORTH_RAFT.offset(-1, 1, row); // on the water, over the north raft's west log of that row
			helper.setBlock(taken, Blocks.OAK_PLANKS);
			((Mob) level.getEntity(lair.captain())).hurt(level.damageSources().fellOutOfWorld(), 1000f);
			helper.assertTrue(Ships.of(level, hall).isEmpty(), "expected the ship to go when its captain fell, it still lies at anchor");
			helper.assertFalse(level.getBlockState(helper.absolutePos(NORTH_RAFT.above())).is(Blocks.CHEST)
				|| level.getBlockState(helper.absolutePos(NORTH_RAFT)).is(Blocks.SPRUCE_LOG),
				"expected no raft off the north side (a block stands on its water at " + taken + "), found the raft built there");
			helper.assertTrue(level.getBlockState(helper.absolutePos(taken)).is(Blocks.OAK_PLANKS)
				&& level.getFluidState(helper.absolutePos(taken.below())).is(FluidTags.WATER), "expected the player's block and the sea under it untouched, found "
				+ level.getBlockState(helper.absolutePos(taken)) + " over " + level.getBlockState(helper.absolutePos(taken.below())));
			assertRaft(helper, SOUTH_RAFT);
			AliveWorkplace.LOG.info("QA raft row {}: block at {} kept the raft off the north; nine logs off the south side", row, taken);
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '200' '"qaShipsRisingsRaftNear1106d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaShipsRisingsRaftNear1106d")
	public void aBlockOnTheRaftsRowNearestTheShipSendsTheRaftOffTheOtherSide(GameTestHelper helper) {
		raftAvoidsABlockOnRow(helper, 1);
	}

	//$ gametest_ticks_batch AREA '200' '"qaShipsRisingsRaftFar1106d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaShipsRisingsRaftFar1106d")
	public void aBlockOnTheRaftsRowFarthestFromTheShipSendsTheRaftOffTheOtherSide(GameTestHelper helper) {
		raftAvoidsABlockOnRow(helper, -1);
	}

	/**
	 * The other side of the boundary: a player's block on the water one row past the north raft spot (between it and
	 * the ship) and one a column past its east side don't take the raft's water, so the raft still goes off the north side, all nine logs on the water,
	 * and the block stays.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaShipsRisingsRaftBeside1106d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaShipsRisingsRaftBeside1106d")
	public void aBlockJustPastTheRaftsRowsLeavesTheRaftWhereItWas(GameTestHelper helper) {
		BlockPos hall = coast(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = anchored(helper, hall, 120);
			BlockPos beside = NORTH_RAFT.offset(0, 1, 2); // on the water one row past the raft's nearest row
			BlockPos east = NORTH_RAFT.offset(2, 1, 0); // on the water one column past the raft's east column
			helper.setBlock(beside, Blocks.OAK_PLANKS);
			helper.setBlock(east, Blocks.OAK_PLANKS);
			((Mob) level.getEntity(lair.captain())).hurt(level.damageSources().fellOutOfWorld(), 1000f);
			helper.assertTrue(Ships.of(level, hall).isEmpty(), "expected the ship to go when its captain fell, it still lies at anchor");
			assertRaft(helper, NORTH_RAFT);
			helper.assertTrue(level.getBlockState(helper.absolutePos(beside)).is(Blocks.OAK_PLANKS), "expected the player's block at " + beside
				+ " untouched, found " + level.getBlockState(helper.absolutePos(beside)));
			helper.assertTrue(level.getBlockState(helper.absolutePos(east)).is(Blocks.OAK_PLANKS), "expected the player's block at " + east
				+ " untouched, found " + level.getBlockState(helper.absolutePos(east)));
			AliveWorkplace.LOG.info("QA raft beside: blocks at {} and {} left the raft off the north side", beside, east);
			helper.succeed();
		});
	}

	/**
	 * "Where the crew lands: of the dry ground at the water's edge in the village, the spot nearest the ship": the
	 * village's reach is a radius of 8 round the hall, on a flat stone shore with one pool of sea east of it. With the
	 * water's edge exactly 8 blocks east of the hall (inside the village) the crew lands there; with the edge 9 blocks
	 * east (outside it) the village has no beach.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaShipsRisingsBeachEdge1106d"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaShipsRisingsBeachEdge1106d")
	public void aBeachAtTheVillagesEdgeIsItsBeachAndOneBeyondIsNot(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		VillageHalls.RADIUS = 8;
		BlockPos hall = helper.absolutePos(new BlockPos(10, 3, 15));
		BlockPos ship = helper.absolutePos(new BlockPos(28, 2, 15));
		shore(helper, 18);
		BlockPos edge = Ships.beach(level, hall, ship);
		helper.assertTrue(helper.absolutePos(new BlockPos(18, 3, 15)).equals(edge),
			"expected the crew to land on the water's edge 8 blocks east of the hall (the village's edge) at " + helper.absolutePos(new BlockPos(18, 3, 15))
				+ ", found " + edge);
		shore(helper, 19);
		BlockPos beyond = Ships.beach(level, hall, ship);
		helper.assertTrue(beyond == null, "expected no beach with the water's edge 9 blocks east (outside the village's 8), found " + beyond);
		AliveWorkplace.LOG.info("QA beach edge: landed at {} with the edge at 8 blocks; none at 9", edge);
		helper.succeed();
	}

	/** A flat stone shore at y 2 from x 2 to {@code last}, z 5 to 25, with one block of sea at ({@code last} + 1, 2, 15), walled in glass. */
	private static void shore(GameTestHelper helper, int last) {
		for (int x = 1; x <= 22; x++) {
			for (int z = 5; z <= 25; z++) {
				for (int y = 2; y <= 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), y == 2 && x >= 2 && x <= last ? Blocks.STONE : Blocks.AIR);
				}
			}
		}
		helper.setBlock(new BlockPos(last + 1, 2, 15), Blocks.WATER);
		helper.setBlock(new BlockPos(last + 2, 2, 15), Blocks.GLASS);
		helper.setBlock(new BlockPos(last + 1, 2, 14), Blocks.GLASS);
		helper.setBlock(new BlockPos(last + 1, 2, 16), Blocks.GLASS);
	}

	// The Pharaoh's curse.

	private static Culture desert(GameTestHelper helper) {
		Optional<Culture> culture = Threats.get(DESERT);
		helper.assertTrue(culture.isPresent(), "setup: expected desert_raiders.json loaded, found none");
		return culture.get();
	}

	private static void curseArea(GameTestHelper helper) {
		Leftovers.clear(helper);
		Risings.forget();
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Husk.class, helper.getBounds().inflate(8), h -> true).forEach(Mob::discard);
			Risings.forget();
		});
	}

	private static Husk husk(GameTestHelper helper, BlockPos at) {
		Husk husk = helper.spawn(EntityType.HUSK, at);
		husk.setNoAi(true);
		return husk;
	}

	private static List<Husk> risen(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Husk.class, helper.getBounds().inflate(2), h -> h.isAlive() && h.getTags().contains(Risings.RISEN_TAG));
	}

	/**
	 * Only a marked Pharaoh raises the fallen: a husk killed 3 blocks from an ordinary husk (no Pharaoh anywhere) stays
	 * down; and the Pharaoh himself, killed beside his husks, doesn't rise ("not he himself").
	 */
	//$ gametest_ticks_batch HUGE '160' '"qaShipsRisingsOnlyPharaoh1106d"'
	@GameTest(template = HUGE, timeoutTicks = 160, batch = "qaShipsRisingsOnlyPharaoh1106d")
	public void onlyALivingMarkedPharaohRaisesAndHeHimselfStaysDown(GameTestHelper helper) {
		curseArea(helper);
		husk(helper, new BlockPos(4, 2, 4));
		Husk plain = husk(helper, new BlockPos(7, 2, 4));
		plain.kill();
		helper.assertTrue(Risings.pending() == 0, "expected a husk killed beside an ordinary husk (no Pharaoh) to stay down, waiting to rise: " + Risings.pending());
		Husk pharaoh = husk(helper, new BlockPos(20, 2, 20));
		Risings.mark(pharaoh, desert(helper).captain().orElseThrow());
		helper.assertTrue(pharaoh.getTags().contains(Risings.RAISER_TAG), "setup: expected the Pharaoh marked as a raiser");
		husk(helper, new BlockPos(22, 2, 20));
		pharaoh.kill();
		helper.assertTrue(Risings.pending() == 0, "expected the Pharaoh himself to stay down, waiting to rise: " + Risings.pending());
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(risen(helper).isEmpty(), "expected no husk risen (no Pharaoh raised them), risen: " + risen(helper).size());
			AliveWorkplace.LOG.info("QA curse: a husk beside an ordinary husk and the Pharaoh himself both stayed down");
			helper.succeed();
		});
	}

	/**
	 * A desert raider (its name "Desert raider" over its head and a role tag; no raid is on, so it isn't a raider) falls 10 blocks from the
	 * Pharaoh: after three seconds it is up where it fell, with the same name shown and every tag it had.
	 */
	//$ gametest_ticks_batch HUGE '160' '"qaShipsRisingsSameMob1106d"'
	@GameTest(template = HUGE, timeoutTicks = 160, batch = "qaShipsRisingsSameMob1106d")
	public void aRisenRaiderComesBackWithItsNameAndItsRole(GameTestHelper helper) {
		curseArea(helper);
		Husk pharaoh = husk(helper, new BlockPos(4, 2, 4));
		Risings.mark(pharaoh, desert(helper).captain().orElseThrow());
		Husk raider = husk(helper, new BlockPos(14, 2, 4));
		raider.setCustomName(Component.literal("Desert raider"));
		raider.setCustomNameVisible(true);
		raider.addTag("aliveworkplace_qa_role_climber");
		Set<String> before = Set.copyOf(raider.getTags());
		raider.kill();
		helper.assertTrue(Risings.pending() == 1, "setup: expected the raider waiting to rise, waiting: " + Risings.pending());
		helper.runAfterDelay(80, () -> {
			List<Husk> up = risen(helper);
			helper.assertTrue(up.size() == 1, "expected the raider risen after 3 s, risen: " + up.size());
			Husk again = up.get(0);
			helper.assertTrue(again.getCustomName() != null && again.getCustomName().getString().equals("Desert raider") && again.isCustomNameVisible(),
				"expected 'Desert raider' over the risen husk's head, found " + again.getCustomName() + " (shown: " + again.isCustomNameVisible() + ")");
			helper.assertTrue(again.getTags().containsAll(before), "expected the risen husk to keep its tags " + before + ", found " + again.getTags());
			helper.assertTrue(again.blockPosition().closerThan(helper.absolutePos(new BlockPos(14, 2, 4)), 1.5),
				"expected it up where it fell, found at " + again.blockPosition());
			AliveWorkplace.LOG.info("QA curse: risen as '{}' with tags {}", again.getCustomName().getString(), again.getTags());
			helper.succeed();
		});
	}
}
