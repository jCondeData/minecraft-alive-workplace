package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.mail.MailboxBlock;
import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-b-1026-1248), from the spec, through a player's own entry points: a player builds a row, scans it with real
 * Scan Tool clicks, and a real builder builds the blueprint from a chest holding exactly its material list.
 * <ul>
 * <li>B448 (wiki builder.md: "The mod's own facing blocks turn and mirror with the build as vanilla's do"): a Mailbox
 * built turned and mirrored faces the way a vanilla furnace beside it faces, the blueprint's facing turned and
 * mirrored.</li>
 * <li>B493 and "Builders never copy what is inside containers, nor what a block holds in its look": a scanned mailbox
 * with mail and a chest with items are built empty; "A crop that grew ... since still counts as built" (and a mailbox
 * given mail since): neither is skipped, broken or rebuilt.</li>
 * </ul>
 */
public class QaBuilderVerifyScan1026bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	/** The scanned row runs east from here (the floor under it, y = 1, is the scan's lower corner). */
	private static final BlockPos SRC = new BlockPos(14, 2, 24);

	/** A scanned build, started by a builder with exactly its material list in a chest. */
	record Job(ServerLevel level, BuildSite site, Map<Item, Integer> asked, String list, BlockPos origin) {
	}

	/**
	 * Builds the row (by {@code setup}, at {@link #SRC} eastward, {@code width} wide, {@code height} tall), scans it with
	 * real Scan Tool clicks, clears it, and has a builder start the blueprint turned and mirrored as given, with exactly
	 * its material list in a chest (materials not free, no crew).
	 */
	static Job scanAndBuild(GameTestHelper helper, int width, int height, Rotation rotation, Mirror mirror, Runnable setup) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		ServerLevel level = helper.getLevel();
		setup.run();
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
		QaStageThreeGameTests.click(helper, player, SRC.below());
		QaStageThreeGameTests.click(helper, player, SRC.east(width).above(height - 1));
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.saved"), "the scan said " + QaStageThreeGameTests.last(seen));
		ItemStack blueprint = ItemStack.EMPTY;
		for (ItemStack s : player.getInventory().items) {
			if (s.is(ModItems.BLUEPRINT)) {
				blueprint = s;
			}
		}
		helper.assertTrue(!blueprint.isEmpty(), "the scan gave no blueprint");
		ResourceLocation id = BlueprintItem.data(blueprint).orElseThrow().structure();
		for (int dx = 0; dx < width; dx++) {
			for (int dy = 0; dy < height; dy++) {
				if (level.getBlockEntity(helper.absolutePos(SRC.east(dx).above(dy))) instanceof Container c) {
					c.clearContent(); // the scanned originals' contents aren't the build's to find lying about
				}
				helper.setBlock(SRC.east(dx).above(dy), Blocks.AIR);
			}
		}
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4))) {
			e.discard();
		}
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlockPos origin = helper.absolutePos(new BlockPos(10, 1, 10));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, mirror);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException("the builder didn't take the scanned blueprint " + id);
		}
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		Map<Item, Integer> asked = site.plan(level).materials();
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : asked.entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				chest.setItem(slot++, new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		String list = asked.entrySet().stream().map(e -> e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).toList().toString();
		LOGGER.info("qa v1026b scan: {} turned {} mirrored {} asks {}", id, rotation, mirror, list);
		return new Job(level, site, asked, list, origin);
	}

	private static boolean building(Job job) {
		return BuildSiteManager.get(job.level()).get(job.site().id()) != null;
	}

	private static String state(Job job) {
		return "stage " + job.site().stage() + " status " + job.site().status() + " skipped " + job.site().skipped() + " missing " + job.site().missing();
	}

	/** Where {@code block} was built in the build's own box (not the bench's supply chest beside it), or null. */
	private static BlockPos find(Job job, Block block) {
		net.minecraft.world.level.levelgen.structure.BoundingBox box = job.site().plan(job.level()).bounds();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (job.level().getBlockState(p).is(block)) {
				return p.immutable();
			}
		}
		return null;
	}

	private static Direction facing(Job job, Block block, GameTestHelper helper) {
		BlockPos at = find(job, block);
		helper.assertTrue(at != null, "no " + BuiltInRegistries.BLOCK.getKey(block).getPath() + " built");
		return job.level().getBlockState(at).getValue(HorizontalDirectionalBlock.FACING);
	}

	private static BlockState south(Block block) {
		return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
	}

	/**
	 * A furnace, a Mailbox and a chest, all facing south, scanned and built turned a quarter and mirrored left to right.
	 * Each comes out facing the blueprint's south mirrored (north), then turned a quarter (east): the Mailbox as vanilla's
	 * furnace and chest beside it (B448). The scanned chest held diamonds: the built one is empty. Nothing is skipped.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaV1026bScanTurned"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaV1026bScanTurned")
	public void aMailboxInAMirroredTurnedScanFacesAsVanillasBlocksDo(GameTestHelper helper) {
		Rotation rotation = Rotation.CLOCKWISE_90;
		Mirror mirror = Mirror.LEFT_RIGHT;
		Direction expected = rotation.rotate(mirror.mirror(Direction.SOUTH));
		Job job = scanAndBuild(helper, 3, 1, rotation, mirror, () -> {
			helper.setBlock(SRC, south(Blocks.FURNACE));
			helper.setBlock(SRC.east(1), south(ModBlocks.MAILBOX));
			helper.setBlock(SRC.east(2), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
			((Container) helper.getBlockEntity(SRC.east(2))).setItem(0, new ItemStack(Items.DIAMOND, 5));
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(building(job), "still building: " + state(job));
			Direction furnace = facing(job, Blocks.FURNACE, helper);
			Direction mailbox = facing(job, ModBlocks.MAILBOX, helper);
			Direction chest = facing(job, Blocks.CHEST, helper);
			helper.assertTrue(furnace == expected, "setup: vanilla's furnace faces " + furnace + ", not " + expected + " (" + rotation + ", " + mirror + ")");
			helper.assertTrue(mailbox == furnace, "the Mailbox faces " + mailbox + " where the vanilla furnace and chest built with it face " + furnace
				+ " (B448: it turns and mirrors as vanilla's blocks do)");
			helper.assertTrue(chest == expected, "the chest faces " + chest + ", not " + expected);
			ChestBlockEntity built = (ChestBlockEntity) job.level().getBlockEntity(find(job, Blocks.CHEST));
			helper.assertTrue(built.isEmpty(), "the scanned chest's contents were copied into the built one: " + built.getItem(0));
			helper.assertTrue(job.site().skipped() == 0, job.site().skipped() + " step(s) skipped");
			LOGGER.info("qa v1026b scan turned: furnace, mailbox and chest all face {}", expected);
		});
	}

	/**
	 * A Mailbox holding a letter (it shows its mail flag) is scanned and built. Builders never copy what is inside
	 * containers, nor what a block holds in its look: the built Mailbox holds nothing and doesn't show mail it doesn't
	 * hold (no flag, no comparator signal), and it is built, not skipped. Fails today (the flag is copied): the test
	 * runs from {@link QaBuilderVerifyScan1026bBugTests}, not registered on main.
	 */
	static void aScannedMailboxWithMailIsBuiltEmptyAndShowsNoMail(GameTestHelper helper) {
		Job job = scanAndBuild(helper, 2, 1, Rotation.NONE, Mirror.NONE, () -> {
			helper.setBlock(SRC, Blocks.COBBLESTONE);
			helper.setBlock(SRC.east(1), south(ModBlocks.MAILBOX));
			MailboxBlockEntity box = helper.getBlockEntity(SRC.east(1));
			helper.assertTrue(box.receive(new ItemStack(Items.PAPER, 3)).isEmpty(), "setup: the mailbox took no letter");
			helper.assertTrue(helper.getBlockState(SRC.east(1)).getValue(MailboxBlock.HAS_MAIL), "setup: a mailbox with a letter shows no mail");
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(building(job), "still building: " + state(job));
			BlockPos at = find(job, ModBlocks.MAILBOX);
			helper.assertTrue(at != null, "no mailbox built (" + state(job) + ")");
			MailboxBlockEntity box = (MailboxBlockEntity) job.level().getBlockEntity(at);
			helper.assertTrue(box.isEmpty(), "the scanned mailbox's mail was copied: " + box.getItem(0));
			BlockState built = job.level().getBlockState(at);
			helper.assertFalse(built.getValue(MailboxBlock.HAS_MAIL), "the built mailbox is empty but shows mail (has_mail, comparator "
				+ built.getAnalogOutputSignal(job.level(), at) + ")");
			helper.assertTrue(job.site().skipped() == 0, job.site().skipped() + " step(s) skipped (" + state(job) + ")");
		});
	}

	/**
	 * Nether wart on soul sand and an empty Mailbox, scanned and built. While the build goes on, the wart grows ripe and
	 * a letter is posted in the mailbox. "A crop that grew ... since still counts as built": neither is skipped, broken or
	 * planted again; the wart stays ripe, the letter stays in the mailbox, no extra wart or mailbox is used, and the
	 * build ends with nothing skipped.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaV1026bScanGrown"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaV1026bScanGrown")
	public void aCropThatGrewAndAMailboxGivenMailSinceStillCountAsBuilt(GameTestHelper helper) {
		Job job = scanAndBuild(helper, 3, 2, Rotation.NONE, Mirror.NONE, () -> {
			helper.setBlock(SRC, Blocks.SOUL_SAND);
			helper.setBlock(SRC.above(), Blocks.NETHER_WART);
			helper.setBlock(SRC.east(1), Blocks.COBBLESTONE);
			helper.setBlock(SRC.east(1).above(), south(ModBlocks.MAILBOX));
			helper.setBlock(SRC.east(2), Blocks.COBBLESTONE);
		});
		BlockPos[] wart = new BlockPos[1];
		BlockPos[] mailbox = new BlockPos[1];
		helper.onEachTick(() -> {
			if (!building(job)) {
				return;
			}
			if (wart[0] == null) {
				wart[0] = find(job, Blocks.NETHER_WART);
				if (wart[0] != null) {
					job.level().setBlock(wart[0], Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, NetherWartBlock.MAX_AGE), 3);
					LOGGER.info("qa v1026b scan grown: the wart grew ripe ({})", state(job));
				}
			}
			if (mailbox[0] == null) {
				mailbox[0] = find(job, ModBlocks.MAILBOX);
				if (mailbox[0] != null && job.level().getBlockEntity(mailbox[0]) instanceof MailboxBlockEntity box) {
					helper.assertTrue(box.receive(new ItemStack(Items.PAPER, 2)).isEmpty(), "the built mailbox took no letter");
					LOGGER.info("qa v1026b scan grown: a letter posted ({})", state(job));
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(wart[0] != null && mailbox[0] != null, "not both built: wart " + wart[0] + ", mailbox " + mailbox[0] + " (" + state(job) + ")");
			helper.assertFalse(building(job), "still building with a ripe wart and a mailbox with mail: " + state(job));
			BlockState w = job.level().getBlockState(wart[0]);
			helper.assertTrue(w.is(Blocks.NETHER_WART) && w.getValue(NetherWartBlock.AGE) == NetherWartBlock.MAX_AGE, "the ripe wart was replanted or broken: " + w);
			helper.assertTrue(job.level().getBlockEntity(mailbox[0]) instanceof MailboxBlockEntity box && box.countItem(Items.PAPER) == 2,
				"the mailbox given mail since was broken or rebuilt: " + job.level().getBlockState(mailbox[0]));
			helper.assertTrue(job.site().skipped() == 0, job.site().skipped() + " step(s) skipped (" + state(job) + ")");
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.NETHER_WART) == 0 && chest.countItem(ModBlocks.MAILBOX.asItem()) == 0,
				"the one wart and one mailbox on the list weren't both used once: the chest still holds " + chest.countItem(Items.NETHER_WART)
				+ " wart and " + chest.countItem(ModBlocks.MAILBOX.asItem()) + " mailbox");
			int loose = job.level().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4)).size();
			helper.assertTrue(loose == 0, loose + " item stack(s) lying about (a wart or mailbox broken?)");
		});
	}
}
