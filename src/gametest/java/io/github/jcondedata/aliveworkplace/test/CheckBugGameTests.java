package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Cavalry;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Bugs the tester found in 0.131.0-Unreleased, each as a test that failed on the code it was found on (minecraft-mod-
 * tester, Check round of v0.130.0..7658df4), committed with its fix. (Its question whether a camel is cavalry waits for
 * the owner: ROADMAP, Notes.)
 */
public class CheckBugGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * Nightfall with the fisher out on the lake: "a shift that ends out on the water brings them ashore at once" (the
	 * fishing-from-boats commit and FisherWork's own promise). Once the fisherman's working day is over they're ashore
	 * within five seconds, the boat put away. Found: they stay out until FisherWork's 1200-tick run times out, since a
	 * running behaviour isn't stopped when the villager's activity changes.
	 */
	//$ gametest_ticks_batch AREA '2400' '"aFisherComesAshoreWhenTheShiftEnds"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "aFisherComesAshoreWhenTheShiftEnds")
	public void aFisherComesAshoreWhenTheShiftEnds(GameTestHelper helper) {
		Villager villager = RidingSpecGameTests.boatFisher(helper);
		long[] nightAt = {-1};
		int[] out = {0};
		helper.onEachTick(() -> {
			if (nightAt[0] < 0 && RidingSpecGameTests.outOnTheLake(villager, out) != null) {
				helper.setDayTime(13000); // nightfall: a fisherman's day (work 2000-9000) is long over
				nightAt[0] = helper.getTick();
			} else if (nightAt[0] >= 0 && helper.getTick() > nightAt[0] + 100) {
				helper.assertTrue(villager.getVehicle() == null, "still out on the water "
					+ (helper.getTick() - nightAt[0]) + " ticks after the shift ended (activity now "
					+ villager.getBrain().getActiveNonCoreActivity().orElse(null) + ")");
				helper.assertTrue(RidingSpecGameTests.afloat(helper).isEmpty(), "the boat was left on the water");
				helper.assertTrue(RidingSpecGameTests.boats(helper, villager) == 1, "boats: " + RidingSpecGameTests.boats(helper, villager));
				helper.succeed();
			}
		});
	}

	/**
	 * When the guard's shift ends they leave the horse where they are (0.137.0): off it within five seconds of the end of
	 * the watch (a guard's WORK ends at 3000), the horse at its own pace again. Found: they ride on until GuardPatrol's
	 * 1200-tick run times out (or they reach their bed on horseback).
	 */
	//$ gametest_ticks_batch AREA '2400' '"aGuardGetsDownWhenTheShiftEnds"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "aGuardGetsDownWhenTheShiftEnds")
	public void aGuardGetsDownWhenTheShiftEnds(GameTestHelper helper) {
		Villager guard = RidingSpecGameTests.guard(helper);
		Horse horse = RidingSpecGameTests.saddledHorse(helper, new BlockPos(6, 2, 6));
		long[] endAt = {-1};
		helper.onEachTick(() -> {
			if (endAt[0] < 0 && guard.getVehicle() == horse) {
				helper.setDayTime(4000); // the watch is over: guards sleep from 3000
				endAt[0] = helper.getTick();
			} else if (endAt[0] >= 0 && helper.getTick() > endAt[0] + 100) {
				helper.assertTrue(guard.getVehicle() == null, "still in the saddle " + (helper.getTick() - endAt[0])
					+ " ticks after the shift ended (activity now " + guard.getBrain().getActiveNonCoreActivity().orElse(null) + ")");
				helper.assertFalse(Cavalry.paced(horse), "the horse kept the guard's pace");
				helper.succeed();
			}
		});
	}

	/**
	 * A stranger's arrows don't hurt a protected village's villagers or animals (0.135.0: "only you, your friends and
	 * operators can ... hurt its villagers and animals"); the owner's still do. The damage is dealt the way an arrow
	 * hitting them deals it. Found: protection only stops melee (AttackEntityCallback), so arrows, tridents and thrown
	 * potions from strangers still hurt and kill the village's villagers and animals.
	 */
	//$ gametest_ticks_batch AREA '100' '"aStrangersArrowsDontHurtTheVillage"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "aStrangersArrowsDontHurtTheVillage")
	public void aStrangersArrowsDontHurtTheVillage(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		ServerLevel level = helper.getLevel();
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		Cow cow = helper.spawn(EntityType.COW, new BlockPos(8, 2, 6));
		villager.setNoAi(true);
		cow.setNoAi(true);
		Arrow strangers = new Arrow(level, stranger, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
		villager.hurt(level.damageSources().arrow(strangers, stranger), 4f);
		cow.hurt(level.damageSources().arrow(strangers, stranger), 4f);
		helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), "a stranger's arrow hurt a villager in a protected village: "
			+ villager.getHealth() + "/" + villager.getMaxHealth());
		helper.assertTrue(cow.getHealth() == cow.getMaxHealth(), "a stranger's arrow hurt a cow in a protected village: " + cow.getHealth());
		Arrow owners = new Arrow(level, owner, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
		cow.invulnerableTime = 0;
		cow.hurt(level.damageSources().arrow(owners, owner), 4f);
		helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), "the owner's arrow didn't hurt their own cow");
		helper.succeed();
	}

	/**
	 * Bandits who raid the village at night can't be pulled into a vanilla raid (BanditCamps.raider sets that, as for the
	 * camp's own band). Found: VillageRaids calls finalizeSpawn after BanditCamps.raider, and vanilla's Raider.finalizeSpawn
	 * sets canJoinRaid back to true, so a player bringing Bad Omen that night gets the bandits in the raid's waves.
	 */
	//$ gametest_ticks_batch AREA '100' '"banditRaidersStayOutOfVanillaRaids"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "banditRaidersStayOutOfVanillaRaids")
	public void banditRaidersStayOutOfVanillaRaids(GameTestHelper helper) {
		java.util.List<net.minecraft.world.entity.Mob> raiders = HallSpecGameTests.banditRaid(helper);
		for (net.minecraft.world.entity.Mob m : raiders) {
			helper.assertTrue(m instanceof net.minecraft.world.entity.raid.Raider r && !r.canJoinRaid(), "a bandit a vanilla raid can take: " + m);
		}
		raiders.forEach(net.minecraft.world.entity.Mob::discard);
		helper.succeed();
	}

	/**
	 * A stranger holding a Village Ledger bound to the hall before it was protected can't empty a protected village's
	 * treasury from afar (0.135.0: only the owner, their friends and operators may open things in a protected village).
	 * Found: the ledger opens the hall's screen for anyone, and clicking the hall's name pays out the treasury.
	 */
	//$ gametest_ticks_batch AREA '100' '"aStrangersLedgerCantEmptyAProtectedTreasury"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "aStrangersLedgerCantEmptyAProtectedTreasury")
	public void aStrangersLedgerCantEmptyAProtectedTreasury(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity hall = ProtectionSpecGameTests.protectedHall(helper, owner);
		ServerLevel level = helper.getLevel();
		hall.setTreasury(300);
		ItemStack ledger = new ItemStack(ModItems.VILLAGE_LEDGER);
		VillageLedgerItem.bind(level, stranger, ledger, helper.absolutePos(ProtectionSpecGameTests.HALL)); // (bound while the village was open)
		stranger.setItemInHand(InteractionHand.MAIN_HAND, ledger);
		helper.runAfterDelay(2, () -> {
			ledger.use(level, stranger, InteractionHand.MAIN_HAND);
			if (stranger.containerMenu instanceof ChoiceMenu menu) {
				menu.press(0, stranger); // the hall's name: collect the treasury
				stranger.closeContainer();
			}
			helper.assertTrue(stranger.getInventory().countItem(Items.EMERALD) == 0 && hall.treasury() == 300,
				"a stranger took " + stranger.getInventory().countItem(Items.EMERALD) + " emeralds from a protected village's treasury");
			helper.assertTrue(Treasury.emeralds(level, helper.absolutePos(ProtectionSpecGameTests.HALL)) == 3, "the treasury: " + hall.treasury());
			helper.succeed();
		});
	}
}
