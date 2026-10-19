package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonMegas;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
import io.github.jcondedata.aliveworkplace.cup.CupBattlers;
import io.github.jcondedata.aliveworkplace.cup.CupBout;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.MegaBracelets;
import io.github.jcondedata.aliveworkplace.wonder.StadiumDays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * ROADMAP 38.5 with Mega Showdown: a City's Trainer Leader, the Festival Cup's finalists and the Stadium Day's visitors
 * carry a Mega Stone as Masters do, a Town's Leader doesn't, and the Mega Bracelet is on exactly the trainers whose team
 * holds one. {@code villageMegas} off: only Masters, no bracelets. A Leader saved and loaded again is the same. Without
 * Mega Showdown: {@code MegaTrainerGameTests}. Each test runs alone, synchronously, and puts back what it changed.
 */
public class MegaTrainerCompatTests implements FabricGameTest {
	static final BlockPos HALL = new BlockPos(1, 2, 1);

	/** An open final: any type, fully evolved, three each at level 50. */
	static CupThemes.Theme openTheme() {
		return CupThemes.read(AliveWorkplace.id("test_open_cup"), JsonParser.parseString("""
			{"name": "cup.aliveworkplace.grand_cup", "order": 97, "level": 50, "bring": 3, "stage": "final",
			 "banned": ["legendary", "mythical", "ultra_beast", "paradox"], "dish": "minecraft:bread", "fireworks": ["#FF0000"],
			 "disc": "minecraft:music_disc_cat"}""").getAsJsonObject());
	}

	/** A hall of this rank (the only one within reach while the test runs: the reach is 8 till {@code done}). */
	static VillageHallBlockEntity hall(GameTestHelper helper, VillageRanks.Rank rank) {
		RaidHelperCompatTests.clearAround(helper);
		VillageHalls.RADIUS = 8;
		VillageHalls.forgetNearest();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		hall.setRank(rank);
		return hall;
	}

