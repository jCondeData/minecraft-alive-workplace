package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.bard.BardWork;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.nurse.Nurses;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.printer.Gazette;
import io.github.jcondedata.aliveworkplace.printer.PrinterWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StockOrders;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.Filterable;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSongs;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * QA 2026-11-05 (lane c): the Printer, the Nurse and the Bard, from their guide pages (docs/guide/printer.md,
 * village-gazette.md, illuminated-book.md, nurse.md, nurse-station.md, bard.md, music-stand.md). Each test quotes the
 * sentence it checks. The Printer's tests drive {@link PrinterWork} tick by tick on a NoAI Printer (as PrinterGameTests
 * does) or read a blank paper with the player's own right-click; the Nurse and the Bard are hired by the player's
 * sneak-right-click and work with their real brains. What a client would hear (a jukebox song starting or stopping, the
 * harp) is read from the packets a listening player is sent.
 */
public class QaPrinterNurseBard1105cGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	static final BlockPos TABLE = new BlockPos(2, 2, 2);
	static final BlockPos CHEST = new BlockPos(2, 2, 4);
	static final BlockPos STANDING = new BlockPos(3, 2, 3);
	private static final BlockPos HALL = new BlockPos(6, 2, 6);
	private static final int USUAL_REACH = VillageHalls.RADIUS;
	/** Ten days into the world, so "6 days ago" and "7 days ago" are both real days. */
	private static final int DAY_TEN = 10 * 24000 + 2000;

	// ---------------------------------------------------------------- helpers

	private static String english(Component c) {
		return QaLuxuryJobsGameTests.english(c);
	}

	static String status(GameTestHelper helper, Villager v) {
		WorkerStatus.Entry e = WorkerStatus.get(v, helper.getLevel().getGameTime());
		return e == null ? "<no status>" : english(e.line());
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
			Stories.Data.get(world).remove(abs);
		});
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		return entity;
	}

	/** A Printer of {@code level} at the table (NoAI), not yet working. */
	private static Villager printer(GameTestHelper helper, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.PRINTER).setLevel(level));
		v.refreshBrain(helper.getLevel());
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(TABLE)));
		return v;
	}

	/** Runs a Printer's shift on {@code v} every tick, as the WORK activity would. */
	private static void work(GameTestHelper helper, Villager v) {
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
	}

	static Container chest(GameTestHelper helper) {
		return helper.getBlockEntity(CHEST);
	}

	private static List<String> pages(GameTestHelper helper, ItemStack paper) {
		WrittenBookContent content = paper.get(DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(content != null, paper + " has no pages: it was never printed");
		List<String> out = new ArrayList<>();
		for (Filterable<Component> page : content.pages()) {
			out.add(english(page.raw()));
		}
		return out;
	}

	/** {@code n} Gazettes printed (with no hall near) and dated {@code day}. */
	private static ItemStack gazettes(GameTestHelper helper, int n, long day) {
		ItemStack stack = new ItemStack(ModItems.GAZETTE, n);
		Gazette.write(helper.getLevel(), helper.absolutePos(CHEST), stack);
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putLong("gazette_day", day);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		helper.assertTrue(Gazette.written(stack) && Gazette.day(stack) == day, "setup: the stock Gazettes aren't dated day " + day);
		return stack;
	}

	/** A player's right-click with {@code stack} in the air (reading it). */
	private static ItemStack read(GameTestHelper helper, ServerPlayer player, ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.gameMode.useItem(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND);
		return player.getMainHandItem();
	}

	/** A villager at {@code at}, made a {@code job} by the player's sneak-right-click with {@code item} (by its block). */
	static Villager hired(GameTestHelper helper, BlockPos at, Item item, VillagerProfession job) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		StationsSpecGameTests.rightClick(StationsSpecGameTests.player(helper), v, new ItemStack(item), true);
		helper.assertTrue(v.getVillagerData().getProfession() == job, "setup: " + BuiltInRegistries.ITEM.getKey(item) + " gave "
			+ BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()) + ", not " + BuiltInRegistries.VILLAGER_PROFESSION.getKey(job));
		return v;
	}

	/** A NoAI villager called {@code name} at {@code at}, ill. */
	private static Villager ill(GameTestHelper helper, BlockPos at, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		Sickness.fallIll(helper.getLevel(), v);
		helper.assertTrue(Sickness.isIll(v), "setup: " + name + " didn't fall ill");
		return v;
	}

	private static boolean asks(GameTestHelper helper, Villager v, Item item) {
		return Requests.of(helper.getLevel(), v).stream().anyMatch(r -> r.accepts().test(new ItemStack(item)));
	}

	private static String requests(GameTestHelper helper, Villager v) {
		return Requests.of(helper.getLevel(), v).stream().map(r -> r.what().getString()).toList().toString();
	}

	/**
	 * A player standing at {@code at} who keeps every packet the server sends them: what their client would hear and see.
	 */
	static ServerPlayer ears(GameTestHelper helper, List<Packet<?>> heard, BlockPos at) {
		ServerLevel level = helper.getLevel();
		var cookie = CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qa-ears"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		new ServerGamePacketListenerImpl(level.getServer(), connection, player, cookie) { // (sets player.connection)
			@Override
			public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
				heard.add(packet);
				WHEN.put(packet, level.getGameTime());
				super.send(packet, listener);
			}
		};
		player.setGameMode(GameType.SPECTATOR);
		BlockPos abs = helper.absolutePos(at);
		player.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	/** When each packet a listener kept was sent (game time). */
	private static final java.util.Map<Packet<?>, Long> WHEN = Collections.synchronizedMap(new java.util.WeakHashMap<>());

	/** The jukebox level events sent for {@code pos}, each with the game time: "1010@123" (a start), "1011@456" (a stop). */
	static List<String> songLog(List<Packet<?>> heard, BlockPos pos) {
		List<String> out = new ArrayList<>();
		synchronized (heard) {
			for (Packet<?> p : heard) {
				if (p instanceof ClientboundLevelEventPacket e && e.getPos().equals(pos) && (e.getType() == 1010 || e.getType() == 1011)) {
					out.add(e.getType() + "@" + WHEN.get(p));
				}
			}
		}
		return out;
	}

	/** The jukebox level events (1010 starts a song, 1011 stops it) sent for {@code pos}. */
	static List<Integer> songEvents(List<Packet<?>> heard, BlockPos pos) {
		List<Integer> out = new ArrayList<>();
		synchronized (heard) {
			for (Packet<?> p : heard) {
				if (p instanceof ClientboundLevelEventPacket e && e.getPos().equals(pos) && (e.getType() == 1010 || e.getType() == 1011)) {
					out.add(e.getType());
				}
			}
		}
		return out;
	}

	/** A player's plain right-click on {@code villager} with an empty hand: the one packet a client sends. */
	private static void emptyHandClick(ServerPlayer player, Villager villager) {
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
	}

	// ---------------------------------------------------------------- the Printer

	/**
	 * village-gazette.md: "With no Village Hall near, it says there's no news to bring." A Printer with no hall in reach
	 * still prints (3 paper and an ink sac make 2), and the paper's one page says so, word for word, dated today.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaPrinterNoHall1105c"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaPrinterNoHall1105c")
	public void aGazettePrintedWithNoHallNearSaysThereIsNoNews(GameTestHelper helper) {
		shop(helper);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.PAPER, 3));
		c.setItem(1, new ItemStack(Items.INK_SAC, 1));
		work(helper, printer(helper, 1));
		helper.succeedWhen(() -> {
			helper.assertTrue(c.countItem(ModItems.GAZETTE) == 2 && c.countItem(Items.PAPER) == 0 && c.countItem(Items.INK_SAC) == 0,
				c.countItem(ModItems.GAZETTE) + " Gazettes, " + c.countItem(Items.PAPER) + " paper, " + c.countItem(Items.INK_SAC) + " ink; expected 2, 0, 0");
			long today = Gazette.today(helper.getLevel());
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (!s.is(ModItems.GAZETTE)) {
					continue;
				}
				List<String> pages = pages(helper, s);
				helper.assertTrue(pages.equals(List.of("Day " + today + "\n\nNo news today: there is no Village Hall near the press to bring any.")),
					"the hall-less Gazette reads " + pages);
				helper.assertTrue(Gazette.home(s).isEmpty() && Gazette.day(s) == today, "home " + Gazette.home(s) + ", day " + Gazette.day(s));
			}
		});
	}

	/**
	 * printer.md: 2 Gazettes come from "3 paper and an ink sac"; "What stops it: ... no paper or ink"; "a missing making
	 * shows on the Storehouse's requests board". Six paper and no ink: nothing printed, not a sheet taken, the bag empty,
	 * and the ink sac asked for. (No Scholar, so no books either.)
	 */
	//$ gametest_ticks_batch AREA '700' '"qaPrinterNoInk1105c"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaPrinterNoInk1105c")
	public void paperWithoutInkPrintsNothingTakesNothingAndAsksForInk(GameTestHelper helper) {
		shop(helper);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.PAPER, 6));
		Villager v = printer(helper, 1);
		int before = PrinterWork.pulls;
		work(helper, v);
		helper.runAfterDelay(500, () -> {
			helper.assertTrue(c.countItem(ModItems.GAZETTE) == 0 && c.countItem(Items.BOOK) == 0, "printed without ink: "
				+ c.countItem(ModItems.GAZETTE) + " Gazettes, " + c.countItem(Items.BOOK) + " books");
			helper.assertTrue(c.countItem(Items.PAPER) == 6, c.countItem(Items.PAPER) + " paper left of 6");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(v).isEmpty(), "the Printer carries " + ModAttachments.BUILDER_BAG.getOrCreate(v).stacks());
			helper.assertTrue(PrinterWork.pulls == before, "the press worked " + (PrinterWork.pulls - before) + " times with nothing to print");
			helper.assertTrue(asks(helper, v, Items.INK_SAC), "no request for an ink sac: " + requests(helper, v));
			helper.succeed();
		});
	}

	/**
	 * printer.md: "They keep the store in 8 of each"; village-gazette.md: "'This week's' means printed in the last 7
	 * days." Eight Gazettes printed 6 days ago are this week's: the store is full and the paper and ink stay put.
	 */
	//$ gametest_ticks_batch AREA '700' '"qaPrinterSixDays1105c"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaPrinterSixDays1105c")
	public void eightGazettesFromSixDaysAgoFillTheStore(GameTestHelper helper) {
		shop(helper);
		helper.setDayTime(DAY_TEN);
		long today = Gazette.today(helper.getLevel());
		Container c = chest(helper);
		c.setItem(0, gazettes(helper, 8, today - 6));
		c.setItem(1, new ItemStack(Items.PAPER, 3));
		c.setItem(2, new ItemStack(Items.INK_SAC, 1));
		work(helper, printer(helper, 1));
		helper.runAfterDelay(500, () -> {
			helper.assertTrue(c.countItem(ModItems.GAZETTE) == 8 && c.countItem(Items.PAPER) == 3 && c.countItem(Items.INK_SAC) == 1,
				"with 8 of this week's Gazettes in store (day " + (today - 6) + ", today " + today + "): " + c.countItem(ModItems.GAZETTE) + " Gazettes, "
					+ c.countItem(Items.PAPER) + " paper, " + c.countItem(Items.INK_SAC) + " ink; expected 8, 3, 1");
			helper.succeed();
		});
	}

	/**
	 * The other side of the week: eight Gazettes printed 7 days ago are old news, so the store holds none of this week's
	 * and the Printer prints two fresh ones, dated today.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaPrinterSevenDays1105c"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaPrinterSevenDays1105c")
	public void eightGazettesFromSevenDaysAgoAreOldNewsAndFreshOnesArePrinted(GameTestHelper helper) {
		shop(helper);
		helper.setDayTime(DAY_TEN);
		long today = Gazette.today(helper.getLevel());
		Container c = chest(helper);
		c.setItem(0, gazettes(helper, 8, today - 7));
		c.setItem(1, new ItemStack(Items.PAPER, 3));
		c.setItem(2, new ItemStack(Items.INK_SAC, 1));
		work(helper, printer(helper, 1));
		helper.succeedWhen(() -> {
			int fresh = 0;
			int old = 0;
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (s.is(ModItems.GAZETTE)) {
					if (Gazette.day(s) == today) {
						fresh += s.getCount();
					} else if (Gazette.day(s) == today - 7) {
						old += s.getCount();
					}
				}
			}
			helper.assertTrue(fresh == 2 && old == 8 && c.countItem(Items.PAPER) == 0 && c.countItem(Items.INK_SAC) == 0,
				fresh + " of today's Gazettes, " + old + " old ones, " + c.countItem(Items.PAPER) + " paper, " + c.countItem(Items.INK_SAC) + " ink; expected 2, 8, 0, 0");
		});
	}

	/**
	 * village-gazette.md / illuminated-book.md: a copy "out of the creative tab ... is printed where you stand the first
	 * time you read it". Read at a new hall with nothing in its chronicle, no quests and a quiet week, every page says so,
	 * word for word; the Illuminated Book is its cover and "The first page is still to be written."
	 */
	//$ gametest_ticks_batch AREA '100' '"qaGazetteQuiet1105c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGazetteQuiet1105c")
	public void blankPapersReadAtAQuietHallSaySoOnEveryPage(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL, 16);
		BlockPos hall = helper.absolutePos(HALL);
		helper.assertTrue(entity.chronicle().isEmpty(), "setup: a new hall's chronicle already holds " + entity.chronicle().size() + " entries");
		long today = Gazette.today(level);
		String village = english(VillageHalls.name(level, hall));
		ServerPlayer player = StationsSpecGameTests.player(helper);
		player.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		ItemStack gazette = read(helper, player, new ItemStack(ModItems.GAZETTE));
		List<String> pages = pages(helper, gazette);
		helper.assertTrue(pages.size() == 4, pages.size() + " pages, expected the front page, the quests, the calendar and the week: " + pages);
		helper.assertTrue(pages.get(0).equals("THE " + village + " GAZETTE\nDay " + today + "\n\nNothing to report. A quiet day is good news too."),
			"the front page: " + pages.get(0));
		helper.assertTrue(pages.get(1).equals("WANTED AT THE HALL\n\nThe board is bare today."), "the quests page: " + pages.get(1));
		helper.assertTrue(pages.get(2).startsWith("THE CALENDAR\n\nNext festival: ") && pages.get(2).contains("\n\nNext market day: ")
			&& !pages.get(2).contains("%"), "the calendar: " + pages.get(2));
		helper.assertTrue(pages.get(3).equals("THIS WEEK\nBirths, weddings and households on the rise\n\nNo births, no weddings and no household moved up this week."),
			"the week: " + pages.get(3));
		helper.assertTrue(english(gazette.getHoverName()).equals("The " + village + " Gazette"), "its name: " + english(gazette.getHoverName()));
		ItemStack book = read(helper, player, new ItemStack(ModItems.ILLUMINATED_BOOK));
		List<String> bookPages = pages(helper, book);
		helper.assertTrue(bookPages.equals(List.of("The Chronicle of " + village + "\n\nIlluminated and bound in gold", "The first page is still to be written.")),
			"the empty chronicle: " + bookPages);
		helper.assertTrue(english(book.getHoverName()).equals("The Chronicle of " + village), "its name: " + english(book.getHoverName()));
		helper.succeed();
	}

	/**
	 * village-gazette.md: "This week: the week's births, weddings and households on the rise"; "'This week's' means
	 * printed in the last 7 days." A birth 7 days ago is left off and one 6 days ago is on; four of the week's run onto a
	 * second page (three to a page), oldest first; the front page is the newest three, newest first.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaGazetteBusyWeek1105c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGazetteBusyWeek1105c")
	public void aBusyWeekRunsOntoASecondPageAndStopsAtSevenDays(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setDayTime(DAY_TEN);
		ServerLevel level = helper.getLevel();
		hall(helper, HALL, 16);
		BlockPos hall = helper.absolutePos(HALL);
		long today = Gazette.today(level);
		Chronicle.atHall(level, hall, Chronicle.Kind.BIRTH, Component.literal("Seven days back"), today - 7);
		Chronicle.atHall(level, hall, Chronicle.Kind.BIRTH, Component.literal("Six days back"), today - 6);
		Chronicle.atHall(level, hall, Chronicle.Kind.BIRTH, Component.literal("Three days back"), today - 3);
		Chronicle.atHall(level, hall, Chronicle.Kind.BIRTH, Component.literal("Yesterday's baby"), today - 1);
		Chronicle.atHall(level, hall, Chronicle.Kind.WEDDING, Component.literal("Today's wedding"), today);
		Chronicle.atHall(level, hall, Chronicle.Kind.BUILT, Component.literal("Today's barn"), today);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		player.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		List<String> pages = pages(helper, read(helper, player, new ItemStack(ModItems.GAZETTE)));
		helper.assertTrue(pages.size() == 5, pages.size() + " pages, expected front, quests, calendar and two of the week: " + pages);
		String front = pages.get(0);
		helper.assertTrue(front.endsWith("\n\nDay " + today + ": Today's barn\n\nDay " + today + ": Today's wedding\n\nDay " + (today - 1) + ": Yesterday's baby"),
			"the front page isn't the newest three, newest first: " + front);
		String head = "THIS WEEK\nBirths, weddings and households on the rise";
		helper.assertTrue(pages.get(3).equals(head + "\n\nDay " + (today - 6) + ": Six days back\n\nDay " + (today - 3) + ": Three days back\n\nDay "
			+ (today - 1) + ": Yesterday's baby"), "the week's first page: " + pages.get(3));
		helper.assertTrue(pages.get(4).equals(head + "\n\nDay " + today + ": Today's wedding"), "the week's second page: " + pages.get(4));
		helper.assertTrue(pages.stream().noneMatch(p -> p.contains("Seven days back")), "a birth 7 days old is in this week's paper: " + pages);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Nurse

	/**
	 * nurse.md: "cures them at once with a honey bottle, a bucket of milk or a potion of healing or regeneration from
	 * her chest"; "Over her head: ... whom she just cured". Milk cures, the empty bucket goes back to the chest, and her
	 * line says "cured Odo".
	 */
	//$ gametest_ticks_batch AREA '500' '"qaNurseMilk1105c"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaNurseMilk1105c")
	public void milkCuresAndTheEmptyBucketGoesBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setDayTime(2000);
		helper.setBlock(TABLE, Blocks.BREWING_STAND);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.MILK_BUCKET));
		Villager nurse = hired(helper, STANDING, Items.HONEY_BOTTLE, ModVillagers.NURSE);
		Villager odo = ill(helper, new BlockPos(9, 2, 9), "Odo");
		helper.succeedWhen(() -> {
			helper.assertFalse(Sickness.isIll(odo), "Odo is still ill");
			helper.assertTrue(c.countItem(Items.MILK_BUCKET) == 0 && c.countItem(Items.BUCKET) == 1,
				c.countItem(Items.MILK_BUCKET) + " milk and " + c.countItem(Items.BUCKET) + " buckets in the chest; expected 0 and 1");
			helper.assertTrue(status(helper, nurse).equals("cured Odo"), "her line: " + status(helper, nurse));
		});
	}

	/**
	 * nurse.md: one remedy, one cure; "With none there, she asks for one on the Storehouse's requests board"; "Over her
	 * head: ... how many are ill and what she needs." One honey bottle, two ill: the nearer is cured, the other stays ill,
	 * honey is asked for, and her line reads "1 ill: needs honey, milk or a healing potion".
	 */
	//$ gametest_ticks_batch AREA '600' '"qaNurseOneHoney1105c"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaNurseOneHoney1105c")
	public void oneHoneyTwoIllTheNearerIsCuredAndTheOtherWaits(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setDayTime(2000);
		helper.setBlock(TABLE, Blocks.BREWING_STAND);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.HONEY_BOTTLE));
		Villager nurse = hired(helper, STANDING, Items.HONEY_BOTTLE, ModVillagers.NURSE);
		Villager ana = ill(helper, new BlockPos(6, 2, 2), "Ana");
		Villager bea = ill(helper, new BlockPos(2, 2, 20), "Bea");
		helper.succeedWhen(() -> {
			helper.assertFalse(Sickness.isIll(ana), "Ana, 4 blocks from the stand, is still ill");
			helper.assertTrue(Sickness.isIll(bea), "Bea was cured too, with one honey bottle");
			helper.assertTrue(c.countItem(Items.HONEY_BOTTLE) == 0 && c.countItem(Items.GLASS_BOTTLE) == 1,
				c.countItem(Items.HONEY_BOTTLE) + " honey and " + c.countItem(Items.GLASS_BOTTLE) + " bottles; expected 0 and 1");
			helper.assertTrue(asks(helper, nurse, Items.HONEY_BOTTLE), "no request for a remedy: " + requests(helper, nurse));
			helper.assertTrue(status(helper, nurse).equals("1 ill: needs honey, milk or a healing potion"), "her line: " + status(helper, nurse));
		});
	}

	/**
	 * nurse.md: she cures "the ill"; "Over her head: 'right-click to be treated'". With nobody ill her honey stays in the
	 * chest, nothing is asked for, and her line is the open one.
	 */
	//$ gametest_ticks_batch AREA '500' '"qaNurseNobodyIll1105c"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaNurseNobodyIll1105c")
	public void nobodyIllNoRemedySpentNothingAsked(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setDayTime(2000);
		helper.setBlock(TABLE, Blocks.BREWING_STAND);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.HONEY_BOTTLE, 2));
		Villager nurse = hired(helper, STANDING, Items.HONEY_BOTTLE, ModVillagers.NURSE);
		Villager well = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 9));
		well.setNoAi(true);
		helper.runAfterDelay(350, () -> {
			helper.assertTrue(c.countItem(Items.HONEY_BOTTLE) == 2 && c.countItem(Items.GLASS_BOTTLE) == 0,
				c.countItem(Items.HONEY_BOTTLE) + " honey and " + c.countItem(Items.GLASS_BOTTLE) + " bottles; expected 2 and 0");
			helper.assertTrue(Requests.of(helper.getLevel(), nurse).isEmpty(), "asked for " + requests(helper, nurse) + " with nobody ill");
			helper.assertTrue(status(helper, nurse).equals("right-click to be treated"), "her line: " + status(helper, nurse));
			helper.succeed();
		});
	}

	/**
	 * nurse.md: the remedies are "a honey bottle, a bucket of milk or a potion of healing or regeneration". A potion of
	 * swiftness, a water bottle and an empty bottle cure nobody and stay in the chest; a potion of regeneration put in
	 * then cures, and its bottle comes back.
	 */
	//$ gametest_ticks_batch AREA '800' '"qaNurseNotRemedies1105c"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaNurseNotRemedies1105c")
	public void potionsThatAreNotRemediesAreLeftAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setDayTime(2000);
		helper.setBlock(TABLE, Blocks.BREWING_STAND);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = chest(helper);
		c.setItem(0, PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS));
		c.setItem(1, PotionContents.createItemStack(Items.POTION, Potions.WATER));
		c.setItem(2, new ItemStack(Items.GLASS_BOTTLE));
		Villager nurse = hired(helper, STANDING, Items.HONEY_BOTTLE, ModVillagers.NURSE);
		Villager odo = ill(helper, new BlockPos(9, 2, 9), "Odo");
		boolean[] added = {false};
		helper.runAfterDelay(320, () -> {
			helper.assertTrue(Sickness.isIll(odo), "cured with swiftness, water or an empty bottle");
			helper.assertTrue(c.countItem(Items.POTION) == 2 && c.countItem(Items.GLASS_BOTTLE) == 1,
				c.countItem(Items.POTION) + " potions and " + c.countItem(Items.GLASS_BOTTLE) + " bottles left; expected 2 and 1");
			helper.assertTrue(asks(helper, nurse, Items.HONEY_BOTTLE), "no request for a remedy: " + requests(helper, nurse));
			c.setItem(5, PotionContents.createItemStack(Items.POTION, Potions.REGENERATION));
			added[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(added[0], "the regeneration potion isn't in yet");
			helper.assertFalse(Sickness.isIll(odo), "a potion of regeneration didn't cure Odo");
			helper.assertTrue(c.countItem(Items.POTION) == 2 && c.countItem(Items.GLASS_BOTTLE) == 2,
				c.countItem(Items.POTION) + " potions and " + c.countItem(Items.GLASS_BOTTLE) + " bottles; expected swiftness and water kept and 2 bottles");
		});
	}

	/**
	 * nurse.md: "Heals: hurt villagers and golems within 16 blocks". A hurt iron golem 8 blocks off is tended; a hurt pig
	 * beside her isn't (not a villager or golem), nor a hurt villager 24 blocks off.
	 */
	//$ gametest_ticks_batch HUGE '500' '"qaNurseWhoIsTended1105c"'
	@GameTest(template = HUGE, timeoutTicks = 500, batch = "qaNurseWhoIsTended1105c")
	public void aHurtGolemIsTendedButNotAPigOrAVillager24BlocksOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setDayTime(2000);
		BlockPos stand = new BlockPos(2, 2, 15);
		helper.setBlock(stand, Blocks.BREWING_STAND);
		hired(helper, stand.east(), Items.HONEY_BOTTLE, ModVillagers.NURSE);
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(10, 2, 15));
		golem.setNoAi(true);
		golem.setHealth(50f);
		Pig pig = helper.spawn(EntityType.PIG, new BlockPos(4, 2, 17));
		pig.setNoAi(true);
		pig.setHealth(5f);
		Villager far = helper.spawn(EntityType.VILLAGER, new BlockPos(27, 2, 15));
		far.setNoAi(true);
		far.setHealth(4f);
		helper.runAfterDelay(320, () -> {
			helper.assertTrue(golem.getHealth() >= 54f, "the golem 8 blocks off is at " + golem.getHealth() + " of 100, from 50");
			helper.assertTrue(pig.getHealth() == 5f, "the pig was healed to " + pig.getHealth());
			helper.assertTrue(far.getHealth() == 4f, "the villager 24 blocks off was healed to " + far.getHealth());
			helper.succeed();
		});
	}

	/**
	 * nurse.md: "Right-click her with an empty hand: your health comes back and bad effects are cleared." The poison
	 * goes, the Speed a player drank stays (it isn't a bad effect), and the hotbar says "Nell patched you up. Take care out
	 * there!". Asleep, she treats nobody: "Nell is asleep. Come back in the morning."
	 */
	//$ gametest_ticks_batch AREA '200' '"qaNurseTreats1105c"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaNurseTreats1105c")
	public void anEmptyHandClickClearsPoisonKeepsSpeedAndAsleepSheSaysSo(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setDayTime(2000);
		Nurses.resetCooldowns();
		helper.setBlock(TABLE, Blocks.BREWING_STAND);
		Villager nurse = hired(helper, STANDING, Items.HONEY_BOTTLE, ModVillagers.NURSE);
		nurse.setNoAi(true);
		nurse.setCustomName(Component.literal("Nell"));
		List<Component> seen = Collections.synchronizedList(new ArrayList<>());
		ServerPlayer player = QaServiceJobs1031GameTests.listener(helper, seen);
		player.setHealth(6f);
		player.addEffect(new MobEffectInstance(MobEffects.POISON, 600));
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600));
		emptyHandClick(player, nurse);
		helper.assertTrue(player.getHealth() == player.getMaxHealth(), "health " + player.getHealth() + " of " + player.getMaxHealth());
		helper.assertFalse(player.hasEffect(MobEffects.POISON), "still poisoned");
		helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED), "the nurse took the player's Speed away");
		List<String> lines = seen.stream().map(QaPrinterNurseBard1105cGameTests::english).toList();
		helper.assertTrue(lines.contains("Nell patched you up. Take care out there!"), "the player was told " + lines);

		BlockPos bed = new BlockPos(6, 2, 2);
		helper.setBlock(bed, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(bed.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		nurse.startSleeping(helper.absolutePos(bed.south()));
		helper.assertTrue(nurse.isSleeping(), "setup: the nurse isn't asleep");
		List<Component> seen2 = Collections.synchronizedList(new ArrayList<>());
		ServerPlayer second = QaServiceJobs1031GameTests.listener(helper, seen2);
		second.setHealth(6f);
		emptyHandClick(second, nurse);
		helper.assertTrue(second.getHealth() == 6f, "an asleep nurse healed the player to " + second.getHealth());
		List<String> lines2 = seen2.stream().map(QaPrinterNurseBard1105cGameTests::english).toList();
		helper.assertTrue(lines2.contains("Nell is asleep. Come back in the morning."), "the player was told " + lines2);
		helper.succeed();
	}

	/**
	 * Don't break saves: a nurse who has cured one villager is saved and loaded (as a restart does). She keeps her count,
	 * and the reloaded nurse cures the next ill villager with the next honey bottle.
	 */
	//$ gametest_ticks_batch AREA '900' '"qaNurseReload1105c"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaNurseReload1105c")
	public void aNurseReloadedKeepsHerCuresAndCuresTheNext(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setDayTime(2000);
		helper.setBlock(TABLE, Blocks.BREWING_STAND);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.HONEY_BOTTLE));
		Villager first = hired(helper, STANDING, Items.HONEY_BOTTLE, ModVillagers.NURSE);
		Villager ana = ill(helper, new BlockPos(9, 2, 9), "Ana");
		Villager[] after = new Villager[1];
		Villager[] bea = new Villager[1];
		helper.onEachTick(() -> {
			if (after[0] == null && !Sickness.isIll(ana)) {
				CompoundTag tag = first.saveWithoutId(new CompoundTag());
				first.discard();
				Villager copy = EntityType.VILLAGER.create(helper.getLevel());
				copy.load(tag);
				helper.getLevel().addFreshEntity(copy);
				after[0] = copy;
				bea[0] = ill(helper, new BlockPos(10, 2, 6), "Bea");
				c.setItem(1, new ItemStack(Items.HONEY_BOTTLE));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(after[0] != null, "Ana was never cured");
			helper.assertTrue(after[0].getVillagerData().getProfession() == ModVillagers.NURSE, "the reloaded villager is no nurse");
			helper.assertFalse(Sickness.isIll(bea[0]), "the reloaded nurse hasn't cured Bea");
			int cured = ModAttachments.VILLAGERS_CURED.getOrElse(after[0], 0);
			helper.assertTrue(cured == 2, "she has cured " + cured + " villagers, expected 2 (one before the reload, one after)");
			helper.assertTrue(c.countItem(Items.HONEY_BOTTLE) == 0 && c.countItem(Items.GLASS_BOTTLE) == 2,
				c.countItem(Items.HONEY_BOTTLE) + " honey, " + c.countItem(Items.GLASS_BOTTLE) + " bottles; expected 0 and 2");
		});
	}

	// ---------------------------------------------------------------- the Bard

	/**
	 * bard.md: "No discs? They make up a little tune on the harp"; "the discs stay in the chest". A chest with a note
	 * block and a goat horn (no music disc): no song is started at the jukebox, the harp is heard, the line says
	 * "playing a tune", and nothing leaves the chest.
	 */
	//$ gametest_ticks_batch AREA '500' '"qaBardHarp1105c"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaBardHarp1105c")
	public void noDiscMeansTheHarpAndTheChestIsLeftAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(1500);
		helper.setBlock(TABLE, Blocks.JUKEBOX);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.NOTE_BLOCK, 3));
		c.setItem(1, new ItemStack(Items.GOAT_HORN));
		List<Packet<?>> heard = Collections.synchronizedList(new ArrayList<>());
		ears(helper, heard, new BlockPos(6, 2, 6));
		Villager bard = hired(helper, STANDING, Items.MUSIC_DISC_CAT, ModVillagers.BARD);
		helper.runAfterDelay(300, () -> {
			BlockPos jukebox = helper.absolutePos(TABLE);
			helper.assertTrue(BardWork.playing(bard).isEmpty(), "playing " + BardWork.playing(bard) + " with no disc");
			helper.assertTrue(songEvents(heard, jukebox).isEmpty(), "jukebox events with no disc: " + songEvents(heard, jukebox));
			long harp;
			synchronized (heard) {
				harp = heard.stream().filter(p -> p instanceof ClientboundSoundPacket s && s.getSound().value() == SoundEvents.NOTE_BLOCK_HARP.value()).count();
			}
			helper.assertTrue(harp > 0, "no harp note was heard in 300 ticks");
			helper.assertTrue(status(helper, bard).equals("playing a tune"), "the line: " + status(helper, bard));
			helper.assertTrue(c.countItem(Items.NOTE_BLOCK) == 3 && c.countItem(Items.GOAT_HORN) == 1, "the chest changed: "
				+ c.countItem(Items.NOTE_BLOCK) + " note blocks, " + c.countItem(Items.GOAT_HORN) + " goat horns");
			helper.succeed();
		});
	}

	/**
	 * The set ending ends the song: bard.md, "Every morning and evening the bard plays". When the morning set is over
	 * (day time past 3500) the bard stops, and the song started at the jukebox is stopped for listeners too.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaBardSetEnds1105c"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaBardSetEnds1105c")
	public void theMorningSetEndingStopsTheSong(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(1500);
		helper.setBlock(TABLE, Blocks.JUKEBOX);
		helper.setBlock(CHEST, Blocks.CHEST);
		chest(helper).setItem(0, new ItemStack(Items.MUSIC_DISC_CAT));
		List<Packet<?>> heard = Collections.synchronizedList(new ArrayList<>());
		ears(helper, heard, new BlockPos(6, 2, 6));
		Villager bard = hired(helper, STANDING, Items.MUSIC_DISC_CAT, ModVillagers.BARD);
		BlockPos jukebox = helper.absolutePos(TABLE);
		long[] endedAt = {-1};
		helper.onEachTick(() -> {
			if (endedAt[0] < 0 && BardWork.playing(bard).map(s -> s.is(JukeboxSongs.CAT)).orElse(false)) {
				helper.setDayTime(4000);
				endedAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(endedAt[0] >= 0, "Cat never started");
			helper.assertTrue(BardWork.playing(bard).isEmpty(), "still playing " + BardWork.playing(bard) + " " + (helper.getTick() - endedAt[0])
				+ " ticks after the set ended");
			List<Integer> events = songEvents(heard, jukebox);
			helper.assertTrue(events.equals(List.of(1010, 1011)), "jukebox events: " + events + ", expected a start then a stop");
		});
	}
}
