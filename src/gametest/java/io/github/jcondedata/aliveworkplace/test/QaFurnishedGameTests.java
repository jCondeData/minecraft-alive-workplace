package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1024-0353) for furnished homes and the furnishing table (ROADMAP 37.2-37.5, 37.12, 37.13), written from the
 * spec: a test "mod" ({@code aliveworkplace_test_pieces}: its chair an upturned stair, its table a block of planks) stands
 * in for a furniture mod. A rotated and mirrored build gets its pieces where its spots are and facing the spot's way; a
 * piece a player breaks after the build is put back as the piece, after a save and reload; a container a player left on a
 * spot is never broken; the Blueprint's material list (Book and Quill in the other hand, a real right-click) lists the
 * pieces this server builds, and sneaking doesn't write one; the switch off gives the vanilla list; the modded library
 * with none of its mods here, even for a site saved as modded, builds vanilla; the furniture text reads in English.
 */
public class QaFurnishedGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	static final ResourceLocation NOOK = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "chair_nook");
	static final Vec3i NOOK_SIZE = new Vec3i(3, 2, 3);
	static final ResourceLocation COTTAGE = AliveWorkplace.id("starter_cottage");
	static final String PIECES = "aliveworkplace_test_pieces";
	/** The nook's chair spot, in the blueprint. */
	static final BlockPos CHAIR = new BlockPos(1, 1, 1);
	static final BlockPos BENCH = new BlockPos(2, 2, 2);
	static final BlockPos CHEST = new BlockPos(2, 2, 4);

	static void resetAfter(GameTestHelper helper) {
		boolean enabled = Upkeep.ENABLED;
		boolean ask = Upkeep.ASK;
		Leftovers.after(helper, () -> {
			new WorkplaceConfig().apply();
			Upkeep.ENABLED = enabled;
			Upkeep.ASK = ask;
		});
	}

	/** Right-clicks the air the way the client does: the main hand first, the off hand only when the main hand did nothing. */
	static void rightClickAir(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.isEmpty()) {
				continue;
			}
			InteractionResult result = player.gameMode.useItem(player, player.serverLevel(), stack, hand);
			if (result.consumesAction()) {
				return;
			}
		}
	}

	/** Right-clicks the top of {@code ground} (absolute) with the main hand, as a player placing a blueprint does. */
	static void rightClickGround(ServerPlayer player, BlockPos ground) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false);
		player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
	}

	static String page(ItemStack book, int i) {
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (content == null || content.pages().size() <= i) {
			return null;
		}
		return content.pages().get(i).raw().getString();
	}

	static String allPages(ItemStack book) {
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (content == null) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		content.pages().forEach(p -> out.append(p.raw().getString()).append(" | "));
		return out.toString();
	}

	static Map<BlockPos, BlockState> stepStates(BuildPlan plan) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			plan.steps(stage).forEach(s -> out.put(s.pos(), s.state()));
		}
		return out;
	}

	static BuildSite cottageSite(GameTestHelper helper) {
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)),
			Rotation.NONE, Mirror.NONE);
		return new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", COTTAGE, placement);
	}

	/** A bench, its builder, and a chest by it holding {@code stock}. */
	static Villager builderWithChest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		return builder;
	}

	static void stock(GameTestHelper helper, BuildPlan plan) {
		Container chest = helper.getBlockEntity(CHEST);
		chest.clearContent();
		int slot = 0;
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			chest.setItem(slot++, new ItemStack(e.getKey(), e.getValue()));
		}
	}

	/**
	 * A nook placed turned a quarter and mirrored is built by its builder with the table's chair, at the spot as the turned
	 * build has it and facing the way the spot faces in it. A player then breaks the chair: the finished building keeps its
	 * piece through a save and reload of the sites, and the builder's repair puts back the upturned chair, not the vanilla one.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaFurnishedTurned"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaFurnishedTurned")
	public void aTurnedMirroredNookIsBuiltAndRepairedWithItsPiece(GameTestHelper helper) {
		Leftovers.clear(helper);
		resetAfter(helper);
		ServerLevel level = helper.getLevel();
		Furnishings.MODS = List.of(PIECES);
		Villager builder = builderWithChest(helper);
		Rotation rotation = Rotation.CLOCKWISE_90;
		Mirror mirror = Mirror.FRONT_BACK;
		BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), NOOK_SIZE, helper.absolutePos(new BlockPos(9, 2, 9)),
			rotation, mirror);
		BuildSite site = Builders.start(level, builder, null, NOOK, placement);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no plan for the nook");
		helper.assertTrue(site.furniture().spots().values().stream().allMatch(p -> p.mod().equals(PIECES)) && !site.furniture().spots().isEmpty(),
			"the turned nook didn't take the table's chair: " + site.furniture());
		stock(helper, plan);
		FurnitureSpot spot = BlueprintFurniture.of(NOOK).get(0);
		BlockPos chairAt = placement.origin().offset(StructureTemplate.transform(CHAIR, mirror, rotation, BlockPos.ZERO));
		Direction facing = rotation.rotate(mirror.mirror(spot.facing().getOpposite()));
		String[] failure = {null};
		boolean[] checked = {false};
		helper.succeedWhen(() -> {
			if (!checked[0]) {
				helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
					"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
				checked[0] = true;
				failure[0] = afterTheBuild(helper, level, builder, placement, chairAt, facing, site.skipped());
			}
			if (failure[0] != null) {
				throw new GameTestAssertException(failure[0]);
			}
		});
	}

	/** The second half of {@link #aTurnedMirroredNookIsBuiltAndRepairedWithItsPiece}; returns what's wrong, or null. */
	private static String afterTheBuild(GameTestHelper helper, ServerLevel level, Villager builder, BlueprintData.Placement placement,
			BlockPos chairAt, Direction facing, int skipped) {
		BlockState built = level.getBlockState(chairAt);
		if (skipped != 0) {
			return skipped + " steps skipped";
		}
		if (!built.is(Blocks.SPRUCE_STAIRS) || built.getValue(StairBlock.HALF) != Half.TOP || built.getValue(StairBlock.FACING) != facing) {
			return "the chair of the turned, mirrored nook is " + built + ", wanted an upturned spruce stair facing " + facing;
		}
		BuildSiteManager manager = BuildSiteManager.get(level);
		if (manager.furniture(placement).spots().isEmpty()) {
			return "the finished nook forgot its furniture: " + manager.furniture(placement);
		}
		// A player breaks the chair; the server saves and loads the sites
		level.destroyBlock(chairAt, false);
		BuildSiteManager reloaded = BuildSiteManager.load(manager.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		FurnitureChoice kept = reloaded.furniture(placement);
		if (kept.spots().isEmpty() || !kept.spots().values().iterator().next().mod().equals(PIECES)) {
			return "after a reload the finished nook's furniture is " + kept;
		}
		// The table's mod is no longer first (none in the order): the repair still puts back what was built
		Furnishings.MODS = List.of();
		Upkeep.ENABLED = true;
		Upkeep.ASK = false;
		BuildSite repair = Upkeep.look(level, builder);
		if (repair == null) {
			return "no repair started for the broken chair";
		}
		BuildPlan plan = repair.plan(level);
		BlockState back = stepStates(plan).get(chairAt);
		if (back == null || !back.is(Blocks.SPRUCE_STAIRS) || back.getValue(StairBlock.HALF) != Half.TOP) {
			return "the repair puts back " + back + " at the chair, not the upturned stair it was built with";
		}
		return null;
	}

	/**
	 * A player left a barrel (with a diamond in it) on the nook's chair spot: the builder finishes the nook around it, never
	 * breaking the barrel or taking what's in it, and the floor is all built.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaFurnishedBarrel"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaFurnishedBarrel")
	public void aBarrelOnTheChairSpotIsLeftWhereItIs(GameTestHelper helper) {
		Leftovers.clear(helper);
		resetAfter(helper);
		ServerLevel level = helper.getLevel();
		Furnishings.MODS = List.of(PIECES);
		Villager builder = builderWithChest(helper);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 9)),
			Rotation.NONE, Mirror.NONE);
		BlockPos chairAt = placement.origin().offset(CHAIR);
		level.setBlockAndUpdate(chairAt, Blocks.BARREL.defaultBlockState());
		((Container) level.getBlockEntity(chairAt)).setItem(4, new ItemStack(Items.DIAMOND));
		BuildSite site = Builders.start(level, builder, null, NOOK, placement);
		BuildPlan plan = site.plan(level);
		stock(helper, plan);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			helper.assertTrue(level.getBlockState(chairAt).is(Blocks.BARREL), "the barrel on the chair spot became " + level.getBlockState(chairAt));
			Container barrel = (Container) level.getBlockEntity(chairAt);
			helper.assertTrue(barrel != null && barrel.getItem(4).is(Items.DIAMOND), "the barrel's diamond is gone");
			for (BlockPos floor : BlockPos.betweenClosed(placement.origin(), placement.origin().offset(2, 0, 2))) {
				helper.assertTrue(level.getBlockState(floor).is(Blocks.OAK_PLANKS), "the nook's floor at " + floor.subtract(placement.origin()).toShortString()
					+ " is " + level.getBlockState(floor));
			}
		});
	}

	/**
	 * The Blueprint's material list through a player's real right-click: blueprint in the main hand and a Book and Quill
	 * in the off hand writes the list into the off hand, and it asks for the table's chair (Spruce Stairs, still to bring,
	 * the chest holding the floor). Sneaking with the same hands writes nothing (it clears the placement instead).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFurnishedList"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFurnishedList")
	public void theListIsWrittenFromTheOffHandBookAndNotWhenSneaking(GameTestHelper helper) {
		resetAfter(helper);
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 9));
		Furnishings.MODS = List.of(PIECES);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayerInLevel();
		player.moveTo(helper.absoluteVec(new Vec3(9.5, 2, 4.5)), 0, 0);
		player.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(NOOK, NOOK_SIZE));
		rightClickGround(player, helper.absolutePos(new BlockPos(9, 1, 9)));
		Optional<BlueprintData> placed = BlueprintItem.data(player.getMainHandItem());
		helper.assertTrue(placed.isPresent() && placed.get().placement().isPresent(), "the blueprint wasn't placed by the click");
		helper.runAfterDelay(2, () -> { // the table becomes a point of interest a moment after it's placed
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WRITABLE_BOOK));
			player.setShiftKeyDown(true);
			rightClickAir(player);
			player.setShiftKeyDown(false);
			helper.assertTrue(player.getOffhandItem().is(Items.WRITABLE_BOOK), "a sneak-click wrote a list: " + player.getOffhandItem());
			helper.assertTrue(BlueprintItem.data(player.getMainHandItem()).map(d -> d.placement().isEmpty()).orElse(false),
				"the sneak-click didn't clear the placement");
			player.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(NOOK, NOOK_SIZE));
			player.getMainHandItem().set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT, placed.get());
			rightClickAir(player);
			ItemStack book = player.getOffhandItem();
			helper.assertTrue(book.is(Items.WRITTEN_BOOK), "no list written into the off hand: " + book);
			String first = page(book, 0);
			String all = allPages(book);
			helper.assertTrue(first != null && first.contains("Still to bring"), "the list's first page: " + first);
			helper.assertTrue(all.contains("☐ 1 × Spruce Stairs"), "the table's chair isn't asked for: " + all);
			helper.assertTrue(all.contains("☑ Oak Planks") || all.contains("☑ 9"), "the floor in the chest isn't ticked: " + all);
			helper.assertTrue(!all.contains("%s") && !all.contains("book.aliveworkplace"), "raw text in the list: " + all);
			helper.assertTrue(player.getMainHandItem().getItem() instanceof BlueprintItem, "the blueprint left the main hand");
			helper.succeed();
		});
	}

	/**
	 * {@code furnishedBuilds} off with the table's mod first in the order: the material list of a cottage is the vanilla
	 * build's (as with no mod in the order), while on it differs (the table's planks); the plan likewise.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaFurnishedSwitch"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaFurnishedSwitch")
	public void switchedOffTheCottageListIsTheVanillaList(GameTestHelper helper) {
		resetAfter(helper);
		ServerLevel level = helper.getLevel();
		BlueprintData data = BlueprintItem.data(BlueprintItem.create(COTTAGE, null)).orElseThrow();
		Furnishings.MODS = List.of();
		List<BlueprintSupplies.Line> vanilla = BlueprintSupplies.checklist(level, data).orElseThrow().lines();
		Map<BlockPos, BlockState> vanillaPlan = stepStates(cottageSite(helper).plan(level));
		Furnishings.MODS = List.of(PIECES);
		List<BlueprintSupplies.Line> furnished = BlueprintSupplies.checklist(level, data).orElseThrow().lines();
		Map<BlockPos, BlockState> furnishedPlan = stepStates(cottageSite(helper).plan(level));
		helper.assertTrue(!furnished.equals(vanilla), "the list with the table's mod is the vanilla list: " + furnished);
		helper.assertTrue(!furnishedPlan.equals(vanillaPlan), "the plan with the table's mod is the vanilla plan");
		WorkplaceConfig.parse("{\"furnishedBuilds\": false, \"furnitureMods\": [\"" + PIECES + "\"]}").apply();
		List<BlueprintSupplies.Line> off = BlueprintSupplies.checklist(level, data).orElseThrow().lines();
		helper.assertTrue(off.equals(vanilla), "switched off, the list isn't the vanilla list: " + off + " vs " + vanilla);
		BuildSite offSite = cottageSite(helper);
		helper.assertTrue(stepStates(offSite.plan(level)).equals(vanillaPlan), "switched off, the plan isn't the vanilla plan");
		helper.assertTrue(offSite.furniture().isVanilla(), "switched off, the site chose " + offSite.furniture());
		helper.succeed();
	}

	/**
	 * The modded library switched on with none of its mods here: every home and workshop with a copy is built from the
	 * vanilla library, and a site saved as modded on a server that had the mods (then lost them) loads and plans the
	 * vanilla build, never an empty or broken one.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaFurnishedLibrary"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaFurnishedLibrary")
	public void theModdedLibraryWithoutItsModsBuildsVanilla(GameTestHelper helper) {
		resetAfter(helper);
		ServerLevel level = helper.getLevel();
		WorkplaceConfig.parse("{\"moddedLibrary\": true, \"furnishedBuilds\": true}").apply();
		List<ResourceLocation> builds = ModdedLibrary.builds();
		helper.assertTrue(!builds.isEmpty(), "the modded library has no builds");
		List<String> problems = new ArrayList<>();
		for (ResourceLocation id : builds.subList(0, Math.min(6, builds.size()))) {
			if (ModdedLibrary.use(id) || FurnitureChoice.forNewBuild(id).modded()) {
				problems.add(id + " built from the modded library without its mods");
			}
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)),
				Rotation.NONE, Mirror.NONE);
			BuildSite fresh = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", id, placement);
			BuildPlan vanilla = fresh.plan(level);
			if (vanilla == null) {
				problems.add(id + " has no plan");
				continue;
			}
			BuildSite saved = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", id, placement);
			saved.setFurniture(FurnitureChoice.VANILLA.withLibrary(true));
			BuildSite loaded = BuildSite.load(saved.save());
			BuildPlan plan = loaded == null ? null : loaded.plan(level);
			if (plan == null) {
				problems.add(id + ": a site saved as modded has no plan after loading");
			} else if (!stepStates(plan).equals(stepStates(vanilla))) {
				problems.add(id + ": a site saved as modded doesn't plan the vanilla build");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Every furniture sentence a player reads is in English: the mods' names, the material list's "any mod" line and its
	 * hover, the settings' names and tooltips; none is a raw key or keeps a {@code %s} once its arguments are given.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theFurnitureTextReadsInEnglish(GameTestHelper helper) {
		Language english = Language.getInstance();
		List<String> problems = new ArrayList<>();
		String[] keys = {"furniture_mod.aliveworkplace.handcrafted", "furniture_mod.aliveworkplace.cozyhome", "furniture_mod.aliveworkplace.cobblefurnies",
			"furniture_mod.aliveworkplace.beautify", "book.aliveworkplace.material_list.mod_options", "aliveworkplace.config.furnishedBuilds",
			"aliveworkplace.config.furnishedBuilds.tooltip", "aliveworkplace.config.moddedLibrary", "aliveworkplace.config.moddedLibrary.tooltip",
			"entity.aliveworkplace.furniture_seat"};
		for (String key : keys) {
			if (!english.has(key)) {
				problems.add("no text for " + key);
			}
		}
		Map<String, Component> rendered = Map.of(
			"any_mod", Component.translatable("book.aliveworkplace.material_list.any_mod", Component.translatable("block.minecraft.spruce_stairs")),
			"mod_option", Component.translatable("book.aliveworkplace.material_list.mod_option", Component.translatable("block.minecraft.spruce_stairs"),
				Component.translatable("furniture_mod.aliveworkplace.handcrafted")));
		rendered.forEach((what, text) -> {
			String s = text.getString();
			if (s.contains("%") || s.contains("aliveworkplace.") || s.contains("block.minecraft")) {
				problems.add(what + " reads '" + s + "'");
			}
		});
		helper.assertTrue(rendered.get("mod_option").getString().equals("Spruce Stairs (Handcrafted)"), "a mod option reads " + rendered.get("mod_option").getString());
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
