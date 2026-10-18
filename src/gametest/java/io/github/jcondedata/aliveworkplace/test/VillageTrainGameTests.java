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
		for (String key : List.of("blueprint.aliveworkplace.train_station", "blueprint.aliveworkplace.railway",
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
}
