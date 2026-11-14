package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.OldHouses;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.legend.LegendSites;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Places;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestTracker;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import java.util.BitSet;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Lane E QA (lane-e-1114-0113), B1212's kin, the whole pack's lag: B1212 stopped the quest checks from loading a far
 * village hall's chunk, and its notes asked for the other loops over saved halls to be checked the same way. These are
 * two more that run on their own: a far hall's chunk must not be loaded (or kept loaded) by them.
 */
public class QaUnloadedHallLoads1114eBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** A spot far from every test area and player, so its chunk is never loaded unless something loads it. */
	private static BlockPos far(GameTestHelper helper, int dx, int dz) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		return new BlockPos(origin.getX() + dx, 70, origin.getZ() + dz);
	}

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	/**
	 * Old houses (27.20): a Steward surveyed a far village whose plan renews old houses, and everyone left. The survey is
	 * done and kept in memory, and the old-house tick (every tick) looks the hall's block entity up for every kept survey,
	 * so the hall's chunk is loaded again every tick and never unloads. Expected: it unloads like any other chunk nobody is
	 * near (a control hall with no renew zone, loaded the same way beside it, does).
	 */
	//$ gametest_ticks_batch AREA '400' '"qa1114eOldHousesUnload"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa1114eOldHousesUnload")
	public void aSurveyedFarHallsChunkUnloadsOnceEveryoneLeaves(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 7000, 7000);
		BlockPos control = far(helper, 7000 + 96, 7000);
		helper.assertTrue(!loaded(level, hall) && !loaded(level, control), "setup: the far chunks are already loaded");
		Leftovers.after(helper, () -> {
			OldHouses.forget(level, hall);
			for (BlockPos h : List.of(hall, control)) {
				if (loaded(level, h) && level.getBlockState(h).is(ModBlocks.VILLAGE_HALL)) {
					level.setBlock(h, Blocks.AIR.defaultBlockState(), 3);
				}
			}
		});

		// A hall with a small Homes zone that renews old houses (no houses in it), surveyed while its chunk is loaded.
		level.getChunk(hall);
		level.getChunk(control);
		level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		level.setBlock(control, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3); // the control: a hall with no renew zone
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		BitSet cells = new BitSet();
		for (int x = 2; x <= 5; x++) {
			for (int z = 0; z <= 3; z++) {
				cells.set(CityPlan.cellAt(hall, hall.offset(x, 0, z)));
			}
		}
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells).editZone(0, "homes", "Homes 1", "", true));
		OldHouses.forget(level, hall);
		helper.assertTrue(OldHouses.request(level, hall).isPresent(), "setup: no survey for the renew zone");

		helper.succeedWhen(() -> {
			helper.assertTrue(OldHouses.result(level, hall).isPresent(), "the survey hasn't finished");
			helper.assertTrue(!loaded(level, control), "the control hall's chunk (no renew zone) hasn't unloaded yet");
			helper.assertTrue(!loaded(level, hall), "the surveyed hall's chunk is still loaded while the control hall's beside it unloaded "
				+ "(the old-house tick loads it every tick)");
		});
	}

	/**
	 * Legends found at sites (a ruined portal, an outpost, a shipwreck): every 5 seconds, for a player standing in an unused
	 * site, LegendSites.qualifying looks up the block entity of every village hall within 1500 blocks, nearest first, so a
	 * village far from the site (and from every player) has its hall's chunk loaded on the server thread. Expected: a site
	 * check never loads a chunk.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1114eLegendSiteUnloaded"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1114eLegendSiteUnloaded")
	public void aLegendSiteCheckNeverLoadsAFarHallsChunk(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -7000, 7000);
		Caravans.Data caravans = Caravans.Data.get(level);
		Leftovers.after(helper, () -> caravans.remove(hall));
		helper.assertTrue(!loaded(level, hall), "setup: the far hall's chunk is already loaded");
		caravans.setWants(hall, Component.literal("Far Village"), List.of());

		// The player stands in a ruined portal 300 blocks from that village (the Pathfinder is found at ruined portals).
		BlockPos site = hall.offset(300, 0, 0);
		LegendSites.qualifying(level, helper.makeMockServerPlayerInLevel(), "ruined_portal", site);
		helper.assertTrue(!loaded(level, hall), "the legend site check loaded the far village hall's chunk");
		helper.succeed();
	}

	/**
	 * Quests with a place to reach (QuestTracker, every 2 seconds for every player): for each open quest whose place the
	 * player stands in, the hall's block entity is looked up before it is asked whether this player may help, so a player
	 * who may not (here, a villager's own request for their friends only, 31.9) standing at the place has the far hall's
	 * chunk loaded every 2 seconds, for nothing. Expected: a player who may not help never loads the hall.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1114eReachUnloaded"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1114eReachUnloaded")
	public void aReachCheckForAPlayerWhoMayNotHelpNeverLoadsAFarHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 7000, -7000);
		Leftovers.after(helper, () -> Stories.Data.get(level).remove(hall));
		helper.assertTrue(!loaded(level, hall), "setup: the far hall's chunk is already loaded");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos place = player.blockPosition();
		long now = level.getGameTime();
		Quest quest = new Quest(UUID.randomUUID(), AliveWorkplace.id("personal/qa1114e"), "villager", Optional.empty(), "Ana", now, now + 24000L * 3,
			List.of(new Objectives.Reach(Places.point(place), 32)), new int[1], List.of());
		quest.villager = UUID.randomUUID(); // a villager nobody here is a friend of
		Stories.post(level, hall, quest);
		helper.assertTrue(((Objectives.Reach) quest.objectives.get(0)).inside(player.blockPosition()), "setup: the player isn't at the place");

		QuestTracker.checkReach(player);
		helper.assertTrue(quest.progress[0] == 0, "a player who may not help moved the request on");
		helper.assertTrue(!loaded(level, hall), "the reach check loaded the far hall's chunk for a player who may not help");
		helper.succeed();
	}

	/**
	 * Festivals: a far village's festival is on (its round put the hall in the festival list) when everyone leaves. The
	 * festival tick (every 40 ticks) asks Festivals.isOn, which looks the hall's block entity up, so the hall's chunk is
	 * loaded again every 2 seconds until the festival ends at dusk. Expected: once its chunk has unloaded, nothing of the
	 * festival loads it again (watched for 120 ticks after it first unloaded).
	 */
	//$ gametest_ticks_batch AREA '600' '"qa1114eFestivalUnloaded"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa1114eFestivalUnloaded")
	public void aFarFestivalNeverLoadsItsHallAgainOnceUnloaded(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -7000, -7000);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall's chunk is already loaded");
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			level.setDayTime(time);
			if (loaded(level, hall) && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
				level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3);
			}
		});
		// Festival time today (the festival starts at 9000), and the far village's festival is today.
		level.setDayTime(Math.floorDiv(time, 24000L) * 24000L + Festivals.START + 500);
		level.getChunk(hall);
		level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setFestivalDay(Chronicle.day(level));
		Festivals.round(level, hall, entity, 0);
		helper.assertTrue(Festivals.isOn(level, hall), "setup: the festival isn't on");

		int[] unloadedFor = {-1};
		helper.onEachTick(() -> {
			if (unloadedFor[0] < 0) {
				if (!loaded(level, hall)) {
					unloadedFor[0] = 0;
				}
				return;
			}
			helper.assertTrue(!loaded(level, hall), "the festival tick loaded the far hall's chunk again " + unloadedFor[0] + " ticks after it unloaded");
			if (++unloadedFor[0] >= 120) {
				helper.succeed();
			}
		});
	}
}
