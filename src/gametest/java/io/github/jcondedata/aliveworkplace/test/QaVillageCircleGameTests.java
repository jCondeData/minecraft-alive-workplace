package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Mourning;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * Helpers for the QA tests of the village's circle ({@link VillageHalls#contains}: RADIUS across, HEIGHT up or down),
 * used by {@link QaVillageCircleBugTests}. No tests of its own.
 */
final class QaVillageCircleGameTests {
	/** Where the tests put the hall, as {@link QaVillageCircleBugTests} does (big_area). */
	static final BlockPos HALL = new BlockPos(11, 2, 11);

	private QaVillageCircleGameTests() {
	}

	/** A Village Hall at {@link #HALL} with a radius of 16, mourning on; the switches are as they were when the test ends. */
	static void mourningVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		boolean mourning = Mourning.ENABLED;
		boolean ages = LifeStages.AGES;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Mourning.ENABLED = mourning;
			LifeStages.AGES = ages;
			VillageHalls.forgetNearest();
			VillageNeeds.forget();
			Moods.forget();
		});
		VillageHalls.RADIUS = 16;
		Mourning.ENABLED = true;
		VillageHalls.forgetNearest();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	/** The hall's needs met and the caches forgotten, so the village's mood doesn't get in the way. */
	static VillageHallBlockEntity mourningReady(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		VillageNeeds.forget();
		Moods.forget();
		return hall;
	}

	/** Checks the staging: every spot of {@code in} is in the village round {@code hall}, none of {@code out} is. */
	static void staged(GameTestHelper helper, BlockPos hall, List<BlockPos> in, List<BlockPos> out) {
		for (BlockPos p : in) {
			helper.assertTrue(VillageHalls.contains(hall, p), "staging: " + p.subtract(hall) + " from the hall isn't in the village");
		}
		for (BlockPos p : out) {
			helper.assertTrue(!VillageHalls.contains(hall, p), "staging: " + p.subtract(hall) + " from the hall is in the village");
		}
	}

	/** A still, grown villager named {@code name} with the job {@code job}, at the absolute position {@code at}. */
	static Villager worker(GameTestHelper helper, BlockPos at, String name, VillagerProfession job) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
		helper.getLevel().addFreshEntity(v);
		return v;
	}
}
