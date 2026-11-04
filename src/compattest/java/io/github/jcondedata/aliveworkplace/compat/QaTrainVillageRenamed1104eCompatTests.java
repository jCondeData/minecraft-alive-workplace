package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-e-1104-0413), B894 from its spec: stations on one Create network never share a name a village gave them, and
 * the goods reach the village they were sent to. The fix's tests name the villages before the trains run; these rename a
 * village with a name tag on its hall after the Pine-Oakford train runs (the usual way two villages come to share a
 * name): the middle village renamed Oakford like the far one, and the far Oakford renamed Pine like its own partner.
 * Either way the four stations keep four names and Pine's 32 logs reach the far village by train, none lost or doubled.
 * Line and villages: {@link QaTrainSameNames1103eCompatTests}.
 */
public class QaTrainVillageRenamed1104eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	private static String name(GameTestHelper helper, BlockPos station) {
		return QaTrainStationNames1102CompatTests.name(helper.getLevel(), helper.absolutePos(station));
	}

	private static void rename(ServerLevel level, Caravans.Data data, BlockPos hall, String name) {
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setCustomName(Component.literal(name)); // a name tag on the hall
		data.setWants(hall, Component.literal(name), List.of());
	}

	/**
	 * The Pine-Oakford train runs (the middle village Elmwood); then {@code renamed} gets the name {@code to}; after 300
	 * more ticks of rounds Pine sends 32 logs to the far village, which must come in there by train.
	 */
	private static void ride(GameTestHelper helper, java.util.function.Function<QaTrainSameNames1103eCompatTests.Four, BlockPos> renamed, String to) {
		ServerLevel level = helper.getLevel();
		QaTrainSameNames1103eCompatTests.Four v = QaTrainSameNames1103eCompatTests.villages(helper, "Elmwood");
		// as QaTrainHub1103eCompatTests.villages: a Storehouse an earlier batch left outside this area, within a hall's
		// reach, would take the goods that come in (Caravans.storehouse), and the chests here would be short
		net.minecraft.world.phys.AABB area = helper.getBounds();
		for (BlockPos hall : v.halls()) {
			for (BlockPos store : level.getPoiManager().findAll(h -> h.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.STOREHOUSE_POI),
					p -> !area.contains(net.minecraft.world.phys.Vec3.atCenterOf(p)), hall, io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS,
					net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
				level.removeBlock(store, false);
			}
		}
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		CreateTrainCompatTests.conductor(helper, new BlockPos(23, 2, 16));
		UUID train = CaravanTrains.railwayId(v.pine(), v.far());
		int[] tick = {0};
		int[] phase = {0};
		int[] since = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 != 0) {
				return;
			}
			QaTrainSameNames1103eCompatTests.round(level, v);
			switch (phase[0]) {
				case 0 -> {
					if (CaravanTrains.realTrain(level, v.pine(), v.far())) {
						rename(level, v.data(), renamed.apply(v), to);
						since[0] = tick[0];
						phase[0] = 1;
					} else if (tick[0] > 2000) {
						helper.fail("setup: no Pine-Oakford train ran in 2000 ticks; " + QaTrainSameNames1103eCompatTests.names(helper));
					}
				}
				case 1 -> {
					if (tick[0] - since[0] < 300) {
						return;
					}
					v.data().clearRoad(v.pine());
					for (BlockPos h : v.halls()) {
						((Container) level.getBlockEntity(h.east(3).north(2))).clearContent();
					}
					((Container) level.getBlockEntity(v.pine().east(3).north(2))).setItem(0, new ItemStack(Items.OAK_LOG, 64));
					v.data().setWants(v.pine(), Component.literal("Pine"), List.of());
					v.data().setWants(v.far(), Component.literal(v.far().equals(renamed.apply(v)) ? to : "Oakford"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
					v.data().sent(v.pine(), -1);
					Caravans.round(level, v.pine(), null);
					v.data().setWants(v.far(), Component.literal(v.far().equals(renamed.apply(v)) ? to : "Oakford"), List.of());
					Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.far())).findFirst().orElse(null);
					helper.assertTrue(s != null && s.byTrain(), "setup: Pine's caravan for the far village doesn't go by train: " + s);
					since[0] = tick[0];
					phase[0] = 2;
				}
				default -> {
					Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.far())).findFirst().orElse(null);
					if (s == null) {
						helper.assertTrue(CreateTrainCompatTests.said(level, v.far(), "chronicle.aliveworkplace.train_came"),
							"Pine's logs reached the far village but not by train: " + QaTrainSameNames1103eCompatTests.names(helper));
						Set<String> names = new HashSet<>();
						for (BlockPos p : List.of(QaTrainSameNames1103eCompatTests.STATION_PINE, QaTrainSameNames1103eCompatTests.STATION_MID,
							QaTrainSameNames1103eCompatTests.STATION_FAR, QaTrainSameNames1103eCompatTests.STATION_DUN)) {
							names.add(name(helper, p));
						}
						helper.assertTrue(names.size() == 4, "two stations on the line share a name after the rename: " + QaTrainSameNames1103eCompatTests.names(helper));
						int logs = 0;
						for (BlockPos h : v.halls()) {
							logs += ((Container) level.getBlockEntity(h.east(3).north(2))).countItem(Items.OAK_LOG);
						}
						helper.assertTrue(logs == 64, "logs were lost or doubled on the way: " + logs + " of 64 in the four chests; the far village's storehouse chests "
							+ Caravans.storehouse(level, v.far()));
						QaTrainSameNames1103eCompatTests.tidy(level, v, train);
						helper.succeed();
					} else if (tick[0] - since[0] > 2600) {
						helper.fail("after the rename Pine's logs never reached the far village by train: caravan " + s + ", train: "
							+ CreateTrainCompatTests.railway().status(level, train) + "; " + QaTrainSameNames1103eCompatTests.names(helper));
					}
				}
			}
		});
	}

	/** The middle village (Elmwood, trading with Dunmore) renamed Oakford with a name tag while the Pine-Oakford train runs. */
	//$ gametest_ticks_batch AREA '5000' '"qaTrainMiddleRenamed"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "qaTrainMiddleRenamed")
	public void theMiddleVillageRenamedLikeTheFarOneStillLetsTheGoodsReachTheFarOne(GameTestHelper helper) {
		ride(helper, QaTrainSameNames1103eCompatTests.Four::mid, "Oakford");
	}

	/** The far Oakford renamed Pine (its own partner's name) with a name tag while the Pine-Oakford train runs. */
	//$ gametest_ticks_batch AREA '5000' '"qaTrainFarRenamedPine"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "qaTrainFarRenamedPine")
	public void theFarVillageRenamedLikeItsPartnerStillGetsItsGoods(GameTestHelper helper) {
		ride(helper, QaTrainSameNames1103eCompatTests.Four::far, "Pine");
	}
}
