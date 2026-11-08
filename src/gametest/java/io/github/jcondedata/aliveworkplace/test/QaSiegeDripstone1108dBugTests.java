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
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * B1032's kin, found by lane D (2026-11-08): a drowned pirate's trident breaks the pointed-dripstone spikes on top of a
 * finished palisade (palisade_2 carries them). ROADMAP 32.16a / ThreatDamage's javadoc: "Nothing else a threat does
 * breaks a builder's block"; a threat is "a raider or what one shot", and the drowned_pirates culture arms its drowned
 * with tridents. Vanilla's {@code PointedDripstoneBlock.onProjectileHit} calls {@code level.destroyBlock(pos, true)},
 * which passes no entity, so the threat-grief guard (which asks about the breaking entity) never learns a raider's
 * trident did it. The throw goes through the block's own projectile-hit entry point, the one a trident in flight calls.
 */
public class QaSiegeDripstone1108dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** A trident thrown by {@code owner} hits the block at {@code pos} (helper coordinates) at a breaking speed. */
	private static void hurl(GameTestHelper helper, LivingEntity owner, BlockPos pos) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(pos);
		ThrownTrident trident = new ThrownTrident(level, owner, new ItemStack(Items.TRIDENT));
		trident.moveTo(at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 0f, 90f);
		trident.setDeltaMovement(0, -2.0, 0);
		BlockState state = level.getBlockState(at);
		state.onProjectileHit(level, state, new BlockHitResult(at.getCenter().add(0, 0.5, 0), Direction.UP, at, false), trident);
	}

	/**
	 * A raider drowned's trident hits a palisade's dripstone spike of a finished build: the spike must stand. A wild
	 * drowned's trident on the same spike breaks it (the game's rules, so the test sees the rule).
	 */
	//$ gametest_ticks_batch AREA '40' '"qaDrownedTridentSpike1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDrownedTridentSpike1108d")
	public void aDrownedPiratesTridentLeavesThePalisadesDripstoneSpikeStanding(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer()));
		BlockPos spike = new BlockPos(6, 2, 6);
		helper.setBlock(spike, Blocks.POINTED_DRIPSTONE);
		helper.assertTrue(helper.getBlockState(spike).is(Blocks.POINTED_DRIPSTONE), "setup: the spike didn't stand on the floor");
		QaSiegeDamage1108dGameTests.finished(helper, spike, "qa_spike_1108d_");
		Drowned raider = EntityType.DROWNED.create(level);
		helper.assertTrue(raider != null, "setup: no drowned");
		raider.addTag(Threats.RAIDER_TAG);
		hurl(helper, raider, spike);
		helper.assertTrue(helper.getBlockState(spike).is(Blocks.POINTED_DRIPSTONE),
			"a drowned pirate's trident broke the builders' palisade spike (pointed dripstone of a finished build): " + helper.getBlockState(spike));
		Drowned wild = EntityType.DROWNED.create(level);
		helper.assertTrue(wild != null, "setup: no wild drowned");
		hurl(helper, wild, spike);
		helper.assertTrue(helper.getBlockState(spike).isAir(), "a wild drowned's trident didn't break the spike either: the test can't see the rule");
		helper.succeed();
	}
}
