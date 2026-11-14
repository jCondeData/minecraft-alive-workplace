package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Escorts;
import io.github.jcondedata.aliveworkplace.guard.RallyBannerItem;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA (lane D, 2026-11-14), the Rally Banner from the player guide (guide/rally-banner.md: "they follow you and fight
 * whatever goes for you"; guide/guard.md: players, villagers, pets and Pokémon are safe from them), for what no test
 * checked: the village's iron golem and an armor stand the player strikes are left alone by a following guard (each
 * kills a mutant of {@code Escorts.fair} that drops its golem or armor-stand check), and a zombie that goes for the
 * player is what the guard goes for. Pets and villagers are QaGuardOrders1104dGameTests'; Pokémon the compat suite's.
 */
public class QaRallySafe1114dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** A guard at a Guard Post near the corner, with a sword, enlisted on a raised banner in {@code player}'s hand. */
	private static Villager follower(GameTestHelper helper, ServerPlayer player) {
		BlockPos post = new BlockPos(3, 2, 3);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager guard = helper.spawn(EntityType.VILLAGER, post.east());
		Jobs.employ(helper.getLevel(), guard, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		ItemStack banner = new ItemStack(ModItems.RALLY_BANNER);
		banner.set(ModComponents.RALLY, new RallyBannerItem.Rally(List.of(guard.getUUID()), true));
		player.getInventory().add(banner);
		Escorts.scan(helper.getLevel().getServer());
		return guard;
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos rel) {
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		BlockPos at = helper.absolutePos(rel);
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		player.setInvulnerable(true);
		return player;
	}

	private static String hurt(LivingEntity e) {
		return e.getType().getDescription().getString() + " (" + (e.isAlive() ? e.getHealth() + " of " + e.getMaxHealth() : "gone") + ")";
	}

	/**
	 * The player strikes the village's iron golem and an armor stand, by turns, for half a minute while a guard follows
	 * the raised banner: the guard is right there and hurts neither (and never picks either as the foe). Then the player
	 * strikes a pig, and the guard goes for that.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaRallySafe1114dGolem"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaRallySafe1114dGolem")
	public void aFollowingGuardLeavesAGolemAndAnArmorStandThePlayerStrikesAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		ServerPlayer player = player(helper, new BlockPos(14, 2, 14));
		Villager guard = follower(helper, player);
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(17, 2, 14));
		golem.setNoAi(true);
		golem.setPlayerCreated(true);
		ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(14, 2, 17));
		Pig[] pig = {null};
		helper.onEachTick(() -> {
			long t = helper.getTick();
			if (pig[0] == null) {
				if (t % 40 == 0) {
					player.setLastHurtMob(t % 80 == 0 ? golem : stand);
				}
				for (LivingEntity friend : List.of(golem, stand)) {
					if (!friend.isAlive() || friend.getHealth() < friend.getMaxHealth()) {
						helper.fail("the guard went for what the player struck: " + hurt(friend));
					}
					if (t > 5 && Escorts.isEscorting(guard) && Escorts.leaderFoe(player, guard) == friend) {
						helper.fail("the guard picked the " + friend.getType().getDescription().getString() + " as the player's foe");
					}
				}
				if (t >= 700) {
					helper.assertTrue(Escorts.isEscorting(guard) && guard.distanceTo(player) < 12, "setup: the guard isn't following the player, "
						+ guard.distanceTo(player) + " blocks off");
					pig[0] = helper.spawn(EntityType.PIG, new BlockPos(14, 2, 11));
					pig[0].setNoAi(true);
				}
			} else if (t % 20 == 0) {
				player.setLastHurtMob(pig[0]);
			}
		});
		helper.succeedWhen(() -> helper.assertTrue(pig[0] != null && !pig[0].isAlive(), "the guard didn't go for the pig the player struck"));
	}

	/**
	 * rally-banner.md: "your guards follow you anywhere", and "lower it, and they go home to their posts". The player
	 * stands 80 blocks from the guard's post with the banner raised: the guard catches up and stays with them through
	 * many rounds of working out who follows whom (never sent back to the post while still called). Then the banner is
	 * lowered: the guard, too far to walk it in good time, is back by the post at once. This is the only test with a
	 * guard more than 64 blocks from their post (Escorts.SEND_HOME); without it, sending home the guards still called
	 * instead of the ones let go went unnoticed.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaRallySafe1114dFar"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaRallySafe1114dFar")
	public void aGuardFollowsEightyBlocksFromTheirPostAndIsHomeOnceTheBannerIsLowered(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		BlockPos farRel = new BlockPos(83, 2, 3);
		BlockPos far = helper.absolutePos(farRel);
		net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(far);
		FarChunks.bringBack(level, chunk);
		ServerPlayer player = player(helper, new BlockPos(14, 2, 14));
		Villager guard = follower(helper, player);
		BlockPos post = helper.absolutePos(new BlockPos(3, 2, 3));
		int floor = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, far.getX(), far.getZ());
		player.teleportTo(far.getX() + 0.5, floor, far.getZ() + 0.5);
		long[] lowered = {-1};
		long[] withPlayer = {0};
		helper.onEachTick(() -> {
			long t = helper.getTick();
			if (lowered[0] < 0) {
				if (guard.distanceTo(player) < 8) {
					withPlayer[0]++;
				} else if (withPlayer[0] > 0 && !Escorts.isEscorting(guard)) {
					helper.fail("the guard stopped following the raised banner " + guard.distanceTo(player) + " blocks from the player");
				} else if (withPlayer[0] > 0) {
					helper.fail("the guard, still called, left the player: " + (int) guard.distanceTo(player) + " blocks off, "
						+ (int) Math.sqrt(guard.blockPosition().distSqr(post)) + " from the post");
				}
				if (withPlayer[0] >= 300) { // fifteen rounds of who follows whom
					player.getInventory().clearContent();
					lowered[0] = t;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(lowered[0] >= 0, "the guard caught up with the player for " + withPlayer[0] + " ticks of 300 ("
				+ (int) guard.distanceTo(player) + " blocks off)");
			double home = Math.sqrt(guard.blockPosition().distSqr(post));
			helper.assertTrue(home < Escorts.SEND_HOME, "the banner is lowered and the guard is still " + (int) home + " blocks from the post");
			helper.assertTrue(helper.getTick() - lowered[0] <= 60, "the guard took " + (helper.getTick() - lowered[0]) + " ticks to be back");
			level.setChunkForced(chunk.x, chunk.z, false);
		});
	}

	/**
	 * rally-banner.md: "fight whatever goes for you". A zombie (in a helmet, so the sun doesn't do the guard's work) goes
	 * for the player 10 blocks off while the guard follows: the guard names it the foe and it falls.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaRallySafe1114dZombie"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaRallySafe1114dZombie")
	public void aFollowingGuardGoesForAZombieAfterThePlayer(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		ServerPlayer player = player(helper, new BlockPos(14, 2, 14));
		Villager guard = follower(helper, player);
		Zombie[] zombie = {null};
		boolean[] named = {false};
		helper.onEachTick(() -> {
			long t = helper.getTick();
			if (zombie[0] == null && t >= 300) {
				helper.assertTrue(Escorts.isEscorting(guard), "setup: the guard isn't following the raised banner");
				zombie[0] = helper.spawn(EntityType.ZOMBIE, new BlockPos(24, 2, 14));
				zombie[0].setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				zombie[0].setNoAi(true);
			}
			if (zombie[0] != null && zombie[0].isAlive()) {
				zombie[0].setTarget(player);
				if (Escorts.leaderFoe(player, guard) == zombie[0]) {
					named[0] = true;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(zombie[0] != null && named[0], "the guard never named the zombie after the player as the foe");
			helper.assertTrue(!zombie[0].isAlive(), "the zombie after the player still stands: " + hurt(zombie[0]));
		});
	}
}
