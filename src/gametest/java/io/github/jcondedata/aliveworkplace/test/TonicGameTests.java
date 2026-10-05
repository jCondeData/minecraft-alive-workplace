package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Tonics;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Tonics (ROADMAP 30.15): a miner right-clicked with Miner's Brew drinks it and works 25% faster for 24000 ticks, also
 * after a save, and a second tonic starts the day again without stacking; a builder refuses it ("Dara has no use for
 * Miner's Brew.") and the player keeps it; the Cleric brews Miner's Brew from the chests by the stand and stops at 4;
 * the Chef cooks Builder's Tea after the menu; a tonic from the test data pack (glow berries for farmers, a tag among its
 * makings) works and a reload with {@code "enabled": false} takes ours away; with every bonus the cap holds; the
 * tooltips; nobody can craft them; {@code tonics} off.
 */
public class TonicGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation MINERS_BREW = AliveWorkplace.id("miners_brew");
	private static final ResourceLocation BUILDERS_TEA = AliveWorkplace.id("builders_tea");
	private static final ResourceLocation CORDIAL = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_cordial");
	private static final float EPSILON = 1e-5f;

	/**
	 * The player's own entry point (a plain right-click): the miner drinks, the bottle is used up, and works at 1/1.25 of
	 * the time for exactly 24000 ticks; the status line says so; a save keeps it; a second brew starts the day again
	 * without stacking; once it wears off the pace is back.
	 */
	//$ gametest_ticks_batch AREA '200' '"tonicMiner"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "tonicMiner")
	public void aMinerDrinksMinersBrewAndWorksFasterForADay(GameTestHelper helper) {
		Leftovers.clear(helper);
		quiet(helper);
		Villager miner = worker(helper, new BlockPos(4, 2, 4), ModVillagers.MINER, "Dara");
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			helper.assertTrue(part(miner) == 1f && !ModAttachments.TONIC.has(miner), "a tonic before drinking");
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MINERS_BREW, 2));
			InteractionResult result = rightClick(player, miner);
			helper.assertTrue(result == InteractionResult.SUCCESS, "the right-click: " + result);
			helper.assertTrue(player.getMainHandItem().getCount() == 1, "not used up: " + player.getMainHandItem());
			Tonics.Drunk drunk = ModAttachments.TONIC.get(miner);
			long now = level.getGameTime();
			helper.assertTrue(drunk != null && drunk.id().equals(MINERS_BREW.toString()) && drunk.until() == now + 24000, "drunk: " + drunk + " at " + now);
			helper.assertTrue(Math.abs(part(miner) - 1f / 1.25f) < EPSILON, "the tonic's part: " + Pace.of(miner));
			helper.assertTrue(Math.abs(Pace.factor(miner) - 1f / 1.25f) < EPSILON, "pace: " + Pace.of(miner));
			helper.assertTrue(Pace.of(miner).percent() == 25, "percent: " + Pace.of(miner).percent());
			String status = Pace.describe(miner).getString();
			helper.assertTrue(status.equals("25% faster (Miner's Brew, 20 min left)"), "status: " + status);
			ModAttachments.TONIC.set(miner, new Tonics.Drunk(MINERS_BREW.toString(), now + 23999));
			status = Pace.describe(miner).getString();
			helper.assertTrue(status.equals("25% faster (Miner's Brew, 19 min left)"), "status a tick later: " + status);
			ModAttachments.TONIC.set(miner, drunk);
			// The sentence the player reads.
			Tonics.Offer again = Tonics.offer(player, miner, player.getMainHandItem());
			helper.assertTrue(again.outcome() == Tonics.Outcome.DRUNK && again.message().getString().equals("Dara drinks the Miner's Brew: 25% faster for 20 minutes."),
				"said: " + again.message().getString());
			helper.assertTrue(player.getMainHandItem().isEmpty(), "the second bottle stays");
			helper.assertTrue(Math.abs(Pace.factor(miner) - 1f / 1.25f) < EPSILON, "two brews stacked: " + Pace.of(miner));

			// A save keeps it; a villager saved before 30.15 has none.
			CompoundTag saved = miner.saveWithoutId(new CompoundTag());
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(saved);
			helper.assertTrue(ModAttachments.TONIC.get(miner).equals(ModAttachments.TONIC.get(copy)), "after a reload: " + ModAttachments.TONIC.get(copy));
			helper.assertTrue(Math.abs(Tonics.pace(copy) - 1f / 1.25f) < EPSILON, "pace after a reload: " + Tonics.pace(copy));
			Villager old = EntityType.VILLAGER.create(level);
			helper.assertTrue(!ModAttachments.TONIC.has(old) && Tonics.pace(old) == 1f, "a villager from an old save has a tonic");
			copy.discard();
			old.discard();

			// Nearly worn off, another brew starts the day again (never 2 × 25%).
			ModAttachments.TONIC.set(miner, new Tonics.Drunk(MINERS_BREW.toString(), level.getGameTime() + 2));
			Tonics.drink(level, miner, Tonics.get(MINERS_BREW).orElseThrow());
			helper.assertTrue(ModAttachments.TONIC.get(miner).until() == level.getGameTime() + 24000, "not started again: " + ModAttachments.TONIC.get(miner));
			// Worn off: the usual pace.
			ModAttachments.TONIC.set(miner, new Tonics.Drunk(MINERS_BREW.toString(), level.getGameTime() + 3));
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(part(miner) == 1f && Pace.factor(miner) == 1f, "still faster after it wore off: " + Pace.of(miner));
				helper.assertTrue(Pace.describe(miner) == null, "status after: " + Pace.describe(miner));
				helper.succeed();
			});
		});
	}

	/**
	 * A builder refuses Miner's Brew with its sentence and the player keeps it; so does a child miner; sneak-right-click
	 * isn't drinking (it stays the job gesture); the builder takes Builder's Tea.
	 */
	//$ gametest_ticks_batch AREA '100' '"tonicRefused"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "tonicRefused")
	public void aBuilderRefusesMinersBrew(GameTestHelper helper) {
		Leftovers.clear(helper);
		quiet(helper);
		Villager builder = worker(helper, new BlockPos(4, 2, 4), ModVillagers.BUILDER, "Dara");
		Villager child = worker(helper, new BlockPos(8, 2, 4), ModVillagers.MINER, "Pip");
		child.setAge(-24000);
		Villager miner = worker(helper, new BlockPos(12, 2, 4), ModVillagers.MINER, "Ash");
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		helper.runAfterDelay(5, () -> {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MINERS_BREW));
			helper.assertTrue(rightClick(player, builder) == InteractionResult.SUCCESS, "no answer from the builder");
			helper.assertTrue(player.getMainHandItem().is(ModItems.MINERS_BREW) && player.getMainHandItem().getCount() == 1, "the brew was taken");
			helper.assertTrue(!ModAttachments.TONIC.has(builder) && part(builder) == 1f, "the builder drank it");
			Tonics.Offer refused = Tonics.offer(player, builder, player.getMainHandItem());
			helper.assertTrue(refused.outcome() == Tonics.Outcome.REFUSED && refused.message().getString().equals("Dara has no use for Miner's Brew."),
				"refused: " + refused.outcome() + " " + (refused.message() == null ? null : refused.message().getString()));
			helper.assertTrue(Tonics.offer(player, child, player.getMainHandItem()).outcome() == Tonics.Outcome.REFUSED && !ModAttachments.TONIC.has(child),
				"a child drank it");
			// Sneaking: not a drink.
			player.setShiftKeyDown(true);
			rightClick(player, miner);
			helper.assertTrue(!ModAttachments.TONIC.has(miner) && player.getMainHandItem().getCount() == 1, "drunk on a sneak-right-click");
			player.setShiftKeyDown(false);
			// The tea suits the builder.
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BUILDERS_TEA));
			rightClick(player, builder);
			helper.assertTrue(ModAttachments.TONIC.has(builder) && ModAttachments.TONIC.get(builder).id().equals(BUILDERS_TEA.toString())
				&& player.getMainHandItem().isEmpty(), "the builder refused the tea: " + ModAttachments.TONIC.get(builder));
			helper.assertTrue(Math.abs(part(builder) - 1f / 1.25f) < EPSILON, "tea: " + Pace.of(builder));
			helper.succeed();
		});
	}

	/**
	 * A Cleric with the makings in the chest by their stand (and nothing for the guards' potions) brews Miner's Brew
	 * into it one at a time, and stops at 4: the makings of 2 more stay.
	 */
	//$ gametest_ticks_batch AREA '4000' '"tonicAlchemist"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "tonicAlchemist")
	public void theClericBrewsMinersBrewAndStopsAtFour(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos stand = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(stand, Blocks.BREWING_STAND);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.GLASS_BOTTLE, 6));
		chest.setItem(1, new ItemStack(Items.GLOWSTONE_DUST, 6));
		chest.setItem(2, new ItemStack(Items.COAL, 6));
		chest.setItem(3, new ItemStack(Items.SUGAR, 6));
		Villager cleric = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), cleric, helper.absolutePos(stand), PoiTypes.CLERIC, VillagerProfession.CLERIC);
		long[] fourAt = {-1};
		helper.succeedWhen(() -> {
			int brews = chest.countItem(ModItems.MINERS_BREW);
			helper.assertTrue(brews <= 4, "brewed past the keep: " + brews);
			helper.assertTrue(brews == 4, "Miner's Brews in the chest: " + brews);
			long now = helper.getLevel().getGameTime();
			if (fourAt[0] < 0) {
				fourAt[0] = now;
			}
			helper.assertTrue(now - fourAt[0] >= 400, "watching that it stops");
			helper.assertTrue(chest.countItem(Items.GLASS_BOTTLE) == 2 && chest.countItem(Items.GLOWSTONE_DUST) == 2
				&& chest.countItem(Items.COAL) == 2 && chest.countItem(Items.SUGAR) == 2, "the makings left: " + contents(chest));
			helper.assertTrue(ModAttachments.POTIONS_BREWED.getOrElse(cleric, 0) == 4, "brewed " + ModAttachments.POTIONS_BREWED.getOrElse(cleric, 0));
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(cleric).isEmpty(), "left in the bag: " + ModAttachments.BUILDER_BAG.getOrCreate(cleric).stacks());
		});
	}

	/** A Chef with a glass bottle, sweet berries and sugar (no dish on the menu has its makings) cooks Builder's Tea, up to 4. */
	//$ gametest_ticks_batch AREA '3000' '"tonicChef"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "tonicChef")
	public void theChefCooksBuildersTeaAfterTheMenu(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos stove = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(stove, ModBlocks.KITCHEN_STOVE);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.GLASS_BOTTLE, 5));
		chest.setItem(1, new ItemStack(Items.SWEET_BERRIES, 10));
		chest.setItem(2, new ItemStack(Items.SUGAR, 5));
		chest.setItem(3, new ItemStack(Items.WHEAT, 3)); // a loaf on the menu first
		Villager chef = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), chef, helper.absolutePos(stove), ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
		long[] fourAt = {-1};
		helper.succeedWhen(() -> {
			int teas = chest.countItem(ModItems.BUILDERS_TEA);
			helper.assertTrue(teas <= 4, "cooked past the keep: " + teas);
			helper.assertTrue(chest.countItem(Items.BREAD) == 1, "the menu first: " + contents(chest));
			helper.assertTrue(teas == 4, "Builder's Teas in the chest: " + teas);
			long now = helper.getLevel().getGameTime();
			if (fourAt[0] < 0) {
				fourAt[0] = now;
			}
			helper.assertTrue(now - fourAt[0] >= 300, "watching that it stops");
			helper.assertTrue(chest.countItem(Items.GLASS_BOTTLE) == 1 && chest.countItem(Items.SWEET_BERRIES) == 2 && chest.countItem(Items.SUGAR) == 1,
				"the makings left: " + contents(chest));
		});
	}

	/**
	 * The test data pack's tonic (glow berries, for farmers, 40% for 6000 ticks, an amethyst shard and two small flowers
	 * of any kind): a farmer drinks it; a librarian isn't offered it at all (glow berries are just glow berries to them);
	 * a tag among the makings takes any of its items.
	 */
	//$ gametest_ticks_batch AREA '100' '"tonicPack"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "tonicPack")
	public void aTonicFromATestDataPackWorks(GameTestHelper helper) {
		Leftovers.clear(helper);
		quiet(helper);
		Villager farmer = worker(helper, new BlockPos(4, 2, 4), VillagerProfession.FARMER, "Bo");
		Villager librarian = worker(helper, new BlockPos(8, 2, 4), VillagerProfession.LIBRARIAN, "Cy");
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		helper.runAfterDelay(5, () -> {
			Tonics.Tonic cordial = Tonics.get(CORDIAL).orElse(null);
			helper.assertTrue(cordial != null && cordial.item() == Items.GLOW_BERRIES && cordial.maker() == Tonics.Maker.CHEF
				&& cordial.ticks() == 6000 && cordial.keep() == 2, "the pack's tonic: " + cordial);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLOW_BERRIES, 3));
			helper.assertTrue(rightClick(player, librarian) == InteractionResult.PASS && player.getMainHandItem().getCount() == 3,
				"glow berries taken from the player by a librarian");
			helper.assertTrue(Tonics.offer(player, librarian, player.getMainHandItem()).outcome() == Tonics.Outcome.NOT_A_TONIC, "refused instead of passed on");
			helper.assertTrue(rightClick(player, farmer) == InteractionResult.SUCCESS && player.getMainHandItem().getCount() == 2, "the farmer didn't drink");
			helper.assertTrue(Math.abs(part(farmer) - 1f / 1.4f) < EPSILON, "the pack's pace: " + Pace.of(farmer));
			String status = Pace.describe(farmer).getString();
			helper.assertTrue(status.equals("40% faster (Glow Berries, 5 min left)"), "status: " + status);
			// Its makings: a tag takes any of its items, even two different ones.
			Map<Item, Long> stock = new LinkedHashMap<>();
			stock.put(Items.AMETHYST_SHARD, 1L);
			stock.put(Items.POPPY, 1L);
			stock.put(Items.DANDELION, 1L);
			Map<Item, Integer> takes = Tonics.takes(cordial, 1, stock);
			helper.assertTrue(takes != null && takes.get(Items.AMETHYST_SHARD) == 1 && takes.get(Items.POPPY) == 1 && takes.get(Items.DANDELION) == 1,
				"takes: " + takes);
			stock.remove(Items.DANDELION);
			helper.assertTrue(Tonics.takes(cordial, 1, stock) == null, "made with one flower");
			stock.put(Items.OAK_PLANKS, 9L);
			helper.assertTrue(Tonics.takes(cordial, 1, stock) == null, "planks counted as a flower");
			helper.succeed();
		});
	}

	/** Reloading with a pack's {@code "enabled": false} at our path takes Miner's Brew away (the rest stay); a broken file is skipped. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aReloadReadsTheTonicFilesAgain(GameTestHelper helper) {
		var manager = helper.getLevel().getServer().getResourceManager();
		Map<ResourceLocation, Resource> real = manager.listResources(Tonics.FOLDER, p -> p.getPath().endsWith(".json"));
		ResourceLocation path = AliveWorkplace.id("tonics/miners_brew.json");
		Resource ours = real.get(path);
		helper.assertTrue(ours != null, "no " + path + " in " + real.keySet());
		Map<ResourceLocation, Resource> withPack = new HashMap<>(real);
		withPack.put(path, new Resource(ours.source(), () -> new ByteArrayInputStream("{\"enabled\": false}".getBytes(StandardCharsets.UTF_8))));
		withPack.put(AliveWorkplace.id("tonics/broken.json"), new Resource(ours.source(), () -> new ByteArrayInputStream(
			"{\"item\": \"minecraft:apple\", \"maker\": \"baker\", \"ingredients\": [\"minecraft:sugar\"], \"jobs\": []}".getBytes(StandardCharsets.UTF_8))));
		try {
			Tonics.load(withPack);
			helper.assertTrue(Tonics.get(MINERS_BREW).isEmpty() && Tonics.of(new ItemStack(ModItems.MINERS_BREW)) == null, "Miner's Brew still there");
			helper.assertTrue(Tonics.get(BUILDERS_TEA).isPresent() && Tonics.get(CORDIAL).isPresent(), "the others went too: " + Tonics.all());
			helper.assertTrue(Tonics.get(AliveWorkplace.id("broken")).isEmpty(), "a broken file loaded");
		} finally {
			Tonics.load(real);
		}
		helper.assertTrue(Tonics.get(MINERS_BREW).isPresent(), "Miner's Brew back");
		helper.assertTrue(rejects(helper, "{\"item\": \"minecraft:apple\", \"maker\": \"baker\", \"ingredients\": [\"minecraft:sugar\"], \"jobs\": []}").contains("baker"),
			"a bad maker");
		helper.assertTrue(rejects(helper, "{\"item\": \"minecraft:nope\", \"maker\": \"chef\", \"ingredients\": [\"minecraft:sugar\"], \"jobs\": []}").contains("nope"),
			"an unknown item");
		helper.assertTrue(rejects(helper, "{\"item\": \"minecraft:apple\", \"maker\": \"chef\", \"ingredients\": [\"minecraft:nope\"], \"jobs\": []}").contains("nope"),
			"an unknown ingredient");
		helper.succeed();
	}

	/**
	 * Every other bonus there is (stand-ins: Pokémon, Legends and the rest need their mods or set-ups) together with
	 * the tonic stop at the cap, 100/maxWorkPace, also a lower one; the tonic is one of the bonuses counted.
	 */
	//$ gametest_ticks_batch AREA '100' '"tonicCap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "tonicCap")
	public void theCapHoldsWithEveryBonus(GameTestHelper helper) {
		Leftovers.clear(helper);
		quiet(helper);
		int cap = Pace.MAX_PERCENT;
		List<Pace.Source> before = new ArrayList<>(Pace.sources());
		for (Pace.Source s : before) {
			if (s.kind() == Pace.Kind.BONUS && s != Pace.TONIC) {
				Pace.register(new Pace.Source(s.id(), Pace.Kind.BONUS, v -> 0.8f, v -> Component.literal(s.id())));
			}
		}
		Leftovers.after(helper, () -> {
			before.forEach(Pace::register);
			Pace.MAX_PERCENT = cap;
		});
		Villager miner = worker(helper, new BlockPos(4, 2, 4), ModVillagers.MINER, "Dara");
		helper.runAfterDelay(5, () -> {
			Pace.MAX_PERCENT = 200;
			Tonics.drink(helper.getLevel(), miner, Tonics.get(MINERS_BREW).orElseThrow());
			Pace.Breakdown pace = Pace.of(miner);
			long bonuses = before.stream().filter(s -> s.kind() == Pace.Kind.BONUS).count();
			helper.assertTrue(pace.faster().size() == bonuses && pace.faster().stream().anyMatch(p -> p.source() == Pace.TONIC),
				"bonuses counted: " + pace.faster().stream().map(p -> p.source().id()).toList() + " of " + bonuses);
			float penalties = pace.slower().stream().map(Pace.Part::factor).reduce(1f, (a, b) -> a * b);
			helper.assertTrue(pace.capped() && pace.bonuses() < 0.5f && Math.abs(pace.factor() - 0.5f * penalties) < EPSILON,
				"over the cap: " + pace);
			Pace.MAX_PERCENT = 150;
			helper.assertTrue(Math.abs(Pace.factor(miner) - 100f / 150f * penalties) < EPSILON, "a cap of 150: " + Pace.factor(miner));
			helper.succeed();
		});
	}

	/** The tooltips say what each does, for which jobs and who makes it from what; nobody can craft them. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theTooltipsAndNoRecipe(GameTestHelper helper) {
		List<String> brew = Tonics.tooltip(Tonics.get(MINERS_BREW).orElseThrow()).stream().map(Component::getString).toList();
		helper.assertTrue(brew.equals(List.of("Drink: 25% faster work for 20 minutes", "For Miners, Sifters and Netherworkers",
			"Brewed by a Cleric from Glass Bottle, Glowstone Dust, Coal and Sugar", "Right-click a villager it suits. Players can't make it")),
			"Miner's Brew: " + brew);
		List<String> tea = Tonics.tooltip(Tonics.get(BUILDERS_TEA).orElseThrow()).stream().map(Component::getString).toList();
		helper.assertTrue(tea.equals(List.of("Drink: 25% faster work for 20 minutes", "For Builders, Carpenters, Masons and Dyers",
			"Cooked by a Chef from Glass Bottle, 2 Sweet Berries and Sugar", "Right-click a villager it suits. Players can't make it")),
			"Builder's Tea: " + tea);
		List<String> cordial = Tonics.tooltip(Tonics.get(CORDIAL).orElseThrow()).stream().map(Component::getString).toList();
		helper.assertTrue(cordial.get(1).equals("For Farmers"), "a job with no plural of its own: " + cordial);
		helper.assertTrue(new ItemStack(ModItems.MINERS_BREW).getHoverName().getString().equals("Miner's Brew")
			&& new ItemStack(ModItems.BUILDERS_TEA).getHoverName().getString().equals("Builder's Tea"), "names");
		// The tooltips go to the players in one packet; nothing left out.
		helper.assertTrue(Tonics.sync().entries().size() == Tonics.all().size(), "sync: " + Tonics.sync().entries().size());
		for (var recipe : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
			Item made = recipe.value().getResultItem(helper.getLevel().registryAccess()).getItem();
			helper.assertTrue(made != ModItems.MINERS_BREW && made != ModItems.BUILDERS_TEA, "a recipe makes a tonic: " + recipe.id());
		}
		helper.succeed();
	}

	/**
	 * {@code tonics} off (on by default): villagers refuse them ("Tonics are switched off on this server: ..."), a tonic
	 * drunk before does nothing (it stays saved) and makers have nothing to make.
	 */
	//$ gametest_ticks_batch AREA '100' '"tonicOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "tonicOff")
	public void tonicsOffAreRefusedAndNotMade(GameTestHelper helper) {
		Leftovers.clear(helper);
		quiet(helper);
		boolean enabled = Tonics.ENABLED;
		Leftovers.after(helper, () -> Tonics.ENABLED = enabled);
		Villager miner = worker(helper, new BlockPos(4, 2, 4), ModVillagers.MINER, "Dara");
		Villager other = worker(helper, new BlockPos(8, 2, 4), ModVillagers.MINER, "Ash");
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		BlockPos chestPos = new BlockPos(10, 2, 10);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.GLASS_BOTTLE, 2));
		chest.setItem(1, new ItemStack(Items.GLOWSTONE_DUST, 2));
		chest.setItem(2, new ItemStack(Items.COAL, 2));
		chest.setItem(3, new ItemStack(Items.SUGAR, 2));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			helper.assertTrue(WorkplaceConfig.parse("{}").tonics && !WorkplaceConfig.parse("{\"tonics\": false}").tonics, "the config switch");
			List<BlockPos> own = List.of(helper.absolutePos(chestPos));
			helper.assertTrue(Tonics.next(level, Tonics.Maker.ALCHEMIST, own, own, 1) != null, "nothing to brew with tonics on");
			Tonics.drink(level, other, Tonics.get(MINERS_BREW).orElseThrow());
			Tonics.ENABLED = false;
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MINERS_BREW));
			Tonics.Offer offer = Tonics.offer(player, miner, player.getMainHandItem());
			helper.assertTrue(offer.outcome() == Tonics.Outcome.DISABLED
				&& offer.message().getString().equals("Tonics are switched off on this server: Dara won't drink Miner's Brew."), "off: " + offer.message().getString());
			helper.assertTrue(player.getMainHandItem().getCount() == 1 && !ModAttachments.TONIC.has(miner), "drunk with tonics off");
			helper.assertTrue(ModAttachments.TONIC.has(other) && part(other) == 1f, "a tonic drunk before still counts: " + Pace.of(other));
			helper.assertTrue(Tonics.next(level, Tonics.Maker.ALCHEMIST, own, own, 1) == null, "brewed with tonics off");
			Tonics.ENABLED = true;
			helper.assertTrue(Math.abs(part(other) - 1f / 1.25f) < EPSILON, "back on: " + Pace.of(other));
			helper.succeed();
		});
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	/**
	 * The pace's numbers alone: no hall another batch left nearby (its village's pace) and no mood (its own pace comes and
	 * goes); put back after. Only for tests alone in their batch.
	 */
	/** A Cleric with the makings of one Smith's Draught in the chest by their station makes it from them (30.16). */
	//$ gametest_ticks_batch AREA '3000' '"tonicSmithMade"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "tonicSmithMade")
	public void theClericBrewsSmithsDraught(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos stand = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(stand, Blocks.BREWING_STAND);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.GLASS_BOTTLE, 1));
		chest.setItem(1, new ItemStack(Items.BLAZE_POWDER, 1));
		chest.setItem(2, new ItemStack(Items.IRON_NUGGET, 2));
		Villager maker = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), maker, helper.absolutePos(stand), PoiTypes.CLERIC, VillagerProfession.CLERIC);
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(ModItems.SMITHS_DRAUGHT) == 1, "Smith's Draught in the chest: " + contents(chest));
			helper.assertTrue(chest.countItem(Items.GLASS_BOTTLE) == 0 && chest.countItem(Items.BLAZE_POWDER) == 0 && chest.countItem(Items.IRON_NUGGET) == 0, "the makings left: " + contents(chest));
		});
	}

	/**
	 * Smith's Draught (30.16): a Toolsmith drinks it from a right-click and works 25% faster for a day; a Cleric has no use
	 * for it and the player keeps it.
	 */
	//$ gametest_ticks_batch AREA '100' '"tonicSmith"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "tonicSmith")
	public void aToolsmithDrinksSmithsDraughtAndAClericRefusesIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		quiet(helper);
		Villager suited = worker(helper, new BlockPos(4, 2, 4), VillagerProfession.TOOLSMITH, "Dara");
		Villager other = worker(helper, new BlockPos(10, 2, 4), VillagerProfession.CLERIC, "Ash");
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		helper.runAfterDelay(5, () -> {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMITHS_DRAUGHT));
			Tonics.Offer refused = Tonics.offer(player, other, player.getMainHandItem());
			helper.assertTrue(refused.outcome() == Tonics.Outcome.REFUSED && refused.message().getString().equals("Ash has no use for Smith's Draught."),
				"refused: " + refused.outcome() + " " + (refused.message() == null ? null : refused.message().getString()));
			helper.assertTrue(rightClick(player, other) == InteractionResult.SUCCESS && player.getMainHandItem().getCount() == 1
				&& !ModAttachments.TONIC.has(other) && part(other) == 1f, "the Cleric drank it");
			helper.assertTrue(rightClick(player, suited) == InteractionResult.SUCCESS, "no answer from the Toolsmith");
			helper.assertTrue(player.getMainHandItem().isEmpty(), "not drunk: " + player.getMainHandItem());
			Tonics.Drunk drunk = ModAttachments.TONIC.get(suited);
			helper.assertTrue(drunk != null && drunk.id().equals(AliveWorkplace.id("smiths_draught").toString())
				&& drunk.until() == helper.getLevel().getGameTime() + 24000, "drunk: " + drunk);
			helper.assertTrue(Math.abs(Pace.factor(suited) - 1f / 1.25f) < EPSILON && Pace.of(suited).percent() == 25, "pace: " + Pace.of(suited));
			String status = Pace.describe(suited).getString();
			helper.assertTrue(status.equals("25% faster (Smith's Draught, 20 min left)"), "status: " + status);
			helper.succeed();
		});
	}

	/** A Cleric with the makings of one Scholar's Infusion in the chest by their station makes it from them (30.16). */
	//$ gametest_ticks_batch AREA '3000' '"tonicScholarMade"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "tonicScholarMade")
	public void theClericBrewsScholarsInfusion(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos stand = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(stand, Blocks.BREWING_STAND);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.GLASS_BOTTLE, 1));
		chest.setItem(1, new ItemStack(Items.AMETHYST_SHARD, 1));
		chest.setItem(2, new ItemStack(Items.GLOW_BERRIES, 1));
		Villager maker = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), maker, helper.absolutePos(stand), PoiTypes.CLERIC, VillagerProfession.CLERIC);
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(ModItems.SCHOLARS_INFUSION) == 1, "Scholar's Infusion in the chest: " + contents(chest));
			helper.assertTrue(chest.countItem(Items.GLASS_BOTTLE) == 0 && chest.countItem(Items.AMETHYST_SHARD) == 0 && chest.countItem(Items.GLOW_BERRIES) == 0, "the makings left: " + contents(chest));
		});
	}

	/**
	 * Scholar's Infusion (30.16): a Scholar drinks it from a right-click and works 25% faster for a day; a Farmer has no use
	 * for it and the player keeps it.
	 */
	//$ gametest_ticks_batch AREA '100' '"tonicScholar"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "tonicScholar")
	public void aScholarDrinksScholarsInfusionAndAFarmerRefusesIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		quiet(helper);
		Villager suited = worker(helper, new BlockPos(4, 2, 4), ModVillagers.SCHOLAR, "Dara");
		Villager other = worker(helper, new BlockPos(10, 2, 4), VillagerProfession.FARMER, "Ash");
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		helper.runAfterDelay(5, () -> {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCHOLARS_INFUSION));
			Tonics.Offer refused = Tonics.offer(player, other, player.getMainHandItem());
			helper.assertTrue(refused.outcome() == Tonics.Outcome.REFUSED && refused.message().getString().equals("Ash has no use for Scholar's Infusion."),
				"refused: " + refused.outcome() + " " + (refused.message() == null ? null : refused.message().getString()));
			helper.assertTrue(rightClick(player, other) == InteractionResult.SUCCESS && player.getMainHandItem().getCount() == 1
				&& !ModAttachments.TONIC.has(other) && part(other) == 1f, "the Farmer drank it");
			helper.assertTrue(rightClick(player, suited) == InteractionResult.SUCCESS, "no answer from the Scholar");
			helper.assertTrue(player.getMainHandItem().isEmpty(), "not drunk: " + player.getMainHandItem());
			Tonics.Drunk drunk = ModAttachments.TONIC.get(suited);
			helper.assertTrue(drunk != null && drunk.id().equals(AliveWorkplace.id("scholars_infusion").toString())
				&& drunk.until() == helper.getLevel().getGameTime() + 24000, "drunk: " + drunk);
			helper.assertTrue(Math.abs(Pace.factor(suited) - 1f / 1.25f) < EPSILON && Pace.of(suited).percent() == 25, "pace: " + Pace.of(suited));
			String status = Pace.describe(suited).getString();
			helper.assertTrue(status.equals("25% faster (Scholar's Infusion, 20 min left)"), "status: " + status);
			helper.succeed();
		});
	}

	/** A Chef with the makings of one Harvest Cordial in the chest by their station makes it from them (30.16). */
	//$ gametest_ticks_batch AREA '3000' '"tonicHarvestMade"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "tonicHarvestMade")
	public void theChefCooksHarvestCordial(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos stand = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(stand, ModBlocks.KITCHEN_STOVE);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.GLASS_BOTTLE, 1));
		chest.setItem(1, new ItemStack(Items.APPLE, 1));
		chest.setItem(2, new ItemStack(Items.WHEAT, 1));
		chest.setItem(3, new ItemStack(Items.SUGAR, 1));
		Villager maker = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), maker, helper.absolutePos(stand), ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(ModItems.HARVEST_CORDIAL) == 1, "Harvest Cordial in the chest: " + contents(chest));
			helper.assertTrue(chest.countItem(Items.GLASS_BOTTLE) == 0 && chest.countItem(Items.APPLE) == 0 && chest.countItem(Items.WHEAT) == 0 && chest.countItem(Items.SUGAR) == 0, "the makings left: " + contents(chest));
		});
	}

	/**
	 * Harvest Cordial (30.16): a Orchard Keeper drinks it from a right-click and works 25% faster for a day; a Librarian has no use
	 * for it and the player keeps it.
	 */
	//$ gametest_ticks_batch AREA '100' '"tonicHarvest"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "tonicHarvest")
	public void anOrchardKeeperDrinksHarvestCordialAndALibrarianRefusesIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		quiet(helper);
		Villager suited = worker(helper, new BlockPos(4, 2, 4), ModVillagers.ORCHARD_KEEPER, "Dara");
		Villager other = worker(helper, new BlockPos(10, 2, 4), VillagerProfession.LIBRARIAN, "Ash");
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		helper.runAfterDelay(5, () -> {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HARVEST_CORDIAL));
			Tonics.Offer refused = Tonics.offer(player, other, player.getMainHandItem());
			helper.assertTrue(refused.outcome() == Tonics.Outcome.REFUSED && refused.message().getString().equals("Ash has no use for Harvest Cordial."),
				"refused: " + refused.outcome() + " " + (refused.message() == null ? null : refused.message().getString()));
			helper.assertTrue(rightClick(player, other) == InteractionResult.SUCCESS && player.getMainHandItem().getCount() == 1
				&& !ModAttachments.TONIC.has(other) && part(other) == 1f, "the Librarian drank it");
			helper.assertTrue(rightClick(player, suited) == InteractionResult.SUCCESS, "no answer from the Orchard Keeper");
			helper.assertTrue(player.getMainHandItem().isEmpty(), "not drunk: " + player.getMainHandItem());
			Tonics.Drunk drunk = ModAttachments.TONIC.get(suited);
			helper.assertTrue(drunk != null && drunk.id().equals(AliveWorkplace.id("harvest_cordial").toString())
				&& drunk.until() == helper.getLevel().getGameTime() + 24000, "drunk: " + drunk);
			helper.assertTrue(Math.abs(Pace.factor(suited) - 1f / 1.25f) < EPSILON && Pace.of(suited).percent() == 25, "pace: " + Pace.of(suited));
			String status = Pace.describe(suited).getString();
			helper.assertTrue(status.equals("25% faster (Harvest Cordial, 20 min left)"), "status: " + status);
			helper.succeed();
		});
	}

	/** A Chef with the makings of one Woodsman's Broth in the chest by their station makes it from them (30.16). */
	//$ gametest_ticks_batch AREA '3000' '"tonicWoodsmanMade"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "tonicWoodsmanMade")
	public void theChefCooksWoodsmansBroth(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos stand = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(stand, ModBlocks.KITCHEN_STOVE);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.BOWL, 1));
		chest.setItem(1, new ItemStack(Items.COOKED_SALMON, 1));
		chest.setItem(2, new ItemStack(Items.CARROT, 1));
		chest.setItem(3, new ItemStack(Items.BROWN_MUSHROOM, 1));
		Villager maker = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), maker, helper.absolutePos(stand), ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(ModItems.WOODSMANS_BROTH) == 1, "Woodsman's Broth in the chest: " + contents(chest));
			helper.assertTrue(chest.countItem(Items.BOWL) == 0 && chest.countItem(Items.COOKED_SALMON) == 0 && chest.countItem(Items.CARROT) == 0 && chest.countItem(Items.BROWN_MUSHROOM) == 0, "the makings left: " + contents(chest));
		});
	}

	/**
	 * Woodsman's Broth (30.16): a Lumberjack drinks it from a right-click and works 25% faster for a day; a Mason has no use
	 * for it and the player keeps it.
	 */
	//$ gametest_ticks_batch AREA '100' '"tonicWoodsman"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "tonicWoodsman")
	public void aLumberjackDrinksWoodsmansBrothAndAMasonRefusesIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		quiet(helper);
		Villager suited = worker(helper, new BlockPos(4, 2, 4), ModVillagers.LUMBERJACK, "Dara");
		Villager other = worker(helper, new BlockPos(10, 2, 4), VillagerProfession.MASON, "Ash");
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		helper.runAfterDelay(5, () -> {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WOODSMANS_BROTH));
			Tonics.Offer refused = Tonics.offer(player, other, player.getMainHandItem());
			helper.assertTrue(refused.outcome() == Tonics.Outcome.REFUSED && refused.message().getString().equals("Ash has no use for Woodsman's Broth."),
				"refused: " + refused.outcome() + " " + (refused.message() == null ? null : refused.message().getString()));
			helper.assertTrue(rightClick(player, other) == InteractionResult.SUCCESS && player.getMainHandItem().getCount() == 1
				&& !ModAttachments.TONIC.has(other) && part(other) == 1f, "the Mason drank it");
			helper.assertTrue(rightClick(player, suited) == InteractionResult.SUCCESS, "no answer from the Lumberjack");
			helper.assertTrue(player.getMainHandItem().isEmpty(), "not drunk: " + player.getMainHandItem());
			Tonics.Drunk drunk = ModAttachments.TONIC.get(suited);
			helper.assertTrue(drunk != null && drunk.id().equals(AliveWorkplace.id("woodsmans_broth").toString())
				&& drunk.until() == helper.getLevel().getGameTime() + 24000, "drunk: " + drunk);
			helper.assertTrue(Math.abs(Pace.factor(suited) - 1f / 1.25f) < EPSILON && Pace.of(suited).percent() == 25, "pace: " + Pace.of(suited));
			String status = Pace.describe(suited).getString();
			helper.assertTrue(status.equals("25% faster (Woodsman's Broth, 20 min left)"), "status: " + status);
			helper.succeed();
		});
	}

	/** The four tonics of 30.16: what their tooltips say (who they suit, who makes them from what). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theTooltipsOfTheFourMoreTonics(GameTestHelper helper) {
		Map<String, List<String>> want = new LinkedHashMap<>();
		want.put("smiths_draught", List.of("For Armorers, Toolsmiths, Weaponsmiths, Tinkerers and Ball Smiths",
			"Brewed by a Cleric from Glass Bottle, Blaze Powder and 2 Iron Nuggets"));
		want.put("scholars_infusion", List.of("For Scholars, Teachers, Librarians, Cartographers and Fossil Scientists",
			"Brewed by a Cleric from Glass Bottle, Amethyst Shard and Glow Berries"));
		want.put("harvest_cordial", List.of("For Farmers, Orchard Keepers, Florists, Beekeepers, Composters, Shepherds, Butchers, Ranchers and Chefs",
			"Cooked by a Chef from Glass Bottle, Apple, Wheat and Sugar"));
		want.put("woodsmans_broth", List.of("For Lumberjacks, Fletchers, Fishermen, Porters and Postmen",
			"Cooked by a Chef from Bowl, Cooked Salmon, Carrot and Brown Mushroom"));
		Map<String, String> names = Map.of("smiths_draught", "Smith's Draught", "scholars_infusion", "Scholar's Infusion",
			"harvest_cordial", "Harvest Cordial", "woodsmans_broth", "Woodsman's Broth");
		want.forEach((id, lines) -> {
			Tonics.Tonic tonic = Tonics.get(AliveWorkplace.id(id)).orElseThrow();
			List<String> got = Tonics.tooltip(tonic).stream().map(Component::getString).toList();
			helper.assertTrue(got.equals(List.of("Drink: 25% faster work for 20 minutes", lines.get(0), lines.get(1),
				"Right-click a villager it suits. Players can't make it")), id + ": " + got);
			Item item = tonic.item();
			helper.assertTrue(new ItemStack(item).getHoverName().getString().equals(names.get(id)), "name of " + id);
			for (var recipe : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
				helper.assertTrue(recipe.value().getResultItem(helper.getLevel().registryAccess()).getItem() != item, "a recipe makes " + id + ": " + recipe.id());
			}
		});
		helper.succeed();
	}

	private static void quiet(GameTestHelper helper) {
		Leftovers.halls(helper);
		io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
		boolean moods = Moods.ENABLED;
		Moods.ENABLED = false;
		Moods.forget();
		Leftovers.after(helper, () -> {
			Moods.ENABLED = moods;
			Moods.forget();
		});
	}

	/** A grown villager of {@code job} named {@code name}, standing still (level 2, so they keep the job). */
	private static Villager worker(GameTestHelper helper, BlockPos at, VillagerProfession job, String name) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setVillagerData(villager.getVillagerData().setProfession(job).setLevel(2));
		villager.setVillagerXp(10);
		villager.setNoAi(true);
		villager.setCustomName(Component.literal(name));
		return villager;
	}

	private static ServerPlayer player(GameTestHelper helper, GameType mode) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(mode);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	/** The player right-clicks {@code villager} with what's in their main hand, through the game's use-entity event. */
	private static InteractionResult rightClick(ServerPlayer player, Villager villager) {
		player.moveTo(villager.getX() + 1, villager.getY(), villager.getZ(), 90f, 0f);
		return UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, villager, null);
	}

	/** The tonic's part of {@code villager}'s pace (1: none). */
	private static float part(Villager villager) {
		return Pace.of(villager).faster().stream().filter(p -> p.source() == Pace.TONIC).map(Pace.Part::factor).findFirst().orElse(1f);
	}

	private static String rejects(GameTestHelper helper, String json) {
		try {
			Tonics.read(AliveWorkplace.id("broken"), JsonParser.parseString(json));
		} catch (RuntimeException e) {
			return String.valueOf(e.getMessage());
		}
		helper.fail("accepted " + json);
		return "";
	}

	private static String contents(Container chest) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (!chest.getItem(i).isEmpty()) {
				out.add(chest.getItem(i).toString());
			}
		}
		return out.toString();
	}
}
