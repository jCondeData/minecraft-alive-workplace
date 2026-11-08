package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import java.util.ArrayList;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.phys.BlockHitResult;

/**
 * QA (qa-lane-b-1108-1837), verifying B1052 from its spec (ROADMAP 32.16a; docs/wiki/sieges.md "What threats may
 * damage"; ThreatDamage's javadoc): a threat is a raider or what one shot; nothing a threat does breaks a builder's
 * block (a finished build's block as drawn, or one inside a build under way); players' own blocks follow the game's
 * rules. Builds on {@link QaSiegeDripstone1108dBugTests} (the block's projectile-hit entry point, a standing spike of a
 * finished build) with: a trident really thrown through the air, a player's own spike, mobGriefing off, a player's
 * trident, a build under way and a hanging spike.
 */
public class QaSiegeDripstoneVerify1108bGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** Turns mobGriefing to {@code on} for the test, putting it back after. */
	private static ServerLevel griefing(GameTestHelper helper, boolean on) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean was = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(on, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(was, level.getServer()));
		return level;
	}

	private static Drowned drowned(GameTestHelper helper, boolean raider) {
		Drowned d = EntityType.DROWNED.create(helper.getLevel());
		helper.assertTrue(d != null, "setup: no drowned");
		if (raider) {
			d.addTag(Threats.RAIDER_TAG);
		}
		return d;
	}

	/** A spike standing on the floor at {@code pos} (helper coordinates). */
	private static void spike(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, Blocks.POINTED_DRIPSTONE);
		helper.assertTrue(helper.getBlockState(pos).is(Blocks.POINTED_DRIPSTONE), "setup: the spike didn't stand at " + pos);
	}

	/** A trident thrown by {@code owner} hits the block at {@code pos} from above at a breaking speed (the block's own entry point). */
	private static void hurl(GameTestHelper helper, LivingEntity owner, BlockPos pos, Direction face) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(pos);
		ThrownTrident trident = new ThrownTrident(level, owner, new ItemStack(Items.TRIDENT));
		trident.moveTo(at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 0f, 90f);
		trident.setDeltaMovement(0, -2.0, 0);
		BlockState state = level.getBlockState(at);
		state.onProjectileHit(level, state, new BlockHitResult(at.getCenter(), face, at, false), trident);
	}

	/** A template of the blocks from {@code pos} ({@code size}), as a build under way at that spot. */
	private static ResourceLocation template(GameTestHelper helper, BlockPos pos, Vec3i size, String name) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", name + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(pos), size, false, Blocks.STRUCTURE_VOID);
		return id;
	}

	/** Records the blocks from {@code pos} ({@code size}) as one finished build. */
	private static void finished(GameTestHelper helper, BlockPos pos, Vec3i size, String name) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = template(helper, pos, size, name);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(pos), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> {
			BuildSiteManager.get(level).forgetFinished(placement);
			VillageFire.forgetBuilds();
		});
		VillageFire.forgetBuilds();
	}

	/**
	 * A raider drowned's trident really thrown: it flies down onto a finished build's spike and the spike stands; a wild
	 * drowned's trident thrown the same way onto another finished build's spike breaks it (so the throw does hit).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDripVerifyFlight1108b"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDripVerifyFlight1108b")
	public void aRaidersTridentInFlightLeavesTheBuildersSpikeStanding(GameTestHelper helper) {
		ServerLevel level = griefing(helper, true);
		BlockPos ours = new BlockPos(4, 2, 4);
		BlockPos control = new BlockPos(11, 2, 11);
		spike(helper, ours);
		spike(helper, control);
		finished(helper, ours, new Vec3i(1, 1, 1), "qa_dripv_flight_a_");
		finished(helper, control, new Vec3i(1, 1, 1), "qa_dripv_flight_b_");
		for (var pair : new Object[][] {{ours, drowned(helper, true)}, {control, drowned(helper, false)}}) {
			BlockPos at = helper.absolutePos((BlockPos) pair[0]);
			ThrownTrident trident = new ThrownTrident(level, (LivingEntity) pair[1], new ItemStack(Items.TRIDENT));
			trident.moveTo(at.getX() + 0.5, at.getY() + 4.0, at.getZ() + 0.5, 0f, 90f);
			trident.setDeltaMovement(0, -2.5, 0);
			level.addFreshEntity(trident);
		}
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(helper.getBlockState(control).isAir(),
				"setup: a wild drowned's trident in flight didn't break its spike, so the throw never hit: " + helper.getBlockState(control));
			helper.assertTrue(helper.getBlockState(ours).is(Blocks.POINTED_DRIPSTONE),
				"a raider's trident in flight broke the finished build's spike: " + helper.getBlockState(ours));
			helper.succeed();
		});
	}

	/** Players' own blocks follow the game's rules: a raider's trident breaks a spike no build drew (mobGriefing on). */
	//$ gametest_ticks_batch AREA '40' '"qaDripVerifyPlayers1108b"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDripVerifyPlayers1108b")
	public void aRaidersTridentBreaksAPlayersOwnSpike(GameTestHelper helper) {
		griefing(helper, true);
		BlockPos builders = new BlockPos(4, 2, 4);
		BlockPos players = new BlockPos(5, 2, 4); // right beside the build, outside its outline
		spike(helper, builders);
		spike(helper, players);
		finished(helper, builders, new Vec3i(1, 1, 1), "qa_dripv_players_");
		Drowned raider = drowned(helper, true);
		hurl(helper, raider, players, Direction.UP);
		helper.assertTrue(helper.getBlockState(players).isAir(), "a raider's trident left a player's own spike standing: " + helper.getBlockState(players));
		hurl(helper, raider, builders, Direction.UP);
		helper.assertTrue(helper.getBlockState(builders).is(Blocks.POINTED_DRIPSTONE), "the raider's trident broke the builders' spike next to it");
		helper.succeed();
	}

	/** With mobGriefing off, a raider's trident leaves even a player's own spike (the game's rule). */
	//$ gametest_ticks_batch AREA '40' '"qaDripVerifyNoGrief1108b"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDripVerifyNoGrief1108b")
	public void withMobGriefingOffARaidersTridentLeavesAPlayersSpike(GameTestHelper helper) {
		griefing(helper, false);
		BlockPos players = new BlockPos(5, 2, 5);
		spike(helper, players);
		hurl(helper, drowned(helper, true), players, Direction.UP);
		helper.assertTrue(helper.getBlockState(players).is(Blocks.POINTED_DRIPSTONE), "mobGriefing off, yet a raider's trident broke a player's spike");
		hurl(helper, drowned(helper, false), players, Direction.UP);
		helper.assertTrue(helper.getBlockState(players).is(Blocks.POINTED_DRIPSTONE), "mobGriefing off, yet a wild drowned's trident broke a player's spike");
		helper.succeed();
	}

	/** Only threats are stopped: a player's own trident breaks the builders' spike, as the game's rules have it. */
	//$ gametest_ticks_batch AREA '40' '"qaDripVerifyPlayer1108b"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDripVerifyPlayer1108b")
	public void aPlayersTridentStillBreaksTheBuildersSpike(GameTestHelper helper) {
		griefing(helper, true);
		BlockPos builders = new BlockPos(6, 2, 6);
		spike(helper, builders);
		finished(helper, builders, new Vec3i(1, 1, 1), "qa_dripv_player_");
		var player = WonderFinishedGameTests.listener(helper, new ArrayList<>(), "DripThrower");
		hurl(helper, player, builders, Direction.UP);
		helper.assertTrue(helper.getBlockState(builders).isAir(), "a player's trident didn't break the builders' spike: " + helper.getBlockState(builders));
		helper.succeed();
	}

	/** A build still under way is never touched: a raider's trident leaves its spike; a wild drowned's breaks it. */
	//$ gametest_ticks_batch AREA '40' '"qaDripVerifyUnderWay1108b"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDripVerifyUnderWay1108b")
	public void aRaidersTridentLeavesTheSpikeOfABuildUnderWay(GameTestHelper helper) {
		ServerLevel level = griefing(helper, true);
		Leftovers.buildSites(helper);
		BlockPos at = new BlockPos(6, 2, 6);
		spike(helper, at);
		ResourceLocation id = template(helper, at, new Vec3i(1, 1, 1), "qa_dripv_underway_");
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(at), Rotation.NONE, Mirror.NONE);
		BuildSite site = BuildSiteManager.get(level).create(UUID.randomUUID(), "", id, placement);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		VillageFire.forgetBuilds();
		hurl(helper, drowned(helper, true), at, Direction.UP);
		helper.assertTrue(helper.getBlockState(at).is(Blocks.POINTED_DRIPSTONE), "a raider's trident broke the spike of a build under way");
		hurl(helper, drowned(helper, false), at, Direction.UP);
		helper.assertTrue(helper.getBlockState(at).isAir(), "setup: a wild drowned's trident didn't break it either: the test can't see the rule");
		helper.succeed();
	}

	/** A spike hanging under a finished build's ceiling (a stalactite) also stands under a raider's trident; a wild one's breaks it. */
	//$ gametest_ticks_batch AREA '40' '"qaDripVerifyHanging1108b"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDripVerifyHanging1108b")
	public void aRaidersTridentLeavesTheBuildersHangingSpike(GameTestHelper helper) {
		griefing(helper, true);
		BlockPos ceiling = new BlockPos(6, 5, 6);
		BlockPos tip = ceiling.below();
		helper.setBlock(ceiling, Blocks.STONE);
		helper.setBlock(tip, Blocks.POINTED_DRIPSTONE.defaultBlockState()
			.setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN).setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP));
		helper.assertTrue(helper.getBlockState(tip).is(Blocks.POINTED_DRIPSTONE), "setup: no hanging spike");
		finished(helper, tip, new Vec3i(1, 2, 1), "qa_dripv_hanging_");
		hurl(helper, drowned(helper, true), tip, Direction.DOWN);
		helper.assertTrue(helper.getBlockState(tip).is(Blocks.POINTED_DRIPSTONE), "a raider's trident broke the builders' hanging spike: " + helper.getBlockState(tip));
		hurl(helper, drowned(helper, false), tip, Direction.DOWN);
		helper.assertTrue(!helper.getBlockState(tip).is(Blocks.POINTED_DRIPSTONE), "setup: a wild drowned's trident didn't break the hanging spike: the test can't see the rule");
		helper.succeed();
	}
}
