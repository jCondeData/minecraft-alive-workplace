package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintNames;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * B458: the name a player gives their own build is kept as typed, apostrophes and accents too, though its id (and its
 * file) holds only a-z, 0-9 and _. Through the player's own entry points: a Scan Tool renamed in an anvil and saved with
 * a sneak-right-click, and a file uploaded at the Blueprint Table (the import every upload goes through).
 */
public class BlueprintNamesGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** The words of the last line {@code seen}, with every argument of its sentence filled in, as a player reads them. */
	static String lastText(List<Component> seen) {
		if (seen.isEmpty()) {
			return "(nothing)";
		}
		return text(seen.get(seen.size() - 1));
	}

	private static String text(Component c) {
		StringBuilder out = new StringBuilder();
		if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
			out.append(t.getKey());
			for (Object arg : t.getArgs()) {
				out.append(' ').append(arg instanceof Component a ? text(a) : String.valueOf(arg));
			}
		} else {
			out.append(c.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents p ? p.text() : c.getString());
		}
		for (Component sibling : c.getSiblings()) {
			out.append(text(sibling));
		}
		return out.toString();
	}

	/** Scans the plank at (3,2,3) with a tool named {@code name} (none: unnamed) and returns the blueprint it gave. */
	private static ItemStack scan(GameTestHelper helper, ServerPlayer player, String name) {
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.OAK_PLANKS);
		ItemStack tool = new ItemStack(ModItems.SCAN_TOOL);
		if (name != null) {
			tool.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, tool);
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT));
		QaStageThreeGameTests.click(helper, player, new BlockPos(3, 2, 3));
		QaStageThreeGameTests.click(helper, player, new BlockPos(4, 3, 4));
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		ItemStack found = ItemStack.EMPTY;
		for (ItemStack stack : player.getInventory().items) {
			if (stack.is(ModItems.BLUEPRINT)) {
				found = stack;
			}
		}
		helper.assertTrue(!found.isEmpty(), "no blueprint saved");
		player.getInventory().removeItem(found);
		return found;
	}

	/**
	 * Two scans named the same "Ana's Crème ..." are told apart as "... 2"; the blueprint item, the saved line and every
	 * name read from the id show the apostrophe and the accent; the names survive the world being saved and loaded again
	 * (the saved data read back), and the ids stay plain resource-location characters.
	 */
	//$ gametest_batch AREA '"blueprintNamesScan"'
	@GameTest(template = AREA, batch = "blueprintNamesScan")
	public void aScanToolNameIsKeptAsTypedAndSurvivesASave(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		String typed = "Ana's Crème Brûlée " + Long.toHexString(helper.getLevel().getGameTime());
		ItemStack first = scan(helper, player, typed);
		helper.assertTrue(lastText(seen).startsWith("message.aliveworkplace.scan.saved") && lastText(seen).contains(typed), "the saved line: " + lastText(seen));
		ItemStack second = scan(helper, player, typed);
		ResourceLocation one = BlueprintItem.data(first).orElseThrow().structure();
		ResourceLocation two = BlueprintItem.data(second).orElseThrow().structure();
		helper.assertTrue(one.getPath().matches("[a-z0-9_/]+") && two.getPath().matches("[a-z0-9_/]+"), "ids not plain: " + one + ", " + two);
		helper.assertTrue(Blueprints.displayName(one).getString().equals(typed), "first called " + Blueprints.displayName(one).getString());
		helper.assertTrue(Blueprints.displayName(two).getString().equals(typed + " 2"), "second called " + Blueprints.displayName(two).getString());
		helper.assertTrue(first.getHoverName().getString().contains(typed), "the blueprint item is called " + first.getHoverName().getString());
		// save and reload: the names written with the world are read back as they were
		var data = BlueprintNames.Data.get(helper.getLevel().getServer());
		CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
		var loaded = BlueprintNames.Data.load(saved, helper.getLevel().registryAccess());
		helper.assertTrue(loaded.name(one).orElse("").equals(typed), "after a reload the first is called " + loaded.name(one));
		helper.assertTrue(loaded.name(two).orElse("").equals(typed + " 2"), "after a reload the second is called " + loaded.name(two));
		helper.succeed();
	}

	/** An unnamed Scan Tool still calls the build "<Player>s Build", made from its id, and keeps no name for it. */
	//$ gametest_batch AREA '"blueprintNamesUnnamed"'
	@GameTest(template = AREA, batch = "blueprintNamesUnnamed")
	public void anUnnamedScanIsStillCalledByItsId(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		ResourceLocation id = BlueprintItem.data(scan(helper, player, null)).orElseThrow().structure();
		helper.assertTrue(BlueprintNames.get(id).isEmpty(), "an unnamed scan kept the name " + BlueprintNames.get(id));
		String name = Blueprints.displayName(id).getString();
		helper.assertTrue(name.equals(Blueprints.prettify(id)) && name.endsWith("s Build"), "an unnamed scan is called " + name);
		helper.succeed();
	}

	/**
	 * An uploaded file "Zoë's Hut.litematic" is called "Zoë's Hut"; a plain "zoe_hut.litematic" is still called "Zoe Hut"
	 * (its underscores read as spaces, as before), with no name kept.
	 */
	//$ gametest_batch AREA '"blueprintNamesUpload"'
	@GameTest(template = AREA, batch = "blueprintNamesUpload")
	public void anUploadedFileKeepsItsApostropheAndAccent(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		String stamp = Long.toString(helper.getLevel().getGameTime());
		try {
			ResourceLocation fancy = BlueprintImporter.importBytes(server, "uploads/test_names", "Zoë's Hut " + stamp + ".litematic",
				ImportGameTests.bytes("hut.litematic")).id();
			ResourceLocation plain = BlueprintImporter.importBytes(server, "uploads/test_names", "zoe_hut_" + stamp + ".litematic",
				ImportGameTests.bytes("hut.litematic")).id();
			helper.assertTrue(Blueprints.displayName(fancy).getString().equals("Zoë's Hut " + stamp), "the upload is called "
				+ Blueprints.displayName(fancy).getString() + " (" + fancy + ")");
			helper.assertTrue(BlueprintNames.get(plain).isEmpty(), "a plain file name kept a name: " + BlueprintNames.get(plain));
			helper.assertTrue(Blueprints.displayName(plain).getString().equals("Zoe Hut " + stamp), "the plain upload is called "
				+ Blueprints.displayName(plain).getString());
		} catch (Exception e) {
			throw new AssertionError("import failed: " + e, e);
		}
		helper.succeed();
	}
}
