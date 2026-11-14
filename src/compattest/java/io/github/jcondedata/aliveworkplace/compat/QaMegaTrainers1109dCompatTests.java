package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
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
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-11-09, lane d) for ROADMAP 38.5 with Mega Showdown, beyond {@link MegaTrainerCompatTests}: a village's rank
 * changing after the fact moves its Leader's Mega Stone and bracelet both ways; every rank and no village at all leave a
 * plain trainer's Mega to Masters alone; a Leader out of the City's reach, or whose hall is broken, or nearer a Town's
 * hall, has none; exactly one holder (never two) over many trainers; Stadium visitors of every level hold exactly one,
 * and lose it when the visit ends. Each test runs alone, synchronously, and puts back what it changed.
 */
public class QaMegaTrainers1109dCompatTests implements FabricGameTest {
	static final BlockPos HALL = MegaTrainerCompatTests.HALL;

	static long stones(List<Pokemon> team) {
		return MegaTrainerCompatTests.stones(team);
	}

	static String show(List<Pokemon> team) {
		return MegaTrainerCompatTests.show(team);
	}

	/**
	 * The species in order and which one holds a Mega Stone (and which stone). Not the other held items: a species with
	 * random forms (Wormadam) can change its form's held item from call to call, a separate issue
	 * ({@code QaMegaTrainersBug1109dCompatTests}).
	 */
	static String megaShape(List<Pokemon> team) {
		return team.stream().map(p -> p.getSpecies().getName() + (io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonMegas.holdsItsStone(p)
			? "@" + p.heldItem().getItem() : "")).toList().toString();
	}

	static Villager trainer(GameTestHelper helper, BlockPos at, int level) {
		return MegaTrainerCompatTests.villager(helper, at, ModVillagers.TRAINER, level);
	}

	static Villager leader(GameTestHelper helper, BlockPos at) {
		return MegaTrainerCompatTests.villager(helper, at, ModVillagers.TRAINER_LEADER, 1);
	}

	/** One full bracelet check round (40 ticks of the villager's life): what its clients are told now. */
	static boolean afterARound(Villager v) {
		for (int i = 0; i < 40; i++) {
			v.tickCount++;
			MegaBracelets.tick(v);
		}
		return MegaBracelets.told(v);
	}

	/** "" when {@code v}'s team has {@code expect} Mega Stone holders and the bracelet agrees, else what's wrong. */
	static String check(Villager v, long expect, String who) {
		List<Pokemon> team = CobblemonTrainers.teamOf(v);
		long n = stones(team);
		boolean wears = MegaBracelets.wears(v);
		boolean can = MegaBracelets.canMegaEvolve(v);
		if (n != expect || wears != (expect > 0) || can != (expect > 0)) {
			return who + ": " + n + " stones (want " + expect + "), wears " + wears + ", canMegaEvolve " + can + " " + show(team) + "; ";
		}
		return "";
	}

