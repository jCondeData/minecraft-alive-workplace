package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.Threats;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * B1032 (found by lane D, 2026-11-08, fixed by lane A): a raider's arrow broke a builder's decorated pot. ROADMAP 32.16a
 * / ThreatDamage's javadoc: "Nothing else a threat does breaks a builder's block"; a threat is "a raider or what one
 * shot". Vanilla's {@code DecoratedPotBlock.onProjectileHit} first sets the pot {@code cracked=true} and only then calls
 * {@code destroyBlock}; by then the pot no longer matched the build as drawn, so the threat-grief guard let the arrow
 * break it (mixin/ThreatPotMixin now asks before the crack). The shot goes through the block's own projectile-hit entry
 * point, the one an arrow in flight calls.
 */
public class QaSiegePot1108aGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** An arrow shot by {@code owner} hits the block at {@code pos} (helper coordinates), as the game's arrow does. */
	private static void shoot(GameTestHelper helper, LivingEntity owner, BlockPos pos) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(pos);
		Arrow arrow = EntityType.ARROW.create(level);
		helper.assertTrue(arrow != null, "setup: no arrow");
		arrow.setOwner(owner);
		arrow.moveTo(at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 0f, 90f);
		BlockState state = level.getBlockState(at);
		state.onProjectileHit(level, state, new BlockHitResult(at.getCenter().add(0, 0.5, 0), Direction.UP, at, false), arrow);
	}

	/**
	 * A raider pillager's arrow hits a decorated pot of a finished build: the pot must stand. The same arrow on a player's
	 * own pot beside it breaks it (the game's rules), and a wild skeleton's arrow breaks the builders' pot (so the test
	 * sees the rule).
	 */
	//$ gametest_ticks_batch AREA '40' '"qaRaiderArrowPot1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRaiderArrowPot1108d")
	public void aRaidersArrowLeavesTheBuildersDecoratedPotStanding(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer()));
		BlockPos pot = new BlockPos(6, 2, 6);
		BlockPos playersPot = new BlockPos(10, 2, 6);
		helper.setBlock(pot, Blocks.DECORATED_POT);
		helper.setBlock(playersPot, Blocks.DECORATED_POT);
		QaSiegeDamage1108dGameTests.finished(helper, pot, "qa_pot_1108d_");
		Pillager raider = EntityType.PILLAGER.create(level);
		helper.assertTrue(raider != null, "setup: no pillager");
		raider.addTag(Threats.RAIDER_TAG);
		shoot(helper, raider, pot);
		helper.assertTrue(helper.getBlockState(pot).is(Blocks.DECORATED_POT), "a raider's arrow broke the builders' decorated pot: " + helper.getBlockState(pot));
		helper.assertTrue(!helper.getBlockState(pot).getValue(DecoratedPotBlock.CRACKED), "a raider's arrow cracked the builders' decorated pot: it must stand as built");
		shoot(helper, raider, playersPot);
		helper.assertTrue(helper.getBlockState(playersPot).isAir(), "a raider's arrow didn't break a player's own pot (the game's rules): " + helper.getBlockState(playersPot));
		Skeleton wild = EntityType.SKELETON.create(level);
		helper.assertTrue(wild != null, "setup: no skeleton");
		shoot(helper, wild, pot);
		helper.assertTrue(helper.getBlockState(pot).isAir(), "a wild skeleton's arrow didn't break the pot either: the test can't see the rule");
		helper.succeed();
	}

	/**
	 * 32.16a spares the builders' pots from threats only: a player's own arrow still breaks a builder's pot (the game's
	 * rules), and with mobGriefing off a raider's arrow does nothing to a player's pot (vanilla, untouched by the mod).
	 */
	//$ gametest_ticks_batch AREA '40' '"qaPlayerArrowPot1108a"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaPlayerArrowPot1108a")
	public void aPlayersArrowStillBreaksABuildersPot(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer()));
		BlockPos pot = new BlockPos(6, 2, 6);
		BlockPos playersPot = new BlockPos(10, 2, 6);
		helper.setBlock(pot, Blocks.DECORATED_POT);
		helper.setBlock(playersPot, Blocks.DECORATED_POT);
		QaSiegeDamage1108dGameTests.finished(helper, pot, "qa_pot_1108a_");
		Pillager raider = EntityType.PILLAGER.create(level);
		helper.assertTrue(raider != null, "setup: no pillager");
		raider.addTag(Threats.RAIDER_TAG);
		shoot(helper, raider, playersPot);
		helper.assertTrue(helper.getBlockState(playersPot).is(Blocks.DECORATED_POT), "with mobGriefing off a raider's arrow broke a player's pot: " + helper.getBlockState(playersPot));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		shoot(helper, player, pot);
		helper.assertTrue(helper.getBlockState(pot).isAir(), "a player's own arrow didn't break the builders' pot (the game's rules): " + helper.getBlockState(pot));
		helper.succeed();
	}
}
