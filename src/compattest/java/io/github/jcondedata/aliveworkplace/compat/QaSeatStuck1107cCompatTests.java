package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeat;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeats;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA lane-c 2026-11-07, B677 and B967 (written from the spec in docs/wiki/furniture-and-storage.md, not from the code): a
 * villager who gets no closer to their seat for 8 seconds (160 ticks) gives up on it early. That rule is for a villager
 * who is penned in, not for one who is walking round a wall, nor for one whose way was blocked for a moment; and the
 * switch turned off mid-walk means nobody sits, and turned on again, the chair is free to sit on.
 */
public class QaSeatStuck1107cCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos CHAIR = new BlockPos(13, 2, 10);

	private static Block oakChair(GameTestHelper helper) {
		Block chair = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("handcrafted:oak_chair"));
		helper.assertTrue(chair.defaultBlockState().is(FurnitureSeats.SEATS), "Handcrafted's oak chair isn't a villager seat");
		return chair;
	}

	private static Runnable clock(ServerLevel level) {
		long day = level.getDayTime();
		long game = level.getGameTime();
		return () -> level.setDayTime(day + level.getGameTime() - game);
	}

	private static void setTime(ServerLevel level, long time) {
		long day = level.getDayTime() / 24000;
		long now = level.getDayTime() % 24000;
		level.setDayTime((now <= time ? day : day + 1) * 24000 + time);
	}

	private static BlockPos seatPos(Villager v) {
		return v.getVehicle() instanceof FurnitureSeat seat ? seat.blockPosition() : null;
	}

	/** A 3x3 glass pen, 3 tall with a roof, round {@code centre} (relative); {@code block} air opens it. */
	private static void pen(GameTestHelper helper, BlockPos centre, Block block) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = 0; y <= 2; y++) {
					if (dx != 0 || dz != 0 || y == 2) {
						helper.setBlock(centre.offset(dx, y, dz), block);
					}
				}
			}
		}
	}

	/**
	 * The chair is 4 blocks east of the villager behind a glass wall (3 tall) whose only gap is 6 blocks to the south: his
	 * way there first leads away from it for several seconds (a door at the end of the room). He is walking round, not
	 * stuck, so he keeps the chair and sits on it.
	 */
	//$ gametest_ticks_batch HUGE_AREA '700' '"qa1107c_seat_detour"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 700, batch = "qa1107c_seat_detour")
	public void aVillagerWalkingRoundAWallToHisChairDoesntGiveItUp(GameTestHelper helper) {
		detour(helper, 6);
	}

	/** The chair behind a wall whose gap is {@code gap} blocks south of it; the villager starts 4 blocks west. */
	private static void detour(GameTestHelper helper, int gap) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(CHAIR, oakChair(helper).defaultBlockState());
		BlockPos chair = helper.absolutePos(CHAIR);
		Vec3 chairAt = Vec3.atBottomCenterOf(chair);
		// The wall two blocks west of the chair, from the area's north edge to {@code gap - 1} blocks south of the chair.
		for (int z = 0; z < CHAIR.getZ() + gap; z++) {
			for (int y = 0; y <= 2; y++) {
				helper.setBlock(new BlockPos(CHAIR.getX() - 2, CHAIR.getY() + y, z), Blocks.GLASS);
			}
		}
		Villager v = helper.spawn(EntityType.VILLAGER, CHAIR.west(4));
		Vec3 spot = v.position();
		double startDistance = spot.distanceTo(chairAt);
		Runnable restore = clock(level);
		setTime(level, FurnitureSeats.SUPPER + 300);
		long[] pickedAt = {-1};
		double[] farthest = {0};
		String[] gaveUp = {null};
		helper.onEachTick(() -> {
			boolean walking = chair.equals(FurnitureSeats.walkingTo(v));
			if (pickedAt[0] < 0) {
				if (walking) {
					pickedAt[0] = helper.getTick();
				} else if (!v.isPassenger()) {
					v.teleportTo(spot.x, spot.y, spot.z); // held till he picks, so a stroll can't put him past the wall's end
				}
			} else {
				farthest[0] = Math.max(farthest[0], v.position().distanceTo(chairAt));
				if (!walking && !v.isPassenger() && gaveUp[0] == null) {
					gaveUp[0] = "the villager walking round the wall gave up on his chair " + (helper.getTick() - pickedAt[0])
						+ " ticks after setting off (he started " + String.format("%.1f", startDistance) + " blocks from it and went "
						+ String.format("%.1f", farthest[0]) + " away); " + FurnitureSeats.describe(level, v);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(pickedAt[0] >= 0, "setup: the villager never set off for the chair; " + FurnitureSeats.describe(level, v));
			helper.assertTrue(gaveUp[0] == null, gaveUp[0]);
			helper.assertTrue(chair.equals(seatPos(v)), "the villager hasn't sat on the chair " + (helper.getTick() - pickedAt[0])
				+ " ticks after setting off round the wall; " + FurnitureSeats.describe(level, v));
			helper.assertTrue(farthest[0] >= startDistance + 2, "setup: the villager sat without walking round the wall (he started "
				+ String.format("%.1f", startDistance) + " blocks from the chair and never went farther than " + String.format("%.1f", farthest[0]) + ")");
			v.discard();
			restore.run();
		});
	}

	/**
	 * A villager in a glass pen 4 blocks from the chair picks it; the pen is opened 80 ticks later (a door shut for a
	 * moment, well inside the 160 ticks of getting no closer). He walks over and sits on it.
	 */
	//$ gametest_ticks_batch HUGE_AREA '700' '"qa1107c_seat_opened"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 700, batch = "qa1107c_seat_opened")
	public void aVillagerWhoseWayWasBlockedForAMomentStillSits(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(CHAIR, oakChair(helper).defaultBlockState());
		BlockPos chair = helper.absolutePos(CHAIR);
		BlockPos penAt = CHAIR.west(4);
		pen(helper, penAt, Blocks.GLASS);
		Villager v = helper.spawn(EntityType.VILLAGER, penAt);
		Runnable restore = clock(level);
		setTime(level, FurnitureSeats.SUPPER + 300);
		long[] at = {-1, -1};
		helper.onEachTick(() -> {
			if (at[0] < 0 && chair.equals(FurnitureSeats.walkingTo(v))) {
				at[0] = helper.getTick();
				helper.runAfterDelay(80, () -> {
					pen(helper, penAt, Blocks.AIR);
					at[1] = helper.getTick();
				});
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(at[0] >= 0, "setup: the penned villager never set off for the chair; " + FurnitureSeats.describe(level, v));
			helper.assertTrue(at[1] >= 0, "waiting for the pen to open");
			helper.assertTrue(chair.equals(seatPos(v)), "the villager hasn't sat on the chair " + (helper.getTick() - at[1])
				+ " ticks after his pen was opened (" + (at[1] - at[0]) + " ticks after he set off); " + FurnitureSeats.describe(level, v));
			v.discard();
			restore.run();
		});
	}

	/**
	 * A villager sets off for a chair 6 blocks away and the furnishings switch is turned off on the way: for the next 200
	 * ticks he doesn't sit and the chair isn't held for him. With the switch on again he sits on it.
	 */
	//$ gametest_ticks_batch HUGE_AREA '900' '"qa1107c_seat_switch_off"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 900, batch = "qa1107c_seat_switch_off")
	public void aVillagerWalkingToAChairWhenTheSwitchGoesOffDoesntSit(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(CHAIR, oakChair(helper).defaultBlockState());
		BlockPos chair = helper.absolutePos(CHAIR);
		Villager v = helper.spawn(EntityType.VILLAGER, CHAIR.west(6));
		Runnable restore = clock(level);
		setTime(level, FurnitureSeats.SUPPER + 300);
		boolean wasOn = Furnishings.ENABLED;
		helper.assertTrue(wasOn, "setup: furnishings are off before the test");
		int[] step = {0};
		long[] at = {-1, -1};
		String[] broken = {null};
		helper.onEachTick(() -> {
			if (step[0] == 0 && chair.equals(FurnitureSeats.walkingTo(v)) && !v.isPassenger()) {
				step[0] = 1;
				at[0] = helper.getTick();
				Furnishings.ENABLED = false;
				helper.runAfterDelay(200, () -> {
					Furnishings.ENABLED = wasOn;
					step[0] = 2;
					at[1] = helper.getTick();
				});
			} else if (step[0] == 1 && broken[0] == null) {
				if (v.isPassenger()) {
					broken[0] = "the villager sat down " + (helper.getTick() - at[0]) + " ticks after the switch went off";
				} else if (helper.getTick() - at[0] > 40 && !FurnitureSeats.free(level, chair, null)) {
					broken[0] = "the chair was still held " + (helper.getTick() - at[0]) + " ticks after the switch went off; "
						+ FurnitureSeats.describe(level, v);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(step[0] >= 1, "setup: the villager never set off for the chair; " + FurnitureSeats.describe(level, v));
			helper.assertTrue(broken[0] == null, broken[0]);
			helper.assertTrue(step[0] >= 2, "waiting with the switch off");
			helper.assertTrue(chair.equals(seatPos(v)), "the villager hasn't sat on the chair " + (helper.getTick() - at[1])
				+ " ticks after the switch came back on; " + FurnitureSeats.describe(level, v));
			v.discard();
			restore.run();
		});
	}
}
