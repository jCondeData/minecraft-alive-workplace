package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.StorehouseBlockEntity;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane b, 2026-11-16, session lane-b-1116-1238) of a Builder supplied by the village Storehouse across a save and
 * reload, from the spec, not the code: wiki builder.md "Whichever table (B290): beyond its bench's chests a Builder
 * counts and takes the village's stock (`Village.stashes`): the overhead Short of line, the status's Needs and the Book
 * and Quill list all count it", "Progress never drops after a restart (B46, B64)", CLAUDE.md "BuilderWork must stay
 * restartable at any tick", ROADMAP 23.1 "no item is duplicated or lost". The layout is QaBuilderStockGameTests': Jesse's
 * Storehouse 24 blocks from his Blueprint Table (well past the table's own chest reach), the table's chest holding the
 * hut's planks, torch and door, and only the Storehouse holding its cobblestone. Gaps the earlier QA left (ROADMAP
 * Notes 2026-11-16, lane-b-1116-0637): QaBuilderSupplies1103 and QaStorehouseFetch1107b never restart the server, and
 * nothing breaks the Storehouse block itself (only its chests) mid-job. Every test counts the cobblestone at every tick:
 * what stands in the hut plus what is in the chests, bags and on the ground is exactly what was stocked.
 */
public class QaStorehouseSupply1116bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos STOREHOUSE = new BlockPos(26, 2, 26);
	private static final List<BlockPos> STORE_CHESTS = List.of(new BlockPos(23, 2, 26), new BlockPos(26, 2, 23));
	private static final UUID JESSE = UUID.fromString("5f1b2c3d-0000-4000-8000-0000000b1024");

	// ------------------------------------------------------------------------------------------------------ helpers

	private static final BuildPlan.Stage[] BUILT = {BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION};

	private static Set<BlockPos> cobbleSpots(BuildPlan plan) {
		Set<BlockPos> out = new HashSet<>();
		for (BuildPlan.Stage stage : BUILT) {
			for (BuildPlan.Step s : plan.steps(stage)) {
				if (!s.state().isAir() && s.state().getBlock().asItem() == Items.COBBLESTONE) {
					out.add(s.pos().immutable());
				}
			}
		}
		return out;
	}

	private static int standing(ServerLevel level, BuildPlan plan) {
		int n = 0;
		for (BlockPos p : cobbleSpots(plan)) {
			if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.COBBLESTONE)) {
				n++;
			}
		}
		return n;
	}

	/** Cobblestone in the Storehouse's and the table's chests, every villager's bag and inventory near the test, and on the ground. */
	private static int loose(GameTestHelper helper) {
		int n = 0;
		for (BlockPos p : List.of(STORE_CHESTS.get(0), STORE_CHESTS.get(1), QaBuilderStockGameTests.TABLE_CHEST)) {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(p)) instanceof Container c) {
				n += c.countItem(Items.COBBLESTONE);
			}
		}
		AABB around = helper.getBounds().inflate(16);
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, around)) {
			n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(Items.COBBLESTONE);
			n += v.getInventory().countItem(Items.COBBLESTONE);
		}
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, around)) {
			if (e.getItem().is(Items.COBBLESTONE)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	private static int need(BuildPlan plan) {
		Integer listed = plan.materials().get(Items.COBBLESTONE);
		if (listed == null || listed != cobbleSpots(plan).size()) {
			throw new GameTestAssertException("setup: the test hut lists " + listed + " cobblestone but draws " + cobbleSpots(plan).size());
		}
		return listed;
	}

	/** The run: the builder (replaced by a fresh entity on a restart), the site's id and its plan. */
	private record Run(ServerLevel level, Villager[] builder, UUID site, BuildPlan plan) {
		Villager villager() {
			return builder[0];
		}

		BuildSite live() {
			return BuildSiteManager.get(level).get(site);
		}

		String where() {
			BuildSite s = live();
			return s == null ? "(no site)" : s.stage() + " " + s.status() + " cursor " + s.cursor() + " placed " + s.placed() + " missing " + s.missing()
				+ " bag " + ModAttachments.BUILDER_BAG.getOrCreate(villager()).stacks().stream().filter(st -> !st.isEmpty()).toList();
		}
	}

	/** Jesse's Storehouse with {@code cobble} cobblestone (and the hut's door), the table's chest with planks and a torch, his builder on the hut. */
	private static Run start(GameTestHelper helper, int cobble) {
		ServerLevel level = QaBuilderStockGameTests.setup(helper, STOREHOUSE, 0, 1);
		for (BlockPos p : STORE_CHESTS) {
			((Container) helper.getBlockEntity(p)).clearContent();
		}
		((Container) helper.getBlockEntity(STORE_CHESTS.get(1))).setItem(0, new ItemStack(Items.OAK_DOOR));
		if (cobble > 0) {
			((Container) helper.getBlockEntity(STORE_CHESTS.get(0))).setItem(0, new ItemStack(Items.COBBLESTONE, cobble));
		}
		QaBuilderStockGameTests.fill(helper.getBlockEntity(QaBuilderStockGameTests.TABLE_CHEST), new ItemStack(Items.OAK_PLANKS, 60), new ItemStack(Items.TORCH));
		QaBuilderStockGameTests.Builder b = QaBuilderStockGameTests.builder(helper, QaBuilderStockGameTests.HUT);
		Village.forget(b.villager());
		UUID id = b.site().id();
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(id) != null) {
				BuildSiteManager.get(level).remove(id);
			}
		});
		return new Run(level, new Villager[]{b.villager()}, id, b.plan());
	}

	/** A server restart: every build site and the Builder saved and loaded again (the Builder as a fresh entity). */
	private static void restart(GameTestHelper helper, Run r) {
		ServerLevel level = r.level();
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		Villager old = r.builder()[0];
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
		level.addFreshEntity(loaded);
		r.builder()[0] = (Villager) loaded;
		Village.forget(r.villager());
	}

	/** The restart itself keeps the site's stage, cursor and progress, and the cobblestone the Builder carries. */
	private static void restartChecked(GameTestHelper helper, Run r, String when) {
		BuildSite before = r.live();
		helper.assertTrue(before != null, when + ": no site to restart");
		BuildPlan.Stage stage = before.stage();
		int cursor = before.cursor();
		int placed = before.placed();
		int bag = ModAttachments.BUILDER_BAG.getOrCreate(r.villager()).count(Items.COBBLESTONE);
		int loose = loose(helper);
		restart(helper, r);
		BuildSite after = r.live();
		helper.assertTrue(after != null && after.stage() == stage && after.placed() == placed
				&& (stage == BuildPlan.Stage.FOUNDATION || stage == BuildPlan.Stage.LANDSCAPE || after.cursor() == cursor),
			"a restart " + when + " moved the site from " + stage + " cursor " + cursor + " placed " + placed + " to " + r.where());
		int bagAfter = ModAttachments.BUILDER_BAG.getOrCreate(r.villager()).count(Items.COBBLESTONE);
		helper.assertTrue(bagAfter == bag, "a restart " + when + " changed the cobblestone in the bag from " + bag + " to " + bagAfter);
		helper.assertTrue(loose(helper) == loose, "a restart " + when + " changed the loose cobblestone from " + loose + " to " + loose(helper));
		r.level().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[qa-storehouse-1116b] restarted " + when + ": " + stage
			+ " cursor " + cursor + " placed " + placed + " bag " + bag));
	}

	private static void assertBuilt(GameTestHelper helper, Run r) {
		helper.assertTrue(r.live() == null, "still building: " + r.where());
		List<BlockPos> wrong = r.plan().unfinished(r.level());
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut not as drawn");
		int bag = 0;
		for (ItemStack s : ModAttachments.BUILDER_BAG.getOrCreate(r.villager()).stacks()) {
			bag += s.getCount();
		}
		helper.assertTrue(bag == 0, "the finished builder still carries " + ModAttachments.BUILDER_BAG.getOrCreate(r.villager()).stacks());
	}

	private static void countEveryTick(GameTestHelper helper, Run r, int[] stocked) {
		int standing = standing(r.level(), r.plan());
		int loose = loose(helper);
		helper.assertTrue(standing + loose == stocked[0], "tick " + helper.getTick() + ": stocked " + stocked[0] + ", now " + standing + " standing + " + loose
			+ " loose (" + r.where() + ")");
	}

	// ------------------------------------------------------------------------------------------------------ the tests

	/**
	 * The Storehouse holds the hut's cobblestone and 8 more. The server is restarted the first tick the Builder carries
	 * Storehouse cobblestone mid-STRUCTURE (5 or more blocks in): the restart keeps the site's place and his bag, and he finishes the hut from
	 * it, the Storehouse ending with exactly the 8 and nothing in his bag or on the ground.
	 */
	//$ gametest_ticks_batch HUGE '5000' '"qaStore1116bReload"'
	@GameTest(template = HUGE, timeoutTicks = 5000, batch = "qaStore1116bReload")
	public void aRestartWithStorehouseCobblestoneInTheBagFinishesTheHutWithNothingLostOrDoubled(GameTestHelper helper) {
		Run probe = start(helper, 0);
		int need = need(probe.plan());
		Container store = helper.getBlockEntity(STORE_CHESTS.get(0));
		store.setItem(0, new ItemStack(Items.COBBLESTONE, need + 8));
		int[] stocked = {need + 8};
		boolean[] restarted = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			countEveryTick(helper, probe, stocked);
			BuildSite site = probe.live();
			if (!restarted[0] && site != null && site.stage() == BuildPlan.Stage.STRUCTURE && site.placed() >= 5
				&& ModAttachments.BUILDER_BAG.getOrCreate(probe.villager()).count(Items.COBBLESTONE) > 0) {
				restartChecked(helper, probe, "with Storehouse cobblestone in the bag");
				restarted[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0], "the builder never carried the Storehouse's cobblestone mid-STRUCTURE: " + probe.where());
			assertBuilt(helper, probe);
			int left = ((Container) helper.getBlockEntity(STORE_CHESTS.get(0))).countItem(Items.COBBLESTONE);
			helper.assertTrue(left == 8 && loose(helper) == 8, "after the hut the Storehouse holds " + left + " cobblestone and " + loose(helper)
				+ " are loose in all; wanted exactly the 8 over");
		});
	}

	/**
	 * The Storehouse partly stocked: 10 short of the hut's cobblestone. The Builder builds what he can and waits, the
	 * status saying it needs cobblestone and the site missing exactly 10 (the Storehouse's stock counted, B290). A restart
	 * while he waits keeps the site's place, and he waits on for the same 10. Ten more put in the Storehouse's chest, he
	 * finishes the hut with none left over anywhere.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaStore1116bPartly"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaStore1116bPartly")
	public void aPartlyStockedStorehouseWaitsForTheRestThroughARestartAndFinishesWhenToppedUp(GameTestHelper helper) {
		Run r = start(helper, 0);
		int need = need(r.plan());
		((Container) helper.getBlockEntity(STORE_CHESTS.get(0))).setItem(0, new ItemStack(Items.COBBLESTONE, need - 10));
		int[] stocked = {need - 10};
		int[] waited = {0};
		boolean[] restarted = {false};
		boolean[] toppedUp = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			countEveryTick(helper, r, stocked);
			BuildSite site = r.live();
			if (toppedUp[0] || site == null) {
				return;
			}
			if (site.status() != BuildSite.Status.WAITING_FOR_MATERIALS) {
				waited[0] = 0;
				return;
			}
			if (++waited[0] < 100) {
				return;
			}
			String status = Builders.statusText(r.level(), site, r.villager()).getString();
			helper.assertTrue(status.contains("Needs") && status.contains("Cobblestone"), "waiting 10 cobblestone short, the status reads: " + status);
			helper.assertTrue(site.missing().getOrDefault(Items.COBBLESTONE, 0) == 10, "10 short with the Storehouse's stock counted, the site is missing "
				+ site.missing());
			helper.assertTrue(standing(r.level(), r.plan()) == need - 10, "waiting, " + standing(r.level(), r.plan()) + " cobblestone stand in the hut; the Storehouse's "
				+ (need - 10) + " should all be used first");
			if (!restarted[0]) {
				restartChecked(helper, r, "while waiting for the last 10");
				restarted[0] = true;
				waited[0] = 0;
				return;
			}
			((Container) helper.getBlockEntity(STORE_CHESTS.get(0))).setItem(5, new ItemStack(Items.COBBLESTONE, 10));
			stocked[0] += 10;
			toppedUp[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0] && toppedUp[0], "the builder never waited (twice) for the last 10: restarted " + restarted[0] + ", " + r.where());
			assertBuilt(helper, r);
			helper.assertTrue(loose(helper) == 0, loose(helper) + " cobblestone left over after the hut");
		});
	}

	/**
	 * The Storehouse block itself (not its chests) broken mid-STRUCTURE, with the server restarted while it is gone,
	 * then put back (Jesse's again): the cobblestone count is exact at every tick (nothing lost, doubled or made), the
	 * site's progress never drops, and the hut is finished once the Storehouse is back, nothing left over.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaStore1116bBroken"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaStore1116bBroken")
	public void theStorehouseBrokenMidJobThenRestoredLosesNothingAndTheHutFinishes(GameTestHelper helper) {
		Run r = start(helper, 0);
		int need = need(r.plan());
		((Container) helper.getBlockEntity(STORE_CHESTS.get(0))).setItem(0, new ItemStack(Items.COBBLESTONE, need));
		int[] stocked = {need};
		long[] brokeAt = {-1};
		int[] placedAt = {-1};
		boolean[] restarted = {false};
		boolean[] back = {false};
		boolean[] finishedWithout = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			countEveryTick(helper, r, stocked);
			BuildSite site = r.live();
			if (brokeAt[0] < 0) {
				if (site != null && site.stage() == BuildPlan.Stage.STRUCTURE && standing(r.level(), r.plan()) >= 4) {
					placedAt[0] = site.placed();
					r.level().destroyBlock(helper.absolutePos(STOREHOUSE), false);
					Village.forget(r.villager());
					brokeAt[0] = helper.getTick();
				}
				return;
			}
			if (back[0]) {
				return;
			}
			if (site == null) {
				// (he may finish from what he carried and what the chests by the broken Storehouse still hold: then the hut must be whole)
				helper.assertTrue(r.plan().unfinished(r.level()).isEmpty(), "the Storehouse broken mid-job removed the hut's site with "
					+ r.plan().unfinished(r.level()).size() + " block(s) of it not built");
				finishedWithout[0] = true;
				r.level().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[qa-storehouse-1116b] the hut finished "
					+ (helper.getTick() - brokeAt[0]) + " ticks after the Storehouse broke, restarted " + restarted[0]));
				back[0] = true;
				return;
			}
			helper.assertTrue(site.placed() >= placedAt[0], "the Storehouse broken mid-job dropped the hut's progress from " + placedAt[0] + " to " + site.placed());
			if (!restarted[0] && helper.getTick() - brokeAt[0] >= 100) {
				restartChecked(helper, r, "with the Storehouse broken (" + site.status() + ", missing " + site.missing() + ")");
				restarted[0] = true;
			}
			if (helper.getTick() - brokeAt[0] >= 400) {
				helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
				if (r.level().getPoiManager().getType(helper.absolutePos(STOREHOUSE)).isEmpty()) {
					net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(helper.getBlockState(STOREHOUSE))
						.ifPresent(type -> r.level().getPoiManager().add(helper.absolutePos(STOREHOUSE), type));
				}
				((StorehouseBlockEntity) helper.getBlockEntity(STOREHOUSE)).setOwner(JESSE, "Jesse");
				Village.forget(r.villager());
				back[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(back[0] && (restarted[0] || finishedWithout[0]), "the Storehouse isn't back yet: " + r.where());
			assertBuilt(helper, r);
			helper.assertTrue(loose(helper) == 0, loose(helper) + " cobblestone left over after the hut");
		});
	}

	/**
	 * The Storehouse block broken before the Builder has fetched any of its cobblestone: its chests, 24 blocks from his
	 * table and no village-mate's, are no longer a Storehouse's, so he builds what he can from his table's chest and then
	 * waits, the status saying it needs cobblestone. The server is restarted while he waits; he waits on. With the
	 * Storehouse put back (Jesse's again) he takes the cobblestone and finishes the hut, none left over, the count exact
	 * at every tick.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaStore1116bGoneFirst"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaStore1116bGoneFirst")
	public void aStorehouseBrokenBeforeHeFetchesLeavesHimWaitingThroughARestartUntilItIsBack(GameTestHelper helper) {
		Run r = start(helper, 0);
		int need = need(r.plan());
		((Container) helper.getBlockEntity(STORE_CHESTS.get(0))).setItem(0, new ItemStack(Items.COBBLESTONE, need));
		helper.getLevel().destroyBlock(helper.absolutePos(STOREHOUSE), false);
		Village.forget(r.villager());
		int[] stocked = {need};
		int[] waited = {0};
		boolean[] restarted = {false};
		boolean[] back = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			countEveryTick(helper, r, stocked);
			BuildSite site = r.live();
			if (back[0]) {
				return;
			}
			helper.assertTrue(site != null, "with the Storehouse gone the hut was finished from its chests anyway: they're no longer a Storehouse's ("
				+ standing(r.level(), r.plan()) + " cobblestone stand)");
			if (site.status() != BuildSite.Status.WAITING_FOR_MATERIALS) {
				waited[0] = 0;
				return;
			}
			if (++waited[0] < 100) {
				return;
			}
			String status = Builders.statusText(r.level(), site, r.villager()).getString();
			helper.assertTrue(status.contains("Needs") && status.contains("Cobblestone"), "waiting with the Storehouse gone, the status reads: " + status);
			if (!restarted[0]) {
				restartChecked(helper, r, "waiting with the Storehouse gone (" + site.missing() + ", " + standing(r.level(), r.plan()) + " cobblestone stand)");
				restarted[0] = true;
				waited[0] = 0;
				return;
			}
			helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
			if (r.level().getPoiManager().getType(helper.absolutePos(STOREHOUSE)).isEmpty()) {
				net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(helper.getBlockState(STOREHOUSE))
					.ifPresent(type -> r.level().getPoiManager().add(helper.absolutePos(STOREHOUSE), type));
			}
			((StorehouseBlockEntity) helper.getBlockEntity(STOREHOUSE)).setOwner(JESSE, "Jesse");
			Village.forget(r.villager());
			back[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(back[0], "the Storehouse isn't back yet: restarted " + restarted[0] + ", " + r.where());
			assertBuilt(helper, r);
			helper.assertTrue(loose(helper) == 0, loose(helper) + " cobblestone left over after the hut");
		});
	}
}
