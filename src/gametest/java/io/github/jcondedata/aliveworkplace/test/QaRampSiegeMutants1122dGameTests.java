package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane-d-1122-1609), from a mutation pass over lane A's B1493 and B1494 fixes (SandRamps, BattleStations,
 * GuardCombat): a test for each survivor a player would notice, written from the spec. sieges.md (B1427, B1494): "An
 * archer is sent to the best station he can get onto: a path there, or one that ends below it within 3.5 blocks, where
 * he climbs."
 */
public class QaRampSiegeMutants1122dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_ramp_siege_mutants_1122d");
	/** The Wall Tower's corner: it stands on x 5 to 9, z 13 to 17, its top's stations at y 10 (as BattleStationGameTests). */
	private static final BlockPos TOWER = new BlockPos(5, 2, 13);
	/** The mound's top solid row: an archer stands on it at y 8, two below the tower's stations. */
	private static final int MOUND_TOP = 7;

	/**
	 * Survivor BattleStations:617 ({@code >= 2} to {@code > 2}): a Wall Tower with a mound of dirt against its south side,
	 * six high, so its top is two blocks under the tower's stations and the drop off it too deep to walk down. The
	 * archer's Guard Post is on the mound. His path to a station ends at the mound's edge, right below the tower's south
	 * crenels: a station two above him, within 3.5 blocks, which he climbs. He must be on a station within 450 ticks, not
	 * passed over for every station and left on the mound.
	 */
	//$ gametest_ticks_batch AREA '500' '"qaRampSiegeMutants1122dMound"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaRampSiegeMutants1122dMound")
	public void qaAnArcherClimbsAStationTwoAboveWhereHisPathEnds(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper, new BlockPos(20, 2, 6));
		finished(helper, StarterBlueprints.WALL_TOWER, TOWER);
		for (int x = 3; x <= 11; x++) {
			for (int z = 18; z <= 23; z++) {
				for (int y = 2; y <= MOUND_TOP; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
				}
			}
		}
		Villager archer = guard(helper, 8, MOUND_TOP + 1, 20);
		Mob foe = raider(helper, 25.5, 26.5);
		Culture culture = Threats.all().stream().filter(c -> c.id().equals(BESIEGERS)).findFirst().orElseThrow();
		VillageRaids.track(level, hall, culture, List.of(foe));
		helper.assertTrue(Sieges.siege(level, hall).isPresent(), "no siege on the village");
		helper.onEachTick(() -> {
			if (BattleStations.onStation(archer)) {
				helper.assertTrue(archer.getY() >= helper.absolutePos(TOWER).getY() + 8 - 0.1, "on a station at y " + helper.relativeVec(archer.position()).y);
				helper.succeed();
			} else if (helper.getTick() > 450) {
				helper.fail("the archer isn't on a station of the tower within 450 ticks: he is at " + helper.relativeVec(archer.position()) + ", his place "
					+ BattleStations.post(archer).map(p -> p.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString()) + ", stations "
					+ BattleStations.stations(level, hall).size());
			}
		});
	}

	/** A village hall at {@code at} with a culture of husks that ram gates, all put back at the end. */
	private static BlockPos village(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		helper.setDayTime(6000);
		BlockPos hall = helper.absolutePos(at);
		Culture culture = Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(BESIEGERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			BattleStations.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		helper.assertTrue(level.getBlockEntity(hall) != null, "no hall");
		return hall;
	}

	/** Stands {@code build} at {@code at} and records it finished. */
	private static void finished(GameTestHelper helper, StarterBlueprints.Entry build, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(at);
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(build.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
	}

	/** An archer (a bow in the off hand) at ({@code x}, {@code y}, {@code z}) with his own Guard Post two blocks south. */
	private static Villager guard(GameTestHelper helper, int x, int y, int z) {
		BlockPos post = new BlockPos(x, y, z + 2);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, y, z));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
		return v;
	}

	/** A raider that stands still at (x, 2, z): a husk carrying the raid's tag. */
	private static Mob raider(GameTestHelper helper, double x, double z) {
		ServerLevel level = helper.getLevel();
		Mob mob = EntityType.HUSK.create(level);
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 0f, 0f);
		mob.addTag(VillageRaids.TAG);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		return mob;
	}
}
