package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Escorts;
import io.github.jcondedata.aliveworkplace.guard.WarParties;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * QA findings for ROADMAP 32.13, registered as each is fixed (B387, B418).
 */
public class QaWarPartyBugTests implements FabricGameTest {
	private static final String AREA = QaWarPartyGameTests.AREA;

	/**
	 * Four guards: the muster's chat and the page's line say one guard is left on the walls, so it should read "1 keeps
	 * the walls", not "1 keep the walls".
	 */
	//$ gametest_batch AREA '"qaWpBugOneKeeps"'
	@GameTest(template = AREA, batch = "qaWpBugOneKeeps")
	public void qaOneGuardLeftOnTheWallsReadsInTheSingular(GameTestHelper helper) {
		BlockPos hall = QaWarPartyGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		QaStageFiveGameTests.Client c = QaWarPartyGameTests.client(helper);
		ServerPlayer p = c.player();
		for (int i = 0; i < 4; i++) {
			QaWarPartyGameTests.guard(helper, new BlockPos(3 + i, 2, 7));
		}
		ChoiceMenu menu = QaWarPartyGameTests.defencePage(p, level, hall);
		List<String> lore = QaStageSixGameTests.lore(menu.icon(DefencePage.MUSTER));
		helper.assertTrue(lore.contains("3 guards would march, 1 keeps the walls"), "the page says: " + lore);
		p.getInventory().add(new ItemStack(ModItems.RALLY_BANNER));
		QaWarPartyGameTests.click(menu, DefencePage.MUSTER, p);
		String said = QaWarPartyGameTests.last(c.seen());
		helper.assertTrue(said.equals("3 guards gather by the hall under your Rally Banner; 1 keeps the walls"), "said: " + said);
		helper.succeed();
	}

	/**
	 * No war party: a guard of the village who is NOT on any banner (on watch near the lair) kills one of the band at
	 * the lair, then the player alone breaks it. The spec gives 10 days and the "warband is broken" mood only "when a lair
	 * breaks with a war party there"; this is the usual 5 days and no cheer.
	 */
	//$ gametest_batch AREA '"qaWpBugLoneGuard"'
	@GameTest(template = AREA, batch = "qaWpBugLoneGuard")
	public void qaALoneGuardsKillAtTheLairIsNotAWarParty(GameTestHelper helper) {
		BlockPos hall = QaWarPartyGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = QaWarPartyGameTests.client(helper).player();
		player.moveTo(helper.absolutePos(new BlockPos(11, 2, 11)).getCenter());
		Villager watch = QaWarPartyGameTests.guard(helper, new BlockPos(12, 2, 12));
		Villager baker = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Lairs.Lair lair = QaWarPartyGameTests.lair(helper, hall, QaWarPartyGameTests.LAIR);
		Escorts.scan(level.getServer());
		helper.assertFalse(Escorts.isEscorting(watch) || WarParties.marching(watch), "setup: the guard is on the march");
		QaWarPartyGameTests.bandit(helper, new BlockPos(14, 2, 12)).hurt(level.damageSources().mobAttack(watch), 1000f);
		Mob captain = (Mob) level.getEntity(lair.captain());
		captain.hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair stands");
		helper.assertTrue(Lairs.resting(level, hall) == 5, "rest " + Lairs.resting(level, hall) + " days with no war party, not 5");
		helper.assertTrue(WarParties.mood(level, baker) == null, "cheered with no war party: " + WarParties.mood(level, baker));
		helper.succeed();
	}

	/**
	 * B418's other side: a guard on a raised Rally Banner kills one of the band at the lair and is then lost (the party
	 * gone from the lair), and the player breaks it. A war party was there, so it still earns its 10 days and the cheer,
	 * also through a save and load of the kills in between.
	 */
	//$ gametest_batch AREA '"qaWpBugLostParty"'
	@GameTest(template = AREA, batch = "qaWpBugLostParty")
	public void qaAWarPartyThatLostItsGuardStillEarnsTheRest(GameTestHelper helper) {
		BlockPos hall = QaWarPartyGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = QaWarPartyGameTests.client(helper).player();
		player.moveTo(helper.absolutePos(new BlockPos(11, 2, 11)).getCenter());
		Villager marcher = QaWarPartyGameTests.guard(helper, new BlockPos(12, 2, 12));
		Villager baker = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Lairs.Lair lair = QaWarPartyGameTests.lair(helper, hall, QaWarPartyGameTests.LAIR);
		ItemStack banner = new ItemStack(ModItems.RALLY_BANNER);
		banner.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.RALLY,
			new io.github.jcondedata.aliveworkplace.guard.RallyBannerItem.Rally(List.of(marcher.getUUID()), true));
		player.getInventory().add(banner);
		Escorts.scan(level.getServer());
		helper.assertTrue(WarParties.marching(marcher), "setup: the guard isn't marching");
		QaWarPartyGameTests.bandit(helper, new BlockPos(14, 2, 12)).hurt(level.damageSources().mobAttack(marcher), 1000f);
		WarParties.Data.get(level).roundTrip(level.registryAccess());
		marcher.discard();
		player.getInventory().clearContent();
		helper.assertTrue(WarParties.at(level, lair).isEmpty(), "setup: a war party is still at the lair");
		Mob captain = (Mob) level.getEntity(lair.captain());
		captain.hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair stands");
		helper.assertTrue(Lairs.resting(level, hall) == WarParties.REST_DAYS, "rest " + Lairs.resting(level, hall) + " days after the war party's kill, not 10");
		helper.assertTrue(WarParties.mood(level, baker) != null, "no cheer after the war party's kill");
		helper.succeed();
	}
}
