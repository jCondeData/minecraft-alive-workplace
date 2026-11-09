package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.people.FamilyBook;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/**
 * QA bug test (lane E, saves, 2026-11-02; B796's kind, a list B796 didn't name): a Village Hall keeps the village's
 * family book (who was born to whom, partners, trades; the Family Tree page and the family names) in its saved
 * {@code families} list. It reads that list with a list codec and {@code .result()}, so one entry that can't be read (a
 * hand-edited or damaged file) loses every family in the village, silently. As B796 does for the hall's other lists,
 * the bad entry must be left out and the rest load. The good copy and the damaged copy (an empty entry put first) must
 * give back the same two people.
 */
public class QaHallFamiliesOneBadEntry1102eBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	//$ gametest AREA
	@GameTest(template = AREA)
	public void oneDamagedPersonDoesntLoseTheVillagesWholeFamilyBook(GameTestHelper helper) {
		Leftovers.clear(helper);
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		BlockPos pos = new BlockPos(1, 2, 1);
		helper.setBlock(pos, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(pos);
		UUID mother = new UUID(0x1102L, 0xE01L);
		UUID child = new UUID(0x1102L, 0xE02L);
		hall.families().put(new FamilyBook.Person(mother, Component.literal("Mara"), 7, 100L, true, -1L, null, null, null, "farmer"));
		hall.families().put(new FamilyBook.Person(child, Component.literal("Tib"), 7, 24100L, true, -1L, mother, null, null, ""));
		helper.assertTrue(hall.families().size() == 2, "setup: the family book holds " + hall.families().size() + " of 2 people");

		CompoundTag saved = hall.saveWithoutMetadata(registries);
		helper.assertTrue(saved.get("families") instanceof ListTag list && list.size() == 2, "setup: the saved families aren't two entries: "
			+ saved.get("families"));
		hall.loadWithComponents(saved.copy(), registries);
		helper.assertTrue(hall.families().size() == 2, "setup: a clean save and load gave back " + hall.families().size() + " of 2 people");

		CompoundTag damaged = saved.copy();
		((ListTag) damaged.get("families")).add(0, new CompoundTag()); // one entry that can't be read, first
		hall.loadWithComponents(damaged, registries);
		helper.assertTrue(hall.families().size() == 2 && hall.families().get(mother) != null && hall.families().get(child) != null,
			"one damaged entry in the hall's saved families lost the village's family book: " + hall.families().size()
				+ " of 2 good people back (expected: the damaged one left out with a warning, the rest kept, as B796 does for edicts and guilds)");
		helper.succeed();
	}
}
