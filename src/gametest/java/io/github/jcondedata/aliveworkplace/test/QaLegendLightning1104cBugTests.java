package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.AREA;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.HALL;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.at;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.hall;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.rare;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.setUp;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.staged;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendSlots;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.FamilyBook;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA bug tests for B943 (lane a, 2026-11-04), written from the expected behavior: a villager turned into a witch by
 * lightning (vanilla {@code Villager.thunderHit}, not Peaceful) is gone as a villager, so it must be handled as a death:
 * a Legend's slot is given up the way a death gives it up, a guest no longer holds the village, and the family no
 * longer counts the witch as a living spouse. Each strike test has a control that kills the same villager normally, to
 * show the checks see a freed slot / a widowed partner when the death hooks do run.
 */
public class QaLegendLightning1104cBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static void difficulty(GameTestHelper helper, Difficulty difficulty) {
		ServerLevel level = helper.getLevel();
		Difficulty was = level.getDifficulty();
		level.getServer().setDifficulty(difficulty, true);
		Leftovers.after(helper, () -> level.getServer().setDifficulty(was, true));
	}

	private static void strike(GameTestHelper helper, Villager villager) {
		ServerLevel level = helper.getLevel();
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		bolt.moveTo(villager.position());
		bolt.setVisualOnly(true);
		villager.thunderHit(level, bolt);
		List<Witch> witches = level.getEntitiesOfClass(Witch.class, helper.getBounds().inflate(16));
		helper.assertTrue(villager.isRemoved() && !witches.isEmpty(), "the strike didn't turn the villager into a witch (setup)");
		witches.forEach(w -> w.setNoAi(true));
	}

	private static void witchesGoAfter(GameTestHelper helper) {
		Leftovers.after(helper, () -> helper.getLevel().getEntitiesOfClass(Witch.class, helper.getBounds().inflate(48)).forEach(Witch::discard));
	}

	private static Villager villager(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		return v;
	}

	/**
	 * A settled Rare Legend gone ({@code struck}: by lightning, else killed): the record shows them gone as a death does,
	 * and once any grave is broken and {@link LegendRecord#FALL_DAYS} days pass, another of their kind may come.
	 */
	private static void legendGone(GameTestHelper helper, boolean struck, String name) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		difficulty(helper, Difficulty.NORMAL);
		witchesGoAfter(helper);
		Legend legend = rare(name, ", \"chance\": 1.0", "");
		helper.runAfterDelay(2, () -> staged(helper, List.of(legend), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			at(level, 1, 1000);
			Villager v = villager(helper, new BlockPos(8, 2, 8));
			UUID id = v.getUUID();
			Legends.make(level, v, legend, "test", Optional.of(hall));
			helper.assertTrue(LegendSlots.whyNot(level, hall, legend, null).isPresent(), "the settled Rare doesn't hold the village's slot (setup)");
			if (struck) {
				strike(helper, v);
			} else {
				v.kill();
			}
			LegendRecord record = LegendRecord.get(level);
			Optional<LegendRecord.Entry> e = record.entry(id);
			helper.assertTrue(e.isEmpty() || !e.get().holds() || e.get().gone() >= 0 || e.get().grave().isPresent(),
				"the Legend " + (struck ? "struck into a witch" : "killed") + " is still on the record as alive and holding the slot: " + e);
			// A grave keeps the slot until it is broken; break any, then let the clock run.
			for (BlockPos grave : Graves.near(level, hall, 96)) {
				level.removeBlockEntity(grave);
				level.setBlock(grave, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
			long today = Chronicle.day(level);
			record.newDay(level.getServer(), today + 1);
			record.newDay(level.getServer(), today + 1 + LegendRecord.FALL_DAYS);
			Optional<net.minecraft.network.chat.Component> why = LegendSlots.whyNot(level, hall, legend, null);
			helper.assertTrue(why.isEmpty(), "after the Legend was " + (struck ? "struck into a witch" : "killed") + " and " + LegendRecord.FALL_DAYS
				+ " days passed, another of their kind still may not come: " + why.map(c -> c.getString()).orElse("") + "; record " + record.entry(id));
			helper.succeed();
		}));
	}

	/** B943: a settled Legend struck into a witch gives up their slot as a death does. */
	//$ gametest_ticks_batch AREA '100' '"qaB943LegendStruck"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaB943LegendStruck")
	public void qaALegendStruckIntoAWitchGivesUpTheirSlot(GameTestHelper helper) {
		legendGone(helper, true, "qa_b943_struck_mason");
	}

	/** Control: the same Legend killed normally gives up the slot (the checks above see a freed slot). */
	//$ gametest_ticks_batch AREA '100' '"qaB943LegendKilled"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaB943LegendKilled")
	public void qaControlALegendKilledGivesUpTheirSlot(GameTestHelper helper) {
		legendGone(helper, false, "qa_b943_killed_mason");
	}

	/** A guest gone ({@code struck}, else killed): the hall holds no guest, and another Legend may visit the next morning. */
	private static void guestGone(GameTestHelper helper, boolean struck, String name) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		difficulty(helper, Difficulty.NORMAL);
		witchesGoAfter(helper);
		Legend first = rare(name, ", \"chance\": 1.0", "");
		Legend next = rare(name + "_next", ", \"chance\": 1.0", "");
		helper.runAfterDelay(2, () -> {
			staged(helper, List.of(first), () -> {
				ServerLevel level = helper.getLevel();
				BlockPos hall = helper.absolutePos(HALL);
				at(level, 1, 1000);
				Villager guest = LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(943L));
				helper.assertTrue(guest != null && ModAttachments.LEGEND.get(guest).guest(), "no guest on day 1 at chance 1 (setup)");
				guest.setNoAi(true);
				if (struck) {
					strike(helper, guest);
				} else {
					guest.kill();
				}
				helper.assertTrue(hall(helper, HALL).legendGuests().guest().isEmpty(), "the hall still holds the guest " + (struck ? "struck into a witch" : "killed")
					+ ": " + hall(helper, HALL).legendGuests().guest());
			});
			staged(helper, List.of(next), () -> {
				ServerLevel level = helper.getLevel();
				BlockPos hall = helper.absolutePos(HALL);
				at(level, 2, 1000);
				Villager second = LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(944L));
				helper.assertTrue(second != null, "no other guest came the morning after the first was " + (struck ? "struck into a witch" : "killed")
					+ " (the village is still held)");
				LegendGuests.leave(level, hall, second);
				hall(helper, HALL).setLegendGuests(LegendGuests.State.EMPTY);
			});
			helper.succeed();
		});
	}

	/** B943: a Legend guest struck into a witch frees the village for another guest. */
	//$ gametest_ticks_batch AREA '100' '"qaB943GuestStruck"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaB943GuestStruck")
	public void qaAGuestStruckIntoAWitchFreesTheVillage(GameTestHelper helper) {
		guestGone(helper, true, "qa_b943_guest_struck");
	}

	/** Control: a guest killed frees the village (B585). */
	//$ gametest_ticks_batch AREA '100' '"qaB943GuestKilled"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaB943GuestKilled")
	public void qaControlAGuestKilledFreesTheVillage(GameTestHelper helper) {
		guestGone(helper, false, "qa_b943_guest_killed");
	}

	/** A married couple in the book, one gone ({@code struck}, else killed): the other is widowed and the book dates the one gone. */
	private static void spouseGone(GameTestHelper helper, boolean struck) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		difficulty(helper, Difficulty.NORMAL);
		witchesGoAfter(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			at(level, 3, 1000);
			Villager a = villager(helper, new BlockPos(8, 2, 8));
			Villager b = villager(helper, new BlockPos(10, 2, 8));
			Couples.wed(level, hall, a, b);
			FamilyBook.write(level, hall, a);
			FamilyBook.write(level, hall, b);
			FamilyBook.Book book = FamilyBook.book(level, hall);
			UUID id = a.getUUID();
			helper.assertTrue(book != null && book.get(id) != null && !book.get(id).dead(), "the spouse isn't in the book as living (setup)");
			helper.assertTrue(Couples.partner(b) != null && Couples.partner(b).married(), "not married (setup)");
			if (struck) {
				strike(helper, a);
			} else {
				a.kill();
			}
			String how = struck ? "struck into a witch" : "killed";
			Couples.Partner p = Couples.partner(b);
			helper.assertTrue(p == null || !p.id().equals(id), "the partner is still married to the spouse " + how + ": " + p);
			helper.assertTrue(ModAttachments.WIDOWED_DAY.get(b) != null, "the partner isn't widowed after the spouse was " + how);
			FamilyBook.Person person = FamilyBook.book(level, hall).get(id);
			helper.assertTrue(person != null && person.dead(), "the Book of Families still lists the spouse " + how + " as living: " + person);
			helper.succeed();
		});
	}

	/** B943: a married villager struck into a witch leaves a widow(er), and the book no longer lists them as living. */
	//$ gametest_ticks_batch AREA '100' '"qaB943SpouseStruck"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaB943SpouseStruck")
	public void qaASpouseStruckIntoAWitchIsNoLongerFamily(GameTestHelper helper) {
		spouseGone(helper, true);
	}

	/** Control: a married villager killed leaves a widow(er) dated in the book. */
	//$ gametest_ticks_batch AREA '100' '"qaB943SpouseKilled"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaB943SpouseKilled")
	public void qaControlASpouseKilledIsNoLongerFamily(GameTestHelper helper) {
		spouseGone(helper, false);
	}
}
