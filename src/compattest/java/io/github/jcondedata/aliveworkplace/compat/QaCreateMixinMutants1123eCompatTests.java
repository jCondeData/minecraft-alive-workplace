package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.HALL;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.REACH;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.done;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.hall;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-e-1123-1313, mutation follow-up): the nightly mutation report let a mutant of ElevatorCabinMixin survive
 * (line 28, {@code return false;} to {@code return true;}: every block Create would leave behind because it needn't move,
 * a flower, grass, air, joins every contraption). Written from docs/wiki/create.md and B1487's text: "Elevators inside the
 * village, or with its protection off, take their cabin as before", and as before means as Create does it: a flower
 * growing beside the cabin is not part of it. Each test builds an Elevator Pulley with a cabin of slime, slime and an oak
 * plank (B1487's set-up) and a poppy on dirt beside the middle slime block, right-clicks the pulley and then breaks it:
 * <ul>
 * <li>beside an open village: the cabin takes the plank and sets it back, the poppy never leaves its spot;</li>
 * <li>a stranger's pulley outside a protected village: the plank and the poppy both stay;</li>
 * <li>Frank's pulley inside his own protected village: the cabin takes the plank and sets it back, the poppy stays.</li>
 * </ul>
 */
public class QaCreateMixinMutants1123eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");

	private static BlockState create(String path) {
		return MechanicCompatTests.create(path).defaultBlockState();
	}

	private static boolean inVillage(BlockPos pos) {
		long dx = pos.getX() - HALL.getX();
		long dz = pos.getZ() - HALL.getZ();
		return dx * dx + dz * dz <= (long) REACH * REACH;
	}

	private static List<AbstractContraptionEntity> contraptions(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(AbstractContraptionEntity.class, helper.getBounds().inflate(8));
	}

	/** Turns the hall's protection off just before {@code timeout}, so a failing test leaves no protected village behind. */
	private static void watchdog(GameTestHelper helper, int timeout) {
		helper.onEachTick(() -> {
			if (helper.getTick() >= timeout - 5 && helper.getLevel().getBlockEntity(helper.absolutePos(HALL)) instanceof VillageHallBlockEntity h) {
				h.setProtected(false);
			}
		});
	}

	private static void motorSpeed(GameTestHelper helper, BlockPos pos, int rpm) {
		BlockEntity be = helper.getBlockEntity(pos);
		CompoundTag tag = be.saveWithoutMetadata(helper.getLevel().registryAccess());
		tag.putInt("ScrollValue", rpm);
		be.loadWithComponents(tag, helper.getLevel().registryAccess());
		be.setChanged();
	}

	/**
	 * The elevator on row z = 15, its pulley at {@code px} (13: just outside the village; 8: inside it), the cabin hanging
	 * at y = 11 from x = px to px - 2 (slime, slime, oak plank), the motor east of the pulley and the Redstone Contact south
	 * of the cabin's first block. The poppy grows on dirt north of the middle slime block.
	 */
	private static void elevator(GameTestHelper helper, boolean protect, int px) {
		VillageHallBlockEntity hall = hall(helper, protect);
		watchdog(helper, 300);
		int z = 15;
		for (int x = px - 3; x <= px + 1; x++) {
			for (int zz = z - 1; zz <= z + 1; zz++) {
				for (int y = 2; y <= 12; y++) {
					helper.setBlock(new BlockPos(x, y, zz), Blocks.AIR);
				}
			}
		}
		BlockPos pulley = new BlockPos(px, 12, z);
		BlockPos mid = new BlockPos(px - 1, 11, z);
		BlockPos end = new BlockPos(px - 2, 11, z);
		BlockPos contact = new BlockPos(px, 11, z + 1);
		BlockPos motor = new BlockPos(px + 1, 12, z);
		BlockPos poppy = mid.north();
		boolean outside = !inVillage(pulley);
		if (outside ? !inVillage(end) || inVillage(mid) : !inVillage(end) || !inVillage(mid)) {
			done(helper, hall);
			throw new GameTestAssertException("set-up: the cabin isn't where this test needs it");
		}
		helper.setBlock(pulley, MechanicCompatTests.with(create("elevator_pulley"), "facing", "north"));
		helper.setBlock(pulley.below(), Blocks.SLIME_BLOCK);
		helper.setBlock(mid, Blocks.SLIME_BLOCK);
		helper.setBlock(end, Blocks.OAK_PLANKS);
		helper.setBlock(poppy.below(), Blocks.DIRT);
		helper.setBlock(poppy, Blocks.POPPY);
		helper.setBlock(contact, MechanicCompatTests.with(create("redstone_contact"), "facing", "south"));
		helper.setBlock(motor, MechanicCompatTests.with(create("creative_motor"), "facing", "west"));
		motorSpeed(helper, motor, 64);
		boolean may = VillageProtection.mayMachineChange(helper.getLevel(), helper.absolutePos(pulley), helper.absolutePos(end));
		if (protect && outside && may || !outside && !may) {
			done(helper, hall);
			throw new GameTestAssertException("set-up: the village's protection against the pulley isn't what this test needs (may change the plank: " + may + ")");
		}
		boolean takesPlank = !(protect && outside);
		int[] seen = {0};
		boolean[] plankWhileAssembled = {true};
		boolean[] poppyWhileAssembled = {false};
		helper.onEachTick(() -> seen[0] = Math.max(seen[0], contraptions(helper).size()));
		helper.runAfterDelay(10, () -> {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(pulley)) instanceof ElevatorPulleyBlockEntity e) {
				e.clicked(); // what right-clicking the pulley does
			}
		});
		helper.runAfterDelay(50, () -> {
			plankWhileAssembled[0] = helper.getBlockState(end).is(Blocks.OAK_PLANKS);
			poppyWhileAssembled[0] = helper.getBlockState(poppy).is(Blocks.POPPY);
		});
		helper.runAfterDelay(60, () -> helper.destroyBlock(pulley));
		helper.runAfterDelay(200, () -> {
			boolean plankAfter = helper.getBlockState(end).is(Blocks.OAK_PLANKS);
			boolean poppyAfter = helper.getBlockState(poppy).is(Blocks.POPPY);
			int planks = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(Items.OAK_PLANKS)).size();
			int poppies = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(Items.POPPY)).size();
			for (AbstractContraptionEntity e : contraptions(helper)) {
				e.discard();
			}
			done(helper, hall);
			String state = "pulley at " + pulley.toShortString() + (outside ? " (outside)" : " (inside)") + ", village " + (protect ? "protected" : "open")
				+ "; plank at " + end.toShortString() + " while the cabin was together: " + plankWhileAssembled[0] + ", after: " + plankAfter
				+ "; poppy beside the cabin at " + poppy.toShortString() + " while together: " + poppyWhileAssembled[0] + ", after: " + poppyAfter
				+ " (items on the ground: planks " + planks + ", poppies " + poppies + "; cabins seen " + seen[0] + ")";
			LOG.info("QA mutants elevator protect={} px={}: {}", protect, px, state);
			helper.assertTrue(seen[0] > 0, "the elevator never put its cabin together: " + state);
			helper.assertTrue(poppyWhileAssembled[0] && poppyAfter && poppies == 0,
				"the elevator's cabin took the poppy growing beside it (Create leaves a flower it needn't move where it is): " + state);
			if (takesPlank) {
				helper.assertTrue(!plankWhileAssembled[0], "the elevator didn't take its whole cabin as before (the plank stayed behind): " + state);
				helper.assertTrue(plankAfter && planks == 0, "breaking the pulley didn't set the plank back in its place: " + state);
			} else {
				helper.assertTrue(plankWhileAssembled[0] && plankAfter && planks == 0,
					"a stranger's elevator pulley outside Frank's protected village took the plank in the village's last cell: " + state);
			}
			helper.succeed();
		});
	}

	/** Beside an open village the elevator takes its cabin as before, and the poppy growing beside it stays where it is. */
	//$ gametest_ticks_batch AREA '300' '"qa_e1123m_elevator_open_poppy"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_e1123m_elevator_open_poppy")
	public void anElevatorBesideAnOpenVillageLeavesTheFlowerBesideItsCabin(GameTestHelper helper) {
		elevator(helper, false, 13);
	}

	/** A stranger's elevator outside a protected village leaves both the village's plank and the poppy beside its cabin. */
	//$ gametest_ticks_batch AREA '300' '"qa_e1123m_elevator_protected_poppy"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_e1123m_elevator_protected_poppy")
	public void aStrangersElevatorAtAProtectedVillageLeavesTheFlowerBesideItsCabin(GameTestHelper helper) {
		elevator(helper, true, 13);
	}

	/** Frank's elevator inside his own protected village takes its whole cabin as before, and leaves the poppy beside it. */
	//$ gametest_ticks_batch AREA '300' '"qa_e1123m_elevator_inside_poppy"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_e1123m_elevator_inside_poppy")
	public void anElevatorInsideAProtectedVillageTakesItsCabinAndLeavesTheFlower(GameTestHelper helper) {
		elevator(helper, true, 8);
	}
}
