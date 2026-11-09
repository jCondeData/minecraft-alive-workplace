package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.inn.Traveller;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.work.ChoiceList;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA (lane E, 2026-11-09), ROADMAP 42.3 at its edges, from the spec ("Inn: Hire with the price, red while the player
 * can't pay; it follows the money while open"; "Research: a row per topic with level, price and Research; gray while
 * locked or done, gold Under way") and across a save: the hire price one emerald short and exact, a fully researched
 * topic's row (and a click on it, as a chest-screen client could still send), and research under way read back from
 * the hall's saved data with the top line's percent and the busy refusal.
 */
public class QaJobListEdges1109eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";

	private static boolean colored(Component c, ChatFormatting color) {
		return c != null && c.getStyle().getColor() != null && c.getStyle().getColor().getValue() == color.getColor();
	}

	private static boolean said(java.util.List<Component> seen, String key) {
		return seen.stream().anyMatch(c -> JobListMenusGameTests.key(c).equals(key));
	}

	/**
	 * One emerald short of a traveler's price: Hire is red, a click is refused and takes nothing; one more emerald while
	 * the screen is open turns it green, and the click hires them for exactly the price (none left).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaJobList1109eInn"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaJobList1109eInn")
	public void qaHirePriceOneShortThenExact(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager guest = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		guest.setNoAi(true);
		guest.setVillagerData(guest.getVillagerData().setProfession(VillagerProfession.NITWIT));
		Traveller t = new Traveller(level.getGameTime(), 2);
		ModAttachments.TRAVELLER.set(guest, t);
		int price = Innkeepers.emeralds(guest, t);
		helper.assertTrue(price > 1, "a price of " + price);
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer player = client.player();
		player.getInventory().setItem(9, new ItemStack(Items.EMERALD, price - 1));
		ChoiceMenu menu = JobListMenusGameTests.open(helper, player, guest, false);
		ChoiceList.Mark hire = JobListMenusGameTests.row(helper, menu, Innkeepers.HIRE_SLOT);
		helper.assertTrue(colored(hire.price(), ChatFormatting.RED), price - 1 + " emeralds for a price of " + price + " isn't red: " + hire);
		JobListMenusGameTests.click(player, menu, Innkeepers.HIRE_SLOT);
		helper.assertTrue(Innkeepers.isTraveller(guest) && player.getInventory().countItem(Items.EMERALD) == price - 1
			&& said(client.seen(), "message.aliveworkplace.inn.cant_afford"),
			"one short: hired, charged or not told why; emeralds " + player.getInventory().countItem(Items.EMERALD));
		player.getInventory().setItem(10, new ItemStack(Items.EMERALD, 1));
		helper.runAfterDelay(3, () -> {
			ChoiceList.Mark now = JobListMenusGameTests.row(helper, menu, Innkeepers.HIRE_SLOT);
			helper.assertTrue(colored(now.price(), ChatFormatting.GREEN), "exactly the price isn't green while open: " + now);
			JobListMenusGameTests.click(player, menu, Innkeepers.HIRE_SLOT);
			helper.assertTrue(!Innkeepers.isTraveller(guest) && player.getInventory().countItem(Items.EMERALD) == 0,
				"exactly the price didn't hire them for all of it; emeralds " + player.getInventory().countItem(Items.EMERALD));
			helper.succeed();
		});
	}

	/** A village with a hall and a Scholar at his desk; runs {@code then} with the hall and the Scholar two ticks on. */
	private static void scholarVillage(GameTestHelper helper, java.util.function.BiConsumer<VillageHallBlockEntity, Villager> then) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Research.forget();
		});
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		BlockPos hallAt = new BlockPos(14, 2, 14);
		BlockPos desk = new BlockPos(8, 2, 8);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		helper.setBlock(desk, ModBlocks.SCHOLARS_DESK);
		Villager scholar = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 9));
		Jobs.employ(level, scholar, helper.absolutePos(desk), ModVillagers.SCHOLARS_DESK_POI, ModVillagers.SCHOLAR);
		scholar.setNoAi(true);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(hallAt));
		helper.runAfterDelay(2, () -> then.accept(hall, scholar));
	}

	/**
	 * Swift Hands at its first tier's top: its row reads "Level 3 of 3" and "Fully researched" in green, gray with no
	 * button; a click on that slot anyway changes nothing (no research past the top is started).
	 */
	//$ gametest_ticks_batch BIG '100' '"qaJobList1109eDone"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaJobList1109eDone")
	public void qaAFullyResearchedTopicIsGrayAndCantBeStartedAgain(GameTestHelper helper) {
		scholarVillage(helper, (hall, scholar) -> {
			Research.Topic swift = Research.Topic.SWIFT_HANDS;
			hall.setResearch(new Research.State(Map.of(swift.key(), swift.maxLevel), Optional.empty(), 0, false));
			Research.forget();
			ServerPlayer player = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
			ChoiceMenu menu = JobListMenusGameTests.open(helper, player, scholar, true);
			int slot = ResearchScreen.TOPIC_SLOTS[swift.ordinal()];
			ChoiceList.Mark row = JobListMenusGameTests.row(helper, menu, slot);
			helper.assertTrue(row.off() && !row.on() && row.button() == null, "a fully researched topic isn't a gray row without a button: " + row);
			helper.assertTrue(row.second() != null && row.second().getString().equals(
				Component.translatable("screen.aliveworkplace.research.level", swift.maxLevel, swift.maxLevel).getString()), "its level: " + row.second());
			helper.assertTrue(JobListMenusGameTests.key(row.price()).equals("screen.aliveworkplace.research.complete") && colored(row.price(), ChatFormatting.GREEN),
				"no green 'Fully researched' where the price was: " + row.price());
			JobListMenusGameTests.click(player, menu, slot);
			Research.State after = hall.research();
			helper.assertTrue(after.currentTopic() == null && after.level(swift) == swift.maxLevel,
				"a click on a fully researched topic started research or changed its level: " + after);
			helper.assertTrue(JobListMenusGameTests.row(helper, menu, slot).off(), "the row came back clickable");
			helper.succeed();
		});
	}

	/**
	 * Research paid for and half done, the hall saved and loaded: the list's top still says "Researching Swift Hands I:
	 * 50%", its row is gold Under way, and another topic's Research is refused as busy.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaJobList1109eSaved"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaJobList1109eSaved")
	public void qaResearchUnderWayReadsTheSameAfterASaveAndLoad(GameTestHelper helper) {
		scholarVillage(helper, (hall, scholar) -> {
			ServerLevel level = helper.getLevel();
			Research.Topic swift = Research.Topic.SWIFT_HANDS;
			Research.Topic other = Arrays.stream(Research.Topic.values())
				.filter(t -> t != swift && t.inFirstTier() && Research.State.EMPTY.available(t)).findFirst().orElse(null);
			helper.assertTrue(other != null, "no other topic open at the start");
			int half = swift.points(1) / 2;
			hall.setResearch(new Research.State(Map.of(), Optional.of(swift.key()), half, true));
			CompoundTag tag = hall.saveWithoutMetadata(level.registryAccess());
			hall.setResearch(Research.State.EMPTY);
			hall.loadWithComponents(tag, level.registryAccess());
			Research.forget();
			helper.assertTrue(hall.research().currentTopic() == swift && hall.research().progress() == half && hall.research().paid(),
				"the research under way didn't come back from the save: " + hall.research());
			QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
			ServerPlayer player = client.player();
			ChoiceMenu menu = JobListMenusGameTests.open(helper, player, scholar, true);
			Component top = JobListMenusGameTests.info(helper, menu).money();
			int percent = Math.round(100f * half / swift.points(1));
			helper.assertTrue(top != null && top.getContents() instanceof TranslatableContents tc && tc.getKey().equals("screen.aliveworkplace.research.now")
				&& tc.getArgs().length == 2 && String.valueOf(tc.getArgs()[1]).equals(String.valueOf(percent)),
				"the top after a load isn't 'Researching ...: " + percent + "%': " + top);
			ChoiceList.Mark row = JobListMenusGameTests.row(helper, menu, ResearchScreen.TOPIC_SLOTS[swift.ordinal()]);
			helper.assertTrue(row.on() && !row.off() && JobListMenusGameTests.key(row.button()).equals("screen.aliveworkplace.research.button_under_way"),
				"Swift Hands isn't Under way after a load: " + row);
			JobListMenusGameTests.click(player, menu, ResearchScreen.TOPIC_SLOTS[other.ordinal()]);
			helper.assertTrue(hall.research().currentTopic() == swift && hall.research().progress() == half && said(client.seen(), "message.aliveworkplace.research.busy"),
				other + " replaced the paid research after a load, or the player wasn't told: " + hall.research());
			helper.succeed();
		});
	}
}
