package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.held;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.speed;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-e-1123, round 4): a stranger's Deployer outside a protected village using tools on the block in its last
 * column. create.md: "A stranger's deployer in a protected village can do only what the stranger may", and B1015 and
 * B1501 (a stranger's Deployer outside changes nothing inside). Earlier classes cover placing, buckets, lava, bone meal,
 * flint and steel, the axe on a log and punching stone ({@link QaCreateDeployerUses1122eCompatTests},
 * {@link QaCreateEdgeVerify1123eCompatTests}); this one adds the tools they left out: Create's wrench on a shaft, a
 * shovel on grass and a hoe on dirt.
 *
 * The deployer is {@link QaCreateDeployerUses1122eCompatTests}'s: the stranger's, at (13, 2, 15) outside, facing west, in
 * use mode, its hand reaching {@link QaCreateDeployerUses1122eCompatTests#TARGET} (11, 2, 15), the village's last column
 * (x = 12, between them, is outside and empty). Each case has its open-village control: the same deployer changes the block.
 */
public class QaCreateDeployerTools1123eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	static final BlockPos DEPLOYER = QaCreateDeployerUses1122eCompatTests.DEPLOYER;
	static final BlockPos TARGET = QaCreateDeployerUses1122eCompatTests.TARGET;

	/**
	 * Frank's {@code block} at the target (air above it and between it and the deployer); the stranger's deployer in use
	 * mode holding {@code tool}. Protected: the block never changes in 300 ticks; open: it has become {@code changed}
	 * (or, with {@code changed} null, it changed at some tick: a wrench turns the shaft back and forth).
	 */
	static void use(GameTestHelper helper, boolean protect, BlockState block, ItemStack tool, BlockState changed) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		for (int x = TARGET.getX(); x < DEPLOYER.getX(); x++) {
			for (int y = 2; y <= 4; y++) {
				helper.setBlock(new BlockPos(x, y, TARGET.getZ()), Blocks.AIR);
			}
		}
		helper.setBlock(TARGET.below(), Blocks.STONE);
		helper.setBlock(TARGET, block);
		helper.assertTrue(QaCreateHosePulley1122eCompatTests.inside(TARGET) && !QaCreateHosePulley1122eCompatTests.inside(TARGET.east()), "set-up: the target isn't in the village's last column");
		QaCreateCannonPipes1122eCompatTests.ruleSays(helper, protect, DEPLOYER, TARGET);
		QaCreateDeployerOnOurThings1102eCompatTests.deployer(helper, DEPLOYER, QaCreateDeployerUses1122eCompatTests.STRANGER, tool);
		if (!"USE".equalsIgnoreCase(QaCreateDeployerUses1122eCompatTests.mode(helper, DEPLOYER))) {
			throw new GameTestAssertException("set-up: a new deployer isn't in use mode: " + QaCreateDeployerUses1122eCompatTests.mode(helper, DEPLOYER));
		}
		String[] firstChange = {""};
		helper.onEachTick(() -> {
			if (firstChange[0].isEmpty() && helper.getBlockState(TARGET) != block) {
				firstChange[0] = helper.getBlockState(TARGET) + " at tick " + helper.getTick();
			}
		});
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 300, () -> {
			float turning = speed(helper, DEPLOYER);
			BlockState now = helper.getBlockState(TARGET);
			String state = "the block was " + block + ", now " + now + (firstChange[0].isEmpty() ? "" : " (first changed to " + firstChange[0] + ")") + "; holding "
				+ held(helper, DEPLOYER);
			helper.assertTrue(turning != 0, "set-up: the deployer never turned, so the test proves nothing: " + state);
			if (protect) {
				helper.assertTrue(firstChange[0].isEmpty(), "a stranger's Deployer with " + tool.getHoverName().getString() + ", outside Frank's protected village,"
					+ " changed the block in its last column: " + state);
			} else {
				helper.assertTrue(changed == null ? !firstChange[0].isEmpty() : now == changed, "the control (village open): the stranger's deployer with "
					+ tool.getHoverName().getString() + " didn't change the block as a player would: " + state);
			}
		});
	}

	/** An upright Create shaft: a wrench on its east face turns it to lie along z. */
	static BlockState shaft() {
		return MechanicCompatTests.with(MechanicCompatTests.create("shaft").defaultBlockState(), "axis", "y");
	}

	static ItemStack wrench() {
		return new ItemStack(MechanicCompatTests.createItem("wrench"));
	}

	/** A stranger's deployer with Create's wrench neither turns nor picks up Frank's shaft. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123d_wrench_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123d_wrench_protected")
	public void aStrangersDeployerWrenchesNothingInAProtectedVillage(GameTestHelper helper) {
		use(helper, true, shaft(), wrench(), null);
	}

	/** The control: in an open village the wrench turns the shaft. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123d_wrench_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123d_wrench_open")
	public void aStrangersDeployerWrenchesAShaftInAnOpenVillage(GameTestHelper helper) {
		use(helper, false, shaft(), wrench(), null);
	}

	/** A stranger's deployer with a shovel makes no path on Frank's grass. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123d_shovel_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123d_shovel_protected")
	public void aStrangersDeployerMakesNoPathInAProtectedVillage(GameTestHelper helper) {
		use(helper, true, Blocks.GRASS_BLOCK.defaultBlockState(), new ItemStack(Items.IRON_SHOVEL), Blocks.DIRT_PATH.defaultBlockState());
	}

	/** The control: in an open village the shovel makes a path. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123d_shovel_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123d_shovel_open")
	public void aStrangersDeployerMakesAPathInAnOpenVillage(GameTestHelper helper) {
		use(helper, false, Blocks.GRASS_BLOCK.defaultBlockState(), new ItemStack(Items.IRON_SHOVEL), Blocks.DIRT_PATH.defaultBlockState());
	}

	/** A stranger's deployer with a hoe tills none of Frank's dirt. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123d_hoe_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123d_hoe_protected")
	public void aStrangersDeployerTillsNothingInAProtectedVillage(GameTestHelper helper) {
		use(helper, true, Blocks.DIRT.defaultBlockState(), new ItemStack(Items.IRON_HOE), Blocks.FARMLAND.defaultBlockState());
	}

	/** The control: in an open village the hoe tills the dirt. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123d_hoe_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123d_hoe_open")
	public void aStrangersDeployerTillsDirtInAnOpenVillage(GameTestHelper helper) {
		use(helper, false, Blocks.DIRT.defaultBlockState(), new ItemStack(Items.IRON_HOE), Blocks.FARMLAND.defaultBlockState());
	}
}
