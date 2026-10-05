package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.cup.CupBout;
import io.github.jcondedata.aliveworkplace.cup.CupBouts;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.Cups;
import io.github.jcondedata.aliveworkplace.cup.TypeChart;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * The Festival Cup's bouts between villagers (ROADMAP 28.18), the parts that need no Cobblemon: the type chart (all 18
 * types, Water on Fire 2x, Normal on Ghost 0x), the same seed playing the same bout (also when saved halfway), the
 * bracket's rounds, a bout at the ring ending with a winner and trainer XP for both (banked for one who isn't there),
 * banked XP reaching the real Leader when its village loads, and the bouts stopping with {@code festivalCup} off. The
 * real Pokémon at the ring and the themed teams: {@code CupBoutCompatTests}.
 */
public class CupBoutGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	// ---------------------------------------------------------------- helpers

	private static CupBout.Move move(String id, String type, int power, boolean physical) {
		return new CupBout.Move(id, type, power, 100, physical);
	}

	/** Two small teams: a Fire and a Grass side, so the chart matters. */
	static List<CupBout.Fighter> fire() {
		return List.of(
			new CupBout.Fighter("cobblemon:charmeleon", "cobblemon.species.charmeleon.name", List.of("fire"), 30, 90, 60, 55, 75, 62, 77,
				List.of(move("flamethrower", "fire", 90, false), move("slash", "normal", 70, true))),
			new CupBout.Fighter("cobblemon:growlithe", "cobblemon.species.growlithe.name", List.of("fire"), 30, 95, 70, 48, 65, 50, 60,
				List.of(move("firefang", "fire", 65, true), move("bite", "dark", 60, true))));
	}

	static List<CupBout.Fighter> grass() {
		return List.of(
			new CupBout.Fighter("cobblemon:ivysaur", "cobblemon.species.ivysaur.name", List.of("grass", "poison"), 30, 92, 58, 60, 74, 74, 58,
				List.of(move("razorleaf", "grass", 55, true), move("sludgebomb", "poison", 90, false))),
			new CupBout.Fighter("cobblemon:wartortle", "cobblemon.species.wartortle.name", List.of("water"), 30, 93, 58, 74, 60, 75, 54,
				List.of(move("watergun", "water", 40, false), move("bite", "dark", 60, true))));
	}

	private static Villager trainer(GameTestHelper helper, BlockPos at, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(2));
		v.setCustomName(Component.literal(name));
		return v;
	}

	// ---------------------------------------------------------------- the type chart

	/** The chart ships with all 18 types; Water on Fire is 2x, Normal on Ghost 0x, two types multiply; a file lacking a type is refused. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theTypeChartIsSane(GameTestHelper helper) {
		helper.assertTrue(TypeChart.loadedTypes().size() == 18 && TypeChart.loadedTypes().containsAll(TypeChart.TYPES),
			"the chart has " + TypeChart.loadedTypes());
		helper.assertTrue(TypeChart.multiplier("water", "fire") == 2, "Water on Fire: " + TypeChart.multiplier("water", "fire"));
		helper.assertTrue(TypeChart.multiplier("normal", "ghost") == 0, "Normal on Ghost: " + TypeChart.multiplier("normal", "ghost"));
		helper.assertTrue(TypeChart.multiplier("fire", "water") == 0.5, "Fire on Water: " + TypeChart.multiplier("fire", "water"));
		helper.assertTrue(TypeChart.multiplier("ground", "flying") == 0, "Ground on Flying: " + TypeChart.multiplier("ground", "flying"));
		helper.assertTrue(TypeChart.multiplier("normal", "normal") == 1, "Normal on Normal: " + TypeChart.multiplier("normal", "normal"));
		helper.assertTrue(TypeChart.multiplier("electric", List.of("water", "flying")) == 4, "Electric on Water/Flying: "
			+ TypeChart.multiplier("electric", List.of("water", "flying")));
		helper.assertTrue(TypeChart.multiplier("dragon", "fairy") == 0 && TypeChart.multiplier("fairy", "dragon") == 2, "Dragon and Fairy");
		boolean refused = false;
		try {
			TypeChart.read(JsonParser.parseString("{\"chart\": {\"water\": {\"fire\": 2}}}").getAsJsonObject());
		} catch (IllegalArgumentException e) {
			refused = e.getMessage().contains("missing type");
		}
		helper.assertTrue(refused, "a chart without all 18 types is read");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the same seed, the same bout

	/** The same seed plays the same bout move for move; another seed doesn't; a bout saved halfway carries on exactly the same. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theSameSeedPlaysTheSameBout(GameTestHelper helper) {
		UUID a = UUID.nameUUIDFromBytes("mira".getBytes());
		UUID b = UUID.nameUUIDFromBytes("dara".getBytes());
		long seed = CupBout.seed(BlockPos.ZERO, 12, 1, a, b);
		helper.assertTrue(seed == CupBout.seed(BlockPos.ZERO, 12, 1, a, b) && seed != CupBout.seed(BlockPos.ZERO, 12, 2, a, b), "the seed isn't the bout's");
		CupBout first = new CupBout(a, b, fire(), grass(), 1, seed, BlockPos.ZERO, BlockPos.ZERO.west(10), BlockPos.ZERO.east(10));
		List<CupBout.Action> log = first.playOut();
		CupBout again = new CupBout(a, b, fire(), grass(), 1, seed, BlockPos.ZERO, BlockPos.ZERO.west(10), BlockPos.ZERO.east(10));
		helper.assertTrue(log.equals(again.playOut()) && first.winner == again.winner, "the same seed played another bout");
		helper.assertTrue(first.over() && !log.isEmpty() && (first.winner == 0 || first.winner == 1), "no winner: " + first.winner);
		helper.assertTrue(first.ticks < CupBout.MAX_TICKS || first.onTime, "it ran past 90 seconds");
		boolean differs = false;
		for (long other = seed + 1; other < seed + 6 && !differs; other++) {
			differs = !log.equals(new CupBout(a, b, fire(), grass(), 1, other, BlockPos.ZERO, BlockPos.ZERO.west(10), BlockPos.ZERO.east(10)).playOut());
		}
		helper.assertTrue(differs, "five other seeds all played the same bout");
		// every move: damage by the formula, 0 only for a miss or a 0x match-up, the chart's multiplier
		for (CupBout.Action act : log) {
			helper.assertTrue(act.hit() && act.effectiveness() > 0 ? act.damage() >= 1 : act.damage() == 0, "a move did " + act.damage());
		}
		// saved halfway (with the Cup, as the world saves it), then on to the end: the same bout
		CupBout half = new CupBout(a, b, fire(), grass(), 1, seed, BlockPos.ZERO, BlockPos.ZERO.west(10), BlockPos.ZERO.east(10));
		List<CupBout.Action> before = new ArrayList<>();
		while (before.size() < log.size() / 2) {
			CupBout.Action act = half.tick();
			if (act != null) {
				before.add(act);
			}
		}
		CupData data = new CupData();
		data.cup(BlockPos.ZERO).bout = half;
		ServerLevel level = helper.getLevel();
		CupBout loaded = CupData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess()).cup(BlockPos.ZERO).bout;
		helper.assertTrue(loaded != null && loaded.ticks == half.ticks && loaded.step == half.step, "the bout isn't saved halfway");
		before.addAll(loaded.playOut());
		helper.assertTrue(before.equals(log) && loaded.winner == first.winner, "a bout saved halfway played on differently");
		// a side with no Pokémon loses at once
		CupBout empty = new CupBout(a, b, List.of(), grass(), 1, seed, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO);
		helper.assertTrue(empty.over() && empty.winner == 1 && empty.walkover(), "a side with no team doesn't lose by walkover");
		helper.succeed();
	}

	/** The damage formula: the same-type bonus, the chart (2x, 0.5x, 0x) and the level all count. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theDamageFormulaCountsTypesAndLevel(GameTestHelper helper) {
		CupBout.Fighter wartortle = grass().get(1);
		CupBout.Fighter charmeleon = fire().get(0);
		CupBout.Move water = new CupBout.Move("surf", "water", 90, 100, false);
		java.util.function.DoubleFunction<Integer> hit = m -> CupBout.damage(wartortle, charmeleon, water, m, new java.util.Random(1));
		int normal = hit.apply(1);
		helper.assertTrue(hit.apply(2) >= 2 * normal - 1 && hit.apply(0.5) <= normal / 2 + 1 && hit.apply(0) == 0,
			"1x " + normal + ", 2x " + hit.apply(2) + ", 0.5x " + hit.apply(0.5) + ", 0x " + hit.apply(0));
		CupBout.Move tackle = new CupBout.Move("tackle", "normal", 90, 100, false);
		int offType = CupBout.damage(wartortle, charmeleon, tackle, 1, new java.util.Random(1));
		helper.assertTrue(normal > offType, "no same-type bonus: " + normal + " vs " + offType);
		CupBout.Fighter older = new CupBout.Fighter(wartortle.species(), wartortle.nameKey(), wartortle.types(), 60, wartortle.hp(), wartortle.attack(),
			wartortle.defence(), wartortle.spAttack(), wartortle.spDefence(), wartortle.speed(), wartortle.moves());
		helper.assertTrue(CupBout.damage(older, charmeleon, water, 1, new java.util.Random(1)) > normal, "the level doesn't count");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the bracket

	/** Round 1's bouts in order, the next round only when the last is done, byes go through, a player's bout waits, then a champion. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBracketGoesRoundByRound(GameTestHelper helper) {
		List<CupData.Entrant> e = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			e.add(new CupData.Entrant(CupData.Kind.TRAINER, UUID.nameUUIDFromBytes(("t" + i).getBytes()), "T" + i, 5 - i, 0, BlockPos.ZERO));
		}
		CupData.Cup cup = new CupData.Cup();
		cup.bracket.addAll(Cups.bracket(e, 4)); // 1 v 4, 2 v 3
		CupBouts.Pairing p = CupBouts.next(cup);
		helper.assertTrue(p != null && p.round() == 1 && p.a() == e.get(0) && p.b() == e.get(3), "first: " + p);
		cup.results.add(new CupData.Result(1, e.get(3).id(), e.get(0).id())); // the 4th seed wins
		p = CupBouts.next(cup);
		helper.assertTrue(p != null && p.round() == 1 && p.a() == e.get(1) && p.b() == e.get(2), "second: " + p);
		helper.assertTrue(CupBouts.champion(cup) == null, "a champion before the final");
		cup.results.add(new CupData.Result(1, e.get(1).id(), e.get(2).id()));
		p = CupBouts.next(cup);
		helper.assertTrue(p != null && p.round() == 2 && p.a() == e.get(3) && p.b() == e.get(1), "the final: " + p);
		cup.results.add(new CupData.Result(2, e.get(1).id(), e.get(3).id()));
		helper.assertTrue(CupBouts.next(cup) == null && CupBouts.champion(cup) == e.get(1), "after the final: " + CupBouts.next(cup));
		// three entrants: seed 1 has a bye; a bout with a player waits (28.20) while the villagers' one goes on
		CupData.Cup three = new CupData.Cup();
		CupData.Entrant player = new CupData.Entrant(CupData.Kind.PLAYER, UUID.nameUUIDFromBytes("p".getBytes()), "Alex", 0, 0, BlockPos.ZERO);
		three.bracket.addAll(java.util.Arrays.asList(e.get(0), null, e.get(1), player));
		helper.assertTrue(CupBouts.next(three) == null, "a player's bout started without them: " + CupBouts.next(three));
		three.results.add(new CupData.Result(1, e.get(1).id(), player.id()));
		p = CupBouts.next(three);
		helper.assertTrue(p != null && p.round() == 2 && p.a() == e.get(0) && p.b() == e.get(1), "after the bye: " + p);
		helper.succeed();
	}

	// ---------------------------------------------------------------- a bout at the ring

	/** A bout between two trainers at the ring plays out to a winner; both get trainer XP (the winner more), one away has it banked. */
	//$ gametest_ticks_batch AREA '2400' '"cupBoutRing"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "cupBoutRing")
	public void aBoutEndsWithAWinnerAndBothGetXp(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos host = helper.absolutePos(HALL);
		BlockPos far = host.offset(40_000, 0, 40_000);
		Leftovers.after(helper, () -> {
			CupData.get(level).forget(host);
			Caravans.Data.get(level).remove(far);
		});
		Villager mira = trainer(helper, new BlockPos(5, 2, 10), "Mira");
		UUID dara = UUID.nameUUIDFromBytes("dara-away".getBytes()); // a far village's Leader, not here
		CupData.Cup cup = CupData.get(level).cup(host);
		cup.entrants.add(new CupData.Entrant(CupData.Kind.HOST_TRAINER, mira.getUUID(), "Mira", 2, 0, host));
		cup.entrants.add(new CupData.Entrant(CupData.Kind.LEADER, dara, "Dara", 4, 300, far));
		int xpBefore = mira.getVillagerXp();
		BlockPos ring = helper.absolutePos(new BlockPos(15, 2, 10));
		CupBout bout = new CupBout(mira.getUUID(), dara, fire(), grass(), 1, CupBout.seed(host, 3, 1, mira.getUUID(), dara), ring, ring.west(10), ring.east(10));
		CupBout replay = new CupBout(mira.getUUID(), dara, fire(), grass(), 1, bout.seed, ring, ring.west(10), ring.east(10));
		replay.playOut();
		CupBouts.begin(level, host, cup, bout);
		helper.assertTrue(mira.blockPosition().closerThan(ring.west(10), 1.5), "Mira isn't in her box: " + mira.blockPosition());
		helper.succeedWhen(() -> {
			helper.assertTrue(cup.bout == null, "still fighting: tick " + bout.ticks + " step " + bout.step);
			helper.assertTrue(cup.results.size() == 1 && cup.results.get(0).round() == 1, "results " + cup.results);
			CupData.Result r = cup.results.get(0);
			UUID winner = replay.winner == 0 ? mira.getUUID() : dara;
			helper.assertTrue(r.winner().equals(winner), "the ring's bout isn't the seed's: " + r);
			helper.assertTrue(mira.getVillagerXp() > xpBefore, "Mira got no trainer XP");
			int banked = Caravans.Data.get(level).banked(far).getOrDefault(dara, 0);
			int expected = Trainers.XP_PER_BATTLE + (winner.equals(dara) ? Trainers.XP_FOR_WIN : 0);
			helper.assertTrue(banked == expected, "Dara's banked XP " + banked + ", expected " + expected);
		});
	}

	/** With {@code festivalCup} off, a bout in the ring stops at once without a result. */
	//$ gametest_ticks_batch AREA '200' '"cupBoutOff"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "cupBoutOff")
	public void theBoutStopsWithTheCupOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos host = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			Cups.ENABLED = true;
			CupData.get(level).forget(host);
		});
		UUID a = UUID.nameUUIDFromBytes("off-a".getBytes());
		UUID b = UUID.nameUUIDFromBytes("off-b".getBytes());
		CupData.Cup cup = CupData.get(level).cup(host);
		BlockPos ring = helper.absolutePos(new BlockPos(15, 2, 10));
		CupBouts.begin(level, host, cup, new CupBout(a, b, fire(), grass(), 1, 7, ring, ring.west(10), ring.east(10)));
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(cup.bout != null && cup.bout.ticks > 0, "the bout isn't running");
			Cups.ENABLED = false;
		});
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(cup.bout == null && cup.results.isEmpty(), "the bout goes on with the Cup off: " + cup.results);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- banked XP

	/** A delegate's banked XP is saved with the caravans' list and given to the real Leader when the village's hall next runs its round. */
	//$ gametest_ticks_batch AREA '200' '"cupBanked"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "cupBanked")
	public void bankedXpReachesTheLeaderOnLoad(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> Caravans.Data.get(helper.getLevel()).remove(helper.absolutePos(HALL)));
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			Caravans.Data data = Caravans.Data.get(level);
			data.setWants(hall, Component.literal("Ashford"), List.of());
			UUID leaderId = UUID.nameUUIDFromBytes("banked-leader".getBytes());
			data.bankXp(hall, leaderId, Trainers.XP_PER_BATTLE + Trainers.XP_FOR_WIN);
			data.bankXp(hall, leaderId, Trainers.XP_PER_BATTLE);
			int total = 2 * Trainers.XP_PER_BATTLE + Trainers.XP_FOR_WIN;
			Map<UUID, Integer> saved = Caravans.Data.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess()).banked(hall);
			helper.assertTrue(saved.getOrDefault(leaderId, 0) == total, "banked XP isn't saved: " + saved);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
			Cups.round(level, hall, entity); // the Leader isn't home: it stays banked
			helper.assertTrue(data.banked(hall).getOrDefault(leaderId, 0) == total, "paid with nobody to pay: " + data.banked(hall));
			// the village loads with its Leader in it
			Villager leader = EntityType.VILLAGER.create(level);
			leader.setUUID(leaderId);
			leader.setNoAi(true);
			leader.setVillagerData(leader.getVillagerData().setProfession(ModVillagers.TRAINER_LEADER).setLevel(4));
			leader.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(12.5, 2, 12.5)));
			level.addFreshEntity(leader);
			int before = leader.getVillagerXp();
			Cups.round(level, hall, entity);
			helper.assertTrue(leader.getVillagerXp() > before, "the Leader didn't get the banked XP");
			helper.assertTrue(data.banked(hall).isEmpty(), "still banked: " + data.banked(hall));
			leader.discard();
			helper.succeed();
		});
	}
}
