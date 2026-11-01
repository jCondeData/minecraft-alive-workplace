package io.github.jcondedata.aliveworkplace.test;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * QA (lane E, saves, 2026-11-01), bugs found by {@link QaSavedDataOneBadEntry1101GameTests}: one damaged entry in these
 * lists makes the file's load throw, and Minecraft then drops the whole file (every bounty; every caravan village, route
 * and shipment; every village's quests and arcs). Each test puts the damaged entries the main test does first in the list
 * and loads the file; it fails while the load throws.
 */
public class QaSavedDataOneBadEntry1101BugTests implements FabricGameTest {

	private static void loadsWithOneBadEntry(GameTestHelper helper, String className, String list) {
		QaSavedDataCollections1101GameTests.Maker maker = new QaSavedDataCollections1101GameTests.Maker();
		maker.registries = helper.getLevel().registryAccess();
		List<String> threw = new ArrayList<>();
		int tried = 0;
		try {
			Class<?> c = Class.forName(QaSavedDataCollections1101GameTests.MOD + className);
			CompoundTag tag = ((SavedData) QaSavedDataOneBadEntry1101GameTests.sample(c, maker, new ArrayList<>())).save(new CompoundTag(),
				helper.getLevel().registryAccess());
			List<List<Object>> paths = new ArrayList<>();
			QaSavedDataOneBadEntry1101GameTests.lists(tag, new ArrayList<>(), paths);
			for (List<Object> path : paths) {
				if (!QaSavedDataOneBadEntry1101GameTests.pattern(QaSavedDataOneBadEntry1101GameTests.show(path)).equals(list)) {
					continue;
				}
				for (Tag bad : QaSavedDataOneBadEntry1101GameTests.damaged((ListTag) QaSavedDataOneBadEntry1101GameTests.at(tag, path))) {
					tried++;
					CompoundTag hurt = tag.copy();
					((ListTag) QaSavedDataOneBadEntry1101GameTests.at(hurt, path)).add(0, bad);
					try {
						QaSavedDataCollections1101GameTests.loader(c).invoke(null, hurt, helper.getLevel().registryAccess());
					} catch (java.lang.reflect.InvocationTargetException x) {
						threw.add(QaSavedDataOneBadEntry1101GameTests.show(path) + " with " + bad + " first: " + x.getCause());
					}
				}
			}
		} catch (Exception x) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("the sample of " + className + " couldn't be made: " + x);
		}
		helper.assertTrue(tried > 0, "no list " + list + " in the saved " + className);
		helper.assertTrue(threw.isEmpty(), "one damaged entry makes the whole " + className + " file fail to load (Minecraft drops it): " + threw);
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aClaimedBountyIdThatIsntAUuidDoesntLoseEveryBounty(GameTestHelper helper) {
		loadsWithOneBadEntry(helper, "bounty.Bounties$Data", "claimed");
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aCaravanVillageWithoutANameDoesntLoseEveryVillage(GameTestHelper helper) {
		loadsWithOneBadEntry(helper, "hall.Caravans$Data", "villages");
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aCaravanSaleWithoutABuyerDoesntLoseEveryVillage(GameTestHelper helper) {
		loadsWithOneBadEntry(helper, "hall.Caravans$Data", "sales");
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aDamagedMoodBoostDoesntLoseEveryVillagesQuests(GameTestHelper helper) {
		loadsWithOneBadEntry(helper, "story.Stories$Data", "halls[].mood_boosts");
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aTrackedQuestWithoutAPlayerDoesntLoseEveryVillagesQuests(GameTestHelper helper) {
		loadsWithOneBadEntry(helper, "story.Stories$Data", "tracked");
	}
}
