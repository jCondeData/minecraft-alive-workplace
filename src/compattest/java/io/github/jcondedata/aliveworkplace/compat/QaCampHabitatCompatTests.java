package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.camp.CampCooks;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupMatches;
import io.github.jcondedata.aliveworkplace.cup.Cups;
import io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA (ROADMAP 28.8, 28.10, 28.1a), written from the spec: the lure-spot range from both sides, the fourth marked spot,
 * the grass ring, a sighting not told twice across a save, rare and too-far sightings, the Camp Cook's keep-16 limit and
 * switch, the job never taken with the wrong item or by itself, and the Cup purse with and without the City bonus.
 */
public class QaCampHabitatCompatTests implements FabricGameTest {
	static final String AREA = CompatGameTests.AREA;
	static final BlockPos STATION = new BlockPos(2, 2, 2);
	static final BlockPos CHEST = new BlockPos(2, 2, 4);

	static ResourceLocation c(String path) {
		return ResourceLocation.fromNamespaceAndPath("cobblemon", path);
	}

	static Item item(String path) {
		return BuiltInRegistries.ITEM.get(c(path));
	}

	static String said(GameTestHelper helper, Villager v) {
		WorkerStatus.Entry e = WorkerStatus.get(v, helper.getLevel().getGameTime());
		return e == null ? "no status" : e.line().getString();
	}

