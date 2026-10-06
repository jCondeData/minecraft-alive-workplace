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
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;

/**
 * The Festival Cup's champions (ROADMAP 28.21): after a final the Cup banner flies on the champion's pole (Arena III)
 * or on top of the hall, and comes down when a new champion is crowned; the holders' mood comes and goes with it; the
 * Cup's entry is in every circuit hall's chronicle; the holder is named on its hall's tooltip, in trade-route lists and
 * on its Village Map; the champion's win bonuses; a far champion gets its banner when it loads; the defending champion
 * is seeded first; the title and banners are saved; with {@code festivalCup} off the banner comes down.
 */
public class CupChampionGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	/** Thornholm, the host: Arena III on record, its champion's pole at (15, 7, 1). */
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	/** Ashford, a neighbour on the host's circuit, 2 villages away from the host's Arena. */
	private static final BlockPos ASHFORD = new BlockPos(3, 2, 27);
	private static final BlockPos POLE = new BlockPos(15, 7, 1);
	private static final ResourceLocation THEME = AliveWorkplace.id("test_cup_champions");

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
		BlueprintData.Placement arena;
		Map<ResourceLocation, CupThemes.Theme> themes;
	}

	private static Setup setUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = new Setup();
		s.host = helper.absolutePos(HALL);
		s.ashford = helper.absolutePos(ASHFORD);
		s.entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		s.entity.setRank(VillageRanks.Rank.VILLAGE);
		s.entity.setCustomName(Component.literal("Thornholm"));
		s.arena = new BlueprintData.Placement(level.dimension().location(), s.host.offset(-12, -1, -14), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.ARENAS.get(2).id(), s.arena, UUID.randomUUID());
		helper.setBlock(POLE, Blocks.STRIPPED_SPRUCE_LOG);
		helper.setBlock(POLE.above(), Blocks.GOLD_BLOCK);
		s.themes = new LinkedHashMap<>(CupThemes.loaded());
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
			BuildSiteManager.get(level).forgetFinished(s.arena);
			CupData data = CupData.get(level);
			for (BlockPos v : List.of(s.host, s.ashford, s.east, s.north)) {
				data.forget(v);
				data.takeNotes(v);
				data.setBanners(v, List.of());
				caravans.remove(v);
			}
			CupThemes.setForTest(s.themes);
			Cups.COBBLEMON = io.github.jcondedata.aliveworkplace.trainer.Trainers.COBBLEMON;
			Cups.ENABLED = true;
			CupDays.forget();
			Festivals.forget();
			Moods.forget();
		});
		return s;
	}

	private static UUID id(String name) {
		return UUID.nameUUIDFromBytes(name.getBytes());
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, net.minecraft.world.entity.npc.VillagerProfession job, int lvl, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		if (job != null) {
			v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		}
		if (name != null) {
			v.setCustomName(Component.literal(name));
		}
		return v;
	}

	/** Draws a Cup of 4 on {@code day} ({@code bracket} in slot order) and plays it out: slot 0 beats 1, {@code second} wins 2 v 3, the final to {@code champion}. */
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

	private static boolean hasReason(Moods.Mood mood, String text) {
		return mood.good().stream().anyMatch(c -> c.getString().equals(text));
	}

	private static boolean hasEntry(VillageHallBlockEntity entity, long day) {
		return entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.CUP && e.day() == day);
	}

	// ---------------------------------------------------------------- the banner, the pride, the title passing

	/** The host's Leader wins: the banner on its pole; then Ashford's Leader wins: the pole's banner comes down, Ashford's hall gets it. */
	//$ gametest_ticks_batch AREA '200' '"cupChampions"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "cupChampions")
	public void theBannerFliesOverTheHolderAndPasses(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(ASHFORD, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			Setup s = setUp(helper);
			VillageHallBlockEntity ash = (VillageHallBlockEntity) helper.getBlockEntity(ASHFORD);
			ash.setCustomName(Component.literal("Ashford"));
			Villager mira = villager(helper, new BlockPos(15, 2, 20), ModVillagers.TRAINER_LEADER, 4, "Mira");
			Villager baker = villager(helper, new BlockPos(17, 2, 20), null, 1, null);
			Villager ashen = villager(helper, new BlockPos(4, 2, 25), null, 1, null);
			CupData.Entrant m = new CupData.Entrant(CupData.Kind.LEADER, mira.getUUID(), "Mira", 4, mira.getVillagerXp(), s.host);
			CupData.Entrant oren = new CupData.Entrant(CupData.Kind.LEADER, id("Oren"), "Oren", 3, 100, s.ashford);
			CupData.Entrant dara = new CupData.Entrant(CupData.Kind.LEADER, id("Dara"), "Dara", 3, 90, s.east);
			CupData.Entrant ren = new CupData.Entrant(CupData.Kind.LEADER, id("Ren"), "Ren", 2, 50, s.north);
			int xpBefore = mira.getVillagerXp();
			helper.assertTrue(CupChampions.mood(level, baker) == null, "a mood before any Cup");

			// Cup 1: Mira of Thornholm is champion
			long day1 = Chronicle.day(level);
			CupData.Cup cup = final_(level, s, day1, List.of(m, ren, oren, dara), oren, m);
			helper.assertTrue(cup.finaleDay == day1 && CupChampions.holder(cup) != null && CupChampions.holder(cup).village().equals(s.host)
				&& mira.getUUID().equals(CupChampions.holder(cup).id()), "Mira isn't on the roll: " + cup.champions);
			helper.assertTrue(CupChampions.holds(level, s.host) && !CupChampions.holds(level, s.ashford), "the holder");
			// the banner on both faces of the champion's pole, in the theme's colours (a red cup on lime)
			for (BlockPos side : List.of(POLE.north(), POLE.south())) {
				helper.assertTrue(CupChampions.isCupBanner(level, helper.absolutePos(side)), "no Cup banner at " + side + ": " + helper.getBlockState(side));
				BannerBlockEntity banner = (BannerBlockEntity) helper.getBlockEntity(side);
				helper.assertTrue(banner.getBaseColor() == DyeColor.LIME && banner.getPatterns().layers().size() == 1
					&& banner.getPatterns().layers().get(0).color() == DyeColor.RED, "the banner's colours: " + banner.getBaseColor() + " " + banner.getPatterns());
				Direction facing = helper.getBlockState(side).getValue(WallBannerBlock.FACING);
				helper.assertTrue(facing == (side.getZ() < POLE.getZ() ? Direction.NORTH : Direction.SOUTH), "the banner faces " + facing);
			}
			helper.assertTrue(CupData.get(level).banners(s.host).size() == 2, "banners recorded " + CupData.get(level).banners(s.host));
			helper.assertTrue(helper.getBlockState(ASHFORD.above()).isAir(), "a banner over Ashford");
			// a second round puts up no second banner
			CupChampions.round(level, s.host);
			helper.assertTrue(CupData.get(level).banners(s.host).size() == 2, "the banner went up twice");
			// the pride
			helper.assertTrue(hasReason(Moods.work(level, baker), "our village holds the Cup"), "no pride in Thornholm: " + Moods.work(level, baker).good());
			helper.assertTrue(CupChampions.mood(level, baker).points() == CupChampions.MOOD, "the pride's points");
			helper.assertTrue(!hasReason(Moods.work(level, ashen), "our village holds the Cup"), "pride in Ashford");
			// the champion's two win bonuses
			helper.assertTrue(mira.getVillagerXp() > xpBefore, "no win bonus for Mira: " + xpBefore + " -> " + mira.getVillagerXp());
			// the chronicle of every circuit hall
			helper.assertTrue(hasEntry(s.entity, day1) && hasEntry(ash, day1), "the chronicle line isn't in every loaded circuit hall");
			helper.assertTrue(CupData.get(level).notes(s.east).size() == 1 && CupData.get(level).notes(s.north).size() == 1, "far halls' entries not kept");
			// named on the hall's tooltip, in the trade-route lists and on the Village Map
			List<Component> lines = CupChampions.hallLines(level, s.host);
			helper.assertTrue(lines.size() == 1 && lines.get(0).getString().equals("Holders of the Thornholm Cup, won on day " + day1), "tooltip: " + lines);
			List<Component> route = CupChampions.holderLines(level, s.host);
			helper.assertTrue(route.size() == 1 && route.get(0).getString().equals("Holders of the Thornholm Cup"), "route line: " + route);
			helper.assertTrue(CupChampions.holderLines(level, s.ashford).isEmpty(), "Ashford named a holder");
			ItemStack map = VillageMaps.map(level, s.host);
			ItemLore lore = map.get(DataComponents.LORE);
			helper.assertTrue(lore != null && lore.lines().stream().anyMatch(c -> c.getString().equals("Holders of the Thornholm Cup")), "the map's legend: " + lore);

			// Cup 2: Oren of Ashford beats Mira; Mira (the defending champion) is seeded first
			Cups.over(cup);
			List<CupData.Entrant> seeded = Cups.seed(List.of(oren, dara, m, ren), CupChampions.defending(cup, List.of(oren, dara, m, ren)));
			helper.assertTrue(seeded.get(0).equals(m), "the defending champion isn't seeded first: " + seeded);
			long day2 = day1 + 1;
			final_(level, s, day2, List.of(oren, dara, m, ren), m, oren);
			helper.assertTrue(CupChampions.holds(level, s.ashford) && !CupChampions.holds(level, s.host), "the title didn't pass");
			for (BlockPos side : List.of(POLE.north(), POLE.south())) {
				helper.assertTrue(helper.getBlockState(side).isAir(), "the old holder's banner still flies at " + side);
			}
			helper.assertTrue(CupData.get(level).banners(s.host).isEmpty(), "the old banners still recorded");
			// Ashford has no Arena of its own (the host's is nearer Thornholm): the banner stands on its hall
			helper.assertTrue(CupChampions.isCupBanner(level, s.ashford.above()), "no banner on Ashford's hall: " + helper.getBlockState(ASHFORD.above()));
			helper.assertTrue(hasReason(Moods.work(level, ashen), "our village holds the Cup") && !hasReason(Moods.work(level, baker), "our village holds the Cup"),
				"the pride didn't move");
			helper.assertTrue(Moods.of(baker) == null || !hasReason(Moods.of(baker), "our village holds the Cup"), "Thornholm's kept mood still proud");
			helper.assertTrue(hasEntry(s.entity, day2) && hasEntry(ash, day2), "the second Cup isn't in every circuit hall");
			helper.assertTrue(CupChampions.hallLines(level, s.host).isEmpty() && CupChampions.hallLines(level, s.ashford).get(0).getString()
				.equals("Holders of the Thornholm Cup, won on day " + day2), "the tooltip lines didn't move");

			// saved and loaded: the champion's id and the banners
			CupData back = CupData.load(CupData.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			helper.assertTrue(back.banners(s.ashford).equals(List.of(s.ashford.above())), "banners reloaded: " + back.banners(s.ashford));
			helper.assertTrue(back.existing(s.host).champions.equals(cup.champions) && id("Oren").equals(CupChampions.holder(back.existing(s.host)).id()),
				"champions reloaded: " + back.existing(s.host).champions);

			// festivalCup off: nobody holds a Cup, the banner comes down at the next round, no pride
			Cups.ENABLED = false;
			CupChampions.round(level, s.ashford);
			helper.assertTrue(helper.getBlockState(ASHFORD.above()).isAir() && CupChampions.mood(level, ashen) == null, "the Cup off left the banner or the pride");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- a far champion

	/** Ashford's hall isn't there at the final (a far village, not loaded): no banner then, its XP banked; it gets the banner at its next round. */
	//$ gametest_ticks_batch AREA '200' '"cupChampionsFar"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "cupChampionsFar")
	public void aFarChampionGetsItsBannerWhenItLoads(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(ASHFORD, Blocks.AIR);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			Setup s = setUp(helper);
			CupData.Entrant pip = new CupData.Entrant(CupData.Kind.HOST_TRAINER, id("Pip"), "Pip", 2, 10, s.host);
			CupData.Entrant oren = new CupData.Entrant(CupData.Kind.LEADER, id("Oren"), "Oren", 3, 100, s.ashford);
			CupData.Entrant dara = new CupData.Entrant(CupData.Kind.LEADER, id("Dara"), "Dara", 3, 90, s.east);
			CupData.Entrant ren = new CupData.Entrant(CupData.Kind.LEADER, id("Ren"), "Ren", 2, 50, s.north);
			long day = Chronicle.day(level);
			final_(level, s, day, List.of(oren, pip, dara, ren), dara, oren);
			helper.assertTrue(CupChampions.holds(level, s.ashford), "Ashford doesn't hold the Cup");
			helper.assertTrue(CupData.get(level).banners(s.ashford).isEmpty() && helper.getBlockState(ASHFORD.above()).isAir(), "a banner over a village away");
			helper.assertTrue(CupData.get(level).banners(s.host).isEmpty(), "a banner over the host");
			helper.assertTrue(Caravans.Data.get(level).banked(s.ashford).getOrDefault(id("Oren"), 0) == CupChampions.WIN_BONUSES
				* io.github.jcondedata.aliveworkplace.trainer.Trainers.XP_FOR_WIN, "Oren's win bonuses not banked: " + Caravans.Data.get(level).banked(s.ashford));
			helper.assertTrue(CupData.get(level).notes(s.ashford).size() == 1, "Ashford's chronicle entry not kept");
			// the defending champion, gone from the next Cup: Ashford's villager entrant stands first instead
			CupData.Entrant newLeader = new CupData.Entrant(CupData.Kind.TRAINER, id("Wren"), "Wren", 1, 5, s.ashford);
			CupData.Entrant player = new CupData.Entrant(CupData.Kind.PLAYER, id("Alex"), "Alex", 0, 0, s.ashford);
			CupData.Cup cup = CupData.get(level).existing(s.host);
			List<CupData.Entrant> next = List.of(dara, ren, player, newLeader, pip);
			helper.assertTrue(id("Wren").equals(CupChampions.defending(cup, next)) && Cups.seed(next, CupChampions.defending(cup, next)).get(0).equals(newLeader),
				"the holder's entrant isn't seeded first");
			helper.assertTrue(Cups.seed(next, null).get(0).equals(dara), "plain seeding changed");

			// Ashford loads: its hall's round puts the banner up, and its chronicle gets the Cup
			helper.setBlock(ASHFORD, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(2, () -> {
				VillageHallBlockEntity ash = (VillageHallBlockEntity) helper.getBlockEntity(ASHFORD);
				Cups.round(level, s.ashford, ash);
				helper.assertTrue(CupChampions.isCupBanner(level, s.ashford.above()), "no banner when Ashford loaded: " + helper.getBlockState(ASHFORD.above()));
				helper.assertTrue(CupData.get(level).banners(s.ashford).equals(List.of(s.ashford.above())), "not recorded");
				helper.assertTrue(hasEntry(ash, day), "Ashford's chronicle has no Cup");
				// the pattern's name, in each colour
				helper.assertTrue(Component.translatable("block.aliveworkplace.banner.cup.red").getString().equals("Red Cup")
					&& Component.translatable("block.aliveworkplace.banner.cup.light_blue").getString().equals("Light Blue Cup"), "the pattern's name");
				helper.assertTrue(Component.translatable("mood.aliveworkplace.reason.cup").getString().equals("our village holds the Cup"), "the mood's line");
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- the banner's colours

	/** The theme's firework colours as dyes: the cup in the first, the field in the second; one colour: a white (or black) field. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBannerTakesTheThemesColours(GameTestHelper helper) {
		helper.assertTrue(CupChampions.nearest(0xF5C542) == DyeColor.YELLOW && CupChampions.nearest(0xE83F3F) == DyeColor.RED, "nearest dyes");
		CupChampions.Colours grand = CupChampions.colours(theme(List.of(0xF5C542, 0xE83F3F)));
		helper.assertTrue(grand.cup() == DyeColor.YELLOW && grand.field() == DyeColor.RED, "the Grand Cup's banner: " + grand);
		CupChampions.Colours one = CupChampions.colours(theme(List.of(0xF9FFFE)));
		helper.assertTrue(one.cup() == DyeColor.WHITE && one.field() == DyeColor.BLACK, "a white cup alone: " + one);
		CupChampions.Colours none = CupChampions.colours(null);
		helper.assertTrue(none.cup() == DyeColor.YELLOW && none.field() == DyeColor.RED, "no theme: " + none);
		helper.succeed();
	}

	private static CupThemes.Theme theme(List<Integer> fireworks) {
		return new CupThemes.Theme(THEME, "cup.aliveworkplace.grand_cup", 1, "singles", 50, 3, List.of(), "any", List.of(), List.of(), 6000, 18000,
			List.of(), ResourceLocation.withDefaultNamespace("pumpkin_pie"), new ArrayList<>(fireworks), ResourceLocation.withDefaultNamespace("music_disc_cat"));
	}
}
