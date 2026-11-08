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
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * Found by lane D (2026-11-08), from ROADMAP 32.16a (the owner's rule of 2026-10-12): "anything threats damage must be
 * repairable by builders; no progress is ever lost". The builders hang a blueprint's item frames when a build is done
 * (the guildhalls and the traveller camp carry them; build/BuildEntities). A raider's creeper blows up inside a finished
 * hut: the blast spares every log (ThreatDamage), but not the builders' item frame on the wall, and the builder never
 * hangs it again: Upkeep only counts missing blocks, and a repair's plan (BuildPlan.repair) carries no entities.
 * Expected: the frame is spared, or the builder puts it back from the frame in his chest.
 */
public class QaThreatFrames1108dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** The hut's corner: logs at its four corners (y 2 and 3), a 5 x 5 roof of logs at y 4. */
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	/** Where the builders' frame hangs: inside the hut, on the north-west corner log's east face. */
	private static final BlockPos FRAME = HUT.offset(1, 1, 0);

	private static List<ItemFrame> frames(GameTestHelper helper, BlockPos at) {
		return helper.getLevel().getEntitiesOfClass(ItemFrame.class, new AABB(helper.absolutePos(at)).inflate(0.2), ItemFrame::isAlive);
	}

	/**
	 * A raider's creeper blows up in the middle of a finished log hut with the builders' item frame on its wall. The
	 * builder (repairs on, an item frame and logs in his chest) must have the frame back on the wall.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaThreatFrame1108d"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaThreatFrame1108d")
	public void aRaidersBlastLeavesTheBuildersItemFrameOrItIsHungAgain(GameTestHelper helper) {
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
				if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
					helper.setBlock(HUT.offset(x, 0, z), Blocks.OAK_LOG);
					helper.setBlock(HUT.offset(x, 1, z), Blocks.OAK_LOG);
				}
				helper.setBlock(HUT.offset(x, 2, z), Blocks.OAK_LOG);
			}
		}
		ItemFrame hung = new ItemFrame(level, helper.absolutePos(FRAME), Direction.EAST);
		helper.assertTrue(hung.survives(), "setup: the frame can't hang on the corner log");
		level.addFreshEntity(hung);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_frame_1108d_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(HUT), new Vec3i(5, 3, 5), true, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 17));
		BuildSiteManager.get(level).recordFinished(id, placement, builder.getUUID()); // the builder's to look after
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		VillageFire.forgetBuilds();
		BlockPos chestPos = new BlockPos(2, 2, 18);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.ITEM_FRAME, 4));
		chest.setItem(1, new ItemStack(Items.OAK_LOG, 16));
		BlockPos bench = new BlockPos(1, 2, 17);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Builders.employ(level, builder, helper.absolutePos(bench));
		boolean[] blown = {false};
		helper.runAfterDelay(4, () -> {
			BlockPos at = helper.absolutePos(HUT.offset(2, 1, 1));
			Creeper raider = EntityType.CREEPER.create(level);
			helper.assertTrue(raider != null, "setup: no creeper");
			raider.addTag(Threats.RAIDER_TAG);
			raider.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			level.explode(raider, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3f, Level.ExplosionInteraction.MOB);
			blown[0] = frames(helper, FRAME).isEmpty();
			helper.assertTrue(helper.getBlockState(HUT).is(Blocks.OAK_LOG) && helper.getBlockState(HUT.offset(0, 1, 0)).is(Blocks.OAK_LOG),
				"setup: the raider's blast took the corner log the frame hangs on (ThreatDamage should spare it)");
		});
		helper.runAfterDelay(20, () -> Upkeep.look(level, builder));
		helper.runAfterDelay(200, () -> Upkeep.look(level, builder));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 4, "(the blast hasn't gone off yet)");
			if (frames(helper, FRAME).isEmpty()) {
				helper.assertTrue(false, "the raider's blast broke the builders' item frame and it isn't back on the hut's wall (blown: " + blown[0] + ", tick " + helper.getTick() + ")");
			}
		});
	}

	/** The log hut and the frame on its wall; with {@code builders}, recorded as a finished build of the builders'. */
	private static ItemFrame hut(GameTestHelper helper, boolean builders) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
					helper.setBlock(HUT.offset(x, 0, z), Blocks.OAK_LOG);
					helper.setBlock(HUT.offset(x, 1, z), Blocks.OAK_LOG);
				}
				helper.setBlock(HUT.offset(x, 2, z), Blocks.OAK_LOG);
			}
		}
		ItemFrame hung = new ItemFrame(level, helper.absolutePos(FRAME), Direction.EAST);
		helper.assertTrue(hung.survives(), "setup: the frame can't hang on the corner log");
		level.addFreshEntity(hung);
		if (builders) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_frame_1108d_" + Long.toHexString(level.getGameTime()));
			level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(HUT), new Vec3i(5, 3, 5), true, Blocks.STRUCTURE_VOID);
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
			BuildSiteManager.get(level).recordFinished(id, placement, java.util.UUID.randomUUID());
			Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		}
		VillageFire.forgetBuilds();
		return hung;
	}

	/** B1053's guard: a player can still take down the builders' frame himself (only threats are refused). */
	//$ gametest_batch AREA '"qaThreatFrame1108dPlayer"'
	@GameTest(template = AREA, batch = "qaThreatFrame1108dPlayer")
	public void aPlayerStillBreaksTheBuildersItemFrame(GameTestHelper helper) {
		Leftovers.clear(helper);
		ItemFrame hung = hut(helper, true);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.moveTo(helper.absoluteVec(FRAME.getCenter()));
		hung.hurt(helper.getLevel().damageSources().playerAttack(player), 1f);
		helper.assertTrue(frames(helper, FRAME).isEmpty(), "a player's hit didn't break the builders' item frame (only threats' damage is refused)");
		helper.succeed();
	}

	/** B1053's guard: a frame on a player's own wall (not the builders') is still blown off by a raider's creeper. */
	//$ gametest_batch AREA '"qaThreatFrame1108dOwn"'
	@GameTest(template = AREA, batch = "qaThreatFrame1108dOwn")
	public void aRaidersBlastStillBreaksAFrameNotOnABuild(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		hut(helper, false);
		BlockPos at = helper.absolutePos(HUT.offset(2, 1, 1));
		Creeper raider = EntityType.CREEPER.create(level);
		helper.assertTrue(raider != null, "setup: no creeper");
		raider.addTag(Threats.RAIDER_TAG);
		raider.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		level.explode(raider, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3f, Level.ExplosionInteraction.NONE);
		helper.assertTrue(frames(helper, FRAME).isEmpty(), "a raider's blast left a frame that isn't on the builders' work (only theirs is spared)");
		helper.succeed();
	}
}
