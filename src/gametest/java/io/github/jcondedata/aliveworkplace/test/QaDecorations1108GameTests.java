package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks.State;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (qa-lane-b-1108-1238) of ROADMAP 26.14, "More decoration builds": at least 12 new small decorations, each in the
 * Blueprint Table library and the unlocks list. {@link MoreDecorationsGameTests} and {@link QaDecorations1104fGameTests}
 * already build all fourteen from their lists and check names, beauty and starting unlocks from the code's own lists;
 * here, from the player's side: the Table screen a player opens in a village lists every one by its id with its size, a
 * name in American English and a way to get it, and its material list holds nothing a survival player can't get; the
 * template itself holds no creative-only block; each has its "built" advancement; a found Hay Cart blueprint used on the
 * Table unlocks it there and the Table then hands it out for a Blank Blueprint; a Lily Pond survives a server restart
 * mid-build and finishes exactly from its list; Flower Planters short of their lanterns make the builder wait (nothing
 * else hung in their place, nothing skipped) and finish once the lanterns arrive.
 */
public class QaDecorations1108GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final List<BlockPos> CHESTS = List.of(new BlockPos(2, 2, 4), new BlockPos(4, 2, 4), new BlockPos(1, 2, 5), new BlockPos(4, 2, 1));
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 9);

	/** Items no survival player can get. */
	private static final Set<Item> UNOBTAINABLE = Set.of(Items.AIR, Items.BEDROCK, Items.BARRIER, Items.LIGHT, Items.STRUCTURE_VOID, Items.STRUCTURE_BLOCK,
		Items.COMMAND_BLOCK, Items.CHAIN_COMMAND_BLOCK, Items.REPEATING_COMMAND_BLOCK, Items.COMMAND_BLOCK_MINECART, Items.SPAWNER, Items.TRIAL_SPAWNER,
		Items.VAULT, Items.JIGSAW, Items.DEBUG_STICK, Items.KNOWLEDGE_BOOK, Items.PETRIFIED_OAK_SLAB, Items.BUDDING_AMETHYST, Items.REINFORCED_DEEPSLATE,
		Items.END_PORTAL_FRAME, Items.FARMLAND, Items.DIRT_PATH, Items.INFESTED_STONE, Items.INFESTED_COBBLESTONE, Items.INFESTED_STONE_BRICKS);
	/** Blocks that must never be in a survival build's template. */
	private static final Set<Block> CREATIVE_BLOCKS = Set.of(Blocks.BEDROCK, Blocks.BARRIER, Blocks.LIGHT, Blocks.STRUCTURE_BLOCK, Blocks.COMMAND_BLOCK,
		Blocks.CHAIN_COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK, Blocks.SPAWNER, Blocks.TRIAL_SPAWNER, Blocks.VAULT, Blocks.JIGSAW, Blocks.END_PORTAL_FRAME,
		Blocks.END_PORTAL, Blocks.NETHER_PORTAL, Blocks.END_GATEWAY, Blocks.REINFORCED_DEEPSLATE, Blocks.BUDDING_AMETHYST, Blocks.PETRIFIED_OAK_SLAB,
		Blocks.INFESTED_STONE, Blocks.INFESTED_COBBLESTONE, Blocks.INFESTED_STONE_BRICKS, Blocks.MOVING_PISTON);
	/** British spellings the owner asked to keep out of player text (2026-10-26). */
	private static final List<String> BRITISH = List.of("colour", "centre", "grey", "jewell", "defence", "armour", "cancelled", "theatre", "metre");

	// ------------------------------------------------------------------------------------- 1. the Table's listing

	/**
	 * The Table a player opens in a fresh village (its screen payload) lists all fourteen decorations of 26.14 by id, none
	 * as a "???": the Flower Planters unlocked, every other one locked with a hint that reads as words. Each has a size,
	 * blocks, a name (no raw key, in American English, all fourteen different), its materials as the Table shows them (none
	 * unobtainable), no creative-only block or villager job block in its template, and a "built" advancement.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaDeco1108Table"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaDeco1108Table")
	public void theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		QaBlueprintUnlocksGameTests.village(helper, QaBlueprintUnlocksGameTests.HALL, QaBlueprintUnlocksGameTests.TABLE);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(QaBlueprintUnlocksGameTests.HALL);
			BlockPos table = helper.absolutePos(QaBlueprintUnlocksGameTests.TABLE);
			List<StarterBlueprints.Entry> decorations = StarterBlueprints.MORE_DECORATIONS;
			helper.assertTrue(decorations.size() >= 12, "26.14 asks for at least 12 new decorations, there are " + decorations.size());
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, QaBlueprintUnlocksGameTests.TABLE);
			ServerPlayer player = c.player();
			c.sent();
			TableServer.open(player, table);
			List<TablePayloads.Open> opened = c.sent(TablePayloads.Open.class);
			helper.assertTrue(opened.size() == 1, "opening the Table sent " + opened.size() + " screens");
			Map<ResourceLocation, TablePayloads.Entry> listed = new LinkedHashMap<>();
			for (TablePayloads.Entry e : opened.get(0).entries()) {
				listed.put(e.id(), e);
			}
			Set<String> names = new HashSet<>();
			for (StarterBlueprints.Entry d : decorations) {
				ResourceLocation id = d.id();
				TablePayloads.Entry shown = listed.get(id);
				helper.assertTrue(shown != null, "the Table screen doesn't list " + id + " (it may be a ???)");
				helper.assertTrue(shown.sizeX() > 0 && shown.sizeY() > 0 && shown.sizeZ() > 0 && shown.blocks() > 0, id + " is listed with no size: " + shown);
				if (d == StarterBlueprints.FLOWER_PLANTERS) {
					helper.assertTrue(shown.unlocked(), "the Flower Planters aren't unlocked at a new village's Table: state " + shown.state());
				} else {
					helper.assertTrue(shown.state() == State.LOCKED.ordinal(), id + " is listed as state " + shown.state() + ", not locked");
					String hint = Component.translatable(shown.hint()).getString();
					helper.assertTrue(!shown.hint().isEmpty() && !hint.equals(shown.hint()) && !hint.contains("aliveworkplace."),
						id + " says how to get it as '" + hint + "' (key " + shown.hint() + ")");
				}
				String name = Blueprints.displayName(id).getString();
				helper.assertTrue(!name.isBlank() && !name.contains(".") && !name.contains("_") && Character.isUpperCase(name.charAt(0)),
					id + " is named '" + name + "'");
				for (String word : BRITISH) {
					helper.assertTrue(!name.toLowerCase().contains(word), id + "'s name '" + name + "' isn't American English (" + word + ")");
				}
				helper.assertTrue(names.add(name), "two decorations are both named '" + name + "'");

				Optional<TablePayloads.Details> details = TableServer.details(level, hall, id);
				helper.assertTrue(details.isPresent(), "the Table sends no details for the listed " + id);
				helper.assertTrue(!details.get().materials().isEmpty(), "the Table shows " + id + " with no materials");
				for (TablePayloads.Material m : details.get().materials()) {
					Item item = BuiltInRegistries.ITEM.get(m.item());
					helper.assertTrue(m.count() > 0, id + " lists " + m.item() + " x" + m.count());
					helper.assertTrue(!UNOBTAINABLE.contains(item), id + "'s materials on the Table ask for " + m.item() + ", which no survival player can get");
				}

				Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow(() -> new GameTestAssertException("no template for " + id));
				List<String> bad = new ArrayList<>();
				for (Blueprint.Entry b : blueprint.blocks()) {
					BlockState s = b.state();
					if (CREATIVE_BLOCKS.contains(s.getBlock()) || PoiTypes.forState(s).isPresent()) {
						bad.add(b.pos().toShortString() + "=" + BuiltInRegistries.BLOCK.getKey(s.getBlock()));
					}
				}
				helper.assertTrue(bad.isEmpty(), id + "'s template holds blocks a survival builder can't use or a job block: " + bad);

				ResourceLocation advancement = AliveWorkplace.id("built/" + id.getPath());
				helper.assertTrue(level.getServer().getAdvancements().get(advancement) != null, "no advancement " + advancement + " for building " + name);
				LOGGER.info("qa deco 1108 table {} '{}' state {} hint '{}' {} materials", id, name, shown.state(), shown.hint(), details.get().materials().size());
			}
			helper.succeed();
		});
	}

	/**
	 * The unlocks list's way in for a found blueprint: at a new village's Table the Hay Cart is locked, Get Blueprint for
	 * it is refused (naming it and how to get it, no Blank Blueprint used); a Hay Cart blueprint used on the Table (the
	 * player's own right-click) unlocks it with one "new blueprint" line, the screen that opens lists it unlocked, and the
	 * Table then hands out a Hay Cart blueprint for one Blank Blueprint.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaDeco1108Unlock"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaDeco1108Unlock")
	public void aFoundHayCartUsedOnTheTableUnlocksItAndTheTableHandsItOut(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		BlockPos hallRel = QaBlueprintUnlocksGameTests.HALL;
		BlockPos tableRel = QaBlueprintUnlocksGameTests.TABLE;
		QaBlueprintUnlocksGameTests.village(helper, hallRel, tableRel);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			StarterBlueprints.Entry cart = StarterBlueprints.HAY_CART;
			BlockPos table = helper.absolutePos(tableRel);
			helper.assertTrue(QaBlueprintUnlocksGameTests.state(helper, hallRel, cart) == State.LOCKED,
				"the Hay Cart isn't locked at a new village: " + QaBlueprintUnlocksGameTests.state(helper, hallRel, cart));
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, tableRel);
			ServerPlayer p = c.player();
			p.getInventory().setItem(10, new ItemStack(ModItems.BLANK_BLUEPRINT, 2)); // in the bag, not the hand the found blueprint goes in
			helper.assertFalse(TableServer.take(p, table, cart.id()), "the Table handed out the locked Hay Cart");
			helper.assertTrue(p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 2, "the refusal used a Blank Blueprint");
			Component locked = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.table.locked");
			helper.assertTrue(locked != null, "no 'locked' line: " + QaStageFourGameTests.keys(c.seen()));
			String said = locked.getString();
			helper.assertTrue(said.contains("Hay Cart") && said.contains("A market-day trader"), "the locked line reads: " + said);

			p.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(cart.id(), cart.size()));
			c.seen().clear();
			c.sent();
			QaBlueprintUnlocksGameTests.clickTable(helper, p, tableRel);
			helper.assertTrue(QaBlueprintUnlocksGameTests.state(helper, hallRel, cart) == State.UNLOCKED, "the found Hay Cart blueprint didn't unlock it");
			long lines = QaBlueprintUnlocksGameTests.linesOf(c.seen(), QaBlueprintUnlocksGameTests.NEW_LINE);
			helper.assertTrue(lines == 1, "'new blueprint' was said " + lines + " times: " + QaStageFourGameTests.keys(c.seen()));
			Component news = QaStageFourGameTests.find(c.seen(), QaBlueprintUnlocksGameTests.NEW_LINE);
			helper.assertTrue(news.getString().contains("Hay Cart"), "the new-blueprint line reads: " + news.getString());
			List<TablePayloads.Open> opened = c.sent(TablePayloads.Open.class);
			helper.assertTrue(opened.size() == 1, "the Table screen opened " + opened.size() + " times");
			TablePayloads.Entry shown = opened.get(0).entries().stream().filter(e -> e.id().equals(cart.id())).findFirst().orElse(null);
			helper.assertTrue(shown != null && shown.unlocked(), "the screen shows the Hay Cart as " + shown);

			p.containerMenu = p.inventoryMenu;
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			c.seen().clear();
			helper.assertTrue(TableServer.take(p, table, cart.id()), "the Table won't hand out the unlocked Hay Cart: " + QaStageFourGameTests.keys(c.seen())
				+ " blanks " + p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) + " player at " + p.blockPosition() + " table " + table);
			helper.assertTrue(p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 1, "taking it didn't cost one Blank Blueprint: "
				+ p.getInventory().countItem(ModItems.BLANK_BLUEPRINT));
			boolean got = false;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				got |= BlueprintItem.data(p.getInventory().getItem(i)).map(d -> d.structure().equals(cart.id())).orElse(false);
			}
			helper.assertTrue(got, "no Hay Cart blueprint in the inventory after Get Blueprint");
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------------------------------ 2. the builder

	private record Job(ServerLevel level, Villager[] builder, UUID site, BuildPlan plan, Map<Item, Integer> list, BlueprintData.Placement placement,
					   StarterBlueprints.Entry entry) {
		BuildSite live() {
			return BuildSiteManager.get(level).get(site);
		}
	}

	private static String where(GameTestHelper helper, Job j) {
		BuildSite s = j.live();
		return s == null ? "(finished)" : s.stage() + " " + s.status() + " cursor " + s.cursor() + " skipped " + s.skipped() + " missing " + s.missing()
			+ "; builder at " + helper.relativePos(j.builder()[0].blockPosition()) + " alive " + j.builder()[0].isAlive();
	}

	/** The rules a builder test needs (no free materials, no helpers, a short delay), put back after; nothing left over. */
	private static void rules(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
	}

	/** A bench with empty chests, the held blueprint's Material List, that list stocked except {@code without}, a builder on it. */
	private static Job start(GameTestHelper helper, StarterBlueprints.Entry entry, Item without) {
		rules(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		if (level.getPoiManager().getType(helper.absolutePos(BENCH)).isEmpty()) {
			PoiTypes.forState(level.getBlockState(helper.absolutePos(BENCH))).ifPresent(t -> level.getPoiManager().add(helper.absolutePos(BENCH), t));
		}
		for (BlockPos chest : CHESTS) {
			helper.setBlock(chest, Blocks.CHEST);
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BlueprintSupplies.Checklist checklist = BlueprintSupplies.checklist(level, new BlueprintData(entry.id(), Optional.of(entry.size()), Optional.of(placement)))
			.orElseThrow(() -> new GameTestAssertException(entry.id() + ": the held blueprint has no material list"));
		Map<Item, Integer> list = new LinkedHashMap<>();
		for (BlueprintSupplies.Line line : checklist.lines()) {
			list.merge(line.item(), line.toBring(), Integer::sum);
		}
		helper.assertTrue(without == null || list.containsKey(without), entry.id() + "'s list has no " + without + ": " + list);
		List<ItemStack> stacks = new ArrayList<>();
		list.forEach((item, n) -> {
			if (item == without) {
				return;
			}
			for (int left = n; left > 0; left -= item.getDefaultMaxStackSize()) {
				stacks.add(new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
			}
		});
		int i = 0;
		for (BlockPos chest : CHESTS) {
			Container c = helper.getBlockEntity(chest);
			for (int slot = 0; slot < c.getContainerSize() && i < stacks.size(); slot++) {
				c.setItem(slot, stacks.get(i++));
			}
		}
		helper.assertTrue(i == stacks.size(), "setup: more stacks than chests");
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, entry.id(), placement);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no blueprint " + entry.id());
		UUID id = site.id();
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(id) != null) {
				BuildSiteManager.get(level).remove(id);
			}
		});
		LOGGER.info("qa deco 1108 {} list {} (stocked without {})", entry.id(), list, without);
		return new Job(level, new Villager[]{villager}, id, plan, list, placement, entry);
	}

	/** A server restart: every site saved and loaded again, the builder saved and loaded as a fresh entity. */
	private static void restart(GameTestHelper helper, Job j) {
		ServerLevel level = j.level();
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		Villager old = j.builder()[0];
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
		level.addFreshEntity(loaded);
		j.builder()[0] = (Villager) loaded;
	}

	private static void assertAsDrawn(GameTestHelper helper, Job j, int skipped) {
		List<BlockPos> wrong = j.plan().unfinished(j.level());
		helper.assertTrue(wrong.isEmpty(), j.entry().id() + ": " + wrong.size() + " block(s) not as drawn, e.g. " + wrong.stream().limit(5)
			.map(p -> p.subtract(j.placement().origin()).toShortString() + "=" + j.level().getBlockState(p)).collect(Collectors.joining("; ")));
		helper.assertTrue(skipped == 0, j.entry().id() + ": the build skipped " + skipped + " block(s)");
	}

	/**
	 * A Lily Pond (water poured on a clay bed) from exactly its Material List, the server restarted once mid-build (three
	 * steps into its STRUCTURE stage, or the first tick after it if that stage is shorter): the site comes back in the same stage with
	 * the same blueprint, and the builder, a fresh entity, finishes it with every block (and its water) as drawn, nothing
	 * skipped. Stocked exactly, so anything lost in the restart would leave it waiting forever.
	 */
	//$ gametest_ticks_batch BIG '9000' '"qaDeco1108Restart"'
	@GameTest(template = BIG, timeoutTicks = 9000, batch = "qaDeco1108Restart")
	public void aLilyPondSurvivesARestartMidBuildAndFinishesAsDrawn(GameTestHelper helper) {
		Job j = start(helper, StarterBlueprints.LILY_POND, null);
		boolean[] restarted = {false};
		int[] skipped = {0};
		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			BuildSite s = j.live();
			if (s != null) {
				skipped[0] = s.skipped();
				boolean midStructure = s.stage() == BuildPlan.Stage.STRUCTURE && s.cursor() >= 3;
				if (!restarted[0] && (midStructure || s.stage().ordinal() > BuildPlan.Stage.STRUCTURE.ordinal()) && s.stage() != BuildPlan.Stage.DONE) {
					BuildPlan.Stage stage = s.stage();
					int cursor = s.cursor();
					restart(helper, j);
					restarted[0] = true;
					BuildSite back = j.live();
					helper.assertTrue(back != null, "the Lily Pond's site was lost in the restart");
					helper.assertTrue(back.stage() == stage && back.structure().equals(j.entry().id()),
						"after the restart the site is " + back.stage() + " " + back.structure() + ", was " + stage);
					helper.assertTrue(back.cursor() <= cursor, "the restart moved the cursor forward: " + back.cursor() + " > " + cursor);
					LOGGER.info("qa deco 1108 lily pond restarted in {} at cursor {}", stage, cursor);
				}
			}
			if (ticks[0] == 8800) {
				throw new GameTestAssertException("the Lily Pond didn't finish after the restart (restarted " + restarted[0] + "): " + where(helper, j));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0] && j.live() == null, "still building: " + where(helper, j));
			assertAsDrawn(helper, j, skipped[0]);
			LOGGER.info("qa deco 1108 lily pond finished after the restart in {} ticks", ticks[0]);
		});
	}

	/**
	 * Flower Planters from their Material List with no lanterns: the builder builds the rest and then waits for the
	 * lanterns (status waiting, the lanterns in what he's missing), never hanging anything else where a lantern goes and
	 * skipping nothing; the lanterns put in a chest, he finishes with every block as drawn.
	 */
	//$ gametest_ticks_batch BIG '9000' '"qaDeco1108Lanterns"'
	@GameTest(template = BIG, timeoutTicks = 9000, batch = "qaDeco1108Lanterns")
	public void flowerPlantersWithoutTheirLanternsWaitThenFinishWhenTheyArrive(GameTestHelper helper) {
		Job j = start(helper, StarterBlueprints.FLOWER_PLANTERS, Items.LANTERN);
		List<BlockPos> lanternSpots = new ArrayList<>();
		for (BuildPlan.Stage stage : BuildPlan.Stage.values()) {
			for (BuildPlan.Step step : j.plan().steps(stage)) {
				if (step.state().is(Blocks.LANTERN)) {
					lanternSpots.add(step.pos().immutable());
				}
			}
		}
		helper.assertTrue(!lanternSpots.isEmpty(), "the Flower Planters' plan places no lantern");
		int lanterns = j.list().get(Items.LANTERN);
		boolean[] waited = {false};
		int[] skipped = {0};
		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			for (BlockPos p : lanternSpots) {
				BlockState there = j.level().getBlockState(p);
				helper.assertTrue(there.isAir() || there.is(Blocks.LANTERN), "with no lanterns the builder put " + there + " where a lantern goes ("
					+ p.subtract(j.placement().origin()).toShortString() + ")");
			}
			BuildSite s = j.live();
			if (s != null) {
				skipped[0] = s.skipped();
				helper.assertTrue(s.skipped() == 0, "the builder skipped " + s.skipped() + " block(s) instead of waiting: " + where(helper, j));
				if (!waited[0] && s.status() == BuildSite.Status.WAITING_FOR_MATERIALS && s.missing().getOrDefault(Items.LANTERN, 0) > 0) {
					waited[0] = true;
					helper.assertTrue(s.missing().getOrDefault(Items.LANTERN, 0) == lanterns, "he's missing " + s.missing() + ", wanted " + lanterns + " lanterns");
					for (BlockPos p : lanternSpots) {
						helper.assertTrue(j.level().getBlockState(p).isAir(), "a lantern spot holds " + j.level().getBlockState(p) + " with no lanterns given");
					}
					Container chest = helper.getBlockEntity(CHESTS.get(3));
					chest.setItem(chest.getContainerSize() - 1, new ItemStack(Items.LANTERN, lanterns));
					LOGGER.info("qa deco 1108 planters waited for {} lanterns at tick {}: {}", lanterns, ticks[0], where(helper, j));
				}
			} else {
				helper.assertTrue(waited[0], "the Flower Planters finished without their lanterns (never waited for them)");
			}
			if (ticks[0] == 8800) {
				throw new GameTestAssertException("the Flower Planters didn't finish (waited " + waited[0] + "): " + where(helper, j));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(waited[0] && j.live() == null, "still building: " + where(helper, j));
			assertAsDrawn(helper, j, skipped[0]);
			LOGGER.info("qa deco 1108 planters finished in {} ticks", ticks[0]);
		});
	}
}
