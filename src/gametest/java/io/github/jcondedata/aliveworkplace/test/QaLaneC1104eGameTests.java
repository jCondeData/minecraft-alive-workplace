package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes;
import io.github.jcondedata.aliveworkplace.guard.MilitiaCombat;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Conscription;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.Words;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane C, 2026-11-04, run e): checks of the fixes B878/B920/B931 (crafter-family counted names read in the plural,
 * one in the singular), B926 (a conscript goes for the nearest raider within 24 blocks, up and down too), and tests that
 * kill two survivors of the nightly mutation report: the Village Hall's job hand-out to a baby or a nitwit
 * ({@code VillageHalls.assign}) and a reform story step shown done before a hand-in ({@code ReformStories.step}, B917).
 */
public class QaLaneC1104eGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation CONSCRIPTION = AliveWorkplace.id("conscription");

	// ---- B878 / B920 / B931: counted names ---------------------------------------------------------------------------

	/** Names that read the same for one or many (mass nouns, or names already plural): a missing plural is right. */
	private static boolean massOrPlural(String one) {
		return one.endsWith("Wool") || one.endsWith("Berries") || one.equals("Paper") || one.equals("Leather")
			|| one.equals("String") || one.equals("Lapis Lazuli") || one.endsWith("Clothes") || one.endsWith("Robes")
			|| one.equals("Drowned") || one.equals("Bogged") || one.equals("Silverfish"); // creatures whose plural is their name
	}

	/**
	 * B920's newly named things: one reads the item's own name (1 Stick, 1 Egg, 1 Cherry Log), any other count reads the
	 * plural (2, 8, 64, and 0 too), and the plural differs from the one-form.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void newlyNamedMakingsReadOneAsOneAndManyAsMany(GameTestHelper helper) {
		Map<Item, String> plural = new LinkedHashMap<>();
		plural.put(Items.STICK, "Sticks");
		plural.put(Items.COPPER_INGOT, "Copper Ingots");
		plural.put(Items.IRON_NUGGET, "Iron Nuggets");
		plural.put(Items.GOLD_NUGGET, "Gold Nuggets");
		plural.put(Items.EGG, "Eggs");
		plural.put(Items.BUCKET, "Buckets");
		plural.put(Items.CHERRY_LOG, "Cherry Logs");
		plural.put(Items.JUNGLE_LOG, "Jungle Logs");
		plural.put(Items.CRIMSON_STEM, "Crimson Stems");
		plural.put(Items.WARPED_STEM, "Warped Stems");
		plural.put(Items.CLAY_BALL, "Clay Balls");
		plural.put(Items.FEATHER, "Feathers");
		plural.put(Items.DIAMOND, "Diamonds");
		plural.put(Items.ENDER_PEARL, "Ender Pearls");
		plural.put(Items.COOKED_PORKCHOP, "Cooked Porkchops");
		plural.put(Items.BEETROOT_SOUP, "Bowls of Beetroot Soup");
		List<String> wrong = new ArrayList<>();
		plural.forEach((item, many) -> {
			String one = item.getDescription().getString();
			check(wrong, item + " x1", Words.itemName(item, 1).getString(), one);
			for (long n : new long[] {0, 2, 8, 64}) {
				check(wrong, item + " x" + n, Words.itemName(item, n).getString(), many);
			}
			if (one.equals(many)) {
				wrong.add(item + ": the plural is the one-form '" + one + "'");
			}
		});
		helper.assertTrue(wrong.isEmpty(), "counted names: " + wrong);
		helper.succeed();
	}

	/**
	 * The crafter's own shortage line (B426/B920) through {@code JobShortages.lackLine}: many inputs and many outputs in the
	 * plural, one in the singular, in the same sentence.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aShortageLineNamesManyInThePluralAndOneInTheSingular(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<Item, Integer> lack = new LinkedHashMap<>();
		lack.put(Items.STICK, 2);
		lack.put(Items.COPPER_INGOT, 1);
		check(wrong, "pickaxe", JobShortages.lackLine(lack, 1, Items.IRON_PICKAXE).getString(), "short of 2 Sticks, 1 Copper Ingot to make 1 Iron Pickaxe");
		Map<Item, Integer> nuggets = new LinkedHashMap<>();
		nuggets.put(Items.GOLD_NUGGET, 3);
		nuggets.put(Items.EGG, 1);
		check(wrong, "pies", JobShortages.lackLine(nuggets, 2, Items.PUMPKIN_PIE).getString(), "short of 3 Gold Nuggets, 1 Egg to make 2 Pumpkin Pies");
		Map<Item, Integer> stems = new LinkedHashMap<>();
		stems.put(Items.CRIMSON_STEM, 1);
		check(wrong, "one stem", JobShortages.lackLine(stems, 4, Items.CRIMSON_PLANKS).getString(), "short of 1 Crimson Stem to make 4 Crimson Planks");
		helper.assertTrue(wrong.isEmpty(), "shortage lines: " + wrong);
		helper.succeed();
	}

	/**
	 * Data-driven, from the lang: every {@code plural.aliveworkplace.<namespace>.<item>} key names an item that exists
	 * (or belongs to a mod not loaded), is not empty, holds no format argument, and differs from the item's own name
	 * (but for names already plural); every creature plural differs from the creature's name.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyPluralInTheLangIsANonEmptyPluralOfARealThing(GameTestHelper helper) {
		JsonObject lang;
		try (var in = QaLaneC1104eGameTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new IllegalStateException("can't read en_us.json", e);
		}
		List<String> wrong = new ArrayList<>();
		int items = 0;
		int creatures = 0;
		for (String key : lang.keySet()) {
			if (!key.startsWith("plural.aliveworkplace.")) {
				continue;
			}
			String value = lang.get(key).getAsString();
			if (value.isBlank() || value.contains("%") || value.contains("aliveworkplace.")) {
				wrong.add(key + " = '" + value + "'");
				continue;
			}
			String rest = key.substring("plural.aliveworkplace.".length());
			if (rest.startsWith("profession.")) {
				continue;
			}
			boolean entity = rest.startsWith("entity.");
			if (entity) {
				rest = rest.substring("entity.".length());
			}
			int dot = rest.indexOf('.');
			ResourceLocation id = dot < 0 ? null : ResourceLocation.tryBuild(rest.substring(0, dot), rest.substring(dot + 1));
			if (id == null) {
				wrong.add(key + ": not <namespace>.<path>");
				continue;
			}
			boolean ours = id.getNamespace().equals("minecraft") || id.getNamespace().equals(AliveWorkplace.MOD_ID);
			String one;
			if (entity) {
				if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
					if (ours) {
						wrong.add(key + ": no such creature");
					}
					continue;
				}
				one = BuiltInRegistries.ENTITY_TYPE.get(id).getDescription().getString();
				creatures++;
			} else {
				if (!BuiltInRegistries.ITEM.containsKey(id)) {
					if (ours) {
						wrong.add(key + ": no such item");
					}
					continue;
				}
				Item item = BuiltInRegistries.ITEM.get(id);
				one = item.getDescription().getString();
				check(wrong, key + " via Words x3", Words.itemName(item, 3).getString(), value);
				items++;
			}
			if (value.equals(one) && !massOrPlural(one)) {
				wrong.add(key + ": the plural is the one-form '" + one + "'");
			}
		}
		helper.assertTrue(items > 300 && creatures > 20, "setup: " + items + " item plurals, " + creatures + " creature plurals checked");
		helper.assertTrue(wrong.isEmpty(), "plural keys: " + wrong);
		helper.succeed();
	}

	/**
	 * Every countable thing a crafter-family worker counts: the mod's luxury recipes' inputs (a Jeweler "short of 2
	 * Amethyst Shards") and outputs, read in the plural for a count above one (one in the singular), unless the name is
	 * a mass noun or already plural.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyCountableLuxuryInputHasAPlural(GameTestHelper helper) {
		Set<Item> items = new LinkedHashSet<>();
		int recipes = 0;
		for (LuxuryRecipes.Recipe r : LuxuryRecipes.all().values()) {
			if (!r.id().getNamespace().equals(AliveWorkplace.MOD_ID)) {
				continue; // the GameTests' own data pack
			}
			recipes++;
			for (LuxuryRecipes.Input in : r.inputs()) {
				if (in.item().isPresent()) {
					items.add(in.item().get());
				}
			}
			if (r.output() != Items.AIR) {
				items.add(r.output());
			}
		}
		helper.assertTrue(recipes >= 10 && items.contains(Items.AMETHYST_SHARD) && items.contains(Items.GOLD_NUGGET), "setup: " + recipes + " recipes, items " + items);
		List<String> wrong = new ArrayList<>();
		for (Item item : items) {
			String one = item.getDescription().getString();
			String many = Words.itemName(item, 2).getString();
			if (!Words.itemName(item, 1).getString().equals(one)) {
				wrong.add(one + ": 1 reads '" + Words.itemName(item, 1).getString() + "'");
			}
			if (many.equals(one) && !massOrPlural(one)) {
				wrong.add(BuiltInRegistries.ITEM.getKey(item) + ": '2 " + many + "'");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "counted as one after 2: " + wrong);
		helper.succeed();
	}

	private static void check(List<String> wrong, String what, String got, String want) {
		if (!got.equals(want)) {
			wrong.add(what + ": '" + got + "' (want '" + want + "')");
		}
	}

	// ---- B926: a conscript's reach, up and down ----------------------------------------------------------------------

	private static void village(GameTestHelper helper, int size) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = size;
		long time = helper.getLevel().getDayTime();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			helper.getLevel().setDayTime(time);
			VillageRaids.forget();
			VillageNeeds.forget();
			CivicEffects.forget();
			Conscription.forget();
			Moods.forget();
		});
	}

	/** Conscription in force at the hall at {@code at}, a Village, at night. */
	private static void conscription(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(at);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setRank(VillageRanks.Rank.VILLAGE);
		entity.setReforms(List.of());
		entity.setRaidWorkUntil(0);
		entity.setEdicts(List.of(new Edicts.InForce(CONSCRIPTION.toString(), Chronicle.day(level))));
		Leftovers.after(helper, () -> {
			entity.setEdicts(List.of());
			level.setBlockAndUpdate(hall, Blocks.AIR.defaultBlockState());
		});
		level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + 14000);
	}

	private static Villager conscript(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		villager.setVillagerXp(10);
		villager.refreshBrain(helper.getLevel());
		return villager;
	}

	private static Zombie raider(GameTestHelper helper, BlockPos at) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, at);
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		zombie.addTag(VillageRaids.TAG);
		return zombie;
	}

	/**
	 * B926 from below (the existing tests look down from towers): a conscript on the ground with a raider 16 blocks above
	 * him and 12 across (on a cliff top, 20 blocks away) is drawn into the fight; another with one 16 above and 21 across
	 * (26.4 away) never is; the reformed work stop's "near" agrees with each. (The huge area's barrier roof at 21 keeps a
	 * raider from standing a full 23 to 25 blocks straight up inside it.)
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaLc1104eAbove"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaLc1104eAbove")
	public void aRaiderAboveWithin24BlocksDrawsAConscriptOneBeyondDoesNot(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 32);
		Leftovers.halls(helper);
		BlockPos hallAt = new BlockPos(15, 2, 15);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		BlockPos nearAt = new BlockPos(3, 2, 3);
		BlockPos farAt = new BlockPos(3, 2, 26);
		helper.setBlock(new BlockPos(15, 17, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(24, 17, 26), Blocks.STONE);
		boolean[] fought = new boolean[2];
		// B978: how far the NEAREST raider was when each conscript's fight began (he goes for the nearest in range, so the
		// far one, wandering, may turn on the near raider once he is within 24 of it), and where he stood.
		double[] startedAt = {-1, -1};
		String[] startedHow = {"", ""};
		Villager[] watched = new Villager[2];
		Zombie[] foes = new Zombie[2];
		helper.onEachTick(() -> {
			for (int i = 0; i < 2; i++) {
				if (!fought[i] && watched[i] != null && MilitiaCombat.isFighting(watched[i])) {
					fought[i] = true;
					double toNear = Math.sqrt(watched[i].distanceToSqr(foes[0]));
					double toFar = Math.sqrt(watched[i].distanceToSqr(foes[1]));
					startedAt[i] = Math.min(toNear, toFar);
					startedHow[i] = String.format("at %s, %.1f from the near raider and %.1f from the far one",
						helper.relativeVec(watched[i].position()), toNear, toFar);
				}
			}
		});
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			conscription(helper, hallAt);
			Villager near = conscript(helper, nearAt);
			Villager far = conscript(helper, farAt);
			Zombie nearRaider = raider(helper, new BlockPos(15, 18, 3));
			Zombie farRaider = raider(helper, new BlockPos(24, 18, 26));
			double n = Math.sqrt(near.distanceToSqr(nearRaider));
			double f = Math.sqrt(far.distanceToSqr(farRaider));
			double nUp = nearRaider.getY() - near.getY();
			double fUp = farRaider.getY() - far.getY();
			helper.assertTrue(n < 24 && f > 24 && nUp > 15 && fUp > 15, String.format("setup: raiders %.1f (%.1f up) and %.1f (%.1f up) away", n, nUp, f, fUp));
			VillageRaids.track(level, helper.absolutePos(hallAt), 2);
			CivicEffects.forget();
			Conscription.forget();
			foes[0] = nearRaider;
			foes[1] = farRaider;
			watched[0] = near;
			watched[1] = far;
			boolean nearCounted = Conscription.raiderNear(level, near, 24);
			boolean farCounted = Conscription.raiderNear(level, far, 24);
			helper.assertTrue(nearCounted && !farCounted, "the work stop's near disagrees with the distances: " + nearCounted + ", " + farCounted);
			helper.runAfterDelay(150, () -> {
				boolean armed = Conscription.militia(near) != null && Conscription.militia(far) != null;
				nearRaider.discard();
				farRaider.discard();
				VillageRaids.forget();
				helper.assertTrue(armed, "not both conscripts in the raid: near " + (Conscription.militia(near) != null)
					+ ", far " + (Conscription.militia(far) != null));
				helper.assertTrue(fought[0], String.format("a raider %.1f blocks away (%.1f above) never drew the conscript (now at %s)",
					n, nUp, helper.relativeVec(near.position())));
				// The far conscript wanders (no pen: a pen would hide the raider): if he ever turns on a raider, he must
				// have come within the 24 blocks of one first.
				helper.assertTrue(!fought[1] || startedAt[1] <= 24.5, String.format(
					"the far conscript (raider %.1f away, %.1f above, at the start) was drawn with no raider within 24: %s", f, fUp, startedHow[1]));
				helper.succeed();
			});
		});
	}

	/**
	 * "Goes for the nearest raider": two raiders in sight on the ground, the far one (10 blocks) spawned first and the near
	 * one (5 blocks) on the other side: the conscript's first blow lands on the near one, the far one is untouched then.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaLc1104eNearest"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaLc1104eNearest")
	public void aConscriptGoesForTheNearestOfTwoRaiders(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 32);
		Leftovers.halls(helper);
		BlockPos hallAt = new BlockPos(3, 2, 3);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		AtomicReference<String> firstHit = new AtomicReference<>();
		Zombie[] raiders = new Zombie[2];
		helper.onEachTick(() -> {
			if (firstHit.get() == null && raiders[0] != null) {
				boolean nearHurt = raiders[0].getHealth() < raiders[0].getMaxHealth();
				boolean farHurt = raiders[1].getHealth() < raiders[1].getMaxHealth();
				if (nearHurt || farHurt) {
					firstHit.set(nearHurt && !farHurt ? "near" : farHurt && !nearHurt ? "far" : "both");
				}
			}
		});
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			conscription(helper, hallAt);
			Zombie far = raider(helper, new BlockPos(15, 2, 5));
			Villager v = conscript(helper, new BlockPos(15, 2, 15));
			Zombie near = raider(helper, new BlockPos(15, 2, 20));
			raiders[0] = near;
			raiders[1] = far;
			VillageRaids.track(level, helper.absolutePos(hallAt), 2);
			CivicEffects.forget();
			Conscription.forget();
			helper.succeedWhen(() -> {
				helper.assertTrue(firstHit.get() != null, "no raider struck yet (fighting: " + MilitiaCombat.isFighting(v) + ")");
				helper.assertTrue(firstHit.get().equals("near"), "the first blow landed on: " + firstHit.get() + " (want the one 5 blocks off, not the one 10 off)");
				near.discard();
				far.discard();
				VillageRaids.forget();
			});
		});
	}

	// ---- Mutant: VillageHalls.assign hands no job to a baby or a nitwit -----------------------------------------------

	/**
	 * The hall gives a job only to a grown villager who can work ("not a nitwit or a child"): a baby farmer-to-be (not a
	 * nitwit) and a grown nitwit are each refused a free Stonecutter, which stays free and isn't theirs; a grown jobless
	 * villager beside them gets it. (Kills the survivor {@code isBaby() && ... == NITWIT}.)
	 */
	//$ gametest_ticks_batch BIG '100' '"qaLc1104eHallBaby"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaLc1104eHallBaby")
	public void theHallGivesNoJobToABabyOrANitwitButToAGrownVillager(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos cutter = new BlockPos(4, 2, 4);
		helper.setBlock(cutter, Blocks.STONECUTTER);
		Villager baby = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 4));
		baby.setNoAi(true);
		baby.setAge(-24000);
		Villager nitwit = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		nitwit.setNoAi(true);
		nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
		Villager grown = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 6));
		grown.setNoAi(true);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos at = helper.absolutePos(cutter);
			VillageHalls.FreeStation station = new VillageHalls.FreeStation(at, VillagerProfession.MASON,
				BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolderOrThrow(PoiTypes.MASON));
			helper.assertTrue(baby.isBaby() && baby.getVillagerData().getProfession() != VillagerProfession.NITWIT, "setup: the baby " + baby.getVillagerData());
			helper.assertTrue(!nitwit.isBaby(), "setup: the nitwit is a baby");
			helper.assertTrue(level.getPoiManager().getFreeTickets(at) == 1, "setup: the stonecutter isn't free");
			helper.assertFalse(VillageHalls.assign(level, baby, station), "the hall gave a baby villager the Stonecutter");
			helper.assertTrue(level.getPoiManager().getFreeTickets(at) == 1 && baby.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
				"after the baby's refusal: free tickets " + level.getPoiManager().getFreeTickets(at) + ", baby's job site " + baby.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.assertFalse(VillageHalls.assign(level, nitwit, station), "the hall gave a grown nitwit the Stonecutter");
			helper.assertTrue(level.getPoiManager().getFreeTickets(at) == 1 && nitwit.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
				"after the nitwit's refusal: free tickets " + level.getPoiManager().getFreeTickets(at));
			helper.assertTrue(VillageHalls.assign(level, grown, station), "control: a grown jobless villager wasn't given the Stonecutter");
			helper.assertTrue(level.getPoiManager().getFreeTickets(at) == 0 && grown.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent(),
				"control: after the grown villager took it, free tickets " + level.getPoiManager().getFreeTickets(at));
			helper.succeed();
		});
	}

	// ---- Mutant: a reform story step is never done before a hand-in (B917) ---------------------------------------------

	private static final String LONG_SHIFTS = AliveWorkplace.id("long_shifts").toString();
	private static final BlockPos REFORM_HALL = new BlockPos(6, 2, 6);
	private static final BlockPos ASKER = new BlockPos(8, 2, 6);

	/**
	 * B917, "never done before a hand-in": the Shift Bell's edict in force and the hall holding part-progress at step 1's
	 * whole count (24 of its 24 clocks, as an older save or a smaller step would leave it); the asker talked to, the story's step-1 quest holds 23 of 24, not done, and one clock handed to it finishes it.
	 * (Handing all 24 on M30's step finishes that step at once, so the story never sees it.) Kills the survivor
	 * {@code step.count() + 1}.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaLc1104eReformFull"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaLc1104eReformFull")
	public void aStoryStepIsNeverShownDoneBeforeAHandInEvenWithTheWholeCountCarried(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager smith = QaReformStoryGameTests.villager(helper, ASKER, VillagerProfession.TOOLSMITH);
		boolean enabled = Arcs.ENABLED;
		Leftovers.after(helper, () -> Arcs.ENABLED = enabled);
		Arcs.ENABLED = true;
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(REFORM_HALL);
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.SURVIVAL);
			Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
			player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
			long before = level.getDayTime();
			Leftovers.after(helper, () -> level.setDayTime(before));
			long day = Chronicle.day(level) + 1;
			level.setDayTime((day - 1) * VillageNeeds.DAY + 1000);
			entity.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS, day)));
			QaReformStoryGameTests.autoRound(level, hall, entity);
			// The hall's saved part-progress at the step's whole count (a save from before the edict file's step got
			// smaller, or kills counted after the step's last check): carried as B917 carries a story's progress.
			Reforms.carry(entity, LONG_SHIFTS, 0, 24);
			helper.assertTrue(Reforms.handed(entity, LONG_SHIFTS, 0) == 24 && Reforms.progress(entity, LONG_SHIFTS).step() == 0,
				"setup: the hall's carried progress " + Reforms.progress(entity, LONG_SHIFTS));
			ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, smith);
			List<Quest> told = Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
			helper.assertTrue(told.size() == 1, "setup: " + told.size() + " story quests up after the ask (progress " + Reforms.progress(entity, LONG_SHIFTS) + ")");
			Quest q = told.get(0);
			helper.assertTrue(q.progress.length > 0 && q.progress[0] == 23, "the story's step 1 holds " + (q.progress.length > 0 ? q.progress[0] : -1) + " of 24 (want 23: what was handed, but one short of done)");
			helper.assertFalse(q.done(), "the story's step 1 shows done before any hand-in to it");
			player.getInventory().add(new ItemStack(Items.CLOCK, 3));
			helper.assertTrue(Stories.handIn(player, hall, q.id) == 1, "the story's step took more or less than the one clock it lacked");
			helper.assertTrue(player.getInventory().countItem(Items.CLOCK) == 2, "clocks left: " + player.getInventory().countItem(Items.CLOCK));
			helper.succeed();
		});
	}
}
