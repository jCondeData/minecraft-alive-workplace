package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupPage;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.cup.Cups;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * The Festival Cup's calendar, themes and entrants (ROADMAP 28.17): the themes as data (a broken file logged and
 * skipped), the circuit from trade routes, the calendar (every festival, every second), the entrants (Leader, best
 * Trainer, the players' rights and caps), seeding and byes, the Leader record on the caravans' list read while the
 * village is unloaded, the Cup saved and reloaded, and the hall's Cup page with every sentence it shows.
 */
public class CupGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final ResourceLocation GRAND = AliveWorkplace.id("grand_cup");

	// ---------------------------------------------------------------- helpers

	/** A host: the hall (a Village), a finished Arena by it on record, Cobblemon assumed; put back after. */
	private static VillageHallBlockEntity host(GameTestHelper helper, List<BlueprintData.Placement> arenas) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		entity.setRank(VillageRanks.Rank.VILLAGE);
		BlueprintData.Placement arena = new BlueprintData.Placement(level.dimension().location(), hall.offset(-12, -1, 3), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.ARENA.id(), arena, UUID.randomUUID());
		arenas.add(arena);
		Cups.COBBLEMON = true;
		return entity;
	}

	private static void cleanUp(GameTestHelper helper, List<BlueprintData.Placement> arenas, Runnable more) {
		Leftovers.after(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			arenas.forEach(BuildSiteManager.get(level)::forgetFinished);
			CupData.get(level).forget(hall);
			Caravans.Data.get(level).remove(hall);
			Cups.COBBLEMON = io.github.jcondedata.aliveworkplace.trainer.Trainers.COBBLEMON;
			Cups.EVERY = 1;
			Cups.ENABLED = true;
			more.run();
		});
	}

	private static Villager trainer(GameTestHelper helper, BlockPos at, boolean leader, int level, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(leader ? ModVillagers.TRAINER_LEADER : ModVillagers.TRAINER).setLevel(level));
		if (name != null) {
			v.setCustomName(Component.literal(name));
		}
		return v;
	}

	private static CupData.Entrant entrant(CupData.Kind kind, int tier, String name) {
		return new CupData.Entrant(kind, UUID.nameUUIDFromBytes(name.getBytes()), name, tier, tier * 10, BlockPos.ZERO);
	}

	/** The hall's screen for {@code player}, on its Cup page. */
	private static ChoiceMenu page(ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.forTest(player, hall);
		menu.press(HallPages.slot(CupPage.PAGE), player);
		return menu;
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static String name(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? "" : name.getString();
	}

	private static void has(GameTestHelper helper, ItemStack icon, String line) {
		helper.assertTrue(lore(icon).contains(line), name(icon) + " lacks \"" + line + "\": " + lore(icon));
	}

	// ---------------------------------------------------------------- themes

	/** The Grand Cup ships as data; a broken file is logged and skipped while the good one beside it loads; themes come in order. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"cupThemes"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "cupThemes")
	public void themesLoadAndABrokenFileIsSkipped(GameTestHelper helper) {
		CupThemes.Theme grand = CupThemes.get(GRAND);
		helper.assertTrue(grand != null, "the Grand Cup isn't loaded: " + CupThemes.loaded().keySet());
		helper.assertTrue(grand.name().equals("cup.aliveworkplace.grand_cup") && grand.order() == 8 && !grand.doubles() && grand.level() == 100
			&& grand.bring() == 6 && grand.types().isEmpty() && grand.stage().equals("any"), "Grand Cup: " + grand);
		helper.assertTrue(grand.banned().equals(List.of("legendary", "mythical", "ultra_beast", "paradox"))
			&& grand.rules().equals(List.of("Species Clause", "Item Clause")), "Grand Cup's bans and rules: " + grand);
		helper.assertTrue(grand.start() == CupThemes.NOON && grand.end() == CupThemes.MIDNIGHT, "Grand Cup's hours: " + grand.start() + "-" + grand.end());
		helper.assertTrue(grand.wares().size() == 5 && grand.wares().stream().allMatch(w -> w.price() >= 4 && w.price() <= 16)
			&& grand.wares().get(0).item().toString().equals("cobblemon:ability_capsule"), "Grand Cup's wares: " + grand.wares());
		helper.assertTrue(grand.dish().toString().equals("cobblemon:big_malasada") && grand.disc().toString().equals("minecraft:music_disc_creator")
			&& grand.fireworks().equals(List.of(0xF5C542, 0xE83F3F)), "Grand Cup's dish, disc, fireworks: " + grand);

		String good = "{\"name\": \"cup.test.dusk\", \"order\": 2, \"format\": \"doubles\", \"level\": 50, \"bring\": 4, \"types\": [\"ghost\"],"
			+ " \"stage\": \"final\", \"start\": 12000, \"end\": 0, \"wares\": [{\"item\": \"minecraft:apple\", \"price\": 4}],"
			+ " \"dish\": \"minecraft:bread\", \"fireworks\": [\"#800080\"], \"disc\": \"minecraft:music_disc_13\"}";
		String first = good.replace("cup.test.dusk", "cup.test.dawn").replace("\"order\": 2", "\"order\": 1");
		Map<ResourceLocation, String> files = new LinkedHashMap<>();
		files.put(ResourceLocation.parse("aliveworkplace_test:cups/good_dusk.json"), good);
		files.put(ResourceLocation.parse("aliveworkplace_test:cups/bad_json.json"), "{not json");
		files.put(ResourceLocation.parse("aliveworkplace_test:cups/bad_format.json"), good.replace("doubles", "triples"));
		files.put(ResourceLocation.parse("aliveworkplace_test:cups/bad_missing_level.json"), good.replace("\"level\": 50,", ""));
		files.put(ResourceLocation.parse("aliveworkplace_test:cups/bad_colour.json"), good.replace("#800080", "purple"));
		files.put(ResourceLocation.parse("aliveworkplace_test:cups/good_dawn.json"), first);
		Map<ResourceLocation, CupThemes.Theme> loaded = CupThemes.load(files);
		helper.assertTrue(loaded.keySet().equals(new java.util.LinkedHashSet<>(List.of(ResourceLocation.parse("aliveworkplace_test:good_dawn"),
			ResourceLocation.parse("aliveworkplace_test:good_dusk")))), "loaded (in order) " + loaded.keySet());
		CupThemes.Theme dusk = loaded.get(ResourceLocation.parse("aliveworkplace_test:good_dusk"));
		helper.assertTrue(dusk.doubles() && dusk.start() == 12000 && dusk.end() == 24000 && dusk.types().equals(List.of("ghost")),
			"a theme from dusk runs until dawn: " + dusk);

		Map<ResourceLocation, CupThemes.Theme> saved = CupThemes.loaded();
		try {
			CupThemes.setForTest(loaded);
			CupThemes.Theme dawn = loaded.get(ResourceLocation.parse("aliveworkplace_test:good_dawn"));
			helper.assertTrue(CupThemes.next(null) == dawn && CupThemes.next(dawn.id()) == dusk && CupThemes.next(dusk.id()) == dawn,
				"themes don't come in order");
			List<String> rules = CupPage.rules(dusk).stream().map(Component::getString).toList();
			helper.assertTrue(rules.equals(List.of("Doubles; each trainer brings 4 Pokémon, all at level 50", "Types: Ghost", "Only fully evolved Pokémon",
				"Bouts from dusk until dawn")), "rules in plain words: " + rules);
		} finally {
			CupThemes.setForTest(saved);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- circuit and calendar

	/** The circuit: the host and every village on the list it has a route with, either way, nearest first, seven at most. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"cupCircuit"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "cupCircuit")
	public void circuitIsTheHostAndItsTradePartnersNearestFirst(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Caravans.Data data = Caravans.Data.get(level);
		BlockPos host = helper.absolutePos(BlockPos.ZERO).offset(40_000, 0, 40_000); // far off: only on the list
		List<BlockPos> made = new ArrayList<>();
		try {
			data.setWants(host, Component.literal("Host"), List.of());
			made.add(host);
			List<BlockPos> partners = new ArrayList<>();
			for (int i = 1; i <= 9; i++) {
				BlockPos p = host.offset(i % 2 == 0 ? 100 * (10 - i) : -100 * (10 - i), 0, 7 * i);
				data.setWants(p, Component.literal("P" + i), List.of());
				made.add(p);
				partners.add(p);
				// odd ones: the host's route out; even ones: their route to the host
				helper.assertTrue(i % 2 == 1 ? data.toggleRoute(host, p, 10) : data.toggleRoute(p, host, 10), "setup: no route " + i);
			}
			BlockPos stranger = host.offset(30, 0, 30); // nearest, but no route
			data.setWants(stranger, Component.literal("Stranger"), List.of());
			made.add(stranger);
			BlockPos gone = host.offset(20, 0, 0); // a route to a hall that's no longer on the list
			data.toggleRoute(host, gone, 10);
			List<BlockPos> circuit = Cups.circuit(level, host);
			List<BlockPos> nearest = new ArrayList<>(partners);
			nearest.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(host)));
			List<BlockPos> expected = new ArrayList<>();
			expected.add(host);
			expected.addAll(nearest.subList(0, Cups.MAX_CIRCUIT - 1));
			helper.assertTrue(circuit.equals(expected), "circuit " + circuit + ", expected " + expected);
			helper.assertTrue(!circuit.contains(stranger) && !circuit.contains(gone), "a village with no route is on the circuit");
		} finally {
			made.forEach(data::remove);
		}
		helper.succeed();
	}

	/** The calendar: with a Cup every festival, the next festival is one; with every second, festivals 0, 2, 4... from the hall's offset. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void calendarEveryFestivalOrEverySecond(GameTestHelper helper) {
		// festivals every 8 days from day 3: days 3, 11, 19, 27...
		helper.assertTrue(Cups.cupDay(1, 3, 8, 1) == 3 && Cups.cupDay(3, 3, 8, 1) == 3 && Cups.cupDay(4, 3, 8, 1) == 11 && Cups.cupDay(12, 3, 8, 1) == 19,
			"every festival: " + Cups.cupDay(1, 3, 8, 1) + ", " + Cups.cupDay(4, 3, 8, 1) + ", " + Cups.cupDay(12, 3, 8, 1));
		helper.assertTrue(Cups.cupDay(1, 3, 8, 2) == 3 && Cups.cupDay(4, 3, 8, 2) == 19 && Cups.cupDay(20, 3, 8, 2) == 35,
			"every second festival: " + Cups.cupDay(4, 3, 8, 2) + ", " + Cups.cupDay(20, 3, 8, 2));
		helper.assertTrue(Cups.cupDay(2, 3, 2, 1) == 3 && Cups.cupDay(4, 3, 2, 2) == 7, "a Festival Season's shorter gap");
		helper.assertTrue(Cups.isCup(0, 2) && !Cups.isCup(1, 2) && Cups.isCup(2, 2) && Cups.isCup(1, 1) && Cups.isCup(5, 1), "isCup");
		helper.succeed();
	}

	// ---------------------------------------------------------------- seeding and byes

	/** Seeds: Leaders by tier, then players, then the host's Trainers; byes to the top seeds; fewer than 4, no Cup. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void seedingAndByesFor3_5_8And11(GameTestHelper helper) {
		List<CupData.Entrant> all = List.of(
			entrant(CupData.Kind.HOST_TRAINER, 5, "host5"), entrant(CupData.Kind.PLAYER, 0, "alex"), entrant(CupData.Kind.LEADER, 4, "lead4"),
			entrant(CupData.Kind.TRAINER, 3, "train3"), entrant(CupData.Kind.LEADER, 5, "lead5"), entrant(CupData.Kind.PLAYER, 0, "sam"),
			entrant(CupData.Kind.HOST_TRAINER, 2, "host2"), entrant(CupData.Kind.TRAINER, 4, "train4"), entrant(CupData.Kind.LEADER, 3, "lead3"),
			entrant(CupData.Kind.PLAYER, 0, "kim"), entrant(CupData.Kind.TRAINER, 2, "train2"));
		List<String> seeded = Cups.seed(all).stream().map(CupData.Entrant::name).toList();
		helper.assertTrue(seeded.equals(List.of("lead5", "lead4", "train4", "lead3", "train3", "train2", "alex", "sam", "kim", "host5", "host2")),
			"seeds: " + seeded);
		helper.assertTrue(Cups.capacity(VillageRanks.Rank.VILLAGE) == 4 && Cups.capacity(VillageRanks.Rank.TOWN) == 8
			&& Cups.capacity(VillageRanks.Rank.CITY) == 16, "capacities");
		List<CupData.Entrant> seeds = Cups.seed(all);

		helper.assertTrue(Cups.bracket(seeds.subList(0, 3), 16).isEmpty(), "3 entrants: no Cup");
		checkBracket(helper, seeds.subList(0, 5), 16, 8, 3);
		checkBracket(helper, seeds.subList(0, 8), 16, 8, 0);
		checkBracket(helper, seeds, 16, 16, 5);
		// a Town host's bracket holds 8: the three lowest seeds are left out
		List<CupData.Entrant> town = Cups.bracket(seeds, 8);
		helper.assertTrue(town.size() == 8 && Cups.byes(town) == 0 && !town.contains(seeds.get(8)) && town.contains(seeds.get(7)),
			"Town host with 11: " + town.stream().map(e -> e == null ? "bye" : e.name()).toList());
		checkBracket(helper, seeds.subList(0, 4), 4, 4, 0);
		helper.assertTrue(java.util.Arrays.equals(Cups.order(8), new int[]{1, 8, 4, 5, 2, 7, 3, 6}), "order(8) " + java.util.Arrays.toString(Cups.order(8)));
		helper.succeed();
	}

	/** A bracket of {@code size} with {@code byes} byes, each against one of the top seeds, and seed 1 and 2 in different halves. */
	private static void checkBracket(GameTestHelper helper, List<CupData.Entrant> seeds, int capacity, int size, int byes) {
		List<CupData.Entrant> bracket = Cups.bracket(seeds, capacity);
		helper.assertTrue(bracket.size() == size && Cups.byes(bracket) == byes, seeds.size() + " entrants: " + bracket.size() + " slots, "
			+ Cups.byes(bracket) + " byes");
		List<CupData.Entrant> gotBye = new ArrayList<>();
		for (int i = 0; i < bracket.size(); i += 2) {
			CupData.Entrant a = bracket.get(i);
			CupData.Entrant b = bracket.get(i + 1);
			helper.assertTrue(a != null || b != null, "two byes meet");
			if (a == null || b == null) {
				gotBye.add(a == null ? b : a);
			}
		}
		helper.assertTrue(gotBye.size() == byes && gotBye.stream().allMatch(e -> seeds.indexOf(e) < byes), "byes went to "
			+ gotBye.stream().map(CupData.Entrant::name).toList());
		helper.assertTrue(bracket.indexOf(seeds.get(0)) < size / 2 && bracket.indexOf(seeds.get(1)) >= size / 2, "seeds 1 and 2 meet before the final");
		helper.assertTrue(bracket.get(0) == seeds.get(0), "seed 1 isn't first");
	}

	// ---------------------------------------------------------------- the Leader record

	/** The hall's round writes its Trainer Leader on the caravans' list; it's read from there while the village is unloaded, and saved. */
	//$ gametest_ticks_batch AREA '200' '"cupLeaderRecord"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "cupLeaderRecord")
	public void leaderRecordWrittenAndReadWhileUnloaded(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<BlueprintData.Placement> arenas = new ArrayList<>();
		BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(50_000, 0, -50_000);
		cleanUp(helper, arenas, () -> Caravans.Data.get(helper.getLevel()).remove(far));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
			Caravans.Data data = Caravans.Data.get(level);
			data.setWants(hall, Component.literal("Ashford"), List.of());
			helper.assertTrue(data.leader(hall) == null, "a village has no Leader on record before its round");
			Villager mira = trainer(helper, new BlockPos(12, 2, 12), true, 4, "Mira");
			mira.setVillagerXp(150);
			trainer(helper, new BlockPos(13, 2, 12), false, 5, "Tess"); // a Master Trainer, but no Leader
			Cups.round(level, hall, entity);
			Caravans.Leader written = data.leader(hall);
			helper.assertTrue(written != null && written.id().equals(mira.getUUID()) && written.name().equals("Mira") && written.tier() == 4
				&& written.xp() == 150 && written.leader(), "written " + written);
			// saved and loaded with the list; a list saved before 28.17 has none
			CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
			helper.assertTrue(written.equals(Caravans.Data.load(tag, level.registryAccess()).leader(hall)), "the record isn't saved");
			// the village unloaded: its Leader read from the record
			mira.discard();
			helper.assertTrue(written.equals(Cups.delegate(level, hall, false)), "read while unloaded: " + Cups.delegate(level, hall, false));
			Caravans.Leader live = Cups.delegate(level, hall, true);
			helper.assertTrue(live != null && live.name().equals("Tess") && !live.leader() && live.tier() == 5, "without a Leader, the best Trainer: " + live);
			// a far village nobody has loaded sends the one on record
			data.setWants(far, Component.literal("Farhollow"), List.of());
			data.setLeader(far, new Caravans.Leader(UUID.nameUUIDFromBytes("far".getBytes()), "Oren", 5, 400, true));
			helper.assertTrue(!level.isLoaded(far) && Cups.delegate(level, far) != null && Cups.delegate(level, far).name().equals("Oren"),
				"a far village's Leader isn't read from the record");
			// the round writes the best Trainer when there's no Leader, and none when there's no one
			Cups.round(level, hall, entity);
			helper.assertTrue(data.leader(hall) != null && data.leader(hall).name().equals("Tess"), "the record now: " + data.leader(hall));
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			Cups.round(level, hall, entity);
			helper.assertTrue(data.leader(hall) == null, "a village with no Trainer still has one on record");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- entrants

	/** Entrants: the Leader, players by right (owner, friend, nobody's village) two a village, the host's Trainers to fill it. */
	//$ gametest_ticks_batch AREA '200' '"cupEntrants"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "cupEntrants")
	public void entrantsLeaderBestTrainerPlayersRightsAndCaps(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<BlueprintData.Placement> arenas = new ArrayList<>();
		List<UUID[]> friends = new ArrayList<>();
		cleanUp(helper, arenas, () -> friends.forEach(f -> Friends.get(helper.getLevel().getServer()).remove(f[0], f[1])));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			VillageHallBlockEntity entity = host(helper, arenas);
			Villager leader = trainer(helper, new BlockPos(12, 2, 12), true, 4, "Mira");
			Villager t3 = trainer(helper, new BlockPos(13, 2, 12), false, 3, "Ren");
			Villager t2 = trainer(helper, new BlockPos(14, 2, 12), false, 2, "Pip");
			trainer(helper, new BlockPos(15, 2, 12), false, 1, "Ned");
			Cups.round(level, hall, entity);
			CupData.Cup cup = CupData.get(level).existing(hall);
			helper.assertTrue(cup != null && GRAND.equals(cup.theme) && cup.day >= 0, "no Cup set: " + (cup == null ? null : cup.theme + " " + cup.day));
			cup.closed = false;

			ServerPlayer owner = helper.makeMockServerPlayerInLevel();
			ServerPlayer friend = helper.makeMockServerPlayerInLevel();
			ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
			entity.setOwner(owner.getUUID(), "Owner");
			Friends.get(level.getServer()).add(owner.getUUID(), friend.getUUID(), "Friend");
			friends.add(new UUID[]{owner.getUUID(), friend.getUUID()});
			String refused = Cups.signUp(level, hall, stranger).getString();
			helper.assertTrue(refused.startsWith("You may not stand for ") && refused.endsWith(": only its owner and the owner's friends may"),
				"a stranger to an owned village: " + refused);
			helper.assertTrue(Cups.signUp(level, hall, owner).getString().startsWith("You're signed up for the Cup, standing for "), "the owner");
			helper.assertTrue(Cups.signUp(level, hall, friend).getString().startsWith("You're signed up"), "the owner's friend");
			helper.assertTrue(Cups.signUp(level, hall, owner).getString().equals("You've already signed up for this Cup"), "signed up twice");
			entity.setOwner(null, "");
			String full = Cups.signUp(level, hall, stranger).getString();
			helper.assertTrue(full.endsWith(" already has its 2 players"), "a third player for one village: " + full);
			helper.assertTrue(Cups.withdraw(level, hall, owner).getString().equals("You've withdrawn from the Cup"), "withdraw");
			helper.assertTrue(Cups.withdraw(level, hall, owner).getString().equals("You aren't signed up for this Cup"), "withdraw twice");
			helper.assertTrue(Cups.signUp(level, hall, stranger).getString().startsWith("You're signed up"), "anyone, for a village nobody owns");

			List<CupData.Entrant> entrants = Cups.entrants(level, hall, cup);
			List<String> got = entrants.stream().map(e -> e.kind() + ":" + e.name()).toList();
			helper.assertTrue(got.equals(List.of("LEADER:Mira", "PLAYER:" + friend.getGameProfile().getName(), "PLAYER:" + stranger.getGameProfile().getName(),
				"HOST_TRAINER:Ren")), "entrants (a Village host's bracket holds 4): " + got);
			helper.assertTrue(entrants.get(1).id().equals(friend.getUUID()) && entrants.get(2).id().equals(stranger.getUUID()), "players in sign-up order");

			// a Town host's bracket holds 8: two of the host's Trainers at most
			entity.setRank(VillageRanks.Rank.TOWN);
			got = Cups.entrants(level, hall, cup).stream().map(e -> e.kind() + ":" + e.name()).toList();
			helper.assertTrue(got.size() == 5 && got.get(3).equals("HOST_TRAINER:Ren") && got.get(4).equals("HOST_TRAINER:Pip"), "Town host: " + got);
			// no Leader: the best Trainer goes for the village
			leader.discard();
			got = Cups.entrants(level, hall, cup).stream().map(e -> e.kind() + ":" + e.name()).toList();
			helper.assertTrue(got.get(0).equals("TRAINER:Ren") && got.contains("HOST_TRAINER:Pip") && got.contains("HOST_TRAINER:Ned"),
				"without a Leader: " + got);

			// sign-up closes on the day: drawn and kept; then no one may sign up or withdraw
			entity.setRank(VillageRanks.Rank.VILLAGE);
			Cups.close(level, hall, cup);
			helper.assertTrue(cup.closed && cup.bracket.size() == 4 && Cups.byes(cup.bracket) == 0 && cup.noCup.isEmpty(), "closed: " + cup.bracket);
			helper.assertTrue(Cups.signUp(level, hall, owner).getString().equals("Sign-up for this Cup has closed"), "signed up after it closed");
			helper.assertTrue(Cups.withdraw(level, hall, stranger).getString().equals("Sign-up for this Cup has closed"), "withdrew after it closed");
			// fewer than 4: no Cup, and why
			t3.discard();
			t2.discard();
			cup.signups.clear();
			Cups.close(level, hall, cup);
			helper.assertTrue(cup.bracket.isEmpty() && Component.translatable(cup.noCup, Cups.MIN_ENTRANTS).getString()
				.equals("Fewer than 4 entrants: no Cup this time, just a plain festival"), "too few: " + cup.noCup);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- calendar from the round, and the Cup saved

	/** The round sets the Cup on the next festival (every second with cupEveryFestivals 2), and the whole Cup saves and loads. */
	//$ gametest_ticks_batch AREA '200' '"cupSaved"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "cupSaved")
	public void cupCalendarFromTheRoundAndSavedAndReloaded(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<BlueprintData.Placement> arenas = new ArrayList<>();
		cleanUp(helper, arenas, () -> {
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			VillageHallBlockEntity entity = host(helper, arenas);
			// not a host: off, no Cobblemon, a Hamlet, no Arena
			Cups.ENABLED = false;
			helper.assertTrue("screen.aliveworkplace.cup.why.off".equals(Cups.hostProblem(level, hall)), "off");
			Cups.ENABLED = true;
			Cups.COBBLEMON = false;
			helper.assertTrue("screen.aliveworkplace.cup.why.cobblemon".equals(Cups.hostProblem(level, hall)), "no Cobblemon");
			Cups.COBBLEMON = true;
			entity.setRank(VillageRanks.Rank.HAMLET);
			helper.assertTrue("screen.aliveworkplace.cup.why.rank".equals(Cups.hostProblem(level, hall)), "a Hamlet");
			entity.setRank(VillageRanks.Rank.VILLAGE);
			BuildSiteManager.get(level).forgetFinished(arenas.get(0));
			helper.assertTrue("screen.aliveworkplace.cup.why.arena".equals(Cups.hostProblem(level, hall)), "no Arena");
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.ARENA.id(), arenas.get(0), UUID.randomUUID());
			helper.assertTrue(Cups.canHost(level, hall), "can't host: " + Cups.hostProblem(level, hall));

			long now = level.getDayTime();
			long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
			long from = now % 24000 < CupThemes.MIDNIGHT ? today : today + 1;
			int gap = Festivals.every(entity);
			long offset = Festivals.offset(hall);
			Cups.round(level, hall, entity);
			CupData.Cup cup = CupData.get(level).existing(hall);
			helper.assertTrue(cup != null && cup.day == Cups.cupDay(from, offset, gap, 1), "every festival: day " + (cup == null ? -1 : cup.day)
				+ ", expected " + Cups.cupDay(from, offset, gap, 1));
			helper.assertTrue(Math.floorMod(cup.day - offset, gap) == 0, "the Cup isn't on a festival day");
			CupData.get(level).forget(hall);
			Cups.EVERY = 2;
			Cups.round(level, hall, entity);
			cup = CupData.get(level).existing(hall);
			long n = Math.floorDiv(cup.day - offset, gap);
			helper.assertTrue(cup.day == Cups.cupDay(from, offset, gap, 2) && n % 2 == 0 && cup.day >= from, "every second festival: day " + cup.day);
			helper.assertTrue(!cup.closed || cup.day == today, "sign-up closed early");

			// a whole Cup: saved and loaded
			cup.signups.add(new CupData.Signup(UUID.nameUUIDFromBytes("p".getBytes()), "Alex", hall));
			cup.closed = true;
			cup.entrants.add(new CupData.Entrant(CupData.Kind.LEADER, UUID.nameUUIDFromBytes("l".getBytes()), "Mira", 4, 150, hall));
			cup.entrants.add(new CupData.Entrant(CupData.Kind.PLAYER, UUID.nameUUIDFromBytes("p".getBytes()), "Alex", 0, 0, hall));
			cup.bracket.add(cup.entrants.get(0));
			cup.bracket.add(null);
			cup.bracket.add(cup.entrants.get(1));
			cup.bracket.add(null);
			cup.results.add(new CupData.Result(1, UUID.nameUUIDFromBytes("l".getBytes()), UUID.nameUUIDFromBytes("p".getBytes())));
			cup.champions.add(new CupData.Champion(3, GRAND, "Mira", hall));
			cup.themePicked = true;
			cup.lastTheme = ResourceLocation.parse("aliveworkplace:little_cup");
			cup.toldOpen = cup.day;
			CompoundTag tag = CupData.get(level).save(new CompoundTag(), level.registryAccess());
			CupData.Cup back = CupData.load(tag, level.registryAccess()).existing(hall);
			helper.assertTrue(back != null && GRAND.equals(back.theme) && back.day == cup.day && back.closed && back.themePicked
				&& back.lastTheme.equals(cup.lastTheme) && back.toldOpen == cup.day && back.toldEvening == cup.toldEvening, "the Cup reloaded: " + back);
			helper.assertTrue(back.signups.equals(cup.signups) && back.entrants.equals(cup.entrants) && back.bracket.equals(cup.bracket)
				&& back.results.equals(cup.results) && back.champions.equals(cup.champions), "lists reloaded: " + back.bracket + " " + back.champions);
			// a Cup saved with no fields (an older save) loads with the defaults
			CompoundTag bare = new CompoundTag();
			net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
			CompoundTag one = new CompoundTag();
			one.putLong("host", hall.asLong());
			list.add(one);
			bare.put("cups", list);
			CupData.Cup empty = CupData.load(bare, level.registryAccess()).existing(hall);
			helper.assertTrue(empty != null && empty.theme == null && empty.day == -1 && !empty.closed && empty.signups.isEmpty(), "bare Cup");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the page

	/** The hall's Cup page: the Cup's card with its rules in plain words, the circuit and its entrant, Sign up / Withdraw, champions. */
	//$ gametest_ticks_batch AREA '200' '"cupPage"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "cupPage")
	public void cupPageShowsTheCupAndSignsUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<BlueprintData.Placement> arenas = new ArrayList<>();
		cleanUp(helper, arenas, () -> {
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			Cups.COBBLEMON = true;
			// before it can host: the page says why
			ChoiceMenu menu = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.forTest(player, hall);
			helper.assertTrue(HallPages.slot(CupPage.PAGE) >= 0, "no Cup tab");
			ItemStack tab = menu.icon(HallPages.slot(CupPage.PAGE));
			helper.assertTrue(name(tab).equals("Festival Cup") && lore(tab).contains("Click: the next Cup, its circuit and sign-up"), "tab " + lore(tab));
			menu.press(HallPages.slot(CupPage.PAGE), player);
			ItemStack none = menu.icon(CupPage.CARD);
			helper.assertTrue(name(none).equals("No Festival Cup here yet"), "card " + name(none));
			has(helper, none, "This village is too small to host a Cup: it must be a Village or bigger");
			has(helper, none, "A host needs a finished Arena and Village rank or better");
			has(helper, none, "Villages with a trade route to a host join its circuit");
			has(helper, menu.icon(CupPage.CHAMPIONS), "No champions yet");

			VillageHallBlockEntity entity = host(helper, arenas);
			trainer(helper, new BlockPos(12, 2, 12), true, 4, "Mira");
			Cups.round(level, hall, entity);
			CupData.Cup cup = CupData.get(level).existing(hall);
			cup.closed = false;
			cup.toldOpen = -1;
			menu = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.forTest(player, hall);
			tab = menu.icon(HallPages.slot(CupPage.PAGE));
			helper.assertTrue(lore(tab).contains("Next: the Grand Cup, day " + cup.day), "tab " + lore(tab));
			menu = page(player, hall);
			String village = VillageHalls.name(level, hall).getString();
			has(helper, menu.icon(4), "Trainers from the trade partners' villages, and players, battle at the host's Arena on its festival");
			helper.assertTrue(name(menu.icon(4)).equals("The Festival Cup at " + village), "header " + name(menu.icon(4)));
			ItemStack card = menu.icon(CupPage.CARD);
			helper.assertTrue(name(card).equals("Grand Cup"), "card " + name(card));
			has(helper, card, "At " + village + ", on day " + cup.day);
			has(helper, card, "Singles; each trainer brings 6 Pokémon, all at level 100");
			has(helper, card, "Any type");
			has(helper, card, "Not allowed: legendary, mythical, Ultra Beast, paradox Pokémon");
			has(helper, card, "Species Clause: no two of the same Pokémon");
			has(helper, card, "Item Clause: no two holding the same item");
			has(helper, card, "Bouts from noon until midnight");
			boolean open = Cups.signupOpen(level, entity, cup);
			has(helper, card, open ? "Sign-up is open" : "Sign-up opens after the festival on day " + Cups.opensDay(cup.day, entity));
			ItemStack theme = menu.icon(CupPage.THEME);
			helper.assertTrue(name(theme).equals("Theme: Grand Cup"), "theme " + name(theme));
			has(helper, theme, "The next theme in order");
			has(helper, theme, "Click: the next theme instead (the host's owner, until sign-up closes)");
			has(helper, menu.icon(CupPage.SIGN_UP), "You may stand for this village");
			helper.assertTrue(name(menu.icon(CupPage.SIGN_UP)).equals("Sign up for " + village), "sign up " + name(menu.icon(CupPage.SIGN_UP)));
			has(helper, menu.icon(CupPage.WITHDRAW), "You haven't signed up");
			helper.assertTrue(name(menu.icon(CupPage.BRACKET)).equals("The bracket holds 4"), "bracket " + name(menu.icon(CupPage.BRACKET)));
			has(helper, menu.icon(CupPage.BRACKET), "Entrants: 1");
			has(helper, menu.icon(CupPage.BRACKET), "Fewer than 4 entrants: no Cup this time, just a plain festival");
			ItemStack bell = menu.icon(CupPage.CIRCUIT_ROW);
			helper.assertTrue(name(bell).equals(village), "circuit " + name(bell));
			has(helper, bell, "Hosts the Cup");
			has(helper, bell, "Sends Mira, Expert");
			helper.assertTrue(name(menu.icon(CupPage.PLAYERS_ROW)).equals("No players signed up yet"), "players " + name(menu.icon(CupPage.PLAYERS_ROW)));
			ItemStack seed = menu.icon(CupPage.SEEDS_ROW);
			helper.assertTrue(name(seed).equals("Seed 1: Mira"), "seed " + name(seed));
			has(helper, seed, "Trainer Leader of " + village);

			// the buttons
			menu.press(CupPage.THEME, player); // nobody owns the hall: anyone may pick (one theme: it stays)
			helper.assertTrue(cup.themePicked && GRAND.equals(cup.theme), "theme pick");
			has(helper, menu.icon(CupPage.THEME), "Picked by the host's owner");
			menu.press(CupPage.SIGN_UP, player);
			helper.assertTrue(cup.signups.size() == 1 && cup.signups.get(0).player().equals(player.getUUID()), "sign up: " + cup.signups);
			ItemStack signed = menu.icon(CupPage.PLAYERS_ROW);
			helper.assertTrue(name(signed).equals(player.getGameProfile().getName()), "signed up " + name(signed));
			has(helper, signed, "Stands for " + village);
			has(helper, menu.icon(CupPage.WITHDRAW), "Click: take your name off the list");
			has(helper, menu.icon(CupPage.SEEDS_ROW + 1), "Player, for " + village);
			menu.press(CupPage.WITHDRAW, player);
			helper.assertTrue(cup.signups.isEmpty(), "withdraw: " + cup.signups);
			ServerPlayer owner = helper.makeMockServerPlayerInLevel();
			entity.setOwner(owner.getUUID(), "Owner");
			helper.assertTrue(Cups.nextTheme(level, hall, player).getString().equals("Only the host village's owner may pick the Cup's theme"), "owner only");
			helper.assertTrue(Cups.nextTheme(level, hall, owner).getString().equals("The next Cup will be the Grand Cup"), "owner picks");
			menu = page(player, hall);
			has(helper, menu.icon(CupPage.SIGN_UP), "Only its owner and the owner's friends may stand for this village");

			// the roll of champions; a closed Cup's status
			cup.champions.add(new CupData.Champion(3, GRAND, "Ash", hall));
			cup.closed = true;
			menu = page(player, hall);
			has(helper, menu.icon(CupPage.CHAMPIONS), "Day 3, Grand Cup: Ash of " + village);
			has(helper, menu.icon(CupPage.CARD), "Sign-up has closed: the bracket is drawn");
			helper.assertTrue(name(menu.icon(CupPage.CHAMPIONS)).equals("Roll of champions"), "champions " + name(menu.icon(CupPage.CHAMPIONS)));

			// what players are told
			Component cupName = Component.translatable("cup.aliveworkplace.grand_cup");
			helper.assertTrue(Component.translatable("message.aliveworkplace.cup.open", cupName, village, 12).getString()
				.equals("Sign-up is open for the Grand Cup at " + village + " on day 12: sign up on the Cup page of any Village Hall on its circuit."), "open");
			helper.assertTrue(Component.translatable("message.aliveworkplace.cup.tomorrow", cupName, village, Cups.hour(6000)).getString()
				.equals("The Grand Cup at " + village + " is tomorrow: the bouts start at noon."), "tomorrow");
			helper.assertTrue(Cups.hour(12000).getString().equals("dusk") && Cups.hour(0).getString().equals("dawn")
				&& Cups.hour(18000).getString().equals("midnight") && Cups.hour(9000).getString().equals("15:00"), "hours");
			helper.assertTrue(Component.translatable("message.aliveworkplace.cup.no_host").getString().equals("This village is on no Festival Cup's circuit")
				&& Component.translatable("screen.aliveworkplace.cup.why.cobblemon").getString().equals("The Festival Cup needs Cobblemon")
				&& Component.translatable("screen.aliveworkplace.cup.why.off").getString().equals("The Festival Cup is switched off (config festivalCup)")
				&& Component.translatable("screen.aliveworkplace.cup.why.arena").getString().equals("This village has no finished Arena to host a Cup in")
				&& Component.translatable("screen.aliveworkplace.cup.why.hall").getString().equals("Only a village with a Village Hall can hold a Cup")
				&& Component.translatable("screen.aliveworkplace.cup.why.soon").getString().equals("The next Cup is set at the hall's next round"),
				"why-not lines");
			helper.succeed();
		});
	}
}
