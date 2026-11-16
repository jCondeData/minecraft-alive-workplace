package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.RequestErrands;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * QA (lane-c-1116-0352) for the personal requests the earlier classes leave thin (ROADMAP 31.10, docs/wiki/personal-requests.md):
 * a cat by the door with any cat (untamed, or tamed and sat down by another player) and a cat on a roof just outside
 * ten blocks, the giver's bed moved or lost mid-request, the switch off before dawn; the letter refused by the wrong
 * villager in the other village and its reply carried home by a second friend; a renamed heirloom handed in by a second
 * friend; the chapel wedding's five-day wait at its edge; and the lines of these kinds in American English. Hand-ins go
 * through the player's right-click (Fabric's UseEntityCallback), as in the game.
 */
public class QaRequestKinds1116cGameTests implements FabricGameTest {
	static final String AREA = PersonalRequestGameTests.AREA;
	static final String HUGE = "aliveworkplace_test:huge_area";
	static final BlockPos HALL = PersonalRequestGameTests.HALL;
	static final String ME = PersonalRequestGameTests.ME;

	static VillageHallBlockEntity hallEntity(GameTestHelper helper) {
		return PersonalRequestGameTests.hallEntity(helper);
	}

	/** {@code player} right-clicks {@code villager} with what they hold, the way the game asks (the main hand's turn). */
	static InteractionResult click(ServerPlayer player, Villager villager) {
		return UseEntityCallback.EVENT.invoker().interact(player, player.serverLevel(), InteractionHand.MAIN_HAND, villager, null);
	}

