package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.wonder.GreatWallPowers;
import io.github.jcondedata.aliveworkplace.wonder.GreatWallSentries;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-11-01), from the specs of B134, B208, B211 and B364 and their wiki pages, the gaps the builders' tests leave:
 * <ul>
 * <li>B134: Peaceful posts no bounty and the tab's sentence is in words; a lair's captain is not posted while the village
 * is quiet and is once it isn't; an open bounty at threat level 0 can still be hunted and paid, and posting resumes when
 * the level rises.</li>
 * <li>B208: the log says each refused mob type once, forced or not; a camp whose chief alone is refused isn't made and
 * leaves nobody behind; a raid with some of its monsters refused counts only those in the world; both switches are on by
 * default, a file without them keeps them on, and the file turns them off.</li>
 * <li>B211: a Great Wall sentry refused still takes its post; vanilla's own golem and pups are never forced (with the
 * switch off a lost sentry's iron is used up for nothing: QaThreatSpawnsPeaceBugs1101GameTests).</li>
 * <li>B364: a camp forced past a blocklist, then the world turned Peaceful, leaves in peace: players near are told so
 * (not "broken up"), and no broken camp is counted toward a story's goal.</li>
 * </ul>
 */
public class QaThreatSpawnsPeace1101GameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos RAID_HALL = new BlockPos(1, 2, 1);
	private static final BlockPos RAID_CAMP = new BlockPos(12, 1, 12);
	private static final BlockPos BOUNTY_HALL = new BlockPos(5, 2, 5);
	private static final BlockPos BOUNTY_CAMP = new BlockPos(18, 2, 18);
	private static final long DAY = 24000;

	// ---------------------------------------------------------------- shared

	static boolean in(ServerLevel level, Entity e) {
		return e != null && e.isAlive() && level.getEntity(e.getUUID()) == e;
	}

	/** Every warning the mod's logger writes while the test runs, gathered from the root logger. */
	private static List<String> warnings(GameTestHelper helper, String name) {
		List<String> seen = java.util.Collections.synchronizedList(new ArrayList<>());
		var appender = new org.apache.logging.log4j.core.appender.AbstractAppender(name, null, null, true,
			org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
			@Override
			public void append(org.apache.logging.log4j.core.LogEvent event) {
				if (event.getLevel() == org.apache.logging.log4j.Level.WARN) {
					seen.add(event.getMessage().getFormattedMessage());
				}
			}
		};
		var root = ((org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false))
			.getConfiguration().getRootLogger();
		appender.start();
		root.addAppender(appender, null, null);
		Leftovers.after(helper, () -> {
			root.removeAppender(name);
			appender.stop();
		});
		return seen;
	}

	private static long count(List<String> lines, String start, String type) {
		synchronized (lines) {
			return lines.stream().filter(l -> l.startsWith(start) && l.contains(" " + type + " ")).count();
		}
	}

	/** The world refuses what {@code refused} names as MobsBeGone does, with both switches as given; all undone at the end. */
	static void refusing(GameTestHelper helper, Predicate<Entity> refused, boolean force, boolean forceVillage) {
		HostileSpawns.forget();
		HostileSpawns.FORCE = force;
		HostileSpawns.FORCE_VILLAGE = forceVillage;
		HostileSpawns.REFUSED_FOR_TESTS = refused;
		Leftovers.after(helper, () -> {
			HostileSpawns.REFUSED_FOR_TESTS = null;
			HostileSpawns.FORCE = true;
			HostileSpawns.FORCE_VILLAGE = true;
			HostileSpawns.forget();
		});
	}

	/** A hall (village radius 16) with four villagers for camps and raids; threats, clock and difficulty put back at the end. */
	private static BlockPos raidVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		Lairs.forget(level);
		VillageRaids.forget();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		Difficulty difficulty = level.getDifficulty();
		VillageHalls.RADIUS = 16;
		helper.setBlock(RAID_HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(RAID_HALL);
		ThreatData.get(level).forgetPast(hall);
		ThreatData.get(level).forgetClock(hall);
		Leftovers.after(helper, () -> {
			level.getServer().setDifficulty(difficulty, true);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Lairs.forget(level);
			BanditCamps.forget(level);
			VillageRaids.forget();
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		level.setDayTime(39 * DAY + 1000);
		return hall;
	}

	/** A Village-rank hall with its quests quiet and the bounties forgotten; settings, peace and clock put back at the end. */
	private static BlockPos bountyVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		boolean enabled = Bounties.ENABLED;
		boolean auto = Bounties.AUTO;
		int threat = Threats.LEVEL;
		long time = level.getDayTime();
		Difficulty difficulty = level.getDifficulty();
		VillageHalls.RADIUS = 16;
		Bounties.forget(level);
		Lairs.forget(level);
		Leftovers.after(helper, () -> {
			level.getServer().setDifficulty(difficulty, true);
			VillageHalls.RADIUS = radius;
			Bounties.ENABLED = enabled;
			Bounties.AUTO = auto;
			Threats.LEVEL = threat;
			if (level.getBlockEntity(helper.absolutePos(BOUNTY_HALL)) instanceof VillageHallBlockEntity e) {
				e.setAtPeace(false, -1);
			}
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(Bounties.TAG)
					|| m.getTags().contains(Bounties.CAPTAIN_TAG)).forEach(Mob::discard);
			Bounties.forget(level);
			Lairs.forget(level);
			BanditCamps.forget(level);
			level.setDayTime(time);
		});
		helper.setBlock(BOUNTY_HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(BOUNTY_HALL));
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		entity.setRank(VillageRanks.Rank.VILLAGE);
		return helper.absolutePos(BOUNTY_HALL);
	}

	private static void setDay(ServerLevel level, long day) {
		level.setDayTime((day - 1) * VillageNeeds.DAY + 1000);
	}

	private static void bountyRound(ServerLevel level, BlockPos hall, boolean auto) {
		boolean was = Bounties.AUTO;
		Bounties.AUTO = auto;
		try {
			Bounties.round(level, hall);
		} finally {
			Bounties.AUTO = was;
		}
	}

	// ---------------------------------------------------------------- B134

	/**
	 * B134 on Peaceful (bounties.md: "every village on Peaceful post no new bounty"): the round and a hand post nothing,
	 * and the Bounties tab says why in words; raised to Normal the next round posts one (the boundary from both sides).
	 */
	//$ gametest_ticks_batch HUGE '40' '"qa1101BountyPeaceful"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qa1101BountyPeaceful")
	public void onPeacefulNoBountyIsPostedAndTheTabSaysWhyInWords(GameTestHelper helper) {
		BlockPos hall = bountyVillage(helper);
		ServerLevel level = helper.getLevel();
		setDay(level, 300);
		level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
		helper.assertTrue(Bounties.quiet(level, hall) && !Bounties.mayPost(level, hall), "a village on Peaceful may post");
		bountyRound(level, hall, true);
		helper.assertTrue(Bounties.post(level, hall, RandomSource.create(1101)) == null, "posted by hand on Peaceful");
		helper.assertTrue(Bounties.open(level, hall).isEmpty(), "posted on Peaceful: " + Bounties.open(level, hall).size());
		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(GameType.SURVIVAL);
		ChoiceMenu menu = ChoiceMenu.detached(me, m -> QuestJournal.render(m, level, hall, me, QuestJournal.Tab.BOUNTIES));
		List<String> lore = menu.icon(22).get(DataComponents.LORE).lines().stream().map(Component::getString).toList();
		helper.assertTrue(lore.contains("No captains are posted while the village is at peace or threats are off."), "the tab on Peaceful: " + lore);
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		helper.assertTrue(!Bounties.quiet(level, hall) && Bounties.mayPost(level, hall), "still quiet off Peaceful");
		bountyRound(level, hall, true);
		helper.assertTrue(Bounties.open(level, hall).size() == 1, "off Peaceful the round posted " + Bounties.open(level, hall).size());
		helper.succeed();
	}

	/**
	 * B134's lair captain (bounties.md: a quiet village posts "no lair's captain"): a bandit camp stands by a village set
	 * At peace, and at threat level 0: its round posts no bounty on the chief; peace lifted and the level back at 2, the
	 * next round posts his bounty, and it stays open when the village turns quiet again.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qa1101BountyLair"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qa1101BountyLair")
	public void aQuietVillagesLairCaptainIsPostedOnlyOnceItIsntQuiet(GameTestHelper helper) {
		BlockPos hall = bountyVillage(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		setDay(level, 300);
		entity.setAtPeace(true, 300);
		helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(new BlockPos(20, 1, 20))) != null, "no camp");
		Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
		bountyRound(level, hall, false);
		helper.assertTrue(Bounties.open(level, hall).isEmpty(), "a lair captain posted At peace: " + Bounties.open(level, hall).size());
		entity.setAtPeace(false, -1);
		Threats.LEVEL = 0;
		bountyRound(level, hall, false);
		helper.assertTrue(Bounties.open(level, hall).isEmpty(), "a lair captain posted at threat level 0: " + Bounties.open(level, hall).size());
		Threats.LEVEL = 2;
		bountyRound(level, hall, false);
		List<Bounties.Bounty> open = Bounties.open(level, hall);
		helper.assertTrue(open.size() == 1 && open.get(0).lair && lair.captain().equals(open.get(0).captain),
			"no lair captain's bounty once the village wasn't quiet: " + open.size());
		entity.setAtPeace(true, 300);
		bountyRound(level, hall, false);
		helper.assertTrue(Bounties.open(level, hall).size() == 1, "the lair captain's open bounty didn't stay At peace");
		helper.succeed();
	}

	/**
	 * B134's "open ones stay until they lapse or are paid", at threat level 0: a bounty open before the level went to 0
	 * still raises its captain, and his insignia still pays; no new one follows until the level is raised again.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qa1101BountyPaidAtZero"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qa1101BountyPaidAtZero")
	public void anOpenBountyAtThreatLevelZeroIsStillHuntedAndPaid(GameTestHelper helper) {
		BlockPos hall = bountyVillage(helper);
		ServerLevel level = helper.getLevel();
		setDay(level, 300);
		ResourceLocation quirk = AliveWorkplace.id("hexer");
		helper.assertTrue(Bounties.quirks().containsKey(quirk), "the quirk didn't load: " + Bounties.quirks().keySet());
		Bounties.Bounty b = Bounties.post(level, hall, quirk, 0, 0, hall.offset(160, 0, 0));
		helper.assertTrue(b != null, "no bounty to start with");
		Threats.LEVEL = 0;
		setDay(level, 301);
		bountyRound(level, hall, true);
		helper.assertTrue(Bounties.open(level, hall).size() == 1, "the open bounty didn't stay alone at level 0: " + Bounties.open(level, hall).size());
		helper.assertTrue(Bounties.raise(level, b, helper.absolutePos(BOUNTY_CAMP), helper.absolutePos(BOUNTY_CAMP.north(4))), "his hideout didn't stand at level 0");
		Mob captain = level.getEntity(b.captain) instanceof Mob m ? m : null;
		helper.assertTrue(in(level, captain), "no captain at level 0");
		ServerPlayer killer = helper.makeMockServerPlayerInLevel();
		killer.setGameMode(GameType.SURVIVAL);
		captain.hurt(level.damageSources().playerAttack(killer), 1000f);
		helper.runAfterDelay(2, () -> {
			ItemEntity drop = level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BOUNTY_CAMP)).inflate(4),
				e -> e.getItem().is(ModItems.CAPTAINS_INSIGNIA)).stream().findFirst().orElse(null);
			helper.assertTrue(drop != null && b.fallen, "no insignia at level 0");
			killer.getInventory().add(drop.getItem().copy());
			drop.discard();
			helper.assertTrue(Bounties.claimAny(killer, level, hall) == Bounties.Claim.PAID && killer.getInventory().countItem(Items.EMERALD) == 15,
				"not paid at level 0: " + killer.getInventory().countItem(Items.EMERALD));
			setDay(level, 304);
			bountyRound(level, hall, true);
			helper.assertTrue(Bounties.open(level, hall).isEmpty(), "a new one posted at level 0");
			Threats.LEVEL = 2;
			String rank = String.valueOf(VillageRanks.of(level, hall));
			// (the hall's own tick ranks a village with no villagers down: this test's village is only a hall, so it's put back)
			((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.VILLAGE);
			bountyRound(level, hall, true);
			helper.assertTrue(Bounties.open(level, hall).size() == 1, "level 2 again, the round posted " + Bounties.open(level, hall).size()
				+ " (may post " + Bounties.mayPost(level, hall) + ", quiet " + Bounties.quiet(level, hall) + ", day " + Chronicle.day(level) + ", rank was " + rank + ")");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- B208

	/**
	 * B208/B211's "a clear log line once per mob type": three pillagers and two vindicators refused and forced, two
	 * golems refused and forced, then the same with both switches off: each type is in the server log once per way.
	 */
	//$ gametest_ticks_batch BIG '40' '"qa1101LogOnce"'
	@GameTest(template = BIG, timeoutTicks = 40, batch = "qa1101LogOnce")
	public void eachRefusedMobTypeIsLoggedOncePerWay(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<String> lines = warnings(helper, "qa1101LogOnce");
		refusing(helper, e -> e instanceof Enemy || e instanceof IronGolem, true, true);
		List<Entity> made = new ArrayList<>();
		Leftovers.after(helper, () -> made.forEach(Entity::discard));
		Vec3 at = helper.absoluteVec(new Vec3(8, 2, 8));
		for (int i = 0; i < 3; i++) {
			Pillager p = EntityType.PILLAGER.create(level);
			p.moveTo(at);
			made.add(p);
			helper.assertTrue(HostileSpawns.add(level, p, "QA camp " + i), "a pillager wasn't forced");
		}
		for (int i = 0; i < 2; i++) {
			Vindicator v = EntityType.VINDICATOR.create(level);
			v.moveTo(at);
			made.add(v);
			helper.assertTrue(HostileSpawns.add(level, v, "QA raid " + i), "a vindicator wasn't forced");
			IronGolem g = GolemSmith.build(level, helper.absolutePos(new BlockPos(4, 2, 4)), GolemSmith.Role.SENTRY);
			made.add(g);
			helper.assertTrue(in(level, g), "a golem wasn't forced");
		}
		HostileSpawns.FORCE = false;
		HostileSpawns.FORCE_VILLAGE = false;
		for (int i = 0; i < 2; i++) {
			Pillager p = EntityType.PILLAGER.create(level);
			p.moveTo(at);
			made.add(p);
			helper.assertTrue(!HostileSpawns.add(level, p, "QA camp off " + i), "a pillager was forced with the switch off");
			helper.assertTrue(GolemSmith.build(level, helper.absolutePos(new BlockPos(4, 2, 4)), GolemSmith.Role.SENTRY) == null, "a golem was forced off");
		}
		String forced = "Another mod refused to let";
		String off = "Could not spawn";
		long[] got = {count(lines, forced, "minecraft:pillager"), count(lines, forced, "minecraft:vindicator"), count(lines, forced, "minecraft:iron_golem"),
			count(lines, off, "minecraft:pillager"), count(lines, off, "minecraft:iron_golem")};
		helper.assertTrue(java.util.Arrays.stream(got).allMatch(n -> n == 1),
			"lines (forced pillager, vindicator, golem; off pillager, golem): " + java.util.Arrays.toString(got) + " in " + lines);
		helper.succeed();
	}

	/**
	 * B208's "no camp recorded without its captain", when only the chief's kind is refused and forcing is off: no camp,
	 * nothing broken up, and none of the band he would have led is left standing about.
	 */
	//$ gametest_ticks_batch BIG '60' '"qa1101ChiefRefused"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qa1101ChiefRefused")
	public void aCampWhoseChiefAloneIsRefusedIsNotMadeAndLeavesNobody(GameTestHelper helper) {
		BlockPos hall = raidVillage(helper);
		refusing(helper, e -> e instanceof Vindicator, false, true);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(RAID_CAMP)) == null, "a camp was made without its chief");
			helper.assertTrue(Lairs.near(level, hall).isEmpty() && Lairs.resting(level, hall) == 0, "a camp was recorded, or broken up");
			List<Mob> left = level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m instanceof Pillager);
			helper.assertTrue(left.isEmpty(), "bandits left standing without a camp: " + left);
			helper.assertTrue(HostileSpawns.lastWarning().contains("minecraft:vindicator") && HostileSpawns.lastWarning().contains("forceThreatSpawns is off"),
				"the log line: " + HostileSpawns.lastWarning());
			helper.runAfterDelay(40, () -> {
				Lairs.round(level, hall, false, RandomSource.create(1101));
				helper.assertTrue(Lairs.resting(level, hall) == 0, "a camp nobody made was broken up");
				helper.succeed();
			});
		});
	}

	/**
	 * B208's "raids count only raiders in the world": the monsters' night attack with its zombies refused and forcing
	 * off comes with its skeletons and spiders, and its count is exactly the raiders standing.
	 */
	//$ gametest_ticks_batch BIG '60' '"qa1101RaidCount"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qa1101RaidCount")
	public void aRaidWithSomeMonstersRefusedCountsOnlyThoseInTheWorld(GameTestHelper helper) {
		BlockPos hall = raidVillage(helper);
		refusing(helper, e -> e instanceof Zombie, false, true);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			level.setDayTime(39 * DAY + 15000);
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 12, 0, Double.NaN, Threats.on(Threats.MONSTERS).orElseThrow(), RandomSource.create(1101));
			helper.assertTrue(raid != null, "no raid of skeletons and spiders");
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raid.raiders() > 0 && raiders.size() == raid.raiders() && raiders.stream().allMatch(m -> in(level, m) && !(m instanceof Zombie)),
				"the raid counts " + raid.raiders() + " but " + raiders.size() + " stand: " + raiders);
			helper.assertTrue(HostileSpawns.lastWarning().contains("minecraft:zombie"), "the zombies' refusal isn't logged: " + HostileSpawns.lastWarning());
			helper.runAfterDelay(20, () -> {
				helper.assertTrue(raiders.stream().allMatch(m -> in(level, m)), "raiders gone after 20 ticks: " + raiders);
				helper.succeed();
			});
		});
	}

	/**
	 * Both switches are on by default, a config file from before them (no keys) keeps them on, the file's false turns
	 * them off, and the settings screen names them in words.
	 */
	//$ gametest_ticks_batch BIG '20' '"qa1101Switches"'
	@GameTest(template = BIG, timeoutTicks = 20, batch = "qa1101Switches")
	public void bothForceSwitchesAreOnUnlessTheFileSaysOff(GameTestHelper helper) {
		try {
			WorkplaceConfig fresh = new WorkplaceConfig();
			helper.assertTrue(fresh.forceThreatSpawns && fresh.forceVillageSpawns, "not on by default");
			WorkplaceConfig old = WorkplaceConfig.parse("{\"threatLevel\": 2}");
			helper.assertTrue(old.forceThreatSpawns && old.forceVillageSpawns, "a file without the keys turned them off");
			old.apply();
			helper.assertTrue(HostileSpawns.FORCE && HostileSpawns.FORCE_VILLAGE, "an old file's apply left them off");
			WorkplaceConfig.parse("{\"forceThreatSpawns\": false}").apply();
			helper.assertTrue(!HostileSpawns.FORCE && HostileSpawns.FORCE_VILLAGE, "forceThreatSpawns false: " + HostileSpawns.FORCE + ", " + HostileSpawns.FORCE_VILLAGE);
			WorkplaceConfig.parse("{\"forceVillageSpawns\": false}").apply();
			helper.assertTrue(HostileSpawns.FORCE && !HostileSpawns.FORCE_VILLAGE, "forceVillageSpawns false: " + HostileSpawns.FORCE + ", " + HostileSpawns.FORCE_VILLAGE);
			for (String key : List.of("forceThreatSpawns", "forceVillageSpawns")) {
				helper.assertTrue(WorkplaceConfig.optionNames().contains(key) && WorkplaceConfig.isSwitch(key), key + " isn't a switch on the settings screen");
			}
			helper.assertTrue(Component.translatable("aliveworkplace.config.forceThreatSpawns").getString().equals("Force Raider Spawns")
				&& Component.translatable("aliveworkplace.config.forceVillageSpawns").getString().equals("Force Village Spawns"), "the switches' names");
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- B211

	private static final ResourceLocation LITTLE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1101_little_wall");

	/** A little finished Great Wall of the test's own round {@code hall}, one sentry post at 10, 9, 10 on a stone walk. */
	static WonderSite wall(GameTestHelper helper, BlockPos hall, BlockPos stone) {
		Map<ResourceLocation, Wonder> before = Wonders.loaded();
		JsonObject json = JsonParser.parseString("""
			{"name": "wonder.aliveworkplace.great_wall.name", "description": "wonder.aliveworkplace.great_wall.description",
			 "icon": "minecraft:stone_brick_wall",
			 "stages": [{"name": "wonder.aliveworkplace.great_wall.stage.1", "parts": [{"part": "aliveworkplace:wonder/great_wall/stage_1_0"}]}],
			 "powers": [{"type": "sentries", "range": 16, "posts": [[10, 9, 10]]}]}
			""").getAsJsonObject();
		Map<ResourceLocation, Wonder> with = new LinkedHashMap<>(before);
		with.put(LITTLE, Wonders.read(LITTLE, json));
		Wonders.setForTest(with);
		Leftovers.after(helper, () -> Wonders.setForTest(before));
		for (int x = 5; x <= 15; x++) {
			for (int z = 9; z <= 11; z++) {
				for (int y = 1; y <= 8; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE_BRICKS);
				}
			}
		}
		ServerLevel level = helper.getLevel();
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(LITTLE, hall, null, "", 1);
		manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), helper.absolutePos(BlockPos.ZERO), Rotation.NONE, Mirror.NONE), stone);
		manager.finish(site.id(), Chronicle.day(level));
		manager.setKept(site.id(), new WonderSite.Kept(100, Chronicle.day(level) + 10_000, false, 0));
		Wonders.forgetPowers();
		UUID uuid = site.id();
		Leftovers.after(helper, () -> {
			manager.remove(uuid);
			Wonders.forgetPowers();
			GreatWallPowers.forget();
			GreatWallSentries.forget();
			level.getEntitiesOfClass(IronGolem.class, helper.getBounds().inflate(16)).forEach(Entity::discard);
		});
		return manager.site(uuid).orElseThrow();
	}

	static BlockPos wallHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setBlock(new BlockPos(5, 2, 24), ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(new BlockPos(5, 2, 24));
	}

	/** B211: a Great Wall sentry (the fix names them) the world refuses still takes its post, stays, and the log says so. */
	//$ gametest_ticks_batch HUGE '160' '"qa1101WallForced"'
	@GameTest(template = HUGE, timeoutTicks = 160, batch = "qa1101WallForced")
	public void aGreatWallSentryRefusedByAnotherModStillTakesItsPost(GameTestHelper helper) {
		BlockPos hall = wallHall(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(12, 2, 24), Blocks.STONE);
		WonderSite site = wall(helper, hall, helper.absolutePos(new BlockPos(12, 2, 24)));
		refusing(helper, e -> e instanceof IronGolem, true, true);
		IronGolem plain = EntityType.IRON_GOLEM.create(level);
		plain.moveTo(helper.absoluteVec(new Vec3(20, 2, 4)));
		helper.assertTrue(!level.addFreshEntity(plain) && plain.isRemoved(), "the world didn't refuse an iron golem");
		helper.assertTrue(GreatWallSentries.man(level, site) == 1, "the refused sentry didn't take its post");
		GreatWallSentries.Post post = GreatWallSentries.posts(level, hall).get(0);
		IronGolem sentry = GreatWallSentries.sentry(level, post).orElse(null);
		helper.assertTrue(in(level, sentry) && GreatWallSentries.isSentry(sentry), "the sentry isn't in the world: " + sentry);
		helper.assertTrue(HostileSpawns.lastWarning().contains("minecraft:iron_golem") && HostileSpawns.lastWarning().contains("forceVillageSpawns"),
			"the log line: " + HostileSpawns.lastWarning());
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(in(level, sentry), "the sentry is gone after 100 ticks");
			helper.succeed();
		});
	}

	/**
	 * B211: "vanilla's own spawns are never forced", even with forceVillageSpawns on: a player's iron golem (a pumpkin
	 * on four iron blocks) and a pup two wolves breed the vanilla way stay refused, while the same golem stands when
	 * nothing refuses it.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qa1101Vanilla"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qa1101Vanilla")
	public void vanillasOwnGolemAndPupAreNeverForced(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> level.getEntitiesOfClass(Entity.class, helper.getBounds().inflate(8),
			e -> e instanceof IronGolem || e instanceof Wolf).forEach(Entity::discard));
		List<Wolf> wolves = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			Wolf w = helper.spawn(EntityType.WOLF, new BlockPos(14 + i, 2, 24));
			w.setAge(0);
			wolves.add(w);
		}
		// the control: nothing refuses, the T of iron with a pumpkin is a golem
		golemT(helper, 4);
		helper.assertTrue(level.getEntitiesOfClass(IronGolem.class, new AABB(helper.absolutePos(new BlockPos(4, 2, 4))).inflate(4)).size() == 1,
			"the control golem wasn't made: the pattern is wrong");
		helper.runAfterDelay(2, () -> {
			refusing(helper, e -> e instanceof IronGolem || e instanceof Wolf, true, true);
			golemT(helper, 22);
			helper.assertTrue(level.getEntitiesOfClass(IronGolem.class, new AABB(helper.absolutePos(new BlockPos(22, 2, 4))).inflate(4)).isEmpty(),
				"a player's golem was forced past the blocklist");
			int before = level.getEntitiesOfClass(Wolf.class, helper.getBounds()).size();
			wolves.get(0).spawnChildFromBreeding(level, wolves.get(1));
			int after = level.getEntitiesOfClass(Wolf.class, helper.getBounds()).size();
			helper.assertTrue(after == before, "a pup bred the vanilla way was forced: " + before + " -> " + after);
			helper.succeed();
		});
	}

	private static void golemT(GameTestHelper helper, int x) {
		helper.setBlock(new BlockPos(x, 2, 4), Blocks.IRON_BLOCK);
		helper.setBlock(new BlockPos(x - 1, 3, 4), Blocks.IRON_BLOCK);
		helper.setBlock(new BlockPos(x, 3, 4), Blocks.IRON_BLOCK);
		helper.setBlock(new BlockPos(x + 1, 3, 4), Blocks.IRON_BLOCK);
		helper.setBlock(new BlockPos(x, 4, 4), Blocks.CARVED_PUMPKIN);
	}

	// ---------------------------------------------------------------- B364

	/**
	 * B364 with B208: a camp whose bandits the world refused (forced in) stands; the world goes Peaceful and takes them.
	 * The next round sends it away in peace: a player near is told "packed up and gone", never "broken up", the
	 * chronicle says it left, no rest days, and no broken camp is counted toward a story's goal.
	 */
	//$ gametest_ticks_batch BIG '80' '"qa1101PeacefulForced"'
	@GameTest(template = BIG, timeoutTicks = 80, batch = "qa1101PeacefulForced")
	public void aForcedCampOnPeacefulLeavesInPeaceAndPlayersAreToldSo(GameTestHelper helper) {
		BlockPos hall = raidVillage(helper);
		refusing(helper, e -> e instanceof Enemy, true, true);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = RealmGameTests.listener(helper, seen, "qa1101");
		Vec3 near = helper.absoluteVec(new Vec3(4, 2, 4));
		// (through the connection: a mock player never confirms a teleport, and a plain one can be put back to where it logged in)
		player.connection.teleport(near.x, near.y, near.z, 0f, 0f);
		int broken = Stories.banditCampsBroken(level, hall);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(RAID_CAMP)) != null, "no camp");
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			helper.assertTrue(in(level, level.getEntity(lair.captain())), "the forced chief isn't in the world");
			int from = seen.size();
			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
			helper.runAfterDelay(10, () -> {
				helper.assertTrue(level.getEntity(lair.captain()) == null, "Peaceful didn't take the forced chief");
				// (the hall's own round may already have sent the camp away in these ticks: what the player was told since counts)
				player.connection.teleport(near.x, near.y, near.z, 0f, 0f);
				String where = "player at " + player.blockPosition().toShortString() + " in " + player.level().dimension().location()
					+ " (removed " + player.isRemoved() + ", listed " + level.players().contains(player) + "), hall at " + hall.toShortString();
				Lairs.round(level, hall, true, RandomSource.create(1101));
				helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the camp still stands on Peaceful");
				List<Component> since = List.copyOf(seen.subList(from, seen.size()));
				List<String> keys = since.stream().filter(c -> c.getContents() instanceof TranslatableContents).map(c -> ((TranslatableContents) c.getContents()).getKey()).toList();
				helper.assertTrue(keys.contains("message.aliveworkplace.lair.left") && keys.stream().noneMatch(k -> k.contains("broken")), "the player was told: " + keys + "; " + where);
				String told = since.stream().filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("message.aliveworkplace.lair.left"))
					.findFirst().map(Component::getString).orElse("");
				String name = VillageHalls.name(level, hall).getString();
				helper.assertTrue(told.equals("The raiders of the Bandit camp have packed up and gone: " + name + " is left in peace."), "the line: '" + told + "'");
				List<Chronicle.Entry> chronicle = entity.chronicle();
				String last = chronicle.isEmpty() ? "" : chronicle.get(chronicle.size() - 1).text().getString();
				helper.assertTrue(last.equals("The raiders of the Bandit camp packed up and left the village in peace"), "the chronicle: '" + last + "'");
				helper.assertTrue(Lairs.resting(level, hall) == 0 && Stories.banditCampsBroken(level, hall) == broken,
					"counted as broken up: resting " + Lairs.resting(level, hall) + ", broken " + broken + " -> " + Stories.banditCampsBroken(level, hall));
				helper.succeed();
			});
		});
	}
}
