package io.github.jcondedata.aliveworkplace.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * QA for drowned pirates (ROADMAP 32.8) and the threat level (32.21), written from the spec for what the feature's own
 * tests leave out: the {@code /workplace threat} command is for operators only; a ship anchors on water 5 deep but not
 * 4; a City (above Town) gets pirates and a Hamlet doesn't; the captain has exactly +40 health; at threat level 3 a
 * pirate raid brings a quarter more crew and every one of them still wades ashore at the beach nearest the ship; a block
 * a player swapped into the ship stays when it sinks; At peace, a Pirate Ship at anchor sails at the next dawn and none
 * comes after.
 */
public class QaPiratesThreatLevelGameTests {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	/** On the beach (tall_area) and on the floor (big_area). */
	private static final BlockPos COAST_HALL = new BlockPos(1, 8, 1);
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	/** The column the ship anchors in, and the beach nearest it (the sea as in PirateGameTests). */
	private static final BlockPos SHIP = new BlockPos(15, 7, 14);
	private static final BlockPos BEACH = new BlockPos(15, 8, 3);
	private static final long DAY = 24000;
	private static final ResourceLocation PIRATES = AliveWorkplace.id("drowned_pirates");

	static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/**
	 * A beach along the north (z 0 to 3, its top at y 7) with a hall on it in a beach biome, and to the south a pool walled
	 * with glass whose water runs from y {@code floor} to 7 (stone under it); the config, clock and threats go back after.
	 */
	static BlockPos coast(GameTestHelper helper, int floor) {
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
						: x == 0 || x == 29 || z == 29 ? Blocks.GLASS.defaultBlockState() : y < floor ? Blocks.STONE.defaultBlockState() : Blocks.WATER.defaultBlockState();
					level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), block, 2);
				}
			}
		}
		helper.setBlock(COAST_HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(COAST_HALL);
		String old = level.getBiome(hall).unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
		fill(level, hall, "minecraft:beach");
		Conditions.forgetCoasts();
		ThreatData.get(level).forgetPast(hall);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(64), m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(VillageRaids.TAG))
				.forEach(Mob::discard);
			VillageRaids.forget();
			BanditCamps.forget(level);
			Ships.forget(level);
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			Threats.DISABLED = off;
			Expansions.openForTests = open;
			Lairs.ENABLED = lairs;
			level.setDayTime(time);
			fill(level, hall, old);
			Conditions.forgetCoasts();
			new WorkplaceConfig().apply();
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

	static void rank(ServerLevel level, BlockPos hall, VillageRanks.Rank rank) {
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(rank);
	}

	static Culture pirates(GameTestHelper helper) {
		Optional<Culture> culture = Threats.get(PIRATES);
		helper.assertTrue(culture.isPresent(), "drowned_pirates.json isn't loaded");
		return culture.get();
	}

	/** How often, in 200 rolls, the pirates are the ones that make camp. */
	private static int pirateCamps(ServerLevel level, BlockPos hall) {
		int n = 0;
		for (int i = 0; i < 200; i++) {
			if (Lairs.comer(level, hall, true, RandomSource.create(9000L + i)).equals(Optional.of(PIRATES))) {
				n++;
			}
		}
		return n;
	}

	private static double horizontal(BlockPos a, double x, double z) {
		double dx = a.getX() + 0.5 - x;
		double dz = a.getZ() + 0.5 - z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static CommandSourceStack source(ServerLevel level, BlockPos at, int permission, List<String> said) {
		CommandSource out = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message.getString());
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		return new CommandSourceStack(out, Vec3.atCenterOf(at.south(2)), Vec2.ZERO, level, permission, "test", Component.literal("test"), level.getServer(), null);
	}

	/**
	 * {@code /workplace threat} is an operator's order: a player without permission (level 0) or a level-1 player can't
	 * run start, lair, disaster, end or clear (the command isn't there for them and nothing starts); an operator (2) can.
	 */
	//$ gametest_ticks_batch BIG '60' '"qaThreatCommandOp"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qaThreatCommandOp")
	public void onlyAnOperatorMayCallAThreat(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			VillageRaids.forget();
			BanditCamps.forget(level);
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		helper.runAfterDelay(2, () -> {
			at(level, 84, 15000);
			List<String> said = new ArrayList<>();
			for (int permission : new int[] {0, 1}) {
				CommandSourceStack player = source(level, hall, permission, said);
				for (String cmd : List.of("workplace threat start bandits", "workplace threat lair bandits", "workplace threat disaster aliveworkplace:drought",
					"workplace threat end", "workplace threat clear")) {
					boolean refused;
					try {
						level.getServer().getCommands().getDispatcher().execute(cmd, player);
						refused = false;
					} catch (CommandSyntaxException e) {
						refused = true;
					}
					helper.assertTrue(refused, "a player of permission " + permission + " ran /" + cmd);
				}
				helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty() && Lairs.near(level, hall).isEmpty()
					&& ((VillageHallBlockEntity) level.getBlockEntity(hall)).disaster() == null, "a non-operator's command started something");
			}
			CommandSourceStack op = source(level, hall, 2, said);
			int sent;
			try {
				sent = level.getServer().getCommands().getDispatcher().execute("workplace threat start bandits", op);
			} catch (CommandSyntaxException e) {
				throw new IllegalStateException("an operator can't run /workplace threat start: " + e.getMessage());
			}
			helper.assertTrue(sent > 0 && ThreatData.get(level).raid(hall).isPresent(), "an operator's raid didn't start: " + said);
			helper.succeed();
		});
	}

	/** The ship anchors on water at least 5 deep: a sea exactly 5 deep fits it, one 4 deep doesn't (the culture's own sea). */
	//$ gametest_ticks_batch TALL '40' '"qaPiratesDepth5"'
	@GameTest(template = TALL, timeoutTicks = 40, batch = "qaPiratesDepth5")
	public void aShipAnchorsOnWaterFiveDeep(GameTestHelper helper) {
		BlockPos hall = coast(helper, 3); // water y 3 to 7: 5 deep
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Culture.Sea sea = pirates(helper).lair().get().sea().orElseThrow();
			var template = level.getStructureManager().get(AliveWorkplace.id("camp/pirate_ship")).orElseThrow();
			BlockPos top = Ships.waterTop(level, helper.absolutePos(SHIP));
			helper.assertTrue(Ships.depth(level, top) == 5, "the sea isn't 5 deep: " + Ships.depth(level, top));
			helper.assertTrue(Ships.fits(level, top, hall, sea, template), "a ship doesn't fit on water 5 deep");
			helper.succeed();
		});
	}

	/** ... and on water 4 deep it doesn't. */
	//$ gametest_ticks_batch TALL '40' '"qaPiratesDepth4"'
	@GameTest(template = TALL, timeoutTicks = 40, batch = "qaPiratesDepth4")
	public void aShipWontAnchorOnWaterFourDeep(GameTestHelper helper) {
		BlockPos hall = coast(helper, 4); // water y 4 to 7: 4 deep
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Culture.Sea sea = pirates(helper).lair().get().sea().orElseThrow();
			var template = level.getStructureManager().get(AliveWorkplace.id("camp/pirate_ship")).orElseThrow();
			BlockPos top = Ships.waterTop(level, helper.absolutePos(SHIP));
			helper.assertTrue(Ships.depth(level, top) == 4, "the sea isn't 4 deep: " + Ships.depth(level, top));
			helper.assertFalse(Ships.fits(level, top, hall, sea, template), "a ship fits on water 4 deep");
			helper.succeed();
		});
	}

	/**
	 * Town and up: a coastal City gets pirates and a Hamlet doesn't. Captain Silas Brine has exactly +40 health (60). At
	 * threat level 3 every pirate of the raid comes ashore at the beach nearest the ship. (How many come at level 3 is in
	 * QaPiratesThreatLevelBugTests: a lair's raid gets no quarter more.)
	 */
	//$ gametest_ticks_batch TALL '100' '"qaPiratesCity"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaPiratesCity")
	public void aCityGetsPiratesAndAtLevelThreeMoreWadeAshore(GameTestHelper helper) {
		BlockPos hall = coast(helper, 2);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.HAMLET);
			helper.assertTrue(pirateCamps(level, hall) == 0, "pirates make camp by a coastal Hamlet");
			rank(level, hall, VillageRanks.Rank.CITY);
			helper.assertTrue(pirateCamps(level, hall) > 0, "no pirates by a coastal City");

			at(level, 86, 13000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(SHIP), PIRATES);
			helper.assertTrue(lair != null && lair.culture().equals(PIRATES), "no pirate ship by the City: " + lair);
			Mob captain = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(captain != null && captain.getType() == EntityType.DROWNED, "no drowned captain: " + captain);
			double base = EntityType.DROWNED.create(level).getMaxHealth();
			helper.assertTrue(Math.abs(captain.getMaxHealth() - (base + 40)) < 0.01, "the captain's health isn't +40: " + captain.getMaxHealth() + " on a base of " + base);

			WorkplaceConfig.parse("{\"threatLevel\": 3}").apply();
			BlockPos beach = Ships.beach(level, hall, lair.pos());
			helper.assertTrue(helper.absolutePos(BEACH).equals(beach), "the beach nearest the ship: " + beach);
			at(level, 86, 15000);
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 52, 0, Double.NaN, pirates(helper), RandomSource.create(5L));
			helper.assertTrue(raid != null, "no pirate raid at level 3");
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raiders.size() >= 8, "a pirate raid at level 3 sent fewer than a level-2 one: " + raiders.size());
			for (Mob m : raiders) {
				helper.assertTrue(horizontal(beach, m.getX(), m.getZ()) <= 5 && m.getZ() > beach.getZ(),
					"a pirate came ashore away from the beach nearest the ship: " + m.getType() + " at " + m.blockPosition());
			}
			helper.succeed();
		});
	}

	/**
	 * A captain is his mob plus his file's extra health even when vanilla made him a leader zombie (B579): on a hard,
	 * long-played spot {@code finalizeSpawn} now and then multiplies a zombie's max health by 2 to 5, and CI once saw a
	 * pirate captain at 178.7 instead of 60. A drowned that vanilla made a leader at the hardest local difficulty comes
	 * out at its base + 40 once he is made a captain.
	 */
	//$ gametest_ticks_batch BIG '40' '"qaCaptainLeaderZombie"'
	@GameTest(template = BIG, timeoutTicks = 40, batch = "qaCaptainLeaderZombie")
	public void aCaptainVanillaMadeALeaderZombieHasJustHisFortyMore(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		double base = EntityType.DROWNED.create(level).getMaxHealth();
		// Hard, a long-played chunk under a full moon: the special multiplier is 1, so 1 drowned in 20 is a leader.
		DifficultyInstance hardest = new DifficultyInstance(Difficulty.HARD, 3_600_000L, 3_600_000L, 1f);
		helper.assertTrue(hardest.getSpecialMultiplier() == 1f, "not the hardest local difficulty: " + hardest.getSpecialMultiplier());
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 2, 4.5));
		Mob leader = null;
		for (int i = 0; i < 2000 && leader == null; i++) {
			Mob drowned = EntityType.DROWNED.create(level);
			drowned.moveTo(at.x, at.y, at.z, 0f, 0f);
			// (a grown drowned with no jockey: nothing else is spawned)
			drowned.finalizeSpawn(level, hardest, MobSpawnType.EVENT, new Zombie.ZombieGroupData(false, false));
			if (drowned.getMaxHealth() > base + 0.01) {
				leader = drowned;
			} else {
				drowned.discard();
			}
		}
		helper.assertTrue(leader != null, "vanilla made no leader drowned in 2000 at the hardest difficulty");
		Threats.captainHealth(leader, AliveWorkplace.id("bandit_chief"), 40);
		helper.assertTrue(Math.abs(leader.getMaxHealth() - (base + 40)) < 0.01,
			"a leader drowned made a captain has " + leader.getMaxHealth() + " health, not " + (base + 40));
		helper.assertTrue(leader.getHealth() == leader.getMaxHealth(), "the captain isn't healed to his max: " + leader.getHealth());
		leader.discard();
		helper.succeed();
	}

	/**
	 * Threat level 3 is "a quarter more raiders": a raid from a lair too (B383). With threat level 3 set before the Pirate
	 * Ship anchors, the ship's raid on a coastal Town of 52 is a quarter more than the 8 it sends at level 2 (10): the
	 * band at home is a quarter bigger on hard.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaBugLevel3Lair"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaBugLevel3Lair")
	public void atThreatLevelThreeALairsRaidBringsAQuarterMore(GameTestHelper helper) {
		BlockPos hall = coast(helper, 2);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			WorkplaceConfig.parse("{\"threatLevel\": 3}").apply();
			rank(level, hall, VillageRanks.Rank.TOWN);
			at(level, 90, 13000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(SHIP), PIRATES);
			helper.assertTrue(lair != null, "no pirate ship");
			at(level, 90, 15000);
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 52, 0, Double.NaN, pirates(helper), RandomSource.create(5L));
			helper.assertTrue(raid != null, "no pirate raid at level 3");
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raiders.size() == 10, "a lair's raid of 8 at threat level 3 sent " + raiders.size() + " (the band at home: " + lair.strength() + ")");
			helper.succeed();
		});
	}

	/**
	 * A block that isn't the ship's any more stays when it sinks: a player swapped one of the deck's own blocks for a
	 * diamond block, and when the captain falls that diamond block is still there after the sinking.
	 */
	//$ gametest_ticks_batch TALL '320' '"qaPiratesSwapped"'
	@GameTest(template = TALL, timeoutTicks = 320, batch = "qaPiratesSwapped")
	public void aBlockSwappedIntoTheShipStaysWhenItSinks(GameTestHelper helper) {
		BlockPos hall = coast(helper, 2);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.TOWN);
			at(level, 88, 13000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(SHIP), PIRATES);
			helper.assertTrue(lair != null, "no pirate ship");
			Ships.Placed ship = Ships.of(level, hall).orElseThrow();
			Map<BlockPos, BlockState> blocks = Ships.blocks(level, ship);
			// A solid ship block above the waterline with no block entity: the player's swap.
			BlockPos swapped = blocks.entrySet().stream()
				.filter(e -> !e.getValue().isAir() && e.getKey().getY() > ship.waterTop() && level.getBlockEntity(e.getKey()) == null
					&& e.getValue().isSolidRender(level, e.getKey()) && MaterialRules.matches(level.getBlockState(e.getKey()), e.getValue()))
				.map(Map.Entry::getKey).findFirst().orElse(null);
			helper.assertTrue(swapped != null, "no solid ship block above the water");
			level.setBlockAndUpdate(swapped, Blocks.DIAMOND_BLOCK.defaultBlockState());
			((Mob) level.getEntity(lair.captain())).hurt(level.damageSources().fellOutOfWorld(), 1000f);
			helper.assertTrue(Ships.sinking(level).size() == 1, "the ship isn't sinking");
			helper.runAfterDelay(Ships.SINK_TICKS + 10, () -> {
				helper.assertTrue(level.getBlockState(swapped).is(Blocks.DIAMOND_BLOCK), "the player's block in the ship went with it: " + level.getBlockState(swapped));
				for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
					helper.assertFalse(!e.getValue().isAir() && MaterialRules.matches(level.getBlockState(e.getKey()), e.getValue()),
						"a ship block is left at " + e.getKey());
				}
				helper.succeed();
			});
		});
	}

	/** A random whose every float is 0: any chance above 0 comes. */
	private static RandomSource always() {
		return new LegacyRandomSource(7L) {
			@Override
			public float nextFloat() {
				return 0f;
			}

			@Override
			public double nextDouble() {
				return 0d;
			}
		};
	}

	/**
	 * At peace with a Pirate Ship at anchor: the same day it stays; at the next dawn the lair leaves (captain and crew
	 * gone) and the ship weighs anchor, its blocks gone from the sea once it has sailed; no new lair comes after, however
	 * the dice fall, and the village's chance of a lair is 0.
	 */
	//$ gametest_ticks_batch TALL '320' '"qaPiratesAtPeace"'
	@GameTest(template = TALL, timeoutTicks = 320, batch = "qaPiratesAtPeace")
	public void atPeaceThePirateShipSailsAtDawnAndNoneComes(GameTestHelper helper) {
		BlockPos hall = coast(helper, 2);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		Leftovers.after(helper, () -> {
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity e) {
				e.setAtPeace(false, -1);
			}
		});
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.TOWN);
			at(level, 92, 13000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(SHIP), PIRATES);
			helper.assertTrue(lair != null, "no pirate ship");
			Ships.Placed ship = Ships.of(level, hall).orElseThrow();
			Map<BlockPos, BlockState> blocks = Ships.blocks(level, ship);
			helper.assertTrue(Lairs.band(level, lair).size() == 9, "the captain and his crew of 8: " + Lairs.band(level, lair).size());
			entity.setAtPeace(true, 92);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent() && Ships.of(level, hall).isPresent(), "the ship left before dawn");
			at(level, 93, 100);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the pirates' lair is still there at dawn At peace");
			helper.assertTrue(Lairs.band(level, lair).isEmpty() && level.getEntity(lair.captain()) == null, "the crew stayed: " + Lairs.band(level, lair).size());
			helper.assertTrue(Ships.of(level, hall).isEmpty(), "the ship is still at anchor by a village At peace");
			helper.runAfterDelay(Ships.SINK_TICKS + 10, () -> {
				for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
					helper.assertFalse(!e.getValue().isAir() && MaterialRules.matches(level.getBlockState(e.getKey()), e.getValue()),
						"a block of the ship that sailed is left at " + e.getKey() + ": " + level.getBlockState(e.getKey()));
				}
				for (long day = 93; day < 97; day++) {
					at(level, day, 6000);
					Lairs.round(level, hall, true, always());
				}
				helper.assertTrue(Lairs.near(level, hall).isEmpty() && Ships.of(level, hall).isEmpty() && Lairs.dailyChance(level, hall) == 0,
					"a new pirate ship came At peace");
				helper.succeed();
			});
		});
	}
}
