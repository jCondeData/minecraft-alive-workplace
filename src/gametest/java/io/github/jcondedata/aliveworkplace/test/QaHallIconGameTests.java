package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/**
 * QA for B19 (from its spec: "the hall's buttons show only their own lines"): beyond the main page, the quest page's
 * buttons, reached by pressing Quests, include a slay quest (drawn as a sword) and bring quests for a sword and a
 * pickaxe; none may show "When in Main Hand" attribute lines.
 */
public class QaHallIconGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	//$ gametest_ticks_batch AREA '40' '"qaHallQuestButtonsHideAttributeLines"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaHallQuestButtonsHideAttributeLines")
	public void qaHallQuestButtonsHideAttributeLines(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.getServer().getPlayerList().remove(player);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		long now = level.getGameTime();
		entity.setQuests(List.of(
			new VillageQuests.Quest(UUID.randomUUID(), VillageQuests.Kind.SLAY, "minecraft:air", 5, 0, 3, now, "Ana", Optional.empty()),
			new VillageQuests.Quest(UUID.randomUUID(), VillageQuests.Kind.BRING, "minecraft:diamond_sword", 1, 0, 9, now, "Bo", Optional.empty()),
			new VillageQuests.Quest(UUID.randomUUID(), VillageQuests.Kind.BRING, "minecraft:netherite_pickaxe", 1, 0, 9, now, "Cy", Optional.empty())));
		player.getInventory().add(new ItemStack(Items.DIAMOND_SWORD));
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(VillageHallScreen.QUESTS, player);
		Item[] expected = {Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_PICKAXE};
		for (int i = 0; i < expected.length; i++) {
			ItemStack icon = menu.icon(VillageHallScreen.QUEST_SLOTS[i]);
			helper.assertTrue(icon.is(expected[i]), "quest slot " + i + " isn't the " + expected[i] + " quest: " + icon);
			List<String> keys = lines(level, player, icon);
			helper.assertTrue(keys.contains("screen.aliveworkplace.hall.quest_reward"), "quest " + i + " lost its own lines: " + keys);
		}
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.icon(slot);
			if (icon.isEmpty()) {
				continue;
			}
			for (String key : lines(level, player, icon)) {
				helper.assertTrue(!key.startsWith("attribute.") && !key.startsWith("item.modifiers."),
					"quest page slot " + slot + " (" + icon.getItem() + ") shows an attribute line: " + key);
			}
		}
		helper.succeed();
	}

	private static List<String> lines(ServerLevel level, ServerPlayer player, ItemStack stack) {
		List<String> out = new ArrayList<>();
		for (Component line : stack.getTooltipLines(Item.TooltipContext.of(level), player, TooltipFlag.NORMAL)) {
			walk(line, out);
		}
		return out;
	}

	private static void walk(Component component, List<String> out) {
		if (component.getContents() instanceof TranslatableContents t) {
			out.add(t.getKey());
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component c) {
					walk(c, out);
				}
			}
		}
		for (Component sibling : component.getSiblings()) {
			walk(sibling, out);
		}
	}
}
