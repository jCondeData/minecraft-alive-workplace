package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-10-25), ROADMAP 39.7b from the player's side, hunting B483 (CreateTrainCompatTests' stepped-road
 * test failed once with "the chronicle doesn't say a builder built the train"). 39.7b: the first village's builder
 * builds the train from its blueprint, then the station assembles it. The hall's look at its trains assembles the train
 * once its blocks all stand ({@code CaravanTrains.build} asks {@code Railway.built}, not whether the train site is
 * finished), so a look that came between the builder's last block and the site's end would glue the blocks away under
 * a site still open. Here both halls look every tick instead of every 10, so no such gap is missed: the train is
 * assembled once, its site finishes (it doesn't wait for parts it no longer sees, nor build a second set), and the
 * chronicle says a builder built the train. (It passes: at assembly the site was already finished, so B483 isn't this.)
 */
public class QaTrainSiteRaceCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	//$ gametest_ticks_batch AREA '4000' '"qaTrainSiteRace"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaTrainSiteRace")
	public void aTrainAssembledTheTickItsLastBlockGoesUpLeavesNoSiteOpenAndIsInTheChronicle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper);
		CreateTrainCompatTests.Villages v = CreateTrainCompatTests.villages(helper);
		builder(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		BlockPos first = CaravanTrains.first(v.a(), v.b()) ? v.a() : v.b();
		int[] tick = {0};
		int[] assembledAt = {-1};
		String[] then = {""};
		helper.onEachTick(() -> {
			tick[0]++;
			CaravanTrains.serve(level, v.a(), CreateTrainCompatTests.hall(level, v.a()));
			CaravanTrains.serve(level, v.b(), CreateTrainCompatTests.hall(level, v.b()));
			List<BuildSite> trainSites = Roads.openSegments(level, first).stream().filter(s -> CaravanTrains.isTrainSite(s.structure())).toList();
			if (assembledAt[0] < 0) {
				if (CaravanTrains.realTrain(level, v.a(), v.b())) {
					assembledAt[0] = tick[0];
					then[0] = "at assembly (tick " + tick[0] + ") the train site was " + (trainSites.isEmpty() ? "finished"
						: trainSites.stream().map(s -> s.stage() + " " + s.status()).toList()) + " and the chronicle "
						+ (CreateTrainCompatTests.said(level, first, "chronicle.aliveworkplace.train_built") ? "said" : "didn't say") + " it was built";
				} else if (tick[0] == 3000) {
					helper.fail("no train after 3000 ticks; train sites " + trainSites.stream().map(s -> s.stage() + " " + s.status() + " missing " + s.missing()).toList());
				}
				return;
			}
			if (tick[0] - assembledAt[0] < 600) {
				return;
			}
			try {
				helper.assertTrue(trainSites.isEmpty(), "600 ticks after the train was assembled its site is still open: "
					+ trainSites.stream().map(s -> s.stage() + " " + s.status() + " missing " + s.missing()).toList() + " (" + then[0] + ")");
				helper.assertTrue(CaravanTrains.realTrain(level, v.a(), v.b()), "the assembled train is gone (" + then[0] + ")");
				helper.assertTrue(CreateTrainCompatTests.said(level, first, "chronicle.aliveworkplace.train_built"),
					"the chronicle never says a builder built the train (" + then[0] + ")");
				List<BlockPos> ours = v.data().stations(first);
				List<BlockPos> theirs = v.data().stations(first.equals(v.a()) ? v.b() : v.a());
				helper.assertTrue(!CreateTrainCompatTests.railway().built(level, ours.get(0), theirs.get(0)),
					"a second set of the train's blocks stands at the station (" + then[0] + ")");
			} finally {
				CreateTrainCompatTests.railway().scrap(level, owner);
				CreateTrainCompatTests.tidy(level, v);
			}
			helper.succeed();
		});
	}

	/** The Builder's Bench, its barrel with exactly one train's parts and its builder, by the halls (as CreateTrainCompatTests). */
	private static void builder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(14, 2, 20);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(new BlockPos(13, 2, 20), Blocks.BARREL);
		Container barrel = helper.getBlockEntity(new BlockPos(13, 2, 20));
		barrel.setItem(0, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "railway_casing")), 3));
		barrel.setItem(4, new ItemStack(Items.SPRUCE_FENCE, 3));
		barrel.setItem(5, new ItemStack(Items.SPRUCE_SLAB, 4));
		barrel.setItem(6, new ItemStack(Items.LANTERN, 1));
		barrel.setItem(1, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "controls")), 2));
		barrel.setItem(2, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "brown_seat")), 1));
		barrel.setItem(3, new ItemStack(Items.CHEST, 1));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 21));
		Builders.employ(level, builder, helper.absolutePos(bench));
	}
}
