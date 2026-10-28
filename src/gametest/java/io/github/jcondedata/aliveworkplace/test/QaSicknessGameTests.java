package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.herb.HerbBlock;
import io.github.jcondedata.aliveworkplace.herb.Herbs;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.story.SicknessStory;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * QA (Check tier) for The Sickness (ROADMAP 31.17 and 31.18), written from the spec: the trigger's boundaries (Village
 * rank, 12 villagers, the switch) through the real roll; the night's pass-on at 8 blocks and not 9, by where villagers
 * work and sleep; the half-village cap with an odd count; the Grey Cough kept over a save and old saves ordinary; the
 * hand-in with wrong herbs and wrong counts; the tonic going round only to coughing villagers; every gatherer thanked
 * and nobody else; the healer lost mid-brew; Doctor Wren brewing by the hall and leaving; every sentence a player reads;
 * the herb biome tags; and patches on snowy ground.
 */
public class QaSicknessGameTests implements FabricGameTest {
	static final String TALL = "aliveworkplace_test:tall_area";
	static final String HUGE = "aliveworkplace_test:huge_area";
	static final BlockPos HALL = new BlockPos(15, 2, 15);
	static final long DAY = VillageNeeds.DAY;

	// ---- helpers ---------------------------------------------------------------------------------------------------

