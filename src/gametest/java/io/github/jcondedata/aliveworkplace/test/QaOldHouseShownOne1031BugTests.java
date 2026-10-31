package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.OldHouses;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.StewardDeskPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * QA (lane-e-1031-1913, the sentences a player reads): the Steward's desk (the hall's advice page) lists a village's
 * old houses, and clicking the list makes their outlines glow: "Showing %s old houses for %s seconds: orange ones can
 * be renewed" ({@code message.aliveworkplace.steward.desk.old_houses_shown}, passed through a plain translatable). A
 * village with exactly one old house, an ordinary case (one vanilla house left in the Homes zone), reads "Showing 1 old
 * houses for 10 seconds". Expected: "Showing 1 old house for 10 seconds: ...". The village and the house are
 * {@link QaOldHousesGameTests}'.
 */
public class QaOldHouseShownOne1031BugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos HOUSE = new BlockPos(12, 2, 4);
	private static final ResourceLocation SMALL_HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1");

	//$ gametest_ticks_batch AREA '200' '"qaOldHouseShownOne1031"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseShownOne1031")
	public void showingOneOldHouseReadsInTheSingular(GameTestHelper helper) {
		QaOldHousesGameTests.Village v = QaOldHousesGameTests.village(helper);
		List<Component> heard = new ArrayList<>();
		ServerPlayer owner = RealmGameTests.listener(helper, heard, "desk-owner");
		owner.setPos(v.hall().getX() + 0.5, v.hall().getY(), v.hall().getZ() + 2.5);
		v.entity().setOwner(owner.getUUID(), owner.getGameProfile().getName());
		OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(v.level(), owner, plan, v.hall());
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		desk(owner, v.hall());
		helper.succeedWhen(() -> {
			helper.assertTrue(OldHouses.result(v.level(), v.hall()).isPresent(), "the survey hasn't finished");
			helper.assertTrue(OldHouses.result(v.level(), v.hall()).get().size() == 1, "setup: one old house expected, found "
				+ OldHouses.result(v.level(), v.hall()).get());
			ChoiceMenu menu = desk(owner, v.hall());
			String count = menu.icon(StewardDeskPage.OLD_HOUSES).getHoverName().getString();
			helper.assertTrue(count.startsWith("Old houses: 1"), "setup: the desk reads \"" + count + "\"");
			heard.clear();
			menu.press(StewardDeskPage.OLD_HOUSES, owner);
			List<String> lines = heard.stream().map(Component::getString).toList();
			helper.assertTrue(lines.stream().anyMatch(l -> l.startsWith("Showing 1 old ")), "the click said nothing about the outlines: " + lines);
			helper.assertTrue(lines.stream().anyMatch(l -> l.startsWith("Showing 1 old house for ")), "one old house shown: " + lines);
		});
	}

	private static ChoiceMenu desk(ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(VillageHallScreen.ADVICE, player);
		return menu;
	}
}
