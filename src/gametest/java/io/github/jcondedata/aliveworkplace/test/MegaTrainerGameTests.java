package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupDays;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.MegaBracelets;
import io.github.jcondedata.aliveworkplace.wonder.StadiumDays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * ROADMAP 38.5 without Mega Showdown (the main suite never loads it): nothing changes. A City's Trainer Leader, a Master,
 * a Stadium visitor and a Cup finalist carry no extra Mega Stone and wear no bracelet, and no client is told anything.
 * Which villagers fight in a Cup final is known here too. The teams and the bracelets with the mod:
 * {@code MegaTrainerCompatTests}.
 */
public class MegaTrainerGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:build_area";
	static final BlockPos HALL = new BlockPos(1, 2, 1);

	static Villager villager(GameTestHelper helper, BlockPos at, VillagerProfession job, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
		v.setNoAi(true);
		return v;
	}

	/** Without Mega Showdown: a City Leader, a Master, a Stadium visitor and a Cup finalist neither Mega Evolve nor wear a bracelet. */
	//$ gametest_batch AREA '"megaTrainersNoMod"'
	@GameTest(template = AREA, batch = "megaTrainersNoMod")
	public void withoutMegaShowdownNothingChanges(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(!MegaBracelets.MOD, "Mega Showdown is loaded in the main suite");
		helper.assertTrue(MegaBracelets.ENABLED, "villageMegas should be on in GameTests");
		helper.assertTrue(!MegaBracelets.extraMegas(), "the extra Megas are on without Mega Showdown");
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setRank(VillageRanks.Rank.CITY);
		Villager leader = villager(helper, new BlockPos(3, 2, 3), ModVillagers.TRAINER_LEADER, 1);
		Villager master = villager(helper, new BlockPos(5, 2, 3), ModVillagers.TRAINER, 5);
		Villager visitor = villager(helper, new BlockPos(3, 2, 5), ModVillagers.TRAINER, 3);
		ModAttachments.STADIUM_VISITOR.set(visitor, new StadiumDays.Visitor(helper.absolutePos(HALL), UUID.randomUUID(), Optional.empty(),
			new UUID(0, 0), 0, false, 0));
		Villager finalist = villager(helper, new BlockPos(5, 2, 5), ModVillagers.TRAINER, 2);
		BlockPos host = helper.absolutePos(HALL);
		CupData.Cup cup = finalCup(level, host, finalist, master);
		try {
			helper.assertTrue(MegaBracelets.cityLeader(level, leader), "the Leader's village isn't a City");
			helper.assertTrue(CupDays.inFinal(level, finalist), "the finalist isn't in the final");
			for (Villager v : List.of(leader, master, visitor, finalist)) {
				helper.assertTrue(!MegaBracelets.canMegaEvolve(v), v.getVillagerData().getProfession() + " " + v.getVillagerData().getLevel()
					+ " can Mega Evolve without Mega Showdown");
				helper.assertTrue(!MegaBracelets.wears(v), "a bracelet without Mega Showdown");
				for (int i = 0; i < 40; i++) { // a whole check round of the villager's life
					v.tickCount++;
					MegaBracelets.tick(v);
				}
				helper.assertTrue(!MegaBracelets.told(v), "a client was told about a bracelet without Mega Showdown");
			}
		} finally {
			CupData.get(level).forget(host);
		}
		helper.succeed();
	}

	/** A Cup's final is known by who is in it: both entrants of the last pair (or a delegate for one), only on the Cup's day, and not once decided. */
	//$ gametest_batch AREA '"megaTrainersFinal"'
	@GameTest(template = AREA, batch = "megaTrainersFinal")
	public void aCupFinalistIsKnown(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos host = helper.absolutePos(HALL);
		Villager a = villager(helper, new BlockPos(3, 2, 3), ModVillagers.TRAINER, 2);
		Villager b = villager(helper, new BlockPos(5, 2, 3), ModVillagers.TRAINER, 3);
		Villager c = villager(helper, new BlockPos(3, 2, 5), ModVillagers.TRAINER, 3);
		Villager d = villager(helper, new BlockPos(5, 2, 5), ModVillagers.TRAINER, 3);
		try {
			CupData.Cup cup = finalCup(level, host, a, b, c, d);
			helper.assertTrue(List.of(a, b, c, d).stream().noneMatch(v -> CupDays.inFinal(level, v)), "a first round isn't the final");
			cup.results.add(new CupData.Result(1, a.getUUID(), b.getUUID()));
			cup.results.add(new CupData.Result(1, d.getUUID(), c.getUUID()));
			helper.assertTrue(CupDays.inFinal(level, a) && CupDays.inFinal(level, d), "the two winners are in the final");
			helper.assertTrue(!CupDays.inFinal(level, b) && !CupDays.inFinal(level, c), "a loser is in the final");
			cup.day = Chronicle.day(level) - 1;
			helper.assertTrue(!CupDays.inFinal(level, a), "a final on another day");
			cup.day = Chronicle.day(level);
			cup.results.add(new CupData.Result(2, a.getUUID(), d.getUUID()));
			helper.assertTrue(!CupDays.inFinal(level, a) && !CupDays.inFinal(level, d), "a decided final");
		} finally {
			CupData.get(level).forget(host);
		}
		helper.succeed();
	}

	/** A Cup at {@code host} today, closed, with these villagers in its bracket (two of them: the final is next). */
	static CupData.Cup finalCup(ServerLevel level, BlockPos host, Villager... trainers) {
		CupData.Cup cup = CupData.get(level).cup(host);
		cup.closed = true;
		cup.noCup = "";
		cup.day = Chronicle.day(level);
		for (Villager v : trainers) {
			CupData.Entrant e = new CupData.Entrant(CupData.Kind.HOST_TRAINER, v.getUUID(), "", v.getVillagerData().getLevel(), 0, host);
			cup.entrants.add(e);
			cup.bracket.add(e);
		}
		return cup;
	}
}
