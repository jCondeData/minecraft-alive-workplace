package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import java.util.Arrays;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane-e-1031-1913, upgrading old saves): B715's fix gives the wagon-bed list ({@code aliveworkplace_camp_beds}) a
 * save format, version 2. A list saved before it holds every bed the old look ever took, so on the first load each of
 * its beds is "unverified": looked at once more the first time it is asked about (kept if it has the camp's look, a
 * home again if not). On the owner's live server many of those beds sit in chunks nobody loads between the upgrade and
 * the next autosave. So, from the spec ("lists saved before this fix ... are looked at once more"):
 * <ul>
 * <li>an older list's beds that nobody has looked at yet stay unverified through any number of saves (not dropped, so
 * an old camp's bedroll far away is still a wagon bed when looked at; not promoted to listed for good, so a cabin's bed
 * far away still becomes a home again when looked at), and a second save is the first;</li>
 * <li>asking about such a bed while its chunk isn't loaded changes nothing;</li>
 * <li>a bed both listed and unverified in a file (a hand-merged or half-written one) is listed: the camp's record wins;</li>
 * <li>an old file with no beds at all writes the new format and stays empty.</li>
 * </ul>
 */
public class QaCampBedsUpgrade1031GameTests implements FabricGameTest {
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCampBedsUpgrade1031"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCampBedsUpgrade1031")
	public void anOlderListsUnlookedBedsStayUnverifiedThroughSaves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var registries = level.registryAccess();
		// far from every test area and never loaded in a GameTest run
		BlockPos farRoll = new BlockPos(2_000_000, 70, 2_000_000);
		BlockPos farCabin = new BlockPos(-2_000_000, 64, 2_000_016);
		helper.assertTrue(!level.isLoaded(farRoll) && !level.isLoaded(farCabin), "setup: the far beds' chunks are loaded");

		CompoundTag before = new CompoundTag(); // a list from before B715: no version
		before.putLongArray("beds", new long[] {farRoll.asLong(), farCabin.asLong()});
		CampBeds.Data first = CampBeds.Data.load(before, registries);
		helper.assertTrue(first.unverified(farRoll) && first.unverified(farCabin) && !first.has(farRoll) && !first.has(farCabin),
			"after the upgrade's first load: listed " + first.has(farRoll) + "/" + first.has(farCabin) + ", unverified "
				+ first.unverified(farRoll) + "/" + first.unverified(farCabin));

		// asked about while unloaded: nothing changes
		var live = CampBeds.Data.get(level);
		helper.assertTrue(!live.unverified(farRoll), "setup: the live list already holds the far bed");
		helper.assertTrue(!CampBeds.isWagonBed(level, farRoll), "an unloaded bed counted as a wagon bed");

		CompoundTag saved = first.save(new CompoundTag(), registries);
		helper.assertTrue(saved.getInt("version") == 2, "the upgraded list saves version " + saved.getInt("version"));
		helper.assertTrue(saved.getLongArray("beds").length == 0, "unlooked beds were saved as listed for good: " + Arrays.toString(saved.getLongArray("beds")));
		helper.assertTrue(sorted(saved.getLongArray("unverified")).equals(sorted(new long[] {farRoll.asLong(), farCabin.asLong()})),
			"the unverified beds saved: " + Arrays.toString(saved.getLongArray("unverified")));

		CampBeds.Data second = CampBeds.Data.load(saved, registries);
		helper.assertTrue(second.unverified(farRoll) && second.unverified(farCabin) && !second.has(farRoll) && !second.has(farCabin),
			"after the second load the far beds are listed " + second.has(farRoll) + "/" + second.has(farCabin) + ", unverified "
				+ second.unverified(farRoll) + "/" + second.unverified(farCabin));
		CompoundTag again = second.save(new CompoundTag(), registries);
		helper.assertTrue(normal(again).equals(normal(saved)), "a second save differs from the first: " + again + " vs " + saved);

		// a bed both listed and unverified (a half-written or hand-merged file): listed wins
		CompoundTag both = new CompoundTag();
		both.putInt("version", 2);
		both.putLongArray("beds", new long[] {farRoll.asLong()});
		both.putLongArray("unverified", new long[] {farRoll.asLong(), farCabin.asLong()});
		CampBeds.Data merged = CampBeds.Data.load(both, registries);
		helper.assertTrue(merged.has(farRoll) && !merged.unverified(farRoll) && merged.unverified(farCabin) && !merged.has(farCabin),
			"a bed listed and unverified: listed " + merged.has(farRoll) + ", unverified " + merged.unverified(farRoll));
		CompoundTag mergedSaved = merged.save(new CompoundTag(), registries);
		helper.assertTrue(sorted(mergedSaved.getLongArray("unverified")).equals(sorted(new long[] {farCabin.asLong()})),
			"the listed bed was saved as unverified too: " + Arrays.toString(mergedSaved.getLongArray("unverified")));

		// an old empty list: the new format, nothing in it
		CompoundTag empty = CampBeds.Data.load(new CompoundTag(), registries).save(new CompoundTag(), registries);
		helper.assertTrue(empty.getInt("version") == 2 && empty.getLongArray("beds").length == 0
			&& (!empty.contains("unverified", Tag.TAG_LONG_ARRAY) || empty.getLongArray("unverified").length == 0), "an empty old list saves " + empty);
		helper.succeed();
	}

	private static String sorted(long[] values) {
		long[] copy = values.clone();
		Arrays.sort(copy);
		return Arrays.toString(copy);
	}

	/** The tag with its arrays sorted (the list is a hash set: its order isn't the point). */
	private static String normal(CompoundTag tag) {
		return tag.getInt("version") + " " + sorted(tag.getLongArray("beds")) + " " + sorted(tag.getLongArray("unverified"));
	}
}
