package io.github.jcondedata.aliveworkplace.compat;

import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * QA (lane-e-1123, round 2): more Create item paths at a protected village's edge, from the spec. create.md ("Create's
 * machines keep out of a protected village": "Item transport stops at the edge too: a funnel or chute outside finds no
 * inventory in a chest or barrel inside the village (B994)") and B994 (vanilla has no horizontal path into a chest from
 * the next column, so a stranger's Create machine outside takes nothing from, and puts nothing into, a container inside
 * a protected village). Frank's chest stands in the village's last column at (11, 2, 15); the stranger's machine at
 * x = 12, outside:
 *
 * <ul>
 *   <li>A Mechanical Crafter whose output points west at Frank's chest (Create inserts its result through its guarded
 *       inventory behaviour). Control: open village, the ingots land in the chest.</li>
 *   <li>A Mechanical Belt whose end (x = 12) faces Frank's chest (B1520 covers the belt onto a Depot). Create hands a belt's
 *       items on only to blocks with a belt input (a depot, belt, funnel), never to a chest, so the chest must stay empty
 *       with the village protected or open (the diamonds are thrown off the end and lie loose on the chest); the diamonds
 *       reaching the belt's end prove it ran.</li>
 *   <li>A Frogport beside Frank's chest: it pushes packages only into the block below it (its own column, outside), never
 *       sideways into his chest. (A Postbox reaches no neighbouring inventory at all, so it has no case here.)</li>
 * </ul>
 *
 * Frank's hall is {@link QaCreateDeployerUses1122eCompatTests#hall}'s: at (3, 2, 15), 8 columns across.
 */
