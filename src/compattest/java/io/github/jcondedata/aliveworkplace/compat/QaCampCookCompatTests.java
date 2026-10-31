package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.camp.CampCooks;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (ROADMAP 28.8), written from the spec: a Poké Snack short of its honey isn't cooked, a cook saved and reloaded with
 * the lid shut finishes the dish once, the shipped menu's "always / asked / order" lines, the campCooks key, and her
 * trades by level.
 */
public class QaCampCookCompatTests implements FabricGameTest {
	static final String AREA = CompatGameTests.AREA;
	static final BlockPos POT = QaCampHabitatCompatTests.STATION;
	static final BlockPos CHEST = QaCampHabitatCompatTests.CHEST;

	static ResourceLocation c(String path) {
		return ResourceLocation.fromNamespaceAndPath("cobblemon", path);
	}

	static Item item(String path) {
		return QaCampHabitatCompatTests.item(path);
	}

	static ItemStack stack(String path, int count) {
		return new ItemStack(item(path), count);
	}

	/**
	 * Missing makings: the Poké Snack's makings without the honey. She cooks no Poké Snack, and nothing is lost: the milk,
	 * Vivichoke and Hearty Grains she didn't cook with are still in the chest, her bag or the pot.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qa_cc_no_honey"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qa_cc_no_honey")
	public void withoutHoneyNoPokeSnackIsCooked(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager cook = QaCampHabitatCompatTests.cook(helper, stack("moomoo_milk", 3), stack("vivichoke", 1), stack("hearty_grains", 3));
		int[] most = {0};
		helper.onEachTick(() -> most[0] = Math.max(most[0], QaCampHabitatCompatTests.snacks(helper, cook)));
		helper.runAfterDelay(1200, () -> {
			helper.assertTrue(most[0] == 0, "a Poké Snack was cooked with no honey; she says " + QaCampHabitatCompatTests.said(helper, cook));
			Container chest = helper.getBlockEntity(CHEST);
			Container pot = (Container) helper.getBlockEntity(POT);
			helper.assertTrue(count(chest, pot, cook, item("poke_snack")) == 0, "a Poké Snack appeared somewhere");
			helper.assertTrue(ModAttachments.DISHES_COOKED.getOrElse(cook, 0) > 0 || count(chest, pot, cook, item("vivichoke")) == 1
				&& count(chest, pot, cook, item("hearty_grains")) == 3, "she cooked nothing yet makings went missing: vivichoke "
				+ count(chest, pot, cook, item("vivichoke")) + ", grains " + count(chest, pot, cook, item("hearty_grains")));
			helper.succeed();
		});
	}

	static int count(Container chest, Container pot, Villager cook, Item it) {
		int n = chest.countItem(it) + ModAttachments.BUILDER_BAG.getOrCreate(cook).stacks().stream().filter(s -> s.is(it)).mapToInt(ItemStack::getCount).sum();
		for (int i = 0; i < pot.getContainerSize(); i++) {
			if (pot.getItem(i).is(it)) {
				n += pot.getItem(i).getCount();
			}
		}
		return n;
	}

	/**
	 * Save and reload mid-job: once the lid shuts, the cook is saved and reloaded (the old one gone, as when her chunk
	 * unloads); the reloaded cook still finishes the Poké Snack: it reaches the chest, the makings are used once, and
	 * the dish is counted on her.
	 */
	//$ gametest_ticks_batch AREA '2800' '"qa_cc_reload"'
	@GameTest(template = AREA, timeoutTicks = 2800, batch = "qa_cc_reload")
	public void aCookReloadedWithTheLidShutFinishesTheSnack(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager first = QaCampHabitatCompatTests.cook(helper, stack("moomoo_milk", 3), new ItemStack(Items.HONEY_BOTTLE, 2), stack("vivichoke", 1),
			stack("hearty_grains", 3));
		CampCooks.Pot pot = CampCooks.pot();
		Villager[] cook = {first};
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			if (!reloaded[0] && pot.lidShut(helper.getBlockState(POT))) {
				reloaded[0] = true;
				CompoundTag tag = new CompoundTag();
				first.saveWithoutId(tag);
				first.discard();
				Villager copy = EntityType.VILLAGER.create(helper.getLevel());
				copy.load(tag);
				copy.setUUID(java.util.UUID.randomUUID());
				helper.assertTrue(helper.getLevel().addFreshEntity(copy), "the reloaded cook wasn't added");
				cook[0] = copy;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "the lid never shut; she says " + QaCampHabitatCompatTests.said(helper, first));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(item("poke_snack")) >= 1, "no Poké Snack in the chest after the reload; she says "
				+ QaCampHabitatCompatTests.said(helper, cook[0]) + ", pot " + ((Container) helper.getBlockEntity(POT)).getItem(0));
			helper.assertTrue(chest.countItem(item("hearty_grains")) == 0 && chest.countItem(item("vivichoke")) == 0
				&& count(chest, (Container) helper.getBlockEntity(POT), cook[0], item("hearty_grains")) == 0, "makings left over (or put back and cooked twice?)");
			helper.assertTrue(ModAttachments.DISHES_COOKED.getOrElse(cook[0], 0) >= 1, "the reloaded cook didn't count the dish");
			helper.assertTrue(CampCooks.isCook(cook[0]), "the reloaded villager isn't a Camp Cook: " + cook[0].getVillagerData().getProfession());
		});
	}

	/**
	 * The shipped menu as the spec lists it: the "always" dishes up to 16; the asked ones (Poké Snacks for the Habitat
	 * Keeper, Poké Bait for the fishermen); the order-only ones never cooked unasked.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theShippedMenuMatchesTheSpec(GameTestHelper helper) {
		List<CampCooks.Dish> menu = CampCooks.menu();
		Map<ResourceLocation, List<CampCooks.Dish>> byItem = menu.stream().collect(Collectors.groupingBy(CampCooks.Dish::item));
		List<String> always = new ArrayList<>(List.of("poke_snack", "poke_bait", "exp_candy_xs", "exp_candy_s", "exp_candy_m", "ponigiri",
			"leek_and_potato_stew", "smoked_tail_curry", "open_faced_sandwich", "vivichoke_dip", "sinister_tea"));
		for (String colour : List.of("red", "yellow", "green", "blue", "pink", "black", "white")) {
			always.add("aprijuice_" + colour);
		}
		List<String> problems = new ArrayList<>();
		for (String dish : always) {
			List<CampCooks.Dish> lines = byItem.getOrDefault(c(dish), List.of());
			if (lines.stream().noneMatch(d -> d.when() == CampCooks.When.ALWAYS && d.keep() == 16)) {
				problems.add(dish + " isn't kept always, up to 16: " + lines);
			}
		}
		List<String> order = new ArrayList<>(List.of("big_malasada", "casteliacone", "jubilife_muffin", "lava_cookie", "lumiose_galette", "old_gateau",
			"pewter_crunchies", "rage_candy_bar", "whipped_dream", "potion", "super_potion", "hyper_potion", "antidote", "awakening", "burn_heal",
			"ice_heal", "paralyze_heal", "full_heal", "health_candy", "mighty_candy", "tough_candy", "smart_candy", "courage_candy", "quick_candy"));
		for (String sweet : List.of("berry", "clover", "flower", "love", "ribbon", "star", "strawberry")) {
			order.add(sweet + "_sweet");
		}
		for (String mochi : List.of("health", "muscle", "resist", "genius", "clever", "swift")) {
			order.add(mochi + "_mochi");
		}
		for (String dish : order) {
			List<CampCooks.Dish> lines = byItem.getOrDefault(c(dish), List.of());
			if (lines.isEmpty() || lines.stream().anyMatch(d -> d.when() != CampCooks.When.ORDER)) {
				problems.add(dish + " isn't on stock orders only: " + lines);
			}
		}
		Set<ResourceLocation> snackAskers = byItem.getOrDefault(c("poke_snack"), List.of()).stream().filter(d -> d.when() == CampCooks.When.ASKED)
			.flatMap(d -> d.askedBy().stream()).collect(Collectors.toSet());
		if (!snackAskers.contains(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "habitat_keeper"))) {
			problems.add("Poké Snacks aren't asked by the Habitat Keeper: " + snackAskers);
		}
		Set<ResourceLocation> baitAskers = byItem.getOrDefault(c("poke_bait"), List.of()).stream().filter(d -> d.when() == CampCooks.When.ASKED)
			.flatMap(d -> d.askedBy().stream()).collect(Collectors.toSet());
		if (!baitAskers.contains(ResourceLocation.withDefaultNamespace("fisherman"))) {
			problems.add("Poké Bait isn't asked by the fishermen: " + baitAskers);
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** The campCooks key as a server owner writes it: false switches her off (Hearty Grains pick no job), and it defaults on. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qa_cc_config"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa_cc_config")
	public void theCampCooksKeySwitchesHerOff(GameTestHelper helper) {
		PartnerShowsCompatTests.after(helper, () -> new WorkplaceConfig().apply());
		try {
			helper.assertTrue(new WorkplaceConfig().campCooks, "campCooks doesn't default to on");
			WorkplaceConfig.parse("{\"campCooks\": false}").apply();
			helper.assertTrue(!CampCooks.ENABLED && !CampCooks.isGrains(stack("hearty_grains", 1)), "campCooks false didn't switch her off");
			new WorkplaceConfig().apply();
			helper.assertTrue(CampCooks.ENABLED && CampCooks.isGrains(stack("hearty_grains", 1)), "back on, Hearty Grains pick no job");
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/**
	 * Her trades by level: Poké Bait and Poké Snacks (Novice), Aprijuice (Apprentice), Exp. Candy S and M (Journeyman),
	 * Lumiose Galette and Big Malasada (Expert), Exp. Candy L (Master).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void herTradesByLevelAreTheSpecs(GameTestHelper helper) {
		var byLevel = VillagerTrades.TRADES.get(ModVillagers.CAMP_COOK);
		helper.assertTrue(byLevel != null, "the Camp Cook has no trades");
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		Set<Item> novice = QaHabitatKeeperCompatTests.sold(v, byLevel.get(1));
		helper.assertTrue(novice.contains(item("poke_bait")) && novice.contains(item("poke_snack")), "Novice sells " + novice);
		Set<Item> apprentice = QaHabitatKeeperCompatTests.sold(v, byLevel.get(2));
		helper.assertTrue(apprentice.stream().anyMatch(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).getPath().startsWith("aprijuice_")),
			"Apprentice sells " + apprentice);
		Set<Item> journeyman = QaHabitatKeeperCompatTests.sold(v, byLevel.get(3));
		helper.assertTrue(journeyman.contains(item("exp_candy_s")) && journeyman.contains(item("exp_candy_m")), "Journeyman sells " + journeyman);
		Set<Item> expert = QaHabitatKeeperCompatTests.sold(v, byLevel.get(4));
		helper.assertTrue(expert.contains(item("lumiose_galette")) && expert.contains(item("big_malasada")), "Expert sells " + expert);
		Set<Item> master = QaHabitatKeeperCompatTests.sold(v, byLevel.get(5));
		helper.assertTrue(master.contains(item("exp_candy_l")), "Master sells " + master);
		v.discard();
		helper.succeed();
	}
}
