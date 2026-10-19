package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeat;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeats;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.Homes;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/**
 * ROADMAP 37.6 with Handcrafted: villagers sit on its chairs at supper and in the evening and stand up at dawn, when hurt
 * or with the switch off; one chair seats one villager; a seat loaded from a save lets its villager go; and a Handcrafted
 * fancy bed is a real villager home: a villager claims it and sleeps in it, the house it's in counts as their home, and
 * the house's chair makes it a furnished home worth beauty to them.
 */
public class FurnitureSeatsCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos CHAIR = new BlockPos(6, 2, 6);

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
	}

	/**
	 * Sets the day time for a test and returns how to put the clock back where it would be without the test: later batches
	 * (a miner's partner at work, for one) run at the time of day the suite reached, not at this test's supper or night.
	 */
	private static Runnable clock(ServerLevel level) {
		long day = level.getDayTime();
		long game = level.getGameTime();
		return () -> level.setDayTime(day + level.getGameTime() - game);
	}

	/** Day time {@code time} today (a later time of day) or tomorrow (an earlier one). */
	private static void setTime(ServerLevel level, long time) {
		long day = level.getDayTime() / 24000;
		long now = level.getDayTime() % 24000;
		level.setDayTime((now <= time ? day : day + 1) * 24000 + time);
	}

	private static BlockPos chair(GameTestHelper helper) {
		Block chair = block("handcrafted:oak_chair");
		helper.assertTrue(chair.defaultBlockState().is(FurnitureSeats.SEATS), "Handcrafted's oak chair isn't a villager seat");
		helper.setBlock(CHAIR, chair.defaultBlockState());
		return helper.absolutePos(CHAIR);
	}

	@org.jetbrains.annotations.Nullable
	private static FurnitureSeat seatOf(Villager v) {
		return v.getVehicle() instanceof FurnitureSeat seat ? seat : null;
	}

	private static List<FurnitureSeat> seats(GameTestHelper helper, BlockPos chair) {
		return helper.getLevel().getEntitiesOfClass(FurnitureSeat.class, new AABB(chair).inflate(1), Entity::isAlive);
	}

	//$ gametest_ticks_batch HUGE_AREA '1200' '"furniture_seat_supper"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "furniture_seat_supper")
	public void aVillagerSitsOnAHandcraftedChairAtSupperAndGetsUpAtDawn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos chair = chair(helper);
		Villager v = helper.spawn(EntityType.VILLAGER, CHAIR.south());
		Runnable restore = clock(level);
		setTime(level, FurnitureSeats.SUPPER + 300);
		boolean[] dawn = {false};
		helper.onEachTick(() -> {
			if (!dawn[0] && seatOf(v) != null) {
				helper.assertTrue(seatOf(v).blockPosition().equals(chair), "seated off the chair at " + seatOf(v).blockPosition().toShortString());
				dawn[0] = true;
				setTime(level, 100); // dawn the next day
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(dawn[0], "the villager hasn't sat on the chair at supper");
			helper.assertFalse(v.isPassenger(), "the villager is still sitting at dawn");
			helper.assertTrue(seats(helper, chair).isEmpty(), "the seat stayed after the villager got up");
			v.discard();
			restore.run();
		});
	}

	//$ gametest_ticks_batch FabricGameTest.EMPTY_STRUCTURE '20' '"furniture_seat_hours"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 20, batch = "furniture_seat_hours")
	public void sittingTimeRunsFromTheFirstTickOfSupperTillBedtime(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Runnable restore = clock(level);
		long[] times = {FurnitureSeats.SUPPER - 1, FurnitureSeats.SUPPER, FurnitureSeats.BEDTIME - 1, FurnitureSeats.BEDTIME};
		boolean[] sitting = new boolean[times.length];
		for (int i = 0; i < times.length; i++) {
			setTime(level, times[i]);
			sitting[i] = FurnitureSeats.sittingTime(level);
		}
		restore.run();
		helper.assertFalse(sitting[0], "sitting time before supper");
		helper.assertTrue(sitting[1], "not sitting time at the first tick of supper");
		helper.assertTrue(sitting[2], "not sitting time just before bed");
		helper.assertFalse(sitting[3], "still sitting time at bedtime");
		helper.succeed();
	}

	//$ gametest_ticks_batch HUGE_AREA '200' '"furniture_seat_riding"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "furniture_seat_riding")
	public void aVillagerRidingAMinecartIsntPulledOntoAChairAtSupper(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos chair = chair(helper);
		Entity cart = helper.spawn(EntityType.MINECART, CHAIR.south());
		Villager v = helper.spawn(EntityType.VILLAGER, CHAIR.south());
		helper.assertTrue(v.startRiding(cart, true), "the villager couldn't get into the minecart");
		Runnable restore = clock(level);
		setTime(level, FurnitureSeats.SUPPER + 600);
		helper.runAfterDelay(100, () -> {
			boolean inCart = v.getVehicle() == cart;
			int onChair = seats(helper, chair).size();
			v.discard();
			cart.discard();
			restore.run();
			helper.assertTrue(inCart, "a villager riding a minecart was taken out of it at supper");
			helper.assertTrue(onChair == 0, onChair + " seats on the chair next to a villager in a minecart");
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch HUGE_AREA '600' '"furniture_seat_once"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 600, batch = "furniture_seat_once")
	public void aSeatInUseIsntTakenTwice(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos chair = chair(helper);
		Villager a = helper.spawn(EntityType.VILLAGER, CHAIR.south());
		Villager b = helper.spawn(EntityType.VILLAGER, CHAIR.east());
		Runnable restore = clock(level);
		setTime(level, FurnitureSeats.SUPPER + 600);
		helper.runAfterDelay(300, () -> {
			restore.run();
			int seated = (seatOf(a) != null ? 1 : 0) + (seatOf(b) != null ? 1 : 0);
			helper.assertTrue(seated == 1, seated + " villagers seated on one chair");
			helper.assertTrue(seats(helper, chair).size() == 1, seats(helper, chair).size() + " seats on one chair");
			helper.assertFalse(FurnitureSeats.free(level, chair, null), "the chair in use counts as free");
			a.discard();
			b.discard();
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch HUGE_AREA '600' '"furniture_seat_danger"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 600, batch = "furniture_seat_danger")
	public void aVillagerStandsUpWhenHurtAndNobodySitsWithTheSwitchOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		chair(helper);
		Villager v = helper.spawn(EntityType.VILLAGER, CHAIR.south());
		Runnable restore = clock(level);
		setTime(level, FurnitureSeats.SUPPER + 600);
		int[] step = {0};
		helper.onEachTick(() -> {
			if (step[0] == 0 && seatOf(v) != null) {
				step[0] = 1;
				v.hurt(level.damageSources().generic(), 0.5f);
			} else if (step[0] == 1 && !v.isPassenger()) {
				step[0] = 2;
				Furnishings.ENABLED = false;
				helper.runAfterDelay(200, () -> {
					boolean sat = v.isPassenger();
					Furnishings.ENABLED = true;
					v.discard();
					restore.run();
					helper.assertFalse(sat, "a villager sat down with furnished builds off");
					helper.succeed();
				});
			}
		});
	}

	//$ gametest_ticks_batch HUGE_AREA '200' '"furniture_seat_saved"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "furniture_seat_saved")
	public void aSeatLoadedFromASaveLetsItsVillagerGo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos chair = chair(helper);
		Villager v = helper.spawn(EntityType.VILLAGER, CHAIR.south());
		FurnitureSeat saved = FurnitureSeat.at(level, chair);
		FurnitureSeat loaded = FurnitureSeat.at(level, chair);
		loaded.load(saved.saveWithoutId(new CompoundTag()));
		level.addFreshEntity(loaded);
		helper.assertTrue(v.startRiding(loaded, true), "the villager couldn't sit on the loaded seat");
		helper.succeedWhen(() -> {
			helper.assertFalse(v.isPassenger(), "the villager is still on a seat loaded from a save");
			helper.assertFalse(loaded.isAlive(), "the loaded seat wasn't removed");
			v.discard();
		});
	}

	//$ gametest_ticks_batch HUGE_AREA '2400' '"furniture_fancy_bed"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "furniture_fancy_bed")
	public void aVillagerClaimsAndSleepsInAFancyBedAndTheHouseIsTheirHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Block fancy = block("handcrafted:oak_fancy_bed");
		helper.assertTrue(fancy instanceof BedBlock && fancy.defaultBlockState().is(BlockTags.BEDS), "Handcrafted's fancy bed isn't a bed");
		BlockPos foot = new BlockPos(5, 2, 6);
		BlockState footState = fancy.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT);
		helper.setBlock(foot, footState);
		helper.setBlock(foot.north(), footState.setValue(BedBlock.PART, BedPart.HEAD));
		BlockPos head = helper.absolutePos(foot.north());
		helper.assertTrue(level.getPoiManager().getType(head).map(t -> t.is(PoiTypes.HOME)).orElse(false), "the fancy bed isn't a villager home");
		helper.setBlock(CHAIR.east(2), block("handcrafted:oak_chair").defaultBlockState());
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)),
			Rotation.NONE, Mirror.NONE);
		sites.recordFinished(StarterBlueprints.STARTER_COTTAGE.id(), placement, UUID.randomUUID());
		Villager v = helper.spawn(EntityType.VILLAGER, foot.south(2));
		Runnable restore = clock(level);
		setTime(level, 13000);
		helper.succeedWhen(() -> {
			BlockPos bed = VillageNeeds.bed(level, v);
			helper.assertTrue(bed != null && level.getBlockState(bed).is(fancy), "the villager hasn't claimed the fancy bed");
			helper.assertTrue(v.isSleeping(), "the villager hasn't gone to sleep in the fancy bed");
			helper.assertTrue(Homes.of(level, v).map(h -> h.structure().equals(StarterBlueprints.STARTER_COTTAGE.id())).orElse(false),
				"the house the fancy bed is in isn't the villager's home");
			int beauty = ClassNeeds.beauty(level, v);
			helper.assertTrue(beauty >= FurnitureSeats.FURNISHED_BEAUTY, "the furnished home adds no beauty (" + beauty + ")");
			Furnishings.ENABLED = false;
			int plain = ClassNeeds.beauty(level, v);
			Furnishings.ENABLED = true;
			helper.assertTrue(plain == beauty - FurnitureSeats.FURNISHED_BEAUTY, "with furnished builds off the home's beauty is " + plain + ", not "
				+ (beauty - FurnitureSeats.FURNISHED_BEAUTY));
			sites.forgetFinished(placement);
			v.discard();
			restore.run();
		});
	}
}
