package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;

/**
 * The Guide Book (the owner, ROADMAP 26.2a): every player is given one the first time they join, a lost one is crafted
 * from a book and wheat, and every page in the language file has its picture.
 */
public class GuideGameTests implements FabricGameTest {
	/** The first-join advancement hands over one Guide Book, and only the first time. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aNewPlayerIsGivenTheGuideBookOnce(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		var advancement = helper.getLevel().getServer().getAdvancements().get(AliveWorkplace.id("guide_book"));
		helper.assertTrue(advancement != null, "no aliveworkplace:guide_book advancement");
		helper.assertFalse(advancement.value().display().isPresent(), "the gift shows a toast (it should be hidden)");
		player.getInventory().clearContent();
		player.getAdvancements().revoke(advancement, "joined");
		player.getAdvancements().award(advancement, "joined");
		helper.assertTrue(player.getInventory().countItem(ModItems.GUIDE_BOOK) == 1,
			"a new player has " + player.getInventory().countItem(ModItems.GUIDE_BOOK) + " Guide Books");
		player.getAdvancements().award(advancement, "joined");
		helper.assertTrue(player.getInventory().countItem(ModItems.GUIDE_BOOK) == 1, "the book was given twice");
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	/** A book and wheat make a Guide Book. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theGuideBookIsCraftedFromABookAndWheat(GameTestHelper helper) {
		var level = helper.getLevel();
		CraftingInput input = CraftingInput.of(2, 1, List.of(new ItemStack(Items.BOOK), new ItemStack(Items.WHEAT)));
		ItemStack made = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
			.map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
		helper.assertTrue(made.is(ModItems.GUIDE_BOOK), "a book and wheat make " + made);
		helper.succeed();
	}

	/**
	 * Every page the language file names ({@code guide.aliveworkplace.page.<id>.title}) has its words and its picture,
	 * a 384 x 216 screenshot in {@code textures/gui/guide/<id>.png}. (The 'guide' screenshot scene opens each page in the
	 * real client and checks that its words fit.)
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyGuidePageHasItsPictureAndWords(GameTestHelper helper) {
		ModContainer mod = FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow();
		JsonObject lang;
		try (InputStream in = Files.newInputStream(mod.findPath("assets/aliveworkplace/lang/en_us.json").orElseThrow())) {
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("can't read en_us.json: " + e);
		}
		List<String> pages = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		for (String key : lang.keySet()) {
			if (!key.startsWith("guide.aliveworkplace.page.") || !key.endsWith(".title")) {
				continue;
			}
			String id = key.substring("guide.aliveworkplace.page.".length(), key.length() - ".title".length());
			pages.add(id);
			if (!lang.has("guide.aliveworkplace.page." + id + ".text")) {
				problems.add(id + " has no text");
			}
			Optional<Path> png = mod.findPath("assets/aliveworkplace/textures/gui/guide/" + id + ".png");
			if (png.isEmpty()) {
				problems.add(id + " has no picture");
				continue;
			}
			try (InputStream in = Files.newInputStream(png.get())) {
				BufferedImage image = ImageIO.read(in);
				if (image == null || image.getWidth() != 384 || image.getHeight() != 216) {
					problems.add(id + "'s picture isn't 384 x 216");
				}
			} catch (IOException e) {
				problems.add(id + "'s picture can't be read");
			}
		}
		helper.assertTrue(pages.size() >= 30, "only " + pages.size() + " guide pages");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		org.slf4j.LoggerFactory.getLogger("aliveworkplace").info("[test] guide: " + pages.size() + " pages, each with its picture and words");
		helper.succeed();
	}
}
