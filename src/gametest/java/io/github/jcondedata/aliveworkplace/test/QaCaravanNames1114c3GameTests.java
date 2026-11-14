package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

/**
 * QA (lane c, 2026-11-14, third pass) for B1233, written from the bug's expected behaviour: a saved caravan-list
 * village whose name is missing or broken keeps its routes and data, and is called by its made-up name. These attack the
 * name with JSON that parses but isn't a name ({}, 42, [], ""), a very long and a non-Latin name, two villages both
 * broken, and a second save and reload (the fallback name must stay the same, never re-rolled or lost). The far halls'
 * chunks are never loaded, so the name a player reads is the saved one ({@link VillageHalls#nameIfLoaded}).
 */
public class QaCaravanNames1114c3GameTests implements FabricGameTest {
	private static final BlockPos HALL = new BlockPos(1240, 64, -3480);
	private static final BlockPos OTHER = new BlockPos(1560, 64, -3480);
	private static final BlockPos THIRD = new BlockPos(1880, 64, -3800);

	/** A caravan list with three far villages (two with routes, wants, color and a track), saved and edited. */
	private static CompoundTag saved(GameTestHelper helper, String hallName, String otherName) {
		Caravans.Data data = new Caravans.Data();
		data.setWants(HALL, Component.literal(hallName), List.of(new Caravans.Want(Items.BREAD, 3)));
		data.setWants(OTHER, Component.literal(otherName), List.of(new Caravans.Want(Items.WHEAT, 9)));
		data.setWants(THIRD, Component.literal("Thirdwick"), List.of());
		data.toggleRoute(HALL, THIRD);
		data.toggleRoute(OTHER, HALL);
		data.setColour(HALL, DyeColor.LIME);
		data.setColour(OTHER, DyeColor.PURPLE);
		data.setTracked(HALL, THIRD, true);
		return data.save(new CompoundTag(), helper.getLevel().registryAccess());
	}

	/** The saved entry of {@code hall} changed by {@code edit}. */
	private static void edit(CompoundTag tag, BlockPos hall, Consumer<CompoundTag> edit) {
		ListTag villages = tag.getList("villages", Tag.TAG_COMPOUND);
		for (int i = 0; i < villages.size(); i++) {
			if (BlockPos.of(villages.getCompound(i).getLong("hall")).equals(hall)) {
				edit.accept(villages.getCompound(i));
			}
		}
	}

	/** {@code tag} read back as the server would, in the live list's place until the test ends. */
	private static Caravans.Data load(GameTestHelper helper, CompoundTag tag) {
		ServerLevel level = helper.getLevel();
		Caravans.Data live = Caravans.Data.get(level);
		Caravans.Data read = Caravans.Data.load(tag, level.registryAccess());
		level.getDataStorage().set(QaFarVillageName1114eGameTests.CARAVANS, read);
		Leftovers.after(helper, () -> level.getDataStorage().set(QaFarVillageName1114eGameTests.CARAVANS, live));
		return read;
	}

	/** The village is still in the list with its routes, wants and color, and the name a player reads isn't blank. */
	private static void assertKept(GameTestHelper helper, Caravans.Data loaded, String how) {
		Caravans.Village v = loaded.village(HALL);
		helper.assertTrue(v != null, how + ": the village was dropped from the caravan list, its trade routes lost");
		helper.assertTrue(v.wants().size() == 1 && v.wants().get(0).item() == Items.BREAD && v.wants().get(0).count() == 3, how + ": its wants are now " + v.wants());
		helper.assertTrue(loaded.routesFrom(HALL).contains(THIRD), how + ": its trade route to the third village was lost: " + loaded.routesFrom(HALL));
		helper.assertTrue(loaded.routesFrom(OTHER).contains(HALL), how + ": the other village's route to it was lost");
		helper.assertTrue(loaded.colour(HALL) == DyeColor.LIME, how + ": its banner color is now " + loaded.colour(HALL));
		helper.assertTrue(loaded.tracked(HALL, THIRD), how + ": its track towards the third village was lost");
		String shown = VillageHalls.nameIfLoaded(helper.getLevel(), HALL).getString();
		helper.assertTrue(!shown.isBlank(), how + ": a player reads the village's name as blank");
	}

	/** {@code json} as the saved name: the entry is kept and the player reads the made-up name. */
	private static void assertFallsBack(GameTestHelper helper, String json) {
		CompoundTag tag = saved(helper, "Willowmere", "Ashford");
		edit(tag, HALL, t -> t.putString("name", json));
		Caravans.Data loaded = load(helper, tag);
		assertKept(helper, loaded, "saved name " + json);
		String made = VillageHalls.madeUpName(HALL).getString();
		String shown = VillageHalls.nameIfLoaded(helper.getLevel(), HALL).getString();
		helper.assertTrue(shown.equals(made), "saved name " + json + ": a player reads '" + shown + "', not the made-up " + made);
	}

