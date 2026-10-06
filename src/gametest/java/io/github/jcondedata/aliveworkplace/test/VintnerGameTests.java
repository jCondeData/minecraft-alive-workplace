package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes;
import io.github.jcondedata.aliveworkplace.hall.NobleBalls;
import io.github.jcondedata.aliveworkplace.people.Luxuries;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StockOrders;
import io.github.jcondedata.aliveworkplace.vintner.VintnerWork;
import io.github.jcondedata.aliveworkplace.vintner.Vintners;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * The Vintner (ROADMAP 34.9): picked at a cauldron with sweet berries, glow berries or an apple; presses Cider (Novice),
 * Berry Wine (Apprentice) and Vintage Wine from Berry Wine three days old (Journeyman) through the luxury workshop
 * engine, from the chest by their cauldron. The pressing tests drive {@link VintnerWork} tick by tick on a NoAI Vintner.
 */
public class VintnerGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos CAULDRON = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STANDING = new BlockPos(3, 2, 3);

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	/** A cauldron with a chest beside it, and no store nearby but the chest. */
	private static void vat(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int range = StockOrders.RANGE;
		StockOrders.RANGE = 4;
		Leftovers.after(helper, () -> StockOrders.RANGE = range);
		helper.setBlock(CAULDRON, Blocks.CAULDRON);
		helper.setBlock(CHEST, Blocks.CHEST);
	}

	/** A Vintner of {@code level} at the vat (NoAI), not yet working. */
	private static Villager vintner(GameTestHelper helper, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.VINTNER).setLevel(level));
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(CAULDRON)));
		return v;
	}

	/** Runs a Vintner's shift on {@code v} every tick, as the WORK activity would. */
	private static VintnerWork work(GameTestHelper helper, Villager v) {
		ServerLevel world = helper.getLevel();
		VintnerWork work = new VintnerWork();
		helper.onEachTick(() -> {
			if (!v.isAlive() || v.isRemoved()) {
				return;
			}
			long now = world.getGameTime();
			if (work.getStatus() == Behavior.Status.STOPPED) {
				work.tryStart(world, v, now);
			} else {
				work.tickOrStop(world, v, now);
			}
		});
		return work;
	}

	private static Container chest(GameTestHelper helper) {
		return helper.getBlockEntity(CHEST);
	}

	private static int count(Container c, Item item) {
		return c.countItem(item);
	}

	private static String name(VillagerProfession job) {
		return net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).toString();
	}

	/** Sweet berries, glow berries and an apple each make a jobless villager by a cauldron its Vintner; leather still a Leatherworker. */
	//$ gametest_batch AREA '"vintnerPicked"'
	@GameTest(template = AREA, batch = "vintnerPicked")
	public void pickedByEachItemAtACauldron(GameTestHelper helper) {
		vat(helper);
		ServerPlayer player = player(helper);
		for (Item item : List.of(Items.SWEET_BERRIES, Items.GLOW_BERRIES, Items.APPLE, Items.LEATHER, Items.GRAVEL)) {
			Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
			InteractionResult result = Stations.choose(player, villager, new ItemStack(item));
			VillagerProfession want = item == Items.LEATHER ? VillagerProfession.LEATHERWORKER : item == Items.GRAVEL ? ModVillagers.SIFTER : ModVillagers.VINTNER;
			helper.assertTrue(result.consumesAction(), item + ": nothing happened (" + result + ")");
			helper.assertTrue(villager.getVillagerData().getProfession() == want,
				item + " gave " + name(villager.getVillagerData().getProfession()) + ", not " + name(want));
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null)
				.equals(helper.absolutePos(CAULDRON)), item + ": not working at the cauldron");
			villager.discard();
			helper.getLevel().getPoiManager().release(helper.absolutePos(CAULDRON));
		}
		helper.assertTrue(Stations.of(ModVillagers.VINTNER).map(s -> s.block() == Blocks.CAULDRON).orElse(false), "the Vintner's station isn't the cauldron");
		helper.succeed();
	}

	/** A Novice presses Cider from 3 apples and a bottle each (purple splashes as they press), and leaves the berries alone. */
	//$ gametest_ticks_batch AREA '1600' '"vintnerCider"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "vintnerCider")
	public void aNovicePressesCider(GameTestHelper helper) {
		vat(helper);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.APPLE, 9));
		c.setItem(1, new ItemStack(Items.GLASS_BOTTLE, 3));
		c.setItem(2, new ItemStack(Items.SWEET_BERRIES, 12));
		int pressedBefore = VintnerWork.pressings;
		Villager v = vintner(helper, 1);
		work(helper, v);
		helper.succeedWhen(() -> {
			helper.assertTrue(count(c, ModItems.CIDER) == 3, count(c, ModItems.CIDER) + " cider, expected 3");
			helper.assertTrue(count(c, Items.APPLE) == 0 && count(c, Items.GLASS_BOTTLE) == 0,
				count(c, Items.APPLE) + " apples and " + count(c, Items.GLASS_BOTTLE) + " bottles left");
			helper.assertTrue(count(c, ModItems.BERRY_WINE) == 0 && count(c, Items.SWEET_BERRIES) == 12, "a Novice pressed Berry Wine");
			helper.assertTrue(VintnerWork.pressings > pressedBefore, "no splashes at the vat");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(v).isEmpty(), "things left in the bag");
		});
	}

	/** An Apprentice presses Berry Wine from 6 sweet berries or 4 glow berries and a bottle; each is stamped with today. */
	//$ gametest_ticks_batch AREA '1600' '"vintnerBerry"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "vintnerBerry")
	public void anApprenticePressesBerryWine(GameTestHelper helper) {
		vat(helper);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.SWEET_BERRIES, 12));
		c.setItem(1, new ItemStack(Items.GLOW_BERRIES, 4));
		c.setItem(2, new ItemStack(Items.GLASS_BOTTLE, 3));
		long today = LuxuryRecipes.today(helper.getLevel());
		work(helper, vintner(helper, 2));
		helper.succeedWhen(() -> {
			helper.assertTrue(count(c, ModItems.BERRY_WINE) == 3, count(c, ModItems.BERRY_WINE) + " berry wine, expected 3");
			helper.assertTrue(count(c, Items.SWEET_BERRIES) == 0 && count(c, Items.GLOW_BERRIES) == 0 && count(c, Items.GLASS_BOTTLE) == 0,
				"makings left: " + count(c, Items.SWEET_BERRIES) + " sweet, " + count(c, Items.GLOW_BERRIES) + " glow, " + count(c, Items.GLASS_BOTTLE) + " bottles");
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (s.is(ModItems.BERRY_WINE)) {
					LuxuryRecipes.MadeDay day = s.get(ModComponents.MADE_DAY);
					helper.assertTrue(day != null && day.day() == today && day.vintageDays() == 3, "made day " + day + ", expected day " + today + ", 3 to vintage");
				}
			}
		});
	}

	/** A Journeyman leaves Berry Wine pressed today alone; three days on, it is re-corked as Vintage Wine. */
	//$ gametest_ticks_batch AREA '1600' '"vintnerVintage"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "vintnerVintage")
	public void vintageWaitsItsThreeDays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long dayTime = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(dayTime));
		vat(helper);
		Container c = chest(helper);
		long today = LuxuryRecipes.today(level);
		ItemStack fresh = new ItemStack(ModItems.BERRY_WINE, 2);
		fresh.set(ModComponents.MADE_DAY, new LuxuryRecipes.MadeDay(today, 3));
		c.setItem(0, fresh);
		work(helper, vintner(helper, 3));
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(count(c, ModItems.VINTAGE_WINE) == 0, "Vintage Wine from Berry Wine pressed today");
			helper.assertTrue(count(c, ModItems.BERRY_WINE) == 2, "the fresh Berry Wine was taken");
			level.setDayTime(level.getDayTime() + 3 * 24000L);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(LuxuryRecipes.today(level) >= today + 3, "the day hasn't moved on yet");
			helper.assertTrue(count(c, ModItems.VINTAGE_WINE) == 2 && count(c, ModItems.BERRY_WINE) == 0,
				count(c, ModItems.VINTAGE_WINE) + " vintage and " + count(c, ModItems.BERRY_WINE) + " berry wine three days on, expected 2 and 0");
		});
	}

	/** With nothing to press, a Novice asks for apples on the requests board. */
	//$ gametest_ticks_batch AREA '600' '"vintnerAsks"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "vintnerAsks")
	public void asksForApplesWhenTheChestIsEmpty(GameTestHelper helper) {
		vat(helper);
		Villager v = vintner(helper, 1);
		work(helper, v);
		helper.succeedWhen(() -> {
			boolean asked = Requests.of(helper.getLevel(), v).stream().anyMatch(r -> r.accepts().test(new ItemStack(Items.APPLE)));
			helper.assertTrue(asked, "no request for apples: " + Requests.of(helper.getLevel(), v).stream().map(r -> r.what().getString()).toList());
			helper.assertTrue(count(chest(helper), ModItems.CIDER) == 0, "cider out of nothing");
		});
	}

	/**
	 * Saved and loaded mid-job (the apples already in the bag): the reloaded Vintner puts back or finishes what they had,
	 * and every apple ends up as Cider.
	 */
	//$ gametest_ticks_batch AREA '2400' '"vintnerReload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "vintnerReload")
	public void aReloadMidJobLosesNothing(GameTestHelper helper) {
		vat(helper);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.APPLE, 6));
		c.setItem(1, new ItemStack(Items.GLASS_BOTTLE, 2));
		Villager first = vintner(helper, 1);
		work(helper, first);
		Villager[] copy = new Villager[1];
		VintnerWork after = new VintnerWork(); // the reloaded Vintner's shift: a new one, as after a restart
		ServerLevel world = helper.getLevel();
		helper.onEachTick(() -> {
			if (copy[0] == null && first.isAlive() && !ModAttachments.BUILDER_BAG.getOrCreate(first).isEmpty()) {
				CompoundTag tag = first.saveWithoutId(new CompoundTag());
				first.discard();
				Villager v = EntityType.VILLAGER.create(world);
				v.load(tag);
				v.setUUID(UUID.randomUUID());
				world.addFreshEntity(v);
				v.setNoAi(true);
				copy[0] = v;
			} else if (copy[0] != null && copy[0].isAlive()) {
				long now = world.getGameTime();
				if (after.getStatus() == Behavior.Status.STOPPED) {
					after.tryStart(world, copy[0], now);
				} else {
					after.tickOrStop(world, copy[0], now);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(copy[0] != null, "the Vintner never fetched anything");
			helper.assertTrue(copy[0].getVillagerData().getProfession() == ModVillagers.VINTNER, "the job was lost on reload");
			helper.assertTrue(count(c, ModItems.CIDER) == 2 && count(c, Items.APPLE) == 0 && count(c, Items.GLASS_BOTTLE) == 0,
				count(c, ModItems.CIDER) + " cider, " + count(c, Items.APPLE) + " apples, " + count(c, Items.GLASS_BOTTLE) + " bottles; expected 2, 0, 0");
		});
	}

	/** Config {@code vintners} off: the fruit picks no job at a cauldron, and a Vintner already hired presses nothing. */
	//$ gametest_ticks_batch AREA '400' '"vintnerOff"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "vintnerOff")
	public void switchedOffPressesNothing(GameTestHelper helper) {
		vat(helper);
		Vintners.ENABLED = false;
		Leftovers.after(helper, () -> Vintners.ENABLED = true);
		Villager jobless = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 3));
		Stations.choose(player(helper), jobless, new ItemStack(Items.APPLE));
		helper.assertTrue(jobless.getVillagerData().getProfession() != ModVillagers.VINTNER, "made a Vintner with the switch off");
		jobless.discard();
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.APPLE, 9));
		c.setItem(1, new ItemStack(Items.GLASS_BOTTLE, 3));
		work(helper, vintner(helper, 1));
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(count(c, ModItems.CIDER) == 0 && count(c, Items.APPLE) == 9, "pressed with the switch off");
			helper.succeed();
		});
	}

	/** The drinks: Cider 2 hunger, Berry Wine 3, Vintage Wine 4 and Regeneration; the bottle comes back. */
	//$ gametest_batch AREA '"vintnerDrink"'
	@GameTest(template = AREA, batch = "vintnerDrink")
	public void drinkingGivesTheBottleBack(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		int[] hunger = {2, 3, 4};
		List<Item> drinks = List.of(ModItems.CIDER, ModItems.BERRY_WINE, ModItems.VINTAGE_WINE);
		for (int i = 0; i < drinks.size(); i++) {
			player.getInventory().clearContent();
			player.getFoodData().setFoodLevel(10);
			ItemStack stack = new ItemStack(drinks.get(i), 2);
			ItemStack left = stack.finishUsingItem(helper.getLevel(), player);
			helper.assertTrue(player.getFoodData().getFoodLevel() == 10 + hunger[i],
				drinks.get(i) + ": hunger " + player.getFoodData().getFoodLevel() + ", expected " + (10 + hunger[i]));
			helper.assertTrue(left.is(drinks.get(i)) && left.getCount() == 1, drinks.get(i) + ": " + left + " left in hand");
			helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) == 1, drinks.get(i) + ": the bottle didn't come back");
			ItemStack last = new ItemStack(drinks.get(i));
			helper.assertTrue(last.finishUsingItem(helper.getLevel(), player).is(Items.GLASS_BOTTLE), drinks.get(i) + ": the last one leaves no bottle in hand");
		}
		helper.assertTrue(player.hasEffect(MobEffects.REGENERATION), "Vintage Wine gave no Regeneration");
		helper.assertTrue(player.getEffect(MobEffects.REGENERATION).getDuration() <= 100, "Regeneration longer than 5 s");
		helper.succeed();
	}

	/** The luxury files, the recipes and the ball's wine tag hold the three drinks; without Cobblemon its berry recipe stays out. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theLuxuryFilesAndRecipes(GameTestHelper helper) {
		check(helper, "cider", ModItems.CIDER, 4);
		check(helper, "berry_wine", ModItems.BERRY_WINE, 2);
		check(helper, "vintage_wine", ModItems.VINTAGE_WINE, 2);
		LuxuryRecipes.Recipe vintage = LuxuryRecipes.all().get(AliveWorkplace.id("vintage_wine"));
		helper.assertTrue(vintage != null && vintage.level() == 3 && vintage.inputs().get(0).minAgeDays() == 3
			&& vintage.inputs().get(0).item().orElse(null) == ModItems.BERRY_WINE, "the vintage recipe: " + vintage);
		helper.assertTrue(LuxuryRecipes.making(ModItems.BERRY_WINE).size() == 2, "berry wine recipes without Cobblemon: " + LuxuryRecipes.making(ModItems.BERRY_WINE));
		helper.assertTrue(LuxuryRecipes.forJob(ModVillagers.VINTNER, 1).size() == 1, "a Novice's recipes: " + LuxuryRecipes.forJob(ModVillagers.VINTNER, 1));
		for (Item wine : List.of(ModItems.CIDER, ModItems.BERRY_WINE, ModItems.VINTAGE_WINE)) {
			helper.assertTrue(new ItemStack(wine).is(NobleBalls.WINE), wine + " isn't served at the ball");
			helper.assertTrue(new ItemStack(wine).is(io.github.jcondedata.aliveworkplace.hall.VillageNeeds.NOT_A_MEAL), wine + " is eaten as a meal");
		}
		helper.succeed();
	}

	private static void check(GameTestHelper helper, String id, Item item, int every) {
		Luxuries.Luxury luxury = Luxuries.get(AliveWorkplace.id(id));
		helper.assertTrue(luxury != null && luxury.everyDays() == every && luxury.matches(new ItemStack(item)), id + ": " + luxury);
	}

	/** Every new sentence a player reads. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theText(GameTestHelper helper) {
		expect(helper, Component.translatable("message.aliveworkplace.vintner.title", 5), "Vintner · 5 made");
		expect(helper, Component.translatable(Stations.itemKey(Stations.of(ModVillagers.VINTNER).orElseThrow().jobs().stream()
			.filter(j -> j.profession().get() == ModVillagers.VINTNER).findFirst().orElseThrow())), "Sweet berries, glow berries or an apple");
		expect(helper, Component.translatable("entity.minecraft.villager.vintner"), "Vintner");
		expect(helper, Component.translatable("entity.minecraft.zombie_villager.vintner"), "Zombie Vintner");
		expect(helper, Component.translatable("item.aliveworkplace.cider"), "Cider");
		expect(helper, Component.translatable("item.aliveworkplace.berry_wine"), "Berry Wine");
		expect(helper, Component.translatable("item.aliveworkplace.vintage_wine"), "Vintage Wine");
		expect(helper, Component.translatable("aliveworkplace.config.vintners"), "Vintners");
		helper.assertTrue(english(Component.translatable("aliveworkplace.config.vintners.tooltip")).startsWith("Sneak-right-click a villager by a cauldron"),
			"the setting's tooltip");
		helper.succeed();
	}

	private static void expect(GameTestHelper helper, Component text, String want) {
		String got = english(text);
		helper.assertTrue(got.equals(want), "\"" + got + "\", expected \"" + want + "\"");
	}

	/** {@code text} in English, from the mod's own en_us.json (the server has no mod language). */
	private static String english(Component text) {
		JsonObject lang;
		try (InputStream in = Files.newInputStream(FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow()
				.findPath("assets/aliveworkplace/lang/en_us.json").orElseThrow())) {
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new GameTestAssertException("can't read en_us.json: " + e);
		}
		TranslatableContents contents = (TranslatableContents) text.getContents();
		if (!lang.has(contents.getKey())) {
			throw new GameTestAssertException("no English for " + contents.getKey());
		}
		String out = lang.get(contents.getKey()).getAsString();
		for (Object arg : contents.getArgs()) {
			out = out.replaceFirst("%s", String.valueOf(arg instanceof Component c ? c.getString() : arg));
		}
		return out;
	}
}
