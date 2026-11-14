package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendSites;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Places;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestTracker;
import io.github.jcondedata.aliveworkplace.story.RequestErrands;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1114-0653) for B1212's far-hall fixes, B1220 to B1225, from their Expected and Decision lines: the "after"
 * side of each decision. What a far (unloaded) hall's quests counted is paid once and only once when the hall loads and
 * runs its round, even past the quest's deadline; a place reached by a helper while the hall is far counts without loading
 * it; two revivals for one far request answer it once; a legend site skips a far hall but still offers for a loaded one
 * behind it; a far siege past its dawn stands still while nobody is near and is lifted once someone is back.
 */
public class QaFarHall1114cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	private static BlockPos far(GameTestHelper helper, int dx, int dz) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		return new BlockPos(origin.getX() + dx, 70, origin.getZ() + dz);
	}

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	private static void cleanUp(GameTestHelper helper, ServerLevel level, BlockPos hall) {
		Leftovers.after(helper, () -> {
			if (loaded(level, hall) && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
				level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3);
			}
			Stories.Data.get(level).remove(hall);
		});
	}

	/** The far hall's chunk loads (someone went there) with its hall. */
	private static VillageHallBlockEntity load(ServerLevel level, BlockPos hall) {
		level.getChunk(hall);
		level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setLastQuestDay(Long.MAX_VALUE / 2); // no new quest this morning
		return entity;
	}

	private static long questLines(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.QUEST).count();
	}

	private static Quest hallQuest(ServerLevel level, String file, long due, Objectives.Objective objective) {
		long now = level.getGameTime();
		return new Quest(UUID.randomUUID(), AliveWorkplace.id("qa1114c/" + file), "hall", Optional.empty(), "Ana", now, due,
			List.of(objective), new int[1], List.of());
	}

	/**
	 * B1220 Decision, "a kill still counts and a quest it finishes waits; the round pays it": three kills for a far hall's
	 * two-zombie "anywhere" quest fill it to two (not three) without loading the hall; the hall's first round pays it (one
	 * chronicle line, finished in the village), and a second round pays nothing more.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFarHall1114cPaidOnce"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFarHall1114cPaidOnce")
	public void farKillsAreCappedAndPaidOnceWhenTheHallLoads(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 8200, -8200);
		cleanUp(helper, level, hall);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		Quest q = hallQuest(level, "kills", level.getGameTime() + 24000L * 3, new Objectives.Kill("minecraft:zombie", 2, true));
		Stories.post(level, hall, q);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		for (int k = 0; k < 3; k++) {
			Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
			zombie.setNoAi(true);
			Stories.onKill(level, zombie, player);
			zombie.discard();
		}
		helper.assertTrue(!loaded(level, hall), "a kill loaded the far hall's chunk");
		helper.assertTrue(q.progress[0] == 2 && q.done(), "the kills didn't fill the quest to its need of 2: " + q.progress[0]);
		helper.assertTrue(q.helpers.getOrDefault(player.getUUID(), 0) == 2, "the third kill counted for a full quest: " + q.helpers);
		helper.assertTrue(Stories.open(level, hall).equals(List.of(q)), "paid (or dropped) before the hall loaded: " + Stories.open(level, hall));

		VillageHallBlockEntity entity = load(level, hall);
		long before = questLines(entity);
		Stories.round(level, hall, entity);
		helper.assertTrue(Stories.open(level, hall).isEmpty(), "not paid at the hall's round: " + Stories.open(level, hall));
		helper.assertTrue(Stories.finished(level, hall, q.file), "the quest isn't finished in the village");
		helper.assertTrue(questLines(entity) == before + 1, "the chronicle's quest lines went from " + before + " to " + questLines(entity));
		Stories.round(level, hall, entity);
		helper.assertTrue(questLines(entity) == before + 1, "paid again at a second round: " + questLines(entity));
		helper.succeed();
	}

	/**
	 * B1220: a quest a far kill finished waits for the round "and a quest it finishes is paid at the hall's next round".
	 * Its deadline passes before anyone goes back: it was finished in time, so the round pays it rather than dropping it
	 * as overdue.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFarHall1114cPastDue"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFarHall1114cPastDue")
	public void aFarQuestFinishedInTimeIsPaidEvenIfItsDeadlinePassesBeforeTheHallLoads(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -8200, 8200);
		cleanUp(helper, level, hall);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		Quest q = hallQuest(level, "due", level.getGameTime() + 3, new Objectives.Kill("minecraft:zombie", 1, true));
		Stories.post(level, hall, q);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
		zombie.setNoAi(true);
		Stories.onKill(level, zombie, helper.makeMockServerPlayerInLevel());
		zombie.discard();
		helper.assertTrue(q.done() && !loaded(level, hall), "setup: the kill didn't finish the quest, or loaded the hall");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(level.getGameTime() >= q.due, "setup: the deadline hasn't passed");
			VillageHallBlockEntity entity = load(level, hall);
			long before = questLines(entity);
			Stories.round(level, hall, entity);
			helper.assertTrue(Stories.open(level, hall).isEmpty(), "still up after the round: " + Stories.open(level, hall));
			helper.assertTrue(Stories.finished(level, hall, q.file) && questLines(entity) == before + 1,
				"a quest finished in time was dropped as overdue instead of paid (finished " + Stories.finished(level, hall, q.file) + ")");
			helper.succeed();
		});
	}

	/**
	 * B1223 Decision, the helper's side: a player who may help, standing at a far hall's quest place, has it counted ("the
	 * place counts, paid at the hall's round as in B1220") without the hall's chunk loading; the round pays it once.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFarHall1114cReach"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFarHall1114cReach")
	public void aPlaceReachedForAFarHallCountsWithoutLoadingItAndIsPaidAtItsRound(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 8200, 8200);
		cleanUp(helper, level, hall);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Quest q = hallQuest(level, "reach", level.getGameTime() + 24000L * 3, new Objectives.Reach(Places.point(player.blockPosition()), 32));
		Stories.post(level, hall, q);

		QuestTracker.checkReach(player);
		helper.assertTrue(!loaded(level, hall), "the reach check loaded the far hall's chunk for a helper");
		helper.assertTrue(q.progress[0] == 1 && q.done(), "the place didn't count while the hall was far: " + q.progress[0]);
		QuestTracker.checkReach(player);
		helper.assertTrue(q.progress[0] == 1 && Stories.open(level, hall).equals(List.of(q)), "a second check: " + q.progress[0] + " " + Stories.open(level, hall));

		VillageHallBlockEntity entity = load(level, hall);
		long before = questLines(entity);
		Stories.round(level, hall, entity);
		Stories.round(level, hall, entity);
		helper.assertTrue(Stories.open(level, hall).isEmpty() && Stories.finished(level, hall, q.file), "not paid at the round: " + Stories.open(level, hall));
		helper.assertTrue(questLines(entity) == before + 1, "paid " + (questLines(entity) - before) + " times");
		helper.succeed();
	}

	/**
	 * B1220 Decision, the revival: two revivals of the same partner while the hall is far are both kept, and at the hall's
	 * round they answer the request once (it moves on to its need, not past it), with no error.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFarHall1114cRevivedTwice"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFarHall1114cRevivedTwice")
	public void twoRevivalsWhileTheHallIsFarAnswerItsRequestOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -8200, -8200);
		cleanUp(helper, level, hall);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		UUID was = UUID.randomUUID();
		long now = level.getGameTime();
		Quest q = new Quest(UUID.randomUUID(), AliveWorkplace.id("personal/qa1114c"), "villager", Optional.empty(), "Ana", now, now + 24000L * 3,
			List.of(new RequestErrands.Revive(Optional.of(was), "Bo")), new int[1], List.of());
		q.villager = UUID.randomUUID();
		Stories.post(level, hall, q);
		Villager back = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		back.setNoAi(true);
		RequestErrands.onRevived(level, was, back);
		RequestErrands.onRevived(level, was, back);
		helper.assertTrue(!loaded(level, hall), "a revival loaded the far hall's chunk");
		helper.assertTrue(q.progress[0] == 0 && PersonalRequests.open(level, hall).equals(List.of(q)), "the request before the hall loads: " + q.progress[0]);

		VillageHallBlockEntity entity = load(level, hall);
		Stories.round(level, hall, entity);
		Stories.round(level, hall, entity);
		back.discard();
		helper.assertTrue(q.progress[0] == 1, "the request's progress after two kept revivals: " + q.progress[0]);
		helper.assertTrue(Stories.open(level, hall).isEmpty(), "the answered request is still up: " + Stories.open(level, hall));
		helper.succeed();
	}

	private static Legend legend() {
		return Legends.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1114c_found"), JsonParser.parseString(
			"{\"rarity\": \"rare\", \"job\": \"minecraft:cartographer\", \"title\": \"Found\", \"lore\": \"the lore\","
				+ " \"names\": [\"Pip\"], \"arrive\": [{\"way\": \"found\", \"site\": \"ruined_portal\"}]}").getAsJsonObject());
	}

	/**
	 * B1222 Decision, "a hall whose chunk isn't entity-ticking is skipped": skipped, not the end of the search. A far
	 * village nearer the site than the player's own (loaded) village is passed over without loading it, and the player's
	 * village behind it still qualifies.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaFarHall1114cLegendBehind"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaFarHall1114cLegendBehind")
	public void aLegendSiteSkipsAFarHallAndStillOffersForTheLoadedOneBehindIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 2))).setOwner(owner.getUUID(), owner.getName().getString());
		BlockPos near = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos site = near.offset(1400, 0, 0);
		BlockPos farHall = new BlockPos(site.getX() + 10, 70, site.getZ());
		Caravans.Data caravans = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			caravans.remove(near);
			caravans.remove(farHall);
		});
		helper.assertTrue(!loaded(level, farHall), "setup: the far hall's chunk is loaded");
		caravans.setWants(near, Component.literal("Home"), List.of());
		caravans.setWants(farHall, Component.literal("Far Village"), List.of());
		Optional<LegendSites.Match> match;
		try {
			Legend l = legend();
			Legends.setForTest(new java.util.LinkedHashMap<>(Map.of(l.id(), l)));
			match = LegendSites.qualifying(level, owner, "ruined_portal", site);
		} finally {
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		}
		helper.assertTrue(!loaded(level, farHall), "the site check loaded the far hall's chunk");
		helper.assertTrue(match.isPresent() && match.get().hall().equals(near), "the owner's loaded village behind the far one: " + match);
		helper.succeed();
	}

	/**
	 * B1225 Decision: "a far siege stands still and loads nothing; once someone is back the hall's tick ends the raid and
	 * the director lifts it at once if its dawn has passed". A siege whose raid is over and whose dawn has passed is not
	 * lifted (nor loaded) while nobody is near; once the village's chunk is loaded and ticking again, it is lifted.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaFarHall1114cSiegeBack"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaFarHall1114cSiegeBack")
	public void aFarSiegePastItsDawnWaitsAndIsLiftedOnceSomeoneIsBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 8600, 8600);
		ThreatData data = ThreatData.get(level);
		helper.assertTrue(!loaded(level, hall), "setup: the far chunk is loaded");
		int cx = hall.getX() >> 4;
		int cz = hall.getZ() >> 4;
		Leftovers.after(helper, () -> {
			data.forgetSiege(hall);
			data.end(hall);
			Sieges.forget();
			if (loaded(level, hall) && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
				level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3);
			}
			level.setChunkForced(cx, cz, false);
		});
		// The raid is over (no raid on record) and the siege's dawn has passed: only nobody being near keeps it up.
		ThreatData.Siege siege = new ThreatData.Siege(hall, level.getGameTime(), level.getDayTime() - 1);
		data.beginSiege(siege);

		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			if (ticks[0] <= 60) {
				helper.assertTrue(!loaded(level, hall), "the far siege loaded its hall's chunk (tick " + ticks[0] + ")");
				helper.assertTrue(data.siege(hall).isPresent(), "the far siege was lifted while nobody was near (tick " + ticks[0] + ")");
				if (ticks[0] == 60) {
					level.setChunkForced(cx, cz, true); // someone is back
				}
				return;
			}
			if (ticks[0] == 61) {
				level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
			}
			if (data.siege(hall).isEmpty()) {
				helper.assertTrue(level.isPositionEntityTicking(hall), "lifted before the village was ticking");
				helper.succeed();
			}
		});
	}
}
