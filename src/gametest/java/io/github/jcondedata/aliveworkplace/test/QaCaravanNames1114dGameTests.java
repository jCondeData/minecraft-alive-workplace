package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

/**
 * QA (qa-1114d), B1233 from the spec: a caravan-list entry whose saved village name is missing, null or unreadable keeps
 * the rest of the entry (routes, wants, banner color, stations, tracks) and is named by the village's made-up name
 * ({@link VillageHalls#madeUpName}, B1229's fallback). These go past {@code CaravanSavedNameB1233GameTests}: a name tag
 * of the wrong NBT type, JSON that isn't a text component, a second save and load of a recovered entry, several broken
 * entries side by side, and the lists a player actually reads the names from (the hall's routes page and the colony
 * charter's other villages).
 */
public class QaCaravanNames1114dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1700, 64, -4100);
	private static final BlockPos OTHER = new BlockPos(2000, 64, -4100);
	private static final BlockPos STATION = new BlockPos(1710, 64, -4095);

	/** A caravan list with a fully set-up village at {@link #HALL} (wants, last caravan day, route, color, station, track) and a plain one at {@link #OTHER}. */
	private static Caravans.Data fresh() {
		Caravans.Data data = new Caravans.Data();
		data.setWants(HALL, Component.literal("Willowmere"), List.of(new Caravans.Want(Items.BREAD, 4)));
		data.setWants(OTHER, Component.literal("Ashford"), List.of());
		data.sent(HALL, 9);
		data.toggleRoute(HALL, OTHER);
		data.setColour(HALL, DyeColor.LIME);
		data.setStations(HALL, List.of(STATION));
		data.setTracked(HALL, OTHER, true);
		return data;
	}

	/** The saved village entry for {@code hall} in a caravan-list tag. */
	private static CompoundTag entry(CompoundTag tag, BlockPos hall) {
		ListTag list = tag.getList("villages", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			if (list.getCompound(i).getLong("hall") == hall.asLong()) {
				return list.getCompound(i);
			}
		}
		throw new AssertionError("the saved caravan list has no entry for the village at " + hall.toShortString());
	}

	/** Saves {@code data}, lets {@code edit} change {@link #HALL}'s entry, and loads it back as the server would. */
	private static Caravans.Data reload(GameTestHelper helper, Caravans.Data data, Consumer<CompoundTag> edit) {
		ServerLevel level = helper.getLevel();
		CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
		edit.accept(entry(tag, HALL));
		return Caravans.Data.load(tag, level.registryAccess());
	}

	/** Everything {@link #HALL} had besides its name is still there after the load. */
	private static void assertDataKept(GameTestHelper helper, Caravans.Data loaded, String how) {
		Caravans.Village v = loaded.village(HALL);
		helper.assertTrue(v != null, "The village whose saved name was " + how + " was dropped from the caravan list when the save loaded");
		helper.assertTrue(v.wants().size() == 1 && v.wants().get(0).item() == Items.BREAD && v.wants().get(0).count() == 4,
			"The village whose saved name was " + how + " lost what it wants (expected 4 bread, got " + v.wants() + ")");
		helper.assertTrue(v.lastCaravanDay() == 9, "The village whose saved name was " + how + " forgot the day of its last caravan (expected 9, got " + v.lastCaravanDay() + ")");
		helper.assertTrue(loaded.routesFrom(HALL).equals(Set.of(OTHER)), "The village whose saved name was " + how + " lost its trade route (got " + loaded.routesFrom(HALL) + ")");
		helper.assertTrue(loaded.colour(HALL) == DyeColor.LIME, "The village whose saved name was " + how + " lost its lime banner color (got " + loaded.colour(HALL) + ")");
		helper.assertTrue(loaded.stations(HALL).equals(List.of(STATION)), "The village whose saved name was " + how + " lost its station (got " + loaded.stations(HALL) + ")");
		helper.assertTrue(loaded.tracked(HALL, OTHER), "The village whose saved name was " + how + " lost its track to the other village");
		Caravans.Village other = loaded.village(OTHER);
		helper.assertTrue(other != null && other.name().getString().equals("Ashford"),
			"The other village (with a good name) wasn't kept as Ashford when its neighbour's name was " + how + " (got " + other + ")");
	}

	private static void assertMadeUpName(GameTestHelper helper, Caravans.Data loaded, BlockPos hall, String how) {
		String made = VillageHalls.madeUpName(hall).getString();
		String got = loaded.village(hall).name().getString();
		helper.assertTrue(!made.isBlank(), "The made-up name for the village at " + hall.toShortString() + " is blank");
		helper.assertTrue(got.equals(made), "The village whose saved name was " + how + " is called '" + got + "' instead of its made-up name '" + made + "'");
	}

	/** A name tag that isn't a string at all (a number, a compound, a list, a byte array): the entry is kept under the made-up name. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aNameTagOfTheWrongTypeKeepsTheEntryUnderItsMadeUpName(GameTestHelper helper) {
		CompoundTag compound = new CompoundTag();
		compound.putString("text", "Willowmere");
		ListTag list = new ListTag();
		list.add(StringTag.valueOf("Willowmere"));
		Map<String, Tag> wrong = Map.of("a number", IntTag.valueOf(42), "a compound", compound, "a list", list,
			"a byte array", new ByteArrayTag(new byte[] {1, 2, 3}));
		for (Map.Entry<String, Tag> e : wrong.entrySet()) {
			String how = "saved as " + e.getKey() + " instead of text";
			Caravans.Data loaded = reload(helper, fresh(), t -> t.put("name", e.getValue().copy()));
			assertDataKept(helper, loaded, how);
			assertMadeUpName(helper, loaded, HALL, how);
		}
		helper.succeed();
	}

	/**
	 * A name that is valid JSON but not a text component ([], {}, a number, true, an empty object with only a color, an
	 * empty string): nothing is thrown, the entry is kept, and the name is either the made-up one or readable text, never
	 * raw JSON. Where a player reads it (the far village's name), it is never blank.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1114dJsonNotText"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1114dJsonNotText")
	public void aNameThatIsJsonButNotTextKeepsTheEntryWithAReadableName(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> jsons = List.of("[]", "{}", "123", "true", "{\"color\":\"red\"}", "\"\"", "[\"\"]", "{\"text\":\"\"}");
		List<BlockPos> fars = new java.util.ArrayList<>();
		for (int i = 0; i < jsons.size(); i++) {
			fars.add(QaFarVillageName1114eGameTests.far(helper, 9000 + 400 * i));
		}
		QaFarVillageName1114eGameTests.setUp(helper, fars.get(0));
		Caravans.Data live = Caravans.Data.get(level);
		for (BlockPos far : fars) {
			Leftovers.after(helper, () -> Caravans.Data.get(level).remove(far));
			live.setWants(far, Component.literal("Placeholder"), List.of(new Caravans.Want(Items.BREAD, 1)));
		}
		for (String json : jsons) {
			String how = "the JSON " + json;
			Caravans.Data loaded = reload(helper, fresh(), t -> t.putString("name", json));
			assertDataKept(helper, loaded, how);
			String got = loaded.village(HALL).name().getString();
			String made = VillageHalls.madeUpName(HALL).getString();
			helper.assertTrue(got.equals(made) || !(got.contains("{") || got.contains("[") || got.contains("\"")),
				"The village whose saved name was " + how + " shows raw JSON as its name: '" + got + "'");
		}
		// what a player reads for each of those villages when its hall is far away, after the live list reloads
		QaFarVillageName1114eGameTests.reload(helper, tag -> {
			for (int i = 0; i < jsons.size(); i++) {
				QaFarVillageName1114eGameTests.entry(tag, fars.get(i)).putString("name", jsons.get(i));
			}
		});
		for (int i = 0; i < jsons.size(); i++) {
			String how = "the JSON " + jsons.get(i);
			BlockPos far = fars.get(i);
			helper.assertTrue(Caravans.Data.get(level).village(far) != null, "A far village whose saved name was " + how + " was dropped from the live caravan list");
			Component shown = VillageHalls.nameIfLoaded(level, far);
			helper.assertTrue(!shown.getString().isBlank(), "A far village whose saved name was " + how + " is shown to the player with no name");
			helper.assertTrue(shown.getString().equals(VillageHalls.madeUpName(far).getString()) || !(shown.getString().contains("{") || shown.getString().contains("[")),
				"A far village whose saved name was " + how + " is shown to the player as raw JSON: '" + shown.getString() + "'");
			helper.assertTrue(!QaFarVillageName1114eGameTests.loaded(level, far), "Reading a far village's name loaded its hall's chunk");
		}
		helper.succeed();
	}

	/**
	 * A recovered entry survives a second save and load: the made-up name is the same both times, and the routes, wants,
	 * color, station and track are still there.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aRecoveredEntrySurvivesASecondSaveAndLoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (String how : List.of("missing", "cut off mid-write", "null")) {
			Caravans.Data first = reload(helper, fresh(), t -> {
				switch (how) {
					case "missing" -> t.remove("name");
					case "cut off mid-write" -> t.putString("name", "{\"text\":\"Willow");
					default -> t.putString("name", "null");
				}
			});
			assertDataKept(helper, first, how + " (first load)");
			assertMadeUpName(helper, first, HALL, how + " (first load)");
			String firstName = first.village(HALL).name().getString();
			Caravans.Data second = Caravans.Data.load(first.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			assertDataKept(helper, second, how + " (second load)");
			String secondName = second.village(HALL).name().getString();
			helper.assertTrue(secondName.equals(firstName),
				"The village whose saved name was " + how + " changed its name between two loads: '" + firstName + "' then '" + secondName + "'");
			Caravans.Data third = Caravans.Data.load(second.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			helper.assertTrue(third.village(HALL) != null && third.village(HALL).name().getString().equals(firstName),
				"The village whose saved name was " + how + " didn't keep its made-up name through a third load (got " + third.village(HALL) + ")");
		}
		helper.succeed();
	}

	/**
	 * Two villages with broken names (one missing, one unreadable) next to one with a good name: each broken one gets its
	 * own made-up name (different from each other), the good one keeps its name, and every route among them is kept.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void twoBrokenNamesEachGetTheirOwnMadeUpNameAndTheGoodOneStays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = new BlockPos(1700, 64, -4600);
		BlockPos b = new BlockPos(2100, 64, -4900);
		BlockPos c = new BlockPos(1900, 64, -4400);
		helper.assertTrue(!VillageHalls.madeUpName(a).getString().equals(VillageHalls.madeUpName(b).getString()),
			"Setup: the two test spots share a made-up name, pick other spots");
		Caravans.Data data = new Caravans.Data();
		data.setWants(a, Component.literal("Oldtown"), List.of(new Caravans.Want(Items.WHEAT, 2)));
		data.setWants(b, Component.literal("Newtown"), List.of(new Caravans.Want(Items.COAL, 5)));
		data.setWants(c, Component.literal("Ferncombe"), List.of());
		data.toggleRoute(a, c);
		data.toggleRoute(b, c);
		data.toggleRoute(c, a);
		CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
		entry(tag, a).remove("name");
		entry(tag, b).putString("name", "{\"translate\":");
		Caravans.Data loaded = Caravans.Data.load(tag, level.registryAccess());
		for (BlockPos h : List.of(a, b, c)) {
			helper.assertTrue(loaded.village(h) != null, "The village at " + h.toShortString() + " was dropped from the caravan list when the save loaded");
		}
		assertMadeUpName(helper, loaded, a, "missing");
		assertMadeUpName(helper, loaded, b, "unreadable");
		helper.assertTrue(!loaded.village(a).name().getString().equals(loaded.village(b).name().getString()),
			"Two villages with broken saved names were both called '" + loaded.village(a).name().getString() + "'");
		helper.assertTrue(loaded.village(c).name().getString().equals("Ferncombe"),
			"The village with a good saved name lost it: '" + loaded.village(c).name().getString() + "'");
		helper.assertTrue(loaded.village(a).wants().size() == 1 && loaded.village(a).wants().get(0).item() == Items.WHEAT,
			"The village with no saved name lost its wants: " + loaded.village(a).wants());
		helper.assertTrue(loaded.village(b).wants().size() == 1 && loaded.village(b).wants().get(0).item() == Items.COAL,
			"The village with an unreadable saved name lost its wants: " + loaded.village(b).wants());
		helper.assertTrue(loaded.routesFrom(a).equals(Set.of(c)) && loaded.routesFrom(b).equals(Set.of(c)) && loaded.routesFrom(c).equals(Set.of(a)),
			"Trade routes were lost: from the first " + loaded.routesFrom(a) + ", the second " + loaded.routesFrom(b) + ", the good one " + loaded.routesFrom(c));
		helper.succeed();
	}

	/**
	 * What the player reads: a hall's routes page lists its neighbours ({@link Caravans#neighbours}, each button named by
	 * the village's name) and a colony charter lists the other villages ({@link Colonies#otherVillages}). After the live
	 * caravan list reloads with one neighbour's name broken, both list it, by its made-up name, never by an empty one;
	 * the neighbour with a good name keeps it.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1114dCaravanNamesShown"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1114dCaravanNamesShown")
	public void theRoutesPageAndCharterListTheBrokenVillageByItsMadeUpName(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos home = QaFarVillageName1114eGameTests.far(helper, 7600);
		BlockPos broken = QaFarVillageName1114eGameTests.far(helper, 8000);
		BlockPos good = QaFarVillageName1114eGameTests.far(helper, 8400);
		QaFarVillageName1114eGameTests.setUp(helper, home);
		for (BlockPos h : List.of(broken, good)) {
			Leftovers.after(helper, () -> Caravans.Data.get(level).remove(h));
		}
		Caravans.Data live = Caravans.Data.get(level);
		live.setWants(home, Component.literal("Homestead"), List.of());
		live.setWants(broken, Component.literal("Brokenvale"), List.of(new Caravans.Want(Items.BREAD, 2)));
		live.setWants(good, Component.literal("Goodmere"), List.of());
		helper.assertTrue(live.toggleRoute(home, broken), "Setup: the trade route to the village whose name will break didn't open");
		QaFarVillageName1114eGameTests.reload(helper, tag -> QaFarVillageName1114eGameTests.entry(tag, broken).putString("name", "{\"text\":\"Brokenv"));
		String made = VillageHalls.madeUpName(broken).getString();

		List<Caravans.Village> neighbours = Caravans.neighbours(level, home);
		Caravans.Village shown = neighbours.stream().filter(v -> v.hall().equals(broken)).findFirst().orElse(null);
		helper.assertTrue(shown != null, "The hall's routes page no longer lists the village whose saved name was broken");
		helper.assertTrue(shown.name().getString().equals(made),
			"The hall's routes page names the village with the broken saved name '" + shown.name().getString() + "' instead of its made-up name '" + made + "'");
		helper.assertTrue(Caravans.Data.get(level).routesFrom(home).contains(broken), "The trade route to the village with the broken saved name was lost");
		helper.assertTrue(neighbours.stream().anyMatch(v -> v.hall().equals(good) && v.name().getString().equals("Goodmere")),
			"The hall's routes page lost the neighbour with a good name, or renamed it: " + neighbours.stream().map(v -> v.name().getString()).toList());
		helper.assertTrue(neighbours.stream().noneMatch(v -> v.name().getString().isBlank()),
			"The hall's routes page shows a village with no name: " + neighbours.stream().map(v -> v.name().getString()).toList());

		List<Colonies.Mark> marks = Colonies.otherVillages(level, home);
		Colonies.Mark mark = marks.stream().filter(m -> m.hall().equals(broken)).findFirst().orElse(null);
		helper.assertTrue(mark != null && mark.name().getString().equals(made),
			"The colony charter's list of other villages doesn't name the village with the broken saved name by its made-up name '" + made + "': "
				+ marks.stream().map(m -> m.name().getString()).toList());
		helper.assertTrue(!QaFarVillageName1114eGameTests.loaded(level, broken), "Listing the neighbours loaded the far village's hall chunk");
		helper.succeed();
	}
}
