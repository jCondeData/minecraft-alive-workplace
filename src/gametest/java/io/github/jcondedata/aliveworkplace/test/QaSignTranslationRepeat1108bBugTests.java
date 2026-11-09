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
 * QA (lane b, 2026-11-08), from B1047's promise: a sign built from an uploaded file holds no more words than a player
 * could write (384 letters a line; a longer line is cut to its plain words). A translation's arguments are shown once
 * for each {@code %1$s} in it, so a short line can repeat its words many times over: ten {@code %1$s} nested five deep
 * is a line of about 330 characters of JSON that shows 100,000 letters, and eight deep shows 100,000,000, which every
 * client near the sign works through to draw it. A player's sign shows at most 384 letters a line.
 */
public class QaSignTranslationRepeat1108bBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(22, 2, 22);
	private static final BlockPos ORIGIN = new BlockPos(10, 2, 10);
	private static final int MAX_LINE = 384;

	//$ gametest_ticks_batch HUGE '2400' '"qaSignTranslationRepeat1108b"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaSignTranslationRepeat1108b")
	public void aLineRepeatedThroughNestedTranslationsShowsNoMoreThanAPlayerCouldWrite(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();

		// The file: one waxed oak sign, written with the game, then its first line rewritten as a hand-made file could hold it
		helper.setBlock(SRC, Blocks.OAK_SIGN);
		((SignBlockEntity) helper.getBlockEntity(SRC)).setWaxed(true);
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		CompoundTag file = template.save(new CompoundTag());
		CompoundTag signData = file.getList("blocks", Tag.TAG_COMPOUND).getCompound(0).getCompound("nbt");
		helper.assertTrue(signData.contains("front_text"), "(setup) the scanned sign has no front_text: " + signData);
		String line = repeated(10, 5);
		CompoundTag text = signData.getCompound("front_text");
		ListTag messages = text.getList("messages", Tag.TAG_STRING);
		messages.set(0, StringTag.valueOf(line));
		text.put("messages", messages);
		signData.put("front_text", text);
		helper.setBlock(SRC, Blocks.AIR);

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
				"echo_sign_" + Long.toHexString(level.getGameTime()) + ".nbt", bytes).id();
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
			helper.assertTrue(!words.isEmpty() && words.chars().allMatch(c -> c == 'W'), "(setup) the built line isn't W's: '"
				+ words.substring(0, Math.min(40, words.length())) + "'");
			helper.assertTrue(words.length() <= MAX_LINE, "the first line of a sign a builder built from an uploaded file shows "
				+ words.length() + " letters from " + line.length() + " characters of saved JSON (one W repeated through nested"
				+ " translations); a player's line shows at most " + MAX_LINE + ", and nested three levels deeper the same line"
				+ " would be 100,000,000 letters every client near it works through to draw the sign");
		});
	}

	/** A line showing one W {@code times}^{@code depth} times: each level is a translation of {@code times} %1$s. */
	private static String repeated(int times, int depth) {
		String key = "%1$s".repeat(times);
		String json = "\"W\"";
		for (int i = 0; i < depth; i++) {
			json = "{\"translate\":\"" + key + "\",\"with\":[" + json + "]}";
		}
		return json;
	}
}
