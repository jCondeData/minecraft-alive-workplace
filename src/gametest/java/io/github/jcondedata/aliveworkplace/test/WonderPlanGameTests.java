package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlans;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.wonder.WondersPage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Wonders page and the Wonder Plan (ROADMAP 35.3): the test folly's parts merge into the tower as drawn, no part
 * sets air over an earlier stage's block, and the whole blueprint stays off the Blueprint Table; the page's cards match
 * the requirements (one unmet line crossed) and the cap, and "What next?" names the folly; Draw up the plans takes the
 * emeralds (the player's, or the treasury's) and gives the plan, and refuses over the cap; a placed plan is refused
 * outside the village, beyond 96 blocks and over a finished building, and goes down elsewhere in all four turns.
 */
public class WonderPlanGameTests implements FabricGameTest {
	private static final String SMALL = "aliveworkplace_test:build_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final ResourceLocation WHOLE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "wonder/test_folly");
	private static final ResourceLocation DRAWN = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "wonder_drawn/test_folly");
	private static final BlockPos HALL = new BlockPos(8, 2, 8);

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		List<String> out = new ArrayList<>();
		if (lore != null) {
			lore.lines().forEach(l -> out.add(l.getString()));
		}
		return out;
	}

	private static String key(Component c) {
		return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, VillagerProfession job, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
		return v;
	}

	/** A Town round {@code HALL} owned by the returned player, with a Master builder and two farmers; Legends stand in. */
	private static ServerPlayer town(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean legends = Wonders.LEGENDS;
		Wonders.LEGENDS = false; // the Master Architect's stand-in: a Master builder
		Leftovers.after(helper, () -> Wonders.LEGENDS = legends);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setOwner(player.getUUID(), "Owner");
		entity.setRank(VillageRanks.Rank.TOWN);
		entity.setTreasury(0);
		villager(helper, new BlockPos(3, 2, 3), ModVillagers.BUILDER, 5);
		villager(helper, new BlockPos(13, 2, 3), VillagerProfession.FARMER, 1);
		villager(helper, new BlockPos(8, 2, 13), VillagerProfession.FARMER, 1); // two stay when the builder leaves
		return player;
	}

	private static Wonder folly(GameTestHelper helper) {
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
		return folly;
	}

	// ---------------------------------------------------------------- the whole blueprint

	/** The folly's three parts merge into the tower as drawn; no part sets air where an earlier stage put a block; it's hidden from the Table. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theFollysPartsMergeIntoTheTower(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Wonder folly = folly(helper);
		Optional<Blueprint> whole = BlueprintLibrary.get(level, WHOLE);
		Optional<Blueprint> drawn = BlueprintLibrary.get(level, DRAWN);
		helper.assertTrue(whole.isPresent() && drawn.isPresent(), "the whole folly " + whole.isPresent() + ", as drawn " + drawn.isPresent());
		helper.assertTrue(whole.get().size().equals(drawn.get().size()), "size " + whole.get().size() + " vs drawn " + drawn.get().size());
		Map<BlockPos, BlockState> merged = new HashMap<>();
		whole.get().blocks().stream().filter(e -> !e.state().isAir()).forEach(e -> merged.put(e.pos(), e.state()));
		Map<BlockPos, BlockState> asDrawn = new HashMap<>();
		drawn.get().blocks().stream().filter(e -> !e.state().isAir()).forEach(e -> asDrawn.put(e.pos(), e.state()));
		helper.assertTrue(merged.size() > 400 && merged.equals(asDrawn), "merged " + merged.size() + " blocks, drawn " + asDrawn.size()
			+ "; first difference at " + asDrawn.keySet().stream().filter(p -> !asDrawn.get(p).equals(merged.get(p))).findFirst());
		helper.assertTrue(whole.get().blocks().stream().anyMatch(e -> e.state().isAir()), "stage I clears the site: it has air");
		// the same blueprint object again: cached
		helper.assertTrue(BlueprintLibrary.get(level, WHOLE).get() == whole.get(), "the whole folly isn't cached");

		// stage by stage, no part sets air where an earlier stage put a block
		Set<BlockPos> built = new HashSet<>();
		int parts = 0;
		for (Wonder.Stage stage : folly.stages()) {
			Set<BlockPos> now = new HashSet<>();
			for (Wonder.Part part : stage.parts()) {
				Blueprint piece = BlueprintLibrary.get(level, part.part()).orElse(null);
				helper.assertTrue(piece != null, "missing part " + part.part());
				helper.assertTrue(piece.size().getX() <= 48 && piece.size().getY() <= 48 && piece.size().getZ() <= 48, "a part over 48 a side: " + piece.size());
				parts++;
				for (Blueprint.Entry e : piece.blocks()) {
					BlockPos pos = e.pos().offset(part.offset());
					helper.assertTrue(!(e.state().isAir() && built.contains(pos)), part.part() + " sets air over an earlier stage's block at " + pos);
					if (!e.state().isAir()) {
						now.add(pos);
					}
				}
			}
			built.addAll(now);
		}
		helper.assertTrue(parts == 3 && WonderBlueprints.missingParts(level.getServer(), folly).isEmpty(), "three parts, none missing: " + parts);
		helper.assertTrue(BlueprintLibrary.isWorldgenPiece(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "wonder/great_cathedral/stage_1_0")),
			"wonder/ isn't hidden from the Blueprint Table");
		helper.assertTrue(BlueprintLibrary.list(level.getServer(), false).stream().noneMatch(id -> id.getPath().startsWith("wonder")), "a Wonder blueprint on the Table");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the page

	/** The folly's card ticks every line and offers the plans; one unmet line (no Master builder) shows crossed; the cap; "What next?". */
	//$ gametest_batch SMALL '"wonderPage"'
	@GameTest(template = SMALL, batch = "wonderPage")
	public void thePageLinesMatchTheConditions(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = town(helper);
		BlockPos hall = helper.absolutePos(HALL);
		Wonder folly = folly(helper);
		int index = new ArrayList<>(Wonders.all()).indexOf(folly);
		helper.assertTrue(index >= 0, "the folly isn't on the list");

		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		int tab = HallPages.slot(WondersPage.PAGE);
		helper.assertTrue(tab >= 0 && menu.icon(tab).getHoverName().getString().equals("Wonders")
			&& lore(menu.icon(tab)).contains("Wonders: 0 of 1 for a Town"), "the Wonders tab: " + menu.icon(tab).getHoverName().getString() + " " + lore(menu.icon(tab)));
		menu.press(tab, player);
		ItemStack card = menu.icon(WondersPage.slot(index));
		List<String> lines = lore(card);
		helper.assertTrue(card.is(Items.STONE_BRICKS) && card.getHoverName().getString().equals("Test Folly"), "the card's icon and name: " + card + " " + card.getHoverName().getString());
		helper.assertTrue(lines.get(0).equals("A stone tower with a dome: +2 beauty, and the village 4 happier"), "the power line: " + lines);
		helper.assertTrue(lines.contains("  ✔ Standing in for the Master Architect: Builders at Master or above: 1 of 1"), "the Master builder's line: " + lines);
		for (Condition.Progress p : Wonders.requirements(level, hall, folly)) {
			helper.assertTrue(lines.contains("  ✔ " + p.line().getString()), "not ticked: " + p.line().getString() + " in " + lines);
		}
		helper.assertTrue(lines.stream().noneMatch(l -> l.startsWith("  ✘")) && lines.contains("Wonders: 0 of 1 for a Town")
			&& lines.contains("Click: Draw up the plans (32 emeralds)"), "everything ticked, the cap, the plans offered: " + lines);
		List<VillageAdvice.Tip> ready = WondersPage.tips(level, hall);
		helper.assertTrue(ready.size() == 1 && ready.get(0).key().equals("wonder_ready") && ready.get(0).title().getString().equals("The Test Folly can be begun"),
			"What next? names the folly ready: " + ready.stream().map(t -> t.title().getString()).toList());
		helper.assertTrue(VillageAdvice.tips(level, hall).stream().anyMatch(t -> t.key().equals("wonder_ready")), "the tip isn't on What next?");

		// the Master builder leaves: that one line is crossed, the rest still ticked, no plans
		level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(hall).inflate(16),
			v -> v.getVillagerData().getProfession() == ModVillagers.BUILDER).forEach(v -> v.discard());
		menu = VillageHallScreen.forTest(player, hall);
		menu.press(tab, player);
		lines = lore(menu.icon(WondersPage.slot(index)));
		List<String> crossed = lines.stream().filter(l -> l.startsWith("  ✘")).toList();
		helper.assertTrue(crossed.size() == 1 && lines.stream().filter(l -> l.startsWith("  ✔")).count() == 2
			&& lines.contains("Draw up the plans once every line is ticked"), "one line crossed: " + lines);
		List<VillageAdvice.Tip> close = WondersPage.tips(level, hall);
		helper.assertTrue(close.size() == 1 && close.get(0).key().equals("wonder") && close.get(0).title().getString().startsWith("The Test Folly is nearly in reach: ")
			&& crossed.get(0).endsWith(((Component) close.get(0).args()[1]).getString()), "What next? names the missing line: " + close.stream().map(t -> t.title().getString()).toList());
		// a click says why, takes nothing, gives nothing
		player.getInventory().add(new ItemStack(Items.EMERALD, 32));
		menu.press(WondersPage.slot(index), player);
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 32 && player.getInventory().countItem(ModItems.WONDER_PLAN) == 0, "drawn without its requirements");

		// a Village has no room for one: no tip
		((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setRank(VillageRanks.Rank.VILLAGE);
		helper.assertTrue(WondersPage.tips(level, hall).isEmpty(), "a tip before the village is a Town");
		helper.succeed();
	}

	// ---------------------------------------------------------------- drawing up the plans

	/** The card's click takes the player's 32 emeralds and gives the plan; the treasury pays when it can; over the cap it's refused. */
	//$ gametest_batch SMALL '"wonderDraw"'
	@GameTest(template = SMALL, batch = "wonderDraw")
	public void drawingUpThePlansTakesTheEmeralds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = town(helper);
		BlockPos hall = helper.absolutePos(HALL);
		Wonder folly = folly(helper);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		int index = new ArrayList<>(Wonders.all()).indexOf(folly);
		player.getInventory().add(new ItemStack(Items.EMERALD, 40));

		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(HallPages.slot(WondersPage.PAGE), player);
		menu.press(WondersPage.slot(index), player);
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 8, "32 of the player's 40 emeralds taken: " + player.getInventory().countItem(Items.EMERALD));
		ItemStack plan = ItemStack.EMPTY;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(ModItems.WONDER_PLAN)) {
				plan = player.getInventory().getItem(i);
			}
		}
		helper.assertTrue(!plan.isEmpty() && WonderPlanItem.plan(plan).map(p -> p.wonder().equals(FOLLY) && p.hall().pos().equals(hall)).orElse(false)
			&& BlueprintItem.data(plan).map(d -> d.structure().equals(WHOLE) && d.size().isPresent()).orElse(false)
			&& plan.getHoverName().getString().equals("Wonder Plan: Test Folly"), "the plan: " + plan + " " + plan.getComponents());

		// the treasury pays when it holds the price
		entity.setTreasury(5000);
		WonderPlans.Drawn second = WonderPlans.draw(level, hall, player, FOLLY);
		helper.assertTrue(second.drawn() && second.payer() == WonderPlans.Payer.HALL && entity.treasury() == 1800
			&& player.getInventory().countItem(Items.EMERALD) == 8, "the treasury paid: " + second.payer() + ", left " + entity.treasury());

		// neither has it: refused, nothing taken
		entity.setTreasury(100);
		WonderPlans.Drawn poor = WonderPlans.draw(level, hall, player, FOLLY);
		helper.assertTrue(!poor.drawn() && key(poor.refusal()).equals("message.aliveworkplace.wonder_plan.money") && entity.treasury() == 100
			&& player.getInventory().countItem(Items.EMERALD) == 8, "refused for money: " + key(poor.refusal()));

		// over the cap: a Town holding a Wonder already
		player.getInventory().add(new ItemStack(Items.EMERALD, 32));
		WonderManager manager = WonderManager.get(level);
		WonderSite other = manager.begin(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "other_wonder"), hall, player.getUUID(), "Owner", 1);
		manager.finish(other.id(), 2);
		Leftovers.after(helper, () -> manager.remove(other.id()));
		WonderPlans.Drawn capped = WonderPlans.draw(level, hall, player, FOLLY);
		helper.assertTrue(!capped.drawn() && key(capped.refusal()).equals("wonder.aliveworkplace.refused.cap") && player.getInventory().countItem(Items.EMERALD) == 40,
			"over the cap: " + key(capped.refusal()));
		menu = VillageHallScreen.forTest(player, hall);
		menu.press(HallPages.slot(WondersPage.PAGE), player);
		List<String> lines = lore(menu.icon(WondersPage.slot(index)));
		helper.assertTrue(lines.contains("Wonders: 1 of 1 for a Town") && lines.stream().noneMatch(l -> l.startsWith("Click: Draw up")), "the card over the cap: " + lines);

		// a stranger may not draw them up
		manager.remove(other.id());
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		stranger.getInventory().add(new ItemStack(Items.EMERALD, 32));
		WonderPlans.Drawn theirs = WonderPlans.draw(level, hall, stranger, FOLLY);
		helper.assertTrue(!theirs.drawn() && key(theirs.refusal()).equals("message.aliveworkplace.wonder_plan.not_yours")
			&& stranger.getInventory().countItem(Items.EMERALD) == 32, "a stranger drew them up: " + key(theirs.refusal()));
		helper.succeed();
	}

	// ---------------------------------------------------------------- placing the plan

	private static void use(ServerPlayer player, BlockPos ground, boolean sneak) {
		player.setShiftKeyDown(sneak);
		ItemStack stack = player.getMainHandItem();
		stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false)));
		player.setShiftKeyDown(false);
	}

	private static Optional<BlueprintData.Placement> placed(ServerPlayer player) {
		return BlueprintItem.data(player.getMainHandItem()).flatMap(BlueprintData::placement);
	}

	/** placeRefusal's reason for the plan at {@code at} turned {@code rotation}, with the records it looks at near the hall. */
	private static String why(ServerLevel level, ItemStack plan, BlockPos at, Rotation rotation) {
		WonderPlanItem.Plan p = WonderPlanItem.plan(plan).get();
		net.minecraft.core.Vec3i size = BlueprintItem.data(plan).get().size().get();
		BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), size, at, rotation);
		String reason = WonderPlans.placeRefusal(level, p, placement, size).map(c -> key(c) + " (" + c.getString() + ")").orElse("none");
		BlockPos hall = p.hall().pos();
		List<String> near = new ArrayList<>();
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.finishedNear(level, hall, WonderPlans.REACH + 2 * io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS)
			.forEach(f -> near.add("finished " + f.structure() + " at " + f.placement().origin().subtract(hall).toShortString()));
		sites.all().stream().filter(s -> s.structure().getPath().startsWith(WonderBlueprints.FOLDER))
			.forEach(s -> near.add("Wonder site " + s.structure() + " at " + s.placement().origin().subtract(hall).toShortString()));
		return "refusal " + reason + "; near the hall: " + near;
	}

	/** Refused outside the village, beyond 96 blocks and over a finished building, each saying why; elsewhere it goes down and turns all four ways. */
	//$ gametest_batch SMALL '"wonderPlace"'
	@GameTest(template = SMALL, batch = "wonderPlace")
	public void placingThePlanKeepsToTheVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = town(helper);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.wonderGround(helper, hall); // B125: buildings and Wonder sites earlier batches left round the hall
		Wonder folly = folly(helper);
		ItemStack plan = WonderPlanItem.of(level, hall, folly, Component.literal("Testholm"));
		player.setItemInHand(InteractionHand.MAIN_HAND, plan);
		player.setYRot(180); // facing north: the plan's front faces the player, south

		// outside the village: its centre 80 blocks out
		use(player, hall.offset(80, -1, 0), false);
		helper.assertTrue(placed(player).isEmpty(), "placed outside the village");
		helper.assertTrue(WonderPlans.placeRefusal(level, WonderPlanItem.plan(plan).get(), BlueprintItem.placementAt(level.dimension().location(),
			BlueprintItem.data(plan).get().size().get(), hall.offset(80, 0, 0), Rotation.NONE), BlueprintItem.data(plan).get().size().get())
			.map(WonderPlanGameTests::key).orElse("").equals("message.aliveworkplace.wonder_plan.outside"), "the reason: outside");

		// over a finished building
		Blueprint cottage = BlueprintLibrary.get(level, StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow();
		BlueprintData.Placement there = new BlueprintData.Placement(level.dimension().location(), hall.offset(20, 0, 20), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.STARTER_COTTAGE.id(), there, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(there));
		BlockPos onIt = hall.offset(20 + cottage.size().getX() / 2, -1, 20 + cottage.size().getZ() / 2);
		use(player, onIt, false);
		helper.assertTrue(placed(player).isEmpty(), "placed over a finished building");
		BlueprintData.Placement over = BlueprintItem.placementAt(level.dimension().location(), BlueprintItem.data(plan).get().size().get(), onIt.above(), Rotation.NONE);
		helper.assertTrue(WonderPlans.placeRefusal(level, WonderPlanItem.plan(plan).get(), over, BlueprintItem.data(plan).get().size().get())
			.map(WonderPlanGameTests::key).orElse("").equals("message.aliveworkplace.wonder_plan.over_building"), "the reason: over a building");

		// elsewhere in the village: down, then turned all four ways
		BlockPos ground = hall.offset(-20, -1, -20);
		use(player, ground, false);
		helper.assertTrue(placed(player).isPresent(), "not placed inside the village, away from buildings: " + why(level, plan, ground.above(), Rotation.NONE));
		Set<Rotation> turns = new HashSet<>();
		turns.add(placed(player).get().rotation());
		for (int i = 0; i < 3; i++) {
			BlueprintData.Placement before = placed(player).get();
			use(player, ground, true);
			helper.assertTrue(placed(player).isPresent() && placed(player).get().rotation() == before.rotation().getRotated(Rotation.CLOCKWISE_90),
				"turn " + (i + 1) + " refused: " + placed(player) + ", " + why(level, plan, ground.above(), before.rotation().getRotated(Rotation.CLOCKWISE_90)));
			turns.add(placed(player).get().rotation());
		}
		helper.assertTrue(turns.size() == 4, "all four turns: " + turns);
		helper.succeed();
	}
}
