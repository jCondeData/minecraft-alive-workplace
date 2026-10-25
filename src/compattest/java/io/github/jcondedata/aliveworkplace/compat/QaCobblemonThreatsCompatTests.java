package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.bounty.Rogues;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonRogues;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.MegaBracelets;
import io.github.jcondedata.aliveworkplace.trainer.MegaShrines;
import io.github.jcondedata.aliveworkplace.work.Bank;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-d-1025-0108), written from ROADMAP 31.14 and 38.7 rather than the code, with the real Cobblemon and Mega
 * Showdown: the rogue bounty pays one player once whoever comes second, a rogue killed outside a battle pays nobody and
 * leaves the bounty up, the bounty survives a save, the config switch posts no rogue, the poster and messages read
 * right; the Mega Shrine holds no show unless finished, and its show gives exactly +3 mood, once.
 */
public class QaCobblemonThreatsCompatTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_compat:huge_area";
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos SPOT = new BlockPos(18, 2, 18);
	/** The shrine's origin in the test area: its pedestal at (5, 4, 8), its trainer's spot at (5, 4, 7). */
	private static final BlockPos ORIGIN = new BlockPos(2, 1, 5);
	private static final BlockPos SHRINE_HALL = new BlockPos(1, 2, 1);

	// ---- Rogue bounties (31.14) -----------------------------------------------------------------------------------

	private static BlockPos village(GameTestHelper helper, VillageRanks.Rank rank) {
		ServerLevel level = helper.getLevel();
		level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(48)).forEach(Entity::discard);
		int radius = VillageHalls.RADIUS;
		boolean enabled = Bounties.ENABLED;
		boolean rogues = Rogues.ENABLED;
		VillageHalls.RADIUS = 10;
		Bounties.ENABLED = true;
		Rogues.ENABLED = true;
		Bounties.forget(level);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		hall.setLastQuestDay(Long.MAX_VALUE / 2);
		hall.setRank(rank);
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Bounties.ENABLED = enabled;
			Rogues.ENABLED = rogues;
			Bounties.forget(level);
			level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(48)).forEach(Entity::discard);
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
		});
		return helper.absolutePos(HALL);
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
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

	/**
	 * One rogue, one payout: a player who wins pays; a second player who then catches the same Pokémon (or beats it
	 * again) gets nothing. A plain wild Pokémon of the same species, carrying no bounty, pays nobody.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa_rogue_two_players"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa_rogue_two_players")
	public void theBountyPaysOnlyTheFirstOfTwoPlayers(GameTestHelper helper) {
		BlockPos hall = village(helper, VillageRanks.Rank.VILLAGE);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:kangaskhan"), helper.absolutePos(SPOT));
		PokemonEntity rogue = spawn(helper, b);
		ServerPlayer first = player(helper);
		ServerPlayer second = player(helper);
		Bounties.take(first, level, b);
		Bounties.take(second, level, b);
		long firstBefore = money(first);
		long secondBefore = money(second);
		Pokemon pokemon = rogue.getPokemon();
		helper.assertTrue(CobblemonRogues.beaten(level.getServer(), pokemon, first, false), "the first player's win didn't pay");
		helper.assertTrue(!CobblemonRogues.beaten(level.getServer(), pokemon, second, true), "a second player's capture paid the same bounty again");
		helper.assertTrue(!CobblemonRogues.beaten(level.getServer(), pokemon, second, false), "a second player's win paid the same bounty again");
		long want = (long) b.reward * Money.DOLLARS_PER_EMERALD;
		helper.assertTrue(b.reward == Math.round(Rogues.REWARD * VillageRanks.questRewardFactor(VillageRanks.Rank.VILLAGE)), "16 emeralds times the Village factor: " + b.reward);
		helper.assertTrue(money(first) - firstBefore == want, "the winner got " + (money(first) - firstBefore) + ", not " + want);
		helper.assertTrue(money(second) == secondBefore, "the second player got " + (money(second) - secondBefore));
		// A plain wild Kangaskhan, no bounty on it: nothing.
		PokemonEntity wild = com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse("kangaskhan", " ", "=").createEntity(level);
		helper.assertTrue(CobblemonRogues.bountyOf(wild.getPokemon()) == null, "a plain wild Pokémon carries a bounty");
		helper.assertTrue(!CobblemonRogues.beaten(level.getServer(), wild.getPokemon(), second, false), "a plain wild Pokémon paid a bounty");
		helper.assertTrue(money(second) == secondBefore, "a plain wild Pokémon paid " + (money(second) - secondBefore));
		wild.discard();
		helper.succeed();
	}

	/**
	 * A rogue killed outside a battle (a sword) pays nobody, and the bounty stays up: the rogue comes back and a later
	 * win still pays, once.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa_rogue_killed"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa_rogue_killed")
	public void aRogueKilledOutsideABattlePaysNobodyAndStaysPosted(GameTestHelper helper) {
		BlockPos hall = village(helper, VillageRanks.Rank.VILLAGE);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:rapidash"), helper.absolutePos(SPOT));
		PokemonEntity rogue = spawn(helper, b);
		ServerPlayer player = player(helper);
		Bounties.take(player, level, b);
		long before = money(player);
		rogue.hurt(level.damageSources().playerAttack(player), Float.MAX_VALUE);
		if (rogue.isAlive()) {
			rogue.kill();
		}
		helper.assertTrue(!rogue.isAlive(), "the rogue couldn't be killed outside a battle");
		helper.assertTrue(money(player) == before, "a kill outside a battle paid " + (money(player) - before));
		helper.assertTrue(!Bounties.Data.get(level).claimed.contains(b.id), "a kill outside a battle claimed the bounty");
		helper.assertTrue(Bounties.open(level, hall).stream().anyMatch(o -> o.id.equals(b.id)), "the bounty came down when its rogue was killed by a sword");
		// It comes back, and beating that one pays.
		PokemonEntity again = spawn(helper, b);
		helper.assertTrue(b.id.equals(CobblemonRogues.bountyOf(again.getPokemon())), "the rogue came back without its bounty");
		helper.assertTrue(CobblemonRogues.beaten(level.getServer(), again.getPokemon(), player, false), "the win after the kill didn't pay");
		helper.assertTrue(money(player) - before == (long) b.reward * Money.DOLLARS_PER_EMERALD, "paid " + (money(player) - before));
		helper.succeed();
	}

	/**
	 * Mid-bounty save and reload: the bounty comes back with its species, level, reward, rogue, spot and takers, and is
	 * still open; the rogue is kept (Cobblemon never despawns it).
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa_rogue_save"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa_rogue_save")
	public void aRogueBountySurvivesASave(GameTestHelper helper) {
		BlockPos hall = village(helper, VillageRanks.Rank.CITY);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:aggron"), helper.absolutePos(SPOT));
		PokemonEntity rogue = spawn(helper, b);
		ServerPlayer player = player(helper);
		Bounties.take(player, level, b);
		Bounties.Data data = Bounties.Data.get(level);
		CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
		Bounties.Data loaded = Bounties.Data.load(saved, level.registryAccess());
		Bounties.Bounty back = loaded.bounties.get(b.id);
		helper.assertTrue(back != null, "the rogue bounty was lost in a save");
		helper.assertTrue(ResourceLocation.parse("cobblemon:aggron").equals(back.rogue), "species after a save: " + back.rogue);
		helper.assertTrue(back.rogueLevel == 65, "a City rogue's level after a save: " + back.rogueLevel);
		helper.assertTrue(back.reward == Math.round(Rogues.REWARD * VillageRanks.questRewardFactor(VillageRanks.Rank.CITY)), "reward after a save: " + back.reward);
		helper.assertTrue(rogue.getUUID().equals(back.captain), "the rogue after a save: " + back.captain);
		helper.assertTrue(back.hideout.spot.equals(helper.absolutePos(SPOT)), "where it was last seen after a save: " + back.hideout.spot);
		helper.assertTrue(back.takers.contains(player.getUUID()), "its takers after a save: " + back.takers);
		helper.assertTrue(!back.fallen && !loaded.claimed.contains(b.id), "the bounty isn't open after a save");
		helper.assertTrue(back.name(level).getString().equals("Rogue Aggron"), "its name after a save: " + back.name(level).getString());
		helper.assertTrue(rogue.isPersistenceRequired(), "the rogue may despawn like any wild Pokémon");
		helper.succeed();
	}

	/** {@code rogueBounties} off: the board never posts a rogue (it posts its other kind). */
	//$ gametest_ticks_batch HUGE '200' '"qa_rogue_off"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa_rogue_off")
	public void theConfigSwitchOffPostsNoRogue(GameTestHelper helper) {
		BlockPos hall = village(helper, VillageRanks.Rank.VILLAGE);
		ServerLevel level = helper.getLevel();
		Rogues.ENABLED = false;
		helper.assertTrue(!Rogues.available(), "rogues available with rogueBounties off");
		for (int seed = 0; seed < 6; seed++) {
			Bounties.forget(level);
			helper.assertTrue(Rogues.post(level, hall, RandomSource.create(seed)) == null, "a rogue posted with rogueBounties off (seed " + seed + ")");
			Bounties.Bounty any = Bounties.post(level, hall, RandomSource.create(seed));
			helper.assertTrue(any == null || any.rogue == null, "the board posted a rogue with rogueBounties off: " + (any == null ? null : any.rogue));
		}
		helper.succeed();
	}

	/**
	 * Every sentence a player reads: the poster names "Rogue Tauros", its level and how to win it, where it was last
	 * seen and the reward; the posted, win and capture lines exist and read with the names and amounts filled in.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa_rogue_text"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa_rogue_text")
	public void thePosterAndItsLinesReadRight(GameTestHelper helper) {
		BlockPos hall = village(helper, VillageRanks.Rank.VILLAGE);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:tauros"), helper.absolutePos(SPOT));
		List<String> lines = Bounties.lines(level, b).stream().map(Component::getString).toList();
		helper.assertTrue(lines.size() == 4, "the poster's lines: " + lines);
		helper.assertTrue(lines.get(0).equals("Wanted: Rogue Tauros"), "the poster's name: " + lines.get(0));
		helper.assertTrue(lines.get(1).equals("A huge, mean wild Pokémon, level 35. Beat it in battle or catch it"), "the poster's quirk: " + lines.get(1));
		helper.assertTrue(lines.get(2).startsWith("Last seen ") && lines.get(2).length() > "Last seen ".length() && !lines.get(2).contains("%"),
			"where it was last seen: " + lines.get(2));
		helper.assertTrue(b.reward == Math.round(Rogues.REWARD * VillageRanks.questRewardFactor(VillageRanks.Rank.VILLAGE)), "the reward: " + b.reward);
		helper.assertTrue(lines.get(3).equals("Reward: " + b.reward + " emeralds"), "the poster's reward: " + lines.get(3));
		String hover = Bounties.poster(level, b).getHoverName().getString();
		helper.assertTrue(hover.contains("Rogue Tauros"), "the poster item's name: " + hover);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.BOUNTY && e.text().getString().startsWith("Wanted: Rogue Tauros (")),
			"no chronicle line for the posting");
		Language lang = Language.getInstance();
		for (String key : List.of("bounty.aliveworkplace.rogue_name", "bounty.aliveworkplace.quirk.rogue", "message.aliveworkplace.bounty.posted",
			"message.aliveworkplace.bounty.rogue_beaten", "message.aliveworkplace.bounty.rogue_caught", "message.aliveworkplace.bounty.rogue_expired",
			"chronicle.aliveworkplace.bounty.rogue_beaten", "chronicle.aliveworkplace.bounty.rogue_caught", "tooltip.aliveworkplace.wanted_poster.seen")) {
			helper.assertTrue(lang.has(key), "no English for " + key);
		}
		String caught = Component.translatable("message.aliveworkplace.bounty.rogue_caught", b.name(level), b.reward).getString();
		helper.assertTrue(caught.equals("You caught Rogue Tauros! It's yours to keep, and so is the bounty: " + b.reward + " emeralds."), "the capture line: " + caught);
		String beaten = Component.translatable("message.aliveworkplace.bounty.rogue_beaten", b.name(level), b.reward).getString();
		helper.assertTrue(beaten.equals("You beat Rogue Tauros! The bounty is yours: " + b.reward + " emeralds."), "the win line: " + beaten);
		ServerPlayer player = player(helper);
		PokemonEntity rogue = spawn(helper, b);
		CobblemonRogues.beaten(level.getServer(), rogue.getPokemon(), player, true);
		helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.BOUNTY && e.text().getString().contains("caught Rogue Tauros and was paid " + b.reward + " emeralds")),
			"no chronicle line for the capture");
		helper.succeed();
	}

	// ---- The Mega Shrine (38.7) -----------------------------------------------------------------------------------

	private static BlueprintData.Placement placement(GameTestHelper helper) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
	}

	/** The festival's hours today, the village radius wide enough; with {@code finished} a finished shrine is remembered. */
	private static void festival(GameTestHelper helper, boolean finished) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = placement(helper);
		if (finished) {
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.MEGA_SHRINE.id(), placement, UUID.randomUUID());
			// The shrine's pedestal stands, as a builder leaves it (B421: without it there is no show).
			level.setBlockAndUpdate(io.github.jcondedata.aliveworkplace.hall.Arenas.world(placement, MegaShrines.PEDESTAL_SPOT),
				net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(MegaShrines.PEDESTAL).defaultBlockState());
		}
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		long time = level.getDayTime();
		MegaShrines.forget();
		level.setDayTime((Chronicle.day(level) - 1) * 24000L + Festivals.START + 100);
		PartnerShowsCompatTests.after(helper, () -> {
			BuildSiteManager.get(level).forgetFinished(placement);
			VillageHalls.RADIUS = radius;
			MegaBracelets.ENABLED = true;
			Festivals.forget();
			MegaShrines.forget();
			level.setDayTime(time);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(4))) {
				v.discard();
			}
		});
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, boolean trainer) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		if (trainer) {
			v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(3));
		}
		v.setNoAi(true);
		return v;
	}

	/** No finished shrine (only planned, or not yet built): the festival's crowd isn't taken there and nobody gets the show's mood. */
	//$ gametest_batch AREA '"qa_shrine_unfinished"'
	@GameTest(template = AREA, batch = "qa_shrine_unfinished")
	public void anUnfinishedShrineHoldsNoShow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		festival(helper, false);
		BlockPos hall = helper.absolutePos(SHRINE_HALL);
		villager(helper, new BlockPos(5, 4, 7), true);
		Villager watcher = villager(helper, new BlockPos(5, 2, 2), false);
		helper.assertTrue(MegaShrines.pedestal(level, hall).isEmpty(), "a pedestal without a finished shrine: " + MegaShrines.pedestal(level, hall));
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(!MegaShrines.tick(level, hall), "the festival's crowd taken to a shrine that isn't finished");
		}
		helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(watcher) == null && MegaShrines.mood(level, watcher) == 0, "a show at a shrine that isn't finished");
		// Some other finished building there is no shrine either.
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.MARKET_STALL.id(), placement(helper), UUID.randomUUID());
		helper.assertTrue(!MegaShrines.tick(level, hall) && ModAttachments.MEGA_SHOW_DAY.get(watcher) == null, "another finished building held the shrine's show");
		helper.succeed();
	}

	/**
	 * On an ordinary day (no festival planned), through the real festival tick: a finished shrine, its trainer at the
	 * pedestal and villagers round it, the afternoon passes with no show and no mood.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_shrine_ordinary_day"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_shrine_ordinary_day")
	public void noShowOnADayWithoutAFestival(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean festivals = Festivals.ENABLED;
		Festivals.ENABLED = true;
		festival(helper, true);
		PartnerShowsCompatTests.after(helper, () -> Festivals.ENABLED = festivals);
		helper.setBlock(SHRINE_HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(SHRINE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity.festivalDay() != Chronicle.day(level) && !Festivals.isOn(level, hall), "a festival is on today");
		villager(helper, new BlockPos(5, 4, 7), true);
		List<Villager> watchers = List.of(villager(helper, new BlockPos(5, 2, 2), false), villager(helper, new BlockPos(4, 2, 2), false),
			villager(helper, new BlockPos(6, 2, 2), false));
		helper.runAfterDelay(150, () -> {
			helper.assertTrue(!Festivals.isOn(level, hall), "a festival began on an ordinary day");
			for (Villager v : watchers) {
				helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(v) == null && MegaShrines.mood(level, v) == 0, "a show on a day without a festival");
			}
			helper.succeed();
		});
	}

	/**
	 * The show's mood is exactly +3 on the villager's mood score, and only once: a second tick that day, or a second
	 * show watched, doesn't make it +6 (the festival's own mood for gathering counted apart).
	 */
	//$ gametest_batch AREA '"qa_shrine_mood"'
	@GameTest(template = AREA, batch = "qa_shrine_mood")
	public void theShowGivesExactlyThreeMoodOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		festival(helper, true);
		BlockPos hall = helper.absolutePos(SHRINE_HALL);
		Villager star = villager(helper, new BlockPos(5, 4, 7), true);
		Villager watcher = villager(helper, new BlockPos(5, 2, 2), false);
		int before = Moods.work(level, watcher).score();
		helper.assertTrue(MegaShrines.mood(level, watcher) == 0, "mood from a show before the show");
		MegaShrines.tick(level, hall);
		helper.assertTrue(Long.valueOf(Chronicle.day(level)).equals(ModAttachments.MEGA_SHOW_DAY.get(watcher)), "the watcher didn't see the show");
		int festival = Festivals.enjoyedLately(level, watcher) ? Festivals.mood(level, watcher) : 0; // gathering is the festival's own mood
		int after = Moods.work(level, watcher).score() - festival;
		helper.assertTrue(after - before == 3, "the show's mood: " + (after - before) + " (" + before + " -> " + after + ")");
		helper.assertTrue(Moods.work(level, watcher).good().stream().anyMatch(c -> c.getString().equals("watched a Mega Evolution at the shrine")),
			"the mood's reason: " + Moods.work(level, watcher).good().stream().map(Component::getString).toList());
		MegaShrines.tick(level, hall);
		MegaShrines.tick(level, hall);
		MegaShrines.show(level, hall, MegaShrines.pedestal(level, hall).orElseThrow(), star);
		int again = Moods.work(level, watcher).score() - festival;
		helper.assertTrue(again - before == 3, "a second show the same day made the mood +" + (again - before));
		helper.succeed();
	}

	/**
	 * The players near read one line, with the trainer's name in it, and the shrine's name reads right at the Table.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theShrinesSentencesReadRight(GameTestHelper helper) {
		Language lang = Language.getInstance();
		for (String key : List.of("message.aliveworkplace.mega_shrine.show", "mood.aliveworkplace.reason.mega_show", "blueprint.aliveworkplace.mega_shrine")) {
			helper.assertTrue(lang.has(key), "no English for " + key);
		}
		String line = Component.translatable("message.aliveworkplace.mega_shrine.show", Component.literal("Ash")).getString();
		helper.assertTrue(line.equals("Ash Mega Evolves their ace at the Mega Shrine, and the village cheers!"), "the show's line: " + line);
		helper.assertTrue(lang.getOrDefault("blueprint.aliveworkplace.mega_shrine").equals("Mega Shrine"), "the shrine's name at the Table");
		helper.succeed();
	}
}
