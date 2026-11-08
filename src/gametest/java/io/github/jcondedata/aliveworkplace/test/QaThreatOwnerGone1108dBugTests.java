package io.github.jcondedata.aliveworkplace.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DecoratedPotBlock;

/**
 * B1064's kin (QA 2026-11-08): B1064 found that a raider's trident whose thrower is gone breaks a builder's dripstone
 * spike. The same gap reaches every other way a threat's shot or blast breaks blocks, because
 * {@code ThreatDamage.isThreat} only counts a projectile as a threat's while {@code getOwner()} still finds its
 * raider, and a projectile's owner reads as null once that raider was discarded (left at dawn, a call-off), killed by a
 * guard, or unloaded with its chunk. The spec (docs/wiki/sieges.md, "What threats may damage"): a threat is a raider or
 * a projectile one shot; nothing a threat does breaks a builder's block. The shots go through the blocks' own
 * projectile-hit entry points and the blast through the level's explosion, the ones the game calls.
 */
public class QaThreatOwnerGone1108dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** A raider pillager's arrow at the builders' decorated pot; the pillager is then removed for {@code reason}. */
	private static void potAfter(GameTestHelper helper, Entity.RemovalReason reason, String gone, String name) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		QaThreatOwnerGone1108dGameTests.griefing(helper);
		BlockPos pot = new BlockPos(6, 2, 6);
		helper.setBlock(pot, Blocks.DECORATED_POT);
		QaSiegeDamage1108dGameTests.finished(helper, pot, name);
		Pillager raider = QaThreatOwnerGone1108dGameTests.raider(helper);
		Arrow shot = QaThreatOwnerGone1108dGameTests.arrow(helper, raider, pot);
		raider.remove(reason);
		QaThreatOwnerGone1108dGameTests.hit(helper, shot, pot);
		helper.assertTrue(helper.getBlockState(pot).is(Blocks.DECORATED_POT),
			"a raider's arrow broke the decorated pot of a builder's finished build because its pillager " + gone
				+ " while the arrow flew (a threat's shot must never break a builder's block): " + helper.getBlockState(pot));
		helper.assertTrue(!helper.getBlockState(pot).getValue(DecoratedPotBlock.CRACKED),
			"a raider's arrow cracked the builders' decorated pot because its pillager " + gone + " while the arrow flew");
		helper.succeed();
	}

	/** A raid's pillager left at dawn (discarded) with its arrow in the air: the builders' pot must stand. */
	//$ gametest_ticks_batch AREA '40' '"qaOwnerGonePotLeft1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaOwnerGonePotLeft1108d")
	public void aRaidersArrowWhoseShooterLeftLeavesTheBuildersPotStanding(GameTestHelper helper) {
		potAfter(helper, Entity.RemovalReason.DISCARDED, "left (discarded)", "qa_gone_pot_left_1108d_");
	}

	/** A guard killed the pillager with its arrow in the air, the commonest case in a raid: the builders' pot must stand. */
	//$ gametest_ticks_batch AREA '40' '"qaOwnerGonePotKilled1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaOwnerGonePotKilled1108d")
	public void aRaidersArrowWhoseShooterWasKilledLeavesTheBuildersPotStanding(GameTestHelper helper) {
		potAfter(helper, Entity.RemovalReason.KILLED, "was killed", "qa_gone_pot_killed_1108d_");
	}

	/** The pillager's chunk unloaded (it still exists, saved) with its arrow in the air: the builders' pot must stand. */
	//$ gametest_ticks_batch AREA '40' '"qaOwnerGonePotUnloaded1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaOwnerGonePotUnloaded1108d")
	public void aRaidersArrowWhoseShooterUnloadedLeavesTheBuildersPotStanding(GameTestHelper helper) {
		potAfter(helper, Entity.RemovalReason.UNLOADED_TO_CHUNK, "was unloaded with its chunk", "qa_gone_pot_unloaded_1108d_");
	}

	/**
	 * The chorus flower path (vanilla breaks it with {@code destroyBlock(pos, true, projectile)}, the threat-grief guard's
	 * entry point): a raider's arrow whose pillager left breaks the builders' flower.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaOwnerGoneChorus1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaOwnerGoneChorus1108d")
	public void aRaidersArrowWhoseShooterLeftLeavesTheBuildersChorusStanding(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		QaThreatOwnerGone1108dGameTests.griefing(helper);
		BlockPos flower = new BlockPos(6, 3, 6);
		QaThreatOwnerGone1108dGameTests.chorus(helper, flower);
		QaSiegeDamage1108dGameTests.finished(helper, flower, "qa_gone_chorus_left_1108d_");
		Pillager raider = QaThreatOwnerGone1108dGameTests.raider(helper);
		Arrow shot = QaThreatOwnerGone1108dGameTests.arrow(helper, raider, flower);
		raider.discard();
		QaThreatOwnerGone1108dGameTests.hit(helper, shot, flower);
		helper.assertTrue(helper.getBlockState(flower).is(Blocks.CHORUS_FLOWER),
			"a raider's arrow broke the chorus flower of a builder's finished build because its pillager left while the arrow flew: "
				+ helper.getBlockState(flower));
		helper.succeed();
	}

	/**
	 * A raider ghast's fireball is still flying when its ghast leaves (a fireball keeps flying when its owner is gone):
	 * the burst must not blow away the builders' wool beside it.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaOwnerGoneFireball1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaOwnerGoneFireball1108d")
	public void aRaidersFireballWhoseGhastLeftSparesTheBuildersBlock(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		QaThreatOwnerGone1108dGameTests.griefing(helper);
		BlockPos wool = new BlockPos(6, 2, 6);
		BlockPos playersWool = new BlockPos(8, 2, 6);
		BlockPos burst = new BlockPos(7, 2, 6);
		helper.setBlock(wool, Blocks.WHITE_WOOL);
		helper.setBlock(playersWool, Blocks.WHITE_WOOL);
		QaSiegeDamage1108dGameTests.finished(helper, wool, "qa_gone_ball_left_1108d_");
		Ghast ghast = QaThreatOwnerGone1108dGameTests.ghast(helper);
		LargeFireball ball = QaThreatOwnerGone1108dGameTests.fireball(helper, ghast, burst);
		ghast.discard();
		QaThreatOwnerGone1108dGameTests.blast(helper, ball, burst, 2f, Level.ExplosionInteraction.MOB);
		helper.assertTrue(helper.getBlockState(playersWool).isAir(),
			"setup: the fireball didn't blow away a player's own wool beside it, so the test can't see the blast: " + helper.getBlockState(playersWool));
		helper.assertTrue(helper.getBlockState(wool).is(Blocks.WHITE_WOOL),
			"a raider ghast's fireball blew away the wool of a builder's finished build because its ghast left while the fireball flew: "
				+ helper.getBlockState(wool));
		helper.succeed();
	}
}
