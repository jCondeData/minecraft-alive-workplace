package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mc.Boats;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The full check's ferry edges (ROADMAP 21.2): leaving the game in the middle of a ride, and a Travel Ticket to a post in
 * another dimension. Written from FerryRides' contract ("leaving the game mid-ride" lands them straight away; the ride
 * takes them to the destination post wherever it is).
 */
public class FerryEdgeGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** A jetty post at (2, 2, 2) with its ferryman at (3, 2, 2); the player stands at the post. */
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

	private static boolean boatsLeft(GameTestHelper helper) {
		return !helper.getLevel().getEntitiesOfClass(Boat.class, helper.getBounds().inflate(8)).isEmpty();
	}

	/**
	 * Leaving the game in the middle of a ferry ride (the connection drops): the player is landed by the far post at
	 * once, so they log back in over there; the boat goes and the ferryman is back on his jetty, not stuck in a boat.
	 */
	//$ gametest_ticks_batch AREA '200' '"aPlayerWhoLeavesMidRideIsLanded"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "aPlayerWhoLeavesMidRideIsLanded")
	public void aPlayerWhoLeavesMidRideIsLanded(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Villager ferryman = jetty(helper, player);
		Vec3 ferrymanHome = ferryman.position();
		BlockPos away = new BlockPos(18, 2, 18);
		helper.setBlock(away.below(), Blocks.STONE);
		helper.setBlock(away, ModBlocks.TRAVEL_POST);
		TravelNetwork.Post far = TravelNetwork.get(helper.getLevel().getServer())
			.add(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(away)), "Far Shore");
		ItemStack ticket = Ferrymen.ticket(far);
		Entity[] boat = {null};
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(Ferrymen.travel(player, ticket), "the ticket didn't work at the post");
			boat[0] = player.getVehicle();
			helper.assertTrue(boat[0] != null && ferryman.getVehicle() == boat[0], "not in the boat with the ferryman");
		});
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(FerryRides.riding(player), "the ride ended by itself");
			player.connection.disconnect(Component.literal("left the game")); // (closes the connection, as quitting does)
		});
		helper.runAfterDelay(22, () -> {
			helper.assertTrue(player.hasDisconnected(), "the player is still connected");
			helper.assertFalse(FerryRides.riding(player), "still on the ride after leaving");
			helper.assertTrue(player.getVehicle() == null, "saved sitting in the boat");
			helper.assertTrue(player.blockPosition().closerThan(helper.absolutePos(away), 4),
				"left at " + helper.relativePos(player.blockPosition()) + ", not by the far post");
			helper.assertTrue(boat[0].isRemoved() && !boatsLeft(helper), "the ferry boat was left behind");
			helper.assertTrue(ferryman.getVehicle() == null && ferryman.position().distanceTo(ferrymanHome) < 2,
				"the ferryman is at " + helper.relativePos(ferryman.blockPosition()) + (ferryman.getVehicle() != null ? ", in a boat" : ""));
			helper.succeed();
		});
	}

	/**
	 * A Travel Ticket to a post in the Nether, used at an Overworld post: a boat ride, then the player is in the Nether
	 * by that post, standing on its floor, with the post added to the places they know; nothing is left behind here.
	 */
	//$ gametest_ticks_batch AREA '300' '"aTicketToAnotherDimensionTakesYouThere"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "aTicketToAnotherDimensionTakesYouThere")
	public void aTicketToAnotherDimensionTakesYouThere(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Villager ferryman = jetty(helper, player);
		Vec3 ferrymanHome = ferryman.position();
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "no Nether on the test server");
		// A small room in the Nether, under this test's own spot (x and z as here, at y 100): floor, post, air around
		BlockPos here = helper.absolutePos(new BlockPos(0, 0, 0));
		BlockPos post = new BlockPos(here.getX(), 100, here.getZ());
		nether.getChunk(post);
		for (BlockPos p : BlockPos.betweenClosed(post.offset(-3, -1, -3), post.offset(3, 3, 3))) {
			nether.setBlockAndUpdate(p, p.getY() == post.getY() - 1 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState());
		}
		nether.setBlockAndUpdate(post, ModBlocks.TRAVEL_POST.defaultBlockState());
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		TravelNetwork.Post fortress = network.add(GlobalPos.of(Level.NETHER, post), "Fortress Gate");
		ItemStack ticket = Ferrymen.ticket(fortress);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(Ferrymen.travel(player, ticket), "the ticket to the Nether didn't work at the post");
			helper.assertTrue(player.getVehicle() != null && Boats.isBoat(player.getVehicle()), "no boat ride first");
		});
		helper.runAfterDelay(2 + FerryRides.RIDE_TICKS + 3, () -> {
			helper.assertFalse(FerryRides.riding(player), "still on the ride");
			helper.assertTrue(player.level() == nether, "arrived in " + player.level().dimension().location());
			helper.assertTrue(player.blockPosition().closerThan(post, 4), "arrived at " + player.blockPosition() + ", the post is at " + post);
			helper.assertTrue(nether.getBlockState(player.blockPosition().below()).isSolid() && !nether.getBlockState(player.blockPosition()).isSolid(),
				"not standing on the floor: " + nether.getBlockState(player.blockPosition()) + " at " + player.blockPosition());
			helper.assertTrue(network.known(player.getUUID(), null).contains(fortress), "the Nether post isn't among the places they know");
			helper.assertFalse(boatsLeft(helper), "the ferry boat was left behind in the Overworld");
			helper.assertTrue(ferryman.getVehicle() == null && ferryman.position().distanceTo(ferrymanHome) < 2 && ferryman.level() == helper.getLevel(),
				"the ferryman didn't get back to his jetty: " + helper.relativePos(ferryman.blockPosition()));
			helper.succeed();
		});
	}

	/**
	 * A player who can't sit in a boat just now (already riding something, here a minecart) is taken straight to the
	 * ticket's post, with no boat ride: FerryRides' contract for players already riding, spectators and sleepers. Found by
	 * the full check's mutants (21.2): deleting that straight-there trip went unnoticed.
	 */
	//$ gametest_ticks_batch AREA '100' '"aPlayerAlreadyRidingGoesStraightThere"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "aPlayerAlreadyRidingGoesStraightThere")
	public void aPlayerAlreadyRidingGoesStraightThere(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Villager ferryman = jetty(helper, player);
		Vec3 ferrymanHome = ferryman.position();
		BlockPos away = new BlockPos(18, 2, 18);
		helper.setBlock(away.below(), Blocks.STONE);
		helper.setBlock(away, ModBlocks.TRAVEL_POST);
		TravelNetwork.Post far = TravelNetwork.get(helper.getLevel().getServer())
			.add(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(away)), "Far Shore");
		ItemStack ticket = Ferrymen.ticket(far);
		helper.runAfterDelay(2, () -> {
			Entity cart = helper.spawn(EntityType.MINECART, new BlockPos(4, 2, 4));
			helper.assertTrue(player.startRiding(cart, true), "the player couldn't get into the minecart");
			helper.assertTrue(Ferrymen.travel(player, ticket), "the ticket didn't work for a player in a minecart");
			helper.assertFalse(FerryRides.riding(player), "a ferry ride started for a player already riding");
		});
		helper.runAfterDelay(4, () -> {
			helper.assertTrue(player.blockPosition().closerThan(helper.absolutePos(away), 4),
				"the player is at " + helper.relativePos(player.blockPosition()) + ", not by the far post");
			helper.assertFalse(boatsLeft(helper), "a ferry boat was put out for a player already riding");
			helper.assertTrue(TravelNetwork.get(helper.getLevel().getServer()).known(player.getUUID(), null).contains(far),
				"the far post isn't among the places they know");
			helper.assertTrue(ferryman.getVehicle() == null && ferryman.position().distanceTo(ferrymanHome) < 2, "the ferryman left his jetty");
			helper.succeed();
		});
	}

	/**
	 * Only the ferryman rows: a plain villager standing closer to the player than the ferryman stays where he is. Found by
	 * the full check's mutants (21.2): letting any villager near the post row went unnoticed.
	 */
	//$ gametest_ticks_batch AREA '100' '"onlyTheFerrymanRows"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "onlyTheFerrymanRows")
	public void onlyTheFerrymanRows(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Villager ferryman = jetty(helper, player);
		Villager bystander = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 4));
		bystander.setNoAi(true);
		BlockPos away = new BlockPos(18, 2, 18);
		helper.setBlock(away.below(), Blocks.STONE);
		helper.setBlock(away, ModBlocks.TRAVEL_POST);
		TravelNetwork.Post far = TravelNetwork.get(helper.getLevel().getServer())
			.add(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(away)), "Far Shore");
		ItemStack ticket = Ferrymen.ticket(far);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(bystander.distanceToSqr(player) < ferryman.distanceToSqr(player), "the bystander isn't the closer villager");
			helper.assertTrue(Ferrymen.travel(player, ticket), "the ticket didn't work at the post");
			Entity boat = player.getVehicle();
			helper.assertTrue(boat != null && ferryman.getVehicle() == boat, "the ferryman isn't rowing the player's boat");
			helper.assertTrue(bystander.getVehicle() == null, "the bystander got into the boat");
			helper.succeed();
		});
	}
}
