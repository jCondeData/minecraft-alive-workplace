package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Miners digging quarries on a real (headless) server. */
public class MinerGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);

	private record Setup(ServerLevel level, Villager miner, QuarrySite site) {
	}

	private static Setup setup(GameTestHelper helper, BlockPos minRel, BlockPos maxRel, ItemStack... chest) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(BENCH, ModBlocks.MINERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Miners.employ(level, villager, helper.absolutePos(BENCH));
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(minRel), helper.absolutePos(maxRel));
		QuarrySite site = Miners.start(level, villager, null, box, box.getYSpan());
		return new Setup(level, villager, site);
	}

	private static void fillStone(GameTestHelper helper, BlockPos min, BlockPos max) {
		for (BlockPos p : BlockPos.betweenClosed(min, max)) {
			helper.setBlock(p, Blocks.STONE);
		}
	}

	private static boolean finished(Setup s) {
		return QuarrySiteManager.get(s.level()).get(s.site().id()) == null;
	}

	/** A 4×3×4 block of stone with coal and iron in it: dug out, everything ends up in the chest. */
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void minerDigsOutAQuarry(GameTestHelper helper) {
		BlockPos min = new BlockPos(6, 2, 6);
		BlockPos max = new BlockPos(9, 4, 9);
		fillStone(helper, min, max);
		helper.setBlock(new BlockPos(7, 3, 7), Blocks.COAL_ORE);
		helper.setBlock(new BlockPos(8, 2, 8), Blocks.IRON_ORE);
		Setup s = setup(helper, min, max, new ItemStack(Items.STONE_PICKAXE));
		s.site().setStairs(false); // everything dug out (stairs: see minerLeavesStairsDownThePit)
		helper.succeedWhen(() -> {
			helper.assertTrue(finished(s), "still digging: " + Math.round(s.site().progress() * 100) + "% (" + s.site().status() + "), "
				+ s.site().mined() + " dug, " + s.site().skipped() + " left");
			for (BlockPos p : BlockPos.betweenClosed(min, max)) {
				helper.assertBlockPresent(Blocks.AIR, p);
			}
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 46, "chest has " + chest.countItem(Items.COBBLESTONE) + " cobblestone");
			helper.assertTrue(chest.countItem(Items.COAL) == 1 && chest.countItem(Items.RAW_IRON) == 1, "the ores should be in the chest");
			helper.assertTrue(chest.countItem(Items.STONE_PICKAXE) == 1, "the pickaxe should be back in the chest");
			helper.assertTrue(chest.countItem(ModItems.QUARRY_MARKER) == 1, "the marker should be back in the chest");
		});
	}

	/**
	 * A 5×5 pit 5 deep, cut into the ground: one block per layer is left as a step, each along the wall from the one
	 * above, so the steps spiral down the walls. A sand step is swapped for cobblestone, a gap (a cave) filled in.
	 */
	@GameTest(template = AREA, timeoutTicks = 6000)
	public void minerLeavesStairsDownThePit(GameTestHelper helper) {
		BlockPos min = new BlockPos(6, 2, 6);
		BlockPos max = new BlockPos(10, 6, 10);
		fillStone(helper, new BlockPos(5, 2, 5), new BlockPos(11, 6, 11)); // the pit and the ground around it
		Setup s = setup(helper, min, max, new ItemStack(Items.IRON_PICKAXE));
		helper.assertTrue(s.site().hasStairs(), "a 5x5x5 quarry should get stairs");
		BlockPos[] steps = new BlockPos[5];
		for (BlockPos p : BlockPos.betweenClosed(min, max)) {
			if (s.site().isStep(helper.absolutePos(p))) {
				int layer = max.getY() - p.getY();
				helper.assertTrue(steps[layer] == null, "two steps in layer " + layer);
				steps[layer] = p.immutable();
			}
		}
		for (int layer = 0; layer < 5; layer++) {
			helper.assertTrue(steps[layer] != null, "no step in layer " + layer);
			if (layer > 0) {
				int dx = Math.abs(steps[layer].getX() - steps[layer - 1].getX());
				int dz = Math.abs(steps[layer].getZ() - steps[layer - 1].getZ());
				helper.assertTrue(dx + dz == 1, "step " + layer + " at " + steps[layer] + " isn't next to step " + (layer - 1) + " at " + steps[layer - 1]);
			}
		}
		helper.assertTrue(steps[0].equals(new BlockPos(6, 6, 6)), "the stairs should start at the corner nearest the bench, not " + steps[0]);
		helper.setBlock(steps[2], Blocks.SAND);
		helper.setBlock(steps[3], Blocks.AIR);
		helper.succeedWhen(() -> {
			helper.assertTrue(finished(s), "still digging: " + Math.round(s.site().progress() * 100) + "% (" + s.site().status() + "), "
				+ s.site().mined() + " dug, " + s.site().skipped() + " left, miner at " + helper.relativePos(s.miner().blockPosition()));
			for (BlockPos p : BlockPos.betweenClosed(min, max)) {
				int layer = max.getY() - p.getY();
				if (p.equals(steps[layer])) {
					helper.assertBlockPresent(layer == 2 || layer == 3 ? Blocks.COBBLESTONE : Blocks.STONE, p);
				} else {
					helper.assertBlockPresent(Blocks.AIR, p);
				}
			}
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.SAND) == 1, "the sand step should be in the chest");
		});
	}

	/**
	 * Keeping a quarry's chunks loaded (its owner online) must not change the quarry: before 0.45.0 it grew the box to
	 * take in the ground around the Miner's Bench, and the miner dug that up too.
	 */
	@GameTest(template = AREA)
	public void keepingAQuarryLoadedLeavesItsBoxAlone(GameTestHelper helper) {
		BlockPos min = new BlockPos(8, 2, 8);
		BlockPos max = new BlockPos(11, 4, 11);
		fillStone(helper, min, max);
		net.minecraft.server.level.ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Setup s = setup(helper, min, max, new ItemStack(Items.IRON_PICKAXE));
		QuarrySite site = Miners.start(s.level(), s.miner(), owner, BoundingBox.fromCorners(helper.absolutePos(min), helper.absolutePos(max)), 3);
		BoundingBox before = new BoundingBox(site.box().minX(), site.box().minY(), site.box().minZ(), site.box().maxX(), site.box().maxY(), site.box().maxZ());
		io.github.jcondedata.aliveworkplace.work.KeepLoaded.chunksToKeep(s.level());
		helper.assertTrue(site.box().equals(before), "keeping the quarry loaded changed its box from " + before + " to " + site.box());
		helper.succeed();
	}

	/**
	 * A quarry saved by an older version with its bench inside the box (stretched by the chunk-loading bug) is stopped
	 * before the miner digs any of it, and the marker comes back blank.
	 */
	@GameTest(template = AREA)
	public void stretchedOldQuarryIsStopped(GameTestHelper helper) {
		BlockPos min = new BlockPos(8, 2, 8);
		BlockPos max = new BlockPos(10, 3, 10);
		fillStone(helper, min, max);
		Setup s = setup(helper, min, max, new ItemStack(Items.IRON_PICKAXE));
		{
			net.minecraft.nbt.CompoundTag old = s.site().save();
			old.remove("stairs");
			old.remove("stair_start");
			old.putUUID("id", java.util.UUID.randomUUID());
			BlockPos bench = helper.absolutePos(BENCH);
			BlockPos far = helper.absolutePos(max);
			old.putIntArray("box", new int[]{bench.getX() - 8, bench.getY(), bench.getZ() - 8, far.getX(), far.getY(), far.getZ()});
			QuarrySite stretched = QuarrySite.load(old);
			helper.assertTrue(stretched != null && stretched.looksStretched(), "the old quarry should look stretched");
			QuarrySiteManager.get(s.level()).remove(s.site().id());
			QuarrySiteManager.get(s.level()).restore(stretched);
			s.miner().setAttached(io.github.jcondedata.aliveworkplace.registry.ModAttachments.MINER_JOB,
				new io.github.jcondedata.aliveworkplace.build.BuilderJob(stretched.id(), false));
			helper.assertTrue(Miners.activeSite(s.level(), s.miner()) == null, "the stretched quarry is still going");
			helper.assertTrue(QuarrySiteManager.get(s.level()).get(stretched.id()) == null, "the stretched quarry wasn't removed");
		}
		Container chest = helper.getBlockEntity(CHEST);
		ItemStack marker = ItemStack.EMPTY;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(ModItems.QUARRY_MARKER)) {
				marker = chest.getItem(i);
			}
		}
		helper.assertFalse(marker.isEmpty(), "no marker in the chest");
		helper.assertTrue(marker.get(io.github.jcondedata.aliveworkplace.registry.ModComponents.QUARRY).first().isEmpty(), "the marker should come back blank");
		// A quarry saved by this version is never taken for a stretched one, even with its bench inside.
		net.minecraft.nbt.CompoundTag current = s.site().save();
		BlockPos bench = helper.absolutePos(BENCH);
		current.putIntArray("box", new int[]{bench.getX() - 1, bench.getY() - 1, bench.getZ() - 1, bench.getX() + 1, bench.getY() + 1, bench.getZ() + 1});
		helper.assertFalse(QuarrySite.load(current).looksStretched(), "a new quarry shouldn't look stretched");
		helper.succeed();
	}

	/**
	 * A blast furnace by the bench: the miner's raw iron goes in with coal from the chest, and once it's smelted the
	 * ingots come back out into the chest. The cobblestone in the chest stays there.
	 */
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void minerSmeltsOreInAFurnaceByTheBench(GameTestHelper helper) {
		BlockPos min = new BlockPos(8, 2, 8);
		BlockPos max = new BlockPos(10, 2, 10);
		for (BlockPos p : BlockPos.betweenClosed(min, max)) {
			helper.setBlock(p, Blocks.IRON_ORE);
		}
		BlockPos furnacePos = new BlockPos(4, 2, 2);
		helper.setBlock(furnacePos, Blocks.BLAST_FURNACE);
		Setup s = setup(helper, min, max, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.COAL, 4), new ItemStack(Items.COBBLESTONE, 10));
		helper.succeedWhen(() -> {
			helper.assertTrue(finished(s), "still digging: " + Math.round(s.site().progress() * 100) + "%");
			net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace = helper.getBlockEntity(furnacePos);
			helper.assertTrue(furnace.getItem(0).isEmpty() && furnace.getItem(2).getCount() == 9,
				"furnace: in " + furnace.getItem(0) + ", fuel " + furnace.getItem(1) + ", out " + furnace.getItem(2));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.RAW_IRON) == 0, "raw iron left in the chest");
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 10, "the cobblestone went into the furnace");
			// Next drop-off: the ingots come out.
			io.github.jcondedata.aliveworkplace.work.Furnaces.tend(s.level(), helper.absolutePos(BENCH),
				io.github.jcondedata.aliveworkplace.build.SupplyContainers.find(s.level(), helper.absolutePos(BENCH), null),
				io.github.jcondedata.aliveworkplace.work.Furnaces::isOre);
			helper.assertTrue(chest.countItem(Items.IRON_INGOT) == 9 && furnace.getItem(2).isEmpty(), "the ingots didn't come out: " + chest.countItem(Items.IRON_INGOT));
		});
	}

	/**
	 * A 7 × 7 strip mine in a block of stone: 2-high tunnels on every third row, joined at the end nearest the bench,
	 * the rock between them left standing, but the ores in that rock dug out.
	 */
	@GameTest(template = AREA, timeoutTicks = 4000)
	public void minerDigsAStripMine(GameTestHelper helper) {
		fillStone(helper, new BlockPos(5, 2, 5), new BlockPos(13, 5, 13));
		BlockPos min = new BlockPos(6, 3, 6);
		BlockPos max = new BlockPos(12, 4, 12);
		BlockPos coal = new BlockPos(9, 4, 7);  // in the rock between the first two tunnels
		BlockPos iron = new BlockPos(10, 3, 11);
		helper.setBlock(coal, Blocks.COAL_ORE);
		helper.setBlock(iron, Blocks.IRON_ORE);
		Setup s = setup(helper, min, max, new ItemStack(Items.STONE_PICKAXE));
		s.site().setStripMine(true);
		helper.assertTrue(QuarryData.EMPTY.withDepth(QuarryData.STRIP_MINE).isStripMine(), "depth 2 on the marker is a strip mine");
		helper.succeedWhen(() -> {
			helper.assertTrue(finished(s), "still digging: " + Math.round(s.site().progress() * 100) + "% (" + s.site().status() + "), miner at "
				+ helper.relativePos(s.miner().blockPosition()));
			for (BlockPos p : BlockPos.betweenClosed(min, max)) {
				int row = p.getZ() - min.getZ();
				boolean tunnel = row % 3 == 0 || p.getX() == min.getX(); // tunnels along x; the cross tunnel on the bench's side
				if (tunnel || p.equals(coal) || p.equals(iron)) {
					helper.assertBlockPresent(Blocks.AIR, p);
				} else {
					helper.assertBlockPresent(Blocks.STONE, p);
				}
			}
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(8, 5, 6)); // the roof stays
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.COAL) == 1 && chest.countItem(Items.RAW_IRON) == 1, "the ores in the rock should be in the chest");
		});
	}

	/**
	 * A strip mine at a set height under the marked corners: the miner digs a ladder shaft down from the corner nearest
	 * the bench — sealing off the water beside it and getting past a cave in its way — digs the tunnels, and puts the
	 * last two ladders up at the foot of the shaft.
	 */
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 9000, batch = "shaft")
	public void minerDigsALadderShaftDownToAStripMine(GameTestHelper helper) {
		Leftovers.clear(helper);
		fillStone(helper, new BlockPos(5, 2, 6), new BlockPos(15, 14, 12));
		BlockPos min = new BlockPos(6, 3, 7);
		BlockPos max = new BlockPos(14, 4, 11);
		BlockPos water = new BlockPos(7, 10, 7);  // beside the shaft
		helper.setBlock(water, Blocks.WATER);
		for (int y = 7; y <= 8; y++) {             // a cave the shaft goes through
			helper.setBlock(new BlockPos(6, y, 7), Blocks.AIR);
			helper.setBlock(new BlockPos(7, y, 8), Blocks.AIR);
			helper.setBlock(new BlockPos(7, y, 7), Blocks.AIR);
		}
		Setup s = setup(helper, min, max, new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.LADDER, 16), new ItemStack(Items.TORCH, 8));
		s.site().setStripMine(true);
		int ground = helper.absolutePos(new BlockPos(0, 14, 0)).getY();
		helper.assertTrue(Miners.shaftStart(s.level(), s.site(), ground + 3) == ground, "the shaft should start at the ground, not in the air above it");
		s.site().setShaft(ground);
		helper.assertTrue(s.site().shaftColumn().getX() == helper.absolutePos(min).getX() && s.site().shaftColumn().getZ() == helper.absolutePos(min).getZ(),
			"the shaft should come down at the corner nearest the bench, not " + helper.relativePos(s.site().shaftColumn()));
		helper.succeedWhen(() -> {
			helper.assertTrue(finished(s), "still digging: " + Math.round(s.site().progress() * 100) + "% (" + s.site().status() + ", "
				+ s.site().phase() + " at " + (s.site().current() == null ? "-" : helper.relativePos(s.site().current())) + "), "
				+ s.site().skipped() + " skipped, miner at " + helper.relativePos(s.miner().blockPosition()));
			for (int y = 3; y <= 14; y++) {
				helper.assertBlockPresent(Blocks.LADDER, new BlockPos(6, y, 7));
			}
			int torches = 0;
			for (int y = 5; y <= 14; y++) {
				for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
					torches += helper.getBlockState(new BlockPos(6, y, 7).relative(d)).is(Blocks.TORCH) ? 1 : 0;
				}
			}
			helper.assertTrue(torches >= 1, "no torch in the shaft's wall");
			helper.assertTrue(helper.getBlockState(water).getFluidState().isEmpty(), "the water beside the shaft should be sealed off");
			for (BlockPos p : BlockPos.betweenClosed(min, max)) {
				if (p.getX() == 6 && p.getZ() == 7) {
					continue; // the foot of the shaft
				}
				boolean tunnel = (p.getZ() - min.getZ()) % 3 == 0 || p.getX() == min.getX();
				helper.assertBlockPresent(tunnel ? Blocks.AIR : Blocks.STONE, p);
			}
			helper.assertTrue(s.miner().isAlive(), "the miner didn't make it");
		});
	}

	/** {@code /workplace strip <height>} sets the held marker to a strip mine at any height in the world. */
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE)
	public void stripMineHeightCanBeTypedIn(GameTestHelper helper) {
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(io.github.jcondedata.aliveworkplace.registry.ModItems.QUARRY_MARKER));
		var commands = helper.getLevel().getServer().getCommands();
		commands.performPrefixedCommand(player.createCommandSourceStack(), "workplace strip -30");
		QuarryData data = io.github.jcondedata.aliveworkplace.mine.QuarryMarkerItem.data(player.getMainHandItem());
		helper.assertTrue(data.isStripMine() && data.stripLevel().equals(java.util.Optional.of(-30)), "the marker says " + data);
		commands.performPrefixedCommand(player.createCommandSourceStack(), "workplace strip -100");
		data = io.github.jcondedata.aliveworkplace.mine.QuarryMarkerItem.data(player.getMainHandItem());
		helper.assertTrue(data.stripLevel().equals(java.util.Optional.of(-30)), "below the world's floor should be refused, the marker says " + data);
		helper.succeed();
	}

	/** The marker's choices: pits, a strip mine here, then strip mines down a shaft; strip mines may be longer. */
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE)
	public void quarryMarkerOffersStripMinesDownAShaft(GameTestHelper helper) {
		QuarryData data = new QuarryData(java.util.Optional.of(helper.getLevel().dimension().location()), java.util.Optional.of(new BlockPos(0, 70, 0)),
			java.util.Optional.of(new BlockPos(40, 68, 9)), 64);
		java.util.List<String> seen = new java.util.ArrayList<>();
		for (int i = 0; i < 5; i++) {
			data = data.next();
			seen.add(data.depth() + data.stripLevel().map(l -> "@" + l).orElse(""));
		}
		helper.assertTrue(seen.equals(java.util.List.of("2", "2@16", "2@-16", "2@-53", "4")), "the marker cycles " + seen);
		QuarryData diamonds = data.withDepth(QuarryData.STRIP_MINE).next().next().next();
		helper.assertTrue(diamonds.stripLevel().equals(java.util.Optional.of(-53)), "expected diamonds, got " + diamonds);
		BoundingBox box = diamonds.area(-59).orElseThrow();
		helper.assertTrue(box.minY() == -53 && box.maxY() == -52 && box.getXSpan() == 41 && box.getZSpan() == 10, "tunnels at " + box);
		helper.assertTrue(diamonds.shaftTop(-59).equals(java.util.OptionalInt.of(70)), "the shaft starts at the marked corners");
		helper.assertTrue(diamonds.area(-40).orElseThrow().minY() == -40, "never below the world's floor");
		QuarryData above = new QuarryData(diamonds.dimension(), java.util.Optional.of(new BlockPos(0, 10, 0)), java.util.Optional.of(new BlockPos(9, 10, 9)),
			QuarryData.STRIP_MINE, java.util.Optional.of(16));
		helper.assertTrue(above.shaftTop(-59).isEmpty() && above.area(-59).orElseThrow().maxY() == 10, "a height above the corners: tunnels at the corners");
		helper.assertTrue(QuarryData.fits(64, 32, true) && !QuarryData.fits(64, 33, true) && !QuarryData.fits(40, 10, false) && QuarryData.fits(32, 32, false),
			"size limits");

		// Saved and loaded, the shaft stays.
		QuarrySite site = new QuarrySite(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "", helper.getLevel().dimension().location(), box, 2);
		site.setStripMine(true);
		site.setShaft(70);
		QuarrySite loaded = QuarrySite.load(site.save());
		helper.assertTrue(loaded != null && loaded.hasShaft() && loaded.shaftTop() == 70 && loaded.total() == site.total()
			&& loaded.at(0).getY() == 70 && loaded.phase() == QuarrySite.Phase.SHAFT, "the shaft wasn't saved");
		helper.succeed();
	}

	/** A block of stone taller than the miner: it has to get on top and work its way down. */
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 8000)
	public void minerDigsDownThroughATallBlock(GameTestHelper helper) {
		BlockPos min = new BlockPos(8, 2, 8);
		BlockPos max = new BlockPos(13, 7, 13);
		fillStone(helper, min, max);
		Setup s = setup(helper, min, max, new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_PICKAXE));
		helper.succeedWhen(() -> {
			helper.assertTrue(finished(s), "still digging: " + Math.round(s.site().progress() * 100) + "% (" + s.site().status() + "), "
				+ s.site().mined() + " dug, " + s.site().skipped() + " left, miner at " + helper.relativePos(s.miner().blockPosition()));
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " block(s) left standing");
		});
	}

	/** Blocks touching lava stay put, so the lava never gets into the pit. */
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void minerLeavesBlocksNextToLava(GameTestHelper helper) {
		BlockPos min = new BlockPos(6, 2, 6);
		BlockPos max = new BlockPos(8, 3, 8);
		fillStone(helper, min, max);
		BlockPos lava = new BlockPos(9, 2, 7);
		for (BlockPos wall : new BlockPos[]{new BlockPos(10, 2, 7), new BlockPos(9, 2, 6), new BlockPos(9, 2, 8), new BlockPos(9, 3, 7)}) {
			helper.setBlock(wall, Blocks.COBBLESTONE);
		}
		helper.setBlock(lava, Blocks.LAVA);
		Setup s = setup(helper, min, max, new ItemStack(Items.IRON_PICKAXE));
		helper.succeedWhen(() -> {
			helper.assertTrue(finished(s), "still digging: " + Math.round(s.site().progress() * 100) + "%");
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(8, 2, 7));
			helper.assertBlockPresent(Blocks.LAVA, lava);
			helper.assertTrue(s.miner().isAlive(), "the miner didn't make it");
			for (BlockPos p : BlockPos.betweenClosed(min, max)) {
				helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(p)).isEmpty(), "lava got into the pit at " + p);
			}
		});
	}

	/** No pickaxe, no digging: the miner waits (and says so) until one turns up in the chest. */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void minerWaitsForAPickaxe(GameTestHelper helper) {
		BlockPos min = new BlockPos(6, 2, 6);
		BlockPos max = new BlockPos(7, 2, 7);
		fillStone(helper, min, max);
		Setup s = setup(helper, min, max);
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(s.site().mined() == 0, "dug without a pickaxe");
			helper.assertTrue(s.site().status() == QuarrySite.Status.NEEDS_PICKAXE, "status is " + s.site().status());
			Container chest = helper.getBlockEntity(CHEST);
			chest.setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
		});
		helper.succeedWhen(() -> helper.assertTrue(finished(s), "still waiting (" + s.site().status() + ")"));
	}
}
