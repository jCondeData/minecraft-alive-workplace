package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;

/**
 * QA (lane-c-1116-2153) of B1316, the chapel-wedding request (ROADMAP 31.10, docs/wiki/personal-requests.md), the edges
 * QaRequestFixes1116cGameTests and QaRequestKinds1116c* leave: a Chapel finished well before the fifth day pays once
 * (the request's look and the bell's round run again pay and marry nothing more); a save and load between the bell
 * wedding and the hall's look still misses the request once and a Chapel finished afterwards pays nothing; a couple
 * married (at the bell) is never asked for a chapel wedding again, and a village that has its Chapel doesn't ask one.
 */
public class QaChapelWedding1116c3GameTests {
	static final String AREA = PersonalRequestGameTests.AREA;
	static final BlockPos HALL = PersonalRequestGameTests.HALL;
	static final String ME = PersonalRequestGameTests.ME;
	static final String MISSED = "You didn't help Dara in time (A chapel wedding). They feel let down: 30 friendship lost.";

	static void midday(ServerLevel level) {
		level.setDayTime(level.getDayTime() - level.getDayTime() % 24000 + 7000);
	}

	/** As a save and load of the world would bring a villager back: its save read into a new villager, the old one gone. */
	static Villager reloaded(GameTestHelper helper, Villager v) {
		CompoundTag tag = v.saveWithoutId(new CompoundTag());
		v.discard();
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(tag);
		helper.getLevel().addFreshEntity(copy);
		return copy;
	}

