package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.moved;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * QA (lane E, 2026-11-10): the Mover (45.3) moving a building with a Create Mechanical Arm set up to take from one Depot
 * and put on another. 45.3 and moving-buildings.md: the building comes across block for block, turned with the move, so a
 * machine that worked before works after. Create keeps an arm's targets as positions relative to the arm (its
 * 'InteractionPoints'), which a quarter-turned move has to turn with the building, as Create's own contraptions do
 * (ArmInteractionPoint.transformPos).
 */
public class QaCreateMoverArm1110eCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";
	static final BlockPos ARM = new BlockPos(5, 3, 5);
	static final BlockPos IN = new BlockPos(3, 3, 5);
	static final BlockPos OUT = new BlockPos(5, 3, 3);

	static CompoundTag point(BlockPos rel, String mode) {
		CompoundTag p = new CompoundTag();
		p.putString("Type", "create:depot");
		p.put("Pos", NbtUtils.writeBlockPos(rel));
		p.putString("Mode", mode);
		return p;
	}

	/** The world positions the arm at {@code arm} aims at now, read from its saved data. */
	static List<BlockPos> targets(ServerLevel level, BlockPos arm) {
		List<BlockPos> out = new ArrayList<>();
		BlockEntity be = level.getBlockEntity(arm);
		if (be == null) {
			return out;
		}
		ListTag points = be.saveWithoutMetadata(level.registryAccess()).getList("InteractionPoints", Tag.TAG_COMPOUND);
		for (int i = 0; i < points.size(); i++) {
			NbtUtils.readBlockPos(points.getCompound(i), "Pos").ifPresent(rel -> out.add(arm.offset(rel)));
		}
		return out;
	}

	/** An arm taking from the Depot to its west and putting on the one to its north, moved a quarter turned: still aimed at both. */
	//$ gametest_batch AREA '"qa_e1110b_mover_arm"'
	@GameTest(template = AREA, batch = "qa_e1110b_mover_arm")
	public void aMovedMechanicalArmStillAimsAtItsDepots(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		set(level, helper.absolutePos(IN), block("create:depot").defaultBlockState());
		set(level, helper.absolutePos(OUT), block("create:depot").defaultBlockState());
		set(level, helper.absolutePos(ARM), block("create:mechanical_arm").defaultBlockState());
		helper.runAfterDelay(2, () -> {
			BlockEntity be = level.getBlockEntity(helper.absolutePos(ARM));
			helper.assertTrue(be != null, "the arm has no block entity");
			CompoundTag data = be.saveWithoutMetadata(level.registryAccess());
			ListTag points = new ListTag();
			points.add(point(IN.subtract(ARM), "TAKE"));
			points.add(point(OUT.subtract(ARM), "DEPOSIT"));
			data.put("InteractionPoints", points);
			be.loadWithComponents(data, level.registryAccess());
			be.setChanged();
			helper.runAfterDelay(5, () -> {
				List<BlockPos> before = targets(level, helper.absolutePos(ARM));
				helper.assertTrue(before.equals(List.of(helper.absolutePos(IN), helper.absolutePos(OUT))),
					"before the move the arm aims at " + before + ", not the depots " + helper.absolutePos(IN) + ", " + helper.absolutePos(OUT));
				Setup s = setupAround(helper, "arm");
				helper.runAfterDelay(2, () -> {
					MoveSite site = work(level, s, x -> false);
					MoveSite.Stage end = site.stage();
					int skipped = site.skipped();
					BlockPos arm = moved(s, ARM);
					BlockPos in = moved(s, IN);
					BlockPos out = moved(s, OUT);
					helper.runAfterDelay(10, () -> { // the arm reads its targets again on its first ticks
						List<BlockPos> after = targets(level, arm);
						String states = level.getBlockState(arm) + " at " + arm.toShortString() + ", " + level.getBlockState(in) + " at " + in.toShortString()
							+ ", " + level.getBlockState(out) + " at " + out.toShortString();
						tidy(helper, s);
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
						helper.assertTrue(after.equals(List.of(in, out)), "after the move the arm aims at " + after + ", not its depots at "
							+ in.toShortString() + " (take) and " + out.toShortString() + " (put): " + states);
						helper.succeed();
					});
				});
			});
		});
	}
}
