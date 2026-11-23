package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.gem.GemGrowers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1123-0953) of ROADMAP 28.11, the Gem Grower, from docs/wiki/gem-grower.md. Not covered before: her status
 * lines as a player reads them over her head ("Tending 2 gem beds", "No gem beds within 16 blocks...", "Her stonecutter
 * is gone", "She has no chest..."), her title after one pick read as one, a budding block broken mid-job forgotten, the
 * switch turned off mid-job and back on, and a grower saved and reloaded mid-job with her picking still in her hands.
 * Random ticks are off in every test so no bud grows on its own.
 */
public class QaGemGrower1123cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos CUTTER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos BUDDING = new BlockPos(8, 2, 2);
	private static final BlockPos BUDDING_2 = new BlockPos(8, 2, 6);

	/**
	 * Clears what an earlier batch left within her scan (16 blocks round the stonecutter, which reaches outside the test
	 * area): budding amethyst and its buds, and containers. Only for tests alone in their batch.
	 */
	static void clearAround(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.containers(helper, 20);
		ServerLevel level = helper.getLevel();
		net.minecraft.world.phys.AABB area = helper.getBounds();
		net.minecraft.world.phys.AABB around = area.inflate(20);
		for (BlockPos p : BlockPos.betweenClosed((int) around.minX, (int) Math.max(around.minY, level.getMinBuildHeight()), (int) around.minZ,
				(int) around.maxX, (int) around.maxY, (int) around.maxZ)) {
			if (area.contains(net.minecraft.world.phys.Vec3.atCenterOf(p))) {
				continue;
			}
			net.minecraft.world.level.block.state.BlockState s = level.getBlockState(p);
			if (s.is(Blocks.BUDDING_AMETHYST) || s.getBlock() instanceof AmethystClusterBlock) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
			}
		}
	}

	static void noRandomTicks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GameRules.IntegerValue ticking = level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
		int was = ticking.get();
		ticking.set(0, level.getServer());
		Leftovers.after(helper, () -> ticking.set(was, level.getServer()));
	}

	/** A stonecutter (and no chest) with a villager made its Gem Grower by a player's sneak-click with an amethyst shard. */
	private static Villager growerWithoutChest(GameTestHelper helper) {
		helper.setDayTime(2000);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.AMETHYST_SHARD));
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		InteractionResult picked = Stations.choose(player, villager, player.getMainHandItem());
		helper.assertTrue(picked == InteractionResult.SUCCESS, "the shard picked no job: " + picked);
		helper.assertTrue(GemGrowers.isGrower(villager), "not a gem grower: " + villager.getVillagerData().getProfession());
		return villager;
	}

	static void cluster(GameTestHelper helper, BlockPos pos, Direction facing) {
		helper.setBlock(pos, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, facing));
	}

	/** What she says over her head (the line), or "" with no status yet. */
	static String line(GameTestHelper helper, Villager grower) {
		WorkerStatus.Entry e = WorkerStatus.get(grower, helper.getLevel().getGameTime());
		return e == null ? "" : e.line().getString();
	}

	private static String title(GameTestHelper helper, Villager grower) {
		WorkerStatus.Entry e = WorkerStatus.get(grower, helper.getLevel().getGameTime());
		return e == null ? "" : e.title().getString();
	}

	static String said(GameTestHelper helper, Villager grower) {
		return "'" + line(helper, grower) + "' (title '" + title(helper, grower) + "') at " + helper.relativePos(grower.blockPosition())
			+ ", beds " + GemGrowers.beds(grower) + ", bag " + ModAttachments.BUILDER_BAG.getOrCreate(grower).stacks();
	}

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	/** Two budding blocks with nothing ripe on them: once both are found she reads "Tending 2 gem beds", plural. */
	//$ gametest_ticks_batch AREA '1200' '"qaGem1123cTwo"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaGem1123cTwo")
	public void qaTwoBedsReadTendingTwoGemBeds(GameTestHelper helper) {
		clearAround(helper);
		noRandomTicks(helper);
		helper.setBlock(BUDDING, Blocks.BUDDING_AMETHYST);
		helper.setBlock(BUDDING_2, Blocks.BUDDING_AMETHYST);
		Villager grower = GemGrowerGameTests.grower(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(GemGrowers.beds(grower).size() == 2, "beds found: " + said(helper, grower));
			helper.assertTrue(line(helper, grower).equals("Tending 2 gem beds"), "she says " + said(helper, grower));
			helper.assertTrue(title(helper, grower).equals("Gem Grower · 0 clusters picked"), "her title: " + said(helper, grower));
		});
	}

	/** No budding amethyst anywhere near: she says exactly which blocks make a bed, as a warning. */
	//$ gametest_ticks_batch AREA '1200' '"qaGem1123cNoBeds"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaGem1123cNoBeds")
	public void qaNoBedsNearSaysWhatMakesOne(GameTestHelper helper) {
		clearAround(helper);
		noRandomTicks(helper);
		Villager grower = GemGrowerGameTests.grower(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(GemGrowers.beds(grower).isEmpty(), "beds found in an empty area: " + GemGrowers.beds(grower));
			helper.assertTrue(line(helper, grower).equals(
				"No gem beds within 16 blocks of her stonecutter (budding amethyst, magma or lava, a crystal core)"), "she says " + said(helper, grower));
		});
	}

	/**
	 * A bed found, then its budding block broken mid-job by a player: she forgets it (her remembered beds drop to none)
	 * and says there are no beds, rather than tending a bed that is gone.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaGem1123cBroken"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaGem1123cBroken")
	public void qaABuddingBlockBrokenMidJobIsForgotten(GameTestHelper helper) {
		clearAround(helper);
		noRandomTicks(helper);
		helper.setBlock(BUDDING, Blocks.BUDDING_AMETHYST);
		Villager grower = GemGrowerGameTests.grower(helper);
		boolean[] broken = {false};
		helper.onEachTick(() -> {
			if (!broken[0] && GemGrowers.beds(grower).size() == 1) {
				helper.setBlock(BUDDING, Blocks.AIR);
				broken[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0], "the bed was never found: " + said(helper, grower));
			helper.assertTrue(GemGrowers.beds(grower).isEmpty(), "she still remembers the broken bed: " + said(helper, grower));
			helper.assertTrue(line(helper, grower).startsWith("No gem beds within 16 blocks"), "she says " + said(helper, grower));
		});
	}

	/**
	 * The switch turned off mid-job and back on: while off the ripe cluster stays and she says she's switched off; once on
	 * again she picks it (the grower already hired stood idle, she wasn't fired).
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaGem1123cSwitch"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaGem1123cSwitch")
	public void qaTheSwitchOffThenOnSheCarriesOn(GameTestHelper helper) {
		clearAround(helper);
		noRandomTicks(helper);
		helper.setBlock(BUDDING, Blocks.BUDDING_AMETHYST);
		cluster(helper, BUDDING.above(), Direction.UP);
		Villager grower = GemGrowerGameTests.grower(helper);
		GemGrowers.ENABLED = false;
		Leftovers.after(helper, () -> GemGrowers.ENABLED = true);
		boolean[] on = {false};
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(helper.getBlockState(BUDDING.above()).is(Blocks.AMETHYST_CLUSTER), "she picked while switched off");
			helper.assertTrue(line(helper, grower).equals("Gem Growers are switched off (config gemGrowers)"), "she says " + said(helper, grower));
			helper.assertTrue(grower.getVillagerData().getProfession() == ModVillagers.GEM_GROWER, "the switch fired her");
			GemGrowers.ENABLED = true;
			on[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(on[0], "still off");
			helper.assertFalse(helper.getBlockState(BUDDING.above()).is(Blocks.AMETHYST_CLUSTER), "switched on again she didn't pick: " + said(helper, grower));
			helper.assertTrue(helper.getBlockState(BUDDING).is(Blocks.BUDDING_AMETHYST), "the budding block was broken");
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.AMETHYST_SHARD) >= 4, "the shards aren't in her chest: " + said(helper, grower));
		});
	}

	/**
	 * No chest by her stonecutter: she picks the ripe cluster, holds the shards (none dropped on the ground), says she has
	 * no chest, and her title reads "1 cluster picked". Saved and reloaded with the shards still in her hands, then a chest
	 * placed by the stonecutter: the reloaded grower puts every shard in it.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaGem1123cNoChest"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaGem1123cNoChest")
	public void qaNoChestSheHoldsHerPickingThroughAReloadUntilOneIsPut(GameTestHelper helper) {
		clearAround(helper);
		noRandomTicks(helper);
		helper.setBlock(BUDDING, Blocks.BUDDING_AMETHYST);
		cluster(helper, BUDDING.above(), Direction.UP);
		Villager grower = growerWithoutChest(helper);
		Villager[] copy = {null};
		int[] held = {0};
		helper.onEachTick(() -> {
			if (copy[0] != null || !line(helper, grower).equals("She has no chest by her stonecutter to put her picking in")) {
				return;
			}
			helper.assertFalse(helper.getBlockState(BUDDING.above()).is(Blocks.AMETHYST_CLUSTER), "no-chest line before the pick");
			held[0] = ModAttachments.BUILDER_BAG.getOrCreate(grower).count(Items.AMETHYST_SHARD);
			helper.assertTrue(held[0] >= 4, "she doesn't hold the cluster's shards: " + said(helper, grower));
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16)).isEmpty(), "shards dropped on the ground");
			helper.assertTrue(title(helper, grower).equals("Gem Grower · 1 cluster picked"), "her title after one pick: " + said(helper, grower));
			CompoundTag saved = grower.saveWithoutId(new CompoundTag());
			grower.discard();
			Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
			loaded.load(saved);
			loaded.setUUID(UUID.randomUUID());
			helper.getLevel().addFreshEntity(loaded);
			helper.assertTrue(loaded.getVillagerData().getProfession() == ModVillagers.GEM_GROWER, "reloaded as " + loaded.getVillagerData().getProfession());
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(loaded).count(Items.AMETHYST_SHARD) == held[0],
				"her shards after a reload: " + ModAttachments.BUILDER_BAG.getOrCreate(loaded).stacks());
			helper.setBlock(CHEST, Blocks.CHEST);
			copy[0] = loaded;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(copy[0] != null, "she never said she had no chest: " + said(helper, grower));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.AMETHYST_SHARD) == held[0], "shards in the new chest: " + chest.countItem(Items.AMETHYST_SHARD)
				+ " of " + held[0] + "; she says " + said(helper, copy[0]));
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(copy[0]).count(Items.AMETHYST_SHARD) == 0, "she still holds shards");
			helper.assertTrue(ModAttachments.GEMS_PICKED.getOrElse(copy[0], 0) == 1, "count after a reload: " + ModAttachments.GEMS_PICKED.getOrElse(copy[0], 0));
		});
	}

	/** Every status line read with its argument as a player sees it: American English and the counts filled in. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaHerLinesReadRight(GameTestHelper helper) {
		helper.assertTrue(text("message.aliveworkplace.gem_grower.state.picking", Component.translatable("gem_bed.aliveworkplace.amethyst"))
			.equals("Picking a ripe cluster (Amethyst)"), "picking reads wrong");
		helper.assertTrue(text("message.aliveworkplace.gem_grower.state.planting", Component.translatable("gem_bed.aliveworkplace.fire_gem"))
			.equals("Planting (Fire Gem)"), "planting reads wrong");
		helper.assertTrue(text("message.aliveworkplace.gem_grower.not_yours", "Ada").equals("Ada takes orders from whoever hired her."), "not_yours reads wrong");
		helper.assertTrue(text("screen.aliveworkplace.gem_grower.orders", "Ada").equals("Gem beds for Ada"), "orders title reads wrong");
		helper.assertTrue(text("screen.aliveworkplace.gem_grower.planted_with", "Tumblestone").equals("Planted with: Tumblestone"), "planted_with wrong");
		String[] keys = {"state.off", "state.no_stonecutter", "state.no_beds", "state.needs_planting", "state.tending", "state.fetching",
			"state.putting_away", "state.no_chest", "state.blank_tm", "not_yours"};
		for (String k : keys) {
			String s = Language.getInstance().getOrDefault("message.aliveworkplace.gem_grower." + k);
			for (String british : new String[] {"colour", "armour", "jeweller", "cancelled", "grey", "defence", "centre"}) {
				helper.assertFalse(s.toLowerCase().contains(british), k + " is British English: " + s);
			}
		}
		helper.succeed();
	}
}