	/**
	 * The Chapel finished on the third day of courting (the request waits past the usual 2): the bell's round doesn't marry
	 * them before it, the hall's look marries them in the Chapel, pays 6 once and Dara thanks word for word; more looks and
	 * rounds pay nothing more and add no second wedding line.
	 */
	//$ gametest_ticks_batch AREA '80' '"qa1116c3ChapelEarly"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1116c3ChapelEarly")
	public void aChapelFinishedOnTheThirdDayPaysOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			Leftovers.finished(helper);
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager[] couple = QaRequestFixes1116cGameTests.courting(helper, 3);
			Villager dara = couple[0];
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			Quest quest = PersonalRequestGameTests.accepted(helper, "chapel_wedding", dara, me);
			midday(level);
			Couples.round(level, hall);
			helper.assertTrue(!Couples.partner(dara).married(), "married at the bell on day 3 though the request waits for the Chapel");
			QaRequestFixes1116cGameTests.chapelFinished(helper);
			PersonalRequests.look(level, hall, PersonalRequestGameTests.hallEntity(helper), false);
			helper.assertTrue(PersonalRequests.of(dara) == null, "the Chapel on day 3 didn't finish the request: " + quest);
			helper.assertValueEqual(me.getInventory().countItem(Items.EMERALD), 6, "emeralds for the Chapel wedding");
			helper.assertTrue(Couples.partner(dara).married() && Couples.partner(couple[1]).married(), "not married in the Chapel");
			helper.assertValueEqual(PersonalRequestGameTests.said(dara),
				"Married in the Chapel, with the whole village at the feast. It was everything I dreamed of, " + ME + ".", "Dara's thanks");
			List<String> weddings = PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.WEDDING);
			helper.assertValueEqual(weddings.stream().filter("Dara and Odo were married in the new Chapel, and the whole village sat down to a feast"::equals).count(), 1L,
				"the Chapel wedding in the chronicle " + weddings);
			for (int i = 0; i < 3; i++) {
				PersonalRequests.look(level, hall, PersonalRequestGameTests.hallEntity(helper), false);
				Couples.round(level, hall);
			}
			helper.assertValueEqual(me.getInventory().countItem(Items.EMERALD), 6, "emeralds after more looks and rounds");
			helper.assertValueEqual(PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.WEDDING), weddings, "the wedding lines after more looks and rounds");
			helper.succeed();
		});
	}

	/**
	 * Married at the bell on the fifth day, then the game saved and loaded (the stories and both villagers) before the
	 * hall looks: the request is still missed, its line told once, and a Chapel finished afterwards pays nothing and isn't
	 * thanked for.
	 */
	//$ gametest_ticks_batch AREA '80' '"qa1116c3BellReload"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1116c3BellReload")
	public void aSaveAndLoadAfterTheBellStillMissesTheRequest(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			Leftovers.finished(helper);
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager[] couple = QaRequestFixes1116cGameTests.courting(helper, 5);
			Villager dara = couple[0];
			List<Component> heard = new ArrayList<>();
			ServerPlayer me = QaRequestFixes1116cGameTests.listener(helper, heard, "qa1116c3Me", new BlockPos(5, 2, 6), dara, 300);
			Quest quest = PersonalRequestGameTests.accepted(helper, "chapel_wedding", dara, me);
			midday(level);
			Couples.round(level, hall);
			helper.assertTrue(Couples.partner(dara).married(), "setup: not married at the bell at 5 days");
			// The save and load.
			Stories.Data loaded = Stories.Data.load(Stories.Data.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			level.getDataStorage().set("aliveworkplace_stories", loaded);
			PersonalRequests.forget();
			dara = reloaded(helper, dara);
			Villager odo = reloaded(helper, couple[1]);
			helper.assertTrue(Couples.partner(dara).married() && Couples.partner(odo).married(), "the bell wedding was lost on reload");
			helper.assertTrue(Stories.open(level, hall).stream().anyMatch(q -> q.id.equals(quest.id)), "setup: the request wasn't saved open");
			heard.clear();
			PersonalRequests.look(level, hall, PersonalRequestGameTests.hallEntity(helper), false);
			PersonalRequests.look(level, hall, PersonalRequestGameTests.hallEntity(helper), false);
			helper.assertTrue(PersonalRequests.of(dara) == null, "the request is still open after the bell and a reload");
			helper.assertTrue(Stories.open(level, hall).stream().noneMatch(q -> q.id.equals(quest.id)), "the request still on the hall's list");
			helper.assertValueEqual(QaRequestFixes1116cGameTests.lines(heard, MISSED), 1L, "the missed line, once: " + QaRequestFixes1116cGameTests.strings(heard));
			QaRequestFixes1116cGameTests.chapelFinished(helper);
			heard.clear();
			PersonalRequests.look(level, hall, PersonalRequestGameTests.hallEntity(helper), false);
			Couples.round(level, hall);
			helper.assertValueEqual(me.getInventory().countItem(Items.EMERALD), 0, "emeralds for a Chapel finished after the bell and a reload");
			helper.assertTrue(!PersonalRequestGameTests.said(dara).startsWith("Married in the Chapel"), "Dara thanks for a Chapel wedding: " + PersonalRequestGameTests.said(dara));
			helper.assertTrue(PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.WEDDING).stream().noneMatch(l -> l.contains("Chapel")),
				"a Chapel wedding in the chronicle: " + PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.WEDDING));
			helper.assertTrue(QaRequestFixes1116cGameTests.strings(heard).stream().noneMatch(s -> s.contains("Dara")), "lines about Dara after the Chapel: "
				+ QaRequestFixes1116cGameTests.strings(heard));
			helper.succeed();
		});
	}

	/**
	 * "One of a courting couple, in a village with no Chapel" asks: courting with no Chapel, Dara has the request to ask
	 * (the control); married at the bell, neither she nor Odo has it to ask again; and a courting couple in a village whose
	 * Chapel is finished isn't asked for one.
	 */
	//$ gametest_ticks_batch AREA '80' '"qa1116c3NotAgain"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1116c3NotAgain")
	public void aMarriedCoupleIsntAskedForAChapelWeddingAgain(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			Leftovers.finished(helper);
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager[] couple = QaRequestFixes1116cGameTests.courting(helper, 1);
			Villager dara = couple[0];
			Villager odo = couple[1];
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			PersonalRequestGameTests.befriend(helper, odo, me, 300);
			helper.assertTrue(PersonalRequestGameTests.resolve(helper, "chapel_wedding", dara, me) != null,
				"control: a courting Dara with 3 hearts and no Chapel has no chapel wedding to ask " + PersonalRequestGameTests.why(helper, dara, me));
			// Married at the bell (5 days courting, no request open).
			long today = Chronicle.day(level);
			ModAttachments.PARTNER.set(dara, new Couples.Partner(odo.getUUID(), odo.getDisplayName(), today - 5, false));
			ModAttachments.PARTNER.set(odo, new Couples.Partner(dara.getUUID(), dara.getDisplayName(), today - 5, false));
			midday(level);
			Couples.round(level, hall);
			helper.assertTrue(Couples.partner(dara).married() && Couples.partner(odo).married(), "setup: not married at the bell");
			helper.assertTrue(PersonalRequestGameTests.resolve(helper, "chapel_wedding", dara, me) == null, "a married Dara still asks for a chapel wedding");
			helper.assertTrue(PersonalRequestGameTests.resolve(helper, "chapel_wedding", odo, me) == null, "a married Odo still asks for a chapel wedding");
			// A courting couple once the village has its Chapel.
			ModAttachments.PARTNER.set(dara, new Couples.Partner(odo.getUUID(), odo.getDisplayName(), today - 1, false));
			ModAttachments.PARTNER.set(odo, new Couples.Partner(dara.getUUID(), dara.getDisplayName(), today - 1, false));
			QaRequestFixes1116cGameTests.chapelFinished(helper);
			helper.assertTrue(PersonalRequestGameTests.resolve(helper, "chapel_wedding", dara, me) == null, "asked for a chapel wedding in a village with its Chapel");
			helper.succeed();
		});
	}
}
