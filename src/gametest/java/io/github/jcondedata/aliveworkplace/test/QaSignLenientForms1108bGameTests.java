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
 * QA (lane b, 2026-11-08), from B1062's expected fix: "every line the lenient parser reads as a text component is
 * cleaned the same way", and B1044/B1047's promise that a sign from an uploaded file keeps its words but never runs a
 * command nor works out a player's name or data. The game's lenient reader also takes keys without quotes, values in
 * single quotes and comments between keys, and works out selector and nbt parts inside a translation's arguments or a
 * line's later parts. One sign holds all four forms; a control sign with the same saved lines proves the game reads each.
 */
public class QaSignLenientForms1108bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(22, 2, 22);
	private static final BlockPos ORIGIN = new BlockPos(10, 2, 10);
	private static final BlockPos CONTROL = new BlockPos(22, 2, 4);
	private static final BlockPos[] BUILT_MARKS = {new BlockPos(14, 2, 16), new BlockPos(16, 2, 16)};
	private static final BlockPos[] CONTROL_MARKS = {new BlockPos(26, 2, 4), new BlockPos(26, 2, 8)};

	//$ gametest_ticks_batch HUGE '2400' '"qaSignLenientForms1108b"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaSignLenientForms1108b")
	public void aSignWrittenInLooseJsonFromAnUploadedFileRunsAndRevealsNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		helper.assertTrue(!player.hasPermissions(2), "(setup) the mock player is an operator");
		String name = player.getGameProfile().getName();

		// The file: one waxed oak sign, written with the game, then its lines rewritten as a hand-made file could hold them
		helper.setBlock(SRC, Blocks.OAK_SIGN);
		((SignBlockEntity) helper.getBlockEntity(SRC)).setWaxed(true);
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		CompoundTag file = template.save(new CompoundTag());
		CompoundTag signData = file.getList("blocks", Tag.TAG_COMPOUND).getCompound(0).getCompound("nbt");
		helper.assertTrue(signData.contains("front_text"), "(setup) the scanned sign has no front_text: " + signData);
		setLines(signData, lines(helper, BUILT_MARKS));
		helper.setBlock(SRC, Blocks.AIR);

		// Control: a sign loaded with exactly these saved lines runs both commands and shows the player's name and position,
		// so the game does read every form and a click in this test reaches its commands
		helper.setBlock(CONTROL, Blocks.OAK_SIGN);
		CompoundTag controlData = signData.copy();
		setLines(controlData, lines(helper, CONTROL_MARKS));
		helper.getBlockEntity(CONTROL).loadWithComponents(controlData, level.registryAccess());
		SignBlockEntity control = (SignBlockEntity) helper.getBlockEntity(CONTROL);
		String controlWho = control.getFrontText().getMessage(2, false).getString();
		String controlAt = control.getFrontText().getMessage(3, false).getString();
		helper.assertTrue(controlWho.contains(name), "(setup) the control's translation shows no player name: '" + controlWho + "'");
		helper.assertTrue(controlAt.length() > "At: ".length(), "(setup) the control's nbt part shows nothing: '" + controlAt + "'");
		click(helper, player, CONTROL);
		for (BlockPos mark : CONTROL_MARKS) {
			helper.assertTrue(helper.getBlockState(mark).is(Blocks.GOLD_BLOCK),
				"(setup) the control sign ran nothing at " + mark + " when clicked, so the test can't tell: " + helper.getBlockState(mark));
		}

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
				"loose_gold_" + Long.toHexString(level.getGameTime()) + ".nbt", bytes).id();
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
			String[] expected = {"Free gold", "More gold", "Who: ", "At: "};
			for (int i = 0; i < 4; i++) {
				String words = sign.getFrontText().getMessage(i, false).getString();
				helper.assertTrue(words.equals(expected[i]), "line " + (i + 1) + " of the sign a builder built from an uploaded file in loose JSON reads '"
					+ words + "', expected '" + expected[i] + "' (its words, with no player's name or data worked out)");
			}
			click(helper, player, ORIGIN);
			for (BlockPos mark : BUILT_MARKS) {
				helper.assertTrue(!helper.getBlockState(mark).is(Blocks.GOLD_BLOCK),
					"clicking the sign a builder built from an uploaded file written in loose JSON (keys without quotes, a comment"
						+ " between keys) ran its /setblock at " + mark + " with permission level 2 for a non-operator; it should run nothing");
			}
			player.discard();
		});
	}

	/**
	 * The four saved lines: a command with keys unquoted and values in single quotes; a command behind a comment between
	 * keys; a translation whose argument is an every-player selector; words followed by a player's position (nbt part).
	 */
	private static String[] lines(GameTestHelper helper, BlockPos[] marks) {
		BlockPos a = helper.absolutePos(marks[0]);
		BlockPos b = helper.absolutePos(marks[1]);
		return new String[] {
			"{text:'Free gold',clickEvent:{action:run_command,value:'/setblock " + a.getX() + " " + a.getY() + " " + a.getZ() + " minecraft:gold_block'}}",
			"{\"text\":\"More gold\",/* a note */\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/setblock " + b.getX() + " " + b.getY() + " "
				+ b.getZ() + " minecraft:gold_block\"}}",
			"{translate:'Who: %s',with:[{selector:'@a'}]}",
			"{text:'At: ',extra:[{nbt:Pos,entity:'@p'}]}"
		};
	}

	/** Sets the four lines of both sides of the saved sign {@code data}. */
	private static void setLines(CompoundTag data, String[] lines) {
		for (String side : new String[] {"front_text", "back_text"}) {
			CompoundTag text = data.getCompound(side);
			ListTag messages = new ListTag();
			for (String line : lines) {
				messages.add(StringTag.valueOf(line));
			}
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
