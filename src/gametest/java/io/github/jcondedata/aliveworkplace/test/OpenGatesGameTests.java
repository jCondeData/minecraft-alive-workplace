package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/**
 * Open Gates and its reform (ROADMAP 30.7): the effects {@code inn}, {@code market_traders}, {@code legend_visits} and
 * {@code bandit_camps}, each number with and without The Watchful Gate: the inn's guests (2, then 4) and arrivals a
 * morning (1, then 2), the market's traders (one more), the bandits' daily chance (12%, then 24%, reformed 12% again),
 * and Open Gates refused while Curfew is in force (both ways). Nothing here is random but the travellers' looks.
 */
public class OpenGatesGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos COUNTER = new BlockPos(6, 2, 6);
	private static final ResourceLocation OPEN_GATES = AliveWorkplace.id("open_gates");
	private static final ResourceLocation CURFEW = AliveWorkplace.id("curfew");

	/** It loads with its text, icon, boost, cost, Curfew excluded and a three-step reform that takes the cost away. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void openGatesLoadsWithItsReform(GameTestHelper helper) {
		Edicts.Edict gates = Edicts.get(OPEN_GATES).orElse(null);
		helper.assertTrue(gates != null, "Open Gates didn't load: " + Edicts.all());
		helper.assertTrue(gates.name().getString().equals("Open Gates") && gates.icon().equals(ResourceLocation.withDefaultNamespace("oak_fence_gate")),
			"Open Gates: " + gates.name().getString() + ", " + gates.icon());
		helper.assertTrue(gates.description().getString().equals("Inns take 4 guests and up to two travellers arrive a morning; one more trader comes"
			+ " on market days; bandits make camp near the village twice as often."), "description: " + gates.description().getString());
		helper.assertTrue(gates.boost().size() == 3
			&& gates.boost().get(0) instanceof CivicEffects.Inn inn && inn.guests().orElse(0) == 4 && inn.arrivals().orElse(0) == 2
			&& gates.boost().get(1) instanceof CivicEffects.MarketTraders m && m.extra() == 1
			&& gates.boost().get(2) instanceof CivicEffects.LegendVisits l && l.factor() == 2f, "boost: " + gates.boost());
		helper.assertTrue(gates.cost().size() == 1 && gates.cost().get(0) instanceof CivicEffects.BanditCampChance b && b.factor() == 2f, "cost: " + gates.cost());
		helper.assertTrue(gates.excludes().equals(List.of(CURFEW)), "excludes: " + gates.excludes());
		Reforms.Reform watch = gates.reform().orElse(null);
		helper.assertTrue(watch != null && watch.name().getString().equals("The Watchful Gate") && watch.effects().isEmpty(), "reform: " + watch);
		helper.assertTrue(watch.steps().size() == 3, "steps: " + watch.steps());
		Reforms.Step iron = watch.steps().get(0);
		Reforms.Step arrows = watch.steps().get(1);
		Reforms.Step monsters = watch.steps().get(2);
		helper.assertTrue(iron.kind() == VillageQuests.Kind.BRING && iron.item().equals("minecraft:iron_ingot") && iron.count() == 192 && iron.reward() == 5,
			"step 1: " + iron);
		helper.assertTrue(arrows.kind() == VillageQuests.Kind.BRING && arrows.item().equals("minecraft:arrow") && arrows.count() == 256 && arrows.reward() == 4,
			"step 2: " + arrows);
		helper.assertTrue(monsters.kind() == VillageQuests.Kind.SLAY && monsters.count() == 40 && monsters.reward() == 6, "step 3: " + monsters);

		// Summed: guests and arrivals the highest named, traders add, factors multiply; none: the usual.
		CivicEffects.Sum sum = new CivicEffects.Sum(List.of(
			new CivicEffects.Active(new CivicEffects.Inn(Optional.of(4), Optional.empty(), List.of()), Component.empty()),
			new CivicEffects.Active(new CivicEffects.Inn(Optional.of(3), Optional.of(2), List.of()), Component.empty()),
			new CivicEffects.Active(new CivicEffects.MarketTraders(1, List.of()), Component.empty()),
			new CivicEffects.Active(new CivicEffects.MarketTraders(2, List.of()), Component.empty()),
			new CivicEffects.Active(new CivicEffects.LegendVisits(2f, List.of()), Component.empty()),
			new CivicEffects.Active(new CivicEffects.BanditCampChance(2f, List.of()), Component.empty()),
			new CivicEffects.Active(new CivicEffects.BanditCampChance(1.5f, List.of()), Component.empty())));
		helper.assertTrue(sum.innGuests(2) == 4 && sum.innArrivals() == 2 && sum.marketTraders() == 3 && sum.legendVisits() == 2f
			&& Math.abs(sum.banditCamps() - 3f) < 1e-6, "summed: " + sum.innGuests(2) + ", " + sum.innArrivals() + ", " + sum.marketTraders()
			+ ", " + sum.legendVisits() + ", " + sum.banditCamps());
		CivicEffects.Sum none = CivicEffects.Sum.EMPTY;
		helper.assertTrue(none.innGuests(2) == 2 && none.innArrivals() == 1 && none.marketTraders() == 0 && none.legendVisits() == 1f
			&& none.banditCamps() == 1f, "no edicts: the usual");
		helper.succeed();
	}

	/**
	 * The inn: without the edict one traveller a morning and two at most; under Open Gates two arrive in one morning, two
	 * more the next, and the inn is full at four. Reformed: still four and two a morning. Edicts off: two and one. An
	 * innkeeper saved before 30.7 who took a traveller today counts as having taken one.
	 */
	//$ gametest_ticks_batch AREA '100' '"openGatesInn"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "openGatesInn")
	public void openGatesInnsTakeFourGuestsTwoAMorning(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(COUNTER, ModBlocks.INN_COUNTER);
		for (int x : new int[] {2, 4, 6, 8, 10, 12}) {
			helper.setBlock(new BlockPos(x, 2, 17), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
			helper.setBlock(new BlockPos(x, 2, 18), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		}
		// The inn counts guests within the test area only: travellers of other tests' inns stay out of the count.
		int radius = Innkeepers.RADIUS;
		Innkeepers.RADIUS = 14;
		Leftovers.after(helper, () -> Innkeepers.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			Leftovers.clear(helper);
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos counter = helper.absolutePos(COUNTER);
			Villager innkeeper = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 7));
			Jobs.employ(level, innkeeper, counter, ModVillagers.INN_COUNTER_POI, ModVillagers.INNKEEPER);
			innkeeper.setNoAi(true);
			VillageHallBlockEntity entity = ready(helper);
			long morning = (level.getDayTime() / VillageNeeds.DAY + 1) * VillageNeeds.DAY + 1000;
			level.setDayTime(morning);

			// No edict: one a morning, two at most.
			helper.assertTrue(Innkeepers.maxGuests(innkeeper) == 2 && Innkeepers.arrivalsPerMorning(innkeeper) == 1, "usual: " + numbers(innkeeper));
			tend(helper, level, innkeeper, counter, 1);
			tend(helper, level, innkeeper, counter, 1);
			helper.assertTrue(ModAttachments.GUESTS_TODAY.getOrElse(innkeeper, 0) == 1, "counted today: " + ModAttachments.GUESTS_TODAY.getOrElse(innkeeper, 0));
			// Saved before 30.7: a traveller today and no count means one.
			ModAttachments.GUESTS_TODAY.remove(innkeeper);
			helper.assertTrue(Innkeepers.arrivedOn(innkeeper, morning / VillageNeeds.DAY) == 1, "an old save's count");

			// Open Gates: a second the same morning, two more the next, full at four.
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(OPEN_GATES).orElseThrow()).done(), "proclaimed");
			helper.assertTrue(Innkeepers.maxGuests(innkeeper) == 4 && Innkeepers.arrivalsPerMorning(innkeeper) == 2, "Open Gates: " + numbers(innkeeper));
			tend(helper, level, innkeeper, counter, 2);
			tend(helper, level, innkeeper, counter, 2);
			level.setDayTime(morning + VillageNeeds.DAY);
			tend(helper, level, innkeeper, counter, 3);
			tend(helper, level, innkeeper, counter, 4);
			level.setDayTime(morning + 2 * VillageNeeds.DAY);
			helper.assertTrue(Innkeepers.tend(level, innkeeper, counter).equals("full"), "a fifth guest");
			helper.assertTrue(Innkeepers.guests(level, counter).size() == 4, "guests: " + Innkeepers.guests(level, counter).size());
			helper.assertTrue(ModAttachments.GUESTS_HOSTED.getOrElse(innkeeper, 0) == 4, "hosted: " + ModAttachments.GUESTS_HOSTED.getOrElse(innkeeper, 0));
			// In the afternoon nobody arrives, room or not.
			Innkeepers.guests(level, counter).get(0).discard();
			level.setDayTime(morning + 2 * VillageNeeds.DAY + Innkeepers.MORNING);
			tend(helper, level, innkeeper, counter, 3);

			// Reformed (The Watchful Gate): the travellers keep coming.
			entity.setReforms(List.of(new Reforms.Progress(OPEN_GATES.toString(), 3, true, 0)));
			helper.assertTrue(Innkeepers.maxGuests(innkeeper) == 4 && Innkeepers.arrivalsPerMorning(innkeeper) == 2, "reformed: " + numbers(innkeeper));
			level.setDayTime(morning + 3 * VillageNeeds.DAY);
			tend(helper, level, innkeeper, counter, 4);

			// Edicts off: the usual.
			try {
				Edicts.setEnabled(false);
				helper.assertTrue(Innkeepers.maxGuests(innkeeper) == 2 && Innkeepers.arrivalsPerMorning(innkeeper) == 1, "off: " + numbers(innkeeper));
			} finally {
				Edicts.setEnabled(true);
			}
			helper.succeed();
		});
	}

	/** Runs the inn's round and checks the guests staying after it (each held still where they came in). */
	private static void tend(GameTestHelper helper, ServerLevel level, Villager innkeeper, BlockPos counter, int want) {
		String state = Innkeepers.tend(level, innkeeper, counter);
		List<Villager> guests = Innkeepers.guests(level, counter);
		guests.forEach(g -> g.setNoAi(true));
		helper.assertTrue(guests.size() == want, guests.size() + " guests, not " + want + " (" + state + ", day time " + level.getDayTime() % VillageNeeds.DAY
			+ ", " + numbers(innkeeper) + "): " + guests.stream().map(g -> g.blockPosition().toShortString() + " arrived "
				+ Innkeepers.traveller(g).arrived() + " now " + level.getGameTime()).toList());
	}

	private static String numbers(Villager innkeeper) {
		return Innkeepers.maxGuests(innkeeper) + " guests at most, " + Innkeepers.arrivalsPerMorning(innkeeper) + " a morning";
	}

	/** Market day: two traders for a Village, three under Open Gates (and reformed), and three come to the square. */
	//$ gametest_ticks_batch AREA '100' '"openGatesMarket"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "openGatesMarket")
	public void openGatesBringsOneMoreMarketTrader(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> helper.getLevel().getEntitiesOfClass(WanderingTrader.class, helper.getBounds().inflate(16)).forEach(t -> t.discard()));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			int usual = MarketDays.traders(level, hall);
			helper.assertTrue(usual == 2, "a Village's market: " + usual);
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(OPEN_GATES).orElseThrow()).done(), "proclaimed");
			helper.assertTrue(MarketDays.traders(level, hall) == 3, "Open Gates: " + MarketDays.traders(level, hall));
			List<WanderingTrader> came = MarketDays.hold(level, hall, helper.absolutePos(new BlockPos(6, 2, 6)));
			AABB around = helper.getBounds();
			helper.assertTrue(came.size() == 3 && level.getEntitiesOfClass(WanderingTrader.class, around).size() == 3,
				"traders at the square: " + came.size() + ", " + level.getEntitiesOfClass(WanderingTrader.class, around).size());
			entity.setReforms(List.of(new Reforms.Progress(OPEN_GATES.toString(), 3, true, 0)));
			helper.assertTrue(MarketDays.traders(level, hall) == 3, "reformed: " + MarketDays.traders(level, hall));
			try {
				Edicts.setEnabled(false);
				helper.assertTrue(MarketDays.traders(level, hall) == 2, "off: " + MarketDays.traders(level, hall));
			} finally {
				Edicts.setEnabled(true);
			}
			helper.succeed();
		});
	}

	/** Bandits make camp with 12% a day; 24% under Open Gates; 12% again once The Watchful Gate reforms it. */
	//$ gametest_ticks_batch AREA '100' '"openGatesBandits"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "openGatesBandits")
	public void openGatesDoublesTheBanditsChanceUntilReformed(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			float usual = BanditCamps.dailyChance(level, hall);
			helper.assertTrue(Math.abs(usual - BanditCamps.DAILY_CHANCE) < 1e-6 && Math.abs(usual - 0.12f) < 1e-6, "usual: " + usual);
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(OPEN_GATES).orElseThrow()).done(), "proclaimed");
			float open = BanditCamps.dailyChance(level, hall);
			helper.assertTrue(Math.abs(open - 0.24f) < 1e-6, "Open Gates: " + open);
			entity.setReforms(List.of(new Reforms.Progress(OPEN_GATES.toString(), 3, true, 0)));
			float reformed = BanditCamps.dailyChance(level, hall);
			helper.assertTrue(Math.abs(reformed - 0.12f) < 1e-6, "reformed: " + reformed);
			entity.setReforms(List.of());
			try {
				Edicts.setEnabled(false);
				helper.assertTrue(Math.abs(BanditCamps.dailyChance(level, hall) - 0.12f) < 1e-6, "off: " + BanditCamps.dailyChance(level, hall));
			} finally {
				Edicts.setEnabled(true);
			}
			helper.succeed();
		});
	}

	/**
	 * Open Gates and Curfew (30.9) exclude each other: while Curfew is in force Open Gates is refused, and the other way
	 * round, with a message naming both.
	 */
	//$ gametest_ticks_batch AREA '100' '"openGatesCurfew"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "openGatesCurfew")
	public void openGatesIsRefusedWhileCurfewIsInForce(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			Edicts.Edict curfew = Edicts.get(CURFEW).orElseThrow();
			Edicts.Edict gates = Edicts.get(OPEN_GATES).orElseThrow();
			String c = curfew.name().getString();
			helper.assertTrue(Edicts.proclaim(level, hall, null, curfew).done(), "Curfew proclaimed");
			Edicts.Result refused = Edicts.proclaim(level, hall, null, gates);
			helper.assertTrue(!refused.done() && refused.message().getString().equals("Open Gates can't be in force alongside " + c + "."),
				"Open Gates under Curfew: " + refused.message().getString());
			helper.assertTrue(entity.edicts().size() == 1, "in force: " + entity.edicts());

			entity.setEdicts(List.of());
			helper.assertTrue(Edicts.proclaim(level, hall, null, gates).done(), "Open Gates proclaimed");
			Edicts.Result other = Edicts.proclaim(level, hall, null, curfew);
			helper.assertTrue(!other.done() && other.message().getString().equals(c + " can't be in force alongside Open Gates."),
				"Curfew under Open Gates: " + other.message().getString());
			helper.assertTrue(entity.edicts().size() == 1, "in force: " + entity.edicts());
			helper.succeed();
		});
	}

	/**
	 * The Book of Edicts tells Open Gates in words: the inn's numbers, the extra trader and the bandits, no promise of
	 * Legends while nothing sends them (and the line once something does); reformed, the bandits' line goes.
	 */
	//$ gametest_ticks_batch AREA '100' '"openGatesBook"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "openGatesBook")
	public void theBookTellsOpenGates(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		boolean read = CivicEffects.LEGEND_VISITS_READ;
		Leftovers.after(helper, () -> {
			helper.getLevel().getServer().getPlayerList().remove(owner);
			CivicEffects.LEGEND_VISITS_READ = read;
		});
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			owner.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);

			CivicEffects.LEGEND_VISITS_READ = false;
			List<String> lore = bookLore(owner, hall);
			for (String want : List.of("+ inns take 4 guests (not 2), up to 2 travellers arrive a morning (not 1)",
				"+ 1 more trader comes on market days", "- bandits make camp near the village twice as often",
				"Reform: The Watchful Gate, step 1 of 3",
				"A gate watch that knows every face: travellers keep coming, and bandits are no likelier to make camp than usual.")) {
				helper.assertTrue(lore.contains(want), "Open Gates lacks \"" + want + "\": " + lore);
			}
			helper.assertTrue(lore.stream().noneMatch(l -> l.contains("Legend")), "Legends promised before they visit: " + lore);
			CivicEffects.LEGEND_VISITS_READ = true;
			helper.assertTrue(bookLore(owner, hall).contains("+ Legends visit the inn twice as often"), "the Legends' line: " + bookLore(owner, hall));
			CivicEffects.LEGEND_VISITS_READ = false;

			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(OPEN_GATES).orElseThrow()).done(), "proclaimed");
			List<VillageQuests.Quest> shown = Reforms.shown(entity);
			helper.assertTrue(shown.size() == 1 && shown.get(0).item().equals("minecraft:iron_ingot") && shown.get(0).count() == 192,
				"the Watchful Gate's first step: " + shown);
			entity.setReforms(List.of(new Reforms.Progress(OPEN_GATES.toString(), 3, true, 0)));
			List<String> reformed = bookLore(owner, hall);
			helper.assertTrue(reformed.contains("+ 1 more trader comes on market days") && reformed.contains("Reformed: The Watchful Gate")
				&& reformed.stream().noneMatch(l -> l.startsWith("- ")), "reformed: " + reformed);
			helper.succeed();
		});
	}

	private static List<String> bookLore(ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.clicked(VillageHallScreen.BOOK, 0, ClickType.PICKUP, player);
		for (int s = EdictBook.FIRST_EDICT; s < EdictBook.END_EDICTS; s++) {
			ItemStack icon = menu.icon(s);
			if (icon.getHoverName().getString().equals("Open Gates")) {
				ItemLore lore = icon.get(DataComponents.LORE);
				return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
			}
		}
		return List.of("(Open Gates isn't in the Book)");
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	/** A village of radius 16; every cache and the edicts reset after the test. */
	private static void village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			CivicEffects.forget();
			Moods.forget();
		});
	}

	/** The hall after its first round: a Hamlet with nothing in force and no reform, every cache asked again. */
	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setRank(VillageRanks.Rank.HAMLET);
		hall.setEdicts(List.of());
		hall.setReforms(List.of());
		VillageNeeds.forget();
		CivicEffects.forget();
		Moods.forget();
		return hall;
	}
}
