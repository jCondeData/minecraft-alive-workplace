package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.CampCleanup;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.SandRamps;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-04, round 3) of the player guide's pages "Bandit camps" and "Desert raiders", written from what
 * the pages promise rather than from the code, for what the earlier camp and desert tests leave unchecked: a Hamlet
 * never gets a camp and a Village does; 12% a day, rarer or more often by threat level and none at 0; the camp a player
 * sees (the expansion gate closed: a chief in iron, three or four men, the chat line naming how far and which way) and
 * its breaking (after a save, by a player: the chat line, the chronicle naming him, the band gone, five days' rest);
 * raids twice as likely and the village less safe while it stands; with Monster Raids off a camp still comes but no
 * raid; the Tomb Camp's chat line, its band called "Desert raider" and the Pharaoh's exactly 50 more health; the tomb
 * camp growing 10, 12, 14, 16 and no more through a save; the Pharaoh leading at a City but not a Town; the
 * sarcophagus's loot and its dune trim one time in five; {@code desert_raiders} off in {@code raiderCultures}; and with
 * Sieges off no sand ramp.
 */
public class QaCampsDesert1104d3GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(16, 1, 16);
	private static final long DAY = 24000;
	private static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");
	private static final String[] DIRECTIONS = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};

	// --- A village by a hall, as QaRaiderCulturesGameTests makes one.

	private static BlockPos village(GameTestHelper helper, String biome) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		Lairs.forget(level);
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		Set<String> off = Threats.DISABLED;
		boolean open = Expansions.openForTests;
		boolean lairs = Lairs.ENABLED;
		boolean raids = VillageRaids.ENABLED;
		boolean ruins = CampCleanup.RUINS;
		Lairs.ENABLED = true;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		String old = level.getBiome(hall).unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
		fill(level, hall, biome);
		ThreatData.get(level).forgetPast(hall);
		ThreatData.get(level).forgetClock(hall);
		Leftovers.after(helper, () -> {
			Expansions.openForTests = open;
			for (Lairs.Lair lair : Lairs.all(level)) {
				if (lair.hall().equals(hall)) {
					Lairs.leave(level, lair);
				}
			}
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(64), m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG)
				|| m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16), e -> true).forEach(ItemEntity::discard);
			VillageRaids.forget();
			BanditCamps.forget(level);
			Lairs.forget(level);
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
			Threats.DISABLED = off;
			Lairs.ENABLED = lairs;
			VillageRaids.ENABLED = raids;
			CampCleanup.RUINS = ruins;
			level.setDayTime(time);
			fill(level, hall, old);
		});
		for (int i = 0; i < 4; i++) {
			Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
			villager.setNoAi(true);
		}
		return hall;
	}

	private static void rank(ServerLevel level, BlockPos hall, VillageRanks.Rank rank) {
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(rank);
	}

	private static void fill(ServerLevel level, BlockPos at, String biome) {
		String cmd = "fillbiome " + (at.getX() - 6) + " " + (at.getY() - 4) + " " + (at.getZ() - 6) + " " + (at.getX() + 6) + " " + (at.getY() + 4)
			+ " " + (at.getZ() + 6) + " " + biome;
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
	}

	/** How often, in 200 rolls with fixed seeds, {@code culture} is the one that makes camp. */
	private static int comers(ServerLevel level, BlockPos hall, ResourceLocation culture) {
		int n = 0;
		for (int i = 0; i < 200; i++) {
			if (Lairs.comer(level, hall, true, RandomSource.create(9100L + i)).equals(Optional.of(culture))) {
				n++;
			}
		}
		return n;
	}

	private static Culture culture(GameTestHelper helper, ResourceLocation id) {
		Optional<Culture> culture = Threats.get(id);
		helper.assertTrue(culture.isPresent(), id + " isn't loaded");
		return culture.get();
	}

	/** A random whose every float is 0: any chance above 0 comes; its ints and doubles are seeded. */
	private static RandomSource always(long seed) {
		return new LegacyRandomSource(seed) {
			@Override
			public float nextFloat() {
				return 0f;
			}
		};
	}

	/** "92 blocks north", worked out here from where the camp stands, as a player reads it. */
	private static String where(BlockPos hall, BlockPos pos) {
		double dx = pos.getX() - hall.getX();
		double dz = pos.getZ() - hall.getZ();
		int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		double bearing = Math.toDegrees(Math.atan2(dx, -dz)); // clockwise from north (-z)
		int eighth = Math.floorMod((int) Math.round(bearing / 45.0), 8);
		return distance + " blocks " + DIRECTIONS[eighth];
	}

	private static String lastChronicle(ServerLevel level, BlockPos hall) {
		List<Chronicle.Entry> chronicle = ((VillageHallBlockEntity) level.getBlockEntity(hall)).chronicle();
		return chronicle.isEmpty() ? "" : chronicle.get(chronicle.size() - 1).text().getString();
	}

	private static List<Mob> men(ServerLevel level, Lairs.Lair lair) {
		List<Mob> men = new ArrayList<>(Lairs.band(level, lair));
		men.removeIf(m -> m.getUUID().equals(lair.captain()));
		return men;
	}

	private static List<String> said(QaStageFiveGameTests.Client client) {
		return client.seen().stream().map(Component::getString).toList();
	}

	// --- Bandit camps.

	/** "A Hamlet never gets a camp": in 200 rolls a Hamlet gets no bandit camp; one rank up, a Village, gets one every time. */
	//$ gametest_ticks_batch AREA '100' '"qaD3HamletCamp"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaD3HamletCamp")
	public void aHamletNeverGetsABanditCampAndAVillageDoes(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.HAMLET);
			int hamlet = comers(level, hall, Threats.BANDITS);
			helper.assertTrue(hamlet == 0, "a Hamlet got a bandit camp in " + hamlet + " of 200 days that one came");
			rank(level, hall, VillageRanks.Rank.VILLAGE);
			int village = comers(level, hall, Threats.BANDITS);
			helper.assertTrue(village == 200, "a Village got a bandit camp in only " + village + " of 200 days that one came");
			helper.succeed();
		});
	}

	/**
	 * "Chance a day: 12%", made rarer or more frequent by the Threat Level and none at 0: 12% at the default level 2,
	 * 6% at 1, 18% at 3, 0 at 0 (the village is quiet), and back to 12% at 2.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaD3CampChance"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaD3CampChance")
	public void theCampsChanceIsTwelvePercentADayAndNoneAtThreatLevelZero(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.VILLAGE);
			float[] expected = {0f, 0.06f, 0.12f, 0.18f};
			for (int threat : new int[] {2, 1, 3, 0, 2}) {
				WorkplaceConfig.parse("{\"threatLevel\": " + threat + "}").apply();
				Lairs.ENABLED = true;
				float chance = Lairs.dailyChance(level, hall);
				helper.assertTrue(Math.abs(chance - expected[threat]) < 1e-4, "at Threat Level " + threat + " a camp comes " + chance * 100
					+ "% of days, the page says " + expected[threat] * 100 + "%");
				helper.assertTrue(Threats.quiet(level, hall) == (threat == 0), "at Threat Level " + threat + " the village is quiet: " + Threats.quiet(level, hall));
			}
			WorkplaceConfig.parse("{\"threatLevel\": 0}").apply();
			Lairs.ENABLED = true;
			for (long seed = 0; seed < 20; seed++) {
				Lairs.round(level, hall, true, always(seed));
			}
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "a camp came at Threat Level 0: " + Lairs.near(level, hall));
			helper.succeed();
		});
	}

	/**
	 * The camp as a player sees it today (Milestone 32 not finished, so the expansion gate is shut): a Bandit Chief, a
	 * vindicator in an iron helmet and chestplate, with three or four men, pillagers and vindicators; and everyone nearby
	 * reads "Smoke rises beyond <village>: bandits have made camp <N> blocks <way>. Until their chief falls, they'll raid
	 * the village at night." with the distance and direction of the camp from the hall.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaD3PlayersCamp"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaD3PlayersCamp")
	public void aPlayersCampHasAChiefInIronAndThreeOrFourMenAndSaysWhereItIs(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		QaStageFiveGameTests.Client client = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("QaCampWatcher"));
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.VILLAGE);
			client.seen().clear();
			Expansions.openForTests = false;
			Lairs.Lair lair;
			try {
				helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no bandit camp was made");
				lair = Lairs.near(level, hall).orElseThrow();
			} finally {
				Expansions.openForTests = true;
			}
			Mob chief = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(chief != null && chief.getType() == EntityType.VINDICATOR, "the chief: " + chief);
			helper.assertTrue(chief.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && chief.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),
				"the chief isn't in iron: " + chief.getItemBySlot(EquipmentSlot.HEAD) + " " + chief.getItemBySlot(EquipmentSlot.CHEST));
			helper.assertTrue(chief.getCustomName() != null && chief.getCustomName().getString().equals("Bandit Chief"), "the chief is called " + chief.getCustomName());
			List<Mob> men = men(level, lair);
			helper.assertTrue(men.size() == 3 || men.size() == 4, "the chief has " + men.size() + " men, the page says three or four");
			for (Mob man : men) {
				helper.assertTrue(man.getType() == EntityType.PILLAGER || man.getType() == EntityType.VINDICATOR, "one of his men is a " + man.getType());
			}
			String name = VillageHalls.name(level, hall).getString();
			String expected = "Smoke rises beyond " + name + ": bandits have made camp " + where(hall, lair.pos())
				+ ". Until their chief falls, they'll raid the village at night.";
			helper.assertTrue(said(client).contains(expected), "a player nearby read " + said(client) + ", expected \"" + expected + "\"");
			helper.succeed();
		});
	}

	/**
	 * Breaking it up, after a save and reload: a player kills the chief; at once the camp is gone from the village's
	 * record (the Defense page), his band is gone, everyone nearby reads "The bandit camp near <village> is broken up: its
	 * chief has fallen and the band has scattered.", the chronicle says "<player> broke up the bandit camp", and no new camp
	 * comes for five days, which a second save keeps.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaD3BreakUp"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaD3BreakUp")
	public void killingTheChiefAfterASaveBreaksTheCampAndTheChronicleNamesThePlayer(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		String who = QaStageSixGameTests.name("QaChiefSlayer");
		QaStageFiveGameTests.Client client = QaStageSixGameTests.client(helper, GameType.SURVIVAL, who);
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.VILLAGE);
			at(level, 60, 1000);
			Expansions.openForTests = false;
			try {
				helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no bandit camp was made");
				Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
				helper.assertTrue(!men(level, lair).isEmpty(), "the camp has no band");
				Lairs.Data.get(level).roundTrip(level.registryAccess());
				Lairs.Lair saved = Lairs.near(level, hall).orElse(null);
				helper.assertTrue(saved != null && saved.captain().equals(lair.captain()), "after a save the camp is " + saved);
				client.seen().clear();
				Mob chief = (Mob) level.getEntity(lair.captain());
				chief.hurt(level.damageSources().playerAttack(client.player()), 1000f);
				helper.assertTrue(!chief.isAlive(), "the chief survived");
				helper.assertTrue(BanditCamps.near(level, hall).isEmpty(), "the village still knows the camp after its chief fell");
				helper.assertTrue(men(level, lair).isEmpty(), "his band didn't scatter: " + men(level, lair).size() + " still at the camp");
				String name = VillageHalls.name(level, hall).getString();
				String broken = "The bandit camp near " + name + " is broken up: its chief has fallen and the band has scattered.";
				helper.assertTrue(said(client).contains(broken), "a player nearby read " + said(client) + ", expected \"" + broken + "\"");
				helper.assertTrue(lastChronicle(level, hall).equals(who + " broke up the bandit camp"), "the chronicle says \"" + lastChronicle(level, hall) + "\"");
				helper.assertTrue(Lairs.resting(level, hall) == 5, "days without a new camp: " + Lairs.resting(level, hall) + ", the page says 5");
				Lairs.Data.get(level).roundTrip(level.registryAccess());
				helper.assertTrue(Lairs.resting(level, hall) == 5, "after a save, days without a new camp: " + Lairs.resting(level, hall));
				at(level, 64, 1000);
				helper.assertTrue(Lairs.resting(level, hall) == 1, "four days on, days of peace left: " + Lairs.resting(level, hall));
				at(level, 65, 1000);
				helper.assertTrue(Lairs.resting(level, hall) == 0, "five days on, days of peace left: " + Lairs.resting(level, hall));
			} finally {
				Expansions.openForTests = true;
			}
		});
		// The camp comes down a couple of seconds after (QaCampCleanup's tests): wait for it, so it's gone before the next batch.
		helper.runAfterDelay(CampCleanup.DELAY + CampCleanup.CLEAR_TICKS + 20, () -> {
			helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the camp is still coming down");
			helper.succeed();
		});
	}

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/**
	 * While it stands: the night raids are twice as likely, and the villagers feel less safe (their safety counts 60%,
	 * so with every villager lit and no guards the wellbeing is 0.06 lower); with the chief dead both are as before.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaD3LessSafe"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaD3LessSafe")
	public void whileACampStandsRaidsAreTwiceAsLikelyAndTheVillageFeelsLessSafe(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < 4; i++) {
			helper.setBlock(new BlockPos(2 + i, 2, 4), Blocks.GLOWSTONE);
			helper.setBlock(new BlockPos(2 + i, 2, 2), Blocks.GLOWSTONE);
		}
		helper.runAfterDelay(4, () -> {
			rank(level, hall, VillageRanks.Rank.VILLAGE);
			at(level, 70, 1000);
			float raid = VillageRaids.chance(level, hall, 12);
			VillageNeeds.Needs before = VillageNeeds.count(level, hall);
			helper.assertTrue(raid > 0 && raid < 0.5f, "the raid chance without a camp: " + raid);
			helper.assertTrue(before.villagers() == 4 && before.lit() == 4 && before.guards() == 0, "the village: " + before);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no bandit camp was made");
			float withCamp = VillageRaids.chance(level, hall, 12);
			helper.assertTrue(Math.abs(withCamp - 2 * raid) < 1e-5, "a night raid with a camp standing: " + withCamp + ", without: " + raid + "; the page says twice as often");
			VillageNeeds.Needs during = VillageNeeds.count(level, hall);
			helper.assertTrue(Math.abs(before.wellbeing() - during.wellbeing() - 0.06f) < 0.002f,
				"the wellbeing with a camp: " + during.wellbeing() + ", without: " + before.wellbeing() + " (the villagers should feel less safe)");
			Mob chief = (Mob) level.getEntity(Lairs.near(level, hall).orElseThrow().captain());
			chief.kill();
			helper.assertTrue(Math.abs(VillageRaids.chance(level, hall, 12) - raid) < 1e-5, "the raid chance after the chief fell: " + VillageRaids.chance(level, hall, 12));
			helper.assertTrue(Math.abs(VillageNeeds.count(level, hall).wellbeing() - before.wellbeing()) < 1e-4,
				"the wellbeing after the chief fell: " + VillageNeeds.count(level, hall).wellbeing() + ", was " + before.wellbeing());
		});
		helper.runAfterDelay(4 + CampCleanup.DELAY + CampCleanup.CLEAR_TICKS + 20, () -> {
			helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the camp is still coming down");
			helper.succeed();
		});
	}

	/**
	 * "With Monster Raids off, a camp may still go up but no raids come from it": with raids off the bandits still come
	 * (12% a day) and a camp stands, yet five nights rolled with dice that always raid bring none; with raids on the same
	 * dice bring tonight's raid, from the camp.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaD3RaidsOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaD3RaidsOff")
	public void withMonsterRaidsOffACampStillComesButNoRaidComesFromIt(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.VILLAGE);
			at(level, 80, 1000);
			WorkplaceConfig.parse("{\"villageRaids\": false}").apply();
			Lairs.ENABLED = true;
			VillageRaids.ENABLED = false;
			helper.assertTrue(Lairs.comer(level, hall, true, RandomSource.create(5L)).equals(Optional.of(Threats.BANDITS)) && Lairs.dailyChance(level, hall) > 0.1f,
				"with Monster Raids off no bandits come: " + Lairs.comer(level, hall, true, RandomSource.create(5L)) + ", " + Lairs.dailyChance(level, hall));
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no bandit camp was made with Monster Raids off");
			long today = 80 - 1;
			VillageRaids.Night night = VillageRaids.rollAhead(level, hall, 12, today - 10, today + 5, always(3L));
			helper.assertTrue(!night.raid() && Threats.clock(level, hall).isEmpty(), "with Monster Raids off a raid is coming: " + Threats.clock(level, hall));
			VillageRaids.ENABLED = true;
			night = VillageRaids.rollAhead(level, hall, 12, today - 10, today + 1, always(3L));
			helper.assertTrue(night.raid(), "with Monster Raids on and the dice always raiding, no raid tonight (test setup): " + Threats.clock(level, hall));
			helper.assertTrue(Threats.clock(level, hall).stream().allMatch(a -> a.culture().equals(Threats.BANDITS)), "the raid isn't from the camp: " + Threats.clock(level, hall));
			VillageRaids.ENABLED = false;
			helper.succeed();
		});
	}

	// --- Desert raiders.

	/**
	 * The Tomb Camp by a desert Town: chat says "Sand on the wind beyond <town>: Pharaoh <name> has risen from an old
	 * tomb..." (the Pharaoh as he is called over his head); his raiders are each called "Desert raider"; and the Pharaoh
	 * has exactly 50 more health than a husk.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaD3TombCamp"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaD3TombCamp")
	public void aTombCampTellsTheTownThePharaohHasRisen(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:desert");
		ServerLevel level = helper.getLevel();
		QaStageFiveGameTests.Client client = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("QaTombWatcher"));
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.TOWN);
			at(level, 90, 1000);
			client.seen().clear();
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), DESERT);
			helper.assertTrue(lair != null, "no tomb camp was made");
			Mob pharaoh = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(pharaoh != null && pharaoh.getCustomName() != null, "the Pharaoh: " + pharaoh);
			String title = pharaoh.getCustomName().getString();
			helper.assertTrue(title.startsWith("Pharaoh ") && title.length() > 9, "the Pharaoh is called \"" + title + "\"");
			String name = VillageHalls.name(level, hall).getString();
			String expected = "Sand on the wind beyond " + name + ": " + title + " has risen from an old tomb " + where(hall, lair.pos());
			helper.assertTrue(said(client).stream().anyMatch(s -> s.startsWith(expected)), "a player nearby read " + said(client) + ", expected it to begin \"" + expected + "\"");
			double husk = EntityType.HUSK.create(level).getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
			helper.assertTrue(Math.abs(pharaoh.getMaxHealth() - (husk + 50)) < 0.01 && Math.abs(pharaoh.getHealth() - pharaoh.getMaxHealth()) < 0.01,
				"the Pharaoh has " + pharaoh.getHealth() + "/" + pharaoh.getMaxHealth() + " health, a husk " + husk + "; the page says 50 more");
			List<Mob> band = men(level, lair);
			helper.assertTrue(!band.isEmpty(), "the Pharaoh has no raiders at his tomb");
			for (Mob raider : band) {
				helper.assertTrue(raider.getCustomName() != null && raider.getCustomName().getString().equals("Desert raider"),
					"one of his raiders (a " + raider.getType() + ") is called " + raider.getCustomName());
			}
			helper.succeed();
		});
	}

	/**
	 * "The camp starts 10 strong and grows by 2 a day up to 16": 10, a day later 12, a save, the next day 14, then 16,
	 * and many days later still 16.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaD3TombGrowth"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaD3TombGrowth")
	public void theTombCampGrowsTwoADayFromTenToSixteenThroughASave(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:desert");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.TOWN);
			at(level, 100, 1000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), DESERT);
			helper.assertTrue(lair != null && lair.strength() == 10, "the tomb camp starts " + (lair == null ? "missing" : lair.strength() + " strong") + ", the page says 10");
			long day = lair.grown();
			int[] want = {12, 14, 16, 16};
			for (int i = 0; i < want.length; i++) {
				Lairs.dawn(level, hall, day + i + 1);
				if (i == 0) {
					Lairs.Data.get(level).roundTrip(level.registryAccess());
				}
				int now = Lairs.near(level, hall).orElseThrow().strength();
				helper.assertTrue(now == want[i], (i + 1) + " days on the tomb camp is " + now + " strong, expected " + want[i]);
			}
			Lairs.dawn(level, hall, day + 40);
			helper.assertTrue(Lairs.near(level, hall).orElseThrow().strength() == 16, "40 days on the tomb camp is " + Lairs.near(level, hall).orElseThrow().strength()
				+ " strong, the page says 16 at most");
			helper.succeed();
		});
	}

	/** "He leads the raids on a City himself": the Pharaoh of a Town's tomb camp stays home; when the Town is a City, he leads. */
	//$ gametest_ticks_batch AREA '100' '"qaD3PharaohLeads"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaD3PharaohLeads")
	public void thePharaohLeadsTheRaidsOnACityButNotOnATown(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:desert");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.TOWN);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), DESERT);
			helper.assertTrue(lair != null, "no tomb camp was made");
			helper.assertTrue(!Lairs.leads(level, lair), "the Pharaoh leads the raids on a Town himself");
			rank(level, hall, VillageRanks.Rank.CITY);
			helper.assertTrue(Lairs.leads(level, lair), "the Pharaoh doesn't lead the raids on a City himself");
			helper.succeed();
		});
	}

	/**
	 * The sarcophagus, over 500 chests: always gold, emeralds and a golden apple; now and then a diamond; gold nuggets,
	 * bones, lapis and enchanted books turn up; the dune armor trim one time in five (15% to 25% of chests); and nothing
	 * the page doesn't list but the rotten flesh and blueprints the page now names.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaD3Sarcophagus"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaD3Sarcophagus")
	public void theSarcophagusHoldsGoldEmeraldsAnAppleAndTheDuneTrimOneTimeInFive(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Culture desert = culture(helper, DESERT);
		ResourceLocation table = desert.loot().orElseThrow();
		var loot = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, table));
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 2, 5))))
			.create(LootContextParamSets.CHEST);
		Set<net.minecraft.world.item.Item> listed = Set.of(Items.GOLD_INGOT, Items.EMERALD, Items.GOLDEN_APPLE, Items.GOLD_NUGGET, Items.BONE,
			Items.LAPIS_LAZULI, Items.ENCHANTED_BOOK, Items.DIAMOND, Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.ROTTEN_FLESH,
			io.github.jcondedata.aliveworkplace.registry.ModItems.BLUEPRINT);
		int chests = 500;
		int trims = 0;
		int diamonds = 0;
		Map<net.minecraft.world.item.Item, Integer> seen = new HashMap<>();
		for (int i = 0; i < chests; i++) {
			List<ItemStack> items = loot.getRandomItems(params, 11040L + i);
			helper.assertTrue(items.stream().anyMatch(s -> s.is(Items.GOLD_INGOT)) && items.stream().anyMatch(s -> s.is(Items.EMERALD))
				&& items.stream().anyMatch(s -> s.is(Items.GOLDEN_APPLE)), "chest " + i + " lacks gold, emeralds or a golden apple: " + items);
			for (ItemStack stack : items) {
				helper.assertTrue(listed.contains(stack.getItem()), "chest " + i + " holds " + stack + ", which the page doesn't list");
				seen.merge(stack.getItem(), 1, Integer::sum);
			}
			if (items.stream().anyMatch(s -> s.is(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE))) {
				trims++;
			}
			if (items.stream().anyMatch(s -> s.is(Items.DIAMOND))) {
				diamonds++;
			}
		}
		helper.assertTrue(trims >= chests * 15 / 100 && trims <= chests * 25 / 100, "the dune armor trim was in " + trims + " of " + chests + " chests, the page says one in five");
		helper.assertTrue(diamonds > 0 && diamonds < chests / 2, "a diamond was in " + diamonds + " of " + chests + " chests, the page says now and then");
		for (net.minecraft.world.item.Item item : List.of(Items.GOLD_NUGGET, Items.BONE, Items.LAPIS_LAZULI, Items.ENCHANTED_BOOK)) {
			helper.assertTrue(seen.getOrDefault(item, 0) > 0, "no " + item + " in " + chests + " chests");
		}
		helper.succeed();
	}

	/**
	 * "Switched off: desert_raiders under raiderCultures in the config file": with it false a desert Town gets no tomb
	 * camp in 200 rolls; with the config as it comes, it does.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaD3DesertOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaD3DesertOff")
	public void desertRaidersSwitchedOffInTheConfigNeverOpenATomb(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:desert");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.TOWN);
			WorkplaceConfig.parse("{\"raiderCultures\": {\"desert_raiders\": false}}").apply();
			Lairs.ENABLED = true;
			int off = comers(level, hall, DESERT);
			helper.assertTrue(off == 0, "with desert_raiders false a desert Town got a tomb camp in " + off + " of 200 days one came");
			new WorkplaceConfig().apply();
			Lairs.ENABLED = true;
			int on = comers(level, hall, DESERT);
			helper.assertTrue(on > 0, "with desert_raiders on a desert Town never got a tomb camp (test setup)");
			helper.succeed();
		});
	}

	// --- Sieges off: no sand ramps. The yard of QaThreatsM32GameTests (stone brick walls, a finished Stone Wall).

	private static final BlockPos YARD_HALL = new BlockPos(14, 2, 8);
	private static final int WEST = 5;
	private static final int EAST = 23;
	private static final int BACK = 4;
	private static final int FRONT = 15;

	private static Villager yard(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(YARD_HALL);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			Ladders.forget();
			SandRamps.forget();
			VillageHalls.RADIUS = radius;
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
		for (int y = 2; y <= 5; y++) {
			for (int x = WEST; x <= EAST; x++) {
				helper.setBlock(new BlockPos(x, y, BACK), Blocks.STONE_BRICKS);
				if (x < 11 || x > 17) {
					helper.setBlock(new BlockPos(x, y, FRONT), Blocks.STONE_BRICKS);
				}
			}
			for (int z = BACK; z <= FRONT; z++) {
				helper.setBlock(new BlockPos(WEST, y, z), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(EAST, y, z), Blocks.STONE_BRICKS);
			}
		}
		BlockPos origin = helper.absolutePos(new BlockPos(11, 2, FRONT - 2));
		level.getStructureManager().get(StarterBlueprints.STONE_WALL.id()).orElseThrow().placeInWorld(level, origin, origin,
			new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.STONE_WALL.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		for (int x = 9; x <= 11; x++) {
			for (int z = 6; z <= 8; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), x == 10 && z == 7 && y < 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
				}
			}
		}
		return helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 7));
	}

	private static Mob raider(GameTestHelper helper, Culture culture, Villager villager, Culture.Role role, double x, double z) {
		ServerLevel level = helper.getLevel();
		Culture.Member member = culture.roster().stream().filter(m -> m.role() == role).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 180f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
		if (mob instanceof Zombie zombie) {
			zombie.setBaby(false);
		}
		if (mob.getVehicle() != null) {
			net.minecraft.world.entity.Entity vehicle = mob.getVehicle();
			mob.stopRiding();
			vehicle.discard();
		}
		mob.getRandom().setSeed(1L);
		Threats.outfit(level, mob, culture, member);
		mob.setTarget(villager);
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	private static Map<BlockPos, BlockState> blocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1))) {
			out.put(pos.immutable(), level.getBlockState(pos));
		}
		return out;
	}

	private static List<String> changes(GameTestHelper helper, Map<BlockPos, BlockState> before) {
		ServerLevel level = helper.getLevel();
		List<String> out = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
			BlockState now = level.getBlockState(e.getKey());
			if (now != e.getValue()) {
				out.add(e.getKey().toShortString() + ": " + e.getValue() + " -> " + now);
			}
		}
		return out;
	}

	/**
	 * "With Sieges off, no sand ramps": a desert raiders' husk climber comes at a walled village with Sieges switched off
	 * in the config; no siege is laid, no sand is piled and no block of the yard changes in 12 seconds (the ramp tests'
	 * climber piles sand within a few).
	 */
	//$ gametest_ticks_batch AREA '300' '"qaD3SiegesOff"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaD3SiegesOff")
	public void withSiegesOffDesertRaidersPileNoSand(GameTestHelper helper) {
		Culture desert = culture(helper, DESERT);
		Villager villager = yard(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(YARD_HALL);
		WorkplaceConfig.parse("{\"sieges\": false}").apply();
		helper.assertTrue(!Sieges.ENABLED, "the Sieges switch didn't turn sieges off");
		Map<BlockPos, BlockState> before = blocks(helper);
		Mob climber = raider(helper, desert, villager, Culture.Role.CLIMBER, 14.5, 23.5);
		VillageRaids.track(level, hall, desert, List.of(climber));
		helper.assertTrue(VillageRaids.active(hall).isPresent(), "the desert raid didn't come with Sieges off (it should, as a plain raid)");
		helper.onEachTick(() -> {
			helper.assertTrue(Sieges.siege(level, hall).isEmpty(), "a siege was laid with Sieges off");
			helper.assertTrue(SandRamps.sand(level, hall).isEmpty(), "sand was piled with Sieges off: " + SandRamps.sand(level, hall));
			if (helper.getTick() >= 250) {
				List<String> changed = changes(helper, before);
				helper.assertTrue(changed.isEmpty(), "the yard changed with Sieges off: " + changed);
				helper.assertTrue(climber.isAlive(), "the climber is gone (test setup)");
				helper.succeed();
			}
		});
	}
}
