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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Bug test (lane D, 2026-11-08; fails on main, do not register until the fix): a raider's arrow breaks a builder's
 * decorated pot. ROADMAP 32.16a / ThreatDamage's javadoc: "Nothing else a threat does breaks a builder's block"; a
 * threat is "a raider or what one shot". Vanilla's {@code DecoratedPotBlock.onProjectileHit} first sets the pot
 * {@code cracked=true} (setBlock) and only then calls {@code destroyBlock(pos, true, projectile)}; by then the pot no
 * longer matches the build "as drawn" ({@code MaterialRules.matches} compares {@code cracked}), so
 * {@code VillageFire.builderMade} says it isn't the builders' and {@code ThreatDamage.spares} lets the arrow break it.
 * A pillager in a siege who misses a guard or villager standing by a pot in a builder's house breaks the pot (and it
 * is not on the threat-repair list, so the builders don't put it back either). Likely fix: treat {@code cracked} as a
 * property that doesn't decide "as drawn" (MaterialRules.VOLATILE_PROPERTY_NAMES or the like), or spare the pot before
 * the crack.
 */
public class QaSiegeDamage1108dBugTests implements FabricGameTest {
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
	 * sees the rule). On main 2026-11-08 the first check fails: "a raider's arrow broke the builders' decorated pot:
	 * Block{minecraft:air}".
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
		shoot(helper, raider, playersPot);
		helper.assertTrue(helper.getBlockState(playersPot).isAir(), "a raider's arrow didn't break a player's own pot (the game's rules): " + helper.getBlockState(playersPot));
		Skeleton wild = EntityType.SKELETON.create(level);
		helper.assertTrue(wild != null, "setup: no skeleton");
		shoot(helper, wild, pot);
		helper.assertTrue(helper.getBlockState(pot).isAir(), "a wild skeleton's arrow didn't break the pot either: the test can't see the rule");
		helper.succeed();
	}
}
