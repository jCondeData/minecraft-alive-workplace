package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes;
import io.github.jcondedata.aliveworkplace.craft.LuxuryWork;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.jeweller.JewellerWork;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StockOrders;
import io.github.jcondedata.aliveworkplace.tailor.TailorWork;
import io.github.jcondedata.aliveworkplace.tailor.Tailors;
import io.github.jcondedata.aliveworkplace.vintner.VintnerWork;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1024-0353): the four luxury jobs (ROADMAP 34.5, 34.9-34.12) from the player's side, written from the spec's
 * Done when: each job picked by a real sneak-right-click with its item in either hand (through the game's use-entity
 * event, every listener included), the class gate at its boundary, the store's 8 counted once with goods that carry a
 * made day, Vintage Wine's 3 days from both sides, and nothing lost when the output chest is full, the maker is killed
 * mid-job, the workstation is broken mid-job or the job's switch goes off mid-job; and the lines above a maker's head.
 */
public class QaLuxuryJobsGameTests implements FabricGameTest {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("QaLuxuryJobs");
	static final String AREA = "aliveworkplace_test:big_area";
	static final BlockPos STATION = new BlockPos(2, 2, 2);
	static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STANDING = new BlockPos(3, 2, 3);
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	private static final BlockPos SECOND = new BlockPos(4, 2, 12);

	// ---- helpers ----

