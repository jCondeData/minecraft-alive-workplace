package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupDays;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.cup.Cups;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * The Festival Cup's day (ROADMAP 28.19): a Cup with 4 entrants (2 of them delegates from far villages) from morning to
 * a champion: the delegates come in from their homes' side and are gone by dawn, the fair sells the theme's wares, the
 * villagers are in the stands for the final and cheer their village's win, the champion is in every circuit village's
 * chronicle (kept for the far ones until they load). With {@code festivalCup} off it's a plain festival; an Arena gone,
 * a host not loaded (put off, then called off), the end time settling the rest, and every new sentence.
 */
public class CupDayGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final ResourceLocation THEME = AliveWorkplace.id("test_cup_day");
	private static final long DAY = 24000;

	/** What one test set up, to put back. */
	private static final class Setup {
		final List<BlueprintData.Placement> arenas = new ArrayList<>();
		final List<BlockPos> villages = new ArrayList<>();
		Map<ResourceLocation, CupThemes.Theme> themes;
		BlockPos hall;
		VillageHallBlockEntity entity;
		BlueprintData.Placement arena;
	}

	private static CupThemes.Theme theme() {
		return new CupThemes.Theme(THEME, "cup.aliveworkplace.grand_cup", 1, "singles", 50, 3, List.of(), "any", List.of(), List.of(), 6000, 18000,
			List.of(new CupThemes.Ware(ResourceLocation.withDefaultNamespace("diamond"), 3), new CupThemes.Ware(ResourceLocation.parse("cobblemon:not_here"), 5)),
			ResourceLocation.withDefaultNamespace("pumpkin_pie"), List.of(0xFF0000, 0x00FF00), ResourceLocation.withDefaultNamespace("music_disc_cat"));
	}

	/** The host (a Village with Arena I on record, ring at (15, 2, 10), benches at z 17 and 18), the test theme, Cobblemon assumed. */
	private static Setup host(GameTestHelper helper) {
		Setup s = new Setup();
		ServerLevel level = helper.getLevel();
		s.hall = helper.absolutePos(HALL);
		s.entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		s.entity.setRank(VillageRanks.Rank.VILLAGE);
		s.entity.setCustomName(Component.literal("Thornholm"));
		s.arena = new BlueprintData.Placement(level.dimension().location(), s.hall.offset(-12, -1, -14), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.ARENA.id(), s.arena, UUID.randomUUID());
		s.arenas.add(s.arena);
		s.themes = new LinkedHashMap<>(CupThemes.loaded());
		CupThemes.setForTest(Map.of(THEME, theme()));
		Cups.COBBLEMON = true;
		Leftovers.after(helper, () -> {
			s.arenas.forEach(BuildSiteManager.get(level)::forgetFinished);
			CupData.get(level).forget(s.hall);
			CupData.get(level).takeNotes(s.hall);
			s.villages.forEach(v -> {
				Caravans.Data.get(level).remove(v);
				CupData.get(level).takeNotes(v);
				CupData.get(level).forget(v);
			});
			Caravans.Data.get(level).remove(s.hall);
			CupThemes.setForTest(s.themes);
			Cups.COBBLEMON = io.github.jcondedata.aliveworkplace.trainer.Trainers.COBBLEMON;
			Cups.ENABLED = true;
			CupDays.forget();
			Festivals.forget();
		});
		return s;
	}

	/** A far village on the host's trade routes, its Leader on the caravans' list. */
	private static BlockPos far(GameTestHelper helper, Setup s, BlockPos offset, String village, String leader, int tier, int xp) {
		Caravans.Data data = Caravans.Data.get(helper.getLevel());
		BlockPos at = s.hall.offset(offset);
		data.setWants(at, Component.literal(village), List.of());
		data.toggleRoute(s.hall, at, 10);
		data.setLeader(at, new Caravans.Leader(UUID.nameUUIDFromBytes(leader.getBytes()), leader, tier, xp, true));
		s.villages.add(at);
		return at;
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

	/** Sets the clock to {@code t} into the Cup's day. */
	private static void at(ServerLevel level, CupData.Cup cup, long t) {
		level.setDayTime((cup.day - 1) * DAY + t);
	}

	private static boolean hasEntry(VillageHallBlockEntity entity, long day) {
		return entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.CUP && e.day() == day);
	}

	// ---------------------------------------------------------------- the whole day

	/** Morning to champion: delegates from their homes' side, the fair, the stands for the final, cheers, the champion everywhere, gone by dawn. */
	//$ gametest_ticks_batch AREA '400' '"cupDay"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "cupDay")
	public void aCupDayFromMorningToChampion(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			Setup s = host(helper);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(s.hall)).forEach(Villager::discard);
			BlockPos east = far(helper, s, new BlockPos(40_000, 0, 0), "Eastholm", "Dara", 4, 300);
			BlockPos north = far(helper, s, new BlockPos(0, 0, -40_000), "Northby", "Ren", 3, 100);
			Villager mira = villager(helper, new BlockPos(3, 2, 25), ModVillagers.TRAINER_LEADER, 4, "Mira");
			villager(helper, new BlockPos(4, 2, 25), ModVillagers.TRAINER, 2, "Pip");
			List<Villager> crowd = new ArrayList<>();
			for (int x = 12; x <= 14; x++) {
				crowd.add(villager(helper, new BlockPos(x, 2, 17), null, 1, null));
			}
			Cups.round(level, s.hall, s.entity);
			CupData.Cup cup = CupData.get(level).existing(s.hall);
			helper.assertTrue(cup != null && THEME.equals(cup.theme) && cup.day >= 1, "no Cup set");
			// the morning: sign-up closes, 4 entrants, 2 of them from far villages
			at(level, cup, 500);
			Cups.round(level, s.hall, s.entity);
			helper.assertTrue(cup.closed && cup.noCup.isEmpty() && cup.bracket.size() == 4, "not drawn: " + cup.noCup + " " + cup.bracket);
			long farOnes = cup.entrants.stream().filter(e -> !e.village().equals(s.hall)).count();
			helper.assertTrue(cup.entrants.size() == 4 && farOnes == 2, "entrants " + cup.entrants);
			CupDays.tick(level);
			helper.assertTrue(CupDays.delegates(level, s.hall).isEmpty(), "delegates before 1000");

			// 1000: the delegates arrive from their homes' side; the fair opens with the theme's wares
			at(level, cup, CupDays.ARRIVE);
			CupDays.tick(level);
			List<Villager> delegates = CupDays.delegates(level, s.hall);
			helper.assertTrue(delegates.size() == 2, "delegates: " + delegates.size());
			for (Villager d : delegates) {
				CupDays.Delegate data = CupDays.delegate(d);
				String name = d.getCustomName().getString();
				helper.assertTrue(d.getVillagerData().getProfession() == ModVillagers.TRAINER_LEADER, "not in a Trainer Leader's outfit");
				if (data.home().equals(east)) {
					helper.assertTrue(name.equals("Dara of Eastholm"), "east delegate's name: " + name);
					helper.assertTrue(d.getX() - s.hall.getX() >= 7 && Math.abs(d.getZ() - s.hall.getZ()) < 3, "Dara didn't come from the east: " + d.blockPosition());
				} else {
					helper.assertTrue(data.home().equals(north) && name.equals("Ren of Northby"), "north delegate: " + name + " " + data.home());
					helper.assertTrue(s.hall.getZ() - d.getZ() >= 7 && Math.abs(d.getX() - s.hall.getX()) < 3, "Ren didn't come from the north: " + d.blockPosition());
				}
				helper.assertTrue(data.host().equals(s.hall) && data.day() == cup.day && data.leader().equals(UUID.nameUUIDFromBytes(
					(data.home().equals(east) ? "Dara" : "Ren").getBytes())), "delegate data " + data);
				helper.assertTrue(CupDays.noChallenge(level, d).getString().equals(name + " is here for the Cup and takes no challenges today"), "a delegate takes challenges");
				// saved with the villager
				CupDays.Delegate back = CupDays.Delegate.CODEC.parse(NbtOps.INSTANCE, CupDays.Delegate.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow()).getOrThrow();
				helper.assertTrue(back.equals(data), "the delegate isn't saved: " + back);
			}
			CupDays.tick(level);
			helper.assertTrue(CupDays.delegates(level, s.hall).size() == 2, "delegates came twice");
			Arenas.Arena arena = Arenas.find(level, s.hall).orElseThrow();
			List<WanderingTrader> fair = level.getEntitiesOfClass(WanderingTrader.class, new AABB(arena.noticeBoard()).inflate(8), WanderingTrader::isAlive);
			helper.assertTrue(fair.size() >= CupDays.FAIR_EXTRA, "the fair has " + fair.size() + " traders");
			helper.assertTrue(fair.stream().allMatch(t -> t.getOffers().stream().anyMatch(o -> o.getResult().is(Items.DIAMOND) && o.getCostA().getCount() == 3)),
				"the fair doesn't sell the theme's wares");
			// a real Leader whose village is loaded is away
			Villager realDara = EntityType.VILLAGER.create(level);
			realDara.setUUID(UUID.nameUUIDFromBytes("Dara".getBytes()));
			realDara.setVillagerData(realDara.getVillagerData().setProfession(ModVillagers.TRAINER_LEADER).setLevel(4));
			realDara.setCustomName(Component.literal("Dara"));
			helper.assertTrue(CupDays.noChallenge(level, realDara) != null && CupDays.noChallenge(level, realDara).getString()
				.equals("Dara is away at the Cup in Thornholm today and takes no challenges"), "the real Leader isn't away");
			helper.assertTrue(CupDays.noChallenge(level, mira) == null, "the host's own Leader is away");

			// noon: the stands fill, the feast, the festival's square is the ring
			at(level, cup, CupDays.STANDS + 100);
			CupDays.tick(level);
			helper.assertTrue(Festivals.square(level, s.hall).equals(arena.ring()), "the festival isn't at the ring");
			helper.assertTrue(cup.feastDay == cup.day && CupDays.dish(level, s.hall) == Items.PUMPKIN_PIE, "no feast with the theme's dish");
			helper.assertTrue(crowd.stream().allMatch(Villager::isPassenger) && CupDays.seated(level, arena.ring()).size() == 3,
				"in the stands: " + CupDays.seated(level, arena.ring()).size());
			// round 1 (as the ring would fight it): the host's Leader wins hers last, the stands cheer
			CupData.Entrant mine = cup.entrants.stream().filter(e -> e.id().equals(mira.getUUID())).findFirst().orElseThrow();
			int mi = cup.bracket.indexOf(mine);
			CupData.Entrant rival = cup.bracket.get(mi ^ 1);
			int other = (mi / 2 == 0) ? 2 : 0;
			cup.results.add(new CupData.Result(1, cup.bracket.get(other).id(), cup.bracket.get(other + 1).id()));
			cup.results.add(new CupData.Result(1, mine.id(), rival.id()));
			CupData.Entrant finalist = cup.bracket.get(other);
			// a child comes along: for the final every villager sits
			Villager child = villager(helper, new BlockPos(15, 2, 17), null, 1, null);
			child.setAge(-24000);
			helper.assertTrue(CupDays.finalNext(cup), "the final isn't next");
			CupDays.tick(level);
			helper.assertTrue(CupDays.cheers(s.hall) >= 3 && CupDays.cheers(s.hall) == CupDays.seated(level, arena.ring()).size(), "the stands didn't cheer the host's win: " + CupDays.cheers(s.hall));
			helper.assertTrue(child.isPassenger(), "the child isn't in the stands for the final");
			// saved mid-day, loaded the same
			CupData loaded = CupData.load(CupData.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			CupData.Cup again = loaded.existing(s.hall);
			helper.assertTrue(again != null && again.delegatesDay == cup.day && again.fairDay == cup.day && again.feastDay == cup.day && again.cheered == 2
				&& again.finaleDay == -1, "the day isn't saved");

			// the final: the champion, fireworks, heroes, the festival's mood, every circuit chronicle
			cup.results.add(new CupData.Result(2, mine.id(), finalist.id()));
			CupDays.tick(level);
			helper.assertTrue(cup.finaleDay == cup.day && !cup.champions.isEmpty() && cup.champions.get(cup.champions.size() - 1).name().equals("Mira"),
				"no champion cheered: " + cup.champions);
			helper.assertTrue(hasEntry(s.entity, cup.day), "the host's chronicle has no Cup");
			String line = s.entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CUP).reduce((a, b) -> b).orElseThrow().text().getString();
			helper.assertTrue(line.equals("The Grand Cup at Thornholm: Mira of Thornholm is champion, beating " + finalist.name() + " of "
				+ (finalist.village().equals(east) ? "Eastholm" : finalist.village().equals(north) ? "Northby" : "Thornholm") + " in the final"), line);
			for (BlockPos v : List.of(east, north)) {
				helper.assertTrue(CupData.get(level).notes(v).size() == 1, "no entry kept for " + v);
			}
			helper.assertTrue(!level.getEntitiesOfClass(FireworkRocketEntity.class, new AABB(arena.ring()).inflate(12, 40, 12)).isEmpty(), "no fireworks");
			helper.assertTrue(crowd.stream().allMatch(v -> Long.valueOf(Chronicle.day(level)).equals(ModAttachments.FESTIVAL_DAY.get(v))), "no festival mood");
			helper.assertTrue(Component.translatable("message.aliveworkplace.cup.champion", "Mira", "Thornholm", Component.translatable("cup.aliveworkplace.grand_cup"))
				.getString().equals("Mira of Thornholm wins the Grand Cup!"), "the champion's line");

			// dawn: the day is over, the delegates are gone, the stands empty
			level.setDayTime(cup.day * DAY + 50);
			Cups.round(level, s.hall, s.entity);
			CupDays.tick(level);
			helper.assertTrue(CupDays.delegates(level, s.hall).isEmpty(), "delegates still here at dawn");
			helper.runAfterDelay(25, () -> {
				helper.assertTrue(crowd.stream().noneMatch(Villager::isPassenger), "still seated after the Cup");
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- festivalCup off

	/** With festivalCup off on the Cup's day: no delegates, no fair, no stands; a plain festival round the village's square. */
	//$ gametest_ticks_batch AREA '200' '"cupDayOff"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "cupDayOff")
	public void withTheCupOffItsAPlainFestival(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			Setup s = host(helper);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(s.hall)).forEach(Villager::discard);
			far(helper, s, new BlockPos(40_000, 0, 0), "Eastholm", "Dara", 4, 300);
			far(helper, s, new BlockPos(0, 0, -40_000), "Northby", "Ren", 3, 100);
			villager(helper, new BlockPos(3, 2, 25), ModVillagers.TRAINER_LEADER, 4, "Mira");
			villager(helper, new BlockPos(4, 2, 25), ModVillagers.TRAINER, 2, "Pip");
			Villager guest = villager(helper, new BlockPos(12, 2, 17), null, 1, null);
			Cups.round(level, s.hall, s.entity);
			CupData.Cup cup = CupData.get(level).existing(s.hall);
			at(level, cup, 500);
			Cups.round(level, s.hall, s.entity);
			helper.assertTrue(cup.closed && cup.bracket.size() == 4, "not drawn");
			Cups.ENABLED = false;
			Festivals.ENABLED = true; // off in the GameTest server
			Festivals.round(level, s.hall, s.entity, Festivals.MIN_VILLAGERS);
			Festivals.ENABLED = false;
			helper.assertTrue(s.entity.festivalDay() == Chronicle.day(level), "no festival on the Cup's day");
			at(level, cup, CupDays.STANDS + 100);
			CupDays.tick(level);
			Arenas.Arena arena = Arenas.find(level, s.hall).orElseThrow();
			helper.assertTrue(CupDays.delegates(level, s.hall).isEmpty() && !guest.isPassenger(), "Cup things with the Cup off");
			helper.assertTrue(level.getEntitiesOfClass(WanderingTrader.class, new AABB(arena.noticeBoard()).inflate(8)).isEmpty(), "a fair with the Cup off");
			helper.assertTrue(!Festivals.square(level, s.hall).equals(arena.ring()) && CupDays.dish(level, s.hall) == null, "the festival is at the ring");
			Cups.ENABLED = true;
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- what breaks it

	/** The end time settles the rest (a player away loses by walkover); an Arena gone: a plain festival; a host not loaded: put off, then called off. */
	//$ gametest_ticks_batch AREA '200' '"cupDayBreaks"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "cupDayBreaks")
	public void endTimeArenaGoneAndHostNotLoaded(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			Setup s = host(helper);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(s.hall)).forEach(Villager::discard);
			// the end time: every bout left settled; the player who isn't there loses by walkover
			CupData.Cup cup = CupData.get(level).cup(s.hall);
			cup.theme = THEME;
			cup.day = Chronicle.day(level);
			cup.closed = true;
			UUID player = UUID.nameUUIDFromBytes("away-player".getBytes());
			List<CupData.Entrant> four = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				four.add(new CupData.Entrant(CupData.Kind.HOST_TRAINER, UUID.nameUUIDFromBytes(("t" + i).getBytes()), "T" + i, 3 - i, 0, s.hall));
			}
			four.add(new CupData.Entrant(CupData.Kind.PLAYER, player, "Alex", 0, 0, s.hall));
			cup.entrants.addAll(four);
			cup.bracket.addAll(Cups.bracket(Cups.seed(four), 4));
			CupDays.settle(level, s.hall, cup);
			helper.assertTrue(cup.results.size() == 3 && io.github.jcondedata.aliveworkplace.cup.CupBouts.champion(cup) != null, "not settled: " + cup.results);
			helper.assertTrue(cup.results.stream().anyMatch(r -> r.loser().equals(player)), "the absent player didn't lose by walkover");
			helper.assertTrue(cup.results.stream().noneMatch(r -> r.winner().equals(player)), "the absent player won");
			helper.assertTrue(cup.finaleDay == cup.day && hasEntry(s.entity, cup.day), "no champion after the end time");

			// the Arena gone on the day: no Cup, a plain festival
			Cups.over(cup);
			cup.theme = THEME;
			cup.day = Chronicle.day(level);
			cup.closed = true;
			cup.entrants.addAll(four.subList(0, 3));
			cup.entrants.add(new CupData.Entrant(CupData.Kind.LEADER, UUID.randomUUID(), "Dara", 4, 0, s.hall.offset(40_000, 0, 0)));
			cup.bracket.addAll(Cups.bracket(Cups.seed(cup.entrants), 4));
			s.arenas.forEach(BuildSiteManager.get(level)::forgetFinished);
			level.setDayTime((cup.day - 1) * DAY + CupDays.ARRIVE + 10);
			CupDays.tick(level);
			helper.assertTrue("screen.aliveworkplace.cup.why.arena".equals(cup.noCup), "the Cup goes on without an Arena");
			helper.assertTrue(CupDays.delegates(level, s.hall).isEmpty() && CupDays.ring(level, s.hall).isEmpty(), "Cup things without an Arena");

			// a host not loaded on its morning: put off a day at a time, eight at most, then called off
			BlockPos away = s.hall.offset(-40_000, 0, 40_000);
			s.villages.add(away);
			CupData.Cup off = CupData.get(level).cup(away);
			off.theme = THEME;
			long today = Chronicle.day(level);
			off.day = today;
			CupDays.tick(level);
			helper.assertTrue(off.day == today + 1 && off.postponed == 1 && !off.closed, "not put off: day " + off.day + " postponed " + off.postponed);
			for (int i = 2; i <= CupDays.MAX_POSTPONED; i++) {
				off.day = today;
				CupDays.tick(level);
			}
			helper.assertTrue(off.postponed == CupDays.MAX_POSTPONED, "put off " + off.postponed);
			off.day = today;
			CupDays.tick(level);
			helper.assertTrue(off.postponed == 0 && off.day == -1 && !off.closed, "not called off after eight days: " + off.day);
			CupData saved = CupData.load(CupData.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			helper.assertTrue(saved.existing(away) != null && saved.existing(away).postponed == 0, "postponement isn't saved");

			// an entry kept for a village that wasn't loaded goes into its chronicle at its next round
			CupData.get(level).note(s.hall, new CupData.Note(3, Component.Serializer.toJson(Component.literal("kept"), level.registryAccess())));
			Cups.round(level, s.hall, s.entity);
			helper.assertTrue(s.entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.CUP && e.day() == 3 && e.text().getString().equals("kept")),
				"the kept entry wasn't written");
			helper.assertTrue(CupData.get(level).notes(s.hall).isEmpty(), "the kept entry is still kept");
			helper.succeed();
		});
	}

	/** Every new sentence a player reads on a Cup's day. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void cupDaySentences(GameTestHelper helper) {
		Map<String, Component> lines = new LinkedHashMap<>();
		lines.put("Dara of Eastholm", Component.translatable("entity.aliveworkplace.cup_delegate", "Dara", "Eastholm"));
		lines.put("Stand seat", Component.translatable("entity.aliveworkplace.stand_seat"));
		lines.put("Dara is away at the Cup in Thornholm today and takes no challenges", Component.translatable("message.aliveworkplace.cup.away", "Dara", "Thornholm"));
		lines.put("Thornholm has no Arena any more: no Cup today, just a plain festival", Component.translatable("message.aliveworkplace.cup.no_arena", "Thornholm"));
		lines.put("The Grand Cup at Thornholm is put off a day: the village isn't loaded. It's now on day 12.",
			Component.translatable("message.aliveworkplace.cup.put_off", Component.translatable("cup.aliveworkplace.grand_cup"), "Thornholm", 12));
		lines.put("The Grand Cup at Thornholm is called off: the village wasn't loaded for 8 days.",
			Component.translatable("message.aliveworkplace.cup.called_off", Component.translatable("cup.aliveworkplace.grand_cup"), "Thornholm", 8));
		lines.put("Time is up: Mira of Thornholm beats Dara of Eastholm in an exhibition",
			Component.translatable("message.aliveworkplace.cup.settled", "Mira", "Thornholm", "Dara", "Eastholm"));
		lines.put("Time is up: Mira of Thornholm goes on by walkover, Alex wasn't at the ring",
			Component.translatable("message.aliveworkplace.cup.settled_walkover", "Mira", "Thornholm", "Alex"));
		lines.put("The Grand Cup at Thornholm: Mira of Thornholm is champion",
			Component.translatable("chronicle.aliveworkplace.cup_walkover", Component.translatable("cup.aliveworkplace.grand_cup"), "Thornholm", "Mira", "Thornholm"));
		lines.forEach((want, c) -> helper.assertTrue(c.getString().equals(want), "\"" + c.getString() + "\" isn't \"" + want + "\""));
		helper.assertTrue(Chronicle.Kind.CUP.icon == Items.GOLD_INGOT, "the Cup's chronicle icon");
		helper.succeed();
	}
}
