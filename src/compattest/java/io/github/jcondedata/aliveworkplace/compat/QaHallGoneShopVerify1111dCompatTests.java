package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * QA (lane d, 2026-11-11) verifying two fixes from their specs, beyond what their own tests check.
 * <ul>
 * <li>B996 (with Waystones): a village whose Village Hall is broken leaves the waystone network. Here: the hall put back
 * rejoins; three villages with the middle one's hall gone (the other two stay linked, settlers go through the third, the
 * neighbors' rounds don't bring the orphaned plaza back as Brookmere); a hall gone by a region edit (no block update):
 * no settlers through it and a caravan to it walks; a caravan by waystone on its way when the hall breaks; the network
 * file saved before the hall went, loaded again.</li>
 * <li>B1147 (CobbleDollars shop): goods and price both changed say "This counter now sells ..." with the new price and take
 * nothing; a swap the buyer's screen already shows (redrawn) or one put back before Confirm buys at once with no
 * sentence; goods swapped to something the chest lacks: the new sentence, nothing taken.</li>
 * </ul>
 */
public class QaHallGoneShopVerify1111dCompatTests implements FabricGameTest {
	private static final String WAY = "aliveworkplace_compat:huge_area";
	private static final String SHOP = CompatGameTests.AREA;
	private static final BlockPos HALL_A = new BlockPos(1, 2, 1);
	private static final BlockPos HALL_B = new BlockPos(28, 2, 28);
	private static final BlockPos HALL_C = new BlockPos(1, 2, 28);
	private static final BlockPos PLAZA_A = new BlockPos(3, 2, 3);
	private static final BlockPos PLAZA_B = new BlockPos(19, 2, 19);
	private static final BlockPos PLAZA_C = new BlockPos(3, 2, 19);
	/** The stone's spot in the plaza (lower half), as tools/blueprints/waystones.py draws it. */
	private static final BlockPos STONE = new BlockPos(3, 2, 3);
	private static final String NETWORK = "aliveworkplace_village_waystones";
	private static final int SLOT = Shops.FIRST_GOODS_SLOT;

	// ---- villages with their plazas (as QaWaystones1107dBugCompatTests sets them up) ---------------------------------

