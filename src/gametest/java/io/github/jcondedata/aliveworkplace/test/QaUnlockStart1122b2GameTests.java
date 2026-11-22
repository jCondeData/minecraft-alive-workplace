package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks.State;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-b-1122-2138), from the specs of B1392 and B1319, written from the data files and the bugs' "Expected", not
 * from the fix.
 *
 * <p>B1392 (ROADMAP 26.10): in a new village the Blueprint Table has every build of
 * {@code data/aliveworkplace/blueprint_unlocks/starting.json} unlocked; every level II or higher of a starting build
 * that isn't itself in starting.json (Smithy II, Stone House II...) is locked, as is the Market Stall; every build of
 * {@code rare.json} is a hidden ???; and all of that is the same after the hall is saved and loaded again. The two data
 * files are read here straight from the server's resources, so the test doesn't trust the mod's own reading of them.
 *
 * <p>B1319: the questbook's "Music: a Bandstand" says it's a dark ??? at the end of the Table's list, a rare build, found
 * in a chest out in the world (six named loot tables) and unlocked by right-clicking the Table with it. Each claim is
 * checked against the game: hidden in a new village, its quest text, the six loot tables really holding it, and using a
 * found one on the Table unlocking it. And no quest calls a rare build's name gray.
 */
public class QaUnlockStart1122b2GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos TABLE = new BlockPos(5, 2, 5);
	private static final Pattern LEVEL = Pattern.compile("(.+)_(\\d+)");
	private static final String GRAY = "Its name is gray at the Blueprint Table at first";

	/** Its own hall and Table, the switch on (it's off in GameTests) and the village radius small, both put back after. */
	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		boolean was = BlueprintUnlocks.ENABLED;
		int radius = VillageHalls.RADIUS;
		BlueprintUnlocks.ENABLED = true;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			BlueprintUnlocks.ENABLED = was;
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		VillageHalls.forgetNearest();
	}

	/** The ids of a {@code blueprint_unlocks} data file, read from the server's resources (every datapack's file of that name). */
	private static Set<ResourceLocation> dataFile(GameTestHelper helper, String name) {
		Set<ResourceLocation> out = new LinkedHashSet<>();
		ResourceLocation file = ResourceLocation.fromNamespaceAndPath(AliveWorkplace.MOD_ID, "blueprint_unlocks/" + name + ".json");
		List<Resource> stack = helper.getLevel().getServer().getResourceManager().getResourceStack(file);
		helper.assertFalse(stack.isEmpty(), "setup: no " + file + " in the server's data");
		for (Resource r : stack) {
			try (Reader reader = r.openAsReader()) {
				JsonArray ids = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("blueprints");
				for (JsonElement id : ids) {
					out.add(ResourceLocation.parse(id.getAsString()));
				}
			} catch (Exception e) {
				helper.fail("setup: " + file + " doesn't read: " + e);
			}
		}
		return out;
	}

	/** What the server-wide (far from any hall) set holds: a new village takes it in, so a leftover there would show. */
	private static String wild(ServerLevel level) {
		try {
			Class<?> wild = Class.forName("io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks$Wild");
			java.lang.reflect.Method get = wild.getDeclaredMethod("get", net.minecraft.server.MinecraftServer.class);
			get.setAccessible(true);
			java.lang.reflect.Field ids = wild.getDeclaredField("ids");
			ids.setAccessible(true);
			return String.valueOf(ids.get(get.invoke(null, level.getServer())));
		} catch (ReflectiveOperationException e) {
			return "(unread: " + e + ")";
		}
	}

	/** Every level II or higher of a starting build ({@code <id>_<n>}, n >= 2) in the library, not itself a starting build. */
	private static Set<ResourceLocation> higherLevels(ServerLevel level, Set<ResourceLocation> starting) {
		Set<ResourceLocation> out = new TreeSet<>();
		Set<ResourceLocation> library = new LinkedHashSet<>(BlueprintLibrary.list(level.getServer(), false));
		library.addAll(BlueprintLibrary.list(level.getServer(), true));
		for (ResourceLocation id : library) {
			Matcher m = LEVEL.matcher(id.getPath());
			if (m.matches() && Integer.parseInt(m.group(2)) >= 2 && !starting.contains(id)
				&& starting.contains(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), m.group(1)))) {
				out.add(id);
			}
		}
		return out;
	}

	/**
	 * The whole spec against the village round {@code hall}: states by id and the Table's listing as the player gets it.
	 * Returns every disagreement (empty: all as specified).
	 */
	private static List<String> check(GameTestHelper helper, BlockPos hall, Set<ResourceLocation> starting, Set<ResourceLocation> rare,
		Set<ResourceLocation> higher, Set<ResourceLocation> unlockedOnPurpose) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		Map<ResourceLocation, TablePayloads.Entry> byId = TableServer.listing(level, hall).stream()
			.collect(Collectors.toMap(TablePayloads.Entry::id, Function.identity(), (a, b) -> a));
		Set<ResourceLocation> library = Set.copyOf(BlueprintLibrary.list(level.getServer(), false));
		for (ResourceLocation id : starting) {
			State s = BlueprintUnlocks.state(level, hall, id);
			if (s != State.UNLOCKED) {
				wrong.add(id + " (starting.json) is " + s);
			}
			TablePayloads.Entry e = byId.get(id);
			if (library.contains(id) && (e == null || !e.unlocked())) {
				wrong.add(id + " (starting.json) at the Table: " + e);
			}
		}
		for (ResourceLocation id : higher) {
			if (unlockedOnPurpose.contains(id)) {
				continue;
			}
			State want = rare.contains(id) ? State.HIDDEN : State.LOCKED;
			State s = BlueprintUnlocks.state(level, hall, id);
			if (s != want) {
				wrong.add(id + " (a higher level of a starting build) is " + s + ", not " + want);
			}
			TablePayloads.Entry e = byId.get(id);
			if (want == State.LOCKED && library.contains(id) && (e == null || !e.locked())) {
				wrong.add(id + " at the Table: " + e + ", not a gray locked entry");
			}
		}
		for (ResourceLocation id : rare) {
			State s = BlueprintUnlocks.state(level, hall, id);
			if (s != State.HIDDEN) {
				wrong.add(id + " (rare.json) is " + s + ", not HIDDEN");
			}
			if (byId.containsKey(id)) {
				wrong.add(id + " (rare.json) is listed at the Table by its id: " + byId.get(id));
			}
		}
		State stall = BlueprintUnlocks.state(level, hall, StarterBlueprints.MARKET_STALL.id());
		if (stall != State.LOCKED) {
			wrong.add("the Market Stall is " + stall);
		}
		return wrong;
	}

	/**
	 * B1392: a new village's Table: every build of starting.json unlocked, every higher level of one locked (or ??? when
	 * rare), every rare build a ???, the Market Stall locked; then the hall saved and loaded into the world again (a
	 * restart): the same, plus Smithy II, unlocked before the save, still unlocked and nothing else with it.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaUnlockStart1122b2Fresh"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaUnlockStart1122b2Fresh")
	public void aNewVillageHasExactlyTheStartingSetAndKeepsItOverASave(GameTestHelper helper) {
		setUp(helper);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			VillageHalls.forgetNearest();
			BlockPos hall = helper.absolutePos(HALL);
			helper.assertTrue(hall.equals(BlueprintUnlocks.village(level, helper.absolutePos(TABLE))), "setup: the Table isn't in the test's village");
			Set<ResourceLocation> starting = dataFile(helper, "starting");
			Set<ResourceLocation> rare = dataFile(helper, "rare");
			helper.assertTrue(starting.size() >= 15, "setup: starting.json holds only " + starting);
			Set<ResourceLocation> higher = higherLevels(level, starting);
			helper.assertTrue(higher.contains(StarterBlueprints.SMITHY_2.id()) && higher.contains(StarterBlueprints.STONE_HOUSE_2.id()),
				"setup: Smithy II or Stone House II isn't a higher level of a starting build in the library: " + higher);
			helper.assertTrue(higher.size() >= 10, "setup: only " + higher.size() + " higher levels of starting builds: " + higher);

			List<String> wrong = check(helper, hall, starting, rare, higher, Set.of());
			helper.assertTrue(wrong.isEmpty(), "a new village's Table: " + wrong + " (server-wide set: " + wild(level) + ")");

			// A save and load of the hall (the village's library lives on it), with Smithy II unlocked first.
			ResourceLocation smithy2 = StarterBlueprints.SMITHY_2.id();
			helper.assertTrue(BlueprintUnlocks.unlock(level, hall, smithy2, List.of()), "Smithy II wasn't locked before it was unlocked");
			CompoundTag saved = level.getBlockEntity(hall).saveWithoutMetadata(level.registryAccess());
			VillageHallBlockEntity loaded = new VillageHallBlockEntity(hall, level.getBlockState(hall));
			loaded.loadWithComponents(saved, level.registryAccess());
			level.setBlockEntity(loaded);
			VillageHalls.forgetNearest();
			helper.assertTrue(level.getBlockEntity(hall) == loaded, "setup: the loaded hall isn't the one in the world");
			helper.assertTrue(loaded.blueprintUnlocksSeeded(), "the loaded hall forgot its library was set up");
			helper.assertTrue(BlueprintUnlocks.state(level, hall, smithy2) == State.UNLOCKED,
				"Smithy II, unlocked before the save, is " + BlueprintUnlocks.state(level, hall, smithy2) + " after it");
			List<String> after = check(helper, hall, starting, rare, higher, Set.of(smithy2));
			helper.assertTrue(after.isEmpty(), "after a save and load: " + after + " (the hall's set: " + loaded.blueprintUnlocks() + ")");
			helper.succeed();
		});
	}

	/** B1392's own words: Smithy II and the Market Stall are locked, gray and named, in a new village, each with how to get it. */
	//$ gametest_ticks_batch AREA '40' '"qaUnlockStart1122b2Smithy"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaUnlockStart1122b2Smithy")
	public void smithyTwoAndTheMarketStallAreGrayWithAHintInANewVillage(GameTestHelper helper) {
		setUp(helper);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			VillageHalls.forgetNearest();
			BlockPos hall = helper.absolutePos(HALL);
			Map<ResourceLocation, TablePayloads.Entry> byId = TableServer.listing(level, hall).stream()
				.collect(Collectors.toMap(TablePayloads.Entry::id, Function.identity(), (a, b) -> a));
			for (StarterBlueprints.Entry e : List.of(StarterBlueprints.SMITHY_2, StarterBlueprints.MARKET_STALL)) {
				TablePayloads.Entry entry = byId.get(e.id());
				helper.assertTrue(entry != null && entry.locked() && !entry.hidden() && !entry.hint().isEmpty() && entry.blocks() > 0,
					e.id() + " at a new village's Table: " + entry + " (server-wide set: " + wild(level) + ")");
			}
			TablePayloads.Entry smithy = byId.get(StarterBlueprints.SMITHY.id());
			helper.assertTrue(smithy != null && smithy.unlocked(), "the Smithy (starting set) at a new village's Table: " + smithy);
			helper.succeed();
		});
	}

	/** The quest's text, read from the questbook files the mod ships (aliveworkplace/questbook/, listed in manifest.txt). */
	private static Optional<String> questText(GameTestHelper helper, String title, List<String> grayRare, Set<String> rareNames) {
		String found = null;
		try {
			List<String> files = new ArrayList<>();
			try (InputStream in = AliveWorkplace.class.getResourceAsStream("/aliveworkplace/questbook/manifest.txt")) {
				helper.assertTrue(in != null, "setup: no questbook manifest in the jar");
				new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().map(String::trim)
					.map(l -> l.split("\\s+")[0]).filter(l -> l.endsWith(".snbt")).forEach(files::add);
			}
			helper.assertFalse(files.isEmpty(), "setup: the questbook manifest names no chapter");
			Pattern quest = Pattern.compile("title: \"((?:[^\"\\\\]|\\\\.)*)\",\\s*description: \\[(.*?)\\],\\s*\\n", Pattern.DOTALL);
			for (String file : files) {
				try (InputStream in = AliveWorkplace.class.getResourceAsStream("/aliveworkplace/questbook/" + file)) {
					helper.assertTrue(in != null, "setup: the manifest's " + file + " isn't in the jar");
					String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
					Matcher m = quest.matcher(text);
					while (m.find()) {
						String t = m.group(1);
						String d = m.group(2);
						if (t.equals(title)) {
							found = d;
						}
						if (d.contains(GRAY)) {
							for (String name : rareNames) {
								if (Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(t).find()) {
									grayRare.add(file + ": '" + t + "' calls the rare " + name + " gray and named");
								}
							}
						}
					}
				}
			}
		} catch (java.io.IOException e) {
			helper.fail("setup: the questbook doesn't read: " + e);
		}
		return Optional.ofNullable(found);
	}

	/**
	 * B1319: the Bandstand is what its quest says. In a new village it's a ??? (hidden, not listed by id); the quest
	 * "Music: a Bandstand" says a dark ??? and a rare build, never "Its name is gray"; each of the six chests it names
	 * (pirate ship, war camp, coven hut, piglin hoard, bandit camp, pharaoh's tomb) really holds its Blueprint now and
	 * then (fixed seeds); and right-clicking the Table with a found one unlocks it there. No quest about a rare build
	 * says it's gray and named.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaUnlockStart1122b2Bandstand"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaUnlockStart1122b2Bandstand")
	public void theBandstandIsAQuestionMarkFoundInTheChestsItsQuestNames(GameTestHelper helper) {
		setUp(helper);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			VillageHalls.forgetNearest();
			BlockPos hall = helper.absolutePos(HALL);
			ResourceLocation bandstand = StarterBlueprints.BANDSTAND.id();
			Set<ResourceLocation> rare = dataFile(helper, "rare");
			State state = BlueprintUnlocks.state(level, hall, bandstand);
			boolean listed = TableServer.listing(level, hall).stream().anyMatch(e -> e.id().equals(bandstand));

			Set<String> rareNames = new TreeSet<>();
			JsonObject lang;
			try (InputStream in = AliveWorkplace.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
				helper.assertTrue(in != null, "setup: no en_us.json in the jar");
				lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (java.io.IOException e) {
				throw new net.minecraft.gametest.framework.GameTestAssertException("setup: en_us.json doesn't read: " + e);
			}
			for (ResourceLocation id : rare) {
				JsonElement name = lang.get("blueprint." + id.getNamespace() + "." + id.getPath());
				if (name != null) {
					rareNames.add(name.getAsString());
				}
			}
			helper.assertTrue(rareNames.size() >= rare.size() - 1 && !rareNames.isEmpty(), "setup: rare.json's builds have names " + rareNames + " for " + rare);
			List<String> grayRare = new ArrayList<>();
			Optional<String> text = questText(helper, "Music: a Bandstand", grayRare, rareNames);
			helper.assertTrue(text.isPresent(), "no quest 'Music: a Bandstand' in the questbook");
			String desc = text.get();
			helper.assertTrue(grayRare.isEmpty(), "quests call a rare build gray and named: " + grayRare);

			if (rare.contains(bandstand)) {
				helper.assertTrue(state == State.HIDDEN && !listed, "the Bandstand, in rare.json, is " + state + (listed ? ", listed by id" : "") + " at a new village's Table");
				helper.assertTrue(desc.contains("???") && desc.contains("rare build") && !desc.contains(GRAY),
					"the Bandstand is a ??? but its quest says: " + desc);
			} else {
				helper.assertTrue(state == State.LOCKED && listed, "the Bandstand, not rare, is " + state + " at a new village's Table");
				helper.assertTrue(desc.contains(GRAY) && !desc.contains("???"), "the Bandstand is gray but its quest says: " + desc);
			}

			// The six chests the quest names, as loot tables of the mod; the quest must name each, and each must hold it.
			Map<String, String> chests = new java.util.LinkedHashMap<>();
			chests.put("pirate ship", "chests/pirate_ship");
			chests.put("war camp", "chests/war_camp");
			chests.put("coven hut", "chests/coven_hut");
			chests.put("piglin hoard", "chests/piglin_hoard");
			chests.put("bandit camp", "chests/bandit_camp");
			chests.put("pharaoh's tomb", "chests/pharaohs_tomb");
			LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(hall)).create(LootContextParamSets.CHEST);
			List<String> never = new ArrayList<>();
			for (Map.Entry<String, String> chest : chests.entrySet()) {
				helper.assertTrue(desc.contains(chest.getKey()), "setup: the quest no longer names the " + chest.getKey() + ": " + desc);
				var table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, AliveWorkplace.id(chest.getValue())));
				boolean seen = false;
				for (int i = 0; i < 1000 && !seen; i++) {
					for (ItemStack stack : table.getRandomItems(params, 1122L * 7919 + i)) {
						if (BlueprintItem.data(stack).map(BlueprintData::structure).filter(bandstand::equals).isPresent()) {
							seen = true;
						}
					}
				}
				if (!seen) {
					never.add(chest.getKey() + " (" + chest.getValue() + ")");
				}
			}
			helper.assertTrue(never.isEmpty(), "the quest says the Bandstand's Blueprint is found in these, but 1,000 of each held none: " + never);

			// Right-click the Table with a found one: it unlocks, and the player keeps the blueprint.
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			try {
				player.setGameMode(GameType.SURVIVAL);
				BlockPos table = helper.absolutePos(TABLE);
				player.moveTo(new Vec3(table.getX() + 1.5, table.getY(), table.getZ() + 0.5));
				ItemStack found = BlueprintItem.create(bandstand, null);
				player.setItemInHand(InteractionHand.MAIN_HAND, found);
				player.gameMode.useItemOn(player, level, found, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(table), Direction.UP, table, false));
				helper.assertTrue(BlueprintUnlocks.state(level, hall, bandstand) == State.UNLOCKED,
					"a found Bandstand used on the Table left it " + BlueprintUnlocks.state(level, hall, bandstand));
				helper.assertTrue(player.getMainHandItem().is(ModItems.BLUEPRINT), "the Table took the found blueprint");
				helper.assertTrue(TableServer.listing(level, hall).stream().anyMatch(e -> e.id().equals(bandstand) && e.unlocked()),
					"the unlocked Bandstand isn't listed, named, at the Table");
			} finally {
				level.getServer().getPlayerList().remove(player);
			}
			helper.succeed();
		});
	}
}
