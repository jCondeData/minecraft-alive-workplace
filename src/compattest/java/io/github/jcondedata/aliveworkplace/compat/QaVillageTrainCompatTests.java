package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA for ROADMAP 39.7a-c (real Create trains between villages), written from the roadmap's spec with the real Create:
 * a road that turns and steps up at the same spot, and a diagonal too long for one curve, each still one Create network
 * a train runs end to end on; the builder's train with a part missing (it waits, a block it put up broken meanwhile,
 * and finishes once the part is brought); goods in the carriage's chest when the other village's storehouse is full
 * (none lost, none doubled, in once there's room); the caravans' list really saved and loaded again (swapped in, as on
 * a restart) while the goods sit in the chest at the station (they arrive once); a caravan each way on one train at
 * once; and every sentence the trains put in a chronicle, read with real names and one of a thing.
 */
public class QaVillageTrainCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final int TRACK_Z = 12;
	private static final BlockPos STATION_A = new BlockPos(6, 2, TRACK_Z + 1);
	private static final BlockPos STATION_B = new BlockPos(23, 2, TRACK_Z + 1);
	private static final BlockPos HALL_A = new BlockPos(6, 2, 22);
	private static final BlockPos HALL_B = new BlockPos(23, 2, 22);
	/** The caravans' list's name in the level's saved data (Caravans.Data). */
	private static final String DATA_NAME = "aliveworkplace_caravans";

	// ---- 39.7c: track Create takes as one line on roads that turn and step, and over a long diagonal --------------

	/**
	 * A road east along z = 3 that turns south at x = 16 and steps up a block right at the turn (the south leg on a bank
	 * one block higher): the track laid as the village's builder lays it joins one Create network, and the drawn train
	 * runs from the station on the east leg to the one on the south leg and back.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaTrainTurnStep"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaTrainTurnStep")
	public void aStepOnATurnStillJoinsOneNetworkAndTheTrainRuns(GameTestHelper helper) {
		ground(helper, (x, z) -> x >= 13 && x <= 19 && z >= 4 ? 2 : 1);
		List<BlockPos> way = new ArrayList<>();
		for (int x = 1; x <= 16; x++) {
			way.add(new BlockPos(x, 2, 3));
		}
		for (int z = 4; z <= 28; z++) {
			way.add(new BlockPos(16, 3, z)); // south from the turn, a block up at once
		}
		CaravanTrains.TrackPlan plan = lay(helper, way);
		helper.assertTrue(!plan.curves().isEmpty(), "no curve where the road turns and steps: " + plan.curves());
		helper.setBlock(new BlockPos(4, 2, 4), state("track_station"));
		helper.setBlock(new BlockPos(17, 3, 24), state("track_station"));
		runs(helper, new BlockPos(4, 2, 4), new BlockPos(17, 3, 24), new BlockPos(4, 2, 7), "round a stepped turn");
	}

	/**
	 * A diagonal road (single steps east and south, the road planner's way) fifteen blocks each way: too long for one
	 * curve, so the track is split into several joined curves; Create still takes it as one line and the train runs
	 * from end to end and back.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaTrainLongDiagonal"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaTrainLongDiagonal")
	public void aLongDiagonalIsSplitIntoSeveralCurvesAndTheTrainRuns(GameTestHelper helper) {
		ground(helper, (x, z) -> 1);
		List<BlockPos> way = new ArrayList<>();
		for (int x = 1; x <= 7; x++) {
			way.add(new BlockPos(x, 2, 2));
		}
		int x = 7;
		int z = 2;
		while (z < 17) {
			way.add(new BlockPos(++x, 2, z));
			way.add(new BlockPos(x, 2, ++z));
		}
		for (x = 23; x <= 28; x++) {
			way.add(new BlockPos(x, 2, 17));
		}
		CaravanTrains.TrackPlan plan = lay(helper, way);
		helper.assertTrue(plan.curves().size() >= 2, "a diagonal longer than one curve isn't split: " + plan.curves());
		for (int i = 0; i + 1 < plan.curves().size(); i++) {
			helper.assertTrue(plan.curves().get(i).b().equals(plan.curves().get(i + 1).a()) || plan.curves().get(i + 1).b().equals(plan.curves().get(i).a()),
				"the curves over the diagonal don't meet on a piece: " + plan.curves());
		}
		helper.setBlock(new BlockPos(3, 2, 3), state("track_station"));
		helper.setBlock(new BlockPos(25, 2, 18), state("track_station"));
		runs(helper, new BlockPos(3, 2, 3), new BlockPos(25, 2, 18), new BlockPos(3, 2, 6), "over a long diagonal");
	}

	// ---- 39.7b: the builder's train with a part missing, a block of it broken -----------------------------------

	/**
	 * The bench's barrel holds every part of the drawn train but the lantern (and a spare of the rest): the builder puts
	 * up what it can, the site waits for the lantern, and no train is assembled. A block of the train the builder put up
	 * is broken meanwhile. Once a lantern is in the barrel, the train is finished, the chronicle says the builder built it,
	 * and the station assembles it.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaTrainMissingPart"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaTrainMissingPart")
	public void aTrainMissingAPartWaitsThenFinishesWhenItIsBrought(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		line(helper);
		Villages v = villages(helper);
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(14, 2, 20);
		BlockPos barrelAt = new BlockPos(13, 2, 20);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(barrelAt, Blocks.BARREL);
		Container barrel = helper.getBlockEntity(barrelAt);
		barrel.setItem(0, new ItemStack(createItem("railway_casing"), 4));
		barrel.setItem(1, new ItemStack(createItem("controls"), 3));
		barrel.setItem(2, new ItemStack(createItem("brown_seat"), 2));
		barrel.setItem(3, new ItemStack(Items.CHEST, 2));
		barrel.setItem(4, new ItemStack(Items.SPRUCE_FENCE, 4));
		barrel.setItem(5, new ItemStack(Items.SPRUCE_SLAB, 5));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 21));
		Builders.employ(level, builder, helper.absolutePos(bench));
		conductor(helper, new BlockPos(6, 2, 16));
		BlockPos first = CaravanTrains.first(v.a(), v.b()) ? v.a() : v.b();
		BlockPos other = first.equals(v.a()) ? v.b() : v.a();
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		int[] stage = {0}; // 0 waiting for a block put up, 1 waiting for the site to wait on the lantern, 2 lantern brought
		int[] tick = {0};
		int[] since = {0};
		BlockPos[] broken = {null};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				CaravanTrains.serve(level, v.a(), hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), hall(level, v.b()));
			}
			BuildSite site = Roads.openSegments(level, first).stream().filter(st -> CaravanTrains.isTrainSite(st.structure())).findFirst().orElse(null);
			if (stage[0] < 2 && CaravanTrains.realTrain(level, v.a(), v.b())) {
				helper.fail("a train was assembled without its lantern (stage " + stage[0] + ")");
			}
			if (stage[0] == 0) {
				List<BlockPos> ours = v.data().stations(first);
				List<BlockPos> theirs = v.data().stations(other);
				if (site != null && !ours.isEmpty() && !theirs.isEmpty()) {
					Map<BlockPos, BlockState> parts = railway().train(level, ours.get(0), theirs.get(0));
					if (parts != null) {
						for (Map.Entry<BlockPos, BlockState> e : parts.entrySet()) {
							if (e.getValue().is(Blocks.SPRUCE_FENCE) && level.getBlockState(e.getKey()).is(Blocks.SPRUCE_FENCE)) {
								level.setBlockAndUpdate(e.getKey(), Blocks.AIR.defaultBlockState()); // a player breaks a post the builder put up
								broken[0] = e.getKey();
								stage[0] = 1;
								since[0] = tick[0];
								break;
							}
						}
					}
				}
				if (stage[0] == 0 && tick[0] == 3000) {
					helper.fail("the builder put up no post of the train in 3000 ticks: site " + describe(site));
				}
				return;
			}
			if (stage[0] == 1) {
				if (site != null && site.missing().containsKey(Items.LANTERN) && tick[0] - since[0] >= 100) {
					barrel.setItem(6, new ItemStack(Items.LANTERN, 1));
					stage[0] = 2;
					since[0] = tick[0];
				} else if (tick[0] - since[0] > 2000) {
					helper.fail("the train site doesn't wait for the lantern: site " + describe(site));
				}
				return;
			}
			if (CaravanTrains.realTrain(level, v.a(), v.b())) {
				helper.assertTrue(said(level, first, "chronicle.aliveworkplace.train_built"), "the chronicle doesn't say a builder built the train");
				helper.assertTrue(said(level, first, "chronicle.aliveworkplace.train_assembled"), "the chronicle doesn't say the train was put on the rails");
				railway().scrap(level, owner);
				tidy(level, v);
				helper.succeed();
			} else if (tick[0] - since[0] > 2600) {
				helper.fail("the train wasn't finished after its lantern was brought (post broken at " + broken[0] + ", now "
					+ level.getBlockState(broken[0]) + "): site " + describe(site) + ", built " + railway().built(level, v.data().stations(first).get(0), v.data().stations(other).get(0)));
			}
		});
	}

	private static String describe(BuildSite site) {
		return site == null ? "none" : site.stage() + " " + site.status() + " missing " + site.missing();
	}

	// ---- 39.7b: the carriage's chest, the storehouse full, a save and load, a caravan each way -------------------

	/**
	 * The logs come in at Bramble in the carriage's chest while every storehouse chest in reach is full: they come out of
	 * the carriage's chest and wait on the road (none lost, none left in the train), then go in once (and only once) when
	 * there's room.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaTrainStoreFull"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaTrainStoreFull")
	public void goodsInTheChestWithTheStorehouseFullAreNeitherLostNorDoubled(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		line(helper);
		Villages v = villages(helper);
		conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		Container chestA = (Container) level.getBlockEntity(v.a().east(3).north(2));
		Container chestB = (Container) level.getBlockEntity(v.b().east(3).north(2));
		int[] stage = {0}; // 0 no train, 1 sent, 2 in the chest seen, 3 came in to full chests, 4 room made
		int[] tick = {0};
		int[] since = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				buildTrain(level, v);
				CaravanTrains.serve(level, v.a(), hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), hall(level, v.b()));
			}
			if (tick[0] == 2300) {
				helper.fail("stuck at stage " + stage[0] + ": road " + road(v) + ", train " + railway().status(level, owner) + ", chest " + railway().cargo(level, owner));
			}
			if (stage[0] == 0) {
				if (CaravanTrains.realTrain(level, v.a(), v.b())) {
					sendAgain(level, v);
					helper.assertTrue(chestA.countItem(Items.OAK_LOG) == 32 && toB(v) != null && logs(toB(v).goods()) == 32,
						"Ashford didn't send 32 logs: " + chestA.countItem(Items.OAK_LOG) + " left, road " + road(v));
					chestA.clearContent(); // only the caravan's 32 logs in play from here
					fill(chestA); // both chests are in each hall's reach: nowhere for the logs to go
					fill(chestB);
					stage[0] = 1;
				} else if (tick[0] > 600) {
					helper.fail("no village train was put together in 600 ticks");
				}
				return;
			}
			Caravans.Shipment s = toB(v);
			if (stage[0] == 1) {
				if (s != null && s.arrives() == CaravanTrains.IN_CHEST) {
					helper.assertTrue(logs(railway().cargo(level, owner)) == 32, "the logs aren't in the carriage's chest: " + railway().cargo(level, owner));
					stage[0] = 2;
				}
				return;
			}
			int inChests = chestA.countItem(Items.OAK_LOG) + chestB.countItem(Items.OAK_LOG);
			int onRoad = road(v).stream().mapToInt(x -> logs(x.goods())).sum();
			int inTrain = logs(railway().cargo(level, owner));
			// while the caravan rides with its goods in the chest, its record lists them: counted once, from the record
			int counted = inChests + onRoad + (s != null && s.arrives() == CaravanTrains.IN_CHEST ? 0 : inTrain);
			helper.assertTrue(counted == 32, "logs lost or doubled: " + inChests + " in the chests, " + onRoad + " on the road, "
				+ inTrain + " in the train (of the caravan's 32), stage " + stage[0] + ", road " + road(v));
			if (stage[0] == 2) {
				if (s != null && !CaravanTrains.waiting(s.arrives())) {
					// came in at a full storehouse: out of the carriage's chest, waiting on the road for room
					helper.assertTrue(inTrain == 0, "logs stayed in the carriage's chest at full storehouses: " + railway().cargo(level, owner));
					helper.assertTrue(logs(s.goods()) == 32 && chestB.countItem(Items.OAK_LOG) == 0, "the logs went somewhere with no room: " + s);
					stage[0] = 3;
					since[0] = tick[0];
				}
				return;
			}
			if (stage[0] == 3) {
				if (tick[0] - since[0] < 60) {
					return; // a few rounds with no room: they keep waiting
				}
				helper.assertTrue(chestB.countItem(Items.OAK_LOG) == 0 && onRoad == 32, "the waiting logs changed with no room: " + road(v));
				for (int i = 0; i < 3; i++) {
					chestB.setItem(i, ItemStack.EMPTY); // Bramble makes room
				}
				v.data().hurry(v.b());
				Caravans.round(level, v.b(), null);
				stage[0] = 4;
				since[0] = tick[0];
				return;
			}
			if (tick[0] - since[0] < 200) {
				return; // the train keeps running and the halls keep looking: nothing may come twice
			}
			helper.assertTrue(chestB.countItem(Items.OAK_LOG) == 32, "Bramble didn't get the 32 logs once there was room: " + chestB.countItem(Items.OAK_LOG)
				+ ", road " + road(v));
			helper.assertTrue(onRoad == 0 && inTrain == 0, "logs left on the road or in the train: " + road(v) + " / " + railway().cargo(level, owner));
			railway().scrap(level, owner);
			tidy(level, v);
			helper.succeed();
		});
	}

	/** Every empty slot of {@code chest} filled with cobblestone. */
	private static void fill(Container chest) {
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).isEmpty()) {
				chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			}
		}
	}

	/**
	 * The caravans' list written out and read back in place of the one in memory (as a server restart does) right after
	 * the logs went into the carriage's chest at the station: the caravan still rides with them in the chest, and they
	 * come in at Bramble exactly once (not again from the caravan's record, not left in the chest).
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaTrainReloadInChest"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaTrainReloadInChest")
	public void theListSavedAndLoadedWithTheGoodsInTheChestTheyArriveOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		line(helper);
		Villages v = villages(helper);
		conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		int[] stage = {0}; // 0 no train, 1 sent, 2 reloaded, 3 arrived
		int[] tick = {0};
		int[] since = {0};
		long[] before = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				buildTrain(level, v);
				CaravanTrains.serve(level, v.a(), hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), hall(level, v.b()));
			}
			if (tick[0] == 2300) {
				helper.fail("stuck at stage " + stage[0] + ": road " + road(v) + ", train " + railway().status(level, owner) + ", chest " + railway().cargo(level, owner));
			}
			if (stage[0] == 0) {
				if (CaravanTrains.realTrain(level, v.a(), v.b())) {
					before[0] = came(level, v.b()); // a timed trip before the train was there may have come in already
					sendAgain(level, v);
					stage[0] = 1;
				} else if (tick[0] > 600) {
					helper.fail("no village train was put together in 600 ticks");
				}
				return;
			}
			Caravans.Shipment s = toB(v);
			if (stage[0] == 1) {
				if (s != null && s.arrives() == CaravanTrains.IN_CHEST) {
					Caravans.Data loaded = Caravans.Data.load(v.data().save(new CompoundTag(), level.registryAccess()), level.registryAccess());
					level.getDataStorage().set(DATA_NAME, loaded);
					helper.assertTrue(Caravans.Data.get(level) == loaded, "the list read back isn't the one in use");
					Caravans.Shipment back = toB(v);
					helper.assertTrue(back != null && back.byTrain() && back.arrives() == CaravanTrains.IN_CHEST && logs(back.goods()) == 32,
						"the caravan with its goods in the chest changed over the save: " + back);
					helper.assertTrue(logs(railway().cargo(level, owner)) == 32, "the carriage's chest doesn't hold the logs: " + railway().cargo(level, owner));
					stage[0] = 2;
				}
				return;
			}
			int total = chestLogs(level, v) + v.data().onTheRoad().stream().filter(x -> x.from().equals(v.a()) || x.to().equals(v.a()))
				.mapToInt(x -> logs(x.goods())).sum();
			int inTrain = logs(railway().cargo(level, owner));
			// while aboard, the caravan's record lists the goods in the chest: counted once, from the record
			int counted = s != null && s.arrives() == CaravanTrains.IN_CHEST ? total : total + inTrain;
			helper.assertTrue(counted == 64, "logs lost or doubled after the save: " + total + " in the chests and on the road, " + inTrain + " in the train");
			if (stage[0] == 2) {
				if (s == null && said(level, v.b(), "chronicle.aliveworkplace.train_came")) {
					stage[0] = 3;
					since[0] = tick[0];
				}
				return;
			}
			if (tick[0] - since[0] < 300) {
				return; // the train runs on: the logs mustn't come in a second time
			}
			helper.assertTrue(chestLogs(level, v) == 64 && inTrain == 0 && road(v).isEmpty(),
				"after the save the logs didn't arrive exactly once: " + chestLogs(level, v) + " of 64 in the chests, train " + railway().cargo(level, owner) + ", road " + road(v));
			helper.assertTrue(came(level, v.b()) - before[0] == 1, "Bramble's chronicle says the train came in " + (came(level, v.b()) - before[0]) + " times");
			railway().scrap(level, owner);
			tidy(level, v);
			helper.succeed();
		});
	}

	/**
	 * A caravan each way at once on the one train between Ashford and Bramble: Ashford's 32 logs to Bramble and Bramble's
	 * 16 wheat to Ashford, both loaded into the carriage's chest at their own station and unloaded at the other. Every log
	 * and every wheat is in the chests once at the end, nothing left in the train or on the road, and both chronicles'
	 * train lines read with the villages' names and the goods (no placeholder, no key).
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaTrainBothWays"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaTrainBothWays")
	public void aCaravanEachWayOnOneTrainBothArriveWithTheirGoods(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		line(helper);
		Villages v = villages(helper);
		conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		Container chestA = (Container) level.getBlockEntity(v.a().east(3).north(2));
		Container chestB = (Container) level.getBlockEntity(v.b().east(3).north(2));
		boolean[] sent = {false};
		boolean[] logsInChest = {false};
		boolean[] wheatInChest = {false};
		long[] before = {0, 0};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				buildTrain(level, v);
				CaravanTrains.serve(level, v.a(), hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), hall(level, v.b()));
			}
			if (!sent[0]) {
				if (CaravanTrains.realTrain(level, v.a(), v.b())) {
					Caravans.Data data = v.data();
					before[0] = came(level, v.a());
					before[1] = came(level, v.b());
					data.clearRoad(v.a());
					chestA.clearContent();
					chestB.clearContent();
					chestA.setItem(0, new ItemStack(Items.OAK_LOG, 64));
					chestB.setItem(0, new ItemStack(Items.WHEAT, 64));
					data.setWants(v.a(), Component.literal("Ashford"), List.of(new Caravans.Want(Items.WHEAT, 16)));
					data.setWants(v.b(), Component.literal("Bramble"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
					if (!data.routesFrom(v.b()).contains(v.a())) {
						data.toggleRoute(v.b(), v.a());
					}
					data.sent(v.a(), -1);
					data.sent(v.b(), -1);
					Caravans.round(level, v.a(), null);
					Caravans.round(level, v.b(), null);
					Caravans.Shipment logs = toB(v);
					Caravans.Shipment wheat = toA(v);
					helper.assertTrue(logs != null && wheat != null && CaravanTrains.waiting(logs.arrives()) && CaravanTrains.waiting(wheat.arrives()),
						"not a caravan each way waiting for the train: " + road(v));
					sent[0] = true;
				} else if (tick[0] > 600) {
					helper.fail("no village train was put together in 600 ticks");
				}
				return;
			}
			Caravans.Shipment logs = toB(v);
			Caravans.Shipment wheat = toA(v);
			if (logs != null && logs.arrives() == CaravanTrains.IN_CHEST) {
				logsInChest[0] = true;
			}
			if (wheat != null && wheat.arrives() == CaravanTrains.IN_CHEST) {
				wheatInChest[0] = true;
			}
			int logCount = chestA.countItem(Items.OAK_LOG) + chestB.countItem(Items.OAK_LOG) + road(v).stream().mapToInt(x -> logs(x.goods())).sum()
				+ (logs != null && logs.arrives() == CaravanTrains.IN_CHEST ? 0 : count(railway().cargo(level, owner), Items.OAK_LOG));
			int wheatCount = chestA.countItem(Items.WHEAT) + chestB.countItem(Items.WHEAT) + road(v).stream().mapToInt(x -> count(x.goods(), Items.WHEAT)).sum()
				+ (wheat != null && wheat.arrives() == CaravanTrains.IN_CHEST ? 0 : count(railway().cargo(level, owner), Items.WHEAT));
			helper.assertTrue(logCount == 64 && wheatCount == 64, "goods lost or doubled with a caravan each way: " + logCount + " logs, " + wheatCount
				+ " wheat (of 64 each); road " + road(v) + ", train " + railway().cargo(level, owner));
			if (tick[0] == 3500) {
				helper.fail("not both caravans came in: road " + road(v) + ", train " + railway().status(level, owner) + ", chest " + railway().cargo(level, owner));
			}
			if (logs == null && wheat == null && road(v).isEmpty()) {
				helper.assertTrue(logsInChest[0] && wheatInChest[0], "a caravan arrived without riding in the carriage's chest: logs " + logsInChest[0] + ", wheat " + wheatInChest[0]);
				helper.assertTrue(railway().cargo(level, owner).isEmpty(), "goods left in the carriage's chest: " + railway().cargo(level, owner));
				helper.assertTrue(came(level, v.a()) - before[0] == 1 && came(level, v.b()) - before[1] == 1, "each chronicle should say once that the train came in: Ashford "
					+ (came(level, v.a()) - before[0]) + ", Bramble " + (came(level, v.b()) - before[1]));
				for (BlockPos h : List.of(v.a(), v.b())) {
					for (Chronicle.Entry e : hall(level, h).chronicle()) {
						if (e.kind() != Chronicle.Kind.CARAVAN || !(e.text().getContents() instanceof TranslatableContents t) || !t.getKey().contains("train")) {
							continue;
						}
						String text = e.text().getString();
						// the villages' names here are the halls' own (the list's names are only for the caravans' wants)
						helper.assertTrue(!text.contains("%") && !text.contains("aliveworkplace.") && !text.isBlank()
							&& t.getArgs().length > 0 && java.util.Arrays.stream(t.getArgs()).allMatch(a -> !(a instanceof Component c) || !c.getString().isBlank()),
							"a train line in the chronicle doesn't read right: \"" + text + "\" (" + t.getKey() + ")");
					}
				}
				railway().scrap(level, owner);
				tidy(level, v);
				helper.succeed();
			}
		});
	}

	/**
	 * Every sentence the village trains put before a player, read with real arguments: the villages' names, a builder's
	 * and a Conductor's name, and one of a thing ("1 × Oak Log", never a plural or a leftover placeholder); and the
	 * Create train's own name.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyTrainSentenceReadsWithRealNamesAndOneOfAThing(GameTestHelper helper) {
		Language en = Language.getInstance();
		Component ashford = Component.literal("Ashford");
		Component bramble = Component.literal("Bramble");
		Component one = Caravans.describe(List.of(new ItemStack(Items.OAK_LOG, 1)));
		helper.assertTrue(one.getString().equals("1 × Oak Log"), "one log reads \"" + one.getString() + "\"");
		Component two = Caravans.describe(List.of(new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.WHEAT, 1)));
		helper.assertTrue(two.getString().equals("32 × Oak Log, 1 × Wheat"), "goods read \"" + two.getString() + "\"");
		Component someone = Component.translatable("steward.aliveworkplace.jobs.someone");
		List<Component> lines = List.of(
			Component.translatable("chronicle.aliveworkplace.train_left", bramble, one),
			Component.translatable("chronicle.aliveworkplace.train_came", ashford, one),
			Component.translatable("chronicle.aliveworkplace.train_walked", ashford),
			Component.translatable("chronicle.aliveworkplace.train_assembled", Component.literal("Jem"), bramble),
			Component.translatable("chronicle.aliveworkplace.train_track_laid", Component.literal("Bob"), bramble),
			Component.translatable("chronicle.aliveworkplace.train_built", Component.literal("Bob")),
			Component.translatable("chronicle.aliveworkplace.train_built", someone),
			Component.translatable("chronicle.aliveworkplace.train_goods_left", bramble),
			Component.translatable("chronicle.aliveworkplace.railway_open", bramble),
			Component.translatable("train.aliveworkplace.caravan", "Ashford", "Bramble"),
			Component.translatable("message.aliveworkplace.conductor.arriving", bramble),
			Component.translatable("blueprint.aliveworkplace.village_train"),
			Component.translatable("blueprint.aliveworkplace.train_station"));
		for (Component line : lines) {
			String key = ((TranslatableContents) line.getContents()).getKey();
			helper.assertTrue(en.has(key), key + " has no text");
			String text = line.getString();
			helper.assertTrue(!text.contains("%") && !text.contains("aliveworkplace") && !text.isBlank() && !text.contains("  "),
				key + " reads \"" + text + "\"");
			helper.assertTrue(text.equals(text.strip()), key + " has stray spaces: \"" + text + "\"");
		}
		helper.assertTrue(lines.get(0).getString().contains("Bramble") && lines.get(0).getString().contains("1 × Oak Log"),
			"the train leaving doesn't name where and what: " + lines.get(0).getString());
		helper.assertTrue(lines.get(6).getString().contains("a villager"), "a train built by nobody known doesn't say who: " + lines.get(6).getString());
		helper.assertTrue(lines.get(9).getString().contains("Ashford") && lines.get(9).getString().contains("Bramble"), "the train's name: " + lines.get(9).getString());
		helper.succeed();
	}

	// ---- shared: the track tests (as CreateTrackCompatTests lays them) --------------------------------------------

	private static BlockState state(String path) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path)).defaultBlockState();
	}

	private static Item createItem(String path) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", path));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BlockState shaped(String shape) {
		BlockState track = state("track");
		net.minecraft.world.level.block.state.properties.Property p = track.getBlock().getStateDefinition().getProperty("shape");
		return track.setValue(p, (Comparable) p.getValue(shape).orElseThrow());
	}

	private static CaravanTrains.Railway railway() {
		return CaravanTrains.RAILWAY.call(r -> r, null);
	}

	private static void ground(GameTestHelper helper, java.util.function.BiFunction<Integer, Integer, Integer> top) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				int t = top.apply(x, z);
				for (int y = 1; y < 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), y <= t ? Blocks.STONE : Blocks.AIR);
				}
			}
		}
	}

	private static CaravanTrains.TrackPlan lay(GameTestHelper helper, List<BlockPos> way) {
		List<BlockPos> route = way.stream().map(helper::absolutePos).toList();
		CaravanTrains.TrackPlan plan = CaravanTrains.plan(route, p -> true);
		plan.shapes().forEach((pos, shape) -> helper.getLevel().setBlockAndUpdate(pos, shaped(shape)));
		for (CaravanTrains.Curve curve : plan.curves()) {
			helper.assertTrue(railway().curve(helper.getLevel(), curve.a(), curve.outA(), curve.b(), curve.outB()), "Create's curve didn't join " + curve);
		}
		return plan;
	}

	private static void runs(GameTestHelper helper, BlockPos stationA, BlockPos stationB, BlockPos driverAt, String what) {
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(stationA);
		BlockPos b = helper.absolutePos(stationB);
		UUID owner = UUID.randomUUID();
		Villager driver = EntityType.VILLAGER.spawn(level, helper.absolutePos(driverAt), MobSpawnType.COMMAND);
		driver.setNoAi(true);
		int[] stage = {0};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			CaravanTrains.Railway r = railway();
			if (stage[0] == 0) {
				boolean aimed = r.aim(level, a, b, "Alpha " + what) & r.aim(level, b, a, "Beta " + what);
				if (aimed && r.linked(level, a, b)) {
					Map<BlockPos, BlockState> parts = r.train(level, a, b);
					helper.assertTrue(parts != null && parts.size() == CaravanTrains.trainBlocks(level).size(), "no room for the drawn train past the station: " + parts);
					parts.forEach((pos, st) -> level.setBlockAndUpdate(pos, st));
					helper.assertTrue(r.assemble(level, a, b, owner, driver) != null, "the station didn't assemble the drawn train (" + what + ")");
					stage[0] = 1;
				} else if (tick[0] > 200) {
					helper.fail("Create's railway doesn't join the two stations " + what + " (aimed " + aimed + ", linked " + r.linked(level, a, b) + ")");
				}
			} else if (tick[0] == 2300) {
				helper.fail("the train didn't run " + what + " to the other station and back (stage " + stage[0] + "): " + r.status(level, owner));
			} else if (stage[0] == 1 && r.at(level, owner, b)) {
				stage[0] = 2;
			} else if (stage[0] == 2 && r.at(level, owner, a)) {
				r.scrap(level, owner);
				helper.succeed();
			}
		});
	}

	// ---- shared: two villages on a straight line of track (as CreateTrainCompatTests sets them up) ---------------

	private static void line(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				for (int y = 2; y < 8; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		BlockState track = shaped("xo");
		for (int x = 1; x <= 28; x++) {
			helper.setBlock(new BlockPos(x, 2, TRACK_Z), track);
		}
		helper.setBlock(STATION_A, state("track_station"));
		helper.setBlock(STATION_B, state("track_station"));
	}

	private static Villager conductor(GameTestHelper helper, BlockPos at) {
		Villager villager = EntityType.VILLAGER.spawn(helper.getLevel(), helper.absolutePos(at), MobSpawnType.COMMAND);
		villager.setNoAi(true);
		return villager;
	}

	private record Villages(BlockPos a, BlockPos b) {
		/** The caravans' list in use now (a test may swap in one read back from a save). */
		Caravans.Data data() {
			return Caravans.Data.get(level);
		}

		private static ServerLevel level;
	}

	private static Villages villages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villages.level = level;
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		Caravans.Data data = Caravans.Data.get(level);
		net.minecraft.world.phys.AABB own = helper.getBounds();
		for (BlockPos hall : List.of(a, b)) {
			for (BlockPos store : level.getPoiManager().findAll(h -> h.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.STOREHOUSE_POI),
					p -> !own.contains(net.minecraft.world.phys.Vec3.atCenterOf(p)), hall, io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS,
					net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
				level.removeBlock(store, false);
			}
		}
		for (BlockPos hall : List.of(a, b)) {
			data.remove(hall);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			level.setBlockAndUpdate(hall.east(3), ModBlocks.STOREHOUSE.defaultBlockState());
			level.setBlockAndUpdate(hall.east(3).north(2), Blocks.CHEST.defaultBlockState());
		}
		((Container) level.getBlockEntity(a.east(3).north(2))).setItem(0, new ItemStack(Items.OAK_LOG, 64));
		data.setWants(a, Component.literal("Ashford"), List.of());
		data.setWants(b, Component.literal("Bramble"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
		data.toggleRoute(a, b);
		BlockPos meet = a.offset(8, 0, 0);
		data.setHalf(a, b, new Caravans.Half(meet, true, false, true));
		data.setHalf(b, a, new Caravans.Half(meet, true, false, true));
		data.setTracked(a, b, true);
		data.setTracked(b, a, true);
		CaravanTrains.noteStations(level, a, data);
		CaravanTrains.noteStations(level, b, data);
		helper.assertTrue(CaravanTrains.rides(data, a, b), "the two villages aren't on a railway: stations " + data.stations(a) + " / " + data.stations(b));
		return new Villages(a, b);
	}

	private static void sendAgain(ServerLevel level, Villages v) {
		v.data().clearRoad(v.a());
		((Container) level.getBlockEntity(v.a().east(3).north(2))).clearContent();
		((Container) level.getBlockEntity(v.a().east(3).north(2))).setItem(0, new ItemStack(Items.OAK_LOG, 64));
		((Container) level.getBlockEntity(v.b().east(3).north(2))).clearContent();
		v.data().setWants(v.a(), Component.literal("Ashford"), List.of());
		v.data().setWants(v.b(), Component.literal("Bramble"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
		v.data().sent(v.a(), -1);
		Caravans.round(level, v.a(), null);
	}

	private static void buildTrain(ServerLevel level, Villages v) {
		BlockPos hall = CaravanTrains.first(v.a(), v.b()) ? v.a() : v.b();
		BlockPos other = hall.equals(v.a()) ? v.b() : v.a();
		List<BlockPos> ours = v.data().stations(hall);
		List<BlockPos> theirs = v.data().stations(other);
		if (ours.isEmpty() || theirs.isEmpty() || CaravanTrains.realTrain(level, v.a(), v.b()) || !railway().linked(level, ours.get(0), theirs.get(0))
			|| railway().built(level, ours.get(0), theirs.get(0))) {
			return;
		}
		Map<BlockPos, BlockState> parts = railway().train(level, ours.get(0), theirs.get(0));
		if (parts != null) {
			parts.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
		}
	}

	private static void tidy(ServerLevel level, Villages v) {
		for (BlockPos hall : List.of(v.a(), v.b())) {
			if (level.getBlockEntity(hall.east(3).north(2)) instanceof Container c) {
				c.clearContent();
			}
			level.removeBlock(hall.east(3).north(2), false);
			level.removeBlock(hall.east(3), false);
			level.removeBlock(hall, false);
			v.data().clearRoad(hall);
			v.data().remove(hall);
		}
	}

	private static Caravans.Shipment toB(Villages v) {
		return v.data().onTheRoad().stream().filter(x -> x.from().equals(v.a()) && x.to().equals(v.b())).findFirst().orElse(null);
	}

	private static Caravans.Shipment toA(Villages v) {
		return v.data().onTheRoad().stream().filter(x -> x.from().equals(v.b()) && x.to().equals(v.a())).findFirst().orElse(null);
	}

	private static List<Caravans.Shipment> road(Villages v) {
		return v.data().onTheRoad().stream().filter(s -> s.to().equals(v.a()) || s.to().equals(v.b()) || s.from().equals(v.a()) || s.from().equals(v.b())).toList();
	}

	private static int logs(List<ItemStack> stacks) {
		return count(stacks, Items.OAK_LOG);
	}

	private static int count(List<ItemStack> stacks, Item item) {
		return stacks.stream().filter(st -> st.is(item)).mapToInt(ItemStack::getCount).sum();
	}

	private static int chestLogs(ServerLevel level, Villages v) {
		return ((Container) level.getBlockEntity(v.a().east(3).north(2))).countItem(Items.OAK_LOG)
			+ ((Container) level.getBlockEntity(v.b().east(3).north(2))).countItem(Items.OAK_LOG);
	}

	private static VillageHallBlockEntity hall(ServerLevel level, BlockPos pos) {
		return (VillageHallBlockEntity) level.getBlockEntity(pos);
	}

	private static boolean said(ServerLevel level, BlockPos hall, String key) {
		return hall(level, hall).chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.CARAVAN
			&& e.text().getContents() instanceof TranslatableContents t && t.getKey().equals(key));
	}

	/** How many times the hall's chronicle says a train came in. */
	private static long came(ServerLevel level, BlockPos hall) {
		return hall(level, hall).chronicle().stream().filter(e -> e.text().getContents() instanceof TranslatableContents t
			&& t.getKey().equals("chronicle.aliveworkplace.train_came")).count();
	}
}
