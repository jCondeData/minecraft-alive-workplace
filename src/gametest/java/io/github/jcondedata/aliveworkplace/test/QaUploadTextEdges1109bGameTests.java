package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * QA (lane b, 2026-11-09), from B1074's and B1088's promise: a sign line from an uploaded blueprint file shows at most
 * the 384 letters a player could write, counted as the game shows them, and the builder copies it without trouble.
 * The edges here: a translation's fallback text (shown when no language knows the key) repeating its argument, nested;
 * placeholders the game can't read (a huge argument number, a %d); plain text and translations at exactly the limit and
 * one past it. All through a player's upload and a builder building it.
 */
public class QaUploadTextEdges1109bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(26, 2, 26);
	private static final BlockPos ORIGIN = new BlockPos(10, 2, 10);
	private static final int MAX_LINE = 384;

	//$ gametest_ticks_batch HUGE '2400' '"qaUploadTextEdges1109bFallback"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUploadTextEdges1109bFallback")
	public void aFallbackThatRepeatsItsArgumentNestedShowsNoMoreThanAPlayerCouldWrite(GameTestHelper helper) {
		// No language knows the key, so the game shows the fallback: ten copies of the argument, six deep = a million W
		String format = "%1$s".repeat(10);
		String json = "\"W\"";
		for (int i = 0; i < 6; i++) {
			json = "{\"translate\":\"qa.no.such.key\",\"fallback\":\"" + format + "\",\"with\":[" + json + "]}";
		}
		String line = json;
		helper.assertTrue(line.length() < 1024, "(setup) the line is " + line.length() + " characters");
		build(helper, Blocks.OAK_SIGN, List.of(nbt -> line(nbt, line), nbt -> line(nbt, "{\"translate\":\"qa.no.such.key\","
			+ "\"fallback\":\"%1$s and %1$s\",\"with\":[\"Ada\"]}")), built -> {
			String words = shown(helper, built.get(0));
			helper.assertTrue(words.length() <= MAX_LINE, "a sign built from an uploaded line whose fallback repeats its argument "
				+ "ten times, six deep, shows " + words.length() + " letters; a player's line shows at most " + MAX_LINE);
			String small = shown(helper, built.get(1));
			helper.assertTrue(small.equals("Ada and Ada"), "a short fallback line shows '" + small + "', expected 'Ada and Ada'"
				+ " (the game's own words, kept as written)");
		});
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaUploadTextEdges1109bFormats"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUploadTextEdges1109bFormats")
	public void placeholdersTheGameCantReadNeitherStopTheBuildNorGrowTheLine(GameTestHelper helper) {
		// A huge argument number, an argument past the end, a %d and a lone % at the end: the game shows each format as
		// it is; the builder still finishes and every line stays within a player's
		List<String> lines = List.of(
			"{\"translate\":\"qa.no.such.key\",\"fallback\":\"%99999999999$s%1$s%1$s\",\"with\":[\"" + "Z".repeat(300) + "\"]}",
			"{\"translate\":\"qa.no.such.key\",\"fallback\":\"%3$s%1$s%1$s\",\"with\":[\"" + "Z".repeat(300) + "\"]}",
			"{\"translate\":\"qa.no.such.key\",\"fallback\":\"%d%1$s%1$s\",\"with\":[\"" + "Z".repeat(300) + "\"]}",
			"{\"translate\":\"qa.no.such.key\",\"fallback\":\"%1$s%1$s%\",\"with\":[\"" + "Z".repeat(300) + "\"]}",
			"{\"translate\":\"qa.no.such.key\",\"fallback\":\"100%% %1$s\",\"with\":[\"sure\"]}");
		List<Consumer<CompoundTag>> edits = new ArrayList<>();
		for (String l : lines) {
			edits.add(nbt -> line(nbt, l));
		}
		build(helper, Blocks.OAK_SIGN, edits, built -> {
			for (int i = 0; i < lines.size(); i++) {
				String words = shown(helper, built.get(i));
				helper.assertTrue(words.length() <= MAX_LINE, "sign " + i + " built from the uploaded line " + lines.get(i).substring(0, 60)
					+ "... shows " + words.length() + " letters; a player's line shows at most " + MAX_LINE);
			}
			String percent = shown(helper, built.get(4));
			helper.assertTrue(percent.equals("100% sure"), "a fallback with an escaped percent shows '" + percent + "', expected '100% sure'");
		});
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaUploadTextEdges1109bLimit"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUploadTextEdges1109bLimit")
	public void aLineOfExactlyAPlayersLengthIsKeptAndOneMoreIsCut(GameTestHelper helper) {
		// 384 letters through a translation (two arguments of 192) is a player's longest line: kept whole, with its color;
		// 385 is cut to 384
		String half = "Q".repeat(MAX_LINE / 2);
		String exact = "{\"translate\":\"qa.no.such.key\",\"fallback\":\"%s%s\",\"with\":[\"" + half + "\",\"" + half + "\"],\"color\":\"red\"}";
		String over = "{\"translate\":\"qa.no.such.key\",\"fallback\":\"%s%s\",\"with\":[\"" + half + "\",\"" + half + "Q\"],\"color\":\"red\"}";
		build(helper, Blocks.OAK_SIGN, List.of(nbt -> line(nbt, exact), nbt -> line(nbt, over)), built -> {
			Component kept = ((SignBlockEntity) built.get(0)).getFrontText().getMessage(0, false);
			helper.assertTrue(kept.getString().length() == MAX_LINE, "a " + MAX_LINE + "-letter uploaded line shows "
				+ kept.getString().length() + " letters; a player's line of that length is kept whole");
			helper.assertTrue(kept.getStyle().getColor() != null && "red".equals(kept.getStyle().getColor().serialize()),
				"a " + MAX_LINE + "-letter red line lost its color: " + kept.getStyle());
			String cut = shown(helper, built.get(1));
			helper.assertTrue(cut.length() == MAX_LINE, "a " + (MAX_LINE + 1) + "-letter uploaded line shows " + cut.length()
				+ " letters, expected it cut to " + MAX_LINE);
		});
	}

	// ---- helpers ----

	private static String shown(GameTestHelper helper, BlockEntity blockEntity) {
		helper.assertTrue(blockEntity instanceof SignBlockEntity, "(setup) no sign built: " + blockEntity);
		return ((SignBlockEntity) blockEntity).getFrontText().getMessage(0, false).getString();
	}

	/**
	 * Scans one {@code block} the game placed (for its real saved form), writes a file of one such block per edit along
	 * x, each with its saved data changed by that edit, imports it as a player's upload, lets a builder build it with free
	 * materials, then runs {@code check} on the built block entities in order.
	 */
	static void build(GameTestHelper helper, Block block, List<Consumer<CompoundTag>> edits, Consumer<List<BlockEntity>> check) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, block);
		if (helper.getBlockEntity(SRC) instanceof SignBlockEntity sign) {
			sign.setWaxed(true);
		}
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(SRC, Blocks.AIR);
		CompoundTag file = template.save(new CompoundTag());
		CompoundTag one = file.getList("blocks", Tag.TAG_COMPOUND).getCompound(0);
		ListTag blocks = new ListTag();
		for (int x = 0; x < edits.size(); x++) {
			CompoundTag entry = one.copy();
			CompoundTag nbt = entry.getCompound("nbt");
			edits.get(x).accept(nbt);
			entry.put("nbt", nbt);
			ListTag pos = new ListTag();
			pos.add(IntTag.valueOf(x));
			pos.add(IntTag.valueOf(0));
			pos.add(IntTag.valueOf(0));
			entry.put("pos", pos);
			blocks.add(entry);
		}
		file.put("blocks", blocks);
		ListTag size = new ListTag();
		size.add(IntTag.valueOf(edits.size()));
		size.add(IntTag.valueOf(1));
		size.add(IntTag.valueOf(1));
		file.put("size", size);
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
			id = BlueprintImporter.importBytes(level.getServer(), "uploads/qa_texter",
				"qa1109b_" + Long.toHexString(level.getGameTime()) + "_" + edits.size() + ".nbt", bytes).id();
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
		BlockPos origin = helper.absolutePos(ORIGIN);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		boolean[] checked = {false};
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			if (!checked[0]) {
				List<BlockEntity> built = new ArrayList<>();
				for (int x = 0; x < edits.size(); x++) {
					built.add(level.getBlockEntity(origin.offset(x, 0, 0)));
				}
				check.accept(built);
				checked[0] = true;
			}
		});
	}

	static void line(CompoundTag nbt, String json) {
		CompoundTag text = nbt.getCompound("front_text");
		ListTag messages = text.getList("messages", Tag.TAG_STRING);
		messages.set(0, StringTag.valueOf(json));
		text.put("messages", messages);
		nbt.put("front_text", text);
	}
}
