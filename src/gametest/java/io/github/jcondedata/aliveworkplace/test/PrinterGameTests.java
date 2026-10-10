package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Luxuries;
import io.github.jcondedata.aliveworkplace.printer.Gazette;
import io.github.jcondedata.aliveworkplace.printer.PrinterWork;
import io.github.jcondedata.aliveworkplace.printer.Printers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.store.StockOrders;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestFiles;
import io.github.jcondedata.aliveworkplace.story.Rewards;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Printer (ROADMAP 34.11): picked at a cartography table with an ink sac (the Cartographer back with a compass);
 * prints books and the Village Gazette (Novice) and the Illuminated Book (Journeyman) through the luxury workshop
 * engine, from the chest by the table. The Gazette is written from the hall that day (its chronicle, its open quests,
 * the calendar, the week's births, weddings and risen households), the Illuminated Book from the whole chronicle. A
 * Scholar's research takes the printed books, and the hall quest <i>Spread the news</i> pays whoever carries this
 * week's Gazette to the hall of a village on a caravan route. The printing tests drive {@link PrinterWork} tick by tick
 * on a NoAI Printer.
 */
public class PrinterGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STANDING = new BlockPos(3, 2, 3);
	private static final BlockPos HALL = new BlockPos(6, 2, 6);
	private static final BlockPos OTHER_HALL = new BlockPos(15, 2, 15);
	/** A village's usual reach, put back after each test (two halls in one test would otherwise put back the first one's test reach). */
	private static final int USUAL_REACH = VillageHalls.RADIUS;

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	/** A cartography table with a chest beside it, no hall near and no store but the chest. */
	private static void shop(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int range = StockOrders.RANGE;
		StockOrders.RANGE = 4;
		Leftovers.after(helper, () -> StockOrders.RANGE = range);
		helper.setBlock(TABLE, Blocks.CARTOGRAPHY_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
	}

	/** A Village Hall at {@code at} whose village reaches {@code reach} blocks (its own rounds post no quest). */
	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at, int reach) {
		VillageHalls.RADIUS = reach;
		BlockPos abs = helper.absolutePos(at);
		ServerLevel world = helper.getLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = USUAL_REACH;
			Stories.Data.get(world).remove(abs); // the quests a test posted here don't wait for the next test at this spot
		});
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(at));
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		return entity;
	}

	/** A Printer of {@code level} at the table (NoAI), not yet working. */
	private static Villager printer(GameTestHelper helper, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.PRINTER).setLevel(level));
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(TABLE)));
		return v;
	}

	/** Runs a Printer's shift on {@code v} every tick, as the WORK activity would. */
	private static PrinterWork work(GameTestHelper helper, Villager v) {
		ServerLevel world = helper.getLevel();
		PrinterWork work = new PrinterWork();
		helper.onEachTick(() -> {
			if (!v.isAlive() || v.isRemoved()) {
				return;
			}
			long now = world.getGameTime();
			if (work.getStatus() == Behavior.Status.STOPPED) {
				work.tryStart(world, v, now);
			} else {
				work.tickOrStop(world, v, now);
			}
		});
		return work;
	}

	private static Container chest(GameTestHelper helper) {
		return helper.getBlockEntity(CHEST);
	}

	private static int count(Container c, Item item) {
		return c.countItem(item);
	}

	private static ItemStack first(Container c, Item item) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).is(item)) {
				return c.getItem(i);
			}
		}
		return ItemStack.EMPTY;
	}

	private static String name(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).toString();
	}

	private static VillagerProfession job(Villager villager) {
		return villager.getVillagerData().getProfession();
	}

	private static boolean atTheTable(GameTestHelper helper, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(TABLE)::equals).isPresent();
	}

	/** Everything a page says, keys and all: each translation's key, then its arguments, then what is appended. */
	private static String flat(Component text) {
		StringBuilder out = new StringBuilder();
		if (text.getContents() instanceof TranslatableContents t) {
			out.append('<').append(t.getKey());
			for (Object arg : t.getArgs()) {
				out.append('|').append(arg instanceof Component c ? flat(c) : String.valueOf(arg));
			}
			out.append('>');
		} else if (text.getContents() instanceof PlainTextContents p) {
			out.append(p.text());
		}
		for (Component sibling : text.getSiblings()) {
			out.append(flat(sibling));
		}
		return out.toString();
	}

	private static List<String> pages(GameTestHelper helper, ItemStack paper) {
		WrittenBookContent content = paper.get(DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(content != null, paper + " has no pages: it was never printed");
		List<String> out = new ArrayList<>();
		for (Filterable<Component> page : content.pages()) {
			out.add(flat(page.raw()));
		}
		return out;
	}

	private static void click(ServerPlayer player, ServerLevel level, BlockPos at, ItemStack held) {
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.moveTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 2.5, 180f, 0f);
		player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.SOUTH, 0.5), Direction.SOUTH, at, false));
	}

	/**
	 * Sneak-right-clicking a jobless villager by a cartography table with an ink sac makes them its Printer; a compass
	 * brings the Cartographer back at the same table, and an ink sac the Printer again. A plain right-click picks nothing.
	 */
	//$ gametest_batch AREA '"printerPicked"'
	@GameTest(template = AREA, batch = "printerPicked")
	public void pickedWithAnInkSacAtACartographyTable(GameTestHelper helper) {
		shop(helper);
		ServerPlayer player = player(helper);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		villager.setNoAi(true);
		StationsSpecGameTests.rightClick(player, villager, new ItemStack(Items.INK_SAC), false);
		helper.assertTrue(job(villager) == VillagerProfession.NONE, "a plain right-click with an ink sac gave " + name(job(villager)));
		StationsSpecGameTests.rightClick(player, villager, new ItemStack(Items.INK_SAC), true);
		helper.assertTrue(job(villager) == ModVillagers.PRINTER, "an ink sac at a cartography table gave " + name(job(villager)) + ", not the Printer");
		helper.assertTrue(atTheTable(helper, villager), "the Printer isn't working at the cartography table");
		StationsSpecGameTests.rightClick(player, villager, new ItemStack(Items.COMPASS), true);
		helper.assertTrue(job(villager) == VillagerProfession.CARTOGRAPHER, "a compass gave " + name(job(villager)) + ", not the Cartographer back");
		helper.assertTrue(atTheTable(helper, villager), "the Cartographer left the table");
		StationsSpecGameTests.rightClick(player, villager, new ItemStack(Items.NETHERRACK), true);
		helper.assertTrue(job(villager) == ModVillagers.NETHERWORKER, "netherrack gave " + name(job(villager)) + ": the table's Netherworker is gone");
		StationsSpecGameTests.rightClick(player, villager, new ItemStack(Items.INK_SAC), true);
		helper.assertTrue(job(villager) == ModVillagers.PRINTER && atTheTable(helper, villager), "an ink sac didn't make the Netherworker a Printer");
		helper.assertTrue(Stations.of(ModVillagers.PRINTER).map(s -> s.block() == Blocks.CARTOGRAPHY_TABLE).orElse(false), "the Printer's station isn't the cartography table");
		helper.assertTrue(Village.takesPart(villager), "porters wouldn't visit a Printer");
		helper.succeed();
	}

	/**
	 * A Novice in a village with a Scholar prints 2 books from 3 paper and a leather and 2 Gazettes from 3 paper and an
	 * ink sac (the press working), and leaves the gold alone: no Illuminated Book at their level. The Gazette's pages come
	 * from the staged hall: the three newest chronicle entries on the front page, the open quest with what it pays, the
	 * calendar, and the week's birth, wedding and risen household, without the household that fell, last month's birth
	 * or the building.
	 */
	//$ gametest_ticks_batch AREA '1600' '"printerWork"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "printerWork")
	public void aNovicePrintsBooksAndTheGazetteFromTheHall(GameTestHelper helper) {
		shop(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(4000);
		hall(helper, HALL, 16);
		BlockPos hall = helper.absolutePos(HALL);
		long today = Chronicle.day(level);
		Chronicle.atHall(level, hall, Chronicle.Kind.BIRTH, Component.literal("An old birth"), today - 30);
		Chronicle.atHall(level, hall, Chronicle.Kind.BIRTH, Component.literal("A child for Ana"), today - 2);
		Chronicle.atHall(level, hall, Chronicle.Kind.CLASS, Component.translatable("chronicle.aliveworkplace.class.fell", "The Fallers", "Peasants"), today - 1);
		Chronicle.atHall(level, hall, Chronicle.Kind.BUILT, Component.literal("A new barn"), today - 1);
		Chronicle.atHall(level, hall, Chronicle.Kind.WEDDING, Component.literal("Bea wed Cal"), today - 1);
		Chronicle.atHall(level, hall, Chronicle.Kind.CLASS, Component.translatable("chronicle.aliveworkplace.class.rose", "The Risers", "Burghers"), today);
		Stories.post(level, hall, new Quest(UUID.randomUUID(), AliveWorkplace.id("daily/want_bread"), "hall", Optional.empty(), "Ana", level.getGameTime(),
			level.getGameTime() + 72000, List.of(new Objectives.Bring("minecraft:bread", 16, "hall", Optional.empty())), new int[1],
			List.of(new Rewards.Money_(5, 0, 0, false))));
		Villager scholar = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		scholar.setNoAi(true);
		scholar.setVillagerData(scholar.getVillagerData().setProfession(ModVillagers.SCHOLAR));
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.PAPER, 6));
		c.setItem(1, new ItemStack(Items.LEATHER, 1));
		c.setItem(2, new ItemStack(Items.INK_SAC, 1));
		c.setItem(3, new ItemStack(Items.GOLD_NUGGET, 2));
		c.setItem(4, new ItemStack(Items.LAPIS_LAZULI, 1));
		c.setItem(5, new ItemStack(Items.GLOW_INK_SAC, 1));
		int before = PrinterWork.pulls;
		Villager v = printer(helper, 1);
		work(helper, v);
		helper.succeedWhen(() -> {
			helper.assertTrue(count(c, Items.BOOK) == 2, count(c, Items.BOOK) + " books, expected 2");
			helper.assertTrue(count(c, ModItems.GAZETTE) == 2, count(c, ModItems.GAZETTE) + " Gazettes, expected 2");
			helper.assertTrue(count(c, Items.PAPER) == 0 && count(c, Items.LEATHER) == 0 && count(c, Items.INK_SAC) == 0,
				count(c, Items.PAPER) + " paper, " + count(c, Items.LEATHER) + " leather and " + count(c, Items.INK_SAC) + " ink sacs left");
			helper.assertTrue(count(c, ModItems.ILLUMINATED_BOOK) == 0 && count(c, Items.GOLD_NUGGET) == 2 && count(c, Items.GLOW_INK_SAC) == 1,
				"a Novice made an Illuminated Book");
			helper.assertTrue(PrinterWork.pulls > before, "the press never worked");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(v).isEmpty(), "things left in the bag");
			ItemStack gazette = first(c, ModItems.GAZETTE);
			List<String> pages = pages(helper, gazette);
			helper.assertTrue(pages.size() == 4, pages.size() + " pages, expected the front page, the quests, the calendar and the week: " + pages);
			String front = pages.get(0);
			helper.assertTrue(front.startsWith("<book.aliveworkplace.gazette.front|") && front.contains("|" + today + ">"), "the front page's head: " + front);
			helper.assertTrue(front.contains("class.rose") && front.contains("Bea wed Cal") && front.contains("A new barn"), "the three newest entries: " + front);
			helper.assertTrue(front.indexOf("class.rose") < front.indexOf("Bea wed Cal") && !front.contains("class.fell") && !front.contains("A child for Ana"),
				"the front page isn't the newest three, newest first: " + front);
			String quests = pages.get(1);
			helper.assertTrue(quests.contains("<book.aliveworkplace.gazette.quest|<quest.aliveworkplace.bring|16|") && quests.contains("5"),
				"the open quest and its 5 emeralds: " + quests);
			helper.assertTrue(pages.get(2).startsWith("<book.aliveworkplace.gazette.calendar|<book.aliveworkplace.gazette."), "the calendar: " + pages.get(2));
			String week = pages.get(3);
			helper.assertTrue(week.contains("A child for Ana") && week.contains("Bea wed Cal") && week.contains("class.rose|The Risers"), "the week: " + week);
			helper.assertTrue(!week.contains("An old birth") && !week.contains("class.fell") && !week.contains("A new barn"),
				"old news, a fall or a building on the week's page: " + week);
			helper.assertTrue(Gazette.home(gazette).equals(Optional.of(hall)) && Gazette.day(gazette) == today && Gazette.thisWeek(gazette, today + 6)
				&& !Gazette.thisWeek(gazette, today + 7), "the Gazette's hall and day: " + Gazette.home(gazette) + ", day " + Gazette.day(gazette));
		});
	}

	/** Without a Scholar in the village the Printer keeps no books in stock; once one moves in, the books are printed. */
	//$ gametest_ticks_batch AREA '1600' '"printerBooks"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "printerBooks")
	public void booksAreOnlyKeptForAScholar(GameTestHelper helper) {
		shop(helper);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.PAPER, 3));
		c.setItem(1, new ItemStack(Items.LEATHER, 1));
		work(helper, printer(helper, 1));
		boolean[] moved = new boolean[1];
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(count(c, Items.BOOK) == 0 && count(c, Items.PAPER) == 3 && count(c, Items.LEATHER) == 1, "books printed with no Scholar to read them");
			Villager scholar = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
			scholar.setNoAi(true);
			scholar.setVillagerData(scholar.getVillagerData().setProfession(ModVillagers.SCHOLAR));
			moved[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(moved[0], "the Scholar hasn't moved in yet");
			helper.assertTrue(count(c, Items.BOOK) == 2 && count(c, Items.PAPER) == 0 && count(c, Items.LEATHER) == 0,
				count(c, Items.BOOK) + " books, " + count(c, Items.PAPER) + " paper, " + count(c, Items.LEATHER) + " leather; expected 2, 0, 0");
		});
	}

	/**
	 * A Journeyman makes an Illuminated Book from a book, 2 gold nuggets, a lapis lazuli and a glow ink sac: the hall's
	 * whole chronicle, from its first entry, behind a cover.
	 */
	//$ gametest_ticks_batch AREA '1600' '"printerIlluminated"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "printerIlluminated")
	public void aJourneymanIlluminatesTheWholeChronicle(GameTestHelper helper) {
		shop(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, HALL, 16);
		BlockPos hall = helper.absolutePos(HALL);
		long today = Chronicle.day(level);
		for (int i = 1; i <= 5; i++) {
			Chronicle.atHall(level, hall, Chronicle.Kind.BUILT, Component.literal("Entry number " + i), today - 50 + i);
		}
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.BOOK, 1));
		c.setItem(1, new ItemStack(Items.GOLD_NUGGET, 2));
		c.setItem(2, new ItemStack(Items.LAPIS_LAZULI, 1));
		c.setItem(3, new ItemStack(Items.GLOW_INK_SAC, 1));
		work(helper, printer(helper, 3));
		helper.succeedWhen(() -> {
			helper.assertTrue(count(c, ModItems.ILLUMINATED_BOOK) == 1, count(c, ModItems.ILLUMINATED_BOOK) + " Illuminated Books, expected 1");
			helper.assertTrue(count(c, Items.BOOK) == 0 && count(c, Items.GOLD_NUGGET) == 0 && count(c, Items.LAPIS_LAZULI) == 0
				&& count(c, Items.GLOW_INK_SAC) == 0, "makings left over");
			List<String> pages = pages(helper, first(c, ModItems.ILLUMINATED_BOOK));
			helper.assertTrue(pages.size() == 3 && pages.get(0).startsWith("<book.aliveworkplace.illuminated.cover|"), "a cover and two pages: " + pages);
			String all = pages.get(1) + pages.get(2);
			for (int i = 1; i <= 5; i++) {
				helper.assertTrue(all.contains("Entry number " + i), "entry " + i + " is missing: " + all);
			}
			helper.assertTrue(pages.get(1).indexOf("Entry number 1") < pages.get(1).indexOf("Entry number 3") && pages.get(2).contains("Entry number 5"),
				"the chronicle isn't in order: " + pages);
			helper.assertTrue(first(c, ModItems.ILLUMINATED_BOOK).hasFoil(), "the Illuminated Book doesn't shine");
		});
	}

	/** With nothing to print, a Novice asks for paper on the requests board. */
	//$ gametest_ticks_batch AREA '600' '"printerAsks"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "printerAsks")
	public void asksForPaperWhenTheChestIsEmpty(GameTestHelper helper) {
		shop(helper);
		Villager v = printer(helper, 1);
		work(helper, v);
		helper.succeedWhen(() -> {
			boolean asked = Requests.of(helper.getLevel(), v).stream().anyMatch(r -> r.accepts().test(new ItemStack(Items.PAPER)));
			helper.assertTrue(asked, "no request for paper: " + Requests.of(helper.getLevel(), v).stream().map(r -> r.what().getString()).toList());
			helper.assertTrue(count(chest(helper), ModItems.GAZETTE) == 0 && count(chest(helper), Items.BOOK) == 0, "papers out of nothing");
		});
	}

	/**
	 * Saved and loaded mid-job (the makings already in the bag): the reloaded Printer puts back or finishes what they
	 * had, and all the paper ends up as Gazettes, each of them printed.
	 */
	//$ gametest_ticks_batch AREA '2400' '"printerReload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "printerReload")
	public void aReloadMidJobLosesNothing(GameTestHelper helper) {
		shop(helper);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.PAPER, 6));
		c.setItem(1, new ItemStack(Items.INK_SAC, 2));
		Villager first = printer(helper, 1);
		work(helper, first);
		Villager[] copy = new Villager[1];
		PrinterWork after = new PrinterWork(); // the reloaded Printer's shift: a new one, as after a restart
		ServerLevel world = helper.getLevel();
		helper.onEachTick(() -> {
			if (copy[0] == null && first.isAlive() && !ModAttachments.BUILDER_BAG.getOrCreate(first).isEmpty()) {
				CompoundTag tag = first.saveWithoutId(new CompoundTag());
				first.discard();
				Villager v = EntityType.VILLAGER.create(world);
				v.load(tag);
				v.setUUID(UUID.randomUUID());
				world.addFreshEntity(v);
				v.setNoAi(true);
				copy[0] = v;
			} else if (copy[0] != null && copy[0].isAlive()) {
				long now = world.getGameTime();
				if (after.getStatus() == Behavior.Status.STOPPED) {
					after.tryStart(world, copy[0], now);
				} else {
					after.tickOrStop(world, copy[0], now);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(copy[0] != null, "the Printer never fetched anything");
			helper.assertTrue(job(copy[0]) == ModVillagers.PRINTER, "the job was lost on reload");
			helper.assertTrue(count(c, ModItems.GAZETTE) == 4 && count(c, Items.PAPER) == 0 && count(c, Items.INK_SAC) == 0,
				count(c, ModItems.GAZETTE) + " Gazettes, " + count(c, Items.PAPER) + " paper, " + count(c, Items.INK_SAC) + " ink sacs; expected 4, 0, 0");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(copy[0]).isEmpty(), "things left in the bag");
			for (int i = 0; i < c.getContainerSize(); i++) {
				helper.assertTrue(!c.getItem(i).is(ModItems.GAZETTE) || Gazette.written(c.getItem(i)), "a blank Gazette came off the press");
			}
		});
	}

	/**
	 * Config {@code printers} off: an ink sac picks no job at a cartography table (a compass still a Cartographer), a
	 * Printer already hired prints nothing, and no Spread the news quest can go up.
	 */
	//$ gametest_ticks_batch AREA '400' '"printerOff"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "printerOff")
	public void switchedOffPrintsNothing(GameTestHelper helper) {
		shop(helper);
		Printers.ENABLED = false;
		Leftovers.after(helper, () -> Printers.ENABLED = true);
		ServerPlayer player = player(helper);
		Villager jobless = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 3));
		jobless.setNoAi(true);
		StationsSpecGameTests.rightClick(player, jobless, new ItemStack(Items.INK_SAC), true);
		helper.assertTrue(job(jobless) == VillagerProfession.NONE, "an ink sac gave " + name(job(jobless)) + " with the switch off");
		StationsSpecGameTests.rightClick(player, jobless, new ItemStack(Items.COMPASS), true);
		helper.assertTrue(job(jobless) == VillagerProfession.CARTOGRAPHER, "a compass gave " + name(job(jobless)) + " with the switch off, not the Cartographer");
		jobless.discard();
		helper.getLevel().getPoiManager().release(helper.absolutePos(TABLE));
		Stations.Station table = Stations.at(Blocks.CARTOGRAPHY_TABLE).orElseThrow();
		helper.assertTrue(table.jobs().stream().filter(Stations::available).noneMatch(j -> j.profession().get() == ModVillagers.PRINTER),
			"the cartography table's tooltip still offers the Printer");
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.PAPER, 6));
		c.setItem(1, new ItemStack(Items.INK_SAC, 2));
		Villager hired = printer(helper, 1);
		work(helper, hired);
		ServerLevel level = helper.getLevel();
		BlockPos here = helper.absolutePos(HALL);
		hall(helper, HALL, 16);
		hall(helper, OTHER_HALL, 16);
		Caravans.Data.get(level).toggleRoute(here, helper.absolutePos(OTHER_HALL));
		Leftovers.after(helper, () -> Caravans.Data.get(level).remove(here));
		helper.assertTrue(new Objectives.SpreadNews(Optional.empty(), "").resolve(new Objectives.Context(level, here, VillageHalls.census(level, here),
			RandomSource.create(1), "")) == null, "Spread the news went up with the switch off");
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(count(c, ModItems.GAZETTE) == 0 && count(c, Items.PAPER) == 6, "printed with the switch off");
			helper.succeed();
		});
	}

	/**
	 * A Scholar's research takes the books the Printer printed: Architecture needs 4 books, the desk's chest has none,
	 * and the Printer's chest has the paper and leather for exactly 4. The research is paid and done, and the printed
	 * books are gone from the Printer's chest.
	 */
	//$ gametest_ticks_batch AREA '2400' '"printerScholar"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "printerScholar")
	public void aScholarResearchesWithPrintedBooks(GameTestHelper helper) {
		shop(helper);
		Leftovers.village(helper, 32);
		int points = Research.POINTS;
		Research.POINTS = 100;
		Leftovers.after(helper, () -> {
			Research.POINTS = points;
			Research.forget();
		});
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		BlockPos hallAt = new BlockPos(12, 2, 12);
		BlockPos desk = new BlockPos(9, 2, 9);
		BlockPos deskChest = new BlockPos(9, 2, 11);
		VillageHallBlockEntity entity = hall(helper, hallAt, 16);
		helper.setBlock(desk, ModBlocks.SCHOLARS_DESK);
		helper.setBlock(deskChest, Blocks.CHEST);
		Container own = helper.getBlockEntity(deskChest);
		own.setItem(0, new ItemStack(Items.PAPER, 16));
		own.setItem(1, new ItemStack(Items.EMERALD, 4));
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.PAPER, 6));
		c.setItem(1, new ItemStack(Items.LEATHER, 2));
		Villager scholar = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 10));
		Jobs.employ(level, scholar, helper.absolutePos(desk), ModVillagers.SCHOLARS_DESK_POI, ModVillagers.SCHOLAR);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		work(helper, printer(helper, 1));
		boolean[] printed = new boolean[1];
		helper.runAfterDelay(3, () -> {
			entity.setResearch(new Research.State(Map.of("swift_hands", 2, "hearth", 1), Optional.empty(), 0, false));
			ChoiceMenu menu = ResearchScreen.forTest(player, helper.absolutePos(hallAt));
			menu.press(ResearchScreen.TOPIC_SLOTS[Research.Topic.ARCHITECTURE.ordinal()], player);
			helper.assertTrue(entity.research().currentTopic() == Research.Topic.ARCHITECTURE, "Architecture not chosen");
		});
		helper.onEachTick(() -> printed[0] |= count(c, Items.BOOK) > 0);
		helper.succeedWhen(() -> {
			helper.assertTrue(printed[0], "the Printer printed no books");
			helper.assertTrue(entity.research().level(Research.Topic.ARCHITECTURE) == 1, "Architecture isn't researched: " + entity.research());
			helper.assertTrue(count(c, Items.BOOK) == 0, count(c, Items.BOOK) + " printed books still in the Printer's chest: the research didn't take them");
			helper.assertTrue(own.countItem(Items.PAPER) == 0 && own.countItem(Items.EMERALD) == 0, "the paper and emeralds weren't paid from the desk's chest");
		});
	}

	/**
	 * Spread the news: asked only with a Printer and a caravan route out; the quest names the village at the end of the
	 * route. Old news isn't taken; this week's Gazette, handed in at that village's hall (a right-click with it), goes
	 * into that village's store, finishes the quest and pays its 6 emeralds. A Gazette at its own hall is kept.
	 */
	//$ gametest_ticks_batch AREA '100' '"printerQuest"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "printerQuest")
	public void spreadTheNewsIsPaidOnDelivery(GameTestHelper helper) {
		shop(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(8000);
		hall(helper, HALL, 6);
		VillageHallBlockEntity there = hall(helper, OTHER_HALL, 6);
		BlockPos a = helper.absolutePos(HALL);
		BlockPos b = helper.absolutePos(OTHER_HALL);
		helper.setBlock(new BlockPos(15, 2, 12), Blocks.SMOKER); // the other village's store
		helper.setBlock(new BlockPos(16, 2, 12), Blocks.CHEST);
		Container store = helper.getBlockEntity(new BlockPos(16, 2, 12));
		Caravans.Data caravans = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			caravans.remove(a);
			caravans.remove(b);
		});
		Objectives.SpreadNews blank = new Objectives.SpreadNews(Optional.empty(), "");
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(blank.resolve(new Objectives.Context(level, a, VillageHalls.census(level, a), RandomSource.create(1), "")) == null,
				"asked with no Printer and no route");
			Villager printer = printer(helper, 1);
			printer.setCustomName(Component.literal("Inky"));
			helper.assertTrue(blank.resolve(new Objectives.Context(level, a, VillageHalls.census(level, a), RandomSource.create(1), "")) == null,
				"asked with no caravan route to carry it along");
			helper.assertTrue(caravans.toggleRoute(a, b), "setup: no route");
			Objectives.Context context = new Objectives.Context(level, a, VillageHalls.census(level, a), RandomSource.create(1), "");
			Objectives.Objective asked = blank.resolve(context);
			helper.assertTrue(asked instanceof Objectives.SpreadNews news && news.to().equals(Optional.of(b))
				&& news.village().equals(VillageHalls.name(level, b).getString()) && context.poster.equals("Inky"), "the quest as asked: " + asked + " by " + context.poster);
			helper.assertTrue(Objectives.parse(asked.json()).equals(asked), "the quest doesn't save and load as asked: " + asked.json());
			QuestFiles.QuestFile file = QuestFiles.get(AliveWorkplace.id("daily/spread_news")).orElseThrow(() -> new GameTestAssertException("no daily/spread_news quest file"));
			helper.assertTrue(file.giver().equals("hall") && file.objectives().get(0) instanceof Objectives.SpreadNews, "the quest file: " + file);
			List<Objectives.Objective> objectives = List.of(asked);
			Quest quest = new Quest(UUID.randomUUID(), file.id(), file.giver(), file.name(), context.poster, level.getGameTime(), level.getGameTime() + 72000,
				objectives, new int[1], file.rewards().stream().map(r -> r.resolve(objectives, 1f)).toList());
			helper.assertTrue(quest.emeralds() == 6, "Spread the news pays " + quest.emeralds() + " emeralds, expected 6");
			Stories.post(level, a, quest);

			ServerPlayer player = player(helper);
			long today = Chronicle.day(level);
			ItemStack old = new ItemStack(ModItems.GAZETTE);
			Gazette.write(level, a, old);
			CompoundTag tag = old.get(DataComponents.CUSTOM_DATA).copyTag();
			tag.putLong("gazette_day", today - 7);
			old.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			click(player, level, b, old);
			helper.assertTrue(player.getMainHandItem().is(ModItems.GAZETTE) && Stories.open(level, a).size() == 1
				&& player.getInventory().countItem(Items.EMERALD) == 0, "last week's Gazette was taken");

			ItemStack fresh = new ItemStack(ModItems.GAZETTE);
			Gazette.write(level, a, fresh);
			helper.assertTrue(Gazette.home(fresh).equals(Optional.of(a)) && Gazette.thisWeek(fresh, today), "setup: the Gazette isn't this week's of " + a);
			click(player, level, a, fresh);
			helper.assertTrue(player.getMainHandItem().is(ModItems.GAZETTE) && Stories.open(level, a).size() == 1, "a Gazette was handed in at its own hall");
			player.closeContainer();
			click(player, level, b, fresh);
			helper.assertTrue(player.getInventory().countItem(ModItems.GAZETTE) == 0, "the Gazette is still with the player: " + player.getMainHandItem());
			helper.assertTrue(Stories.open(level, a).isEmpty(), "the quest is still open: " + Stories.open(level, a));
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 6, player.getInventory().countItem(Items.EMERALD) + " emeralds paid, expected 6");
			helper.assertTrue(store.countItem(ModItems.GAZETTE) == 1, "the Gazette isn't in the other village's store");
			helper.assertTrue(there.chronicle().stream().anyMatch(e -> e.text().getContents() instanceof TranslatableContents t
				&& t.getKey().equals("chronicle.aliveworkplace.gazette")), "the other village's chronicle doesn't say the Gazette came");
			helper.succeed();
		});
	}

	/**
	 * The luxury files, the recipes with their levels and counts, the Pokémon that help and the trades: today's Gazette
	 * for 1 emerald from a Novice, the Illuminated Book for 8 from a Journeyman, both written as they're bought; and a
	 * blank paper is written when first read.
	 */
	//$ gametest_batch AREA '"printerData"'
	@GameTest(template = AREA, batch = "printerData")
	public void theLuxuryFilesRecipesAndTrades(GameTestHelper helper) {
		shop(helper);
		ServerLevel level = helper.getLevel();
		Luxuries.Luxury gazette = Luxuries.get(AliveWorkplace.id("gazette"));
		helper.assertTrue(gazette != null && gazette.everyDays() == 7 && gazette.matches(new ItemStack(ModItems.GAZETTE)), "gazette: " + gazette);
		Luxuries.Luxury illuminated = Luxuries.get(AliveWorkplace.id("illuminated_book"));
		helper.assertTrue(illuminated != null && illuminated.everyDays() == 16 && illuminated.matches(new ItemStack(ModItems.ILLUMINATED_BOOK)),
			"illuminated_book: " + illuminated);
		helper.assertTrue(!gazette.matches(new ItemStack(Items.WRITTEN_BOOK)) && !illuminated.matches(new ItemStack(Items.WRITTEN_BOOK)),
			"any written book counts as a luxury");
		helper.assertTrue(LuxuryRecipes.forJob(ModVillagers.PRINTER, 1).size() == 2 && LuxuryRecipes.forJob(ModVillagers.PRINTER, 2).size() == 2
			&& LuxuryRecipes.forJob(ModVillagers.PRINTER, 3).size() == 3, "recipes by level: " + LuxuryRecipes.forJob(ModVillagers.PRINTER, 5));
		LuxuryRecipes.Recipe books = LuxuryRecipes.all().get(AliveWorkplace.id("books"));
		helper.assertTrue(books != null && books.level() == 1 && books.output() == Items.BOOK && books.count() == 2 && books.inputs().size() == 2
			&& needs(books, Items.PAPER, 3) && needs(books, Items.LEATHER, 1), "the books recipe: " + books);
		LuxuryRecipes.Recipe paper = LuxuryRecipes.all().get(AliveWorkplace.id("gazette"));
		helper.assertTrue(paper != null && paper.level() == 1 && paper.output() == ModItems.GAZETTE && paper.count() == 2 && paper.inputs().size() == 2
			&& needs(paper, Items.PAPER, 3) && needs(paper, Items.INK_SAC, 1), "the Gazette recipe: " + paper);
		LuxuryRecipes.Recipe gold = LuxuryRecipes.all().get(AliveWorkplace.id("illuminated_book"));
		helper.assertTrue(gold != null && gold.level() == 3 && gold.output() == ModItems.ILLUMINATED_BOOK && gold.count() == 1 && gold.inputs().size() == 4
			&& needs(gold, Items.BOOK, 1) && needs(gold, Items.GOLD_NUGGET, 2) && needs(gold, Items.LAPIS_LAZULI, 1) && needs(gold, Items.GLOW_INK_SAC, 1),
			"the Illuminated Book recipe: " + gold);
		helper.assertTrue(Partners.types(ModVillagers.PRINTER).equals(Set.of("psychic", "normal")), "the Printer's partners: " + Partners.types(ModVillagers.PRINTER));
		helper.assertTrue(LuxuryRecipes.isMaker(ModVillagers.PRINTER), "the Printer isn't a luxury maker");
		// Trades at every level: one buys a making, one sells something printed; the Gazette for 1, the Illuminated Book for 8.
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		List<Item> sold = List.of(ModItems.GAZETTE, Items.BOOK, ModItems.ILLUMINATED_BOOK, Items.BOOK, ModItems.ILLUMINATED_BOOK);
		Set<Item> makings = Set.of(Items.PAPER, Items.INK_SAC, Items.LEATHER, Items.GLOW_INK_SAC, Items.GOLD_NUGGET, Items.LAPIS_LAZULI);
		var byLevel = VillagerTrades.TRADES.get(ModVillagers.PRINTER);
		helper.assertTrue(byLevel != null, "the Printer has no trades");
		for (int lvl = 1; lvl <= 5; lvl++) {
			VillagerTrades.ItemListing[] listings = byLevel.get(lvl);
			helper.assertTrue(listings != null && listings.length == 2, "level " + lvl + ": " + (listings == null ? 0 : listings.length) + " trades, expected 2");
			boolean buys = false;
			boolean sells = false;
			for (VillagerTrades.ItemListing listing : listings) {
				MerchantOffer offer = listing.getOffer(v, RandomSource.create(1));
				ItemStack cost = offer.getBaseCostA();
				buys |= offer.getResult().is(Items.EMERALD) && makings.contains(cost.getItem());
				sells |= cost.is(Items.EMERALD) && offer.getResult().is(sold.get(lvl - 1));
				if (offer.getResult().is(ModItems.GAZETTE)) {
					helper.assertTrue(cost.is(Items.EMERALD) && cost.getCount() == 1 && offer.getResult().getCount() == 1, "the Gazette costs " + cost);
				}
				if (offer.getResult().is(ModItems.ILLUMINATED_BOOK)) {
					helper.assertTrue(cost.is(Items.EMERALD) && cost.getCount() == 8 && offer.getResult().getCount() == 1, "the Illuminated Book costs " + cost);
				}
			}
			helper.assertTrue(buys && sells, "level " + lvl + ": buys a making " + buys + ", sells " + sold.get(lvl - 1) + " " + sells);
		}
		v.discard();
		// Bought: written that moment, from the buyer's village. Out of the creative tab: written when first read.
		hall(helper, HALL, 16);
		BlockPos hall = helper.absolutePos(HALL);
		Chronicle.atHall(level, hall, Chronicle.Kind.BUILT, Component.literal("A new barn"), Chronicle.day(level));
		ServerPlayer player = player(helper);
		player.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		ItemStack bought = new ItemStack(ModItems.GAZETTE);
		helper.assertTrue(!Gazette.written(bought), "a new Gazette is already written");
		bought.onCraftedBy(level, player, 1);
		helper.assertTrue(Gazette.home(bought).equals(Optional.of(hall)) && pages(helper, bought).get(0).contains("A new barn"), "the bought Gazette isn't today's of this village");
		ItemStack blank = new ItemStack(ModItems.ILLUMINATED_BOOK);
		player.setItemInHand(InteractionHand.MAIN_HAND, blank);
		blank.use(level, player, InteractionHand.MAIN_HAND);
		helper.assertTrue(pages(helper, player.getMainHandItem()).stream().anyMatch(p -> p.contains("A new barn")), "reading a blank Illuminated Book didn't fill it");
		ItemStack lost = new ItemStack(ModItems.GAZETTE);
		Gazette.write(level, hall.offset(500, 0, 500), lost);
		helper.assertTrue(Gazette.home(lost).isEmpty() && pages(helper, lost).get(0).startsWith("<book.aliveworkplace.gazette.no_hall|"), "a Gazette printed far from any hall: " + pages(helper, lost));
		helper.succeed();
	}

	private static boolean needs(LuxuryRecipes.Recipe recipe, Item item, int count) {
		return recipe.inputs().stream().anyMatch(i -> i.matches(new ItemStack(item)) && i.count() == count);
	}

	/** Every new sentence a player reads. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theText(GameTestHelper helper) {
		expect(helper, Component.translatable("message.aliveworkplace.printer.title", 5), "Printer · 5 made");
		Stations.Station table = Stations.of(ModVillagers.PRINTER).orElseThrow();
		expect(helper, Component.translatable(Stations.itemKey(table.jobs().stream()
			.filter(j -> j.profession().get() == ModVillagers.PRINTER).findFirst().orElseThrow())), "Ink Sac");
		expect(helper, Component.translatable("entity.minecraft.villager.printer"), "Printer");
		expect(helper, Component.translatable("entity.minecraft.zombie_villager.printer"), "Zombie Printer");
		expect(helper, Component.translatable("item.aliveworkplace.gazette"), "Village Gazette");
		expect(helper, Component.translatable("item.aliveworkplace.illuminated_book"), "Illuminated Book");
		expect(helper, Component.translatable("aliveworkplace.config.printers"), "Printers");
		helper.assertTrue(english(Component.translatable("aliveworkplace.config.printers.tooltip")).startsWith("Sneak-right-click a villager by a cartography table with an ink sac"),
			"the setting's tooltip");
		expect(helper, Component.translatable("book.aliveworkplace.gazette.title", "Oakbrook"), "The Oakbrook Gazette");
		expect(helper, Component.translatable("book.aliveworkplace.gazette.author", "Oakbrook"), "The printers of Oakbrook");
		expect(helper, Component.translatable("book.aliveworkplace.gazette.front", "Oakbrook", 12), "THE Oakbrook GAZETTE\nDay 12");
		expect(helper, Component.translatable("book.aliveworkplace.gazette.entry", 12, "Bea wed Cal"), "Day 12: Bea wed Cal");
		expect(helper, Component.translatable("book.aliveworkplace.gazette.quests"), "WANTED AT THE HALL");
		expect(helper, Component.translatable("book.aliveworkplace.gazette.quest", "Bring 16 Bread", "5 emeralds"), "Bring 16 Bread\nPays 5 emeralds");
		expect(helper, Component.translatable("book.aliveworkplace.gazette.no_quests"), "The board is bare today.");
		expect(helper, Component.translatable("book.aliveworkplace.gazette.no_news"), "Nothing to report. A quiet day is good news too.");
		expect(helper, Component.translatable("book.aliveworkplace.gazette.calendar", "tomorrow", "in 3 days (day 15)"),
			"THE CALENDAR\n\nNext festival: tomorrow\n\nNext market day: in 3 days (day 15)");
		expect(helper, Component.translatable("book.aliveworkplace.gazette.today"), "today");
		expect(helper, Component.translatable("book.aliveworkplace.gazette.tomorrow"), "tomorrow");
		expect(helper, Component.translatable("book.aliveworkplace.gazette.in_days", 3, 15), "in 3 days (day 15)");
		expect(helper, Component.translatable("book.aliveworkplace.gazette.week"), "THIS WEEK\nBirths, weddings and households on the rise");
		expect(helper, Component.translatable("book.aliveworkplace.gazette.quiet_week"), "No births, no weddings and no household moved up this week.");
		expect(helper, Component.translatable("book.aliveworkplace.gazette.no_hall", 12), "Day 12\n\nNo news today: there is no Village Hall near the press to bring any.");
		expect(helper, Component.translatable("book.aliveworkplace.illuminated.title", "Oakbrook"), "The Chronicle of Oakbrook");
		expect(helper, Component.translatable("book.aliveworkplace.illuminated.cover", "Oakbrook"), "The Chronicle of Oakbrook\n\nIlluminated and bound in gold");
		expect(helper, Component.translatable("book.aliveworkplace.illuminated.empty"), "The first page is still to be written.");
		expect(helper, Component.translatable("tooltip.aliveworkplace.gazette.day", 12), "Printed on day 12");
		expect(helper, Component.translatable("tooltip.aliveworkplace.gazette.use"), "Right-click to read");
		expect(helper, Component.translatable("quest.aliveworkplace.spread_news", "Ashford"), "Spread the news: carry this week's Gazette to the hall of Ashford");
		expect(helper, Component.translatable("message.aliveworkplace.gazette.old_news", 5), "That Gazette is old news (printed on day 5). Bring one printed this week.");
		expect(helper, Component.translatable("chronicle.aliveworkplace.gazette", "Steve", "Oakbrook"), "Steve brought the Gazette of Oakbrook");
		helper.succeed();
	}

	private static void expect(GameTestHelper helper, Component text, String want) {
		String got = english(text);
		helper.assertTrue(got.equals(want), "\"" + got + "\", expected \"" + want + "\"");
	}

	/** {@code text} in English, from the mod's own en_us.json (the server has no mod language). */
	private static String english(Component text) {
		JsonObject lang;
		try (InputStream in = Files.newInputStream(FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow()
				.findPath("assets/aliveworkplace/lang/en_us.json").orElseThrow())) {
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new GameTestAssertException("can't read en_us.json: " + e);
		}
		TranslatableContents contents = (TranslatableContents) text.getContents();
		if (!lang.has(contents.getKey())) {
			throw new GameTestAssertException("no English for " + contents.getKey());
		}
		String out = lang.get(contents.getKey()).getAsString();
		for (Object arg : contents.getArgs()) {
			out = out.replaceFirst("%s", String.valueOf(arg instanceof Component c ? c.getString() : arg));
		}
		return out;
	}
}
