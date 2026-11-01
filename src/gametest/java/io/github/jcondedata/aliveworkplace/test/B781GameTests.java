package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.people.RemedyItem;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.PhysicianWork;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.TradeDisplay;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * B781: the trade display's shown Remedy (a marked ware) is never the Physician's own (the display itself:
 * QaTradeDisplayPhysician1101BugTests). Here the edges: the ware kept through a save and load, an empty chest (no cure,
 * and the ware isn't spent either), and a real Remedy of his own in hand still cures without touching the chest.
 */
public class B781GameTests implements FabricGameTest {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final BlockPos STAND = new BlockPos(20, 2, 20);
	private static final BlockPos CHEST = new BlockPos(20, 2, 21);

	/** A shown Remedy saved and loaded with the Physician is still the display's: the cure spends the chest's Remedy. */
	//$ gametest_ticks_batch TALL '100' '"b781Reload"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "b781Reload")
	public void aShownRemedyKeptThroughASaveIsNeverUsedToCure(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager[] setup = setup(helper, 1);
		Villager physician = setup[0];
		Villager patient = setup[1];
		ItemStack shown = new ItemStack(ModItems.REMEDY);
		TradeDisplay.mark(shown);
		physician.setItemSlot(EquipmentSlot.MAINHAND, shown);
		physician.load(physician.saveWithoutId(new CompoundTag()));
		helper.assertTrue(TradeDisplay.isWare(physician.getMainHandItem()), "setup: the shown Remedy lost its mark in a save: " + physician.getMainHandItem());
		Container chest = helper.getBlockEntity(CHEST);
		PhysicianWork.visit(helper.getLevel(), physician, patient);
		helper.assertTrue(!Sickness.isIll(patient), "the patient wasn't cured with the chest's Remedy");
		helper.assertTrue(chest.countItem(ModItems.REMEDY) == 0, "the chest's Remedy wasn't spent: " + chest.countItem(ModItems.REMEDY));
		helper.assertTrue(!TradeDisplay.isWare(physician.getMainHandItem()), "the shown Remedy is still in hand: " + physician.getMainHandItem());
		helper.succeed();
	}

	/** No Remedy in the chest and nothing to brew one: a shown Remedy doesn't cure, and he asks for one instead. */
	//$ gametest_ticks_batch TALL '100' '"b781NoStock"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "b781NoStock")
	public void withAnEmptyChestAShownRemedyCuresNobody(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager[] setup = setup(helper, 0);
		Villager physician = setup[0];
		Villager patient = setup[1];
		ItemStack shown = new ItemStack(ModItems.REMEDY);
		TradeDisplay.mark(shown);
		physician.setItemSlot(EquipmentSlot.MAINHAND, shown);
		PhysicianWork.visit(helper.getLevel(), physician, patient);
		helper.assertTrue(Sickness.isIll(patient), "the patient was cured with the display's shown Remedy and an empty chest");
		helper.assertTrue(!RemedyItem.isRemedy(physician.getMainHandItem()), "a Remedy is still in hand: " + physician.getMainHandItem());
		helper.succeed();
	}

	/** A real Remedy of his own in hand (unmarked) still cures, and the chest's stock isn't touched. */
	//$ gametest_ticks_batch TALL '100' '"b781OwnRemedy"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "b781OwnRemedy")
	public void aRemedyOfHisOwnInHandStillCures(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager[] setup = setup(helper, 1);
		Villager physician = setup[0];
		Villager patient = setup[1];
		physician.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.REMEDY));
		Container chest = helper.getBlockEntity(CHEST);
		PhysicianWork.visit(helper.getLevel(), physician, patient);
		helper.assertTrue(!Sickness.isIll(patient), "the Physician's own Remedy didn't cure the patient");
		helper.assertTrue(chest.countItem(ModItems.REMEDY) == 1, "the chest's Remedy was spent with one in hand: " + chest.countItem(ModItems.REMEDY));
		helper.assertTrue(physician.getMainHandItem().isEmpty(), "the Remedy wasn't used up: " + physician.getMainHandItem());
		helper.succeed();
	}

	/** Illness and Physicians on; a Physician at a brewing stand with {@code remedies} in the chest; an ill villager. */
	private static Villager[] setup(GameTestHelper helper, int remedies) {
		ServerLevel level = helper.getLevel();
		boolean sick = Sickness.ENABLED;
		boolean physicians = PhysicianWork.ENABLED;
		Sickness.ENABLED = true;
		PhysicianWork.ENABLED = true;
		Leftovers.after(helper, () -> {
			Sickness.ENABLED = sick;
			PhysicianWork.ENABLED = physicians;
		});
		helper.setBlock(STAND, Blocks.BREWING_STAND);
		helper.setBlock(CHEST, Blocks.CHEST);
		if (remedies > 0) {
			((Container) helper.getBlockEntity(CHEST)).setItem(0, new ItemStack(ModItems.REMEDY, remedies));
		}
		Villager physician = villager(helper, STAND.getX() + 1, STAND.getZ(), "Physician Wren");
		Stations.assign(level, physician, helper.absolutePos(STAND), ModVillagers.PHYSICIAN);
		helper.assertTrue(PhysicianWork.isPhysician(physician), "setup: no Physician at the brewing stand");
		Villager patient = villager(helper, STAND.getX() - 3, STAND.getZ(), "Patient Ossie");
		Sickness.fallIll(level, patient);
		helper.assertTrue(Sickness.isIll(patient), "setup: the patient isn't ill");
		return new Villager[]{physician, patient};
	}

	private static Villager villager(GameTestHelper helper, int x, int z, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}
}
