package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.SiegeReport;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA of B983 (lane D, 2026-11-07), from the spec, not the fix: ROADMAP 32.6, sieges.md and SiegeReport's javadoc say
 * the siege report's hero is "the guard with the most kills, the first to get there on a tie". These check the corners
 * the bug's own test (QaPlunderReport1106dBugTests) leaves: a lead lost to a tie and won back, a tie where the first
 * killer was also first to the top, a three-way tie, the order kept across a save and load in the middle of the siege,
 * and kills by a player in between, which never make a guard the hero.
 */
public class QaSiegeHeroTie1107dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	private static Villager guard(GameTestHelper helper, int x, int z, String name) {
		BlockPos post = new BlockPos(x, 2, z + 2);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setNoAi(true);
		v.setCustomName(net.minecraft.network.chat.Component.literal(name));
		return v;
	}

	private static Mob raider(GameTestHelper helper, int x, int z) {
		ServerLevel level = helper.getLevel();
		Mob mob = EntityType.HUSK.create(level);
		BlockPos p = helper.absolutePos(new BlockPos(x, 2, z));
		mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		mob.addTag(VillageRaids.TAG);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		return mob;
	}

	/** A siege at the test's hall with {@code came} raiders, everything put back after the test. */
	private static ThreatData.Siege siege(GameTestHelper helper, int came) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData data = ThreatData.get(level);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			data.forgetSiege(hall);
			data.forgetPlunder(hall);
			data.forgetPast(hall);
			Sieges.forget();
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
		});
		data.forgetPlunder(hall);
		data.forgetPast(hall);
		ThreatData.Siege siege = new ThreatData.Siege(hall, level.getGameTime(), level.getDayTime() + 12000);
		siege.came = came;
		data.beginSiege(siege);
		return siege;
	}

	private static List<Mob> raiders(GameTestHelper helper, int count) {
		List<Mob> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(raider(helper, 6 + i, 8 + (i % 2)));
		}
		return list;
	}

	/** {@code killers[i]} kills raider i, in order. */
	private static void kills(GameTestHelper helper, List<Mob> raiders, Villager... killers) {
		for (int i = 0; i < killers.length; i++) {
			raiders.get(i).hurt(helper.getLevel().damageSources().mobAttack(killers[i]), 1000f);
		}
	}

	private static void assertHero(GameTestHelper helper, ThreatData.Siege siege, Villager expected, int count, String story) {
		String report = SiegeReport.text(helper.getLevel(), siege).getString();
		String name = expected.getCustomName().getString();
		helper.assertTrue(expected.getUUID().equals(SiegeReport.hero(siege)) && report.endsWith("Hero of the night: " + name + ", with " + count + " kills."),
			story + ": expected " + name + " with " + count + " kills; the kills " + siege.kills + ", the report: \"" + report + "\"");
	}

	/** Aldred 1, Brannoc 2 (ahead), Aldred 2 (tied, Brannoc still first there), Aldred 3: Aldred, the most, wins outright. */
	//$ gametest_ticks_batch AREA '40' '"qaHeroLeadWonBack1107d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaHeroLeadWonBack1107d")
	public void aGuardWhoTiesAndThenPullsAheadIsTheHero(GameTestHelper helper) {
		ThreatData.Siege siege = siege(helper, 5);
		Villager aldred = guard(helper, 18, 20, "Aldred");
		Villager brannoc = guard(helper, 22, 20, "Brannoc");
		kills(helper, raiders(helper, 5), aldred, brannoc, brannoc, aldred, aldred);
		assertHero(helper, siege, aldred, 3, "Aldred, Brannoc, Brannoc, Aldred, Aldred");
		helper.succeed();
	}

	/** Aldred kills twice, then Brannoc twice: Aldred reached 2 first and is the hero (not whoever killed last). */
	//$ gametest_ticks_batch AREA '40' '"qaHeroFirstBoth1107d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaHeroFirstBoth1107d")
	public void onATieTheFirstToTheTopIsTheHeroEvenWhenHeAlsoKilledFirst(GameTestHelper helper) {
		ThreatData.Siege siege = siege(helper, 4);
		Villager aldred = guard(helper, 18, 20, "Aldred");
		Villager brannoc = guard(helper, 22, 20, "Brannoc");
		kills(helper, raiders(helper, 4), aldred, aldred, brannoc, brannoc);
		assertHero(helper, siege, aldred, 2, "Aldred, Aldred, Brannoc, Brannoc");
		helper.succeed();
	}

	/** Aldred, Brannoc, Carys, Carys (first to 2), Brannoc, Aldred: three tied on 2, Carys got there first. */
	//$ gametest_ticks_batch AREA '40' '"qaHeroThreeWay1107d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaHeroThreeWay1107d")
	public void aThreeWayTieGoesToTheOneWhoGotThereFirst(GameTestHelper helper) {
		ThreatData.Siege siege = siege(helper, 6);
		Villager aldred = guard(helper, 18, 20, "Aldred");
		Villager brannoc = guard(helper, 22, 20, "Brannoc");
		Villager carys = guard(helper, 26, 20, "Carys");
		kills(helper, raiders(helper, 6), aldred, brannoc, carys, carys, brannoc, aldred);
		assertHero(helper, siege, carys, 2, "Aldred, Brannoc, Carys, Carys, Brannoc, Aldred");
		helper.succeed();
	}

	/**
	 * Aldred, Brannoc, Brannoc (first to 2), then the world is saved and loaded (the siege runs through the night, a
	 * restart can fall in it), then Aldred ties: Brannoc is still the hero.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaHeroSaved1107d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaHeroSaved1107d")
	public void theTieBreakSurvivesASaveInTheMiddleOfTheSiege(GameTestHelper helper) {
		siege(helper, 4);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData data = ThreatData.get(level);
		Villager aldred = guard(helper, 18, 20, "Aldred");
		Villager brannoc = guard(helper, 22, 20, "Brannoc");
		List<Mob> raiders = raiders(helper, 4);
		kills(helper, raiders, aldred, brannoc, brannoc);
		data.roundTrip(level.registryAccess());
		raiders.get(3).hurt(level.damageSources().mobAttack(aldred), 1000f);
		ThreatData.Siege loaded = data.siege(hall).orElse(null);
		helper.assertTrue(loaded != null && loaded.toGuards == 4, "the siege after the save: " + (loaded == null ? "gone" : loaded.kills + ", to guards " + loaded.toGuards));
		assertHero(helper, loaded, brannoc, 2, "Aldred, Brannoc, Brannoc, a save, Aldred");
		data.roundTrip(level.registryAccess());
		assertHero(helper, data.siege(hall).orElseThrow(), brannoc, 2, "the same, saved again before the morning report");
		helper.succeed();
	}

	/**
	 * Aldred kills once, a player kills two (players are never the hero), Brannoc kills once: one each, Aldred first to
	 * 1; the player's kills neither break the tie nor take the title.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaHeroPlayerKills1107d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaHeroPlayerKills1107d")
	public void aPlayersKillsInBetweenLeaveTheGuardsTieAsItWas(GameTestHelper helper) {
		ThreatData.Siege siege = siege(helper, 4);
		ServerLevel level = helper.getLevel();
		Villager aldred = guard(helper, 18, 20, "Aldred");
		Villager brannoc = guard(helper, 22, 20, "Brannoc");
		List<Mob> raiders = raiders(helper, 4);
		net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		raiders.get(0).hurt(level.damageSources().mobAttack(aldred), 1000f);
		raiders.get(1).hurt(level.damageSources().playerAttack(player), 1000f);
		raiders.get(2).hurt(level.damageSources().playerAttack(player), 1000f);
		raiders.get(3).hurt(level.damageSources().mobAttack(brannoc), 1000f);
		String report = SiegeReport.text(level, siege).getString();
		helper.assertTrue(aldred.getUUID().equals(SiegeReport.hero(siege)) && report.endsWith("Hero of the night: Aldred, with 1 kill."),
			"Aldred 1, a player 2, Brannoc 1: expected Aldred with 1 kill; the kills " + siege.kills + ", the report: \"" + report + "\"");
		helper.succeed();
	}
}
