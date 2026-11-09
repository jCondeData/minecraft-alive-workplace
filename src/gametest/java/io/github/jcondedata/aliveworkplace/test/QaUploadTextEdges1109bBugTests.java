package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BlockEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;

import java.util.List;

/**
 * QA (lane b, 2026-11-09), from B1074's and B1088's promise (a sign line from an uploaded file shows at most the 384
 * letters a player could write, counted as the game shows them), two ways past it:
 * <ul>
 * <li>a keybind part names a key binding; when no binding has that name the game shows the language's text for the name
 * (KeyMapping.createNameSupplier: Component.translatable(name)), so a keybind of a long language key shows that whole
 * text while BlockText counts only the name;</li>
 * <li>a line nested thousands of levels deep (an NBT string holds 65,535 bytes, so 30,000 nested arrays fit in a file of
 * a few hundred bytes) is walked recursively (BlockText.strip) before it is measured, so the builder copying it throws a
 * StackOverflowError on the server thread. Through a real upload and a builder (tried once, not kept as a test because it
 * stops the whole test server) the upload imports and the server crashes with "Ticking entity" (the builder) when the
 * builder reaches the sign; this test calls the step the builder runs, so the suite survives it.</li>
 * </ul>
 */
public class QaUploadTextEdges1109bBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final int MAX_LINE = 384;

	//$ gametest_ticks_batch HUGE '2400' '"qaUploadTextEdges1109bKeybind"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUploadTextEdges1109bKeybind")
	public void anUploadedLineOfKeybindsToLongLanguageTextsShowsNoMoreThanAPlayerCouldWrite(GameTestHelper helper) {
		// No key binding is called "multiplayerWarning.message", so a client shows that key's English text for each part;
		// 14 names are 364 letters, under a player's 384, so BlockText keeps the line
		String key = "multiplayerWarning.message";
		int one = Component.translatable(key).getString().length();
		helper.assertTrue(one > 200, "(setup) " + key + " shows only " + one + " letters");
		String part = "{\"keybind\":\"" + key + "\"}";
		String line = "[" + String.join(",", java.util.Collections.nCopies(14, part)) + "]";
		helper.assertTrue(line.length() < 1024, "(setup) the line is " + line.length() + " characters");
		QaUploadTextEdges1109bGameTests.build(helper, Blocks.OAK_SIGN, List.of(nbt -> QaUploadTextEdges1109bGameTests.line(nbt, line)), built -> {
			helper.assertTrue(built.get(0) instanceof SignBlockEntity, "(setup) no sign built: " + built.get(0));
			Component kept = ((SignBlockEntity) built.get(0)).getFrontText().getMessage(0, false);
			// what a client draws: each keybind part with no such binding shows Component.translatable(name)
			int shown = shownOnAClient(kept);
			helper.assertTrue(shown <= MAX_LINE, "the first line of a sign a builder built from an uploaded file shows " + shown
				+ " letters on a client (14 keybind parts named " + key + ", " + line.length() + " characters of JSON); a player's"
				+ " line shows at most " + MAX_LINE);
		});
	}

	//$ gametest_ticks_batch HUGE '400' '"qaUploadTextEdges1109bDeep"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaUploadTextEdges1109bDeep")
	public void aLineNestedThirtyThousandDeepIsCopiedWithoutAnError(GameTestHelper helper) {
		// The builder copies a sign's data with BlockEntityData.sanitize (BuilderWork, when it places the block); an error
		// thrown there is thrown on the server thread while the builder ticks
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, Blocks.OAK_SIGN);
		BlockEntity sign = helper.getBlockEntity(pos);
		CompoundTag nbt = sign.saveWithoutMetadata(helper.getLevel().registryAccess());
		int depth = 30000;
		String line = "[".repeat(depth) + "\"deep\"" + "]".repeat(depth);
		QaUploadTextEdges1109bGameTests.line(nbt, line);
		CompoundTag clean;
		try {
			clean = BlockEntityData.sanitize(helper.getLevel(), helper.absolutePos(pos), sign.getBlockState(), sign, nbt);
		} catch (StackOverflowError e) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("copying a sign whose uploaded line is " + depth
				+ " nested arrays (" + line.length() + " characters, a few hundred bytes in a file) threw a StackOverflowError"
				+ " on the server thread; expected the line cut to its words like any too-long line");
		}
		String kept = clean.getCompound("front_text").getList("messages", 8).getString(0);
		helper.assertTrue(kept.length() <= 2 * MAX_LINE + 256, "the deep line was kept at " + kept.length() + " characters of JSON");
		helper.succeed();
	}

	//$ gametest_ticks_batch HUGE '400' '"qaUploadTextEdges1109bDeepName"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaUploadTextEdges1109bDeepName")
	public void aChestNameNestedThirtyThousandDeepIsCopiedWithoutAnError(GameTestHelper helper) {
		// A chest from a file keeps only its name (never its contents); the name goes through the same cleaning
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, Blocks.CHEST);
		BlockEntity chest = helper.getBlockEntity(pos);
		CompoundTag nbt = chest.saveWithoutMetadata(helper.getLevel().registryAccess());
		int depth = 30000;
		String name = "[".repeat(depth) + "\"Deep chest\"" + "]".repeat(depth);
		nbt.putString("CustomName", name);
		CompoundTag clean;
		try {
			clean = BlockEntityData.sanitize(helper.getLevel(), helper.absolutePos(pos), chest.getBlockState(), chest, nbt);
		} catch (StackOverflowError e) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("copying a chest whose uploaded name is " + depth
				+ " nested arrays threw a StackOverflowError on the server thread; expected the name cut to its words");
		}
		helper.assertTrue(clean == null || clean.getString("CustomName").length() <= 2 * MAX_LINE + 256,
			"the deep name was kept at " + clean.getString("CustomName").length() + " characters of JSON");
		helper.succeed();
	}

	/** The letters a client shows for {@code component}: a keybind with no such binding shows its name's translation. */
	private static int shownOnAClient(Component component) {
		int[] count = {0};
		component.visit((style, text) -> {
			count[0] += text.length();
			return java.util.Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
		// the server has no key bindings, so KeybindContents shows the raw name there; count the client's instead
		for (Component part : flatten(component)) {
			if (part.getContents() instanceof net.minecraft.network.chat.contents.KeybindContents keybind) {
				count[0] += Component.translatable(keybind.getName()).getString().length() - keybind.getName().length();
			}
		}
		return count[0];
	}

	private static List<Component> flatten(Component component) {
		List<Component> out = new java.util.ArrayList<>();
		out.add(component);
		for (Component sibling : component.getSiblings()) {
			out.addAll(flatten(sibling));
		}
		return out;
	}
}
