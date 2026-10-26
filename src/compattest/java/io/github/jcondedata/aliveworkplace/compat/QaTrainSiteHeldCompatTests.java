package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-10-26), verifying B483/B484 (39.7b): the station waits to put the village train together while the
 * builder's train site is still open, even with every block of the train standing. The bug lane's test holds the
 * builder still for three of the hall's looks; these take the gap the ways a server meets it:
 * <ul>
 * <li>the server saved and restarted in the gap (the sites written and read back, the builder too): the loaded site is
 * still open, so the station still waits; the builder then finishes it, the chronicle says so and the train is put
 * together, once;</li>
 * <li>the builder killed in the gap (a raid): his train site is dropped with him, and the blocks standing are put
 * together into the train after all (no site waits forever, and no second train site opens over the first).</li>
 * </ul>
 */
public class QaTrainSiteHeldCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** The Builder's Bench, an empty barrel and its builder, by the halls (as CreateTrainCompatTests' builder, no parts). */
	private static void builder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(14, 2, 20);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(new BlockPos(13, 2, 20), Blocks.BARREL);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 21));
		Builders.employ(level, builder, helper.absolutePos(bench));
	}

	private static List<Villager> builders(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds(), Builders::isBuilder);
	}

	private static BuildSite trainSite(ServerLevel level, BlockPos first) {
		return Roads.openSegments(level, first).stream().filter(st -> CaravanTrains.isTrainSite(st.structure())).findFirst().orElse(null);
	}

	private static long trainSites(ServerLevel level, BlockPos first) {
		return Roads.openSegments(level, first).stream().filter(st -> CaravanTrains.isTrainSite(st.structure())).count();
	}

	private static String chronicle(ServerLevel level, BlockPos hall) {
		return CreateTrainCompatTests.hall(level, hall).chronicle().stream().map(e -> e.text().getString()).toList().toString();
	}

	/**
	 * The builder's train site open and the train's blocks all up, then the sites and the held builder saved and loaded
	 * (a restart in the gap): the station still waits while the loaded site is open, and once the builder finishes it the
	 * chronicle says a builder built the train and the train is put together.
	 */
	//$ gametest_ticks_batch AREA '2600' '"qaTrainSiteReload"'
	@GameTest(template = AREA, timeoutTicks = 2600, batch = "qaTrainSiteReload")
	public void aRestartInTheGapStillWaitsForTheSiteThenBuildsTheTrain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper);
		CreateTrainCompatTests.Villages v = CreateTrainCompatTests.villages(helper);
		builder(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		BlockPos first = CaravanTrains.first(v.a(), v.b()) ? v.a() : v.b();
		BlockPos other = first.equals(v.a()) ? v.b() : v.a();
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		int[] tick = {0};
		int[] held = {-1};
		UUID[] siteId = {null};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				CaravanTrains.serve(level, v.a(), CreateTrainCompatTests.hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), CreateTrainCompatTests.hall(level, v.b()));
			}
			BuildSite site = trainSite(level, first);
			if (held[0] < 0) {
				if (site != null) {
					builders(helper).forEach(b -> b.setNoAi(true));
					CreateTrainCompatTests.buildTrain(level, v);
					helper.assertTrue(CreateTrainCompatTests.railway().built(level, v.data().stations(first).get(0), v.data().stations(other).get(0)),
						"the train's blocks don't stand at the station");
					held[0] = tick[0];
					siteId[0] = site.id();
				} else if (tick[0] == 1000) {
					helper.fail("no train site opened in 1000 ticks");
				}
				return;
			}
			if (tick[0] == held[0] + 20) { // the restart: the sites file and the builder written and read back
				HolderLookup.Provider registries = level.registryAccess();
				BuildSiteManager sites = BuildSiteManager.get(level);
				level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(sites.save(new CompoundTag(), registries), registries));
				helper.assertTrue(BuildSiteManager.get(level).get(siteId[0]) != null, "the open train site didn't come back from the save");
				for (Villager b : builders(helper)) {
					CompoundTag tag = new CompoundTag();
					helper.assertTrue(b.save(tag), "the builder wasn't saved");
					b.discard();
					Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
					helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
					level.addFreshEntity(loaded);
				}
				return;
			}
			if (tick[0] < held[0] + 320) { // three of the hall's looks after the restart
				helper.assertTrue(!CaravanTrains.realTrain(level, v.a(), v.b()), "after the restart the train was put together under its builder's open site "
					+ (site == null ? "(gone)" : site.stage() + " " + site.status()) + ": " + chronicle(level, first));
				helper.assertTrue(site != null, "the held builder's train site closed after the restart");
				return;
			}
			if (tick[0] == held[0] + 320) {
				helper.assertTrue(builders(helper).size() == 1, "not one builder after the restart: " + builders(helper).size());
				builders(helper).forEach(b -> b.setNoAi(false));
				return;
			}
			if (CaravanTrains.realTrain(level, v.a(), v.b())) {
				helper.assertTrue(CreateTrainCompatTests.said(level, first, "chronicle.aliveworkplace.train_built"), "the chronicle of " + first.toShortString()
					+ " doesn't say a builder built the train after the restart: " + chronicle(level, first));
				helper.assertTrue(trainSites(level, first) == 0, "a train site is still open with the train put together");
				CreateTrainCompatTests.railway().scrap(level, owner);
				CreateTrainCompatTests.tidy(level, v);
				helper.succeed();
			} else if (tick[0] == 2500) {
				helper.fail("no train after the restart and the builder finishing: site " + (site == null ? "done" : site.stage() + " " + site.status()
					+ " missing " + site.missing()) + ", chronicle " + chronicle(level, first));
			}
		});
	}

	/**
	 * The builder killed in the gap (the train's blocks all up, his train site open): the site goes with him, and the
	 * station puts the standing blocks together into the train after all, with no second train site opened.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaTrainSiteBuilderKilled"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaTrainSiteBuilderKilled")
	public void aBuilderKilledInTheGapLeavesATrainThatIsStillPutTogether(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper);
		CreateTrainCompatTests.Villages v = CreateTrainCompatTests.villages(helper);
		builder(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		BlockPos first = CaravanTrains.first(v.a(), v.b()) ? v.a() : v.b();
		BlockPos other = first.equals(v.a()) ? v.b() : v.a();
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		int[] tick = {0};
		int[] killed = {-1};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				CaravanTrains.serve(level, v.a(), CreateTrainCompatTests.hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), CreateTrainCompatTests.hall(level, v.b()));
			}
			if (killed[0] < 0) {
				if (trainSite(level, first) != null) {
					builders(helper).forEach(b -> b.setNoAi(true));
					CreateTrainCompatTests.buildTrain(level, v);
					helper.assertTrue(CreateTrainCompatTests.railway().built(level, v.data().stations(first).get(0), v.data().stations(other).get(0)),
						"the train's blocks don't stand at the station");
					helper.assertTrue(!CaravanTrains.realTrain(level, v.a(), v.b()), "the train was put together under the open site");
					List<Villager> builders = builders(helper);
					helper.assertTrue(builders.size() == 1, "not one builder: " + builders.size());
					builders.get(0).kill();
					killed[0] = tick[0];
				} else if (tick[0] == 1000) {
					helper.fail("no train site opened in 1000 ticks");
				}
				return;
			}
			helper.assertTrue(trainSites(level, first) <= 1, "a second train site opened after the builder died");
			if (CaravanTrains.realTrain(level, v.a(), v.b())) {
				helper.assertTrue(trainSites(level, first) == 0, "the dead builder's train site is still open with the train put together");
				CreateTrainCompatTests.railway().scrap(level, owner);
				CreateTrainCompatTests.tidy(level, v);
				helper.succeed();
			} else if (tick[0] == killed[0] + 1200) {
				BuildSite site = trainSite(level, first);
				helper.fail("the builder died in the gap and in 1200 ticks the standing train was never put together: site "
					+ (site == null ? "gone" : site.stage() + " " + site.status() + " builder " + site.builder()) + ", chronicle " + chronicle(level, first));
			}
		});
	}
}