	static void bedAt(GameTestHelper helper, Villager villager, BlockPos rel) {
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(rel)));
	}

	static Cat cat(GameTestHelper helper, BlockPos rel) {
		helper.setBlock(rel.below(), Blocks.STONE);
		Cat cat = helper.spawn(EntityType.CAT, rel);
		cat.setNoAi(true);
		return cat;
	}

	// --- A cat by the door ----------------------------------------------------------------------------------------

	/**
	 * "A cat or a tamed wolf within 10 blocks of their bed at dawn": any cat is a cat. A wild one counts, and so does one
	 * tamed by another player and told to sit; only the helper is paid.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaKindsAnyCat"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaKindsAnyCat")
	public void anyCatCountsEvenAnotherPlayersSittingOne(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(3, 2, 3), "Dara", "minecraft:farmer", 2);
			Villager odo = PersonalRequestGameTests.worker(helper, new BlockPos(18, 2, 3), "Odo", "minecraft:mason", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(9, 2, 6), dara, 300);
			Friendship.add(odo, me, 300);
			ServerPlayer other = HeartEventGameTests.player(helper, new BlockPos(10, 2, 6));
			bedAt(helper, dara, new BlockPos(2, 2, 2));
			bedAt(helper, odo, new BlockPos(19, 2, 2));
			Quest daras = PersonalRequestGameTests.accepted(helper, "cat_by_the_door", dara, me);
			Quest odos = PersonalRequestGameTests.accepted(helper, "cat_by_the_door", odo, me);
			// A wild cat 8 blocks from Dara's bed; a cat of the other player's, sitting, 9 blocks from Odo's.
			Cat wild = cat(helper, new BlockPos(2, 2, 10));
			Cat theirs = cat(helper, new BlockPos(19, 2, 11));
			theirs.tame(other);
			theirs.setOrderedToSit(true);
			theirs.setInSittingPose(true);
			helper.assertTrue(!wild.isTame() && theirs.isTame() && theirs.isOwnedBy(other), "the cats aren't as meant");
			PersonalRequests.look(level, hall, hallEntity(helper), true);
			helper.assertTrue(PersonalRequests.of(dara) == null, "a wild cat by the bed didn't count: " + daras);
			helper.assertTrue(PersonalRequests.of(odo) == null, "another player's sitting cat didn't count: " + odos);
			helper.assertTrue(me.getInventory().countItem(Items.EMERALD) == 6 && other.getInventory().countItem(Items.EMERALD) == 0,
				"paid: me " + me.getInventory().countItem(Items.EMERALD) + ", the cat's owner " + other.getInventory().countItem(Items.EMERALD));
			helper.succeed();
		});
	}

	/**
	 * Ten blocks is a distance, not a box: a cat on a roof 7 along and 8 up (10.6 blocks) is inside a 10-block box but not
	 * within 10 blocks, and doesn't count; a tamed wolf of another player 6 along and 8 up (10 exactly) does.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaKindsRoofCat"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaKindsRoofCat")
	public void aCatOnARoofJustOutsideTenBlocksDoesntCount(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(3, 2, 3), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(9, 2, 6), dara, 300);
			ServerPlayer other = HeartEventGameTests.player(helper, new BlockPos(10, 2, 6));
			bedAt(helper, dara, new BlockPos(2, 2, 2));
			Quest quest = PersonalRequestGameTests.accepted(helper, "cat_by_the_door", dara, me);
			Cat roof = cat(helper, new BlockPos(9, 10, 2));
			PersonalRequests.look(level, hall, hallEntity(helper), true);
			helper.assertTrue(PersonalRequests.of(dara) == quest && me.getInventory().countItem(Items.EMERALD) == 0,
				"a cat 10.6 blocks from the bed counted (" + Math.sqrt(roof.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))))) + ")");
			roof.discard();
			helper.setBlock(new BlockPos(8, 9, 2), Blocks.STONE);
			Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(8, 10, 2));
			wolf.setNoAi(true);
			wolf.tame(other);
			wolf.setOrderedToSit(true);
			PersonalRequests.look(level, hall, hallEntity(helper), true);
			helper.assertTrue(PersonalRequests.of(dara) == null && me.getInventory().countItem(Items.EMERALD) == 3,
				"another player's tamed wolf exactly 10 blocks away didn't count: " + quest);
			helper.succeed();
		});
	}

	/**
	 * The giver's home changes mid-request: a cat by the old bed doesn't count once they sleep elsewhere; with no bed at
	 * all the request stays open (nothing done, nobody let down); a cat by the new bed finishes it.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaKindsBedMoved"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaKindsBedMoved")
	public void aBedMovedOrLostMidRequest(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(3, 2, 3), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(9, 2, 6), dara, 300);
			bedAt(helper, dara, new BlockPos(2, 2, 2));
			Quest quest = PersonalRequestGameTests.accepted(helper, "cat_by_the_door", dara, me);
			cat(helper, new BlockPos(3, 2, 2));
			// She moves to a bed 18 blocks away: the cat by the old one is nothing to her.
			bedAt(helper, dara, new BlockPos(20, 2, 20));
			PersonalRequests.look(level, hall, hallEntity(helper), true);
			helper.assertTrue(PersonalRequests.of(dara) == quest, "a cat by the bed she left counted");
			// No bed at all: still open, and nothing lost.
			dara.getBrain().eraseMemory(MemoryModuleType.HOME);
			PersonalRequests.look(level, hall, hallEntity(helper), true);
			helper.assertTrue(PersonalRequests.of(dara) == quest && Friendship.points(dara, me.getUUID()) == 300,
				"with no bed: " + PersonalRequests.of(dara) + ", " + Friendship.points(dara, me.getUUID()));
			// The new bed, and a cat by it.
			bedAt(helper, dara, new BlockPos(20, 2, 20));
			cat(helper, new BlockPos(18, 2, 19));
			PersonalRequests.look(level, hall, hallEntity(helper), true);
			helper.assertTrue(PersonalRequests.of(dara) == null && me.getInventory().countItem(Items.EMERALD) == 3, "a cat by the new bed didn't count: " + quest);
			helper.succeed();
		});
	}

	/**
	 * The switch turned off with a cat request open: the hall's own round still judges it at dawn (an open request is
	 * finished as ever, wiki), and nobody new asks.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaKindsCatSwitchOff"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaKindsCatSwitchOff")
	public void theSwitchOffStillFinishesAnOpenCatRequestAtDawn(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(3, 2, 3), "Dara", "minecraft:farmer", 2);
			Villager odo = PersonalRequestGameTests.worker(helper, new BlockPos(6, 2, 3), "Odo", "minecraft:mason", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			Friendship.add(odo, me, 300);
			bedAt(helper, dara, new BlockPos(2, 2, 2));
			Quest quest = PersonalRequestGameTests.accepted(helper, "cat_by_the_door", dara, me);
			PersonalRequests.ENABLED = false; // (put back by the village)
			cat(helper, new BlockPos(4, 2, 2));
			// The first round of the day is its dawn.
			Stories.round(level, hall, hallEntity(helper));
			helper.assertTrue(PersonalRequests.of(dara) == null && me.getInventory().countItem(Items.EMERALD) == 3,
				"the switch off left the cat request open: " + quest);
			helper.assertTrue(PersonalRequests.of(odo) == null && PersonalRequests.offer(odo) == null, "Odo asked with the switch off");
			helper.succeed();
		});
	}

	// --- A letter to family ---------------------------------------------------------------------------------------

	/**
	 * The letter goes only to the named relative: another villager of her village doesn't take it (and it stays in your
	 * hand); the reply isn't taken by the relative or anyone but the giver. A second friend of the giver (3 hearts) who
	 * carries the reply home is a helper too, and both are paid.
	 */
	//$ gametest_ticks_batch HUGE '120' '"qaKindsLetter"'
	@GameTest(template = HUGE, timeoutTicks = 120, batch = "qaKindsLetter")
	public void theLetterOnlyToTheRelativeAndASecondFriendBringsTheReply(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			int radius = VillageHalls.RADIUS;
			VillageHalls.RADIUS = 6; // two villages in one test area
			BlockPos farRel = new BlockPos(25, 2, 25);
			BlockPos far = helper.absolutePos(farRel);
			helper.setBlock(farRel, ModBlocks.VILLAGE_HALL);
			Leftovers.after(helper, () -> {
				VillageHalls.RADIUS = radius;
				Caravans.Data.get(level).remove(far);
				Stories.Data.get(level).remove(far);
			});
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(7, 2, 7), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(7, 2, 9), dara, 300);
			ServerPlayer ana = PersonalRequestGameTests.friend(helper, new BlockPos(8, 2, 9), dara, 300);
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
				Villager stranger = relative == mira ? nell : mira;
				ItemStack letter = PersonalErrandGameTests.marked(me, "aliveworkplace_letter", false);
				PersonalErrandGameTests.hold(me, letter);
				// To the wrong one in Elmford: not taken, still in hand.
				click(me, stranger);
				helper.assertTrue(quest.progress[0] == 0 && RequestErrands.isCarried(me.getMainHandItem()),
					stranger.getDisplayName().getString() + " took a letter for " + relative.getDisplayName().getString() + ": " + quest);
				// To the relative: delivered, the reply in hand.
				helper.assertTrue(click(me, relative).consumesAction() && quest.progress[0] == 1, "the relative didn't take it: " + quest);
				ItemStack reply = PersonalErrandGameTests.marked(me, "aliveworkplace_letter", true);
				helper.assertTrue(!reply.isEmpty(), "no reply");
				// The reply back to the relative, or to the wrong villager: not taken.
				PersonalErrandGameTests.hold(me, reply);
				click(me, relative);
				click(me, stranger);
				helper.assertTrue(PersonalRequests.of(dara) == quest && RequestErrands.isCarried(me.getMainHandItem()), "the reply was taken by someone not Dara");
				// Ana, a friend of Dara's too, carries the reply home: done, and both are paid.
				ana.setItemInHand(InteractionHand.MAIN_HAND, me.getMainHandItem().copy());
				me.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				helper.assertTrue(click(ana, dara).consumesAction(), "Dara didn't take the reply from Ana");
				helper.assertTrue(PersonalRequests.of(dara) == null && me.getInventory().countItem(Items.EMERALD) == 4 && ana.getInventory().countItem(Items.EMERALD) == 4,
					"paid: me " + me.getInventory().countItem(Items.EMERALD) + ", Ana " + ana.getInventory().countItem(Items.EMERALD));
				helper.assertTrue(Friendship.points(dara, ana.getUUID()) == 450 && Friendship.points(dara, me.getUUID()) == 450,
					"friendship: me " + Friendship.points(dara, me.getUUID()) + ", Ana " + Friendship.points(dara, ana.getUUID()));
				helper.succeed();
			});
		});
	}

	// --- The lost heirloom ----------------------------------------------------------------------------------------

	/**
	 * The heirloom renamed on an anvil is still the heirloom: a second friend of the giver (3 hearts) hands it in and it's
	 * done, every helper paid, the story in the chronicle under its own name.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaKindsHeirloomRenamed"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaKindsHeirloomRenamed")
	public void aRenamedHeirloomHandedInByASecondFriend(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			ServerPlayer ana = PersonalRequestGameTests.friend(helper, new BlockPos(6, 2, 6), dara, 300);
			BlockPos chestRel = new BlockPos(14, 2, 14);
			helper.setBlock(chestRel, Blocks.CHEST);
			Quest quest = PersonalErrandGameTests.acceptedFile(helper, PersonalErrandGameTests.heirloomAt(helper, chestRel, "pocket_watch"), dara, me);
			PersonalErrandGameTests.moveTo(me, helper.absolutePos(chestRel).offset(30, 0, 0));
			helper.assertTrue(RequestErrands.placeHeirlooms(level) == 1, "not put in the chest");
			ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(chestRel));
			ItemStack found = ItemStack.EMPTY;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (!chest.getItem(i).isEmpty()) {
					found = chest.removeItemNoUpdate(i);
				}
			}
			helper.assertTrue(found.is(Items.CLOCK), "the watch: " + found);
			found.set(DataComponents.CUSTOM_NAME, Component.literal("Shiny Thing"));
			PersonalErrandGameTests.moveTo(me, helper.absolutePos(new BlockPos(5, 2, 6)));
			ana.setItemInHand(InteractionHand.MAIN_HAND, found);
			helper.assertTrue(click(ana, dara).consumesAction(), "Dara didn't take her renamed watch");
			helper.assertTrue(PersonalRequests.of(dara) == null && ana.getInventory().countItem(Items.CLOCK) == 0
				&& me.getInventory().countItem(Items.EMERALD) == 8 && ana.getInventory().countItem(Items.EMERALD) == 8,
				"paid: me " + me.getInventory().countItem(Items.EMERALD) + ", Ana " + ana.getInventory().countItem(Items.EMERALD) + "; " + quest);
			List<String> stories = PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.STORY);
			helper.assertTrue(stories.stream().anyMatch(l -> l.startsWith("Grandfather's Pocket Watch ticks again in Dara's house")),
				"the story: " + stories);
			helper.succeed();
		});
	}

	// --- A chapel wedding -----------------------------------------------------------------------------------------

	/** The wedding waits for the Chapel "up to 5 days from when they began courting": at 4 days it still waits, at 5 they marry at the bell. */
	//$ gametest_ticks_batch AREA '80' '"qaKindsChapelWait"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaKindsChapelWait")
	public void theChapelWeddingWaitsFourDaysButNotFive(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			Leftovers.finished(helper);
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
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
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			ModAttachments.PARTNER.set(dara, new Couples.Partner(odo.getUUID(), odo.getDisplayName(), today - 4, false));
			ModAttachments.PARTNER.set(odo, new Couples.Partner(dara.getUUID(), dara.getDisplayName(), today - 4, false));
			Quest quest = PersonalRequestGameTests.accepted(helper, "chapel_wedding", dara, me);
			level.setDayTime(level.getDayTime() - level.getDayTime() % 24000 + 7000);
			Couples.round(level, hall);
			helper.assertTrue(!Couples.partner(dara).married() && !Couples.partner(odo).married(), "married at 4 days without the Chapel");
			// A day later (5 days courting): no Chapel yet, so they marry at the bell as before.
			ModAttachments.PARTNER.set(dara, new Couples.Partner(odo.getUUID(), odo.getDisplayName(), today - 5, false));
			ModAttachments.PARTNER.set(odo, new Couples.Partner(dara.getUUID(), dara.getDisplayName(), today - 5, false));
			Couples.round(level, hall);
			helper.assertTrue(Couples.partner(dara).married() && Couples.partner(odo).married(), "still waiting at 5 days");
			helper.assertTrue(PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.WEDDING).stream().noneMatch(l -> l.contains("Chapel")),
				"a Chapel wedding with no Chapel: " + PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.WEDDING));
			helper.assertTrue(me.getInventory().countItem(Items.EMERALD) == 0, "paid for a wedding at the bell");
			helper.succeed();
		});
	}

	// --- Words ----------------------------------------------------------------------------------------------------

	static final String[] KEYS = {
		"request.aliveworkplace.letter_to_family", "request.aliveworkplace.cat_by_the_door", "request.aliveworkplace.chapel_wedding",
		"request.aliveworkplace.lost_heirloom", "request.aliveworkplace.letter", "request.aliveworkplace.heirloom", "request.aliveworkplace.relative",
		"quest.aliveworkplace.deliver", "quest.aliveworkplace.pet_near_home", "quest.aliveworkplace.build", "quest.aliveworkplace.fetch",
		"item.aliveworkplace.letter.", "item.aliveworkplace.heirloom", "message.aliveworkplace.letter.", "message.aliveworkplace.heirloom",
		"chronicle.aliveworkplace.letter", "chronicle.aliveworkplace.chapel_wedding", "chronicle.aliveworkplace.heirloom",
		"message.aliveworkplace.request.need_hearts", "screen.aliveworkplace.request.need_hearts"};
	static final Pattern BRITISH = Pattern.compile("(?i)\\b(\\w*(favour|colour|neighbour|honour|labour|harbour|armour|centre|theatre|grey|travelled|traveller|"
		+ "cancelled|jeweller|defence|realis|organis|recognis|apologis|whilst|amongst|learnt|spelt|dreamt|programme|cheque|mum)\\w*)\\b");

	/**
	 * Every line a player reads for these kinds is in the lang file, filled the same way in every language slot, and in
	 * American English (owner, 2026-10-26); the request's wants line reads right for every heirloom.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '80' '"qaKindsWords"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80, batch = "qaKindsWords")
	public void theseKindsReadInAmericanEnglish(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> found = new ArrayList<>();
		int seen = 0;
		java.util.Map<String, String> all = new java.util.TreeMap<>();
		try (var in = QaRequestKinds1116cGameTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
			json.entrySet().forEach(e -> all.put(e.getKey(), e.getValue().getAsString()));
		} catch (java.io.IOException | RuntimeException e) {
			helper.fail("the lang file: " + e);
			return;
		}
		for (var e : all.entrySet()) {
			boolean ours = false;
			for (String k : KEYS) {
				ours |= e.getKey().startsWith(k);
			}
			if (!ours) {
				continue;
			}
			seen++;
			helper.assertTrue(lang.has(e.getKey()) && lang.getOrDefault(e.getKey()).equals(e.getValue()), "not loaded: " + e.getKey());
			Matcher m = BRITISH.matcher(e.getValue());
			while (m.find()) {
				found.add(e.getKey() + ": " + m.group(1));
			}
		}
		helper.assertTrue(seen >= 60, "only " + seen + " lines of these kinds");
		helper.assertTrue(found.isEmpty(), "not American English: " + found);
		// The wants line for every heirloom, through the objective a request carries.
		for (String heirloom : RequestErrands.HEIRLOOMS.keySet()) {
			String name = Component.translatable("request.aliveworkplace.heirloom." + heirloom).getString();
			String wants = Component.translatable("request.aliveworkplace.lost_heirloom.wants", "Dara", ME, Component.translatable("request.aliveworkplace.heirloom." + heirloom)).getString();
			helper.assertTrue(wants.equals("Dara wants their family's heirloom back: " + name) && !name.startsWith("request."), "the wants line: " + wants);
		}
		helper.succeed();
	}
}
