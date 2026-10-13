package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Decorations;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.legend.Founder;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wonder.ColossusPlaque;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.wonder.powers.TravellersPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.WonderPowers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * The Colossus's powers (ROADMAP 35.22): a villager at home is 12 happier and one in a caravan-linked village 8, with
 * "the Colossus" among their reasons; two travellers come to its village's inn in a morning with beds free, and twice
 * as many of them are Experts; 8 beauty; the plaque's signs read the Founder (the hall's owner without one), the
 * village's name and its founding day from the chronicle; a damaged Colossus turns the powers off, Wonders off too.
 */
public class ColossusPowerGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final ResourceLocation COLOSSUS = AliveWorkplace.id("colossus");

	private static WonderSite colossus(GameTestHelper helper, BlockPos hall) {
		WonderManager manager = WonderManager.get(helper.getLevel());
		WonderSite site = manager.begin(COLOSSUS, hall, UUID.randomUUID(), "Jesse", 5);
		manager.finish(site.id(), 12);
		Wonders.forgetPowers();
		Leftovers.after(helper, () -> {
			manager.remove(site.id());
			Wonders.forgetPowers();
			Moods.forget();
		});
		return manager.site(site.id()).orElseThrow();
	}

	private static void damage(ServerLevel level, WonderSite site, boolean damaged) {
		WonderManager.get(level).setKept(site.id(), new WonderSite.Kept(damaged ? 70 : 100, 13, damaged, 12));
	}

	private static List<String> reasons(Moods.Mood mood) {
		return mood.good().stream().map(Component::getString).toList();
	}

	/**
	 * The realm happier: with the Colossus standing, a villager at home is 12 happier ("proud of the Colossus") and one in
	 * the caravan partner's village 8 ("the realm's Colossus"); a village off the routes isn't reached; damaged, both are
	 * back where they were and it adds no beauty; repaired, back on; with Wonders off, off.
	 */
	//$ gametest_ticks_batch HUGE '100' '"colossusMood"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "colossusMood")
	public void theColossusMakesItsRealmHappier(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 10;
		BlockPos homeRel = new BlockPos(5, 2, 5);
		BlockPos partnerRel = new BlockPos(24, 2, 24);
		helper.setBlock(homeRel, ModBlocks.VILLAGE_HALL);
		helper.setBlock(partnerRel, ModBlocks.VILLAGE_HALL);
		BlockPos home = helper.absolutePos(homeRel);
		BlockPos partner = helper.absolutePos(partnerRel);
		Caravans.Data caravans = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			if (caravans.routesFrom(home).contains(partner)) {
				caravans.toggleRoute(home, partner, 10);
			}
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			Moods.forget();
		});
		Villager here = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 8));
		Villager there = helper.spawn(EntityType.VILLAGER, new BlockPos(22, 2, 21));
		here.setNoAi(true);
		there.setNoAi(true);
		helper.assertTrue(VillageHalls.nearest(level, here.blockPosition()).equals(java.util.Optional.of(home))
			&& VillageHalls.nearest(level, there.blockPosition()).equals(java.util.Optional.of(partner)), "each villager in its own village");
		int hereBefore = Moods.work(level, here).score();
		int thereBefore = Moods.work(level, there).score();
		int beautyBefore = Decorations.beauty(level, home);
		helper.assertTrue(hereBefore + 12 <= 100 && thereBefore + 8 <= 100, "moods too high to measure: " + hereBefore + ", " + thereBefore);

		WonderSite site = colossus(helper, home);
		// no route yet: only its own village
		helper.assertTrue(Moods.work(level, here).score() == hereBefore + 12, "at home: " + hereBefore + " → " + Moods.work(level, here).score());
		helper.assertTrue(Moods.work(level, there).score() == thereBefore, "a village off its routes was reached: " + reasons(Moods.work(level, there)));
		helper.assertTrue(reasons(Moods.work(level, here)).contains("proud of the Colossus"), "the reasons at home: " + reasons(Moods.work(level, here)));
		helper.assertTrue(Decorations.beauty(level, home) == beautyBefore + 8, "its 8 beauty: " + beautyBefore + " → " + Decorations.beauty(level, home));
		// a caravan route: the partner's villager 8 happier
		helper.assertTrue(caravans.toggleRoute(home, partner, 10), "no caravan route");
		Moods.Mood away = Moods.work(level, there);
		helper.assertTrue(away.score() == thereBefore + 8, "in the linked village: " + thereBefore + " → " + away.score() + " " + reasons(away));
		helper.assertTrue(reasons(away).contains("the realm's Colossus"), "the reasons in the linked village: " + reasons(away));
		helper.assertTrue(Moods.work(level, here).score() == hereBefore + 12, "at home with the route: " + Moods.work(level, here).score());

		// damaged: off
		damage(level, site, true);
		helper.assertTrue(Moods.work(level, here).score() == hereBefore && Moods.work(level, there).score() == thereBefore,
			"a damaged Colossus: " + Moods.work(level, here).score() + ", " + Moods.work(level, there).score());
		helper.assertTrue(reasons(Moods.work(level, here)).stream().noneMatch(r -> r.contains("Colossus"))
			&& reasons(Moods.work(level, there)).stream().noneMatch(r -> r.contains("Colossus")), "still a reason while damaged");
		helper.assertTrue(Decorations.beauty(level, home) == beautyBefore, "a damaged Colossus's beauty");
		damage(level, site, false);
		helper.assertTrue(Moods.work(level, here).score() == hereBefore + 12 && Moods.work(level, there).score() == thereBefore + 8, "repaired");
		// Wonders off
		boolean on = Wonders.ENABLED;
		try {
			Wonders.ENABLED = false;
			helper.assertTrue(Moods.work(level, here).score() == hereBefore && Moods.work(level, there).score() == thereBefore, "with Wonders off");
		} finally {
			Wonders.ENABLED = on;
		}
		helper.succeed();
	}

	/**
	 * The inn: without the Colossus one traveller a morning, two at most; with it two arrive the same morning (beds
	 * free) and four fit; damaged, the usual again (the inn full at two); the Expert odds of the village's travellers
	 * twice as high, the same dice making every Expert of the usual odds one too.
	 */
	//$ gametest_ticks_batch BIG '100' '"colossusInn"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "colossusInn")
	public void travellersComeTwiceAsOftenAndMoreAreExperts(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		int innRadius = Innkeepers.RADIUS;
		VillageHalls.RADIUS = 16;
		Innkeepers.RADIUS = 14; // the inn counts only this test's guests
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Innkeepers.RADIUS = innRadius;
			VillageNeeds.forget();
			CivicEffects.forget();
		});
		BlockPos hallRel = new BlockPos(11, 2, 11);
		BlockPos counterRel = new BlockPos(6, 2, 6);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		helper.setBlock(counterRel, ModBlocks.INN_COUNTER);
		for (int x : new int[] {2, 4, 6, 8, 10, 12}) {
			helper.setBlock(new BlockPos(x, 2, 17), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
			helper.setBlock(new BlockPos(x, 2, 18), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		}
		helper.runAfterDelay(5, () -> {
			BlockPos hall = helper.absolutePos(hallRel);
			BlockPos counter = helper.absolutePos(counterRel);
			Villager innkeeper = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 7));
			Jobs.employ(level, innkeeper, counter, ModVillagers.INN_COUNTER_POI, ModVillagers.INNKEEPER);
			innkeeper.setNoAi(true);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setRank(VillageRanks.Rank.HAMLET);
			entity.setEdicts(List.of());
			CivicEffects.forget();
			long morning = (level.getDayTime() / VillageNeeds.DAY + 1) * VillageNeeds.DAY + 1000;
			level.setDayTime(morning);
			helper.assertTrue(Innkeepers.maxGuests(innkeeper) == 2 && Innkeepers.arrivalsPerMorning(innkeeper) == 1, "usual: " + numbers(innkeeper));
			tend(helper, level, innkeeper, counter, 1);
			tend(helper, level, innkeeper, counter, 1);
			Innkeepers.guests(level, counter).forEach(Villager::discard);

			// the next morning, with the Colossus: two arrive, four fit
			level.setDayTime(morning + VillageNeeds.DAY);
			WonderSite site = colossus(helper, hall);
			helper.assertTrue(Innkeepers.maxGuests(innkeeper) == 4 && Innkeepers.arrivalsPerMorning(innkeeper) == 2, "with the Colossus: " + numbers(innkeeper));
			tend(helper, level, innkeeper, counter, 1);
			tend(helper, level, innkeeper, counter, 2);
			tend(helper, level, innkeeper, counter, 2);
			helper.assertTrue(TravellersPower.expertFactor(level, counter) == 2, "the Expert factor: " + TravellersPower.expertFactor(level, counter));

			// damaged: the usual, so the inn is full at two
			damage(level, site, true);
			level.setDayTime(morning + 2 * VillageNeeds.DAY);
			helper.assertTrue(Innkeepers.maxGuests(innkeeper) == 2 && Innkeepers.arrivalsPerMorning(innkeeper) == 1
				&& TravellersPower.expertFactor(level, counter) == 1, "a damaged Colossus: " + numbers(innkeeper));
			helper.assertTrue(Innkeepers.tend(level, innkeeper, counter).equals("full"), "a third guest with the Colossus damaged");
			damage(level, site, false);
			helper.assertTrue(Innkeepers.arrivalsPerMorning(innkeeper) == 2, "repaired: " + numbers(innkeeper));
			boolean on = Wonders.ENABLED;
			try {
				Wonders.ENABLED = false;
				helper.assertTrue(Innkeepers.maxGuests(innkeeper) == 2 && Innkeepers.arrivalsPerMorning(innkeeper) == 1, "Wonders off: " + numbers(innkeeper));
			} finally {
				Wonders.ENABLED = on;
			}

			// twice as likely Experts: the same dice for both, at a Journeyman innkeeper's inn
			RandomSource usualDice = RandomSource.create(35022L);
			RandomSource colossusDice = RandomSource.create(35022L);
			int usual = 0;
			int more = 0;
			for (int i = 0; i < 4000; i++) {
				int a = Innkeepers.newcomerLevel(usualDice, 3, 1);
				int b = Innkeepers.newcomerLevel(colossusDice, 3, 2);
				helper.assertTrue(a != 4 || b == 4, "an Expert of the usual odds wasn't one with the Colossus (roll " + i + ")");
				usual += a == 4 ? 1 : 0;
				more += b == 4 ? 1 : 0;
			}
			helper.assertTrue(usual > 200 && more >= usual * 1.8 && more <= usual * 2.2, "Experts in 4,000: " + usual + " usually, " + more + " with the Colossus");
			helper.succeed();
		});
	}

	/** Runs the inn's round and checks the guests staying after it (each held still where they came in). */
	private static void tend(GameTestHelper helper, ServerLevel level, Villager innkeeper, BlockPos counter, int want) {
		String state = Innkeepers.tend(level, innkeeper, counter);
		List<Villager> guests = Innkeepers.guests(level, counter);
		guests.forEach(g -> g.setNoAi(true));
		helper.assertTrue(guests.size() == want, guests.size() + " guests, not " + want + " (" + state + ", " + numbers(innkeeper) + ")");
	}

	private static String numbers(Villager innkeeper) {
		return Innkeepers.maxGuests(innkeeper) + " guests at most, " + Innkeepers.arrivalsPerMorning(innkeeper) + " a morning";
	}

	/**
	 * The plaque: the seven signs over the front arch, written once finished, left to right from the road: the hall's
	 * owner as the Founder while there's none, the village's name, the founding day from the chronicle, the day it was
	 * finished; waxed and written once. A sign broken and put back is written again on the next round, now with the
	 * village's settled Founder's name; a Colossus still going up writes nothing; without a founding line, "long ago".
	 */
	//$ gametest_ticks_batch HUGE '100' '"colossusPlaque"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "colossusPlaque")
	public void thePlaqueReadsTheVillageAndItsFoundingDay(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		boolean legends = Legends.ENABLED;
		Legends.ENABLED = true;
		BlockPos hallRel = new BlockPos(26, 2, 26);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(hallRel);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setCustomName(Component.literal("Thornholm"));
		entity.setOwner(UUID.randomUUID(), "Jesse");
		Chronicle.atHall(level, hall, Chronicle.Kind.FOUNDED, Component.literal("Thornholm was founded"), 3);
		Chronicle.atHall(level, hall, Chronicle.Kind.FOUNDED, Component.translatable("chronicle.aliveworkplace.colony.mother", "x", "y", "z"), 2);
		WonderManager manager = WonderManager.get(level);
		WonderSite begun = manager.begin(COLOSSUS, hall, UUID.randomUUID(), "Jesse", 5);
		Leftovers.after(helper, () -> {
			manager.remove(begun.id());
			Wonders.forgetPowers();
			ColossusPlaque.forget();
			Legends.ENABLED = legends;
			LegendPowers.forget();
		});
		manager.place(begun.id(), new BlueprintData.Placement(Ids.of(level.dimension()), helper.absolutePos(new BlockPos(0, 1, 0)), Rotation.NONE, Mirror.NONE),
			helper.absolutePos(new BlockPos(0, 2, 28)));
		WonderSite going = manager.site(begun.id()).orElseThrow();
		List<BlockPos> signs = ColossusPlaque.signs(level, going);
		helper.assertTrue(signs.size() == 7, "the plaque's signs in its drawing: " + signs);
		// build the plaque's signs as drawn (on a dark oak backing)
		for (BlockPos p : signs) {
			BlockState state = WonderUpkeep.blocks(level, going).stream().filter(b -> b.pos().equals(p)).findFirst().orElseThrow().state();
			level.setBlockAndUpdate(p.relative(state.getValue(WallSignBlock.FACING).getOpposite()), Blocks.DARK_OAK_PLANKS.defaultBlockState());
			level.setBlockAndUpdate(p, state);
		}
		helper.assertTrue(ColossusPlaque.write(level, going) == 0 && text(level, signs.get(3)).isBlank(), "written while going up");
		manager.finish(begun.id(), 12);
		WonderSite site = manager.site(begun.id()).orElseThrow();
		helper.assertTrue(ColossusPlaque.write(level, site) == 7, "not every sign written");
		List<String> read = signs.stream().map(p -> text(level, p)).toList();
		helper.assertTrue(read.equals(List.of("Raised in|honor of", "the Founder|Jesse", "who founded", "Thornholm", "on day 3",
			"Finished by|its people|on day 12", "May it stand|a thousand|years")), "the plaque reads: " + read);
		for (BlockPos p : signs) {
			SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(p);
			helper.assertTrue(sign.isWaxed() && sign.getText(true).hasGlowingText(), "a sign that can be edited, or isn't lit: " + p);
		}
		helper.assertTrue(ColossusPlaque.write(level, site) == 0, "written twice");

		// a Founder settles; the second sign broken and put back is written with their name
		Villager founder = helper.spawn(EntityType.VILLAGER, new BlockPos(24, 2, 24));
		founder.setNoAi(true);
		founder.setCustomName(Component.literal("Aldric"));
		Legends.make(level, founder, Legends.get(Founder.ID).orElseThrow(() -> new AssertionError("founder.json didn't load")), "test", java.util.Optional.of(hall));
		LegendPowers.forget();
		Leftovers.after(helper, () -> {
			Legends.clear(founder);
			LegendRecord.get(level).forget(founder.getUUID());
			founder.discard();
		});
		BlockState second = level.getBlockState(signs.get(1));
		level.setBlockAndUpdate(signs.get(1), Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(signs.get(1), second);
		helper.assertTrue(ColossusPlaque.write(level, site) == 1 && text(level, signs.get(1)).equals("the Founder|Aldric"), "the Founder's sign: " + text(level, signs.get(1)));
		// without a founding line: long ago
		helper.assertTrue(ColossusPlaque.foundedOn(level, hall).orElse(-1) == 3, "the founding day: " + ColossusPlaque.foundedOn(level, hall));
		helper.assertTrue(ColossusPlaque.texts(level, site).get(4).getMessage(1, false).getString().equals("on day 3"), "the fifth sign's text");
		helper.assertTrue(ColossusPlaque.foundedOn(level, helper.absolutePos(new BlockPos(1, 2, 1))).isEmpty(), "a founding day where no hall is");
		helper.succeed();
	}

	/** A sign's front lines that aren't empty, joined by "|". */
	private static String text(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
			return "(no sign)";
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < 4; i++) {
			String line = sign.getText(true).getMessage(i, false).getString();
			if (!line.isEmpty()) {
				out.append(out.length() > 0 ? "|" : "").append(line);
			}
		}
		return out.toString();
	}

	/** The type is the Colossus's own (not declared), reads its numbers and refuses bad ones, and its line is written out. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theTravellersPowerReadsItsNumbers(GameTestHelper helper) {
		helper.assertTrue(!WonderPowers.DECLARED.contains(TravellersPower.TYPE) && WonderPowers.types().contains(TravellersPower.TYPE), "still declared");
		var file = Wonders.loaded().get(COLOSSUS);
		helper.assertTrue(file != null && file.powers().stream().anyMatch(p -> p.power() instanceof TravellersPower t && t.factor() == 2 && t.expertFactor() == 2),
			"the Colossus's file: " + (file == null ? Wonders.problems() : file.powers()));
		helper.assertTrue(file.powers().stream().anyMatch(p -> p.power() instanceof io.github.jcondedata.aliveworkplace.wonder.powers.MoodPower m && m.realm()
			&& m.points(false) == 8 && m.points(true) == 12), "the Colossus's mood: " + file.powers());
		helper.assertTrue(file.powers().stream().anyMatch(p -> p.power() instanceof io.github.jcondedata.aliveworkplace.wonder.powers.BeautyPower b && b.points() == 8),
			"the Colossus's beauty: " + file.powers());
		helper.assertTrue(WonderPowers.parse(json("{\"type\": \"travellers\"}")).equals(new TravellersPower(1, 1)), "the defaults");
		for (String bad : List.of("{\"type\": \"travellers\", \"factor\": 0.5}", "{\"type\": \"travellers\", \"expert_factor\": 9}")) {
			try {
				WonderPowers.parse(json(bad));
				helper.fail("read: " + bad);
			} catch (IllegalArgumentException expected) {
				// refused, with the reason the file is logged with
			}
		}
		String line = new TravellersPower(2, 2).describe().getString();
		helper.assertTrue(line.equals("Travelers come to its inns twice as often, twice as likely to be Experts"), "the page's line: " + line);
		String three = new TravellersPower(3, 1.5).describe().getString();
		helper.assertTrue(three.equals("Travelers come to its inns 3 times as often, 1.5 times as likely to be Experts"), "the page's line: " + three);
		helper.succeed();
	}

	private static com.google.gson.JsonObject json(String text) {
		return com.google.gson.JsonParser.parseString(text).getAsJsonObject();
	}
}