	/** A player whose chat lines are kept in {@code seen} (as the main suite's RealmGameTests.listener). */
	static ServerPlayer listener(GameTestHelper helper, List<Component> seen, String name) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message);
				super.displayClientMessage(message, overlay);
			}

			@Override
			public void sendSystemMessage(Component message) {
				seen.add(message);
				super.sendSystemMessage(message);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	static List<String> lines(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	/** A wild Pokémon of {@code props} at {@code rel} (fractional, in the test's frame), still and taken away after. */
	private static PokemonEntity wild(GameTestHelper helper, String props, Vec3 rel) {
		ServerLevel level = helper.getLevel();
		PokemonEntity wild = PokemonProperties.Companion.parse(props, " ", "=").createEntity(level);
		wild.setPos(helper.absoluteVec(rel));
		wild.setNoAi(true);
		helper.assertTrue(level.addFreshEntity(wild), "the wild " + props + " wasn't added");
		PartnerShowsCompatTests.after(helper, wild::discard);
		return wild;
	}

	// ------------------------------------------------------------------ Habitat Keeper: lure spots

	/**
	 * Lure spots go within 32 blocks of the pasture: a Field Marker spot 32 blocks straight out is taken and used, one 33
	 * blocks out is refused with a sentence that says how far it is and the limit, and never added.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa_habitat_range"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_habitat_range")
	public void aSpotThirtyTwoOutIsTakenAndThirtyThreeIsRefused(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen, "qa-marker");
		BlockPos pasture = helper.absolutePos(STATION);
		// The marked block is on the floor (y 1): the snack goes on top of it, level with the pasture.
		ItemStack far = HabitatKeeperCompatTests.marker(helper, STATION.offset(33, -1, 0));
		player.setItemInHand(InteractionHand.MAIN_HAND, far);
		HabitatKeepers.markSpot(player, keeper, far);
		helper.assertTrue(HabitatKeepers.spots(keeper).isEmpty(), "a spot 33 blocks out was taken: " + HabitatKeepers.spots(keeper));
		helper.assertTrue(lines(seen).contains("That spot is 33 blocks from her pasture: lure spots go within 32"), "the refusal reads " + lines(seen));
		seen.clear();
		ItemStack edge = HabitatKeeperCompatTests.marker(helper, STATION.offset(32, -1, 0));
		HabitatKeepers.markSpot(player, keeper, edge);
		BlockPos spot = pasture.offset(32, 0, 0);
		helper.assertTrue(HabitatKeepers.spots(keeper).equals(List.of(spot)), "a spot 32 blocks out wasn't taken: " + HabitatKeepers.spots(keeper)
			+ ", heard " + lines(seen));
		helper.assertTrue(HabitatKeepers.lureSpots(helper.getLevel(), keeper, pasture).contains(spot),
			"the spot 32 blocks out is taken but not used: " + HabitatKeepers.lureSpots(helper.getLevel(), keeper, pasture));
		String name = keeper.getDisplayName().getString();
		helper.assertTrue(lines(seen).contains(name + " keeps a snack set out at " + spot.getX() + " " + spot.getY() + " " + spot.getZ()
			+ " (1 of 3 lure spots)"), "the marked line reads " + lines(seen));
		helper.succeed();
	}

	/** Up to three lure spots: a fourth marked gives up the oldest, and marking a spot she has again doesn't count it twice. */
	//$ gametest_ticks_batch AREA '100' '"qa_habitat_fourth"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_habitat_fourth")
	public void aFourthSpotGivesUpTheOldest(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		ServerPlayer player = listener(helper, new ArrayList<>(), "qa-four");
		List<BlockPos> spots = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			BlockPos marked = new BlockPos(6 + i * 2, 1, 9);
			HabitatKeepers.markSpot(player, keeper, HabitatKeeperCompatTests.marker(helper, marked));
			spots.add(helper.absolutePos(marked.above()));
			if (i == 1) {
				// the same spot again
				HabitatKeepers.markSpot(player, keeper, HabitatKeeperCompatTests.marker(helper, marked));
				helper.assertTrue(HabitatKeepers.spots(keeper).size() == 2, "a spot marked twice counted twice: " + HabitatKeepers.spots(keeper));
			}
		}
		helper.assertTrue(HabitatKeepers.spots(keeper).size() == HabitatKeepers.MAX_SPOTS && !HabitatKeepers.spots(keeper).contains(spots.get(0))
			&& HabitatKeepers.spots(keeper).containsAll(spots.subList(1, 4)), "after four spots she keeps " + HabitatKeepers.spots(keeper));
		helper.succeed();
	}

	/**
	 * With no spot marked she uses grass 16 to 32 blocks out (never nearer, never further, always on grass), and a spot
	 * left over from beyond 32 blocks is never used.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa_habitat_grass"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_habitat_grass")
	public void unmarkedSpotsAreOnGrassSixteenToThirtyTwoOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		BlockPos pasture = helper.absolutePos(STATION);
		// A ring of grass round the pasture, 10 to 36 blocks out (outside the test area too; put back after).
		List<BlockPos> laid = new ArrayList<>();
		List<net.minecraft.world.level.block.state.BlockState> before = new ArrayList<>();
		int floor = pasture.getY() - 1;
		for (int dx = -36; dx <= 36; dx++) {
			for (int dz = -36; dz <= 36; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d < 10 || d > 36) {
					continue;
				}
				BlockPos p = new BlockPos(pasture.getX() + dx, floor, pasture.getZ() + dz);
				if (level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) == floor + 1) {
					laid.add(p);
					before.add(level.getBlockState(p));
					level.setBlock(p, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		PartnerShowsCompatTests.after(helper, () -> {
			for (int i = 0; i < laid.size(); i++) {
				level.setBlock(laid.get(i), before.get(i), Block.UPDATE_CLIENTS);
			}
		});
		HabitatKeepers.addSpot(keeper, pasture.offset(40, 0, 0)); // an old spot, now too far
		List<BlockPos> spots = HabitatKeepers.lureSpots(level, keeper, pasture);
		helper.assertTrue(!spots.contains(pasture.offset(40, 0, 0)), "a spot 40 blocks out is used: " + spots);
		helper.assertTrue(!spots.isEmpty(), "no grass spot found in a ring of grass (" + laid.size() + " blocks laid)");
		for (BlockPos s : spots) {
			double d = Math.sqrt(Math.pow(s.getX() - pasture.getX(), 2) + Math.pow(s.getZ() - pasture.getZ(), 2));
			helper.assertTrue(d >= 15.5 && d <= 32.5, "a grass spot " + String.format("%.1f", d) + " blocks out: " + s);
			helper.assertTrue(level.getBlockState(s.below()).is(Blocks.GRASS_BLOCK), "a spot not on grass: " + s + " over " + level.getBlockState(s.below()));
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ Habitat Keeper: sightings

	/**
	 * A shiny wild Eevee east of her pasture is told once in chat ("<name> spotted a shiny Eevee east of her pasture"); her
	 * memory of it survives a save and reload, so the reloaded keeper doesn't tell it again, minutes later.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_habitat_saved_sighting"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_habitat_saved_sighting")
	public void aSightingIsNotToldAgainAfterASaveAndReload(GameTestHelper helper) {
		helper.getLevel().getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(HabitatKeepers.SIGHT_RANGE + 2)).forEach(e -> e.discard());
		ServerLevel level = helper.getLevel();
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen, "qa-watcher");
		player.moveTo(helper.absoluteVec(new Vec3(4.5, 2, 4.5)));
		wild(helper, "eevee shiny=yes level=5", new Vec3(STATION.getX() + 12.5, 2, STATION.getZ() + 0.5));
		BlockPos pasture = helper.absolutePos(STATION);
		helper.assertTrue(HabitatKeepers.sight(level, keeper, pasture).size() == 1, "the shiny Eevee wasn't sighted");
		String expected = keeper.getDisplayName().getString() + " spotted a shiny Eevee east of her pasture";
		helper.assertTrue(lines(seen).stream().filter(expected::equals).count() == 1, "chat: " + lines(seen) + ", expected once: " + expected);
		CompoundTag tag = new CompoundTag();
		keeper.saveWithoutId(tag);
		Villager reloaded = EntityType.VILLAGER.create(level);
		reloaded.load(tag);
		helper.assertTrue(HabitatKeepers.sightings(reloaded).size() == 1, "her last sightings weren't saved: " + HabitatKeepers.sightings(reloaded));
		seen.clear();
		for (int minute = 0; minute < 3; minute++) {
			helper.assertTrue(HabitatKeepers.sight(level, reloaded, pasture).isEmpty(), "after a reload she told of the same Eevee again");
		}
		helper.assertTrue(seen.stream().noneMatch(c -> c.getString().contains("spotted")), "told again in chat: " + lines(seen));
		helper.succeed();
	}

	/**
	 * Species Cobblemon only spawns as rare are sighted ("a rare Bulbasaur"); a plain common one is not; a shiny beyond
	 * 48 blocks is not, one just within is.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_habitat_rare"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_habitat_rare")
	public void rareIsToldCommonAndTooFarAreNot(GameTestHelper helper) {
		helper.getLevel().getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(HabitatKeepers.SIGHT_RANGE + 4)).forEach(e -> e.discard());
		ServerLevel level = helper.getLevel();
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		BlockPos pasture = helper.absolutePos(STATION);
		wild(helper, "pidgey shiny=no level=5", new Vec3(STATION.getX() + 6.5, 2, STATION.getZ() + 0.5));
		wild(helper, "bulbasaur shiny=no level=5", new Vec3(STATION.getX() + 0.5, 2, STATION.getZ() + 6.5));
		List<HabitatKeepers.Sighting> found = HabitatKeepers.sight(level, keeper, pasture);
		helper.assertTrue(found.size() == 1 && found.get(0).kind().equals("rare") && found.get(0).what().getString().equals("a rare Bulbasaur"),
			"sighted " + found.stream().map(s -> s.kind() + ": " + s.what().getString()).toList() + ", not just a rare Bulbasaur");
		// The distance edge (B474): Pokémon spawned 47-49 blocks outside the test area can land in a chunk that isn't
		// entity-loaded, so no entity query sees them (the 47 one went unseen once; the 49 check was vacuous). So the
		// shinies stand inside the loaded test area and she looks round a pasture 49 and 47 blocks west of them
		// (sight() measures from the pasture it is given).
		BlockPos far = helper.absolutePos(new BlockPos(14 - HabitatKeepers.SIGHT_RANGE - 1, 2, 2));
		PokemonEntity at49 = wild(helper, "eevee shiny=yes level=5", new Vec3(14.5, 2, 2.5));
		helper.assertTrue(at49.blockPosition().distSqr(far) == 49 * 49, "the far shiny isn't 49 blocks out: " + Math.sqrt(at49.blockPosition().distSqr(far)));
		helper.assertTrue(level.getEntitiesOfClass(PokemonEntity.class, new AABB(far).inflate(HabitatKeepers.SIGHT_RANGE + 2)).contains(at49),
			"the shiny 49 blocks out isn't in the level's entity lists, so not seeing it would prove nothing");
		List<HabitatKeepers.Sighting> beyond = HabitatKeepers.sight(level, keeper, far);
		helper.assertTrue(beyond.isEmpty(), "the shiny 49 blocks out was sighted: " + beyond.stream().map(s -> s.kind() + ": " + s.what().getString()).toList());
		PokemonEntity at47 = wild(helper, "eevee shiny=yes level=5", new Vec3(12.5, 2, 2.5));
		helper.assertTrue(at47.blockPosition().distSqr(far) == 47 * 47, "the near shiny isn't 47 blocks out: " + Math.sqrt(at47.blockPosition().distSqr(far)));
		List<HabitatKeepers.Sighting> near = HabitatKeepers.sight(level, keeper, far);
		helper.assertTrue(near.size() == 1 && near.get(0).kind().equals("shiny") && near.get(0).pos().equals(at47.blockPosition()),
			"the shiny 47 blocks out: " + near.stream().map(s -> s.kind() + ": " + s.what().getString() + " at " + s.pos()).toList());
		helper.succeed();
	}

	// ------------------------------------------------------------------ Camp Cook

	static Villager cook(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(STATION, BuiltInRegistries.BLOCK.get(c("campfire")).defaultBlockState());
		((CampfireBlockEntity) helper.getBlockEntity(STATION)).setPotItem(new ItemStack(item("campfire_pot_red")));
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(STATION), ModVillagers.CAMPFIRE_POT_POI, ModVillagers.CAMP_COOK);
		helper.assertTrue(CampCooks.isCook(villager), "not a camp cook");
		return villager;
	}

	/** The makings of three Poké Snacks (as the Camp Cook's own test cooks one). */
	static ItemStack[] snackMakings(int snacks) {
		return new ItemStack[] {new ItemStack(item("poke_snack"), snacks), new ItemStack(item("moomoo_milk"), 9), new ItemStack(Items.HONEY_BOTTLE, 6),
			new ItemStack(item("vivichoke"), 3), new ItemStack(item("hearty_grains"), 9)};
	}

	/** The Poké Snacks by her pot, in the chest and in her bag. */
	static int snacks(GameTestHelper helper, Villager cook) {
		return ((Container) helper.getBlockEntity(CHEST)).countItem(item("poke_snack")) + ModAttachments.BUILDER_BAG.getOrCreate(cook).stacks()
			.stream().filter(s -> s.is(item("poke_snack"))).mapToInt(ItemStack::getCount).sum();
	}

	/** With 16 Poké Snacks by the pot already, she cooks none, though the makings are there. */
	//$ gametest_ticks_batch AREA '1200' '"qa_cook_keep_full"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_cook_keep_full")
	public void sixteenSnacksAreEnough(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager cook = cook(helper, snackMakings(16));
		int[] most = {16};
		helper.onEachTick(() -> most[0] = Math.max(most[0], snacks(helper, cook)));
		helper.runAfterDelay(1000, () -> {
			helper.assertTrue(most[0] == 16, "with 16 kept she cooked more: " + most[0] + "; she says " + said(helper, cook));
			helper.succeed();
		});
	}

	/**
	 * B465: she keeps up to 16 Poké Snacks: with 15 by the pot she cooks (one pot's worth), and once there are 16 or more
	 * she cooks no more of them, though the makings for more are there. The snack out of the pot carries what it was
	 * cooked with, and only plain snacks were counted, so she cooked 15 up to 18.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qa_cook_keep"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qa_cook_keep")
	public void fifteenSnacksAreToppedUpThenNoMore(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager cook = cook(helper, snackMakings(15));
		List<Integer> history = new ArrayList<>(List.of(15));
		long[] reached = {-1};
		helper.onEachTick(() -> {
			int n = snacks(helper, cook);
			if (n != history.get(history.size() - 1)) {
				history.add(n);
			}
			if (n >= 16 && reached[0] < 0) {
				reached[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reached[0] >= 0, "15 snacks never became 16; she says " + said(helper, cook));
			int first = history.stream().filter(n -> n >= 16).findFirst().orElseThrow();
			helper.assertTrue(history.stream().allMatch(n -> n <= first), "she went on cooking snacks past 16: " + history);
			helper.assertTrue(helper.getTick() - reached[0] >= 800, "waiting to see she stops: " + history);
		});
	}

	/** Switched off (campCooks false), a Camp Cook with all the makings cooks nothing and says she's switched off. */
	//$ gametest_ticks_batch AREA '400' '"qa_cook_off"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cook_off")
	public void switchedOffSheCooksNothing(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager cook = cook(helper, snackMakings(0));
		CampCooks.ENABLED = false;
		PartnerShowsCompatTests.after(helper, () -> CampCooks.ENABLED = true);
		helper.runAfterDelay(300, () -> {
			String line = said(helper, cook);
			CampCooks.ENABLED = true;
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(item("poke_snack")) == 0 && chest.countItem(item("hearty_grains")) == 9 && chest.countItem(item("vivichoke")) == 3,
				"she cooked switched off");
			helper.assertTrue(ModAttachments.DISHES_COOKED.getOrElse(cook, 0) == 0, "a dish was counted switched off");
			helper.assertTrue(line.contains("Camp Cooks are switched off (config campCooks)"), "she says " + line);
			helper.succeed();
		});
	}

	/**
	 * Never taken by a jobless villager (one standing by a pot and a pasture for a while stays jobless), and the wrong item
	 * picks neither job: honey at a Campfire Pot, Hearty Grains at a Pasture Block.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_camp_wrong_item"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_camp_wrong_item")
	public void theWrongItemOrNoItemPicksNoJob(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setDayTime(2000);
		helper.setBlock(STATION, BuiltInRegistries.BLOCK.get(c("campfire")).defaultBlockState());
		((CampfireBlockEntity) helper.getBlockEntity(STATION)).setPotItem(new ItemStack(item("campfire_pot_red")));
		BlockPos pasture = new BlockPos(8, 2, 8);
		Block pastureBlock = BuiltInRegistries.BLOCK.get(c("pasture"));
		helper.setBlock(pasture, pastureBlock.defaultBlockState());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		Villager byPot = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 2));
		byPot.setNoAi(true);
		player.setPos(helper.absoluteVec(new Vec3(3.5, 2, 3.5)));
		Stations.choose(player, byPot, new ItemStack(Items.HONEY_BOTTLE));
		helper.assertTrue(byPot.getVillagerData().getProfession() != ModVillagers.CAMP_COOK && byPot.getVillagerData().getProfession() != ModVillagers.HABITAT_KEEPER,
			"honey by a Campfire Pot made a " + byPot.getVillagerData().getProfession());
		Villager byPasture = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 8));
		byPasture.setNoAi(true);
		player.setPos(helper.absoluteVec(new Vec3(9.5, 2, 9.5)));
		Stations.choose(player, byPasture, new ItemStack(item("hearty_grains")));
		helper.assertTrue(byPasture.getVillagerData().getProfession() == VillagerProfession.NONE,
			"Hearty Grains by a Pasture Block made a " + byPasture.getVillagerData().getProfession());
		Villager idle = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(idle.getVillagerData().getProfession() != ModVillagers.CAMP_COOK && idle.getVillagerData().getProfession() != ModVillagers.HABITAT_KEEPER,
				"a jobless villager took the job by itself: " + idle.getVillagerData().getProfession());
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ Festival Cup purse (28.1a)

	/**
	 * The purse: 100,000 a bout won, 500,000 more for the final; at a City host half again (150,000 and 900,000); nothing
	 * different for the City host's loser's opponent; and a Cup at every festival by default.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePurseIsExactAtAHamletAndACity(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos rel = new BlockPos(0, 1, 0);
		helper.setBlock(rel, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getBlockEntity(rel);
		BlockPos host = helper.absolutePos(rel);
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(p));
		CupData.Entrant me = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), p.getGameProfile().getName(), 0, 0, host);
		CupData.Entrant mira = new CupData.Entrant(CupData.Kind.HOST_TRAINER, UUID.nameUUIDFromBytes("QaMira".getBytes()), "Mira", 3, 0, host);
		CupData.Entrant dara = new CupData.Entrant(CupData.Kind.HOST_TRAINER, UUID.nameUUIDFromBytes("QaDara".getBytes()), "Dara", 3, 0, host);
		CupData.Entrant kai = new CupData.Entrant(CupData.Kind.HOST_TRAINER, UUID.nameUUIDFromBytes("QaKai".getBytes()), "Kai", 3, 0, host);
		CupData.Cup cup = CupData.get(level).cup(host);
		PartnerShowsCompatTests.after(helper, () -> CupData.get(level).forget(host));
		cup.theme = AliveWorkplace.id("grand_cup");
		cup.day = -1;
		cup.closed = true;
		cup.entrants.addAll(List.of(me, mira, dara, kai));
		cup.bracket.addAll(List.of(me, mira, dara, kai));
		cup.results.add(new CupData.Result(1, p.getUUID(), mira.id()));
		hall.setRank(VillageRanks.Rank.VILLAGE);
		helper.assertTrue(CupMatches.purse(level, host, cup, p.getUUID()) == 100_000, "a bout at a Village: " + CupMatches.purse(level, host, cup, p.getUUID()));
		hall.setRank(VillageRanks.Rank.CITY);
		helper.assertTrue(CupMatches.purse(level, host, cup, p.getUUID()) == 150_000, "a bout at a City: " + CupMatches.purse(level, host, cup, p.getUUID()));
		cup.results.add(new CupData.Result(1, dara.id(), kai.id()));
		cup.results.add(new CupData.Result(2, p.getUUID(), dara.id()));
		helper.assertTrue(CupMatches.purse(level, host, cup, p.getUUID()) == 900_000, "the final at a City: " + CupMatches.purse(level, host, cup, p.getUUID()));
		hall.setRank(VillageRanks.Rank.TOWN);
		helper.assertTrue(CupMatches.purse(level, host, cup, p.getUUID()) == 600_000, "the final at a Town: " + CupMatches.purse(level, host, cup, p.getUUID()));
		// A Cup at every festival: the config's default, and the next Cup day is the next festival day, whatever the day.
		helper.assertTrue(new WorkplaceConfig().cupEveryFestivals == 1 && Cups.EVERY == 1, "a Cup isn't every festival by default");
		for (long from = 0; from < 30; from++) {
			long festival = from + Math.floorMod(3 - from, 7);
			helper.assertTrue(Cups.cupDay(from, 3, 7, Cups.EVERY) == festival, "from day " + from + " the next Cup is " + Cups.cupDay(from, 3, 7, Cups.EVERY)
				+ ", the next festival " + festival);
		}
		helper.succeed();
	}
}