public class QaCreateEdgeTransport1123eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	static final int Z = 15;
	static final BlockPos CHEST = new BlockPos(11, 2, Z);
	static final BlockPos MACHINE = CHEST.east();

	static boolean inside(BlockPos p) {
		return QaCreateHosePulley1122eCompatTests.inside(p);
	}

	static void clear(GameTestHelper helper) {
		for (int x = 6; x <= 20; x++) {
			for (int y = 2; y <= 6; y++) {
				for (int z = Z - 2; z <= Z + 2; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}

	/** Frank's hall and his empty chest at {@link #CHEST}; the protection rule checked for a machine at {@link #MACHINE}. */
	static Container frank(GameTestHelper helper, boolean protect) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		clear(helper);
		helper.setBlock(CHEST, Blocks.CHEST);
		helper.assertTrue(inside(CHEST) && !inside(MACHINE), "set-up: Frank's chest isn't in the village's last column");
		QaCreateCannonPipes1122eCompatTests.ruleSays(helper, protect, MACHINE, CHEST);
		return helper.getBlockEntity(CHEST);
	}

	static int count(Container c, Item item) {
		return QaCreateEdgeVerify1123eCompatTests.count(c, item);
	}

	static long loose(GameTestHelper helper, Item item) {
		long n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	// ---------------------------------------------------------------- 1. a Mechanical Crafter's output

	/** The crafter state whose output points west, with its cogs turning about the z axis. */
	static BlockState crafterPointingWest() {
		Block block = MechanicCompatTests.create("mechanical_crafter");
		for (BlockState s : block.getStateDefinition().getPossibleStates()) {
			if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && s.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis() == Direction.Axis.Z
				&& MechanicalCrafterBlock.getTargetDirection(s) == Direction.WEST) {
				return s;
			}
		}
		throw new GameTestAssertException("set-up: no Mechanical Crafter state points west");
	}

	/**
	 * A stranger's lone Mechanical Crafter at {@link #MACHINE}, its output pointing west at Frank's chest, a cogwheel
	 * above it turned by a creative motor; an iron block put in at tick 10 (a 1x1 recipe: 9 iron ingots).
	 */
	static void crafter(GameTestHelper helper, boolean protect) {
		Container chest = frank(helper, protect);
		BlockState crafter = crafterPointingWest();
		helper.setBlock(MACHINE, crafter);
		BlockPos cog = MACHINE.above();
		helper.setBlock(cog, MechanicCompatTests.with(MechanicCompatTests.create("cogwheel").defaultBlockState(), "axis", "z"));
		helper.runAfterDelay(2, () -> helper.setBlock(cog.north(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "south")));
		long[] put = {-1};
		helper.runAfterDelay(10, () -> put[0] = QaCreateDeployerOnOurThings1102eCompatTests.insert(helper.getLevel(), helper.absolutePos(MACHINE), new ItemStack(Items.IRON_BLOCK)));
		float[] turned = {0};
		helper.onEachTick(() -> turned[0] = Math.max(turned[0], Math.abs(QaCreateDeployerOnOurThings1102eCompatTests.speed(helper, MACHINE))));
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 300, () -> {
			int ingots = count(chest, Items.IRON_INGOT);
			long looseIngots = loose(helper, Items.IRON_INGOT);
			String state = ingots + " iron ingots in Frank's chest, " + looseIngots + " loose nearby; the crafter took " + put[0] + " iron block, turned at up to "
				+ turned[0] + " rpm (" + helper.getBlockState(MACHINE) + ")";
			QaCreateItemEdge1122eCompatTests.discardItems(helper);
			helper.assertTrue(turned[0] != 0, "set-up: the stranger's crafter never turned: " + state);
			helper.assertTrue(put[0] == 1, "set-up: the crafter didn't take the iron block: " + state);
			if (protect) {
				helper.assertTrue(ingots == 0 && count(chest, null) == 0, "a stranger's Mechanical Crafter outside Frank's protected village put its result into"
					+ " his chest in its last column: " + state);
				helper.assertTrue(looseIngots > 0, "the stranger's crafter crafted nothing (no ingots anywhere), so the protected case proves nothing: " + state);
			} else {
				helper.assertTrue(ingots == 9, "the control (village open): the crafter didn't put its 9 ingots into Frank's chest: " + state);
			}
		});
	}

	/** A stranger's crafter outside puts nothing into Frank's chest in his protected village (the ingots drop outside). */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123t_crafter_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123t_crafter_protected")
	public void aStrangersCrafterPutsNothingIntoAChestInAProtectedVillage(GameTestHelper helper) {
		crafter(helper, true);
	}

	/** The control: with the village open the crafter's ingots go into Frank's chest. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123t_crafter_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123t_crafter_open")
	public void aStrangersCrafterPutsItsResultIntoAChestInAnOpenVillage(GameTestHelper helper) {
		crafter(helper, false);
	}

	// ---------------------------------------------------------------- 2. a Mechanical Belt ending at Frank's chest

	static final BlockPos BELT_START = new BlockPos(16, 2, Z);

	/** A stranger's belt from x = 16 to x = 12 running west, its end facing Frank's chest; a diamond put on every 20 ticks to tick 100. */
	static void belt(GameTestHelper helper, boolean protect) {
		Container chest = frank(helper, protect);
		BlockState shaft = MechanicCompatTests.with(MechanicCompatTests.create("shaft").defaultBlockState(), "axis", "z");
		helper.setBlock(BELT_START, shaft);
		helper.setBlock(MACHINE, shaft);
		BlockPos start = helper.absolutePos(BELT_START);
		BlockPos end = helper.absolutePos(MACHINE);
		helper.assertTrue(BeltConnectorItem.canConnect(helper.getLevel(), start, end), "set-up: Create won't connect the two shafts");
		BeltConnectorItem.createBelts(helper.getLevel(), start, end);
		helper.setBlock(BELT_START.north(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "south"));
		int[] loaded = {0};
		long[] atEnd = {0};
		helper.onEachTick(() -> {
			long t = helper.getTick();
			if (t % 20 == 0 && t > 0 && t <= 100) {
				loaded[0] += (int) QaCreateDeployerOnOurThings1102eCompatTests.insert(helper.getLevel(), helper.absolutePos(BELT_START.west()), new ItemStack(Items.DIAMOND, 1));
			}
			atEnd[0] = Math.max(atEnd[0], QaCreateItemEdge1122eCompatTests.diamonds(helper, MACHINE));
		});
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 360, () -> {
			float turning = QaCreateDeployerOnOurThings1102eCompatTests.speed(helper, BELT_START);
			int inChest = count(chest, Items.DIAMOND);
			long onBelt = 0;
			for (int x = MACHINE.getX(); x <= BELT_START.getX(); x++) {
				onBelt += QaCreateItemEdge1122eCompatTests.diamonds(helper, new BlockPos(x, 2, Z));
			}
			long looseInside = QaCreateItemEdge1122eCompatTests.looseInside(helper);
			String state = inChest + " of " + loaded[0] + " diamonds in Frank's chest, " + onBelt + " on the belt (up to " + atEnd[0] + " on its end segment), "
				+ looseInside + " loose inside the village";
			QaCreateItemEdge1122eCompatTests.discardItems(helper);
			helper.assertTrue(turning != 0, "set-up: the belt never turned: " + state);
			helper.assertTrue(loaded[0] > 0 && atEnd[0] > 0, "set-up: no diamond reached the belt's end, so the test proves nothing: " + state);
			// (diamonds the belt throws off its end may land on top of the chest as loose items, as any thrown item may)
			helper.assertTrue(inChest == 0, "a stranger's Mechanical Belt outside Frank's " + (protect ? "protected" : "open")
				+ " village handed diamonds off its end into his chest in its last column: " + state);
		});
	}

	/** A stranger's belt ending at Frank's chest in his protected village puts nothing into it. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1123t_belt_protected"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1123t_belt_protected")
	public void aStrangersBeltEndingAtAChestInAProtectedVillagePutsNothingIn(GameTestHelper helper) {
		belt(helper, true);
	}

	/** Create's own rule, the same in an open village: a belt's end hands nothing into a chest (only into belt inputs). */
	//$ gametest_ticks_batch AREA '500' '"qa_e1123t_belt_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1123t_belt_open")
	public void aBeltEndingAtAChestInAnOpenVillagePutsNothingInEither(GameTestHelper helper) {
		belt(helper, false);
	}

	// ---------------------------------------------------------------- 3. a Frogport beside Frank's chest

	/**
	 * A stranger's Frogport at {@link #MACHINE} holding a package of 4 emeralds, his own chest under it at (12, 1, 15):
	 * the package goes down into his chest, never sideways into Frank's.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa_e1123t_frogport_protected"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_e1123t_frogport_protected")
	public void aStrangersFrogportPushesNoPackageIntoAChestInAProtectedVillage(GameTestHelper helper) {
		Container chest = frank(helper, true);
		BlockPos below = MACHINE.below();
		helper.setBlock(below, Blocks.CHEST);
		Container theirs = helper.getBlockEntity(below);
		helper.setBlock(MACHINE, MechanicCompatTests.create("package_frogport").defaultBlockState());
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(MACHINE)) instanceof PackagePortBlockEntity port)) {
			throw new GameTestAssertException("set-up: no Frogport block entity at " + MACHINE.toShortString());
		}
		port.inventory.setStackInSlot(0, PackageItem.containing(List.of(new ItemStack(Items.EMERALD, 4))));
		port.setChanged();
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 200, () -> {
			int franks = count(chest, null);
			int below2 = count(theirs, null);
			int held = 0;
			for (int s = 0; s < port.inventory.getSlotCount(); s++) {
				held += port.inventory.getStackInSlot(s).getCount();
			}
			String state = franks + " items in Frank's chest, " + below2 + " in the stranger's chest under the frogport, " + held + " still in the frogport";
			helper.assertTrue(franks == 0, "a stranger's Frogport outside Frank's protected village pushed a package into his chest in its last column: " + state);
			helper.assertTrue(below2 > 0, "set-up: the frogport pushed its package nowhere, so the test proves nothing: " + state);
		});
	}
}
