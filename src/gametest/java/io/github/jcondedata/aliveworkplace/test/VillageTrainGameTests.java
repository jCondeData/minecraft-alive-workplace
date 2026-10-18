package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * ROADMAP 39.7 without Create: the Train Station isn't at the Blueprint Table, it belongs to {@code villageTrains}, its
 * text and the chronicle's train lines read, and caravans walk whatever the caravans' list says; a caravan's
 * {@code byTrain} and a village's stations and track are saved, and a save from before 39.7 loads them as none (it
 * walks). With Create: {@code VillageTrainCompatTests}.
 */
public class VillageTrainGameTests implements FabricGameTest {
	/** Without Create: no station at the table (it still loads by id), no trains, the text is there. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void noTrainsWithoutCreate(GameTestHelper helper) {
		helper.assertTrue(!Platform.get().isModLoaded("create"), "this suite runs without Create");
		ResourceLocation id = StarterBlueprints.TRAIN_STATION.id();
		helper.assertTrue(!BlueprintLibrary.list(helper.getLevel().getServer(), false).contains(id), "the Train Station is listed without Create");
		helper.assertTrue(!CaravanTrains.listed(id) && CaravanTrains.listed(StarterBlueprints.WELL.id()), "the Train Station isn't held back alone");
		helper.assertTrue(BlueprintLibrary.list(helper.getLevel().getServer(), true).contains(id), "the Train Station doesn't load by id");
		helper.assertTrue("villageTrains".equals(StarterBlueprints.expansionSwitch(id)), "the Train Station isn't behind villageTrains");
		helper.assertTrue(!CaravanTrains.live(), "trains run without Create");
		// Everything a train needs on the list, but no Create: the caravan walks
		Caravans.Data data = new Caravans.Data();
		BlockPos a = new BlockPos(0, 64, 0);
		BlockPos b = new BlockPos(0, 64, -140);
		data.setWants(a, Component.literal("Ashford"), List.of());
		data.setWants(b, Component.literal("Bramble"), List.of());
		data.setStations(a, List.of(a.east(6)));
		data.setStations(b, List.of(b.east(6)));
		data.setHalf(a, b, new Caravans.Half(a.north(70), true, false, true));
		data.setHalf(b, a, new Caravans.Half(b.south(70), true, false, true));
		data.setTracked(a, b, true);
		data.setTracked(b, a, true);
		helper.assertTrue(data.roadFinished(a, b) && !CaravanTrains.rides(data, a, b), "a caravan rides a train without Create");
		Language en = Language.getInstance();
		for (String key : List.of("blueprint.aliveworkplace.train_station", "blueprint_desc.aliveworkplace.train_station", "blueprint.aliveworkplace.railway",
			"aliveworkplace.config.villageTrains", "aliveworkplace.config.villageTrains.tooltip", "chronicle.aliveworkplace.train_left",
			"chronicle.aliveworkplace.train_came", "chronicle.aliveworkplace.train_walked", "chronicle.aliveworkplace.train_track_laid",
			"chronicle.aliveworkplace.railway_open")) {
			helper.assertTrue(en.has(key), key + " has no text");
		}
		helper.assertTrue(en.getOrDefault("chronicle.aliveworkplace.train_came").contains("%s"), "the train's arrival names nobody");
		helper.succeed();
	}

	/** Stations, track and a caravan's byTrain come back from a save; a save from before 39.7 has none, and its caravans walk. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void trainsAreSavedAndOldSavesWalk(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		Caravans.Data data = new Caravans.Data();
		BlockPos a = new BlockPos(0, 64, 0);
		BlockPos b = new BlockPos(0, 64, -140);
		data.setWants(a, Component.literal("Ashford"), List.of());
		data.setWants(b, Component.literal("Bramble"), List.of());
		data.setStations(a, List.of(a.east(6), a.west(9)));
		data.setTracked(a, b, true);
		CompoundTag tag = data.save(new CompoundTag(), registries);
		ListTag road = new ListTag();
		CompoundTag train = new CompoundTag();
		train.putLong("from", a.asLong());
		train.putLong("to", b.asLong());
		train.putLong("arrives", 1234);
		ListTag goods = new ListTag();
		goods.add(new ItemStack(Items.OAK_LOG, 32).save(registries));
		train.put("goods", goods);
		CompoundTag walker = train.copy();
		train.putBoolean("byTrain", true);
		road.add(train);
		road.add(walker); // as a save from before 39.7 wrote it
		tag.put("road", road);
		Caravans.Data back = Caravans.Data.load(tag, registries);
		helper.assertTrue(back.stations(a).equals(List.of(a.east(6), a.west(9))) && back.stations(b).isEmpty(), "the stations didn't round-trip: " + back.stations(a));
		helper.assertTrue(back.tracked(a, b) && !back.tracked(b, a), "the track didn't round-trip");
		List<Caravans.Shipment> onTheRoad = back.onTheRoad();
		helper.assertTrue(onTheRoad.size() == 2 && onTheRoad.get(0).byTrain() && !onTheRoad.get(1).byTrain(),
			"byTrain didn't load (a train, then an old save's caravan that walks): " + onTheRoad);
		helper.assertTrue(onTheRoad.get(0).arrives() == 1234 && onTheRoad.get(0).goods().get(0).getCount() == 32, "the train's goods or arrival changed");
		// A save from before 39.7: no stations, no track, nobody by train
		CompoundTag old = Caravans.Data.load(tag, registries).save(new CompoundTag(), registries);
		old.getList("villages", 10).forEach(t -> {
			((CompoundTag) t).remove("stations");
			((CompoundTag) t).remove("tracks");
		});
		Caravans.Data older = Caravans.Data.load(old, registries);
		helper.assertTrue(older.stations(a).isEmpty() && !older.tracked(a, b), "an old save has stations or track");
		// The village leaves the list: its stations and the track towards it go with it
		back.remove(b);
		helper.assertTrue(!back.tracked(a, b), "track towards a village gone from the list is kept");
		helper.succeed();
	}

	/**
	 * 39.7b, the track's plan (no Create needed): Create's trains only pass between pieces that meet nearly straight, so a
	 * way that steps up and back down gets a sloped curve over each step between level pieces two nodes off it (here the
	 * two slopes share the landing between them), a turn with straight way either side a curve between the pieces three
	 * nodes off it, nothing laid on the nodes the curves pass over; a step too short to slope and a node not free stay
	 * gaps; a caravan aboard with its goods in the train's chest counts as waiting.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theTrackPlanSlopesOverStepsAndCurvesRoundTurns(GameTestHelper helper) {
		java.util.List<BlockPos> way = new java.util.ArrayList<>();
		for (int x = 0; x <= 15; x++) {
			way.add(new BlockPos(x, x >= 4 && x <= 7 ? 1 : 0, 0)); // up a block at x = 4, down again at x = 8
		}
		for (int z = 1; z <= 4; z++) {
			way.add(new BlockPos(15, 0, z)); // the turn at (15, 0, 0): south from there
		}
		net.minecraft.core.Direction east = net.minecraft.core.Direction.EAST;
		net.minecraft.core.Direction west = net.minecraft.core.Direction.WEST;
		CaravanTrains.TrackPlan plan = CaravanTrains.plan(way, p -> true);
		helper.assertTrue(plan.curves().equals(List.of(
			new CaravanTrains.Curve(new BlockPos(12, 0, 0), east, new BlockPos(15, 0, 3), net.minecraft.core.Direction.NORTH),
			new CaravanTrains.Curve(new BlockPos(1, 0, 0), east, new BlockPos(6, 1, 0), west),
			new CaravanTrains.Curve(new BlockPos(6, 1, 0), east, new BlockPos(10, 0, 0), west))), "not a curve round the turn and a slope over each step: " + plan.curves());
		java.util.Map<BlockPos, String> expected = new java.util.LinkedHashMap<>();
		for (BlockPos p : List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(6, 1, 0), new BlockPos(10, 0, 0), new BlockPos(11, 0, 0), new BlockPos(12, 0, 0))) {
			expected.put(p, "xo");
		}
		expected.put(new BlockPos(15, 0, 3), "zo");
		expected.put(new BlockPos(15, 0, 4), "zo");
		helper.assertTrue(plan.shapes().equals(expected), "the straight pieces aren't where they go (none under the curves): " + plan.shapes());
		// a step with no room to slope is a gap, and so is a node with something on it
		CaravanTrains.TrackPlan steep = CaravanTrains.plan(List.of(new BlockPos(0, 0, 0), new BlockPos(1, 1, 0)), p -> true);
		helper.assertTrue(steep.curves().isEmpty(), "a slope of a block in a block: " + steep.curves());
		CaravanTrains.TrackPlan taken = CaravanTrains.plan(List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)), p -> p.getX() != 1);
		helper.assertTrue(!taken.shapes().containsKey(new BlockPos(1, 0, 0)) && taken.shapes().size() == 2, "track on a node with something on it: " + taken.shapes());
		helper.assertTrue(CaravanTrains.waiting(CaravanTrains.IN_CHEST) && CaravanTrains.aboard(CaravanTrains.IN_CHEST) && CaravanTrains.aboard(CaravanTrains.ABOARD)
			&& !CaravanTrains.aboard(CaravanTrains.BOARDING), "the train's stages don't read as they should");
		for (String key : List.of("chronicle.aliveworkplace.train_built", "chronicle.aliveworkplace.train_goods_left", "blueprint.aliveworkplace.village_train")) {
			helper.assertTrue(Language.getInstance().has(key), "no text for " + key);
		}
		helper.succeed();
	}
}
