package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * QA for ROADMAP 37.7 (Carved Wood and Rechiseled in the blueprint styles), written from the spec: "a style rule only
 * applies where its result is a real block" (a rule to a missing block falls through to the next rule or leaves the
 * vanilla block, and no block of the build is dropped); a rule under a switch that is off is passed over; each swap keeps
 * the block's facing and chest halves; a builder clearing his site leaves a Carved Wood chest and barrel standing with
 * what's in them; a saved Spruce site loads with its Carved Wood blocks.
 */
public class QaCarvedStylesCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private static String id(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElseThrow(() -> new GameTestAssertException("no block " + id));
	}

	private static BlueprintStyles.Rule rule(String from, String to, String requiresSwitch) {
		return new BlueprintStyles.Rule(Pattern.compile(from), to, null, requiresSwitch);
	}

	/**
	 * "A style rule only applies where its result is a real block": a rule whose result no mod registers is passed over,
	 * so the next rule that names a real block wins, or the vanilla block stays; a whole blueprint in such a style keeps
	 * every block (none turned to air or dropped), so nothing is skipped for a missing mod.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aRuleToAMissingBlockFallsThroughAndDropsNothing(GameTestHelper helper) {
		String tag = "qa_missing_" + UUID.randomUUID().toString().substring(0, 8);
		BlueprintStyles.Style fallsThrough = new BlueprintStyles.Style(tag + "_a", 90, ResourceLocation.parse("minecraft:paper"), List.of(
			rule("minecraft:chest", "qa_absent_mod:spruce_chest", null),
			rule("minecraft:chest", "carved_wood:oak_chest", null),
			rule("minecraft:(oak)_planks", "qa_absent_mod:$1_planks_fancy", null)));
		BlueprintStyles.Style onlyMissing = new BlueprintStyles.Style(tag + "_b", 91, ResourceLocation.parse("minecraft:paper"), List.of(
			rule("minecraft:(.*)", "qa_absent_mod:$1", null)));
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST);
		helper.assertTrue(id(BlueprintStyles.apply(fallsThrough, chest)).equals("carved_wood:oak_chest"),
			"a rule to a missing block wasn't passed over for the next: " + BlueprintStyles.apply(fallsThrough, chest));
		helper.assertTrue(BlueprintStyles.apply(fallsThrough, Blocks.OAK_PLANKS.defaultBlockState()).is(Blocks.OAK_PLANKS),
			"oak planks with only a missing block's rule became " + BlueprintStyles.apply(fallsThrough, Blocks.OAK_PLANKS.defaultBlockState()));
		helper.assertTrue(BlueprintStyles.apply(onlyMissing, chest) == chest, "a chest in a style of missing blocks isn't the vanilla chest");

		ServerLevel level = helper.getLevel();
		Blueprint base = BlueprintLibrary.get(level, AliveWorkplace.id("library")).orElseThrow(() -> new GameTestAssertException("no library"));
		Blueprint styled = BlueprintStyles.apply(onlyMissing, base, BlueprintStyles.styled(base.id(), onlyMissing.name()));
		helper.assertTrue(styled.blocks().size() == base.blocks().size(), "the styled Library has " + styled.blocks().size() + " blocks of " + base.blocks().size());
		List<String> changed = new ArrayList<>();
		for (int i = 0; i < base.blocks().size(); i++) {
			if (base.blocks().get(i).state() != styled.blocks().get(i).state()) {
				changed.add(base.blocks().get(i).pos().toShortString() + " " + base.blocks().get(i).state() + " -> " + styled.blocks().get(i).state());
			}
		}
		helper.assertTrue(changed.isEmpty(), changed.size() + " blocks changed by rules to missing blocks, e.g. " + changed.stream().limit(3).toList());
		helper.succeed();
	}

	/**
	 * 37.7's {@code "requires_switch"} on a rule: off, the rule is passed over as if its block didn't exist, so the next
	 * rule (or the vanilla block) applies; on, it applies; a switch name nobody knows counts as off. Turning the switch
	 * picks the other set of swaps at once (no stale cache).
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCarvedStylesSwitch"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCarvedStylesSwitch")
	public void aRuleUnderASwitchThatIsOffIsPassedOver(GameTestHelper helper) {
		boolean was = Furnishings.ENABLED;
		String tag = "qa_switch_" + UUID.randomUUID().toString().substring(0, 8);
		BlueprintStyles.Style style = new BlueprintStyles.Style(tag, 92, ResourceLocation.parse("minecraft:paper"), List.of(
			rule("minecraft:chest", "carved_wood:spruce_chest", "furnishedBuilds"),
			rule("minecraft:chest", "minecraft:trapped_chest", null),
			rule("minecraft:bookshelf", "carved_wood:spruce_bookshelf", "qaNoSuchSwitch")));
		BlockState chest = Blocks.CHEST.defaultBlockState();
		try {
			Furnishings.ENABLED = false;
			helper.assertTrue(id(BlueprintStyles.apply(style, chest)).equals("minecraft:trapped_chest"),
				"switch off: the chest became " + BlueprintStyles.apply(style, chest));
			Furnishings.ENABLED = true;
			helper.assertTrue(id(BlueprintStyles.apply(style, chest)).equals("carved_wood:spruce_chest"),
				"switch on: the chest became " + BlueprintStyles.apply(style, chest));
			Furnishings.ENABLED = false;
			helper.assertTrue(id(BlueprintStyles.apply(style, chest)).equals("minecraft:trapped_chest"),
				"switch off again: the chest became " + BlueprintStyles.apply(style, chest));
			helper.assertTrue(BlueprintStyles.apply(style, Blocks.BOOKSHELF.defaultBlockState()).is(Blocks.BOOKSHELF),
				"a rule under an unknown switch applied: " + BlueprintStyles.apply(style, Blocks.BOOKSHELF.defaultBlockState()));
		} finally {
			Furnishings.ENABLED = was;
		}
		helper.succeed();
	}

	/**
	 * A swapped block is the same block turned the same way: the Spruce style's Carved Wood chest keeps its facing and
	 * which half of a double chest it is, its ladder and lectern their facing, and over every blueprint the Spruce and
	 * Grand styles change, every property the styled block shares with the drawn one keeps its value (a double chest
	 * that came apart or a ladder turned off its wall would show in the build).
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCarvedStylesSwitch"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCarvedStylesSwitch")
	public void everySwapKeepsTheBlocksFacingAndChestHalves(GameTestHelper helper) {
		boolean was = Furnishings.ENABLED;
		List<String> wrong = new ArrayList<>();
		try {
			Furnishings.ENABLED = true;
			BlueprintStyles.Style spruce = BlueprintStyles.get("spruce").orElseThrow(() -> new GameTestAssertException("no spruce style"));
			BlueprintStyles.Style grand = BlueprintStyles.get("grand").orElseThrow(() -> new GameTestAssertException("no grand style"));
			for (Direction d : Direction.Plane.HORIZONTAL) {
				for (ChestType type : ChestType.values()) {
					BlockState from = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, d).setValue(ChestBlock.TYPE, type);
					BlockState to = BlueprintStyles.apply(spruce, from);
					if (!id(to).equals("carved_wood:spruce_chest") || !to.hasProperty(ChestBlock.TYPE) || to.getValue(ChestBlock.TYPE) != type
							|| !to.hasProperty(ChestBlock.FACING) || to.getValue(ChestBlock.FACING) != d) {
						wrong.add(from + " -> " + to);
					}
				}
				BlockState ladder = BlueprintStyles.apply(spruce, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, d));
				if (!id(ladder).equals("carved_wood:spruce_ladder") || !ladder.hasProperty(LadderBlock.FACING) || ladder.getValue(LadderBlock.FACING) != d) {
					wrong.add("ladder " + d + " -> " + ladder);
				}
				BlockState lectern = BlueprintStyles.apply(spruce, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, d));
				if (!id(lectern).equals("carved_wood:spruce_lectern") || !lectern.hasProperty(HorizontalDirectionalBlock.FACING)
						|| lectern.getValue(HorizontalDirectionalBlock.FACING) != d) {
					wrong.add("lectern " + d + " -> " + lectern);
				}
			}
			ServerLevel level = helper.getLevel();
			int checked = 0;
			for (ResourceLocation baseId : BlueprintLibrary.list(level.getServer(), false)) {
				if (BlueprintStyles.parse(baseId).isPresent()) {
					continue;
				}
				Blueprint base = BlueprintLibrary.get(level, baseId).orElse(null);
				if (base == null) {
					continue;
				}
				for (BlueprintStyles.Style style : List.of(spruce, grand)) {
					if (!BlueprintStyles.changes(style, base)) {
						continue;
					}
					checked++;
					Blueprint styled = BlueprintStyles.apply(style, base, BlueprintStyles.styled(baseId, style.name()));
					if (styled.blocks().size() != base.blocks().size()) {
						wrong.add(baseId + " in " + style.name() + ": " + styled.blocks().size() + " blocks of " + base.blocks().size());
						continue;
					}
					for (int i = 0; i < base.blocks().size() && wrong.size() < 20; i++) {
						BlockState from = base.blocks().get(i).state();
						BlockState to = styled.blocks().get(i).state();
						if (!from.isAir() && to.isAir()) {
							wrong.add(baseId + " in " + style.name() + " " + base.blocks().get(i).pos().toShortString() + ": " + from + " became air");
						}
						for (Property<?> p : from.getProperties()) {
							for (Property<?> q : to.getProperties()) {
								String want = valueName(from, p);
								if (q.getName().equals(p.getName()) && !valueName(to, q).equals(want) && q.getValue(want).isPresent()) {
									wrong.add(baseId + " in " + style.name() + " " + base.blocks().get(i).pos().toShortString() + ": " + from + " -> " + to);
								}
							}
						}
					}
				}
			}
			helper.assertTrue(checked >= 2, "only " + checked + " blueprints are changed by the Spruce and Grand styles");
		} finally {
			Furnishings.ENABLED = was;
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong.stream().limit(8).toList());
		helper.succeed();
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	/**
	 * "Builders never break containers": a Carved Wood chest and barrel stand where the blueprint wants air, each holding
	 * something. The builder clears and builds his site around them; both are still there afterwards with all they held.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaCarvedClearing"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaCarvedClearing")
	public void aBuilderClearingLeavesACarvedWoodChestAndBarrelFull(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Map<BlockPos, BlockState> design = new HashMap<>();
		design.put(new BlockPos(0, 0, 0), Blocks.STONE_BRICKS.defaultBlockState());
		design.put(new BlockPos(1, 0, 0), Blocks.AIR.defaultBlockState());
		design.put(new BlockPos(2, 0, 0), Blocks.AIR.defaultBlockState());
		design.put(new BlockPos(3, 0, 0), Blocks.STONE_BRICKS.defaultBlockState());
		ResourceLocation id = CompatGameTests.blueprintFrom(helper, "qa_carved_clear", design, Map.<BlockPos, CompoundTag>of());
		BlockPos chestRel = CompatGameTests.ORIGIN.offset(1, 0, 0);
		BlockPos barrelRel = CompatGameTests.ORIGIN.offset(2, 0, 0);
		helper.setBlock(chestRel, block("carved_wood:spruce_chest"));
		helper.setBlock(barrelRel, block("carved_wood:oak_barrel"));
		Container chest = helper.getBlockEntity(chestRel);
		Container barrel = helper.getBlockEntity(barrelRel);
		chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
		barrel.setItem(0, new ItemStack(Items.EMERALD, 5));
		CompatGameTests.Setup s = CompatGameTests.setup(helper, id, new ItemStack(Items.STONE_BRICKS, 2));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null,
				"still building: " + s.site().stage() + " " + s.site().status() + " missing " + s.site().missing());
			helper.assertTrue(helper.getBlockState(CompatGameTests.ORIGIN).is(Blocks.STONE_BRICKS)
				&& helper.getBlockState(CompatGameTests.ORIGIN.offset(3, 0, 0)).is(Blocks.STONE_BRICKS), "the build's stone bricks aren't placed");
			helper.assertTrue(id(helper.getBlockState(chestRel)).equals("carved_wood:spruce_chest"), "the Carved Wood chest is now " + helper.getBlockState(chestRel));
			helper.assertTrue(id(helper.getBlockState(barrelRel)).equals("carved_wood:oak_barrel"), "the Carved Wood barrel is now " + helper.getBlockState(barrelRel));
			Container c = helper.getBlockEntity(chestRel);
			Container b = helper.getBlockEntity(barrelRel);
			helper.assertTrue(count(c, Items.DIAMOND) == 7, "the Carved Wood chest holds " + count(c, Items.DIAMOND) + " of 7 diamonds");
			helper.assertTrue(count(b, Items.EMERALD) == 5, "the Carved Wood barrel holds " + count(b, Items.EMERALD) + " of 5 emeralds");
		});
	}

	private static int count(Container c, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			n += c.getItem(i).is(item) ? c.getItem(i).getCount() : 0;
		}
		return n;
	}

	/**
	 * Saves: a Library site in the Spruce style, saved and loaded (as on a server restart), plans the same steps with
	 * the same Carved Wood shelves and lectern.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCarvedStylesSwitch"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCarvedStylesSwitch")
	public void aSavedSpruceSiteLoadsWithItsCarvedWoodBlocks(GameTestHelper helper) {
		boolean was = Furnishings.ENABLED;
		try {
			Furnishings.ENABLED = true;
			ServerLevel level = helper.getLevel();
			ResourceLocation id = BlueprintStyles.styled(AliveWorkplace.id("library"), "spruce");
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)),
				Rotation.NONE, Mirror.NONE);
			BuildSite site = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", id, placement);
			BuildPlan plan = site.plan(level);
			helper.assertTrue(plan != null, "no plan for " + id);
			List<String> before = steps(plan);
			helper.assertTrue(before.contains("carved_wood:spruce_bookshelf") && before.contains("carved_wood:spruce_lectern"),
				"the spruce Library's plan has no Carved Wood shelves or lectern");
			BuildSite loaded = BuildSite.load(site.save());
			BuildPlan again = loaded.plan(level);
			helper.assertTrue(again != null, "the loaded spruce site has no plan");
			List<String> after = steps(again);
			helper.assertTrue(after.equals(before), "the loaded site plans " + after.size() + " steps of " + before.size()
				+ (after.size() == before.size() ? ", in other blocks" : ""));
		} finally {
			Furnishings.ENABLED = was;
		}
		helper.succeed();
	}

	private static List<String> steps(BuildPlan plan) {
		List<String> out = new ArrayList<>();
		Stream.of(BuildPlan.Stage.values()).flatMap(s -> plan.steps(s).stream()).forEach(s -> out.add(id(s.state())));
		return out;
	}
}
