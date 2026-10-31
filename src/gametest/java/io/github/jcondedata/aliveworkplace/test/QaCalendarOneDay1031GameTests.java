package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.hall.Seasons;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * QA (lane-e-1031-1613, config extremes and the player-facing sentences): the hall's calendar at {@code seasonDays} 1,
 * the least the setting allows (WorkplaceConfig.RANGES, 1-120). Every line the calendar page shows reads as English
 * does for one day: "Four seasons of 1 day", "Day 3 of the year"; never "1 days" or "Days 3 to 3".
 */
public class QaCalendarOneDay1031GameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(8, 2, 8);
	private static final Pattern SAME_RANGE = Pattern.compile("Days (\\d+) to (\\d+)");

	//$ gametest_ticks_batch AREA '100' '"qaCalendarOneDay"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCalendarOneDay")
	public void oneDaySeasonsReadInTheSingular(GameTestHelper helper) {
		Leftovers.clear(helper);
		int days = Seasons.DAYS;
		Leftovers.after(helper, () -> Seasons.DAYS = days);
		Seasons.DAYS = 1;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		helper.runAfterDelay(2, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
			VillageHallScreen.open(player, hall);
			helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
			ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
			List<String> words = new ArrayList<>(text(menu.icon(VillageHallScreen.PAGE_ROW)));
			menu.clicked(VillageHallScreen.PAGE_ROW, 0, ClickType.PICKUP, player);
			words.addAll(text(menu.icon(4)));
			for (int slot : Seasons.SEASON_SLOTS) {
				words.addAll(text(menu.icon(slot)));
			}
			player.closeContainer();
			helper.assertTrue(words.stream().anyMatch(w -> w.startsWith("Four seasons of")), "no 'Four seasons of' line on the calendar: " + words);
			List<String> bad = new ArrayList<>();
			for (String line : words) {
				Matcher m = SAME_RANGE.matcher(line);
				if (line.matches(".*\\b1 days\\b.*") || m.find() && m.group(1).equals(m.group(2))) {
					bad.add(line);
				}
			}
			helper.assertTrue(bad.isEmpty(), "with seasonDays 1 the calendar reads: " + bad);
			helper.succeed();
		});
	}

	private static List<String> text(ItemStack stack) {
		List<String> out = new ArrayList<>();
		out.add(stack.getHoverName().getString());
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				out.add(line.getString());
			}
		}
		return out;
	}
}
