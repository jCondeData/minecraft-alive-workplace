package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.RequestErrands;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-c-1116-1852) of the personal-request fixes B1314, B1315 and B1316 (ROADMAP 31.9/31.10,
 * docs/wiki/personal-requests.md), from their specs: the Lucky Crystal is "The Lucky Crystal" only where a sentence starts
 * (thanks, chronicle) and "the Lucky Crystal" mid-sentence (the wants line, the map), the same after a save and load, and
 * every other heirloom unchanged; a player without 3 hearts with the giver keeps the heirloom, the letter or the reply,
 * is told they need the hearts, and isn't paid, at 2 hearts as at 0, while 3 hearts hands it in; married at the bell,
 * the chapel-wedding request is missed (with its line, once), and a Chapel finished on the fifth day wins over the bell.
 * Hand-ins go through the player's right-click, as in the game.
 */
public class QaRequestFixes1116cGameTests implements FabricGameTest {
	static final String AREA = PersonalRequestGameTests.AREA;
	static final String HUGE = "aliveworkplace_test:huge_area";
	static final BlockPos HALL = PersonalRequestGameTests.HALL;
	static final String ME = PersonalRequestGameTests.ME;

	/** A connected player whose chat lands in {@code seen}, standing at {@code rel}, with {@code points} with {@code villager} (none when 0). */
	static ServerPlayer listener(GameTestHelper helper, List<Component> seen, String name, BlockPos rel, Villager villager, int points) {
		ServerPlayer player = QaFarVillageName1114eGameTests.player(helper, seen, name);
		PersonalErrandGameTests.moveTo(player, helper.absolutePos(rel));
		if (points > 0) {
			PersonalRequestGameTests.befriend(helper, villager, player, points);
		}
		return player;
	}

	static long lines(List<Component> seen, String line) {
		return seen.stream().filter(c -> c.getString().equals(line)).count();
	}