	static void done(GameTestHelper helper) {
		VillageHalls.RADIUS = 64;
		VillageHalls.forgetNearest();
		MegaBracelets.ENABLED = true;
		CupData.get(helper.getLevel()).forget(helper.absolutePos(HALL));
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds())) {
			v.discard();
		}
	}

	static Villager villager(GameTestHelper helper, BlockPos at, VillagerProfession job, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
		v.setNoAi(true);
		return v;
	}

	static long stones(List<Pokemon> team) {
		return team.stream().filter(CobblemonMegas::holdsItsStone).count();
	}

	static String show(List<Pokemon> team) {
		return team.stream().map(p -> p.getSpecies().getName() + "@" + p.heldItem().getItem()).toList().toString();
	}

	/** A City's Trainer Leader holds exactly one Mega Stone and wears the bracelet; the same Leader in a Town holds none. */
	//$ gametest_batch 'CompatGameTests.AREA' '"megaCityLeader"'
	@GameTest(template = CompatGameTests.AREA, batch = "megaCityLeader")
	public void aCityLeaderMegaEvolvesATownLeaderDoesNot(GameTestHelper helper) {
		try {
			helper.assertTrue(MegaBracelets.MOD && MegaBracelets.extraMegas(), "Mega Showdown or villageMegas missing");
			VillageHallBlockEntity hall = hall(helper, VillageRanks.Rank.CITY);
			Villager leader = villager(helper, new BlockPos(3, 2, 3), ModVillagers.TRAINER_LEADER, 1);
			List<Pokemon> city = CobblemonTrainers.teamOf(leader);
			helper.assertTrue(stones(city) == 1, "a City Leader's team " + show(city));
			helper.assertTrue(MegaBracelets.wears(leader), "a City Leader without a bracelet");
			hall.setRank(VillageRanks.Rank.TOWN);
			VillageHalls.forgetNearest();
			List<Pokemon> town = CobblemonTrainers.teamOf(leader);
			helper.assertTrue(stones(town) == 0, "a Town Leader's team " + show(town));
			helper.assertTrue(!MegaBracelets.wears(leader), "a Town Leader wears a bracelet");
		} finally {
			done(helper);
		}
		helper.succeed();
	}

	/** The Cup final's trainers each hold one Mega Stone (as fighters too), the same team every time; an earlier round's none. */
	//$ gametest_batch 'CompatGameTests.AREA' '"megaCupFinal"'
	@GameTest(template = CompatGameTests.AREA, batch = "megaCupFinal")
	public void theCupFinalsTrainersHoldAMegaStone(GameTestHelper helper) {
		CupThemes.Theme theme = openTheme();
		for (int i = 0; i < 6; i++) {
			UUID trainer = new UUID(0x38050000L + i * 7919L, 0xf1a1L + i * 104729L);
			int tier = 1 + i % 4;
			List<Pokemon> fin = CobblemonTrainers.team(trainer, tier, theme, true);
			helper.assertTrue(stones(fin) == 1 && fin.size() == 3, "a finalist's team " + show(fin));
			helper.assertTrue(fin.stream().allMatch(p -> p.getLevel() == 50 && p.getSpecies().getEvolutions().isEmpty()), "the Mega breaks the theme " + show(fin));
			helper.assertTrue(show(CobblemonTrainers.team(trainer, tier, theme, true)).equals(show(fin)), "a finalist's team changed");
			helper.assertTrue(stones(CobblemonTrainers.team(trainer, tier, theme)) == 0, "an earlier round with a Mega Stone");
			List<CupBout.Fighter> fighters = CupBattlers.EXTENSION.call(b -> b.team(trainer, tier, theme, true), List.of());
			helper.assertTrue(fighters.size() == 3, "the final's fighters " + fighters);
		}
		try {
			hall(helper, VillageRanks.Rank.HAMLET);
			Villager a = villager(helper, new BlockPos(3, 2, 3), ModVillagers.TRAINER, 2);
			Villager b = villager(helper, new BlockPos(5, 2, 3), ModVillagers.TRAINER, 3);
			helper.assertTrue(!MegaBracelets.wears(a), "a trainer wears a bracelet before the Cup");
			finalCup(helper.getLevel(), helper.absolutePos(HALL), a, b);
			for (Villager v : List.of(a, b)) {
				helper.assertTrue(MegaBracelets.canMegaEvolve(v) && MegaBracelets.wears(v), "a finalist can't Mega Evolve");
				helper.assertTrue(stones(CobblemonTrainers.teamOf(v)) == 1, "a finalist's own team " + show(CobblemonTrainers.teamOf(v)));
			}
		} finally {
			done(helper);
		}
		helper.succeed();
	}

	/** The bracelet is on exactly the trainers whose team holds a Mega Stone, and the server tells the clients so. */
	//$ gametest_batch 'CompatGameTests.AREA' '"megaBracelets"'
	@GameTest(template = CompatGameTests.AREA, batch = "megaBracelets")
	public void theBraceletIsOnExactlyTheMegaTrainers(GameTestHelper helper) {
		try {
			hall(helper, VillageRanks.Rank.CITY);
			ServerLevel level = helper.getLevel();
			Map<Villager, Boolean> expect = new java.util.LinkedHashMap<>();
			expect.put(villager(helper, new BlockPos(2, 2, 3), ModVillagers.TRAINER_LEADER, 1), true);   // a City's Leader
			expect.put(villager(helper, new BlockPos(4, 2, 3), ModVillagers.TRAINER, 5), true);          // a Master
			expect.put(villager(helper, new BlockPos(6, 2, 3), ModVillagers.TRAINER, 4), false);         // an Expert
			expect.put(villager(helper, new BlockPos(2, 2, 5), ModVillagers.TRAINER, 1), false);         // a Novice
			Villager visitor = villager(helper, new BlockPos(4, 2, 5), ModVillagers.TRAINER, 3);         // a Stadium visitor
			ModAttachments.STADIUM_VISITOR.set(visitor, new StadiumDays.Visitor(helper.absolutePos(HALL), UUID.randomUUID(), Optional.empty(),
				new UUID(0, 0), 0, false, 0));
			expect.put(visitor, true);
			expect.put(villager(helper, new BlockPos(6, 2, 5), VillagerProfession.FARMER, 5), false);    // not a trainer
			List<String> wrong = new ArrayList<>();
			for (Map.Entry<Villager, Boolean> e : expect.entrySet()) {
				Villager v = e.getKey();
				boolean wears = MegaBracelets.wears(v);
				MegaBracelets.refresh(v);
				String who = v.getVillagerData().getProfession() + " " + v.getVillagerData().getLevel() + (StadiumDays.isVisitor(v) ? " visitor" : "");
				if (wears != e.getValue() || MegaBracelets.told(v) != wears) {
					wrong.add(who + " wears " + wears + ", told " + MegaBracelets.told(v));
				}
				if (io.github.jcondedata.aliveworkplace.trainer.Trainers.isTrainer(v) && (stones(CobblemonTrainers.teamOf(v)) == 1) != wears) {
					wrong.add(who + ": bracelet " + wears + ", team " + show(CobblemonTrainers.teamOf(v)));
				}
			}
			helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
			helper.assertTrue(level.getEntitiesOfClass(Villager.class, helper.getBounds()).size() >= expect.size(), "a villager went missing");
		} finally {
			done(helper);
		}
		helper.succeed();
	}

	/** {@code villageMegas} off: a City's Leader and a visitor carry no Mega Stone; a Master still does; nobody wears a bracelet. */
	//$ gametest_batch 'CompatGameTests.AREA' '"megaSwitchOff"'
	@GameTest(template = CompatGameTests.AREA, batch = "megaSwitchOff")
	public void switchedOffOnlyMastersMegaEvolve(GameTestHelper helper) {
		try {
			hall(helper, VillageRanks.Rank.CITY);
			Villager leader = villager(helper, new BlockPos(3, 2, 3), ModVillagers.TRAINER_LEADER, 1);
			Villager master = villager(helper, new BlockPos(5, 2, 3), ModVillagers.TRAINER, 5);
			MegaBracelets.refresh(leader);
			helper.assertTrue(MegaBracelets.told(leader), "a City Leader's clients weren't told of the bracelet");
			MegaBracelets.ENABLED = false;
			helper.assertTrue(stones(CobblemonTrainers.teamOf(leader)) == 0, "switched off, a City Leader's team " + show(CobblemonTrainers.teamOf(leader)));
			helper.assertTrue(stones(CobblemonTrainers.teamOf(master)) == 1, "switched off, a Master's team " + show(CobblemonTrainers.teamOf(master)));
			helper.assertTrue(stones(CobblemonTrainers.team(new UUID(1, 38), 3, openTheme(), false)) == 0, "a Cup team with a stone");
			for (Villager v : List.of(leader, master)) {
				helper.assertTrue(!MegaBracelets.wears(v), "switched off, a bracelet");
				for (int i = 0; i < 40; i++) { // a whole check round: the clients are told it's gone
					v.tickCount++;
					MegaBracelets.tick(v);
				}
				helper.assertTrue(!MegaBracelets.told(v), "switched off, the clients still think there's a bracelet");
			}
		} finally {
			done(helper);
		}
		helper.succeed();
	}

	/** A City's Leader saved and loaded again still Mega Evolves, with the same team and the same Mega Stone. */
	//$ gametest_batch 'CompatGameTests.AREA' '"megaSaveLoad"'
	@GameTest(template = CompatGameTests.AREA, batch = "megaSaveLoad")
	public void aLeaderSavedAndLoadedIsTheSame(GameTestHelper helper) {
		try {
			hall(helper, VillageRanks.Rank.CITY);
			Villager leader = villager(helper, new BlockPos(3, 2, 3), ModVillagers.TRAINER_LEADER, 1);
			String before = show(CobblemonTrainers.teamOf(leader));
			CompoundTag saved = new CompoundTag();
			leader.save(saved);
			leader.discard();
			Villager loaded = (Villager) EntityType.loadEntityRecursive(saved, helper.getLevel(), e -> e);
			helper.assertTrue(loaded != null && helper.getLevel().addFreshEntity(loaded), "the Leader didn't load");
			helper.assertTrue(MegaBracelets.wears(loaded), "the loaded Leader has no bracelet");
			helper.assertTrue(before.equals(show(CobblemonTrainers.teamOf(loaded))), "the team changed: " + before + " to " + show(CobblemonTrainers.teamOf(loaded)));
			helper.assertTrue(stones(CobblemonTrainers.teamOf(loaded)) == 1, "the loaded Leader's team " + show(CobblemonTrainers.teamOf(loaded)));
		} finally {
			done(helper);
		}
		helper.succeed();
	}

	/** A Cup at {@code host} today, closed, with these two villagers in its bracket: the final is next. */
	static void finalCup(ServerLevel level, BlockPos host, Villager... trainers) {
		CupData.Cup cup = CupData.get(level).cup(host);
		cup.closed = true;
		cup.noCup = "";
		cup.day = Chronicle.day(level);
		for (Villager v : trainers) {
			CupData.Entrant e = new CupData.Entrant(CupData.Kind.HOST_TRAINER, v.getUUID(), "", v.getVillagerData().getLevel(), 0, host);
			cup.entrants.add(e);
			cup.bracket.add(e);
		}
	}
}
