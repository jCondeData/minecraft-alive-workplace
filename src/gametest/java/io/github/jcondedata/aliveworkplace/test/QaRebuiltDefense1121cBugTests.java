package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA lane C (2026-11-21): failing tests from {@link QaRebuiltDefense1121cGameTests}' run, kept on the branch
 * {@code tests/rebuilt-defense-1121c} (not registered on main). gatehouse.md: "Battle stations ... Reachable within 450
 * ticks by a guard starting on the village side (all the station tests)", and "Level II: at least one station with
 * z >= 4 (on the barbican)". The rebuilt Gatehouse II's barbican roof is all stations (standing at y = 5, z 5 to 8),
 * but no ground a guard walks to on the village side lies within {@link BattleStations#CLIMB_REACH} of any of them, and
 * {@code BattleStations} hands archers stations without asking whether they can get there.
 */
public class QaRebuiltDefense1121cBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** The template check: every barbican station of Gatehouse II, with the gates shut, climbed to from the village side. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaGatehouseIIsBarbicanStationsAreReachedFromTheVillageSide(GameTestHelper helper) {
		Blueprint blueprint = QaRebuiltDefense1121cGameTests.blueprint(helper, StarterBlueprints.GATEHOUSE_2);
		int sx = blueprint.size().getX();
		Set<BlockPos> reached = QaRebuiltDefense1121cGameTests.walk(QaRebuiltDefense1121cGameTests.cells(blueprint),
			QaRebuiltDefense1121cGameTests.street(-2, sx + 1),
			new QaRebuiltDefense1121cGameTests.Bounds(-2, sx + 1, blueprint.size().getZ(), blueprint.size().getY(), false, 3, sx));
		List<BlockPos> barbican = BattleStations.spots(blueprint).stream().filter(s -> s.getZ() >= 4).toList();
		helper.assertTrue(!barbican.isEmpty(), "no barbican station");
		List<BlockPos> unreached = barbican.stream().filter(s -> !QaRebuiltDefense1121cGameTests.climbable(reached, s)).map(BlockPos::above).toList();
		helper.assertTrue(unreached.isEmpty(), "barbican stations (standing) no guard on the village side can climb to: " + unreached
			+ " (the walk reaches z up to " + reached.stream().mapToInt(BlockPos::getZ).max().orElse(-99) + ")");
		helper.succeed();
	}

	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_defense_1121c_besiegers");

	/**
	 * In the world (BattleStationGameTests' set-up, with Gatehouse II in place of the Gatehouse): a village hall to the
	 * north, Gatehouse II finished at (8, 2, 8) in a stone wall along z 11 (its barbican's face at z 17), four archers on the village side and a raider
	 * 7 blocks out in front of the barbican. Within 450 ticks of the siege every archer is up on one of the Gatehouse's
	 * stations (it has more than four).
	 */
	//$ gametest_ticks_batch AREA '600' '"qaDefense1121cBarbicanArcher"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaDefense1121cBarbicanArcher")
	public void qaAnArcherGetsOntoAGatehouseIIStationInASiege(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		helper.setDayTime(6000);
		BlockPos hallAt = new BlockPos(13, 2, 2);
		BlockPos hall = helper.absolutePos(hallAt);
		Culture culture = Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new java.util.LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new java.util.LinkedHashMap<>(loaded);
		with.put(BESIEGERS, culture);
		Threats.setForTest(with);
		BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 8));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			BattleStations.forget();
			Moods.forget();
			BuildSiteManager.get(level).forgetFinished(placement);
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		level.getStructureManager().get(StarterBlueprints.GATEHOUSE_2.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(),
			RandomSource.create(1L), 2);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.GATEHOUSE_2.id(), placement, UUID.randomUUID());
		for (int x = 0; x < 30; x++) { // the stone wall the Gatehouse stands in, along its row z = 3 (as the siege yards stand it)
			if (x >= 8 && x < 8 + StarterBlueprints.GATEHOUSE_2.size().getX()) {
				continue;
			}
			for (int y = 2; y <= 5; y++) {
				helper.setBlock(new BlockPos(x, y, 11), net.minecraft.world.level.block.Blocks.STONE_BRICKS);
			}
		}
		List<Villager> archers = new java.util.ArrayList<>();
		for (int x : new int[] {6, 10, 14, 18}) {
			BlockPos post = new BlockPos(x, 2, 5);
			helper.setBlock(post, ModBlocks.GUARD_POST);
			Villager archer = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, 3));
			Jobs.employ(level, archer, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
			archer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			archer.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
			archers.add(archer);
		}
		Mob foe = EntityType.HUSK.create(level);
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		foe.moveTo(base.getX() + 13.5, base.getY() + 2, base.getZ() + 24.5, 0f, 0f);
		foe.addTag(VillageRaids.TAG);
		foe.setNoAi(true);
		foe.setPersistenceRequired();
		level.addFreshEntity(foe);
		VillageRaids.track(level, hall, culture, List.of(foe));
		helper.assertTrue(Sieges.siege(level, hall).isPresent(), "(setup) no siege on the village");
		helper.onEachTick(() -> {
			if (archers.stream().allMatch(BattleStations::onStation)) {
				helper.succeed();
			} else if (helper.getTick() > 450) {
				List<String> off = archers.stream().filter(a -> !BattleStations.onStation(a)).map(a -> "at " + a.blockPosition().subtract(origin).toShortString()
					+ ", sent to " + BattleStations.post(a).map(p -> p.subtract(origin).toShortString()).orElse("nowhere")).toList();
				helper.fail("archers not on a Gatehouse II station within 450 ticks (drawing coordinates): " + off);
			}
		});
	}
}
