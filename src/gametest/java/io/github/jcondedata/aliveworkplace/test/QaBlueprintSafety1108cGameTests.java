package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * QA (lane c, 2026-11-08) for B1038, B1044 and B1047, written from their specs (ROADMAP Bugs):
 * <ul>
 *   <li>B1038: a block scanned in the middle of a moment (a pressed button, a powered observer, lectern, target, tripwire
 *   hook, detector rail) is built as a player's freshly placed one is, and a hook keeps "attached"; a momentary state
 *   that comes back in the world later (a plate stepped on mid-build) still counts as built.</li>
 *   <li>B1044, B1047: a sign built from an uploaded file keeps its words, color and glow, but runs no click command on
 *   either side (a hanging sign too), works out no selector or score part, and holds lines of at most 384 characters (a
 *   line of exactly 384 is kept whole). "The same for any other text a blueprint copies": a command block in a file
 *   never runs its command, and a lectern's book (whose pages can carry click commands) is never copied.</li>
 * </ul>
 */
public class QaBlueprintSafety1108cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(1, 2, 1);
	private static final BlockPos CHEST = new BlockPos(1, 2, 3);
	private static final BlockPos SOURCE = new BlockPos(6, 2, 6);
	private static final BlockPos SITE = new BlockPos(4, 2, 2);

	// ---------------------------------------------------------------------------------------------------------------
	// B1038: more momentary states

	//$ gametest_ticks_batch AREA '1600' '"qa1108cStuckWoodButton"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa1108cStuckWoodButton")
	public void aWoodenButtonScannedPressedIsBuiltOut(GameTestHelper helper) {
		stuck(helper, "wbutton", Blocks.OAK_BUTTON.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
				.setValue(BlockStateProperties.POWERED, true), Items.OAK_BUTTON,
			s -> !s.getValue(BlockStateProperties.POWERED), s -> true, "pressed in (it powers what is beside it for good)");
	}

	//$ gametest_ticks_batch AREA '1600' '"qa1108cStuckObserver"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa1108cStuckObserver")
	public void anObserverScannedPulsingIsBuiltOff(GameTestHelper helper) {
		stuck(helper, "observer", Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.POWERED, true), Items.OBSERVER,
			s -> !s.getValue(BlockStateProperties.POWERED), s -> true, "powered for good (a stuck pulse)");
	}

	//$ gametest_ticks_batch AREA '1600' '"qa1108cStuckLectern"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa1108cStuckLectern")
	public void aLecternScannedPoweredIsBuiltOff(GameTestHelper helper) {
		stuck(helper, "lectern", Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.POWERED, true), Items.LECTERN,
			s -> !s.getValue(BlockStateProperties.POWERED), s -> true, "powered for good (a page turn that never ends)");
	}

	//$ gametest_ticks_batch AREA '1600' '"qa1108cStuckTarget"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa1108cStuckTarget")
	public void aTargetScannedHitIsBuiltSilent(GameTestHelper helper) {
		stuck(helper, "target", Blocks.TARGET.defaultBlockState().setValue(BlockStateProperties.POWER, 12), Items.TARGET,
			s -> s.getValue(BlockStateProperties.POWER) == 0, s -> true, "giving power 12 for good");
	}

	//$ gametest_ticks_batch AREA '1600' '"qa1108cStuckRail"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa1108cStuckRail")
	public void aDetectorRailScannedWithACartIsBuiltOff(GameTestHelper helper) {
		stuck(helper, "rail", Blocks.DETECTOR_RAIL.defaultBlockState().setValue(BlockStateProperties.POWERED, true), Items.DETECTOR_RAIL,
			s -> !s.getValue(BlockStateProperties.POWERED), s -> true, "powered with no cart on it");
	}

	/** The hook's wire was tripped when scanned: built, it is not powered, but stays hooked up (attached). */
	//$ gametest_ticks_batch AREA '1600' '"qa1108cStuckHook"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa1108cStuckHook")
	public void aTripwireHookScannedTrippedIsBuiltOffButStillAttached(GameTestHelper helper) {
		stuck(helper, "hook", Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
				.setValue(BlockStateProperties.ATTACHED, true).setValue(BlockStateProperties.POWERED, true), Items.TRIPWIRE_HOOK,
			s -> !s.getValue(BlockStateProperties.POWERED),
			s -> s.getValue(BlockStateProperties.ATTACHED), "powered for good");
	}

	/**
	 * A plate is built under something standing on it (a pig in the yard), so it is pressed (powered) the
	 * moment it's down. That is the game's own moment, not a wrong block: the build finishes, takes one plate and
	 * skips nothing.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qa1108cSteppedPlate"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa1108cSteppedPlate")
	public void aPlatePressedRightAfterItIsBuiltStillCountsAsBuilt(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SOURCE, Blocks.OAK_PLANKS);
		helper.setBlock(SOURCE.above(), Blocks.OAK_PRESSURE_PLATE);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1108c_stepped_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(SOURCE), new Vec3i(1, 2, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(SOURCE.above(), Blocks.AIR);
		helper.setBlock(SOURCE, Blocks.AIR);

		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = (Container) helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
		chest.setItem(1, new ItemStack(Items.OAK_PRESSURE_PLATE, 3));
		// a pig (no AI) that walks onto the plate the moment it is down: the game presses the plate for it
		net.minecraft.world.entity.animal.Pig pig = helper.spawn(EntityType.PIG, SITE.above().east(3));
		pig.setNoAi(true);
		Leftovers.after(helper, pig::discard);
		Villager builder = QaCarpenterStorehouse1101GameTests.builder(helper, BENCH);
		BuildSite site = QaCarpenterStorehouse1101GameTests.start(helper, builder, id, SITE);
		boolean[] pressedSeen = {false};
		helper.onEachTick(() -> {
			BlockState s = helper.getBlockState(SITE.above());
			if (s.is(Blocks.OAK_PRESSURE_PLATE) && !pressedSeen[0]) {
				BlockPos plate = helper.absolutePos(SITE.above());
				pig.teleportTo(plate.getX() + 0.5, plate.getY(), plate.getZ() + 0.5);
				s.entityInside(level, plate, pig);
			}
			s = helper.getBlockState(SITE.above());
			if (s.is(Blocks.OAK_PRESSURE_PLATE) && s.getValue(BlockStateProperties.POWERED)) {
				pressedSeen[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, site), "the plate build isn't finished with the plate stepped on: stage "
				+ site.stage() + ", " + site.skipped() + " skipped, plate: " + helper.getBlockState(SITE.above()));
			helper.assertTrue(helper.getBlockState(SITE.above()).is(Blocks.OAK_PRESSURE_PLATE), "no plate built: " + helper.getBlockState(SITE.above()));
			helper.assertTrue(pressedSeen[0], "(setup) the pig never pressed the built plate, so the test can't tell");
			helper.assertTrue(site.skipped() == 0, "the stepped-on plate was counted skipped (" + site.skipped() + ") though it stands built");
			int left = chest.countItem(Items.OAK_PRESSURE_PLATE) + ModAttachments.BUILDER_BAG.getOrCreate(builder).count(Items.OAK_PRESSURE_PLATE);
			helper.assertTrue(left == 2, "the build used " + (3 - left) + " plates for one (it redid the stepped-on plate)");
		});
	}

	/**
	 * Scans {@code top} (on oak planks; a wall-mounted block faces north on a plank wall south of it) set without
	 * updates, so no tick is pending, as in a saved scan; a builder builds it from a chest; {@code atDone} must hold when
	 * the build finishes and {@code settled} within 200 ticks after.
	 */
	private static void stuck(GameTestHelper helper, String name, BlockState top, Item topItem, Predicate<BlockState> settled,
							  Predicate<BlockState> atDone, String stuck) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean wall = top.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && top.is(Blocks.TRIPWIRE_HOOK);
		helper.setBlock(SOURCE, Blocks.OAK_PLANKS);
		if (wall) {
			helper.setBlock(SOURCE.south(), Blocks.OAK_PLANKS);
			helper.setBlock(SOURCE.south().above(), Blocks.OAK_PLANKS);
		}
		level.setBlock(helper.absolutePos(SOURCE.above()), top, Block.UPDATE_CLIENTS);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1108c_stuck_" + name + "_" + Long.toHexString(level.getGameTime()));
		StructureTemplate template = level.getStructureManager().getOrCreate(id);
		template.fillFromWorld(level, helper.absolutePos(SOURCE), new Vec3i(1, 2, wall ? 2 : 1), false, Blocks.STRUCTURE_VOID);
		// Vanilla springs some of these back the moment they're set (an observer, a target, a detector rail check
		// themselves on placing), so the saved scan gets the mid-moment state written in, as a scan taken in that tick has
		CompoundTag saved = template.save(new CompoundTag());
		ListTag palette = saved.getList("palette", Tag.TAG_COMPOUND);
		String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(top.getBlock()).toString();
		boolean patched = false;
		for (int i = 0; i < palette.size(); i++) {
			if (palette.getCompound(i).getString("Name").equals(blockId)) {
				palette.set(i, net.minecraft.nbt.NbtUtils.writeBlockState(top));
				patched = true;
			}
		}
		helper.assertTrue(patched, "(setup) the scan holds no " + blockId);
		template.load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), saved);
		level.setBlock(helper.absolutePos(SOURCE.above()), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		helper.setBlock(SOURCE.south().above(), Blocks.AIR);
		helper.setBlock(SOURCE.south(), Blocks.AIR);
		helper.setBlock(SOURCE, Blocks.AIR);

		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = (Container) helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
		chest.setItem(1, new ItemStack(topItem, 4));
		Villager builder = QaCarpenterStorehouse1101GameTests.builder(helper, BENCH);
		BuildSite site = QaCarpenterStorehouse1101GameTests.start(helper, builder, id, SITE);
		long[] doneAt = {-1};
		BlockState[] atFinish = {null};
		helper.onEachTick(() -> {
			if (doneAt[0] < 0 && QaCarpenterStorehouse1101GameTests.done(helper, site)) {
				doneAt[0] = level.getGameTime();
				atFinish[0] = helper.getBlockState(SITE.above());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(doneAt[0] >= 0, "the " + name + " build isn't finished: stage " + site.stage() + ", " + site.skipped() + " skipped");
			BlockState built = helper.getBlockState(SITE.above());
			helper.assertTrue(built.is(top.getBlock()), "no " + name + " built on top: " + built);
			helper.assertTrue(atFinish[0] != null && atFinish[0].is(top.getBlock()) && atDone.test(atFinish[0]),
				"the " + name + " as the build finished is not as scanned where it should be: " + atFinish[0]);
			if (level.getGameTime() - doneAt[0] < 200) {
				helper.assertTrue(settled.test(built), "waiting for the " + name + " to settle");
			}
			helper.assertTrue(settled.test(built), "the built " + name + " is " + stuck + " 200 ticks after the build finished: " + built);
		});
	}

	// ---------------------------------------------------------------------------------------------------------------
	// B1044, B1047: uploaded files

	private static final BlockPos U_BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(22, 2, 22);
	private static final BlockPos ORIGIN = new BlockPos(10, 2, 10);
	private static final BlockPos CONTROL = new BlockPos(22, 2, 6);
	private static final BlockPos BUILT_MARK = new BlockPos(14, 2, 16);
	private static final BlockPos CONTROL_MARK = new BlockPos(26, 2, 4);

	/** The back of a sign carries the command (the front is plain): clicked from behind, the built sign runs nothing. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1108cSignBack"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1108cSignBack")
	public void aSignsBackCommandFromAnUploadedFileDoesNotRun(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = survivor(helper);
		// control: the back's command runs for this player when clicked from behind
		helper.setBlock(CONTROL, Blocks.OAK_SIGN);
		backCommand((SignBlockEntity) helper.getBlockEntity(CONTROL), helper.absolutePos(CONTROL_MARK));
		click(helper, player, CONTROL, false);
		helper.assertTrue(helper.getBlockState(CONTROL_MARK).is(Blocks.GOLD_BLOCK), "(setup) clicking a sign's back ran nothing: " + helper.getBlockState(CONTROL_MARK));

		helper.setBlock(SRC, Blocks.OAK_SIGN);
		backCommand((SignBlockEntity) helper.getBlockEntity(SRC), helper.absolutePos(BUILT_MARK));
		ResourceLocation id = upload(helper, "back", SRC, new Vec3i(1, 1, 1), null);
		BuildSite site = buildFree(helper, id, ORIGIN);
		helper.succeedWhen(() -> {
			SignBlockEntity sign = builtSign(helper, site, ORIGIN);
			String back = sign.getBackText().getMessage(0, false).getString();
			helper.assertTrue(back.equals("Back door"), "the built sign lost its back words: '" + back + "'");
			click(helper, player, ORIGIN, false);
			helper.assertTrue(!helper.getBlockState(BUILT_MARK).is(Blocks.GOLD_BLOCK),
				"clicking the back of a sign built from an uploaded file ran its /setblock for a non-operator (a gold block appeared)");
			player.discard();
		});
	}

	/** A hanging sign (under a plank) from an uploaded file keeps its words and runs nothing when clicked. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1108cHangingSign"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1108cHangingSign")
	public void aHangingSignFromAnUploadedFileRunsNoCommand(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = survivor(helper);
		helper.setBlock(CONTROL.above(), Blocks.OAK_PLANKS);
		helper.setBlock(CONTROL, Blocks.OAK_HANGING_SIGN);
		commandSign((SignBlockEntity) helper.getBlockEntity(CONTROL), helper.absolutePos(CONTROL_MARK));
		click(helper, player, CONTROL, true);
		helper.assertTrue(helper.getBlockState(CONTROL_MARK).is(Blocks.GOLD_BLOCK), "(setup) clicking a hanging command sign ran nothing: " + helper.getBlockState(CONTROL_MARK));

		helper.setBlock(SRC.above(), Blocks.OAK_PLANKS);
		helper.setBlock(SRC, Blocks.OAK_HANGING_SIGN);
		commandSign((SignBlockEntity) helper.getBlockEntity(SRC), helper.absolutePos(BUILT_MARK));
		ResourceLocation id = upload(helper, "hanging", SRC, new Vec3i(1, 2, 1), null);
		BuildSite site = buildFree(helper, id, ORIGIN);
		helper.succeedWhen(() -> {
			SignBlockEntity sign = builtSign(helper, site, ORIGIN);
			helper.assertTrue(helper.getBlockState(ORIGIN).is(Blocks.OAK_HANGING_SIGN), "not a hanging sign: " + helper.getBlockState(ORIGIN));
			String words = sign.getFrontText().getMessage(0, false).getString();
			helper.assertTrue(words.equals("Free gold"), "the built hanging sign lost its words: '" + words + "'");
			click(helper, player, ORIGIN, true);
			click(helper, player, ORIGIN, false);
			helper.assertTrue(!helper.getBlockState(BUILT_MARK).is(Blocks.GOLD_BLOCK),
				"clicking a hanging sign built from an uploaded file ran its /setblock for a non-operator (a gold block appeared)");
			player.discard();
		});
	}

	/** Color and glow are looks: both sides keep them, while the click command is dropped. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1108cSignLooks"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1108cSignLooks")
	public void aSignFromAnUploadedFileKeepsItsColorAndGlow(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = survivor(helper);
		helper.setBlock(SRC, Blocks.OAK_SIGN);
		SignBlockEntity source = (SignBlockEntity) helper.getBlockEntity(SRC);
		commandSign(source, helper.absolutePos(BUILT_MARK));
		source.setText(source.getFrontText().setColor(DyeColor.RED).setHasGlowingText(true), true);
		source.setText(source.getBackText().setColor(DyeColor.LIGHT_BLUE).setHasGlowingText(true), false);
		ResourceLocation id = upload(helper, "looks", SRC, new Vec3i(1, 1, 1), null);
		BuildSite site = buildFree(helper, id, ORIGIN);
		helper.succeedWhen(() -> {
			SignBlockEntity sign = builtSign(helper, site, ORIGIN);
			SignText front = sign.getFrontText();
			SignText back = sign.getBackText();
			helper.assertTrue(front.getColor() == DyeColor.RED && front.hasGlowingText(),
				"the front lost its look: color " + front.getColor() + ", glowing " + front.hasGlowingText() + " (scanned red, glowing)");
			helper.assertTrue(back.getColor() == DyeColor.LIGHT_BLUE && back.hasGlowingText(),
				"the back lost its look: color " + back.getColor() + ", glowing " + back.hasGlowingText() + " (scanned light blue, glowing)");
			helper.assertTrue(sign.isWaxed(), "the built sign isn't waxed as scanned");
			helper.assertTrue(front.getMessage(0, false).getString().equals("Free gold"), "lost the words: " + front.getMessage(0, false).getString());
			click(helper, player, ORIGIN, true);
			helper.assertTrue(!helper.getBlockState(BUILT_MARK).is(Blocks.GOLD_BLOCK), "the colored sign still ran its command");
			player.discard();
		});
	}

	/** 384 characters is what a player can write on a line: a line of exactly 384 is kept whole, one of 385 trimmed to 384. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1108cSignLength"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1108cSignLength")
	public void aSignLineOf384IsKeptWholeAndOneOf385IsTrimmed(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(SRC, Blocks.OAK_SIGN);
		SignBlockEntity source = (SignBlockEntity) helper.getBlockEntity(SRC);
		String exact = "a".repeat(383) + "Z";
		String over = "b".repeat(384) + "Y";
		source.setText(source.getFrontText().setMessage(0, Component.literal(exact)).setMessage(1, Component.literal(over)), true);
		source.setText(source.getBackText().setMessage(2, Component.literal(over)), false);
		source.setWaxed(true);
		ResourceLocation id = upload(helper, "length", SRC, new Vec3i(1, 1, 1), null);
		BuildSite site = buildFree(helper, id, ORIGIN);
		helper.succeedWhen(() -> {
			SignBlockEntity sign = builtSign(helper, site, ORIGIN);
			String line0 = sign.getFrontText().getMessage(0, false).getString();
			String line1 = sign.getFrontText().getMessage(1, false).getString();
			String back2 = sign.getBackText().getMessage(2, false).getString();
			helper.assertTrue(line0.equals(exact), "a line of exactly 384 characters (as many as a player can write) wasn't kept whole: "
				+ line0.length() + " characters, ends '" + tail(line0) + "'");
			helper.assertTrue(line1.equals("b".repeat(384)), "a front line of 385 characters wasn't trimmed to the first 384: "
				+ line1.length() + " characters, ends '" + tail(line1) + "'");
			helper.assertTrue(back2.equals("b".repeat(384)), "a back line of 385 characters wasn't trimmed to the first 384: "
				+ back2.length() + " characters, ends '" + tail(back2) + "'");
		});
	}

	/**
	 * Selector and score parts are worked out with permission level 2 when a sign loads: from a file they would list the
	 * players and their scores. The built sign shows neither (control: the same data loaded straight into a sign does).
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa1108cSignSelector"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1108cSignSelector")
	public void aSignFromAnUploadedFileShowsNoPlayerNamesOrScores(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivor(helper);
		String name = player.getScoreboardName();
		String objectiveName = "qa1108c" + Long.toHexString(level.getGameTime() & 0xffff);
		Objective objective = level.getScoreboard().addObjective(objectiveName, ObjectiveCriteria.DUMMY, Component.literal("QA"),
			ObjectiveCriteria.RenderType.INTEGER, false, null);
		level.getScoreboard().getOrCreatePlayerScore(player, objective).set(4242);
		Leftovers.after(helper, () -> level.getScoreboard().removeObjective(objective));

		helper.setBlock(SRC, Blocks.OAK_SIGN);
		SignBlockEntity source = (SignBlockEntity) helper.getBlockEntity(SRC);
		source.setText(source.getFrontText()
			.setMessage(0, Component.literal("Who: ").append(Component.selector("@a", java.util.Optional.empty())))
			.setMessage(1, Component.literal("Gold: ").append(Component.score(name, objectiveName))), true);
		source.setWaxed(true);
		CompoundTag data = source.saveWithoutMetadata(level.registryAccess());
		ResourceLocation id = upload(helper, "who", SRC, new Vec3i(1, 1, 1), null);

		// control: vanilla works these parts out when the data loads into a sign
		helper.setBlock(CONTROL, Blocks.OAK_SIGN);
		SignBlockEntity control = (SignBlockEntity) helper.getBlockEntity(CONTROL);
		control.loadWithComponents(data, level.registryAccess());
		String c0 = control.getFrontText().getMessage(0, false).getString();
		String c1 = control.getFrontText().getMessage(1, false).getString();
		helper.assertTrue(c0.contains(name) && c1.contains("4242"), "(setup) vanilla didn't work out the parts, so the test can't tell: '" + c0 + "' / '" + c1 + "'");

		BuildSite site = buildFree(helper, id, ORIGIN);
		helper.succeedWhen(() -> {
			SignBlockEntity sign = builtSign(helper, site, ORIGIN);
			String l0 = sign.getFrontText().getMessage(0, false).getString();
			String l1 = sign.getFrontText().getMessage(1, false).getString();
			helper.assertTrue(l0.startsWith("Who:") && l1.startsWith("Gold:"), "the built sign lost its plain words: '" + l0 + "' / '" + l1 + "'");
			helper.assertTrue(!l0.contains(name), "a sign built from an uploaded file lists the players (selector worked out): '" + l0 + "'");
			helper.assertTrue(!l1.contains("4242"), "a sign built from an uploaded file shows a player's score: '" + l1 + "'");
			player.discard();
		});
	}

	/**
	 * A command block (with its command, set to run by itself) in an uploaded file: a builder never builds it and its
	 * command never runs; the rest of the file is built (control: one set to run by itself, placed directly, does run).
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa1108cCommandBlock"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1108cCommandBlock")
	public void aCommandBlockFromAnUploadedFileIsNeverBuiltNorRun(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, Blocks.OAK_PLANKS);
		helper.setBlock(SRC.above(), Blocks.COMMAND_BLOCK);
		BlockPos mark = helper.absolutePos(BUILT_MARK);
		((CommandBlockEntity) helper.getBlockEntity(SRC.above())).getCommandBlock()
			.setCommand("setblock " + mark.getX() + " " + mark.getY() + " " + mark.getZ() + " minecraft:gold_block");
		ResourceLocation id = upload(helper, "command", SRC, new Vec3i(1, 2, 1), blocks -> {
			for (int i = 0; i < blocks.size(); i++) {
				CompoundTag b = blocks.getCompound(i);
				if (b.contains("nbt", Tag.TAG_COMPOUND) && b.getCompound("nbt").contains("Command")) {
					b.getCompound("nbt").putBoolean("auto", true);
					b.getCompound("nbt").putBoolean("powered", true);
				}
			}
		});
		helper.setBlock(BUILT_MARK, Blocks.AIR);
		// control: a command block set to run by itself does run here, so a command the built one ran would show
		helper.setBlock(CONTROL, Blocks.COMMAND_BLOCK);
		BlockPos controlMark = helper.absolutePos(CONTROL_MARK);
		CommandBlockEntity control = (CommandBlockEntity) helper.getBlockEntity(CONTROL);
		control.getCommandBlock().setCommand("setblock " + controlMark.getX() + " " + controlMark.getY() + " " + controlMark.getZ() + " minecraft:gold_block");
		control.setAutomatic(true);
		BuildSite site = buildFree(helper, id, ORIGIN);
		long[] doneAt = {-1};
		helper.onEachTick(() -> {
			if (doneAt[0] < 0 && BuildSiteManager.get(level).get(site.id()) == null) {
				doneAt[0] = level.getGameTime();
			}
			helper.assertTrue(!helper.getBlockState(BUILT_MARK).is(Blocks.GOLD_BLOCK),
				"a command block from an uploaded file ran its command (a gold block appeared)");
			helper.assertTrue(!helper.getBlockState(ORIGIN.above()).is(Blocks.COMMAND_BLOCK),
				"a builder built a command block from an uploaded file: " + helper.getBlockState(ORIGIN.above()));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(CONTROL_MARK).is(Blocks.GOLD_BLOCK), "(setup) a command block set to run by itself ran nothing here, so the test can't tell");
			helper.assertTrue(doneAt[0] >= 0, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			helper.assertTrue(helper.getBlockState(ORIGIN).is(Blocks.OAK_PLANKS), "the rest of the file wasn't built: " + helper.getBlockState(ORIGIN));
			helper.assertTrue(level.getGameTime() - doneAt[0] >= 60, "watching 60 ticks after the build");
		});
	}

	/**
	 * A lectern holding a written book whose page runs a command when clicked: the book is contents, never copied, so the
	 * built lectern is empty, and shows no book it doesn't hold.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa1108cLecternBook"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1108cLecternBook")
	public void aLecternFromAnUploadedFileIsBuiltWithoutItsBook(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, Blocks.LECTERN);
		LecternBlockEntity source = (LecternBlockEntity) helper.getBlockEntity(SRC);
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		BlockPos mark = helper.absolutePos(BUILT_MARK);
		Component page = Component.literal("Click me").withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
			"/setblock " + mark.getX() + " " + mark.getY() + " " + mark.getZ() + " minecraft:gold_block")));
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Free gold"), "qa", 0,
			List.of(Filterable.passThrough(page)), true));
		LecternBlock.tryPlaceBook(null, level, helper.absolutePos(SRC), helper.getBlockState(SRC), book);
		helper.assertTrue(source.hasBook() && helper.getBlockState(SRC).getValue(BlockStateProperties.HAS_BOOK), "(setup) the lectern holds no book");
		ResourceLocation id = upload(helper, "lectern", SRC, new Vec3i(1, 1, 1), null);
		BuildSite site = buildFree(helper, id, ORIGIN);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			BlockEntity built = level.getBlockEntity(helper.absolutePos(ORIGIN));
			helper.assertTrue(built instanceof LecternBlockEntity, "no lectern built: " + helper.getBlockState(ORIGIN));
			helper.assertTrue(!((LecternBlockEntity) built).hasBook(), "a lectern built from an uploaded file holds its book: "
				+ ((LecternBlockEntity) built).getBook());
			helper.assertTrue(!helper.getBlockState(ORIGIN).getValue(BlockStateProperties.HAS_BOOK),
				"the built lectern holds no book but shows one (has_book=true): " + helper.getBlockState(ORIGIN));
		});
	}

	// ---------------------------------------------------------------------------------------------------------------
	// helpers

	private static ServerPlayer survivor(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		helper.assertTrue(!player.hasPermissions(2), "(setup) the mock player is an operator");
		Leftovers.after(helper, player::discard);
		return player;
	}

	/** Scans the box at {@code src} as a file, removes the source blocks and imports it as a player's upload does. */
	private static ResourceLocation upload(GameTestHelper helper, String name, BlockPos src, Vec3i size, Consumer<ListTag> edit) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(src), size, false, Blocks.STRUCTURE_VOID);
		CompoundTag saved = template.save(new CompoundTag());
		if (edit != null) {
			edit.accept(saved.getList("blocks", Tag.TAG_COMPOUND));
		}
		byte[] bytes;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(saved, out);
			bytes = out.toByteArray();
		} catch (IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
		for (int y = size.getY() - 1; y >= 0; y--) {
			for (int x = 0; x < size.getX(); x++) {
				for (int z = 0; z < size.getZ(); z++) {
					helper.setBlock(src.offset(x, y, z), Blocks.AIR);
				}
			}
		}
		try {
			return BlueprintImporter.importBytes(level.getServer(), "uploads/qa_1108c",
				"qa1108c_" + name + "_" + Long.toHexString(level.getGameTime()) + ".nbt", bytes).id();
		} catch (Exception e) {
			throw new GameTestAssertException("(setup) the upload didn't import: " + e);
		}
	}

	/** A builder at a bench builds the file at {@code origin}, with free materials (as the sign tests do). */
	private static BuildSite buildFree(GameTestHelper helper, ResourceLocation id, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer()));
		helper.setDayTime(2000);
		helper.setBlock(U_BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, U_BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(U_BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		return site;
	}

	private static SignBlockEntity builtSign(GameTestHelper helper, BuildSite site, BlockPos at) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
		BlockEntity built = level.getBlockEntity(helper.absolutePos(at));
		helper.assertTrue(built instanceof SignBlockEntity, "no sign built: " + helper.getBlockState(at));
		return (SignBlockEntity) built;
	}

	/** Both sides say "Free gold" and run /setblock at {@code mark} on a click; waxed. */
	private static void commandSign(SignBlockEntity sign, BlockPos mark) {
		Component words = Component.literal("Free gold").withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, setblock(mark))));
		sign.setText(sign.getFrontText().setMessage(0, words), true);
		sign.setText(sign.getBackText().setMessage(0, words), false);
		sign.setWaxed(true);
	}

	/** Plain front ("Front door"); the back's third line "Back door" runs /setblock at {@code mark}; waxed. */
	private static void backCommand(SignBlockEntity sign, BlockPos mark) {
		sign.setText(sign.getFrontText().setMessage(0, Component.literal("Front door")), true);
		Component words = Component.literal("Back door").withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, setblock(mark))));
		sign.setText(sign.getBackText().setMessage(0, words), false);
		sign.setWaxed(true);
	}

	private static String setblock(BlockPos mark) {
		return "/setblock " + mark.getX() + " " + mark.getY() + " " + mark.getZ() + " minecraft:gold_block";
	}

	/** The player stands two blocks south (front) or north (back) of the sign and right-clicks it with an empty hand. */
	private static void click(GameTestHelper helper, ServerPlayer player, BlockPos rel, boolean front) {
		BlockPos at = helper.absolutePos(rel);
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + (front ? 2.5 : -1.5));
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), front ? Direction.SOUTH : Direction.NORTH, at, false);
		player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
	}

	private static String tail(String s) {
		return s.length() <= 4 ? s : s.substring(s.length() - 4);
	}
}