	/** JSON that parses but is no name ({}, [], "") and a number: kept, called by its made-up name. */
	//$ gametest_batch EMPTY_STRUCTURE '"qaCaravanNames1114c3"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaCaravanNames1114c3")
	public void aNameThatIsJsonButNoNameKeepsTheVillageUnderItsMadeUpName(GameTestHelper helper) {
		assertFallsBack(helper, "{}");
		assertFallsBack(helper, "[]");
		assertFallsBack(helper, "\"\"");
		assertFallsBack(helper, "{\"text\":\"\"}");
		assertFallsBack(helper, "{\"text\":\"   \"}");
		assertFallsBack(helper, "{\"translate\":\"\"}");
		helper.succeed();
	}

	/** A number or true as the saved name: the village and its routes are kept, and it has a readable name. */
	//$ gametest_batch EMPTY_STRUCTURE '"qaCaravanNames1114c3"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaCaravanNames1114c3")
	public void aNumberAsTheSavedNameKeepsTheVillage(GameTestHelper helper) {
		for (String json : List.of("42", "true", "{\"text\":5}", "{\"extra\":[]}")) {
			CompoundTag tag = saved(helper, "Willowmere", "Ashford");
			edit(tag, HALL, t -> t.putString("name", json));
			assertKept(helper, load(helper, tag), "saved name " + json);
		}
		helper.succeed();
	}

	/** A 300-letter name and a non-Latin one with an emoji keep exactly, through two saves and reloads. */
	//$ gametest_batch EMPTY_STRUCTURE '"qaCaravanNames1114c3"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaCaravanNames1114c3")
	public void aLongOrNonLatinNameIsKeptExactly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (String name : List.of("Ab".repeat(150), "Δελφοί — 東京村 🌾 \"quoted\" \\ back")) {
			CompoundTag tag = saved(helper, name, "Ashford");
			Caravans.Data once = load(helper, tag);
			Caravans.Data twice = load(helper, once.save(new CompoundTag(), level.registryAccess()));
			assertKept(helper, twice, "the name " + name);
			String shown = VillageHalls.nameIfLoaded(level, HALL).getString();
			helper.assertTrue(shown.equals(name), "the village's name came back as '" + shown + "', not '" + name + "'");
		}
		helper.succeed();
	}

	/** Two villages with broken names: both kept, each with its own routes, wants and color, nothing merged or swapped. */
	//$ gametest_batch EMPTY_STRUCTURE '"qaCaravanNames1114c3"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaCaravanNames1114c3")
	public void twoVillagesWithBrokenNamesKeepTheirOwnRoutes(GameTestHelper helper) {
		CompoundTag tag = saved(helper, "Willowmere", "Ashford");
		edit(tag, HALL, t -> t.putString("name", "{\"text\":\"Willow"));
		edit(tag, OTHER, t -> t.remove("name"));
		Caravans.Data loaded = load(helper, tag);
		assertKept(helper, loaded, "both names broken");
		Caravans.Village other = loaded.village(OTHER);
		helper.assertTrue(other != null, "the second village with a broken name was dropped, its trade routes lost");
		helper.assertTrue(other.wants().size() == 1 && other.wants().get(0).item() == Items.WHEAT && other.wants().get(0).count() == 9,
			"the second village's wants are now " + other.wants());
		helper.assertTrue(loaded.routesFrom(OTHER).equals(Set.of(HALL)), "the second village's routes are now " + loaded.routesFrom(OTHER));
		helper.assertTrue(loaded.colour(OTHER) == DyeColor.PURPLE, "the second village's banner color is now " + loaded.colour(OTHER));
		helper.assertTrue(loaded.villages().size() == 3, "the list holds " + loaded.villages().size() + " villages, not 3");
		ServerLevel level = helper.getLevel();
		helper.assertTrue(VillageHalls.nameIfLoaded(level, OTHER).getString().equals(VillageHalls.madeUpName(OTHER).getString()),
			"the second village reads '" + VillageHalls.nameIfLoaded(level, OTHER).getString() + "', not its own made-up name");
		helper.assertTrue(loaded.village(THIRD) != null && loaded.village(THIRD).name().getString().equals("Thirdwick"), "the good village's name changed");
		helper.succeed();
	}

	/** A broken name, then saved and read back twice more: the same made-up name every time and the entry still whole. */
	//$ gametest_batch EMPTY_STRUCTURE '"qaCaravanNames1114c3"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaCaravanNames1114c3")
	public void theMadeUpNameStaysTheSameOverMoreReloads(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CompoundTag tag = saved(helper, "Willowmere", "Ashford");
		edit(tag, HALL, t -> t.putString("name", "not json at all {"));
		Caravans.Data first = load(helper, tag);
		String name1 = VillageHalls.nameIfLoaded(level, HALL).getString();
		Caravans.Data second = load(helper, first.save(new CompoundTag(), level.registryAccess()));
		String name2 = VillageHalls.nameIfLoaded(level, HALL).getString();
		Caravans.Data third = load(helper, second.save(new CompoundTag(), level.registryAccess()));
		String name3 = VillageHalls.nameIfLoaded(level, HALL).getString();
		assertKept(helper, third, "after three loads");
		String made = VillageHalls.madeUpName(HALL).getString();
		helper.assertTrue(name1.equals(made) && name2.equals(made) && name3.equals(made),
			"the village's name changed between reloads: '" + name1 + "', '" + name2 + "', '" + name3 + "' (made-up " + made + ")");
		helper.succeed();
	}
}
