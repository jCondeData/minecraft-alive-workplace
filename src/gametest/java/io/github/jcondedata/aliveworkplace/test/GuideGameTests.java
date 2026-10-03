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
	// --- Tester (ROADMAP 26.2a, Check): the gift through the real join trigger, full inventories, death and rejoin,
	// the recipe against every other recipe, and the book used on a server.

	/** A new player standing in this test's area, in survival, who hasn't had the book yet. */
	private static ServerPlayer freshPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		net.minecraft.core.BlockPos at = helper.absolutePos(new net.minecraft.core.BlockPos(0, 1, 0));
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		var advancement = helper.getLevel().getServer().getAdvancements().get(AliveWorkplace.id("guide_book"));
		helper.assertTrue(advancement != null, "no aliveworkplace:guide_book advancement");
		player.getAdvancements().revoke(advancement, "joined");
		player.getInventory().clearContent();
		return player;
	}

	private static List<net.minecraft.world.entity.item.ItemEntity> booksOnTheGround(GameTestHelper helper, ServerPlayer player) {
		return helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
			player.getBoundingBox().inflate(6), e -> e.getItem().is(ModItems.GUIDE_BOOK));
	}

	/** The player's own tick on the server (what runs every tick for everyone online) hands the book over, once. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"guideGiftTick"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "guideGiftTick")
	public void theJoinTickGivesTheBookOnceEvenAfterARejoin(GameTestHelper helper) {
		ServerPlayer player = freshPlayer(helper);
		helper.runAfterDelay(5, () -> {
			int first = player.getInventory().countItem(ModItems.GUIDE_BOOK);
			// Lose the book, then save and load the player's advancements as a rejoin does.
			player.getInventory().clearContent();
			player.getAdvancements().save();
			player.getAdvancements().reload(helper.getLevel().getServer().getAdvancements());
			helper.runAfterDelay(5, () -> {
				int afterRejoin = player.getInventory().countItem(ModItems.GUIDE_BOOK);
				helper.getLevel().getServer().getPlayerList().remove(player);
				helper.assertTrue(first == 1, "five ticks after joining the player has " + first + " Guide Books");
				helper.assertTrue(afterRejoin == 0, "after a rejoin the player was given the book again (" + afterRejoin + ")");
				org.slf4j.LoggerFactory.getLogger("aliveworkplace").info("[test] guide gift: 1 book on the first ticks, none after a rejoin");
				helper.succeed();
			});
		});
	}

	/** A player whose inventory is full still gets the book: it lands at their feet, ready to pick up. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"guideGiftFull"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "guideGiftFull")
	public void aPlayerWithAFullInventoryFindsTheBookAtTheirFeet(GameTestHelper helper) {
		ServerPlayer player = freshPlayer(helper);
		for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
			player.getInventory().items.set(slot, new ItemStack(Items.COBBLESTONE, 64));
		}
		helper.runAfterDelay(5, () -> {
			int held = player.getInventory().countItem(ModItems.GUIDE_BOOK);
			var dropped = booksOnTheGround(helper, player);
			boolean pickable = dropped.size() == 1 && !dropped.get(0).hasPickUpDelay();
			dropped.forEach(e -> e.discard());
			helper.getLevel().getServer().getPlayerList().remove(player);
			helper.assertTrue(held == 0, "a full inventory holds " + held + " Guide Books");
			helper.assertTrue(dropped.size() == 1, dropped.size() + " Guide Books on the ground by a player with a full inventory");
			helper.assertTrue(pickable, "the dropped Guide Book can't be picked up straight away");
			org.slf4j.LoggerFactory.getLogger("aliveworkplace").info("[test] guide gift: a full inventory gets the book at their feet");
			helper.succeed();
		});
	}

	/** Dying (and respawning) doesn't hand out a second book; the one dropped on death is the only one. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"guideGiftDeath"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "guideGiftDeath")
	public void aPlayerWhoDiesIsNotGivenASecondBook(GameTestHelper helper) {
		ServerPlayer player = freshPlayer(helper);
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(player.getInventory().countItem(ModItems.GUIDE_BOOK) == 1, "no book before dying");
			net.minecraft.world.phys.Vec3 diedAt = player.position();
			player.kill();
			ServerPlayer respawned = helper.getLevel().getServer().getPlayerList().respawn(player, false,
				net.minecraft.world.entity.Entity.RemovalReason.KILLED);
			helper.runAfterDelay(5, () -> {
				int after = respawned.getInventory().countItem(ModItems.GUIDE_BOOK);
				var dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					new net.minecraft.world.phys.AABB(diedAt, diedAt).inflate(6), e -> e.getItem().is(ModItems.GUIDE_BOOK));
				dropped.forEach(e -> e.discard());
				helper.getLevel().getServer().getPlayerList().remove(respawned);
				helper.assertTrue(after == 0, "after respawning the player was given " + after + " more Guide Books");
				helper.assertTrue(dropped.size() == 1, dropped.size() + " Guide Books where the player died (expected the 1 they had)");
				org.slf4j.LoggerFactory.getLogger("aliveworkplace").info("[test] guide gift: no second book after death");
				helper.succeed();
			});
		});
	}

	/** Book + wheat makes only the Guide Book, in either order and anywhere in the grid: no other recipe matches it. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBookAndWheatMatchNoOtherRecipe(GameTestHelper helper) {
		var level = helper.getLevel();
		ItemStack book = new ItemStack(Items.BOOK);
		ItemStack wheat = new ItemStack(Items.WHEAT);
		List<CraftingInput> inputs = List.of(
			CraftingInput.of(2, 1, List.of(book, wheat)),
			CraftingInput.of(2, 1, List.of(wheat, book)),
			CraftingInput.of(1, 2, List.of(wheat, book)),
			CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
				ItemStack.EMPTY, ItemStack.EMPTY, wheat, ItemStack.EMPTY, book, ItemStack.EMPTY)));
		for (CraftingInput input : inputs) {
			var all = level.getRecipeManager().getRecipesFor(RecipeType.CRAFTING, input, level);
			helper.assertTrue(all.size() == 1, "book + wheat matches " + all.size() + " recipes: "
				+ all.stream().map(r -> r.id().toString()).toList());
			ItemStack made = all.get(0).value().assemble(input, level.registryAccess());
			helper.assertTrue(made.is(ModItems.GUIDE_BOOK) && made.getCount() == 1, "book + wheat makes " + made);
		}
		// The ingredients alone (or a book with something else) make no Guide Book.
		for (CraftingInput input : List.of(CraftingInput.of(1, 1, List.of(book)), CraftingInput.of(1, 1, List.of(wheat)),
			CraftingInput.of(2, 1, List.of(book, new ItemStack(Items.WHEAT_SEEDS))),
			CraftingInput.of(3, 1, List.of(book, wheat, wheat)))) {
			ItemStack made = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
				.map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
			helper.assertFalse(made.is(ModItems.GUIDE_BOOK), "a Guide Book from " + input.items());
		}
		helper.succeed();
	}

	/** On the server, using the book does nothing harmful: no screen classes, the book stays in the hand. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"guideUse"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "guideUse")
	public void usingTheBookOnTheServerKeepsIt(GameTestHelper helper) {
		ServerPlayer player = freshPlayer(helper);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.GUIDE_BOOK));
		var result = player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(),
			net.minecraft.world.InteractionHand.MAIN_HAND);
		boolean kept = player.getMainHandItem().is(ModItems.GUIDE_BOOK) && player.getMainHandItem().getCount() == 1;
		int used = player.getStats().getValue(net.minecraft.stats.Stats.ITEM_USED.get(ModItems.GUIDE_BOOK));
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.assertTrue(result.consumesAction(), "using the book on the server: " + result);
		helper.assertTrue(kept, "using the book on the server used it up");
		helper.assertTrue(used == 1, "the book's use was counted " + used + " times");
		helper.succeed();
	}

	/**
	 * Bug (found by the 26.2a tester): README says "the recipes for our own blocks are in the recipe book", and the store
	 * page says so too. The mod ships no recipe-unlock advancements and never awards recipes, so a player's recipe book
	 * shows none of ours until they've crafted each one, which they can't find out how to do: a player holding a book
	 * and wheat (or paper and blue dye) has vanilla's writable book and bread in their recipe book, but not the Guide
	 * Book (or the Blank Blueprint).
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"recipeBook"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "recipeBook")
	public void ourRecipesAreInTheRecipeBookOnceThePlayerHasTheIngredients(GameTestHelper helper) {
		ServerPlayer player = freshPlayer(helper);
		player.getInventory().add(new ItemStack(Items.BOOK));
		player.getInventory().add(new ItemStack(Items.WHEAT));
		player.getInventory().add(new ItemStack(Items.PAPER, 8));
		player.getInventory().add(new ItemStack(Items.BLUE_DYE, 8));
		helper.runAfterDelay(20, () -> {
			var book = player.getRecipeBook();
			boolean vanilla = book.contains(net.minecraft.resources.ResourceLocation.withDefaultNamespace("writable_book"));
			List<String> missing = new ArrayList<>();
			for (String id : List.of("guide_book", "blank_blueprint")) {
				if (!book.contains(AliveWorkplace.id(id))) {
					missing.add(id);
				}
			}
			helper.getLevel().getServer().getPlayerList().remove(player);
			helper.assertTrue(vanilla, "setup: vanilla's writable book isn't in the recipe book either");
			helper.assertTrue(missing.isEmpty(), "not in the recipe book of a player holding their ingredients: " + missing);
			helper.succeed();
		});
	}
}
