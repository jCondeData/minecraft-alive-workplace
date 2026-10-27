package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Decorations;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.StrangeMood;
import io.github.jcondedata.aliveworkplace.legend.StrangeMoods;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA for 29.10 (strange moods and Masterworks), written from the spec: only a Master is ever seized (never an
 * Apprentice to an Expert), the hall's daily round rolls once a day and never in a raid, a festival, an unhappy village
 * or with the switch off, never two moods at once; materials in on the last day succeed, two of three (however many of
 * each) never do, and a late third doesn't undo the failure; the Masterwork goes to whoever brought the most, not who
 * brought first, and to the chest when their pack is full; a Masterwork in a chest is no beauty, in a frame 3; a broken
 * workstation never leaves a mood stuck; and the counted lines and the chat read in exact English.
 */
public class QaStrangeMoodGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos STAND = new BlockPos(8, 2, 8);
	private static final BlockPos CHEST = new BlockPos(10, 2, 8);
	private static final List<String> POOL = List.of("minecraft:diamond", "minecraft:blaze_rod", "minecraft:echo_shard", "minecraft:amethyst_shard",
		"minecraft:breeze_rod");
	private static final String NS = "aliveworkplace_test";

	/** A Rare cleric Legend inspired in Master clerics with the given chance, whose Masterwork is an iron axe. */
	private static Legend legend(String name, float chance) {
		return Legends.read(ResourceLocation.fromNamespaceAndPath(NS, name), JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"minecraft:cleric\","
			+ " \"title\": \"" + name + "\", \"lore\": \"l\", \"conditions\": [],"
			+ " \"arrive\": [{\"way\": \"inspired\", \"trades\": [\"minecraft:cleric\"], \"chance\": " + chance + "}],"
			+ " \"masterwork\": {\"item\": \"minecraft:iron_axe\", \"materials\": [\"" + String.join("\", \"", POOL) + "\"]}}").getAsJsonObject());
	}

	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			ServerLevel level = helper.getLevel();
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			for (ItemFrame f : level.getEntitiesOfClass(ItemFrame.class, helper.getBounds().inflate(4))) {
				f.discard();
			}
			VillageRaids.forget(helper.absolutePos(HALL));
			LegendPowers.forget();
		});
	}

	/** Runs {@code body} with strange moods on, moods off and only {@code legends} loaded, all put back within the tick. */
	private static void staged(GameTestHelper helper, List<Legend> legends, Runnable body) {
		boolean moods = Moods.ENABLED;
		boolean strange = StrangeMoods.ENABLED;
		try {
			StrangeMoods.ENABLED = true;
			Moods.ENABLED = false;
			Map<ResourceLocation, Legend> map = new java.util.LinkedHashMap<>();
			legends.forEach(l -> map.put(l.id(), l));
			Legends.setForTest(map);
			body.run();
		} finally {
			Moods.ENABLED = moods;
			StrangeMoods.ENABLED = strange;
			Moods.forget();
			Legends.reload(helper.getLevel().getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	private static Villager worker(GameTestHelper helper, BlockPos station, String name, int lvl) {
		helper.setBlock(station, Blocks.BREWING_STAND);
		Villager v = helper.spawn(EntityType.VILLAGER, station.south());
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.CLERIC).setLevel(lvl));
		v.refreshBrain(helper.getLevel());
		v.setCustomName(Component.literal(name));
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(station)));
		return v;
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(HALL);
	}

	private static void wellbeing(GameTestHelper helper, float wellbeing) {
		hall(helper).setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 0, 0, wellbeing));
	}

	private static Container chest(GameTestHelper helper) {
		helper.setBlock(CHEST, Blocks.CHEST);
		return (Container) helper.getBlockEntity(CHEST);
	}

	private static Item item(ResourceLocation id) {
		return BuiltInRegistries.ITEM.get(id);
	}

	private static List<String> said(QaStageFiveGameTests.Client c) {
		return c.seen().stream().map(Component::getString).toList();
	}

	/** The hall's own round, made due again today (as if the last roll was yesterday). */
	private static void roundNow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		hall(helper).setMoodRolledDay(Chronicle.day(level) - 1);
		StrangeMoods.round(level, helper.absolutePos(HALL));
	}

	/**
	 * Only a Master: with a sure-thing Legend (chance 1), clerics at levels 1 to 4 (Novice to Expert, a Journeyman among
	 * them) are never seized by the roll, nor by the hall's own round; the same villager at level 5 is.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaMoodMasterOnly"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoodMasterOnly")
	public void onlyAMasterIsSeizedNeverAJourneyman(GameTestHelper helper) {
		setUp(helper);
		Legend sure = legend("qa_sure_master", 1f);
		helper.runAfterDelay(2, () -> staged(helper, List.of(sure), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			wellbeing(helper, 0.9f);
			Villager ada = worker(helper, STAND, "Ada", 1);
			long today = Chronicle.day(level);
			for (int lvl = 1; lvl <= 4; lvl++) {
				ada.setVillagerData(ada.getVillagerData().setLevel(lvl));
				helper.assertTrue(StrangeMoods.roll(level, hall, today, RandomSource.create(1L)).isEmpty(), "a level-" + lvl + " cleric was seized by the roll");
				roundNow(helper);
				helper.assertTrue(!ModAttachments.STRANGE_MOOD.has(ada), "a level-" + lvl + " cleric was seized by the hall's round");
			}
			ada.setVillagerData(ada.getVillagerData().setLevel(5));
			roundNow(helper);
			helper.assertTrue(StrangeMoods.claiming(ada), "a Master cleric with a sure-thing Legend wasn't seized by the round");
			helper.succeed();
		}));
	}

	/**
	 * The hall's daily round (the player's entry point), each boundary from both sides with a sure-thing Legend: rolled
	 * already today, nothing; the switch off, a raid, a festival, wellbeing just under happy, nothing; wellbeing at the
	 * happy line, a mood. With two Masters only one is seized, and a new round can't seize the other while it's on.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaMoodRound"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoodRound")
	public void theDailyRoundRespectsEveryBlockAndNeverTwoMoods(GameTestHelper helper) {
		setUp(helper);
		Legend sure = legend("qa_sure_round", 1f);
		helper.runAfterDelay(2, () -> staged(helper, List.of(sure), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			wellbeing(helper, 0.9f);
			Villager ada = worker(helper, STAND, "Ada", 5);
			Villager bo = worker(helper, new BlockPos(13, 2, 13), "Bo", 5);

			long today = Chronicle.day(level);
			hall(helper).setMoodRolledDay(today);
			StrangeMoods.round(level, hall);
			helper.assertTrue(!StrangeMoods.claiming(ada) && !StrangeMoods.claiming(bo), "the round rolled twice in one day");

			StrangeMoods.ENABLED = false;
			roundNow(helper);
			StrangeMoods.ENABLED = true;
			helper.assertTrue(!ModAttachments.STRANGE_MOOD.has(ada) && !ModAttachments.STRANGE_MOOD.has(bo), "a mood with strangeMoods off");

			VillageRaids.track(level, hall, 1);
			roundNow(helper);
			VillageRaids.forget(hall);
			helper.assertTrue(!ModAttachments.STRANGE_MOOD.has(ada) && !ModAttachments.STRANGE_MOOD.has(bo), "a mood during a raid");

			long time = level.getDayTime();
			long festival = hall(helper).festivalDay();
			try {
				level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + 10000);
				hall(helper).setFestivalDay(Chronicle.day(level));
				roundNow(helper);
				helper.assertTrue(!ModAttachments.STRANGE_MOOD.has(ada) && !ModAttachments.STRANGE_MOOD.has(bo), "a mood during a festival");
			} finally {
				level.setDayTime(time);
				hall(helper).setFestivalDay(festival);
			}

			wellbeing(helper, LegendNeeds.HAPPY_WELLBEING - 0.02f);
			roundNow(helper);
			helper.assertTrue(!ModAttachments.STRANGE_MOOD.has(ada) && !ModAttachments.STRANGE_MOOD.has(bo), "a mood in a village just short of happy");

			wellbeing(helper, LegendNeeds.HAPPY_WELLBEING);
			roundNow(helper);
			int seized = (StrangeMoods.claiming(ada) ? 1 : 0) + (StrangeMoods.claiming(bo) ? 1 : 0);
			helper.assertTrue(seized == 1, "a happy village with two Masters and a sure-thing Legend: " + seized + " seized");
			helper.assertTrue(hall(helper).moodRolledDay() == Chronicle.day(level), "the round didn't mark today rolled: " + hall(helper).moodRolledDay());

			roundNow(helper);
			roundNow(helper);
			seized = (StrangeMoods.claiming(ada) ? 1 : 0) + (StrangeMoods.claiming(bo) ? 1 : 0);
			helper.assertTrue(seized == 1, "two moods at once in one village: " + seized);
			helper.succeed();
		}));
	}

	/**
	 * The deadline from the success side: all three materials in on the last of the 3 days make the Masterwork; the line
	 * over her head says "1 day left" that day and "3 days left" on the first; the board shows each material by name.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaMoodLastDay"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoodLastDay")
	public void materialsInOnTheLastDayStillMakeTheMasterwork(GameTestHelper helper) {
		setUp(helper);
		Legend smith = legend("qa_last_day", 0.125f);
		helper.runAfterDelay(2, () -> staged(helper, List.of(smith), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager ada = worker(helper, STAND, "Ada", 5);
			Container chest = chest(helper);
			long day = 20;
			StrangeMood mood = StrangeMoods.start(level, hall, ada, smith, day, RandomSource.create(11L));
			List<Item> mats = mood.materials().stream().map(QaStrangeMoodGameTests::item).toList();
			String wants = StrangeMoods.materials(mood).getString();
			String expectedWants = mats.get(0).getDescription().getString() + ", " + mats.get(1).getDescription().getString() + " and "
				+ mats.get(2).getDescription().getString();
			helper.assertTrue(wants.equals(expectedWants), "the materials read: " + wants);
			String first = StrangeMoods.line(level, mood, day).getString();
			String mid = StrangeMoods.line(level, mood, day + 1).getString();
			String last = StrangeMoods.line(level, mood, day + 2).getString();
			helper.assertTrue(first.equals("Wants " + wants + " in the chest, 3 days left"), "day 1: " + first);
			helper.assertTrue(mid.equals("Wants " + wants + " in the chest, 2 days left"), "day 2: " + mid);
			helper.assertTrue(last.equals("Wants " + wants + " in the chest, 1 day left"), "the last day (singular): " + last);
			List<String> board = Requests.of(level, ada).stream().map(r -> r.what().getString() + " x" + r.count()).toList();
			List<String> expectedBoard = mats.stream().map(m -> m.getDescription().getString() + " x1").toList();
			helper.assertTrue(board.size() == 3 && board.containsAll(expectedBoard), "the board: " + board);

			helper.assertTrue(StrangeMoods.check(level, ada, day + 1).isEmpty() && StrangeMoods.claiming(ada), "the mood ended early with nothing in");
			for (int i = 0; i < 3; i++) {
				chest.setItem(i, new ItemStack(mats.get(i)));
			}
			ItemStack work = StrangeMoods.check(level, ada, day + 2);
			helper.assertTrue(StrangeMoods.isMasterwork(work), "materials in on the last day made nothing: " + work);
			helper.assertTrue(!ModAttachments.STRANGE_MOOD.has(ada) && Legends.of(ada).map(l -> l.id().equals(smith.id())).orElse(false),
				"she isn't the Legend after a last-day Masterwork");
			helper.succeed();
		}));
	}

	/**
	 * Two of three is never success, however many of each: three of one material, one of another and an unrelated item
	 * leave one request on the board and no Masterwork on the last day; a day late the mood fails (chat in exact English);
	 * the missing third arriving after that makes nothing, eats nothing, and the sulk goes on.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaMoodTwoOfThree"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoodTwoOfThree")
	public void twoOfThreeNeverSucceedsAndALateThirdDoesntUndoTheFailure(GameTestHelper helper) {
		setUp(helper);
		Legend smith = legend("qa_two_of_three", 0.125f);
		QaStageFiveGameTests.Client jo = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("Jo"));
		helper.runAfterDelay(2, () -> staged(helper, List.of(smith), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager ada = worker(helper, STAND, "Ada", 5);
			Container chest = chest(helper);
			long day = Chronicle.day(level) - 3;
			jo.seen().clear();
			StrangeMood mood = StrangeMoods.start(level, hall, ada, smith, day, RandomSource.create(5L));
			List<Item> mats = mood.materials().stream().map(QaStrangeMoodGameTests::item).toList();
			String village = VillageHalls.name(level, hall).getString();
			String startLine = "Ada of " + village + " has been taken by a strange mood! They want " + StrangeMoods.materials(mood).getString()
				+ ", one of each, in a chest by their workstation within 3 days.";
			helper.assertTrue(said(jo).contains(startLine), "the start in chat: " + said(jo));

			chest.setItem(0, new ItemStack(mats.get(0), 3));
			chest.setItem(1, new ItemStack(mats.get(1)));
			chest.setItem(2, new ItemStack(Items.EMERALD, 5));
			helper.assertTrue(StrangeMoods.check(level, ada, day + 2).isEmpty() && StrangeMoods.claiming(ada), "two of three made a Masterwork on the last day");
			List<Requests.Request> left = Requests.of(level, ada);
			helper.assertTrue(left.size() == 1 && left.get(0).item() == mats.get(2), "the board after two of three: "
				+ left.stream().map(r -> r.what().getString()).toList());

			jo.seen().clear();
			helper.assertTrue(StrangeMoods.check(level, ada, day + 3).isEmpty(), "two of three made a Masterwork a day late");
			StrangeMood sulk = ModAttachments.STRANGE_MOOD.get(ada);
			helper.assertTrue(sulk != null && !sulk.inMood(), "two of three a day late didn't fail: " + sulk);
			helper.assertTrue(said(jo).contains("Ada's strange mood passed with nothing made. They will sulk for 7 days."), "the failure in chat: " + said(jo));
			helper.assertTrue(Requests.of(level, ada).isEmpty(), "a failed mood still asks on the board");

			chest.setItem(3, new ItemStack(mats.get(2)));
			ItemStack late = StrangeMoods.check(level, ada, day + 3);
			ItemStack later = StrangeMoods.check(level, ada, day + 4);
			helper.assertTrue(late.isEmpty() && later.isEmpty() && chest.countItem(Items.IRON_AXE) == 0, "a late third made the Masterwork");
			helper.assertTrue(chest.countItem(mats.get(0)) == 3 && chest.countItem(mats.get(1)) == 1 && chest.countItem(mats.get(2)) == 1,
				"a failed mood ate the materials");
			helper.assertTrue(StrangeMoods.sulking(ada) && Legends.of(ada).isEmpty(), "the sulk was undone by a late third");
			helper.succeed();
		}));
	}

	/**
	 * Who brought the most, not who brought first: Jo hands one material over at the board first, Max two after. The
	 * Masterwork is Max's, and the chat says so in exact English to both of them.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaMoodMost"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoodMost")
	public void theMasterworkGoesToWhoBroughtMostNotWhoBroughtFirst(GameTestHelper helper) {
		setUp(helper);
		Legend smith = legend("qa_most", 0.125f);
		QaStageFiveGameTests.Client jo = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("Jo"));
		QaStageFiveGameTests.Client max = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("Max"));
		helper.runAfterDelay(2, () -> staged(helper, List.of(smith), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager ada = worker(helper, STAND, "Ada", 5);
			Container chest = chest(helper);
			long day = 8;
			StrangeMood mood = StrangeMoods.start(level, hall, ada, smith, day, RandomSource.create(3L));
			List<Item> mats = mood.materials().stream().map(QaStrangeMoodGameTests::item).toList();
			BlockPos station = helper.absolutePos(STAND);
			chest.setItem(0, new ItemStack(mats.get(0)));
			Requests.given(jo.player(), Requests.forItem(ada, station, mats.get(0), 1), 1);
			helper.assertTrue(StrangeMoods.check(level, ada, day).isEmpty(), "one material made the Masterwork");
			jo.seen().clear();
			max.seen().clear();
			for (int i = 1; i < 3; i++) {
				chest.setItem(i, new ItemStack(mats.get(i)));
				Requests.given(max.player(), Requests.forItem(ada, station, mats.get(i), 1), 1);
			}
			ItemStack work = StrangeMoods.check(level, ada, day + 1);
			helper.assertTrue(StrangeMoods.isMasterwork(work), "no Masterwork: " + work);
			helper.assertTrue(max.player().getInventory().countItem(Items.IRON_AXE) == 1 && jo.player().getInventory().countItem(Items.IRON_AXE) == 0,
				"the Masterwork went to Jo (brought 1, first) rather than Max (brought 2)");
			String line = "Ada has made a Masterwork, " + work.getHoverName().getString() + ", and given it to " + max.player().getName().getString()
				+ ", who brought the most.";
			helper.assertTrue(said(max).contains(line) && said(jo).contains(line), "the chat: " + said(max) + " / " + said(jo));
			helper.succeed();
		}));
	}

	/**
	 * Jo brought all three but her pack is full: the Masterwork is left in the chest (the chat says so), it adds no beauty
	 * there, and put in an item frame in the village it adds exactly 3; taken out of the frame, the 3 go again.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaMoodFullPack"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoodFullPack")
	public void aFullPackLeavesItInTheChestWhereItIsNoBeauty(GameTestHelper helper) {
		setUp(helper);
		Legend smith = legend("qa_full_pack", 0.125f);
		QaStageFiveGameTests.Client jo = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("Jo"));
		helper.runAfterDelay(2, () -> staged(helper, List.of(smith), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager ada = worker(helper, STAND, "Ada", 5);
			Container chest = chest(helper);
			int before = Decorations.beauty(level, hall);
			long day = 12;
			StrangeMood mood = StrangeMoods.start(level, hall, ada, smith, day, RandomSource.create(6L));
			List<Item> mats = mood.materials().stream().map(QaStrangeMoodGameTests::item).toList();
			for (int s = 0; s < jo.player().getInventory().items.size(); s++) {
				jo.player().getInventory().items.set(s, new ItemStack(Items.DIRT, 64));
			}
			for (int i = 0; i < 3; i++) {
				chest.setItem(i, new ItemStack(mats.get(i)));
				Requests.given(jo.player(), Requests.forItem(ada, helper.absolutePos(STAND), mats.get(i), 1), 1);
			}
			jo.seen().clear();
			ItemStack work = StrangeMoods.check(level, ada, day);
			helper.assertTrue(StrangeMoods.isMasterwork(work), "no Masterwork: " + work);
			int inChest = 0;
			for (int s = 0; s < chest.getContainerSize(); s++) {
				inChest += StrangeMoods.isMasterwork(chest.getItem(s)) ? 1 : 0;
			}
			helper.assertTrue(inChest == 1, "a full pack: the Masterwork isn't in the chest (" + inChest + ")");
			String line = "Ada has made a Masterwork, " + work.getHoverName().getString() + ", and left it in the chest by their workstation.";
			helper.assertTrue(said(jo).contains(line), "the chat: " + said(jo));
			helper.assertTrue(Decorations.beauty(level, hall) == before, "a Masterwork in a chest is beauty: " + before + " -> " + Decorations.beauty(level, hall));

			ItemFrame frame = new ItemFrame(level, helper.absolutePos(new BlockPos(5, 3, 1)), Direction.SOUTH);
			frame.setItem(work.copy(), false);
			level.addFreshEntity(frame);
			helper.assertTrue(Decorations.beauty(level, hall) == before + 3, "a framed Masterwork: " + before + " -> " + Decorations.beauty(level, hall));
			frame.setItem(ItemStack.EMPTY, false);
			helper.assertTrue(Decorations.beauty(level, hall) == before, "an emptied frame kept its beauty: " + Decorations.beauty(level, hall));
			helper.succeed();
		}));
	}

	/**
	 * The workstation broken mid-mood: nothing crashes, and the mood still ends (never stuck claiming forever): a day past
	 * the last, with nothing brought, it fails into a sulk and the 10-day block. The sulk's counted line reads "2 days"
	 * then "1 day", and the sulk is over on its seventh day.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaMoodBrokenStation"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoodBrokenStation")
	public void aBrokenWorkstationNeverLeavesAMoodStuck(GameTestHelper helper) {
		setUp(helper);
		Legend smith = legend("qa_broken_station", 0.125f);
		helper.runAfterDelay(2, () -> staged(helper, List.of(smith), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager ada = worker(helper, STAND, "Ada", 5);
			long day = 30;
			StrangeMood mood = StrangeMoods.start(level, hall, ada, smith, day, RandomSource.create(8L));
			helper.setBlock(STAND, Blocks.AIR);
			helper.assertTrue(StrangeMoods.check(level, ada, day + 1).isEmpty(), "a Masterwork from nothing");
			StrangeMoods.check(level, ada, mood.lastDay() + 1);
			StrangeMood sulk = ModAttachments.STRANGE_MOOD.get(ada);
			helper.assertTrue(sulk != null && !sulk.inMood() && !StrangeMoods.claiming(ada), "a mood with a broken workstation never ended: " + sulk);
			long failed = mood.lastDay() + 1;
			helper.assertTrue(hall(helper).noMoodUntil() == failed + 10, "the 10-day block: " + hall(helper).noMoodUntil());
			String two = StrangeMoods.line(level, sulk, sulk.sulkUntil() - 2).getString();
			String one = StrangeMoods.line(level, sulk, sulk.sulkUntil() - 1).getString();
			helper.assertTrue(two.equals("Back to work in 2 days") && one.equals("Back to work in 1 day"), "the sulk's lines: " + two + " / " + one);
			helper.assertTrue(sulk.sulkUntil() == failed + 7, "a week of sulking: until " + sulk.sulkUntil() + ", failed " + failed);
			StrangeMoods.check(level, ada, failed + 7);
			helper.assertTrue(!ModAttachments.STRANGE_MOOD.has(ada), "the sulk didn't end after a week");
			helper.succeed();
		}));
	}
}
