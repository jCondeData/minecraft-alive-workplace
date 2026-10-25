package io.github.jcondedata.aliveworkplace.test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * QA (lane E, saves, 2026-10-25): every world-wide file the mod keeps ({@link SavedData}, the {@code data/aliveworkplace_*}
 * files of a world). CLAUDE.md: "Don't break existing saves: new saved fields need defaults". So for each file:
 * <ul>
 * <li>a world from before the file's features (an empty file: no keys at all) loads, and what the new jar writes for
 * it reads back the same (a second save is the first);</li>
 * <li>a file whose keys hold another kind of value than the jar expects (a key that changed kind between versions,
 * a hand-edited file) still loads: Minecraft drops a file whose load throws and starts it empty, so a throw there is
 * the whole file lost.</li>
 * </ul>
 * The 33 files are named below; {@link #KNOWN} fails when one more {@code extends SavedData} than listed is found by its
 * loader, so a new file isn't left out silently (add it to {@link #FILES}).
 */
public class QaEverySavedDataGameTests implements FabricGameTest {
	private static final String P = "io.github.jcondedata.aliveworkplace.";

	/** Every class of the mod that extends SavedData (grep "extends SavedData" in src/main/java). */
	static final List<String> FILES = List.of(
		"bounty.Bounties$Data", "build.BuildSiteManager", "build.Friends", "camp.CampBeds$Data", "city.PlayerBuilt",
		"craft.DishRequests", "cup.CupData", "guard.RaidDens$Sites", "guard.Scouts$Data", "guard.WarParties$Data",
		"hall.Caravans$Data", "hall.Seasons$Data", "legend.LegendRecord", "mail.PostOffice", "mine.QuarrySiteManager",
		"realm.Feuds$Data", "realm.Pacts$Data", "realm.RealmData", "shop.ShopLedger", "story.ReformStories$Data",
		"story.Stories$Data", "table.BlueprintUnlocks$Wild", "threat.Covens$Data", "threat.Lairs$Data", "threat.Ships$Data",
		"threat.ThreatData", "trainer.LeaderBracelets$Data", "trainer.RaidHelpers$Data", "travel.TravelNetwork",
		"wonder.StadiumDays$Data", "wonder.WonderManager", "work.JobSiteTickets", "work.WorkSites");

	static final int KNOWN = 33;

	/** The file's loader: its static {@code load(CompoundTag, HolderLookup.Provider)}. */
	private static Method loader(String name) {
		try {
			Class<?> type = Class.forName(P + name);
			if (!SavedData.class.isAssignableFrom(type)) {
				throw new GameTestAssertException(name + " no longer extends SavedData: take it off the list");
			}
			for (Method m : type.getDeclaredMethods()) {
				Class<?>[] params = m.getParameterTypes();
				if (m.getName().equals("load") && Modifier.isStatic(m.getModifiers()) && params.length == 2 && params[0] == CompoundTag.class
					&& params[1] == HolderLookup.Provider.class) {
					m.setAccessible(true);
					return m;
				}
			}
			throw new GameTestAssertException(name + " has no static load(CompoundTag, HolderLookup.Provider)");
		} catch (ClassNotFoundException e) {
			throw new GameTestAssertException("no saved file " + name + " (renamed? the list needs it)");
		}
	}

	private static SavedData load(String name, CompoundTag tag, HolderLookup.Provider registries) throws Throwable {
		try {
			return (SavedData) loader(name).invoke(null, tag.copy(), registries);
		} catch (java.lang.reflect.InvocationTargetException e) {
			throw e.getCause();
		}
	}

	/** Every file of the list is found, and there are as many as the mod has. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaSavedFiles"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaSavedFiles")
	public void everySavedFileIsListed(GameTestHelper helper) {
		helper.assertTrue(FILES.size() == KNOWN && FILES.stream().distinct().count() == KNOWN, "the list holds " + FILES.size() + " files, not " + KNOWN);
		FILES.forEach(QaEverySavedDataGameTests::loader);
		helper.succeed();
	}

	/** An empty file (a world from before) loads, and the new jar's save of it reads back to the same save. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaSavedFiles"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaSavedFiles")
	public void anEmptyFileLoadsAndSavesTheSameTwice(GameTestHelper helper) {
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		List<String> wrong = new ArrayList<>();
		for (String name : FILES) {
			try {
				SavedData data = load(name, new CompoundTag(), registries);
				if (data == null) {
					wrong.add(name + ": an empty file loads as nothing");
					continue;
				}
				CompoundTag first = data.save(new CompoundTag(), registries);
				CompoundTag second = load(name, first, registries).save(new CompoundTag(), registries);
				if (!first.equals(second)) {
					wrong.add(name + ": saved " + first + ", read back and saved again " + second);
				}
			} catch (Throwable e) {
				wrong.add(name + ": " + e);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "files a world from before can't load: " + wrong);
		helper.succeed();
	}

	/**
	 * Each key of each file's save, holding a word or a number where the jar expects something else: the file still
	 * loads (a throw would have Minecraft drop the whole file).
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaSavedFiles"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaSavedFiles")
	public void aKeyOfAnotherKindDoesNotLoseTheFile(GameTestHelper helper) {
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		List<String> wrong = new ArrayList<>();
		for (String name : FILES) {
			CompoundTag saved;
			try {
				saved = load(name, new CompoundTag(), registries).save(new CompoundTag(), registries);
			} catch (Throwable e) {
				wrong.add(name + ": " + e);
				continue;
			}
			for (String key : saved.getAllKeys()) {
				for (Tag odd : new Tag[] {StringTag.valueOf("x"), IntTag.valueOf(7)}) {
					if (odd.getId() == saved.get(key).getId()) {
						continue;
					}
					CompoundTag tag = saved.copy();
					tag.put(key, odd);
					try {
						load(name, tag, registries);
					} catch (Throwable e) {
						wrong.add(name + " with " + key + " = " + odd + ": " + e);
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "files lost to one key of another kind: " + wrong);
		helper.succeed();
	}
}
