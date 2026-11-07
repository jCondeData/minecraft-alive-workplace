package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.people.Luxuries;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.Porters;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-c-1107-0353), luxuries and the porter (ROADMAP 34.4, Porters.keeps): "a luxury maker keeps the makings, but
 * the luxuries go to the store, whoever made them". Every loaded luxury that stacks is kept by no job at all, with or
 * without a furnace near, even by a worker whose job keeps everything else in their chests (the Habitat Keeper keeps
 * all but empty bottles). Found by a mutation spot-check: Luxuries.isLuxury answering false for everything survived the
 * luxury tests, because the Florist in the porter test keeps nothing of a stranger's item anyway.
 */
public class QaLuxuryPortersLaneC1107GameTests implements FabricGameTest {

	/** No job keeps a luxury from the porter; the Habitat Keeper keeps an item that isn't one. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaLuxuryPorter1107"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaLuxuryPorter1107")
	public void qaNoJobKeepsALuxuryFromThePorter(GameTestHelper helper) {
		ItemStack trinket = new ItemStack(Items.DISC_FRAGMENT_5); // the test pack's Test Trinket
		ItemStack fine = new ItemStack(Items.PHANTOM_MEMBRANE); // in the Test Fine Trinket's tag
		helper.assertTrue(Luxuries.isLuxury(trinket) && Luxuries.isLuxury(fine), "the test pack's luxuries aren't loaded");
		helper.assertFalse(Luxuries.isLuxury(new ItemStack(Items.ECHO_SHARD)) || Luxuries.isLuxury(ItemStack.EMPTY), "an echo shard or nothing called a luxury");
		helper.assertTrue(Porters.keeps(ModVillagers.HABITAT_KEEPER, new ItemStack(Items.ECHO_SHARD), false) == Porters.ALL,
			"setup: a Habitat Keeper keeps what isn't a luxury (ALL), got " + Porters.keeps(ModVillagers.HABITAT_KEEPER, new ItemStack(Items.ECHO_SHARD), false));
		List<ItemStack> luxuries = new ArrayList<>(List.of(trinket, fine));
		for (Luxuries.Luxury l : Luxuries.all().values()) {
			l.item().map(BuiltInRegistries.ITEM::get).map(ItemStack::new).filter(s -> s.getMaxStackSize() > 1).ifPresent(luxuries::add);
		}
		List<String> kept = new ArrayList<>();
		for (VillagerProfession job : BuiltInRegistries.VILLAGER_PROFESSION) {
			for (ItemStack luxury : luxuries) {
				for (boolean furnace : new boolean[] {false, true}) {
					int keeps = Porters.keeps(job, luxury, furnace);
					if (keeps != 0) {
						kept.add(BuiltInRegistries.VILLAGER_PROFESSION.getKey(job) + " keeps " + keeps + " of " + BuiltInRegistries.ITEM.getKey(luxury.getItem())
							+ (furnace ? " (furnace near)" : ""));
					}
				}
			}
		}
		helper.assertTrue(kept.isEmpty(), "a luxury kept from the porter: " + kept);
		helper.assertTrue(luxuries.size() >= 3, "too few stacking luxuries checked: " + luxuries.size());
		helper.succeed();
	}

	/**
	 * The luxury files at their edges (Luxuries' contract): {@code "enabled": false} and a file whose mod is missing are
	 * left out quietly; {@code every_days} 1 loads and 0 skips the file; an unknown switch name skips it; a luxury behind
	 * the cuisineDishes switch is there only while the switch is on. The real files are loaded back at the end.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaLuxuryFiles1107"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaLuxuryFiles1107")
	public void qaSwitchedOffAndMissingModLuxuriesAreLeftOut(GameTestHelper helper) {
		net.minecraft.server.MinecraftServer server = helper.getLevel().getServer();
		// The game's own data reload loaded the test pack's luxuries (checked first: the cleanup below loads them itself)
		helper.assertTrue(Luxuries.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_trinket")) != null,
			"setup: the data pack's luxuries weren't loaded by the server: " + Luxuries.all().keySet());
		boolean cuisine = io.github.jcondedata.aliveworkplace.craft.CuisineDishes.ENABLED;
		Leftovers.after(helper, () -> {
			io.github.jcondedata.aliveworkplace.craft.CuisineDishes.ENABLED = cuisine;
			Luxuries.load(Luxuries.files(server.getResourceManager()));
		});
		java.util.Map<net.minecraft.resources.ResourceLocation, com.google.gson.JsonElement> files = new java.util.HashMap<>();
		files.put(id("off"), json("{\"item\": \"minecraft:cake\", \"every_days\": 2, \"enabled\": false}"));
		files.put(id("no_mod"), json("{\"fabric:load_conditions\": [{\"condition\": \"fabric:all_mods_loaded\", \"values\": [\"qa_no_such_mod\"]}],"
			+ " \"item\": \"minecraft:cookie\", \"every_days\": 2}"));
		files.put(id("daily"), json("{\"item\": \"minecraft:pumpkin_pie\", \"every_days\": 1}"));
		files.put(id("never"), json("{\"item\": \"minecraft:honey_bottle\", \"every_days\": 0}"));
		files.put(id("odd_switch"), json("{\"item\": \"minecraft:glow_berries\", \"every_days\": 2, \"switch\": \"qaNoSuchSwitch\"}"));
		files.put(id("cuisine"), json("{\"item\": \"minecraft:rabbit_stew\", \"every_days\": 3, \"switch\": \"cuisineDishes\"}"));
		io.github.jcondedata.aliveworkplace.craft.CuisineDishes.ENABLED = true;
		Luxuries.load(files);
		helper.assertTrue(Luxuries.get(id("off")) == null && !Luxuries.isLuxury(new ItemStack(Items.CAKE)), "an \"enabled\": false luxury was loaded");
		helper.assertTrue(Luxuries.get(id("no_mod")) == null && !Luxuries.isLuxury(new ItemStack(Items.COOKIE)), "a luxury whose mod is missing was loaded");
		helper.assertTrue(Luxuries.get(id("daily")) != null && Luxuries.get(id("daily")).everyDays() == 1, "every_days 1 should load: " + Luxuries.get(id("daily")));
		helper.assertTrue(Luxuries.get(id("never")) == null, "every_days 0 should skip the file");
		helper.assertTrue(Luxuries.get(id("odd_switch")) == null, "an unknown switch name should skip the file");
		helper.assertTrue(Luxuries.get(id("cuisine")) != null && Luxuries.isLuxury(new ItemStack(Items.RABBIT_STEW)), "the switch on: the cuisine luxury is wanted");
		helper.assertTrue(Luxuries.all().keySet().equals(java.util.Set.of(id("daily"), id("cuisine"))), "loaded: " + Luxuries.all().keySet());
		io.github.jcondedata.aliveworkplace.craft.CuisineDishes.ENABLED = false;
		helper.assertTrue(Luxuries.get(id("cuisine")) == null && !Luxuries.isLuxury(new ItemStack(Items.RABBIT_STEW))
			&& Luxuries.all().keySet().equals(java.util.Set.of(id("daily"))), "the switch off: the cuisine luxury is still about: " + Luxuries.all().keySet());
		helper.succeed();
	}

	private static net.minecraft.resources.ResourceLocation id(String path) {
		return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_" + path);
	}

	private static com.google.gson.JsonElement json(String text) {
		return com.google.gson.JsonParser.parseString(text);
	}
}
