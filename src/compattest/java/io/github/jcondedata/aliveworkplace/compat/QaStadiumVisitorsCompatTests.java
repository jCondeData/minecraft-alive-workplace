package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.wonder.StadiumDays;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-d-1027-1909) for ROADMAP 35.24, from its text: on a Stadium Day "two to four visiting trainers (from the
 * villages of its realm or its caravan partners; travelling trainers from Journeyman to Master when those have none)
 * stand in the trainer boxes". The power's own tests only see travellers (no linked village). Here the Stadium's village
 * has a caravan partner 1,200 blocks off, its chunk not loaded (the usual case on a server), whose Trainer the caravans'
 * list names: one of the visitors is that Trainer, of his rank, called by his name and his village's; the rest are
 * travellers.
 */
public class QaStadiumVisitorsCompatTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 3);
	private static final BlockPos WEST_POST = new BlockPos(5, 2, 15);
	private static final BlockPos EAST_POST = new BlockPos(25, 2, 15);

	/** A finished Stadium round the test's hall, its spots in the area (as StadiumPowerCompatTests stages it). */
	private static WonderSite stadium(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearHalls(helper);
		helper.assertTrue(Wonders.loaded().containsKey(StadiumDays.STADIUM), "the Stadium's file didn't load with Cobblemon");
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 24;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(WEST_POST, ModBlocks.TRAINING_POST);
		helper.setBlock(EAST_POST, ModBlocks.TRAINING_POST);
		BlockPos hall = helper.absolutePos(HALL);
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(StadiumDays.STADIUM, hall, UUID.randomUUID(), "Jesse", 3);
		manager.finish(site.id(), 9);
		List<BlockPos> seats = new java.util.ArrayList<>();
		for (int x = 9; x <= 21; x++) {
			helper.setBlock(new BlockPos(x, 2, 24), Blocks.RED_CARPET);
			seats.add(helper.absolutePos(new BlockPos(x, 2, 24)));
		}
		StadiumDays.setSpotsForTest(site.id(), new StadiumDays.Spots(helper.absolutePos(new BlockPos(15, 2, 15)),
			List.of(helper.absolutePos(new BlockPos(6, 2, 15)), helper.absolutePos(new BlockPos(24, 2, 15)),
				helper.absolutePos(new BlockPos(6, 2, 16)), helper.absolutePos(new BlockPos(24, 2, 14))),
			List.of(helper.absolutePos(WEST_POST), helper.absolutePos(EAST_POST)), List.copyOf(seats), helper.absolutePos(new BlockPos(15, 2, 28))));
		Wonders.forgetPowers();
		PartnerShowsCompatTests.after(helper, () -> {
			StadiumDays.setSpotsForTest(site.id(), null);
			manager.remove(site.id());
			Wonders.forgetPowers();
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			Moods.forget();
		});
		return manager.site(site.id()).orElseThrow();
	}

	//$ gametest_ticks_batch HUGE '1200' '"qaStadiumPartner"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaStadiumPartner")
	public void aCaravanPartnersTrainerComesToTheStadiumDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WonderSite site = stadium(helper);
		BlockPos far = helper.absolutePos(HALL).offset(1200, 0, 0);
		// Riverton's hall stands in its own chunk, named; then the chunk is left to unload.
		level.setBlock(far, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		if (!(level.getBlockEntity(far) instanceof VillageHallBlockEntity hall)) {
			throw new GameTestAssertException("no hall at Riverton");
		}
		hall.setCustomName(Component.literal("Riverton"));
		Caravans.Data data = Caravans.Data.get(level);
		data.setWants(far, Component.literal("Riverton"), List.of());
		data.toggleRoute(site.hall(), far, 10);
		UUID mara = UUID.nameUUIDFromBytes("qa-mara".getBytes());
		data.setLeader(far, new Caravans.Leader(mara, "Mara", 4, 150, false));
		PartnerShowsCompatTests.after(helper, () -> {
			data.remove(far);
			level.setBlock(far, Blocks.AIR.defaultBlockState(), 3);
		});
		await(helper, site, far, mara, 200);
	}

	private static void await(GameTestHelper helper, WonderSite site, BlockPos far, UUID mara, int tries) {
		ServerLevel level = helper.getLevel();
		if (level.isLoaded(far) && tries > 0) {
			helper.runAfterDelay(5, () -> await(helper, site, far, mara, tries - 1));
			return;
		}
		helper.assertFalse(level.isLoaded(far), "setup: Riverton's chunk never unloaded");
		helper.assertTrue(Wonders.linked(level, site.hall()).stream().anyMatch(g -> g.pos().equals(far)), "setup: Riverton isn't a caravan partner");
		long today = Chronicle.day(level);
		long day = today % 2 == 0 ? today : today + 1;
		StadiumDays.Spots spots = StadiumDays.spots(level, site).orElseThrow();
		StadiumDays.tick(level, day, StadiumDays.AFTERNOON);
		boolean loadedByIt = level.isLoaded(far);
		List<Villager> visitors = StadiumDays.visitors(level, site, spots);
		helper.assertTrue(visitors.size() >= 2 && visitors.size() <= 4, "visitors: " + visitors.size());
		List<Villager> fromRiverton = visitors.stream().filter(v -> StadiumDays.visitor(v).home().equals(Optional.of(far))).toList();
		List<String> names = visitors.stream().map(v -> v.getDisplayName().getString() + " (" + Trainers.title(v).getString() + ")").toList();
		helper.assertTrue(fromRiverton.size() == 1, "not one visitor from the caravan partner Riverton: " + names);
		Villager v = fromRiverton.get(0);
		helper.assertTrue(Trainers.tier(v) == 4, "Riverton's Trainer is an Expert (4), the visitor is " + Trainers.tier(v));
		helper.assertTrue(v.getDisplayName().getString().equals("Mara of Riverton"),
			"Riverton's Trainer at the Stadium is called '" + v.getDisplayName().getString() + "', not 'Mara of Riverton'");
		for (Villager other : visitors) {
			if (other != v) {
				helper.assertTrue(StadiumDays.visitor(other).home().isEmpty() && Trainers.tier(other) >= 3 && Trainers.tier(other) <= 5,
					"the rest aren't travellers of Journeyman to Master: " + names);
			}
		}
		helper.assertFalse(loadedByIt, "the Stadium Day loaded the far partner's chunk (1,200 blocks off) to name its visitor"); // B644
		StadiumDays.tick(level, day, StadiumDays.DUSK);
		helper.succeed();
	}
}
