package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * QA (lane b, 2026-11-08), verifying B1032 from its spec (ROADMAP 32.16a: nothing a threat does breaks a builder's
 * block; ThreatDamage: the builders' blocks are a finished build's blocks as drawn "or inside a build under way"), not
 * from the fix: a raider's arrow on a decorated pot standing in a build that is still under way, and on a finished
 * build's pot a player has since filled, leaves the pot whole, uncracked and with what it holds.
 */
public class QaSiegePotVerify1108bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";

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

	private static Pillager raider(GameTestHelper helper) {
		Pillager raider = EntityType.PILLAGER.create(helper.getLevel());
		helper.assertTrue(raider != null, "setup: no pillager");
		raider.addTag(Threats.RAIDER_TAG);
		return raider;
	}

	private static void griefing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean was = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(was, level.getServer()));
	}

	/** A 5x2x5 blueprint waiting for its stone (no materials): a pot inside its outline is the builders'. */
	//$ gametest_ticks_batch HUGE '200' '"qaSiegePotUnderWay1108b"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaSiegePotUnderWay1108b")
	public void aRaidersArrowSparesAPotInsideABuildUnderWay(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		griefing(helper);
		BlockPos src = new BlockPos(20, 2, 20);
		for (int dx = 0; dx < 5; dx++) {
			for (int dz = 0; dz < 5; dz++) {
				helper.setBlock(src.offset(dx, 0, dz), Blocks.STONE_BRICKS);
			}
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_pot_way_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(src), new Vec3i(5, 2, 5), false, Blocks.STRUCTURE_VOID);
		for (int dx = 0; dx < 5; dx++) {
			for (int dz = 0; dz < 5; dz++) {
				helper.setBlock(src.offset(dx, 0, dz), Blocks.AIR);
			}
		}
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, bench.east());
		Builders.employ(level, builder, helper.absolutePos(bench));
		BlockPos origin = new BlockPos(8, 2, 8);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		BlockPos pot = origin.offset(2, 1, 2);
		helper.setBlock(pot, Blocks.DECORATED_POT);
		BlockPos outside = origin.offset(8, 0, 2);
		helper.setBlock(outside, Blocks.DECORATED_POT);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) != null, "(setup) the build isn't under way any more");
			Pillager raider = raider(helper);
			shoot(helper, raider, pot);
			helper.assertTrue(helper.getBlockState(pot).is(Blocks.DECORATED_POT) && !helper.getBlockState(pot).getValue(DecoratedPotBlock.CRACKED),
				"a raider's arrow broke or cracked a decorated pot inside a build under way: " + helper.getBlockState(pot));
			shoot(helper, raider, outside);
			helper.assertTrue(helper.getBlockState(outside).isAir(), "a raider's arrow didn't break a pot outside every build (the game's rules): "
				+ helper.getBlockState(outside));
			helper.succeed();
		});
	}

	/** A player filled the finished build's pot with 12 emeralds since: the arrow leaves pot and emeralds alone. */
	//$ gametest_ticks_batch BIG '40' '"qaSiegePotFilled1108b"'
	@GameTest(template = BIG, timeoutTicks = 40, batch = "qaSiegePotFilled1108b")
	public void aRaidersArrowSparesTheBuildersPotAndWhatAPlayerPutInIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		griefing(helper);
		BlockPos pot = new BlockPos(6, 2, 6);
		helper.setBlock(pot, Blocks.DECORATED_POT);
		QaSiegeDamage1108dGameTests.finished(helper, pot, "qa_pot_full_1108b_");
		((Container) helper.getBlockEntity(pot)).setItem(0, new ItemStack(Items.EMERALD, 12));
		shoot(helper, raider(helper), pot);
		helper.assertTrue(helper.getBlockState(pot).is(Blocks.DECORATED_POT) && !helper.getBlockState(pot).getValue(DecoratedPotBlock.CRACKED),
			"a raider's arrow broke or cracked the builders' pot once a player filled it: " + helper.getBlockState(pot));
		ItemStack held = ((Container) helper.getBlockEntity(pot)).getItem(0);
		helper.assertTrue(held.is(Items.EMERALD) && held.getCount() == 12, "the pot's emeralds changed under a raider's arrow: " + held);
		helper.succeed();
	}
}