	/**
	 * A village's rank moves after its Leader is there: Hamlet, Village, Town none; becoming a City gives exactly one Mega
	 * Stone and the bracelet (the clients are told within a check round); falling back to a Town takes both away again.
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaMegaRank1109d"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaMegaRank1109d")
	public void aRankChangeMovesTheLeadersMegaBothWays(GameTestHelper helper) {
		try {
			helper.assertTrue(MegaBracelets.MOD && MegaBracelets.extraMegas(), "Mega Showdown or villageMegas missing");
			VillageHallBlockEntity hall = MegaTrainerCompatTests.hall(helper, VillageRanks.Rank.HAMLET);
			Villager leader = leader(helper, new BlockPos(3, 2, 3));
			MegaBracelets.refresh(leader);
			StringBuilder wrong = new StringBuilder();
			VillageRanks.Rank[] path = {VillageRanks.Rank.HAMLET, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.TOWN, VillageRanks.Rank.CITY,
				VillageRanks.Rank.TOWN, VillageRanks.Rank.CITY, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.HAMLET};
			for (VillageRanks.Rank rank : path) {
				hall.setRank(rank); // as the hall's round does it: no forgetNearest
				long want = rank == VillageRanks.Rank.CITY ? 1 : 0;
				wrong.append(check(leader, want, "Leader in a " + rank));
				boolean told = afterARound(leader);
				if (told != (want == 1)) {
					wrong.append("Leader in a ").append(rank).append(": clients told bracelet ").append(told).append("; ");
				}
			}
			helper.assertTrue(wrong.isEmpty(), wrong.toString());
		} finally {
			MegaTrainerCompatTests.done(helper);
		}
		helper.succeed();
	}

	/**
	 * A plain trainer (not a Leader) Mega Evolves only as a Master, whatever the village: Novice to Expert none and a Master
	 * exactly one, in a Hamlet, a Village, a Town, a City and with no Village Hall at all.
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaMegaTiers1109d"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaMegaTiers1109d")
	public void onlyAMasterAmongPlainTrainersWhateverTheRank(GameTestHelper helper) {
		try {
			VillageHallBlockEntity hall = MegaTrainerCompatTests.hall(helper, VillageRanks.Rank.HAMLET);
			List<Villager> trainers = new ArrayList<>();
			for (int level = 1; level <= 5; level++) {
				trainers.add(trainer(helper, new BlockPos(1 + 2 * level, 2, 5), level));
			}
			StringBuilder wrong = new StringBuilder();
			for (VillageRanks.Rank rank : VillageRanks.Rank.values()) {
				hall.setRank(rank);
				for (Villager v : trainers) {
					int level = v.getVillagerData().getLevel();
					wrong.append(check(v, level == 5 ? 1 : 0, "level " + level + " in a " + rank));
				}
			}
			helper.setBlock(HALL, Blocks.AIR);
			VillageHalls.forgetNearest();
			for (Villager v : trainers) {
				int level = v.getVillagerData().getLevel();
				wrong.append(check(v, level == 5 ? 1 : 0, "level " + level + " with no village"));
			}
			helper.assertTrue(wrong.isEmpty(), wrong.toString());
		} finally {
			MegaTrainerCompatTests.done(helper);
		}
		helper.succeed();
	}

	/**
	 * Only a Leader of the City counts: one inside its reach has a Mega, one just outside has none, one nearer a Town's
	 * hall has none, and once the City's hall is broken nobody's Leader has one.
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaMegaReach1109d"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaMegaReach1109d")
	public void onlyALeaderWithinTheCitysReachMegaEvolves(GameTestHelper helper) {
		try {
			MegaTrainerCompatTests.hall(helper, VillageRanks.Rank.CITY);
			VillageHalls.RADIUS = 4;
			VillageHalls.forgetNearest();
			Villager inside = leader(helper, new BlockPos(4, 2, 1));   // 3 blocks from the hall
			Villager outside = leader(helper, new BlockPos(8, 2, 1));  // 7 blocks: out of a 4-block reach
			StringBuilder wrong = new StringBuilder();
			wrong.append(check(inside, 1, "Leader 3 blocks from a City hall (reach 4)"));
			wrong.append(check(outside, 0, "Leader 7 blocks from a City hall (reach 4)"));

			VillageHalls.RADIUS = 10;
			BlockPos townHall = new BlockPos(14, 2, 14);
			helper.setBlock(townHall, ModBlocks.VILLAGE_HALL);
			((VillageHallBlockEntity) helper.getBlockEntity(townHall)).setRank(VillageRanks.Rank.TOWN);
			VillageHalls.forgetNearest();
			Villager townLeader = leader(helper, new BlockPos(12, 2, 12)); // within both reaches, nearer the Town
			wrong.append(check(townLeader, 0, "Leader nearer a Town hall than a City hall"));
			wrong.append(check(inside, 1, "Leader next to the City hall with a Town nearby"));

			helper.setBlock(HALL, Blocks.AIR);
			VillageHalls.forgetNearest();
			wrong.append(check(inside, 0, "Leader after the City hall was broken"));
			helper.setBlock(townHall, Blocks.AIR);
			VillageHalls.forgetNearest();
			wrong.append(check(inside, 0, "Leader with no hall at all"));
			wrong.append(check(townLeader, 0, "Town Leader with no hall at all"));
			helper.assertTrue(wrong.isEmpty(), wrong.toString());
		} finally {
			helper.setBlock(new BlockPos(14, 2, 14), Blocks.AIR);
			MegaTrainerCompatTests.done(helper);
		}
		helper.succeed();
	}

	/**
	 * Exactly one Mega Stone holder, never two, over 30 trainer UUIDs at a Leader's tier (4) and a Master's (5): the same
	 * team size as without the Mega, the same team every time, and none without it. Then 12 real Leaders of a City and 6
	 * Masters: each has one and wears the bracelet.
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaMegaSeeds1109d"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaMegaSeeds1109d")
	public void exactlyOneHolderAcrossManyTrainers(GameTestHelper helper) {
		StringBuilder wrong = new StringBuilder();
		for (int i = 0; i < 30; i++) {
			UUID id = new UUID(0x1109d000L + i * 1_000_003L, 0x38_05L * (i + 1) * 15_485_863L);
			for (int tier = 4; tier <= 5; tier++) {
				List<Pokemon> mega = CobblemonTrainers.team(id, tier, true);
				List<Pokemon> plain = CobblemonTrainers.team(id, tier, false);
				if (stones(mega) != 1 || mega.size() != plain.size() || stones(plain) != 0
					|| !megaShape(CobblemonTrainers.team(id, tier, true)).equals(megaShape(mega))) {
					wrong.append(id).append(" tier ").append(tier).append(": mega ").append(show(mega)).append(" plain ").append(show(plain)).append("; ");
				}
			}
		}
		try {
			MegaTrainerCompatTests.hall(helper, VillageRanks.Rank.CITY);
			VillageHalls.RADIUS = 16; // every Leader below within the City's reach (done() puts 64 back)
			VillageHalls.forgetNearest();
			for (int i = 0; i < 12; i++) {
				Villager v = leader(helper, new BlockPos(2 + (i % 6) * 2, 2, 3 + (i / 6) * 2));
				wrong.append(check(v, 1, "City Leader " + v.getUUID()));
			}
			for (int i = 0; i < 6; i++) {
				Villager v = trainer(helper, new BlockPos(2 + i * 2, 2, 9), 5);
				wrong.append(check(v, 1, "Master " + v.getUUID()));
			}
			helper.assertTrue(wrong.isEmpty(), wrong.toString());
		} finally {
			MegaTrainerCompatTests.done(helper);
		}
		helper.succeed();
	}

	/**
	 * A Stadium Day's visiting champion holds exactly one Mega Stone at every level (Novice to Master, and a visiting
	 * Leader from a Town), with the bracelet; when the visit ends (the visitor mark removed) only the Master keeps one.
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaMegaVisitor1109d"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaMegaVisitor1109d")
	public void stadiumVisitorsHoldExactlyOneMegaWhileVisiting(GameTestHelper helper) {
		try {
			MegaTrainerCompatTests.hall(helper, VillageRanks.Rank.TOWN);
			List<Villager> visitors = new ArrayList<>();
			for (int level = 1; level <= 5; level++) {
				visitors.add(trainer(helper, new BlockPos(1 + 2 * level, 2, 5), level));
			}
			visitors.add(leader(helper, new BlockPos(3, 2, 8)));
			for (Villager v : visitors) {
				ModAttachments.STADIUM_VISITOR.set(v, new StadiumDays.Visitor(helper.absolutePos(HALL), UUID.randomUUID(), Optional.empty(),
					new UUID(0, 0), 0, false, 0));
			}
			StringBuilder wrong = new StringBuilder();
			for (Villager v : visitors) {
				String who = v.getVillagerData().getProfession() + " " + v.getVillagerData().getLevel();
				wrong.append(check(v, 1, "visiting " + who));
				if (!afterARound(v)) {
					wrong.append("visiting ").append(who).append(": clients not told of the bracelet; ");
				}
			}
			for (Villager v : visitors) {
				ModAttachments.STADIUM_VISITOR.remove(v);
				String who = v.getVillagerData().getProfession() + " " + v.getVillagerData().getLevel();
				long want = v.getVillagerData().getProfession() == ModVillagers.TRAINER && v.getVillagerData().getLevel() == 5 ? 1 : 0;
				wrong.append(check(v, want, "after the visit, " + who));
				if (afterARound(v) != (want == 1)) {
					wrong.append("after the visit, ").append(who).append(": clients told ").append(MegaBracelets.told(v)).append("; ");
				}
			}
			helper.assertTrue(wrong.isEmpty(), wrong.toString());
		} finally {
			MegaTrainerCompatTests.done(helper);
		}
		helper.succeed();
	}
}
