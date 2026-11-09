package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;

/**
 * QA lane B, 2026-11-09: two ways a threat still takes the builders' work, from docs/wiki/sieges.md ("nothing a threat
 * does breaks a builder's block"; "a threat is a raider or a projectile one shot") and 32.16a ("anything threats damage
 * must be repairable by builders; no progress is ever lost").
 */
public class QaThreatSpare1109bBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HUT = QaThreatSpare1109bGameTests.HUT;

	/**
	 * A killed raider's arrow hits the builders' frame A, which (B1053) refuses the hit, so the game bounces the arrow
	 * back (AbstractArrow: a refused hit deflects it with its owner, now null). ThreatShotMixin reads that
	 * setOwner(null) as a take-over by nobody and drops the arrow's raider tag (ThreatDamage.shot), so the bounced arrow
	 * is nobody's and knocks the diamond out of the builders' frame B across the room. Expected: a raider's shot stays a
	 * raider's until it lands (B1066), so frame B keeps its diamond too.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaThreatSpare1109bBounce"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaThreatSpare1109bBounce")
	public void aKilledRaidersArrowBouncedOffTheBuildersFrameStaysARaidersShot(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaThreatSpare1109bGameTests.hut(helper, true, true);
		Arrow arrow = QaThreatSpare1109bGameTests.shotOfAKilledRaider(helper);
		helper.runAfterDelay(120, () -> {
			List<ItemFrame> a = QaThreatSpare1109bGameTests.frames(helper, QaThreatSpare1109bGameTests.FRAME_A);
			helper.assertTrue(!a.isEmpty() && a.get(0).getItem().is(Items.DIAMOND), "setup: the arrow took frame A's diamond (B1053's own case)");
			List<ItemFrame> b = QaThreatSpare1109bGameTests.frames(helper, QaThreatSpare1109bGameTests.FRAME_B);
			helper.assertTrue(!b.isEmpty() && b.get(0).getItem().is(Items.DIAMOND),
				"the killed raider's arrow, bounced off the builders' frame A, knocked the diamond out of their frame B (arrow tags after the bounce: "
					+ arrow.getTags() + ")");
			helper.succeed();
		});
	}

	/**
	 * A builder's finished build with an armor stand (Scan Tool blueprints carry them, B441; BuildEntities sets them up).
	 * A raider's creeper blows up inside: ThreatDamage spares the blocks and (B1053) the item frames, but the armor stand
	 * is broken, and Upkeep repairs only blocks, so the builder never puts it back even with one in his chest. Expected:
	 * the stand is spared, or the builder puts it back.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaThreatSpare1109bStand"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaThreatSpare1109bStand")
	public void aRaidersBlastLeavesTheBuildersArmorStandOrItIsPutBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		boolean repairs = Upkeep.ENABLED;
		boolean threats = Upkeep.THREATS;
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
		Upkeep.ENABLED = true;
		Upkeep.THREATS = true;
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
			Upkeep.ENABLED = repairs;
			Upkeep.THREATS = threats;
			ThreatData data = ThreatData.get(level);
			for (BlockPos p : List.copyOf(data.damaged())) {
				if (helper.getBounds().inflate(1).contains(p.getCenter())) {
					data.repaired(p);
				}
			}
		});
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(HUT.offset(x, -1, z), Blocks.OAK_PLANKS);
				if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
					helper.setBlock(HUT.offset(x, 0, z), Blocks.OAK_LOG);
					helper.setBlock(HUT.offset(x, 1, z), Blocks.OAK_LOG);
				}
				helper.setBlock(HUT.offset(x, 2, z), Blocks.OAK_LOG);
			}
		}
		BlockPos standAt = HUT.offset(3, 0, 3);
		ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, standAt);
		stand.setYRot(0f);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_stand_1109b_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(HUT.below()), new Vec3i(5, 4, 5), true, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT.below()), Rotation.NONE, Mirror.NONE);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 17));
		BuildSiteManager.get(level).recordFinished(id, placement, builder.getUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		VillageFire.forgetBuilds();
		BlockPos chestPos = new BlockPos(2, 2, 18);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.ARMOR_STAND, 4));
		chest.setItem(1, new ItemStack(Items.OAK_LOG, 16));
		chest.setItem(2, new ItemStack(Items.OAK_PLANKS, 16));
		BlockPos bench = new BlockPos(1, 2, 17);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Builders.employ(level, builder, helper.absolutePos(bench));
		boolean[] blown = {false};
		helper.runAfterDelay(4, () -> {
			BlockPos at = helper.absolutePos(HUT.offset(2, 0, 2));
			Creeper raider = EntityType.CREEPER.create(level);
			helper.assertTrue(raider != null, "setup: no creeper");
			raider.addTag(Threats.RAIDER_TAG);
			raider.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			level.explode(raider, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3f, Level.ExplosionInteraction.MOB);
			blown[0] = stands(helper, standAt).isEmpty();
			helper.assertTrue(helper.getBlockState(HUT.offset(3, -1, 3)).is(Blocks.OAK_PLANKS),
				"setup: the raider's blast took the builders' floor under the stand (ThreatDamage should spare it)");
		});
		helper.runAfterDelay(20, () -> Upkeep.look(level, builder));
		helper.runAfterDelay(200, () -> Upkeep.look(level, builder));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 4, "(the blast hasn't gone off yet)");
			helper.assertTrue(!stands(helper, standAt).isEmpty(),
				"the raider's blast broke the builders' armor stand and it isn't back in the hut (blown: " + blown[0] + ", tick " + helper.getTick() + ")");
		});
	}

	private static List<ArmorStand> stands(GameTestHelper helper, BlockPos at) {
		return helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(helper.absolutePos(at)).inflate(1.0), ArmorStand::isAlive);
	}

	/**
	 * A village fire (a raider's burning arrow, a chimney spark) burns the builders' roof plank their item frame hangs
	 * under. The builder puts the plank back (32.16: "a fire never destroys a build for good"), but the frame fell off
	 * with its plank and nothing hangs it again: Upkeep and BuildPlan.repair put back blocks only, and B1053 spares
	 * frames from blasts and shots, not from losing their wall. Expected: the frame is back under the roof once the hut
	 * is repaired, from the item frame in the builder's chest.
	 */
	//$ gametest_ticks_batch 'QaFireGameTests.AREA' '4000' '"qaThreatSpare1109bFire"'
	@GameTest(template = QaFireGameTests.AREA, timeoutTicks = 4000, batch = "qaThreatSpare1109bFire")
	public void aFrameUnderARoofPlankTheFireBurnedIsHungAgainWithThePlank(GameTestHelper helper) {
		ServerLevel level = QaFireGameTests.village(helper, QaFireGameTests.HALL, 20);
		BlockPos hut = QaFireGameTests.HUT;
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
					helper.setBlock(hut.offset(x, 0, z), Blocks.OAK_LOG);
					helper.setBlock(hut.offset(x, 1, z), Blocks.OAK_LOG);
				}
				helper.setBlock(hut.offset(x, 2, z), Blocks.OAK_PLANKS);
			}
		}
		BlockPos support = hut.offset(2, 2, 2);
		BlockPos frameAt = hut.offset(2, 1, 2);
		ItemFrame hung = new ItemFrame(level, helper.absolutePos(frameAt), Direction.DOWN);
		helper.assertTrue(hung.survives(), "setup: the frame can't hang under the roof");
		level.addFreshEntity(hung);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_fireframe_1109b_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(hut), new Vec3i(5, 3, 5), true, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(hut), Rotation.NONE, Mirror.NONE);
		boolean was = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		Upkeep.ENABLED = true;
		Upkeep.ASK = false;
		Leftovers.after(helper, () -> {
			Upkeep.ENABLED = was;
			Upkeep.ASK = asks;
			BuildSiteManager.get(level).forgetFinished(placement);
		});
		BlockPos chestPos = new BlockPos(6, 2, 2);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
		chest.setItem(1, new ItemStack(Items.OAK_LOG, 16));
		chest.setItem(2, new ItemStack(Items.ITEM_FRAME, 4));
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BuildSiteManager.get(level).recordFinished(id, placement, builder.getUUID());
		VillageFire.forgetBuilds();
		VillageFire.forgetFactors();
		long[] out = {-1};
		helper.onEachTick(() -> {
			if (out[0] >= 0 || helper.getTick() < 2) {
				return;
			}
			BlockState now = level.getBlockState(helper.absolutePos(support));
			if (!now.is(Blocks.OAK_PLANKS) && !now.is(BlockTags.FIRE)) {
				level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
				QaFireGameTests.putOut(helper);
				out[0] = helper.getTick();
				helper.assertTrue(ThreatData.get(level).damaged().contains(helper.absolutePos(support)), "setup: the burned plank isn't on the repair list");
			} else if (helper.getTick() % 20 == 0 && level.getBlockState(helper.absolutePos(support.above())).isAir()) {
				QaFireGameTests.fire(helper, support.above()); // keep the fire on it until the plank burns
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(out[0] >= 0, "(the plank the frame hangs under hasn't burned yet)");
			helper.assertTrue(level.getBlockState(helper.absolutePos(support)).is(Blocks.OAK_PLANKS), "(the builder hasn't put the plank back yet)");
			helper.assertTrue(!level.getEntitiesOfClass(ItemFrame.class, new AABB(helper.absolutePos(frameAt)).inflate(0.3), ItemFrame::isAlive).isEmpty(),
				"the fire burned the plank the builders' frame hung under; the builder put the plank back but not the frame (fire out at tick "
					+ out[0] + ", now " + helper.getTick() + ")");
		});
	}
}
