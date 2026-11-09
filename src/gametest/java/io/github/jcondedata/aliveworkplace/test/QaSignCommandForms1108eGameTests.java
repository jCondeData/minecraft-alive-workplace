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
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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
 * QA (lane e, 2026-11-08), B1044's fix from the other ways a hand-written file can spell a click command the game still
 * runs: a line saved as a JSON array whose first part carries the click, a key written with a unicode escape
 * (click with its E written as a unicode escape), and a line with spaces and a newline before it. Each control sign with the same saved line runs
 * the command when clicked; the sign a builder builds from the uploaded file keeps the words and runs nothing.
 */
public class QaSignCommandForms1108eGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(22, 2, 22);
	private static final BlockPos ORIGIN = new BlockPos(10, 2, 10);
	private static final BlockPos CONTROL = new BlockPos(22, 2, 4);
	private static final BlockPos BUILT_MARK = new BlockPos(14, 2, 16);
	private static final BlockPos CONTROL_MARK = new BlockPos(26, 2, 4);

	//$ gametest_ticks_batch HUGE '2400' '"qaSignCommandForms1108e"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaSignCommandForms1108e")
	public void aCommandInALineSavedAsAnArrayRunsNothing(GameTestHelper helper) {
		uploadAndBuild(helper, "an array", mark -> "[" + command(mark) + ",{\"text\":\"!\"}]");
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaSignCommandForms1108e2"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaSignCommandForms1108e2")
	public void aCommandWithAUnicodeEscapedKeyRunsNothing(GameTestHelper helper) {
		uploadAndBuild(helper, "a unicode-escaped key", mark -> command(mark).replace("\"clickEvent\"", "\"click\\u0045vent\""));
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaSignCommandForms1108e3"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaSignCommandForms1108e3")
	public void aCommandAfterSpacesAndANewlineRunsNothing(GameTestHelper helper) {
		uploadAndBuild(helper, "spaces and a newline first", mark -> "  \n\t " + command(mark));
	}

	/**
	 * A one-sign file whose first lines are {@code line} for the words "Free gold" with a run_command click event at a
	 * mark, as a player can write it by hand ({@code how} names the form). The control sign with the same saved data runs the command when clicked; the sign a
	 * builder builds from the uploaded file must not.
	 */
	private static void uploadAndBuild(GameTestHelper helper, String how, java.util.function.Function<BlockPos, String> line) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		helper.assertTrue(!player.hasPermissions(2), "(setup) the mock player is an operator");

		// The file: one waxed oak sign, written with the game, then its lines rewritten as a hand-made file could hold them
		helper.setBlock(SRC, Blocks.OAK_SIGN);
		((SignBlockEntity) helper.getBlockEntity(SRC)).setWaxed(true);
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		CompoundTag file = template.save(new CompoundTag());
		CompoundTag signData = file.getList("blocks", Tag.TAG_COMPOUND).getCompound(0).getCompound("nbt");
		helper.assertTrue(signData.contains("front_text"), "(setup) the scanned sign has no front_text: " + signData);
		setLines(signData, line.apply(helper.absolutePos(BUILT_MARK)));
		helper.setBlock(SRC, Blocks.AIR);

		// Control: a sign loaded with exactly this saved data runs the command for this survival player, so the game does
		// read such a line and a click in this test reaches its command
		helper.setBlock(CONTROL, Blocks.OAK_SIGN);
		CompoundTag controlData = signData.copy();
		setLines(controlData, line.apply(helper.absolutePos(CONTROL_MARK)));
		helper.getBlockEntity(CONTROL).loadWithComponents(controlData, level.registryAccess());
		click(helper, player, CONTROL);
		helper.assertTrue(helper.getBlockState(CONTROL_MARK).is(Blocks.GOLD_BLOCK),
			"(setup) a sign with this saved line (" + how + ") ran nothing when clicked, so the test can't tell: " + helper.getBlockState(CONTROL_MARK));

		byte[] bytes;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(file, out);
			bytes = out.toByteArray();
		} catch (IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
		ResourceLocation id;
		try {
			// the import a player's upload at the Table goes through (TableServer: folder "uploads/<player>")
			id = BlueprintImporter.importBytes(level.getServer(), "uploads/qa_signer",
				"gold_forms_" + Long.toHexString(level.getGameTime()) + ".nbt", bytes).id();
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
			String words = ((SignBlockEntity) built).getFrontText().getMessage(0, false).getString();
			helper.assertTrue(words.startsWith("Free gold"), "the built sign lost its words: '" + words + "'");
			click(helper, player, ORIGIN);
			helper.assertTrue(!helper.getBlockState(BUILT_MARK).is(Blocks.GOLD_BLOCK),
				"clicking the sign a builder built from an uploaded file whose line is written as " + how
					+ " ran its /setblock with permission level 2 for a non-operator (a gold block appeared); the sign should"
					+ " keep its words and run nothing");
			player.discard();
		});
	}

	/** The saved JSON of "Free gold" that runs /setblock at {@code mark} on a click. */
	private static String command(BlockPos mark) {
		return "{\"text\":\"Free gold\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/setblock " + mark.getX() + " "
			+ mark.getY() + " " + mark.getZ() + " minecraft:gold_block\"}}";
	}

	/** Sets the first line of both sides of the saved sign {@code data} to {@code line}. */
	private static void setLines(CompoundTag data, String line) {
		for (String side : new String[] {"front_text", "back_text"}) {
			CompoundTag text = data.getCompound(side);
			ListTag messages = text.getList("messages", Tag.TAG_STRING);
			messages.set(0, StringTag.valueOf(line));
			text.put("messages", messages);
			data.put(side, text);
		}
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
