package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.bounty.Rogues;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonRogues;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.work.Bank;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;

/**
 * QA from the player guide's Rogue Pokémon page (docs/guide/rogue-pokemon.md, ROADMAP 31.14) with the real Cobblemon,
 * lane-d-1104-2209: what the page promises that no test checked yet. Every group of the table by its biomes; 20, 24 and
 * 28 emeralds and +40 standing; the chronicle's "caught ... and was paid" line; Village rank or bigger, 150 to 250 blocks
 * out; the rogue appears within about 96 blocks of its spot, not farther; it glows within 32 blocks, not 33; unclaimed
 * after 7 days it wanders off in a puff (not on day 6); with Rogue Bounties off an open rogue bounty still pays.
 */
public class QaRoguePokemon1104d4CompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos SPOT = new BlockPos(18, 2, 18);

	private static BlockPos village(GameTestHelper helper, VillageRanks.Rank rank) {
		ServerLevel level = helper.getLevel();
		level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(48)).forEach(Entity::discard);
		int radius = VillageHalls.RADIUS;
		boolean enabled = Bounties.ENABLED;
		boolean rogues = Rogues.ENABLED;
		boolean auto = Bounties.AUTO;
		VillageHalls.RADIUS = 10;
		Bounties.ENABLED = true;
		Rogues.ENABLED = true;
		Bounties.forget(level);
		Rogues.forget();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		hall.setLastQuestDay(Long.MAX_VALUE / 2);
		hall.setRank(rank);
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Bounties.ENABLED = enabled;
			Rogues.ENABLED = rogues;
			Bounties.AUTO = auto;
			Bounties.forget(level);
			Rogues.forget();
			level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(48)).forEach(Entity::discard);
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
		});
		return helper.absolutePos(HALL);
	}

	/** A player in survival whose chat lines are kept in {@code seen}; removed after the test. */
	private static ServerPlayer listener(GameTestHelper helper, String name, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message);
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(level, helper.absoluteVec(net.minecraft.world.phys.Vec3.atCenterOf(HALL)).x, helper.absolutePos(HALL).getY(),
			helper.absoluteVec(net.minecraft.world.phys.Vec3.atCenterOf(HALL)).z, 0, 0);
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	private static PokemonEntity spawn(GameTestHelper helper, Bounties.Bounty b) {
		Entity e = Rogues.spawn(helper.getLevel(), b, null);
		helper.assertTrue(e instanceof PokemonEntity, "no rogue: " + e);
		PokemonEntity rogue = (PokemonEntity) e;
		rogue.setNoAi(true);
		return rogue;
	}

	private static long money(ServerPlayer player) {
		long dollars = Bank.EXTENSION.call(bank -> bank.balance(player), 0L);
		return dollars + (long) player.getInventory().countItem(Items.EMERALD) * Money.DOLLARS_PER_EMERALD;
	}

	/** Every other player near the test is set to spectator (they don't count as "someone near"); put back after. */
	private static void quietOthers(GameTestHelper helper, List<ServerPlayer> mine) {
		ServerLevel level = helper.getLevel();
		Map<ServerPlayer, GameType> was = new HashMap<>();
		for (ServerPlayer p : new ArrayList<>(level.players())) {
			if (!mine.contains(p) && !p.isSpectator()) {
				was.put(p, p.gameMode.getGameModeForPlayer());
				p.setGameMode(GameType.SPECTATOR);
			}
		}
		PartnerShowsCompatTests.after(helper, () -> was.forEach((p, mode) -> {
			if (!p.isRemoved()) {
				p.setGameMode(mode);
			}
		}));
	}

	/** The guide's table, a biome or two of every row: each lands in the row's group, with its three species in order. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyRowOfTheTableByItsBiomes(GameTestHelper helper) {
		var biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
		List<String> plains = List.of("tauros", "kangaskhan", "rapidash");
		List<String> mountains = List.of("onix", "golem", "aggron");
		List<String> deserts = List.of("sandslash", "krookodile", "flygon");
		List<String> swamps = List.of("tangrowth", "toxicroak", "swampert");
		List<String> beaches = List.of("kingler", "crawdaunt", "barbaracle");
		List<String> snowy = List.of("mamoswine", "abomasnow", "beartic");
		List<String> savannas = List.of("donphan", "rhydon", "lycanroc");
		List<String> forests = List.of("ursaring", "scyther", "pinsir");
		Map<ResourceKey<Biome>, List<String>> table = new java.util.LinkedHashMap<>();
		table.put(Biomes.BIRCH_FOREST, forests);
		table.put(Biomes.DARK_FOREST, forests);
		table.put(Biomes.PLAINS, plains);
		table.put(Biomes.MEADOW, plains);
		table.put(Biomes.JAGGED_PEAKS, mountains);
		table.put(Biomes.STONY_PEAKS, mountains);
		table.put(Biomes.DESERT, deserts);
		table.put(Biomes.BADLANDS, deserts);
		table.put(Biomes.SWAMP, swamps);
		table.put(Biomes.JUNGLE, swamps);
		table.put(Biomes.BEACH, beaches);
		table.put(Biomes.STONY_SHORE, beaches);
		table.put(Biomes.SNOWY_PLAINS, snowy);
		table.put(Biomes.SNOWY_TAIGA, snowy); // snowy lands, though a taiga
		table.put(Biomes.SAVANNA, savannas);
		table.put(Biomes.TAIGA, savannas);
		List<String> wrong = new ArrayList<>();
		table.forEach((biome, want) -> {
			Rogues.Group g = Rogues.group(biomes.getHolderOrThrow(biome));
			List<String> got = g == null ? List.of() : Rogues.species(g).stream().map(ResourceLocation::getPath).toList();
			if (!got.equals(want)) {
				wrong.add(biome.location() + " -> " + got + " (the guide: " + want + ")");
			}
		});
		helper.assertTrue(wrong.isEmpty(), "rogues by biome: " + wrong);
		// The ocean has no row: no rogue is posted there.
		helper.assertTrue(Rogues.group(biomes.getHolderOrThrow(Biomes.DEEP_OCEAN)) == null, "a rogue group in the deep ocean");
		helper.succeed();
	}

	/**
	 * Pays 20 emeralds in a Village, 24 in a Town, 28 in a City, and +40 standing with that village, once; the chronicle
	 * says "<player> caught Rogue Onix and was paid 24 emeralds." and the catcher reads the capture line.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_rogue_pay_1104d4"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_rogue_pay_1104d4")
	public void paysTwentyTwentyFourTwentyEightAndFortyStanding(GameTestHelper helper) {
		BlockPos hall = village(helper, VillageRanks.Rank.VILLAGE);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		VillageRanks.Rank[] ranks = {VillageRanks.Rank.VILLAGE, VillageRanks.Rank.TOWN, VillageRanks.Rank.CITY};
		int[] pay = {20, 24, 28};
		for (int i = 0; i < 3; i++) {
			entity.setRank(ranks[i]);
			Bounties.forget(level);
			Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:onix"), helper.absolutePos(SPOT));
			helper.assertTrue(b.reward == pay[i], ranks[i] + ": the poster pays " + b.reward + ", the guide says " + pay[i]);
			PokemonEntity rogue = spawn(helper, b);
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = listener(helper, "Hunter" + i, seen);
			Bounties.take(player, level, b);
			long before = money(player);
			int standing = Reputation.points(level, hall, player.getUUID());
			Pokemon pokemon = rogue.getPokemon();
			boolean caught = i == 1;
			if (caught) {
				rogue.discard();
				com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon);
			}
			helper.assertTrue(CobblemonRogues.beaten(level.getServer(), pokemon, player, caught), ranks[i] + ": the claim didn't pay");
			helper.assertTrue(money(player) - before == (long) pay[i] * Money.DOLLARS_PER_EMERALD, ranks[i] + ": paid " + (money(player) - before)
				+ ", not " + pay[i] + " emeralds' worth");
			int gained = Reputation.points(level, hall, player.getUUID()) - standing;
			helper.assertTrue(gained == 40, ranks[i] + ": standing +" + gained + ", the guide says +40");
			helper.assertTrue(!CobblemonRogues.beaten(level.getServer(), pokemon, player, caught), ranks[i] + ": paid twice");
			helper.assertTrue(Reputation.points(level, hall, player.getUUID()) - standing == 40, ranks[i] + ": standing twice");
			if (caught) {
				String line = "Hunter1 caught Rogue Onix and was paid 24 emeralds.";
				helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.BOUNTY && e.text().getString().equals(line)),
					"the chronicle doesn't say '" + line + "': " + entity.chronicle().stream().map(e -> e.text().getString()).toList());
				String told = "You caught Rogue Onix! It's yours to keep, and so is the bounty: 24 emeralds.";
				helper.assertTrue(seen.stream().anyMatch(c -> c.getString().equals(told)), "the catcher read " + seen.stream().map(Component::getString).toList());
			}
		}
		helper.succeed();
	}

	/**
	 * Posted by a village of Village rank or bigger: a Hamlet posts no rogue; a Village does, on a spot 150 to 250 blocks
	 * from the hall (across the ground).
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_rogue_ring_1104d4"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_rogue_ring_1104d4")
	public void aHamletPostsNoneAndAVillagesRogueIs150To250Out(GameTestHelper helper) {
		BlockPos hall = village(helper, VillageRanks.Rank.HAMLET);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		for (int seed = 0; seed < 4; seed++) {
			Bounties.forget(level);
			helper.assertTrue(Rogues.post(level, hall, RandomSource.create(seed)) == null, "a Hamlet posted a rogue (seed " + seed + ")");
		}
		helper.assertTrue(!Bounties.mayPost(level, hall), "a Hamlet may post a bounty");
		entity.setRank(VillageRanks.Rank.VILLAGE);
		int posted = 0;
		for (int seed = 0; seed < 6; seed++) {
			Bounties.forget(level);
			Bounties.Bounty b = Rogues.post(level, hall, RandomSource.create(seed));
			if (b == null) {
				continue;
			}
			posted++;
			double dx = b.hideout.spot.getX() - hall.getX();
			double dz = b.hideout.spot.getZ() - hall.getZ();
			double d = Math.sqrt(dx * dx + dz * dz);
			helper.assertTrue(d >= 150 && d <= 250, "seed " + seed + ": the rogue roams " + d + " blocks from the hall, not 150 to 250");
			helper.assertTrue(b.rogue != null && b.rogueLevel == 35, "seed " + seed + ": " + b.rogue + " level " + b.rogueLevel);
		}
		helper.assertTrue(posted > 0, "a Village never posted a rogue in 6 tries");
		helper.succeed();
	}

	/**
	 * The rogue appears when someone first comes within about 96 blocks of its spot: a player 95 blocks off brings it
	 * (once, not twice); gone again, a player 97 blocks off doesn't bring it back.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa_rogue_appears_1104d4"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_rogue_appears_1104d4")
	public void theRogueAppearsWithin96BlocksNotFarther(GameTestHelper helper) {
		BlockPos hall = village(helper, VillageRanks.Rank.VILLAGE);
		ServerLevel level = helper.getLevel();
		BlockPos spot = helper.absolutePos(SPOT);
		Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:tauros"), spot);
		helper.assertTrue(Rogues.rogue(level, b) == null, "the rogue is there before anyone came");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		quietOthers(helper, List.of(player));
		player.setPos(spot.getX() + 0.5 + 97, spot.getY(), spot.getZ() + 0.5);
		Bounties.check(level);
		helper.assertTrue(Rogues.rogue(level, b) == null, "the rogue appeared for a player 97 blocks off");
		player.setPos(spot.getX() + 0.5 + 95, spot.getY(), spot.getZ() + 0.5);
		helper.succeedWhen(() -> {
			Bounties.check(level);
			Entity rogue = Rogues.rogue(level, b);
			helper.assertTrue(rogue != null, "no rogue for a player 95 blocks off");
			Bounties.check(level);
			long rogues = level.getEntitiesOfClass(PokemonEntity.class, new net.minecraft.world.phys.AABB(spot).inflate(40), p -> p.getTags().contains(Rogues.TAG)).size();
			helper.assertTrue(rogues == 1, rogues + " rogues round the spot");
			// Gone (it wandered out of the loaded ground); a player 97 blocks off doesn't bring it back.
			rogue.discard();
			player.setPos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5 + 97);
			Bounties.check(level);
			helper.assertTrue(Rogues.rogue(level, b) == null, "the rogue came back for a player 97 blocks off");
		});
	}

	/** It glows for the players who took its bounty within 32 blocks (31.5 off: yes), not farther (32.6 off: no). */
	//$ gametest_ticks_batch AREA '200' '"qa_rogue_glow_1104d4"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_rogue_glow_1104d4")
	public void itGlowsWithin32BlocksNotFarther(GameTestHelper helper) {
		BlockPos hall = village(helper, VillageRanks.Rank.VILLAGE);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:pinsir"), helper.absolutePos(SPOT));
		PokemonEntity rogue = spawn(helper, b);
		ServerPlayer taker = helper.makeMockServerPlayerInLevel();
		taker.setGameMode(GameType.SURVIVAL);
		Bounties.take(taker, level, b);
		taker.setPos(rogue.getX() + 31.5, rogue.getY(), rogue.getZ());
		Rogues.glow(level);
		helper.assertTrue(Rogues.glowingFor(b).contains(taker.getUUID()), "no glow 31.5 blocks off");
		taker.setPos(rogue.getX() + 32.6, rogue.getY(), rogue.getZ());
		Rogues.glow(level);
		helper.assertTrue(!Rogues.glowingFor(b).contains(taker.getUUID()), "still glows 32.6 blocks off");
		taker.setPos(rogue.getX(), rogue.getY(), rogue.getZ() - 31.5);
		Rogues.glow(level);
		helper.assertTrue(Rogues.glowingFor(b).contains(taker.getUUID()), "no glow 31.5 blocks off on the other axis");
		helper.succeed();
	}

	/**
	 * Unclaimed after 7 days, it wanders off in a puff: on day 6 the bounty is up and the rogue there; on day 7 the
	 * bounty is down, the rogue gone and the players near read that it wandered off.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_rogue_lapse_1104d4"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_rogue_lapse_1104d4")
	public void unclaimedAfterSevenDaysItWandersOff(GameTestHelper helper) {
		BlockPos hall = village(helper, VillageRanks.Rank.VILLAGE);
		ServerLevel level = helper.getLevel();
		Bounties.AUTO = false; // no new poster in the round
		Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:kangaskhan"), helper.absolutePos(SPOT));
		PokemonEntity rogue = spawn(helper, b);
		List<Component> seen = new ArrayList<>();
		listener(helper, "Watcher", seen);
		long time = level.getDayTime();
		PartnerShowsCompatTests.after(helper, () -> level.setDayTime(time));
		helper.assertTrue(b.posted == Chronicle.day(level), "posted on day " + b.posted + ", today is " + Chronicle.day(level));
		level.setDayTime(time + 6 * 24000L); // 6 days on
		Bounties.round(level, hall);
		helper.assertTrue(Bounties.open(level, hall).stream().anyMatch(o -> o.id.equals(b.id)), "the bounty came down after 6 days");
		helper.assertTrue(rogue.isAlive() && !rogue.isRemoved(), "the rogue left after 6 days");
		level.setDayTime(time + 7 * 24000L); // 7 days on
		Bounties.round(level, hall);
		helper.assertTrue(Bounties.open(level, hall).stream().noneMatch(o -> o.id.equals(b.id)), "the bounty is still up after 7 days");
		helper.assertTrue(rogue.isRemoved() || !rogue.isAlive(), "the rogue is still there after 7 days");
		String line = "The bounty on Rogue Kangaskhan came down; it has wandered off.";
		helper.assertTrue(seen.stream().anyMatch(c -> c.getString().equals(line)), "a player near read " + seen.stream().map(Component::getString).toList());
		helper.succeed();
	}

	/** With Rogue Bounties off in the settings, no rogue is posted, but an open rogue bounty still pays its winner. */
	//$ gametest_ticks_batch AREA '200' '"qa_rogue_off_pays_1104d4"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_rogue_off_pays_1104d4")
	public void switchedOffAnOpenRogueBountyStillPays(GameTestHelper helper) {
		BlockPos hall = village(helper, VillageRanks.Rank.TOWN);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:golem"), helper.absolutePos(SPOT));
		PokemonEntity rogue = spawn(helper, b);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Bounties.take(player, level, b);
		Rogues.ENABLED = false;
		helper.assertTrue(Rogues.post(level, hall, RandomSource.create(3)) == null, "a rogue posted with Rogue Bounties off");
		helper.assertTrue(Bounties.open(level, hall).stream().anyMatch(o -> o.id.equals(b.id)), "switching off took the open rogue bounty down");
		long before = money(player);
		helper.assertTrue(CobblemonRogues.beaten(level.getServer(), rogue.getPokemon(), player, false), "the open rogue bounty didn't pay with the switch off");
		helper.assertTrue(money(player) - before == 24L * Money.DOLLARS_PER_EMERALD, "paid " + (money(player) - before));
		helper.succeed();
	}
}
