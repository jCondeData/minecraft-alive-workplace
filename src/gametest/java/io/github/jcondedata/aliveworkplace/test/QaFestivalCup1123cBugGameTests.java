package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupPage;
import io.github.jcondedata.aliveworkplace.cup.Cups;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * QA (lane-c-1123-0953), a failing test for the builder: opening a hall's Cup page loads the chunk of every far village on
 * the circuit (to read its hall's name), and while that chunk is loaded the far village's Trainer Leader, known from the
 * trade-route list (spec 28.17, "an unloaded village can still send them"), is read live from a chunk with no villagers
 * in it: the page says "Has no Trainer to send" and the Leader is no entrant (a sign-up closing then draws them out).
 */
public class QaFestivalCup1123cBugGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final long DAY = 24000;

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	/** A far partner (unloaded) on the circuit whose Leader Dara is on the trade-route list; the page opened twice. */
	//$ gametest_ticks_batch HUGE '200' '"qaCupFarPage1123c"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaCupFarPage1123c")
	public void theCupPageNeitherLoadsAFarVillageNorLosesItsLeader(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			List<BlockPos> listed = new ArrayList<>();
			VillageHallBlockEntity entity = QaCupGameTests.host(helper, hall, (VillageHallBlockEntity) level.getBlockEntity(hall), listed);
			Caravans.Data data = Caravans.Data.get(level);
			BlockPos far = hall.offset(5200, 0, 5200);
			helper.assertFalse(level.isLoaded(far), "setup: the far village is loaded");
			data.setWants(far, Component.literal("Farhollow"), List.of());
			listed.add(far);
			helper.assertTrue(data.toggleRoute(hall, far, 10), "setup: the route");
			UUID dara = UUID.nameUUIDFromBytes("Dara".getBytes(StandardCharsets.UTF_8));
			data.setLeader(far, new Caravans.Leader(dara, "Dara", 4, 40, true));
			int gap = Festivals.every(entity);
			long cupDay = Cups.cupDay(400, Festivals.offset(hall), gap, 1);
			level.setDayTime((cupDay - 2) * DAY + 14_000);
			Cups.round(level, hall, entity);
			CupData.Cup cup = CupData.get(level).existing(hall);
			helper.assertTrue(cup != null && Cups.entrants(level, hall, cup).stream().anyMatch(e -> e.id().equals(dara)), "setup: Dara isn't an entrant");

			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			List<String> seen = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
				menu.press(HallPages.slot(CupPage.PAGE), player);
				seen.add(String.join(" / ", lore(menu.icon(CupPage.CIRCUIT_ROW + 1))));
			}
			boolean loaded = level.isLoaded(far);
			boolean entered = Cups.entrants(level, hall, cup).stream().anyMatch(e -> e.id().equals(dara));
			helper.assertTrue(entered && seen.stream().allMatch(s -> s.contains("Sends Dara")) && !loaded,
				"after opening the Cup page twice: the far village's chunk " + (loaded ? "was loaded" : "stayed unloaded") + ", Dara "
					+ (entered ? "is" : "is no longer") + " an entrant, and its circuit row read " + seen);
			helper.succeed();
		});
	}
}
