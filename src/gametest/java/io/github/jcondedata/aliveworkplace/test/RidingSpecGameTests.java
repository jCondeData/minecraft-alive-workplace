package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.fish.Fishers;
import io.github.jcondedata.aliveworkplace.guard.Cavalry;
import io.github.jcondedata.aliveworkplace.guard.GuardPatrol;
import io.github.jcondedata.aliveworkplace.mc.Boats;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The tester's spec tests for riding (0.137.0 and Unreleased): villagers sitting in what they ride, fishers in boats,
 * ferry rides and cavalry. Written from CHANGELOG.md, the commits and the classes' own promises, not from the code.
 */
public class RidingSpecGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos BARREL = new BlockPos(2, 2, 2);
	private static final BlockPos POST = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	// ---- Villagers sit when they ride (Unreleased) ----

	/**
	 * A villager in a minecart sits in it as a player does (0.6 below the seat, a player's own riding offset); a wandering
	 * trader, which isn't a villager, is left as vanilla has it (feet on the seat).
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void villagersSitInAMinecartLikeAPlayerAndTradersAreUnchanged(GameTestHelper helper) {
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, new BlockPos(8, 2, 4));
		helper.setBlock(new BlockPos(4, 2, 8), Blocks.RAIL);
		Minecart cart = helper.spawn(EntityType.MINECART, new BlockPos(4, 2, 8));
		Boat boat = helper.spawn(EntityType.BOAT, new BlockPos(8, 2, 8));
		helper.assertTrue(villager.startRiding(cart, true) && trader.startRiding(boat, true), "didn't get on");
		cart.positionRider(villager);
		boat.positionRider(trader);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 playerSeat = player.getVehicleAttachmentPoint(cart);
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.assertTrue(villager.getVehicleAttachmentPoint(cart).equals(playerSeat),
			"a villager's seat isn't a player's: " + villager.getVehicleAttachmentPoint(cart) + " vs " + playerSeat);
		double seat = cart.getPassengerRidingPosition(villager).y - playerSeat.y;
		helper.assertTrue(Math.abs(villager.getY() - seat) < 1.0E-6, "the villager isn't sitting in the minecart: at " + villager.getY() + ", the seat " + seat);
		double traderSeat = boat.getPassengerRidingPosition(trader).y;
		helper.assertTrue(Math.abs(trader.getY() - traderSeat) < 1.0E-6, "a wandering trader's seat changed: at " + trader.getY() + ", vanilla " + traderSeat);
		helper.succeed();
	}

	// ---- Fishing from boats (0.137.0) ----

	/** A fisherman with a boat in the barrel and a 16 x 16 lake (as in FisherGameTests). */
	static Villager boatFisher(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		Leftovers.after(helper, () -> helper.setDayTime(2000));
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(BARREL, Blocks.BARREL);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(5, 1, 5), new BlockPos(20, 1, 20))) {
			helper.setBlock(p, Blocks.WATER);
		}
		Container barrel = helper.getBlockEntity(BARREL);
		barrel.setItem(0, new ItemStack(Items.SPRUCE_BOAT));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, villager, helper.absolutePos(BARREL), PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN);
		Fishers.start(level, villager, new ItemStack(Items.FISHING_ROD));
		return villager;
	}

	/** Spruce boats anywhere: in the barrel, in the fisher's bag, lying about as items, and afloat. */
	static int boats(GameTestHelper helper, Villager villager) {
		Container barrel = helper.getBlockEntity(BARREL);
		int n = barrel.countItem(Items.SPRUCE_BOAT) + ModAttachments.BUILDER_BAG.getOrCreate(villager).count(Items.SPRUCE_BOAT);
		for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16), i -> i.getItem().is(Items.SPRUCE_BOAT))) {
			n += item.getItem().getCount();
		}
		return n + afloat(helper).size();
	}

	static java.util.List<Boat> afloat(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Boat.class, helper.getBounds().inflate(16));
	}

	/**
	 * The fisher's boat once they're out on the lake — in it for three seconds on end, by when they've rowed the few
	 * blocks to where they fish — or null. {@code afloat} counts the ticks (one per test).
	 */
	static Entity outOnTheLake(Villager villager, int[] afloat) {
		Entity vehicle = villager.getVehicle();
		if (vehicle == null || !Boats.isBoat(vehicle)) {
			afloat[0] = 0;
			return null;
		}
		return ++afloat[0] >= 60 ? vehicle : null;
	}

	/**
	 * A player breaks the fisher's boat out on the lake: the boat is lost as the one item it drops (never given back to
	 * the fisher as well), and the fisher is left in no boat.
	 */
	//$ gametest_ticks_batch AREA '2400' '"aBrokenFisherBoatIsNotDuplicated"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "aBrokenFisherBoatIsNotDuplicated")
	public void aBrokenFisherBoatIsNotDuplicated(GameTestHelper helper) {
		Villager villager = boatFisher(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		long[] brokenAt = {-1};
		int[] out = {0};
		helper.onEachTick(() -> {
			Entity boat = outOnTheLake(villager, out);
			if (brokenAt[0] < 0 && boat != null) {
				boat.hurt(helper.getLevel().damageSources().playerAttack(player), 100f);
				brokenAt[0] = helper.getTick();
				helper.assertTrue(boat.isRemoved(), "the boat didn't break");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokenAt[0] >= 0, "the fisher never rowed out");
			helper.assertTrue(helper.getTick() > brokenAt[0] + 200, "waiting");
			helper.assertTrue(villager.getVehicle() == null, "still in a boat");
			helper.assertTrue(afloat(helper).isEmpty(), "a boat afloat after it broke");
			int n = boats(helper, villager);
			Container barrel = helper.getBlockEntity(BARREL);
			helper.assertTrue(n == 1, n + " spruce boats in the world after the only one broke (barrel "
				+ barrel.countItem(Items.SPRUCE_BOAT) + ", bag "
				+ ModAttachments.BUILDER_BAG.getOrCreate(villager).count(Items.SPRUCE_BOAT) + ")");
		});
	}

	/**
	 * The world is saved with the fisher out on the lake and loaded again (the boat and the fisher in it come back as new
	 * entities): the fisher comes ashore and the boat is put away — one boat, none left afloat.
	 */
	//$ gametest_ticks_batch AREA '2400' '"aFisherSavedOnTheLakeComesAshore"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "aFisherSavedOnTheLakeComesAshore")
	public void aFisherSavedOnTheLakeComesAshore(GameTestHelper helper) {
		Villager first = boatFisher(helper);
		ServerLevel level = helper.getLevel();
		Villager[] fisher = {first};
		long[] reloadedAt = {-1};
		int[] out = {0};
		helper.onEachTick(() -> {
			Entity boat = outOnTheLake(fisher[0], out);
			if (reloadedAt[0] < 0 && boat != null) {
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(boat.saveAsPassenger(tag), "the boat didn't save");
				fisher[0].discard();
				boat.discard();
				Entity root = EntityType.loadEntityRecursive(tag, level, e -> e);
				helper.assertTrue(root != null && level.tryAddFreshEntityWithPassengers(root), "the boat didn't load");
				helper.assertTrue(root.getFirstPassenger() instanceof Villager, "no fisher in the loaded boat");
				fisher[0] = (Villager) root.getFirstPassenger();
				reloadedAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloadedAt[0] >= 0, "the fisher never rowed out");
			helper.assertTrue(fisher[0].getVehicle() == null, "still out in the boat " + (helper.getTick() - reloadedAt[0]) + " ticks after loading");
			helper.assertTrue(afloat(helper).isEmpty(), "a boat left afloat");
			int n = boats(helper, fisher[0]);
			helper.assertTrue(n == 1, n + " spruce boats after the reload, expected the one");
		});
	}

	/** A pond too small for open water: the fisher fishes from the shore and leaves the boat in the barrel. */
	//$ gametest_ticks_batch AREA '1600' '"noLakeNoBoatTrip"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "noLakeNoBoatTrip")
	public void noLakeNoBoatTrip(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(BARREL, Blocks.BARREL);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(11, 1, 11))) {
			helper.setBlock(p, Blocks.WATER); // 4 x 4: no 5 x 5 of open water anywhere
		}
		Container barrel = helper.getBlockEntity(BARREL);
		barrel.setItem(0, new ItemStack(Items.SPRUCE_BOAT));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, villager, helper.absolutePos(BARREL), PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN);
		Fishers.start(level, villager, new ItemStack(Items.FISHING_ROD));
		helper.onEachTick(() -> {
			if (villager.getVehicle() != null) {
				helper.fail("rowed out on a 4 x 4 pond");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(ModAttachments.FISH_CAUGHT.getOrElse(villager, 0) >= 2, "not fishing from the shore");
			helper.assertTrue(barrel.countItem(Items.SPRUCE_BOAT) == 1, "the boat left the barrel");
		});
	}

	// ---- Ferry rides (0.137.0) ----

	/**
	 * Getting out of the ferry boat early lands the passenger by the destination post straight away; the boat goes and
	 * the ferryman is back on his jetty (FerryRides: "getting out of the boat early ... lands them straight away").
	 */
	//$ gametest_ticks_batch AREA '200' '"aFerryPassengerWhoGetsOutEarlyLands"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "aFerryPassengerWhoGetsOutEarlyLands")
	public void aFerryPassengerWhoGetsOutEarlyLands(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos home = new BlockPos(2, 2, 2);
		BlockPos away = new BlockPos(18, 2, 18);
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		helper.setBlock(home, ModBlocks.TRAVEL_POST);
		helper.setBlock(away, ModBlocks.TRAVEL_POST);
		network.add(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(home)), "Jetty");
		TravelNetwork.Post awayPost = network.add(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(away)), "Far Shore");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		Villager ferryman = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), ferryman, helper.absolutePos(home), ModVillagers.TRAVEL_POST_POI, ModVillagers.FERRYMAN);
		Vec3 ferrymanHome = ferryman.position();
		player.moveTo(helper.absolutePos(new BlockPos(4, 2, 4)).getCenter());
		ItemStack ticket = Ferrymen.ticket(awayPost);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(Ferrymen.travel(player, ticket), "the ticket didn't work at the post");
			helper.assertTrue(player.getVehicle() != null && ferryman.getVehicle() == player.getVehicle(), "not in the boat with the ferryman");
		});
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(FerryRides.riding(player), "the ride ended by itself");
			player.stopRiding(); // sneaking out
		});
		helper.runAfterDelay(15, () -> {
			helper.assertFalse(FerryRides.riding(player), "still on the ride after getting out");
			helper.assertTrue(player.blockPosition().closerThan(helper.absolutePos(away), 4), "got out at " + helper.relativePos(player.blockPosition()));
			helper.assertTrue(ferryman.getVehicle() == null && ferryman.position().distanceTo(ferrymanHome) < 2,
				"the ferryman is at " + helper.relativePos(ferryman.blockPosition()));
			helper.assertTrue(afloat(helper).isEmpty(), "the ferry boat was left behind");
			helper.succeed();
		});
	}

	// ---- Cavalry (0.137.0) ----

	static Villager guard(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Leftovers.after(helper, () -> helper.setDayTime(2000));
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		return villager;
	}

	static Horse saddledHorse(GameTestHelper helper, BlockPos at) {
		Horse horse = helper.spawn(EntityType.HORSE, at);
		horse.setTamed(true);
		horse.equipSaddle(new ItemStack(Items.SADDLE), null);
		return horse;
	}

	/**
	 * A mounted guard gets down to spar at a Training Dummy — never hitting it from the saddle — and back up after
	 * (Cavalry: "get down to spar at a Training Dummy (and back up after)").
	 */
	//$ gametest_ticks_batch AREA '2400' '"aMountedGuardSparsOnFoot"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "aMountedGuardSparsOnFoot")
	public void aMountedGuardSparsOnFoot(GameTestHelper helper) {
		Villager guard = guard(helper);
		Horse horse = saddledHorse(helper, new BlockPos(6, 2, 6));
		helper.setBlock(new BlockPos(12, 2, 12), ModBlocks.TRAINING_DUMMY);
		boolean[] mounted = {false};
		int[] lastHits = {0};
		boolean[] remounted = {false};
		helper.onEachTick(() -> {
			if (guard.getVehicle() == horse) {
				mounted[0] = true;
			}
			int hits = ModAttachments.DUMMY_HITS.getOrElse(guard, 0);
			if (hits > lastHits[0] && guard.getVehicle() != null) {
				helper.fail("hit the dummy from the saddle");
			}
			lastHits[0] = hits;
			if (hits >= GuardPatrol.HITS && guard.getVehicle() == horse) {
				remounted[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(mounted[0], "never got on the horse");
			helper.assertTrue(lastHits[0] >= GuardPatrol.HITS, "hits: " + lastHits[0]);
			helper.assertTrue(remounted[0], "didn't get back on the horse after sparring");
		});
	}

	/**
	 * Which mounts carry a guard (0.137.0: "a tamed, saddled horse (or donkey or mule) ... off its lead"): a saddled
	 * donkey and mule do; a horse a player is riding, a foal and an unsaddled horse don't.
	 */
	//$ gametest_ticks_batch AREA '100' '"whichMountsCarryAGuard"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "whichMountsCarryAGuard")
	public void whichMountsCarryAGuard(GameTestHelper helper) {
		guard(helper);
		AbstractHorse donkey = helper.spawn(EntityType.DONKEY, new BlockPos(4, 2, 8));
		AbstractHorse mule = helper.spawn(EntityType.MULE, new BlockPos(8, 2, 8));
		for (AbstractHorse h : new AbstractHorse[] {donkey, mule}) {
			h.setTamed(true);
			h.equipSaddle(new ItemStack(Items.SADDLE), null);
			helper.assertTrue(Cavalry.usable(h), "a tamed, saddled " + h.getType().getDescription().getString() + " doesn't carry a guard");
		}
		Horse ridden = saddledHorse(helper, new BlockPos(12, 2, 8));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(player.startRiding(ridden, true), "the player didn't get on");
		helper.assertFalse(Cavalry.usable(ridden), "a guard would take the horse a player is riding");
		player.stopRiding();
		helper.getLevel().getServer().getPlayerList().remove(player);
		Horse foal = saddledHorse(helper, new BlockPos(16, 2, 8));
		foal.setAge(-24000);
		helper.assertFalse(Cavalry.usable(foal), "a foal carries a guard");
		Horse bare = helper.spawn(EntityType.HORSE, new BlockPos(16, 2, 12));
		bare.setTamed(true);
		helper.assertFalse(Cavalry.usable(bare), "an unsaddled horse carries a guard");
		helper.succeed();
	}

}
