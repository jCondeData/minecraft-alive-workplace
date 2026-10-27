package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.cup.CupBouts;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupDays;
import io.github.jcondedata.aliveworkplace.cup.CupMatches;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.cup.Cups;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA for the Cup's day (ROADMAP 28.19) and players in the Cup (28.20), written from the spec, covering what the
 * builder's tests leave out: the 1000 arrival and the noon stands from both sides of the boundary, the fair's count
 * ("two more than a market day"), delegates never keeping a job site or a bed, a delegate surviving an entity save, the
 * Cup switched off mid-day sending the delegates home; the two-minute window's last tick, a walkover win paying the purse
 * (emeralds without CobbleDollars) and a loss paying nothing, the City host's half again (and a Town's plain purse), and
 * the players' sentences exactly as en_us.json has them.
 */
public class QaCupDayGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String SMALL = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final ResourceLocation THEME = AliveWorkplace.id("qa_cup_day");
	private static final long DAY = 24000;

	private static CupThemes.Theme theme() {
		return new CupThemes.Theme(THEME, "cup.aliveworkplace.grand_cup", 1, "singles", 50, 3, List.of(), "any", List.of(), List.of(), 6000, 18000,
			List.of(new CupThemes.Ware(ResourceLocation.withDefaultNamespace("diamond"), 3)),
			ResourceLocation.withDefaultNamespace("pumpkin_pie"), List.of(0xFF0000), ResourceLocation.withDefaultNamespace("music_disc_cat"));
	}

	private static void at(ServerLevel level, CupData.Cup cup, long t) {
		level.setDayTime((cup.day - 1) * DAY + t);
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

	/**
	 * The day's numbers from both sides: no delegate at 999, both at 1000; the fair is exactly a market day's traders and
	 * two more; a delegate given a job site and a bed has neither after the next tick; a delegate saved and loaded as an
	 * entity is still a delegate; nobody seated at 5999, the stands full at 6000 and closed at the theme's end; the Cup
	 * switched off mid-day sends the delegates home (out of sight: gone).
	 */
	//$ gametest_ticks_batch AREA '300' '"qaCupDay"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaCupDay")
	public void theDaysNumbersFromBothSides(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			entity.setCustomName(Component.literal("Thornholm"));
			BlueprintData.Placement arenaAt = new BlueprintData.Placement(level.dimension().location(), hall.offset(-12, -1, -14), Rotation.NONE, Mirror.NONE);
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.ARENA.id(), arenaAt, UUID.randomUUID());
			Map<ResourceLocation, CupThemes.Theme> themes = new LinkedHashMap<>(CupThemes.loaded());
			CupThemes.setForTest(Map.of(THEME, theme()));
			Cups.COBBLEMON = true;
			Caravans.Data routes = Caravans.Data.get(level);
			BlockPos east = hall.offset(40_000, 0, 0);
			BlockPos south = hall.offset(0, 0, 40_000);
			Leftovers.after(helper, () -> {
				BuildSiteManager.get(level).forgetFinished(arenaAt);
				for (BlockPos v : List.of(hall, east, south)) {
					CupData.get(level).forget(v);
					CupData.get(level).takeNotes(v);
					routes.remove(v);
				}
				CupThemes.setForTest(themes);
				Cups.COBBLEMON = io.github.jcondedata.aliveworkplace.trainer.Trainers.COBBLEMON;
				Cups.ENABLED = true;
				CupDays.forget();
				Festivals.forget();
			});
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			routes.setWants(east, Component.literal("Eastholm"), List.of());
			routes.toggleRoute(hall, east, 10);
			routes.setLeader(east, new Caravans.Leader(UUID.nameUUIDFromBytes("QaDara".getBytes()), "Dara", 4, 300, true));
			routes.setWants(south, Component.literal("Southby"), List.of());
			routes.toggleRoute(hall, south, 10);
			routes.setLeader(south, new Caravans.Leader(UUID.nameUUIDFromBytes("QaRen".getBytes()), "Ren", 3, 100, true));
			villager(helper, new BlockPos(3, 2, 25), ModVillagers.TRAINER_LEADER, 4, "Mira");
			villager(helper, new BlockPos(4, 2, 25), ModVillagers.TRAINER, 2, "Pip");
			List<Villager> crowd = new ArrayList<>();
			for (int x = 12; x <= 14; x++) {
				crowd.add(villager(helper, new BlockPos(x, 2, 17), null, 1, null));
			}
			Cups.round(level, hall, entity);
			CupData.Cup cup = CupData.get(level).existing(hall);
			helper.assertTrue(cup != null && THEME.equals(cup.theme), "no Cup set");
			at(level, cup, 500);
			Cups.round(level, hall, entity);
			helper.assertTrue(cup.closed && cup.bracket.size() == 4, "not drawn: " + cup.noCup + " " + cup.bracket);

			// 999: nobody yet; 1000: both delegates
			at(level, cup, CupDays.ARRIVE - 1);
			CupDays.tick(level);
			helper.assertTrue(CupDays.delegates(level, hall).isEmpty(), "delegates before 1000: " + CupDays.delegates(level, hall).size());
			Arenas.Arena arena = Arenas.find(level, hall).orElseThrow();
			AABB fairBox = new AABB(arena.noticeBoard()).inflate(12);
			at(level, cup, CupDays.ARRIVE);
			CupDays.tick(level);
			List<Villager> delegates = CupDays.delegates(level, hall);
			helper.assertTrue(delegates.size() == 2, "delegates at 1000: " + delegates.size());
			// the fair: a market day's traders and two more
			int fair = level.getEntitiesOfClass(WanderingTrader.class, fairBox, WanderingTrader::isAlive).size();
			int marketDay = MarketDays.traders(level, hall);
			helper.assertTrue(fair == marketDay + 2, "the fair has " + fair + " traders, a market day " + marketDay);
			// the south delegate comes in from the south
			Villager ren = delegates.stream().filter(d -> CupDays.delegate(d).home().equals(south)).findFirst().orElseThrow();
			helper.assertTrue(ren.getZ() - hall.getZ() >= 7 && Math.abs(ren.getX() - hall.getX()) < 3, "Ren didn't come from the south: " + ren.blockPosition());

			// never a job or a bed
			Villager dara = delegates.stream().filter(d -> CupDays.delegate(d).home().equals(east)).findFirst().orElseThrow();
			GlobalPos somewhere = GlobalPos.of(level.dimension(), hall.offset(2, 0, 2));
			dara.getBrain().setMemory(MemoryModuleType.JOB_SITE, somewhere);
			dara.getBrain().setMemory(MemoryModuleType.HOME, somewhere);
			at(level, cup, CupDays.ARRIVE + 200);
			CupDays.tick(level);
			helper.assertFalse(dara.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE), "a delegate kept a job site");
			helper.assertFalse(dara.getBrain().hasMemoryValue(MemoryModuleType.HOME), "a delegate kept a bed");
			helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.TRAINER_LEADER, "a delegate changed job");
			// saved with the entity (a chunk unloading and loading mid-day)
			CompoundTag saved = dara.saveWithoutId(new CompoundTag());
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(saved);
			helper.assertTrue(CupDays.isDelegate(copy) && CupDays.delegate(copy).equals(CupDays.delegate(dara)), "the delegate isn't saved with the villager");
			helper.assertTrue(CupDays.delegates(level, hall).size() == 2, "a delegate came twice");

			// 5999: nobody seated; 6000: the stands; the theme's end: closed
			at(level, cup, CupDays.STANDS - 1);
			CupDays.tick(level);
			helper.assertFalse(CupDays.standsOpen(level, arena.ring()), "the stands are open before noon");
			helper.assertTrue(crowd.stream().noneMatch(Villager::isPassenger), "seated before noon");
			at(level, cup, CupDays.STANDS);
			helper.assertTrue(CupDays.standsOpen(level, arena.ring()), "the stands aren't open at noon");
			CupDays.tick(level);
			helper.assertTrue(crowd.stream().allMatch(Villager::isPassenger), "in the stands at noon: " + CupDays.seated(level, arena.ring()).size());
			at(level, cup, theme().end());
			helper.assertFalse(CupDays.standsOpen(level, arena.ring()), "the stands are open after the theme's end");

			// the Cup switched off mid-day: the delegates go home (nobody sees them, so they're gone)
			at(level, cup, CupDays.STANDS + 200);
			Cups.ENABLED = false;
			CupDays.tick(level);
			helper.assertTrue(CupDays.delegates(level, hall).isEmpty(), "delegates stayed with the Cup off: " + CupDays.delegates(level, hall).size());
			Cups.ENABLED = true;
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- players (no Cobblemon needed)

	private static CupData.Cup cup(GameTestHelper helper, BlockPos host, CupData.Entrant... entrants) {
		CupData.Cup cup = CupData.get(helper.getLevel()).cup(host);
		cup.theme = AliveWorkplace.id("grand_cup");
		cup.day = -1;
		cup.closed = true;
		cup.entrants.addAll(List.of(entrants));
		cup.bracket.addAll(List.of(entrants));
		Leftovers.after(helper, () -> CupData.get(helper.getLevel()).forget(host));
		return cup;
	}

	private static CupData.Entrant player(ServerPlayer p, BlockPos village) {
		return new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), p.getGameProfile().getName(), 0, 0, village);
	}

	private static CupData.Entrant trainer(String name, BlockPos village) {
		return new CupData.Entrant(CupData.Kind.HOST_TRAINER, UUID.nameUUIDFromBytes(("qa" + name).getBytes()), name, 3, 0, village);
	}

	/**
	 * Two players: one clicks [I'm ready] at the ring, the other never does. One tick before two minutes nothing is
	 * decided; at two minutes the absent one loses by walkover and the ready one goes on and is paid the purse (100,000,
	 * in emeralds at dollarsPerEmerald without CobbleDollars; a round-1 bout of four, not the final); the loser gets nothing.
	 */
	//$ gametest_batch SMALL '"qaCupPurse"'
	@GameTest(template = SMALL, batch = "qaCupPurse")
	public void aWalkoverWinPaysThePurseAndALossNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CupMatches.WINDOW == 2 * 60 * 20, "the window isn't two minutes: " + CupMatches.WINDOW);
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		ServerPlayer q = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		q.setGameMode(GameType.SURVIVAL);
		p.getInventory().clearContent();
		q.getInventory().clearContent();
		BlockPos host = helper.absolutePos(new BlockPos(1, 1, 1));
		// four entrants, so this round-1 bout isn't the final (a two-entrant bracket's only bout is, and pays the final's purse)
		CupData.Cup cup = cup(helper, host, player(p, host), player(q, host), trainer("Mira", host), trainer("Kai", host));
		BlockPos ring = p.blockPosition();
		CupMatches.Call call = CupMatches.call(level, host, cup, new CupBouts.Pairing(1, cup.bracket.get(0), cup.bracket.get(1)), ring, ring.west(8), ring.east(8));
		String said = CupMatches.ready(p).getString();
		helper.assertTrue(said.equals("You're ready. Waiting for your opponent.") && call.ready[0] && !call.ready[1], "ready: " + said);
		call.calledAt = level.getGameTime() - CupMatches.WINDOW + 1;
		CupMatches.tick(level, host, cup);
		helper.assertTrue(cup.call != null && cup.results.isEmpty(), "decided a tick before two minutes: " + cup.results);
		call.calledAt = level.getGameTime() - CupMatches.WINDOW;
		CupMatches.tick(level, host, cup);
		helper.assertTrue(cup.call == null && cup.results.size() == 1 && cup.results.get(0).winner().equals(p.getUUID())
			&& cup.results.get(0).loser().equals(q.getUUID()), "the ready player didn't go on: " + cup.results);
		int want = (int) (CupMatches.PURSE / Math.max(1, Money.DOLLARS_PER_EMERALD));
		int got = p.getInventory().countItem(Items.EMERALD)
			+ level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(4), e -> e.getItem().is(Items.EMERALD))
				.stream().mapToInt(e -> e.getItem().getCount()).sum();
		helper.assertTrue(CupMatches.PURSE == 100_000 && got == want, "the winner was paid " + got + " emeralds, not " + want);
		helper.assertTrue(q.getInventory().countItem(Items.EMERALD) == 0, "the loser was paid");
		helper.succeed();
	}

	/** A City host pays half as much again (150,000 a bout, 900,000 with the final); a Town, one rank short, the plain purse. */
	//$ gametest_batch SMALL '"qaCupCity"'
	@GameTest(template = SMALL, batch = "qaCupCity")
	public void aCityHostPaysHalfAsMuchAgain(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos at = new BlockPos(2, 2, 2);
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer p = helper.makeMockServerPlayerInLevel();
			BlockPos host = helper.absolutePos(at);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(at);
			CupData.Entrant mira = trainer("Mira", host);
			CupData.Entrant dara = trainer("Dara", host);
			CupData.Entrant kai = trainer("Kai", host);
			CupData.Cup cup = cup(helper, host, player(p, host), mira, dara, kai);
			cup.results.add(new CupData.Result(1, p.getUUID(), mira.id()));
			entity.setRank(VillageRanks.Rank.CITY);
			helper.assertTrue(CupMatches.purse(level, host, cup, p.getUUID()) == 150_000, "a City bout: " + CupMatches.purse(level, host, cup, p.getUUID()));
			entity.setRank(VillageRanks.Rank.TOWN);
			helper.assertTrue(CupMatches.purse(level, host, cup, p.getUUID()) == 100_000, "a Town bout: " + CupMatches.purse(level, host, cup, p.getUUID()));
			cup.results.add(new CupData.Result(1, dara.id(), kai.id()));
			cup.results.add(new CupData.Result(2, p.getUUID(), dara.id()));
			helper.assertTrue(CupMatches.purse(level, host, cup, p.getUUID()) == 600_000, "a Town final: " + CupMatches.purse(level, host, cup, p.getUUID()));
			entity.setRank(VillageRanks.Rank.CITY);
			helper.assertTrue(CupMatches.purse(level, host, cup, p.getUUID()) == 900_000, "a City final: " + CupMatches.purse(level, host, cup, p.getUUID()));
			helper.succeed();
		});
	}

	/** A player clicking [I'm ready] or [Watch] with nothing on, and every result line a player reads, exactly as written. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void playersCupSentences(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(CupMatches.ready(p).getString().equals("None of your Cup bouts is being called right now."), "no call: " + CupMatches.ready(p).getString());
		helper.assertTrue(CupMatches.watch(p).getString().equals("No player bout is being fought right now."), "nothing to watch: " + CupMatches.watch(p).getString());
		Map<String, Component> lines = new LinkedHashMap<>();
		lines.put("Festival Cup, round 2: your bout against Dara of Eastholm is next! Come to the Arena of Thornholm (10, 64, -5) and click within 2 minutes, or lose by walkover.",
			Component.translatable("message.aliveworkplace.cup.call", 2, "Dara", "Eastholm", "Thornholm", "10, 64, -5", 2));
		lines.put("Come inside the Arena of Thornholm first, then click [I'm ready].", Component.translatable("message.aliveworkplace.cup.come_inside", "Thornholm"));
		lines.put("You're ready. The bout begins!", Component.translatable("message.aliveworkplace.cup.ready_go"));
		lines.put("Alex of Thornholm beat Dara of Eastholm!", Component.translatable("message.aliveworkplace.cup.match_won", "Alex", "Thornholm", "Dara", "Eastholm"));
		lines.put("Alex of Thornholm fled the battle: Dara of Eastholm goes on",
			Component.translatable("message.aliveworkplace.cup.match_fled", "Dara", "Eastholm", "Alex", "Thornholm"));
		lines.put("Dara of Eastholm goes on by walkover: Alex wasn't ready in time",
			Component.translatable("message.aliveworkplace.cup.match_absent", "Dara", "Eastholm", "Alex", "Thornholm"));
		lines.put("Dara of Eastholm goes on by walkover: Alex had no Pokémon that may fight",
			Component.translatable("message.aliveworkplace.cup.match_no_team", "Dara", "Eastholm", "Alex", "Thornholm"));
		lines.put("2 of your Pokémon can't fight under this Cup's rules:", Component.translatable("message.aliveworkplace.cup.left_out", 2));
		lines.put("Your team: Pikachu, Eevee. Everyone fights healed, at the Cup's level, and nobody gains experience or keeps damage.",
			Component.translatable("message.aliveworkplace.cup.going", "Pikachu, Eevee"));
		lines.put("The Cup's purse for your win: 1000 emeralds",
			Component.translatable("message.aliveworkplace.cup.purse", Component.translatable("message.aliveworkplace.money.emeralds", 1000)));
		lines.put("Round 1 of the Grand Cup: Alex of Thornholm against Dara of Eastholm", Component.translatable("message.aliveworkplace.cup.bout_start", 1,
			Component.translatable("cup.aliveworkplace.grand_cup"), "Alex", "Thornholm", "Dara", "Eastholm"));
		lines.put("[I'm ready]", Component.translatable("message.aliveworkplace.cup.ready_button"));
		lines.put("[Watch]", Component.translatable("message.aliveworkplace.cup.watch_button"));
		lines.forEach((want, c) -> helper.assertTrue(c.getString().equals(want), "\"" + c.getString() + "\" isn't \"" + want + "\""));
		helper.succeed();
	}
}
