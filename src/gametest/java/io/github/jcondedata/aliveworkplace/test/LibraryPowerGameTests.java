package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderWork;
import io.github.jcondedata.aliveworkplace.explore.ExplorerWork;
import io.github.jcondedata.aliveworkplace.explore.Explorers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.nurse.NurseWork;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.wonder.LibraryPowers;
import io.github.jcondedata.aliveworkplace.wonder.WonderCrews;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The Great Library's powers (ROADMAP 35.14): its second research tier, a row of its own on the scholar's screen that
 * reads "Needs the Great Library" until it stands; each topic's bonus (Swift Hands IV and V, Scholarship, Engineering,
 * Cartography, Trade Law, Pharmacy), paused while the Library is damaged though the levels stay; Swift Hands V on top of
 * every other bonus still no more than twice the pace; and a village's old research loading unchanged.
 */
public class LibraryPowerGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation LIBRARY = AliveWorkplace.id("great_library");
	private static final BlockPos HALL = new BlockPos(17, 2, 17);
	private static final BlockPos DESK = new BlockPos(8, 2, 8);
	private static final BlockPos CHEST = new BlockPos(8, 2, 10);

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
	}

	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		int points = Research.POINTS;
		VillageHalls.RADIUS = 16;
		Research.POINTS = 100;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Research.POINTS = points;
			Research.forget();
			VillageNeeds.forget();
		});
		helper.setDayTime(2000);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		return new Village(level, hall, (VillageHallBlockEntity) level.getBlockEntity(hall));
	}

	/** A finished, standing Great Library of the village round {@code hall}; removed after the test. */
	private static UUID library(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Wonders.loaded().containsKey(LIBRARY), "the Great Library isn't loaded: " + Wonders.problems());
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(LIBRARY, hall, null, "", 1);
		manager.finish(site.id(), Chronicle.day(level));
		UUID id = site.id();
		standing(level, id, true);
		Leftovers.after(helper, () -> {
			manager.remove(id);
			Wonders.forgetPowers();
			Research.forget();
			VillageNeeds.forget();
		});
		return id;
	}

	/** Sets the Library standing or damaged (the count far ahead, so that none runs in the test); the caches forgotten. */
	private static void standing(ServerLevel level, UUID id, boolean whole) {
		WonderManager.get(level).setKept(id, new WonderSite.Kept(whole ? 100 : 70, Chronicle.day(level) + 10_000, !whole, 0));
		Wonders.forgetPowers();
		Research.forget();
		VillageNeeds.forget();
	}

	private static void levels(Village v, Map<String, Integer> levels) {
		v.entity().setResearch(new Research.State(Map.copyOf(levels), Optional.empty(), 0, false));
		Research.forget();
		VillageNeeds.forget();
	}

	private static String lore(ItemStack icon) {
		return String.join(" / ", icon.getTooltipLines(Item.TooltipContext.EMPTY, null, net.minecraft.world.item.TooltipFlag.Default.NORMAL)
			.stream().map(Component::getString).toList());
	}

	private static void translated(GameTestHelper helper, String text) {
		helper.assertTrue(!text.contains("aliveworkplace.") && !text.contains("%s"), "untranslated: " + text);
	}

	private static int slot(Research.Topic topic) {
		return ResearchScreen.LIBRARY_SLOTS[ResearchScreen.LIBRARY_TOPICS.indexOf(topic)];
	}

	/**
	 * Without the Library its row reads "Needs the Great Library" and a click does nothing; with it, Swift Hands IV waits
	 * for Swift Hands III, Scholarship is chosen on the screen, the scholar takes 24 paper, 6 books and 8 emeralds and
	 * researches it at twice the first tier's points; damaged, the research waits.
	 */
	//$ gametest_ticks_batch AREA '1200' '"libraryRowLocked"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "libraryRowLocked")
	public void theLibraryRowIsLockedUntilItStands(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(DESK, ModBlocks.SCHOLARS_DESK);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager scholar = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 9));
		Jobs.employ(v.level(), scholar, helper.absolutePos(DESK), ModVillagers.SCHOLARS_DESK_POI, ModVillagers.SCHOLAR);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Container chest = helper.getBlockEntity(CHEST);
		helper.assertTrue(ResearchScreen.LIBRARY_TOPICS.equals(List.of(Research.Topic.SWIFT_HANDS, Research.Topic.SCHOLARSHIP, Research.Topic.ENGINEERING,
			Research.Topic.CARTOGRAPHY, Research.Topic.TRADE_LAW, Research.Topic.PHARMACY)), "the row: " + ResearchScreen.LIBRARY_TOPICS);
		// costs and points
		helper.assertTrue(Research.Topic.SCHOLARSHIP.cost(1).equals(new Research.Cost(24, 6, 8)) && Research.Topic.TRADE_LAW.cost(2).equals(new Research.Cost(48, 12, 16))
			&& Research.Topic.SWIFT_HANDS.cost(4).equals(new Research.Cost(96, 24, 32)), "second-tier costs");
		helper.assertTrue(Research.Topic.SCHOLARSHIP.points(1) == 2 * Research.Topic.HEARTH.points(1)
			&& Research.Topic.SWIFT_HANDS.points(4) == 2 * 4 * Research.POINTS && Research.Topic.SWIFT_HANDS.points(3) == 3 * Research.POINTS, "second-tier points");
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = ResearchScreen.forTest(player, v.hall());
			String header = lore(menu.icon(ResearchScreen.LIBRARY_HEADER));
			helper.assertTrue(header.contains("Needs the Great Library"), "header without it: " + header);
			for (Research.Topic topic : ResearchScreen.LIBRARY_TOPICS) {
				String text = lore(menu.icon(slot(topic)));
				helper.assertTrue(text.contains("Needs the Great Library"), topic + " without it: " + text);
				translated(helper, text);
			}
			helper.assertFalse(Research.State.EMPTY.available(Research.Topic.SCHOLARSHIP), "Scholarship open in the first tier");
			menu.press(slot(Research.Topic.SCHOLARSHIP), player);
			helper.assertTrue(v.entity().research().currentTopic() == null, "chosen without the Library");
			// the first tier's rows are as they were
			helper.assertTrue(menu.icon(ResearchScreen.TOPIC_SLOTS[Research.Topic.RAMPARTS.ordinal()]).is(Items.STONE_BRICK_WALL), "Ramparts moved");
			// with it
			UUID id = library(helper, v.hall());
			menu = ResearchScreen.forTest(player, v.hall());
			header = lore(menu.icon(ResearchScreen.LIBRARY_HEADER));
			helper.assertTrue(header.contains("Open while"), "header with it: " + header);
			translated(helper, header);
			String scholarship = lore(menu.icon(slot(Research.Topic.SCHOLARSHIP)));
			helper.assertTrue(!scholarship.contains("Needs the Great Library") && scholarship.contains("24") && scholarship.contains("Click"), "Scholarship with it: " + scholarship);
			translated(helper, scholarship);
			menu.press(slot(Research.Topic.SWIFT_HANDS), player);
			helper.assertTrue(v.entity().research().currentTopic() == null, "Swift Hands IV before Swift Hands III");
			helper.assertTrue(lore(menu.icon(slot(Research.Topic.SWIFT_HANDS))).contains("First:"), "Swift Hands IV doesn't say it needs III");
			menu.press(slot(Research.Topic.SCHOLARSHIP), player);
			helper.assertTrue(v.entity().research().currentTopic() == Research.Topic.SCHOLARSHIP, "Scholarship not chosen: " + v.entity().research());
			chest.setItem(0, new ItemStack(Items.PAPER, 24));
			chest.setItem(1, new ItemStack(Items.BOOK, 6));
			chest.setItem(2, new ItemStack(Items.EMERALD, 9));
			helper.succeedWhen(() -> {
				helper.assertTrue(v.entity().research().level(Research.Topic.SCHOLARSHIP) == 1, "Scholarship: " + v.entity().research());
				helper.assertTrue(chest.countItem(Items.PAPER) == 0 && chest.countItem(Items.BOOK) == 0 && chest.countItem(Items.EMERALD) == 1,
					"paid: paper " + chest.countItem(Items.PAPER) + ", books " + chest.countItem(Items.BOOK) + ", emeralds " + chest.countItem(Items.EMERALD));
				// damaged: Scholarship II waits, and says so
				standing(v.level(), id, false);
				v.entity().setResearch(v.entity().research().choose(Research.Topic.SCHOLARSHIP).paidFor());
				new io.github.jcondedata.aliveworkplace.research.ScholarWork().work(v.level(), scholar, helper.absolutePos(DESK), true);
				helper.assertTrue(v.entity().research().progress() == 0, "researched with the Library damaged");
				helper.assertTrue(v.entity().research().level(Research.Topic.SCHOLARSHIP) == 1, "the level was lost");
				String waits = Component.translatable("message.aliveworkplace.scholar.state.library", "Scholarship").getString();
				translated(helper, waits);
				helper.assertTrue(waits.contains("Great Library"), "the scholar's status: " + waits);
				ChoiceMenu after = ResearchScreen.forTest(player, v.hall());
				String paused = lore(after.icon(slot(Research.Topic.SCHOLARSHIP)));
				helper.assertTrue(paused.contains("Needs the Great Library") && paused.contains("repaired"), "Scholarship with the Library damaged: " + paused);
				translated(helper, paused);
			});
		});
	}

	/**
	 * Swift Hands V: every job 25% faster with the Library, back to III's 15% while it's damaged (the levels kept); on top
	 * of Scholarship II and a big rush the pace stops at twice the usual.
	 */
	//$ gametest_ticks_batch AREA '100' '"librarySwiftHands"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "librarySwiftHands")
	public void swiftHandsFiveStaysUnderTheCap(GameTestHelper helper) {
		Village v = village(helper);
		Villager scholar = helper.spawn(EntityType.VILLAGER, new BlockPos(15, 2, 15));
		helper.runAfterDelay(2, () -> {
			scholar.setVillagerData(scholar.getVillagerData().setProfession(ModVillagers.SCHOLAR));
			levels(v, Map.of("swift_hands", 5, "scholarship", 2));
			helper.assertTrue(Math.abs(VillageNeeds.swiftHands(scholar) - 1f / 1.15f) < 1e-4, "Swift Hands without the Library: " + VillageNeeds.swiftHands(scholar));
			helper.assertTrue(Pace.of(scholar).faster().stream().noneMatch(p -> p.source().id().equals("scholarship")), "Scholarship without the Library");
			UUID id = library(helper, v.hall());
			levels(v, Map.of("swift_hands", 5, "scholarship", 2));
			helper.assertTrue(Math.abs(VillageNeeds.swiftHands(scholar) - 1f / 1.25f) < 1e-4, "Swift Hands V: " + VillageNeeds.swiftHands(scholar));
			float scholarship = Pace.of(scholar).faster().stream().filter(p -> p.source().id().equals("scholarship")).map(Pace.Part::factor).findFirst().orElse(1f);
			helper.assertTrue(Math.abs(scholarship - 1f / 1.5f) < 1e-4, "Scholarship II: " + scholarship);
			// every other bonus on top: a rush that alone would make work five times as quick
			UUID who = scholar.getUUID();
			Pace.register(new Pace.Source("test_library_rush", Pace.Kind.BONUS, x -> x.getUUID().equals(who) ? 0.2f : 1f));
			Leftovers.after(helper, () -> Pace.register(new Pace.Source("test_library_rush", Pace.Kind.BONUS, x -> 1f)));
			Pace.Breakdown pace = Pace.of(scholar);
			helper.assertTrue(pace.capped() && pace.factor() >= Pace.cap() - 1e-5 && Pace.cap() >= 0.5f - 1e-5,
				"over the cap: " + pace.factor() + " (bonuses " + pace.bonuses() + ")");
			helper.assertTrue(pace.percent() <= 100, "more than twice the pace: " + pace.percent() + "%");
			String line = Pace.describe(scholar).getString();
			helper.assertTrue(line.contains("Swift Hands") && line.contains("Scholarship") && line.contains("at the cap"), "the pace line: " + line);
			// damaged: paused, the levels kept
			standing(v.level(), id, false);
			helper.assertTrue(Math.abs(VillageNeeds.swiftHands(scholar) - 1f / 1.15f) < 1e-4, "Swift Hands with the Library damaged: " + VillageNeeds.swiftHands(scholar));
			helper.assertTrue(v.entity().research().level(Research.Topic.SWIFT_HANDS) == 5, "the levels went");
			helper.succeed();
		});
	}

	/** Scholarship II: a level costs a quarter less (Swift Hands I: 12 paper, 3 emeralds), only while the Library stands. */
	//$ gametest_ticks_batch AREA '100' '"libraryScholarship"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "libraryScholarship")
	public void scholarshipMakesResearchCheaper(GameTestHelper helper) {
		Village v = village(helper);
		helper.runAfterDelay(2, () -> {
			levels(v, Map.of("scholarship", 2));
			helper.assertTrue(Research.Topic.SWIFT_HANDS.cost(1, v.level(), v.entity()).equals(new Research.Cost(16, 0, 4)), "cheaper without the Library");
			UUID id = library(helper, v.hall());
			Research.Cost cost = Research.Topic.SWIFT_HANDS.cost(1, v.level(), v.entity());
			helper.assertTrue(cost.equals(new Research.Cost(12, 0, 3)), "Scholarship II: " + cost);
			helper.assertTrue(Research.Topic.PHARMACY.cost(1, v.level(), v.entity()).equals(new Research.Cost(18, 5, 6)), "a second-tier level with Scholarship II");
			levels(v, Map.of("scholarship", 1));
			helper.assertTrue(Research.Topic.SWIFT_HANDS.cost(1, v.level(), v.entity()).equals(new Research.Cost(16, 0, 4)), "Scholarship I already cheaper");
			levels(v, Map.of("scholarship", 2));
			standing(v.level(), id, false);
			helper.assertTrue(Research.Topic.SWIFT_HANDS.cost(1, v.level(), v.entity()).equals(new Research.Cost(16, 0, 4)), "cheaper with the Library damaged");
			helper.succeed();
		});
	}

	/**
	 * Engineering II: a builder's bag takes 45 stacks instead of 27, and the Library's crew 16 builders instead of 12;
	 * damaged, new stacks keep to 27 again but nothing already in the bag is lost.
	 */
	//$ gametest_ticks_batch AREA '100' '"libraryEngineering"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "libraryEngineering")
	public void engineeringWidensBagsAndCrews(GameTestHelper helper) {
		Village v = village(helper);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(15, 2, 15));
		helper.runAfterDelay(2, () -> {
			levels(v, Map.of("engineering", 2));
			helper.assertTrue(BuilderWork.engineeringSlots(builder) == 27, "bag without the Library: " + BuilderWork.engineeringSlots(builder));
			UUID id = library(helper, v.hall());
			WonderSite site = WonderManager.get(v.level()).site(id).orElseThrow();
			helper.assertTrue(WonderCrews.limit(v.level(), site) == WonderCrews.limit(site) + 4, "the crew: " + WonderCrews.limit(v.level(), site) + " of " + WonderCrews.limit(site));
			levels(v, Map.of("engineering", 1));
			helper.assertTrue(BuilderWork.engineeringSlots(builder) == 36, "Engineering I: " + BuilderWork.engineeringSlots(builder));
			levels(v, Map.of("engineering", 2));
			int slots = BuilderWork.engineeringSlots(builder);
			helper.assertTrue(slots == 45, "Engineering II: " + slots);
			BuilderBag bag = new BuilderBag();
			bag.setSlots(slots);
			List<Item> kinds = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				if (item != Items.AIR && item.getDefaultMaxStackSize() == 64 && kinds.size() < 45) {
					kinds.add(item);
				}
			}
			for (Item item : kinds) {
				helper.assertTrue(bag.add(new ItemStack(item, 64)).isEmpty(), "no room for " + item + " in a bag of 45");
			}
			helper.assertTrue(bag.stacks().size() == 45 && bag.freeSlots() == 0, "the bag holds " + bag.stacks().size());
			// the stacks come back from a save, all 45
			BuilderBag loaded = BuilderBag.CODEC.parse(NbtOps.INSTANCE, BuilderBag.CODEC.encodeStart(NbtOps.INSTANCE, bag).getOrThrow()).getOrThrow();
			helper.assertTrue(loaded.stacks().size() == 45, "loaded " + loaded.stacks().size() + " stacks");
			standing(v.level(), id, false);
			helper.assertTrue(BuilderWork.engineeringSlots(builder) == 27, "bag with the Library damaged");
			helper.assertTrue(WonderCrews.limit(v.level(), site) == WonderCrews.limit(site), "crew with the Library damaged");
			bag.setSlots(BuilderWork.engineeringSlots(builder));
			bag.remove(kinds.get(0), 64);
			helper.assertTrue(bag.stacks().size() == 44 && bag.freeSlots() == 1 && bag.spaceFor(Items.DIAMOND) == 64, "free room in the first 27: " + bag.freeSlots());
			bag.addAll(Items.DIAMOND, 64);
			helper.assertTrue(bag.freeSlots() == 0 && bag.spaceFor(Items.EMERALD) == 0 && bag.add(new ItemStack(Items.EMERALD, 1)).getCount() == 1,
				"a 27-stack bag took more");
			helper.succeed();
		});
	}

	/** Cartography: explorers search twice as far, and their maps can lead to trial chambers and ancient cities. */
	//$ gametest_ticks_batch AREA '100' '"libraryCartography"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "libraryCartography")
	public void cartographySendsExplorersFurther(GameTestHelper helper) {
		Village v = village(helper);
		Villager explorer = helper.spawn(EntityType.VILLAGER, new BlockPos(15, 2, 15));
		helper.runAfterDelay(2, () -> {
			levels(v, Map.of("cartography", 1));
			double plain = ExplorerWork.range(explorer);
			helper.assertTrue(plain == ExplorerWork.RANGE, "range without the Library: " + plain);
			UUID id = library(helper, v.hall());
			helper.assertTrue(ExplorerWork.range(explorer) == 2 * ExplorerWork.RANGE, "range with Cartography: " + ExplorerWork.range(explorer));
			HolderSet<Structure> deep = Explorers.mapPlaces(v.level(), true);
			HolderSet<Structure> usual = Explorers.mapPlaces(v.level(), false);
			List<String> deepIds = deep.stream().map(h -> h.unwrapKey().map(k -> k.location().toString()).orElse("")).toList();
			List<String> usualIds = usual.stream().map(h -> h.unwrapKey().map(k -> k.location().toString()).orElse("")).toList();
			helper.assertTrue(deepIds.contains("minecraft:trial_chambers") && deepIds.contains("minecraft:ancient_city") && deepIds.containsAll(usualIds),
				"Cartography's maps: " + deepIds);
			helper.assertTrue(!usualIds.contains("minecraft:trial_chambers") && !usualIds.contains("minecraft:ancient_city") && !usualIds.isEmpty(),
				"the usual maps: " + usualIds);
			helper.assertTrue(Explorers.placeName(deep.stream().filter(h -> h.is(ResourceLocation.withDefaultNamespace("ancient_city"))).findFirst().orElseThrow())
				.getString().equals("Ancient City"), "the map's name");
			standing(v.level(), id, false);
			helper.assertTrue(ExplorerWork.range(explorer) == ExplorerWork.RANGE, "range with the Library damaged");
			helper.succeed();
		});
	}

	/** Trade Law II: caravans carry 8 stacks instead of 4, and a day's takings are a fifth more; damaged, as before. */
	//$ gametest_ticks_batch AREA '100' '"libraryTradeLaw"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "libraryTradeLaw")
	public void tradeLawLoadsCaravansAndTheTreasury(GameTestHelper helper) {
		Village v = village(helper);
		helper.runAfterDelay(2, () -> {
			levels(v, Map.of("trade_law", 2));
			helper.assertTrue(Caravans.cargo(v.level(), v.hall()) == Caravans.CARGO_STACKS, "cargo without the Library");
			long plain = takings(v);
			UUID id = library(helper, v.hall());
			helper.assertTrue(Caravans.cargo(v.level(), v.hall()) == Caravans.CARGO_STACKS + 4, "Trade Law II's cargo: " + Caravans.cargo(v.level(), v.hall()));
			long more = takings(v);
			helper.assertTrue(plain > 0 && more == plain * 12 / 10, "takings " + plain + " -> " + more);
			helper.assertTrue(Treasury.tradeLaw(1000, 1) == 1100 && Treasury.tradeLaw(1000, 0) == 1000, "10% a level");
			standing(v.level(), id, false);
			helper.assertTrue(Caravans.cargo(v.level(), v.hall()) == Caravans.CARGO_STACKS && takings(v) == plain, "Trade Law with the Library damaged");
			helper.succeed();
		});
	}

	/** One day's takings into an empty treasury, from 10 workers. */
	private static long takings(Village v) {
		v.entity().setTreasury(0);
		v.entity().setLastTaxDay(Chronicle.day(v.level()) - 1);
		Treasury.round(v.level(), v.hall(), v.entity(), 10f);
		return v.entity().treasury();
	}

	/**
	 * Pharmacy: the ill are well after a day instead of three, and a Nurse with no remedy cures one villager a day (the
	 * next waits for tomorrow, or a remedy); damaged, neither.
	 */
	//$ gametest_ticks_batch AREA '100' '"libraryPharmacy"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "libraryPharmacy")
	public void pharmacyHealsInADay(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(DESK, ModBlocks.NURSE_STATION);
		Villager nurse = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 9));
		Villager mira = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		Villager tom = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 12));
		boolean sick = Sickness.ENABLED;
		Sickness.ENABLED = false;
		Leftovers.after(helper, () -> Sickness.ENABLED = sick);
		helper.runAfterDelay(2, () -> {
			Jobs.employ(v.level(), nurse, helper.absolutePos(DESK), ModVillagers.NURSE_STATION_POI, ModVillagers.NURSE);
			levels(v, Map.of("pharmacy", 1));
			Sickness.fallIll(v.level(), mira);
			helper.assertTrue(Sickness.isIll(mira) && Sickness.recovery(mira) == Sickness.RECOVERY, "three days without the Library: " + Sickness.recovery(mira));
			NurseWork.cure(v.level(), nurse);
			helper.assertTrue(Sickness.isIll(mira), "cured without a remedy or the Library");
			UUID id = library(helper, v.hall());
			helper.assertTrue(Sickness.recovery(mira) == VillageNeeds.DAY, "a day with Pharmacy: " + Sickness.recovery(mira));
			Sickness.fallIll(v.level(), tom);
			Component first = NurseWork.cure(v.level(), nurse);
			helper.assertTrue(Sickness.isIll(mira) != Sickness.isIll(tom), "one cured without a remedy: Mira " + Sickness.isIll(mira) + ", Tom " + Sickness.isIll(tom));
			translated(helper, first.getString());
			Component second = NurseWork.cure(v.level(), nurse);
			helper.assertTrue(Sickness.isIll(mira) || Sickness.isIll(tom), "two cured the same day without a remedy");
			helper.assertTrue(second.getString().contains("honey"), "the second: " + second.getString());
			// the next day she cures the other
			ModAttachments.PHARMACY_CURE_DAY.set(nurse, Chronicle.day(v.level()) - 1);
			NurseWork.cure(v.level(), nurse);
			helper.assertTrue(!Sickness.isIll(mira) && !Sickness.isIll(tom), "not cured the next day");
			// damaged: three days again, and remedies needed
			standing(v.level(), id, false);
			Sickness.fallIll(v.level(), mira);
			ModAttachments.PHARMACY_CURE_DAY.set(nurse, Chronicle.day(v.level()) - 1);
			helper.assertTrue(Sickness.recovery(mira) == Sickness.RECOVERY, "a day with the Library damaged");
			NurseWork.cure(v.level(), nurse);
			helper.assertTrue(Sickness.isIll(mira), "cured without a remedy with the Library damaged");
			Sickness.recover(v.level(), mira);
			helper.succeed();
		});
	}

	/**
	 * An existing village's research, saved before the second tier, loads unchanged: the same levels, topic and progress,
	 * every new topic at 0 and none of them open without the Library; and a Library level saved stays saved.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void oldResearchLoadsUnchanged(GameTestHelper helper) {
		Map<String, Integer> old = new HashMap<>();
		for (Research.Topic t : Research.Topic.values()) {
			if (t.inFirstTier()) {
				old.put(t.key(), t.maxLevel);
			}
		}
		old.put("@ancient_lore/old_harvests", 40); // a Legend's tree's work in progress (29.11)
		old.put("ancient_lore/old_harvests", 1);
		Research.State saved = new Research.State(Map.copyOf(old), Optional.of("drill"), 1234, true);
		Tag tag = Research.State.CODEC.encodeStart(NbtOps.INSTANCE, saved).getOrThrow();
		Research.State loaded = Research.State.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(loaded.equals(saved), "loaded " + loaded + " for " + saved);
		helper.assertTrue(loaded.currentTopic() == Research.Topic.DRILL && loaded.progress() == 1234 && loaded.paid(), "the work in hand");
		helper.assertTrue(loaded.totalLevels() == saved.totalLevels(), "the levels' count");
		for (Research.Topic t : Research.Topic.values()) {
			helper.assertTrue(loaded.bonus(t, false) == (t.inFirstTier() ? t.maxLevel : 0), t + "'s bonus: " + loaded.bonus(t, false));
			if (t.inFirstTier()) {
				helper.assertTrue(t.cost(1).equals(new Research.Cost(16, t == Research.Topic.ARCHITECTURE || t == Research.Topic.LORE ? 4 : 0, 4))
					&& t.points(1) == Research.POINTS, t + "'s first level costs something else now");
			}
			helper.assertFalse(loaded.available(t), t + " open without the Library");
		}
		helper.assertTrue(Research.State.CODEC.parse(NbtOps.INSTANCE, new net.minecraft.nbt.CompoundTag()).getOrThrow().equals(Research.State.EMPTY), "a hall with no research");
		// a Library level saved stays, and loads with its bonus paused until the Library stands
		Research.State library = Research.State.CODEC.parse(NbtOps.INSTANCE, Research.State.CODEC.encodeStart(NbtOps.INSTANCE,
			new Research.State(Map.of("swift_hands", 5, "pharmacy", 1), Optional.empty(), 0, false)).getOrThrow()).getOrThrow();
		helper.assertTrue(library.level(Research.Topic.SWIFT_HANDS) == 5 && library.bonus(Research.Topic.SWIFT_HANDS, false) == 3
			&& library.bonus(Research.Topic.SWIFT_HANDS, true) == 5 && library.bonus(Research.Topic.PHARMACY, false) == 0
			&& library.bonus(Research.Topic.PHARMACY, true) == 1, "the Library's levels: " + library);
		// an old builder's bag of 27 stacks comes back as it was
		BuilderBag bag = new BuilderBag();
		for (int i = 0; i < 27; i++) {
			bag.add(new ItemStack(i % 2 == 0 ? Items.COBBLESTONE : Items.OAK_PLANKS, 64));
		}
		BuilderBag back = BuilderBag.CODEC.parse(NbtOps.INSTANCE, BuilderBag.CODEC.encodeStart(NbtOps.INSTANCE, bag).getOrThrow()).getOrThrow();
		helper.assertTrue(back.stacks().size() == 27 && back.slots() == BuilderBag.SLOTS && back.freeSlots() == 0 && back.spaceFor(Items.STONE) == 0,
			"an old bag: " + back.stacks().size() + " stacks, " + back.freeSlots() + " free");
		helper.succeed();
	}
}
