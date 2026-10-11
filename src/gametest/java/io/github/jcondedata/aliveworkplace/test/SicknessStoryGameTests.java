package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.herb.HerbBlock;
import io.github.jcondedata.aliveworkplace.nurse.NurseWork;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.story.SicknessStory;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * The Sickness, II: the Grey Cough (ROADMAP 31.18): the night's pass-on and its half-village cap, a nurse's remedy that
 * only eases it, the healer (the Nurse, else Doctor Wren), the three nearest herbs with a stubbed locator, the brew and
 * the cure round with the ending's thanks, the cough running its course, and the switch.
 */
public class SicknessStoryGameTests implements FabricGameTest {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final long DAY = VillageNeeds.DAY;

	/** Small village areas, nothing far built, illness on, festivals on, herbs found nowhere unless the test says. */
	private static void settings(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean sick = Sickness.ENABLED;
		boolean festivals = Festivals.ENABLED;
		SicknessStory.Locator locator = SicknessStory.LOCATOR;
		VillageHalls.RADIUS = 12;
		Arcs.NEAR = 0;
		Sickness.ENABLED = true;
		Festivals.ENABLED = true;
		SicknessStory.LOCATOR = (level, from, herb) -> null;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			Sickness.ENABLED = sick;
			Festivals.ENABLED = festivals;
			SicknessStory.LOCATOR = locator;
		});
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> Arcs.stopAll(level, abs));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs);
		return entity;
	}

	private static Villager villager(GameTestHelper helper, int x, int z, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}

	/** {@code n} villagers in rows of four, three blocks apart, round the hall. */
	private static List<Villager> village(GameTestHelper helper, int n) {
		List<Villager> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(villager(helper, 11 + (i % 4) * 3, 12 + (i / 4) * 3, "Villager " + (i + 1)));
		}
		return out;
	}

	private static Villager nurse(GameTestHelper helper, BlockPos station) {
		helper.setBlock(station, Blocks.BREWING_STAND);
		Villager nurse = villager(helper, station.getX() + 1, station.getZ(), "Nurse Ada");
		Jobs.employ(helper.getLevel(), nurse, helper.absolutePos(station), ModVillagers.NURSE_STATION_POI, ModVillagers.NURSE);
		nurse.setVillagerXp(70);
		return nurse;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(HALL.offset(0, 0, 2));
		player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	private static void setDay(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	private static Arcs.Arc arc(GameTestHelper helper) {
		Arcs.Arc arc = Arcs.find("the_sickness").orElse(null);
		helper.assertTrue(arc != null && arc.threat() && arc.chapters().size() == 3, "the Sickness didn't load as a threat of three chapters");
		return arc;
	}

	private static List<String> chronicle(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.STORY).map(e -> e.text().getString()).toList();
	}

	private static long grey(List<Villager> vs) {
		return vs.stream().filter(Sickness::isGrey).count();
	}

	/** A locator that finds each herb at its own distance east of the hall (null: not found), counting its lookups. */
	private static SicknessStory.Locator stub(Map<String, Integer> east, AtomicInteger lookups) {
		return (level, from, herb) -> {
			lookups.incrementAndGet();
			Integer d = east.get(BuiltInRegistries.BLOCK.getKey(herb).getPath());
			return d == null ? null : from.offset(d, 0, 0);
		};
	}

	/**
	 * The night's pass-on: three of twelve villagers standing close cough; night after night each passes it on to one
	 * close by at 40%, until half the village coughs and no more; a villager who never came within 8 blocks of them
	 * never catches it, and a Hardy one never does.
	 */
	//$ gametest_batch TALL '"greyCoughPassesOnUpToHalfTheVillage"'
	@GameTest(template = TALL, batch = "greyCoughPassesOnUpToHalfTheVillage")
	public void greyCoughPassesOnUpToHalfTheVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> close = village(helper, 12);
		Villager far = villager(helper, 4, 26, "Far Off");
		List<Villager> all = new ArrayList<>(close);
		all.add(far);
		ArcState s = new ArcState(SicknessStory.ARC.toString());
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(Sickness.greyCough(level, close.get(i)), "villager " + i + " didn't catch it");
		}
		helper.assertTrue(Sickness.isGrey(close.get(0)) && !Sickness.isHexed(close.get(0))
			&& close.get(0).hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the Grey Cough isn't its own illness, with slowness");
		helper.assertTrue(SicknessStory.cap(level, hall) == 6, "half the village: " + SicknessStory.cap(level, hall));
		RandomSource random = RandomSource.create(3118);
		int nights = 0;
		int caught = 0;
		for (; nights < 40 && grey(all) < 6; nights++) {
			caught += SicknessStory.spread(level, hall, s, random);
			helper.assertTrue(grey(all) <= 6, "past half the village on night " + (nights + 1) + ": " + grey(all));
		}
		helper.assertTrue(grey(all) == 6 && caught == 3, "after " + nights + " nights " + grey(all) + " cough (" + caught + " caught it)");
		helper.assertTrue(nights > 1, "everyone caught it in one night: it isn't 40%");
		for (int i = 0; i < 10; i++) {
			SicknessStory.spread(level, hall, s, random);
		}
		helper.assertTrue(grey(all) == 6, "past half the village later: " + grey(all));
		helper.assertTrue(!Sickness.isIll(far), "the villager who was never near caught it");
		helper.assertTrue(SicknessStory.caught(s).size() == 3, "caught: " + SicknessStory.caught(s));
		helper.assertTrue(chronicle(entity).stream().anyMatch(l -> l.contains("passed to") && l.contains("in the night")), "no chronicle line: " + chronicle(entity));
		helper.succeed();
	}

	/**
	 * A nurse's remedy only eases the Grey Cough: the slowness goes for a day, the cough stays, and she doesn't spend
	 * another remedy on them that day; an ordinary illness she still cures. With no Sickness story in the village, the
	 * cough lifts at the hall's round.
	 */
	//$ gametest_batch TALL '"aRemedyOnlyEasesTheGreyCough"'
	@GameTest(template = TALL, batch = "aRemedyOnlyEasesTheGreyCough")
	public void aRemedyOnlyEasesTheGreyCough(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		Villager nurse = nurse(helper, new BlockPos(6, 2, 6));
		helper.setBlock(new BlockPos(6, 2, 7), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(6, 2, 7));
		chest.setItem(0, new ItemStack(Items.HONEY_BOTTLE, 3));
		Villager coughing = villager(helper, 9, 6, "Coughing");
		Villager ill = villager(helper, 20, 20, "Ordinary");
		Sickness.greyCough(level, coughing);
		Sickness.fallIll(level, ill);

		NurseWork.cure(level, nurse);
		helper.assertTrue(Sickness.isGrey(coughing) && Sickness.eased(coughing, level.getGameTime()), "the remedy didn't only ease it");
		helper.assertTrue(!coughing.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "still slow once eased");
		helper.assertTrue(chest.countItem(Items.HONEY_BOTTLE) == 2, "honey left: " + chest.countItem(Items.HONEY_BOTTLE));
		NurseWork.cure(level, nurse); // the eased one waits: the ordinary illness is cured
		helper.assertTrue(!Sickness.isIll(ill) && Sickness.isGrey(coughing), "the next remedy went to the eased cough");
		NurseWork.cure(level, nurse);
		helper.assertTrue(chest.countItem(Items.HONEY_BOTTLE) == 1, "a remedy spent on an eased cough: honey " + chest.countItem(Items.HONEY_BOTTLE));
		ModAttachments.ILL_EASED_UNTIL.set(coughing, level.getGameTime() - 1); // a day on
		NurseWork.cure(level, nurse);
		helper.assertTrue(Sickness.isGrey(coughing) && Sickness.eased(coughing, level.getGameTime()) && chest.countItem(Items.HONEY_BOTTLE) == 0,
			"a day on, the remedy didn't ease it again");

		// No Sickness story here: the hall's round lifts it.
		Sickness.round(level, coughing, 20);
		helper.assertTrue(!Sickness.isIll(coughing) && !ModAttachments.ILL_KIND.has(coughing) && !ModAttachments.ILL_EASED_UNTIL.has(coughing),
			"the cough stayed with no story running");
		helper.succeed();
	}

	/**
	 * The arc begins: three villagers cough, the chronicle and the chatter say so, and the village's Nurse is the healer;
	 * a right-click on her names the herbs. While the story runs, the cough doesn't pass with time, and an eased cough
	 * comes back slow the next day.
	 */
	//$ gametest_batch TALL '"theNurseIsTheHealer"'
	@GameTest(template = TALL, batch = "theNurseIsTheHealer")
	public void theNurseIsTheHealer(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> people = village(helper, 8);
		Villager nurse = nurse(helper, new BlockPos(20, 2, 20));
		ServerPlayer player = player(helper);
		long day = Chronicle.day(level) + 1;
		setDay(level, day, 1000);
		SicknessStory.LOCATOR = stub(Map.of("sea_lavender", 200, "mirewort", 400, "glowcap", 600), new AtomicInteger());

		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null && s.chapter == 0 && s.started, "the arc didn't start");
		helper.assertTrue(grey(people) == 3 && !Sickness.isIll(nurse), "coughing: " + grey(people) + ", the nurse ill: " + Sickness.isIll(nurse));
		helper.assertTrue(SicknessStory.healer(level, s) == nurse, "the healer isn't the nurse");
		helper.assertTrue(chronicle(entity).stream().anyMatch(l -> l.startsWith("The Grey Cough came to the village:")), "no chronicle: " + chronicle(entity));
		helper.assertTrue(Arcs.chatter(level, hall).contains("chatter.aliveworkplace.the_sickness.cough"), "no chatter: " + Arcs.chatter(level, hall));

		Villager sick = people.stream().filter(Sickness::isGrey).findFirst().orElseThrow();
		ModAttachments.ILL_SINCE.set(sick, level.getGameTime() - Sickness.RECOVERY * 3);
		ModAttachments.ILL_EASED_UNTIL.set(sick, level.getGameTime() - 1);
		sick.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		Sickness.round(level, sick, 20);
		helper.assertTrue(Sickness.isGrey(sick) && sick.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the cough passed with time, or the slowness didn't come back");

		helper.assertTrue(SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, people.get(7)) == InteractionResult.PASS, "a villager not the healer took the click");
		helper.assertTrue(SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse) == InteractionResult.SUCCESS, "the nurse didn't listen");
		helper.assertTrue(s.chapter == 1 && s.started && SicknessStory.herbs(s).size() == 3, "the herbs weren't named: chapter " + s.chapter);
		helper.assertTrue(!SicknessStory.busy(level, nurse), "the nurse stopped work before the brew");
		helper.succeed();
	}

	/**
	 * With no Nurse, Doctor Wren comes to the hall the next morning in a cleric's robe and is the healer; he leaves when
	 * the arc is over.
	 */
	//$ gametest_batch TALL '"doctorWrenComesWithNoNurse"'
	@GameTest(template = TALL, batch = "doctorWrenComesWithNoNurse")
	public void doctorWrenComesWithNoNurse(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		village(helper, 8);
		long day = Chronicle.day(level) + 1;
		setDay(level, day, 1000);

		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null && SicknessStory.healer(level, s) == null, "a healer with no nurse");
		SicknessStory.tick(level);
		helper.assertTrue(SicknessStory.healer(level, s) == null, "Wren came the same day");
		setDay(level, day + 1, 1000);
		SicknessStory.tick(level);
		Villager wren = SicknessStory.healer(level, s);
		helper.assertTrue(wren != null && wren.getCustomName() != null && wren.getCustomName().getString().equals("Doctor Wren"), "no Doctor Wren");
		helper.assertTrue(wren.getVillagerData().getProfession() == VillagerProfession.CLERIC, "Wren isn't in a cleric's robe");
		double dx = wren.getX() - hall.getX();
		double dz = wren.getZ() - hall.getZ();
		helper.assertTrue(dx * dx + dz * dz < 6 * 6, "Wren isn't by the hall");
		helper.assertTrue(!Sickness.isIll(wren) && SicknessStory.villagers(level, hall).stream().noneMatch(v -> v == wren), "Wren counts as a villager");
		helper.assertTrue(chronicle(entity).stream().anyMatch(l -> l.startsWith("Doctor Wren")), "no chronicle line for Wren: " + chronicle(entity));
		Arcs.stopAll(level, hall);
		helper.assertTrue(!wren.isAlive(), "Wren stayed after the arc");
		helper.succeed();
	}

	/**
	 * The herbs: the three whose biomes the (stubbed) locator finds nearest, a herb not found passed over for the next,
	 * each looked up once; a patch's place at each and a map to each; then the hand-in, 3 of each, a herb at a time.
	 */
	//$ gametest_batch TALL '"theHealerNamesTheNearestHerbs"'
	@GameTest(template = TALL, batch = "theHealerNamesTheNearestHerbs")
	public void theHealerNamesTheNearestHerbs(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		village(helper, 8);
		Villager nurse = nurse(helper, new BlockPos(20, 2, 20));
		ServerPlayer player = player(helper);
		setDay(level, Chronicle.day(level) + 1, 1000);
		AtomicInteger lookups = new AtomicInteger();
		Map<String, Integer> east = new HashMap<>(Map.of("frostbloom", 900, "mirewort", 300, "peakmoss", 2000, "sea_lavender", 150,
			"glowcap", 1200, "petal_balm", 2500)); // no Sunthistle nor Jungle Orchid within reach
		SicknessStory.LOCATOR = stub(east, lookups);

		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null, "the arc didn't start");
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		List<String> herbs = SicknessStory.herbs(s).stream().map(h -> BuiltInRegistries.BLOCK.getKey(h).getPath()).toList();
		helper.assertTrue(herbs.equals(List.of("sea_lavender", "mirewort", "frostbloom")), "the herbs: " + herbs);
		helper.assertTrue(lookups.get() == 8, "lookups: " + lookups.get());
		for (String herb : herbs) {
			ArcState.ArcPlace p = s.place("herb_" + herb);
			helper.assertTrue(p != null && p.spot.getX() == hall.getX() + east.get(herb) && p.spot.getZ() == hall.getZ(), "the " + herb + " patch: " + (p == null ? null : p.spot));
		}
		int maps = player.getInventory().countItem(Items.FILLED_MAP);
		helper.assertTrue(maps == 3, "maps: " + maps);
		helper.assertTrue(chronicle(entity).stream().anyMatch(l -> l.contains("Sea Lavender, Mirewort and Frostbloom")), "the chronicle: " + chronicle(entity));

		// Nothing in hand: she says what she still wants (no second set of maps, no second lookup).
		helper.assertTrue(SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse) == InteractionResult.SUCCESS && s.chapter == 1,
			"the hand-in with nothing in hand");
		helper.assertTrue(lookups.get() == 8 && player.getInventory().countItem(Items.FILLED_MAP) == 3, "looked up again, or more maps");
		// A herb at a time: 3 Sea Lavender and only 2 Mirewort.
		player.getInventory().add(new ItemStack(ModBlocks.SEA_LAVENDER, 4));
		player.getInventory().add(new ItemStack(ModBlocks.MIREWORT, 2));
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		helper.assertTrue(player.getInventory().countItem(ModBlocks.SEA_LAVENDER.asItem()) == 1 && player.getInventory().countItem(ModBlocks.MIREWORT.asItem()) == 2
			&& s.chapter == 1, "the first hand-in: sea lavender " + player.getInventory().countItem(ModBlocks.SEA_LAVENDER.asItem()));
		player.getInventory().add(new ItemStack(ModBlocks.MIREWORT, 1));
		player.getInventory().add(new ItemStack(ModBlocks.FROSTBLOOM, 3));
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		helper.assertTrue(player.getInventory().countItem(ModBlocks.MIREWORT.asItem()) == 0 && player.getInventory().countItem(ModBlocks.FROSTBLOOM.asItem()) == 0,
			"the herbs weren't all taken");
		helper.assertTrue(s.chapter == 2 && s.started && s.flags.containsKey(SicknessStory.HERBS_GIVEN) && s.helpers.containsKey(player.getUUID()),
			"the brew didn't begin: chapter " + s.chapter);
		helper.assertTrue(SicknessStory.busy(level, nurse), "the nurse isn't brewing");
		helper.assertTrue(chronicle(entity).stream().anyMatch(l -> l.contains("brought the healer Mirewort and Frostbloom")), "the chronicle: " + chronicle(entity));
		helper.succeed();
	}

	/**
	 * The brew and the cure round: half a day at her brewing stand ("Brewing the cure" at 40%), then the nurse takes the
	 * tonic round, to each coughing villager in turn; everyone is well and the ending thanks the gatherer: a festival the
	 * next evening, +150 standing, Healer of the village, +100 friendship with each who was ill.
	 */
	//$ gametest_batch TALL '"theBrewAndTheCureRound"'
	@GameTest(template = TALL, batch = "theBrewAndTheCureRound")
	public void theBrewAndTheCureRound(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> people = village(helper, 10);
		Villager nurse = nurse(helper, new BlockPos(20, 2, 20));
		ServerPlayer player = player(helper);
		long day = Chronicle.day(level) + 1;
		setDay(level, day, 1000);
		SicknessStory.LOCATOR = stub(Map.of("sea_lavender", 200, "mirewort", 400, "glowcap", 600), new AtomicInteger());

		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null, "the arc didn't start");
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		for (HerbBlock herb : SicknessStory.herbs(s)) {
			player.getInventory().add(new ItemStack(herb, 3));
		}
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		helper.assertTrue(s.chapter == 2 && s.started, "the brew didn't begin");
		Villager fourth = people.stream().filter(v -> !Sickness.isIll(v)).findFirst().orElseThrow();
		Sickness.greyCough(level, fourth); // a fourth caught it in the night
		s.flags.put("ill." + fourth.getUUID(), ArcState.FOREVER);
		helper.assertTrue(SicknessStory.caught(s).size() == 4, "caught: " + SicknessStory.caught(s).size());

		long from = SicknessStory.stamp(s, "brew_from");
		level.setDayTime(from + SicknessStory.BREW * 2 / 5);
		SicknessStory.tick(level);
		WorkerStatus.Entry status = WorkerStatus.get(nurse, level.getGameTime());
		helper.assertTrue(status != null && status.title().getString().equals("Brewing the cure") && Math.abs(status.progress() - 0.4f) < 0.02f,
			"the status: " + (status == null ? null : status.title().getString() + " " + status.progress()));
		helper.assertTrue(SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse) == InteractionResult.SUCCESS && s.chapter == 2, "the brewing talk");
		helper.assertTrue(!s.flags.containsKey(SicknessStory.BREWED), "brewed early");

		level.setDayTime(from + SicknessStory.BREW);
		SicknessStory.tick(level);
		helper.assertTrue(s.flags.containsKey(SicknessStory.BREWED) && nurse.getMainHandItem().is(ModItems.THREE_HERB_TONIC), "the tonic isn't ready in her hand");
		for (int i = 0; i < 30 && Arcs.running(level, hall) == s; i++) {
			SicknessStory.tick(level);
			level.setDayTime(level.getDayTime() + 401); // (she steps over to anyone she can't walk to)
		}
		helper.assertTrue(Arcs.running(level, hall) == null, "the arc isn't over");
		List<UUID> ill = SicknessStory.caught(s); // (with any who caught it the night the tonic was ready)
		helper.assertTrue(ill.size() >= 4 && ill.size() <= 5, "caught: " + ill.size());
		helper.assertTrue(people.stream().noneMatch(Sickness::isIll), "still coughing: " + grey(people));
		helper.assertTrue(nurse.getMainHandItem().isEmpty() && !SicknessStory.busy(level, nurse), "the nurse kept the tonic");
		helper.assertTrue(entity.festivalDay() == Chronicle.day(level) + 1, "no festival tomorrow: " + entity.festivalDay());
		helper.assertTrue(Reputation.points(level, hall, player.getUUID()) >= 150, "standing: " + Reputation.points(level, hall, player.getUUID()));
		helper.assertTrue(Reputation.honours(level, hall, player.getUUID()).contains("healer"), "no Healer honour: " + Reputation.honours(level, hall, player.getUUID()));
		for (Villager v : people) {
			boolean was = ill.contains(v.getUUID());
			int points = Friendship.points(v, player.getUUID());
			helper.assertTrue(was ? points >= 100 : points < 100, v.getCustomName().getString() + (was ? " was ill" : " wasn't ill") + ": friendship " + points);
		}
		helper.assertTrue(chronicle(entity).stream().anyMatch(l -> l.startsWith("The Three-Herb Tonic went round") && l.contains(ill.size() + " villagers")), "the chronicle: " + chronicle(entity));
		helper.succeed();
	}

	/**
	 * No cure in 15 days: the cough runs its course; the village's mood drops 15 for 5 days, everyone is well within 3
	 * days, the chronicle says so and the arc ends, failed. Nobody dies.
	 */
	//$ gametest_batch TALL '"withoutTheCureTheCoughRunsItsCourse"'
	@GameTest(template = TALL, batch = "withoutTheCureTheCoughRunsItsCourse")
	public void withoutTheCureTheCoughRunsItsCourse(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> people = village(helper, 8);
		nurse(helper, new BlockPos(20, 2, 20));
		long day = Chronicle.day(level) + 1;
		setDay(level, day, 1000);
		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null && grey(people) == 3, "the arc didn't start");

		setDay(level, day + 14, 1000);
		SicknessStory.tick(level);
		helper.assertTrue(SicknessStory.stamp(s, "course") < 0 && grey(people) == 3, "it ran its course a day early");
		setDay(level, day + 15, 1000);
		SicknessStory.tick(level);
		helper.assertTrue(SicknessStory.stamp(s, "course") == day + 15, "it isn't running its course");
		helper.assertTrue(Stories.mood(level, people.get(0)) == -15, "mood: " + Stories.mood(level, people.get(0)));
		for (int d = 1; d <= 3; d++) {
			setDay(level, day + 15 + d, 1000);
			SicknessStory.tick(level);
			if (d < 3) {
				helper.assertTrue(Arcs.running(level, hall) == s, "the arc ended on day " + d);
			}
		}
		helper.assertTrue(people.stream().noneMatch(Sickness::isIll) && people.stream().allMatch(Villager::isAlive), "not all well and alive after 3 days");
		helper.assertTrue(Arcs.running(level, hall) == null, "the arc didn't end");
		helper.assertTrue(Stories.mood(level, people.get(0)) == -15, "the mood didn't last: " + Stories.mood(level, people.get(0)));
		setDay(level, day + 21, 1000);
		helper.assertTrue(Stories.mood(level, people.get(0)) == 0, "the mood lasted past 5 days: " + Stories.mood(level, people.get(0)));
		List<String> lines = chronicle(entity);
		helper.assertTrue(lines.stream().anyMatch(l -> l.startsWith("No cure came in fifteen days")) && lines.stream().anyMatch(l -> l.startsWith("The Grey Cough ran its course")),
			"the chronicle: " + lines);
		helper.succeed();
	}

	/**
	 * The trigger: a village of Village rank, 12 villagers, {@code villagerSickness} on, 6% a day. With the switch off
	 * the condition fails, and a running arc ends quietly with everyone well.
	 */
	//$ gametest_batch TALL '"sicknessSwitchedOff"'
	@GameTest(template = TALL, batch = "sicknessSwitchedOff")
	public void sicknessSwitchedOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> people = village(helper, 8);
		Arcs.Arc arc = arc(helper);
		List<String> types = arc.conditions().stream().map(Condition::type).toList();
		helper.assertTrue(types.containsAll(List.of("rank_at_least", "villagers", "villager_sickness")) && Math.abs(arc.chance() - 0.06f) < 1e-6,
			"the trigger: " + types + " at " + arc.chance());
		Condition on = Conditions.parse(JsonParser.parseString("{\"type\": \"villager_sickness\"}").getAsJsonObject());
		helper.assertTrue(on.met(level, hall), "the condition fails with sickness on");
		ArcState s = Arcs.start(level, hall, arc);
		helper.assertTrue(s != null && grey(people) == 3, "the arc didn't start");
		Sickness.ENABLED = false;
		helper.assertTrue(!on.met(level, hall), "the condition holds with sickness off");
		SicknessStory.tick(level);
		helper.assertTrue(Arcs.running(level, hall) == null && grey(people) == 0, "the arc kept running with sickness off");
		helper.succeed();
	}

}
