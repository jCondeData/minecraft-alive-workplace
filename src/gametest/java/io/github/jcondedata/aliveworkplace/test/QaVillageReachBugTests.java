package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA bugs for 36.20. The spec: "a spot is in reach when it is inside the village's claimed area (the City Plan zones and
 * the hall's radius), wherever the worker's block stands in it". A worker's block standing in a zone the player painted
 * beyond the hall's radius stands in the village too, so the whole village is in its reach.
 */
public class QaVillageReachBugTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);

	/**
	 * The hall's radius 64; a composter in a Homes zone painted in the plan's corner, 79 blocks from the hall (outside the
	 * radius, inside the claimed area); a field 60 blocks west of the hall, inside the radius, 129 blocks from the composter.
	 * Both are in the village, so the field is in reach. Today the composter counts as in no village: refused with
	 * "That field is 129 blocks from the Composter the farmer works at — keep it within 128." (no village named).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaReachZoneBench"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReachZoneBench")
	public void aWorkBlockInAZonePastTheRadiusReachesTheWholeVillage(GameTestHelper helper) {
		QaVillageReachGameTests.setUp(helper);
		QaVillageReachGameTests.radius(64);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos composter = hall.offset(56, 0, 56);
		// the composter stands 79 blocks off, outside the test area: its chunk is kept loaded so the farmer can be clicked
		// (in a run with only this class nothing else may load it, and a villager there can't be clicked until its entities load)
		net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(composter);
		boolean wasForced = level.getForcedChunks().contains(chunk.toLong());
		level.setChunkForced(chunk.x, chunk.z, true);
		Leftovers.after(helper, () -> level.setChunkForced(chunk.x, chunk.z, wasForced));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(level.areEntitiesLoaded(chunk.toLong()) && level.isPositionEntityTicking(composter),
				"the composter's chunk isn't loaded yet"))
			.thenExecute(() -> {
				VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
				BitSet cells = new BitSet();
				cells.set(CityPlan.cellAt(hall, composter));
				entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
				level.setBlockAndUpdate(composter, Blocks.COMPOSTER.defaultBlockState());
				Leftovers.after(helper, () -> level.setBlockAndUpdate(composter, Blocks.AIR.defaultBlockState()));

				Villager farmer = EntityType.VILLAGER.create(level);
				farmer.moveTo(composter.getX() + 1.5, composter.getY(), composter.getZ() + 0.5);
				farmer.setNoAi(true);
				level.addFreshEntity(farmer);
				Leftovers.after(helper, () -> {
					ModAttachments.FARM_FIELD.remove(farmer);
					farmer.discard();
				});
				Jobs.employ(level, farmer, composter, PoiTypes.FARMER, VillagerProfession.FARMER);
				List<Component> seen = new ArrayList<>();
				ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
				BlockPos field = hall.west(60);
				helper.assertTrue(Math.sqrt(field.distSqr(composter)) > 128, "the field is in the long reach anyway");
				p.setItemInHand(InteractionHand.MAIN_HAND, QaVillageReachGameTests.fieldMarker(level, field));
				QaStageSixGameTests.clickVillager(p, farmer, seen);
				FieldJob job = ModAttachments.FARM_FIELD.get(farmer);
				helper.assertTrue(job != null, "a field in the village, for a composter in a zone of the same village (79 blocks from the hall, radius "
					+ VillageHalls.RADIUS + "), was refused: " + QaVillageReachGameTests.texts(seen));
				helper.succeed();
			});
	}
}
