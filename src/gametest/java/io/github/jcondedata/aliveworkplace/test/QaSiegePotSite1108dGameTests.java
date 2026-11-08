package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
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
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
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
 * B1032's fix, verified by lane D (2026-11-08) on the cases its own tests leave out, from ROADMAP 32.16a and
 * ThreatDamage's javadoc ("A build still under way is never touched either"; a threat is "a raider or what one shot"):
 * a decorated pot already standing inside a build still under way, hit by a raider pillager's arrow and by a drowned
 * pirate's trident, stands whole; a wild skeleton's arrow on the same pot breaks it (the game's rules, so the test sees
 * the rule). Shots go through the block's own projectile-hit entry point, the one a projectile in flight calls.
 */
public class QaSiegePotSite1108dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static void hit(ServerLevel level, BlockPos at, AbstractArrow shot) {
		shot.moveTo(at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 0f, 90f);
		shot.setDeltaMovement(0, -2.0, 0);
		BlockState state = level.getBlockState(at);
		state.onProjectileHit(level, state, new BlockHitResult(at.getCenter().add(0, 0.5, 0), Direction.UP, at, false), shot);
	}

	private static Arrow arrow(ServerLevel level, LivingEntity owner) {
		Arrow arrow = EntityType.ARROW.create(level);
		arrow.setOwner(owner);
		return arrow;
	}

	//$ gametest_ticks_batch AREA '40' '"qaPotUnderWay1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaPotUnderWay1108d")
	public void aPotInABuildUnderWayStandsAgainstARaidersArrowAndTrident(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer()));
		BlockPos corner = new BlockPos(5, 2, 5);
		BlockPos pot = corner.offset(1, 0, 1);
		helper.setBlock(pot, Blocks.DECORATED_POT);
		helper.setBlock(corner, Blocks.OAK_PLANKS);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_pot_site_1108d_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(corner), new Vec3i(3, 2, 3), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(corner), Rotation.NONE, Mirror.NONE);
		BuildSiteManager manager = BuildSiteManager.get(level);
		VillageFire.forgetBuilds();
		BuildSite site = manager.create(UUID.randomUUID(), "", id, placement);
		Leftovers.after(helper, () -> {
			manager.remove(site.id());
			VillageFire.forgetBuilds();
		});
		BlockPos at = helper.absolutePos(pot);
		Pillager pillager = EntityType.PILLAGER.create(level);
		pillager.addTag(Threats.RAIDER_TAG);
		hit(level, at, arrow(level, pillager));
		helper.assertTrue(helper.getBlockState(pot).is(Blocks.DECORATED_POT) && !helper.getBlockState(pot).getValue(DecoratedPotBlock.CRACKED),
			"a raider's arrow broke or cracked a pot of a build under way: " + helper.getBlockState(pot));
		Drowned drowned = EntityType.DROWNED.create(level);
		drowned.addTag(Threats.RAIDER_TAG);
		hit(level, at, new ThrownTrident(level, drowned, new ItemStack(Items.TRIDENT)));
		helper.assertTrue(helper.getBlockState(pot).is(Blocks.DECORATED_POT) && !helper.getBlockState(pot).getValue(DecoratedPotBlock.CRACKED),
			"a drowned pirate's trident broke or cracked a pot of a build under way: " + helper.getBlockState(pot));
		Skeleton wild = EntityType.SKELETON.create(level);
		hit(level, at, arrow(level, wild));
		helper.assertTrue(helper.getBlockState(pot).isAir(), "a wild skeleton's arrow didn't break the pot: the test can't see the rule");
		helper.succeed();
	}
}
