package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.FurnitureStock;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureEquivalents;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * ROADMAP 37.13 with the pack's furniture mods: the Starter Cottage's two spruce chairs are planned as Handcrafted's (first
 * in {@code furnitureMods}); with only Handcrafted's chairs in the chest they stay, with only CobbleFurnies' they become
 * CobbleFurnies' (same spot, same facing), with both the first in the order is used, and with neither mod in the order
 * they're the vanilla stair stand-in whatever the chest holds. A builder given only CobbleFurnies' chair builds it, and
 * the material list names the chair once with both mods on hover.
 */
public class FurnitureAnyModCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final String BUILD_AREA = "aliveworkplace_compat:build_area";
	private static final ResourceLocation COTTAGE = AliveWorkplace.id("starter_cottage");
	private static final ResourceLocation NOOK = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat", "chair_nook");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
	}

	private static Item hcChair() {
		return item("handcrafted:spruce_chair");
	}

	private static Item cfChair() {
		return item("cobblefurnies:spruce_chair");
	}

	private static void chest(GameTestHelper helper, ItemStack... items) {
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.clearContent();
		for (int i = 0; i < items.length; i++) {
			chest.setItem(i, items[i].copy());
		}
	}

	/** A cottage site planned now (the order as it stands), restocked from the chest for {@code wanted}. */
	private static BuildSite restocked(GameTestHelper helper, Item wanted) {
		ServerLevel level = helper.getLevel();
		BuildSite site = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", COTTAGE,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 3)), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		FurnitureStock.restock(level, site, BuilderBag.of(List.of()), SupplyContainers.find(level, helper.absolutePos(BENCH), plan.bounds()), wanted);
		return site;
	}

	/** What the plan builds at each chair spot of the cottage, in spot order. */
	private static List<BlockState> chairs(BuildSite site, ServerLevel level) {
		BuildPlan plan = site.plan(level);
		Map<BlockPos, BlockState> steps = new java.util.HashMap<>();
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			plan.steps(stage).forEach(s -> steps.put(s.pos(), s.state()));
		}
		List<BlockState> out = new ArrayList<>();
		for (FurnitureSpot spot : BlueprintFurniture.of(COTTAGE)) {
			if (spot.kind() == FurnitureSpot.Kind.CHAIR) {
				out.add(steps.getOrDefault(site.placement().origin().offset(spot.pos()), Blocks.AIR.defaultBlockState()));
			}
		}
		return out;
	}

	private static List<String> ids(List<BlockState> states) {
		return states.stream().map(s -> BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString()).toList();
	}

	//$ gametest_batch HUGE_AREA '"furniture_any_mod"'
	@GameTest(template = HUGE_AREA, batch = "furniture_any_mod")
	public void onlyHandcraftedsChairInTheChest(GameTestHelper helper) {
		try {
			new WorkplaceConfig().apply();
			chest(helper, new ItemStack(hcChair(), 2));
			List<String> chairs = ids(chairs(restocked(helper, hcChair()), helper.getLevel()));
			helper.assertTrue(chairs.size() == 2 && chairs.stream().allMatch("handcrafted:spruce_chair"::equals), "the chairs: " + chairs);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	//$ gametest_batch HUGE_AREA '"furniture_any_mod"'
	@GameTest(template = HUGE_AREA, batch = "furniture_any_mod")
	public void onlyCobbleFurniesChairInTheChest(GameTestHelper helper) {
		try {
			new WorkplaceConfig().apply();
			chest(helper, new ItemStack(cfChair(), 2));
			ServerLevel level = helper.getLevel();
			BuildSite site = restocked(helper, hcChair());
			List<BlockState> chairs = chairs(site, level);
			helper.assertTrue(ids(chairs).equals(List.of("cobblefurnies:spruce_chair", "cobblefurnies:spruce_chair")), "the chairs: " + ids(chairs));
			// The same facing the Handcrafted chair had at each spot
			chest(helper);
			BuildSite plannedSite = restocked(helper, hcChair());
			List<BlockState> planned = chairs(plannedSite, level);
			for (int i = 0; i < chairs.size(); i++) {
				Direction was = planned.get(i).getValue(BlockStateProperties.HORIZONTAL_FACING);
				Direction now = chairs.get(i).getValue(BlockStateProperties.HORIZONTAL_FACING);
				helper.assertTrue(was == now, "chair " + i + " faces " + now + ", the Handcrafted one faced " + was);
			}
			// The rest of the cottage (its table, bed, lamps...) is as planned
			plannedSite.furniture().spots().forEach((i, piece) -> {
				if (piece.kind() != FurnitureSpot.Kind.CHAIR) {
					helper.assertTrue(piece.equals(site.furniture().spots().get(i)), piece.kind() + " changed: " + piece.mod() + " -> "
						+ (site.furniture().spots().get(i) == null ? null : site.furniture().spots().get(i).mod()));
				}
			});
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	//$ gametest_batch HUGE_AREA '"furniture_any_mod"'
	@GameTest(template = HUGE_AREA, batch = "furniture_any_mod")
	public void bothInTheChestTheFirstInTheOrderIsUsed(GameTestHelper helper) {
		try {
			ServerLevel level = helper.getLevel();
			new WorkplaceConfig().apply();
			chest(helper, new ItemStack(hcChair(), 2), new ItemStack(cfChair(), 2));
			List<String> chairs = ids(chairs(restocked(helper, hcChair()), level));
			helper.assertTrue(chairs.equals(List.of("handcrafted:spruce_chair", "handcrafted:spruce_chair")), "Handcrafted first: " + chairs);
			// One of each: each chair gets one, nothing counted twice
			chest(helper, new ItemStack(hcChair()), new ItemStack(cfChair()));
			List<String> mixed = ids(chairs(restocked(helper, hcChair()), level));
			helper.assertTrue(mixed.contains("handcrafted:spruce_chair") && mixed.contains("cobblefurnies:spruce_chair"), "one of each: " + mixed);
			// CobbleFurnies first in the order: its chairs, though Handcrafted's are there too
			Furnishings.MODS = List.of("cobblefurnies", "handcrafted");
			chest(helper, new ItemStack(hcChair(), 2), new ItemStack(cfChair(), 2));
			List<String> other = ids(chairs(restocked(helper, cfChair()), level));
			helper.assertTrue(other.equals(List.of("cobblefurnies:spruce_chair", "cobblefurnies:spruce_chair")), "CobbleFurnies first: " + other);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	//$ gametest_batch HUGE_AREA '"furniture_any_mod"'
	@GameTest(template = HUGE_AREA, batch = "furniture_any_mod")
	public void neitherGivesTheStandInOrTheWait(GameTestHelper helper) {
		try {
			ServerLevel level = helper.getLevel();
			// Neither mod in the order: the vanilla stair stand-in, whatever chairs the chest holds
			Furnishings.MODS = List.of("beautify");
			chest(helper, new ItemStack(hcChair(), 2), new ItemStack(cfChair(), 2));
			BuildSite site = restocked(helper, Items.SPRUCE_STAIRS);
			List<String> chairs = ids(chairs(site, level));
			helper.assertTrue(chairs.equals(List.of("minecraft:spruce_stairs", "minecraft:spruce_stairs")), "no chair mod in the order: " + chairs);
			// Neither chair in the chest: Handcrafted's stays planned (the builder waits for it, as before)
			new WorkplaceConfig().apply();
			chest(helper, new ItemStack(Items.SPRUCE_PLANKS, 64));
			List<String> waiting = ids(chairs(restocked(helper, hcChair()), level));
			helper.assertTrue(waiting.equals(List.of("handcrafted:spruce_chair", "handcrafted:spruce_chair")), "neither in the chest: " + waiting);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/** The material list names Handcrafted's chair "(any furniture mod)" with both mods' chairs on hover. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"furniture_any_mod"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "furniture_any_mod")
	public void theMaterialListNamesTheChairOnce(GameTestHelper helper) {
		try {
			new WorkplaceConfig().apply();
			helper.assertTrue(FurnitureEquivalents.options(cfChair()).equals(List.of(hcChair(), cfChair())), "options: " + FurnitureEquivalents.options(cfChair()));
			Component name = FurnitureEquivalents.name(hcChair());
			helper.assertTrue(name.getString().endsWith(" (any furniture mod)"), "the name: " + name.getString());
			HoverEvent hover = name.getStyle().getHoverEvent();
			String shown = hover == null ? "" : hover.getValue(HoverEvent.Action.SHOW_TEXT).getString();
			helper.assertTrue(shown.contains("(Handcrafted)") && shown.contains("(CobbleFurnies)") && !shown.contains("Cozy"),
				"the hover (Cozy Home isn't installed here): " + shown);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/**
	 * The player's way: a builder planned with Handcrafted's chair, the chest holding only CobbleFurnies' spruce chair and the
	 * floor's planks, builds the nook with CobbleFurnies' chair facing the way Handcrafted's would, and the finished
	 * building keeps it for upkeep.
	 */
	//$ gametest_ticks_batch BUILD_AREA '2400' '"furniture_any_mod_build"'
	@GameTest(template = BUILD_AREA, timeoutTicks = 2400, batch = "furniture_any_mod_build")
	public void aBuilderBuildsTheChairInTheChest(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		new WorkplaceConfig().apply();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		chest(helper, new ItemStack(Items.OAK_PLANKS, 9), new ItemStack(cfChair()));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(5, 2, 5)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, NOOK, placement);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no blueprint " + NOOK);
		FurnitureChoice.Placed planned = site.furniture().spots().get(0);
		helper.assertTrue(planned != null && planned.mod().equals("handcrafted"), "Handcrafted's chair should be planned: " + site.furniture());
		BlockPos chair = placement.origin().offset(1, 1, 1);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			BlockState there = level.getBlockState(chair);
			helper.assertTrue(BuiltInRegistries.BLOCK.getKey(there.getBlock()).toString().equals("cobblefurnies:spruce_chair"), "the chair is " + there);
			helper.assertTrue(there.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.SOUTH, "the chair faces " + there);
			helper.assertTrue(level.getBlockState(placement.origin()).is(Blocks.OAK_PLANKS), "the floor isn't built");
			FurnitureChoice kept = BuildSiteManager.get(level).furniture(placement);
			helper.assertTrue(kept.spots().size() == 1 && kept.spots().values().iterator().next().mod().equals("cobblefurnies"),
				"the finished building records " + kept.spots().values().stream().map(FurnitureChoice.Placed::mod).toList());
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(cfChair()) == 0, "the chair is still in the chest");
		});
	}
}
