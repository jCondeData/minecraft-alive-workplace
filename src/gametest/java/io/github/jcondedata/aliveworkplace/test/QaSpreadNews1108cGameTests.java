package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.printer.Gazette;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestFiles;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane qa-lane-c-1108-0352) for the hall quest Spread the news (ROADMAP 34.11), written from the spec: it "pays 6
 * emeralds for this week's Gazette handed in at the hall of the village at the other end of the route". So a Gazette 6
 * days old still counts (the week is the day it was printed and 6 more) and one 7 days old doesn't; one copy of a stack of 2 is taken and the other
 * stays in hand; a third village's Gazette, the right Gazette at a third village's hall, and a second copy once the
 * quest is paid are never taken and never pay.
 */
public class QaSpreadNews1108cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos STANDING = new BlockPos(3, 2, 3);
	/** Village A (the Printer's, which posts the quest), B (at the other end of the route) and C (a third village). */
	private static final BlockPos HALL_A = new BlockPos(6, 2, 6);
	private static final BlockPos HALL_B = new BlockPos(15, 2, 15);
	private static final BlockPos HALL_C = new BlockPos(15, 2, 3);
	private static final BlockPos STORE_B = new BlockPos(16, 2, 12);
	private static final int USUAL_REACH = VillageHalls.RADIUS;

	/** A Gazette printed 6 days ago is still this week's: it's taken at B and the quest pays 6 emeralds. */
	//$ gametest_ticks_batch AREA '100' '"qaSpreadNewsSixDays"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSpreadNewsSixDays")
	public void aGazetteSixDaysOldIsStillThisWeeks(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			s.post();
			ServerPlayer player = player(helper);
			ItemStack paper = gazette(s, s.a, Chronicle.day(s.level) - (Gazette.WEEK - 1));
			click(player, s.level, s.b, paper);
			helper.assertTrue(player.getInventory().countItem(ModItems.GAZETTE) == 0, "a Gazette 6 days old wasn't taken: " + player.getMainHandItem());
			helper.assertTrue(Stories.open(s.level, s.a).isEmpty(), "the quest is still open after a Gazette 6 days old");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 6, player.getInventory().countItem(Items.EMERALD) + " emeralds paid, expected 6");
			helper.succeed();
		});
	}

	/**
	 * The other side of the week: a Gazette printed 7 days ago (on day 14 of day 21) is old news. It stays with the
	 * player, the quest stays open and nothing is paid.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaSpreadNewsSevenDays"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSpreadNewsSevenDays")
	public void aGazetteSevenDaysOldIsOldNews(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			s.post();
			ServerPlayer player = player(helper);
			long today = Chronicle.day(s.level);
			helper.assertTrue(today - Gazette.WEEK > 0, "setup: today is day " + today);
			click(player, s.level, s.b, gazette(s, s.a, today - Gazette.WEEK));
			helper.assertTrue(player.getInventory().countItem(ModItems.GAZETTE) == 1, "a Gazette 7 days old was taken");
			helper.assertTrue(Stories.open(s.level, s.a).size() == 1, "the quest came down for a Gazette 7 days old");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "a Gazette 7 days old paid " + player.getInventory().countItem(Items.EMERALD));
			helper.succeed();
		});
	}

	/** Two copies in hand: one is taken (into B's store) and paid, the other stays in the player's hand. */
	//$ gametest_ticks_batch AREA '100' '"qaSpreadNewsTwoCopies"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSpreadNewsTwoCopies")
	public void ofTwoCopiesOneIsTakenAndOneStays(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			s.post();
			ServerPlayer player = player(helper);
			ItemStack two = gazette(s, s.a, Chronicle.day(s.level));
			two.setCount(2);
			click(player, s.level, s.b, two);
			helper.assertTrue(player.getInventory().countItem(ModItems.GAZETTE) == 1, "Gazettes left with the player: "
				+ player.getInventory().countItem(ModItems.GAZETTE) + " (expected 1 of 2)");
			helper.assertTrue(s.store.countItem(ModItems.GAZETTE) == 1, "Gazettes in B's store: " + s.store.countItem(ModItems.GAZETTE) + " (expected 1)");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 6, player.getInventory().countItem(Items.EMERALD) + " emeralds paid, expected 6");
			helper.succeed();
		});
	}

	/** Village C's own Gazette, handed in at B, isn't the news A's quest asks for: not taken, nothing paid. */
	//$ gametest_ticks_batch AREA '100' '"qaSpreadNewsOtherPaper"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSpreadNewsOtherPaper")
	public void aThirdVillagesGazetteIsNotTaken(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			s.post();
			ServerPlayer player = player(helper);
			ItemStack paper = gazette(s, s.c, Chronicle.day(s.level));
			helper.assertTrue(Gazette.home(paper).equals(Optional.of(s.c)), "setup: the paper isn't C's: " + Gazette.home(paper));
			click(player, s.level, s.b, paper);
			helper.assertTrue(player.getInventory().countItem(ModItems.GAZETTE) == 1, "village C's Gazette was taken at B");
			helper.assertTrue(Stories.open(s.level, s.a).size() == 1, "A's quest came down for C's Gazette");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "paid " + player.getInventory().countItem(Items.EMERALD) + " emeralds for C's Gazette");
			helper.succeed();
		});
	}

	/** A's Gazette handed in at C's hall (not the other end of A's route): not taken, nothing paid, the quest stays. */
	//$ gametest_ticks_batch AREA '100' '"qaSpreadNewsWrongHall"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSpreadNewsWrongHall")
	public void aGazetteAtAThirdVillagesHallIsNotTaken(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			s.post();
			ServerPlayer player = player(helper);
			click(player, s.level, s.c, gazette(s, s.a, Chronicle.day(s.level)));
			helper.assertTrue(player.getInventory().countItem(ModItems.GAZETTE) == 1, "A's Gazette was taken at C's hall");
			helper.assertTrue(Stories.open(s.level, s.a).size() == 1, "A's quest came down at C's hall");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "paid " + player.getInventory().countItem(Items.EMERALD) + " emeralds at C's hall");
			helper.succeed();
		});
	}

	/** Once the quest is paid, a second copy at B is kept by the player and pays nothing more. */
	//$ gametest_ticks_batch AREA '100' '"qaSpreadNewsPaidOnce"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSpreadNewsPaidOnce")
	public void aSecondCopyAfterThePayIsNotTaken(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			s.post();
			ServerPlayer player = player(helper);
			click(player, s.level, s.b, gazette(s, s.a, Chronicle.day(s.level)));
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 6, "setup: the first copy paid " + player.getInventory().countItem(Items.EMERALD));
			player.closeContainer();
			player.getInventory().clearContent();
			click(player, s.level, s.b, gazette(s, s.a, Chronicle.day(s.level)));
			helper.assertTrue(player.getInventory().countItem(ModItems.GAZETTE) == 1, "a second copy was taken after the quest was paid");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "a second copy paid " + player.getInventory().countItem(Items.EMERALD) + " more emeralds");
			helper.assertTrue(s.store.countItem(ModItems.GAZETTE) == 1, "Gazettes in B's store: " + s.store.countItem(ModItems.GAZETTE) + " (expected 1)");
			player.closeContainer();
			helper.succeed();
		});
	}

	/** Three villages, a Printer in A, a caravan route A to B and a store at B; {@link Setup#post} posts A's quest. */
	private static Setup setup(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		// Day 21, so a week back is a real day (Chronicle.day is 1 at day time 8000, and a paper dated before day 0 is old
		// news whatever the week says).
		helper.setDayTime(20 * 24000 + 8000);
		Leftovers.after(helper, () -> level.setDayTime(8000));
		hall(helper, HALL_A, 6);
		hall(helper, HALL_B, 6);
		hall(helper, HALL_C, 6);
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		BlockPos c = helper.absolutePos(HALL_C);
		helper.setBlock(TABLE, Blocks.CARTOGRAPHY_TABLE);
		helper.setBlock(new BlockPos(15, 2, 12), Blocks.SMOKER); // B's store
		helper.setBlock(STORE_B, Blocks.CHEST);
		Container store = helper.getBlockEntity(STORE_B);
		Caravans.Data caravans = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			caravans.remove(a);
			caravans.remove(b);
			caravans.remove(c);
		});
		return new Setup(helper, level, a, b, c, store, caravans);
	}

	private record Setup(GameTestHelper helper, ServerLevel level, BlockPos a, BlockPos b, BlockPos c, Container store, Caravans.Data caravans) {
		/** A's Printer, the route A to B and A's Spread the news quest for B, posted. */
		void post() {
			Villager printer = helper.spawn(EntityType.VILLAGER, STANDING);
			printer.setNoAi(true);
			printer.setVillagerData(printer.getVillagerData().setProfession(ModVillagers.PRINTER).setLevel(1));
			printer.refreshBrain(level);
			printer.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(TABLE)));
			helper.assertTrue(caravans.toggleRoute(a, b), "setup: no route A to B");
			Objectives.Context context = new Objectives.Context(level, a, VillageHalls.census(level, a), RandomSource.create(1), "");
			Objectives.Objective asked = new Objectives.SpreadNews(Optional.empty(), "").resolve(context);
			helper.assertTrue(asked instanceof Objectives.SpreadNews news && news.to().equals(Optional.of(b)), "setup: the quest asks " + asked);
			QuestFiles.QuestFile file = QuestFiles.get(AliveWorkplace.id("daily/spread_news")).orElseThrow(() -> new GameTestAssertException("no daily/spread_news quest file"));
			List<Objectives.Objective> objectives = List.of(asked);
			Quest quest = new Quest(UUID.randomUUID(), file.id(), file.giver(), file.name(), context.poster, level.getGameTime(), level.getGameTime() + 72000,
				objectives, new int[1], file.rewards().stream().map(r -> r.resolve(objectives, 1f)).toList());
			Stories.post(level, a, quest);
			helper.assertTrue(Stories.open(level, a).size() == 1, "setup: the quest isn't posted");
		}
	}

	/** A Gazette printed at the hall {@code at}, dated {@code day}. */
	private static ItemStack gazette(Setup s, BlockPos at, long day) {
		ItemStack paper = new ItemStack(ModItems.GAZETTE);
		Gazette.write(s.level, at, paper);
		CompoundTag tag = paper.get(DataComponents.CUSTOM_DATA).copyTag();
		tag.putLong("gazette_day", day);
		paper.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return paper;
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at, int reach) {
		VillageHalls.RADIUS = reach;
		BlockPos abs = helper.absolutePos(at);
		ServerLevel world = helper.getLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = USUAL_REACH;
			Stories.Data.get(world).remove(abs);
		});
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) world.getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		return entity;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static void click(ServerPlayer player, ServerLevel level, BlockPos at, ItemStack held) {
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.moveTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 2.5, 180f, 0f);
		player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.SOUTH, 0.5), Direction.SOUTH, at, false));
	}
}
