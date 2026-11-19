package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaHelperBags1031GameTests.assertHelpersBack;
import static io.github.jcondedata.aliveworkplace.test.QaHelperBags1031GameTests.bag;
import static io.github.jcondedata.aliveworkplace.test.QaHelperBags1031GameTests.chestOf;
import static io.github.jcondedata.aliveworkplace.test.QaHelperBags1031GameTests.ground;
import static io.github.jcondedata.aliveworkplace.test.QaHelperBags1031GameTests.setup;
import static io.github.jcondedata.aliveworkplace.test.QaHelperBags1031GameTests.state;
import static io.github.jcondedata.aliveworkplace.test.QaHelperBags1031GameTests.sum;
import static io.github.jcondedata.aliveworkplace.test.QaHelperBags1031GameTests.total;

import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.test.QaHelperBags1031GameTests.Run;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * B645: a helper that carries a lead's materials while the build ends without it (it was saved and unloaded: a chunk
 * unloading, a restart) finds the site gone when it comes back. Its bag still goes to the chests by the lead's bench,
 * which it remembers from joining ({@code BuilderJob.bench}), not to its own bench's chest, out of the lead's reach.
 * B670's {@code Builders.sendHelpersBack} only reaches the helpers that are loaded when the build ends. Layout and
 * counting as in QaHelperBags1031GameTests.
 */
public class HelperBagsB645GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final BlockPos LEADS_TABLE = new BlockPos(2, 2, 2);

	/**
	 * The shared story: a helper carrying the lead's materials is saved and unloaded, {@code beforeUnload} runs on it
	 * first; the owner cancels the build ({@code /workplace cancel}); {@code whileAway} runs; the helper loads again.
	 * Then {@code check} runs once the helper has stopped helping with an empty bag, and no material may be lost,
	 * doubled or left on the ground.
	 */
	private static void run(GameTestHelper helper, String name, Consumer<Villager> beforeUnload, Runnable whileAway, Consumer<Run> check) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(name));
		Run r = setup(helper, c.player());
		boolean[] away = {false};
		@SuppressWarnings("unchecked")
		Map<Item, Integer>[] before = new Map[1];
		@SuppressWarnings("unchecked")
		Map<Item, Integer>[] carried = new Map[1];
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (away[0] || r.live() == null || r.carryingHelper() == null) {
				return;
			}
			away[0] = true;
			Villager mate = r.carryingHelper();
			int index = mate == r.mates()[0] ? 0 : 1;
			before[0] = total(helper, r);
			carried[0] = bag(mate);
			beforeUnload.accept(mate);
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(mate.save(tag), "the helper wasn't saved");
			mate.discard();
			QaStageSixGameTests.command(c, "/workplace cancel " + r.site());
			helper.assertTrue(r.live() == null, "/workplace cancel left the site");
			whileAway.run();
			Entity loaded = EntityType.loadEntityRecursive(tag, r.level(), e -> e);
			helper.assertTrue(loaded instanceof Villager, "the helper didn't load: " + loaded);
			r.level().addFreshEntity(loaded);
			r.mates()[index] = (Villager) loaded;
			helper.assertTrue(!bag(r.mates()[index]).isEmpty(), "the helper came back without its bag: " + state(helper, r));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(away[0], "no helper ever carried the lead's materials: " + state(helper, r));
			for (Villager mate : r.mates()) {
				helper.assertTrue(!Builders.isHelping(mate) && bag(mate).isEmpty(), "a helper is still helping or carrying: " + state(helper, r));
			}
			Map<Item, Integer> after = total(helper, r);
			helper.assertTrue(after.equals(before[0]), "materials before the helper left " + before[0] + ", after it came back " + after + ": " + state(helper, r));
			check.accept(r);
			LOGGER.info("b645 {}: the helper carried {}; lead's chest {}, helpers' chest {}, ground {}", name, carried[0], chestOf(r.chest(), r.materials()),
				chestOf(r.matesChest(), r.materials()), ground(helper, r.materials()));
		});
	}

	/** The build is canceled while the carrying helper is unloaded: back again, it empties its bag at the lead's chest. */
	//$ gametest_ticks_batch AREA '4000' '"helperBagsB645Away"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "helperBagsB645Away")
	public void aHelperAwayWhenTheBuildEndedStillEmptiesItsBagAtTheLeadsChest(GameTestHelper helper) {
		run(helper, "b645Away", mate -> {
			BuilderJob job = ModAttachments.BUILDER_JOB.get(mate);
			helper.assertTrue(job != null && job.bench().equals(Optional.of(helper.absolutePos(LEADS_TABLE))),
				"the helper's job doesn't name the lead's bench: " + job);
		}, () -> { }, r -> {
			assertHelpersBack(helper, r, "after the helper came back");
			helper.assertTrue(sum(ground(helper, r.materials())) == 0, "materials dropped on the ground: " + ground(helper, r.materials()));
		});
	}

	/**
	 * A helper saved by a version before the job named the lead's bench (its job has no {@code bench}): it loads, stops
	 * helping and loses nothing (as before, its bag goes to a chest by its own bench).
	 */
	//$ gametest_ticks_batch AREA '4000' '"helperBagsB645Old"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "helperBagsB645Old")
	public void aHelperFromAnOlderSaveLoadsAndLosesNothing(GameTestHelper helper) {
		UUID id = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0");
		CompoundTag old = (CompoundTag) BuilderJob.CODEC.encodeStart(NbtOps.INSTANCE, new BuilderJob(id, true, Optional.of(new BlockPos(1, 2, 3)))).getOrThrow();
		helper.assertTrue(old.contains("bench"), "the job doesn't save the lead's bench: " + old);
		old.remove("bench");
		BuilderJob loaded = BuilderJob.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(loaded.equals(new BuilderJob(id, true)) && loaded.bench().isEmpty(), "an older save's job loaded as " + loaded);
		run(helper, "b645Old", mate -> ModAttachments.BUILDER_JOB.set(mate, new BuilderJob(ModAttachments.BUILDER_JOB.get(mate).siteId(), true)),
			() -> { }, r -> helper.assertTrue(sum(ground(helper, r.materials())) == 0, "materials dropped on the ground: " + ground(helper, r.materials())));
	}

	/**
	 * The lead's bench is broken while the helper is away: there is no lead's bench to go back to, so the bag goes the
	 * old way (a chest by the helper's own bench) and nothing is lost.
	 */
	//$ gametest_ticks_batch AREA '4000' '"helperBagsB645Bench"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "helperBagsB645Bench")
	public void theLeadsBenchGoneWhileAHelperWasAwayLosesNothing(GameTestHelper helper) {
		run(helper, "b645Bench", mate -> { }, () -> helper.setBlock(LEADS_TABLE, Blocks.AIR), r -> { });
	}
}