	static List<String> strings(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	/** Dara's heirloom {@code key} accepted by {@code me}, put in its chest and taken out: the found item. */
	static ItemStack foundHeirloom(GameTestHelper helper, Villager dara, ServerPlayer me, String key, Quest[] quest) {
		ServerLevel level = helper.getLevel();
		BlockPos chestRel = new BlockPos(14, 2, 14);
		helper.setBlock(chestRel, Blocks.CHEST);
		quest[0] = PersonalErrandGameTests.acceptedFile(helper, PersonalErrandGameTests.heirloomAt(helper, chestRel, key), dara, me);
		PersonalErrandGameTests.moveTo(me, helper.absolutePos(chestRel).offset(30, 0, 0));
		helper.assertTrue(RequestErrands.placeHeirlooms(level) == 1, "setup: not put in the chest");
		ItemStack found = QaRequestKinds1116cBugTests.fetchFromChest(helper, chestRel);
		helper.assertTrue(found.is(RequestErrands.HEIRLOOMS.get(key)), "setup: the heirloom in the chest: " + found);
		PersonalErrandGameTests.moveTo(me, helper.absolutePos(new BlockPos(5, 2, 6)));
		return found;
	}

	// --- B1314: "The Lucky Crystal" at a sentence's start only ----------------------------------------------------

	/**
	 * B1314: the Lucky Crystal reads "the Lucky Crystal" mid-sentence (the wants line, the map's name) and "The Lucky
	 * Crystal" where it opens a sentence: Dara's thanks and the chronicle's story, word for word; the request saved and
	 * read back keeps both forms.
	 */
	//$ gametest_ticks_batch AREA '80' '"qa1116cCrystalWords"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1116cCrystalWords")
	public void theLuckyCrystalIsCapitalOnlyWhereASentenceStarts(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			Quest[] quest = new Quest[1];
			ItemStack found = foundHeirloom(helper, dara, me, "crystal", quest);
			// Mid-sentence: lower case, as before.
			helper.assertValueEqual(PersonalErrandGameTests.wants(helper, quest[0]), "Dara wants their family's heirloom back: the Lucky Crystal within 10 days", "the wants line");
			ItemStack map = me.getInventory().items.stream().filter(s -> s.is(Items.FILLED_MAP)).findFirst().orElse(ItemStack.EMPTY);
			helper.assertValueEqual(map.getHoverName().getString(), "Map to where the Lucky Crystal was lost", "the map's name");
			helper.assertValueEqual(found.getHoverName().getString(), "the Lucky Crystal", "the heirloom's own name");
			// Saved and read back: both forms the same.
			CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
			Stories.Data read = Stories.Data.load(saved, level.registryAccess());
			helper.assertTrue(read.save(new CompoundTag(), level.registryAccess()).equals(saved), "the stories changed on reload");
			Objectives.Objective back = Objectives.parse(quest[0].objectives.get(0).json());
			helper.assertValueEqual(back.what().getString(), "the Lucky Crystal", "mid-sentence after a reload");
			helper.assertValueEqual(back.whatStart().getString(), "The Lucky Crystal", "a sentence's start after a reload");
			// Handed back: the thanks and the story open with a capital, word for word.
			me.setItemInHand(InteractionHand.MAIN_HAND, found);
			QaRequestKinds1116cGameTests.click(me, dara);
			helper.assertTrue(PersonalRequests.of(dara) == null && me.getInventory().countItem(Items.EMERALD) == 8, "not handed back: " + quest[0]);
			helper.assertValueEqual(PersonalRequestGameTests.said(dara),
				"The Lucky Crystal! I never thought I'd hold it again. Thank you, " + ME + ", from all of us, the living and the gone.", "Dara's thanks");
			List<String> stories = PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.STORY);
			String tail = " by " + ME + ". The family has had good luck ever since it came to them, and bad luck ever since it went.";
			helper.assertTrue(stories.stream().filter(l -> l.startsWith("The Lucky Crystal is back with Dara, found at ") && l.endsWith(tail)).count() == 1,
				"the chronicle's story: " + stories);
			helper.succeed();
		});
	}

	/**
	 * B1314's other side: every other heirloom already starts with a capital, so its sentence-start form is its name
	 * unchanged, and a compass's thanks read as before ("Grandmother's Compass! ...").
	 */
	//$ gametest_ticks_batch AREA '80' '"qa1116cOtherHeirlooms"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1116cOtherHeirlooms")
	public void theOtherHeirloomsReadAsBefore(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			for (String key : RequestErrands.HEIRLOOMS.keySet()) {
				Objectives.Objective fetch = Objectives.parse(com.google.gson.JsonParser.parseString(
					"{\"type\": \"fetch\", \"heirloom\": \"" + key + "\", \"place\": {\"point\": [0, 64, 0]}}").getAsJsonObject());
				String what = fetch.what().getString();
				String start = fetch.whatStart().getString();
				helper.assertTrue(!start.isEmpty() && Character.isUpperCase(start.charAt(0)), key + "'s sentence start: \"" + start + "\"");
				helper.assertValueEqual(start, Character.toUpperCase(what.charAt(0)) + what.substring(1), key + "'s sentence start against its name");
				if (!key.equals("crystal")) {
					helper.assertValueEqual(start, what, key + " changed at a sentence's start");
				}
			}
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			Quest[] quest = new Quest[1];
			ItemStack found = foundHeirloom(helper, dara, me, "compass", quest);
			me.setItemInHand(InteractionHand.MAIN_HAND, found);
			QaRequestKinds1116cGameTests.click(me, dara);
			helper.assertTrue(PersonalRequests.of(dara) == null, "not handed back");
			helper.assertValueEqual(PersonalRequestGameTests.said(dara),
				"Grandmother's Compass! I never thought I'd hold it again. Thank you, " + ME + ", from all of us, the living and the gone.", "the compass thanks");
			helper.succeed();
		});
	}

	// --- B1315: 3 hearts to move a request on ---------------------------------------------------------------------

	/**
	 * B1315: a player with no hearts with Dara right-clicks her with her heirloom: she doesn't take it (it stays in their
	 * hand), they're told "You need 3 hearts with Dara to help with this.", the request stays open and nobody is paid.
	 */
	//$ gametest_ticks_batch AREA '80' '"qa1116cHeirloomStranger"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1116cHeirloomStranger")
	public void aStrangerKeepsTheHeirloomAndIsToldTheHearts(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			List<Component> heard = new ArrayList<>();
			ServerPlayer zed = listener(helper, heard, "qa1116cZed", new BlockPos(6, 2, 6), dara, 0);
			Quest[] quest = new Quest[1];
			ItemStack found = foundHeirloom(helper, dara, me, "spyglass", quest);
			zed.setItemInHand(InteractionHand.MAIN_HAND, found);
			heard.clear();
			QaRequestKinds1116cGameTests.click(zed, dara);
			helper.assertTrue(PersonalRequests.of(dara) == quest[0] && quest[0].progress[0] == 0, "the request moved on: " + quest[0]);
			helper.assertTrue(zed.getMainHandItem().is(Items.SPYGLASS) && zed.getMainHandItem().getCount() == 1, "the stranger lost the heirloom: " + zed.getMainHandItem());
			helper.assertValueEqual(lines(heard, "You need 3 hearts with Dara to help with this."), 1L, "the stranger's lines " + strings(heard));
			helper.assertTrue(zed.getInventory().countItem(Items.EMERALD) == 0 && me.getInventory().countItem(Items.EMERALD) == 0 && !quest[0].helpers.containsKey(zed.getUUID()),
				"paid: stranger " + zed.getInventory().countItem(Items.EMERALD) + ", me " + me.getInventory().countItem(Items.EMERALD));
			// The one who accepted can still hand it in.
			me.setItemInHand(InteractionHand.MAIN_HAND, zed.getMainHandItem().copy());
			zed.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			QaRequestKinds1116cGameTests.click(me, dara);
			helper.assertTrue(PersonalRequests.of(dara) == null && me.getInventory().countItem(Items.EMERALD) == 8 && zed.getInventory().countItem(Items.EMERALD) == 0,
				"after me: open " + (PersonalRequests.of(dara) != null) + ", me " + me.getInventory().countItem(Items.EMERALD) + ", stranger " + zed.getInventory().countItem(Items.EMERALD));
			helper.succeed();
		});
	}

	/**
	 * B1315's boundary, both sides: at 2 hearts (299 points) the heirloom is refused with the hearts line; one point more
	 * (3 hearts) and the same player hands it in, joins as a helper and both helpers are paid 8.
	 */
	//$ gametest_ticks_batch AREA '80' '"qa1116cHeirloomHearts"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1116cHeirloomHearts")
	public void twoHeartsIsRefusedAndThreeHeartsHandsIn(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			List<Component> heard = new ArrayList<>();
			ServerPlayer ana = listener(helper, heard, "qa1116cAna", new BlockPos(6, 2, 6), dara, 299);
			Quest[] quest = new Quest[1];
			ItemStack found = foundHeirloom(helper, dara, me, "horn", quest);
			ana.setItemInHand(InteractionHand.MAIN_HAND, found);
			heard.clear();
			QaRequestKinds1116cGameTests.click(ana, dara);
			helper.assertTrue(PersonalRequests.of(dara) == quest[0] && ana.getMainHandItem().is(Items.GOAT_HORN), "taken at 2 hearts: " + quest[0]);
			helper.assertValueEqual(lines(heard, "You need 3 hearts with Dara to help with this."), 1L, "the 2-heart player's lines " + strings(heard));
			helper.assertTrue(ana.getInventory().countItem(Items.EMERALD) == 0 && !quest[0].helpers.containsKey(ana.getUUID()), "paid at 2 hearts");
			int got = Friendship.add(dara, ana, 1);
			helper.assertTrue(got == 1 && Friendship.hearts(Friendship.points(dara, ana.getUUID())) == 3, "setup: not at 3 hearts: " + Friendship.points(dara, ana.getUUID()));
			heard.clear();
			QaRequestKinds1116cGameTests.click(ana, dara);
			helper.assertTrue(PersonalRequests.of(dara) == null && ana.getInventory().countItem(Items.GOAT_HORN) == 0, "not taken at 3 hearts: " + quest[0]);
			helper.assertTrue(me.getInventory().countItem(Items.EMERALD) == 8 && ana.getInventory().countItem(Items.EMERALD) == 8,
				"paid at 3 hearts: me " + me.getInventory().countItem(Items.EMERALD) + ", Ana " + ana.getInventory().countItem(Items.EMERALD));
			helper.assertValueEqual(lines(heard, "You need 3 hearts with Dara to help with this."), 0L, "the hearts line at 3 hearts " + strings(heard));
			helper.succeed();
		});
	}

	/**
	 * B1315 for the letter: a player with no hearts with Dara carrying her letter to the right relative isn't let deliver
	 * it (keeps it, told the hearts), nor carry the reply home; the one who accepted does both and alone is paid.
	 */
	//$ gametest_ticks_batch HUGE '120' '"qa1116cLetterStranger"'
	@GameTest(template = HUGE, timeoutTicks = 120, batch = "qa1116cLetterStranger")
	public void aStrangerCantCarryTheLetterOrTheReply(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			// two villages in one test area. HeartEventGameTests.village puts the radius back: a reset here would run after
			// that one (resets run in the order they were added) and leave its 16 for every later batch (the Hall's beds and
			// the questbook's rank counts read 16, not 64, in the 2026-11-16 nightly repeats)
			VillageHalls.RADIUS = 6;
			BlockPos farRel = new BlockPos(25, 2, 25);
			BlockPos far = helper.absolutePos(farRel);
			helper.setBlock(farRel, ModBlocks.VILLAGE_HALL);
			Leftovers.after(helper, () -> {
				Caravans.Data.get(level).remove(far);
				Stories.Data.get(level).remove(far);
			});
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(7, 2, 7), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(7, 2, 9), dara, 300);
			List<Component> heard = new ArrayList<>();
			ServerPlayer zed = listener(helper, heard, "qa1116cZed2", new BlockPos(8, 2, 9), dara, 0);
			helper.runAfterDelay(2, () -> {
				CivicEffects.forget();
				Caravans.Data.get(level).setWants(far, Component.literal("Elmford"), List.of());
				Villager mira = HeartEventGameTests.villager(helper, new BlockPos(25, 2, 23), "Mira");
				Villager nell = HeartEventGameTests.villager(helper, new BlockPos(23, 2, 25), "Nell");
				CivicEffects.forget();
				Quest quest = PersonalRequestGameTests.accepted(helper, "letter_to_family", dara, me);
				RequestErrands.Deliver deliver = (RequestErrands.Deliver) quest.objectives.get(0);
				helper.assertTrue(deliver.relative().isPresent(), "no relative: " + deliver);
				Villager relative = deliver.relative().get().equals(mira.getUUID()) ? mira : nell;
				ItemStack letter = PersonalErrandGameTests.marked(me, "aliveworkplace_letter", false);
				zed.setItemInHand(InteractionHand.MAIN_HAND, letter.copy());
				letter.setCount(0);
				heard.clear();
				QaRequestKinds1116cGameTests.click(zed, relative);
				helper.assertTrue(quest.progress[0] == 0 && RequestErrands.isCarried(zed.getMainHandItem()), "the stranger delivered the letter: " + quest);
				helper.assertValueEqual(lines(heard, "You need 3 hearts with Dara to help with this."), 1L, "the stranger's lines at the relative " + strings(heard));
				// The one who accepted delivers it.
				me.setItemInHand(InteractionHand.MAIN_HAND, zed.getMainHandItem().copy());
				zed.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				helper.assertTrue(QaRequestKinds1116cGameTests.click(me, relative).consumesAction() && quest.progress[0] == 1, "the relative didn't take it from me: " + quest);
				ItemStack reply = PersonalErrandGameTests.marked(me, "aliveworkplace_letter", true);
				helper.assertTrue(!reply.isEmpty(), "no reply");
				// The stranger carries the reply home: refused.
				zed.setItemInHand(InteractionHand.MAIN_HAND, reply.copy());
				reply.setCount(0);
				heard.clear();
				QaRequestKinds1116cGameTests.click(zed, dara);
				helper.assertTrue(PersonalRequests.of(dara) == quest && RequestErrands.isCarried(zed.getMainHandItem()), "Dara took the reply from a stranger");
				helper.assertValueEqual(lines(heard, "You need 3 hearts with Dara to help with this."), 1L, "the stranger's lines at Dara " + strings(heard));
				me.setItemInHand(InteractionHand.MAIN_HAND, zed.getMainHandItem().copy());
				zed.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				helper.assertTrue(QaRequestKinds1116cGameTests.click(me, dara).consumesAction() && PersonalRequests.of(dara) == null, "Dara didn't take the reply from me");
				helper.assertTrue(me.getInventory().countItem(Items.EMERALD) == 4 && zed.getInventory().countItem(Items.EMERALD) == 0,
					"paid: me " + me.getInventory().countItem(Items.EMERALD) + ", stranger " + zed.getInventory().countItem(Items.EMERALD));
				helper.succeed();
			});
		});
	}

	// --- B1316: the bell, then the Chapel ----------------------------------------------------------------------------

	/** Dara and Odo courting since {@code daysAgo} days, Dara's chapel wedding accepted by {@code me}; midday. */
	static Villager[] courting(GameTestHelper helper, long daysAgo) {
		ServerLevel level = helper.getLevel();
		boolean couples = Couples.ENABLED;
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			Couples.ENABLED = couples;
			level.setDayTime(time);
		});
		Couples.ENABLED = true;
		long today = Chronicle.day(level);
		Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 2);
		Villager odo = PersonalRequestGameTests.worker(helper, new BlockPos(7, 2, 4), "Odo", "minecraft:mason", 2);
		ModAttachments.PARTNER.set(dara, new Couples.Partner(odo.getUUID(), odo.getDisplayName(), today - daysAgo, false));
		ModAttachments.PARTNER.set(odo, new Couples.Partner(dara.getUUID(), dara.getDisplayName(), today - daysAgo, false));
		return new Villager[] {dara, odo};
	}

	static void chapelFinished(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement chapel = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 1, 2)), Rotation.NONE, Mirror.NONE);
		sites.recordFinished(StarterBlueprints.CHAPEL.id(), chapel, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(chapel));
	}

	/**
	 * B1316: married at the bell at 5 days with no Chapel, the request is missed on the hall's next look, as the wiki says:
	 * taken down, the helper told once "You didn't help Dara in time (A chapel wedding)..." and 30 friendship lost (from
	 * what they had after the bell wedding's guest friendship); a Chapel finished afterwards pays nothing, and there's no
	 * Chapel wedding in the chronicle or in Dara's mouth.
	 */
	//$ gametest_ticks_batch AREA '80' '"qa1116cBellThenMissed"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1116cBellThenMissed")
	public void marriedAtTheBellTheRequestIsMissedOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			Leftovers.finished(helper);
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager[] couple = courting(helper, 5);
			Villager dara = couple[0];
			List<Component> heard = new ArrayList<>();
			ServerPlayer me = listener(helper, heard, "qa1116cMe", new BlockPos(5, 2, 6), dara, 300);
			Quest quest = PersonalRequestGameTests.accepted(helper, "chapel_wedding", dara, me);
			helper.assertValueEqual(quest.title().getString(), "A chapel wedding", "the request's title");
			level.setDayTime(level.getDayTime() - level.getDayTime() % 24000 + 7000);
			Couples.round(level, hall);
			helper.assertTrue(Couples.partner(dara).married() && Couples.partner(couple[1]).married(), "setup: not married at the bell at 5 days");
			int before = Friendship.points(dara, me.getUUID());
			String why = "(before the look " + before + ", eligible " + Friendship.eligible(dara) + ", hall " + CivicEffects.hallOf(dara) + ", let down "
				+ ModAttachments.REQUEST_LET_DOWN.get(dara) + ")";
			heard.clear();
			PersonalRequests.look(level, hall, PersonalRequestGameTests.hallEntity(helper), false);
			PersonalRequests.look(level, hall, PersonalRequestGameTests.hallEntity(helper), false);
			why += " (after: let down " + ModAttachments.REQUEST_LET_DOWN.get(dara) + ", eligible " + Friendship.eligible(dara) + ")";
			helper.assertTrue(PersonalRequests.of(dara) == null, "the request is still open after the bell wedding");
			helper.assertValueEqual(lines(heard, "You didn't help Dara in time (A chapel wedding). They feel let down: 30 friendship lost."), 1L,
				"the missed line, once: " + strings(heard));
			helper.assertValueEqual(Friendship.points(dara, me.getUUID()), before - 30, "friendship after the miss (the bell wedding's guests got theirs first) " + why);
			chapelFinished(helper);
			heard.clear();
			PersonalRequests.look(level, hall, PersonalRequestGameTests.hallEntity(helper), false);
			Couples.round(level, hall);
			helper.assertValueEqual(me.getInventory().countItem(Items.EMERALD), 0, "emeralds for a Chapel finished after the bell");
			helper.assertTrue(!PersonalRequestGameTests.said(dara).startsWith("Married in the Chapel"), "Dara thanks for a Chapel wedding: " + PersonalRequestGameTests.said(dara));
			List<String> weddings = PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.WEDDING);
			helper.assertTrue(weddings.stream().noneMatch(l -> l.contains("Chapel")), "a Chapel wedding in the chronicle: " + weddings);
			helper.assertTrue(strings(heard).stream().noneMatch(s -> s.contains("Dara")), "lines about Dara after the Chapel: " + strings(heard));
			helper.succeed();
		});
	}

	/**
	 * B1316's other side: the Chapel finished on the very day the 5 days run out wins: the bell's round doesn't marry them,
	 * the request's look marries them in the Chapel with the feast, pays 6 and Dara thanks word for word; a later round
	 * doesn't marry them a second time.
	 */
	//$ gametest_ticks_batch AREA '80' '"qa1116cChapelOnDayFive"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1116cChapelOnDayFive")
	public void theChapelFinishedOnTheFifthDayWinsOverTheBell(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			Leftovers.finished(helper);
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager[] couple = courting(helper, 5);
			Villager dara = couple[0];
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			Quest quest = PersonalRequestGameTests.accepted(helper, "chapel_wedding", dara, me);
			chapelFinished(helper);
			level.setDayTime(level.getDayTime() - level.getDayTime() % 24000 + 7000);
			Couples.round(level, hall);
			helper.assertTrue(!Couples.partner(dara).married(), "married at the bell though the Chapel is finished");
			helper.assertTrue(PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.WEDDING).isEmpty(), "a wedding line at the bell: "
				+ PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.WEDDING));
			PersonalRequests.look(level, hall, PersonalRequestGameTests.hallEntity(helper), false);
			helper.assertTrue(PersonalRequests.of(dara) == null && me.getInventory().countItem(Items.EMERALD) == 6, "the Chapel didn't finish it: " + quest);
			helper.assertTrue(Couples.partner(dara).married() && Couples.partner(couple[1]).married(), "not married in the Chapel");
			helper.assertValueEqual(PersonalRequestGameTests.said(dara),
				"Married in the Chapel, with the whole village at the feast. It was everything I dreamed of, " + ME + ".", "Dara's thanks");
			List<String> weddings = PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.WEDDING);
			helper.assertTrue(weddings.stream().filter("Dara and Odo were married in the new Chapel, and the whole village sat down to a feast"::equals).count() == 1,
				"the Chapel wedding in the chronicle: " + weddings);
			// (the extra "married at the bell" line beside it is QaRequestFixes1116cBugTests' bug)
			Couples.round(level, hall);
			helper.assertTrue(PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.WEDDING).equals(weddings),
				"a later round married them again: " + PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.WEDDING));
			helper.succeed();
		});
	}
}
