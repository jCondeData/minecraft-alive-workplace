package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupDays;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trader.PokemonTradeView;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Bugs QA found in ROADMAP 48.4 (lane-d-1117-0107); each test fails on the code it was written against:
 * <ul>
 * <li>a Cup's chronicle entry written when its village next loads (28.19, dated the Cup's day) lands at the top of the
 * Chronicle, above newer days: the day headers come out of order and the same day gets two headers;</li>
 * <li>the Pokemon Trader's plaque rises above the top of a 320x180 screen (GUI scale 3), its border cut off.</li>
 * </ul>
 */
public class QaChronicleCopper1117dBugGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);

	private static CompoundTag mark(ItemStack icon, String key) {
		CustomData data = icon.get(DataComponents.CUSTOM_DATA);
		return data == null || !data.contains(key) ? null : data.copyTag().getCompound(key);
	}

	private static List<Integer> entrySlots(ChoiceMenu menu) {
		List<Integer> out = new ArrayList<>();
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (mark(menu.icon(slot), VillageHallScreen.CHRONICLE_ENTRY) != null) {
				out.add(slot);
			}
		}
		return out;
	}

	//$ gametest_ticks_batch AREA '100' '"qaChronicle1117dBugCupNote"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChronicle1117dBugCupNote")
	public void aCupEntryWrittenLateKeepsTheDayHeadersInOrder(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Chronicle.atHall(level, hall, Chronicle.Kind.RAID, Component.literal("Raid at the south gate"), 46);
			Chronicle.atHall(level, hall, Chronicle.Kind.BUILT, Component.literal("The Storehouse reached level 3"), 47);
			// the village was away on day 46 when the Cup ended; its entry is written as it loads again, on day 47
			CupData.get(level).note(hall, new CupData.Note(46, Component.Serializer.toJson(Component.literal("The Spring Cup"), level.registryAccess())));
			CupDays.writeNotes(level, hall);
			ChoiceMenu menu = ChoiceMenu.detached(player, m -> VillageHallScreen.renderChronicle(m, level, hall));
			List<Long> days = new ArrayList<>();
			List<String> texts = new ArrayList<>();
			for (int slot : entrySlots(menu)) {
				CompoundTag e = mark(menu.icon(slot), VillageHallScreen.CHRONICLE_ENTRY);
				if (e.getLong("day") >= 46 && e.getLong("day") <= 47) {
					days.add(e.getLong("day"));
					texts.add(menu.icon(slot).getHoverName().getString());
				}
			}
			// newest first, so the page reads DAY 47 (one entry) over DAY 46 (two entries), one header each
			helper.assertTrue(days.equals(List.of(47L, 46L, 46L)),
				"the Chronicle lists days " + days + " (" + texts + "): DAY 46 · DAY 47 · DAY 46, two headers for day 46 and day 47 under the newer one");
			helper.succeed();
		});
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void thePokemonTradersPlaqueStaysOnA320x180Screen(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int[] screen : new int[][]{{320, 180}, {427, 240}}) {
			int[] win = {(screen[0] - PokemonTradeView.WIDTH) / 2, (screen[1] - PokemonTradeView.HEIGHT) / 2, PokemonTradeView.WIDTH, PokemonTradeView.HEIGHT};
			int[] plaque = CopperLayout.plaque(win, 6 * "Pokemon Trader Ash".length());
			if (plaque[1] < 0 || plaque[0] < 0 || plaque[0] + plaque[2] > screen[0]) {
				problems.add(screen[0] + "x" + screen[1] + ": the plaque [" + plaque[0] + "," + plaque[1] + " " + plaque[2] + "x" + plaque[3]
					+ "] starts " + (-plaque[1]) + " pixels above the screen's top");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
