package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * QA (lane D, 2026-11-08) for B1052's fix, ThreatDripstoneMixin, at its edges. The spec (wiki sieges.md, "What threats
 * may damage"; ThreatDamage's javadoc): a "threat" is a raider ({@code aliveworkplace_raider} tag) or a projectile one
 * shot; a "builder's block" is a finished build's block as drawn or a block inside a build site under way. Nothing a
 * threat does breaks a builder's block; players' own blocks, and what mobs that are not raiders do, follow the game's
 * rules (the mixin's javadoc: "A wild drowned's or a player's trident follows the game's rules"). The throw goes through
 * the block's own projectile-hit entry point, the one a trident in flight calls (as QaSiegeDripstone1108dBugTests).
 */
public class QaSiegeDripstoneEdges1108dGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:big_area";
	static final BlockPos SPIKE = new BlockPos(6, 2, 6);

	/** A trident thrown by {@code owner} hits the block at {@code pos} (helper coordinates) at a breaking speed. */
	static ThrownTrident trident(GameTestHelper helper, LivingEntity owner, BlockPos pos) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(pos);
		ThrownTrident trident = new ThrownTrident(level, owner, new ItemStack(Items.TRIDENT));
		trident.moveTo(at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 0f, 90f);
		trident.setDeltaMovement(0, -2.0, 0);
		return trident;
	}

	static void hit(GameTestHelper helper, ThrownTrident trident, BlockPos pos) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(pos);
		BlockState state = level.getBlockState(at);
		state.onProjectileHit(level, state, new BlockHitResult(at.getCenter().add(0, 0.5, 0), Direction.UP, at, false), trident);
	}

	/** Clears the area, turns mobGriefing on (a trident breaks dripstone only with it) and stands a spike at {@link #SPIKE}. */
	static ServerLevel spike(GameTestHelper helper, boolean builders) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer()));
		helper.setBlock(SPIKE, Blocks.POINTED_DRIPSTONE);
		helper.assertTrue(helper.getBlockState(SPIKE).is(Blocks.POINTED_DRIPSTONE), "setup: the spike didn't stand on the floor");
		if (builders) {
			QaSiegeDamage1108dGameTests.finished(helper, SPIKE, "qa_spike_edge_1108d_");
		}
		return level;
	}

	static Drowned drowned(GameTestHelper helper, boolean raider) {
		Drowned d = EntityType.DROWNED.create(helper.getLevel());
		helper.assertTrue(d != null, "setup: no drowned");
		if (raider) {
			d.addTag(Threats.RAIDER_TAG);
		}
		return d;
	}

	/** A player's own thrown trident breaks a builder's dripstone spike: players follow the game's rules. */
	//$ gametest_ticks_batch AREA '40' '"qaSpikeEdgePlayer1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaSpikeEdgePlayer1108d")
	public void aPlayersTridentStillBreaksTheBuildersDripstoneSpike(GameTestHelper helper) {
		ServerLevel level = spike(helper, true);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		player.setGameMode(GameType.SURVIVAL);
		hit(helper, trident(helper, player, SPIKE), SPIKE);
		helper.assertTrue(helper.getBlockState(SPIKE).isAir(),
			"a player's trident left the builders' dripstone spike standing; a player's trident follows the game's rules: " + helper.getBlockState(SPIKE));
		helper.succeed();
	}

	/** A wild (untagged) drowned's trident breaks the builder's spike even while a raid is under way on the village. */
	//$ gametest_ticks_batch AREA '40' '"qaSpikeEdgeWild1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaSpikeEdgeWild1108d")
	public void aWildDrownedsTridentBreaksTheSpikeEvenDuringARaid(GameTestHelper helper) {
		ServerLevel level = spike(helper, true);
		BlockPos hall = helper.absolutePos(new BlockPos(2, 2, 2));
		ThreatData.get(level).begin(new ThreatData.Under(hall, Threats.MONSTERS, 3, level.getGameTime()));
		Leftovers.after(helper, () -> {
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPast(hall);
		});
		hit(helper, trident(helper, drowned(helper, false), SPIKE), SPIKE);
		helper.assertTrue(helper.getBlockState(SPIKE).isAir(),
			"a wild drowned's trident (no raider tag) left the builders' spike standing; mobs that are not raiders follow the game's rules: "
				+ helper.getBlockState(SPIKE));
		helper.succeed();
	}

	/** A raider's trident on natural dripstone (no build's block): the game's rules, it breaks. */
	//$ gametest_ticks_batch AREA '40' '"qaSpikeEdgeNatural1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaSpikeEdgeNatural1108d")
	public void aRaidersTridentBreaksDripstoneThatIsNoBuildersBlock(GameTestHelper helper) {
		spike(helper, false);
		hit(helper, trident(helper, drowned(helper, true), SPIKE), SPIKE);
		helper.assertTrue(helper.getBlockState(SPIKE).isAir(),
			"a raider's trident left natural dripstone (no builder's block) standing; only the builders' blocks are spared: " + helper.getBlockState(SPIKE));
		helper.succeed();
	}

	/**
	 * After the raid has ended (no raid under way), a mob still carrying the raider tag is still a threat (the wiki
	 * defines a threat by the tag alone): its trident leaves the builder's spike standing.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaSpikeEdgeAfterRaid1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaSpikeEdgeAfterRaid1108d")
	public void aRaidersTridentAfterTheRaidEndedStillLeavesTheSpike(GameTestHelper helper) {
		ServerLevel level = spike(helper, true);
		BlockPos hall = helper.absolutePos(new BlockPos(2, 2, 2));
		ThreatData.get(level).begin(new ThreatData.Under(hall, Threats.MONSTERS, 3, level.getGameTime()));
		ThreatData.get(level).end(hall);
		Leftovers.after(helper, () -> ThreatData.get(level).forgetPast(hall));
		helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty(), "setup: the raid is still under way");
		hit(helper, trident(helper, drowned(helper, true), SPIKE), SPIKE);
		helper.assertTrue(helper.getBlockState(SPIKE).is(Blocks.POINTED_DRIPSTONE),
			"after the raid ended, a raider's trident broke the builders' spike (a threat is a raider by its tag): " + helper.getBlockState(SPIKE));
		helper.succeed();
	}

}
