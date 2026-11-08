package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestFiles;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA (lane c, 2026-11-08) for personal requests (ROADMAP 31.9), written from the spec: who is asked for each of the four
 * plain requests (next level, a home, a taste of home, the tools of the trade), through each morning's real pick and
 * through each file's giver conditions; the 3-heart boundary from both sides, per request and per player; and that the
 * giver conditions never hold in a hall-board quest file. Builds on {@link PersonalRequestGameTests}' helpers.
 */
public class QaPersonalAsks1108cGameTests implements FabricGameTest {
	static final String AREA = HeartEventGameTests.AREA;

	/** The four 31.9 requests that don't need Cobblemon. */
	static final List<String> FOUR = List.of("level_up", "home", "taste_of_home", "tools_of_my_trade");

	static Villager worker(GameTestHelper helper, BlockPos at, String name, String job, int level) {
		helper.assertTrue(BuiltInRegistries.VILLAGER_PROFESSION.containsKey(ResourceLocation.parse(job)), "expected the profession " + job + " to be registered, it isn't");
		return PersonalRequestGameTests.worker(helper, at, name, job, level);
	}

	static void job(GameTestHelper helper, Villager v, String job, int level) {
		helper.assertTrue(BuiltInRegistries.VILLAGER_PROFESSION.containsKey(ResourceLocation.parse(job)), "expected the profession " + job + " to be registered, it isn't");
		v.setVillagerData(v.getVillagerData().setProfession(BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(job))).setLevel(level));
	}

	/** The requests (file paths without "personal/") offered by {@code seeds} mornings, each from a fresh start; all of them must be {@code who}'s, for {@code player}. */
	static List<String> mornings(GameTestHelper helper, Villager who, ServerPlayer player, int seeds) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(PersonalRequestGameTests.HALL);
		VillageHallBlockEntity entity = PersonalRequestGameTests.hallEntity(helper);
		long today = Chronicle.day(level);
		List<String> out = new ArrayList<>();
		for (int seed = 0; seed < seeds; seed++) {
			PersonalRequests.forget();
			PersonalRequests.Offer offer = PersonalRequests.pick(level, hall, entity, RandomSource.create(seed), today);
			helper.assertTrue(offer != null, "seed " + seed + ": expected " + who.getDisplayName().getString() + " (3 hearts, friend near) to ask something, nobody asked");
			helper.assertTrue(offer.villager.equals(who.getUUID()) && offer.player.equals(player.getUUID()),
				"seed " + seed + ": expected " + who.getDisplayName().getString() + " to ask the friend, the offer was from " + offer.villager + " to " + offer.player);
			out.add(offer.quest.file.getPath().replace("personal/", ""));
		}
		PersonalRequests.forget();
		return out;
	}

	/** The first morning (of up to 120) on which {@code who} asks for {@code request}; fails when it never does. */
	static Quest firstAsk(GameTestHelper helper, Villager who, ServerPlayer player, String request, String what) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(PersonalRequestGameTests.HALL);
		VillageHallBlockEntity entity = PersonalRequestGameTests.hallEntity(helper);
		long today = Chronicle.day(level);
		Set<String> seen = new TreeSet<>();
		for (int seed = 0; seed < 120; seed++) {
			PersonalRequests.forget();
			PersonalRequests.Offer offer = PersonalRequests.pick(level, hall, entity, RandomSource.create(seed), today);
			if (offer != null) {
				seen.add(offer.quest.file.getPath());
				if (offer.quest.file.getPath().equals("personal/" + request)) {
					PersonalRequests.forget();
					return offer.quest;
				}
			}
		}
		PersonalRequests.forget();
		helper.fail("expected " + what + " to be asked for " + request + " on some morning of 120, they asked only for " + seen);
		return null;
	}

	static Objectives.Bring bring(GameTestHelper helper, Quest quest, String what) {
		helper.assertTrue(quest.objectives.size() == 1 && quest.objectives.get(0) instanceof Objectives.Bring,
			"expected " + what + " to be one bring objective, it was " + quest.objectives);
		return (Objectives.Bring) quest.objectives.get(0);
	}

	/** Finished houses of tier I, II and III round the hall, each with a bed; returns the beds' heads (absolute), by tier (index 1 to 3). */
	static BlockPos[] houses(GameTestHelper helper) {
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		List<BlueprintData.Placement> placed = new ArrayList<>();
		BlockPos[] beds = new BlockPos[4];
		BlockPos[] corners = {null, new BlockPos(2, 8, 2), new BlockPos(10, 8, 2), new BlockPos(2, 8, 10)};
		for (int tier = 1; tier <= 3; tier++) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", tier == 1 ? "qa1108c_house" : "qa1108c_house_" + tier);
			BlockPos corner = corners[tier];
			BlockPos origin = helper.absolutePos(corner);
			level.getStructureManager().getOrCreate(id).fillFromWorld(level, origin.above(60), new Vec3i(5, 4, 5), false, Blocks.AIR);
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
			sites.recordFinished(id, placement, UUID.randomUUID());
			placed.add(placement);
			helper.setBlock(corner.offset(2, 0, 2), Blocks.OAK_PLANKS);
			helper.setBlock(corner.offset(2, 1, 2), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD));
			beds[tier] = origin.offset(2, 1, 2);
		}
		Leftovers.after(helper, () -> placed.forEach(sites::forgetFinished));
		return beds;
	}

	// --- Next level before the festival ---------------------------------------------------------------------------

	/** "A worker below Master": levels 1 to 4 are asked (a level-4 Expert too), a Master never; a mod job the same. */
	//$ gametest_ticks_batch AREA '80' '"qa1108cLevels"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1108cLevels")
	public void nextLevelIsAskedBelowMasterOnly(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			Villager dara = worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			for (String job : List.of("minecraft:farmer", "aliveworkplace:miner")) {
				for (int lvl = 1; lvl <= 5; lvl++) {
					job(helper, dara, job, lvl);
					Quest quest = PersonalRequestGameTests.resolve(helper, "level_up", dara, me);
					if (lvl < 5) {
						helper.assertTrue(quest != null, "expected a level " + lvl + " " + job + " to be able to ask for the next level, they can't");
					} else {
						helper.assertTrue(quest == null, "expected a Master " + job + " never to ask for the next level, they asked: " + quest);
					}
				}
			}
			helper.succeed();
		});
	}

	/** A nitwit and a jobless villager aren't workers: neither asks for the next level. */
	//$ gametest_ticks_batch AREA '80' '"qa1108cLevelNoJob"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1108cLevelNoJob")
	public void nextLevelIsNeverAskedByANitwitOrSomeoneWithoutAJob(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			Villager nit = worker(helper, new BlockPos(5, 2, 4), "Nit", "minecraft:nitwit", 1);
			Villager idle = worker(helper, new BlockPos(7, 2, 4), "Idle", "minecraft:none", 1);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), nit, 300);
			Friendship.add(idle, me, 300);
			Quest a = PersonalRequestGameTests.resolve(helper, "level_up", nit, me);
			Quest b = PersonalRequestGameTests.resolve(helper, "level_up", idle, me);
			helper.assertTrue(a == null, "expected a nitwit never to ask for the next level, they asked: " + a);
			helper.assertTrue(b == null, "expected a villager without a job never to ask for the next level, they asked: " + b);
			helper.succeed();
		});
	}

	/** Each morning's pick, 60 mornings: a Master farmer is never asked about the next level (but still asks for the rest). */
	//$ gametest_ticks_batch AREA '80' '"qa1108cMasterPick"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1108cMasterPick")
	public void aMasterIsNeverAskedForTheNextLevelAnyMorning(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			Villager dara = worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 5);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			List<String> asked = mornings(helper, dara, me, 60);
			helper.assertTrue(!asked.contains("level_up"), "expected a Master never to ask for the next level, 60 mornings asked: " + new TreeSet<>(asked));
			helper.assertTrue(asked.containsAll(List.of("home", "taste_of_home", "tools_of_my_trade")),
				"expected a homeless plains Master farmer to ask for a home, a taste of home and tools over 60 mornings, they asked: " + new TreeSet<>(asked));
			helper.succeed();
		});
	}

	/** Each morning's pick: a level-4 Expert can be asked about the next level, and the quest is the level-up one. */
	//$ gametest_ticks_batch AREA '80' '"qa1108cExpertPick"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1108cExpertPick")
	public void anExpertIsAskedForTheNextLevelSomeMorning(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			Villager dara = worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 4);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			Quest quest = firstAsk(helper, dara, me, "level_up", "a level-4 Expert farmer");
			helper.assertTrue(quest.personal() && quest.poster.equals("Dara"), "expected Dara's personal request, got " + quest.poster + " (personal " + quest.personal() + ")");
			helper.succeed();
		});
	}

	// --- A home of my own -----------------------------------------------------------------------------------------

	/** Asked with no bed of their own or a tier I home; never from a tier II or tier III home, on any morning. */
	//$ gametest_ticks_batch AREA '100' '"qa1108cHome"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1108cHome")
	public void aHomeIsAskedWithoutABedOrFromATierOneHomeOnly(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			BlockPos[] beds = houses(helper);
			Villager odo = worker(helper, new BlockPos(5, 2, 4), "Odo", "minecraft:librarian", 5);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), odo, 300);
			Quest none = PersonalRequestGameTests.resolve(helper, "home", odo, me);
			helper.assertTrue(none != null, "expected a villager with no bed to be able to ask for a home, they can't");
			Beds.home(odo, beds[1]);
			Quest one = PersonalRequestGameTests.resolve(helper, "home", odo, me);
			helper.assertTrue(one != null, "expected a villager in a tier I home to be able to ask for a home, they can't");
			Beds.home(odo, beds[2]);
			Quest two = PersonalRequestGameTests.resolve(helper, "home", odo, me);
			helper.assertTrue(two == null, "expected a villager in a tier II home not to ask for a home, they asked: " + two);
			Beds.home(odo, beds[3]);
			Quest three = PersonalRequestGameTests.resolve(helper, "home", odo, me);
			helper.assertTrue(three == null, "expected a villager in a tier III home not to ask for a home, they asked: " + three);
			// The real morning pick from the tier III home: 40 mornings, never a home.
			List<String> asked = mornings(helper, odo, me, 40);
			helper.assertTrue(!asked.contains("home"), "expected no home to be asked for from a tier III home, 40 mornings asked: " + new TreeSet<>(asked));
			helper.succeed();
		});
	}

	// --- A taste of home ------------------------------------------------------------------------------------------

	/** Each villager type asks, on a real morning, for its own dish in its own amount. */
	//$ gametest_ticks_batch AREA '100' '"qa1108cTaste"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1108cTaste")
	public void aTasteOfHomeAsksForEachTypesDishOnARealMorning(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			Map<VillagerType, ItemStack> dish = new LinkedHashMap<>();
			dish.put(VillagerType.PLAINS, new ItemStack(Items.PUMPKIN_PIE, 1));
			dish.put(VillagerType.DESERT, new ItemStack(Items.RABBIT_STEW, 1));
			dish.put(VillagerType.SAVANNA, new ItemStack(Items.COOKED_MUTTON, 4));
			dish.put(VillagerType.TAIGA, new ItemStack(Items.SWEET_BERRIES, 16));
			dish.put(VillagerType.SNOW, new ItemStack(Items.BAKED_POTATO, 4));
			dish.put(VillagerType.SWAMP, new ItemStack(Items.MUSHROOM_STEW, 1));
			dish.put(VillagerType.JUNGLE, new ItemStack(Items.COOKIE, 8));
			// A Master librarian: no next level, no tools; what's left is a home or a taste of home (or a 31.10 request).
			Villager odo = worker(helper, new BlockPos(5, 2, 4), "Odo", "minecraft:librarian", 5);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), odo, 300);
			for (Map.Entry<VillagerType, ItemStack> e : dish.entrySet()) {
				odo.setVillagerData(odo.getVillagerData().setType(e.getKey()));
				String what = "a " + BuiltInRegistries.VILLAGER_TYPE.getKey(e.getKey()) + " villager";
				Quest quest = firstAsk(helper, odo, me, "taste_of_home", what);
				Objectives.Bring b = bring(helper, quest, what + "'s taste of home");
				helper.assertTrue(Objectives.matches(b.item(), e.getValue()) && b.count() == e.getValue().getCount(),
					"expected " + what + " to miss " + e.getValue().getCount() + " " + BuiltInRegistries.ITEM.getKey(e.getValue().getItem()) + ", they asked for " + b.count() + " " + b.item());
				// And nothing else's dish: another type's food doesn't count.
				for (Map.Entry<VillagerType, ItemStack> other : dish.entrySet()) {
					if (other.getKey() != e.getKey()) {
						helper.assertTrue(!Objectives.matches(b.item(), other.getValue()),
							"expected " + what + "'s request not to take " + BuiltInRegistries.ITEM.getKey(other.getValue().getItem()) + ", it does (" + b.item() + ")");
					}
				}
			}
			helper.succeed();
		});
	}

	// --- The tools of my trade ------------------------------------------------------------------------------------

	/** Each listed job asks, on a real morning, for its own tool (a Master, so the next level is out of the way). */
	//$ gametest_ticks_batch AREA '100' '"qa1108cTools"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1108cTools")
	public void theToolsOfTheTradeAskForEachJobsToolOnARealMorning(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			ItemStack luckyRod = new ItemStack(Items.FISHING_ROD);
			luckyRod.enchant(enchantments.getOrThrow(Enchantments.LUCK_OF_THE_SEA), 1);
			Map<String, ItemStack> tools = new LinkedHashMap<>();
			tools.put("aliveworkplace:miner", new ItemStack(Items.DIAMOND_PICKAXE));
			tools.put("aliveworkplace:lumberjack", new ItemStack(Items.DIAMOND_AXE));
			tools.put("minecraft:farmer", new ItemStack(Items.DIAMOND_HOE));
			tools.put("minecraft:fisherman", luckyRod);
			tools.put("aliveworkplace:guard", new ItemStack(Items.DIAMOND_SWORD));
			tools.put("minecraft:cartographer", new ItemStack(Items.SPYGLASS));
			tools.put("aliveworkplace:netherworker", PotionContents.createItemStack(Items.POTION, Potions.FIRE_RESISTANCE));
			tools.put("aliveworkplace:sifter", new ItemStack(Items.DIAMOND_SHOVEL));
			List<ItemStack> wrong = List.of(new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.FISHING_ROD), new ItemStack(Items.STONE_HOE),
				PotionContents.createItemStack(Items.POTION, Potions.WATER));
			Villager finn = worker(helper, new BlockPos(5, 2, 4), "Finn", "minecraft:farmer", 5);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), finn, 300);
			for (Map.Entry<String, ItemStack> e : tools.entrySet()) {
				job(helper, finn, e.getKey(), 5);
				String what = "a Master " + e.getKey();
				Quest quest = firstAsk(helper, finn, me, "tools_of_my_trade", what);
				Objectives.Bring b = bring(helper, quest, what + "'s tools");
				helper.assertTrue(b.count() == 1 && Objectives.matches(b.item(), e.getValue()),
					"expected " + what + " to need 1 " + e.getValue().getHoverName().getString() + ", they asked for " + b.count() + " " + b.item());
				for (ItemStack w : wrong) {
					helper.assertTrue(!Objectives.matches(b.item(), w), "expected " + what + "'s request not to take " + w.getHoverName().getString() + ", it does (" + b.item() + ")");
				}
			}
			helper.succeed();
		});
	}

	/** A job not on the list (vanilla, the mod's own Builder, a nitwit, no job at all) is never asked for tools. */
	//$ gametest_ticks_batch AREA '100' '"qa1108cNoTools"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1108cNoTools")
	public void theToolsOfTheTradeAreNeverAskedByAJobOffTheList(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			Villager ida = worker(helper, new BlockPos(5, 2, 4), "Ida", "minecraft:librarian", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), ida, 300);
			List<String> offList = List.of("minecraft:none", "minecraft:nitwit", "minecraft:librarian", "minecraft:armorer", "minecraft:butcher",
				"minecraft:cleric", "minecraft:fletcher", "minecraft:leatherworker", "minecraft:mason", "minecraft:shepherd", "minecraft:toolsmith",
				"minecraft:weaponsmith", "aliveworkplace:builder");
			List<String> asked = new ArrayList<>();
			for (String job : offList) {
				job(helper, ida, job, 2);
				Quest quest = PersonalRequestGameTests.resolve(helper, "tools_of_my_trade", ida, me);
				if (quest != null) {
					asked.add(job + " (" + quest.objectives + ")");
				}
			}
			helper.assertTrue(asked.isEmpty(), "expected no job off the list to ask for tools, these did: " + asked);
			// The real mornings for a toolsmith and the Builder: never tools.
			for (String job : List.of("minecraft:toolsmith", "aliveworkplace:builder")) {
				job(helper, ida, job, 2);
				List<String> mornings = mornings(helper, ida, me, 40);
				helper.assertTrue(!mornings.contains("tools_of_my_trade"), "expected a " + job + " never to ask for tools, 40 mornings asked: " + new TreeSet<>(mornings));
			}
			helper.succeed();
		});
	}

	/** Out of the four plain requests, a jungle Master librarian in a tier II home only ever asks for a taste of home: 8 cookies. */
	//$ gametest_ticks_batch AREA '100' '"qa1108cOnlyTaste"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1108cOnlyTaste")
	public void aHousedMasterLibrarianOnlyAsksForATasteOfHome(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			BlockPos[] beds = houses(helper);
			Villager ana = worker(helper, new BlockPos(5, 2, 4), "Ana", "minecraft:librarian", 5);
			ana.setVillagerData(ana.getVillagerData().setType(VillagerType.JUNGLE));
			Beds.home(ana, beds[2]);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), ana, 300);
			List<String> asked = mornings(helper, ana, me, 60);
			List<String> ofTheFour = asked.stream().filter(FOUR::contains).distinct().sorted().toList();
			helper.assertTrue(ofTheFour.equals(List.of("taste_of_home")),
				"expected only a taste of home among 31.9's four requests, 60 mornings asked: " + new TreeSet<>(asked));
			Quest quest = firstAsk(helper, ana, me, "taste_of_home", "a jungle Master librarian");
			Objectives.Bring b = bring(helper, quest, "her taste of home");
			helper.assertTrue(b.count() == 8 && Objectives.matches(b.item(), new ItemStack(Items.COOKIE)), "expected 8 cookies, she asked for " + b.count() + " " + b.item());
			helper.succeed();
		});
	}

	// --- Hearts ---------------------------------------------------------------------------------------------------

	/** Every one of the four: at 3 hearts (300 points) it can be asked, at 299 (2 hearts) it can't. */
	//$ gametest_ticks_batch AREA '80' '"qa1108cHearts"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1108cHearts")
	public void eachRequestNeedsThreeHeartsAndTwoIsNotEnough(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			Villager dara = worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 299);
			helper.assertTrue(Friendship.hearts(Friendship.points(dara, me.getUUID())) == 2, "expected 299 points to be 2 hearts, it's " + Friendship.hearts(Friendship.points(dara, me.getUUID())));
			List<String> atTwo = new ArrayList<>();
			for (String r : FOUR) {
				if (PersonalRequestGameTests.resolve(helper, r, dara, me) != null) {
					atTwo.add(r);
				}
			}
			helper.assertTrue(atTwo.isEmpty(), "expected nothing to be asked at 2 hearts, these could be: " + atTwo);
			Friendship.add(dara, me, 1);
			helper.assertTrue(Friendship.hearts(Friendship.points(dara, me.getUUID())) == 3, "expected 300 points to be 3 hearts, it's " + Friendship.hearts(Friendship.points(dara, me.getUUID())));
			List<String> missing = new ArrayList<>();
			for (String r : FOUR) {
				if (PersonalRequestGameTests.resolve(helper, r, dara, me) == null) {
					missing.add(r);
				}
			}
			helper.assertTrue(missing.isEmpty(), "expected a homeless level 1 plains farmer at 3 hearts to be able to ask all four, these couldn't be asked: " + missing);
			helper.succeed();
		});
	}

	/** The hearts are with that player: a near player with 2 hearts isn't asked while the 3-heart friend is away; the friend, once near, is. */
	//$ gametest_ticks_batch AREA '80' '"qa1108cWhichPlayer"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1108cWhichPlayer")
	public void theThreeHeartsMustBeWithThePlayerAsked(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(PersonalRequestGameTests.HALL);
			VillageHallBlockEntity entity = PersonalRequestGameTests.hallEntity(helper);
			long today = Chronicle.day(level);
			Villager dara = worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			ServerPlayer friend = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 400);
			ServerPlayer near = PersonalRequestGameTests.friend(helper, new BlockPos(6, 2, 6), dara, 299);
			for (String r : FOUR) {
				Quest q = PersonalRequestGameTests.resolve(helper, r, dara, near);
				helper.assertTrue(q == null, "expected " + r + " not to be asked of a player with 2 hearts (another has 4), it was: " + q);
			}
			friend.moveTo(friend.getX() + PersonalRequests.NEAR + 10, friend.getY(), friend.getZ(), 0, 0);
			for (int seed = 0; seed < 20; seed++) {
				PersonalRequests.forget();
				PersonalRequests.Offer offer = PersonalRequests.pick(level, hall, entity, RandomSource.create(seed), today);
				helper.assertTrue(offer == null, "seed " + seed + ": expected nobody to be asked with only a 2-heart player near, " + (offer == null ? "" : offer.player + " was asked for " + offer.quest.file));
			}
			friend.moveTo(near.getX(), near.getY(), near.getZ(), 0, 0);
			for (int seed = 0; seed < 20; seed++) {
				PersonalRequests.forget();
				PersonalRequests.Offer offer = PersonalRequests.pick(level, hall, entity, RandomSource.create(seed), today);
				helper.assertTrue(offer != null && offer.player.equals(friend.getUUID()),
					"seed " + seed + ": expected the 4-heart friend to be asked, " + (offer == null ? "nobody was" : offer.player + " was (friend " + friend.getUUID() + ")"));
			}
			PersonalRequests.forget();
			helper.succeed();
		});
	}

	// --- The hall's board -----------------------------------------------------------------------------------------

	/**
	 * A hall-board quest file (giver "hall") gated by any giver condition is never posted, whoever lives in the village;
	 * an ungated one beside it is, so the board itself works.
	 */
	//$ gametest_ticks_batch AREA '80' '"qa1108cBoard"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1108cBoard")
	public void giverConditionsNeverHoldOnTheHallsBoard(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(PersonalRequestGameTests.HALL);
			// Villagers every condition would hold for, were there a giver: no bed, level 1, plains, a farmer with a 3-heart friend.
			Villager dara = worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			worker(helper, new BlockPos(7, 2, 4), "Bram", "minecraft:farmer", 1);
			PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 1000);
			var manager = level.getServer().getResourceManager();
			Map<ResourceLocation, Resource> real = manager.listResources(QuestFiles.FOLDER, p -> p.getPath().endsWith(".json"));
			Resource any = real.values().iterator().next();
			Map<String, String> gated = new LinkedHashMap<>();
			gated.put("hearts", "{\"type\": \"hearts_at_least\", \"hearts\": 1}");
			gated.put("job", "{\"type\": \"job\", \"jobs\": [\"minecraft:farmer\"]}");
			gated.put("family", "{\"type\": \"job_family\", \"family\": \"land\"}");
			gated.put("level", "{\"type\": \"level_below\", \"level\": 5}");
			gated.put("vtype", "{\"type\": \"villager_type\", \"types\": [\"minecraft:plains\"]}");
			gated.put("tier", "{\"type\": \"home_tier_below\", \"tier\": 3}");
			gated.put("nobed", "{\"type\": \"no_bed\"}");
			Map<ResourceLocation, Resource> pack = new HashMap<>();
			for (Map.Entry<String, String> e : gated.entrySet()) {
				String json = "{\"giver\": \"hall\", \"priority\": 50, \"weight\": 10, \"conditions\": [" + e.getValue() + "],"
					+ " \"objectives\": [{\"type\": \"bring\", \"item\": \"minecraft:torch\", \"count\": 3}], \"rewards\": [{\"type\": \"money\", \"emeralds\": 1}]}";
				pack.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "quests/qa1108c/gated_" + e.getKey() + ".json"),
					new Resource(any.source(), () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
			}
			String control = "{\"giver\": \"hall\", \"priority\": 0, \"weight\": 10, \"objectives\": [{\"type\": \"bring\", \"item\": \"minecraft:torch\", \"count\": 2}],"
				+ " \"rewards\": [{\"type\": \"money\", \"emeralds\": 1}]}";
			pack.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "quests/qa1108c/control.json"),
				new Resource(any.source(), () -> new ByteArrayInputStream(control.getBytes(StandardCharsets.UTF_8))));
			List<String> posted = new ArrayList<>();
			int loaded;
			try {
				QuestFiles.load(pack);
				loaded = QuestFiles.all().size();
				for (int seed = 0; seed < 30; seed++) {
					Quest q = Stories.make(level, hall, 1f, RandomSource.create(seed));
					posted.add(q == null ? "nothing" : q.file.getPath());
				}
			} finally {
				QuestFiles.load(real);
			}
			helper.assertTrue(loaded == gated.size() + 1, "expected all " + (gated.size() + 1) + " test files to load, " + loaded + " did");
			Set<String> kinds = new TreeSet<>(posted);
			helper.assertTrue(kinds.equals(Set.of("qa1108c/control")),
				"expected the board to post only the ungated file (a giver condition never holds there), 30 mornings posted: " + kinds);
			helper.assertTrue(QuestFiles.get(PersonalRequestGameTests.file("level_up")).isPresent(), "expected the real quest files back after the test, level_up is missing");
			helper.succeed();
		});
	}
}
