package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.guard.DenWardens;
import io.github.jcondedata.aliveworkplace.guard.RaidDens;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 38.3, Den Wardens: a guard posted to a raid den from the Defense page walks there by day and home at dusk; a
 * den that glows again is written in the chronicle once; a player who joins a raid by a watched den gets one cheer a
 * raid; a recalled warden goes back to normal duty. Each test runs alone (its own batch) and puts the time back.
 */
public class DenWardenCompatTests implements FabricGameTest {
	static final BlockPos HALL = new BlockPos(2, 2, 2);
	static final BlockPos POST = new BlockPos(3, 2, 5);
	static final BlockPos DEN = new BlockPos(14, 2, 14);

	/**
	 * What earlier batches left round this spot, taken away: villagers that wandered out of their area (a Master or a
	 * guard there would be sent instead of this test's), mock players, Village Halls, and raid dens (a den a failed test
	 * left glowing would be challenged instead of this one). Only for tests alone in their batch.
	 */
	static void clearAround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		net.minecraft.world.phys.AABB around = helper.getBounds().inflate(48);
		for (net.minecraft.world.entity.Entity e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, around,
				e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
			e.discard();
		}
		var list = level.getServer().getPlayerList();
		for (ServerPlayer p : java.util.List.copyOf(level.players())) {
			if (around.contains(p.position())) {
				list.remove(p);
			}
		}
		BlockPos center = BlockPos.containing(helper.getBounds().getCenter());
		for (RaidDens.Den den : RaidDens.near(level, center)) {
			level.setBlockAndUpdate(den.pos(), Blocks.AIR.defaultBlockState());
		}
		RaidDens.near(level, center);
		int reach = io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS + 32;
		for (BlockPos hall : level.getPoiManager().findAll(h -> h.is(ModVillagers.VILLAGE_HALL_POI), p -> true, center, reach,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
			level.removeBlock(hall, false);
		}
		// The village store is the chests by the kitchens (smokers, Kitchen Stoves) and Storehouses within a hall's reach: an
		// earlier batch's Camp Kitchen II, built outside this smaller area, made its chest and barrels this village's store,
		// and the den's loot and the Miner's Key Stones went there (after the 37.5/37.6 merge reordered the batches).
		net.minecraft.world.phys.AABB own = helper.getBounds();
		for (BlockPos store : level.getPoiManager().findAll(h -> h.is(ModVillagers.KITCHEN_STOVE_POI) || h.is(ModVillagers.STOREHOUSE_POI)
				|| h.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.BUTCHER), p -> !own.contains(net.minecraft.world.phys.Vec3.atCenterOf(p)), center, reach,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
			level.removeBlock(store, false);
		}
	}

	/** Sets the time of day and the sky's darkness with it (isNight reads the darkness, updated once a tick). */
	static void time(ServerLevel level, long time) {
		level.setDayTime(time);
		level.updateSkyBrightness();
	}