	/** A workstation with one chest beside it, and no store or hall nearby. */
	static void shop(GameTestHelper helper, Block station) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int range = StockOrders.RANGE;
		StockOrders.RANGE = 4;
		Leftovers.after(helper, () -> StockOrders.RANGE = range);
		helper.setBlock(STATION, station);
		helper.setBlock(CHEST, Blocks.CHEST);
	}

	/** A NoAI worker of {@code job} at {@code level} working at STATION. */
	static Villager worker(GameTestHelper helper, VillagerProfession job, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
		v.refreshBrain(helper.getLevel());
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(STATION)));
		return v;
	}

	/** Runs {@code work}'s shift on {@code v} every tick, as the WORK activity would. */
	static void run(GameTestHelper helper, Villager v, LuxuryWork work) {
		ServerLevel world = helper.getLevel();
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
	}

	static Container chest(GameTestHelper helper) {
		return helper.getBlockEntity(CHEST);
	}

	static int bag(Villager v, Item item) {
		return ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
	}

	/** {@code item} lying on the ground round the test area. */
	static int ground(GameTestHelper helper, Item item) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	/** {@code item} anywhere: the chest, the worker's bag and the ground. */
	static int anywhere(GameTestHelper helper, Villager v, Item item) {
		return chest(helper).countItem(item) + bag(v, item) + ground(helper, item);
	}

	private static String name(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).toString();
	}

	private static VillagerProfession job(Villager v) {
		return v.getVillagerData().getProfession();
	}

	/** A right-click as the game sends it: the main hand's turn through every use-entity listener, then the off hand's if the first passed. */
	static InteractionResult click(ServerPlayer player, Villager villager, boolean sneaking) {
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.setShiftKeyDown(sneaking);
		InteractionResult r = UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, villager, null);
		if (r == InteractionResult.PASS) {
			r = UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.OFF_HAND, villager, null);
		}
		player.setShiftKeyDown(false);
		return r;
	}

	/** Holds {@code stack} in {@code hand} and nothing in the other. */
	static void hold(ServerPlayer player, InteractionHand hand, ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		player.setItemInHand(hand, stack);
	}

	/** A player who keeps every chat and action-bar line they're shown. */
	static ServerPlayer listener(GameTestHelper helper, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qa-lux-listener"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message);
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
	}

	private static JsonObject lang;

	private static JsonObject lang() {
		if (lang == null) {
			try (InputStream in = Files.newInputStream(FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow()
					.findPath("assets/aliveworkplace/lang/en_us.json").orElseThrow())) {
				lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (Exception e) {
				throw new GameTestAssertException("can't read en_us.json: " + e);
			}
		}
		return lang;
	}

	private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?s");

	/**
	 * {@code text} in English, nested arguments included, from the mod's en_us.json (or the vanilla name for a vanilla
	 * key the mod doesn't carry, which the server can't render: those come back as the key in brackets, never as a %s).
	 */
	static String english(Component text) {
		StringBuilder out = new StringBuilder();
		if (text.getContents() instanceof TranslatableContents t) {
			String pattern;
			if (lang().has(t.getKey())) {
				pattern = lang().get(t.getKey()).getAsString();
			} else if (t.getKey().startsWith("item.minecraft.") || t.getKey().startsWith("block.minecraft.")) {
				pattern = "[" + t.getKey() + "]";
			} else {
				throw new GameTestAssertException("no English for " + t.getKey());
			}
			Object[] args = t.getArgs();
			Matcher m = ARG.matcher(pattern);
			int next = 0;
			StringBuilder sb = new StringBuilder();
			while (m.find()) {
				int index = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
				if (index >= args.length) {
					throw new GameTestAssertException(t.getKey() + " wants argument " + (index + 1) + " of " + args.length + ": \"" + pattern + "\"");
				}
				Object a = args[index];
				m.appendReplacement(sb, Matcher.quoteReplacement(a instanceof Component c ? english(c) : String.valueOf(a)));
			}
			m.appendTail(sb);
			out.append(sb);
		} else {
			out.append(text.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents p ? p.text() : "");
		}
		for (Component sibling : text.getSiblings()) {
			out.append(english(sibling));
		}
		return out.toString();
	}

	// ---- the tests ----

	record Pick(Block block, Item item, VillagerProfession job) {
	}

	/**
	 * README/34.9-34.12: sneak-right-click a jobless villager by the block holding the job's item and they take the job
	 * there, the item in the main hand or (main hand empty) the off hand alike: told once who took which job at which
	 * block. A plain right-click with the item picks nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLuxPick"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLuxPick")
	public void sneakClickWithEitherHandPicksEachLuxuryJob(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen);
		List<Pick> picks = List.of(
			new Pick(Blocks.CAULDRON, Items.APPLE, ModVillagers.VINTNER),
			new Pick(Blocks.CAULDRON, Items.SWEET_BERRIES, ModVillagers.VINTNER),
			new Pick(Blocks.CAULDRON, Items.GLOW_BERRIES, ModVillagers.VINTNER),
			new Pick(Blocks.LOOM, Items.STRING, ModVillagers.TAILOR),
			new Pick(Blocks.CARTOGRAPHY_TABLE, Items.INK_SAC, ModVillagers.PRINTER),
			new Pick(Blocks.STONECUTTER, Items.GOLD_NUGGET, ModVillagers.JEWELLER));
		helper.runAfterDelay(2, () -> {
			List<String> checked = new ArrayList<>();
			for (Pick pick : picks) {
				helper.setBlock(STATION, pick.block());
				BlockPos station = helper.absolutePos(STATION);
				for (InteractionHand hand : InteractionHand.values()) {
					String what = pick.item() + " in the " + hand + " at the " + pick.block();
					// a plain right-click first: no job
					Villager plain = helper.spawn(EntityType.VILLAGER, STANDING);
					plain.setNoAi(true);
					hold(player, hand, new ItemStack(pick.item(), 4));
					seen.clear();
					click(player, plain, false);
					helper.assertTrue(job(plain) == VillagerProfession.NONE, what + ", not sneaking: became a " + name(job(plain)));
					plain.discard();

					Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
					v.setNoAi(true);
					v.setCustomName(Component.literal("Wren"));
					hold(player, hand, new ItemStack(pick.item(), 4));
					seen.clear();
					InteractionResult r = click(player, v, true);
					helper.assertTrue(r.consumesAction(), what + ": the click wasn't taken (" + r + "); said " + seen.stream().map(QaLuxuryJobsGameTests::key).toList());
					helper.assertTrue(job(v) == pick.job(), what + ": became a " + name(job(v)) + ", not a " + name(pick.job()) + "; said "
						+ seen.stream().map(QaLuxuryJobsGameTests::key).toList());
					helper.assertTrue(v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(station::equals).isPresent(),
						what + ": not working at the block");
					List<Component> chosen = seen.stream().filter(c -> key(c).equals("message.aliveworkplace.job.chosen")).toList();
					helper.assertTrue(chosen.size() == 1, what + ": told " + chosen.size() + " times who took the job: " + seen.stream().map(QaLuxuryJobsGameTests::key).toList());
					String said = english(chosen.get(0));
					String want = "Wren took the " + english(Component.translatable("entity.minecraft.villager." + BuiltInRegistries.VILLAGER_PROFESSION.getKey(pick.job()).getPath()))
						+ " job at the ";
					helper.assertTrue(said.startsWith(want) && !said.contains("%"), what + ": \"" + said + "\", expected it to start \"" + want + "\"");
					checked.add(what);
					v.discard();
					helper.getLevel().getPoiManager().release(station);
				}
			}
			helper.assertTrue(checked.size() == picks.size() * 2, "checked " + checked);
			LOG.info("QaLuxuryJobs picks checked: {}", checked);
			helper.succeed();
		});
	}

	private static Villager classed(GameTestHelper helper, BlockPos at, String name, String cls) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		SocialClasses.seed(v, SocialClasses.get(AliveWorkplace.id(cls)));
		return v;
	}

	/**
	 * 34.8 with 34.11/34.12: the Printer needs an Artisan, the Jeweller a Burgher. In a village with a hall, a Peasant
	 * sneak-clicked with an ink sac (off hand) is refused and told why, with every name filled in; an Artisan (exactly the
	 * class) becomes the Printer; a Noble (above it) becomes the Jeweller; an Artisan is one class short for the Jeweller.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLuxClass"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLuxClass")
	public void theClassGateAtItsBoundary(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		SocialClasses.ENABLED = true;
		SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
		ClassPerks.forget();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
			ClassPerks.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(STATION, Blocks.CARTOGRAPHY_TABLE);
		helper.setBlock(SECOND, Blocks.STONECUTTER);
		helper.runAfterDelay(2, () -> {
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = listener(helper, seen);

			Villager dara = classed(helper, STANDING, "Dara", "peasant");
			hold(player, InteractionHand.OFF_HAND, new ItemStack(Items.INK_SAC));
			click(player, dara, true);
			helper.assertTrue(job(dara) == VillagerProfession.NONE, "the Peasant became a " + name(job(dara)));
			Component refusal = seen.stream().filter(c -> key(c).equals("message.aliveworkplace.job.class_needed")).findFirst().orElse(null);
			helper.assertTrue(refusal != null, "the Peasant's refusal wasn't said: " + seen.stream().map(QaLuxuryJobsGameTests::key).toList());
			String said = english(refusal);
			helper.assertTrue(said.contains("Dara") && said.contains("Peasant") && said.contains("Printer") && said.contains("Artisan") && !said.contains("%"),
				"the refusal reads \"" + said + "\"");
			helper.assertTrue(seen.stream().noneMatch(c -> key(c).equals("message.aliveworkplace.job.chosen")), "told the Peasant took the job");
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(STATION)) > 0, "the refused click took the table's place");
			dara.discard();

			Villager ann = classed(helper, STANDING, "Ann", "artisan");
			hold(player, InteractionHand.MAIN_HAND, new ItemStack(Items.INK_SAC));
			click(player, ann, true);
			helper.assertTrue(job(ann) == ModVillagers.PRINTER, "the Artisan isn't a Printer: " + name(job(ann)));

			Villager cole = classed(helper, SECOND.south(), "Cole", "artisan");
			seen.clear();
			hold(player, InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_NUGGET));
			click(player, cole, true);
			helper.assertTrue(job(cole) == VillagerProfession.NONE, "the Artisan became a " + name(job(cole)));
			helper.assertTrue(seen.stream().anyMatch(c -> key(c).equals("message.aliveworkplace.job.class_needed")), "the Artisan wasn't told why");
			cole.discard();

			Villager nell = classed(helper, SECOND.south(), "Nell", "noble");
			hold(player, InteractionHand.OFF_HAND, new ItemStack(Items.GOLD_NUGGET));
			click(player, nell, true);
			helper.assertTrue(job(nell) == ModVillagers.JEWELLER, "the Noble isn't a Jeweller: " + name(job(nell)));
			helper.succeed();
		});
	}

	/**
	 * 34.5: a maker makes what the store holds fewer than 8 of. Berry Wine carries the day it was pressed; 5 already in
	 * the chest, an Apprentice presses exactly 3 more (never past 8), each counted once.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaLuxEight"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaLuxEight")
	public void goodsWithAMadeDayCountOnceTowardsEight(GameTestHelper helper) {
		shop(helper, Blocks.CAULDRON);
		Container c = chest(helper);
		long today = LuxuryRecipes.today(helper.getLevel());
		ItemStack had = new ItemStack(ModItems.BERRY_WINE, 5);
		had.set(ModComponents.MADE_DAY, new LuxuryRecipes.MadeDay(today, 3));
		c.setItem(0, had);
		c.setItem(1, new ItemStack(Items.SWEET_BERRIES, 36));
		c.setItem(2, new ItemStack(Items.GLASS_BOTTLE, 6));
		Villager v = worker(helper, ModVillagers.VINTNER, 2);
		run(helper, v, new VintnerWork());
		helper.onEachTick(() -> helper.assertTrue(anywhere(helper, v, ModItems.BERRY_WINE) <= 8,
			anywhere(helper, v, ModItems.BERRY_WINE) + " Berry Wine: pressed past 8"));
		helper.succeedWhen(() -> {
			int wine = c.countItem(ModItems.BERRY_WINE);
			helper.assertTrue(wine == 8, wine + " Berry Wine in the chest, expected 5 + 3 = 8 (sweet berries left " + c.countItem(Items.SWEET_BERRIES) + ")");
			helper.assertTrue(c.countItem(Items.SWEET_BERRIES) == 36 - 18 && c.countItem(Items.GLASS_BOTTLE) == 3,
				c.countItem(Items.SWEET_BERRIES) + " berries and " + c.countItem(Items.GLASS_BOTTLE) + " bottles left, expected 18 and 3");
		});
	}

	/**
	 * 34.9: Vintage Wine is re-corked from Berry Wine "at least 3 days old": one pressed 2 days ago stays Berry Wine, one
	 * pressed 3 days ago becomes Vintage Wine.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaLuxAge"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaLuxAge")
	public void vintageAtThreeDaysNotTwo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long dayTime = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(dayTime));
		level.setDayTime(dayTime + 10 * 24000L);
		shop(helper, Blocks.CAULDRON);
		Container c = chest(helper);
		long today = LuxuryRecipes.today(level);
		ItemStack two = new ItemStack(ModItems.BERRY_WINE, 2);
		two.set(ModComponents.MADE_DAY, new LuxuryRecipes.MadeDay(today - 2, 3));
		ItemStack three = new ItemStack(ModItems.BERRY_WINE, 2);
		three.set(ModComponents.MADE_DAY, new LuxuryRecipes.MadeDay(today - 3, 3));
		c.setItem(0, two);
		c.setItem(1, three);
		Villager v = worker(helper, ModVillagers.VINTNER, 3);
		run(helper, v, new VintnerWork());
		helper.succeedWhen(() -> {
			helper.assertTrue(c.countItem(ModItems.VINTAGE_WINE) == 2, c.countItem(ModItems.VINTAGE_WINE) + " Vintage Wine, expected 2 (from the 3-day-old)");
			int young = 0;
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				LuxuryRecipes.MadeDay d = s.get(ModComponents.MADE_DAY);
				if (s.is(ModItems.BERRY_WINE) && d != null && d.day() == today - 2) {
					young += s.getCount();
				}
				helper.assertTrue(!(s.is(ModItems.BERRY_WINE) && d != null && d.day() == today - 3), "the 3-day-old Berry Wine is still Berry Wine");
			}
			helper.assertTrue(young == 2, young + " of the 2-day-old Berry Wine left, expected 2 (Vintage Wine from wine 2 days old)");
		});
	}

	/**
	 * Output chest full: a Novice Vintner with 64 apples and 16 bottles in a chest that has no free slot. Every Cider
	 * pressed is somewhere (the chest, the bag or dropped by it), and every 3 apples taken became one: nothing vanishes.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaLuxFull"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaLuxFull")
	public void aFullChestLosesNoCider(GameTestHelper helper) {
		shop(helper, Blocks.CAULDRON);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.APPLE, 64));
		c.setItem(1, new ItemStack(Items.GLASS_BOTTLE, 16));
		for (int i = 2; i < c.getContainerSize(); i++) {
			c.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		Villager v = worker(helper, ModVillagers.VINTNER, 1);
		run(helper, v, new VintnerWork());
		helper.runAfterDelay(1200, () -> {
			int apples = anywhere(helper, v, Items.APPLE);
			int bottles = anywhere(helper, v, Items.GLASS_BOTTLE);
			int cider = anywhere(helper, v, ModItems.CIDER);
			int pressed = (64 - apples) / 3;
			LOG.info("QaLuxuryJobs full chest: {} apples, {} bottles, {} cider anywhere", apples, bottles, cider);
			helper.assertTrue((64 - apples) % 3 == 0 && pressed == 16 - bottles,
				"apples and bottles out of step: " + apples + " apples, " + bottles + " bottles left");
			helper.assertTrue(cider == pressed, cider + " Cider anywhere, but " + pressed + " pressings' makings are gone (" + apples + " apples left)");
			helper.assertTrue(c.countItem(Items.COBBLESTONE) == 25 * 64, "the chest's other goods changed: " + c.countItem(Items.COBBLESTONE));
			helper.succeed();
		});
	}

	/**
	 * The maker dies mid-job (a zombie, lava, a player): a Novice Tailor has fetched 3 wool, 2 leather and a string for
	 * Work Clothes and is killed before sewing them. The makings in their hands aren't lost with them: they're dropped
	 * (or back in the chest), or the clothes they made are.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaLuxKilled"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaLuxKilled")
	public void aTailorKilledMidJobLosesNoMakings(GameTestHelper helper) {
		shop(helper, Blocks.LOOM);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.RED_WOOL, 3));
		c.setItem(1, new ItemStack(Items.LEATHER, 2));
		c.setItem(2, new ItemStack(Items.STRING, 1));
		Villager v = worker(helper, ModVillagers.TAILOR, 1);
		long[] killedAt = {-1};
		String[] held = {""};
		// (before the shift's own tick: the Tailor dies holding what the last tick fetched)
		helper.onEachTick(() -> {
			if (killedAt[0] < 0 && bag(v, Items.RED_WOOL) > 0) {
				held[0] = ModAttachments.BUILDER_BAG.getOrCreate(v).stacks().toString();
				v.kill();
				killedAt[0] = helper.getTick();
			}
		});
		run(helper, v, new TailorWork());
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(killedAt[0] >= 0, "the Tailor never fetched anything");
			helper.assertTrue(!v.isAlive(), "the Tailor didn't die");
			int clothes = chest(helper).countItem(ModItems.WORK_CLOTHES) + ground(helper, ModItems.WORK_CLOTHES);
			int wool = chest(helper).countItem(Items.RED_WOOL) + ground(helper, Items.RED_WOOL);
			int leather = chest(helper).countItem(Items.LEATHER) + ground(helper, Items.LEATHER);
			int string = chest(helper).countItem(Items.STRING) + ground(helper, Items.STRING);
			LOG.info("QaLuxuryJobs killed at tick {} holding {}: {} wool, {} leather, {} string, {} clothes left in the world", killedAt[0], held[0], wool, leather, string, clothes);
			helper.assertTrue(wool + 3 * clothes == 3 && leather + 2 * clothes == 2 && string + clothes == 1,
				"killed holding " + held[0] + ": " + wool + " wool, " + leather + " leather, " + string + " string and " + clothes
					+ " Work Clothes left in the chest or on the ground; expected 3, 2, 1 (or the clothes)");
			helper.succeed();
		});
	}

	/**
	 * The workstation broken mid-job: a Novice Jeweller has fetched for an Amethyst Ring when the stonecutter is broken
	 * (and not put back). Nothing is lost: within a minute the shards and copper are back in the chest or on the ground
	 * (or a ring made of them), not kept in the bag of a Jeweller with no bench.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaLuxBroken"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaLuxBroken")
	public void theStonecutterBrokenMidJobLosesNothing(GameTestHelper helper) {
		shop(helper, Blocks.STONECUTTER);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.AMETHYST_SHARD, 2));
		c.setItem(1, new ItemStack(Items.COPPER_INGOT, 2));
		Villager v = worker(helper, ModVillagers.JEWELLER, 1);
		long[] brokenAt = {-1};
		helper.onEachTick(() -> {
			if (brokenAt[0] < 0 && bag(v, Items.AMETHYST_SHARD) > 0) {
				helper.setBlock(STATION, Blocks.AIR);
				brokenAt[0] = helper.getTick();
			}
		});
		run(helper, v, new JewellerWork()); // (after: the block goes before the shift's tick that follows the fetch)
		helper.runAfterDelay(1800, () -> {
			helper.assertTrue(brokenAt[0] >= 0, "the Jeweller never fetched anything");
			LOG.info("QaLuxuryJobs broken at tick {}: chest {} shards {} copper, ground {} {}, rings {}, bag {}", brokenAt[0],
				chest(helper).countItem(Items.AMETHYST_SHARD), chest(helper).countItem(Items.COPPER_INGOT), ground(helper, Items.AMETHYST_SHARD),
				ground(helper, Items.COPPER_INGOT), anywhere(helper, v, ModItems.AMETHYST_RING), ModAttachments.BUILDER_BAG.getOrCreate(v).stacks());
			int rings = anywhere(helper, v, ModItems.AMETHYST_RING);
			int shards = chest(helper).countItem(Items.AMETHYST_SHARD) + ground(helper, Items.AMETHYST_SHARD);
			int copper = chest(helper).countItem(Items.COPPER_INGOT) + ground(helper, Items.COPPER_INGOT);
			helper.assertTrue(shards + 2 * rings == 2 && copper + 2 * rings == 2,
				"after the stonecutter broke (tick " + brokenAt[0] + "): " + shards + " shards and " + copper + " copper in the chest or on the ground, "
					+ rings + " rings; the bag holds " + ModAttachments.BUILDER_BAG.getOrCreate(v).stacks());
			helper.succeed();
		});
	}

	/**
	 * Config {@code tailors} switched off mid-job: "Tailors already hired stand idle". After the switch goes off the
	 * Tailor sews no more than the one in hand, takes nothing more from the chest, and what was fetched isn't lost.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaLuxOff"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaLuxOff")
	public void switchedOffMidJobSewsNoMore(GameTestHelper helper) {
		shop(helper, Blocks.LOOM);
		Leftovers.after(helper, () -> Tailors.ENABLED = true);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.WHITE_WOOL, 9));
		c.setItem(1, new ItemStack(Items.LEATHER, 6));
		c.setItem(2, new ItemStack(Items.STRING, 3));
		Villager v = worker(helper, ModVillagers.TAILOR, 1);
		run(helper, v, new TailorWork());
		int[] wool = {-1};
		helper.onEachTick(() -> {
			if (wool[0] < 0 && !ModAttachments.BUILDER_BAG.getOrCreate(v).isEmpty()) {
				Tailors.ENABLED = false;
				wool[0] = c.countItem(Items.WHITE_WOOL);
			}
		});
		helper.runAfterDelay(1400, () -> {
			helper.assertTrue(wool[0] >= 0, "the Tailor never fetched anything");
			int clothes = anywhere(helper, v, ModItems.WORK_CLOTHES);
			helper.assertTrue(clothes <= 1 + (9 - wool[0]) / 3, clothes + " Work Clothes after the switch went off with " + wool[0] + " wool in the chest");
			helper.assertTrue(c.countItem(Items.WHITE_WOOL) + bag(v, Items.WHITE_WOOL) + ground(helper, Items.WHITE_WOOL) >= wool[0],
				"wool taken after the switch went off: " + c.countItem(Items.WHITE_WOOL) + " in the chest, " + wool[0] + " when it went off");
			int woolAll = anywhere(helper, v, Items.WHITE_WOOL);
			helper.assertTrue(woolAll + 3 * clothes == 9, "wool lost: " + woolAll + " wool and " + clothes + " clothes from 9");
			helper.succeed();
		});
	}

	/**
	 * Every line above a luxury maker's head while they work (34.5's "fetching", "making", "putting away" with a count of
	 * one, then "the store has all it wants"), and the title with 1 made: in English, with no %s or raw key. (That a
	 * count of one reads as one is QaLuxuryJobsBugTests' check.)
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaLuxText"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaLuxText")
	public void theLinesAboveAMakersHeadReadRight(GameTestHelper helper) {
		shop(helper, Blocks.STONECUTTER);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(ModItems.AMETHYST_RING, 7));
		c.setItem(1, new ItemStack(Items.AMETHYST_SHARD, 2));
		c.setItem(2, new ItemStack(Items.COPPER_INGOT, 2));
		Villager v = worker(helper, ModVillagers.JEWELLER, 1);
		run(helper, v, new JewellerWork());
		Set<String> lines = new LinkedHashSet<>();
		Set<String> titles = new LinkedHashSet<>();
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(v, helper.getLevel().getGameTime());
			if (e != null) {
				lines.add(english(e.line()));
				titles.add(english(e.title()));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(c.countItem(ModItems.AMETHYST_RING) == 8, c.countItem(ModItems.AMETHYST_RING) + " rings, expected 8");
			helper.assertTrue(titles.contains("Jeweler · 1 made"), "titles seen: " + titles);
			helper.assertTrue(lines.stream().anyMatch(l -> l.startsWith("fetching")) && lines.stream().anyMatch(l -> l.startsWith("making")),
				"lines seen: " + lines);
			LOG.info("QaLuxuryJobs lines: {} titles: {}", lines, titles);
			for (String l : lines) {
				helper.assertTrue(!l.contains("%") && !l.contains("aliveworkplace.") && !l.contains("["), "a raw line: \"" + l + "\"");
			}
		});
	}
}
