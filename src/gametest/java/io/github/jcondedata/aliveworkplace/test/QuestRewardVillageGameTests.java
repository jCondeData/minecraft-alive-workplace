package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.HomeVillages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageMoments;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * B1318: a questbook reward that unlocks a build ({@code /workplace unlock {p} <id>}) reaches the claiming player's own
 * village wherever they stand ({@link HomeVillages}), never the server-wide set while they have one. The owner away is
 * {@link QaQuestbookClaims1116eBugTests#qaARewardClaimedAwayFromTheVillageStillReachesItsTable}.
 */
public class QuestRewardVillageGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation COTTAGE_2 = StarterBlueprints.STARTER_COTTAGE_2.id();

	/** A spot at least 200 blocks east of {@code hall} that no village reaches. */
	private static BlockPos away(GameTestHelper helper, BlockPos hall) {
		for (int blocks : new int[] {200, 300, 500, 800, 1200}) {
			BlockPos spot = hall.offset(blocks, 0, 0);
			if (VillageHalls.nearest(helper.getLevel(), spot).isEmpty()) {
				return spot;
			}
		}
		throw new AssertionError("setup: no spot away from every village");
	}

	/** A second Village Hall at {@code at} (absolute), taken away after the test. */
	private static VillageHallBlockEntity farHall(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		level.setBlockAndUpdate(at, ModBlocks.VILLAGE_HALL.defaultBlockState());
		VillageHalls.forgetNearest();
		Leftovers.after(helper, () -> {
			level.removeBlock(at, false);
			if (level.getPoiManager().getType(at).filter(h -> h.is(ModVillagers.VILLAGE_HALL_POI)).isPresent()) {
				level.getPoiManager().remove(at);
			}
			VillageHalls.forgetNearest();
		});
		return (VillageHallBlockEntity) level.getBlockEntity(at);
	}

	private static void unlocks(GameTestHelper helper) {
		boolean was = BlueprintUnlocks.ENABLED;
		BlueprintUnlocks.ENABLED = true;
		Leftovers.after(helper, () -> BlueprintUnlocks.ENABLED = was);
	}

	/**
	 * The player owns a village far away and claims the reward standing in another player's village: Starter Cottage II
	 * goes on their own village's Table, not the one they stand in, and they're told once.
	 */
	//$ gametest_ticks_batch AREA '40' '"questRewardOtherVillage"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "questRewardOtherVillage")
	public void aRewardClaimedInAnotherPlayersVillageGoesToYourOwn(GameTestHelper helper) {
		unlocks(helper);
		VillageHallBlockEntity theirs = QaQuestbookClaims1116eGameTests.hall(helper);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = theirs.getBlockPos();
			theirs.setOwner(UUID.randomUUID(), "Someone");
			VillageHallBlockEntity mine = farHall(helper, away(helper, hall));
			ServerPlayer player = QaQuestbookClaims1116eGameTests.playerAt(helper, hall.offset(2, 1, 0).getCenter());
			mine.setOwner(player.getUUID(), player.getGameProfile().getName());
			helper.assertTrue(BlueprintUnlocks.state(level, mine.getBlockPos(), COTTAGE_2) == BlueprintUnlocks.State.LOCKED, "setup: Starter Cottage II isn't locked");
			QaQuestbookClaims1116eGameTests.reward(level, player, COTTAGE_2);
			helper.assertTrue(BlueprintUnlocks.state(level, mine.getBlockPos(), COTTAGE_2) == BlueprintUnlocks.State.UNLOCKED,
				"the reward didn't reach the player's own village: " + BlueprintUnlocks.state(level, mine.getBlockPos(), COTTAGE_2));
			helper.assertTrue(BlueprintUnlocks.state(level, hall, COTTAGE_2) == BlueprintUnlocks.State.LOCKED,
				"the reward unlocked the village the player stood in, someone else's");
			helper.succeed();
		});
	}

	/**
	 * A player who owns no hall but was in a village as it reached a rank: a reward claimed away from every village goes
	 * to that village's Table, not the server-wide set (which every later village takes in).
	 */
	//$ gametest_ticks_batch AREA '40' '"questRewardRanked"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "questRewardRanked")
	public void aRewardGoesToTheVillageYouLastRankedWith(GameTestHelper helper) {
		unlocks(helper);
		VillageHallBlockEntity village = QaQuestbookClaims1116eGameTests.hall(helper);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = village.getBlockPos();
			ServerPlayer player = QaQuestbookClaims1116eGameTests.playerAt(helper, hall.offset(2, 1, 0).getCenter());
			VillageMoments.award(level, hall, VillageMoments.rank(VillageRanks.Rank.VILLAGE), null);
			helper.assertTrue(HomeVillages.get(level.getServer()).rankedWith(player.getUUID()).map(GlobalPos::pos).filter(hall::equals).isPresent(),
				"the rank's moment didn't remember the player's village");
			player.moveTo(away(helper, hall).getCenter());
			helper.assertTrue(BlueprintUnlocks.state(level, null, COTTAGE_2) == BlueprintUnlocks.State.LOCKED, "setup: the server-wide set has Starter Cottage II");
			QaQuestbookClaims1116eGameTests.reward(level, player, COTTAGE_2);
			helper.assertTrue(BlueprintUnlocks.state(level, hall, COTTAGE_2) == BlueprintUnlocks.State.UNLOCKED,
				"the reward didn't reach the village the player ranked with: " + BlueprintUnlocks.state(level, hall, COTTAGE_2));
			helper.assertTrue(BlueprintUnlocks.state(level, null, COTTAGE_2) == BlueprintUnlocks.State.LOCKED,
				"the reward went into the server-wide set too");
			helper.succeed();
		});
	}

	/** The record survives a save and reload, and an older save (no record) starts with none. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void homeVillagesSurviveASaveAndAnOldSaveHasNone(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		GlobalPos owned = GlobalPos.of(helper.getLevel().dimension(), new BlockPos(10, 64, -20));
		GlobalPos ranked = GlobalPos.of(net.minecraft.world.level.Level.NETHER, new BlockPos(-3, 70, 8));
		HomeVillages old = HomeVillages.load(new CompoundTag(), registries);
		helper.assertTrue(old.ownedBy(a).isEmpty() && old.rankedWith(b).isEmpty(), "an old save has a home village");
		HomeVillages filled = withEntries(registries, a, owned, b, ranked);
		HomeVillages back = HomeVillages.load(filled.save(new CompoundTag(), registries), registries);
		helper.assertTrue(back.ownedBy(a).equals(java.util.Optional.of(owned)), "the owned hall after a reload: " + back.ownedBy(a));
		helper.assertTrue(back.rankedWith(b).equals(java.util.Optional.of(ranked)), "the ranked village after a reload: " + back.rankedWith(b));
		helper.assertTrue(back.ownedBy(b).isEmpty() && back.rankedWith(a).isEmpty(), "a record crossed over");
		helper.succeed();
	}

	/** A record with {@code a} owning {@code owned} and {@code b} ranked with {@code ranked}, through the saved form. */
	private static HomeVillages withEntries(net.minecraft.core.HolderLookup.Provider registries, UUID a, GlobalPos owned, UUID b, GlobalPos ranked) {
		var ops = registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
		var map = com.mojang.serialization.Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, GlobalPos.CODEC);
		CompoundTag tag = new CompoundTag();
		tag.put("owned", map.encodeStart(ops, java.util.Map.of(a, owned)).getOrThrow());
		tag.put("ranked", map.encodeStart(ops, java.util.Map.of(b, ranked)).getOrThrow());
		return HomeVillages.load(tag, registries);
	}
}
