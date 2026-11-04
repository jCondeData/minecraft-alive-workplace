package io.github.jcondedata.aliveworkplace.compat;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * QA lane E, 2026-11-04 (Create, B928 and B884 kin): the Mechanic (39.2) carries the part he took from the village store
 * in his hand on the way to the hole, and MechanicWork's header promises "a part in hand when the work stops goes back
 * to the store". B928 made a worker struck by lightning (vanilla's Villager.thunderHit turns him into a witch, no death)
 * leave "whatever is still in their bag" where he stood; these check the Mechanic's carried part the same way: struck
 * into a witch or killed with the cogwheel in hand, the store's cogwheel isn't lost (it is back in the store or on the
 * ground): those two fail today and wait on tests/mechanic-struck-1104 for the fix
 * (QaMechanicPartStruck1104eBugCompatTests). Here: one struck with empty hands leaves the store as it was.
 */
public class QaMechanicPartStruck1104eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** Struck before he takes anything: the store keeps its 2 cogwheels and none lies on the ground. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1104_mechanic_struck_empty"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1104_mechanic_struck_empty")
	public void aMechanicStruckWithEmptyHandsLeavesTheStoreAsItWas(GameTestHelper helper) {
		Setup s = setup(helper);
		strike(helper.getLevel(), s.mechanic());
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(s.mechanic().isRemoved(), "lightning didn't turn the Mechanic into a witch");
			helper.assertTrue(!helper.getLevel().getEntitiesOfClass(Witch.class, helper.getBounds().inflate(4)).isEmpty(), "no witch stands where he was");
			helper.assertTrue(MechanicCompatTests.count(s.store(), cog()) == 2, "the store holds " + MechanicCompatTests.count(s.store(), cog()) + " of its 2 cogwheels");
			helper.assertTrue(ground(helper, cog()) == 0, ground(helper, cog()) + " cogwheel(s) lie on the ground");
			helper.assertTrue(helper.getBlockState(s.hole()).isAir(), "the hole was filled by nobody: " + helper.getBlockState(s.hole()));
			helper.succeed();
		});
	}

	/**
	 * Struck right after he has set the cogwheel in the hole: the mend stands, the store keeps the 1 left, and no cogwheel
	 * is dropped for the one already used (nothing made out of nothing).
	 */
	//$ gametest_ticks_batch AREA '1600' '"qa_e1104_mechanic_struck_after"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_e1104_mechanic_struck_after")
	public void aMechanicStruckAfterTheMendLeavesItStandingAndDropsNothing(GameTestHelper helper) {
		Setup s = setup(helper);
		int[] struckAt = {-1};
		helper.onEachTick(() -> {
			if (struckAt[0] < 0 && helper.getBlockState(s.hole()).is(MechanicCompatTests.create("cogwheel"))) {
				struckAt[0] = (int) helper.getTick();
				strike(helper.getLevel(), s.mechanic());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(struckAt[0] >= 0, "the Mechanic never set the cogwheel in the hole");
			helper.assertTrue(helper.getTick() >= struckAt[0] + 40, "waiting 40 ticks after the strike");
			helper.assertTrue(s.mechanic().isRemoved(), "lightning didn't turn the Mechanic into a witch");
			helper.assertTrue(helper.getBlockState(s.hole()).is(MechanicCompatTests.create("cogwheel")), "the mended cogwheel is gone: " + helper.getBlockState(s.hole()));
			helper.assertTrue(MechanicCompatTests.count(s.store(), cog()) == 1, "the store holds " + MechanicCompatTests.count(s.store(), cog()) + " cogwheels, not the 1 left");
			helper.assertTrue(ground(helper, cog()) == 0, ground(helper, cog()) + " cogwheel(s) lie on the ground");
		});
	}

	record Setup(BlockPos hole, Container store, Villager mechanic) {
	}

	/** The test drive line with its cogwheel gone, a store of 2 cogwheels and a Mechanic at work (day, Normal). */
	static Setup setup(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.assertTrue(helper.getLevel().getDifficulty() != Difficulty.PEACEFUL, "setup: the test world is on Peaceful, where lightning makes no witch");
		List<BlockPos> line = MechanicCompatTests.finishedLine(helper);
		Container store = MechanicCompatTests.chest(helper, new ItemStack(cog(), 2));
		Villager mechanic = MechanicCompatTests.mechanic(helper);
		BlockPos hole = line.get(3);
		helper.setBlock(hole, net.minecraft.world.level.block.Blocks.AIR);
		helper.setDayTime(2000);
		return new Setup(hole, store, mechanic);
	}

	static Item cog() {
		return MechanicCompatTests.createItem("cogwheel");
	}

	static void strike(ServerLevel level, Villager villager) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		bolt.moveTo(villager.position());
		bolt.setVisualOnly(true);
		villager.thunderHit(level, bolt);
	}

	static int ground(GameTestHelper helper, Item item) {
		int n = 0;
		for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8))) {
			if (drop.getItem().is(item)) {
				n += drop.getItem().getCount();
			}
		}
		return n;
	}
}
