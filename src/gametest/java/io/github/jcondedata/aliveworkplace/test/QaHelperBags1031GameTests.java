package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-10-31), B670 from its fix (01db9b4ab, CHANGELOG): when a build ends (it finishes, or its owner cancels it) the
 * site's helpers stop helping first, while the site still names the lead's bench, so what each helper carries goes into
 * the chests by the lead's bench (where his next build reaches it), never into the helper's own bench chest, and nothing
 * is lost or doubled. QaHelperLeftoversGameTests covers a natural finish; these cover the player's cancel
 * ({@code /workplace cancel}), a helper still carrying at the finish, a server restart while a helper helps, and the
 * lead's bench broken or his chests full at the moment the build ends.
 *
 * <p>Layout as in QaHelperLeftoversGameTests: the lead's bench and chest at the west side, two helpers with their own bench
 * and chest 12 blocks east (outside the lead's supply radius of 8). Every count is of the hut's materials across both
 * chests, every bag and items lying on the ground: taken just before the build ends and again right after, in one tick.
 */
public class QaHelperBags1031GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos LEAD = new BlockPos(3, 2, 3);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final BlockPos[] MATE_TABLES = {new BlockPos(13, 2, 2), new BlockPos(15, 2, 2)};
	private static final BlockPos MATES_CHEST = new BlockPos(14, 2, 4);

	/** A lead with a hut under way (owned by {@code owner}), two idle builders nearby, the lead's chest holding twice its materials. */
	private record Run(ServerLevel level, Villager[] lead, Villager[] mates, UUID site, Map<Item, Integer> materials, Container chest,
		Container matesChest) {
		BuildSite live() {
			return BuildSiteManager.get(level).get(site);
		}

		/** A helper helping this site with something in its bag, or null. */
		Villager carryingHelper() {
			for (Villager mate : mates) {
				var job = ModAttachments.BUILDER_JOB.get(mate);
				if (job != null && job.helper() && job.siteId().equals(site) && !ModAttachments.BUILDER_BAG.getOrCreate(mate).isEmpty()) {
					return mate;
				}
			}
			return null;
		}
	}

	private static Run setup(GameTestHelper helper, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.player.Player owner) {
		QaHelperLeftoversGameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		helper.setBlock(MATES_CHEST, Blocks.CHEST);
		Villager lead = helper.spawn(EntityType.VILLAGER, LEAD);
		Builders.employ(level, lead, helper.absolutePos(TABLE));
		Villager[] mates = new Villager[MATE_TABLES.length];
		for (int i = 0; i < mates.length; i++) {
			helper.setBlock(MATE_TABLES[i], ModBlocks.BUILDERS_BENCH);
			mates[i] = helper.spawn(EntityType.VILLAGER, MATE_TABLES[i].south().east());
			Builders.employ(level, mates[i], helper.absolutePos(MATE_TABLES[i]));
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, lead, owner, TEST_HUT, placement);
		helper.assertTrue(site != null, "setup: the hut didn't start");
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "setup: no plan for the test hut");
		Map<Item, Integer> materials = plan.materials();
		Container chest = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : materials.entrySet()) {
			int left = e.getValue() * 2;
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				chest.setItem(slot++, new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		UUID id = site.id();
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(id));
		return new Run(level, new Villager[]{lead}, mates, id, materials, chest, helper.getBlockEntity(MATES_CHEST));
	}

	private static Map<Item, Integer> bag(Villager v) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (ItemStack s : ModAttachments.BUILDER_BAG.getOrCreate(v).stacks()) {
			if (!s.isEmpty()) {
				out.merge(s.getItem(), s.getCount(), Integer::sum);
			}
		}
		return out;
	}

	private static Map<Item, Integer> chestOf(Container c, Map<Item, Integer> materials) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (Item item : materials.keySet()) {
			out.put(item, c.countItem(item));
		}
		return out;
	}

	private static Map<Item, Integer> ground(GameTestHelper helper, Map<Item, Integer> materials) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (Item item : materials.keySet()) {
			out.put(item, 0);
		}
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16), e -> e.isAlive())) {
			if (materials.containsKey(e.getItem().getItem())) {
				out.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
			}
		}
		return out;
	}

	/** The hut's materials not yet built: both chests, every bag and the ground. */
	private static Map<Item, Integer> total(GameTestHelper helper, Run r) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (Item item : r.materials().keySet()) {
			int n = r.chest().countItem(item) + r.matesChest().countItem(item) + bag(r.lead()[0]).getOrDefault(item, 0);
			for (Villager mate : r.mates()) {
				n += bag(mate).getOrDefault(item, 0);
			}
			out.put(item, n);
		}
		ground(helper, r.materials()).forEach((item, n) -> out.merge(item, n, Integer::sum));
		return out;
	}

	private static int sum(Map<Item, Integer> m) {
		return m.values().stream().mapToInt(Integer::intValue).sum();
	}

	private static String state(GameTestHelper helper, Run r) {
		BuildSite site = r.live();
		StringBuilder b = new StringBuilder(site == null ? "(site gone)" : site.stage() + " " + site.status());
		for (Villager mate : r.mates()) {
			b.append("; helper helping=").append(Builders.isHelping(mate)).append(" bag=").append(bag(mate));
		}
		return b.append("; lead bag ").append(bag(r.lead()[0])).append("; lead chest ").append(chestOf(r.chest(), r.materials()))
			.append("; helpers' chest ").append(chestOf(r.matesChest(), r.materials())).toString();
	}

	/** After the build ended: every helper idle with an empty bag, and none of the materials in the helpers' own chest. */
	private static void assertHelpersBack(GameTestHelper helper, Run r, String when) {
		for (Villager mate : r.mates()) {
			helper.assertTrue(!Builders.isHelping(mate), when + ": a helper is still helping: " + state(helper, r));
			helper.assertTrue(bag(mate).isEmpty(), when + ": a helper still carries " + bag(mate) + ": " + state(helper, r));
		}
		helper.assertTrue(sum(chestOf(r.matesChest(), r.materials())) == 0, when + ": the helpers' own chest got "
			+ chestOf(r.matesChest(), r.materials()) + " of the lead's materials, out of his reach: " + state(helper, r));
	}

	/** A server restart: every build site and each given villager saved and loaded again (as fresh entities). */
	private static void restart(GameTestHelper helper, ServerLevel level, Villager[]... groups) {
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		for (Villager[] group : groups) {
			for (int i = 0; i < group.length; i++) {
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(group[i].save(tag), "a villager wasn't saved");
				group[i].discard();
				Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
				helper.assertTrue(loaded instanceof Villager, "a villager didn't load: " + loaded);
				level.addFreshEntity(loaded);
				group[i] = (Villager) loaded;
			}
		}
	}

	// ------------------------------------------------------------------------------------------------------------ tests

	/**
	 * The owner types {@code /workplace cancel <site>} while a helper carries the lead's materials: the helper stops, its
	 * bag goes into the lead's chest (none into its own, none on the ground), and every material is still there.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaHelperBags1031Cancel"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaHelperBags1031Cancel")
	public void aCanceledBuildsHelpersEmptyTheirBagsAtTheLeadsChest(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaHb"));
		Run r = setup(helper, c.player());
		boolean[] done = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (done[0] || r.live() == null || r.carryingHelper() == null) {
				return;
			}
			done[0] = true;
			Map<Item, Integer> before = total(helper, r);
			Map<Item, Integer> carried = bag(r.carryingHelper());
			Map<Item, Integer> leadChestBefore = chestOf(r.chest(), r.materials());
			QaStageSixGameTests.command(c, "/workplace cancel " + r.site());
			helper.assertTrue(r.live() == null, "/workplace cancel left the site: " + state(helper, r));
			assertHelpersBack(helper, r, "after the cancel");
			Map<Item, Integer> after = total(helper, r);
			helper.assertTrue(after.equals(before), "materials before the cancel " + before + ", after " + after + ": " + state(helper, r));
			helper.assertTrue(sum(ground(helper, r.materials())) == 0, "materials dropped on the ground: " + ground(helper, r.materials()));
			Map<Item, Integer> leadChest = chestOf(r.chest(), r.materials());
			for (Map.Entry<Item, Integer> e : carried.entrySet()) {
				helper.assertTrue(leadChest.getOrDefault(e.getKey(), 0) >= leadChestBefore.getOrDefault(e.getKey(), 0) + e.getValue(),
					"the helper carried " + carried + " but the lead's chest went from " + leadChestBefore + " to " + leadChest);
			}
			LOGGER.info("qa helper bags: cancel with a helper carrying {}: all back in the lead's chest {}", carried, leadChest);
		});
		helper.succeedWhen(() -> helper.assertTrue(done[0], "no helper ever carried the lead's materials: " + state(helper, r)));
	}

	/**
	 * The real tick loop to the end, with a helper carrying more than the hut needs (4 extra cobblestone in its bag, as if
	 * it fetched too much): when the hut is done, the spare half and the 4 extra are all in the lead's chest, exactly,
	 * none in the helpers' chest and none doubled.
	 */
	//$ gametest_ticks_batch AREA '5000' '"qaHelperBags1031Finish"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "qaHelperBags1031Finish")
	public void aFinishedBuildsHelperLeftoversEndAtTheLeadsChestExactly(GameTestHelper helper) {
		Run r = setup(helper, null);
		boolean[] extra = {false};
		int[] most = {0};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			most[0] = Math.max(most[0], sum(chestOf(r.matesChest(), r.materials())));
			if (!extra[0] && r.live() != null && r.carryingHelper() != null) {
				extra[0] = true;
				ModAttachments.BUILDER_BAG.getOrCreate(r.carryingHelper()).add(new ItemStack(Items.COBBLESTONE, 4));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(extra[0], "no helper ever carried the lead's materials: " + state(helper, r));
			helper.assertTrue(r.live() == null, "the hut isn't finished: " + state(helper, r));
			assertHelpersBack(helper, r, "after the finish");
			helper.assertTrue(bag(r.lead()[0]).isEmpty(), "the lead's bag isn't empty: " + bag(r.lead()[0]));
			helper.assertTrue(most[0] == 0, "the helpers' own chest got " + most[0] + " of the lead's materials during the build");
			Map<Item, Integer> expected = new LinkedHashMap<>(r.materials());
			expected.merge(Items.COBBLESTONE, 4, Integer::sum);
			Map<Item, Integer> leadChest = chestOf(r.chest(), r.materials());
			helper.assertTrue(leadChest.equals(expected), "the lead's chest should hold the spare plus the 4 extra " + expected + " but holds " + leadChest
				+ " (ground " + ground(helper, r.materials()) + ")");
			LOGGER.info("qa helper bags: finished hut; the lead's chest holds exactly the spare and the helper's extra {}", leadChest);
		});
	}

	/**
	 * {@code Builders.finish} (the lead's last step, also the tests' way to end a build) the tick a helper carries the
	 * lead's materials: the helper stops and its bag lands in the lead's chest, nothing lost or doubled.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaHelperBags1031FinishNow"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaHelperBags1031FinishNow")
	public void finishingWhileAHelperCarriesSendsItsBagToTheLeadsChest(GameTestHelper helper) {
		Run r = setup(helper, null);
		boolean[] done = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (done[0] || r.live() == null || r.carryingHelper() == null) {
				return;
			}
			done[0] = true;
			Map<Item, Integer> before = total(helper, r);
			Map<Item, Integer> carried = bag(r.carryingHelper());
			Builders.finish(r.level(), r.lead()[0], r.live());
			assertHelpersBack(helper, r, "after the finish");
			Map<Item, Integer> after = total(helper, r);
			helper.assertTrue(after.equals(before), "materials before the finish " + before + ", after " + after + ": " + state(helper, r));
			helper.assertTrue(sum(ground(helper, r.materials())) == 0, "materials dropped on the ground: " + ground(helper, r.materials()));
			LOGGER.info("qa helper bags: finish with a helper carrying {}: lead's chest now {}", carried, chestOf(r.chest(), r.materials()));
		});
		helper.succeedWhen(() -> helper.assertTrue(done[0], "no helper ever carried the lead's materials: " + state(helper, r)));
	}

	/**
	 * A server restart (sites, the lead and both helpers saved and loaded) while a helper carries the lead's materials;
	 * the restart keeps every material, the helper goes on helping, and when the owner then cancels, the helpers' bags
	 * go to the lead's chest, nothing lost or doubled.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaHelperBags1031Reload"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaHelperBags1031Reload")
	public void afterARestartAHelpersBagStillGoesToTheLeadsChest(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaHbR"));
		Run r = setup(helper, c.player());
		boolean[] restarted = {false};
		boolean[] done = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (done[0] || r.live() == null) {
				return;
			}
			if (!restarted[0]) {
				if (r.carryingHelper() == null) {
					return;
				}
				restarted[0] = true;
				Map<Item, Integer> before = total(helper, r);
				restart(helper, r.level(), r.lead(), r.mates());
				Map<Item, Integer> after = total(helper, r);
				helper.assertTrue(after.equals(before), "the restart changed the materials from " + before + " to " + after);
				helper.assertTrue(r.carryingHelper() != null, "after the restart no helper is helping with its bag: " + state(helper, r));
				return;
			}
			if (r.carryingHelper() == null) {
				return;
			}
			done[0] = true;
			Map<Item, Integer> before = total(helper, r);
			Map<Item, Integer> carried = bag(r.carryingHelper());
			QaStageSixGameTests.command(c, "/workplace cancel " + r.site());
			helper.assertTrue(r.live() == null, "/workplace cancel left the site: " + state(helper, r));
			assertHelpersBack(helper, r, "after the restart and the cancel");
			Map<Item, Integer> after = total(helper, r);
			helper.assertTrue(after.equals(before), "materials before the cancel " + before + ", after " + after + ": " + state(helper, r));
			helper.assertTrue(sum(ground(helper, r.materials())) == 0, "materials dropped on the ground: " + ground(helper, r.materials()));
			LOGGER.info("qa helper bags: restart then cancel with a helper carrying {}: lead's chest {}", carried, chestOf(r.chest(), r.materials()));
		});
		helper.succeedWhen(() -> helper.assertTrue(done[0], "never got to cancel after the restart (restarted " + restarted[0] + "): " + state(helper, r)));
	}

	/**
	 * The lead's bench broken the same tick the owner cancels, while a helper carries his materials: nothing the helper
	 * carried is lost (it lands in a chest or on the ground) and none goes into the helpers' own chest.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaHelperBags1031Bench"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaHelperBags1031Bench")
	public void theLeadsBenchBrokenAtTheEndLosesNothingTheHelperCarried(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaHbB"));
		Run r = setup(helper, c.player());
		boolean[] done = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (done[0] || r.live() == null || r.carryingHelper() == null) {
				return;
			}
			done[0] = true;
			Map<Item, Integer> before = total(helper, r);
			Map<Item, Integer> carried = bag(r.carryingHelper());
			r.level().destroyBlock(helper.absolutePos(TABLE), false);
			QaStageSixGameTests.command(c, "/workplace cancel " + r.site());
			helper.assertTrue(r.live() == null, "/workplace cancel left the site: " + state(helper, r));
			assertHelpersBack(helper, r, "bench broken, after the cancel");
			Map<Item, Integer> after = total(helper, r);
			helper.assertTrue(after.equals(before), "materials before the cancel " + before + ", after " + after + " (ground "
				+ ground(helper, r.materials()) + "): " + state(helper, r));
			LOGGER.info("qa helper bags: bench broken, helper carried {}: lead's chest {}, ground {}", carried, chestOf(r.chest(), r.materials()),
				ground(helper, r.materials()));
		});
		helper.succeedWhen(() -> helper.assertTrue(done[0], "no helper ever carried the lead's materials: " + state(helper, r)));
	}

	/**
	 * The lead's chest full (every free slot filled with dirt) the tick the owner cancels, while a helper carries his
	 * materials: what doesn't fit is dropped by the lead's bench, never lost and never put in the helpers' own chest.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaHelperBags1031Full"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaHelperBags1031Full")
	public void theLeadsChestFullAtTheEndLosesNothingTheHelperCarried(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaHbF"));
		Run r = setup(helper, c.player());
		boolean[] done = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (done[0] || r.live() == null || r.carryingHelper() == null) {
				return;
			}
			done[0] = true;
			for (int i = 0; i < r.chest().getContainerSize(); i++) {
				ItemStack s = r.chest().getItem(i);
				if (s.isEmpty()) {
					r.chest().setItem(i, new ItemStack(Items.DIRT, 64));
				} else {
					s.setCount(s.getMaxStackSize()); // no room left in the material stacks either
				}
			}
			Map<Item, Integer> before = total(helper, r);
			Map<Item, Integer> carried = bag(r.carryingHelper());
			QaStageSixGameTests.command(c, "/workplace cancel " + r.site());
			helper.assertTrue(r.live() == null, "/workplace cancel left the site: " + state(helper, r));
			assertHelpersBack(helper, r, "lead's chest full, after the cancel");
			Map<Item, Integer> after = total(helper, r);
			helper.assertTrue(after.equals(before), "materials before the cancel " + before + ", after " + after + " (ground "
				+ ground(helper, r.materials()) + "): " + state(helper, r));
			for (Map.Entry<Item, Integer> e : carried.entrySet()) {
				helper.assertTrue(ground(helper, r.materials()).getOrDefault(e.getKey(), 0) >= e.getValue(), "the helper carried " + carried
					+ " and the lead's chest was full, but the ground holds " + ground(helper, r.materials()));
			}
			LOGGER.info("qa helper bags: lead's chest full, helper carried {}: dropped {}", carried, ground(helper, r.materials()));
		});
		helper.succeedWhen(() -> helper.assertTrue(done[0], "no helper ever carried the lead's materials: " + state(helper, r)));
	}
}
