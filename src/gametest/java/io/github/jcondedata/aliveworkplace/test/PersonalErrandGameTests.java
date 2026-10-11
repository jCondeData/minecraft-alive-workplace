package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.school.Schools;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Places;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestFiles;
import io.github.jcondedata.aliveworkplace.story.RequestErrands;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Six more personal requests (ROADMAP 31.10), each through its objective: the letter there and back between two halls
 * (lost and written again, with the switch off on the way), the revive with a grave and the second wedding, the cat at
 * dawn, the schooled child, the chapel wedding (it waits for the Chapel) and its feast, and the heirloom put once when a
 * helper comes near, with the map; a save and reload of the new objectives and of a placed heirloom; and the sentences a
 * player reads. The engine doesn't tick itself in gametests; the tests tick it.
 */
public class PersonalErrandGameTests implements FabricGameTest {
	static final String HUGE = "aliveworkplace_test:huge_area";
	static final String AREA = PersonalRequestGameTests.AREA;
	static final BlockPos HALL = PersonalRequestGameTests.HALL;
	static final String ME = PersonalRequestGameTests.ME;

	static VillageHallBlockEntity hallEntity(GameTestHelper helper) {
		return PersonalRequestGameTests.hallEntity(helper);
	}

	/** The marked item in {@code player}'s inventory under {@code key} (and, for a letter, whether it's the reply), or empty. */
	static ItemStack marked(ServerPlayer player, String key, boolean reply) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			CompoundTag tag = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
			if (tag.contains(key) && tag.getBoolean("aliveworkplace_reply") == reply) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	/** {@code stack} taken out of the inventory and held in the main hand. */
	static void hold(ServerPlayer player, ItemStack stack) {
		ItemStack copy = stack.copy();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i) == stack) {
				player.getInventory().setItem(i, ItemStack.EMPTY);
			}
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, copy);
	}

	static List<String> chronicle(ServerLevel level, BlockPos hall, Chronicle.Kind kind) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		return entity.chronicle().stream().filter(e -> e.kind() == kind).map(e -> e.text().getString()).toList();
	}

	static String wants(GameTestHelper helper, Quest quest) {
		return PersonalRequests.wants(helper.getLevel(), quest).getString();
	}

	// --- A letter to family ---------------------------------------------------------------------------------------

	/**
	 * The letter there and back between two halls: only asked with a neighbouring village that has someone to write to;
	 * the sealed letter on accepting; lost, Dara writes another; Mira in the other village reads it (friendship, a line
	 * in her chronicle) and writes back; the reply to Dara finishes it, paid. The switch off on the way changes nothing
	 * for a request already open, and nobody asks.
	 */
	//$ gametest_ticks_batch HUGE '120' '"requestLetter"'
	@GameTest(template = HUGE, timeoutTicks = 120, batch = "requestLetter")
	public void aLetterThereAndBackBetweenTwoHalls(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHalls.RADIUS = 6; // two villages in one test area (put back afterwards)
			BlockPos farRel = new BlockPos(25, 2, 25);
			BlockPos far = helper.absolutePos(farRel);
			helper.setBlock(farRel, ModBlocks.VILLAGE_HALL);
			Leftovers.after(helper, () -> {
				Caravans.Data.get(level).remove(far);
				Stories.Data.get(level).remove(far);
			});
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(7, 2, 7), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(7, 2, 9), dara, 300);
			helper.runAfterDelay(2, () -> {
				CivicEffects.forget();
				// No village within caravan range: nothing to ask.
				helper.assertTrue(PersonalRequestGameTests.resolve(helper, "letter_to_family", dara, me) == null, "a letter with nowhere to send it");
				Caravans.Data.get(level).setWants(far, Component.literal("Elmford"), List.of());
				helper.assertTrue(PersonalRequestGameTests.resolve(helper, "letter_to_family", dara, me) == null, "a letter to a village with nobody in it");
				Villager mira = HeartEventGameTests.villager(helper, new BlockPos(25, 2, 23), "Mira");
				CivicEffects.forget();

				Quest quest = PersonalRequestGameTests.accepted(helper, "letter_to_family", dara, me);
				RequestErrands.Deliver deliver = (RequestErrands.Deliver) quest.objectives.get(0);
				helper.assertTrue(deliver.relative().equals(Optional.of(mira.getUUID())) && deliver.to().equals(Optional.of(far))
					&& deliver.village().equals("Elmford") && deliver.name().equals("Mira"), "the relative: " + deliver);
				String relation = Component.translatable("request.aliveworkplace.relative." + deliver.relation()).getString();
				helper.assertTrue(wants(helper, quest).equals("Dara wants a letter carried to their " + relation + " Mira in Elmford, and the reply brought back within 7 days"),
					wants(helper, quest));
				helper.assertTrue(quest.objectives.get(0).line().getString().equals("Carry the letter to Mira in Elmford, and their reply back"),
					quest.objectives.get(0).line().getString());
				// The sealed letter: paper, named, with lore and the quest mark.
				ItemStack letter = marked(me, "aliveworkplace_letter", false);
				String home = VillageHalls.name(level, hall).getString();
				helper.assertTrue(letter.is(Items.PAPER) && letter.getHoverName().getString().equals("Sealed Letter to Mira")
					&& PersonalRequestGameTests.lore(letter).equals(List.of("From Dara of " + home, "For Mira in Elmford", "Right-click Mira with it"))
					&& letter.hasFoil(), "the letter: " + letter.getHoverName().getString() + " " + PersonalRequestGameTests.lore(letter));
				// Lost: Dara writes another when you come to her empty-handed.
				me.getInventory().clearContent();
				helper.assertTrue(RequestErrands.use(me, level, InteractionHand.MAIN_HAND, dara) && !marked(me, "aliveworkplace_letter", false).isEmpty(),
					"no new letter for a lost one");
				helper.assertTrue(PersonalRequestGameTests.said(dara).equals("Lost it, " + ME + "? Never mind, I'll write it out again. Here."),
					PersonalRequestGameTests.said(dara));
				// With one in hand she doesn't write again, and the letter means nothing to her.
				hold(me, marked(me, "aliveworkplace_letter", false));
				helper.assertTrue(!RequestErrands.use(me, level, InteractionHand.MAIN_HAND, dara), "Dara took her own letter");

				// The switch off: the open request carries on, and nobody asks.
				PersonalRequests.ENABLED = false;
				helper.assertTrue(PersonalRequests.pick(level, hall, hallEntity(helper), RandomSource.create(1), Chronicle.day(level)) == null, "asked with the switch off");
				int before = Friendship.points(mira, me.getUUID());
				helper.assertTrue(RequestErrands.use(me, level, InteractionHand.MAIN_HAND, mira), "Mira didn't take the letter");
				helper.assertTrue(quest.progress[0] == 1 && Friendship.points(mira, me.getUUID()) > before, "delivered: " + quest + ", " + Friendship.points(mira, me.getUUID()));
				helper.assertTrue(chronicle(level, far, Chronicle.Kind.FRIEND).contains(ME + " brought Mira a letter from Dara of " + home),
					"Elmford's chronicle: " + chronicle(level, far, Chronicle.Kind.FRIEND));
				helper.assertTrue(PersonalRequestGameTests.said(mira).equals("A letter from Dara! It's been so long. Wait here, " + ME + ", I'll write back at once. Take this to them, would you?"),
					PersonalRequestGameTests.said(mira));
				ItemStack reply = marked(me, "aliveworkplace_letter", true);
				helper.assertTrue(reply.getHoverName().getString().equals("Reply from Mira")
					&& PersonalRequestGameTests.lore(reply).equals(List.of("From Mira in Elmford", "For Dara in " + home, "Right-click Dara with it")),
					"the reply: " + reply.getHoverName().getString() + " " + PersonalRequestGameTests.lore(reply));
				// The reply lost too: Mira writes it again.
				me.getInventory().clearContent();
				me.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				helper.assertTrue(RequestErrands.use(me, level, InteractionHand.MAIN_HAND, mira) && !marked(me, "aliveworkplace_letter", true).isEmpty(),
					"no new reply for a lost one");
				// The reply to Dara: done and paid, a line in her chronicle too.
				hold(me, marked(me, "aliveworkplace_letter", true));
				helper.assertTrue(RequestErrands.use(me, level, InteractionHand.MAIN_HAND, dara), "Dara didn't take the reply");
				helper.assertTrue(PersonalRequests.of(dara) == null && me.getInventory().countItem(Items.EMERALD) == 4 && Friendship.points(dara, me.getUUID()) == 450,
					"the reply home: " + PersonalRequests.of(dara) + ", " + Friendship.points(dara, me.getUUID()));
				helper.assertTrue(chronicle(level, hall, Chronicle.Kind.FRIEND).contains(ME + " helped Dara: A letter to family"),
					"Dara's chronicle: " + chronicle(level, hall, Chronicle.Kind.FRIEND));
				helper.succeed();
			});
		});
	}

	// --- Bring back my love ---------------------------------------------------------------------------------------

	/** A widow whose partner's grave is in the village asks; the grave revived (an Undertaker's work) finishes it, and they marry again. */
	//$ gametest_ticks_batch AREA '80' '"requestRevive"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "requestRevive")
	public void bringBackMyLoveWithAGrave(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Graves.ENABLED = true; // (put back by the village)
			long today = Chronicle.day(level);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 2);
			Villager odo = PersonalRequestGameTests.worker(helper, new BlockPos(12, 2, 4), "Odo", "minecraft:mason", 3);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			ModAttachments.PARTNER.set(dara, new Couples.Partner(odo.getUUID(), odo.getDisplayName(), today - 10, true));
			ModAttachments.PARTNER.set(odo, new Couples.Partner(dara.getUUID(), dara.getDisplayName(), today - 10, true));
			helper.assertTrue(PersonalRequestGameTests.resolve(helper, "bring_back_my_love", dara, me) == null, "asked with her husband alive");
			BlockPos grave = Graves.onDeath(level, odo);
			Couples.onDeath(level, odo);
			odo.discard();
			helper.assertTrue(grave != null && ModAttachments.LATE_PARTNER.get(dara) != null, "no grave, or not widowed");
			helper.runAfterDelay(2, () -> { // (the grave's POI goes in after the tick it was placed)

				Quest quest = PersonalRequestGameTests.accepted(helper, "bring_back_my_love", dara, me);
				helper.assertTrue(wants(helper, quest).equals("Dara wants Odo back from the grave within 10 days"), wants(helper, quest));
				helper.assertTrue(quest.objectives.get(0).line().getString()
					.equals("Odo brought back from the grave (an Undertaker, with a golden apple, a healing potion or a totem)"), quest.objectives.get(0).line().getString());
				PersonalRequests.look(level, hall, hallEntity(helper), true);
				helper.assertTrue(PersonalRequests.of(dara) == quest, "done with Odo still in the grave");
				Villager back = Graves.revive(level, grave);
				helper.assertTrue(back != null, "the grave didn't give him back");
				Couples.Partner p = Couples.partner(dara);
				helper.assertTrue(PersonalRequests.of(dara) == null && me.getInventory().countItem(Items.EMERALD) == 6, "the revive didn't finish it: " + quest);
				helper.assertTrue(p != null && p.married() && p.id().equals(back.getUUID()) && ModAttachments.LATE_PARTNER.get(dara) == null
					&& Couples.partner(back) != null && Couples.partner(back).id().equals(dara.getUUID()), "no second wedding: " + p);
				helper.assertTrue(chronicle(level, hall, Chronicle.Kind.WEDDING).contains("Odo came back from the grave, and Dara married them again"),
					"the chronicle: " + chronicle(level, hall, Chronicle.Kind.WEDDING));
				helper.succeed();
			});
		});
	}

	// --- A cat by the door ----------------------------------------------------------------------------------------

	/** A cat by their bed at dawn: judged only at dawn; an untamed wolf doesn't count; with a pet there already, it isn't asked. */
	//$ gametest_ticks_batch AREA '80' '"requestCat"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "requestCat")
	public void aCatByTheDoorAtDawn(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(3, 2, 3), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			// No bed: nothing to ask.
			helper.assertTrue(PersonalRequestGameTests.resolve(helper, "cat_by_the_door", dara, me) == null, "asked with no bed");
			dara.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(2, 2, 2))));
			Quest quest = PersonalRequestGameTests.accepted(helper, "cat_by_the_door", dara, me);
			helper.assertTrue(wants(helper, quest).equals("Dara wants a cat or a tamed wolf by their bed at dawn within 4 days"), wants(helper, quest));
			helper.assertTrue(quest.objectives.get(0).line().getString().equals("A cat or a tamed wolf within 10 blocks of their bed at dawn"),
				quest.objectives.get(0).line().getString());
			Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(4, 2, 2));
			wolf.setNoAi(true);
			PersonalRequests.look(level, hall, hallEntity(helper), true);
			helper.assertTrue(PersonalRequests.of(dara) == quest, "an untamed wolf counted");
			Cat cat = helper.spawn(EntityType.CAT, new BlockPos(2, 2, 4));
			cat.setNoAi(true);
			PersonalRequests.look(level, hall, hallEntity(helper), false);
			helper.assertTrue(PersonalRequests.of(dara) == quest, "judged before dawn");
			PersonalRequests.look(level, hall, hallEntity(helper), true);
			helper.assertTrue(PersonalRequests.of(dara) == null && me.getInventory().countItem(Items.EMERALD) == 3, "the cat at dawn didn't count");
			// Someone with a cat by their bed already has nothing to ask.
			Villager odo = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 3), "Odo", "minecraft:farmer", 2);
			Friendship.add(odo, me, 300);
			odo.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(3, 2, 2))));
			helper.assertTrue(PersonalRequestGameTests.resolve(helper, "cat_by_the_door", odo, me) == null, "asked with a cat by the bed");
			helper.succeed();
		});
	}

	// --- Off to school --------------------------------------------------------------------------------------------

	/** A parent of a child who hasn't been to school asks; the child schooled finishes it. A villager with no such child doesn't ask. */
	//$ gametest_ticks_batch AREA '80' '"requestSchool"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "requestSchool")
	public void offToSchoolWithASchooledChild(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 2);
			Villager finn = PersonalRequestGameTests.worker(helper, new BlockPos(12, 2, 4), "Finn", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			Friendship.add(finn, me, 300);
			Villager pip = HeartEventGameTests.villager(helper, new BlockPos(7, 2, 4), "Pip");
			pip.setAge(-24000);
			ModAttachments.PARENTS.set(pip, new Families.Parents(Component.literal("Dara"), Component.literal("Odo"), "", "", false));
			helper.assertTrue(PersonalRequestGameTests.resolve(helper, "off_to_school", finn, me) == null, "asked with no child");
			Quest quest = PersonalRequestGameTests.accepted(helper, "off_to_school", dara, me);
			helper.assertTrue(wants(helper, quest).equals("Dara wants their child Pip to go to school within 10 days"), wants(helper, quest));
			helper.assertTrue(quest.objectives.get(0).line().getString().equals("Pip goes to school (it needs a Teacher at a Teacher's Desk)"),
				quest.objectives.get(0).line().getString());
			Schools.teach(level, pip, Schools.LESSONS_NEEDED / 2);
			PersonalRequests.look(level, hall, hallEntity(helper), false);
			helper.assertTrue(PersonalRequests.of(dara) == quest, "done half schooled");
			Schools.teach(level, pip, Schools.LESSONS_NEEDED);
			PersonalRequests.look(level, hall, hallEntity(helper), false);
			helper.assertTrue(PersonalRequests.of(dara) == null && me.getInventory().countItem(Items.EMERALD) == 4, "the schooled child didn't count");
			helper.succeed();
		});
	}

	// --- A chapel wedding -----------------------------------------------------------------------------------------

	/**
	 * A courting couple with no Chapel: the wedding waits past its two days for the Chapel; finished, they marry there
	 * with a feast (everyone in the village gets a festival's mood). A villager not courting doesn't ask.
	 */
	//$ gametest_ticks_batch AREA '80' '"requestChapel"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "requestChapel")
	public void aChapelWeddingAndItsFeast(GameTestHelper helper) {
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
			Villager finn = PersonalRequestGameTests.worker(helper, new BlockPos(12, 2, 4), "Finn", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			Friendship.add(finn, me, 300);
			helper.assertTrue(PersonalRequestGameTests.resolve(helper, "chapel_wedding", finn, me) == null, "asked by someone not courting");
			ModAttachments.PARTNER.set(dara, new Couples.Partner(odo.getUUID(), odo.getDisplayName(), today - 3, false));
			ModAttachments.PARTNER.set(odo, new Couples.Partner(dara.getUUID(), dara.getDisplayName(), today - 3, false));
			ModAttachments.PARTNER.set(finn, new Couples.Partner(UUID.randomUUID(), Component.literal("Nobody"), today, true)); // (nobody for him to court in the round)
			Quest quest = PersonalRequestGameTests.accepted(helper, "chapel_wedding", dara, me);
			helper.assertTrue(wants(helper, quest).equals("Dara wants a Chapel built for their wedding within 5 days"), wants(helper, quest));
			// Three days courting, midday: without the request they'd marry; with it, the wedding waits for the Chapel.
			level.setDayTime(level.getDayTime() - level.getDayTime() % 24000 + 7000);
			Couples.round(level, hall);
			helper.assertTrue(!Couples.partner(dara).married(), "married before the Chapel");
			// The Chapel finished: the request is done, and they marry there with a feast.
			BuildSiteManager sites = BuildSiteManager.get(level);
			BlueprintData.Placement chapel = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 1, 2)), Rotation.NONE, Mirror.NONE);
			sites.recordFinished(StarterBlueprints.CHAPEL.id(), chapel, UUID.randomUUID());
			Leftovers.after(helper, () -> sites.forgetFinished(chapel));
			PersonalRequests.look(level, hall, hallEntity(helper), false);
			helper.assertTrue(PersonalRequests.of(dara) == null && me.getInventory().countItem(Items.EMERALD) == 6, "the Chapel didn't finish it: " + quest);
			helper.assertTrue(Couples.partner(dara).married() && Couples.partner(odo).married(), "no wedding at the Chapel");
			helper.assertTrue(chronicle(level, hall, Chronicle.Kind.WEDDING).contains("Dara and Odo were married in the new Chapel, and the whole village sat down to a feast"),
				"the chronicle: " + chronicle(level, hall, Chronicle.Kind.WEDDING));
			helper.assertTrue(!chronicle(level, hall, Chronicle.Kind.FESTIVAL).isEmpty() && Long.valueOf(today).equals(ModAttachments.FESTIVAL_DAY.get(finn)), "no feast for the village");
			helper.succeed();
		});
	}

	// --- The lost heirloom ----------------------------------------------------------------------------------------

	/** The request file as shipped, with its place a point in the test area (the GameTest world has no structures). */
	static QuestFiles.QuestFile heirloomAt(GameTestHelper helper, BlockPos rel, String heirloom) {
		BlockPos at = helper.absolutePos(rel);
		JsonObject json = JsonParser.parseString("{\"giver\": \"villager\", \"name\": {\"translate\": \"request.aliveworkplace.lost_heirloom.title\"},"
			+ " \"text\": \"request.aliveworkplace.lost_heirloom\", \"days\": 10, \"conditions\": [{\"type\": \"hearts_at_least\", \"hearts\": 3}],"
			+ " \"objectives\": [{\"type\": \"fetch\", \"heirloom\": \"" + heirloom + "\", \"place\": {\"point\": [" + at.getX() + ", " + at.getY() + ", " + at.getZ() + "]}}],"
			+ " \"rewards\": [{\"type\": \"money\", \"emeralds\": 8, \"rank_factor\": false}]}").getAsJsonObject();
		return QuestFiles.read(PersonalRequestGameTests.file("lost_heirloom"), json);
	}

	static Quest acceptedFile(GameTestHelper helper, QuestFiles.QuestFile file, Villager villager, ServerPlayer player) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Quest quest = PersonalRequests.resolve(level, hall, hallEntity(helper), VillageHalls.census(level, hall), file, villager, player, RandomSource.create(7));
		helper.assertTrue(quest != null, "no heirloom to ask for");
		PersonalRequests.propose(level, hall, villager, player, quest, Chronicle.day(level));
		PersonalRequestGameTests.command(player, "workplace quest accept " + quest.id);
		helper.assertTrue(PersonalRequests.of(villager) == quest, "not accepted");
		return quest;
	}

	static void moveTo(ServerPlayer player, BlockPos pos) {
		player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
	}

	/**
	 * The heirloom: the file asks for one of six from #aliveworkplace:heirloom_places within 1500 blocks; accepted, you
	 * get the map; it is put in the place's chest once you come near, and only once (a new barrel where there's no
	 * chest); handed back to the giver it's done, and its story is in the chronicle. A save and reload keeps the placed
	 * heirloom and every new objective.
	 */
	//$ gametest_ticks_batch AREA '80' '"requestHeirloom"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "requestHeirloom")
	public void theLostHeirloomPlacedOnceWithTheMap(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			QuestFiles.QuestFile shipped = QuestFiles.get(PersonalRequestGameTests.file("lost_heirloom")).orElseThrow();
			helper.assertTrue(shipped.objectives().get(0) instanceof RequestErrands.Fetch f && f.max() == 1500 && f.heirloom().isEmpty()
				&& f.place().options().get(0).id().equals("#aliveworkplace:heirloom_places"), "the shipped file: " + shipped.objectives());
			helper.assertTrue(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
				.getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.STRUCTURE, RequestErrands.HEIRLOOM_PLACES))
				.map(t -> t.size() >= 9).orElse(false), "the heirloom_places tag is missing or short");
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			BlockPos chestRel = new BlockPos(14, 2, 14);
			helper.setBlock(chestRel, Blocks.CHEST);
			Quest quest = acceptedFile(helper, heirloomAt(helper, chestRel, ""), dara, me);
			RequestErrands.Fetch fetch = (RequestErrands.Fetch) quest.objectives.get(0);
			helper.assertTrue(RequestErrands.HEIRLOOMS.containsKey(fetch.heirloom()) && fetch.place().located(), "the heirloom: " + fetch);
			String name = fetch.what().getString();
			helper.assertTrue(wants(helper, quest).equals("Dara wants their family's heirloom back: " + name + " within 10 days"), wants(helper, quest));
			ItemStack map = me.getInventory().items.stream().filter(s -> s.is(Items.FILLED_MAP)).findFirst().orElse(ItemStack.EMPTY);
			helper.assertTrue(map.getHoverName().getString().equals("Map to where " + name + " was lost"), "the map: " + map.getHoverName().getString());
			// Far away: nothing is put there.
			moveTo(me, helper.absolutePos(chestRel).offset(200, 0, 0));
			helper.assertTrue(RequestErrands.placeHeirlooms(level) == 0 && quest.placed == null, "put there with nobody near");
			// Near: in the chest, once.
			moveTo(me, helper.absolutePos(chestRel).offset(30, 0, 0));
			helper.assertTrue(RequestErrands.placeHeirlooms(level) == 1 && helper.absolutePos(chestRel).equals(quest.placed), "not put in the chest: " + quest.placed);
			helper.assertTrue(RequestErrands.placeHeirlooms(level) == 0, "put there twice");
			ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(chestRel));
			ItemStack found = ItemStack.EMPTY;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (!chest.getItem(i).isEmpty()) {
					found = chest.getItem(i);
					chest.setItem(i, ItemStack.EMPTY);
				}
			}
			helper.assertTrue(found.is(RequestErrands.HEIRLOOMS.get(fetch.heirloom())) && found.getHoverName().getString().equals(name) && found.hasFoil()
				&& PersonalRequestGameTests.lore(found).equals(List.of("The heirloom of Dara's family", "Right-click Dara with it")),
				"in the chest: " + found + " " + PersonalRequestGameTests.lore(found));

			// A save and reload keeps where it was put, and every new objective reads back as it was.
			CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
			CompoundTag again = Stories.Data.load(saved, level.registryAccess()).save(new CompoundTag(), level.registryAccess());
			helper.assertTrue(saved.equals(again) && saved.toString().contains("placed"), "the stories changed on reload:\n" + saved + "\n" + again);
			List<Objectives.Objective> all = List.of(fetch,
				new RequestErrands.Deliver(Optional.of(hall), Optional.of(UUID.randomUUID()), "Mira", "Elmford", "second_cousin"),
				new RequestErrands.Revive(Optional.of(UUID.randomUUID()), "Odo"), new RequestErrands.PetNearHome(10),
				new RequestErrands.Schooled(Optional.of(UUID.randomUUID()), "Pip"), new RequestErrands.Build(StarterBlueprints.CHAPEL.id(), true));
			for (Objectives.Objective o : all) {
				helper.assertTrue(Objectives.parse(o.json()).equals(o), "read back differently: " + o + " -> " + Objectives.parse(o.json()));
			}

			// Back to Dara: done, paid, and its story in the chronicle.
			me.setItemInHand(InteractionHand.MAIN_HAND, found);
			Villager odo = PersonalRequestGameTests.worker(helper, new BlockPos(9, 2, 4), "Odo", "minecraft:farmer", 1);
			helper.assertTrue(!RequestErrands.use(me, level, InteractionHand.MAIN_HAND, odo), "someone else took her heirloom");
			helper.assertTrue(RequestErrands.use(me, level, InteractionHand.MAIN_HAND, dara), "Dara didn't take it back");
			helper.assertTrue(PersonalRequests.of(dara) == null && me.getInventory().countItem(Items.EMERALD) == 8, "the heirloom home: " + quest);
			helper.assertTrue(chronicle(level, hall, Chronicle.Kind.STORY).stream().anyMatch(l -> l.startsWith(name + " ") && l.contains("Dara") && l.contains(ME)),
				"no story: " + chronicle(level, hall, Chronicle.Kind.STORY));

			// Where the chests are gone: a new barrel on the ground (looking only in its own chunk, so no other test's chest is near).
			helper.setBlock(chestRel, Blocks.AIR);
			int chunks = RequestErrands.PLACE_CHUNKS;
			RequestErrands.PLACE_CHUNKS = 0;
			Leftovers.after(helper, () -> RequestErrands.PLACE_CHUNKS = chunks);
			Villager finn = PersonalRequestGameTests.worker(helper, new BlockPos(12, 2, 4), "Finn", "minecraft:farmer", 2);
			Friendship.add(finn, me, 300);
			BlockPos emptyRel = new BlockPos(3, 2, 14);
			// An earlier batch's chest or barrel left in this chunk (outside its own area, so never cleared) would take the
			// heirloom instead: which test stands next door changes whenever tests are added.
			var chunk = level.getChunkAt(helper.absolutePos(emptyRel));
			for (BlockPos leftover : List.copyOf(chunk.getBlockEntities().keySet())) {
				if (level.getBlockEntity(leftover) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity box
					&& (box instanceof net.minecraft.world.level.block.entity.ChestBlockEntity || box instanceof BarrelBlockEntity)) {
					box.clearContent();
					level.removeBlock(leftover, false);
				}
			}
			Quest other = acceptedFile(helper, heirloomAt(helper, emptyRel, "diary"), finn, me);
			helper.assertTrue(RequestErrands.placeHeirlooms(level) == 1 && other.placed != null
				&& level.getBlockEntity(other.placed) instanceof BarrelBlockEntity barrel && !barrel.isEmpty(), "no barrel: " + other.placed);
			ItemStack diary = ((BarrelBlockEntity) level.getBlockEntity(other.placed)).getItem(13);
			helper.assertTrue(diary.is(Items.WRITTEN_BOOK) && diary.getHoverName().getString().equals("Great-aunt's Diary")
				&& diary.get(DataComponents.WRITTEN_BOOK_CONTENT).pages().size() == 3, "the diary: " + diary);
			helper.succeed();
		});
	}
}
