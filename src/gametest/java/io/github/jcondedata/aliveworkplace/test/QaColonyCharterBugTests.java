package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.colony.ColonyCharterItem;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/**
 * QA (lane-c-1025-0653): bugs found in the Colony Charter (ROADMAP 33.8), each failing until it is fixed. The builder
 * commits each with its fix.
 */
public class QaColonyCharterBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, ServerPlayer player, String name) {
	}

	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		boolean enabled = Colonies.ENABLED;
		VillageRanks.Rank needed = Colonies.RANK;
		int cooldown = Colonies.COOLDOWN_DAYS;
		int cap = Colonies.PER_VILLAGE;
		int every = VillageNeeds.CHECK_EVERY;
		Colonies.ENABLED = true;
		Colonies.RANK = VillageRanks.Rank.CITY;
		Colonies.COOLDOWN_DAYS = 7;
		Colonies.PER_VILLAGE = 3;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		RealmData.get(level.getServer()).clearColonies();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Caravans.Data data = Caravans.Data.get(level);
		for (Caravans.Village left : data.villages()) {
			if (Math.abs(left.hall().getX() - hall.getX()) <= 4096 && Math.abs(left.hall().getZ() - hall.getZ()) <= 4096) {
				data.remove(left.hall());
			}
		}
		Leftovers.after(helper, () -> {
			Colonies.ENABLED = enabled;
			Colonies.RANK = needed;
			Colonies.COOLDOWN_DAYS = cooldown;
			Colonies.PER_VILLAGE = cap;
			VillageNeeds.CHECK_EVERY = every;
			RealmData.get(level.getServer()).clearColonies();
		});
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setOwner(player.getUUID(), "Owner");
		entity.setRank(VillageRanks.Rank.CITY);
		entity.setTreasury(6400);
		return new Village(level, hall, entity, player, VillageHalls.name(level, hall).getString());
	}

	/** The number in "(this is N)". */
	private static int said(String message) {
		Matcher m = Pattern.compile("\\(this is (\\d+)\\)").matcher(message);
		return m.find() ? Integer.parseInt(m.group(1)) : Integer.MIN_VALUE;
	}

	/**
	 * A spot just past the ring is refused, but the reason rounds its distance onto the ring's edge and contradicts
	 * itself: 1,024.0005 blocks out reads "a colony goes at most 1024 blocks from its hall (this is 1024)", and 255.97
	 * blocks out "at least 256 blocks ... (this is 256)". Expected: a number on the refused side (1025, 255), so the
	 * player can tell which way to move the spot.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaColChBugRound"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaColChBugRound")
	public void aSpotJustOutsideTheRingIsNotCalledOnItsEdge(GameTestHelper helper) {
		Village v = village(helper);
		v.player().setItemInHand(InteractionHand.MAIN_HAND, ColonyCharterItem.of(v.level(), v.hall()));
		List<String> wrong = new ArrayList<>();
		Colonies.Chosen far = Colonies.clicked(new Colonies.Choose(true, v.hall().getX() + 1024, v.hall().getZ() + 1), v.player());
		helper.assertTrue(!far.ok(), "1,024.0005 blocks out was taken");
		if (said(far.message().getString()) <= 1024) {
			wrong.add(far.message().getString());
		}
		Colonies.Chosen near = Colonies.clicked(new Colonies.Choose(true, v.hall().getX() + 181, v.hall().getZ() + 181), v.player());
		helper.assertTrue(!near.ok(), "255.97 blocks out was taken");
		if (said(near.message().getString()) >= 256) {
			wrong.add(near.message().getString());
		}
		helper.assertTrue(wrong.isEmpty(), "refusals that put the spot on the ring's edge: " + wrong);
		helper.succeed();
	}

	/**
	 * coloniesPerVillage at 1 or 0 ("0: none"): the cap's sentence reads "has founded all 1 colonies a village may" and
	 * "has founded all 0 colonies a village may". Expected: "the 1 colony" (singular), and for 0 a sentence that says
	 * this server allows no colonies, not that the village founded them all.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaColChBugCap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaColChBugCap")
	public void theCapSentenceReadsRightForOneAndNone(GameTestHelper helper) {
		Village v = village(helper);
		RealmData data = RealmData.get(v.level().getServer());
		GlobalPos mother = GlobalPos.of(v.level().dimension(), v.hall());
		List<String> wrong = new ArrayList<>();
		Colonies.PER_VILLAGE = 0;
		Colonies.Bought none = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(none.refusal() == Colonies.Refusal.CAP, "coloniesPerVillage 0 sold a charter: " + none);
		if (none.message().getString().contains("founded all 0")) {
			wrong.add(none.message().getString());
		}
		Colonies.PER_VILLAGE = 1;
		data.recordFounded(mother, GlobalPos.of(v.level().dimension(), v.hall().offset(500, 0, 0)), Chronicle.day(v.level()) - 30);
		Colonies.Bought one = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(one.refusal() == Colonies.Refusal.CAP, "coloniesPerVillage 1 sold a second charter: " + one);
		if (one.message().getString().contains("1 colonies")) {
			wrong.add(one.message().getString());
		}
		helper.assertTrue(wrong.isEmpty(), "the cap's sentences: " + wrong);
		helper.succeed();
	}
}
