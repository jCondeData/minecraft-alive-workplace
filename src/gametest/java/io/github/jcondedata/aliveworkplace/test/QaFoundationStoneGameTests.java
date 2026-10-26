package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (ROADMAP 35.4), from the spec: the Foundation Stone's material list counts the chests within 8 blocks of it (8
 * counts, 9 doesn't) and reads as the part's blueprint tooltip does; the start reaches every player on the server and
 * the chronicle; the owner's {@code /workplace cancel} ends the Wonder and gives the plan back exactly once, a
 * stranger's is refused; a piston can't push the stone; the stage order and "only stage I fills a foundation" hold
 * through a save and reload at every stage boundary; and a Wonder save from before (or none) loads and saves unchanged.
 */
public class QaFoundationStoneGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos BUILDER = new BlockPos(3, 2, 3);

	private record Village(ServerLevel level, ServerPlayer owner, BlockPos hall, Villager builder) {
	}

	/** A Town round {@code hallRel} owned by {@code owner} (a listening player), a builder employed at a bench by it. */
	private static Village village(GameTestHelper helper, BlockPos hallRel, ServerPlayer owner) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Vec3 by = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		owner.moveTo(by.x, by.y, by.z);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(hallRel);
		hall.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		hall.setRank(VillageRanks.Rank.TOWN);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BUILDER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		return new Village(level, owner, helper.absolutePos(hallRel), builder);
	}

	/** The folly's plan for {@code v}'s village, placed with its middle (template 4, 0, 4) at {@code middle} (world). */
	private static ItemStack plan(GameTestHelper helper, Village v, BlockPos middle) {
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
		ItemStack plan = WonderPlanItem.of(v.level(), v.hall(), folly, Component.literal("Testholm"));
		BlueprintData data = BlueprintItem.data(plan).orElseThrow();
		BlockPos origin = middle.subtract(new BlockPos(4, 0, 4));
		plan.set(ModComponents.BLUEPRINT, data.withPlacement(Optional.of(new BlueprintData.Placement(v.level().dimension().location(), origin, Rotation.NONE, Mirror.NONE))));
		return plan;
	}

	private static BlueprintData.Placement placement(ItemStack plan) {
		return BlueprintItem.data(plan).flatMap(BlueprintData::placement).orElseThrow();
	}

	/** Hands the placed plan over (stage I's goods in the stockpile first, so the stage starts at once); returns the Wonder. */
	private static WonderSite begin(GameTestHelper helper, Village v, BlockPos middle) {
		ItemStack plan = plan(helper, v, middle);
		BlueprintData.Placement at = placement(plan);
		WonderGoodsKit.stockAll(v.level(), FOLLY, at);
		helper.assertTrue(WonderStages.handOver(v.owner(), v.builder(), plan) == InteractionResult.SUCCESS, "the plan wasn't handed over");
		List<WonderSite> here = WonderManager.get(v.level()).in(v.hall());
		helper.assertTrue(here.size() == 1, "the Wonder wasn't begun: " + here);
		WonderSite site = here.get(0);
		Leftovers.after(helper, () -> forget(v.level(), site.id()));
		return site;
	}

	private static void forget(ServerLevel level, UUID wonder) {
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite s : List.copyOf(sites.all())) {
			if (wonder.equals(s.wonder())) {
				sites.remove(s.id());
			}
		}
		WonderManager.get(level).remove(wonder);
	}

	private static String all(List<Component> seen) {
		return seen.stream().map(Component::getString).toList().toString();
	}

	private static int plansOnTheGround(GameTestHelper helper) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(40),
			e -> e.getItem().is(ModItems.WONDER_PLAN))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	/** Every saved manager read back from its save, as a restart does. */
	private static void restart(ServerLevel level) {
		var registries = level.registryAccess();
		level.getDataStorage().set(WonderManager.NAME, WonderManager.load(WonderManager.get(level).save(new CompoundTag(), registries), registries));
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
	}

	private static void finish(BuildSite site) {
		for (int i = 0; i < 20 && !site.isDone(); i++) {
			site.finishList();
		}
	}

	// ---------------------------------------------------------------- the stone's material list

	/**
	 * Right-clicking the stone (a real click with an empty hand) shows stage I and its material list as the part's
	 * blueprint tooltip lists it (each item, the same count). The stockpile is the chests within 8 blocks: the top
	 * line's item in a chest 9 blocks away counts for nothing ("N X, N still to bring"); one more in a chest 8 blocks
	 * away brings it down by one, in the line the player reads.
	 */
	//$ gametest_ticks_batch TALL '200' '"qaStoneReach"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaStoneReach")
	public void theStonesListCountsChestsEightBlocksAwayNotNine(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		Village v = village(helper, new BlockPos(2, 2, 27), WonderFinishedGameTests.listener(helper, seen, "QaStoneReach"));
		ServerLevel level = v.level();
		WonderSite site = begin(helper, v, helper.absolutePos(new BlockPos(14, 2, 14)));
		BlockPos stone = site.stone().orElseThrow();
		helper.assertTrue(stone.equals(helper.absolutePos(new BlockPos(9, 2, 9))), "the stone isn't outside the front corner: " + helper.relativePos(stone));
		site = WonderManager.get(level).site(site.id()).orElseThrow();
		helper.assertTrue(site.started() && site.parts().size() == 1, "stage I didn't start with its goods in: " + site);

		// the list as the part's blueprint tooltip shows it
		BuildSite part = BuildSiteManager.get(level).get(site.parts().get(0));
		BlueprintSupplies.Checklist tooltip = BlueprintSupplies.checklist(level, new BlueprintData(part.structure(),
			BlueprintLibrary.get(level, part.structure()).map(Blueprint::size), Optional.empty())).orElseThrow();
		Map<Item, Integer> drawn = new LinkedHashMap<>();
		tooltip.lines().forEach(l -> drawn.merge(l.item(), l.need(), Integer::sum));
		List<WonderStages.Line> lines = WonderStages.materials(level, site, stone);
		Map<Item, Integer> listed = new LinkedHashMap<>();
		lines.forEach(l -> listed.merge(l.item(), l.need(), Integer::sum));
		helper.assertTrue(listed.equals(drawn), "the stone's list " + listed + " isn't the blueprint tooltip's " + drawn);
		helper.assertTrue(lines.stream().allMatch(l -> l.toBring() == l.need()), "something counted with no chest of blocks near: " + lines);

		WonderStages.Line top = lines.get(0);
		BlockPos far = stone.offset(-9, 0, 0);
		BlockPos near = stone.offset(-8, 0, 0);
		level.setBlockAndUpdate(far, Blocks.CHEST.defaultBlockState());
		((Container) level.getBlockEntity(far)).setItem(0, new ItemStack(top.item(), Math.min(top.need(), top.item().getDefaultMaxStackSize())));
		WonderStages.Line after = WonderStages.materials(level, site, stone).stream().filter(l -> l.item() == top.item()).findFirst().orElseThrow();
		helper.assertTrue(after.toBring() == top.need(), "a chest 9 blocks from the stone counted: " + after);

		level.setBlockAndUpdate(near, Blocks.CHEST.defaultBlockState());
		((Container) level.getBlockEntity(near)).setItem(0, new ItemStack(top.item(), 1));
		after = WonderStages.materials(level, site, stone).stream().filter(l -> l.item() == top.item()).findFirst().orElseThrow();
		helper.assertTrue(after.toBring() == top.need() - 1, "a chest 8 blocks from the stone didn't count: " + after);

		// what the player reads on a real right-click
		seen.clear();
		v.owner().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(stone), Direction.UP, stone, false);
		level.getBlockState(stone).useWithoutItem(level, v.owner(), hit);
		helper.assertTrue(!seen.isEmpty(), "the right-click said nothing");
		String text = seen.get(seen.size() - 1).getString();
		String expected = top.need() + " " + top.item().getDescription().getString() + ", " + (top.need() - 1) + " still to bring";
		helper.assertTrue(text.contains("Stage 1 of 3, The Ground Storey: 0% built") && text.contains("This stage still needs (from the chests within 8 blocks):")
			&& text.contains(expected), "the stone said: " + text + " (expected a line '" + expected + "')");
		helper.assertTrue(!text.contains("aliveworkplace.") && !text.contains("%s") && !text.contains("%%"), "a raw key or placeholder in: " + text);
		((Container) level.getBlockEntity(far)).clearContent();
		((Container) level.getBlockEntity(near)).clearContent();
		helper.succeed();
	}

	// ---------------------------------------------------------------- the start, the cancel, the stone

	/** The start reaches every player on the server, not only the owner, naming the owner, the Wonder and the village; and it's in the chronicle. */
	//$ gametest_batch AREA '"qaStoneAnnounce"'
	@GameTest(template = AREA, batch = "qaStoneAnnounce")
	public void theStartReachesEveryPlayerAndTheChronicle(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> ownerSaw = new ArrayList<>();
		List<Component> otherSaw = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, ownerSaw, "QaAnnouncer");
		WonderFinishedGameTests.listener(helper, otherSaw, "QaBystander");
		Village v = village(helper, new BlockPos(14, 2, 2), owner);
		WonderSite site = begin(helper, v, helper.absolutePos(new BlockPos(9, 2, 9)));
		String hallName = io.github.jcondedata.aliveworkplace.hall.VillageHalls.name(v.level(), v.hall()).getString();
		String announce = "QaAnnouncer has begun the Test Folly in " + hallName + "!";
		helper.assertTrue(otherSaw.stream().anyMatch(c -> c.getString().equals(announce)), "another player didn't read '" + announce + "': " + all(otherSaw));
		helper.assertTrue(ownerSaw.stream().anyMatch(c -> c.getString().equals(announce)), "the owner didn't read '" + announce + "': " + all(ownerSaw));
		helper.assertTrue(otherSaw.stream().noneMatch(c -> c.getString().contains("lays the Foundation Stone")), "the bystander was told the owner's hand-over line");
		VillageHallBlockEntity hall = (VillageHallBlockEntity) v.level().getBlockEntity(v.hall());
		List<Chronicle.Entry> wonderEntries = hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.WONDER).toList();
		helper.assertTrue(wonderEntries.stream().filter(e -> e.text().getString().equals("QaAnnouncer laid the Foundation Stone of the Test Folly")).count() == 1,
			"the chronicle: " + wonderEntries.stream().map(e -> e.text().getString()).toList());
		helper.assertTrue(site.begun() == Chronicle.day(v.level()), "the day it was begun: " + site.begun());
		helper.succeed();
	}

	/**
	 * {@code /workplace cancel <part>} typed by a stranger (no operator) is refused and the Wonder goes on; typed by the
	 * owner (no operator) it ends the Wonder: the open part ends, what's built stays, the plan comes back exactly once
	 * (in the owner's inventory, none on the ground), the chronicle says so; typed again it finds no build and gives
	 * no second plan.
	 */
	//$ gametest_batch AREA '"qaStoneCommand"'
	@GameTest(template = AREA, batch = "qaStoneCommand")
	public void theOwnersCommandCancelGivesThePlanOnceAStrangersIsRefused(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> ownerSaw = new ArrayList<>();
		List<Component> strangerSaw = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, ownerSaw, "QaCancelOwner");
		ServerPlayer stranger = WonderFinishedGameTests.listener(helper, strangerSaw, "QaCancelStranger");
		helper.assertTrue(!owner.hasPermissions(2) && !stranger.hasPermissions(2), "the test players are operators: the test proves nothing");
		Village v = village(helper, new BlockPos(14, 2, 2), owner);
		ServerLevel level = v.level();
		WonderSite site = begin(helper, v, helper.absolutePos(new BlockPos(9, 2, 9)));
		site = WonderManager.get(level).site(site.id()).orElseThrow();
		helper.assertTrue(site.parts().size() == 1, "stage I has no part to cancel: " + site);
		UUID part = site.parts().get(0);
		UUID id = site.id();
		BlockPos built = helper.absolutePos(new BlockPos(9, 2, 9));
		level.setBlockAndUpdate(built, Blocks.STONE_BRICKS.defaultBlockState()); // a block "already built"
		var commands = level.getServer().getCommands();

		commands.performPrefixedCommand(stranger.createCommandSourceStack(), "workplace cancel " + part);
		helper.assertTrue(WonderManager.get(level).site(id).isPresent() && BuildSiteManager.get(level).get(part) != null,
			"a stranger's command ended the Wonder: " + all(strangerSaw));
		helper.assertTrue(strangerSaw.stream().anyMatch(c -> c.getString().contains("Only the player who started this build (or an operator) can cancel it.")),
			"the stranger wasn't told why: " + all(strangerSaw));
		helper.assertTrue(stranger.getInventory().countItem(ModItems.WONDER_PLAN) == 0, "the stranger got a plan");

		commands.performPrefixedCommand(owner.createCommandSourceStack(), "workplace cancel " + part);
		helper.assertTrue(WonderManager.get(level).site(id).isEmpty(), "the owner's command didn't end the Wonder: " + all(ownerSaw));
		helper.assertTrue(BuildSiteManager.get(level).get(part) == null, "the open part goes on");
		helper.assertTrue(ownerSaw.stream().anyMatch(c -> c.getString().equals("Canceled the Wonder. What's built stays, and its Wonder Plan has come back.")),
			"the owner wasn't told: " + all(ownerSaw));
		helper.assertTrue(owner.getInventory().countItem(ModItems.WONDER_PLAN) == 1 && plansOnTheGround(helper) == 0,
			"plans: " + owner.getInventory().countItem(ModItems.WONDER_PLAN) + " in the inventory, " + plansOnTheGround(helper) + " on the ground");
		helper.assertTrue(level.getBlockState(built).is(Blocks.STONE_BRICKS), "what's built was taken down");
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(v.hall());
		helper.assertTrue(hall.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.WONDER && e.text().getString().contains("Test Folly")
			&& e.text().getString().toLowerCase().contains("abandon")), "the chronicle doesn't say so: " + hall.chronicle().stream().map(e -> e.text().getString()).toList());

		ownerSaw.clear();
		commands.performPrefixedCommand(owner.createCommandSourceStack(), "workplace cancel " + part);
		helper.assertTrue(owner.getInventory().countItem(ModItems.WONDER_PLAN) == 1 && plansOnTheGround(helper) == 0, "a second cancel gave a second plan");
		helper.assertTrue(ownerSaw.stream().noneMatch(c -> c.getString().startsWith("Canceled the Wonder")), "a second cancel said it canceled: " + all(ownerSaw));
		// the plan that came back is the folly's, for this village, and can be placed again (no placement on it)
		ItemStack back = ItemStack.EMPTY;
		for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
			if (owner.getInventory().getItem(i).is(ModItems.WONDER_PLAN)) {
				back = owner.getInventory().getItem(i);
			}
		}
		helper.assertTrue(WonderPlanItem.plan(back).map(p -> p.wonder().equals(FOLLY) && p.hall().pos().equals(v.hall())).orElse(false)
			&& BlueprintItem.data(back).flatMap(BlueprintData::placement).isEmpty(), "the plan that came back: " + back.getComponents());
		level.setBlockAndUpdate(built, Blocks.AIR.defaultBlockState());
		helper.succeed();
	}

	/** A powered piston facing the stone of a Wonder going up doesn't move it (the Wonder keeps its stone and stockpile). */
	//$ gametest_ticks_batch AREA '100' '"qaStonePiston"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaStonePiston")
	public void aPistonCannotPushTheStone(GameTestHelper helper) {
		Leftovers.clear(helper);
		Village v = village(helper, new BlockPos(14, 2, 2), WonderFinishedGameTests.listener(helper, new ArrayList<>(), "QaPiston"));
		WonderSite site = begin(helper, v, helper.absolutePos(new BlockPos(9, 2, 9)));
		BlockPos stone = site.stone().orElseThrow();
		ServerLevel level = v.level();
		helper.assertTrue(stone.equals(placement(plan(helper, v, helper.absolutePos(new BlockPos(9, 2, 9)))).origin().offset(-1, 0, -1)), "the stone at " + stone);
		BlockPos redstone = stone.west(2);
		level.setBlockAndUpdate(stone.west(), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
		level.setBlockAndUpdate(redstone, Blocks.REDSTONE_BLOCK.defaultBlockState());
		Leftovers.after(helper, () -> {
			level.setBlockAndUpdate(redstone, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(stone.west(), Blocks.AIR.defaultBlockState());
		});
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(v.level().getBlockState(stone).is(ModBlocks.FOUNDATION_STONE), "the stone was pushed away: " + v.level().getBlockState(stone));
			helper.assertTrue(!v.level().getBlockState(stone.east()).is(ModBlocks.FOUNDATION_STONE), "the stone moved east");
			helper.assertTrue(WonderStages.atStone(v.level(), stone).isPresent(), "the Wonder lost its stone");
			helper.assertTrue(level.getBlockState(stone.west()).is(Blocks.PISTON) && level.getBlockState(stone.west()).getValue(PistonBaseBlock.EXTENDED) == false,
				"the piston extended over the stone: " + level.getBlockState(stone.west()));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- restarts at the stage boundaries

	/**
	 * A restart at every point round each stage boundary: mid-stage I (stage II never starts), right after stage I's
	 * last part is done but before the check (it starts after, not before), while stage II waits for its goods (it
	 * still waits, with no parts), and after its goods are handed in (it starts). Only stage I's parts fill a
	 * foundation, read back after the reload too; the folly ends done.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaStoneRestarts"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaStoneRestarts")
	public void restartsAtEveryStageBoundaryKeepTheOrder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos origin = new BlockPos(4300, 70, 4000);
		WonderSite begun = WonderManager.get(level).begin(FOLLY, origin, UUID.randomUUID(), "QaRestart", 1);
		UUID id = begun.id();
		Leftovers.after(helper, () -> forget(level, id));
		BlueprintData.Placement at = new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.CLOCKWISE_180, Mirror.FRONT_BACK);
		UUID builder = UUID.randomUUID();
		WonderStages.start(level, id, at, origin.offset(1, 0, 1), builder);
		for (int stage = 1; stage <= 3; stage++) {
			final int s = stage;
			WonderSite now = WonderManager.get(level).site(id).orElseThrow();
			helper.assertTrue(now.stage() == stage && now.started() && !now.parts().isEmpty(), "stage " + stage + " isn't going: " + now);
			// mid-stage: a restart and a few checks don't move it on
			restart(level);
			for (int i = 0; i < 3; i++) {
				WonderStages.tick(level);
			}
			now = WonderManager.get(level).site(id).orElseThrow();
			helper.assertTrue(now.stage() == stage, "stage " + (stage + 1) + " began after a restart mid-stage " + stage + ": " + now);
			for (UUID p : now.parts()) {
				BuildSite part = BuildSiteManager.get(level).get(p);
				helper.assertTrue(part != null && id.equals(part.wonder()) && part.foundation() == (s == 1),
					"after a restart, a part of stage " + s + (s == 1 ? " doesn't fill" : " fills") + " a foundation: " + (part == null ? "gone" : part.foundation()));
			}
			// every part done, then a restart before the check
			for (UUID p : now.parts()) {
				finish(BuildSiteManager.get(level).get(p));
			}
			restart(level);
			helper.assertTrue(WonderManager.get(level).site(id).orElseThrow().stage() == stage, "the stage moved on in the save itself");
			WonderStages.tick(level);
			WonderSite next = WonderManager.get(level).site(id).orElseThrow();
			if (stage == 3) {
				helper.assertTrue(next.done(), "not finished after stage III and a restart: " + next);
				break;
			}
			helper.assertTrue(next.stage() == stage + 1 && next.parts().isEmpty() && !next.started(), "after stage " + stage + " and a restart: " + next);
			// waiting for its goods through a restart
			restart(level);
			WonderStages.tick(level);
			next = WonderManager.get(level).site(id).orElseThrow();
			helper.assertTrue(next.stage() == stage + 1 && next.parts().isEmpty() && !next.started(), "stage " + (stage + 1) + " began without its goods after a restart: " + next);
			WonderGoodsKit.handInStage(level, id);
			restart(level);
			WonderStages.tick(level);
		}
		helper.succeed();
	}

	/**
	 * A world with no Wonders saved, and one saved by 35.2 or 35.3 (a site with only its id, Wonder, hall, owner's name,
	 * stage and day begun), load with the defaults and save exactly as they were.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anOldWonderSaveLoadsAndSavesUnchanged(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		WonderManager none = WonderManager.load(new CompoundTag(), registries);
		helper.assertTrue(none.sites().isEmpty(), "sites from an empty save: " + none.sites());
		CompoundTag noneSaved = none.save(new CompoundTag(), registries);
		helper.assertTrue(WonderManager.load(noneSaved, registries).sites().isEmpty(), "an empty save read back with sites: " + noneSaved);

		UUID id = UUID.fromString("0f0e0d0c-0b0a-4908-8706-050403020100");
		CompoundTag site = new CompoundTag();
		site.put("id", NbtUtils.createUUID(id));
		site.putString("wonder", FOLLY.toString());
		site.put("hall", NbtUtils.writeBlockPos(new BlockPos(100, 64, -200)));
		site.putString("ownerName", "Jesse");
		site.putInt("stage", 2);
		site.putLong("begun", 7L);
		ListTag list = new ListTag();
		list.add(site);
		CompoundTag old = new CompoundTag();
		old.put("sites", list);
		CompoundTag before = old.copy();
		WonderManager read = WonderManager.load(old, registries);
		WonderSite s = read.site(id).orElse(null);
		helper.assertTrue(s != null, "the old site wasn't read: " + read.sites());
		helper.assertTrue(s.stage() == 2 && s.begun() == 7L && s.ownerName().equals("Jesse") && s.owner().isEmpty() && s.placement().isEmpty()
			&& s.stone().isEmpty() && s.parts().isEmpty() && s.builder().isEmpty() && s.finished().isEmpty() && s.delivered().isEmpty()
			&& !s.dedicated() && !s.called() && !s.done(), "the old site's defaults: " + s);
		CompoundTag after = read.save(new CompoundTag(), registries);
		helper.assertTrue(after.equals(before), "an old save didn't save unchanged:\n " + before + "\n " + after);
		helper.succeed();
	}
}