	/** A hall, a guard post with its guard, and a known den; returns {hall, den, guard's place}. */
	static BlockPos[] village(GameTestHelper helper, boolean active) {
		clearAround(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		helper.setBlock(DEN, RaidDenCompatTests.den("tier_three", "fire", active));
		BlockPos den = helper.absolutePos(DEN);
		RaidDens.chunkLoaded(level, level.getChunkAt(den));
		RaidDens.tick(level);
		helper.assertTrue(RaidDenCompatTests.listed(helper, helper.absolutePos(HALL), den).isPresent(), "the den isn't known");
		return new BlockPos[] {helper.absolutePos(HALL), den};
	}

	static Villager guard(GameTestHelper helper) {
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		return villager;
	}

	static void cleanUp(GameTestHelper helper, BlockPos den, long time) {
		ServerLevel level = helper.getLevel();
		DenWardens.wardenOf(level, den).ifPresent(w -> DenWardens.recall(level, den));
		level.setBlockAndUpdate(den, Blocks.AIR.defaultBlockState());
		RaidDens.near(level, helper.absolutePos(HALL));
		time(level, time);
	}

	static double flat(Villager v, BlockPos p) {
		double dx = v.getX() - (p.getX() + 0.5);
		double dz = v.getZ() - (p.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Posted by day, the guard walks to the den and stands watch; at dusk they walk back to their post. */
	//$ gametest_ticks_batch 'CompatGameTests.AREA' '1600' '"denWardenWalk"'
	@GameTest(template = CompatGameTests.AREA, timeoutTicks = 1600, batch = "denWardenWalk")
	public void aWardenWalksToTheDenAndBackAtDusk(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		time(level, 1000);
		BlockPos[] at = village(helper, true);
		Villager guard = guard(helper);
		String said = DenWardens.post(level, at[0], at[1]).getString();
		helper.assertTrue(said.contains("will stand watch") && DenWardens.wardenOf(level, at[1]).equals(java.util.Optional.of(guard.getUUID())), "posted: " + said);
		helper.assertTrue(DenWardens.busy(guard), "a posted guard should be the den's by day");
		BlockPos post = helper.absolutePos(POST);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(flat(guard, at[1]) <= 3.5, "the warden is " + Math.round(flat(guard, at[1])) + " blocks from the den"))
			.thenExecute(() -> time(level, 13000))
			.thenWaitUntil(() -> helper.assertTrue(flat(guard, post) <= 4 && !DenWardens.busy(guard),
				"at dusk the warden is " + Math.round(flat(guard, post)) + " blocks from the post, still the den's: " + DenWardens.busy(guard)))
			.thenExecute(() -> {
				helper.assertTrue(DenWardens.wardenOf(level, at[1]).isPresent(), "still the den's warden for tomorrow");
				cleanUp(helper, at[1], time);
			})
			.thenSucceed();
	}

	/** A watched den that glows again is written in the chronicle, once. */
	//$ gametest_batch 'CompatGameTests.AREA' '"denWardenGlow"'
	@GameTest(template = CompatGameTests.AREA, batch = "denWardenGlow")
	public void aDenGlowingAgainIsWrittenOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		BlockPos[] at = village(helper, false);
		guard(helper);
		DenWardens.post(level, at[0], at[1]);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(at[0]);
		int before = glows(hall);
		level.setBlockAndUpdate(at[1], RaidDenCompatTests.den("tier_three", "fire", true));
		RaidDens.near(level, at[0]);
		RaidDens.near(level, at[0]);
		helper.assertTrue(glows(hall) == before + 1, "glow lines: " + (glows(hall) - before) + "; last: " + last(hall));
		helper.assertTrue(last(hall).matches("The Fire raid den \\(\\d+ blocks (north|south|east|west|north-east|north-west|south-east|south-west)\\) glows again"),
			"the line: " + last(hall));
		cleanUp(helper, at[1], time);
		helper.succeed();
	}

	static int glows(VillageHallBlockEntity hall) {
		return (int) hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.SIGHTING && e.text().getString().contains("glows again")).count();
	}

	static String last(VillageHallBlockEntity hall) {
		var chronicle = hall.chronicle();
		return chronicle.isEmpty() ? "" : chronicle.get(chronicle.size() - 1).text().getString();
	}

