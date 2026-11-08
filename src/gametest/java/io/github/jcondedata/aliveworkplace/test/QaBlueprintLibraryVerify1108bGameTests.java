package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks.State;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * QA (qa-lane-b-1108-1837) of the Blueprint library's unlocks (docs/wiki/blueprint-library.md, sections 1 to 4 and 7),
 * written from the wiki page for what earlier tests left open: the starting set is exactly the wiki's list and the
 * builder's own trades and the rare builds start the way it says; every builder trade's and questbook tier's hint word
 * for word; the refusals a player reads for a locked and a "???" build; a creative player is refused too; another
 * player's unlock belongs to the village (only the unlocker is told); a level-up takes the lowest tier first, then by
 * id, and one short of three unlocks what's left and never a rare build; the server-wide set through a save and
 * reload; and the switch turned off and on again (a blueprint taken out meanwhile still unlocks it). Each test is alone
 * in its batch, with its own hall, the switch on and a small village radius (put back after).
 */
public class QaBlueprintLibraryVerify1108bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = QaBlueprintUnlocksGameTests.AREA;
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = QaBlueprintUnlocksGameTests.HALL;
	private static final BlockPos TABLE = QaBlueprintUnlocksGameTests.TABLE;
	private static final String NEW_LINE = QaBlueprintUnlocksGameTests.NEW_LINE;
	private static final String LOCKED = "message.aliveworkplace.table.locked";
	private static final String LOCKED_HIDDEN = "message.aliveworkplace.table.locked_hidden";

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("aliveworkplace", path);
	}

	private static State state(GameTestHelper helper, ResourceLocation id) {
		return BlueprintUnlocks.state(helper.getLevel(), helper.absolutePos(HALL), id);
	}

	private static int blueprintsOf(ServerPlayer p, ResourceLocation id) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (BlueprintItem.data(s).map(d -> d.structure().equals(id)).orElse(false)) {
				n += s.getCount();
			}
		}
		return n;
	}

	/** The last action-bar or chat line with {@code key}, as the player reads it. */
	private static String read(GameTestHelper helper, List<Component> seen, String key) {
		Component line = null;
		for (Component c : seen) {
			if (key.equals(QaStageFiveGameTests.key(c))) {
				line = c;
			}
		}
		helper.assertTrue(line != null, "no line " + key + " among " + QaStageFiveGameTests.keys(seen));
		return line.getString();
	}

	/**
	 * Section 1, "The starting set (20)": in a new village the Table's unlocked builds of the mod are exactly the wiki's
	 * list (the Pokémon Center only with Cobblemon), the builder's own trades (Market Stall to Supply Shop) are locked,
	 * not ???, and each rare build the wiki names (Chapel, Market Square, Map Room, Bandstand, Guildhall, Nether Gate,
	 * Library III) and the builder-only Manor is a ??? the listing never names.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1108bStartingSet"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1108bStartingSet")
	public void aNewVillageStartsWithTheWikisSetAndTheRestLockedOrRare(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		QaBlueprintUnlocksGameTests.village(helper, HALL, TABLE);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Set<ResourceLocation> expected = Stream.of("starter_cottage", "stone_house", "terrace", "farmstead", "storehouse", "smithy",
				"masons_yard", "fletchers_lodge", "fishers_hut", "weavers_cottage", "builders_workshop", "carpenters_workshop", "kitchen",
				"guard_house", "clinic", "post_office", "library", "village_hall", "flower_planters").map(QaBlueprintLibraryVerify1108bGameTests::id).collect(Collectors.toCollection(TreeSet::new));
			if (io.github.jcondedata.aliveworkplace.platform.Platform.get().isModLoaded("cobblemon")) {
				expected.add(id("pokemon_center"));
			}
			List<TablePayloads.Entry> listing = TableServer.listing(level, hall);
			Set<ResourceLocation> unlocked = listing.stream().filter(TablePayloads.Entry::unlocked).map(TablePayloads.Entry::id)
				.filter(BlueprintUnlocks::managed).collect(Collectors.toCollection(TreeSet::new));
			helper.assertTrue(unlocked.equals(expected), "the new village's unlocked builds aren't the wiki's starting set: extra "
				+ unlocked.stream().filter(i -> !expected.contains(i)).toList() + ", missing " + expected.stream().filter(i -> !unlocked.contains(i)).toList());
			Set<ResourceLocation> listed = listing.stream().map(TablePayloads.Entry::id).collect(Collectors.toSet());
			for (StarterBlueprints.Entry trade : List.of(StarterBlueprints.MARKET_STALL, StarterBlueprints.LOOKOUT_TOWER,
				StarterBlueprints.HEALING_CENTER, StarterBlueprints.SUPPLY_SHOP)) {
				helper.assertTrue(state(helper, trade.id()) == State.LOCKED && listed.contains(trade.id()), "the builder's trade " + trade.id() + " is "
					+ state(helper, trade.id()) + (listed.contains(trade.id()) ? "" : ", not listed"));
			}
			for (StarterBlueprints.Entry rare : List.of(StarterBlueprints.CHAPEL, StarterBlueprints.MARKET_SQUARE, StarterBlueprints.MAP_ROOM,
				StarterBlueprints.BANDSTAND, StarterBlueprints.GUILDHALL, StarterBlueprints.NETHER_GATE, StarterBlueprints.LIBRARY_3, StarterBlueprints.MANOR)) {
				helper.assertTrue(state(helper, rare.id()) == State.HIDDEN, "the rare " + rare.id() + " is " + state(helper, rare.id()) + ", not a ???");
				helper.assertFalse(listed.contains(rare.id()), "the rare " + rare.id() + " is listed by its id");
			}
			helper.succeed();
		});
	}

	/**
	 * Section 1's hints, word for word: the builder's four trades say the builder's level that sells them; the five
	 * questbook tiers (Starter Cottage II and III, Stone House II and III, Terrace II) say "Sold by a builder once the
	 * tier below stands" without FTB Quests (section 7) and "A questbook reward" with it. The listing sends the same key.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1108bHints"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1108bHints")
	public void eachLockedBuildSaysHowToGetItWordForWord(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		QaBlueprintUnlocksGameTests.village(helper, HALL, TABLE);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			Map<ResourceLocation, TablePayloads.Entry> byId = TableServer.listing(helper.getLevel(), helper.absolutePos(HALL)).stream()
				.collect(Collectors.toMap(TablePayloads.Entry::id, e -> e, (a, b) -> a));
			Map<StarterBlueprints.Entry, String> builder = new java.util.LinkedHashMap<>();
			builder.put(StarterBlueprints.MARKET_STALL, "Sold by an Apprentice Builder");
			builder.put(StarterBlueprints.LOOKOUT_TOWER, "Sold by a Journeyman Builder");
			builder.put(StarterBlueprints.HEALING_CENTER, "Sold by an Expert Builder");
			builder.put(StarterBlueprints.SUPPLY_SHOP, "Sold by a Master Builder");
			boolean ftb = io.github.jcondedata.aliveworkplace.platform.Platform.get().isModLoaded("ftbquests");
			String quest = ftb ? "A questbook reward" : "Sold by a builder once the tier below stands";
			Map<StarterBlueprints.Entry, String> all = new java.util.LinkedHashMap<>(builder);
			for (StarterBlueprints.Entry tier : List.of(StarterBlueprints.STARTER_COTTAGE_2, StarterBlueprints.STARTER_COTTAGE_3,
				StarterBlueprints.STONE_HOUSE_2, StarterBlueprints.STONE_HOUSE_3, StarterBlueprints.TERRACE_2)) {
				all.put(tier, quest);
			}
			for (Map.Entry<StarterBlueprints.Entry, String> e : all.entrySet()) {
				ResourceLocation id = e.getKey().id();
				TablePayloads.Entry listed = byId.get(id);
				helper.assertTrue(listed != null && listed.locked(), id + " isn't listed locked: " + listed);
				String said = Component.translatable(listed.hint()).getString();
				helper.assertTrue(said.equals(e.getValue()), id + "'s hint reads \"" + said + "\", not \"" + e.getValue() + "\"");
				helper.assertTrue(listed.hint().equals(BlueprintUnlocks.hint(id)), id + "'s listing hint " + listed.hint() + " isn't its refusal's " + BlueprintUnlocks.hint(id));
			}
			helper.succeed();
		});
	}

	/**
	 * "Get Blueprint is grayed out" for a locked build, and a request for it anyway is refused with a line saying why:
	 * "Market Stall is locked in this village's library. Sold by an Apprentice Builder"; a ??? build is refused without
	 * its name ("That blueprint isn't in this village's library yet"). Neither uses a Blank Blueprint or gives a
	 * blueprint; an unlocked build of the starting set right after does both.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1108bRefusals"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1108bRefusals")
	public void aLockedAndARareRequestAreRefusedWithTheirLines(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		QaBlueprintUnlocksGameTests.village(helper, HALL, TABLE);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, TABLE);
			ServerPlayer p = c.player();
			BlockPos table = helper.absolutePos(TABLE);
			p.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 3));
			p.containerMenu = p.inventoryMenu;
			helper.assertFalse(TableServer.take(p, table, StarterBlueprints.MARKET_STALL.id()), "the Table handed out the locked Market Stall");
			String locked = read(helper, c.seen(), LOCKED);
			helper.assertTrue(locked.equals("Market Stall is locked in this village's library. Sold by an Apprentice Builder"), "the locked line reads \"" + locked + "\"");
			helper.assertFalse(TableServer.take(p, table, StarterBlueprints.CHAPEL.id()), "the Table handed out the ??? Chapel");
			String hidden = read(helper, c.seen(), LOCKED_HIDDEN);
			helper.assertTrue(hidden.equals("That blueprint isn't in this village's library yet"), "the ??? line reads \"" + hidden + "\"");
			helper.assertFalse(c.seen().stream().anyMatch(l -> l.getString().contains("Chapel")), "a refusal named the ??? Chapel: " + QaStageFiveGameTests.keys(c.seen()));
			helper.assertTrue(p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 3 && p.getInventory().countItem(ModItems.BLUEPRINT) == 0,
				"a refusal used a Blank Blueprint or gave a blueprint: " + p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) + " blank, "
					+ p.getInventory().countItem(ModItems.BLUEPRINT) + " blueprints");
			helper.assertTrue(TableServer.take(p, table, StarterBlueprints.SMITHY.id()), "the Table refused the Smithy of the starting set");
			helper.assertTrue(p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 2 && blueprintsOf(p, StarterBlueprints.SMITHY.id()) == 1,
				"the Smithy didn't turn one Blank Blueprint into a Smithy blueprint");
			helper.succeed();
		});
	}

	/** Locked means locked in creative too: a creative player's Get Blueprint for a locked or ??? build gives nothing; an unlocked one is handed out. */
	//$ gametest_ticks_batch AREA '40' '"qa1108bCreative"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1108bCreative")
	public void aCreativePlayerIsRefusedALockedBuildToo(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		QaBlueprintUnlocksGameTests.village(helper, HALL, TABLE);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, TABLE);
			ServerPlayer p = c.player();
			p.setGameMode(GameType.CREATIVE);
			BlockPos table = helper.absolutePos(TABLE);
			p.containerMenu = p.inventoryMenu;
			helper.assertFalse(TableServer.take(p, table, StarterBlueprints.MARKET_STALL.id()), "a creative player got the locked Market Stall");
			helper.assertFalse(TableServer.take(p, table, StarterBlueprints.MANOR.id()), "a creative player got the ??? Manor");
			helper.assertTrue(blueprintsOf(p, StarterBlueprints.MARKET_STALL.id()) == 0 && blueprintsOf(p, StarterBlueprints.MANOR.id()) == 0,
				"a refused build reached the creative player's inventory");
			helper.assertTrue(state(helper, StarterBlueprints.MARKET_STALL.id()) == State.LOCKED, "asking unlocked the Market Stall");
			helper.assertTrue(TableServer.take(p, table, StarterBlueprints.STONE_HOUSE.id()), "a creative player was refused the Stone House");
			helper.assertTrue(blueprintsOf(p, StarterBlueprints.STONE_HOUSE.id()) == 1, "the creative player's Stone House blueprint isn't in the inventory");
			helper.succeed();
		});
	}

	/**
	 * "Unlocks belong to the village": a found Fountain one player uses on the Table unlocks it for a second player at
	 * the same Table (Get Blueprint works for them, their screen lists it unlocked), but only the player who used it reads
	 * "The village's library has a new blueprint: Fountain".
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1108bTwoPlayers"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1108bTwoPlayers")
	public void anotherPlayersUnlockIsTheVillagesAndOnlyTheyAreTold(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		QaBlueprintUnlocksGameTests.village(helper, HALL, TABLE);
		List<QaStageFiveGameTests.Client> clients = new ArrayList<>();
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			clients.add(QaBlueprintUnlocksGameTests.client(helper, TABLE));
		});
		// a tick later, so the two players don't share a name
		helper.runAfterDelay(5, () -> {
			clients.add(QaBlueprintUnlocksGameTests.client(helper, TABLE));
			QaStageFiveGameTests.Client a = clients.get(0);
			QaStageFiveGameTests.Client b = clients.get(1);
			helper.assertFalse(a.player().getGameProfile().getName().equals(b.player().getGameProfile().getName()), "the two players share a name");
			ResourceLocation fountain = StarterBlueprints.FOUNTAIN.id();
			helper.assertTrue(state(helper, fountain) == State.LOCKED, "the Fountain isn't locked to begin with");
			a.player().setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(fountain, StarterBlueprints.FOUNTAIN.size()));
			QaBlueprintUnlocksGameTests.clickTable(helper, a.player(), TABLE);
			helper.assertTrue(state(helper, fountain) == State.UNLOCKED, "the found Fountain didn't unlock it");
			String told = read(helper, a.seen(), NEW_LINE);
			helper.assertTrue(told.equals("The village's library has a new blueprint: Fountain"), "the unlocker read \"" + told + "\"");
			helper.assertTrue(QaBlueprintUnlocksGameTests.linesOf(b.seen(), NEW_LINE) == 0, "the other player was told of the Fountain too");
			ServerPlayer pb = b.player();
			pb.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
			b.sent();
			TableServer.open(pb, helper.absolutePos(TABLE));
			List<TablePayloads.Open> opened = b.sent(TablePayloads.Open.class);
			helper.assertTrue(opened.size() == 1, "the second player's Table opened " + opened.size() + " times");
			TablePayloads.Entry shown = opened.get(0).entries().stream().filter(e -> e.id().equals(fountain)).findFirst().orElse(null);
			helper.assertTrue(shown != null && shown.unlocked(), "the second player's screen shows the Fountain as " + shown);
			pb.containerMenu = pb.inventoryMenu;
			helper.assertTrue(TableServer.take(pb, helper.absolutePos(TABLE), fountain), "the second player was refused the village's Fountain");
			helper.assertTrue(blueprintsOf(pb, fountain) == 1 && pb.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 0, "the second player's Fountain wasn't made from their Blank Blueprint");
			helper.succeed();
		});
	}

	/**
	 * Way 2, "the next 3 locked builds (lowest tier first, then by id; never a rare one)", from both sides of three:
	 * with four left locked (Stone House III, Smithy II, the Market Stall and the Fountain) a level-up unlocks the
	 * Fountain, the Market Stall and Smithy II and leaves Stone House III; the next level-up, one short of three, unlocks
	 * Stone House III alone. Every rare build stays a ???.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1108bLevelUpOrder"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1108bLevelUpOrder")
	public void aLevelUpTakesTheLowestTierFirstAndOneShortTakesWhatIsLeft(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		QaBlueprintUnlocksGameTests.village(helper, HALL, TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(1));
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ResourceLocation fountain = StarterBlueprints.FOUNTAIN.id();
			ResourceLocation stall = StarterBlueprints.MARKET_STALL.id();
			ResourceLocation smithy2 = StarterBlueprints.SMITHY_2.id();
			ResourceLocation stone3 = StarterBlueprints.STONE_HOUSE_3.id();
			Set<ResourceLocation> keep = Set.of(fountain, stall, smithy2, stone3);
			List<ResourceLocation> rares = new ArrayList<>();
			for (ResourceLocation id : BlueprintLibrary.list(level.getServer(), false)) {
				State s = BlueprintUnlocks.state(level, hall, id);
				if (s == State.LOCKED && !keep.contains(id)) {
					BlueprintUnlocks.unlock(level, hall, id, List.of());
				} else if (s == State.HIDDEN) {
					rares.add(id);
				}
			}
			for (StarterBlueprints.Entry rare : List.of(StarterBlueprints.CHAPEL, StarterBlueprints.MANOR, StarterBlueprints.LIBRARY_3)) {
				if (!rares.contains(rare.id())) {
					rares.add(rare.id());
				}
			}
			for (ResourceLocation id : keep) {
				helper.assertTrue(BlueprintUnlocks.state(level, hall, id) == State.LOCKED, id + " isn't locked to begin with");
			}
			builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(1));
			io.github.jcondedata.aliveworkplace.build.BuilderLevels.addXp(level, builder, 30, null);
			helper.assertTrue(builder.getVillagerData().getLevel() == 2, "the builder didn't level up: " + builder.getVillagerData().getLevel());
			Set<ResourceLocation> first = new HashSet<>();
			for (ResourceLocation id : keep) {
				if (BlueprintUnlocks.state(level, hall, id) == State.UNLOCKED) {
					first.add(id);
				}
			}
			helper.assertTrue(first.equals(Set.of(fountain, stall, smithy2)), "the first level-up unlocked " + first + ", not the Fountain, Market Stall and Smithy II");
			builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(2));
			io.github.jcondedata.aliveworkplace.build.BuilderLevels.addXp(level, builder, 70, null);
			helper.assertTrue(builder.getVillagerData().getLevel() == 3, "the builder didn't level up again: " + builder.getVillagerData().getLevel());
			helper.assertTrue(BlueprintUnlocks.state(level, hall, stone3) == State.UNLOCKED, "the level-up one short of three left Stone House III locked");
			for (ResourceLocation id : rares) {
				helper.assertTrue(BlueprintUnlocks.state(level, hall, id) == State.HIDDEN, "a level-up unlocked the rare " + id + ": " + BlueprintUnlocks.state(level, hall, id));
			}
			helper.succeed();
		});
	}

	/**
	 * Section 4, "the overworld's aliveworkplace_blueprint_unlocks saved data": a found Fountain used on a Table far from
	 * every hall goes into the server-wide set, which comes back from its saved form (under "ids") with the Fountain in it.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qa1108bWildReload"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qa1108bWildReload")
	public void theServerWideSetSurvivesASaveAndReload(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 8);
		ServerLevel level = helper.getLevel();
		ResourceLocation fountain = StarterBlueprints.FOUNTAIN.id();
		boolean was = BlueprintUnlocks.unlocked(level, null).contains(fountain);
		Leftovers.after(helper, () -> {
			if (!was) {
				wildIds(level.getServer()).remove(fountain);
			}
		});
		BlockPos tableRel = new BlockPos(15, 2, 15);
		helper.setBlock(tableRel, ModBlocks.BLUEPRINT_TABLE);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			helper.assertTrue(BlueprintUnlocks.village(level, helper.absolutePos(tableRel)) == null, "a hall is in reach of the far Table");
			if (was) {
				wildIds(level.getServer()).remove(fountain);
			}
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, tableRel);
			c.player().setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(fountain, StarterBlueprints.FOUNTAIN.size()));
			QaBlueprintUnlocksGameTests.clickTable(helper, c.player(), tableRel);
			helper.assertTrue(BlueprintUnlocks.state(level, null, fountain) == State.UNLOCKED, "the found Fountain didn't unlock it far from every hall");
			try {
				Class<?> wild = Class.forName("io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks$Wild");
				java.lang.reflect.Method get = wild.getDeclaredMethod("get", MinecraftServer.class);
				get.setAccessible(true);
				Object set = get.invoke(null, level.getServer());
				helper.assertTrue(((net.minecraft.world.level.saveddata.SavedData) set).isDirty(), "the server-wide set wasn't marked to be saved");
				CompoundTag tag = ((net.minecraft.world.level.saveddata.SavedData) set).save(new CompoundTag(), level.registryAccess());
				helper.assertTrue(tag.contains("ids"), "the saved set has no ids: " + tag);
				java.lang.reflect.Method load = wild.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
				load.setAccessible(true);
				Object back = load.invoke(null, tag.copy(), level.registryAccess());
				java.lang.reflect.Field ids = wild.getDeclaredField("ids");
				ids.setAccessible(true);
				helper.assertTrue(((Set<?>) ids.get(back)).contains(fountain), "the reloaded server-wide set lost the Fountain: " + tag);
			} catch (ReflectiveOperationException e) {
				throw new GameTestAssertException("couldn't reach the server-wide set: " + e);
			}
			helper.succeed();
		});
	}

	@SuppressWarnings("unchecked")
	private static Set<ResourceLocation> wildIds(MinecraftServer server) {
		try {
			Class<?> wild = Class.forName("io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks$Wild");
			java.lang.reflect.Method get = wild.getDeclaredMethod("get", MinecraftServer.class);
			get.setAccessible(true);
			java.lang.reflect.Field ids = wild.getDeclaredField("ids");
			ids.setAccessible(true);
			return (Set<ResourceLocation>) ids.get(get.invoke(null, server));
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("couldn't reach the server-wide set: " + e);
		}
	}

	/**
	 * Section 3, the switch turned off and on again: off, the locked Market Stall is handed out and a blueprint used on
	 * the Table says nothing new; on again, the village never kept it (locked, refused at the Table), and the Market
	 * Stall blueprint taken out meanwhile is still a blueprint "had any way": used on the Table it unlocks the build and
	 * says so once.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1108bSwitchBack"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1108bSwitchBack")
	public void aBlueprintTakenWithTheSwitchOffUnlocksItOnceTheSwitchIsBack(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		QaBlueprintUnlocksGameTests.village(helper, HALL, TABLE);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ResourceLocation stall = StarterBlueprints.MARKET_STALL.id();
			helper.assertTrue(state(helper, stall) == State.LOCKED, "the Market Stall isn't locked to begin with");
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, TABLE);
			ServerPlayer p = c.player();
			BlockPos table = helper.absolutePos(TABLE);
			p.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
			BlueprintUnlocks.ENABLED = false;
			p.containerMenu = p.inventoryMenu;
			helper.assertTrue(TableServer.take(p, table, stall), "with the switch off the Market Stall was refused");
			helper.assertTrue(blueprintsOf(p, stall) == 1, "the Market Stall taken with the switch off isn't in the inventory");
			int slot = -1;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				if (BlueprintItem.data(p.getInventory().getItem(i)).map(d -> d.structure().equals(stall)).orElse(false)) {
					slot = i;
				}
			}
			ItemStack taken = p.getInventory().removeItemNoUpdate(slot);
			p.setItemInHand(InteractionHand.MAIN_HAND, taken);
			p.containerMenu = p.inventoryMenu;
			QaBlueprintUnlocksGameTests.clickTable(helper, p, TABLE);
			helper.assertTrue(QaBlueprintUnlocksGameTests.linesOf(c.seen(), NEW_LINE) == 0, "with the switch off a blueprint on the Table said 'new blueprint'");

			BlueprintUnlocks.ENABLED = true;
			helper.assertTrue(state(helper, stall) == State.LOCKED, "the village kept the Market Stall from the switch-off: " + state(helper, stall));
			p.containerMenu = p.inventoryMenu;
			helper.assertFalse(TableServer.take(p, table, stall), "with the switch back on the Table still hands out the Market Stall");
			helper.assertTrue(p.getMainHandItem().is(ModItems.BLUEPRINT) && blueprintsOf(p, stall) == 1, "the Market Stall blueprint taken out earlier is gone: " + p.getMainHandItem());
			c.sent();
			QaBlueprintUnlocksGameTests.clickTable(helper, p, TABLE);
			helper.assertTrue(state(helper, stall) == State.UNLOCKED, "the Market Stall blueprint used on the Table didn't unlock it");
			helper.assertTrue(QaBlueprintUnlocksGameTests.linesOf(c.seen(), NEW_LINE) == 1, "'new blueprint' was said " + QaBlueprintUnlocksGameTests.linesOf(c.seen(), NEW_LINE) + " times");
			helper.assertTrue(read(helper, c.seen(), NEW_LINE).equals("The village's library has a new blueprint: Market Stall"), "the line reads \"" + read(helper, c.seen(), NEW_LINE) + "\"");
			helper.assertTrue(blueprintsOf(p, stall) == 1, "the Table took the Market Stall blueprint");
			helper.succeed();
		});
	}
}
