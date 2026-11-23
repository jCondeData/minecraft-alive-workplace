package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

/**
 * QA (lane-d-1123-0108) of the pillager warband (ROADMAP 32.7, docs/wiki/warbands.md), written from the wiki page for the
 * sentences no earlier test checks: at most 10 stand at the camp at both sides of that line while the strength grows past
 * it (and the cap of 18 from 17); a player who brings the Warlord down at the walls is named, the band at the camp
 * scatters, the raiders at the walls fight on and the chest stays; the Warlord saved and loaded mid-raid still leads and
 * goes home at dawn; the config switch off: a standing camp sends no raids; sieges off: still the ravagers and evoker;
 * the lines a player reads, word for word; and at the war camp a war party (32.13) meets the Warlord at the front only
 * by a City, and nobody more turns out once the band's strength is spent.
 */
public class QaWarbandSpec1123dGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(16, 1, 16);
	private static final long DAY = 24000;
	private static final ResourceLocation WARBAND = AliveWorkplace.id("pillager_warband");

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	private static BlockPos village(GameTestHelper helper, String biome) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		Set<String> off = Threats.DISABLED;
		boolean open = Expansions.openForTests;
		boolean lairs = Lairs.ENABLED;
		boolean sieges = Sieges.ENABLED;
		boolean raids = VillageRaids.ENABLED;
		Lairs.ENABLED = true;
		VillageRaids.ENABLED = true;
		Expansions.openForTests = true;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		String old = level.getBiome(hall).unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
		fill(level, hall, biome);
		ThreatData.get(level).forgetPast(hall);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(64), m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(VillageRaids.TAG)
				|| m.getTags().contains(Lairs.CAPTAIN_TAG) || m instanceof Vex).forEach(Mob::discard);
			VillageRaids.forget();
			BanditCamps.forget(level);
			Lairs.forget(level);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			ThreatData.get(level).forgetSiege(hall);
			VillageHalls.RADIUS = radius;
			Threats.DISABLED = off;
			Expansions.openForTests = open;
			Lairs.ENABLED = lairs;
			Sieges.ENABLED = sieges;
			VillageRaids.ENABLED = raids;
			level.setDayTime(time);
			fill(level, hall, old);
			Leftovers.players(helper);
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
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

	private static Lairs.Lair lair(GameTestHelper helper, BlockPos hall) {
		Optional<Lairs.Lair> lair = Lairs.near(helper.getLevel(), hall);
		helper.assertTrue(lair.isPresent(), "no lair by the village");
		return lair.get();
	}

	/** The band standing at the camp (not out on a raid), the Warlord apart. */
	private static List<Mob> men(ServerLevel level, Lairs.Lair lair) {
		List<Mob> men = new ArrayList<>(Lairs.band(level, lair));
		men.removeIf(m -> m.getUUID().equals(lair.captain()));
		return men;
	}

	private static long count(List<Mob> mobs, EntityType<?> type) {
		return mobs.stream().filter(m -> m.getType() == type).count();
	}

	private static Culture warband(GameTestHelper helper) {
		Optional<Culture> culture = Threats.get(WARBAND);
		helper.assertTrue(culture.isPresent(), "pillager_warband.json isn't loaded");
		return culture.get();
	}

	private static String lastChronicle(ServerLevel level, BlockPos hall) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		return entity.chronicle().get(entity.chronicle().size() - 1).text().getString();
	}

	private static Mob warlord(ServerLevel level, Lairs.Lair lair) {
		return (Mob) level.getEntity(lair.captain());
	}

	/**
	 * "Strength. 10 when the camp is made, 2 more a day, at most 18; at most 10 stand at the camp." Grown to 18 the camp
	 * still has 10 standing (and its strength is 18, not 10); at 11 still 10, at 10 ten, at 9 nine; 17 and a day is 18,
	 * not 19. A raid of 6 from 18 leaves 10 standing (12 at home), and at dawn all 18 again with 10 standing.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaWarband1123dHomeCap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWarband1123dHomeCap")
	public void atMostTenStandAtTheCampAsTheStrengthGrowsToEighteen(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.TOWN);
			Culture culture = warband(helper);
			at(level, 80, 1000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), WARBAND);
			helper.assertTrue(lair != null && lair.strength() == 10 && men(level, lair).size() == 10, "the war camp at founding: " + lair);
			long day = lair.grown();
			Lairs.dawn(level, hall, day + 4);
			Lairs.round(level, hall, true);
			Lairs.Lair grown = lair(helper, hall);
			helper.assertTrue(grown.strength() == 18, "four days on the strength isn't 18: " + grown);
			helper.assertTrue(men(level, grown).size() == 10, "at strength 18, standing at the camp: " + men(level, grown).size());
			for (int[] row : new int[][] {{11, 10}, {10, 10}, {9, 9}}) {
				Lairs.setStrength(level, hall, row[0]);
				Lairs.round(level, hall, true);
				int standing = men(level, lair(helper, hall)).size();
				helper.assertTrue(standing == row[1], "at strength " + row[0] + ", " + standing + " stand at the camp (want " + row[1] + ")");
			}
			// 17, a day later: 18, not 19.
			Lairs.setStrength(level, hall, 17);
			long grownDay = lair(helper, hall).grown();
			Lairs.dawn(level, hall, grownDay + 1);
			helper.assertTrue(lair(helper, hall).strength() == 18, "17 and a day: " + lair(helper, hall));
			// A raid of 6 from a camp of 18.
			at(level, 80, 15000);
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 12, 0, Double.NaN, culture, RandomSource.create(11L));
			helper.assertTrue(raid != null, "no raid");
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			long band = raiders.stream().filter(m -> !m.getTags().contains(Threats.EXTRA_TAG)).count();
			Lairs.Lair out = lair(helper, hall);
			helper.assertTrue(band == 6 && out.out() == 6 && out.strength() == 18 && out.home() == 12, "the raid of 6 from 18: band " + band + ", " + out);
			helper.assertTrue(men(level, out).size() == 10, "12 at home, standing at the camp: " + men(level, out).size());
			// Dawn, all lived: 18 again, 10 standing.
			ThreatData.get(level).begin(new ThreatData.Under(hall, WARBAND, raiders.size(), level.getGameTime() - 2000));
			at(level, 80, 22500);
			VillageRaids.tick(level, hall, 12, 0, 70, d -> { });
			Lairs.Lair home = lair(helper, hall);
			helper.assertTrue(VillageRaids.active(hall).isEmpty() && home.strength() == 18 && home.out() == 0, "after the raid: " + home);
			helper.assertTrue(men(level, home).size() == 10, "after the raid, standing at the camp: " + men(level, home).size());
			helper.succeed();
		});
	}

	/**
	 * "Kill him at the walls and the camp breaks: its band at the camp scatters, its chest is yours, the chronicle says who
	 * did it; the raiders already at the walls fight on." A player's blow at a City: the chronicle names the player and
	 * the Warlord, the six left at the camp are gone, the six of the band and the three extras at the walls are still
	 * there with the raid on, and the war camp's loot chest still stands for the taking.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaWarband1123dPlayerKill"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWarband1123dPlayerKill")
	public void aPlayerWhoBringsTheWarlordDownAtTheWallsIsNamedAndTheBandAtTheCampScatters(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.CITY);
			Culture culture = warband(helper);
			at(level, 90, 1000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), WARBAND);
			helper.assertTrue(lair != null, "no war camp");
			Mob warlord = warlord(level, lair);
			String name = warlord.getCustomName().getString();
			Lairs.setStrength(level, hall, 12);
			Lairs.round(level, hall, true);
			at(level, 90, 15000);
			helper.assertTrue(VillageRaids.start(level, hall, 12, 0, Double.NaN, culture, RandomSource.create(12L)) != null && Lairs.leading(warlord), "no raid led");
			Lairs.Lair out = lair(helper, hall);
			helper.assertTrue(out.out() == 6 && men(level, out).size() == 6, "6 out, 6 at the camp: " + out + ", standing " + men(level, out).size());
			List<Mob> atCamp = men(level, out);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.SURVIVAL);
			String who = player.getDisplayName().getString();
			warlord.hurt(level.damageSources().playerAttack(player), 1000f);
			helper.assertTrue(!warlord.isAlive(), "the Warlord lived");
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the war camp stands with its Warlord dead");
			helper.assertTrue(atCamp.stream().noneMatch(Entity::isAlive), "the band at the camp didn't scatter: "
				+ atCamp.stream().filter(Entity::isAlive).count() + " still there");
			List<Mob> walls = VillageRaids.raiders(level, hall);
			helper.assertTrue(walls.size() == 9 && count(walls, EntityType.RAVAGER) == 2 && count(walls, EntityType.EVOKER) == 1 && VillageRaids.active(hall).isPresent(),
				"the raiders at the walls didn't fight on: " + walls);
			String last = lastChronicle(level, hall);
			helper.assertTrue(last.equals(who + " brought down " + name + " and broke the war camp"), "the chronicle: " + last);
			boolean chest = false;
			for (BlockPos p : BlockPos.betweenClosed(lair.pos().offset(-13, -2, -13), lair.pos().offset(13, 6, 13))) {
				if (level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity c && c.getLootTable() != null
					&& c.getLootTable().location().equals(AliveWorkplace.id("chests/war_camp"))) {
					chest = true;
				}
			}
			helper.assertTrue(chest, "the war camp's chest is gone the moment its Warlord fell");
			helper.succeed();
		});
	}

	/**
	 * "The Warlord out on a raid carries the raiders' entity tags, so a restart mid-raid keeps him with it" and "If he
	 * lives through the night, he goes home to his camp at dawn." The Warlord leading a City raid is saved and loaded (his
	 * entity, the camps and the raid's data): he is still one of the raiders, the camp still stands at its round, and at
	 * dawn he goes home, alive, to his camp.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaWarband1123dReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWarband1123dReload")
	public void theWarlordSavedAndLoadedMidRaidStillGoesHomeAtDawn(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.CITY);
			Culture culture = warband(helper);
			at(level, 100, 1000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), WARBAND);
			helper.assertTrue(lair != null, "no war camp");
			Mob warlord = warlord(level, lair);
			String name = warlord.getCustomName().getString();
			at(level, 100, 15000);
			helper.assertTrue(VillageRaids.start(level, hall, 12, 0, Double.NaN, culture, RandomSource.create(13L)) != null && Lairs.leading(warlord), "no raid led");
			// The Warlord is unloaded and loaded again, as a restart does.
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(warlord.save(tag), "the Warlord wasn't saved");
			warlord.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
			Lairs.Data.get(level).roundTrip(level.registryAccess());
			Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
			helper.assertTrue(loaded instanceof Mob && level.addFreshEntity(loaded), "the Warlord couldn't be loaded");
			Mob back = (Mob) loaded;
			helper.assertTrue(back.getUUID().equals(lair.captain()) && Lairs.leading(back) && back.getCustomName().getString().equals(name),
				"after loading he isn't leading: " + back.getTags());
			helper.assertTrue(VillageRaids.raiders(level, hall).contains(back), "after loading he isn't one of the raiders");
			Lairs.round(level, hall, true);
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp broke at its round with the loaded Warlord out leading");
			ThreatData.get(level).begin(new ThreatData.Under(hall, WARBAND, VillageRaids.raiders(level, hall).size(), level.getGameTime() - 2000));
			at(level, 100, 22500);
			VillageRaids.tick(level, hall, 12, 0, 90, d -> { });
			helper.assertTrue(VillageRaids.active(hall).isEmpty(), "the raid didn't end at dawn");
			helper.assertTrue(back.isAlive() && !back.isRemoved() && !Lairs.leading(back) && back.distanceToSqr(lair.pos().getCenter()) < 8 * 8,
				"the loaded Warlord didn't go home: " + back.blockPosition() + ", removed " + back.isRemoved() + ", the camp at " + lair.pos());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp didn't stand after the raid");
			helper.succeed();
		});
	}

	/**
	 * The switch: "raiderCultures pillager_warband false: no warband makes camp, and a standing camp sends no raids." With
	 * the camp standing and the warband's attack due tonight, switched on it comes (the control); switched off nothing of
	 * the warband comes, nobody leaves the camp, and the village's next raid isn't the warband's.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaWarband1123dSwitch"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWarband1123dSwitch")
	public void aStandingCampSwitchedOffInTheConfigSendsNoRaids(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.TOWN);
			warband(helper);
			at(level, 110, 1000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), WARBAND);
			helper.assertTrue(lair != null, "no war camp");
			// Switched off: the attack due tonight doesn't come.
			Threats.DISABLED = Set.of("pillager_warband");
			at(level, 110, 15000);
			long today = Chronicle.day(level);
			ThreatData.get(level).rolled(hall, today - 1, new ThreatData.Attack(today, WARBAND, 0, 14000));
			VillageRaids.tick(level, hall, 12, 0, today - 10, d -> { });
			helper.assertTrue(VillageRaids.active(hall).isEmpty() && VillageRaids.raiders(level, hall).isEmpty(), "a camp switched off sent a raid: "
				+ VillageRaids.raiders(level, hall));
			helper.assertTrue(lair(helper, hall).out() == 0, "the camp sent its band out while switched off: " + lair(helper, hall));
			helper.assertTrue(!VillageRaids.cultureFor(level, hall, 12, RandomSource.create(4L)).map(Culture::id).equals(Optional.of(WARBAND)),
				"the next raid on the village is the switched-off warband's");
			// Switched on again (the control): the warband's attack due tonight comes.
			Threats.DISABLED = Set.of();
			ThreatData.get(level).forgetClock(hall);
			ThreatData.get(level).rolled(hall, today - 1, new ThreatData.Attack(today, WARBAND, 0, 14000));
			VillageRaids.tick(level, hall, 12, 0, today - 10, d -> { });
			helper.assertTrue(VillageRaids.active(hall).isPresent() && lair(helper, hall).out() > 0, "the control: the warband switched on didn't come: " + lair(helper, hall));
			helper.succeed();
		});
	}

	/**
	 * "sieges off: the warband's raids are plain raids" and "Every raid of the warband brings its ravagers and evoker, siege
	 * or not." With sieges off a City raid still brings two ravager rams and the evoker, the Warlord still leads it, and no
	 * siege is laid.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaWarband1123dSiegesOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWarband1123dSiegesOff")
	public void withSiegesOffTheWarbandStillBringsItsRavagersAndEvoker(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.CITY);
			Culture culture = warband(helper);
			at(level, 120, 1000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), WARBAND);
			helper.assertTrue(lair != null, "no war camp");
			Sieges.ENABLED = false;
			at(level, 120, 15000);
			helper.assertTrue(VillageRaids.start(level, hall, 12, 0, Double.NaN, culture, RandomSource.create(14L)) != null, "no raid with sieges off");
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(count(raiders, EntityType.RAVAGER) == 2 && count(raiders, EntityType.EVOKER) == 1, "the extras with sieges off: " + raiders);
			helper.assertTrue(raiders.stream().anyMatch(m -> m.getUUID().equals(lair.captain())), "the Warlord didn't lead with sieges off");
			helper.assertTrue(Sieges.siege(level, hall).isEmpty(), "a siege was laid with sieges off");
			helper.assertTrue(lair(helper, hall).out() == 6, "the extras or the Warlord were taken from the band: " + lair(helper, hall));
			helper.succeed();
		});
	}

	/**
	 * A war party at the war camp (docs/wiki/war-party.md, 32.13): "At a City, the captain leaves his fire and fights at
	 * the front." By a Town the band goes for the war party and the Warlord keeps to his fire; by a City he goes for it
	 * too, his leash off. The band turns out "until all its strength is spent": with every one of the band killed at the
	 * camp, nobody more turns out.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaWarband1123dWarParty"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWarband1123dWarParty")
	public void atAWarCampTheWarlordFightsAtTheFrontOnlyByACity(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.TOWN);
			warband(helper);
			at(level, 140, 1000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), WARBAND);
			helper.assertTrue(lair != null, "no war camp");
			Mob warlord = warlord(level, lair);
			net.minecraft.world.entity.npc.Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(24, 2, 24));
			guard.setNoAi(true);
			guard.setInvulnerable(true);
			Lairs.turnOut(level, lair, 6, List.of(guard));
			List<Mob> men = men(level, lair(helper, hall));
			helper.assertTrue(!men.isEmpty() && men.stream().allMatch(m -> m.getTarget() == guard), "the band didn't go for the war party");
			helper.assertTrue(warlord.getTarget() != guard && ((net.minecraft.world.entity.PathfinderMob) warlord).hasRestriction(),
				"by a Town the Warlord left his fire for the war party");
			rank(level, hall, VillageRanks.Rank.CITY);
			Lairs.turnOut(level, lair(helper, hall), 6, List.of(guard));
			helper.assertTrue(warlord.getTarget() == guard && !((net.minecraft.world.entity.PathfinderMob) warlord).hasRestriction(),
				"by a City the Warlord didn't fight at the front: target " + warlord.getTarget());
			// All its strength spent at the camp: nobody more turns out.
			for (int round = 0; round < 4 && lair(helper, hall).strength() > 0; round++) {
				for (Mob m : men(level, lair(helper, hall))) {
					m.hurt(level.damageSources().fellOutOfWorld(), 1000f);
				}
				Lairs.turnOut(level, lair(helper, hall), 6, List.of(guard));
			}
			Lairs.Lair spent = lair(helper, hall);
			helper.assertTrue(spent.strength() == 0, "the band killed at the camp isn't spent: " + spent);
			int more = Lairs.turnOut(level, spent, 6, List.of(guard));
			helper.assertTrue(more == 0 && men(level, spent).isEmpty(), "with its strength spent " + more + " more turned out");
			helper.succeed();
		});
	}

	/**
	 * The lines a player reads, word for word as the wiki gives them: the chronicle's "Warlord Grath Ironmaw and his
	 * pillager warband raised a war camp 92 blocks south" (his name one of the twenty, the distance and side the camp's),
	 * the horn's "Warlord Grath Ironmaw himself leads 9 of his warband against Thornholm! Bring him down and his war camp
	 * breaks.", and the Warlord's name over his head "Warlord Kael Blackthorne".
	 */
	//$ gametest_ticks_batch AREA '100' '"qaWarband1123dLines"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWarband1123dLines")
	public void theWarbandsLinesReadAsTheWikiWritesThem(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.CITY);
			Culture culture = warband(helper);
			Language lang = Language.getInstance();
			List<String> names = new ArrayList<>();
			for (int i = 1; i <= 20; i++) {
				names.add(lang.getOrDefault("threat.aliveworkplace.pillager_warband.names." + i));
			}
			helper.assertTrue(names.stream().distinct().count() == 20 && names.stream().noneMatch(n -> n.startsWith("threat.")), "not twenty names: " + names);
			at(level, 130, 1000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), WARBAND);
			helper.assertTrue(lair != null, "no war camp");
			Mob warlord = warlord(level, lair);
			String over = warlord.getCustomName().getString();
			helper.assertTrue(over.startsWith("Warlord ") && names.contains(over.substring("Warlord ".length())), "the name over his head: " + over);
			String camp = lastChronicle(level, hall);
			Matcher m = Pattern.compile(Pattern.quote(over) + " and his pillager warband raised a war camp (\\d+) blocks (south-east|south|east)").matcher(camp);
			helper.assertTrue(m.matches(), "the camp's line in the chronicle: " + camp);
			double dx = lair.pos().getX() - hall.getX();
			double dz = lair.pos().getZ() - hall.getZ();
			int said = Integer.parseInt(m.group(1));
			helper.assertTrue(Math.abs(said - Math.sqrt(dx * dx + dz * dz)) <= 2, "the camp is said to be " + said + " blocks away, it is " + Math.sqrt(dx * dx + dz * dz));
			// The horn's line, word for word with the wiki's example.
			String horn = Lairs.message(lair, "raid_led", false, 9, net.minecraft.network.chat.Component.literal("Thornholm"), Lairs.captainName(lair)).getString();
			helper.assertTrue(horn.equals(over + " himself leads 9 of his warband against Thornholm! Bring him down and his war camp breaks."), "the horn: " + horn);
			at(level, 130, 15000);
			helper.assertTrue(VillageRaids.start(level, hall, 12, 0, Double.NaN, culture, RandomSource.create(15L)) != null, "no raid");
			String led = lastChronicle(level, hall);
			helper.assertTrue(Pattern.compile(Pattern.quote(over) + " led \\d+ of his warband against the village").matcher(led).matches(), "the raid's line in the chronicle: " + led);
			helper.succeed();
		});
	}
}