	private static BlockPos abs(GameTestHelper helper, BlockPos rel) {
		return helper.absolutePos(rel);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	private static void plaza(GameTestHelper helper, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint"));
		BlueprintData.Placement placement = placement(helper, origin);
		BuildPlan plan = BuildPlan.create(blueprint, placement);
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
				}
			}
		}
		BuildSiteManager.get(level).recordFinished(VillageWaystones.PLAZA, placement, UUID.randomUUID());
	}

	private static BlockPos stone(GameTestHelper helper, BlockPos plaza) {
		return helper.absolutePos(plaza).offset(STONE);
	}

	private static void hall(GameTestHelper helper, BlockPos at, String name, UUID owner) {
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(at);
		hall.setCustomName(Component.literal(name));
		hall.setOwner(owner, "Jesse");
		VillageHalls.forgetNearest();
	}

	/** Ashford and Brookmere (and Carrow with {@code three}), each with its finished plaza and its standing stone. */
	private static void villages(GameTestHelper helper, boolean three) {
		RaidHelperCompatTests.clearAround(helper); // halls, stones and entities earlier batches left nearby
		VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : manager.finishedNear(level, abs(helper, HALL_A), 160)) {
			if (VillageWaystones.isPlaza(f.structure())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (io.github.jcondedata.aliveworkplace.build.BuildSite site : List.copyOf(manager.all())) {
			if (VillageWaystones.isPlaza(site.structure()) && site.placement().origin().closerThan(abs(helper, HALL_A), 160)) {
				manager.remove(site.id());
			}
		}
		cleanUp(helper);
		VillageWaystones.Data.get(level).halls().forEach(VillageWaystones.Data.get(level)::forget);
		UUID owner = UUID.randomUUID();
		hall(helper, HALL_A, "Ashford", owner);
		plaza(helper, PLAZA_A);
		hall(helper, HALL_B, "Brookmere", owner);
		plaza(helper, PLAZA_B);
		if (three) {
			hall(helper, HALL_C, "Carrow", owner);
			plaza(helper, PLAZA_C);
		}
		VillageHalls.forgetNearest();
		VillageWaystones.round(level, abs(helper, HALL_A));
		VillageWaystones.round(level, abs(helper, HALL_B));
		if (three) {
			VillageWaystones.round(level, abs(helper, HALL_C));
			helper.assertTrue(VillageWaystones.linked(level, abs(helper, HALL_A), abs(helper, HALL_C)), "setup: Ashford and Carrow aren't linked");
			helper.assertTrue(VillageWaystones.stoneOf(level, abs(helper, HALL_C)).equals(Optional.of(stone(helper, PLAZA_C))),
				"setup: Carrow's stone is " + VillageWaystones.stoneOf(level, abs(helper, HALL_C)));
		}
		helper.assertTrue(VillageWaystones.linked(level, abs(helper, HALL_A), abs(helper, HALL_B)), "setup: Ashford and Brookmere aren't linked");
		helper.assertTrue(VillageWaystones.stoneOf(level, abs(helper, HALL_B)).equals(Optional.of(stone(helper, PLAZA_B))),
			"setup: Brookmere's stone is " + VillageWaystones.stoneOf(level, abs(helper, HALL_B)));
	}

	private static void cleanUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Caravans.Data caravans = Caravans.Data.get(level);
		for (BlockPos at : List.of(HALL_A, HALL_B, HALL_C)) {
			caravans.remove(helper.absolutePos(at));
			VillageWaystones.Data.get(level).forget(helper.absolutePos(at));
			level.getPoiManager().remove(helper.absolutePos(at)); // (a hall taken out by a "region edit" leaves its point)
		}
		RealmData.get(level.getServer()).removeOrder(GlobalPos.of(level.dimension(), abs(helper, HALL_A)));
		for (BlockPos plaza : List.of(PLAZA_A, PLAZA_B, PLAZA_C)) {
			BuildSiteManager.get(level).forgetFinished(placement(helper, plaza));
		}
		VillageHalls.forgetNearest();
	}

	/** Breaks the hall as a player does (a block update: the hall's onRemove runs). */
	private static void breakHall(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, Blocks.AIR);
		VillageHalls.forgetNearest();
	}

	/**
	 * Takes the hall out as a region edit does: the chunk's block is swapped with no block update, so the hall's onRemove
	 * never runs and the network file still lists the village (the case linked's standsAt check is for).
	 */
	private static void editHallAway(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(at);
		LevelChunk chunk = level.getChunkAt(pos);
		chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, Blocks.AIR.defaultBlockState());
		chunk.removeBlockEntity(pos);
		VillageHalls.forgetNearest();
		helper.assertTrue(level.getBlockState(pos).isAir(), "setup: the edited hall is still there");
	}

	/** An order from Ashford for Newbrook at {@code spot}, mustering, not yet going through any stone. */
	private static RealmData.Order order(GameTestHelper helper, BlockPos spot) {
		ServerLevel level = helper.getLevel();
		return new RealmData.Order(GlobalPos.of(level.dimension(), abs(helper, HALL_A)), spot, Optional.of(Component.literal("Newbrook")), RealmData.MUSTER,
			level.getGameTime(), level.getGameTime() + 5000, 0, level.getGameTime(), List.of(), Optional.empty(), Map.of(), List.of(), false, Optional.empty());
	}

	private static Optional<BlockPos> via(GameTestHelper helper, BlockPos spot) {
		return Settlers.via(helper.getLevel(), order(helper, spot));
	}

	/** A spot far past Brookmere (diagonally): Brookmere's stone is nearest it, Carrow's next, Ashford's hall farthest. */
	private static BlockPos beyondB(GameTestHelper helper) {
		return abs(helper, HALL_B).offset(300, 0, 300);
	}

	// ---- caravans (as QaCaravanStone1102dCompatTests sets them up) ---------------------------------------------------

	/** A Storehouse with a chest by Ashford and Brookmere, Ashford's with 64 oak logs. */
	private static Caravans.Data trade(GameTestHelper helper) {
		helper.setBlock(new BlockPos(14, 2, 1), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(14, 2, 3), Blocks.CHEST);
		helper.setBlock(new BlockPos(24, 2, 14), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(24, 2, 16), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(14, 2, 3));
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
		return Caravans.Data.get(helper.getLevel());
	}

	private static void route(GameTestHelper helper, Caravans.Data data) {
		data.setWants(abs(helper, HALL_A), Component.literal("Ashford"), List.of());
		data.setWants(abs(helper, HALL_B), Component.literal("Brookmere"), List.of(new Caravans.Want(Items.OAK_LOG, 16)));
		helper.assertTrue(data.toggleRoute(abs(helper, HALL_A), abs(helper, HALL_B)), "setup: no route");
	}

	private static List<Caravans.Shipment> toB(GameTestHelper helper, Caravans.Data data) {
		return data.onTheRoad().stream().filter(s -> s.from().equals(abs(helper, HALL_A)) && s.to().equals(abs(helper, HALL_B))).toList();
	}

	/** Ashford's hall round, allowed to send today's caravan again. */
	private static void sendRound(GameTestHelper helper, Caravans.Data data) {
		data.sent(abs(helper, HALL_A), -1);
		Caravans.round(helper.getLevel(), abs(helper, HALL_A), null);
	}

	// ---- B996 tests --------------------------------------------------------------------------------------------------

	/**
	 * Brookmere's hall broken: off the network (not linked, not among the villages, no settlers through it). The hall put
	 * back on its spot, its round run: Brookmere is back on the network, linked again, and settlers go through its stone.
	 */
	//$ gametest_ticks_batch WAY '100' '"qaHallGoneBack1111d"'
	@GameTest(template = WAY, timeoutTicks = 100, batch = "qaHallGoneBack1111d")
	public void aHallPutBackRejoinsTheNetwork(GameTestHelper helper) {
		villages(helper, false);
		ServerLevel level = helper.getLevel();
		BlockPos a = abs(helper, HALL_A);
		BlockPos b = abs(helper, HALL_B);
		try {
			helper.assertTrue(via(helper, beyondB(helper)).equals(Optional.of(b)), "setup: settlers don't go through Brookmere: " + via(helper, beyondB(helper)));
			breakHall(helper, HALL_B);
			helper.assertFalse(VillageWaystones.linked(level, a, b), "still linked with Brookmere after its hall broke");
			helper.assertFalse(VillageWaystones.villages(level).contains(b), "Brookmere still among the network's villages: " + VillageWaystones.villages(level));
			helper.assertTrue(VillageWaystones.stoneOf(level, b).isEmpty(), "Brookmere's stone still kept: " + VillageWaystones.stoneOf(level, b));
			helper.assertTrue(via(helper, beyondB(helper)).isEmpty(), "settlers go through hall-less Brookmere: " + via(helper, beyondB(helper)));

			hall(helper, HALL_B, "Brookmere", UUID.randomUUID());
			VillageWaystones.round(level, b);
			helper.assertTrue(VillageWaystones.linked(level, a, b), "the hall put back on its spot isn't linked again after its round (stone "
				+ VillageWaystones.stoneOf(level, b) + ")");
			helper.assertTrue(VillageWaystones.villages(level).contains(b), "Brookmere, its hall back, isn't among the villages");
			helper.assertTrue(via(helper, beyondB(helper)).equals(Optional.of(b)), "settlers don't go through Brookmere again: " + via(helper, beyondB(helper)));
			helper.assertTrue(VillageWaystones.EXTENSION.call(s -> s.nameAt(level, stone(helper, PLAZA_B)), Optional.<String>empty())
				.equals(Optional.of("Brookmere")), "the stone of the hall put back isn't called Brookmere");
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	/**
	 * Three villages, Brookmere's hall broken (in the middle of the road to a spot past it), then Ashford's and Carrow's
	 * rounds run (the orphaned plaza's nearest hall is now Carrow's). Ashford and Carrow stay linked; settlers for a spot
	 * past Brookmere go through Carrow, never Brookmere; Brookmere is linked with no one and stays off the villages and the
	 * hall's Map, the neighbors' rounds don't bring it back.
	 */
	//$ gametest_ticks_batch WAY '100' '"qaHallGoneThree1111d"'
	@GameTest(template = WAY, timeoutTicks = 100, batch = "qaHallGoneThree1111d")
	public void threeVillagesTheMiddleHallGone(GameTestHelper helper) {
		villages(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos a = abs(helper, HALL_A);
		BlockPos b = abs(helper, HALL_B);
		BlockPos c = abs(helper, HALL_C);
		try {
			helper.assertTrue(via(helper, beyondB(helper)).equals(Optional.of(b)), "setup: with all halls standing the settlers should go through Brookmere: "
				+ via(helper, beyondB(helper)));
			breakHall(helper, HALL_B);
			VillageWaystones.round(level, a);
			VillageWaystones.round(level, c);
			helper.assertTrue(VillageWaystones.linked(level, a, c), "Ashford and Carrow aren't linked after Brookmere's hall broke");
			helper.assertTrue(VillageWaystones.linked(level, c, a), "Carrow and Ashford aren't linked (the other way)");
			helper.assertFalse(VillageWaystones.linked(level, a, b) || VillageWaystones.linked(level, c, b) || VillageWaystones.linked(level, b, c),
				"a link with hall-less Brookmere survived");
			helper.assertFalse(VillageWaystones.villages(level).contains(b), "Brookmere is among the villages after the neighbors' rounds: " + VillageWaystones.villages(level));
			Optional<BlockPos> through = via(helper, beyondB(helper));
			helper.assertTrue(through.equals(Optional.of(c)), "settlers for a spot past Brookmere should go through Carrow, go through " + through);
			List<VillageWaystones.Village> map = VillageWaystones.network(level, a, null);
			helper.assertTrue(map.stream().noneMatch(v -> v.hall().equals(b)), "Brookmere is on Ashford's Map: " + map);
			Optional<String> orphan = VillageWaystones.EXTENSION.call(s -> s.nameAt(level, stone(helper, PLAZA_B)), Optional.<String>empty());
			org.slf4j.LoggerFactory.getLogger("QaHallGone1111d").info("QA-OBSERVE orphaned stone name={} carrowStone={} ashfordStone={}", orphan,
				VillageWaystones.stoneOf(level, c).map(BlockPos::toShortString), VillageWaystones.stoneOf(level, a).map(BlockPos::toShortString));
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	/**
	 * Brookmere's hall taken out by a region edit (no block update: the network file and the caravans' list still hold
	 * it). linked and villages leave it out; settlers don't go through its stone; and the caravan on Ashford's route to
	 * Brookmere doesn't go by waystone (a caravan sent with the hall standing did).
	 */
	//$ gametest_ticks_batch WAY '100' '"qaHallGoneEdit1111d"'
	@GameTest(template = WAY, timeoutTicks = 100, batch = "qaHallGoneEdit1111d")
	public void aHallGoneByARegionEditIsOffTheNetwork(GameTestHelper helper) {
		villages(helper, false);
		ServerLevel level = helper.getLevel();
		BlockPos a = abs(helper, HALL_A);
		BlockPos b = abs(helper, HALL_B);
		Caravans.Data data = trade(helper);
		try {
			route(helper, data);
			sendRound(helper, data);
			List<Caravans.Shipment> first = toB(helper, data);
			helper.assertTrue(first.size() == 1 && first.get(0).byWaystone(), "setup: with both halls the caravan should go by waystone: " + first);
			editHallAway(helper, HALL_B);
			helper.assertTrue(VillageWaystones.Data.get(level).stone(b).isPresent(), "setup: the network forgot Brookmere without a block update");
			helper.assertFalse(VillageWaystones.linked(level, a, b), "linked with a village whose hall a region edit took out");
			helper.assertFalse(VillageWaystones.villages(level).contains(b), "villages() lists a village whose hall is gone: " + VillageWaystones.villages(level));
			helper.assertTrue(via(helper, beyondB(helper)).isEmpty(), "settlers go through hall-less Brookmere: " + via(helper, beyondB(helper)));
			sendRound(helper, data);
			List<Caravans.Shipment> after = toB(helper, data);
			helper.assertTrue(after.stream().skip(1).noneMatch(Caravans.Shipment::byWaystone),
				"a caravan sent to Brookmere after its hall went goes by waystone: " + after);
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	/**
	 * A caravan by waystone on its way from Ashford to Brookmere when Brookmere's hall is broken: it turns home with its
	 * goods (the 16 logs), not by waystone; no caravan is left going to Brookmere, and the next round sends none there.
	 */
	//$ gametest_ticks_batch WAY '100' '"qaHallGoneCaravan1111d"'
	@GameTest(template = WAY, timeoutTicks = 100, batch = "qaHallGoneCaravan1111d")
	public void aCaravanByWaystoneOnItsWayWhenTheHallBreaks(GameTestHelper helper) {
		villages(helper, false);
		ServerLevel level = helper.getLevel();
		BlockPos a = abs(helper, HALL_A);
		BlockPos b = abs(helper, HALL_B);
		Caravans.Data data = trade(helper);
		try {
			route(helper, data);
			sendRound(helper, data);
			List<Caravans.Shipment> out = toB(helper, data);
			helper.assertTrue(out.size() == 1 && out.get(0).byWaystone(), "setup: the caravan should go by waystone: " + out);
			breakHall(helper, HALL_B);
			helper.assertTrue(toB(helper, data).isEmpty(), "a caravan still goes to hall-less Brookmere: " + toB(helper, data));
			List<Caravans.Shipment> home = data.onTheRoad().stream().filter(s -> s.from().equals(b) && s.to().equals(a)).toList();
			helper.assertTrue(home.size() == 1, "the caravan didn't turn home: " + data.onTheRoad());
			helper.assertFalse(home.get(0).byWaystone(), "the caravan turned home by waystone through hall-less Brookmere's stone");
			int logs = home.get(0).goods().stream().filter(g -> g.is(Items.OAK_LOG)).mapToInt(ItemStack::getCount).sum();
			helper.assertTrue(logs == 16, "the caravan turned home with " + logs + " logs, not its 16: " + home.get(0).goods());
			sendRound(helper, data);
			helper.assertTrue(toB(helper, data).isEmpty(), "the next round sent a caravan to hall-less Brookmere: " + toB(helper, data));
			helper.assertTrue(data.onTheRoad().stream().filter(s -> s.from().equals(b) || s.to().equals(b)).noneMatch(Caravans.Shipment::byWaystone),
				"a caravan to or from hall-less Brookmere goes by waystone: " + data.onTheRoad());
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	/**
	 * The network file across a save and load: saved after the hall broke, Brookmere isn't in it. Saved before (an older
	 * save, or a world stopped before the fix) and loaded with the hall gone, Brookmere is in the file but stays off the
	 * network: not linked, not among the villages, no settlers through it; Ashford is untouched.
	 */
	//$ gametest_ticks_batch WAY '100' '"qaHallGoneSaved1111d"'
	@GameTest(template = WAY, timeoutTicks = 100, batch = "qaHallGoneSaved1111d")
	public void theNetworkFileSavedAndLoadedKeepsAHallLessVillageOff(GameTestHelper helper) {
		villages(helper, false);
		ServerLevel level = helper.getLevel();
		BlockPos a = abs(helper, HALL_A);
		BlockPos b = abs(helper, HALL_B);
		var registries = level.registryAccess();
		VillageWaystones.Data original = VillageWaystones.Data.get(level);
		try {
			CompoundTag before = original.save(new CompoundTag(), registries);
			breakHall(helper, HALL_B);
			CompoundTag after = VillageWaystones.Data.get(level).save(new CompoundTag(), registries);
			VillageWaystones.Data reloaded = VillageWaystones.Data.load(after, registries);
			helper.assertTrue(reloaded.stone(b).isEmpty(), "saved after the hall broke, the file still holds Brookmere");
			helper.assertTrue(reloaded.stone(a).equals(Optional.of(stone(helper, PLAZA_A))), "saved and loaded, Ashford's stone is " + reloaded.stone(a));

			VillageWaystones.Data old = VillageWaystones.Data.load(before, registries);
			helper.assertTrue(old.stone(b).isPresent(), "setup: the older file lacks Brookmere");
			level.getDataStorage().set(NETWORK, old);
			helper.assertTrue(VillageWaystones.Data.get(level) == old, "setup: the loaded file isn't the level's");
			helper.assertFalse(VillageWaystones.linked(level, a, b), "loaded from a file saved before the hall broke, Ashford is linked with Brookmere");
			helper.assertFalse(VillageWaystones.villages(level).contains(b), "loaded from an older file, villages() lists hall-less Brookmere");
			helper.assertTrue(via(helper, beyondB(helper)).isEmpty(), "loaded from an older file, settlers go through Brookmere: " + via(helper, beyondB(helper)));
			helper.assertTrue(VillageWaystones.villages(level).contains(a) && VillageWaystones.standing(level, a), "Ashford fell off the network on the load");
		} finally {
			level.getDataStorage().set(NETWORK, original);
			cleanUp(helper);
		}
		helper.succeed();
	}

	// ---- B1147 tests -------------------------------------------------------------------------------------------------

	private static ItemStack cobble(int n) {
		return new ItemStack(Items.COBBLESTONE, n);
	}

	private static ItemStack emeralds(int n) {
		return new ItemStack(Items.EMERALD, n);
	}

	private static String goodsChanged(int count, String goods, String price) {
		return "This counter now sells " + count + " " + goods + " for " + price + ". Click Confirm again to buy that.";
	}

	private static long paid(long before, ServerPlayer p) {
		return before - CobbleDollarsBank.balance(p);
	}

	/**
	 * Goods and price both changed between Buy and Confirm (16 Cobblestone for 200 to 1 Diamond for 500): Confirm takes
	 * nothing and says "This counter now sells 1 Diamond for 500 CobbleDollars." (the goods sentence with the new price,
	 * never "The price changed"); the row stays gold at 500 and the second Confirm charges 500 for the one diamond.
	 */
	//$ gametest_ticks_batch SHOP '100' '"qaShopBothChanged1111d"'
	@GameTest(template = SHOP, timeoutTicks = 100, batch = "qaShopBothChanged1111d")
	public void goodsAndPriceBothChangedSayTheNewGoodsAtTheNewPrice(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = QaJobShopLists1109cCompatTests.shop(helper, owner);
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(2));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, cobble(32));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(1, new ItemStack(Items.DIAMOND, 3));
		List<String> seen = new ArrayList<>();
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(buyer, 2000);
		long before = CobbleDollarsBank.balance(buyer);
		long ownerBefore = CobbleDollarsBank.balance(owner);
		ChoiceMenu menu = QaJobShopLists1109cCompatTests.open(helper, buyer, keeper, false);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.DIAMOND, 1), emeralds(5));
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(paid(before, buyer) == 0 && buyer.getInventory().countItem(Items.DIAMOND) == 0 && buyer.getInventory().countItem(Items.COBBLESTONE) == 0,
			"Confirm on a row whose goods and price both changed took " + paid(before, buyer) + " and gave diamonds "
				+ buyer.getInventory().countItem(Items.DIAMOND) + ", cobblestone " + buyer.getInventory().countItem(Items.COBBLESTONE));
		helper.assertTrue(seen.contains(goodsChanged(1, "Diamond", "500 CobbleDollars")), "the buyer read: " + seen);
		helper.assertTrue(seen.stream().noneMatch(s -> s.startsWith("The price changed")), "goods and price changed, the buyer read: " + seen);
		helper.assertTrue(QaJobShopLists1109cCompatTests.row(helper, menu, SLOT).on()
			&& QaJobShopLists1109cCompatTests.row(helper, menu, SLOT).price().getString().equals("500 CobbleDollars"),
			"the row after the refusal: " + QaJobShopLists1109cCompatTests.row(helper, menu, SLOT).price().getString());
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(paid(before, buyer) == 500 && buyer.getInventory().countItem(Items.DIAMOND) == 1 && buyer.getInventory().countItem(Items.COBBLESTONE) == 0,
			"second Confirm: paid " + paid(before, buyer) + ", diamonds " + buyer.getInventory().countItem(Items.DIAMOND));
		helper.assertTrue(CobbleDollarsBank.balance(owner) - ownerBefore == 500, "the owner got " + (CobbleDollarsBank.balance(owner) - ownerBefore));
		helper.assertTrue(QaJobShopLists1109cCompatTests.chest(helper).countItem(Items.COBBLESTONE) == 32
			&& QaJobShopLists1109cCompatTests.chest(helper).countItem(Items.DIAMOND) == 2, "the chest: cobblestone "
			+ QaJobShopLists1109cCompatTests.chest(helper).countItem(Items.COBBLESTONE) + ", diamonds " + QaJobShopLists1109cCompatTests.chest(helper).countItem(Items.DIAMOND));
		helper.succeed();
	}

	/**
	 * A swap the buyer already sees: goods swapped and the screen redrawn (a server tick), Confirm buys the diamond at once,
	 * with no changed sentence. A swap put back before Confirm (cobblestone to diamond to cobblestone, no redraw): the row
	 * showed 16 Cobblestone for 200 all along, so Confirm buys that at once, no sentence either.
	 */
	//$ gametest_ticks_batch SHOP '100' '"qaShopSwapSeen1111d"'
	@GameTest(template = SHOP, timeoutTicks = 100, batch = "qaShopSwapSeen1111d")
	public void aSwapAlreadyOnScreenOrPutBackBuysAtOnce(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = QaJobShopLists1109cCompatTests.shop(helper, owner);
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(2));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, cobble(32));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(1, new ItemStack(Items.DIAMOND, 3));
		List<String> seen = new ArrayList<>();
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(buyer, 2000);
		long before = CobbleDollarsBank.balance(buyer);
		ChoiceMenu menu = QaJobShopLists1109cCompatTests.open(helper, buyer, keeper, false);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.DIAMOND, 1), emeralds(2));
		buyer.containerMenu.broadcastChanges(); // the server's tick redraws the open screen
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(paid(before, buyer) == 200 && buyer.getInventory().countItem(Items.DIAMOND) == 1 && buyer.getInventory().countItem(Items.COBBLESTONE) == 0,
			"the redrawn screen showed 1 Diamond for 200; Confirm: paid " + paid(before, buyer) + ", diamonds " + buyer.getInventory().countItem(Items.DIAMOND)
				+ ", cobblestone " + buyer.getInventory().countItem(Items.COBBLESTONE) + "; read " + seen);
		helper.assertTrue(seen.stream().noneMatch(s -> s.startsWith("This counter now sells") || s.startsWith("The price changed")),
			"a swap already on screen still got a changed sentence: " + seen);

		// put back before Confirm, the screen never redrawn
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(2));
		buyer.containerMenu.broadcastChanges();
		long mid = CobbleDollarsBank.balance(buyer);
		int lines = seen.size();
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.DIAMOND, 1), emeralds(2));
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(2));
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(paid(mid, buyer) == 200 && buyer.getInventory().countItem(Items.COBBLESTONE) == 16 && buyer.getInventory().countItem(Items.DIAMOND) == 1,
			"swapped and put back: paid " + paid(mid, buyer) + ", cobblestone " + buyer.getInventory().countItem(Items.COBBLESTONE)
				+ ", diamonds " + buyer.getInventory().countItem(Items.DIAMOND) + "; read " + seen);
		helper.assertTrue(seen.subList(lines, seen.size()).stream().noneMatch(s -> s.startsWith("This counter now sells") || s.startsWith("The price changed")),
			"a swap put back got a changed sentence: " + seen.subList(lines, seen.size()));
		helper.succeed();
	}

	/**
	 * Goods swapped to something the chest doesn't hold (16 Cobblestone to 4 Iron Ingot, same 200): Confirm takes nothing
	 * and says "Iron Ingot is sold out." (B1189; before it, "This counter now sells ... Click Confirm again"); a second click on the row's slot still takes
	 * nothing; the chest keeps its cobblestone, the owner gets nothing. (What the buyer is told then is
	 * QaHallGoneShopVerify1111dBugCompatTests'.)
	 */
	//$ gametest_ticks_batch SHOP '100' '"qaShopSwapNoStock1111d"'
	@GameTest(template = SHOP, timeoutTicks = 100, batch = "qaShopSwapNoStock1111d")
	public void goodsSwappedToSomethingOutOfStockTakeNothing(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = QaJobShopLists1109cCompatTests.shop(helper, owner);
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(2));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, cobble(32));
		List<String> seen = new ArrayList<>();
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(buyer, 2000);
		long before = CobbleDollarsBank.balance(buyer);
		long ownerBefore = CobbleDollarsBank.balance(owner);
		ChoiceMenu menu = QaJobShopLists1109cCompatTests.open(helper, buyer, keeper, false);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.IRON_INGOT, 4), emeralds(2));
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(paid(before, buyer) == 0, "Confirm on the swapped lot took " + paid(before, buyer));
		// B1189: no stock of the new goods, so the row is gone: "sold out", never "Click Confirm again".
		helper.assertTrue(seen.contains("Iron Ingot is sold out.") && !seen.contains(goodsChanged(4, "Iron Ingot", "200 CobbleDollars")),
			"the buyer read: " + seen);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(paid(before, buyer) == 0 && buyer.getInventory().countItem(Items.IRON_INGOT) == 0 && buyer.getInventory().countItem(Items.COBBLESTONE) == 0,
			"second Confirm on goods not in stock: paid " + paid(before, buyer) + ", iron " + buyer.getInventory().countItem(Items.IRON_INGOT)
				+ ", cobblestone " + buyer.getInventory().countItem(Items.COBBLESTONE));
		helper.assertTrue(QaJobShopLists1109cCompatTests.chest(helper).countItem(Items.COBBLESTONE) == 32, "the chest lost cobblestone");
		helper.assertTrue(CobbleDollarsBank.balance(owner) == ownerBefore, "the owner was paid " + (CobbleDollarsBank.balance(owner) - ownerBefore));
		helper.succeed();
	}
}
