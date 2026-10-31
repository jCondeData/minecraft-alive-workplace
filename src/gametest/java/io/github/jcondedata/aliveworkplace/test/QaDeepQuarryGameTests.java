package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA for 36.22 (miners dig deeper as they level up), from the spec, on top of {@link DeepQuarryGameTests}: the marker a
 * miner stopped short with comes back as the deep one and, handed again to a miner who has the level, takes the whole
 * pit ("Hand them the marker again once they get there"); and a miner who levels up step by step mid-quarry takes the
 * pit down one step at a time, the owner hearing each step once and never again once the marker's floor is reached.
 * Same layout as {@link DeepQuarryGameTests}: the pit is a column above the test area of the superflat world.
 */
public class QaDeepQuarryGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:huge_area";
	static final BlockPos BENCH = new BlockPos(2, 2, 2);
	static final BlockPos CHEST = new BlockPos(2, 2, 4);
	static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	static final int LO = 10;
	static final int HI = 12;

	record Deep(ServerLevel level, Villager miner, ServerPlayer player, List<Component> seen) {
	}

	static Deep miner(GameTestHelper helper, int villagerLevel) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(BENCH, ModBlocks.MINERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.IRON_PICKAXE));
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Miners.employ(level, villager, helper.absolutePos(BENCH));
		villager.setVillagerData(villager.getVillagerData().setLevel(villagerLevel));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		return new Deep(level, villager, player, seen);
	}

	static ItemStack marker(GameTestHelper helper, Deep d, int top, int depth) {
		ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
		marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(d.level().dimension().location()),
			Optional.of(helper.absolutePos(new BlockPos(LO, top, LO))), Optional.of(helper.absolutePos(new BlockPos(HI, top, HI))), depth));
		return marker;
	}

	/** The player right-clicks the miner holding {@code marker}, the way the client sends it. */
	static QuarrySite click(GameTestHelper helper, Deep d, ItemStack marker) {
		d.player().setItemInHand(InteractionHand.MAIN_HAND, marker);
		QaStageFourGameTests.pressOn(d.player(), d.miner(), d.seen());
		QuarrySite site = Miners.activeSite(d.level(), d.miner());
		helper.assertTrue(site != null, "the miner didn't take the marker: " + QaStageFourGameTests.keys(d.seen()));
		return site;
	}

	/** Removes the miner's quarries and the column above the test area, after the test. */
	static void cleanUp(GameTestHelper helper, Deep d, int top) {
		Leftovers.after(helper, () -> {
			QuarrySite site = Miners.activeSite(d.level(), d.miner());
			if (site != null) {
				QuarrySiteManager.get(d.level()).remove(site.id());
			}
			for (BlockPos p : BlockPos.betweenClosed(LO - 1, -1, LO - 1, HI + 1, top + 1, HI + 1)) {
				helper.getLevel().setBlock(helper.absolutePos(p), Blocks.AIR.defaultBlockState(), 2);
			}
		});
	}

	/** Rock from {@code rockFrom} to {@code rockTo} in the pit, walled round, dug above it but for the steps (see DeepQuarryGameTests). */
	static void column(GameTestHelper helper, QuarrySite site, int rockFrom, int rockTo, int top) {
		for (int y = rockFrom - 1; y <= top; y++) {
			for (int x = LO; x <= HI; x++) {
				for (int z = LO; z <= HI; z++) {
					BlockPos abs = helper.absolutePos(new BlockPos(x, y, z));
					boolean rock = y <= rockTo || site.isStep(abs);
					helper.getLevel().setBlock(abs, (rock ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
				}
			}
		}
		for (BlockPos p : BlockPos.betweenClosed(LO - 1, rockFrom - 1, LO - 1, HI + 1, rockTo + 2, HI + 1)) {
			if (p.getX() == LO - 1 || p.getX() == HI + 1 || p.getZ() == LO - 1 || p.getZ() == HI + 1) {
				helper.getLevel().setBlock(helper.absolutePos(p), Blocks.STONE.defaultBlockState(), 2);
			}
		}
	}

	static long count(List<Component> seen, String key) {
		return seen.stream().filter(c -> key.equals(QaStageFourGameTests.key(c))).count();
	}

	/**
	 * A Novice handed a 128 marker stops 64 down and says so; the marker comes back to the chest still marked 128 deep
	 * at the same corners. Once the miner is an Expert, the player hands that same marker over again: the quarry takes
	 * the whole 128 from the top, with no word about levels.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaDeepQuarryAgain"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaDeepQuarryAgain")
	public void theMarkerAMinerStoppedShortWithTakesTheWholePitOnceTheyHaveTheLevel(GameTestHelper helper) {
		Deep d = miner(helper, 1);
		int floor = d.level().getMinBuildHeight() + 5;
		int bottom = helper.relativePos(new BlockPos(0, floor, 0)).getY();
		int top = bottom + 127;
		cleanUp(helper, d, top);
		QuarrySite site = click(helper, d, marker(helper, d, top, 128));
		helper.assertTrue(site.box().getYSpan() == 64 && site.wantsDeeper(), "a Novice should stop 64 down: " + site.box());
		int cut = top - 63;
		column(helper, site, cut - 4, cut + 3, top);
		boolean[] again = {false};
		String[] failed = {null};
		helper.succeedWhen(() -> {
			if (failed[0] != null) {
				helper.fail(failed[0]);
			}
			if (!again[0]) {
				helper.assertTrue(QuarrySiteManager.get(d.level()).get(site.id()) == null, "still digging the Novice's 64: " + Math.round(site.progress() * 100) + "%");
				helper.assertTrue(QaStageFourGameTests.saw(d.seen(), "message.aliveworkplace.quarry.stopped_short"), "no word of stopping short");
				Container chest = helper.getBlockEntity(CHEST);
				ItemStack back = ItemStack.EMPTY;
				for (int i = 0; i < chest.getContainerSize(); i++) {
					if (chest.getItem(i).is(ModItems.QUARRY_MARKER)) {
						back = chest.getItem(i).copy();
						chest.setItem(i, ItemStack.EMPTY);
					}
				}
				helper.assertTrue(!back.isEmpty(), "the marker didn't come back to the chest");
				QuarryData data = back.get(ModComponents.QUARRY);
				helper.assertTrue(data != null && data.depth() == 128 && data.first().equals(Optional.of(helper.absolutePos(new BlockPos(LO, top, LO))))
					&& data.second().equals(Optional.of(helper.absolutePos(new BlockPos(HI, top, HI)))),
					"the marker came back changed: " + data);
				again[0] = true;
				d.miner().setVillagerData(d.miner().getVillagerData().setLevel(4));
				d.seen().clear();
				try {
					QuarrySite whole = click(helper, d, back);
					helper.assertTrue(whole.box().getYSpan() == 128 && whole.box().minY() == floor && !whole.wantsDeeper(),
						"handed again to an Expert, the quarry should take all 128: " + whole.box() + ", wanted bottom " + whole.wantedBottom());
					helper.assertFalse(QaStageFourGameTests.saw(d.seen(), "message.aliveworkplace.quarry.too_deep"), "an Expert was told they can't dig 128");
					helper.assertTrue(d.player().getMainHandItem().isEmpty(), "the marker stayed in the player's hand");
				} catch (RuntimeException e) {
					failed[0] = e.getMessage();
					helper.fail(failed[0]);
				}
			}
		});
	}

	/**
	 * A Novice handed a 128 marker over a 128-deep pit, leveling up mid-quarry to Journeyman, then Expert, then Master:
	 * the pit goes down to 96 and then 128, the owner hears each step once, and nothing more once the marker's floor is
	 * reached (the deeper check runs every work tick).
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaDeepQuarrySteps"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaDeepQuarrySteps")
	public void levelingUpStepByStepDeepensEachStepAndSaysItOnce(GameTestHelper helper) {
		Deep d = miner(helper, 1);
		int floor = d.level().getMinBuildHeight() + 5;
		int bottom = helper.relativePos(new BlockPos(0, floor, 0)).getY();
		int top = bottom + 127;
		cleanUp(helper, d, top);
		QuarrySite site = click(helper, d, marker(helper, d, top, 128));
		int topAbs = helper.absolutePos(new BlockPos(0, top, 0)).getY();
		int cut = top - 63;
		column(helper, site, cut - 4, cut + 6, top);
		int[] stage = {0};
		long[] masterAt = {-1};
		helper.onEachTick(() -> {
			String deeper = "message.aliveworkplace.quarry.deeper";
			if (stage[0] == 0 && site.mined() >= 1) {
				stage[0] = 1;
				d.miner().setVillagerData(d.miner().getVillagerData().setLevel(3));
			} else if (stage[0] == 1 && count(d.seen(), deeper) == 1) {
				stage[0] = 2;
				helper.assertTrue(site.box().minY() == topAbs - 95 && site.wantsDeeper(), "a Journeyman should take it to 96: " + site.box());
				d.miner().setVillagerData(d.miner().getVillagerData().setLevel(4));
			} else if (stage[0] == 2 && count(d.seen(), deeper) == 2) {
				stage[0] = 3;
				helper.assertTrue(site.box().minY() == floor && !site.wantsDeeper(), "an Expert should take it to 128: " + site.box());
				d.miner().setVillagerData(d.miner().getVillagerData().setLevel(5));
				masterAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stage[0] == 3 && helper.getTick() >= masterAt[0] + 100,
				"stage " + stage[0] + ", dug " + site.mined() + ", box " + site.box() + ", heard " + QaStageFourGameTests.keys(d.seen()));
			List<Component> steps = d.seen().stream().filter(c -> "message.aliveworkplace.quarry.deeper".equals(QaStageFourGameTests.key(c))).toList();
			helper.assertTrue(steps.size() == 2, "the owner heard " + steps.size() + " deeper lines, not one per step: " + steps.stream().map(Component::getString).toList());
			QaStageFourGameTests.assertRendered(helper, steps.get(0), "Journeyman", "96");
			QaStageFourGameTests.assertRendered(helper, steps.get(1), "Expert", "128");
			helper.assertTrue(site.box().minY() == floor && site.box().maxY() == topAbs, "the pit isn't the marker's 128: " + site.box());
			helper.assertFalse(QaStageFourGameTests.saw(d.seen(), "message.aliveworkplace.quarry.stopped_short"), "said they stopped short");
		});
	}
}
