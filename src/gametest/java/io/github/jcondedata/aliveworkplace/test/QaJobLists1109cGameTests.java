package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.inn.Traveller;
import io.github.jcondedata.aliveworkplace.legend.Gifted;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.research.ResearchTree;
import io.github.jcondedata.aliveworkplace.research.ResearchTrees;
import io.github.jcondedata.aliveworkplace.work.ChoiceList;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA (lane C's tester, 2026-11-09) of ROADMAP 42.3, the core job menus on the list screen, written from the spec: a
 * traveler's hire screen (money at the top, the traveler's row, Hire with the price, red while the player can't pay,
 * following the money while open) and the Research screen (tabs per tree, a row per topic with its level, price and
 * Research; gray while locked or done, gold "Under way"; the Great Library's rows). Everything goes through the player's
 * own right-click and chest-click packets ({@link JobListMenusGameTests#open} and {@link JobListMenusGameTests#click}).
 * Not repeated here: what JobListMenusGameTests already checks (red to green as money comes in, a locked Architecture,
 * choosing Swift Hands) and QaResearchDoneClick1109eBugTests (a click on a fully researched topic).
 */
public class QaJobLists1109cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	private static final BlockPos DESK = new BlockPos(8, 2, 8);

	private static boolean colored(Component c, ChatFormatting color) {
		return c != null && c.getStyle().getColor() != null && c.getStyle().getColor().getValue() == color.getColor();
	}

	private static boolean said(List<Component> seen, String key) {
		return seen.stream().anyMatch(c -> JobListMenusGameTests.key(c).equals(key));
	}

	private static int emeralds(ServerPlayer p) {
		return p.getInventory().countItem(Items.EMERALD);
	}

	/** A level-3 traveler (16 emeralds, no guild, not Gifted) standing still at (4, 2, 4). */
	private static Villager traveler(GameTestHelper helper) {
		Villager guest = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		guest.setNoAi(true);
		guest.setVillagerData(guest.getVillagerData().setProfession(VillagerProfession.NITWIT));
		ModAttachments.TRAVELLER.set(guest, new Traveller(helper.getLevel().getGameTime(), 3));
		Gifted.set(guest, null);
		return guest;
	}

	/** A Village Hall and a Scholar at their desk inside it; the hall's radius and research cache put back afterwards. */
	private static Villager scholarsHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Research.forget();
		});
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(DESK, ModBlocks.SCHOLARS_DESK);
		Villager scholar = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 9));
		Jobs.employ(level, scholar, helper.absolutePos(DESK), ModVillagers.SCHOLARS_DESK_POI, ModVillagers.SCHOLAR);
		scholar.setNoAi(true);
		return scholar;
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
	}

	// ---------------------------------------------------------------- the inn

	/**
	 * The money leaving while the hire screen is open: Hire is green with 20 emeralds, and after the player spends 15 of
	 * them (no click on the screen) the price turns red and the top says 5 emeralds; a click on Hire is then refused with
	 * the reason, nothing is taken and the screen stays open.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaJobListInnDown1109c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaJobListInnDown1109c")
	public void hirePriceTurnsRedWhenTheMoneyGoesWhileOpen(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager guest = traveler(helper);
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer player = client.player();
		player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 20));
		ChoiceMenu menu = JobListMenusGameTests.open(helper, player, guest, false);
		ChoiceList.Mark hire = JobListMenusGameTests.row(helper, menu, Innkeepers.HIRE_SLOT);
		helper.assertTrue(colored(hire.price(), ChatFormatting.GREEN), "Hire with 20 emeralds (price 16) isn't green: " + hire);
		player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 5));
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(player.containerMenu == menu, "the screen closed when the money changed");
			ChoiceList.Mark now = JobListMenusGameTests.row(helper, menu, Innkeepers.HIRE_SLOT);
			helper.assertTrue(colored(now.price(), ChatFormatting.RED), "5 emeralds left, but Hire's price didn't turn red: " + now);
			Component top = JobListMenusGameTests.info(helper, menu).money();
			helper.assertTrue(top != null && top.getString().equals(Money.balance(player).getString()), "the money at the top didn't follow: " + top);
			client.seen().clear();
			JobListMenusGameTests.click(player, menu, Innkeepers.HIRE_SLOT);
			helper.assertTrue(Innkeepers.isTraveller(guest), "hired with 5 emeralds for a 16-emerald traveler");
			helper.assertTrue(emeralds(player) == 5, "a refused hire took emeralds: " + emeralds(player));
			helper.assertTrue(said(client.seen(), "message.aliveworkplace.inn.cant_afford"), "not told why: " + client.seen());
			helper.assertTrue(player.containerMenu == menu, "a refused hire closed the screen");
			helper.succeed();
		});
	}

	/**
	 * Exactly the price: 16 emeralds for a 16-emerald traveler is enough, so Hire is green (not red at the edge), and the
	 * click hires them, takes all 16 and closes the screen. The traveler's row has no button of its own.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaJobListInnExact1109c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaJobListInnExact1109c")
	public void exactlyThePriceIsGreenAndHires(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager guest = traveler(helper);
		Traveller t = Innkeepers.traveller(guest);
		helper.assertTrue(Innkeepers.emeralds(guest, t) == 16, "a level-3 traveler's price: " + Innkeepers.emeralds(guest, t));
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer player = client.player();
		player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 16));
		ChoiceMenu menu = JobListMenusGameTests.open(helper, player, guest, false);
		ChoiceList.Mark who = JobListMenusGameTests.row(helper, menu, Innkeepers.INFO_SLOT);
		helper.assertTrue(who.button() == null && who.price() == null, "the traveler's row has a button or a price: " + who);
		ChoiceList.Mark hire = JobListMenusGameTests.row(helper, menu, Innkeepers.HIRE_SLOT);
		helper.assertTrue(colored(hire.price(), ChatFormatting.GREEN), "exactly the price shows red: " + hire);
		helper.assertTrue(hire.price().getString().equals(Money.describe(Innkeepers.dollars(guest, t), 16).getString()),
			"the price column isn't the price: " + hire.price().getString());
		helper.assertTrue(JobListMenusGameTests.key(hire.second()).equals("screen.aliveworkplace.inn.joins"), "the Hire row's line: " + hire.second());
		JobListMenusGameTests.click(player, menu, Innkeepers.HIRE_SLOT);
		helper.assertTrue(!Innkeepers.isTraveller(guest), "exactly the price didn't hire them");
		helper.assertTrue(emeralds(player) == 0, "the price taken isn't 16: " + emeralds(player) + " left");
		helper.assertTrue(player.containerMenu == player.inventoryMenu, "the screen stayed open after hiring");
		helper.assertTrue(said(client.seen(), "message.aliveworkplace.inn.hired"), "not told they joined: " + client.seen());
		helper.succeed();
	}

	/**
	 * A Gifted traveler costs twice: the Hire row says so ("Gifted: twice the usual price") and its price is 32, red for a
	 * player with 20 emeralds, enough for the usual 16. The click is refused and takes nothing.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaJobListInnGifted1109c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaJobListInnGifted1109c")
	public void giftedTravelerShowsTheDoubledPriceInRed(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.assertTrue(Gifted.CHANCE > 0 && !Gifted.all().isEmpty(), "no gifts loaded: " + Gifted.all().keySet());
		Villager guest = traveler(helper);
		Gifted.set(guest, Gifted.all().keySet().iterator().next());
		Traveller t = Innkeepers.traveller(guest);
		helper.assertTrue(Innkeepers.emeralds(guest, t) == 32, "a Gifted level-3 traveler's price: " + Innkeepers.emeralds(guest, t));
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer player = client.player();
		player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 20));
		ChoiceMenu menu = JobListMenusGameTests.open(helper, player, guest, false);
		ChoiceList.Mark hire = JobListMenusGameTests.row(helper, menu, Innkeepers.HIRE_SLOT);
		helper.assertTrue(JobListMenusGameTests.key(hire.second()).equals("screen.aliveworkplace.inn.gifted_price"), "the Gifted line: " + hire.second());
		helper.assertTrue(hire.price().getString().equals(Money.describe(Innkeepers.dollars(guest, t), 32).getString()),
			"the price column isn't the doubled price: " + hire.price().getString());
		helper.assertTrue(colored(hire.price(), ChatFormatting.RED), "20 emeralds for a 32-emerald traveler isn't red: " + hire);
		JobListMenusGameTests.click(player, menu, Innkeepers.HIRE_SLOT);
		helper.assertTrue(Innkeepers.isTraveller(guest) && emeralds(player) == 20 && said(client.seen(), "message.aliveworkplace.inn.cant_afford"),
			"the Gifted hire wasn't refused cleanly: traveler " + Innkeepers.isTraveller(guest) + ", emeralds " + emeralds(player));
		helper.succeed();
	}

	/**
	 * Two players on the same traveler's hire screen: the first hires them; the second's Hire (still on their screen)
	 * neither hires them again nor takes a single emerald, and the second's screen closes, the traveler being gone.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaJobListInnTwo1109c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaJobListInnTwo1109c")
	public void aSecondPlayerCantHireAnAlreadyHiredTraveler(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager guest = traveler(helper);
		QaStageFiveGameTests.Client first = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		QaStageFiveGameTests.Client second = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		first.player().getInventory().setItem(9, new ItemStack(Items.EMERALD, 20));
		second.player().getInventory().setItem(9, new ItemStack(Items.EMERALD, 20));
		ChoiceMenu a = JobListMenusGameTests.open(helper, first.player(), guest, false);
		ChoiceMenu b = JobListMenusGameTests.open(helper, second.player(), guest, false);
		JobListMenusGameTests.click(first.player(), a, Innkeepers.HIRE_SLOT);
		helper.assertTrue(!Innkeepers.isTraveller(guest) && emeralds(first.player()) == 4, "the first player didn't hire: " + emeralds(first.player()));
		JobListMenusGameTests.click(second.player(), b, Innkeepers.HIRE_SLOT);
		Employer boss = ModAttachments.BUILDER_EMPLOYER.get(guest);
		helper.assertTrue(emeralds(second.player()) == 20, "the second player paid for a hired villager: " + emeralds(second.player()));
		helper.assertTrue(boss != null && boss.id().equals(first.player().getUUID()), "the employer changed to the second player: " + boss);
		helper.assertTrue(!said(second.seen(), "message.aliveworkplace.inn.hired"), "the second player was told they hired them");
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(second.player().containerMenu == second.player().inventoryMenu,
				"the second player's hire screen stayed open for a traveler already hired: " + second.player().containerMenu);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- research

	/**
	 * Swift Hands under way and already paid for (half done): its row is gold with "Under way" and the top says
	 * "Researching ...: 50%". A second click on it, and a click on Hearth, are refused as busy and change nothing: the
	 * paid work and its progress stay (a click never starts it over, so the chests' paper isn't asked for twice).
	 */
	//$ gametest_ticks_batch BIG '100' '"qaJobListResearchBusy1109c"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaJobListResearchBusy1109c")
	public void clickingAPaidTopicAgainDoesntStartItOver(GameTestHelper helper) {
		Villager scholar = scholarsHall(helper);
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer player = client.player();
		helper.runAfterDelay(2, () -> {
			VillageHallBlockEntity hall = hall(helper);
			Research.Topic swift = Research.Topic.SWIFT_HANDS;
			int half = swift.points(1) / 2;
			Research.State paid = Research.State.EMPTY.choose(swift).paidFor().withProgress(half);
			hall.setResearch(paid);
			Research.forget();
			ChoiceMenu menu = JobListMenusGameTests.open(helper, player, scholar, true);
			int swiftSlot = ResearchScreen.TOPIC_SLOTS[swift.ordinal()];
			int hearthSlot = ResearchScreen.TOPIC_SLOTS[Research.Topic.HEARTH.ordinal()];
			ChoiceList.Mark s = JobListMenusGameTests.row(helper, menu, swiftSlot);
			helper.assertTrue(s.on() && !s.off() && JobListMenusGameTests.key(s.button()).equals("screen.aliveworkplace.research.button_under_way"),
				"Swift Hands under way isn't gold with Under way: " + s);
			ChoiceList.Mark h = JobListMenusGameTests.row(helper, menu, hearthSlot);
			helper.assertTrue(!h.on(), "Hearth is gold while Swift Hands is under way: " + h);
			Component top = JobListMenusGameTests.info(helper, menu).money();
			int percent = Math.round(100f * half / swift.points(1));
			helper.assertTrue(JobListMenusGameTests.key(top).equals("screen.aliveworkplace.research.now") && top.getString().contains(percent + "%"),
				"the top doesn't say what's under way and how far (" + percent + "%): " + top.getString());
			client.seen().clear();
			JobListMenusGameTests.click(player, menu, swiftSlot);
			helper.assertTrue(hall.research().equals(paid), "a second click on the paid topic changed the research: " + hall.research());
			helper.assertTrue(said(client.seen(), "message.aliveworkplace.research.busy"), "not told it's under way: " + client.seen());
			JobListMenusGameTests.click(player, menu, hearthSlot);
			helper.assertTrue(hall.research().equals(paid), "a click on Hearth dropped the paid Swift Hands: " + hall.research());
			helper.assertTrue(JobListMenusGameTests.row(helper, menu, swiftSlot).on(), "Swift Hands lost its gold after the refused clicks");
			helper.succeed();
		});
	}

	/**
	 * Save and reload mid-research: Swift Hands paid and a quarter done, the hall saved and loaded back (as a restart
	 * does); a fresh right-click shows the same gold "Under way" row and the same percent at the top.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaJobListResearchReload1109c"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaJobListResearchReload1109c")
	public void underWaySurvivesSaveAndReload(GameTestHelper helper) {
		Villager scholar = scholarsHall(helper);
		ServerPlayer player = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = hall(helper);
			Research.Topic swift = Research.Topic.SWIFT_HANDS;
			int quarter = swift.points(1) / 4;
			Research.State state = Research.State.EMPTY.choose(swift).paidFor().withProgress(quarter);
			hall.setResearch(state);
			CompoundTag saved = hall.saveWithFullMetadata(level.registryAccess());
			hall.setResearch(Research.State.EMPTY);
			hall.loadWithComponents(saved, level.registryAccess());
			Research.forget();
			helper.assertTrue(hall.research().equals(state), "the research didn't come back: " + hall.research());
			ChoiceMenu menu = JobListMenusGameTests.open(helper, player, scholar, true);
			ChoiceList.Mark s = JobListMenusGameTests.row(helper, menu, ResearchScreen.TOPIC_SLOTS[swift.ordinal()]);
			helper.assertTrue(s.on() && JobListMenusGameTests.key(s.button()).equals("screen.aliveworkplace.research.button_under_way"),
				"after a reload Swift Hands isn't gold Under way: " + s);
			Component top = JobListMenusGameTests.info(helper, menu).money();
			int percent = Math.round(100f * quarter / swift.points(1));
			helper.assertTrue(JobListMenusGameTests.key(top).equals("screen.aliveworkplace.research.now") && top.getString().contains(percent + "%"),
				"after a reload the top says: " + top.getString());
			helper.succeed();
		});
	}

	/**
	 * The Great Library's rows with the Library standing and Swift Hands at III: the scholars' Swift Hands row is gray,
	 * "Fully researched", no button; the Library's heading isn't gray and its Swift Hands IV-V row has Research; a click
	 * there chooses it, and only the Library's row turns gold (the scholars' finished row stays gray, not gold). Pharmacy,
	 * a second-tier topic at 0, has Research too. With the Library down, every one of its rows is gray.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaJobListResearchLibrary1109c"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaJobListResearchLibrary1109c")
	public void greatLibraryRowsFollowTheLibrary(GameTestHelper helper) {
		Villager scholar = scholarsHall(helper);
		Research.SecondTier before = Research.SECOND_TIER;
		Leftovers.after(helper, () -> Research.SECOND_TIER = before);
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer player = client.player();
		helper.runAfterDelay(2, () -> {
			VillageHallBlockEntity hall = hall(helper);
			Research.Topic swift = Research.Topic.SWIFT_HANDS;
			hall.setResearch(new Research.State(Map.of(swift.key(), swift.maxLevel), Optional.empty(), 0, false));
			Research.forget();
			int swiftLibrary = ResearchScreen.LIBRARY_SLOTS[ResearchScreen.LIBRARY_TOPICS.indexOf(swift)];
			int pharmacy = ResearchScreen.LIBRARY_SLOTS[ResearchScreen.LIBRARY_TOPICS.indexOf(Research.Topic.PHARMACY)];
			int swiftOwn = ResearchScreen.TOPIC_SLOTS[swift.ordinal()];
			// the Library down: all its rows gray, no button
			Research.SECOND_TIER = (l, h) -> false;
			ChoiceMenu closed = JobListMenusGameTests.open(helper, player, scholar, true);
			for (int slot : ResearchScreen.LIBRARY_SLOTS) {
				if (!closed.icon(slot).isEmpty()) {
					ChoiceList.Mark m = JobListMenusGameTests.row(helper, closed, slot);
					helper.assertTrue(m.off() && m.button() == null, "a Library row is open without the Library, slot " + slot + ": " + m);
				}
			}
			player.closeContainer();
			// the Library standing
			Research.SECOND_TIER = (l, h) -> true;
			ChoiceMenu menu = JobListMenusGameTests.open(helper, player, scholar, true);
			ChoiceList.Mark own = JobListMenusGameTests.row(helper, menu, swiftOwn);
			helper.assertTrue(own.off() && !own.on() && own.button() == null
				&& JobListMenusGameTests.key(own.price()).equals("screen.aliveworkplace.research.complete"), "the scholars' Swift Hands at III: " + own);
			ChoiceList.Mark header = JobListMenusGameTests.row(helper, menu, ResearchScreen.LIBRARY_HEADER);
			helper.assertTrue(!header.off(), "the Library's heading is gray with the Library standing: " + header);
			ChoiceList.Mark lib = JobListMenusGameTests.row(helper, menu, swiftLibrary);
			helper.assertTrue(!lib.off() && !lib.on() && JobListMenusGameTests.key(lib.button()).equals("screen.aliveworkplace.research.button_research")
				&& lib.price() != null && lib.order() > 0, "the Library's Swift Hands IV row: " + lib);
			ChoiceList.Mark ph = JobListMenusGameTests.row(helper, menu, pharmacy);
			helper.assertTrue(!ph.off() && JobListMenusGameTests.key(ph.button()).equals("screen.aliveworkplace.research.button_research"), "Pharmacy: " + ph);
			JobListMenusGameTests.click(player, menu, swiftLibrary);
			helper.assertTrue(hall.research().currentTopic() == swift, "the Library's Swift Hands wasn't chosen: " + hall.research() + " " + client.seen());
			lib = JobListMenusGameTests.row(helper, menu, swiftLibrary);
			helper.assertTrue(lib.on() && JobListMenusGameTests.key(lib.button()).equals("screen.aliveworkplace.research.button_under_way"),
				"the Library's Swift Hands isn't gold Under way: " + lib);
			own = JobListMenusGameTests.row(helper, menu, swiftOwn);
			helper.assertTrue(own.off() && !own.on(), "the scholars' finished Swift Hands turned gold too: " + own);
			helper.succeed();
		});
	}

	/**
	 * Tabs per tree: with the test Sage living in the village the scholars' screen has a tab per tree, the scholars' one
	 * on; clicking the Sage's tab turns it on (the scholars' off) and lists the Sage's topics: Calm Minds with Research,
	 * Herb Lore (needs Calm Minds) gray with no button, and The Iron Pact gray once its rival The Flame is taken. A click
	 * on Herb Lore chooses nothing; a click on Calm Minds turns it gold, "Under way", and the scholars' research is untouched.
	 */
	//$ gametest_ticks_batch BIG '200' '"qaJobListResearchTabs1109c"'
	@GameTest(template = BIG, timeoutTicks = 200, batch = "qaJobListResearchTabs1109c")
	public void legendTreeTabListsItsTopics(GameTestHelper helper) {
		Villager scholar = scholarsHall(helper);
		Leftovers.after(helper, () -> {
			CivicEffects.forget();
			LegendPowers.forget();
		});
		ServerLevel level = helper.getLevel();
		BlockPos bed = new BlockPos(4, 2, 4);
		helper.setBlock(bed, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		Villager sage = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 9));
		Legends.make(level, sage, Legends.get(ResearchTreesGameTests.SAGE).orElseThrow(() -> new IllegalStateException("test Sage not loaded")), "test");
		sage.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(bed)));
		sage.setNoAi(true);
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer player = client.player();
		helper.runAfterDelay(3, () -> {
			VillageHallBlockEntity hall = hall(helper);
			ResearchTree tree = ResearchTrees.get(ResearchTreesGameTests.TREE).orElseThrow(() -> new IllegalStateException("test tree not loaded"));
			ResearchTree.Topic flame = tree.topic("flame").orElseThrow();
			hall.setResearch(new Research.State(Map.of(ResearchTrees.levelKey(tree, flame), 1), Optional.empty(), 0, false));
			Research.forget();
			Research.State scholars = hall.research();
			ChoiceMenu menu = JobListMenusGameTests.open(helper, player, scholar, true);
			ChoiceList.Mark mainTab = ChoiceList.mark(menu.icon(ResearchScreen.TAB_SLOTS[0]));
			ChoiceList.Mark sageTab = ChoiceList.mark(menu.icon(ResearchScreen.TAB_SLOTS[1]));
			helper.assertTrue(mainTab != null && mainTab.kind() == ChoiceList.Kind.TAB && mainTab.on(), "the scholars' tab isn't an open tab: " + mainTab);
			helper.assertTrue(sageTab != null && sageTab.kind() == ChoiceList.Kind.TAB && !sageTab.on(), "no closed tab for the Sage's tree: " + sageTab
				+ " " + menu.icon(ResearchScreen.TAB_SLOTS[1]));
			JobListMenusGameTests.click(player, menu, ResearchScreen.TAB_SLOTS[1]);
			helper.assertTrue(player.containerMenu == menu, "the tab closed the screen");
			helper.assertTrue(ChoiceList.mark(menu.icon(ResearchScreen.TAB_SLOTS[1])).on() && !ChoiceList.mark(menu.icon(ResearchScreen.TAB_SLOTS[0])).on(),
				"the Sage's tab isn't the open one after a click");
			ChoiceList.Info info = JobListMenusGameTests.info(helper, menu);
			helper.assertTrue(JobListMenusGameTests.key(info.money()).equals("screen.aliveworkplace.research.nothing_short") && info.columns().size() == 3,
				"the tree's own line: " + info);
			int calm = ResearchScreen.TOPIC_SLOTS[0];
			int herb = ResearchScreen.TOPIC_SLOTS[1];
			int pact = ResearchScreen.TOPIC_SLOTS[7];
			ChoiceList.Mark c = JobListMenusGameTests.row(helper, menu, calm);
			helper.assertTrue(!c.off() && !c.on() && JobListMenusGameTests.key(c.button()).equals("screen.aliveworkplace.research.button_research")
				&& c.price() != null && JobListMenusGameTests.key(c.second()).equals("screen.aliveworkplace.research.level"), "Calm Minds' row: " + c);
			ChoiceList.Mark h = JobListMenusGameTests.row(helper, menu, herb);
			helper.assertTrue(h.off() && h.button() == null, "Herb Lore (needs Calm Minds I) isn't gray without a button: " + h);
			ChoiceList.Mark p = JobListMenusGameTests.row(helper, menu, pact);
			helper.assertTrue(p.off() && p.button() == null, "The Iron Pact (its rival The Flame taken) isn't gray without a button: " + p);
			JobListMenusGameTests.click(player, menu, herb);
			helper.assertTrue(ResearchTrees.current(hall.research(), tree).isEmpty(), "Herb Lore chosen before its needs: " + hall.research());
			JobListMenusGameTests.click(player, menu, calm);
			Optional<ResearchTrees.Current> now = ResearchTrees.current(hall.research(), tree);
			helper.assertTrue(now.isPresent() && now.get().topic().id().equals("calm_minds"), "Calm Minds not chosen: " + hall.research() + " " + client.seen());
			c = JobListMenusGameTests.row(helper, menu, calm);
			helper.assertTrue(c.on() && JobListMenusGameTests.key(c.button()).equals("screen.aliveworkplace.research.button_under_way"), "Calm Minds under way: " + c);
			helper.assertTrue(hall.research().currentTopic() == scholars.currentTopic(), "the scholars' own research changed: " + hall.research());
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- words

	/**
	 * Every sentence the two screens' rows, buttons, tops and hints show is in the language file (no raw key on screen),
	 * with its "%s" slots filled, and in American English (no British spelling); the singular "Staying 1 more day" too.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void everyListLineIsAmericanEnglish(GameTestHelper helper) {
		Language en = Language.getInstance();
		Pattern british = Pattern.compile("(?i)\\b(colour|favour|honour|grey|traveller|travellers|cancelled|defence|armour|jeweller|centre|labour)\\b");
		List<String> keys = List.of(
			"screen.aliveworkplace.inn.joins", "screen.aliveworkplace.inn.button_hire", "screen.aliveworkplace.inn.hire_hint",
			"screen.aliveworkplace.inn.staying", "screen.aliveworkplace.inn.staying.one", "screen.aliveworkplace.inn.gifted_price",
			"screen.aliveworkplace.inn.traveller", "message.aliveworkplace.inn.cant_afford", "message.aliveworkplace.inn.hired",
			"screen.aliveworkplace.research.nothing_short", "screen.aliveworkplace.research.list_hint", "screen.aliveworkplace.research.column_topic",
			"screen.aliveworkplace.research.column_level", "screen.aliveworkplace.research.column_next", "screen.aliveworkplace.research.button_research",
			"screen.aliveworkplace.research.button_under_way", "screen.aliveworkplace.research.now", "screen.aliveworkplace.research.level",
			"screen.aliveworkplace.research.complete", "screen.aliveworkplace.research.library", "screen.aliveworkplace.research.library.open",
			"screen.aliveworkplace.research.library.locked", "message.aliveworkplace.research.busy", "message.aliveworkplace.research.locked",
			"message.aliveworkplace.research.chosen", "message.aliveworkplace.money.emeralds", "message.aliveworkplace.money.emeralds.one");
		StringBuilder bad = new StringBuilder();
		for (String key : keys) {
			if (!en.has(key)) {
				bad.append("\n  missing: ").append(key);
				continue;
			}
			String text = en.getOrDefault(key);
			if (text.isBlank() || text.equals(key)) {
				bad.append("\n  empty: ").append(key);
			}
			if (british.matcher(text).find()) {
				bad.append("\n  British: ").append(key).append(" = ").append(text);
			}
		}
		// filled in, as the screens fill them: no "%s" left, no key showing
		List<Component> filled = List.of(
			Component.translatable("screen.aliveworkplace.research.now", Component.literal("Swift Hands"), 50),
			Component.translatable("screen.aliveworkplace.research.level", 1, 3),
			io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.inn.staying", 1, 1),
			io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.inn.staying", 3, 3),
			Money.describe(1600, 16), Money.describe(100, 1));
		for (Component c : filled) {
			String s = c.getString();
			if (s.contains("%s") || s.contains("%d") || s.contains("aliveworkplace.")) {
				bad.append("\n  unfilled: ").append(s);
			}
		}
		helper.assertTrue(Component.translatable("screen.aliveworkplace.research.now", Component.literal("X"), 50).getString().endsWith("50%"),
			"the percent sign: " + Component.translatable("screen.aliveworkplace.research.now", Component.literal("X"), 50).getString());
		helper.assertTrue(bad.isEmpty(), "the list screens' words:" + bad);
		helper.succeed();
	}
}
