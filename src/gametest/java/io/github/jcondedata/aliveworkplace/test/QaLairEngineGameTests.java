package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane D, 2026-10-24), ROADMAP 32.2 and 32.3 from the spec, on what LairGameTests and ThreatGameTests leave: a lair
 * makes camp 80-104 blocks from the hall on dry ground, at most one per village, never while the village rests from
 * the last one (5 days) and never for a culture the config switches off; the band at home is its strength but at most
 * 8, and a raid is never bigger than today's raid size; another culture's lair comes back from a save with its
 * captain, name and band (and an older file without its strength takes the culture's), and its captain's fall is told
 * in the chronicle with its own lines; every line a lair culture shows a player is written.
 */
public class QaLairEngineGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(12, 1, 12);
	private static final long DAY = 24000;
	private static final ResourceLocation WARBAND = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_warband");

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/**
	 * A random whose every float is 0 (any chance above 0 comes) but whose doubles and ints are seeded, so each seed
	 * looks for a site in another direction (in a crowded suite one direction may be all other tests' ground).
	 */
	private static RandomSource comes(long seed) {
		return new LegacyRandomSource(seed) {
			@Override
			public float nextFloat() {
				return 0f;
			}
		};
	}

	/** The hall's round with every seed up to {@code seeds}, till a lair stands; whether one does. */
	private static boolean roundsTillALair(ServerLevel level, BlockPos hall, int seeds) {
		for (long seed = 0; seed < seeds && Lairs.near(level, hall).isEmpty(); seed++) {
			Lairs.round(level, hall, true, comes(seed));
		}
		return Lairs.near(level, hall).isPresent();
	}

	/** Why no lair came: the round's conditions and the sites the seeds would find. */
	private static String why(ServerLevel level, BlockPos hall) {
		int sites = 0;
		for (long seed = 0; seed < 40; seed++) {
			if (Lairs.site(level, hall, Lairs.NEAR, Lairs.FAR, RandomSource.create(seed)) != null) {
				sites++;
			}
		}
		return "comer=" + Lairs.comer(level, hall, true, comes(1)) + " chance=" + Lairs.dailyChance(level, hall) + " quiet=" + Threats.quiet(level, hall)
			+ " rest=" + Lairs.resting(level, hall) + " rank=" + VillageRanks.of(level, hall) + " sites=" + sites + "/40";
	}

	/** A hall (village radius 16, a Town) with four villagers; config, clock and threats put back at the end. */
	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		Lairs.forget(level);
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		Set<String> off = Threats.DISABLED;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		ThreatData.get(level).forgetClock(hall);
		Leftovers.after(helper, () -> {
			for (Lairs.Lair lair : Lairs.all(level)) {
				if (lair.hall().equals(hall)) {
					Lairs.leave(level, lair); // (a camp out in the ring is packed up)
				}
			}
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(130, 48, 130),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Lairs.forget(level);
			BanditCamps.forget(level);
			VillageRaids.forget();
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
			Threats.DISABLED = off;
			level.setDayTime(time);
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		return hall;
	}

	/** Loads the ground 120 blocks round the hall, where a lair may make camp, and makes the village a Town (bandits come). */
	private static void loadRing(ServerLevel level, BlockPos hall) {
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		for (int cx = (hall.getX() - 124) >> 4; cx <= (hall.getX() + 124) >> 4; cx++) {
			for (int cz = (hall.getZ() - 124) >> 4; cz <= (hall.getZ() + 124) >> 4; cz++) {
				level.getChunk(cx, cz);
			}
		}
	}

	private static double flat(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static List<Mob> men(ServerLevel level, Lairs.Lair lair) {
		List<Mob> men = new ArrayList<>(Lairs.band(level, lair));
		men.removeIf(m -> m.getUUID().equals(lair.captain()));
		return men;
	}

	private static String last(ServerLevel level, BlockPos hall) {
		List<Chronicle.Entry> chronicle = ((VillageHallBlockEntity) level.getBlockEntity(hall)).chronicle();
		return chronicle.isEmpty() ? "" : chronicle.get(chronicle.size() - 1).text().getString();
	}

	private static long lairsOf(ServerLevel level, BlockPos hall) {
		return Lairs.all(level).stream().filter(l -> l.hall().equals(hall)).count();
	}

	/**
	 * Where a lair makes camp: every site found for it is 80-104 blocks from the hall (the spec's ring; a block's
	 * rounding allowed), on the ground, and ground under water is no site.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLairSite"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLairSite")
	public void aLairsSiteIsEightyToAHundredAndFourBlocksOutOnDryGround(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			loadRing(level, hall);
			int found = 0;
			for (long seed = 0; seed < 40; seed++) {
				BlockPos site = Lairs.site(level, hall, Lairs.NEAR, Lairs.FAR, RandomSource.create(seed));
				if (site == null) {
					continue;
				}
				found++;
				double d = flat(site, hall);
				helper.assertTrue(d >= Lairs.NEAR - 1.5 && d <= Lairs.FAR + 0.5, "seed " + seed + ": a site " + d + " blocks from the hall: " + site);
				helper.assertTrue(level.getBlockState(site).isSolid() && level.getBlockState(site.above()).isAir(), "seed " + seed + ": the site isn't the ground: " + site);
			}
			helper.assertTrue(found >= 30, "only " + found + " of 40 sites found on open flat ground");
			// The same ground with a pond on it is no camp.
			BlockPos ground = Lairs.site(level, hall, Lairs.NEAR, Lairs.FAR, RandomSource.create(3L));
			helper.assertTrue(ground != null && Lairs.fits(level, ground, false, 7, 6), "the ground doesn't fit: " + ground);
			var was = level.getBlockState(ground.offset(2, 0, 1));
			level.setBlockAndUpdate(ground.offset(2, 0, 1), Blocks.WATER.defaultBlockState());
			try {
				helper.assertFalse(Lairs.fits(level, ground, false, 7, 6), "a camp fits on a pond");
			} finally {
				level.setBlockAndUpdate(ground.offset(2, 0, 1), was);
			}
			helper.succeed();
		});
	}

	/**
	 * The rest: a camp broken up by a player leaves the village 5 days' rest. On each of those days no lair comes,
	 * however the dice fall and however often the hall's round runs; the day the rest ends one does, 80-104 blocks out,
	 * and only one, whatever more rounds roll.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaLairRest"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaLairRest")
	public void noLairComesWhileTheVillageRestsAndOnlyOneAfter(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		helper.runAfterDelay(2, () -> {
			at(level, 100, 1000);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
			Lairs.Lair camp = Lairs.near(level, hall).orElseThrow();
			Mob chief = (Mob) level.getEntity(camp.captain());
			chief.hurt(level.damageSources().playerAttack(player), 1000f);
			helper.assertTrue(Lairs.near(level, hall).isEmpty() && Lairs.resting(level, hall) == Lairs.REST_DAYS, "the rest: " + Lairs.resting(level, hall));
			loadRing(level, hall);
			for (long day = 100; day < 105; day++) {
				at(level, day, 2000);
				roundsTillALair(level, hall, 12);
				helper.assertTrue(Lairs.near(level, hall).isEmpty(), "a lair came on day " + day + " with " + Lairs.resting(level, hall) + " days of rest left");
			}
			at(level, 105, 2000);
			helper.assertTrue(Lairs.resting(level, hall) == 0, "the rest didn't end after 5 days: " + Lairs.resting(level, hall));
			helper.assertTrue(roundsTillALair(level, hall, 40), "no lair came the day the rest ended: " + why(level, hall));
			Lairs.Lair next = Lairs.near(level, hall).orElseThrow();
			helper.assertTrue(next.culture().equals(Threats.BANDITS), "the lair that came: " + next);
			double d = flat(next.pos(), hall);
			helper.assertTrue(d >= Lairs.NEAR - 1.5 && d <= Lairs.FAR + 0.5, "the new camp is " + d + " blocks from the hall");
			for (int round = 0; round < 6; round++) {
				at(level, 105, 3000 + round * 3000);
				Lairs.round(level, hall, true, comes(round));
			}
			Lairs.Lair still = Lairs.near(level, hall).orElse(null);
			helper.assertTrue(still != null && still.captain().equals(next.captain()) && still.pos().equals(next.pos()) && lairsOf(level, hall) == 1,
				"a second lair by one village: " + Lairs.all(level));
			helper.succeed();
		});
	}

	/**
	 * The config: bandits switched off in {@code raiderCultures} (read from the config file as a player writes it) make
	 * no camp on any day, however the dice fall; switched back on they do.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaLairConfig"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaLairConfig")
	public void banditsSwitchedOffInTheConfigNeverMakeCamp(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			loadRing(level, hall);
			try {
				WorkplaceConfig.parse("{\"raiderCultures\": {\"bandits\": false}}").apply();
				helper.assertTrue(!Threats.enabled(Threats.BANDITS), "the config didn't switch the bandits off");
				for (long day = 110; day < 115; day++) {
					at(level, day, 6000);
					roundsTillALair(level, hall, 12);
					helper.assertTrue(Lairs.near(level, hall).isEmpty(), "bandits made camp on day " + day + " with their culture off: " + Lairs.near(level, hall));
				}
				WorkplaceConfig.parse("{\"raiderCultures\": {\"bandits\": true}}").apply();
				helper.assertTrue(Threats.enabled(Threats.BANDITS), "the config didn't switch the bandits back on");
				helper.assertTrue(roundsTillALair(level, hall, 40), "switched back on, no camp came: " + why(level, hall));
				helper.assertTrue(Lairs.near(level, hall).orElseThrow().culture().equals(Threats.BANDITS), "the camp that came: " + Lairs.near(level, hall));
			} finally {
				new WorkplaceConfig().apply(); // (the defaults every test starts from, as the other config tests leave them)
			}
			helper.succeed();
		});
	}

	/**
	 * The band at home is the lair's strength, but never more than 8 (strength 10: 8 stand at the camp); a raid from it
	 * is never bigger than today's raid size (5 from a village of 8), and those left at home are the rest.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaLairHomeMax"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaLairHomeMax")
	public void theBandAtHomeIsNeverMoreThanEightNorARaidMoreThanItsSize(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			at(level, 120, 1000);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			Lairs.setStrength(level, hall, 10);
			Lairs.round(level, hall, false);
			Lairs.round(level, hall, false);
			helper.assertTrue(Lairs.near(level, hall).orElseThrow().strength() == 10, "the strength: " + Lairs.near(level, hall));
			helper.assertTrue(men(level, lair).size() == 8, "a band of strength 10 at home: " + men(level, lair).size());
			at(level, 120, 15000);
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 8, 0);
			helper.assertTrue(raid != null && raid.raiders() == 5, "a raid from a lair of 10 on a village of 8: " + raid);
			Lairs.Lair out = Lairs.near(level, hall).orElseThrow();
			helper.assertTrue(out.out() == 5 && out.home() == 5, "with 5 out: " + out);
			Lairs.round(level, hall, false);
			helper.assertTrue(men(level, out).size() == 5, "left at the camp with 5 out: " + men(level, out).size());
			helper.succeed();
		});
	}

	/**
	 * Another culture's lair (the test pack's warband) comes back from a save and load the same: culture, strength,
	 * captain and his name; the hall's rounds after it muster the same band, not a second one. A file written before a
	 * lair kept its strength gives it the strength from its culture's file (7), not the bandits' 6.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaLairWarbandSave"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaLairWarbandSave")
	public void aWarbandsLairComesBackFromASaveWithOneBand(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			at(level, 130, 15000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), WARBAND);
			helper.assertTrue(lair != null && lair.strength() == 7, "the warband's lair: " + lair);
			String name = Lairs.captainName(lair).getString();
			Lairs.Data data = Lairs.Data.get(level);
			data.roundTrip(level.registryAccess());
			Lairs.Lair back = Lairs.near(level, hall).orElseThrow();
			helper.assertTrue(back.equals(lair) && Lairs.captainName(back).getString().equals(name), "after a save and load: " + back + ", was " + lair);
			Mob captain = (Mob) level.getEntity(back.captain());
			helper.assertTrue(captain != null && captain.isAlive() && captain.getCustomName() != null && captain.getCustomName().getString().equals(name),
				"his name over his head after the load: " + (captain == null ? null : captain.getCustomName()));
			for (int round = 0; round < 4; round++) {
				at(level, 130, 16000 + round * 500);
				Lairs.round(level, hall, false);
			}
			helper.assertTrue(men(level, back).size() == 7 && Lairs.band(level, back).size() == 8, "the band after the rounds: " + men(level, back).size()
				+ " men, " + Lairs.band(level, back).size() + " with the captain");
			helper.assertTrue(level.getEntitiesOfClass(Mob.class, new AABB(back.pos()).inflate(48), m -> m.getTags().contains(Lairs.CAPTAIN_TAG)).size() == 1,
				"two captains at one lair");
			// An older file, without the lair's strength: the culture's own.
			CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
			saved.getList("camps", 10).getCompound(0).remove("strength");
			data.read(saved);
			Lairs.Lair old = Lairs.near(level, hall).orElseThrow();
			helper.assertTrue(old.culture().equals(WARBAND) && old.strength() == 7 && old.captain().equals(lair.captain()), "a warband's lair saved without its strength: " + old);
			helper.succeed();
		});
	}

	/**
	 * The warband's captain falls to a player: the lair is broken up, its band gone, 5 days' rest, and the chronicle
	 * names the player and the captain in a raiders' line, not a bandits' one.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaLairWarbandFall"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaLairWarbandFall")
	public void aWarbandCaptainsFallIsToldInItsOwnWords(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		helper.runAfterDelay(2, () -> {
			at(level, 140, 15000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), WARBAND);
			helper.assertTrue(lair != null, "no lair");
			String name = Lairs.captainName(lair).getString();
			Mob captain = (Mob) level.getEntity(lair.captain());
			captain.hurt(level.damageSources().playerAttack(player), 1000f);
			helper.assertTrue(Lairs.near(level, hall).isEmpty() && Lairs.resting(level, hall) == Lairs.REST_DAYS, "the lair still stands, or no rest");
			helper.assertTrue(Lairs.band(level, lair).isEmpty(), "the band didn't scatter: " + Lairs.band(level, lair).size());
			String line = last(level, hall);
			helper.assertTrue(line.contains(name) && line.contains(player.getGameProfile().getName()) && line.contains("broke up"), "the chronicle's line: " + line);
			helper.assertFalse(line.toLowerCase().contains("bandit"), "a warband's fall told as the bandits': " + line);
			helper.succeed();
		});
	}

	private static String key(MutableComponent line) {
		return line.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	/**
	 * Every line a lair's culture shows a player is written in en_us: its name, its lair's name, its captain's title
	 * and 20 different names, and its camp, raid and broken-up lines in chat and the chronicle, plain and named; the
	 * broken-up lines carry the village, the captain and whoever broke it up, with no placeholder left.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyLairCulturesLinesAreWritten(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> missing = new ArrayList<>();
		int cultures = 0;
		for (Culture culture : Threats.all()) {
			ResourceLocation id = culture.id();
			if (!id.getNamespace().equals("aliveworkplace") || culture.lair().isEmpty()) {
				continue;
			}
			cultures++;
			String base = "threat." + id.getNamespace() + "." + id.getPath();
			for (String k : List.of(base + ".name", base + ".lair")) {
				if (!lang.has(k)) {
					missing.add(k);
				}
			}
			Optional<Culture.Captain> captain = culture.captain();
			Lairs.Lair lair = new Lairs.Lair(BlockPos.ZERO, BlockPos.ZERO, new UUID(1, 2), 1, id, 6, 1, 0, 0, 3);
			boolean named = Lairs.named(lair);
			if (captain.isPresent()) {
				captain.get().title().filter(t -> !lang.has(t)).ifPresent(missing::add);
				if (captain.get().names().isPresent()) {
					Set<String> names = new HashSet<>();
					for (int i = 1; i <= Lairs.NAMES; i++) {
						String k = captain.get().names().get() + "." + i;
						if (!lang.has(k)) {
							missing.add(k);
						}
						names.add(lang.getOrDefault(k));
					}
					helper.assertTrue(names.size() == Lairs.NAMES, id + " has " + names.size() + " different names");
				}
			}
			String captainName = Lairs.captainName(lair).getString();
			helper.assertFalse(captainName.contains("threat.") || captainName.contains("entity.") || captainName.contains("%"), id + "'s captain: " + captainName);
			for (boolean n : named ? new boolean[] {false, true} : new boolean[] {false}) {
				for (String event : List.of("camp", "broken")) {
					String k = key(Lairs.message(lair, event, n));
					if (!lang.has(k)) {
						missing.add(id + " message " + event + (n ? "_named" : "") + ": " + k);
					}
				}
				for (String event : List.of("camp", "broken", "broken_by")) {
					String k = key(Lairs.chronicle(lair, event, n));
					if (!lang.has(k)) {
						missing.add(id + " chronicle " + event + (n ? "_named" : "") + ": " + k);
					}
				}
			}
			if (named) {
				for (String event : List.of("raid_named")) {
					String m = key(Lairs.message(lair, "raid", true));
					String c = key(Lairs.chronicle(lair, "raid", true));
					if (!lang.has(m)) {
						missing.add(id + " message " + event + ": " + m);
					}
					if (!lang.has(c)) {
						missing.add(id + " chronicle " + event + ": " + c);
					}
				}
			}
			Component village = Component.literal("Oakhollow");
			Component captainLine = Lairs.captainName(lair);
			String told = Lairs.message(lair, "broken", named, village, captainLine).getString();
			helper.assertTrue(told.contains("Oakhollow") && (!named || told.contains(captainName)) && !told.contains("%"), id + "'s broken-up message: " + told);
			String byLine = Lairs.chronicle(lair, "broken_by", named, Component.literal("Steve"), captainLine).getString();
			helper.assertTrue(byLine.contains("Steve") && (!named || byLine.contains(captainName)) && !byLine.contains("%"), id + "'s broken-up chronicle line: " + byLine);
		}
		helper.assertTrue(cultures >= 2, "only " + cultures + " cultures with a lair loaded");
		helper.assertTrue(missing.isEmpty(), "lines with no text: " + missing);
		helper.succeed();
	}
}
