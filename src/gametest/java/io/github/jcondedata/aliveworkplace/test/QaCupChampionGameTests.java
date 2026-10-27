package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.cup.CupChampions;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupDays;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.cup.Cups;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA for ROADMAP 28.21 (Champions), written from the spec: a player champion's village flies the banner and the player
 * gets no trainer XP (the purse only); a village holding two hosts' Cups keeps its banner and pride when it loses one; a
 * Cup the next festival that doesn't happen keeps the holder; the banner never replaces a block on the hall; an old
 * holder away when the title passes takes the banner down at its next round; the chronicle line's exact text in every
 * circuit hall.
 */
public class QaCupChampionGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos ASHFORD = new BlockPos(3, 2, 27);
	private static final BlockPos POLE = new BlockPos(15, 7, 1);
	private static final ResourceLocation THEME = AliveWorkplace.id("test_qa_cup_champions");
	private static final String PRIDE = "our village holds the Cup";

	private static CupThemes.Theme theme() {
		return new CupThemes.Theme(THEME, "cup.aliveworkplace.grand_cup", 1, "singles", 50, 3, List.of(), "any", List.of(), List.of(), 6000, 18000,
			List.of(), ResourceLocation.withDefaultNamespace("pumpkin_pie"), List.of(0xFF0000, 0x00FF00), ResourceLocation.withDefaultNamespace("music_disc_cat"));
	}

	private static final class Setup {
		BlockPos host;
		BlockPos ashford;
		BlockPos east;
		BlockPos north;
		VillageHallBlockEntity entity;
		VillageHallBlockEntity ash;
	}

	/** Thornholm (the host, with an Arena III and its champion's pole) and Ashford (a neighbour with no Arena) on one circuit, plus two far villages. */
	private static Setup setUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = new Setup();
		s.host = helper.absolutePos(HALL);
		s.ashford = helper.absolutePos(ASHFORD);
		s.entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		s.entity.setRank(VillageRanks.Rank.VILLAGE);
		s.entity.setCustomName(Component.literal("Thornholm"));
		s.ash = (VillageHallBlockEntity) helper.getBlockEntity(ASHFORD);
		s.ash.setCustomName(Component.literal("Ashford"));
		BlueprintData.Placement arena = new BlueprintData.Placement(level.dimension().location(), s.host.offset(-12, -1, -14), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.ARENAS.get(2).id(), arena, UUID.randomUUID());
		helper.setBlock(POLE, Blocks.STRIPPED_SPRUCE_LOG);
		helper.setBlock(POLE.above(), Blocks.GOLD_BLOCK);
		Map<ResourceLocation, CupThemes.Theme> themes = new LinkedHashMap<>(CupThemes.loaded());
		CupThemes.setForTest(Map.of(THEME, theme()));
		Cups.COBBLEMON = true;
		Caravans.Data caravans = Caravans.Data.get(level);
		caravans.setWants(s.ashford, Component.literal("Ashford"), List.of());
		caravans.toggleRoute(s.host, s.ashford, 10);
		s.east = s.host.offset(40_000, 0, 0);
		s.north = s.host.offset(0, 0, -40_000);
		caravans.setWants(s.east, Component.literal("Eastholm"), List.of());
		caravans.toggleRoute(s.host, s.east, 10);
		caravans.setWants(s.north, Component.literal("Northby"), List.of());
		caravans.toggleRoute(s.host, s.north, 10);
		Leftovers.after(helper, () -> {
			BuildSiteManager.get(level).forgetFinished(arena);
			CupData data = CupData.get(level);
			for (BlockPos v : List.of(s.host, s.ashford, s.east, s.north)) {
				data.forget(v);
				data.takeNotes(v);
				data.setBanners(v, List.of());
				caravans.remove(v);
			}
			CupThemes.setForTest(themes);
			Cups.COBBLEMON = io.github.jcondedata.aliveworkplace.trainer.Trainers.COBBLEMON;
			Cups.ENABLED = true;
			CupDays.forget();
			Festivals.forget();
			Moods.forget();
		});
		return s;
	}

	private static UUID id(String name) {
		return UUID.nameUUIDFromBytes(("qa-" + name).getBytes());
	}

	private static Villager villager(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		return v;
	}

	/** A Cup of 4 at the host on {@code day}: slot 0 beats 1, {@code second} wins the other bout, the final to {@code champion}. */
	private static CupData.Cup final_(ServerLevel level, Setup s, long day, List<CupData.Entrant> bracket, CupData.Entrant second, CupData.Entrant champion) {
		CupData.Cup cup = CupData.get(level).cup(s.host);
		cup.theme = THEME;
		cup.day = day;
		cup.closed = true;
		cup.noCup = "";
		cup.entrants.clear();
		cup.entrants.addAll(bracket);
		cup.bracket.clear();
		cup.bracket.addAll(bracket);
		cup.results.clear();
		cup.results.add(new CupData.Result(1, bracket.get(0).id(), bracket.get(1).id()));
		CupData.Entrant other = second == bracket.get(2) ? bracket.get(3) : bracket.get(2);
		cup.results.add(new CupData.Result(1, second.id(), other.id()));
		CupData.Entrant runnerUp = champion == bracket.get(0) ? second : bracket.get(0);
		cup.results.add(new CupData.Result(2, champion.id(), runnerUp.id()));
		CupDays.settle(level, s.host, cup);
		return cup;
	}

	private static boolean proud(ServerLevel level, Villager v) {
		return Moods.work(level, v).good().stream().anyMatch(c -> c.getString().equals(PRIDE));
	}

	private static List<String> cupLines(VillageHallBlockEntity hall, long day) {
		return hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CUP && e.day() == day).map(e -> e.text().getString()).toList();
	}

	/** A player of Ashford wins the final: the banner flies over Ashford (it belongs to the village), no trainer XP for anyone, Ashford is proud. */
	//$ gametest_ticks_batch AREA '200' '"qaCupChampionsPlayer"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupChampionsPlayer")
	public void aPlayerChampionsVillageFliesTheBannerAndThePlayerGetsNoXp(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(ASHFORD, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			Setup s = setUp(helper);
			Villager ashen = villager(helper, new BlockPos(4, 2, 25));
			Villager baker = villager(helper, new BlockPos(17, 2, 20));
			CupData.Entrant alex = new CupData.Entrant(CupData.Kind.PLAYER, id("Alex"), "Alex", 0, 0, s.ashford);
			CupData.Entrant mira = new CupData.Entrant(CupData.Kind.LEADER, id("Mira"), "Mira", 4, 300, s.host);
			CupData.Entrant dara = new CupData.Entrant(CupData.Kind.LEADER, id("Dara"), "Dara", 3, 90, s.east);
			CupData.Entrant ren = new CupData.Entrant(CupData.Kind.LEADER, id("Ren"), "Ren", 2, 50, s.north);
			long day = Chronicle.day(level);
			CupData.Cup cup = final_(level, s, day, List.of(alex, ren, mira, dara), mira, alex);
			CupData.Champion c = CupChampions.holder(cup);
			helper.assertTrue(c != null && c.village().equals(s.ashford) && id("Alex").equals(c.id()) && c.name().equals("Alex"), "the player isn't on the roll: " + cup.champions);
			helper.assertTrue(CupChampions.holds(level, s.ashford) && !CupChampions.holds(level, s.host), "Ashford doesn't hold the Cup");
			helper.assertTrue(CupChampions.isCupBanner(level, s.ashford.above()), "no banner over the player's village: " + helper.getBlockState(ASHFORD.above()));
			helper.assertTrue(CupData.get(level).banners(s.host).isEmpty(), "a banner at the host");
			// the purse only: no trainer XP banked for the player (or the host's Leader, who lost the final)
			Map<UUID, Integer> banked = Caravans.Data.get(level).banked(s.ashford);
			helper.assertTrue(banked.getOrDefault(id("Alex"), 0) == 0, "a player champion got trainer XP: " + banked);
			helper.assertTrue(Caravans.Data.get(level).banked(s.host).getOrDefault(id("Mira"), 0) == 0, "the runner-up got the win bonuses");
			helper.assertTrue(proud(level, ashen) && !proud(level, baker), "the pride: Ashford " + proud(level, ashen) + ", Thornholm " + proud(level, baker));
			helper.assertTrue(CupChampions.mood(level, ashen).points() == 5, "the pride isn't +5");
			// the chronicle, word for word, in both circuit halls (the player's name and village in it)
			String line = "The Grand Cup at Thornholm: Alex of Ashford is champion, beating Mira of Thornholm in the final";
			helper.assertTrue(cupLines(s.ash, day).equals(List.of(line)) && cupLines(s.entity, day).equals(List.of(line)),
				"the chronicle lines: Ashford " + cupLines(s.ash, day) + ", Thornholm " + cupLines(s.entity, day));
			helper.assertTrue(CupChampions.hallLines(level, s.ashford).get(0).getString().equals("Holders of the Thornholm Cup, won on day " + day), "tooltip");
			helper.succeed();
		});
	}

	/**
	 * Ashford holds two hosts' Cups (Thornholm's and Eastholm's). It loses Thornholm's: the banner stays (it still holds
	 * one), so does the pride, and one tooltip line is left. Thornholm's next Cup doesn't happen (too few): the title stays.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCupChampionsTwo"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupChampionsTwo")
	public void aVillageHoldingTwoCupsKeepsItsBannerWhenItLosesOne(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(ASHFORD, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			Setup s = setUp(helper);
			Villager ashen = villager(helper, new BlockPos(4, 2, 25));
			long day = Chronicle.day(level);
			// Eastholm's Cup, won by Ashford's Oren some days ago
			CupData.get(level).cup(s.east).champions.add(new CupData.Champion(day - 3, THEME, "Oren", s.ashford, id("Oren")));
			CupData.Entrant oren = new CupData.Entrant(CupData.Kind.LEADER, id("Oren"), "Oren", 3, 100, s.ashford);
			CupData.Entrant mira = new CupData.Entrant(CupData.Kind.LEADER, id("Mira"), "Mira", 4, 300, s.host);
			CupData.Entrant dara = new CupData.Entrant(CupData.Kind.LEADER, id("Dara"), "Dara", 3, 90, s.east);
			CupData.Entrant ren = new CupData.Entrant(CupData.Kind.LEADER, id("Ren"), "Ren", 2, 50, s.north);
			CupData.Cup cup = final_(level, s, day, List.of(oren, ren, mira, dara), mira, oren);
			helper.assertTrue(CupChampions.held(level, s.ashford).size() == 2 && CupChampions.hallLines(level, s.ashford).size() == 2,
				"Ashford holds " + CupChampions.held(level, s.ashford).size() + " Cups");
			helper.assertTrue(CupData.get(level).banners(s.ashford).size() == 1 && CupChampions.isCupBanner(level, s.ashford.above()),
				"one banner over Ashford: " + CupData.get(level).banners(s.ashford));
			// Thornholm's next Cup doesn't happen (too few came): Ashford still holds it
			Cups.over(cup);
			cup.day = day + 1;
			cup.closed = true;
			cup.noCup = "too_few";
			cup.entrants.clear();
			cup.bracket.clear();
			cup.results.clear();
			CupDays.settle(level, s.host, cup);
			CupChampions.round(level, s.ashford);
			helper.assertTrue(CupChampions.held(level, s.ashford).size() == 2, "a Cup that didn't happen took the title: " + CupChampions.held(level, s.ashford).size());
			// Thornholm's Cup after: Mira wins it back
			final_(level, s, day + 2, List.of(mira, ren, oren, dara), oren, mira);
			helper.assertTrue(CupChampions.holds(level, s.host), "Thornholm doesn't hold its Cup");
			helper.assertTrue(CupChampions.held(level, s.ashford).size() == 1, "Ashford still holds " + CupChampions.held(level, s.ashford).size());
			CupChampions.round(level, s.ashford);
			helper.assertTrue(CupChampions.isCupBanner(level, s.ashford.above()) && CupData.get(level).banners(s.ashford).size() == 1,
				"Ashford's banner came down while it still holds Eastholm's Cup: " + helper.getBlockState(ASHFORD.above()));
			helper.assertTrue(proud(level, ashen), "Ashford lost its pride while it still holds a Cup");
			List<Component> lines = CupChampions.hallLines(level, s.ashford);
			helper.assertTrue(lines.size() == 1 && lines.get(0).getString().equals("Holders of the Eastholm Cup, won on day " + (day - 3)), "tooltip: " + lines);
			helper.assertTrue(CupChampions.isCupBanner(level, helper.absolutePos(POLE.north())), "no banner on Thornholm's pole");
			helper.succeed();
		});
	}

	/** A stone block on Ashford's hall: the banner never replaces it and stands on the roof over the hall; when the title passes the banner goes and the stone stays. */
	//$ gametest_ticks_batch AREA '200' '"qaCupChampionsRoof"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupChampionsRoof")
	public void theBannerNeverReplacesABlockOnTheHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(ASHFORD, ModBlocks.VILLAGE_HALL);
		helper.setBlock(ASHFORD.above(), Blocks.STONE);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			Setup s = setUp(helper);
			CupData.Entrant oren = new CupData.Entrant(CupData.Kind.LEADER, id("Oren"), "Oren", 3, 100, s.ashford);
			CupData.Entrant mira = new CupData.Entrant(CupData.Kind.LEADER, id("Mira"), "Mira", 4, 300, s.host);
			CupData.Entrant dara = new CupData.Entrant(CupData.Kind.LEADER, id("Dara"), "Dara", 3, 90, s.east);
			CupData.Entrant ren = new CupData.Entrant(CupData.Kind.LEADER, id("Ren"), "Ren", 2, 50, s.north);
			long day = Chronicle.day(level);
			final_(level, s, day, List.of(oren, ren, mira, dara), mira, oren);
			helper.assertTrue(helper.getBlockState(ASHFORD.above()).is(Blocks.STONE), "the stone on the hall was replaced: " + helper.getBlockState(ASHFORD.above()));
			// the banner goes on the roof over the hall (the highest block of its column), never in place of the stone
			List<BlockPos> flying = CupData.get(level).banners(s.ashford);
			helper.assertTrue(flying.size() == 1 && flying.get(0).getX() == s.ashford.getX() && flying.get(0).getZ() == s.ashford.getZ()
				&& flying.get(0).getY() >= s.ashford.getY() + 2 && CupChampions.isCupBanner(level, flying.get(0))
				&& !level.getBlockState(flying.get(0).below()).isAir(), "the banner isn't on top over the hall: " + flying);
			final_(level, s, day + 1, List.of(mira, ren, oren, dara), oren, mira);
			helper.assertTrue(!CupChampions.isCupBanner(level, flying.get(0)) && level.getBlockState(flying.get(0)).isAir() && helper.getBlockState(ASHFORD.above()).is(Blocks.STONE),
				"after the title passed: " + level.getBlockState(flying.get(0)) + ", the stone " + helper.getBlockState(ASHFORD.above()));
			helper.assertTrue(CupData.get(level).banners(s.ashford).isEmpty(), "banners still recorded");
			helper.succeed();
		});
	}

	/** Thornholm holds the Cup (banners on its pole); its hall is gone when Ashford wins; the banners come down at the hall's next round, and the pride with them. */
	//$ gametest_ticks_batch AREA '200' '"qaCupChampionsAway"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupChampionsAway")
	public void anOldHolderAwayWhenTheTitlePassesTakesItsBannerDownAtItsRound(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(ASHFORD, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			Setup s = setUp(helper);
			Villager baker = villager(helper, new BlockPos(17, 2, 20));
			CupData.Entrant oren = new CupData.Entrant(CupData.Kind.LEADER, id("Oren"), "Oren", 3, 100, s.ashford);
			CupData.Entrant mira = new CupData.Entrant(CupData.Kind.LEADER, id("Mira"), "Mira", 4, 300, s.host);
			CupData.Entrant dara = new CupData.Entrant(CupData.Kind.LEADER, id("Dara"), "Dara", 3, 90, s.east);
			CupData.Entrant ren = new CupData.Entrant(CupData.Kind.LEADER, id("Ren"), "Ren", 2, 50, s.north);
			long day = Chronicle.day(level);
			final_(level, s, day, List.of(mira, ren, oren, dara), oren, mira);
			helper.assertTrue(CupData.get(level).banners(s.host).size() == 2, "no banners on Thornholm's pole: " + CupData.get(level).banners(s.host));
			helper.assertTrue(proud(level, baker), "Thornholm isn't proud");
			// the hall is away (not there) for the next final
			helper.setBlock(HALL, Blocks.AIR);
			final_(level, s, day + 1, List.of(oren, ren, mira, dara), mira, oren);
			helper.assertTrue(CupChampions.holds(level, s.ashford) && !CupChampions.holds(level, s.host), "the title didn't pass");
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(2, () -> {
				CupChampions.round(level, s.host);
				for (BlockPos side : List.of(POLE.north(), POLE.south())) {
					helper.assertTrue(helper.getBlockState(side).isAir(), "the old holder's banner still flies at " + side + " after its round");
				}
				helper.assertTrue(CupData.get(level).banners(s.host).isEmpty(), "the old banners still recorded");
				Moods.forget();
				helper.assertTrue(!proud(level, baker), "Thornholm still proud");
				helper.succeed();
			});
		});
	}
}
