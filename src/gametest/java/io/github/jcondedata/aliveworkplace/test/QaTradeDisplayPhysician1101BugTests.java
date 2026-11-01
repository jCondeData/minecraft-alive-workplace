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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-e-1101-1913), B776 from its spec: "a ware is the display's, never the worker's: never bagged, stored or used
 * as a tool". The Physician sells the Remedy (level 1, 4 emeralds), so a player standing by with emeralds makes the
 * trade display put a marked Remedy in the Physician's empty hand, the hand the Physician cures with. The real Remedies
 * are in the chest by the brewing stand: a cure must spend one of them, never the display's copy (a free cure, and the
 * stock never goes down). PhysicianWork reads the hand directly (not through TradeDisplay.workHand).
 */
public class QaTradeDisplayPhysician1101BugTests implements FabricGameTest {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final BlockPos STAND = new BlockPos(20, 2, 20);
	private static final BlockPos CHEST = new BlockPos(20, 2, 21);

	/** A shown (marked) Remedy in hand, as the display leaves it (or as a restart keeps it): the cure takes the chest's. */
	//$ gametest_ticks_batch TALL '100' '"qaPhysicianWareHand1101"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaPhysicianWareHand1101")
	public void qaAPhysicianNeverCuresWithTheDisplaysRemedy(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager[] setup = setup(helper);
		Villager physician = setup[0];
		Villager patient = setup[1];
		ItemStack shown = new ItemStack(ModItems.REMEDY);
		TradeDisplay.mark(shown);
		physician.setItemSlot(EquipmentSlot.MAINHAND, shown);
		cureAndCheck(helper, physician, patient);
	}

	/** The real display: a player with emeralds beside the Physician until the Remedy is on show; then a villager is seen. */
	//$ gametest_ticks_batch TALL '1400' '"qaPhysicianWareDisplay1101"'
	@GameTest(template = TALL, timeoutTicks = 1400, batch = "qaPhysicianWareDisplay1101")
	public void qaAPhysicianShowingARemedyStillCuresWithTheChestsOwn(GameTestHelper helper) {
		Leftovers.clear(helper);
		// The Physician's evening idle hour, when the display runs: he keeps worker hours (BUILDER_SCHEDULE, at work
		// 1000-11000), so at 9500 he is at work and (rightly) takes the chest's Remedy before the display can show one.
		// Set before the villagers spawn, since a villager takes its activity from the clock as it spawns (B781).
		long time = helper.getLevel().getDayTime();
		helper.setDayTime(11050);
		Villager[] setup = setup(helper);
		Leftovers.after(helper, () -> helper.getLevel().setDayTime(time)); // after setup's own reset, which saw 9500
		Villager physician = setup[0];
		Villager patient = setup[1];
		physician.setNoAi(false);
		helper.setDayTime(11050); // his idle hour: the display runs
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 8));
		boolean[] done = {false};
		int[] waited = {0};
		helper.onEachTick(() -> {
			if (done[0]) {
				return;
			}
			player.teleportTo(physician.getX() + 1.5, physician.getY(), physician.getZ());
			ItemStack held = physician.getMainHandItem();
			if (RemedyItem.isRemedy(held) && TradeDisplay.isWare(held)) {
				done[0] = true;
				cureAndCheck(helper, physician, patient);
			} else if (++waited[0] > 1200) {
				helper.fail("setup: the display never showed the Physician's Remedy in 1200 ticks (hand: " + held + ", offers: "
					+ physician.getOffers().stream().map(o -> o.getResult().toString()).toList() + ")");
			}
		});
	}

	/** The visit: a patient cured means one real Remedy went from the chest, and no Remedy of the display's was spent. */
	private static void cureAndCheck(GameTestHelper helper, Villager physician, Villager patient) {
		ServerLevel level = helper.getLevel();
		Container chest = helper.getBlockEntity(CHEST);
		int before = chest.countItem(ModItems.REMEDY);
		PhysicianWork.visit(level, physician, patient);
		int after = chest.countItem(ModItems.REMEDY);
		helper.assertTrue(!Sickness.isIll(patient) ? after == before - 1 : after == before,
			"the Physician " + (Sickness.isIll(patient) ? "didn't cure" : "cured") + " the patient and the chest's Remedies went " + before + " -> " + after
				+ ": the display's shown Remedy was used as a real one (a free cure); hand now " + physician.getMainHandItem());
		helper.succeed();
	}

	/** Illness and Physicians on; a Physician at a brewing stand with one real Remedy in the chest; an ill villager. */
	private static Villager[] setup(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean sick = Sickness.ENABLED;
		boolean physicians = PhysicianWork.ENABLED;
		Sickness.ENABLED = true;
		PhysicianWork.ENABLED = true;
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			Sickness.ENABLED = sick;
			PhysicianWork.ENABLED = physicians;
			level.setDayTime(time);
		});
		helper.setBlock(STAND, Blocks.BREWING_STAND);
		helper.setBlock(CHEST, Blocks.CHEST);
		((Container) helper.getBlockEntity(CHEST)).setItem(0, new ItemStack(ModItems.REMEDY, 1));
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
