package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.ProfessorsThesis;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-d-1031-0408) for The Professor's Thesis (ROADMAP 31.21), from its spec: Peer Review is against "the Trainer
 * Leader of a village with a hall within caravan range; with none, a rival scholar, Thessaly, comes". The edge of
 * caravan range from both sides (a Leader's village exactly {@code caravanRange} blocks away is picked, one block
 * further is not), a village whose known trainer is no Leader is passed over, and with only a Leader past the range
 * Thessaly comes to the hall. The far villages are known to the caravans only (their chunks never load), as a far
 * village is on a real server.
 */
public class QaThesisPeerReview1031CompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);

	/** A Town with an Expert Scholar, Alder; the thesis forced to {@code topic}, started. */
	private static ArcState start(GameTestHelper helper, String topic) {
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 8;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		entity.setRank(VillageRanks.Rank.TOWN);
		BlockPos hall = helper.absolutePos(HALL);
		Arcs.stopAll(level, hall);
		Villager alder = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		alder.setNoAi(true);
		alder.setVillagerData(alder.getVillagerData().setProfession(ModVillagers.SCHOLAR).setLevel(4));
		alder.setCustomName(Component.literal("Alder"));
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			ProfessorsThesis.FORCE_TOPIC = null;
			Arcs.stopAll(level, hall);
			level.setBlockAndUpdate(hall, Blocks.AIR.defaultBlockState());
		});
		ProfessorsThesis.FORCE_TOPIC = topic;
		Arcs.Arc arc = Arcs.get(ProfessorsThesis.ARC).orElseThrow(() -> new AssertionError("professors_thesis.json didn't load with Cobblemon"));
		ArcState s = Arcs.start(level, hall, arc);
		helper.assertTrue(s != null && s.chapter == 0, "the thesis didn't start");
		return s;
	}

	/** A far village known to the caravans only, at {@code hall} + {@code dx} east, with a known trainer ({@code leader} or not). */
	private static BlockPos farVillage(GameTestHelper helper, BlockPos hall, int dx, String name, boolean leader) {
		Caravans.Data data = Caravans.Data.get(helper.getLevel());
		BlockPos far = hall.offset(dx, 0, 0);
		data.setWants(far, Component.literal(name), List.of());
		data.setLeader(far, new Caravans.Leader(UUID.randomUUID(), name + "'s trainer", 4, 0, leader));
		PartnerShowsCompatTests.after(helper, () -> {
			data.setLeader(far, null);
			data.remove(far);
		});
		return far;
	}

	//$ gametest_ticks_batch AREA '200' '"qa_thesis_range"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_thesis_range")
	public void aLeaderAtCaravanRangeIsPickedAndOneBlockPastItIsNot(GameTestHelper helper) {
		start(helper, "cold");
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		int range = Caravans.RANGE;
		BlockPos past = farVillage(helper, hall, range + 1, "Cerulean", true);
		helper.assertTrue(!past.equals(ProfessorsThesis.leaderVillage(level, hall)), "a Leader one block past caravan range was picked");
		BlockPos plain = farVillage(helper, hall, range - 10, "Vermilion", false);
		helper.assertTrue(!plain.equals(ProfessorsThesis.leaderVillage(level, hall)), "a village whose trainer is no Leader was picked");
		BlockPos edge = farVillage(helper, hall, range, "Pewter", true);
		helper.assertTrue(edge.equals(ProfessorsThesis.leaderVillage(level, hall)), "a Leader exactly at caravan range wasn't picked: " + ProfessorsThesis.leaderVillage(level, hall));
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '200' '"qa_thesis_past_range"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_thesis_past_range")
	public void withOnlyALeaderPastCaravanRangeThessalyComes(GameTestHelper helper) {
		ArcState s = start(helper, "cold");
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		farVillage(helper, hall, Caravans.RANGE + 1, "Cerulean", true);
		helper.assertTrue(ProfessorsThesis.leaderVillage(level, hall) == null, "a Leader was found: " + ProfessorsThesis.leaderVillage(level, hall));
		Arcs.next(level, hall);
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 2, "not at Peer Review: chapter " + (s.chapter + 1));
		Villager thessaly = ProfessorsThesis.thessaly(level, s);
		helper.assertTrue(thessaly != null && thessaly.getCustomName().getString().equals("Thessaly"), "Thessaly didn't come with the only Leader past caravan range");
		helper.succeed();
	}
}
