package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * QA (lane b, 2026-11-08), from the builder's promise (builder.md, BlockEntityData: a blueprint's block data carries
 * looks, sign text among them, never anything that hands out free goods) and the upload rule (game rule
 * workplaceAllowUploads, on by default: any player may upload a blueprint file at a Blueprint Table). Vanilla lets only
 * operators set a sign's data from an item (SignBlockEntity.onlyOpCanSetNbt) because a sign's words can carry a
 * run_command click event, which runs with permission level 2 for whoever clicks the sign. An uploaded file is any
 * player's data, so a sign built from it must keep its words but not run a command when clicked.
 */
public class QaSignCommands1108BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(22, 2, 22);
	private static final BlockPos ORIGIN = new BlockPos(10, 2, 10);
	private static final BlockPos CONTROL = new BlockPos(22, 2, 4);
	private static final BlockPos BUILT_MARK = new BlockPos(14, 2, 16);
	private static final BlockPos CONTROL_MARK = new BlockPos(26, 2, 4);

	//$ gametest_ticks_batch HUGE '2400' '"qaSignCommands1108"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaSignCommands1108")
	public void aSignFromAnUploadedFileKeepsItsWordsButRunsNoCommand(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		helper.assertTrue(!player.hasPermissions(2), "(setup) the mock player is an operator");

		// Control: a sign carrying the command, placed directly, does run it when this survival player clicks it, so a
		// click in this test really reaches the sign's commands
		helper.setBlock(CONTROL, Blocks.OAK_SIGN);
		commandSign((SignBlockEntity) helper.getBlockEntity(CONTROL), helper.absolutePos(CONTROL_MARK));
		click(helper, player, CONTROL);
		helper.assertTrue(helper.getBlockState(CONTROL_MARK).is(Blocks.GOLD_BLOCK),
			"(setup) clicking a command sign ran nothing, so the test can't tell: " + helper.getBlockState(CONTROL_MARK));

		// The uploaded file: one waxed oak sign whose words "Free gold" run /setblock ... gold_block when clicked
		helper.setBlock(SRC, Blocks.OAK_SIGN);
		commandSign((SignBlockEntity) helper.getBlockEntity(SRC), helper.absolutePos(BUILT_MARK));
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		byte[] bytes;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(template.save(new CompoundTag()), out);
			bytes = out.toByteArray();
		} catch (IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
		helper.setBlock(SRC, Blocks.AIR);
		ResourceLocation id;
		try {
			// the import a player's upload at the Table goes through (TableServer: folder "uploads/<player>")
			id = BlueprintImporter.importBytes(level.getServer(), "uploads/qa_signer",
				"free_gold_" + Long.toHexString(level.getGameTime()) + ".nbt", bytes).id();
		} catch (Exception e) {
			throw new GameTestAssertException("(setup) the upload didn't import: " + e);
		}

		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer()));
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			BlockEntity built = level.getBlockEntity(helper.absolutePos(ORIGIN));
			helper.assertTrue(built instanceof SignBlockEntity, "no sign built: " + helper.getBlockState(ORIGIN));
			SignBlockEntity sign = (SignBlockEntity) built;
			String words = sign.getFrontText().getMessage(0, false).getString();
			helper.assertTrue(words.equals("Free gold"), "the built sign lost its words: '" + words + "'");
			click(helper, player, ORIGIN);
			helper.assertTrue(!helper.getBlockState(BUILT_MARK).is(Blocks.GOLD_BLOCK),
				"clicking the sign a builder built from an uploaded file ran its /setblock with permission level 2 for a"
					+ " non-operator player (a gold block appeared); the sign should keep its words and run nothing");
			player.discard();
		});
	}

	/**
	 * A sign's words can also hold computed parts (nbt, selector, score) that vanilla works out when the sign loads, with
	 * permission level 2 (SignBlockEntity.loadLines). From an uploaded file that lets any player read what only an
	 * operator could: here another player's position. The built sign must not show it.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaSignCommands1108b"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaSignCommands1108b")
	public void aSignFromAnUploadedFileDoesNotRevealAPlayersPosition(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos stand = helper.absolutePos(new BlockPos(27, 2, 27));
		player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
		String x = Integer.toString(stand.getX());

		helper.setBlock(SRC, Blocks.OAK_SIGN);
		SignBlockEntity source = (SignBlockEntity) helper.getBlockEntity(SRC);
		Component words = Component.literal("Base: ").append(Component.nbt("Pos", false, java.util.Optional.empty(),
			new net.minecraft.network.chat.contents.EntityDataSource("@p")));
		source.setText(source.getFrontText().setMessage(0, words), true);
		source.setWaxed(true);
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		byte[] bytes;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(template.save(new CompoundTag()), out);
			bytes = out.toByteArray();
		} catch (IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
		helper.setBlock(SRC, Blocks.AIR);
		ResourceLocation id;
		try {
			id = BlueprintImporter.importBytes(level.getServer(), "uploads/qa_signer",
				"where_" + Long.toHexString(level.getGameTime()) + ".nbt", bytes).id();
		} catch (Exception e) {
			throw new GameTestAssertException("(setup) the upload didn't import: " + e);
		}

		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer()));
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			BlockEntity built = level.getBlockEntity(helper.absolutePos(ORIGIN));
			helper.assertTrue(built instanceof SignBlockEntity, "no sign built: " + helper.getBlockState(ORIGIN));
			String shown = ((SignBlockEntity) built).getFrontText().getMessage(0, false).getString();
			helper.assertTrue(shown.startsWith("Base:"), "the built sign lost its words: '" + shown + "'");
			helper.assertTrue(!shown.contains(x), "the sign a builder built from an uploaded file shows a player's position, worked out"
				+ " with permission level 2: '" + shown + "' (the player stands at x " + x + ")");
			player.discard();
		});
	}

	/** Both sides say "Free gold"; the words run /setblock at {@code mark} on a click. Waxed, as a map maker leaves them. */
	private static void commandSign(SignBlockEntity sign, BlockPos mark) {
		String command = "/setblock " + mark.getX() + " " + mark.getY() + " " + mark.getZ() + " minecraft:gold_block";
		Component words = Component.literal("Free gold").withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
		sign.setText(sign.getFrontText().setMessage(0, words), true);
		sign.setText(sign.getBackText().setMessage(0, words), false);
		sign.setWaxed(true);
	}

	/** The player stands two blocks south of the sign and right-clicks it with an empty hand. */
	private static void click(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
		BlockPos at = helper.absolutePos(rel);
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.SOUTH, at, false);
		player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
	}
}
