package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA lane B, 2026-11-09: B1053 (a threat's blast or shot never breaks an item frame hung on the builders' work) and
 * B1066 (a raider's shot stays a threat's after its shooter is gone), from docs/wiki/sieges.md, "What threats may
 * damage". A real arrow in flight, not a damage call: the shot of a raider the guards already killed flies into the
 * builders' frame and must leave its item on it; the same shot into a frame on a wall that isn't the builders' knocks the
 * item out as the game does (which also proves the arrow really hits). The failing cases are in
 * QaThreatSpare1109bBugTests (tests/threat-spare-1109).
 */
public class QaThreatSpare1109bGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** The hut's corner: logs at its four corners (y 2 and 3), a 5 x 5 roof of logs at y 4. */
	static final BlockPos HUT = new BlockPos(6, 2, 6);
	/** Frame A: on the north-west corner log's east face. */
	static final BlockPos FRAME_A = HUT.offset(1, 1, 0);
	/** Frame B: on the north-east corner log's west face, facing frame A. */
	static final BlockPos FRAME_B = HUT.offset(3, 1, 0);

	static List<ItemFrame> frames(GameTestHelper helper, BlockPos at) {
		return helper.getLevel().getEntitiesOfClass(ItemFrame.class, new AABB(helper.absolutePos(at)).inflate(0.2), ItemFrame::isAlive);
	}

	/** The log hut with a diamond in a frame at A (and at B with {@code two}); with {@code builders}, a finished build of theirs. */
	static void hut(GameTestHelper helper, boolean builders, boolean two) {
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
		hang(helper, FRAME_A, Direction.EAST);
		if (two) {
			hang(helper, FRAME_B, Direction.WEST);
		}
		if (builders) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_spare_1109b_" + Long.toHexString(level.getGameTime()));
			level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(HUT), new Vec3i(5, 3, 5), true, Blocks.STRUCTURE_VOID);
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
			BuildSiteManager.get(level).recordFinished(id, placement, java.util.UUID.randomUUID());
			Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		}
		VillageFire.forgetBuilds();
	}

	private static void hang(GameTestHelper helper, BlockPos at, Direction facing) {
		ItemFrame frame = new ItemFrame(helper.getLevel(), helper.absolutePos(at), facing);
		helper.assertTrue(frame.survives(), "setup: the frame at " + at + " can't hang");
		frame.setItem(new ItemStack(Items.DIAMOND));
		helper.getLevel().addFreshEntity(frame);
	}

	/**
	 * A raider's arrow, its pillager already killed, flying west at frame A from 1.5 blocks off (no gravity, so its path
	 * is the same every run).
	 */
	static Arrow shotOfAKilledRaider(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Pillager raider = helper.spawn(EntityType.PILLAGER, new BlockPos(2, 2, 2));
		raider.addTag(Threats.RAIDER_TAG);
		Arrow arrow = EntityType.ARROW.create(level);
		helper.assertTrue(arrow != null, "setup: no arrow");
		arrow.setOwner(raider);
		raider.kill(); // a guard got him while his arrow flew
		raider.discard();
		helper.assertTrue(arrow.getOwner() == null, "setup: the dead raider still reads as the arrow's owner");
		var a = helper.absolutePos(FRAME_A);
		arrow.moveTo(a.getX() + 1.5, a.getY() + 0.45, a.getZ() + 0.5, 90f, 0f);
		arrow.setNoGravity(true);
		arrow.setDeltaMovement(-1.5, 0, 0);
		level.addFreshEntity(arrow);
		return arrow;
	}

	/** The killed raider's arrow hits the builders' frame: the frame stays and keeps its diamond. */
	//$ gametest_ticks_batch AREA '100' '"qaThreatSpare1109bShot"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaThreatSpare1109bShot")
	public void aKilledRaidersArrowLeavesTheDiamondInTheBuildersFrame(GameTestHelper helper) {
		Leftovers.clear(helper);
		hut(helper, true, false);
		shotOfAKilledRaider(helper);
		helper.runAfterDelay(30, () -> {
			List<ItemFrame> left = frames(helper, FRAME_A);
			helper.assertTrue(!left.isEmpty(), "the killed raider's arrow broke the builders' item frame");
			helper.assertTrue(left.get(0).getItem().is(Items.DIAMOND),
				"the killed raider's arrow knocked the diamond out of the builders' item frame (holds " + left.get(0).getItem() + ")");
			helper.succeed();
		});
	}

	/** Control: the same arrow into the same frame on a wall that isn't the builders' knocks the diamond out. */
	//$ gametest_ticks_batch AREA '100' '"qaThreatSpare1109bShotOwn"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaThreatSpare1109bShotOwn")
	public void aKilledRaidersArrowStillKnocksTheItemOutOfAFrameNotOnABuild(GameTestHelper helper) {
		Leftovers.clear(helper);
		hut(helper, false, false);
		shotOfAKilledRaider(helper);
		helper.runAfterDelay(30, () -> {
			List<ItemFrame> left = frames(helper, FRAME_A);
			helper.assertTrue(left.isEmpty() || left.get(0).getItem().isEmpty(),
				"the raider's arrow left the diamond in a frame that isn't the builders' (only theirs is spared), or it missed the frame");
			helper.succeed();
		});
	}
}
