package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.travel.FerryRides;
import io.github.jcondedata.aliveworkplace.travel.Ferrymen;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * QA lane (qa-1003-1433), bug B13 from its spec: "the player is landed by the far post before being saved, on the server
 * thread, the ferryman stays home". These check what vanilla actually wrote to the player's file when they left, a ride
 * to a post in another dimension, and that a player's own (non-ferry) boat is still saved with them as vanilla does.
 */
public class FerryLeaveQaGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** A jetty post at (2, 2, 2) with its ferryman at (3, 2, 2); the player stands by it. */
	private static Villager jetty(GameTestHelper helper, ServerPlayer player) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.TRAVEL_POST);
		TravelNetwork.get(helper.getLevel().getServer()).add(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(2, 2, 2))), "Jetty");
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> {
			if (!player.hasDisconnected()) {
				player.getServer().getPlayerList().remove(player);
			}
		});
		Villager ferryman = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 2));
		Jobs.employ(helper.getLevel(), ferryman, helper.absolutePos(new BlockPos(2, 2, 2)), ModVillagers.TRAVEL_POST_POI, ModVillagers.FERRYMAN);
		player.moveTo(helper.absolutePos(new BlockPos(4, 2, 4)).getCenter());
		return ferryman;
	}

	/** The file vanilla saved for {@code player} when they left (world/playerdata/<uuid>.dat). */
	private static CompoundTag savedFile(GameTestHelper helper, ServerPlayer player) {
		Path file = helper.getLevel().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getStringUUID() + ".dat");
		helper.assertTrue(Files.exists(file), "nothing was saved for the player who left: " + file);
		try {
			return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		} catch (java.io.IOException e) {
			throw new AssertionError("can't read the saved player: " + e);
		}
	}

	private static Vec3 savedPos(CompoundTag tag) {
		ListTag pos = tag.getList("Pos", 6);
		return new Vec3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
	}

	/**
	 * Leaving mid-ride: the file vanilla writes has the player by the far post, with no RootVehicle (no boat, no
	 * ferryman inside one), so they log back in on the far shore and the ferryman isn't carried off with them.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaSavedFileOfAPlayerWhoLeftMidRide"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaSavedFileOfAPlayerWhoLeftMidRide")
	public void qaSavedFileOfAPlayerWhoLeftMidRide(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Villager ferryman = jetty(helper, player);
		BlockPos away = new BlockPos(18, 2, 18);
		helper.setBlock(away.below(), Blocks.STONE);
		helper.setBlock(away, ModBlocks.TRAVEL_POST);
		TravelNetwork.Post far = TravelNetwork.get(helper.getLevel().getServer())
			.add(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(away)), "Far Shore");
		ItemStack ticket = Ferrymen.ticket(far);
		helper.runAfterDelay(2, () -> helper.assertTrue(Ferrymen.travel(player, ticket), "the ticket didn't work at the post"));
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(FerryRides.riding(player) && player.getVehicle() != null, "not on the ride");
			player.connection.disconnect(Component.literal("left the game"));
		});
		helper.runAfterDelay(17, () -> {
			CompoundTag saved = savedFile(helper, player);
			helper.assertFalse(saved.contains("RootVehicle"), "the player was saved sitting in a vehicle: " + saved.getCompound("RootVehicle").getCompound("Entity").getString("id"));
			Vec3 pos = savedPos(saved);
			helper.assertTrue(pos.distanceTo(helper.absolutePos(away).getCenter()) < 4,
				"saved at " + pos + ", not by the far post at " + helper.absolutePos(away));
			helper.assertTrue(saved.getString("Dimension").equals(helper.getLevel().dimension().location().toString()),
				"saved in " + saved.getString("Dimension"));
			helper.assertTrue(ferryman.isAlive() && !ferryman.isRemoved() && ferryman.getVehicle() == null, "the ferryman left with the player");
			helper.succeed();
		});
	}

	/**
	 * Leaving mid-ride to a post in the Nether: the player is saved in the Nether by that post (not in the Overworld,
	 * not in a boat), and nothing of them stays behind in either world.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLeavingMidRideToTheNetherSavesThemThere"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLeavingMidRideToTheNetherSavesThemThere")
	public void qaLeavingMidRideToTheNetherSavesThemThere(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Villager ferryman = jetty(helper, player);
		Vec3 ferrymanHome = ferryman.position();
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "no Nether on the test server");
		BlockPos here = helper.absolutePos(new BlockPos(0, 0, 0));
		BlockPos post = new BlockPos(here.getX(), 100, here.getZ());
		nether.getChunk(post);
		for (BlockPos p : BlockPos.betweenClosed(post.offset(-3, -1, -3), post.offset(3, 3, 3))) {
			nether.setBlockAndUpdate(p, p.getY() == post.getY() - 1 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState());
		}
		nether.setBlockAndUpdate(post, ModBlocks.TRAVEL_POST.defaultBlockState());
		TravelNetwork.Post gate = TravelNetwork.get(helper.getLevel().getServer()).add(GlobalPos.of(Level.NETHER, post), "Fortress Gate");
		ItemStack ticket = Ferrymen.ticket(gate);
		helper.runAfterDelay(2, () -> helper.assertTrue(Ferrymen.travel(player, ticket), "the ticket to the Nether didn't work"));
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(FerryRides.riding(player), "not on the ride");
			player.connection.disconnect(Component.literal("left the game"));
		});
		helper.runAfterDelay(17, () -> {
			CompoundTag saved = savedFile(helper, player);
			helper.assertTrue(saved.getString("Dimension").equals(Level.NETHER.location().toString()), "saved in " + saved.getString("Dimension") + ", not the Nether");
			helper.assertTrue(savedPos(saved).distanceTo(post.getCenter()) < 4, "saved at " + savedPos(saved) + ", the post is at " + post);
			helper.assertFalse(saved.contains("RootVehicle"), "saved sitting in a vehicle");
			helper.assertTrue(nether.getPlayerByUUID(player.getUUID()) == null && helper.getLevel().getPlayerByUUID(player.getUUID()) == null,
				"the player who left is still in a world");
			helper.assertTrue(helper.getLevel().getServer().getPlayerList().getPlayer(player.getUUID()) == null, "the player who left is still on the player list");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(Boat.class, helper.getBounds().inflate(8)).isEmpty(), "the ferry boat was left behind");
			helper.assertTrue(ferryman.getVehicle() == null && ferryman.level() == helper.getLevel() && ferryman.position().distanceTo(ferrymanHome) < 2,
				"the ferryman isn't home: " + helper.relativePos(ferryman.blockPosition()));
			helper.succeed();
		});
	}

	/**
	 * Not a ferry ride: a player leaving in their own boat is saved in it, as in vanilla (the leaving hook must touch
	 * only ferry rides), and the ferryman nearby isn't moved.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaAPlayerInTheirOwnBoatLeavesInIt"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaAPlayerInTheirOwnBoatLeavesInIt")
	public void qaAPlayerInTheirOwnBoatLeavesInIt(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Villager ferryman = jetty(helper, player);
		Vec3 ferrymanHome = ferryman.position();
		Boat boat = helper.spawn(EntityType.BOAT, new BlockPos(8, 2, 8));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(player.startRiding(boat, true), "couldn't get in the boat");
			helper.assertFalse(FerryRides.riding(player), "an own boat counts as a ferry ride");
			player.connection.disconnect(Component.literal("left the game"));
		});
		helper.runAfterDelay(4, () -> {
			CompoundTag saved = savedFile(helper, player);
			helper.assertTrue(saved.contains("RootVehicle") && saved.getCompound("RootVehicle").getCompound("Entity").getString("id").equals("minecraft:boat"),
				"the player's own boat wasn't saved with them, as vanilla does");
			helper.assertTrue(savedPos(saved).distanceTo(helper.absolutePos(new BlockPos(8, 2, 8)).getCenter()) < 3, "moved on leaving: " + savedPos(saved));
			helper.assertTrue(ferryman.position().distanceTo(ferrymanHome) < 2, "the ferryman was moved");
			helper.succeed();
		});
	}
}
