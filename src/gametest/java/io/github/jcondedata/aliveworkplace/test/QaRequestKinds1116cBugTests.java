package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.RequestErrands;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** QA (lane-c-1116-0352): the personal-request failures found by QaRequestKinds1116cGameTests' run, kept off main until fixed. */
public class QaRequestKinds1116cBugTests implements FabricGameTest {
	static final String AREA = PersonalRequestGameTests.AREA;
	static final BlockPos HALL = PersonalRequestGameTests.HALL;
	static final String ME = PersonalRequestGameTests.ME;

	static ItemStack fetchFromChest(GameTestHelper helper, BlockPos chestRel) {
		ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(chestRel));
		ItemStack found = ItemStack.EMPTY;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (!chest.getItem(i).isEmpty()) {
				found = chest.removeItemNoUpdate(i);
			}
		}
		return found;
	}

	/** The Lucky Crystal home: Dara's thanks and the chronicle's story are sentences, so they start with a capital ("The Lucky Crystal! ..."). */
	//$ gametest_ticks_batch AREA '80' '"qaKindsBugCrystal"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaKindsBugCrystal")
	public void theLuckyCrystalsThanksAndStoryStartWithACapital(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			BlockPos chestRel = new BlockPos(14, 2, 14);
			helper.setBlock(chestRel, Blocks.CHEST);
			PersonalErrandGameTests.acceptedFile(helper, PersonalErrandGameTests.heirloomAt(helper, chestRel, "crystal"), dara, me);
			PersonalErrandGameTests.moveTo(me, helper.absolutePos(chestRel).offset(30, 0, 0));
			helper.assertTrue(RequestErrands.placeHeirlooms(level) == 1, "not put in the chest");
			ItemStack found = fetchFromChest(helper, chestRel);
			PersonalErrandGameTests.moveTo(me, helper.absolutePos(new BlockPos(5, 2, 6)));
			me.setItemInHand(InteractionHand.MAIN_HAND, found);
			QaRequestKinds1116cGameTests.click(me, dara);
			helper.assertTrue(PersonalRequests.of(dara) == null, "not handed back");
			String thanks = PersonalRequestGameTests.said(dara);
			List<String> stories = PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.STORY);
			helper.assertTrue(!thanks.isEmpty() && Character.isUpperCase(thanks.charAt(0)), "Dara's thanks start in lower case: \"" + thanks + "\"");
			helper.assertTrue(!stories.isEmpty() && stories.stream().allMatch(l -> Character.isUpperCase(l.charAt(0))), "the chronicle starts in lower case: " + stories);
			helper.succeed();
		});
	}

	/**
	 * Wiki: a player joins "by moving it on (handing in, winning the battle). A player with fewer hearts can't." A player
	 * with no hearts with Dara who picks up her heirloom doesn't finish her request and isn't paid (as a bring hand-in is refused).
	 */
	//$ gametest_ticks_batch AREA '80' '"qaKindsBugStranger"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaKindsBugStranger")
	public void aStrangerWithNoHeartsCantHandInTheHeirloom(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			ServerPlayer stranger = HeartEventGameTests.player(helper, new BlockPos(6, 2, 6));
			BlockPos chestRel = new BlockPos(14, 2, 14);
			helper.setBlock(chestRel, Blocks.CHEST);
			Quest quest = PersonalErrandGameTests.acceptedFile(helper, PersonalErrandGameTests.heirloomAt(helper, chestRel, "horn"), dara, me);
			PersonalErrandGameTests.moveTo(me, helper.absolutePos(chestRel).offset(30, 0, 0));
			helper.assertTrue(RequestErrands.placeHeirlooms(level) == 1, "not put in the chest");
			ItemStack found = fetchFromChest(helper, chestRel);
			PersonalErrandGameTests.moveTo(me, helper.absolutePos(new BlockPos(5, 2, 6)));
			stranger.setItemInHand(InteractionHand.MAIN_HAND, found);
			QaRequestKinds1116cGameTests.click(stranger, dara);
			helper.assertTrue(PersonalRequests.of(dara) == quest && stranger.getInventory().countItem(Items.EMERALD) == 0 && !quest.helpers.containsKey(stranger.getUUID()),
				"a player with no hearts with Dara finished her request: open " + (PersonalRequests.of(dara) != null) + ", stranger paid "
					+ stranger.getInventory().countItem(Items.EMERALD) + ", me " + me.getInventory().countItem(Items.EMERALD));
			helper.succeed();
		});
	}

	/**
	 * Wiki: "With no Chapel by then they marry at the bell as before, and the request is missed." Married at the bell at 5
	 * days, a Chapel finished afterwards doesn't pay the request or thank anyone for a Chapel wedding that never was.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaKindsBugBellThenChapel"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaKindsBugBellThenChapel")
	public void marriedAtTheBellAChapelLaterDoesntPayTheChapelWedding(GameTestHelper helper) {
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
			ModAttachments.PARTNER.set(dara, new Couples.Partner(odo.getUUID(), odo.getDisplayName(), today - 5, false));
			ModAttachments.PARTNER.set(odo, new Couples.Partner(dara.getUUID(), dara.getDisplayName(), today - 5, false));
			level.setDayTime(level.getDayTime() - level.getDayTime() % 24000 + 7000);
			Couples.round(level, hall);
			helper.assertTrue(Couples.partner(dara).married(), "not married at the bell at 5 days");
			BuildSiteManager sites = BuildSiteManager.get(level);
			BlueprintData.Placement chapel = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 1, 2)), Rotation.NONE, Mirror.NONE);
			sites.recordFinished(StarterBlueprints.CHAPEL.id(), chapel, UUID.randomUUID());
			Leftovers.after(helper, () -> sites.forgetFinished(chapel));
			PersonalRequests.look(level, hall, PersonalRequestGameTests.hallEntity(helper), false);
			helper.assertTrue(me.getInventory().countItem(Items.EMERALD) == 0,
				"paid " + me.getInventory().countItem(Items.EMERALD) + " emeralds for a Chapel wedding after they married at the bell; Dara says \""
					+ PersonalRequestGameTests.said(dara) + "\"; open " + (PersonalRequests.of(dara) == quest));
			helper.succeed();
		});
	}
}
