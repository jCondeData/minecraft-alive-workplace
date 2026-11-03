package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.realm.Leaderboard;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA for B363, B398, B409 and B422 (2026-11-03): each fixed sentence at 0, 1 and 2 (and the boundaries the fix touches),
 * in the exact English of en_us.json, reached the way a player meets it where that can be done: the hall's What next?
 * tips and the Steward's reasons in {@code /workplace steward explain}, the hall name tag's tax split, a Librarian's
 * shortage line under a waiting builder, and the weekly leaderboard's realm line. No line shows a raw translation key.
 */
public class QaVillageWords1103cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String LAND = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos TAX_HALL = new BlockPos(9, 2, 9);

	private static void check(List<String> wrong, String what, String got, String want) {
		if (got == null) {
			wrong.add(what + ": nothing (want \"" + want + "\")");
		} else if (got.contains("aliveworkplace.")) {
			wrong.add(what + ": raw key in \"" + got + "\"");
		} else if (!got.equals(want)) {
			wrong.add(what + ": \"" + got + "\" (want \"" + want + "\")");
		}
	}

	private static void noRawKeys(List<String> wrong, String what, List<String> lines) {
		for (String s : lines) {
			if (s.contains("aliveworkplace.") || s.contains("%s")) {
				wrong.add(what + ": raw \"" + s + "\"");
			}
		}
	}

	private static List<String> text(ItemStack stack) {
		List<String> out = new ArrayList<>();
		if (stack.isEmpty()) {
			return out;
		}
		out.add(stack.getHoverName().getString());
		for (Component line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
			out.add(line.getString());
		}
		return out;
	}

	// ---- B363: one villager short of a bed (and one without work) ----------------------------------------------------

	/** What the player reads on the hall's screen about What next?: the button's lore, then every icon of the page it opens. */
	private static List<String> hallAdvice(ServerPlayer player, BlockPos hallAbs) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hallAbs);
		List<String> out = new ArrayList<>(text(menu.icon(VillageHallScreen.ADVICE)));
		menu.press(VillageHallScreen.ADVICE, player);
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			out.addAll(text(menu.icon(slot)));
		}
		return out;
	}

	/** {@code /workplace steward explain} run by the player standing two blocks from the hall, as the server's dispatcher runs it. */
	private static List<String> explain(QaStageFiveGameTests.Client c, GameTestHelper helper) {
		ServerPlayer p = c.player();
		p.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(HALL.east(2))));
		c.seen().clear();
		p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), "workplace steward explain");
		return c.seen().stream().map(Component::getString).map(String::strip).toList();
	}

	/** {@code adults} villagers without a bed or job and {@code beds} beds; returns what's wrong with the bed and work lines. */
	private static void bedsCase(GameTestHelper helper, int adults, int beds, String bedLine, String workLine) {
		QaHallHomesGameTests.hall(helper, HALL);
		for (int i = 0; i < adults; i++) {
			QaHallHomesGameTests.adult(helper, new BlockPos(5 + i, 2, 5));
		}
		for (int i = 0; i < beds; i++) {
			QaHallHomesGameTests.bed(helper, new BlockPos(1 + 2 * i, 2, 6));
		}
		helper.runAfterDelay(3, () -> {
			int shortBy = QaHallHomesGameTests.bedsShort(helper, HALL);
			helper.assertTrue(shortBy == Math.max(0, adults - beds), "setup: short of " + shortBy + " beds");
			List<String> wrong = new ArrayList<>();
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaVw"));
			List<String> screen = hallAdvice(c.player(), helper.absolutePos(HALL));
			List<String> said = explain(c, helper);
			noRawKeys(wrong, "hall screen", screen);
			noRawKeys(wrong, "explain", said);
			List<String> bedScreen = screen.stream().filter(s -> s.contains("no bed")).distinct().toList();
			List<String> bedWhy = said.stream().filter(s -> s.startsWith("Why:") && s.contains("no bed")).distinct().toList();
			List<String> workWhy = said.stream().filter(s -> s.startsWith("Why:") && s.contains("no work")).distinct().toList();
			if (bedLine == null) {
				if (!bedScreen.isEmpty() || !bedWhy.isEmpty()) {
					wrong.add("no one short of a bed, yet: " + bedScreen + " " + bedWhy);
				}
			} else {
				if (!bedScreen.equals(List.of(bedLine))) {
					wrong.add("the What next? beds tip on the hall screen: " + bedScreen + " (want [" + bedLine + "])");
				}
				if (!bedWhy.equals(List.of("Why: " + bedLine))) {
					wrong.add("the Steward's beds reason: " + bedWhy + " (want [Why: " + bedLine + "])");
				}
			}
			if (!workWhy.equals(List.of("Why: " + workLine))) {
				wrong.add("the Steward's jobs reason: " + workWhy + " (want [Why: " + workLine + "])");
			}
			for (String s : screen) {
				if (s.matches(".*\\b1 villagers\\b.*")) {
					wrong.add("hall screen reads '" + s + "'");
				}
			}
			for (String s : said) {
				if (s.matches(".*\\b1 villagers\\b.*")) {
					wrong.add("explain reads '" + s + "'");
				}
			}
			helper.assertTrue(wrong.isEmpty(), adults + " adults, " + beds + " beds: " + String.join("; ", wrong));
			helper.succeed();
		});
	}

	/** One adult with a bed of his own: no beds tip, no beds reason; his lack of work still reads "1 villager has no work". */
	//$ gametest_ticks_batch AREA '60' '"qaVw1103cBedsNone"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaVw1103cBedsNone")
	public void noBedShortSaysNothingAboutBeds(GameTestHelper helper) {
		bedsCase(helper, 1, 1, null, "1 villager has no work");
	}

	/** One adult, no bed: the hall's tip and the Steward's reason read "1 villager has no bed", and "1 villager has no work". */
	//$ gametest_ticks_batch AREA '60' '"qaVw1103cBedsOne"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaVw1103cBedsOne")
	public void oneBedShortReadsSingularOnTheScreenAndInExplain(GameTestHelper helper) {
		bedsCase(helper, 1, 0, "1 villager has no bed", "1 villager has no work");
	}

	/** Two adults, no beds: "2 villagers have no bed" (the Stone House's reason) and "2 villagers have no work". */
	//$ gametest_ticks_batch AREA '60' '"qaVw1103cBedsTwo"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaVw1103cBedsTwo")
	public void twoBedsShortReadPluralOnTheScreenAndInExplain(GameTestHelper helper) {
		bedsCase(helper, 2, 0, "2 villagers have no bed", "2 villagers have no work");
	}

	/** The beds tip's title and the Steward's beds and jobless reasons, word for word at 0, 1 and 2. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void bedAndWorkSentencesAtZeroOneAndTwo(GameTestHelper helper) {
		String[][] beds = {{"0 villagers have no bed"}, {"1 villager has no bed"}, {"2 villagers have no bed"}};
		String[][] work = {{"0 villagers have no work"}, {"1 villager has no work"}, {"2 villagers have no work"}};
		List<String> wrong = new ArrayList<>();
		StewardRules.Effect effect = new StewardRules.Effect(StewardRules.Kind.ASSIGN_JOBS, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
		for (int n = 0; n <= 2; n++) {
			check(wrong, "tip " + n, new VillageAdvice.Tip("beds", Items.RED_BED, n).title().getString(), beds[n][0]);
			check(wrong, "beds reason " + n, new StewardWishes.Wish(AliveWorkplace.id("qa_vw"), effect, 50, "steward.aliveworkplace.why.beds",
				List.of((long) n)).reason().getString(), beds[n][0]);
			check(wrong, "jobless reason " + n, new StewardWishes.Wish(AliveWorkplace.id("qa_vw"), effect, 50, "steward.aliveworkplace.why.jobless",
				List.of((long) n)).reason().getString(), work[n][0]);
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	// ---- B398: the hall name tag's tax split by class ---------------------------------------------------------------

	private static Villager classed(GameTestHelper helper, int x, String cls, VillagerProfession job) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, 7));
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job));
		SocialClasses.seed(v, SocialClasses.get(AliveWorkplace.id(cls)));
		// a worker, as the census counts one: a job site of their own (no AI, so it's never checked)
		v.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE,
			net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(x, 2, 9))));
		return v;
	}

	/** The hall's name tag lines under "Daily takings by class:", read by a player from the hall's screen. */
	private static List<String> taxSplit(GameTestHelper helper, String peasantsAndBurghers) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		VillageHalls.forgetNearest();
		SocialClasses.ENABLED = true;
		SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
			new WorkplaceConfig().apply();
			ClassPerks.forget();
		});
		helper.setBlock(TAX_HALL, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(TAX_HALL))).setRank(VillageRanks.Rank.HAMLET);
		int x = 4;
		for (char ch : peasantsAndBurghers.toCharArray()) {
			if (ch == 'p') {
				classed(helper, x, "peasant", x % 4 == 0 ? VillagerProfession.FARMER : VillagerProfession.FISHERMAN);
			} else {
				classed(helper, x, "burgher", ModVillagers.SCHOLAR);
			}
			x += 2;
		}
		ServerPlayer player = ReputationGameTests.player(helper, TAX_HALL.south(2));
		List<String> lore = RealmGameTests.lore(VillageHallScreen.forTest(player, helper.absolutePos(TAX_HALL)).icon(VillageHallScreen.NAME));
		int at = lore.indexOf("Daily takings by class:");
		helper.assertTrue(at >= 0, "no takings by class on the name tag: " + lore);
		List<String> out = new ArrayList<>();
		for (int i = at + 1; i < lore.size() && lore.get(i).matches("[A-Z][a-z]+: \\d+ pays? .*"); i++) {
			out.add(lore.get(i));
		}
		List<String> wrong = new ArrayList<>();
		noRawKeys(wrong, "name tag", lore);
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		return out;
	}

	/** Two Peasants and one Burgher: "Peasant: 2 pay N emeralds", "Burgher: 1 pays N emeralds"; no line for a class nobody's in. */
	//$ gametest_ticks_batch BIG '40' '"qaVw1103cTaxOneBurgher"'
	@GameTest(template = BIG, timeoutTicks = 40, batch = "qaVw1103cTaxOneBurgher")
	public void theNameTagSaysOneBurgherPays(GameTestHelper helper) {
		List<String> lines = taxSplit(helper, "ppb");
		helper.assertTrue(lines.size() == 2 && lines.get(0).matches("Peasant: 2 pay \\d+\\.\\d\\d emeralds")
			&& lines.get(1).matches("Burgher: 1 pays \\d+\\.\\d\\d emeralds"), "the tax split: " + lines);
		helper.succeed();
	}

	/** One Peasant and two Burghers: "Peasant: 1 pays ...", "Burgher: 2 pay ..."; and the key's own forms at 0, 1 and 2. */
	//$ gametest_ticks_batch BIG '40' '"qaVw1103cTaxOnePeasant"'
	@GameTest(template = BIG, timeoutTicks = 40, batch = "qaVw1103cTaxOnePeasant")
	public void theNameTagSaysOnePeasantPaysAndTwoBurghersPay(GameTestHelper helper) {
		List<String> lines = taxSplit(helper, "pbb");
		helper.assertTrue(lines.size() == 2 && lines.get(0).matches("Peasant: 1 pays \\d+\\.\\d\\d emeralds")
			&& lines.get(1).matches("Burgher: 2 pay \\d+\\.\\d\\d emeralds"), "the tax split: " + lines);
		List<String> wrong = new ArrayList<>();
		String[] want = {"Noble: 0 pay 0.00 emeralds", "Noble: 1 pays 0.50 emeralds", "Noble: 2 pay 1.00 emeralds"};
		String[] money = {"0.00", "0.50", "1.00"};
		for (int n = 0; n <= 2; n++) {
			check(wrong, "tax_class " + n, Words.counted("screen.aliveworkplace.hall.tax_class", n, "Noble", n, money[n]).getString(), want[n]);
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	// ---- B409: a Librarian's shortage line names what the chest holds too few of -------------------------------------

	private static String lackLine(GameTestHelper helper, int shelves, Object... itemsAndCounts) {
		Map<Item, Long> usable = new LinkedHashMap<>();
		for (int i = 0; i < itemsAndCounts.length; i += 2) {
			usable.put((Item) itemsAndCounts[i], ((Number) itemsAndCounts[i + 1]).longValue());
		}
		Map<Item, Integer> lack = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.BOOKSHELF, shelves, usable).entrySet().stream()
			.filter(e -> e.getValue() > 0).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
		return lack.isEmpty() ? null : JobShortages.lackLine(lack, shelves, Items.BOOKSHELF).getString();
	}

	/**
	 * The chain's shortage, as the Librarian notes it, for one and two Bookshelves: 1 or 2 Leather short names the leather
	 * (never the Rabbit Hide that makes it), with the cane short too it names both, and "1 Bookshelf" / "2 Bookshelves".
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theShortageNamesTheLeatherByItsCount(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		check(wrong, "2 of 3 leather", lackLine(helper, 1, Items.SUGAR_CANE, 9, Items.LEATHER, 2, Items.OAK_PLANKS, 6),
			"short of 1 Leather to make 1 Bookshelf");
		check(wrong, "1 of 3 leather", lackLine(helper, 1, Items.SUGAR_CANE, 9, Items.LEATHER, 1, Items.OAK_PLANKS, 6),
			"short of 2 Leather to make 1 Bookshelf");
		check(wrong, "5 of 6 leather, two shelves", lackLine(helper, 2, Items.SUGAR_CANE, 18, Items.LEATHER, 5, Items.OAK_PLANKS, 12),
			"short of 1 Leather to make 2 Bookshelves");
		check(wrong, "everything there", String.valueOf(lackLine(helper, 1, Items.SUGAR_CANE, 9, Items.LEATHER, 3, Items.OAK_PLANKS, 6)), "null");
		String two = lackLine(helper, 1, Items.SUGAR_CANE, 6, Items.LEATHER, 2, Items.OAK_PLANKS, 6);
		if (two == null || !(two.equals("short of 1 Leather, 3 Sugar Cane to make 1 Bookshelf") || two.equals("short of 3 Sugar Cane, 1 Leather to make 1 Bookshelf"))) {
			wrong.add("leather and cane both short: \"" + two + "\" (want 1 Leather and 3 Sugar Cane, to make 1 Bookshelf)");
		}
		String none = lackLine(helper, 1, Items.SUGAR_CANE, 9, Items.OAK_PLANKS, 6);
		if (none == null || none.contains("aliveworkplace.") || none.matches(".*\\b0 \\w.*") || !none.endsWith(" to make 1 Bookshelf")) {
			wrong.add("no leather at all: \"" + none + "\"");
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** A Librarian, a builder waiting for three Bookshelves and {@code items} in the chest: the builder's line reads {@code want}. */
	private static void librarianLine(GameTestHelper helper, List<String> want, ItemStack... items) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.BOOKSHELF, items);
		QaJobChainsGameTests.librarian(helper);
		helper.succeedWhen(() -> {
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing());
			String got = line == null ? null : line.getString();
			helper.assertTrue(got != null && want.contains(got), "the builder's shortage line: " + got + " (want " + want + ")");
			Container chest = helper.getBlockEntity(a.chest());
			for (ItemStack s : items) {
				helper.assertTrue(chest.countItem(s.getItem()) == s.getCount(), "the chest's " + s.getHoverName().getString() + " was touched: "
					+ chest.countItem(s.getItem()) + " of " + s.getCount());
			}
		});
	}

	/** One Leather for three shelves (nine needed): "Can't be made yet: short of 8 Leather to make 3 Bookshelves", nothing taken. */
	//$ gametest_ticks_batch AREA '1600' '"qaVw1103cLibrarianLeather"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaVw1103cLibrarianLeather")
	public void aLibrarianEightLeatherShortSaysEightLeather(GameTestHelper helper) {
		librarianLine(helper, List.of("Can't be made yet: short of 8 Leather to make 3 Bookshelves"),
			new ItemStack(Items.SUGAR_CANE, 27), new ItemStack(Items.LEATHER, 1), new ItemStack(Items.OAK_PLANKS, 18));
	}

	/** One Leather and 20 cane for three shelves: both are named, "8 Leather" and "7 Sugar Cane", nothing taken. */
	//$ gametest_ticks_batch AREA '1600' '"qaVw1103cLibrarianTwoShort"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaVw1103cLibrarianTwoShort")
	public void aLibrarianShortOfLeatherAndCaneNamesBoth(GameTestHelper helper) {
		librarianLine(helper, List.of("Can't be made yet: short of 8 Leather, 7 Sugar Cane to make 3 Bookshelves",
				"Can't be made yet: short of 7 Sugar Cane, 8 Leather to make 3 Bookshelves"),
			new ItemStack(Items.SUGAR_CANE, 20), new ItemStack(Items.LEATHER, 1), new ItemStack(Items.OAK_PLANKS, 18));
	}

	// ---- B422: the weekly leaderboard's realm line --------------------------------------------------------------------

	/** Every count of villages and villagers the realm board can show, and members only (villagers -1), at 0, 1 and 2. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theRealmValueCountsEachNumberOnItsOwn(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		String[] villages = {"0 villages", "1 village", "2 villages"};
		String[] villagers = {"0 villagers", "1 villager", "2 villagers"};
		for (int v = 0; v <= 2; v++) {
			check(wrong, "members only " + v, Leaderboard.value("realm", v, -1).getString(), villages[v]);
			check(wrong, "biggest " + v, Leaderboard.value("biggest", v, v).getString(), villagers[v]);
			for (int p = 0; p <= 2; p++) {
				check(wrong, v + " villages, " + p + " villagers", Leaderboard.value("realm", v, p).getString(), villages[v] + ", " + villagers[p]);
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** The Greatest realm line as the week's standings read it, a realm under Cal's Corth growing and shrinking. */
	//$ gametest_ticks_batch LAND '100' '"qaVw1103cRealmLine"'
	@GameTest(template = LAND, timeoutTicks = 100, batch = "qaVw1103cRealmLine")
	public void theGreatestRealmLineReadsEachCountInItsOwnNumber(GameTestHelper helper) {
		Land land = LeaderboardGameTests.three(helper);
		MinecraftServer server = land.level().getServer();
		UUID id = UUID.randomUUID();
		List<String> wrong = new ArrayList<>();
		// one village, two villagers
		land.data().putRealm(new RealmData.Realm(id, Optional.empty(), land.at(2), List.of(new RealmData.Member(land.at(2), 0)), Optional.empty(), "barony", 0));
		Realms.round(land.level(), land.hall(2), land.entity(2), 2);
		String line = Leaderboard.line(Leaderboard.Board.REALM, Leaderboard.standings(server, Leaderboard.Board.REALM)).getString();
		if (!line.endsWith("(Cal) 1 village, 2 villagers")) {
			wrong.add("1 village of 2: \"" + line + "\"");
		}
		// two villages, one villager each
		land.data().putRealm(new RealmData.Realm(id, Optional.empty(), land.at(2), List.of(new RealmData.Member(land.at(2), 0), new RealmData.Member(land.at(1), 0)),
			Optional.empty(), "barony", 0));
		Realms.round(land.level(), land.hall(1), land.entity(1), 1);
		Realms.round(land.level(), land.hall(2), land.entity(2), 1);
		line = Leaderboard.line(Leaderboard.Board.REALM, Leaderboard.standings(server, Leaderboard.Board.REALM)).getString();
		if (!line.endsWith("(Cal) 2 villages, 2 villagers")) {
			wrong.add("2 villages of 1 each: \"" + line + "\"");
		}
		// two villages, one villager between them
		Realms.round(land.level(), land.hall(1), land.entity(1), 0);
		line = Leaderboard.line(Leaderboard.Board.REALM, Leaderboard.standings(server, Leaderboard.Board.REALM)).getString();
		if (!line.endsWith("(Cal) 2 villages, 1 villager")) {
			wrong.add("2 villages, 1 villager: \"" + line + "\"");
		}
		if (line.contains("aliveworkplace.")) {
			wrong.add("raw key: " + line);
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}
}
