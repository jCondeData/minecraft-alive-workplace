package io.github.jcondedata.aliveworkplace.compat;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane E, 2026-11-04 (saves; B884's fix, ROADMAP 39.2): "a part in hand that no hole needs (after a save) goes back
 * too" and "a part in hand ... goes back to the store by the toolbox (or at its foot when the store is full)"
 * (MechanicWork). {@code QaMechanicPartInHandSaved1103eCompatTests} has the store with room; here the cogwheel he carried
 * through a save, its hole filled by a player meanwhile, has nowhere in the store to go: the store filled to the brim,
 * or the store broken. Either way it is out of his hand and on the ground, never lost.
 */
public class QaMechanicHandNoRoom1104eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	/** MechanicCompatTests.chest's store. */
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/** The store brim full while he was unloaded: the cogwheel lies at the toolbox's foot, the store's 64 untouched. */
	//$ gametest_ticks_batch AREA '2400' '"qa_e1104_mechanic_hand_store_full"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_e1104_mechanic_hand_store_full")
	public void aCogwheelInHandThroughASaveWithTheStoreFullLiesAtTheToolbox(GameTestHelper helper) {
		Item cog = MechanicCompatTests.createItem("cogwheel");
		Villager[] reloaded = setup(helper, store -> {
			store.setItem(0, new ItemStack(cog, 64)); // a player tops the cogwheels up and fills every other slot
			for (int i = 1; i < store.getContainerSize(); i++) {
				store.setItem(i, new ItemStack(Items.DIRT, 64));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0] != null, "the Mechanic never took the cogwheel from the store");
			Container store = (Container) helper.getBlockEntity(CHEST);
			helper.assertTrue(reloaded[0].getMainHandItem().isEmpty(), "he still holds " + reloaded[0].getMainHandItem());
			helper.assertTrue(MechanicCompatTests.count(store, cog) == 64, "the full store holds " + MechanicCompatTests.count(store, cog) + " cogwheels");
			helper.assertTrue(ground(helper, cog) == 1, "the cogwheel he carried isn't on the ground by his toolbox: " + ground(helper, cog)
				+ " there, his hand " + reloaded[0].getMainHandItem());
		});
	}

	/** The store broken while he was unloaded (its cogwheel spills): his cogwheel goes on the ground too, both kept. */
	//$ gametest_ticks_batch AREA '2400' '"qa_e1104_mechanic_hand_store_gone"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_e1104_mechanic_hand_store_gone")
	public void aCogwheelInHandThroughASaveWithTheStoreBrokenIsDropped(GameTestHelper helper) {
		Item cog = MechanicCompatTests.createItem("cogwheel");
		Villager[] reloaded = setup(helper, store -> helper.getLevel().destroyBlock(helper.absolutePos(CHEST), true));
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0] != null, "the Mechanic never took the cogwheel from the store");
			helper.assertTrue(reloaded[0].getMainHandItem().isEmpty(), "he still holds " + reloaded[0].getMainHandItem());
			helper.assertTrue(ground(helper, cog) == 2, "of the 2 cogwheels (the store's spilled one and the one he carried) "
				+ ground(helper, cog) + " lie on the ground, his hand " + reloaded[0].getMainHandItem());
		});
	}

	/**
	 * The test drive line with its cogwheel gone, a store of 2 cogwheels and a Mechanic; the tick he holds the cogwheel he
	 * took, he is saved, a player fills the hole and does {@code meanwhile} to the store, and he is loaded in his place.
	 */
	private static Villager[] setup(GameTestHelper helper, java.util.function.Consumer<Container> meanwhile) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		List<BlockPos> line = MechanicCompatTests.finishedLine(helper);
		Container store = MechanicCompatTests.chest(helper, new ItemStack(MechanicCompatTests.createItem("cogwheel"), 2));
		Villager mechanic = MechanicCompatTests.mechanic(helper);
		BlockPos hole = line.get(3);
		helper.setBlock(hole, Blocks.AIR);
		helper.setDayTime(2000);
		Villager[] reloaded = new Villager[1];
		helper.onEachTick(() -> {
			if (reloaded[0] != null || !mechanic.getMainHandItem().is(MechanicCompatTests.createItem("cogwheel"))) {
				return;
			}
			helper.assertTrue(MechanicCompatTests.count(store, MechanicCompatTests.createItem("cogwheel")) == 1,
				"setup: the store doesn't hold the 1 cogwheel left after he took one");
			CompoundTag tag = new CompoundTag();
			if (!mechanic.save(tag)) {
				throw new GameTestAssertException("the Mechanic wasn't saved");
			}
			mechanic.discard();
			helper.setBlock(hole, MechanicCompatTests.with(MechanicCompatTests.create("cogwheel").defaultBlockState(), "axis", "x"));
			meanwhile.accept(store);
			Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
			if (!(back instanceof Villager villager)) {
				throw new GameTestAssertException("the Mechanic didn't load as a villager: " + back);
			}
			level.addFreshEntity(villager);
			reloaded[0] = villager;
		});
		return reloaded;
	}

	private static int ground(GameTestHelper helper, Item item) {
		int n = 0;
		for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8))) {
			if (drop.getItem().is(item)) {
				n += drop.getItem().getCount();
			}
		}
		return n;
	}
}
