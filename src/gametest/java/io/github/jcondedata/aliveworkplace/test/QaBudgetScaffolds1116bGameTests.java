package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildBudget;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-11-16): the per-tick block-change budget ({@link BuildBudget}, 12 changes a level a tick; past it a builder
 * waits a tick, nothing is lost, the budget is never saved) meeting scaffolding (ROADMAP 35.6, {@code Scaffolds}: every
 * scaffold is recorded in the site, taken down when the stage is done and, when the build is cancelled or ends any other
 * way, taken down at once into the supply chests; none left standing or lost). The builder works on the test pillar
 * (14 high with an overhanging cap) from a chest holding exactly its blocks and 64 scaffolding.
 */
public class QaBudgetScaffolds1116bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final ResourceLocation PILLAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");
	private static final BlockPos BENCH = new BlockPos(3, 2, 3);
	private static final BlockPos CHEST = new BlockPos(3, 2, 5);
	private static final BlockPos VILLAGER = new BlockPos(4, 2, 4);
	private static final BlockPos PILLAR_AT = new BlockPos(11, 2, 11);
	private static final int SCAFFOLDING = 64;
	/** Ticks the budget stays spent after a cancel or a death. */
	private static final int STARVE = 40;

	private static final class Site {
		final ServerLevel level;
		final Villager[] builder;
		final UUID id;
		BuildSite site;
		final BuildPlan plan;

		Site(ServerLevel level, Villager builder, BuildSite site, BuildPlan plan) {
			this.level = level;
			this.builder = new Villager[] {builder};
			this.id = site.id();
			this.site = site;
			this.plan = plan;
		}

		BuildSite live() {
			return BuildSiteManager.get(level).get(id);
		}
	}

	private static void log(GameTestHelper helper, String line) {
		helper.getLevel().getServer().sendSystemMessage(Component.literal("[qa-budget-scaffolds-1116b] " + line));
	}

	/** Takes every block change {@code level} allows at {@code gameTime}. */
	private static void spend(ServerLevel level, long gameTime) {
		int guard = 0;
		while (BuildBudget.take(level, gameTime) && guard++ < 1000) {
			// spend it all
		}
	}

	// ------------------------------------------------------------------------------------------------ cancel

	/**
	 * Cancelled with 5 or more scaffolds up in a tick whose budget is already spent (and kept spent 40 ticks more): every scaffold
	 * is down that same tick, the site lists none, and the chest holds all 64 again; still none standing 40 ticks later.
	 */
	//$ gametest_ticks_batch BIG '6000' '"qaBudgetScaffoldsCancel"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "qaBudgetScaffoldsCancel")
	public void cancelledInASpentTickTakesEveryScaffoldDown(GameTestHelper helper) {
		Site s = pillar(helper);
		int[] cancelledWith = {0};
		int[] after = {-1};
		helper.onEachTick(() -> {
			long now = s.level.getGameTime();
			if (after[0] >= 0) {
				if (after[0] < STARVE) {
					spend(s.level, now + 1);
					after[0]++;
				}
				return;
			}
			if (s.site.scaffolds().size() >= 5) {
				cancelledWith[0] = s.site.scaffolds().size();
				spend(s.level, now);
				helper.assertFalse(BuildBudget.take(s.level, now), "couldn't spend the tick's budget before the cancel");
				Builders.cancel(s.level, s.site);
				assertNoScaffolding(helper, s, "the tick of the cancel");
				helper.assertTrue(s.site.scaffolds().isEmpty(), "the cancelled site still lists scaffolds: " + s.site.scaffolds());
				helper.assertTrue(chestCount(helper) == SCAFFOLDING, "the chest holds " + chestCount(helper) + " scaffolding the tick of the cancel, not "
					+ SCAFFOLDING + " (cancelled with " + cancelledWith[0] + " up)");
				spend(s.level, now + 1);
				after[0] = 0;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(cancelledWith[0] > 0, "fewer than 5 scaffolds up yet: " + s.site.scaffolds().size());
			helper.assertTrue(after[0] >= STARVE, "still starving: " + after[0]);
			helper.assertTrue(s.live() == null, "the build wasn't cancelled");
			assertNoScaffolding(helper, s, STARVE + " ticks after the cancel");
			helper.assertTrue(chestCount(helper) == SCAFFOLDING, "the chest holds " + chestCount(helper) + " scaffolding, not " + SCAFFOLDING);
			log(helper, "cancel in a spent tick with " + cancelledWith[0] + " scaffolds up: none standing that tick or " + STARVE
				+ " ticks later, chest " + chestCount(helper) + "/" + SCAFFOLDING);
		});
	}

	// ------------------------------------------------------------------------------------------------ death

	/**
	 * The builder killed with 5 or more scaffolds up, the level's budget spent that tick and the next 40: the site goes within that
	 * window, none is left standing, and all 64 are in the chest or lying on the ground (what his bag held drops).
	 */
	//$ gametest_ticks_batch BIG '6000' '"qaBudgetScaffoldsDeath"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "qaBudgetScaffoldsDeath")
	public void theBuilderKilledInSpentTicksLeavesNoScaffoldUp(GameTestHelper helper) {
		Site s = pillar(helper);
		int[] killedWith = {0};
		int[] after = {-1};
		int[] goneAt = {-1};
		helper.onEachTick(() -> {
			long now = s.level.getGameTime();
			if (after[0] >= 0) {
				if (goneAt[0] < 0 && s.live() == null) {
					goneAt[0] = after[0];
					assertNoScaffolding(helper, s, "the tick the dead builder's site went (" + after[0] + " ticks after, budget spent)");
				}
				if (after[0] < STARVE) {
					spend(s.level, now + 1);
					after[0]++;
				}
				return;
			}
			if (s.site.scaffolds().size() >= 5) {
				killedWith[0] = s.site.scaffolds().size();
				spend(s.level, now);
				s.builder[0].kill();
				spend(s.level, now + 1);
				after[0] = 0;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(killedWith[0] > 0, "fewer than 5 scaffolds up yet: " + s.site.scaffolds().size());
			helper.assertTrue(after[0] >= STARVE, "still starving: " + after[0]);
			helper.assertTrue(goneAt[0] >= 0 && goneAt[0] < STARVE, "the dead builder's site wasn't removed while the budget was spent: " + goneAt[0]);
			assertNoScaffolding(helper, s, "after the death");
			int loose = loose(helper, s);
			helper.assertTrue(chestCount(helper) + loose == SCAFFOLDING, "scaffolding after the builder died: " + chestCount(helper) + " in the chest and "
				+ loose + " on the ground, not " + SCAFFOLDING + " (killed with " + killedWith[0] + " up)");
			log(helper, "killed with " + killedWith[0] + " up, budget spent: site gone " + goneAt[0] + " ticks later, none standing, chest "
				+ chestCount(helper) + " + ground " + loose + " = " + SCAFFOLDING);
		});
	}

	// ------------------------------------------------------------------------------------------------ throttled build

	/**
	 * The whole pillar built with the level's budget spent every other tick: scaffolding still goes up and all comes
	 * down when the stage is done, the pillar is exact with nothing skipped, all 64 are back in the chest, and in no
	 * spent tick did the site's scaffold list change.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaBudgetScaffoldsHalf"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaBudgetScaffoldsHalf")
	public void aPillarBuiltOnHalfTheTicksTakesEveryScaffoldDown(GameTestHelper helper) {
		Site s = pillar(helper);
		int[] most = {0};
		int[] last = {0};
		int[] downs = {0};
		int[] spentTicks = {0};
		long[] spentFor = {-1};
		helper.onEachTick(() -> {
			long now = s.level.getGameTime();
			int size = s.site.scaffolds().size();
			if (spentFor[0] == now && s.live() != null) {
				helper.assertTrue(size == last[0], "the scaffold list changed from " + last[0] + " to " + size + " in a tick with the budget spent");
			}
			if (size < last[0]) {
				downs[0] += last[0] - size;
			}
			last[0] = size;
			most[0] = Math.max(most[0], size);
			if ((now + 1) % 2 == 0) {
				spend(s.level, now + 1);
				spentFor[0] = now + 1;
				spentTicks[0]++;
			}
		});
		helper.succeedWhen(() -> {
			assertFinished(helper, s);
			helper.assertTrue(most[0] > 0, "no scaffolding was put up");
			helper.assertTrue(s.site.skipped() == 0, s.site.skipped() + " step(s) skipped");
			assertNoScaffolding(helper, s, "the finished pillar");
			helper.assertTrue(s.site.scaffolds().isEmpty(), "the finished site still lists scaffolds: " + s.site.scaffolds());
			helper.assertTrue(chestCount(helper) == SCAFFOLDING, "the chest holds " + chestCount(helper) + " scaffolding, not " + SCAFFOLDING);
			log(helper, "half the ticks spent (" + spentTicks[0] + "): up to " + most[0] + " scaffolds, " + downs[0]
				+ " taken down, pillar exact, 0 skipped, chest " + chestCount(helper) + "/" + SCAFFOLDING);
		});
	}

	// ------------------------------------------------------------------------------------------------ reload mid take-down

	/**
	 * The stage's take-down starts (the scaffold list first shrinks), then the budget is spent every tick for 30 ticks and
	 * at the 10th the server restarts (sites and builder saved and loaded). Afterwards the pillar finishes exactly, no
	 * scaffold stands and all 64 are back in the chest.
	 */
	//$ gametest_ticks_batch BIG '9000' '"qaBudgetScaffoldsReload"'
	@GameTest(template = BIG, timeoutTicks = 9000, batch = "qaBudgetScaffoldsReload")
	public void aRestartWhileTheTakeDownIsThrottledLosesNone(GameTestHelper helper) {
		Site s = pillar(helper);
		int[] last = {0};
		int[] throttle = {-1};
		int[] reloadedWith = {-1};
		helper.onEachTick(() -> {
			if (s.live() == null) {
				return;
			}
			long now = s.level.getGameTime();
			int size = s.site.scaffolds().size();
			if (throttle[0] < 0 && size < last[0] && size > 0) {
				throttle[0] = 0;
			}
			last[0] = size;
			if (throttle[0] >= 0 && throttle[0] < 30) {
				if (throttle[0] == 10) {
					reloadedWith[0] = s.site.scaffolds().size();
					restart(helper, s);
					helper.assertTrue(s.site.scaffolds().size() == reloadedWith[0], "the restart changed the site's scaffolds from " + reloadedWith[0]
						+ " to " + s.site.scaffolds().size());
					last[0] = s.site.scaffolds().size();
				}
				spend(s.level, now + 1);
				throttle[0]++;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloadedWith[0] > 0, "no restart during a take-down with scaffolds up: " + reloadedWith[0]);
			assertFinished(helper, s);
			helper.assertTrue(s.site.skipped() == 0, s.site.skipped() + " step(s) skipped");
			assertNoScaffolding(helper, s, "after the restart");
			helper.assertTrue(s.site.scaffolds().isEmpty(), "the finished site still lists scaffolds: " + s.site.scaffolds());
			int loose = loose(helper, s);
			helper.assertTrue(chestCount(helper) == SCAFFOLDING, "the chest holds " + chestCount(helper) + " scaffolding (and " + loose
				+ " on the ground), not " + SCAFFOLDING + " (restarted with " + reloadedWith[0] + " up)");
			log(helper, "restart mid take-down with " + reloadedWith[0] + " up and the budget spent: pillar exact, 0 skipped, none standing, chest "
				+ chestCount(helper) + "/" + SCAFFOLDING);
		});
	}

	// ------------------------------------------------------------------------------------------------ helpers

	/** A server restart: the build sites and the builder saved and loaded (the builder a fresh entity). */
	private static void restart(GameTestHelper helper, Site s) {
		ServerLevel level = s.level;
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		BuildSite back = s.live();
		helper.assertTrue(back != null && back != s.site, "the site didn't come back from the restart as a new object: " + back);
		s.site = back;
		Villager old = s.builder[0];
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
		level.addFreshEntity(loaded);
		s.builder[0] = (Villager) loaded;
	}

	/** A builder on the pillar, its chest holding exactly the pillar's blocks and 64 scaffolding. */
	private static Site pillar(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, PILLAR,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(PILLAR_AT), Rotation.NONE, Mirror.NONE));
		if (site == null) {
			throw new GameTestAssertException("the build of " + PILLAR + " didn't start");
		}
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + PILLAR);
		}
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.STONE_BRICKS, 14));
		chest.setItem(1, new ItemStack(Items.POLISHED_ANDESITE, 25));
		chest.setItem(2, new ItemStack(Items.SCAFFOLDING, SCAFFOLDING));
		return new Site(level, villager, site, plan);
	}

	private static void assertFinished(GameTestHelper helper, Site s) {
		BuildSite live = s.live();
		helper.assertTrue(live == null, "still building: stage " + s.site.stage() + " status " + s.site.status() + " scaffolds "
			+ s.site.scaffolds().size() + " builder at " + helper.relativePos(s.builder[0].blockPosition()));
		List<BlockPos> unfinished = s.plan.unfinished(s.level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong, e.g. "
			+ unfinished.stream().limit(4).map(p -> helper.relativePos(p) + "=" + s.level.getBlockState(p)).toList());
	}

	/** Not a scaffold standing anywhere in the area (big_area is 18 tall; the column can reach above it, so up to 29). */
	private static void assertNoScaffolding(GameTestHelper helper, Site s, String when) {
		BlockPos low = helper.absolutePos(BlockPos.ZERO);
		BlockPos high = helper.absolutePos(new BlockPos(21, 29, 21));
		for (BlockPos p : BlockPos.betweenClosed(low, high)) {
			if (s.level.getBlockState(p).is(Blocks.SCAFFOLDING)) {
				throw new GameTestAssertException("scaffolding left standing at " + helper.relativePos(p) + " (" + when + ")");
			}
		}
	}

	private static int loose(GameTestHelper helper, Site s) {
		int n = 0;
		for (ItemEntity item : s.level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(32, 32, 32))) {
			n += item.getItem().is(Items.SCAFFOLDING) ? item.getItem().getCount() : 0;
		}
		return n;
	}

	private static int chestCount(GameTestHelper helper) {
		Container chest = helper.getBlockEntity(CHEST);
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			ItemStack stack = chest.getItem(i);
			n += stack.is(Items.SCAFFOLDING) ? stack.getCount() : 0;
		}
		return n;
	}
}
