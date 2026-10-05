package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.CradleSeat;
import io.github.jcondedata.aliveworkplace.hall.Cradles;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * The Cradle (ROADMAP 30.12): within 4 blocks of a bed in a village with a hall, children grow up in half the time
 * (12000 ticks, not 24000: each hall round ages them by its length again), one more baby a day may be born (half a day
 * apart; a third of a day with Large Families), and at night a child sleeps in it, seated. A cradle with no bed near
 * counts for nothing, a seated child is saved with its seat, and with {@code cradles} off it's furniture. The aging is
 * worked through hall rounds of {@link VillageNeeds#CHECK_EVERY} ticks with the ticks between them added by hand, so a day
 * takes no real day. Each test has its own batch and leaves no hall or cradle behind.
 */
public class CradleGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos CHEST = new BlockPos(17, 2, 19);
	/** The bed's head at (5, 2, 16); the cradle beside it, 2 blocks away. */
	private static final BlockPos CRADLE = new BlockPos(7, 2, 16);
	/** 6 blocks from the bed's head: too far. */
	private static final BlockPos FAR_CRADLE = new BlockPos(11, 2, 16);
	private static final ResourceLocation LARGE_FAMILIES = AliveWorkplace.id("large_families");

	/** A child grows up in 12000 ticks with a cradle (not yet at 11400) and in 24000 without (still a child at 12000). */
	//$ gametest_ticks_batch AREA '100' '"cradleGrowUp"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "cradleGrowUp")
	public void aChildGrowsUpInHalfTheTimeWithACradle(GameTestHelper helper) {
		village(helper, true);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ready(helper);
			helper.assertTrue(Cradles.nursery(level, hall), "a cradle beside a bed: not a nursery village");
			Villager child = child(helper, new BlockPos(9, 2, 9));
			helper.assertTrue(rounds(level, hall, child, 19) && child.isBaby(), "grown up after 11400 ticks: " + child.getAge());
			helper.assertTrue(rounds(level, hall, child, 1) && !child.isBaby(), "still a child after 12000 ticks: " + child.getAge());

			helper.setBlock(CRADLE, Blocks.AIR);
			helper.assertTrue(!Cradles.nursery(level, hall), "a nursery with the cradle gone");
			Villager other = child(helper, new BlockPos(9, 2, 10));
			helper.assertTrue(rounds(level, hall, other, 20) && other.isBaby(), "grown up in 12000 ticks without a cradle");
			helper.assertTrue(rounds(level, hall, other, 19) && other.isBaby(), "grown up in 23400 ticks without a cradle: " + other.getAge());
			helper.assertTrue(rounds(level, hall, other, 1) && !other.isBaby(), "still a child after 24000 ticks: " + other.getAge());
			helper.succeed();
		});
	}

	/** A second birth the same day with a cradle and not without; with Large Families too, births a third of a day apart. */
	//$ gametest_ticks_batch AREA '100' '"cradleBirths"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "cradleBirths")
	public void aCradleMeansOneMoreBabyADay(GameTestHelper helper) {
		village(helper, false);
		helper.setBlock(new BlockPos(17, 2, 17), ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST, Blocks.CHEST);
		helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8)).setNoAi(true);
		helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 8)).setNoAi(true);
		for (int x : new int[] {3, 13}) {
			bed(helper, x);
		}
		VillageNeeds.Needs happy = new VillageNeeds.Needs(2, 2, 2, 2, 2, 0, 0, 1f);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ready(helper);
			Container chest = helper.getBlockEntity(CHEST);
			chest.setItem(0, new ItemStack(Items.BREAD, 64));
			long now = level.getGameTime();

			// Without a cradle: a day apart.
			helper.assertTrue(VillageGrowth.every(level, hall) == 24000, "usual: " + VillageGrowth.every(level, hall));
			helper.assertTrue(VillageGrowth.blocker(level, hall, happy, now - 12000) == VillageGrowth.Blocker.TOO_SOON, "half a day after a birth, no cradle");

			// A cradle by a bed: half a day apart, two babies in a day.
			helper.setBlock(CRADLE, ModBlocks.CRADLE);
			helper.assertTrue(VillageGrowth.every(level, hall) == 12000, "with a cradle: " + VillageGrowth.every(level, hall));
			helper.assertTrue(VillageGrowth.blocker(level, hall, happy, now - 11999) == VillageGrowth.Blocker.TOO_SOON, "11999 ticks after a birth");
			born(helper, level, hall, chest, happy, now - 12000);
			born(helper, level, hall, chest, happy, now - 12000);

			// A second cradle adds nothing.
			helper.setBlock(new BlockPos(14, 2, 17), ModBlocks.CRADLE);
			helper.assertTrue(Cradles.cradles(level, hall).size() == 2 && VillageGrowth.every(level, hall) == 12000,
				"two cradles: " + VillageGrowth.every(level, hall));

			// With Large Families as well: three a day, a third of a day apart.
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(LARGE_FAMILIES).orElseThrow()).done(), "proclaimed");
			helper.assertTrue(VillageGrowth.every(level, hall) == 8000, "Large Families and a cradle: " + VillageGrowth.every(level, hall));
			chest.setItem(0, new ItemStack(Items.BREAD, 64));
			helper.assertTrue(VillageGrowth.blocker(level, hall, happy, now - 7999) == VillageGrowth.Blocker.TOO_SOON, "7999 ticks after a birth");
			born(helper, level, hall, chest, happy, now - 8000);
			helper.succeed();
		});
	}

	/** A cradle 6 blocks from the nearest bed doesn't count: no faster growing, no extra baby. */
	//$ gametest_ticks_batch AREA '100' '"cradleNoBed"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "cradleNoBed")
	public void aCradleWithNoBedNearDoesntCount(GameTestHelper helper) {
		village(helper, false);
		bed(helper, 5);
		helper.setBlock(FAR_CRADLE, ModBlocks.CRADLE);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ready(helper);
			helper.assertTrue(!Cradles.nursery(level, hall) && VillageGrowth.every(level, hall) == 24000, "a cradle 6 blocks from a bed counted");
			Villager child = child(helper, new BlockPos(9, 2, 9));
			helper.assertTrue(rounds(level, hall, child, 20) && child.isBaby() && child.getAge() == -12000, "aged faster: " + child.getAge());
			helper.assertTrue(Cradles.status(level, hall).getString().startsWith("No Cradle near a bed"), "status: " + Cradles.status(level, hall).getString());
			// Moved beside the bed, it counts.
			helper.setBlock(FAR_CRADLE, Blocks.AIR);
			helper.setBlock(CRADLE, ModBlocks.CRADLE);
			helper.assertTrue(Cradles.nursery(level, hall), "beside the bed: not counted");
			helper.assertTrue(Cradles.status(level, hall).getString().equals("A Cradle near a bed: children grow up twice as fast, one more baby a day"),
				"status: " + Cradles.status(level, hall).getString());
			helper.succeed();
		});
	}

	/**
	 * At night the hall's round seats a child of the house in the cradle; at dawn the child gets up again. A broken
	 * cradle lets the child go. The seat and its child are saved together: saved and loaded, the child still sits in it.
	 */
	//$ gametest_ticks_batch AREA '200' '"cradleNight"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "cradleNight")
	public void aChildSleepsInTheCradleAtNight(GameTestHelper helper) {
		village(helper, true);
		long time = helper.getLevel().getDayTime();
		Leftovers.after(helper, () -> helper.getLevel().setDayTime(time));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ready(helper);
			Villager child = child(helper, new BlockPos(5, 2, 13));
			level.setDayTime(1000);
			Cradles.round(level, hall, 0);
			helper.assertTrue(!child.isPassenger(), "seated by day");
			level.setDayTime(18000);
			Cradles.round(level, hall, 0);
			helper.assertTrue(child.getVehicle() instanceof CradleSeat seat && seat.blockPosition().equals(helper.absolutePos(CRADLE)),
				"not in the cradle at night: " + child.getVehicle());
			Cradles.round(level, hall, 0);
			helper.assertTrue(level.getEntitiesOfClass(CradleSeat.class, new net.minecraft.world.phys.AABB(helper.absolutePos(CRADLE)).inflate(2)).size() == 1,
				"a second seat in one cradle");

			// Saved and loaded with its seat.
			CradleSeat seat = (CradleSeat) child.getVehicle();
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(seat.save(tag), "the seat wasn't saved");
			seat.ejectPassengers();
			seat.discard();
			child.discard();
			Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
			helper.assertTrue(loaded instanceof CradleSeat && loaded.getPassengers().size() == 1 && loaded.getPassengers().get(0) instanceof Villager v && v.isBaby(),
				"loaded: " + loaded + " with " + (loaded == null ? "" : loaded.getPassengers()));
			level.addFreshEntityWithPassengers(loaded);
			Villager back = (Villager) loaded.getPassengers().get(0);
			back.setNoAi(true);
			helper.runAfterDelay(25, () -> {
				helper.assertTrue(back.getVehicle() == loaded && loaded.isAlive(), "the loaded child left the cradle at night");
				// Dawn: up again, the seat gone.
				level.setDayTime(24000 + 500);
				helper.runAfterDelay(25, () -> {
					helper.assertTrue(!back.isPassenger() && !loaded.isAlive(), "still in the cradle by day");
					// The next night, the cradle broken under the child: let go.
					level.setDayTime(18000);
					Cradles.round(level, hall, 0);
					helper.assertTrue(back.getVehicle() instanceof CradleSeat, "not seated the next night");
					Entity again = back.getVehicle();
					helper.setBlock(CRADLE, Blocks.AIR);
					helper.runAfterDelay(25, () -> {
						helper.assertTrue(!back.isPassenger() && !again.isAlive(), "still seated in a broken cradle");
						helper.succeed();
					});
				});
			});
		});
	}

	/** With {@code cradles} off, a cradle is furniture: no nursery, no extra baby, no faster growing, nobody seated. Its recipe. */
	//$ gametest_ticks_batch AREA '100' '"cradleOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "cradleOff")
	public void cradlesOffMakesThemFurniture(GameTestHelper helper) {
		village(helper, true);
		long time = helper.getLevel().getDayTime();
		Leftovers.after(helper, () -> {
			Cradles.ENABLED = true;
			helper.getLevel().setDayTime(time);
		});
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ready(helper);
			Cradles.ENABLED = false;
			Villager child = child(helper, new BlockPos(5, 2, 13));
			helper.assertTrue(!Cradles.nursery(level, hall) && VillageGrowth.every(level, hall) == 24000, "off: still a nursery");
			helper.assertTrue(rounds(level, hall, child, 20) && child.getAge() == -12000, "off: aged faster " + child.getAge());
			level.setDayTime(18000);
			Cradles.round(level, hall, 0);
			helper.assertTrue(!child.isPassenger(), "off: seated");
			helper.assertTrue(Cradles.status(level, hall).getString().equals("Cradles are off in the config"), "off status: " + Cradles.status(level, hall).getString());

			// Planks, sticks and white wool make one.
			CraftingInput input = CraftingInput.of(3, 3, List.of(
				new ItemStack(Items.WHITE_WOOL), ItemStack.EMPTY, new ItemStack(Items.WHITE_WOOL),
				new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.WHITE_WOOL), new ItemStack(Items.OAK_PLANKS),
				new ItemStack(Items.STICK), ItemStack.EMPTY, new ItemStack(Items.STICK)));
			ItemStack made = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
				.map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
			helper.assertTrue(made.is(ModBlocks.CRADLE.asItem()), "the recipe made " + made);
			helper.succeed();
		});
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	/** Hall rounds of {@link VillageNeeds#CHECK_EVERY} ticks, each with the ticks before it; true (for chaining). */
	private static boolean rounds(ServerLevel level, BlockPos hall, Villager child, int n) {
		int round = VillageNeeds.CHECK_EVERY;
		for (int i = 0; i < n; i++) {
			child.setAge(Math.min(0, child.getAge() + round)); // the round's own ticks
			Cradles.round(level, hall, round);
		}
		return true;
	}

	/** A newborn (24000 ticks from grown up), standing still. */
	private static Villager child(GameTestHelper helper, BlockPos at) {
		Villager child = helper.spawn(EntityType.VILLAGER, at);
		child.setAge(-24000);
		child.setNoAi(true);
		return child;
	}

	/** A bed at x, its foot at z = 15 and head at z = 16. */
	private static void bed(GameTestHelper helper, int x) {
		helper.setBlock(new BlockPos(x, 2, 15), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(new BlockPos(x, 2, 16), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
	}

	private static void born(GameTestHelper helper, ServerLevel level, BlockPos hall, Container chest, VillageNeeds.Needs needs, long lastBirth) {
		chest.setItem(0, new ItemStack(Items.BREAD, 64));
		Villager baby = VillageGrowth.grow(level, hall, needs, lastBirth);
		helper.assertTrue(baby != null && baby.isBaby(), "no baby " + (level.getGameTime() - lastBirth) + " ticks after the last: "
			+ VillageGrowth.blocker(level, hall, needs, lastBirth));
		baby.setNoAi(true);
	}

	/**
	 * A village of radius 16 with its hall (and, if {@code cradle}, a bed and a cradle beside it); afterwards no hall,
	 * edict or cradle is left here and every cache is asked again.
	 */
	private static void village(GameTestHelper helper, boolean cradle) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		if (cradle) {
			bed(helper, 5);
			helper.setBlock(CRADLE, ModBlocks.CRADLE);
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(HALL)) instanceof VillageHallBlockEntity hall) {
				hall.setEdicts(List.of());
				hall.setReforms(List.of());
			}
			helper.getLevel().setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
			helper.getLevel().setBlockAndUpdate(helper.absolutePos(CRADLE), Blocks.AIR.defaultBlockState());
			helper.getLevel().setBlockAndUpdate(helper.absolutePos(FAR_CRADLE), Blocks.AIR.defaultBlockState());
			helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(14, 2, 17)), Blocks.AIR.defaultBlockState());
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			CivicEffects.forget();
			Moods.forget();
		});
	}

	/** The hall after its first round: a Hamlet with nothing in force, every cache asked again. */
	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setRank(VillageRanks.Rank.HAMLET);
		hall.setEdicts(List.of());
		hall.setReforms(List.of());
		VillageNeeds.forget();
		CivicEffects.forget();
		Moods.forget();
		return hall;
	}
}
