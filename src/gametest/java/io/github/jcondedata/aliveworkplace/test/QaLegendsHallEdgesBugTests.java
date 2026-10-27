package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.LegendsPage;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * QA bug test for 29.4 (not registered until fixed): a Legend only visiting as a guest is counted under "Legends living
 * here: 1" on the hall's nether-star tab and the Legends page's header, while their own card says "A guest of the
 * village until day 9". A guest doesn't live here (29.8: they may leave on the third evening); the count should be 0.
 */
public class QaLegendsHallEdgesBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	//$ gametest_ticks_batch AREA '200' '"qaLegendsHallEdgesGuestCount"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLegendsHallEdgesGuestCount")
	public void aGuestIsNotCountedAsLivingHere(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.after(helper, () -> {
			for (var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, helper.getBounds().inflate(16), ModAttachments.LEGEND::has)) {
				LegendRecord.get(helper.getLevel()).forget(e.getUUID());
				e.discard();
			}
			Legends.reload(helper.getLevel().getServer().getResourceManager());
			LegendPowers.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_visitor");
			Legend visitor = Legends.read(id, JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"aliveworkplace:legend\", \"title\": \"qa_visitor\", \"lore\": \"l\"}")
				.getAsJsonObject());
			Legends.setForTest(Map.of(id, visitor));
			Villager gil = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 14));
			gil.setNoAi(true);
			gil.setVillagerData(gil.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
			gil.setCustomName(Component.literal("Gil"));
			ModAttachments.LEGEND.set(gil, new LegendData(id, "", true, Optional.empty(), 1, 9, Map.of(), -1, -1, "visit"));
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			int tab = HallPages.slot(LegendsPage.PAGE);
			List<String> tabLore = lore(menu.icon(tab));
			helper.assertTrue(tabLore.contains("Legends living here: 0"), "the tab counts a guest as living here: " + tabLore);
			helper.succeed();
		});
	}
}
