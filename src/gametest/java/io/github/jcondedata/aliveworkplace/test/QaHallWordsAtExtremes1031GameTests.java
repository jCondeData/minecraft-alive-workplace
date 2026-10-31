package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * QA (lane-e-1031-2213, config extremes and the sentences a player reads). Lane E found three "1 days"-kind sentences
 * one at a time (B727 calendar, B733 Colonies tab, B734 rank perks), each at one config value set to its least. This
 * sweeps the whole Village Hall at once: every number the config file holds ({@link WorkplaceConfig#RANGES}) set to 1
 * (or its least, where 1 is out of range), and then every number at its least, and each time every button of the
 * hall's main page, every page those buttons open, and every button of those pages is read (name and tooltip), and what each click tells the player in chat. No line
 * may say "1" followed by a plural ("1 days", "1 villagers"), which is the counted-sentence rule (24.5).
 */
public class QaHallWordsAtExtremes1031GameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	/** "1" (not part of a bigger number, a decimal or a fraction) and a word ending in s. */
	private static final Pattern ONE_PLURAL = Pattern.compile("(?<![\\d.,/:#×x-])\\b1 ([A-Za-z]+s)\\b");
	/** Words ending in s that aren't plurals. */
	private static final Set<String> NOT_PLURAL = Set.of("is", "was", "has", "does", "plus", "its", "this", "us", "as", "less", "Cobblemons",
		"CobbleDollars", "Pokémons", "Ss");

	//$ gametest_ticks_batch BIG '200' '"qaHallWordsAtExtremes1031"'
	@GameTest(template = BIG, timeoutTicks = 200, batch = "qaHallWordsAtExtremes1031")
	public void noHallLineSaysOneWithAPluralWithEveryNumberAtOne(GameTestHelper helper) {
		sweep(helper, true);
	}

	//$ gametest_ticks_batch BIG '200' '"qaHallWordsAtExtremes1031b"'
	@GameTest(template = BIG, timeoutTicks = 200, batch = "qaHallWordsAtExtremes1031b")
	public void noHallLineSaysOneWithAPluralWithEveryNumberAtItsLeast(GameTestHelper helper) {
		sweep(helper, false);
	}

	private static void sweep(GameTestHelper helper, boolean ones) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, net.minecraft.world.level.GameType.SURVIVAL);
		ServerPlayer player = client.player();
		Leftovers.after(helper, () -> {
			new WorkplaceConfig().apply();
			VillageNeeds.forget();
		});
		StringBuilder json = new StringBuilder("{");
		for (Map.Entry<String, WorkplaceConfig.Range> e : WorkplaceConfig.RANGES.entrySet()) {
			int min = e.getValue().min();
			int value = ones ? Math.max(min, Math.min(1, e.getValue().max())) : min;
			if (e.getKey().equals("villageHallRadius")) {
				value = 16; // the hall must still see this test's villagers
			}
			json.append(json.length() > 1 ? "," : "").append('"').append(e.getKey()).append("\": ").append(value);
		}
		json.append("}");
		WorkplaceConfig.parse(json.toString()).apply();
		VillageNeeds.forget();
		helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 8));
		helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 8));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		player.setPos(hall.getX() + 0.5, hall.getY(), hall.getZ() + 3.5);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
			entity.setOwner(player.getUUID(), player.getGameProfile().getName());
			entity.setRank(VillageRanks.Rank.CITY);
			HEARD[0] = 0;
			Map<String, String> wrong = new java.util.LinkedHashMap<>();
			int pages = 0;
			int lines = 0;
			List<String> top = read(VillageHallScreen.forTest(player, hall));
			lines += top.size();
			check("main page", top, wrong);
			for (int a = 0; a < ChoiceMenu.SIZE; a++) {
				ChoiceMenu first = VillageHallScreen.forTest(player, hall);
				if (first.icon(a).isEmpty()) {
					continue;
				}
				first.press(a, player);
				List<String> page = read(first);
				check("chat after button " + a, heard(client), wrong);
				pages++;
				lines += page.size();
				check("button " + a, page, wrong);
				for (int b = 0; b < ChoiceMenu.SIZE; b++) {
					if (page.isEmpty() || first.icon(b).isEmpty()) {
						continue;
					}
					ChoiceMenu second = VillageHallScreen.forTest(player, hall);
					second.press(a, player);
					second.press(b, player);
					List<String> deeper = read(second);
					check("chat after button " + a + " then " + b, heard(client), wrong);
					pages++;
					lines += deeper.size();
					check("button " + a + " then " + b, deeper, wrong);
				}
			}
			helper.assertTrue(pages > 20 && lines > 200 && HEARD[0] > 0, "setup: the sweep read only " + pages + " pages, " + lines + " lines, "
				+ HEARD[0] + " chat lines");
			helper.assertTrue(wrong.isEmpty(), (ones ? "every config number at 1" : "every config number at its least") + ", " + VillageHalls.name(helper.getLevel(), hall).getString()
				+ "'s hall (" + pages + " pages read) says: " + wrong);
			helper.succeed();
		});
	}

	/** What the player was told since the last call (chat and action bar). */
	private static List<String> heard(QaStageFiveGameTests.Client client) {
		List<String> out = client.seen().stream().map(Component::getString).toList();
		HEARD[0] += out.size();
		client.seen().clear();
		client.sent();
		return out;
	}

	private static final int[] HEARD = new int[1];

	private static List<String> read(ChoiceMenu menu) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < ChoiceMenu.SIZE; i++) {
			ItemStack icon = menu.icon(i);
			if (icon.isEmpty()) {
				continue;
			}
			out.add(icon.getHoverName().getString());
			for (Component line : icon.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
				out.add(line.getString());
			}
		}
		return out;
	}

	private static void check(String where, List<String> lines, Map<String, String> wrong) {
		for (String line : lines) {
			Matcher m = ONE_PLURAL.matcher(line);
			while (m.find()) {
				if (!NOT_PLURAL.contains(m.group(1))) {
					wrong.putIfAbsent("'" + line + "'", where);
				}
			}
		}
	}
}
