package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaHelperBags1031GameTests.assertHelpersBack;
import static io.github.jcondedata.aliveworkplace.test.QaHelperBags1031GameTests.bag;
import static io.github.jcondedata.aliveworkplace.test.QaHelperBags1031GameTests.chestOf;
import static io.github.jcondedata.aliveworkplace.test.QaHelperBags1031GameTests.ground;
import static io.github.jcondedata.aliveworkplace.test.QaHelperBags1031GameTests.setup;
import static io.github.jcondedata.aliveworkplace.test.QaHelperBags1031GameTests.state;
import static io.github.jcondedata.aliveworkplace.test.QaHelperBags1031GameTests.sum;
import static io.github.jcondedata.aliveworkplace.test.QaHelperBags1031GameTests.total;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.test.QaHelperBags1031GameTests.Run;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (qa-lane-b-1122-1838), B645 from its spec: "a builder must never wait forever for materials the player stocked by
 * his table"; what a helper fetched from a lead's chests and didn't use goes back to the lead's chests (wiki builder.md:
 * a helper "empties the bag at the chests when it finishes or stops helping"), also when the helper wasn't loaded the
 * moment the build ended. HelperBagsB645GameTests covers the owner's cancel while a helper is away, an older save and
 * the lead's bench removed; these add the soak's own route (the lead FINISHES on his own while the helper is away), a
 * server restart while it is away, the lead's chest full when it comes back, the helpers' game rule switched off while
 * it is away, the lead's bench broken and put back (it stands again), and the soak's symptom end to end: the lead's
 * next build needs exactly what the helper carried off and must still finish.
 *
 * <p>Layout and counting as in QaHelperBags1031GameTests (lead's bench and chest west, two helpers with their own bench
 * and chest 12 blocks east, out of the lead's reach).
 */
public class QaHelperAway1122bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos LEADS_TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final BlockPos[] MATE_TABLES = {new BlockPos(13, 2, 2), new BlockPos(15, 2, 2)};

	/** How the story is told: how the build ends while the helper is away, what happens while it is away, the last check. */
	private interface Story {
		/** Ends the build (or starts its ending) right after the carrying helper was saved and unloaded. */
		default void end(GameTestHelper helper, Run r, QaStageFiveGameTests.Client c) {
			QaStageSixGameTests.command(c, "/workplace cancel " + r.site());
			helper.assertTrue(r.live() == null, "/workplace cancel left the site: " + state(helper, r));
		}

		/** Runs once the site is gone, before the helper loads again. */
		default void whileAway(GameTestHelper helper, Run r, QaStageFiveGameTests.Client c) {
		}

		/** Whether what the helper carried must be found in the lead's chest at the end (false: his chest is full). */
		default boolean intoTheLeadsChest() {
			return true;
		}

		/** Whether nothing may be used up after the helper loaded (false: the lead builds again). */
		default boolean conserves() {
			return true;
		}

		/** Extra checks once the story is over (retried until they hold or the test times out). */
		default void check(GameTestHelper helper, Run r) {
		}
	}

	/**
	 * The shared story. A helper carrying the lead's materials is saved and discarded (its chunk unloaded); the build
	 * ends ({@link Story#end}); once the site is gone, {@link Story#whileAway} runs, then the helper loads again from its
	 * save. Once every helper is idle with an empty bag: none of the lead's materials in the helpers' own chest; from the
	 * moment the helper loaded to the end nothing is lost or doubled (chests, bags, the ground); and what it carried is in
	 * the lead's chest on top of what was there when it loaded.
	 */
	private static void run(GameTestHelper helper, String name, Story story) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(name));
		Run r = setup(helper, c.player());
		int[] phase = {0};
		int[] index = {0};
		CompoundTag[] saved = {null};
		@SuppressWarnings("unchecked")
		Map<Item, Integer>[] carried = new Map[1];
		@SuppressWarnings("unchecked")
		Map<Item, Integer>[] atLoad = new Map[1];
		@SuppressWarnings("unchecked")
		Map<Item, Integer>[] leadChestAtLoad = new Map[1];
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (phase[0] == 0) {
				if (r.live() == null || r.carryingHelper() == null) {
					return;
				}
				Villager mate = r.carryingHelper();
				index[0] = mate == r.mates()[0] ? 0 : 1;
				carried[0] = bag(mate);
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(mate.save(tag), "the helper wasn't saved");
				saved[0] = tag;
				mate.discard();
				phase[0] = 1;
				story.end(helper, r, c);
				return;
			}
			if (phase[0] == 1) {
				if (r.live() != null) {
					return; // (the lead is still building: the soak's route waits for his own finish)
				}
				story.whileAway(helper, r, c);
				Entity loaded = EntityType.loadEntityRecursive(saved[0], r.level(), e -> e);
				helper.assertTrue(loaded instanceof Villager, "the helper didn't load: " + loaded);
				r.level().addFreshEntity(loaded);
				r.mates()[index[0]] = (Villager) loaded;
				helper.assertTrue(bag(r.mates()[index[0]]).equals(carried[0]), "the helper came back with " + bag(r.mates()[index[0]]) + ", not "
					+ carried[0]);
				atLoad[0] = total(helper, r);
				leadChestAtLoad[0] = chestOf(r.chest(), r.materials());
				phase[0] = 2;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 2, "the helper never came back (phase " + phase[0] + "): " + state(helper, r));
			story.check(helper, r);
			assertHelpersBack(helper, r, "after the helper came back");
			if (story.conserves()) {
				Map<Item, Integer> now = total(helper, r);
				helper.assertTrue(now.equals(atLoad[0]), "materials when the helper loaded " + atLoad[0] + ", now " + now + " (ground "
					+ ground(helper, r.materials()) + "): " + state(helper, r));
			}
			if (story.intoTheLeadsChest()) {
				Map<Item, Integer> leadChest = chestOf(r.chest(), r.materials());
				for (Map.Entry<Item, Integer> e : carried[0].entrySet()) {
					if (r.materials().containsKey(e.getKey())) {
						helper.assertTrue(leadChest.getOrDefault(e.getKey(), 0) >= leadChestAtLoad[0].getOrDefault(e.getKey(), 0) + e.getValue(),
							"the helper carried " + carried[0] + " but the lead's chest went from " + leadChestAtLoad[0] + " to " + leadChest
								+ " (ground " + ground(helper, r.materials()) + ")");
					}
				}
			}
			LOGGER.info("qa helper away 1122b {}: the helper carried {}; lead's chest {}, helpers' chest {}, ground {}", name, carried[0],
				chestOf(r.chest(), r.materials()), chestOf(r.matesChest(), r.materials()), ground(helper, r.materials()));
		});
	}

	/** The soak's route: nobody cancels; the lead finishes the hut on his own while the carrying helper is unloaded. */
	//$ gametest_ticks_batch AREA '6000' '"qaHelperAway1122bFinish"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaHelperAway1122bFinish")
	public void aHelperAwayWhileTheLeadFinishesOnHisOwnEmptiesItsBagAtTheLeadsChest(GameTestHelper helper) {
		run(helper, "qaHa1", new Story() {
			@Override
			public void end(GameTestHelper h, Run r, QaStageFiveGameTests.Client c) {
				// nothing: the real tick loop goes on to the finish without the helper
			}

			@Override
			public void check(GameTestHelper h, Run r) {
				h.assertTrue(sum(ground(h, r.materials())) == 0, "materials dropped on the ground: " + ground(h, r.materials()));
			}
		});
	}

	/** A server restart while the helper is away (every site and the lead saved and loaded again), then it loads. */
	//$ gametest_ticks_batch AREA '4000' '"qaHelperAway1122bRestart"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaHelperAway1122bRestart")
	public void aHelperAwayOverACancelAndARestartEmptiesItsBagAtTheLeadsChest(GameTestHelper helper) {
		run(helper, "qaHa2", new Story() {
			@Override
			public void whileAway(GameTestHelper h, Run r, QaStageFiveGameTests.Client c) {
				ServerLevel level = r.level();
				var registries = level.registryAccess();
				level.getDataStorage().set(BuildSiteManager.NAME,
					BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
				CompoundTag tag = new CompoundTag();
				h.assertTrue(r.lead()[0].save(tag), "the lead wasn't saved");
				r.lead()[0].discard();
				Entity lead = EntityType.loadEntityRecursive(tag, level, e -> e);
				h.assertTrue(lead instanceof Villager, "the lead didn't load: " + lead);
				level.addFreshEntity(lead);
				r.lead()[0] = (Villager) lead;
				h.assertTrue(r.live() == null, "the canceled site came back after the restart");
			}

			@Override
			public void check(GameTestHelper h, Run r) {
				h.assertTrue(sum(ground(h, r.materials())) == 0, "materials dropped on the ground: " + ground(h, r.materials()));
			}
		});
	}

	/**
	 * The lead's chest is filled to the last slot while the helper is away: when it comes back nothing is lost (what
	 * doesn't fit lies by the LEAD's bench), and none of it goes into the helpers' own chest.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaHelperAway1122bFull"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaHelperAway1122bFull")
	public void aHelperAwayFindingTheLeadsChestFullLosesNothingAndKeepsItByTheLead(GameTestHelper helper) {
		run(helper, "qaHa3", new Story() {
			@Override
			public void whileAway(GameTestHelper h, Run r, QaStageFiveGameTests.Client c) {
				for (int i = 0; i < r.chest().getContainerSize(); i++) {
					ItemStack s = r.chest().getItem(i);
					if (s.isEmpty()) {
						r.chest().setItem(i, new ItemStack(Items.DIRT, 64));
					} else {
						s.setCount(s.getMaxStackSize());
					}
				}
			}

			@Override
			public boolean intoTheLeadsChest() {
				return false;
			}

			@Override
			public void check(GameTestHelper h, Run r) {
				BlockPos lead = h.absolutePos(LEADS_TABLE);
				for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(16),
					e -> e.isAlive() && r.materials().containsKey(e.getItem().getItem()))) {
					double toLead = e.blockPosition().distSqr(lead);
					for (BlockPos t : MATE_TABLES) {
						h.assertTrue(toLead < e.blockPosition().distSqr(h.absolutePos(t)), e.getItem() + " lies at " + e.blockPosition().toShortString()
							+ ", by a helper's bench, not by the lead's " + lead.toShortString());
					}
				}
			}
		});
	}

	/** The owner switches the helpers' game rule off while the helper is away: it comes back, stops and empties its bag at the lead's chest. */
	//$ gametest_ticks_batch AREA '4000' '"qaHelperAway1122bRuleOff"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaHelperAway1122bRuleOff")
	public void aHelperAwayWithHelpingSwitchedOffStillEmptiesItsBagAtTheLeadsChest(GameTestHelper helper) {
		run(helper, "qaHa4", new Story() {
			@Override
			public void whileAway(GameTestHelper h, Run r, QaStageFiveGameTests.Client c) {
				r.level().getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, r.level().getServer()); // (prepare puts it back)
			}

			@Override
			public void check(GameTestHelper h, Run r) {
				h.assertTrue(sum(ground(h, r.materials())) == 0, "materials dropped on the ground: " + ground(h, r.materials()));
			}
		});
	}

	/**
	 * The lead's bench is broken while the helper is away and the player puts it back on the same spot (the boundary on
	 * the other side of HelperBagsB645's "bench gone"): it stands again by the lead's chest, so the bag goes there.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaHelperAway1122bBenchBack"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaHelperAway1122bBenchBack")
	public void theLeadsBenchBrokenAndPutBackWhileAHelperIsAwayStillGetsItsBag(GameTestHelper helper) {
		run(helper, "qaHa5", new Story() {
			@Override
			public void whileAway(GameTestHelper h, Run r, QaStageFiveGameTests.Client c) {
				r.level().destroyBlock(h.absolutePos(LEADS_TABLE), false);
				h.assertBlockNotPresent(ModBlocks.BUILDERS_BENCH, LEADS_TABLE);
				h.setBlock(LEADS_TABLE, ModBlocks.BUILDERS_BENCH);
			}

			@Override
			public void check(GameTestHelper h, Run r) {
				h.assertTrue(sum(ground(h, r.materials())) == 0, "materials dropped on the ground: " + ground(h, r.materials()));
			}
		});
	}

	/** The end-to-end story: the lead's next build, stocked so that it needs exactly what the away helper carried. */
	private static final class NextBuild implements Story {
		private UUID next;
		private Map<BlockPos, net.minecraft.world.level.block.state.BlockState> hut = Map.of();

		@Override
		public void whileAway(GameTestHelper h, Run r, QaStageFiveGameTests.Client c) {
			ServerLevel level = r.level();
			// take the canceled hut's blocks down so the next hut on the same spot needs all of its materials again
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), h.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
			Set<BlockPos> box = new HashSet<>(io.github.jcondedata.aliveworkplace.build.BuildPlan.create(
				io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, TEST_HUT).orElseThrow(), placement).targets().keySet());
			for (BlockPos p : box) {
				if (p.getY() >= h.absolutePos(HUT).getY()) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(16), Entity::isAlive)) {
				e.discard();
			}
			ModAttachments.BUILDER_BAG.remove(r.lead()[0]);
			for (Villager mate : r.mates()) {
				if (mate.isAlive()) {
					h.assertTrue(bag(mate).isEmpty(), "the loaded helper still carries " + bag(mate) + " after the cancel");
				}
			}
			BuildSite site = Builders.start(level, r.lead()[0], c.player(), TEST_HUT, placement);
			h.assertTrue(site != null, "the lead's next hut didn't start");
			next = site.id();
			this.hut = io.github.jcondedata.aliveworkplace.build.BuildPlan.create(
				io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, TEST_HUT).orElseThrow(), placement).targets();
			Leftovers.after(h, () -> BuildSiteManager.get(level).remove(next));
			Map<Item, Integer> needs = site.plan(level).materials();
			// the player's stock by the lead's table: what the next hut needs, less what the away helper carries off
			Map<Item, Integer> carried = new LinkedHashMap<>();
			for (Villager mate : r.mates()) {
				if (!mate.isAlive()) {
					carried = bag(mate);
				}
			}
			r.chest().clearContent();
			int slot = 0;
			for (Map.Entry<Item, Integer> e : needs.entrySet()) {
				int left = e.getValue() - carried.getOrDefault(e.getKey(), 0);
				while (left > 0) {
					int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
					r.chest().setItem(slot++, new ItemStack(e.getKey(), n));
					left -= n;
				}
			}
			LOGGER.info("qa helper away 1122b next build: needs {}, the helper carries {}, the lead's chest {}", needs, carried,
				chestOf(r.chest(), r.materials()));
		}

		@Override
		public boolean conserves() {
			return false;
		}

		@Override
		public boolean intoTheLeadsChest() {
			return false; // (the lead takes it out again for the next hut: the hut finishing is the proof)
		}

		@Override
		public void check(GameTestHelper h, Run r) {
			BuildSite site = next == null ? null : BuildSiteManager.get(r.level()).get(next);
			h.assertTrue(site == null, "the lead's next hut never finished: " + (site == null ? "" : site.stage() + " " + site.status()
				+ ", missing " + site.missing()) + "; " + state(h, r));
			// finished means built: every block of the hut stands (not a site that vanished)
			ServerLevel level = r.level();
			java.util.List<String> wrong = new java.util.ArrayList<>();
			hut.forEach((pos, want) -> {
				if (!io.github.jcondedata.aliveworkplace.build.MaterialRules.matches(level.getBlockState(pos), want)) {
					wrong.add(pos.toShortString() + " " + level.getBlockState(pos).getBlock().getDescriptionId() + " (wanted " + want.getBlock().getDescriptionId() + ")");
				}
			});
			h.assertTrue(wrong.isEmpty(), "the lead's next hut is gone but " + wrong.size() + " of its " + hut.size() + " blocks don't stand: "
				+ wrong.subList(0, Math.min(8, wrong.size())) + "; " + state(h, r));
			LOGGER.info("qa helper away 1122b next build finished: all {} blocks stand; {}", hut.size(), state(h, r));
		}
	}

	/**
	 * The soak's symptom end to end ("a builder waits forever for materials the player stocked by his table"): the owner
	 * cancels while the helper is away, then starts the lead's next hut with the lead's chest holding what it needs LESS
	 * what the helper carried off. The hut can only finish if the helper's bag comes back to the lead's chest.
	 */
	//$ gametest_ticks_batch AREA '7000' '"qaHelperAway1122bNext"'
	@GameTest(template = AREA, timeoutTicks = 7000, batch = "qaHelperAway1122bNext")
	public void theLeadsNextBuildFinishesWithWhatTheAwayHelperBroughtBack(GameTestHelper helper) {
		run(helper, "qaHa6", new NextBuild());
	}
}
