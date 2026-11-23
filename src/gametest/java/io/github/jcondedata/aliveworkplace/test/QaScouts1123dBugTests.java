package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.guard.Scouts;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * QA lane D, 2026-11-23: a Scouts (32.12) bug these tests show, failing until it is fixed. scouts.md: "Hand them a bow,
 * shield or healing potion and they are that kind again; the spyglass comes back to you", and "A scout retrained ...
 * mid-mission has the mission called off and puts a raised spyglass away". A scout handed a shield while the spyglass
 * is raised at the lookout (in their main hand, the sword in the off hand) stays a Scout: the player gets the sword,
 * not the spyglass, and once the look ends the spyglass goes back to the off hand and the shield into the main hand.
 */
public class QaScouts1123dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos POST = new BlockPos(4, 2, 4);

	/**
	 * Ash, sent to a lair 12 blocks off, raises the spyglass at once; a player hands him a shield (sneak-right-click):
	 * he is a Knight ("Ash is a Knight now"), the mission is called off, the player has the spyglass back, and Ash keeps
	 * his sword and the shield in his off hand.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaScouts1123dShieldAtLookout"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaScouts1123dShieldAtLookout")
	public void aScoutHandedAShieldWithTheSpyglassRaisedIsAKnightAndTheSpyglassComesBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		boolean open = Expansions.openForTests;
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 32;
		Expansions.openForTests = true;
		Scouts.forget(level);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		helper.setBlock(new BlockPos(4, 2, 6), Blocks.CHEST);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(80), m -> m.getTags().contains(Lairs.TAG)).forEach(Mob::discard);
			Lairs.forget(level);
			Scouts.forget(level);
			VillageHalls.RADIUS = radius;
			Expansions.openForTests = open;
			level.setDayTime(time);
		});
		level.setDayTime(1000);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = StationsSpecGameTests.listeningPlayer(helper, seen);
		player.teleportTo(hall.getX() + 2.5, hall.getY(), hall.getZ() + 0.5);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		Villager ash = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		ash.setNoAi(true);
		ash.setCustomName(Component.literal("Ash"));
		Jobs.employ(level, ash, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		ash.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		ash.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SPYGLASS));
		// A bandits' lair 12 blocks east of the hall: within sight from the village, so the look starts at once.
		int x = hall.getX() + 12;
		BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, hall.getZ()), hall.getZ());
		Vindicator captain = EntityType.VINDICATOR.create(level);
		captain.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		captain.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
		captain.setNoAi(true);
		captain.setInvulnerable(true);
		captain.addTag(Lairs.TAG);
		captain.addTag(Lairs.CAPTAIN_TAG);
		level.addFreshEntity(captain);
		Lairs.putForTest(level, new Lairs.Lair(pos, hall, captain.getUUID(), Chronicle.day(level), Threats.BANDITS, 7, Chronicle.day(level), 0, 0, 3));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(Scouts.send(level, hall) == Scouts.Sent.SENT, "setup: Ash wasn't sent");
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(Scouts.missionOf(level, ash.getUUID()).map(m -> m.phase() == Scouts.Phase.LOOK).orElse(false)
					&& ash.getMainHandItem().is(Items.SPYGLASS), "setup: Ash isn't looking: " + Scouts.missionOf(level, ash.getUUID()) + ", holding " + ash.getMainHandItem());
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
				ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
				helper.assertTrue(Guards.makesKind(held), "a shield isn't taken");
				Guards.hand(player, ash, held); // what BuilderEvents does on a sneak-right-click with it
				List<String> wrong = new ArrayList<>();
				if (!seen.contains("Ash is a Knight now")) {
					wrong.add("the action bar says " + seen);
				}
				helper.runAfterDelay(Scouts.LOOK_TICKS + 20, () -> {
					if (Guards.kind(ash) != Guards.Kind.KNIGHT) {
						wrong.add("Ash is a " + Guards.kind(ash) + ", not a KNIGHT");
					}
					if (!ash.getOffhandItem().is(Items.SHIELD) || !ash.getMainHandItem().is(Items.IRON_SWORD)) {
						wrong.add("Ash holds " + ash.getMainHandItem() + " in the main hand and " + ash.getOffhandItem() + " in the off hand, not the sword and the shield");
					}
					if (player.getInventory().countItem(Items.SPYGLASS) != 1 || player.getInventory().countItem(Items.IRON_SWORD) != 0) {
						wrong.add("the player got " + player.getInventory().countItem(Items.SPYGLASS) + " spyglasses and "
							+ player.getInventory().countItem(Items.IRON_SWORD) + " iron swords back, not the 1 spyglass");
					}
					if (Scouts.out(ash)) {
						wrong.add("Ash is still on the mission: " + Scouts.missionOf(level, ash.getUUID()));
					}
					helper.assertTrue(wrong.isEmpty(), "a scout handed a shield at the lookout: " + String.join("; ", wrong));
					helper.succeed();
				});
			});
		});
	}
}
