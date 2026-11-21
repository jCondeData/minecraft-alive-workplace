package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1115-2137), past B1288's fix, written from crafter.md ("The line names what the chest is short of ... It goes
 * further down only when that uses something that's there") and 46.2 (the Shepherd dyes white wool, or wool he spins from
 * string): a builder short of dyed wool reads what a player gets the wool from (white wool or string) and the dye, never
 * the same wool he asked for, another color's wool or a second color's dye. In the base game, 1.21's "dye any wool" recipe
 * takes any wool but its own color, so each color goes down into another: blue wool from nothing names
 * {lapis_lazuli=2, ink_sac=1, black_dye=1, blue_wool=2}.
 */
public class QaDyedWoolLine1115bBugTests implements FreshPlanning {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);

	/** Every dyed wool from nothing, and from just its own dye: nothing named is dyed wool (its own color or another's). */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaDyedWool1115bFromNothing"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaDyedWool1115bFromNothing")
	public void dyedWoolFromNothingNamesNoDyedWool(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		for (Item wool : BuiltInRegistries.ITEM) {
			if (!wool.builtInRegistryHolder().is(ItemTags.WOOL) || wool == Items.WHITE_WOOL
				|| !"minecraft".equals(BuiltInRegistries.ITEM.getKey(wool).getNamespace())) {
				continue;
			}
			for (Crafting.Kind kind : new Crafting.Kind[] {Crafting.Kind.CRAFTING, Crafting.Kind.WORKSHOP}) {
				Map<Item, Integer> lack = Crafting.lacking(level, kind, wool, 1, Map.of());
				if (lack.keySet().stream().anyMatch(i -> i != Items.WHITE_WOOL && i.builtInRegistryHolder().is(ItemTags.WOOL))) {
					wrong.add(kind + " " + BuiltInRegistries.ITEM.getKey(wool).getPath() + " from nothing lacks " + names(lack));
				}
			}
		}
		Map<Item, Integer> blue = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BLUE_WOOL, 2, Map.of(Items.BLUE_DYE, 2L));
		if (blue.keySet().stream().anyMatch(i -> i != Items.WHITE_WOOL && i.builtInRegistryHolder().is(ItemTags.WOOL))
			|| blue.containsKey(Items.BLACK_DYE) || blue.containsKey(Items.INK_SAC) || blue.containsKey(Items.LAPIS_LAZULI)) {
			wrong.add("2 blue wool from 2 blue dye lacks " + names(blue) + ", expected only the wool's makings (white wool or string)");
		}
		helper.assertTrue(wrong.isEmpty(), "dyed wool's lines: " + wrong);
		helper.succeed();
	}

	/**
	 * Through the player's entry point: a builder short of 2 blue wool with 2 blue dye in his chest, a Shepherd at his loom:
	 * the line names the white wool or string the dye goes on, never Blue Wool (or another color's wool or dye) to make Blue
	 * Wool; nothing is taken.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaDyedWool1115bLine"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaDyedWool1115bLine")
	public void aBuilderShortOfBlueWoolWithItsDyeReadsTheWoolsMakings(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(ROW, Blocks.BLUE_WOOL);
		helper.setBlock(ROW.east(), Blocks.BLUE_WOOL);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qadyedwool_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(2, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(ROW, Blocks.AIR);
		helper.setBlock(ROW.east(), Blocks.AIR);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.BLUE_DYE, 2));
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		if (site == null || site.plan(level) == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		helper.setBlock(STATION, Blocks.LOOM);
		helper.setBlock(STATION.east(), Blocks.CHEST);
		Villager shepherd = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(level, shepherd, helper.absolutePos(STATION), PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
		helper.runAfterDelay(1100, () -> {
			Component line = JobShortages.line(level, site.id(), site.missing(), helper.absolutePos(BENCH));
			String text = line == null ? null : line.getString();
			helper.assertTrue(chest.countItem(Items.BLUE_DYE) == 2, "the dye was taken with no wool to dye: " + chest.countItem(Items.BLUE_DYE) + " left");
			helper.assertTrue(text != null && text.startsWith("Can't be made yet: short of ") && text.contains(" to make 2 Blue Wool"),
				"the builder's line: " + text);
			String named = text.substring(0, text.indexOf(" to make "));
			helper.assertTrue((named.contains("White Wool") || named.contains("String")) && !named.contains("Blue Wool") && !named.contains("Black")
				&& !named.contains("Ink Sac") && !named.contains("Lapis"), "the builder's line names the wrong makings: " + text);
			helper.succeed();
		});
	}

	private static String names(Map<Item, Integer> m) {
		StringBuilder sb = new StringBuilder("{");
		m.forEach((k, v) -> sb.append(BuiltInRegistries.ITEM.getKey(k).getPath()).append('=').append(v).append(' '));
		return sb.append('}').toString();
	}
}
