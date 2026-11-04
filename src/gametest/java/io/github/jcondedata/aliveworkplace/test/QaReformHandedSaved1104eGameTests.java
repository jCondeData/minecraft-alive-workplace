package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * QA lane E (lane-e-1104-1613, saves): B917's new saved field, a reform step's part-progress handed in the other way
 * ({@code handed}, on the Village Hall). The bug's promise is that half a step is never lost when the story is switched
 * off or on; the owner's server restarts, so it must also come through a save and reload, and a hall saved before B917
 * (no {@code handed}) must load with nothing handed and the rest of its reform progress as it was.
 */
public class QaReformHandedSaved1104eGameTests implements FabricGameTest {
	private static final String SHIFTS = AliveWorkplace.id("long_shifts").toString();
	private static final String BREAD = AliveWorkplace.id("free_bread").toString();

	private static VillageHallBlockEntity reload(GameTestHelper helper, CompoundTag tag) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		VillageHallBlockEntity loaded = new VillageHallBlockEntity(pos, ModBlocks.VILLAGE_HALL.defaultBlockState());
		loaded.loadWithComponents(tag, helper.getLevel().registryAccess());
		return loaded;
	}

	/** Two reforms part-way through their steps (14 clocks, 200 wheat) keep what was handed in through a save and reload. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void halfAStepHandedInComesThroughASaveAndReload(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		VillageHallBlockEntity hall = new VillageHallBlockEntity(pos, ModBlocks.VILLAGE_HALL.defaultBlockState());
		hall.setReforms(List.of(new Reforms.Progress(SHIFTS, 0, false, 7L, 14), new Reforms.Progress(BREAD, 1, false, 9L, 200)));
		CompoundTag saved = hall.saveWithoutMetadata(helper.getLevel().registryAccess());
		VillageHallBlockEntity loaded = reload(helper, saved);
		helper.assertTrue(Reforms.handed(loaded, SHIFTS, 0) == 14,
			"the Shift Bell's step 1 holds " + Reforms.handed(loaded, SHIFTS, 0) + " of the 14 clocks handed in after a reload: " + loaded.reforms());
		helper.assertTrue(Reforms.handed(loaded, BREAD, 1) == 200,
			"the Free Bread's step 2 holds " + Reforms.handed(loaded, BREAD, 1) + " of the 200 wheat handed in after a reload: " + loaded.reforms());
		helper.assertTrue(loaded.reforms().equals(hall.reforms()), "the reforms' progress changed through a save: " + hall.reforms() + " -> " + loaded.reforms());
		// And a second save of the loaded hall changes nothing (the field isn't dropped on the way back out).
		VillageHallBlockEntity twice = reload(helper, loaded.saveWithoutMetadata(helper.getLevel().registryAccess()));
		helper.assertTrue(twice.reforms().equals(hall.reforms()), "the reforms' progress changed through a second save: " + twice.reforms());
		helper.succeed();
	}

	/** A hall saved before B917: its reform entries have no "handed"; they load with nothing handed and the rest kept. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aHallSavedBeforeTheFieldLoadsWithNothingHandedAndItsStepsKept(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		CompoundTag tag = new VillageHallBlockEntity(pos, ModBlocks.VILLAGE_HALL.defaultBlockState())
			.saveWithoutMetadata(helper.getLevel().registryAccess());
		ListTag old = new ListTag();
		CompoundTag shifts = new CompoundTag();
		shifts.putString("id", SHIFTS);
		shifts.putInt("step", 2);
		shifts.putBoolean("reformed", false);
		shifts.putLong("nextDay", 12L);
		old.add(shifts);
		CompoundTag bread = new CompoundTag();
		bread.putString("id", BREAD);
		bread.putInt("step", 3);
		bread.putBoolean("reformed", true);
		old.add(bread);
		tag.put("reforms", old);
		VillageHallBlockEntity loaded = reload(helper, tag);
		List<Reforms.Progress> want = List.of(new Reforms.Progress(SHIFTS, 2, false, 12L, 0), new Reforms.Progress(BREAD, 3, true, 0L, 0));
		helper.assertTrue(loaded.reforms().equals(want), "an old hall's reforms loaded as " + loaded.reforms() + ", expected " + want);
		helper.assertTrue(Reforms.handed(loaded, SHIFTS, 2) == 0, "an old hall's step holds " + Reforms.handed(loaded, SHIFTS, 2) + " handed in");
		helper.succeed();
	}
}
