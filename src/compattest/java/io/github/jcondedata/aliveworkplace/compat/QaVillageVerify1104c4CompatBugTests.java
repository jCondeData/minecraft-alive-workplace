package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeat;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeats;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1104-1254), B676 follow-up (37.6): a villager who picked a chair it can't reach (penned in glass 4 blocks
 * off) keeps it from everyone for good: a villager standing right beside the empty chair never sits, though the penned
 * one gives up after 600 ticks (it picks the same chair again at once). Expected: a chair nobody can reach isn't kept
 * from the villager beside it; once the walker gives up, the chair is free for others.
 */
public class QaVillageVerify1104c4CompatBugTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos CHAIR = new BlockPos(6, 2, 6);

	private static BlockPos chair(GameTestHelper helper, BlockPos at) {
		Block chair = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("handcrafted:oak_chair"));
		helper.assertTrue(chair.defaultBlockState().is(FurnitureSeats.SEATS), "Handcrafted's oak chair isn't a villager seat");
		helper.setBlock(at, chair.defaultBlockState());
		return helper.absolutePos(at);
	}

	@org.jetbrains.annotations.Nullable
	private static FurnitureSeat seatOf(Villager v) {
		return v.getVehicle() instanceof FurnitureSeat seat ? seat : null;
	}

	private static List<FurnitureSeat> seats(GameTestHelper helper, BlockPos chair) {
		return helper.getLevel().getEntitiesOfClass(FurnitureSeat.class, new AABB(chair).inflate(1), Entity::isAlive);
	}

	/** Supper, the clock kept running from there; returns what puts the clock back. */
	private static Runnable supper(ServerLevel level) {
		long day = level.getDayTime();
		long game = level.getGameTime();
		long now = day % 24000;
		long time = FurnitureSeats.SUPPER + 300;
		level.setDayTime((now <= time ? day / 24000 : day / 24000 + 1) * 24000 + time);
		return () -> level.setDayTime(day + level.getGameTime() - game);
	}

	/**
	 * A villager penned in glass 4 blocks from a chair picks it but can't reach it; a villager then comes right beside
	 * the chair. Once the penned one gives up (600 ticks), the one beside it sits: a chair nobody can reach isn't kept
	 * from everyone for good.
	 */
	//$ gametest_ticks_batch HUGE_AREA '1600' '"qa1104c4_penned_walker"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1600, batch = "qa1104c4_penned_walker")
	public void aVillagerWhoCantReachAChairDoesntKeepItFromTheOneBesideIt(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos chair = chair(helper, CHAIR);
		BlockPos pen = CHAIR.west(4);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = 2; y <= 4; y++) {
					if (dx != 0 || dz != 0 || y == 4) {
						helper.setBlock(pen.offset(dx, y - 2, dz), Blocks.GLASS);
					}
				}
			}
		}
		Villager penned = helper.spawn(EntityType.VILLAGER, pen);
		Runnable restore = supper(level);
		Villager[] beside = {null};
		long[] pickedAt = {-1};
		helper.onEachTick(() -> {
			if (beside[0] == null && !FurnitureSeats.free(level, chair, null) && seats(helper, chair).isEmpty()) {
				pickedAt[0] = helper.getTick();
				beside[0] = helper.spawn(EntityType.VILLAGER, CHAIR.south());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(beside[0] != null, "setup: the penned villager never picked the chair");
			helper.assertFalse(penned.isPassenger(), "setup: the penned villager reached the chair");
			helper.assertTrue(seatOf(beside[0]) != null, "the villager right beside the chair hasn't sat " + (helper.getTick() - pickedAt[0])
				+ " ticks after the penned villager (who can't reach it) picked it; the chair is free: " + FurnitureSeats.free(level, chair, beside[0]));
			penned.discard();
			beside[0].discard();
			restore.run();
		});
	}

}
