package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialList;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.build.SiteList;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * B279: the material list of a build under way. A Book and Quill used on the builder (or his Builder's Bench) writes
 * what the rest of the build needs, less what is in his chests, his bag and the village store; a sneak-right-click with
 * an empty hand puts the whole list in chat. The test hut needs 25 cobblestone, 55 oak planks, an oak door and a torch.
 */
public class BuilderSiteListGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);

	private record Setup(ServerLevel level, Villager builder, BuildSite site, BuildPlan plan, Container chest) {
	}

	/** A builder at his bench with a chest by it, the test hut handed to him and its cobblestone walls half up. */
	private static Setup setup(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 4));
		chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 20));
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		builder.setNoAi(true); // the list is read as it stands: the builder mustn't work on it meanwhile
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no plan for the test hut");
		return new Setup(level, builder, site, plan, chest);
	}

	/** Mid-build: the first {@code n} cobblestone blocks of the plan stand. */
	private static int placeCobblestone(Setup s, int n) {
		int placed = 0;
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step step : s.plan().steps(stage)) {
				if (placed < n && step.state().is(Blocks.COBBLESTONE)) {
					s.level().setBlockAndUpdate(step.pos(), step.state());
					placed++;
				}
			}
		}
		return placed;
	}

	/** What is left, worked out here from the site's own steps: every step from its stage on not standing, less the chest. */
	private static Map<Item, Integer> expectedToBring(Setup s, BuildSite site) {
		Map<Item, Integer> need = new HashMap<>();
		for (BuildPlan.Step step : site.upcoming(s.plan(), Integer.MAX_VALUE)) {
			if (!MaterialRules.matches(s.level().getBlockState(step.pos()), step.state())) {
				for (MaterialRules.Requirement r : step.requirements()) {
					need.merge(r.item(), r.count(), Integer::sum);
				}
			}
		}
		Map<Item, Integer> out = new HashMap<>();
		need.forEach((item, n) -> {
			int have = 0;
			for (int i = 0; i < s.chest().getContainerSize(); i++) {
				if (s.chest().getItem(i).is(item)) {
					have += s.chest().getItem(i).getCount();
				}
			}
			out.put(item, Math.max(0, n - have));
		});
		return out;
	}

	private static Map<Item, Integer> toBring(BlueprintSupplies.Checklist list) {
		return list.lines().stream().collect(Collectors.toMap(BlueprintSupplies.Line::item, BlueprintSupplies.Line::toBring));
	}

	/** A test player who keeps every chat and action-bar line they're shown. */
	private static ServerPlayer listeningPlayer(GameTestHelper helper, List<String> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "test-lister"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				seen.add(message.getString());
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	/** The player's own right-click on the villager, as the client sends it. */
	private static void rightClick(ServerPlayer player, Villager villager, ItemStack stack, boolean sneaking) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, sneaking, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		player.setShiftKeyDown(false);
	}

	private static List<String> pages(ItemStack book) {
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		return content == null ? List.of() : content.pages().stream().map(p -> p.raw().getString()).toList();
	}

	/** Mid-build, a Book and Quill used on the builder becomes the list of the rest: the site's steps not standing, less the chest. */
	//$ gametest_ticks_batch AREA '40' '"builderSiteList"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "builderSiteList")
	public void aBookAndQuillOnTheBuilderWritesWhatIsLeft(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.assertTrue(placeCobblestone(s, 9) == 9, "the hut has fewer than 9 cobblestone blocks");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		rightClick(player, s.builder(), new ItemStack(Items.WRITABLE_BOOK), false);
		ItemStack book = player.getMainHandItem();
		helper.assertTrue(book.is(Items.WRITTEN_BOOK), "the Book and Quill wasn't written: " + book + ", the player saw " + seen);

		BlueprintSupplies.Checklist list = SiteList.checklist(s.level(), s.site(), s.builder());
		Map<Item, Integer> expected = expectedToBring(s, s.site());
		helper.assertTrue(toBring(list).equals(expected), "the list " + toBring(list) + " isn't the site's steps less the chest " + expected);
		// 25 cobblestone, 9 in place, 4 in the chest: 12 to bring; 55 planks, 20 in the chest: 35
		helper.assertTrue(expected.get(Items.COBBLESTONE) == 12 && expected.get(Items.OAK_PLANKS) == 35, "the hut's counts changed: " + expected);

		List<String> pages = pages(book);
		helper.assertTrue(pages.get(0).contains("Still to build: 0% done, Clearing the site."), "the first page: " + pages.get(0));
		helper.assertTrue(pages.get(0).contains("Still to bring: 49 items of 4 kinds."), "the first page: " + pages.get(0));
		helper.assertTrue(pages.get(1).equals("☐ 35 × Oak Planks (of 55)\n☐ 12 × Cobblestone (of 16)\n☐ 1 × Oak Door\n☐ 1 × Torch"),
			"the list page: " + pages.get(1));
		helper.assertTrue(seen.stream().anyMatch(m -> m.startsWith("Wrote the material list for the ")), "no word it was written: " + seen);
		helper.succeed();
	}

	/** What the builder carries and what the village storehouse holds come off the list too. */
	//$ gametest_ticks_batch AREA '40' '"builderSiteListStore"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "builderSiteListStore")
	public void theBagAndTheVillageStoreComeOff(GameTestHelper helper) {
		Setup s = setup(helper);
		Leftovers.storehouses(helper);
		Leftovers.village(helper, 48); // village sharing is off in tests unless a test turns it on
		ModAttachments.BUILDER_BAG.getOrCreate(s.builder()).add(new ItemStack(Items.COBBLESTONE, 5));
		BlockPos storehouse = new BlockPos(14, 2, 14);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(storehouse.east(), Blocks.CHEST);
		Container store = helper.getBlockEntity(storehouse.east());
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 30));
		helper.runAfterDelay(2, () -> { // the storehouse becomes a point of interest a moment after it's placed
			io.github.jcondedata.aliveworkplace.work.Village.forget(s.builder());
			Map<Item, Integer> got = toBring(SiteList.checklist(s.level(), s.site(), s.builder()));
			helper.assertTrue(got.get(Items.COBBLESTONE) == 25 - 4 - 5, "the bag's cobblestone didn't come off: " + got);
			helper.assertTrue(got.get(Items.OAK_PLANKS) == 55 - 20 - 30, "the storehouse's planks didn't come off: " + got);
			helper.succeed();
		});
	}

	/** Every stage, and after a save and reload of the site: the list is that stage's steps on, the same as before the reload. */
	//$ gametest_ticks_batch AREA '40' '"builderSiteListStages"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "builderSiteListStages")
	public void everyStageAndAfterAReload(GameTestHelper helper) {
		Setup s = setup(helper);
		placeCobblestone(s, 6);
		BuildSite site = s.site();
		List<BuildPlan.Stage> seenStages = new ArrayList<>();
		while (site.stage() != BuildPlan.Stage.LANDSCAPE && site.stage() != BuildPlan.Stage.DONE) {
			seenStages.add(site.stage());
			Map<Item, Integer> got = toBring(SiteList.checklist(s.level(), site, s.builder()));
			Map<Item, Integer> expected = expectedToBring(s, site);
			helper.assertTrue(got.equals(expected), "in " + site.stage() + " the list " + got + " isn't " + expected);
			BuildSite reloaded = BuildSite.load(site.save());
			helper.assertTrue(reloaded != null && reloaded.stage() == site.stage(), "the site didn't reload in " + site.stage());
			Map<Item, Integer> after = toBring(SiteList.checklist(s.level(), reloaded, s.builder()));
			helper.assertTrue(after.equals(got), "in " + site.stage() + " after the reload the list " + after + " isn't " + got);
			site.finishList();
		}
		helper.assertTrue(seenStages.contains(BuildPlan.Stage.STRUCTURE) && seenStages.contains(BuildPlan.Stage.DECORATION), "stages: " + seenStages);
		helper.succeed();
	}

	/** Every block in place: the builder says so plainly and the Book and Quill is left as it was; with no build, he says that. */
	//$ gametest_ticks_batch AREA '40' '"builderSiteListDone"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "builderSiteListDone")
	public void aFinishedOrIdleBuilderSaysSo(GameTestHelper helper) {
		Setup s = setup(helper);
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step step : s.plan().steps(stage)) {
				s.level().setBlockAndUpdate(step.pos(), step.state());
			}
		}
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		rightClick(player, s.builder(), new ItemStack(Items.WRITABLE_BOOK), false);
		helper.assertTrue(player.getMainHandItem().is(Items.WRITABLE_BOOK), "a list was written for a finished build: " + player.getMainHandItem());
		String name = Blueprints.displayName(TEST_HUT).getString();
		helper.assertTrue(seen.contains("Every block of the " + name + " is in place. Nothing is left to bring."), "the player saw " + seen);

		seen.clear();
		Villager idle = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 2));
		idle.setNoAi(true);
		helper.setBlock(new BlockPos(4, 2, 1), ModBlocks.BUILDERS_BENCH);
		Builders.employ(s.level(), idle, helper.absolutePos(new BlockPos(4, 2, 1)));
		rightClick(player, idle, new ItemStack(Items.WRITABLE_BOOK), false);
		helper.assertTrue(player.getMainHandItem().is(Items.WRITABLE_BOOK), "a list was written for an idle builder");
		helper.assertTrue(seen.contains(idle.getDisplayName().getString() + " has no build under way, so there is no list to write. Hand them a placed blueprint."),
			"the player saw " + seen);
		helper.succeed();
	}

	/** A sneak-right-click with an empty hand shows the status and then the whole list in chat, not just the first few. */
	//$ gametest_ticks_batch AREA '40' '"builderSiteListChat"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "builderSiteListChat")
	public void anEmptyHandShowsTheWholeListInChat(GameTestHelper helper) {
		Setup s = setup(helper);
		placeCobblestone(s, 9);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		rightClick(player, s.builder(), ItemStack.EMPTY, true);
		String name = Blueprints.displayName(TEST_HUT).getString();
		String list = seen.stream().filter(m -> m.startsWith("Still to bring for the " + name)).findFirst().orElse(null);
		helper.assertTrue(list != null, "no list in chat: " + seen);
		helper.assertTrue(list.equals("Still to bring for the " + name + ": 49 items of 4 kinds\n  ☐ 35 × Oak Planks (of 55)\n  ☐ 12 × Cobblestone (of 16)"
			+ "\n  ☐ 1 × Oak Door\n  ☐ 1 × Torch"), "the chat list: " + list);

		// Everything in the chest: it says so, and lists what's there.
		s.chest().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		s.chest().setItem(1, new ItemStack(Items.OAK_PLANKS, 64));
		s.chest().setItem(2, new ItemStack(Items.OAK_DOOR));
		s.chest().setItem(3, new ItemStack(Items.TORCH));
		seen.clear();
		rightClick(player, s.builder(), ItemStack.EMPTY, true);
		helper.assertTrue(seen.contains("Everything the " + name + " still needs is in the chests or the village store.\n  Already there: Oak Planks, Cobblestone, Oak Door, Torch"),
			"the player saw " + seen);
		helper.succeed();
	}

	/** The Builder's Bench: a Book and Quill used on it writes its build's list; a bench with no build says so. */
	//$ gametest_ticks_batch AREA '40' '"builderSiteListBench"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "builderSiteListBench")
	public void aBookAndQuillOnTheBenchWritesTheList(GameTestHelper helper) {
		Setup s = setup(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		BlockPos bench = helper.absolutePos(BENCH);
		player.moveTo(bench.getX() + 0.5, bench.getY() + 1, bench.getZ() - 1.5, 0f, 30f);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WRITABLE_BOOK, 2));
		player.gameMode.useItemOn(player, s.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(bench), Direction.NORTH, bench, false));
		helper.assertTrue(player.getMainHandItem().is(Items.WRITABLE_BOOK) && player.getMainHandItem().getCount() == 1,
			"one Book and Quill should be used up: " + player.getMainHandItem() + ", the player saw " + seen);
		ItemStack book = player.getInventory().items.stream().filter(st -> st.is(Items.WRITTEN_BOOK)).findFirst().orElse(ItemStack.EMPTY);
		List<String> pages = pages(book);
		helper.assertTrue(!pages.isEmpty() && pages.get(1).startsWith("☐ 35 × Oak Planks (of 55)"), "the bench's list: " + pages);

		// A bench nobody builds from.
		seen.clear();
		BlockPos lone = new BlockPos(14, 2, 2);
		helper.setBlock(lone, ModBlocks.BUILDERS_BENCH);
		BlockPos loneAbs = helper.absolutePos(lone);
		player.moveTo(loneAbs.getX() + 0.5, loneAbs.getY() + 1, loneAbs.getZ() - 1.5, 0f, 30f);
		player.gameMode.useItemOn(player, s.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(loneAbs), Direction.NORTH, loneAbs, false));
		helper.assertTrue(player.getMainHandItem().is(Items.WRITABLE_BOOK), "a list was written at a bench with no build");
		helper.assertTrue(seen.contains("No build is under way from this bench. Hand its builder a placed blueprint."), "the player saw " + seen);
		helper.succeed();
	}

	/**
	 * B394: the Book and Quill in the OFF hand with the main hand empty: the main hand's empty turn used to answer with the
	 * bench's chests line; now the off hand's book gets the list, as from the main hand.
	 */
	//$ gametest_ticks_batch AREA '40' '"builderSiteListBenchOff"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "builderSiteListBenchOff")
	public void aBookAndQuillInTheOffHandOnTheBenchWritesTheList(GameTestHelper helper) {
		Setup s = setup(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		BlockPos bench = helper.absolutePos(BENCH);
		player.moveTo(bench.getX() + 0.5, bench.getY() + 1, bench.getZ() - 1.5, 0f, 30f);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WRITABLE_BOOK, 2));
		player.gameMode.useItemOn(player, s.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(bench), Direction.NORTH, bench, false));
		helper.assertTrue(player.getOffhandItem().is(Items.WRITABLE_BOOK) && player.getOffhandItem().getCount() == 1,
			"one off-hand Book and Quill should be used up: " + player.getOffhandItem() + ", the player saw " + seen);
		ItemStack book = player.getInventory().items.stream().filter(st -> st.is(Items.WRITTEN_BOOK)).findFirst().orElse(ItemStack.EMPTY);
		List<String> pages = pages(book);
		helper.assertTrue(pages.size() > 1 && pages.get(1).startsWith("☐ 35 × Oak Planks (of 55)"), "the bench's list from the off hand: " + pages
			+ ", the player saw " + seen);
		helper.succeed();
	}

	/** Taking a building down needs nothing, and the list says so; the book's own words read right. */
	//$ gametest_ticks_batch AREA '40' '"builderSiteListWords"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "builderSiteListWords")
	public void takingDownNeedsNothing(GameTestHelper helper) {
		Setup s = setup(helper);
		BuildSite down = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "Jesse", TEST_HUT, s.site().placement());
		down.setDeconstruction();
		String said = SiteList.chat(s.level(), down, s.builder()).getString();
		helper.assertTrue(said.equals(s.builder().getDisplayName().getString() + " is taking a building down. That needs no materials."), "said: " + said);
		BlueprintSupplies.Checklist list = SiteList.checklist(s.level(), s.site(), s.builder());
		String first = MaterialList.pages(Component.literal("Test Hut"), list, Component.translatable("book.aliveworkplace.material_list.site", 40,
			Component.translatable("message.aliveworkplace.status.stage.structure"))).get(0).getString();
		helper.assertTrue(first.equals("Materials for the Test Hut\n\nStill to build: 40% done, Building the structure. Blocks already in place are left off. "
			+ "What the builder and any helpers carry, and what the village store can spare, is taken off too.\n\n"
			+ "What the chests by the Blueprint Table or bench at " + helper.absolutePos(BENCH).getX() + " " + helper.absolutePos(BENCH).getY() + " "
			+ helper.absolutePos(BENCH).getZ() + " hold is taken off.\n\nStill to bring: 58 items of 4 kinds."), "the first page: " + first);
		helper.succeed();
	}

	/**
	 * B404: five planks short and nothing else, the book reads "Still to bring: 5 items of 1 kind." (it read "1 kinds"),
	 * and one plank short, "1 item of 1 kind.".
	 */
	//$ gametest_ticks_batch AREA '40' '"builderSiteListOneKind"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "builderSiteListOneKind")
	public void oneKindShortReadsInTheSingular(GameTestHelper helper) {
		Setup s = setup(helper);
		s.chest().setItem(1, ItemStack.EMPTY);
		int planks = expectedToBring(s, s.site()).getOrDefault(Items.OAK_PLANKS, 0);
		helper.assertTrue(planks > 5 && planks <= 64, "the hut needs " + planks + " planks");
		s.chest().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		s.chest().setItem(2, new ItemStack(Items.OAK_DOOR, 4));
		s.chest().setItem(3, new ItemStack(Items.TORCH, 16));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		for (int shortBy : new int[] {5, 1}) {
			s.chest().setItem(1, new ItemStack(Items.OAK_PLANKS, planks - shortBy));
			helper.assertTrue(expectedToBring(s, s.site()).entrySet().stream().filter(e -> e.getValue() > 0).toList()
				.equals(List.of(Map.entry(Items.OAK_PLANKS, shortBy))), "not just " + shortBy + " planks short: " + expectedToBring(s, s.site()));
			rightClick(player, s.builder(), new ItemStack(Items.WRITABLE_BOOK), false);
			ItemStack book = player.getMainHandItem();
			helper.assertTrue(book.is(Items.WRITTEN_BOOK), "the Book and Quill wasn't written: " + book + ", the player saw " + seen);
			String first = pages(book).get(0);
			String want = shortBy == 1 ? "Still to bring: 1 item of 1 kind." : "Still to bring: 5 items of 1 kind.";
			helper.assertTrue(first.contains(want) && !first.contains("kinds"), "short by " + shortBy + ", the first page: " + first.replace("\n", " "));
		}
		helper.succeed();
	}
}