	/** A player who joins a raid by a watched den gets one cheer, once a raid; the next raid (the den glowing again) another. */
	//$ gametest_batch 'CompatGameTests.AREA' '"denWardenCheer"'
	@GameTest(template = CompatGameTests.AREA, batch = "denWardenCheer")
	public void aRaiderByAWatchedDenGetsOneCheer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		BlockPos[] at = village(helper, true);
		guard(helper);
		DenWardens.post(level, at[0], at[1]);
		ServerPlayer near = helper.makeMockServerPlayerInLevel();
		near.teleportTo(at[1].getX() + 3.5, at[1].getY(), at[1].getZ() + 0.5);
		ServerPlayer far = helper.makeMockServerPlayerInLevel();
		far.teleportTo(at[1].getX() + 40.5, at[1].getY(), at[1].getZ() + 0.5);
		try {
			grant(helper, near, "join_raid");
			grant(helper, far, "join_raid");
			helper.assertTrue(cheers(near) == 1, "cheers for the raider by the den: " + cheers(near));
			helper.assertTrue(cheers(far) == 0, "cheers for a player 40 blocks off: " + cheers(far));
			// The same raid: a tier advancement too, and join_raid again, give nothing more.
			grant(helper, near, "tier_three");
			revoke(helper, near, "join_raid");
			grant(helper, near, "join_raid");
			helper.assertTrue(cheers(near) == 1, "more than one cheer a raid: " + cheers(near));
			// The den is beaten and glows again: the next raid earns another.
			level.setBlockAndUpdate(at[1], RaidDenCompatTests.den("tier_three", "fire", false));
			RaidDens.near(level, at[0]);
			level.setBlockAndUpdate(at[1], RaidDenCompatTests.den("tier_three", "fire", true));
			RaidDens.near(level, at[0]);
			revoke(helper, near, "join_raid");
			grant(helper, near, "join_raid");
			helper.assertTrue(cheers(near) == 2, "the next raid's cheer: " + cheers(near));
		} finally {
			level.getServer().getPlayerList().remove(near);
			level.getServer().getPlayerList().remove(far);
			cleanUp(helper, at[1], time);
		}
		helper.succeed();
	}

	static void grant(GameTestHelper helper, ServerPlayer player, String path) {
		AdvancementHolder holder = advancement(helper, path);
		for (String criterion : holder.value().criteria().keySet()) {
			player.getAdvancements().award(holder, criterion);
		}
	}

	static void revoke(GameTestHelper helper, ServerPlayer player, String path) {
		AdvancementHolder holder = advancement(helper, path);
		for (String criterion : holder.value().criteria().keySet()) {
			player.getAdvancements().revoke(holder, criterion);
		}
	}

	static AdvancementHolder advancement(GameTestHelper helper, String path) {
		AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath("cobblemonraiddens", path));
		helper.assertTrue(holder != null, "no advancement cobblemonraiddens:" + path);
		return holder;
	}

	static int cheers(ServerPlayer player) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			if (id.startsWith("cobblemonraiddens:cheer_")) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** Recalled, the warden is nobody's but the village's again: no den, back to the guards' own day. */
	//$ gametest_batch 'CompatGameTests.AREA' '"denWardenRecall"'
	@GameTest(template = CompatGameTests.AREA, batch = "denWardenRecall")
	public void aRecalledWardenGoesBackToNormalDuty(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		time(level, 1000);
		BlockPos[] at = village(helper, true);
		Villager guard = guard(helper);
		DenWardens.post(level, at[0], at[1]);
		helper.assertTrue(DenWardens.busy(guard), "posted");
		String said = DenWardens.recall(level, at[1]).getString();
		helper.assertTrue(said.contains("back on normal duty"), "recalled: " + said);
		helper.assertTrue(DenWardens.wardenOf(level, at[1]).isEmpty() && DenWardens.postOf(level, guard.getUUID()).isEmpty() && !DenWardens.busy(guard),
			"the guard is still the den's");
		helper.assertTrue(DenWardens.recall(level, at[1]).getString().contains("no warden"), "a second recall");
		cleanUp(helper, at[1], time);
		helper.succeed();
	}

	/** The den broken: its warden goes back to normal duty at their next look; with the switch off, a posted guard isn't the den's. */
	//$ gametest_batch 'CompatGameTests.AREA' '"denWardenGone"'
	@GameTest(template = CompatGameTests.AREA, batch = "denWardenGone")
	public void aWardenOfAGoneDenOrSwitchedOffIsFree(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		time(level, 1000);
		BlockPos[] at = village(helper, true);
		Villager guard = guard(helper);
		DenWardens.post(level, at[0], at[1]);
		boolean was = DenWardens.ENABLED;
		try {
			DenWardens.ENABLED = false;
			helper.assertFalse(DenWardens.busy(guard), "with denWardens off, the guard's own day should take over");
		} finally {
			DenWardens.ENABLED = was;
		}
		helper.assertTrue(DenWardens.busy(guard), "switched back on, the warden is the den's again");
		level.setBlockAndUpdate(at[1], Blocks.AIR.defaultBlockState());
		DenWardens.tick(level, guard, at[1]);
		helper.assertTrue(DenWardens.postOf(level, guard.getUUID()).isEmpty() && !DenWardens.busy(guard), "the warden of a broken den is still posted");
		cleanUp(helper, at[1], time);
		helper.succeed();
	}

	/** The wardens and who had a den's cheer are saved and read back as they were. */
	//$ gametest_batch 'CompatGameTests.AREA' '"denWardenSave"'
	@GameTest(template = CompatGameTests.AREA, batch = "denWardenSave")
	public void wardensSurviveASave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		BlockPos[] at = village(helper, true);
		Villager guard = guard(helper);
		DenWardens.post(level, at[0], at[1]);
		ServerPlayer near = helper.makeMockServerPlayerInLevel();
		near.teleportTo(at[1].getX() + 3.5, at[1].getY(), at[1].getZ() + 0.5);
		try {
			grant(helper, near, "join_raid");
			net.minecraft.nbt.CompoundTag saved = DenWardens.Data.get(level).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
			String text = saved.toString();
			helper.assertTrue(text.contains(Long.toString(at[1].asLong())) && text.contains(near.getUUID().toString()), "the save: " + text);
			net.minecraft.nbt.CompoundTag again = DenWardens.Data.load(saved.copy(), level.registryAccess()).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
			helper.assertTrue(again.equals(saved), "read back: " + again + " saved: " + saved);
			helper.assertTrue(DenWardens.Data.load(new net.minecraft.nbt.CompoundTag(), level.registryAccess()).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess())
				.toString().equals("{wardens:[]}"), "an old save without wardens");
			helper.assertTrue(DenWardens.postOf(level, guard.getUUID()).equals(java.util.Optional.of(at[1])), "the guard's post");
		} finally {
			level.getServer().getPlayerList().remove(near);
			cleanUp(helper, at[1], time);
		}
		helper.succeed();
	}
}
