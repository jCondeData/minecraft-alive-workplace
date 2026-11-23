package io.github.jcondedata.aliveworkplace.compat;

import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * B1520's other side: the guard that keeps a stranger's Weighted Ejector and belt outside a protected village from
 * handing items onto a depot inside it (EjectorTargetMixin, BeltEndMixin) leaves Frank's own machines alone. His
 * ejector and his belt, standing wholly inside his protected village (hall at (3, 2, 15), as in
 * {@link QaCreateItemEdge1122eCompatTests}), still deliver diamonds onto his depot inside it. The same geometry as
 * that class, moved 5 blocks west so that everything is in the village.
 */
public class CreateItemEdgeInsideCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	static final int Z = QaCreateItemEdge1122eCompatTests.Z;

	/** Frank's empty depot at {@code depot}, inside his protected village, and the rule checked for a machine at {@code machine}. */
	static void frank(GameTestHelper helper, BlockPos depot, BlockPos machine) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, true);
		QaCreateItemEdge1122eCompatTests.clear(helper);
		helper.setBlock(depot, MechanicCompatTests.create("depot"));
		helper.assertTrue(QaCreateItemEdge1122eCompatTests.inside(depot) && QaCreateItemEdge1122eCompatTests.inside(machine),
			"set-up: the depot and the machine aren't both in Frank's village");
		helper.assertTrue(VillageProtection.mayMachineChange(helper.getLevel(), helper.absolutePos(machine), helper.absolutePos(depot)),
			"set-up: the protection rule says Frank's own machine inside may not change his depot");
	}

	static final BlockPos EJECTOR = new BlockPos(10, 2, Z);
	static final BlockPos EJECTOR_DEPOT = new BlockPos(6, 2, Z);

	/** Frank's Weighted Ejector inside his protected village launches diamonds onto his depot there. */
	//$ gametest_ticks_batch AREA '400' '"create_item_edge_inside_ejector"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "create_item_edge_inside_ejector")
	public void anOwnersEjectorInsideHisProtectedVillageStillLaunchesOntoHisDepot(GameTestHelper helper) {
		frank(helper, EJECTOR_DEPOT, EJECTOR);
		helper.setBlock(EJECTOR.north(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "south"));
		helper.setBlock(EJECTOR, MechanicCompatTests.with(MechanicCompatTests.create("weighted_ejector").defaultBlockState(), "facing", "west"));
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(EJECTOR)) instanceof EjectorBlockEntity be)) {
			throw new GameTestAssertException("set-up: no Weighted Ejector block entity at " + EJECTOR.toShortString());
		}
		be.setTarget(EJECTOR.getX() - EJECTOR_DEPOT.getX(), 0);
		BlockPos aimed = be.getTargetPosition().subtract(helper.absolutePos(BlockPos.ZERO));
		helper.assertTrue(aimed.equals(EJECTOR_DEPOT), "set-up: the ejector aims at " + aimed.toShortString() + ", not Frank's depot");
		int[] loaded = {0};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 20 == 0 && loaded[0] < 4 && QaCreateItemEdge1122eCompatTests.diamonds(helper, EJECTOR) == 0) {
				loaded[0] += (int) QaCreateDeployerOnOurThings1102eCompatTests.insert(helper.getLevel(), helper.absolutePos(EJECTOR), new ItemStack(Items.DIAMOND, 1));
			}
		});
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 300, () -> {
			float turning = QaCreateDeployerOnOurThings1102eCompatTests.speed(helper, EJECTOR);
			long onDepot = QaCreateItemEdge1122eCompatTests.diamonds(helper, EJECTOR_DEPOT);
			long onPlate = QaCreateItemEdge1122eCompatTests.diamonds(helper, EJECTOR);
			long loose = QaCreateItemEdge1122eCompatTests.looseAll(helper);
			QaCreateItemEdge1122eCompatTests.discardItems(helper);
			helper.assertTrue(turning != 0, "set-up: the ejector never turned, so the test proves nothing");
			helper.assertTrue(loaded[0] > 0, "set-up: the ejector took no diamond");
			helper.assertTrue(onDepot > 0, "Frank's own Weighted Ejector inside his protected village put no diamond on his depot: " + loaded[0]
				+ " loaded, " + onPlate + " still on the ejector, " + loose + " loose");
		});
	}

	static final BlockPos BELT_START = new BlockPos(10, 2, Z);
	static final BlockPos BELT_END = new BlockPos(6, 2, Z);
	static final BlockPos BELT_DEPOT = BELT_END.west();

	/** Frank's belt inside his protected village carries diamonds onto his depot at its end. */
	//$ gametest_ticks_batch AREA '500' '"create_item_edge_inside_belt"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "create_item_edge_inside_belt")
	public void anOwnersBeltInsideHisProtectedVillageStillCarriesOntoHisDepot(GameTestHelper helper) {
		frank(helper, BELT_DEPOT, BELT_END);
		helper.assertTrue(QaCreateItemEdge1122eCompatTests.inside(BELT_START), "set-up: the belt's start isn't in Frank's village");
		var shaft = MechanicCompatTests.with(MechanicCompatTests.create("shaft").defaultBlockState(), "axis", "z");
		helper.setBlock(BELT_START, shaft);
		helper.setBlock(BELT_END, shaft);
		BlockPos start = helper.absolutePos(BELT_START);
		BlockPos end = helper.absolutePos(BELT_END);
		helper.assertTrue(BeltConnectorItem.canConnect(helper.getLevel(), start, end), "set-up: Create won't connect the two shafts");
		BeltConnectorItem.createBelts(helper.getLevel(), start, end);
		// as QaCreateItemEdge1122eCompatTests' belt with reverse: the motor north of the start, so the belt runs west
		helper.setBlock(BELT_START.north(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "south"));
		int[] loaded = {0};
		int[] tick = {0};
		BlockPos feed = BELT_START.west();
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 20 == 0 && tick[0] <= 100) {
				loaded[0] += (int) QaCreateDeployerOnOurThings1102eCompatTests.insert(helper.getLevel(), helper.absolutePos(feed), new ItemStack(Items.DIAMOND, 1));
			}
		});
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 360, () -> {
			float turning = QaCreateDeployerOnOurThings1102eCompatTests.speed(helper, BELT_START);
			long onDepot = QaCreateItemEdge1122eCompatTests.diamonds(helper, BELT_DEPOT);
			long onBelt = 0;
			for (int x = BELT_END.getX(); x <= BELT_START.getX(); x++) {
				onBelt += QaCreateItemEdge1122eCompatTests.diamonds(helper, new BlockPos(x, 2, Z));
			}
			long loose = QaCreateItemEdge1122eCompatTests.looseAll(helper);
			QaCreateItemEdge1122eCompatTests.discardItems(helper);
			helper.assertTrue(turning != 0, "set-up: the belt never turned, so the test proves nothing");
			helper.assertTrue(loaded[0] > 0, "set-up: the belt took no diamond");
			helper.assertTrue(onDepot > 0, "Frank's own belt inside his protected village put no diamond on his depot at its end: " + loaded[0]
				+ " put on, " + onBelt + " still on the belt, " + loose + " loose");
		});
	}
}
