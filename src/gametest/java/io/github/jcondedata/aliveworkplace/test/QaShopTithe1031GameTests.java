package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA (lane E, 2026-10-31), B706's last clause: the Tithe edict's {@code trade_prices} ("emerald prices 10% higher")
 * on a player's shop in the village. B706 expects a player's shop to ask exactly the owner's price from every buyer;
 * the village's edict would raise a price the shop's owner set (20 to 22).
 */
public class QaShopTithe1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	//$ gametest_ticks_batch AREA '100' '"qaShopTithe"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopTithe")
	public void aTitheVillagesShopAsksTheOwnersPrice(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			CivicEffects.forget();
		});
		QaShopPaymentSaves1031GameTests.shop(helper, new ItemStack(Items.EMERALD, 20));
		Villager keeper = QaShopPaymentSaves1031GameTests.keeper(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
			hall.setRank(VillageRanks.Rank.HAMLET);
			hall.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("tithe").toString(), 1)));
			hall.setReforms(List.of());
			VillageNeeds.forget();
			CivicEffects.forget();
			helper.assertTrue(CivicEffects.of(keeper).tradePrices(keeper) == 10, "setup: the shopkeeper isn't under the village's Tithe: " + CivicEffects.of(keeper).tradePrices(keeper));
			QaStageFiveGameTests.Client buyer = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaTitheBuyer"));
			ServerPlayer p = buyer.player();
			p.setShiftKeyDown(Money.cobbleDollars());
			QaStageFourGameTests.pressOn(p, keeper, buyer.seen());
			helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the trade screen didn't open: " + p.containerMenu);
			ItemStack cost = ((MerchantMenu) p.containerMenu).getOffers().get(0).getCostA();
			String asked = cost.toString();
			boolean twenty = cost.getCount() == 20 && cost.is(Items.EMERALD);
			p.closeContainer();
			helper.assertTrue(twenty, "in a village under the Tithe edict, a player's shop asks " + asked + " for a diamond block its owner priced at 20 emeralds");
			helper.succeed();
		});
	}
}
