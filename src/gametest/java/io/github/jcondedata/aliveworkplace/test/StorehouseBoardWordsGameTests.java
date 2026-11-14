package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.Requests;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * B1098: the Storehouse board's messages read as sentences for the Hall's stand-alone request names ("A pickaxe",
 * "Seeds"): "Gave a pickaxe to the Miner's chests", never "Gave 1 A pickaxe"; "You don't have a pickaxe to give", never
 * "You have no A pickaxe to give". Item names read as before ("Gave 25 Cobblestone").
 */
public class StorehouseBoardWordsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	private static Villager worker(GameTestHelper helper, VillagerProfession profession) {
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(2));
		villager.setNoAi(true);
		return villager;
	}

	private static Requests.Request request(GameTestHelper helper, Villager worker, ItemStack icon, int count, Component what) {
		return new Requests.Request(worker, helper.absolutePos(new BlockPos(1, 2, 1)), icon, count, what, stack -> stack.is(icon.getItem()));
	}

	private static void says(GameTestHelper helper, Component message, String expected) {
		String text = message.getString();
		helper.assertTrue(text.equals(expected), "'" + text + "' should read '" + expected + "'");
	}

	/** A Miner's pickaxe request, a Farmer's seeds and a builder's cobblestone, as the board's messages word them. */
	//$ gametest_batch AREA '"storehouseBoardWords"'
	@GameTest(template = AREA, batch = "storehouseBoardWords")
	public void boardMessagesReadAsSentences(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager miner = worker(helper, ModVillagers.MINER);
		Requests.Request pickaxe = request(helper, miner, new ItemStack(Items.STONE_PICKAXE), 1, Component.translatable("request.aliveworkplace.pickaxe"));
		says(helper, StorehouseBoard.gaveMessage(pickaxe, 1), "Gave a pickaxe to the Miner's chests");
		says(helper, StorehouseBoard.noneMessage(pickaxe), "You don't have a pickaxe to give (or their chests are full)");

		Villager farmer = worker(helper, VillagerProfession.FARMER);
		Requests.Request seeds = request(helper, farmer, new ItemStack(Items.WHEAT_SEEDS), 12, Component.translatable("request.aliveworkplace.seeds"));
		says(helper, StorehouseBoard.gaveMessage(seeds, 12), "Gave 12 seeds to the Farmer's chests");
		says(helper, StorehouseBoard.noneMessage(seeds), "You don't have seeds to give (or their chests are full)");

		Requests.Request cobble = request(helper, miner, new ItemStack(Items.COBBLESTONE), 25, Items.COBBLESTONE.getDefaultInstance().getHoverName());
		says(helper, StorehouseBoard.gaveMessage(cobble, 25), "Gave 25 Cobblestone to the Miner's chests");
		says(helper, StorehouseBoard.noneMessage(cobble), "You have no Cobblestone to give (or their chests are full)");
		helper.succeed();
	}

	/** A count before an article ("Gave 1 a"), "no" before one ("You have no a"), a count of one ("Gave 1 "), a capital mid-sentence. */
	private static final java.util.regex.Pattern WRONG = java.util.regex.Pattern.compile(
		"\\b\\d+ (a|an|A|An) |\\bno (a|an|A|An) |\\bGave 1 |.\\b(A|An|Seeds)\\b|\\b1 seeds\\b|\\bGave \\d+ (a|an) ");

	/**
	 * B1108, B1109: every request name a job posts in place of an item's name (each in {@link Requests#NAMES}) reads as a
	 * sentence on the board, for one, a few and a stack handed over and for none, and has its own words (not a raw key).
	 * Every plain request name in en_us.json ("request.aliveworkplace.<name>" with no placeholder) is in that list, so a
	 * new job's name can't fall back to "Gave 16 <name>".
	 */
	//$ gametest_batch AREA '"storehouseBoardEveryName"'
	@GameTest(template = AREA, batch = "storehouseBoardEveryName")
	public void everyRequestNameAJobPostsReadsAsASentence(GameTestHelper helper) {
		Leftovers.clear(helper);
		java.util.List<String> wrong = new java.util.ArrayList<>();
		try (java.io.InputStream in = Requests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			helper.assertTrue(in != null, "en_us.json isn't on the classpath");
			com.google.gson.JsonObject lang = com.google.gson.JsonParser.parseReader(
				new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
			for (String key : lang.keySet()) {
				if (key.matches("request\\.aliveworkplace\\.[a-z_]+") && !lang.get(key).getAsString().contains("%")
					&& !Requests.NAMES.contains(key)) {
					wrong.add(key + " is a request name the board has no sentences for");
				}
			}
		} catch (java.io.IOException e) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("en_us.json: " + e);
		}
		Villager worker = worker(helper, VillagerProfession.FARMER);
		for (String key : Requests.NAMES) {
			Requests.Request r = request(helper, worker, new ItemStack(Items.WHEAT_SEEDS), 16, Component.translatable(key));
			java.util.List<String> sentences = new java.util.ArrayList<>();
			for (int given : new int[] {1, 2, 16}) {
				sentences.add(StorehouseBoard.gaveMessage(r, given).getString());
			}
			sentences.add(StorehouseBoard.noneMessage(r).getString());
			for (String s : sentences) {
				if (s.contains("aliveworkplace") || WRONG.matcher(s).find()) {
					wrong.add(key + ": '" + s + "'");
				}
			}
			if (!sentences.get(0).endsWith(" to the Farmer's chests") || !sentences.get(1).startsWith("Gave 2 ")
				|| !sentences.get(3).startsWith("You don't have ")) {
				wrong.add(key + " reads " + sentences);
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}
}
