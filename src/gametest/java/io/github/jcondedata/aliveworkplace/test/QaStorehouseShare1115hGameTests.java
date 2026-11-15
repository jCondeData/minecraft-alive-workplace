package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane b, 2026-11-15) of Builders fed from a shared Storehouse, from the spec (wiki builder.md "Whichever table
 * (B290)", "What shared chests keep back (B31, 23.5, B1187)", B132, B636, config-switches.md `workplaceFreeMaterials`),
 * not from the code. Targets what QaBuilderSupplies1103, QaStorehouseFetch1107b and QaColonySupplies1102 leave out: the
 * Storehouse holding exactly one hut's stone for two builders (each builder's "Needs" honest), a save and reload of
 * the world while a builder carries stone fetched from the Storehouse (alone and with a second builder sharing it), and
 * free materials on (the Storehouse is left alone). Every test counts the cobblestone at every tick: what stands in the
 * huts plus what is in the chests, bags and on the ground is exactly what was stocked.
 */
public class QaStorehouseShare1115hGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos STOREHOUSE = new BlockPos(26, 2, 26);
	private static final List<BlockPos> STORE_CHESTS = List.of(new BlockPos(23, 2, 26), new BlockPos(26, 2, 23));
	private static final BlockPos TABLE2 = new BlockPos(18, 2, 2);
	private static final BlockPos CHEST2 = new BlockPos(18, 2, 3);
	private static final BlockPos HUT2 = new BlockPos(15, 2, 12);
	private static final UUID JESSE = UUID.fromString("5f1b2c3d-0000-4000-8000-0000000b1024");
	private static final BuildPlan.Stage[] BUILT = {BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION};

	// ------------------------------------------------------------------------------------------------------ helpers

	private static Set<BlockPos> spotsOf(BuildPlan plan, Item item) {
		Set<BlockPos> out = new HashSet<>();
		for (BuildPlan.Stage stage : BUILT) {
			for (BuildPlan.Step s : plan.steps(stage)) {
				if (!s.state().isAir() && s.state().getBlock().asItem() == item) {
					out.add(s.pos().immutable());
				}
			}
		}
		return out;
	}

	private static int placedOf(ServerLevel level, BuildPlan plan, Item item) {
		int n = 0;
		for (BlockPos p : spotsOf(plan, item)) {
			if (level.getBlockState(p).getBlock().asItem() == item) {
				n++;
			}
		}
		return n;
	}

	private static int inContainers(GameTestHelper helper, List<BlockPos> containers, Item item) {
		int n = 0;
		for (BlockPos p : containers) {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(p)) instanceof Container c) {
				n += c.countItem(item);
			}
		}
		return n;
	}

	/** {@code item} in these containers, every villager's bag and inventory near the test and on the ground near it. */
	private static int loose(GameTestHelper helper, List<BlockPos> containers, Item item) {
		int n = inContainers(helper, containers, item);
		AABB around = helper.getBounds().inflate(16);
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, around)) {
			if (v.isRemoved()) {
				continue;
			}
			n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
			n += v.getInventory().countItem(item);
		}
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, around)) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	private static int bagTotal(Villager v) {
		int n = 0;
		for (ItemStack s : ModAttachments.BUILDER_BAG.getOrCreate(v).stacks()) {
			n += s.getCount();
		}
		return n;
	}

	private static void ensurePoi(ServerLevel level, BlockPos pos) {
		if (level.getPoiManager().getType(pos).isEmpty()) {
			PoiTypes.forState(level.getBlockState(pos)).ifPresent(type -> level.getPoiManager().add(pos.immutable(), type));
		}
	}

	private static int cobbleOf(BuildPlan plan) {
		Integer listed = plan.materials().get(Items.COBBLESTONE);
		if (listed == null || listed != spotsOf(plan, Items.COBBLESTONE).size()) {
			throw new GameTestAssertException("setup: the test hut lists " + listed + " cobblestone but draws " + spotsOf(plan, Items.COBBLESTONE).size());
		}
		return listed;
	}

	/** How many of {@code item} the Storehouse's board asks for (0: none), as a player opening it sees it. */
	private static int asked(GameTestHelper helper, ServerPlayer player, Item item) {
		ChoiceMenu board = StorehouseBoard.boardForTest(player, helper.absolutePos(STOREHOUSE));
		int n = 0;
		for (int i = StorehouseBoard.FIRST_REQUEST; i < ChoiceMenu.SIZE; i++) {
			if (board.icon(i).is(item)) {
				n += board.icon(i).getCount();
			}
		}
		return n;
	}

	private static List<BlockPos> stockContainers() {
		List<BlockPos> all = new ArrayList<>(STORE_CHESTS);
		all.add(QaBuilderStockGameTests.TABLE_CHEST);
		all.add(CHEST2);
		return all;
	}

	/** Jesse's Storehouse (no cobblestone, one door), the table with planks and a torch, and his builder on the test hut. */
	private static QaBuilderStockGameTests.Builder firstBuilder(GameTestHelper helper) {
		QaBuilderStockGameTests.setup(helper, STOREHOUSE, 0, 1);
		for (BlockPos p : STORE_CHESTS) {
			((Container) helper.getBlockEntity(p)).setItem(0, ItemStack.EMPTY);
		}
		((Container) helper.getBlockEntity(STORE_CHESTS.get(1))).setItem(0, new ItemStack(Items.OAK_DOOR));
		QaBuilderStockGameTests.fill(helper.getBlockEntity(QaBuilderStockGameTests.TABLE_CHEST), new ItemStack(Items.OAK_PLANKS, 60), new ItemStack(Items.TORCH));
		QaBuilderStockGameTests.Builder b = QaBuilderStockGameTests.builder(helper, QaBuilderStockGameTests.HUT);
		Village.forget(b.villager());
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(b.site().id()) != null) {
				BuildSiteManager.get(level).remove(b.site().id());
			}
		});
		return b;
	}

	/** A second builder of Jesse's at another table 16 blocks off, with planks, a torch and a door, on a second hut. */
	private static QaBuilderStockGameTests.Builder secondBuilder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE2, ModBlocks.BLUEPRINT_TABLE);
		ensurePoi(level, helper.absolutePos(TABLE2));
		helper.setBlock(CHEST2, Blocks.CHEST);
		QaBuilderStockGameTests.fill(helper.getBlockEntity(CHEST2), new ItemStack(Items.OAK_PLANKS, 60), new ItemStack(Items.TORCH), new ItemStack(Items.OAK_DOOR));
		Villager v2 = helper.spawn(EntityType.VILLAGER, TABLE2.east());
		Builders.employ(level, v2, helper.absolutePos(TABLE2));
		ModAttachments.BUILDER_EMPLOYER.set(v2, new Employer(JESSE, "Jesse"));
		BuildSite site2 = Builders.start(level, v2, JESSE, "Jesse", TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT2), Rotation.NONE, Mirror.NONE));
		BuildPlan plan2 = site2.plan(level);
		if (plan2 == null) {
			throw new GameTestAssertException("blueprint not found: " + TEST_HUT);
		}
		Village.forget(v2);
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site2.id()) != null) {
				BuildSiteManager.get(level).remove(site2.id());
			}
		});
		return new QaBuilderStockGameTests.Builder(v2, site2, plan2);
	}

	private static boolean finished(ServerLevel level, QaBuilderStockGameTests.Builder b) {
		return BuildSiteManager.get(level).get(b.site().id()) == null;
	}

	private static void assertBuilt(GameTestHelper helper, ServerLevel level, QaBuilderStockGameTests.Builder b) {
		helper.assertTrue(finished(level, b), "still building at " + b.site().bench() + ": stage=" + b.site().stage() + " status=" + b.site().status()
			+ " missing=" + b.site().missing());
		helper.assertTrue(b.plan().unfinished(level).isEmpty(), b.plan().unfinished(level).size() + " block(s) of the hut at " + b.site().bench() + " not as drawn");
		helper.assertTrue(bagTotal(b.villager()) == 0, "the finished builder still carries " + ModAttachments.BUILDER_BAG.getOrCreate(b.villager()).stacks());
	}

	/** As a save and a load of the world would: every build site from its save, and the builder from his. */
	private static QaBuilderStockGameTests.Builder reload(GameTestHelper helper, ServerLevel level, QaBuilderStockGameTests.Builder old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.villager().save(tag), "the builder wasn't saved");
		old.villager().discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
		level.addFreshEntity(back);
		Village.forget((Villager) back);
		BuildSite site = BuildSiteManager.get(level).get(old.site().id());
		helper.assertTrue(site != null, "the build was lost in the save and load");
		return new QaBuilderStockGameTests.Builder((Villager) back, site, old.plan());
	}

	/** The site as the manager has it now (a reload replaces the objects). */
	private static BuildSite live(ServerLevel level, QaBuilderStockGameTests.Builder b) {
		BuildSite s = BuildSiteManager.get(level).get(b.site().id());
		return s == null ? b.site() : s;
	}

	// ------------------------------------------------------------------------------ exactly one hut's worth for two

	/**
	 * B132, B290, 23.5: two of Jesse's builders share a Storehouse that holds exactly the first hut's cobblestone, not
	 * one more. At every tick the count is exact. After 2500 ticks the huts aren't both finished; each builder's "Needs"
	 * never asks more cobblestone than his own hut still lacks, and the two together are short of at least what really is
	 * missing (nobody reads "nothing needed" while a stone is missing). Topped up with the second hut's worth, both finish
	 * and no cobblestone is left anywhere.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qa1115hExactOne"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qa1115hExactOne")
	public void twoBuildersSharingExactlyOneHutsStoneAreHonestAndFinishWhenToppedUp(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder first = firstBuilder(helper);
		QaBuilderStockGameTests.Builder second = secondBuilder(helper);
		ServerLevel level = helper.getLevel();
		int need1 = cobbleOf(first.plan());
		int need2 = cobbleOf(second.plan());
		((Container) helper.getBlockEntity(STORE_CHESTS.get(0))).setItem(0, new ItemStack(Items.COBBLESTONE, need1));
		List<BlockPos> all = stockContainers();
		int[] stocked = {need1};
		boolean[] toppedUp = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			int standing = placedOf(level, first.plan(), Items.COBBLESTONE) + placedOf(level, second.plan(), Items.COBBLESTONE);
			int loose = loose(helper, all, Items.COBBLESTONE);
			helper.assertTrue(standing + loose == stocked[0], "tick " + helper.getTick() + ": stocked " + stocked[0] + " cobblestone, now " + standing
				+ " standing + " + loose + " loose = " + (standing + loose));
			if (toppedUp[0] || helper.getTick() < 2500) {
				return;
			}
			helper.assertTrue(!(finished(level, first) && finished(level, second)), "both huts finished with one hut's cobblestone");
			int short1 = 0;
			int short2 = 0;
			int lacks1 = need1 - placedOf(level, first.plan(), Items.COBBLESTONE);
			int lacks2 = need2 - placedOf(level, second.plan(), Items.COBBLESTONE);
			if (!finished(level, first)) {
				Builders.statusText(level, first.site(), first.villager());
				short1 = first.site().missing().getOrDefault(Items.COBBLESTONE, 0);
			}
			if (!finished(level, second)) {
				Builders.statusText(level, second.site(), second.villager());
				short2 = second.site().missing().getOrDefault(Items.COBBLESTONE, 0);
			}
			LOGGER.info("qa 1115h exact one at 2500: first {} lacks {} needs {}, second {} lacks {} needs {}", finished(level, first), lacks1, short1,
				finished(level, second), lacks2, short2);
			helper.assertTrue(short1 <= lacks1, "the first builder's status needs " + short1 + " cobblestone, his hut lacks only " + lacks1);
			helper.assertTrue(short2 <= lacks2, "the second builder's status needs " + short2 + " cobblestone, his hut lacks only " + lacks2);
			helper.assertTrue(short1 + short2 >= need2, "the two statuses need " + short1 + " + " + short2 + " cobblestone, the huts are really short of " + need2);
			Container chest = (Container) helper.getBlockEntity(STORE_CHESTS.get(0));
			int slot = 0;
			while (!chest.getItem(slot).isEmpty()) {
				slot++;
			}
			chest.setItem(slot, new ItemStack(Items.COBBLESTONE, need2));
			chest.setChanged();
			stocked[0] += need2;
			toppedUp[0] = true;
			Village.forget(first.villager());
			Village.forget(second.villager());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(toppedUp[0], "not topped up yet");
			assertBuilt(helper, level, first);
			assertBuilt(helper, level, second);
			int left = loose(helper, all, Items.COBBLESTONE);
			helper.assertTrue(left == 0, "after both huts " + left + " cobblestone are left, wanted 0");
		});
	}

	// ---------------------------------------------------------------------------- save and reload while carrying

	/**
	 * "Don't break existing saves" and 23.1: the Storehouse holds 40 cobblestone. Mid-STRUCTURE, with cobblestone from
	 * the Storehouse in his bag and some already standing, the world is saved and loaded (the build sites and the builder).
	 * Right after, he carries exactly what he carried; at every tick the count is exact (nothing doubled by the load, none
	 * lost); the hut is finished and exactly 40 less the hut's is left.
	 */
	//$ gametest_ticks_batch HUGE '5000' '"qa1115hReloadCarrying"'
	@GameTest(template = HUGE, timeoutTicks = 5000, batch = "qa1115hReloadCarrying")
	public void aBuilderSavedAndLoadedWhileCarryingStorehouseStoneDoublesAndLosesNone(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder first = firstBuilder(helper);
		ServerLevel level = helper.getLevel();
		int need = cobbleOf(first.plan());
		((Container) helper.getBlockEntity(STORE_CHESTS.get(0))).setItem(0, new ItemStack(Items.COBBLESTONE, 40));
		QaBuilderStockGameTests.Builder[] b = {first};
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			int standing = placedOf(level, first.plan(), Items.COBBLESTONE);
			int loose = loose(helper, stockContainers(), Items.COBBLESTONE);
			helper.assertTrue(standing + loose == 40, "tick " + helper.getTick() + " (reloaded " + reloaded[0] + "): stocked 40, now " + standing + " standing + "
				+ loose + " loose");
			if (reloaded[0] || live(level, b[0]).stage() != BuildPlan.Stage.STRUCTURE || standing == 0
				|| ModAttachments.BUILDER_BAG.getOrCreate(b[0].villager()).count(Items.COBBLESTONE) == 0) {
				return;
			}
			int carried = ModAttachments.BUILDER_BAG.getOrCreate(b[0].villager()).count(Items.COBBLESTONE);
			b[0] = reload(helper, level, b[0]);
			reloaded[0] = true;
			int after = ModAttachments.BUILDER_BAG.getOrCreate(b[0].villager()).count(Items.COBBLESTONE);
			LOGGER.info("qa 1115h reload carrying at {}: {} standing, carried {}, after the load {}", helper.getTick(), standing, carried, after);
			helper.assertTrue(after == carried, "he carried " + carried + " cobblestone before the save and " + after + " after the load");
			int loose2 = loose(helper, stockContainers(), Items.COBBLESTONE);
			helper.assertTrue(standing + loose2 == 40, "right after the load: " + standing + " standing + " + loose2 + " loose, stocked 40");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never saved and loaded while carrying");
			QaBuilderStockGameTests.Builder now = new QaBuilderStockGameTests.Builder(b[0].villager(), live(level, b[0]), b[0].plan());
			assertBuilt(helper, level, now);
			int left = loose(helper, stockContainers(), Items.COBBLESTONE);
			helper.assertTrue(left == 40 - need, left + " cobblestone left, wanted " + (40 - need));
		});
	}

	/**
	 * B1187 after a load: two builders share a Storehouse holding exactly both huts' cobblestone. Once the second carries
	 * some from it mid-STRUCTURE, the world is saved and loaded (both sites, the second builder). At every tick the count
	 * is exact; both huts are finished (neither stalls on stone the other's bag held before the load) and none is left.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qa1115hReloadShared"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qa1115hReloadShared")
	public void twoBuildersSharingTheStorehouseBothFinishAfterOneIsReloadedCarrying(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder first = firstBuilder(helper);
		QaBuilderStockGameTests.Builder second = secondBuilder(helper);
		ServerLevel level = helper.getLevel();
		int need1 = cobbleOf(first.plan());
		int need2 = cobbleOf(second.plan());
		((Container) helper.getBlockEntity(STORE_CHESTS.get(0))).setItem(0, new ItemStack(Items.COBBLESTONE, need1 + need2));
		List<BlockPos> all = stockContainers();
		QaBuilderStockGameTests.Builder[] b2 = {second};
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			int standing = placedOf(level, first.plan(), Items.COBBLESTONE) + placedOf(level, second.plan(), Items.COBBLESTONE);
			int loose = loose(helper, all, Items.COBBLESTONE);
			helper.assertTrue(standing + loose == need1 + need2, "tick " + helper.getTick() + " (reloaded " + reloaded[0] + "): stocked " + (need1 + need2)
				+ ", now " + standing + " standing + " + loose + " loose");
			if (reloaded[0] || live(level, b2[0]).stage() != BuildPlan.Stage.STRUCTURE
				|| ModAttachments.BUILDER_BAG.getOrCreate(b2[0].villager()).count(Items.COBBLESTONE) == 0) {
				return;
			}
			b2[0] = reload(helper, level, b2[0]);
			reloaded[0] = true;
			LOGGER.info("qa 1115h reload shared at {}: second carries {}", helper.getTick(), ModAttachments.BUILDER_BAG.getOrCreate(b2[0].villager()).stacks());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never saved and loaded while carrying");
			assertBuilt(helper, level, new QaBuilderStockGameTests.Builder(first.villager(), live(level, first), first.plan()));
			assertBuilt(helper, level, new QaBuilderStockGameTests.Builder(b2[0].villager(), live(level, b2[0]), b2[0].plan()));
			int left = loose(helper, all, Items.COBBLESTONE);
			helper.assertTrue(left == 0, "after both huts " + left + " cobblestone are left, wanted 0");
		});
	}

	// ------------------------------------------------------------------------------------------- free materials on

	/**
	 * config-switches.md: `workplaceFreeMaterials` on, "Builders place blocks without needing materials". The Storehouse
	 * holds 40 cobblestone; the hut is finished, the Storehouse still holds all 40 (he took none of the village's stock),
	 * he carries nothing, and its board never asks for cobblestone while he builds.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qa1115hFree"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qa1115hFree")
	public void withFreeMaterialsTheStorehouseStoneIsLeftAlone(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder b = firstBuilder(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer()));
		((Container) helper.getBlockEntity(STORE_CHESTS.get(0))).setItem(0, new ItemStack(Items.COBBLESTONE, 40));
		Village.forget(b.villager());
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (helper.getTick() % 20 == 0) {
				int store = inContainers(helper, STORE_CHESTS, Items.COBBLESTONE);
				helper.assertTrue(store == 40, "tick " + helper.getTick() + ": with free materials the Storehouse holds " + store + " cobblestone, stocked 40");
				int a = asked(helper, player, Items.COBBLESTONE);
				helper.assertTrue(a == 0, "tick " + helper.getTick() + ": with free materials the board asks for " + a + " cobblestone");
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, level, b);
			helper.assertTrue(inContainers(helper, STORE_CHESTS, Items.COBBLESTONE) == 40, "the Storehouse holds " + inContainers(helper, STORE_CHESTS, Items.COBBLESTONE)
				+ " cobblestone after the free build, stocked 40");
		});
	}
}
