package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane D, 2026-11-23), ROADMAP 32.10 from docs/wiki/piglins.md, for promises the earlier piglin tests leave out:
 * the outpost's strength (10 when raised, 2 more a day, at most 16, kept through a save and load) and its band at home
 * (at most 8, the Warlord aside); the Warlord's name, one of twenty, over his head and in the camp's chat line word for
 * word as the spec quotes it, and every raider called "Piglin raider"; and the switch: with {@code piglin_incursion}
 * off, a standing outpost sends no raid.
 */
public class QaPiglins1123dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PIGLINS = AliveWorkplace.id("piglin_incursion");

	private static Culture piglins(GameTestHelper helper) {
		Optional<Culture> culture = Threats.get(PIGLINS);
		helper.assertTrue(culture.isPresent(), "piglin_incursion.json didn't load");
		return culture.get();
	}

	/** A Town's hall at (1, 2, 1) with a village radius of 20; the outpost and everything else put back when the test ends. */
	private static BlockPos town(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		boolean lairs = Lairs.ENABLED;
		Lairs.ENABLED = true;
		Set<String> off = Threats.DISABLED;
		BlockPos hall = helper.absolutePos(new BlockPos(1, 2, 1));
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> true).forEach(ItemEntity::discard);
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetPlunder(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Lairs.forget(level);
			Lairs.ENABLED = lairs;
			Threats.DISABLED = off;
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
		});
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		return hall;
	}

	/**
	 * "Strength. 10 when the outpost is raised, 2 more a day, at most 16; at most 8 stand at the outpost": raised, it is
	 * 10 and 8 of its band stand there besides the Warlord; a day later 12 (the same dawn twice grows it once), three
	 * days later 16, kept through a save and load, and thirty days later still 16.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1123dOutpostStrength"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1123dOutpostStrength")
	public void theOutpostGrowsTwoADayUpToSixteenAndKeepsEightAtHome(GameTestHelper helper) {
		BlockPos hall = town(helper);
		ServerLevel level = helper.getLevel();
		piglins(helper);
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(new BlockPos(15, 1, 15)), PIGLINS);
			helper.assertTrue(lair != null && lair.strength() == 10, "the outpost when raised: " + lair);
			List<Mob> band = Lairs.band(level, lair);
			long men = band.stream().filter(m -> !m.getUUID().equals(lair.captain())).count();
			boolean warlord = band.stream().anyMatch(m -> m.getUUID().equals(lair.captain()));
			helper.assertTrue(men == 8 && warlord, "at the outpost: " + men + " of the band (8 wanted), Warlord there " + warlord);
			long day = lair.grown();
			Lairs.dawn(level, hall, day + 1);
			helper.assertTrue(Lairs.near(level, hall).get().strength() == 12, "a day later: " + Lairs.near(level, hall).get());
			Lairs.dawn(level, hall, day + 1);
			helper.assertTrue(Lairs.near(level, hall).get().strength() == 12, "the same dawn twice: " + Lairs.near(level, hall).get());
			Lairs.dawn(level, hall, day + 3);
			helper.assertTrue(Lairs.near(level, hall).get().strength() == 16, "three days later: " + Lairs.near(level, hall).get());
			Lairs.Data.get(level).roundTrip(level.registryAccess());
			helper.assertTrue(Lairs.near(level, hall).get().strength() == 16, "after a save and load: " + Lairs.near(level, hall));
			Lairs.dawn(level, hall, day + 4);
			Lairs.dawn(level, hall, day + 30);
			helper.assertTrue(Lairs.near(level, hall).get().strength() == 16, "past the cap: " + Lairs.near(level, hall).get());
			helper.succeed();
		});
	}

	/**
	 * "His name over his head (one of twenty: 'Warlord Vorgash Goldmaw')", "Chat says so ('A portal has been lit beyond
	 * Thornholm: Warlord Grukk the Gilded has raised a blackstone outpost 60 blocks west...')", and "the raiders, all
	 * called 'Piglin raider'": the twenty names from the first to the twentieth, the twenty-first back to the first; the
	 * raised Warlord's name over his head is his lair's; the camp's chat line as quoted; every roster line's raider named.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1123dWarlordName"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1123dWarlordName")
	public void theWarlordIsOneOfTwentyAndTheChatReadsAsTheSpecQuotes(GameTestHelper helper) {
		BlockPos hall = town(helper);
		ServerLevel level = helper.getLevel();
		Culture culture = piglins(helper);
		helper.runAfterDelay(2, () -> {
			Lairs.Lair grukk = new Lairs.Lair(hall, hall, UUID.randomUUID(), 0, PIGLINS, 10, 0, 0, 0, 0);
			helper.assertTrue(Lairs.captainName(grukk).getString().equals("Warlord Grukk the Gilded"), "name 1: " + Lairs.captainName(grukk).getString());
			Lairs.Lair vorgash = new Lairs.Lair(hall, hall, UUID.randomUUID(), 0, PIGLINS, 10, 0, 0, 0, 15);
			helper.assertTrue(Lairs.captainName(vorgash).getString().equals("Warlord Vorgash Goldmaw"), "name 16: " + Lairs.captainName(vorgash).getString());
			Lairs.Lair last = new Lairs.Lair(hall, hall, UUID.randomUUID(), 0, PIGLINS, 10, 0, 0, 0, 19);
			helper.assertTrue(Lairs.captainName(last).getString().equals("Warlord Grommash Cinderhide"), "name 20: " + Lairs.captainName(last).getString());
			Lairs.Lair wrap = new Lairs.Lair(hall, hall, UUID.randomUUID(), 0, PIGLINS, 10, 0, 0, 0, 20);
			helper.assertTrue(Lairs.captainName(wrap).getString().equals("Warlord Grukk the Gilded"), "name 21: " + Lairs.captainName(wrap).getString());
			for (int i = 0; i < 20; i++) {
				String name = Lairs.captainName(new Lairs.Lair(hall, hall, UUID.randomUUID(), 0, PIGLINS, 10, 0, 0, 0, i)).getString();
				helper.assertTrue(name.startsWith("Warlord ") && !name.contains("threat.aliveworkplace"), "name " + (i + 1) + " untranslated: " + name);
			}

			String where = VillageHallScreen.where(hall, hall.west(60)).getString();
			helper.assertTrue(where.equals("60 blocks west"), "where: " + where);
			String chat = Lairs.message(grukk, "camp", true, Component.literal("Thornholm"), VillageHallScreen.where(hall, hall.west(60)), Lairs.captainName(grukk)).getString();
			String quoted = "A portal has been lit beyond Thornholm: Warlord Grukk the Gilded has raised a blackstone outpost 60 blocks west";
			helper.assertTrue(chat.startsWith(quoted), "the camp's chat line: " + chat);

			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(new BlockPos(15, 1, 15)), PIGLINS);
			helper.assertTrue(lair != null, "no outpost");
			Mob warlord = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(warlord != null && warlord.getCustomName() != null && warlord.isCustomNameVisible()
				&& warlord.getCustomName().getString().equals(Lairs.captainName(lair).getString()),
				"the name over the Warlord's head: " + (warlord == null ? null : warlord.getCustomName()) + ", the lair's " + Lairs.captainName(lair).getString());

			for (Culture.Member member : culture.roster()) {
				Mob mob = Threats.create(level, member);
				helper.assertTrue(mob != null, "no " + member.entity());
				BlockPos p = helper.absolutePos(new BlockPos(5, 2, 5));
				mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
				mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
				Threats.outfit(level, mob, culture, member);
				String name = mob.getCustomName() == null ? null : mob.getCustomName().getString();
				helper.assertTrue("Piglin raider".equals(name), member.entity() + " is called " + name);
				mob.discard();
			}
			helper.succeed();
		});
	}

	/**
	 * Switches: "raiderCultures piglin_incursion false: ... one that stands sends no raids." With the outpost standing,
	 * a raid on the village is the piglins' while they are on; switched off, a raid over many seeded rolls is never theirs.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1123dSwitchOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1123dSwitchOff")
	public void aStandingOutpostSendsNoRaidWhenThePiglinsAreSwitchedOff(GameTestHelper helper) {
		BlockPos hall = town(helper);
		ServerLevel level = helper.getLevel();
		piglins(helper);
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(new BlockPos(15, 1, 15)), PIGLINS);
			helper.assertTrue(lair != null, "no outpost");
			Optional<Culture> on = VillageRaids.cultureFor(level, hall, 10, RandomSource.create(1123L));
			helper.assertTrue(on.isPresent() && on.get().id().equals(PIGLINS), "with the outpost standing, the raid isn't the piglins': " + on.map(Culture::id));
			Threats.DISABLED = Set.of("piglin_incursion");
			RandomSource random = RandomSource.create(1123L);
			for (int i = 0; i < 200; i++) {
				Optional<Culture> off = VillageRaids.cultureFor(level, hall, 10, random);
				helper.assertFalse(off.isPresent() && off.get().id().equals(PIGLINS), "switched off, roll " + i + " still sends the piglins");
			}
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the standing outpost went away (the spec says it stands and sends no raids)");
			helper.succeed();
		});
	}
}