	/** Small village areas, nothing far built, illness and festivals on, three herbs found east (Sea Lavender nearest). */
	static void settings(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean sick = Sickness.ENABLED;
		boolean festivals = Festivals.ENABLED;
		SicknessStory.Locator locator = SicknessStory.LOCATOR;
		VillageHalls.RADIUS = 12;
		Arcs.NEAR = 0;
		Sickness.ENABLED = true;
		Festivals.ENABLED = true;
		SicknessStory.LOCATOR = stub(Map.of("sea_lavender", 200, "mirewort", 400, "glowcap", 600));
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			Sickness.ENABLED = sick;
			Festivals.ENABLED = festivals;
			SicknessStory.LOCATOR = locator;
		});
	}

	/** A locator that finds each named herb at its distance east of the hall, and the rest nowhere. */
	static SicknessStory.Locator stub(Map<String, Integer> east) {
		return (level, from, herb) -> {
			Integer d = east.get(BuiltInRegistries.BLOCK.getKey(herb).getPath());
			return d == null ? null : from.offset(d, 0, 0);
		};
	}

	static VillageHallBlockEntity hall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> Arcs.stopAll(level, abs));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs);
		return entity;
	}

	static Villager villager(GameTestHelper helper, int x, int z, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}

	/** {@code n} villagers in rows of four, three blocks apart, round the hall. */
	static List<Villager> village(GameTestHelper helper, int n) {
		List<Villager> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(villager(helper, 11 + (i % 4) * 3, 12 + (i / 4) * 3, "Villager " + (i + 1)));
		}
		return out;
	}

	static Villager nurse(GameTestHelper helper, BlockPos station) {
		helper.setBlock(station, Blocks.BREWING_STAND);
		Villager nurse = villager(helper, station.getX() + 1, station.getZ(), "Nurse Ada");
		Jobs.employ(helper.getLevel(), nurse, helper.absolutePos(station), ModVillagers.NURSE_STATION_POI, ModVillagers.NURSE);
		nurse.setVillagerXp(70);
		return nurse;
	}

	static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(HALL.offset(0, 0, 2));
		player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	static void setDay(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	static Arcs.Arc arc(GameTestHelper helper) {
		Arcs.Arc arc = Arcs.find("the_sickness").orElse(null);
		helper.assertTrue(arc != null, "the Sickness didn't load");
		return arc;
	}

	static List<String> chronicle(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.STORY).map(e -> e.text().getString()).toList();
	}

	static long grey(List<Villager> vs) {
		return vs.stream().filter(Sickness::isGrey).count();
	}

	static int count(ServerPlayer player, HerbBlock herb) {
		return player.getInventory().countItem(herb.asItem());
	}

	/** The healer is told and the herbs named; then {@code player} hands in 3 of each. */
	static void gather(GameTestHelper helper, ServerLevel level, ArcState s, ServerPlayer player, Villager healer) {
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, healer);
		helper.assertTrue(s.chapter == 1 && SicknessStory.herbs(s).size() == 3, "the herbs weren't named: chapter " + s.chapter);
		for (HerbBlock herb : SicknessStory.herbs(s)) {
			player.getInventory().add(new ItemStack(herb, 3));
		}
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, healer);
		helper.assertTrue(s.chapter == 2 && s.started, "the brew didn't begin: chapter " + s.chapter);
	}

	/** Ticks the story on, a little over the healer's walk limit at a time, until the arc is over (or {@code max} ticks). */
	static void runRound(ServerLevel level, BlockPos hall, ArcState s, int max) {
		for (int i = 0; i < max && Arcs.running(level, hall) == s; i++) {
			SicknessStory.tick(level);
			level.setDayTime(level.getDayTime() + 401);
		}
	}

	static void assertAmerican(GameTestHelper helper, String where, String text) {
		String lower = text.toLowerCase(java.util.Locale.ROOT);
		for (String british : new String[] {"grey", "honour", "travelling", "colour", "favour", "neighbour", "centre"}) {
			helper.assertFalse(lower.contains(british), where + " is in British English (" + british + "): " + text);
		}
		helper.assertFalse(text.contains("%s") || text.contains("%1$s") || text.contains("%2$s"), where + " has an argument left over: " + text);
		helper.assertFalse(text.matches("^(message|chronicle|arc|chatter|screen|honour|entity|mood)\\.aliveworkplace\\..*"), where + " is a raw key: " + text);
	}

	// ---- 31.18: the trigger ----------------------------------------------------------------------------------------

	/**
	 * The trigger through the real daily roll (every other arc switched off, the roll forced 300 times): a Hamlet with 12
	 * villagers never starts it; a Village with 11 never; a Village with 12 and {@code villagerSickness} off never; a
	 * Village with exactly 12 and sickness on does, with three coughing.
	 */
	//$ gametest_ticks_batch TALL '200' '"qaSickTrigger"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaSickTrigger")
	public void theTriggerNeedsVillageRankAndTwelve(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		Set<String> disabled = Arcs.DISABLED;
		int cooldown = Arcs.COOLDOWN_DAYS;
		int atOnce = Arcs.AT_ONCE;
		boolean enabled = Arcs.ENABLED;
		Set<String> others = new HashSet<>();
		for (Arcs.Arc a : Arcs.all()) {
			if (!a.id().equals(SicknessStory.ARC)) {
				others.add(a.id().toString());
			}
		}
		Arcs.DISABLED = others;
		Arcs.COOLDOWN_DAYS = 0;
		Arcs.AT_ONCE = 1000;
		Arcs.ENABLED = true;
		Leftovers.after(helper, () -> {
			Arcs.DISABLED = disabled;
			Arcs.COOLDOWN_DAYS = cooldown;
			Arcs.AT_ONCE = atOnce;
			Arcs.ENABLED = enabled;
		});
		List<Villager> people = village(helper, 11);
		RandomSource random = RandomSource.create(3118);

		entity.setRank(VillageRanks.Rank.VILLAGE);
		for (int i = 0; i < 300; i++) {
			helper.assertTrue(Arcs.tryStart(level, hall, random, 1) == null, "it started in a Village of 11 villagers");
		}
		Villager twelfth = villager(helper, 11, 25, "Villager 12");
		people.add(twelfth);
		entity.setRank(VillageRanks.Rank.HAMLET);
		for (int i = 0; i < 300; i++) {
			helper.assertTrue(Arcs.tryStart(level, hall, random, 1) == null, "it started in a Hamlet of 12");
		}
		entity.setRank(VillageRanks.Rank.VILLAGE);
		Sickness.ENABLED = false;
		for (int i = 0; i < 300; i++) {
			helper.assertTrue(Arcs.tryStart(level, hall, random, 1) == null, "it started with villagerSickness off");
		}
		Sickness.ENABLED = true;
		ArcState s = null;
		int rolls = 0;
		for (; rolls < 300 && s == null; rolls++) {
			s = Arcs.tryStart(level, hall, random, 1);
		}
		helper.assertTrue(s != null && s.id.equals(SicknessStory.ARC.toString()), "a Village of exactly 12 never started it in 300 rolls");
		helper.assertTrue(rolls > 1, "it started on the first roll: 6% a day?");
		helper.assertTrue(grey(people) == 3, "coughing at the start: " + grey(people));
		helper.succeed();
	}

	// ---- 31.18: the night's pass-on --------------------------------------------------------------------------------

	/**
	 * Within 8 blocks and not 9: one coughing villager, one well villager exactly 8 blocks off and one exactly 9 blocks
	 * off (and six far away, so the cap is no limit). Over 60 nights the one at 8 catches it; the one at 9 never does.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaSickEightNotNine"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaSickEightNotNine")
	public void thePassOnReachesEightBlocksNotNine(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		Villager cougher = villager(helper, 5, 5, "Cougher");
		Villager eight = villager(helper, 13, 5, "Eight Off");
		Villager nine = villager(helper, 5, 14, "Nine Off");
		for (int[] p : new int[][] {{25, 25}, {22, 25}, {25, 22}, {22, 22}, {19, 25}, {25, 19}}) {
			villager(helper, p[0], p[1], "Far " + p[0] + "," + p[1]);
		}
		helper.assertTrue(SicknessStory.cap(level, hall) == 4, "cap: " + SicknessStory.cap(level, hall));
		helper.assertTrue(Sickness.greyCough(level, cougher), "the first didn't catch it");
		ArcState s = new ArcState(SicknessStory.ARC.toString());
		RandomSource random = RandomSource.create(818);
		for (int night = 0; night < 60; night++) {
			SicknessStory.spread(level, hall, s, random);
			helper.assertFalse(Sickness.isIll(nine), "the villager 9 blocks off caught it on night " + (night + 1));
		}
		helper.assertTrue(Sickness.isGrey(eight), "the villager exactly 8 blocks off never caught it in 60 nights");
		helper.succeed();
	}

	/**
	 * By where they work and sleep: a villager standing next to the cougher whose bed and workstation are far away never
	 * catches it; one standing far away whose bed is 3 blocks from the cougher does.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaSickWorkSleep"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaSickWorkSleep")
	public void thePassOnGoesByWhereTheyWorkAndSleep(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		Villager cougher = villager(helper, 5, 5, "Cougher");
		Villager visitor = villager(helper, 6, 5, "Visitor");
		visitor.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(25, 2, 25))));
		visitor.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(25, 2, 22))));
		Villager sleeper = villager(helper, 25, 5, "Sleeper");
		sleeper.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(7, 2, 7))));
		for (int[] p : new int[][] {{14, 25}, {11, 25}, {14, 22}, {11, 22}, {8, 25}, {8, 22}}) {
			villager(helper, p[0], p[1], "Far " + p[0] + "," + p[1]);
		}
		helper.assertTrue(Sickness.greyCough(level, cougher), "the first didn't catch it");
		ArcState s = new ArcState(SicknessStory.ARC.toString());
		RandomSource random = RandomSource.create(42);
		for (int night = 0; night < 60; night++) {
			SicknessStory.spread(level, hall, s, random);
			helper.assertFalse(Sickness.isIll(visitor), "the visitor who lives and works far off caught it on night " + (night + 1));
		}
		helper.assertTrue(Sickness.isGrey(sleeper), "the villager who sleeps 3 blocks from the cougher never caught it");
		helper.succeed();
	}

	/**
	 * The half-village cap with an odd count: 13 villagers close together, three coughing; night after night it never
	 * passes 6 (half of 13, rounded down), and it does reach 6.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaSickOddCap"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaSickOddCap")
	public void theCapIsHalfOfAnOddVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> people = village(helper, 13);
		helper.assertTrue(SicknessStory.cap(level, hall) == 6, "cap of 13: " + SicknessStory.cap(level, hall));
		for (int i = 0; i < 3; i++) {
			Sickness.greyCough(level, people.get(i));
		}
		ArcState s = new ArcState(SicknessStory.ARC.toString());
		RandomSource random = RandomSource.create(1313);
		for (int night = 0; night < 80; night++) {
			SicknessStory.spread(level, hall, s, random);
			helper.assertTrue(grey(people) <= 6, "past half of 13 on night " + (night + 1) + ": " + grey(people));
		}
		helper.assertTrue(grey(people) == 6, "it never reached half of 13: " + grey(people));
		helper.succeed();
	}

	/**
	 * The healer never catches it (B646): the village's Nurse works right beside two coughing villagers, yet over 60
	 * nights the pass-on goes only to the others round her (each one who catches it is made well again, so half the
	 * village is never reached and the pass-on goes on every night), as the outbreak passes the healer over too.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaSickHealerWell"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaSickHealerWell")
	public void theHealerNeverCatchesIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> people = village(helper, 10);
		Villager nurse = nurse(helper, new BlockPos(20, 2, 20));
		setDay(level, Chronicle.day(level) + 1, 1000);
		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null && SicknessStory.healer(level, s) == nurse, "the Nurse isn't the healer");
		helper.assertFalse(Sickness.isIll(nurse), "the outbreak reached the healer");
		for (Villager v : people) {
			Sickness.recover(level, v);
		}
		Villager left = people.get(7); // (20, 15): 5 blocks from the Nurse
		Villager right = people.get(9); // (14, 18): 6 blocks from her bench
		helper.assertTrue(Sickness.greyCough(level, left) && Sickness.greyCough(level, right), "the two didn't catch it");
		RandomSource random = RandomSource.create(646);
		int others = 0;
		for (int night = 0; night < 60; night++) {
			SicknessStory.spread(level, hall, s, random);
			helper.assertFalse(Sickness.isIll(nurse), "the healer caught it on night " + (night + 1));
			for (Villager v : people) {
				if (v != left && v != right && Sickness.isGrey(v)) {
					others++;
					Sickness.recover(level, v);
				}
			}
		}
		helper.assertTrue(others >= 20, "the pass-on hardly went on: " + others + " in 60 nights");
		helper.assertFalse(SicknessStory.caught(s).contains(nurse.getUUID()), "the healer is counted among the ill");
		helper.succeed();
	}

	// ---- 31.18: saves ----------------------------------------------------------------------------------------------

	/**
	 * The illness kind survives a save: a coughing, eased villager reloads coughing and eased. A villager saved the old way
	 * (ill, with no kind) reloads with an ordinary illness: not the Grey Cough, and it passes with time.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaSickSaves"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaSickSaves")
	public void theGreyCoughSurvivesASaveAndOldSavesStayOrdinary(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		Villager coughing = villager(helper, 5, 5, "Coughing");
		Villager old = villager(helper, 9, 5, "Old Save");
		helper.assertTrue(Sickness.greyCough(level, coughing), "didn't catch it");
		Sickness.ease(level, coughing);
		long eased = ModAttachments.ILL_EASED_UNTIL.get(coughing);
		long since = level.getGameTime() - Sickness.RECOVERY - 5;
		ModAttachments.ILL_SINCE.set(old, since);

		Villager back = reload(helper, coughing);
		helper.assertTrue(Sickness.isGrey(back), "the Grey Cough was lost in the save (kind " + ModAttachments.ILL_KIND.get(back) + ")");
		helper.assertTrue(Long.valueOf(eased).equals(ModAttachments.ILL_EASED_UNTIL.get(back)) && Sickness.eased(back, level.getGameTime()),
			"the ease was lost in the save");

		CompoundTag oldTag = new CompoundTag();
		old.save(oldTag);
		helper.assertFalse(oldTag.toString().contains("ill_kind"), "an ordinary illness saves a kind: " + oldTag);
		Villager oldBack = reload(helper, old);
		helper.assertTrue(Sickness.isIll(oldBack) && !Sickness.isGrey(oldBack) && !Sickness.isHexed(oldBack) && !Sickness.isPlagueIll(oldBack),
			"an old save's illness isn't ordinary: kind " + ModAttachments.ILL_KIND.get(oldBack));
		oldBack.setPos(old.position());
		old.discard();
		level.addFreshEntity(oldBack);
		Sickness.round(level, oldBack, 20);
		helper.assertFalse(Sickness.isIll(oldBack), "the old save's ordinary illness didn't pass with time");
		helper.succeed();
	}

	private static Villager reload(GameTestHelper helper, Villager villager) {
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(villager.save(tag), "the villager wasn't saved");
		Entity loaded = EntityType.loadEntityRecursive(tag, helper.getLevel(), e -> e);
		helper.assertTrue(loaded instanceof Villager && loaded != villager, "the save didn't load as a villager: " + loaded);
		return (Villager) loaded;
	}

	// ---- 31.18: the hand-in ----------------------------------------------------------------------------------------

	/**
	 * Handing in, adversarially: a herb not named isn't taken; 2 of a herb aren't taken; of 9 only 3 are; a herb already
	 * handed in isn't taken again; 3 split over two stacks count; the last herb from the off hand ends the chapter. What
	 * isn't wanted stays in the bag.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaSickHandIn"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaSickHandIn")
	public void theHandInTakesOnlyThreeOfEachNamedHerb(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		village(helper, 8);
		Villager nurse = nurse(helper, new BlockPos(20, 2, 20));
		ServerPlayer player = player(helper);
		setDay(level, Chronicle.day(level) + 1, 1000);
		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null, "the arc didn't start");
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		List<String> named = SicknessStory.herbs(s).stream().map(h -> BuiltInRegistries.BLOCK.getKey(h).getPath()).toList();
		helper.assertTrue(named.equals(List.of("sea_lavender", "mirewort", "glowcap")), "named: " + named);

		player.getInventory().setItem(9, new ItemStack(ModBlocks.PETAL_BALM, 3));
		player.getInventory().setItem(10, new ItemStack(ModBlocks.SEA_LAVENDER, 2));
		player.getInventory().setItem(11, new ItemStack(ModBlocks.MIREWORT, 2));
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		helper.assertTrue(count(player, ModBlocks.PETAL_BALM) == 3 && count(player, ModBlocks.SEA_LAVENDER) == 2 && count(player, ModBlocks.MIREWORT) == 2,
			"something was taken: balm " + count(player, ModBlocks.PETAL_BALM) + ", lavender " + count(player, ModBlocks.SEA_LAVENDER)
				+ ", mirewort " + count(player, ModBlocks.MIREWORT));
		helper.assertTrue(s.chapter == 1 && s.helpers.isEmpty(), "a wrong hand-in counted: chapter " + s.chapter + ", helpers " + s.helpers);

		player.getInventory().setItem(10, new ItemStack(ModBlocks.SEA_LAVENDER, 9));
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		helper.assertTrue(count(player, ModBlocks.SEA_LAVENDER) == 6, "of 9 Sea Lavender, left: " + count(player, ModBlocks.SEA_LAVENDER));
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		helper.assertTrue(count(player, ModBlocks.SEA_LAVENDER) == 6, "Sea Lavender taken twice: left " + count(player, ModBlocks.SEA_LAVENDER));
		helper.assertTrue(s.chapter == 1, "the chapter ended with one herb");

		player.getInventory().setItem(20, new ItemStack(ModBlocks.MIREWORT, 1)); // 2 + 1 in two stacks
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		helper.assertTrue(count(player, ModBlocks.MIREWORT) == 0, "3 Mirewort over two stacks weren't taken: left " + count(player, ModBlocks.MIREWORT));
		helper.assertTrue(s.chapter == 1, "the chapter ended with two herbs");

		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModBlocks.GLOWCAP, 3));
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		helper.assertTrue(player.getOffhandItem().isEmpty(), "the Glowcap in the off hand wasn't taken");
		helper.assertTrue(s.chapter == 2 && s.started, "all three handed in, the brew didn't begin: chapter " + s.chapter);
		helper.assertTrue(count(player, ModBlocks.PETAL_BALM) == 3 && count(player, ModBlocks.SEA_LAVENDER) == 6, "what wasn't wanted left the bag");
		helper.succeed();
	}

	// ---- 31.18: the cure round -------------------------------------------------------------------------------------

	/**
	 * The tonic goes round only to the coughing: a villager ill with an ordinary illness and a hexed one stay as they are
	 * once the arc is over, and the coughing are all well; nobody who wasn't coughing is counted among the ill.
	 */
	//$ gametest_ticks_batch TALL '200' '"qaSickOnlyCoughers"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaSickOnlyCoughers")
	public void theTonicGoesOnlyToTheCoughing(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> people = village(helper, 10);
		Villager nurse = nurse(helper, new BlockPos(20, 2, 20));
		ServerPlayer player = player(helper);
		long day = Chronicle.day(level) + 1;
		setDay(level, day, 1000);
		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null && grey(people) == 3, "the arc didn't start");
		List<Villager> well = people.stream().filter(v -> !Sickness.isIll(v)).toList();
		Villager ordinary = well.get(0);
		Villager hexed = well.get(1);
		Sickness.fallIll(level, ordinary);
		Sickness.hex(level, hexed);
		helper.assertTrue(Sickness.isIll(ordinary) && !Sickness.isGrey(ordinary) && Sickness.isHexed(hexed), "the other illnesses didn't take");
		gather(helper, level, s, player, nurse);
		level.setDayTime(SicknessStory.stamp(s, "brew_from") + SicknessStory.BREW);
		runRound(level, hall, s, 40);
		helper.assertTrue(Arcs.running(level, hall) == null, "the arc isn't over");
		helper.assertTrue(grey(people) == 0, "still coughing: " + grey(people));
		helper.assertTrue(Sickness.isIll(ordinary) && !Sickness.isGrey(ordinary), "the tonic went to a villager with an ordinary illness");
		helper.assertTrue(Sickness.isHexed(hexed), "the tonic lifted a hex");
		List<UUID> ill = SicknessStory.caught(s);
		helper.assertFalse(ill.contains(ordinary.getUUID()) || ill.contains(hexed.getUUID()) || ill.contains(nurse.getUUID()),
			"one who wasn't coughing counted among the ill");
		helper.assertTrue(Friendship.points(ordinary, player.getUUID()) < SicknessStory.FRIENDSHIP, "the ordinary ill villager thanked the gatherer as if cured");
		helper.succeed();
	}

	/**
	 * Every gatherer is thanked and nobody else: two players hand in the herbs between them (two and one); a third stands by.
	 * Each gatherer gets +150 standing, the honour Healer of the village ("Healer of <village>") and +100 friendship with
	 * each villager who was ill; the bystander gets none of it.
	 */
	//$ gametest_ticks_batch TALL '200' '"qaSickGatherers"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaSickGatherers")
	public void everyGathererIsThankedAndNobodyElse(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> people = village(helper, 10);
		Villager nurse = nurse(helper, new BlockPos(20, 2, 20));
		ServerPlayer first = player(helper);
		ServerPlayer second = player(helper);
		ServerPlayer bystander = player(helper);
		setDay(level, Chronicle.day(level) + 1, 1000);
		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null, "the arc didn't start");
		SicknessStory.onUse(bystander, level, InteractionHand.MAIN_HAND, nurse); // the bystander only talks
		List<HerbBlock> herbs = SicknessStory.herbs(s);
		helper.assertTrue(herbs.size() == 3, "herbs: " + herbs);
		first.getInventory().add(new ItemStack(herbs.get(0), 3));
		first.getInventory().add(new ItemStack(herbs.get(1), 3));
		SicknessStory.onUse(first, level, InteractionHand.MAIN_HAND, nurse);
		second.getInventory().add(new ItemStack(herbs.get(2), 3));
		SicknessStory.onUse(second, level, InteractionHand.MAIN_HAND, nurse);
		helper.assertTrue(s.chapter == 2, "the brew didn't begin: chapter " + s.chapter);
		level.setDayTime(SicknessStory.stamp(s, "brew_from") + SicknessStory.BREW);
		runRound(level, hall, s, 40);
		helper.assertTrue(Arcs.running(level, hall) == null, "the arc isn't over");
		List<UUID> ill = SicknessStory.caught(s);
		helper.assertTrue(ill.size() >= 3, "caught: " + ill.size());
		String village = VillageHalls.name(level, hall).getString();
		for (ServerPlayer gatherer : List.of(first, second)) {
			String who = gatherer == first ? "the first gatherer" : "the second gatherer";
			helper.assertTrue(Reputation.points(level, hall, gatherer.getUUID()) >= 150, who + "'s standing: " + Reputation.points(level, hall, gatherer.getUUID()));
			helper.assertTrue(Reputation.honours(level, hall, gatherer.getUUID()).contains("healer"), who + " has no Healer honour");
			for (Villager v : people) {
				if (ill.contains(v.getUUID())) {
					helper.assertTrue(Friendship.points(v, gatherer.getUUID()) >= SicknessStory.FRIENDSHIP,
						who + "'s friendship with " + v.getCustomName().getString() + ": " + Friendship.points(v, gatherer.getUUID()));
				}
			}
		}
		helper.assertTrue(Reputation.points(level, hall, bystander.getUUID()) < 150, "the bystander's standing: " + Reputation.points(level, hall, bystander.getUUID()));
		helper.assertFalse(Reputation.honours(level, hall, bystander.getUUID()).contains("healer"), "the bystander got the Healer honour");
		for (Villager v : people) {
			helper.assertTrue(Friendship.points(v, bystander.getUUID()) < SicknessStory.FRIENDSHIP, "the bystander's friendship with " + v.getCustomName().getString());
		}
		String honour = Reputation.honourName("healer", VillageHalls.name(level, hall)).getString();
		helper.assertTrue(honour.equals("Healer of " + village), "the honour reads: " + honour);
		helper.succeed();
	}

	// ---- 31.18: the healer lost or moved ---------------------------------------------------------------------------

	/**
	 * The Nurse dies during the brew: Doctor Wren is sent for, comes the next morning, takes up the tonic and takes it
	 * round; the arc ends with everyone well, not by running its course.
	 */
	//$ gametest_ticks_batch TALL '200' '"qaSickNurseDies"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaSickNurseDies")
	public void theNurseDiesMidBrewAndWrenFinishes(GameTestHelper helper) {
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
		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null, "the arc didn't start");
		gather(helper, level, s, player, nurse);
		level.setDayTime(level.getDayTime() + SicknessStory.BREW / 4);
		SicknessStory.tick(level);
		Arcs.NEAR = 96; // a player is about (the herb patches are 200 blocks and more away)
		nurse.kill();
		SicknessStory.tick(level);
		helper.assertTrue(SicknessStory.stamp(s, "wren_due") == day + 1, "Doctor Wren wasn't sent for: due " + SicknessStory.stamp(s, "wren_due"));
		setDay(level, day + 1, 1000);
		SicknessStory.tick(level);
		Villager wren = SicknessStory.healer(level, s);
		helper.assertTrue(wren != null && wren.getCustomName() != null && wren.getCustomName().getString().equals("Doctor Wren"), "no Doctor Wren the next morning");
		runRound(level, hall, s, 40);
		helper.assertTrue(Arcs.running(level, hall) == null && s.flags.containsKey(SicknessStory.CURED), "the cure didn't go round after the nurse died");
		helper.assertTrue(grey(people) == 0, "still coughing: " + grey(people));
		helper.assertTrue(chronicle(entity).stream().anyMatch(l -> l.startsWith("The Three-Herb Tonic went round")), "the chronicle: " + chronicle(entity));
		helper.succeed();
	}

	/**
	 * The brewing stand broken during the brew (and the nurse's memory of it gone, as a villager's brain drops a lost
	 * workstation): the brew still finishes and the tonic still goes round.
	 */
	//$ gametest_ticks_batch TALL '200' '"qaSickStandBroken"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaSickStandBroken")
	public void theBrewingStandBrokenMidBrew(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> people = village(helper, 8);
		BlockPos stand = new BlockPos(20, 2, 20);
		Villager nurse = nurse(helper, stand);
		ServerPlayer player = player(helper);
		setDay(level, Chronicle.day(level) + 1, 1000);
		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null, "the arc didn't start");
		gather(helper, level, s, player, nurse);
		long from = SicknessStory.stamp(s, "brew_from");
		helper.destroyBlock(stand);
		nurse.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
		level.setDayTime(from + SicknessStory.BREW / 2);
		SicknessStory.tick(level);
		WorkerStatus.Entry status = WorkerStatus.get(nurse, level.getGameTime());
		helper.assertTrue(status != null && status.title().getString().equals("Brewing the cure"), "no brewing status with the stand gone");
		level.setDayTime(from + SicknessStory.BREW);
		runRound(level, hall, s, 40);
		helper.assertTrue(Arcs.running(level, hall) == null && s.flags.containsKey(SicknessStory.CURED), "the cure didn't go round with the stand broken");
		helper.assertTrue(grey(people) == 0 && nurse.getMainHandItem().isEmpty(), "still coughing: " + grey(people));
		helper.succeed();
	}

	/**
	 * Doctor Wren all the way: sent for (the player is told a travelling physician comes), at the hall the next morning,
	 * told and handed the herbs, he brews by the hall (his walk goes there), takes the tonic round, and is gone once the
	 * arc is over.
	 */
	//$ gametest_ticks_batch TALL '200' '"qaSickWrenThrough"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaSickWrenThrough")
	public void doctorWrenBrewsByTheHallAndLeaves(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> people = village(helper, 8);
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, heard);
		long day = Chronicle.day(level) + 1;
		setDay(level, day, 1000);
		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null, "the arc didn't start");
		Component sent = QaStageFourGameTests.find(heard, "message.aliveworkplace.sickness.wren_sent");
		helper.assertTrue(sent != null, "the player wasn't told a physician is sent for: " + QaStageFourGameTests.keys(heard));
		assertAmerican(helper, "wren_sent", sent.getString());
		setDay(level, day + 1, 1000);
		SicknessStory.tick(level);
		Villager wren = SicknessStory.healer(level, s);
		helper.assertTrue(wren != null, "no Doctor Wren");
		Component arrives = QaStageFourGameTests.find(heard, "message.aliveworkplace.sickness.wren_arrives");
		helper.assertTrue(arrives != null && arrives.getString().contains("Doctor Wren"), "the player wasn't told Wren came");
		assertAmerican(helper, "wren_arrives", arrives.getString());
		chronicle(entity).forEach(l -> assertAmerican(helper, "the chronicle", l));
		gather(helper, level, s, player, wren);
		level.setDayTime(SicknessStory.stamp(s, "brew_from") + SicknessStory.BREW / 2);
		SicknessStory.tick(level);
		WorkerStatus.Entry status = WorkerStatus.get(wren, level.getGameTime());
		helper.assertTrue(status != null && status.title().getString().equals("Brewing the cure") && Math.abs(status.progress() - 0.5f) < 0.02f,
			"Wren's status: " + (status == null ? null : status.title().getString() + " " + status.progress()));
		BlockPos goal = wren.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(t -> t.getTarget().currentBlockPosition()).orElse(wren.blockPosition());
		double dx = goal.getX() - hall.getX();
		double dz = goal.getZ() - hall.getZ();
		helper.assertTrue(dx * dx + dz * dz <= 5 * 5, "Wren doesn't brew by the hall: he goes to " + goal + ", the hall is at " + hall);
		level.setDayTime(SicknessStory.stamp(s, "brew_from") + SicknessStory.BREW);
		runRound(level, hall, s, 40);
		helper.assertTrue(Arcs.running(level, hall) == null && s.flags.containsKey(SicknessStory.CURED), "Wren's cure didn't go round");
		helper.assertTrue(grey(people) == 0, "still coughing: " + grey(people));
		helper.assertTrue(!wren.isAlive() || wren.isRemoved(), "Doctor Wren stayed after the arc was over");
		helper.succeed();
	}

	// ---- 31.18: every sentence a player reads ----------------------------------------------------------------------

	/**
	 * Every new sentence on the way through, as a player reads it: the chapter headers and intros, the outbreak, "see the
	 * healer", the healer's words (told, still wanted, thanks, brewing at 40%, taking it round), the herbs, the brew, the
	 * cure; the chatter; the chronicle; the hall's line for a coughing villager; the honour. All rendered with their
	 * arguments, in American English ("Gray"), naming what the spec names.
	 */
	//$ gametest_ticks_batch TALL '200' '"qaSickWords"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaSickWords")
	public void everySentenceAPlayerReads(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> people = village(helper, 10);
		Villager nurse = nurse(helper, new BlockPos(20, 2, 20));
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, heard);
		setDay(level, Chronicle.day(level) + 1, 1000);
		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null, "the arc didn't start");
		Component outbreak = QaStageFourGameTests.find(heard, "message.aliveworkplace.sickness.outbreak");
		helper.assertTrue(outbreak != null, "no outbreak line: " + QaStageFourGameTests.keys(heard));
		String sick = people.stream().filter(Sickness::isGrey).findFirst().map(v -> v.getCustomName().getString()).orElse("?");
		helper.assertTrue(outbreak.getString().contains("Gray Cough") && outbreak.getString().contains(sick), "the outbreak line: " + outbreak.getString());
		Component see = QaStageFourGameTests.find(heard, "message.aliveworkplace.sickness.see_healer");
		helper.assertTrue(see != null && see.getString().contains("Nurse Ada"), "the see-the-healer line: " + (see == null ? null : see.getString()));
		Villager coughing = people.stream().filter(Sickness::isGrey).findFirst().orElseThrow();
		ItemLore lore = VillageHallScreen.person(level, hall, coughing).get(DataComponents.LORE);
		List<String> lines = lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
		helper.assertTrue(lines.stream().anyMatch(l -> l.startsWith("The Gray Cough")), "the hall's page for a coughing villager: " + lines);
		for (String key : Arcs.chatter(level, hall)) {
			assertAmerican(helper, "chatter " + key, Component.translatable(key).getString());
		}

		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		Component herbs = QaStageFourGameTests.find(heard, "message.aliveworkplace.sickness.herbs");
		helper.assertTrue(herbs != null && herbs.getString().contains("Sea Lavender, Mirewort and Glowcap") && herbs.getString().contains("Bring 3 of each"),
			"the herbs line: " + (herbs == null ? null : herbs.getString()));
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse); // nothing in hand: what's still wanted
		player.getInventory().add(new ItemStack(ModBlocks.SEA_LAVENDER, 3));
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		player.getInventory().add(new ItemStack(ModBlocks.MIREWORT, 3));
		player.getInventory().add(new ItemStack(ModBlocks.GLOWCAP, 3));
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		helper.assertTrue(s.chapter == 2, "the brew didn't begin");
		long from = SicknessStory.stamp(s, "brew_from");
		level.setDayTime(from + SicknessStory.BREW * 2 / 5);
		SicknessStory.tick(level);
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		for (String key : Arcs.chatter(level, hall)) {
			assertAmerican(helper, "chatter " + key, Component.translatable(key).getString());
		}
		level.setDayTime(from + SicknessStory.BREW);
		SicknessStory.tick(level);
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		runRound(level, hall, s, 40);
		helper.assertTrue(Arcs.running(level, hall) == null, "the arc isn't over");

		List<String> said = heard.stream().map(Component::getString).toList();
		for (String line : said) {
			assertAmerican(helper, "a chat line", line);
		}
		helper.assertTrue(said.stream().anyMatch(l -> l.contains("The tonic is brewing: 40% done")), "the brewing talk at 40%: " + said);
		helper.assertTrue(said.stream().anyMatch(l -> l.contains("I still need 3 of each: Sea Lavender, Mirewort and Glowcap")), "the still-wanted talk: " + said);
		helper.assertTrue(said.stream().anyMatch(l -> l.contains("Sea Lavender, good. I still need 3 of each: Mirewort and Glowcap")), "the thanks-and-more talk: " + said);
		helper.assertTrue(said.stream().anyMatch(l -> l.contains("The tonic is ready.")), "the taking-round talk: " + said);
		helper.assertTrue(said.stream().anyMatch(l -> l.startsWith("The Gray Cough is gone: ") && l.contains("villagers are well again")), "the cure line: " + said);
		helper.assertTrue(said.stream().anyMatch(l -> l.contains("The Gray Cough")) && said.stream().anyMatch(l -> l.contains("Three Far Cures"))
			&& said.stream().anyMatch(l -> l.contains("The Remedy")), "the chapter headers: " + said);
		for (String line : chronicle(entity)) {
			assertAmerican(helper, "the chronicle", line);
		}
		helper.assertTrue(chronicle(entity).stream().anyMatch(l -> l.contains("Nurse Ada brewed the Three-Herb Tonic")), "the chronicle's brew line: " + chronicle(entity));
		String honour = Reputation.honourName("healer", VillageHalls.name(level, hall)).getString();
		assertAmerican(helper, "the honour", honour);
		helper.assertTrue(honour.startsWith("Healer of "), "the honour: " + honour);
		helper.succeed();
	}

	// ---- 31.17: the herbs ------------------------------------------------------------------------------------------

	/**
	 * Each herb's biome tag holds every biome the spec names, and each tag file adds to (doesn't replace) the tag, so a
	 * datapack or a biome mod can add biomes of its own.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void herbBiomeTagsHoldTheSpecsBiomesAndCanBeExtended(GameTestHelper helper) {
		Map<HerbBlock, List<String>> spec = new LinkedHashMap<>();
		spec.put(ModBlocks.FROSTBLOOM, List.of("snowy_plains", "snowy_slopes", "grove", "ice_spikes", "snowy_taiga"));
		spec.put(ModBlocks.SUNTHISTLE, List.of("desert", "badlands", "eroded_badlands", "wooded_badlands"));
		spec.put(ModBlocks.MIREWORT, List.of("swamp", "mangrove_swamp"));
		spec.put(ModBlocks.PEAKMOSS, List.of("windswept_hills", "stony_peaks", "jagged_peaks", "meadow"));
		spec.put(ModBlocks.JUNGLE_ORCHID, List.of("jungle", "sparse_jungle", "bamboo_jungle"));
		spec.put(ModBlocks.SEA_LAVENDER, List.of("beach", "snowy_beach", "stony_shore"));
		spec.put(ModBlocks.GLOWCAP, List.of("dark_forest"));
		spec.put(ModBlocks.PETAL_BALM, List.of("cherry_grove"));
		helper.assertTrue(spec.size() == Herbs.all().size(), "not eight herbs");
		var biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
		var resources = helper.getLevel().getServer().getResourceManager();
		for (Map.Entry<HerbBlock, List<String>> e : spec.entrySet()) {
			HerbBlock herb = e.getKey();
			ResourceLocation tag = Herbs.biomes(herb).location();
			String path = BuiltInRegistries.BLOCK.getKey(herb).getPath();
			helper.assertTrue(tag.toString().equals("aliveworkplace:herb_biomes/" + path), "the tag's id: " + tag);
			for (String biome : e.getValue()) {
				var holder = biomes.getHolderOrThrow(ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace(biome)));
				helper.assertTrue(holder.is(Herbs.biomes(herb)), path + " doesn't grow in " + biome);
			}
			ResourceLocation file = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "tags/worldgen/biome/herb_biomes/" + path + ".json");
			var stack = resources.getResourceStack(file);
			helper.assertTrue(!stack.isEmpty(), "no tag file " + file);
			for (var resource : stack) {
				try (Reader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
					JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
					helper.assertFalse(json.has("replace") && json.get("replace").getAsBoolean(), file + " replaces the tag: a datapack can't add to it");
				} catch (java.io.IOException ex) {
					helper.fail("can't read " + file + ": " + ex);
				}
			}
		}
		helper.succeed();
	}

	/**
	 * Patches on the snowy ground of their own biomes: a snowy plain (grass under a layer of snow) takes a Frostbloom
	 * patch, a snowy beach (sand under a layer of snow) takes a Sea Lavender patch, 5 to 9 each, on that ground.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaSickSnowyPatch"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaSickSnowyPatch")
	public void patchesGrowOnSnowyGround(GameTestHelper helper) {
		Leftovers.clear(helper);
		openSky(helper);
		ServerLevel level = helper.getLevel();
		BlockState snow = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1);
		for (Object[] c : new Object[][] {{ModBlocks.FROSTBLOOM, Blocks.GRASS_BLOCK}, {ModBlocks.SEA_LAVENDER, Blocks.SAND}}) {
			HerbBlock herb = (HerbBlock) c[0];
			net.minecraft.world.level.block.Block ground = (net.minecraft.world.level.block.Block) c[1];
			for (int x = 2; x <= 28; x++) {
				for (int z = 2; z <= 28; z++) {
					helper.setBlock(new BlockPos(x, 1, z), ground);
					helper.setBlock(new BlockPos(x, 2, z), snow);
				}
			}
			int planted = Herbs.patch(level, helper.absolutePos(new BlockPos(15, 2, 15)), herb, RandomSource.create(5));
			int found = 0;
			for (BlockPos p : BlockPos.betweenClosed(0, 1, 0, 29, 8, 29)) {
				if (helper.getBlockState(p).is(herb)) {
					found++;
					helper.assertTrue(helper.getBlockState(p.below()).is(ground), path(herb) + " off its ground at " + p);
				}
			}
			helper.assertTrue(planted >= Herbs.LEAST && planted <= Herbs.MOST && found == planted,
				path(herb) + " on snowy " + path(ground) + ": planted " + planted + ", found " + found);
			for (BlockPos p : BlockPos.betweenClosed(0, 1, 0, 29, 8, 29)) {
				if (helper.getBlockState(p).is(herb)) {
					helper.setBlock(p, Blocks.AIR);
				}
			}
		}
		helper.succeed();
	}

	private static String path(net.minecraft.world.level.block.Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getPath();
	}

	/** Takes away the barriers over the area, so the sky is open. */
	static void openSky(GameTestHelper helper) {
		AABB box = helper.getBounds();
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}
}
