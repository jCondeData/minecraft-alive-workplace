package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** QA stage 6's failing tests (2026-10-24): each one a bug filed in ROADMAP, kept off main until it's fixed. */
public class QaStageSixBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";

	/**
	 * QA stage 6: the charter from the off hand (main hand empty), sneak-clicked on a Master in survival, charters her and is used up. */
	//$ gametest_ticks_batch BIG '100' '"qa6GuildOffHand"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa6GuildOffHand")
	public void guildCharterFromTheOffHand(GameTestHelper helper) {
		VillageHallBlockEntity hall = QaStageSixGameTests.guildVillage(helper);
		Villager dara = QaStageSixGameTests.guildVillager(helper, new BlockPos(8, 2, 6), "Dara", ModVillagers.BUILDER, 5);
		helper.runAfterDelay(5, () -> {
			QaStageSixGameTests.ready(hall, VillageRanks.Rank.VILLAGE);
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qa6go"));
			ServerPlayer p = c.player();
			p.setShiftKeyDown(true);
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.GUILD_CHARTER, 2));
			QaStageSixGameTests.clickVillager(p, dara, c.seen());
			helper.assertTrue(ModAttachments.GUILD_MASTER.get(dara) != null, "off hand: Dara isn't chartered, saw " + QaStageFourGameTests.keys(c.seen()));
			helper.assertTrue(p.getOffhandItem().getCount() == 1, "off hand: charters left " + p.getOffhandItem());
			helper.assertTrue(p.containerMenu == p.inventoryMenu, "off hand: a trade screen opened");
			helper.succeed();
		});
	}

}
