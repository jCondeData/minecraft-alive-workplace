package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.MaterialFamilies;
import io.github.jcondedata.aliveworkplace.build.MaterialList;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA for 23.4 (qa-1004-0833), written from the item's spec: "a checklist of everything a blueprint needs, minus what's
 * in the supply chests, as a written book". Checks every blueprint in the library, not only the test hut, chests that
 * hold more than needed or things the build doesn't use, and the book taken the other way round (blueprint in the off
 * hand).
 */
public class QaMaterialListGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final Vec3i HUT_SIZE = new Vec3i(5, 4, 5);
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST_A = new BlockPos(2, 2, 4);
	private static final BlockPos CHEST_B = new BlockPos(4, 2, 2);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	/** What a book page shows: the list puts 7 lines on a page so a wrapped name still fits. */
	private static final int LINES_PER_PAGE = 7;

	/**
	 * Every blueprint a player can pick: its unplaced book lists exactly what the builder's own plan for it uses (the
	 * same items, the same counts), every kind once, at most 7 lines a page, and the book is one the game can save and
	 * send to a client (title and pages within vanilla's limits).
	 */
	//$ gametest_ticks 'FabricGameTest.EMPTY_STRUCTURE' '200'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void everyLibraryBlueprintsListMatchesItsPlanAndFitsABook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<ResourceLocation> ids = BlueprintLibrary.list(level.getServer(), false);
		helper.assertTrue(ids.size() >= 10, "only " + ids.size() + " blueprints in the library: " + ids);
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (ResourceLocation id : ids) {
			Optional<Blueprint> blueprint = BlueprintLibrary.get(level, id);
			if (blueprint.isEmpty()) {
				problems.add(id + ": not loadable");
				continue;
			}
			BlueprintData data = BlueprintItem.data(BlueprintItem.create(id, blueprint.get().size())).orElseThrow();
			Optional<BlueprintSupplies.Checklist> list = BlueprintSupplies.checklist(level, data);
			if (list.isEmpty()) {
				continue; // too big for a list: the item says so in the action bar
			}
			checked++;
			Map<Item, Integer> listed = new LinkedHashMap<>();
			for (BlueprintSupplies.Line line : list.get().lines()) {
				if (listed.put(line.item(), line.need()) != null) {
					problems.add(id + ": " + line.item() + " listed twice");
				}
				if (line.need() <= 0 || line.toBring() != line.need()) {
					problems.add(id + ": odd line " + line);
				}
			}
			BuildPlan plan = BuildPlan.create(blueprint.get(),
				new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(BlockPos.ZERO), Rotation.NONE, Mirror.NONE));
			Map<Item, Integer> planned = new LinkedHashMap<>();
			plan.materials().forEach((item, n) -> planned.merge(MaterialFamilies.key(item), n, Integer::sum));
			if (!listed.equals(planned)) {
				problems.add(id + ": the book lists " + diff(listed, planned) + " against the builder's plan");
			}
			Component name = Blueprints.displayName(id);
			List<Component> pages = MaterialList.pages(name, list.get());
			int kinds = list.get().lines().size();
			int expectedPages = 1 + (kinds + LINES_PER_PAGE - 1) / LINES_PER_PAGE;
			if (pages.size() != Math.min(expectedPages, 100)) {
				problems.add(id + ": " + pages.size() + " pages for " + kinds + " kinds");
			}
			Set<String> seen = new HashSet<>();
			for (int p = 1; p < pages.size(); p++) {
				String[] lines = pages.get(p).getString().split("\n");
				if (lines.length > LINES_PER_PAGE) {
					problems.add(id + ": page " + (p + 1) + " has " + lines.length + " lines");
				}
				for (String l : lines) {
					if (!seen.add(l)) {
						problems.add(id + ": line '" + l + "' twice");
					}
				}
			}
			if (seen.size() != kinds) {
				problems.add(id + ": " + seen.size() + " lines in the book for " + kinds + " kinds");
			}
			Optional<ItemStack> book = MaterialList.write(level, data, "test-mock-player");
			if (book.isEmpty()) {
				problems.add(id + ": no book written");
				continue;
			}
			WrittenBookContent content = book.get().get(DataComponents.WRITTEN_BOOK_CONTENT);
			if (content == null || content.title().raw().isEmpty() || content.title().raw().length() > 32) {
				problems.add(id + ": bad title " + (content == null ? null : content.title().raw()));
			}
			var saved = ItemStack.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), book.get());
			if (saved.error().isPresent()) {
				problems.add(id + ": the book can't be saved: " + saved.error().get().message());
			}
		}
		helper.assertTrue(checked >= 10, "only " + checked + " blueprints had a list");
		helper.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
		helper.succeed();
	}

	/**
	 * Two chests by the table: one holds more cobblestone than the hut needs, the other planks split with the first plus
	 * dirt the hut never uses. Nothing goes below zero, the chests add up, dirt isn't listed, and the two things still to
	 * bring (door, torch) come first.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaMaterialList"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaMaterialList")
	public void chestsAddUpAndNeverGoBelowZero(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(CHEST_A, Blocks.CHEST);
		helper.setBlock(CHEST_B, Blocks.CHEST);
		Container a = helper.getBlockEntity(CHEST_A);
		a.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		a.setItem(1, new ItemStack(Items.OAK_PLANKS, 30));
		Container b = helper.getBlockEntity(CHEST_B);
		b.setItem(0, new ItemStack(Items.OAK_PLANKS, 30));
		b.setItem(1, new ItemStack(Items.DIRT, 64));
		BlueprintData data = placed(helper);
		helper.runAfterDelay(2, () -> {
			BlueprintSupplies.Checklist list = BlueprintSupplies.checklist(level, data).orElseThrow();
			Map<Item, Integer> toBring = list.lines().stream().collect(Collectors.toMap(BlueprintSupplies.Line::item, BlueprintSupplies.Line::toBring));
			helper.assertTrue(toBring.equals(Map.of(Items.COBBLESTONE, 0, Items.OAK_PLANKS, 0, Items.OAK_DOOR, 1, Items.TORCH, 1)), "to bring: " + toBring);
			helper.assertTrue(list.toBring() == 2, "2 items to bring, not " + list.toBring());
			helper.assertTrue(list.lines().get(0).toBring() > 0 && list.lines().get(1).toBring() > 0
				&& list.lines().get(2).toBring() == 0 && list.lines().get(3).toBring() == 0, "what's still to bring isn't first: " + list.lines());
			List<String> pages = MaterialList.pages(Component.literal("Test Hut"), list).stream().map(Component::getString).toList();
			helper.assertTrue(pages.get(0).contains("Still to bring: 2 items of 2 kinds."), "the first page: " + pages.get(0));
			helper.assertTrue(pages.get(1).equals("☐ 1 × Oak Door\n☐ 1 × Torch\n☑ Oak Planks\n☑ Cobblestone"), "the list page: " + pages.get(1));
			helper.succeed();
		});
	}

	/** The other way round: the Book and Quill in the main hand, right-click with the blueprint in the off hand. */
	//$ gametest_ticks_batch AREA '40' '"qaMaterialList"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaMaterialList")
	public void theBlueprintInTheOffHandWritesTheListToo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		BlueprintData data = placed(helper);
		helper.runAfterDelay(2, () -> {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			ItemStack blueprint = BlueprintItem.create(TEST_HUT, HUT_SIZE);
			blueprint.set(ModComponents.BLUEPRINT, data);
			player.setItemInHand(InteractionHand.OFF_HAND, blueprint);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WRITABLE_BOOK));
			blueprint.use(level, player, InteractionHand.OFF_HAND);
			ItemStack book = player.getMainHandItem();
			helper.assertTrue(book.is(Items.WRITTEN_BOOK), "the Book and Quill didn't become the list: " + book);
			WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
			helper.assertTrue(content != null && content.pages().size() == 2, "pages: " + (content == null ? null : content.pages().size()));
			helper.assertTrue(content.author().equals(player.getName().getString()), "author: " + content.author());
			String first = content.pages().get(0).raw().getString();
			helper.assertTrue(first.contains("Still to bring: 82 items of 4 kinds."), "the first page with empty surroundings: " + first);
			helper.assertTrue(BlueprintItem.data(player.getOffhandItem()).equals(Optional.of(data)), "the blueprint changed: " + player.getOffhandItem());
			helper.succeed();
		});
	}

	private static BlueprintData placed(GameTestHelper helper) {
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(HUT),
			Rotation.NONE, Mirror.NONE);
		return BlueprintItem.data(BlueprintItem.create(TEST_HUT, HUT_SIZE)).orElseThrow().withPlacement(Optional.of(placement));
	}

	private static String diff(Map<Item, Integer> listed, Map<Item, Integer> planned) {
		List<String> out = new ArrayList<>();
		Set<Item> all = new HashSet<>(listed.keySet());
		all.addAll(planned.keySet());
		for (Item item : all) {
			int l = listed.getOrDefault(item, 0);
			int p = planned.getOrDefault(item, 0);
			if (l != p) {
				out.add(item + " " + l + " (plan " + p + ")");
			}
		}
		return out.toString();
	}
}
