package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.LegendsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * QA bug test for 29.4 (fails until fixed): the Legends page's "how they come" line for a Legend born to a trade
 * whose name starts with a vowel reads "Born to a Armorer"; the mod's other sentences use "an" (B523).
 */
public class QaLegendsHallBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	private static ResourceLocation id(String name) {
		return ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", name);
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static String name(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? "" : name.getString();
	}

	/** Born to a trade that starts with a vowel reads "an Armorer", as the mod's other sentences do (B523). */
	//$ gametest_ticks_batch AREA '200' '"qaLegendsHallArticle"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLegendsHallArticle")
	public void bornToAnArmorer(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.after(helper, () -> {
			Legends.reload(helper.getLevel().getServer().getResourceManager());
			LegendPowers.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			Legend smith = Legends.read(id("qa_smith"), JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"minecraft:armorer\", \"title\": \"qa_smith\","
				+ " \"lore\": \"l\", \"arrive\": [{\"way\": \"born\", \"trades\": [\"minecraft:armorer\"]}]}").getAsJsonObject());
			Legends.setForTest(Map.of(smith.id(), smith));
			ItemStack card = LegendsPage.cards(level, hall).stream().filter(c -> name(c).equals("qa_smith")).findFirst().orElse(ItemStack.EMPTY);
			helper.assertTrue(lore(card).contains("  • Born to an Armorer"), "how they come: " + lore(card));
			helper.succeed();
		});
	}
}
