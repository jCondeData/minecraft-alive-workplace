package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * QA (lane-c-1102-0953, saves; B796's kind, for the Steward's part of the Village Hall): B796's fix reads the hall's
 * edicts, guilds, reforms, services, furniture looks, plan and research entry by entry, so one damaged entry is skipped
 * with a warning and the rest loads. The hall's Steward record ({@code steward}: the ranked wishes and what each rule has
 * done) and the Steward's desk ({@code steward_desk}: the proposals, the rules declined until a day, the next proposal
 * id, the renewals of old houses under way) are still read whole with {@code .result()}: one entry that can't be read in
 * any of their lists or maps empties the whole record, silently. Each case saves a record with good fields beside one
 * empty (unreadable) entry; the load must keep the good fields.
 */
public class QaStewardSavesOneBadEntry1102BugTests implements FabricGameTest {

	private record Case(String what, String key, Consumer<CompoundTag> fill, Function<VillageHallBlockEntity, String> missing) {
	}

	private static ListTag oneBad() {
		ListTag list = new ListTag();
		list.add(new CompoundTag());
		return list;
	}

	private static List<Case> cases() {
		Function<VillageHallBlockEntity, String> desk = h -> {
			StewardDesk.State d = h.stewardDesk();
			return d.declined().getOrDefault("aliveworkplace:more_beds", -1L) == 40L && d.told() == 7 && d.next() == 12 ? null
				: "declined " + d.declined() + ", told " + d.told() + ", next " + d.next() + " (want aliveworkplace:more_beds until 40, 7, 12)";
		};
		Function<VillageHallBlockEntity, String> wishes = h -> {
			StewardWishes.State w = h.stewardWishes();
			StewardWishes.Used used = w.used().get("aliveworkplace:more_beds");
			return w.day() == 9 && used != null && used.times() == 3 && used.lastDay() == 8 ? null
				: "day " + w.day() + ", used " + w.used() + " (want day 9, aliveworkplace:more_beds 3 times, last on day 8)";
		};
		return List.of(
			new Case("a proposal on the desk", "steward_desk", t -> t.put("proposals", oneBad()), desk),
			new Case("a renewal under way", "steward_desk", t -> {
				CompoundTag book = new CompoundTag();
				book.put("active", oneBad());
				book.putLong("last_day", 5);
				t.put("renewals", book);
			}, desk),
			new Case("a ranked wish", "steward", t -> t.put("wishes", oneBad()), wishes),
			new Case("a rule's use", "steward", t -> {
				CompoundTag used = (CompoundTag) t.get("used");
				used.put("aliveworkplace:broken", new CompoundTag());
			}, wishes));
	}

	/** The good part of each record: what the hall must keep whatever else in it is damaged. */
	private static CompoundTag good(String key) {
		CompoundTag t = new CompoundTag();
		if (key.equals("steward_desk")) {
			CompoundTag declined = new CompoundTag();
			declined.putLong("aliveworkplace:more_beds", 40);
			t.put("declined", declined);
			t.putLong("told", 7);
			t.putInt("next", 12);
		} else {
			t.putLong("day", 9);
			CompoundTag used = new CompoundTag();
			CompoundTag beds = new CompoundTag();
			beds.putInt("times", 3);
			beds.putLong("last_day", 8);
			used.put("aliveworkplace:more_beds", beds);
			t.put("used", used);
		}
		return t;
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void oneDamagedEntryInTheStewardsRecordsDoesntLoseTheRest(GameTestHelper helper) {
		var level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		var state = ModBlocks.VILLAGE_HALL.defaultBlockState();
		CompoundTag base = new VillageHallBlockEntity(pos, state).saveWithoutMetadata(level.registryAccess());
		List<String> lost = new ArrayList<>();
		for (Case c : cases()) {
			CompoundTag clean = base.copy();
			clean.put(c.key(), good(c.key()));
			VillageHallBlockEntity a = new VillageHallBlockEntity(pos, state);
			a.loadWithComponents(clean, level.registryAccess());
			String setUp = c.missing().apply(a);
			if (setUp != null) {
				lost.add(c.what() + ": the clean load already lost it (the test's record is wrong): " + setUp);
				continue;
			}
			CompoundTag hurt = base.copy();
			CompoundTag record = good(c.key());
			c.fill().accept(record);
			hurt.put(c.key(), record);
			VillageHallBlockEntity b = new VillageHallBlockEntity(pos, state);
			b.loadWithComponents(hurt, level.registryAccess());
			String gone = c.missing().apply(b);
			if (gone != null) {
				lost.add("one unreadable " + c.what() + " (" + c.key() + "): " + gone);
			}
		}
		helper.assertTrue(lost.isEmpty(), "one damaged entry loses the Steward's whole record: " + lost);
		helper.succeed();
	}

	/**
	 * The same for the hall's Book of Families ({@code families}, 34.21): one person who can't be read (an empty entry put
	 * first) must not cost the village every other person in the book.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void oneDamagedPersonInTheBookOfFamiliesDoesntLoseTheRest(GameTestHelper helper) {
		var level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		var state = ModBlocks.VILLAGE_HALL.defaultBlockState();
		VillageHallBlockEntity a = new VillageHallBlockEntity(pos, state);
		java.util.UUID ana = new java.util.UUID(1, 1);
		java.util.UUID ben = new java.util.UUID(2, 2);
		a.families().put(new io.github.jcondedata.aliveworkplace.people.FamilyBook.Person(ana, net.minecraft.network.chat.Component.literal("Ana"),
			1, 3, true, -1, null, null, ben, "minecraft:farmer"));
		a.families().put(new io.github.jcondedata.aliveworkplace.people.FamilyBook.Person(ben, net.minecraft.network.chat.Component.literal("Ben"),
			1, 2, false, -1, null, null, ana, "minecraft:mason"));
		CompoundTag tag = a.saveWithoutMetadata(level.registryAccess());
		VillageHallBlockEntity clean = new VillageHallBlockEntity(pos, state);
		clean.loadWithComponents(tag.copy(), level.registryAccess());
		helper.assertTrue(clean.families().size() == 2, "set-up: the clean load has " + clean.families().size() + " of 2 people");
		CompoundTag hurt = tag.copy();
		helper.assertTrue(hurt.get("families") instanceof ListTag, "set-up: the hall saved no families list: " + hurt.getAllKeys());
		((ListTag) hurt.get("families")).add(0, new CompoundTag());
		VillageHallBlockEntity b = new VillageHallBlockEntity(pos, state);
		b.loadWithComponents(hurt, level.registryAccess());
		helper.assertTrue(b.families().get(ana) != null && b.families().get(ben) != null,
			"one unreadable person loses the Book of Families: " + b.families().size() + " of 2 people back");
		helper.succeed();
	}
}
