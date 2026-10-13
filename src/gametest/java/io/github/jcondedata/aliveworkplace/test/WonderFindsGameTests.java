package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.rules.JobLevel;
import io.github.jcondedata.aliveworkplace.rules.LegendSettled;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderGoods;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * A Wonder's goods as the owner changed them (ROADMAP 35.7a, 35.7b): every stage of every shipped Wonder asks for a few
 * hard things found by exploring (one of each, never "64 of something") and a rare good from an upgraded workshop; found
 * goods aren't made by crafters nor asked for by the daily quest, and the board says where they're found; a rare worker
 * in the village finds their line in play time and brings it (the chronicle says so), the search surviving a save and
 * stopping with nobody on the server; and a save from before the change hands back what was handed in for the old lines.
 * The Great Library's stage I is the real one: the Heart Pottery Sherd (a Master Sifter finds it), the Dune Armor Trim
 * Template (the Pathfinder) and an Illuminated Book (a Printer).
 */
public class WonderFindsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation LIBRARY = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "great_library");
	private static final ResourceLocation PATHFINDER = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "pathfinder");

	/** A village and its Great Library waiting at stage I: the owner, the hall, the stone, the stockpile's chest, the Wonder. */
	private record W(ServerLevel level, ServerPlayer owner, BlockPos hall, BlockPos stone, Container pile, UUID id) {
		WonderSite site() {
			return WonderManager.get(level).site(id).orElseThrow();
		}

		List<WonderGoods.Line> lines() {
			return WonderGoods.lines(level, site());
		}
	}

	private static W village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos hallRel = new BlockPos(14, 2, 14);
		BlockPos stoneRel = new BlockPos(2, 2, 2);
		BlockPos pileRel = new BlockPos(2, 2, 5);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity hall = helper.getBlockEntity(hallRel);
		hall.setOwner(owner.getUUID(), "Owner");
		hall.setRank(VillageRanks.Rank.TOWN);
		helper.setBlock(stoneRel, ModBlocks.FOUNDATION_STONE);
		helper.setBlock(pileRel, Blocks.CHEST);
		BlockPos stone = helper.absolutePos(stoneRel);
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(LIBRARY, helper.absolutePos(hallRel), owner.getUUID(), "Owner", Chronicle.day(level));
		Leftovers.after(helper, () -> forget(level, site.id()));
		manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), stone.offset(1, 0, 1), Rotation.NONE, Mirror.NONE), stone);
		return new W(level, owner, helper.absolutePos(hallRel), stone, helper.getBlockEntity(pileRel), site.id());
	}

	private static void forget(ServerLevel level, UUID wonder) {
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite s : List.copyOf(sites.all())) {
			if (wonder.equals(s.wonder())) {
				sites.remove(s.id());
			}
		}
		Requests.clearAt(level, WonderManager.get(level).site(wonder).flatMap(WonderSite::stone).orElse(BlockPos.ZERO));
		WonderManager.get(level).remove(wonder);
	}

	/** Every translation key in {@code c} and its siblings and arguments. */
	private static List<String> keys(Component c) {
		List<String> out = new ArrayList<>();
		if (c.getContents() instanceof TranslatableContents t) {
			out.add(t.getKey());
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component a) {
					out.addAll(keys(a));
				}
			}
		}
		for (Component s : c.getSiblings()) {
			out.addAll(keys(s));
		}
		return out;
	}

	private static boolean chronicled(W w, String key) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) w.level().getBlockEntity(w.hall());
		return hall.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.WONDER && keys(e.text()).contains(key));
	}

	private static int count(Container box, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < box.getContainerSize(); i++) {
			if (box.getItem(i).is(item)) {
				n += box.getItem(i).getCount();
			}
		}
		return n;
	}

	private static long searched(W w, int line) {
		return WonderManager.get(w.level()).searches(w.id()).stream().filter(s -> s.line() == line).mapToLong(WonderManager.Search::ticks).sum();
	}

	// ---------------------------------------------------------------- the shipped Wonders' goods

	/**
	 * Every stage of every shipped Wonder asks for at least two things found by exploring, one of each, each with a rare
	 * worker who'd find it (a Legend or a Master) and where it's found in the language file, and a rare good from a
	 * workshop; no line asks for more than two of anything.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyStageAsksForFoundGoodsNotCounts(GameTestHelper helper) {
		int wonders = 0;
		for (Wonder wonder : Wonders.loaded().values()) {
			if (!wonder.id().getNamespace().equals("aliveworkplace")) {
				continue;
			}
			wonders++;
			for (int n = 0; n < wonder.stages().size(); n++) {
				List<Wonder.Good> goods = wonder.stages().get(n).goods();
				String where = wonder.id() + " stage " + (n + 1);
				helper.assertTrue(goods.stream().filter(Wonder.Good::found).count() >= 2, where + ": fewer than two found goods: " + goods);
				helper.assertTrue(goods.stream().anyMatch(g -> !g.found()), where + ": no workshop good: " + goods);
				for (Wonder.Good good : goods) {
					helper.assertTrue(good.count() <= 2, where + ": a count of " + good.count() + " (" + good.item() + ")");
					if (!good.found()) {
						continue;
					}
					helper.assertTrue(good.count() == 1 && good.item().isPresent() && good.helper().isPresent(), where + ": a found line " + good);
					ResourceLocation item = good.item().get();
					helper.assertTrue(Language.getInstance().has("goods.aliveworkplace.hint." + item.getNamespace() + "." + item.getPath()),
						where + ": no hint for " + item);
					Wonder.Helper h = good.helper().get();
					boolean rare = h.condition() instanceof LegendSettled legend && legend.standIn().isEmpty()
						|| h.condition() instanceof JobLevel job && job.level() == 5;
					helper.assertTrue(rare && h.days() >= 1 && h.days() <= Wonders.MAX_HELPER_DAYS, where + ": " + item + "'s helper " + h);
					String name = WonderGoods.what(good).getString();
					helper.assertTrue(!name.equals("Smithing Template") && !name.equals("Music Disc") && !name.contains("trim_pattern.")
						&& !name.contains("jukebox_song.") && !name.contains("upgrade."), where + ": " + item + " is called " + name);
				}
			}
		}
		helper.assertTrue(wonders >= 7, "shipped Wonders loaded: " + wonders);
		helper.assertTrue(Language.getInstance().has("goods.aliveworkplace.hint.default"), "no text for a found thing with no hint of its own");
		helper.succeed();
	}

	// ---------------------------------------------------------------- found goods aren't made

	/**
	 * The Great Library's stage I waits for its three lines: the stone says where the two found things are found and who
	 * would find them; they're on the board as "Found in ...", but no crafter makes them and the hall's quest asks for the
	 * Illuminated Book, the one line a workshop makes. Every sentence is in the language file.
	 */
	//$ gametest_batch AREA '"wonderFindsStage"'
	@GameTest(template = AREA, batch = "wonderFindsStage")
	public void aStageAsksForExploredThingsNotCounts(GameTestHelper helper) {
		W w = village(helper);
		ServerLevel level = w.level();
		List<WonderGoods.Line> lines = w.lines();
		helper.assertTrue(lines.size() == 3 && lines.get(0).good().found() && lines.get(1).good().found() && !lines.get(2).good().found()
			&& lines.stream().allMatch(l -> l.need() == 1), "stage I's lines: " + lines);
		BlockPos storehouse = new BlockPos(14, 2, 4);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(storehouse.east(), Blocks.CHEST);
		WonderStages.tick(level);
		helper.assertTrue(!w.site().started(), "stage I began without its goods");
		List<Requests.Request> posts = Requests.atBlocks(level, w.stone(), 4).stream().filter(Requests.Request::forBlock).toList();
		helper.assertTrue(posts.size() == 3 && posts.stream().filter(p -> p.block().found()).count() == 2, "the posts: " + posts.size());
		ChoiceMenu board = StorehouseBoard.boardForTest(w.owner(), helper.absolutePos(storehouse));
		String lore = "";
		for (int i = StorehouseBoard.FIRST_REQUEST; i < ChoiceMenu.SIZE; i++) {
			if (board.icon(i).is(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE)) {
				lore = board.icon(i).get(DataComponents.LORE).lines().stream().map(Component::getString).toList().toString();
			}
		}
		helper.assertTrue(lore.contains("Found in a desert temple's chests"), "the template on the board: " + lore);
		List<WonderGoods.Order> orders = WonderGoods.orders(level, w.hall());
		helper.assertTrue(orders.size() == 1 && BuiltInRegistries.ITEM.getKey(orders.get(0).item()).getPath().equals("illuminated_book"),
			"what crafters are asked to make: " + orders);
		Optional<WonderGoods.Wanted> wanted = WonderGoods.shortest(level, w.hall());
		helper.assertTrue(wanted.isPresent() && wanted.get().item().equals("aliveworkplace:illuminated_book"), "the hall's quest asks for " + wanted);
		Component status = WonderStages.statusText(level, w.stone());
		String text = status.getString();
		List<String> said = keys(status);
		helper.assertTrue(said.stream().filter("message.aliveworkplace.wonder.goods.found_line"::equals).count() == 2
			&& said.stream().filter("message.aliveworkplace.wonder.goods.helper_wanted"::equals).count() == 2
			&& said.contains("message.aliveworkplace.wonder.goods.line"), "the stone's lines: " + said);
		helper.assertTrue(text.contains("0 of 1 Dune Armor Trim Template · found in a desert temple's chests · faster with the Pathfinder (finds it in 9 days)")
			&& text.contains("0 of 1 Heart Pottery Sherd · found by brushing the suspicious gravel of trail ruins · faster with the Master Sifter"),
			"the lines as shown: " + text);
		for (String key : said) {
			helper.assertTrue(key.startsWith("trim_pattern.") || Language.getInstance().has(key), "no text for " + key);
		}
		// the template handed in: its line is in, found by the players themselves
		w.pile().setItem(0, new ItemStack(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE));
		helper.assertTrue(w.lines().get(1).full() && !w.lines().get(1).brought(), "the template in the stockpile: " + w.lines().get(1));
		helper.succeed();
	}

	// ---------------------------------------------------------------- a rare worker finds it

	/**
	 * With the Pathfinder settled in the village, his search for the template goes on in play time (the stone counts the
	 * days); nobody searches for the sherd (no Master Sifter). The search survives a save, stops with nobody on the server,
	 * and after his 9 days the template is in, brought by him: the chronicle says so, its post comes down and the stone
	 * says who brought it.
	 */
	//$ gametest_batch AREA '"wonderFindsHelper"'
	@GameTest(template = AREA, batch = "wonderFindsHelper")
	public void aRareWorkerFindsHisLine(GameTestHelper helper) {
		W w = village(helper);
		ServerLevel level = w.level();
		Wonder.Good template = w.lines().get(1).good();
		int days = template.helper().orElseThrow().days();
		helper.assertTrue(days == 9, "the Pathfinder's days: " + days);
		int step = 2400; // a tenth of a day each round
		WonderGoods.search(level, w.site(), step);
		helper.assertTrue(searched(w, 1) == 0, "searched without the Pathfinder: " + searched(w, 1));

		Legend pathfinder = Legends.get(PATHFINDER).orElseThrow();
		Villager him = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		him.setNoAi(true);
		Legends.make(level, him, pathfinder, "test");
		for (int i = 0; i < 35; i++) {
			WonderGoods.search(level, w.site(), step);
		}
		helper.assertTrue(searched(w, 1) == 35L * step && searched(w, 0) == 0, "after 3.5 days: template " + searched(w, 1) + ", sherd " + searched(w, 0));
		String text = WonderStages.statusText(level, w.stone()).getString();
		helper.assertTrue(text.contains("the Pathfinder is searching: day 4 of 9"), "the stone while he searches: " + text);

		// saved and read back
		var registries = level.registryAccess();
		WonderManager back = WonderManager.load(WonderManager.get(level).save(new CompoundTag(), registries), registries);
		helper.assertTrue(back.searches(w.id()).equals(WonderManager.get(level).searches(w.id())), "the search read back: " + back.searches(w.id()));

		// nobody on the server: the search waits (checked when this test's player is the only one on)
		if (level.getServer().getPlayerList().getPlayers().size() == 1) {
			level.getServer().getPlayerList().remove(w.owner());
			WonderGoods.search(level, w.site(), step);
			helper.assertTrue(searched(w, 1) == 35L * step, "searched with nobody on: " + searched(w, 1));
			ProtectionSpecGameTests.player(helper);
		}

		for (int i = 0; i < 60 && !w.lines().get(1).brought(); i++) {
			WonderGoods.search(level, w.site(), step);
		}
		WonderGoods.Line line = w.lines().get(1);
		helper.assertTrue(line.brought() && line.full() && line.in() == 1 && searched(w, 1) == days * WonderGoods.DAY, "not brought after 9 days: " + line);
		helper.assertTrue(chronicled(w, "chronicle.aliveworkplace.wonder.brought"), "the chronicle doesn't say he brought it");
		List<Requests.Request> posts = Requests.atBlocks(level, w.stone(), 4).stream().filter(Requests.Request::forBlock).toList();
		helper.assertTrue(posts.size() == 2 && posts.stream().noneMatch(p -> p.icon().is(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE)), "the posts: " + posts.size());
		List<String> said = keys(WonderStages.statusText(level, w.stone()));
		helper.assertTrue(said.contains("message.aliveworkplace.wonder.goods.helper_brought"), "the stone after: " + said);
		helper.assertTrue(WonderStages.statusText(level, w.stone()).getString().contains("1 of 1 Dune Armor Trim Template"), "the line after");
		for (String key : List.of("chronicle.aliveworkplace.wonder.brought", "message.aliveworkplace.wonder.goods.brought",
			"message.aliveworkplace.wonder.goods.helper_brought", "message.aliveworkplace.wonder.goods.helper_searching")) {
			helper.assertTrue(Language.getInstance().has(key), "no text for " + key);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- an old save

	/**
	 * A Great Library saved mid-stage before the change, with 100 sugar cane and 10 spruce logs handed in for its old
	 * lines (and an Illuminated Book on the wrong line): the old goods don't count for the new lines; on the next round
	 * they go into the stockpile's chest, the book moves to its line, the chronicle says so, and an old world's file
	 * without searches reads as nothing searched.
	 */
	//$ gametest_batch AREA '"wonderFindsOldSave"'
	@GameTest(template = AREA, batch = "wonderFindsOldSave")
	public void anOldSaveHandsBackItsOldGoods(GameTestHelper helper) {
		W w = village(helper);
		ServerLevel level = w.level();
		ResourceLocation book = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "illuminated_book");
		WonderManager.get(level).setDelivered(w.id(), List.of(
			new WonderSite.Handed(0, ResourceLocation.withDefaultNamespace("sugar_cane"), Optional.empty(), 100),
			new WonderSite.Handed(2, ResourceLocation.withDefaultNamespace("spruce_log"), Optional.empty(), 10),
			new WonderSite.Handed(0, book, Optional.empty(), 1)));
		List<WonderGoods.Line> before = w.lines();
		helper.assertTrue(before.get(0).in() == 0 && before.get(2).in() == 0, "old goods counted for the new lines: " + before);
		var registries = level.registryAccess();
		CompoundTag saved = WonderManager.get(level).save(new CompoundTag(), registries);
		helper.assertTrue(!saved.contains("searches"), "a file with nothing searched writes no searches");
		helper.assertTrue(WonderManager.load(saved, registries).searches(w.id()).isEmpty(), "an old file's searches");

		WonderStages.tick(level);
		WonderSite now = w.site();
		helper.assertTrue(now.delivered().size() == 1 && now.delivered().get(0).line() == 2 && now.delivered().get(0).item().equals(book),
			"what's kept as handed in: " + now.delivered());
		helper.assertTrue(count(w.pile(), Items.SUGAR_CANE) == 100 && count(w.pile(), Items.SPRUCE_LOG) == 10,
			"the stockpile: " + count(w.pile(), Items.SUGAR_CANE) + " sugar cane, " + count(w.pile(), Items.SPRUCE_LOG) + " logs");
		helper.assertTrue(w.lines().get(2).full() && !w.lines().get(0).full() && !now.started(), "the lines now: " + w.lines());
		helper.assertTrue(chronicled(w, "chronicle.aliveworkplace.wonder.goods_changed"), "the chronicle doesn't say the goods changed");
		helper.assertTrue(Language.getInstance().has("message.aliveworkplace.wonder.goods.changed")
			&& Language.getInstance().has("chronicle.aliveworkplace.wonder.goods_changed"), "no text for the change");
		WonderStages.tick(level);
		helper.assertTrue(count(w.pile(), Items.SUGAR_CANE) == 100 && w.site().delivered().size() == 1, "handed back twice");
		helper.succeed();
	}
}
