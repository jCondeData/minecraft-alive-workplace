package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The Foundation Stone (ROADMAP 35.4): a placed Wonder Plan handed to a builder of the village lays the stone outside the
 * Wonder's front corner and begins it (refused, saying why, when the plan isn't placed, the builder is another
 * village's or has no bench, or Wonders are off); right-clicking the stone tells the stage, its progress and what the
 * chests are short of; only the owner or an operator, sneaking, can break it, which ends the Wonder and gives the plan
 * back. A real builder raises the folly from the chests round the stone, stage by stage, never starting stage II
 * before stage I is done, and the result matches the drawing in all four turns and mirrored; and a save and reload
 * mid-stage II (the manager, the sites and the builder) loses nothing.
 */
public class WonderSiteGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	/** 30 x 30 x 30: the folly is 19 tall and stands on y = 2. */
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final ResourceLocation WHOLE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "wonder/test_folly");
	private static final ResourceLocation DRAWN = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "wonder_drawn/test_folly");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos BUILDER = new BlockPos(3, 2, 3);
	/** The folly's middle (template 4, 0, 4) in the tall area: its footprint is x and z 10 to 18 in every turn. */
	private static final BlockPos MIDDLE = new BlockPos(14, 2, 14);

	private record Village(ServerLevel level, ServerPlayer owner, BlockPos hall, Villager builder) {
	}

	/** A Town round {@code hallRel}, owned by a player; a builder employed at a bench by it. */
	private static Village village(GameTestHelper helper, BlockPos hallRel) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity hall = helper.getBlockEntity(hallRel);
		hall.setOwner(player.getUUID(), "Owner");
		hall.setRank(VillageRanks.Rank.TOWN);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BUILDER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		return new Village(level, player, helper.absolutePos(hallRel), builder);
	}

	/** The folly's plan for {@code v}'s village, placed with its middle at {@code middle} (world), turned and mirrored. */
	private static ItemStack plan(GameTestHelper helper, Village v, BlockPos middle, Rotation turn, Mirror mirror) {
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
		ItemStack plan = WonderPlanItem.of(v.level(), v.hall(), folly, Component.literal("Testholm"));
		BlueprintData data = BlueprintItem.data(plan).orElseThrow();
		BlockPos origin = middle.subtract(StructureTemplate.transform(new BlockPos(4, 0, 4), mirror, turn, BlockPos.ZERO));
		plan.set(ModComponents.BLUEPRINT, data.withPlacement(Optional.of(new BlueprintData.Placement(v.level().dimension().location(), origin, turn, mirror))));
		return plan;
	}

	private static BlueprintData.Placement placement(ItemStack plan) {
		return BlueprintItem.data(plan).flatMap(BlueprintData::placement).orElseThrow();
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
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

	private static void forget(ServerLevel level, UUID wonder) {
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite s : List.copyOf(sites.all())) {
			if (wonder.equals(s.wonder())) {
				sites.remove(s.id());
			}
		}
		WonderManager.get(level).remove(wonder);
	}

	// ---------------------------------------------------------------- handing over, the stone

	/**
	 * Refused with its reason when the plan isn't placed, the builder has no bench or is another village's, and when
	 * Wonders are off; handed over properly, the stone goes down outside the front corner, the Wonder begins at stage I
	 * (in the chronicle), its part is queued for the builder with the stone as bench and a foundation, and the plan is
	 * used up. The stone's status names the stage and what the chests lack, and shows it all there once they hold it.
	 */
	//$ gametest_batch AREA '"wonderHandOver"'
	@GameTest(template = AREA, batch = "wonderHandOver")
	public void handingThePlanToABuilderLaysTheStone(GameTestHelper helper) {
		Village v = village(helper, new BlockPos(14, 2, 2));
		ServerLevel level = v.level();
		ItemStack plan = plan(helper, v, helper.absolutePos(new BlockPos(9, 2, 9)), Rotation.NONE, Mirror.NONE);
		BlueprintData.Placement at = placement(plan);
		v.owner().setItemInHand(InteractionHand.MAIN_HAND, plan);

		// not placed
		ItemStack loose = plan.copy();
		loose.set(ModComponents.BLUEPRINT, BlueprintItem.data(loose).orElseThrow().withPlacement(Optional.empty()));
		helper.assertTrue(WonderStages.handOver(v.owner(), v.builder(), loose) == InteractionResult.CONSUME
			&& WonderManager.get(level).in(v.hall()).isEmpty(), "an unplaced plan was taken");
		// Wonders off
		boolean on = Wonders.ENABLED;
		Wonders.ENABLED = false;
		InteractionResult off = WonderStages.handOver(v.owner(), v.builder(), plan);
		Wonders.ENABLED = on;
		helper.assertTrue(off == InteractionResult.CONSUME && WonderManager.get(level).in(v.hall()).isEmpty() && plan.getCount() == 1,
			"handed over with Wonders off");
		// a builder with no bench
		Villager jobless = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 2));
		jobless.setVillagerData(jobless.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER));
		helper.assertTrue(WonderStages.handOver(v.owner(), jobless, plan) == InteractionResult.CONSUME && WonderManager.get(level).in(v.hall()).isEmpty(),
			"a builder without a bench took it");
		jobless.discard();

		// handed over: the stone at the front corner, outside the Wonder (35.7: with stage I's goods in, it starts at once)
		WonderGoodsKit.stockAll(level, FOLLY, at);
		BlockPos stone = at.origin().offset(-1, 0, -1);
		helper.assertTrue(WonderStages.handOver(v.owner(), v.builder(), plan) == InteractionResult.SUCCESS, "not handed over");
		helper.assertTrue(level.getBlockState(stone).is(ModBlocks.FOUNDATION_STONE), "no Foundation Stone at " + stone + ": " + level.getBlockState(stone));
		helper.assertTrue(plan.isEmpty(), "the plan wasn't used up");
		List<WonderSite> here = WonderManager.get(level).in(v.hall());
		helper.assertTrue(here.size() == 1, "the Wonder wasn't begun: " + here);
		WonderSite site = here.get(0);
		Leftovers.after(helper, () -> forget(level, site.id()));
		helper.assertTrue(site.stage() == 1 && site.stone().equals(Optional.of(stone)) && site.placement().equals(Optional.of(at))
			&& site.owner().equals(Optional.of(v.owner().getUUID())), "the Wonder as begun: " + site);
		helper.assertTrue(site.parts().size() == 1, "stage I has one part: " + site.parts());
		BuildSite part = BuildSiteManager.get(level).get(site.parts().get(0));
		helper.assertTrue(part != null && stone.equals(part.bench()) && v.builder().getUUID().equals(part.builder()) && part.foundation()
			&& !part.levelGround() && site.id().equals(part.wonder()), "stage I's part: queued for the builder, the stone its bench, with a foundation");
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(v.hall());
		helper.assertTrue(hall.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.WONDER
			&& key(e.text()).equals("chronicle.aliveworkplace.wonder.begun")), "not in the chronicle");
		// a second plan of the same Wonder: refused (the village has it)
		ItemStack again = plan(helper, v, helper.absolutePos(new BlockPos(9, 2, 9)), Rotation.NONE, Mirror.NONE);
		helper.assertTrue(WonderStages.handOver(v.owner(), v.builder(), again) == InteractionResult.CONSUME && again.getCount() == 1
			&& WonderManager.get(level).in(v.hall()).size() == 1, "the same Wonder begun twice");

		// the stone's status: the stage, its progress, what's short; with the chests full, all there
		Component status = WonderStages.statusText(level, stone);
		List<String> said = keys(status);
		helper.assertTrue(said.contains("message.aliveworkplace.wonder.status.header") && said.contains("message.aliveworkplace.wonder.status.stage")
			&& said.contains("message.aliveworkplace.wonder.status.materials") && said.contains("message.aliveworkplace.wonder.status.short")
			&& said.contains("message.aliveworkplace.wonder.status.abandon"), "the status with nothing in the chests: " + said);
		helper.assertTrue(status.getString().contains("The Ground Storey"), "the stage's name: " + status.getString());
		List<WonderStages.Line> lines = WonderStages.materials(level, site, stone);
		helper.assertTrue(!lines.isEmpty() && lines.stream().allMatch(l -> l.toBring() == l.need()), "everything short: " + lines);
		BlockPos chestPos = stone.offset(-2, 0, 0);
		level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
		Container chest = (Container) level.getBlockEntity(chestPos);
		int slot = 0;
		for (WonderStages.Line l : lines) {
			for (int left = l.need(); left > 0 && slot < chest.getContainerSize(); left -= l.item().getDefaultMaxStackSize()) {
				chest.setItem(slot++, new ItemStack(l.item(), Math.min(left, l.item().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(slot < chest.getContainerSize(), "stage I fits a chest");
		said = keys(WonderStages.statusText(level, stone));
		helper.assertTrue(said.contains("message.aliveworkplace.wonder.status.all_there") && said.contains("message.aliveworkplace.wonder.status.there")
			&& !said.contains("message.aliveworkplace.wonder.status.short"), "the status with the chests full: " + said);
		helper.assertTrue(keys(WonderStages.statusText(level, stone.above())).contains("message.aliveworkplace.wonder.status.none"), "a stone of no Wonder");
		chest.clearContent();
		helper.succeed();
	}

	/** The builder of another village is refused, saying whose builder they are. */
	//$ gametest_batch AREA '"wonderOtherVillage"'
	@GameTest(template = AREA, batch = "wonderOtherVillage")
	public void anotherVillagesBuilderIsRefused(GameTestHelper helper) {
		Village v = village(helper, new BlockPos(14, 2, 2));
		ServerLevel level = v.level();
		// the plan is drawn for a hall far away (in its own Town), the builder's bench is here
		BlockPos far = v.hall().offset(200, 0, 0);
		level.setBlockAndUpdate(far, ModBlocks.VILLAGE_HALL.defaultBlockState());
		Leftovers.after(helper, () -> level.setBlockAndUpdate(far, Blocks.AIR.defaultBlockState()));
		((VillageHallBlockEntity) level.getBlockEntity(far)).setRank(VillageRanks.Rank.TOWN);
		Village there = new Village(level, v.owner(), far, v.builder());
		ItemStack plan = plan(helper, there, far.offset(10, 0, 10), Rotation.NONE, Mirror.NONE);
		helper.assertTrue(WonderStages.handOver(v.owner(), v.builder(), plan) == InteractionResult.CONSUME && plan.getCount() == 1
			&& WonderManager.get(level).in(far).isEmpty(), "another village's builder took the plan");
		helper.succeed();
	}

	/**
	 * Only the owner or an operator, sneaking, can break the stone: anyone else and a plain break are refused, the
	 * Wonder going on; the sneaking owner's break ends it, keeps what's built and gives the plan back.
	 */
	//$ gametest_batch AREA '"wonderStoneBreak"'
	@GameTest(template = AREA, batch = "wonderStoneBreak")
	public void sneakBreakingTheStoneEndsTheWonder(GameTestHelper helper) {
		Village v = village(helper, new BlockPos(14, 2, 2));
		ServerLevel level = v.level();
		ItemStack plan = plan(helper, v, helper.absolutePos(new BlockPos(9, 2, 9)), Rotation.CLOCKWISE_90, Mirror.FRONT_BACK);
		BlueprintData.Placement at = placement(plan);
		BlockPos stone = at.origin().offset(StructureTemplate.transform(new BlockPos(-1, 0, -1), at.mirror(), at.rotation(), BlockPos.ZERO));
		helper.assertTrue(WonderStages.handOver(v.owner(), v.builder(), plan) == InteractionResult.SUCCESS, "not handed over");
		helper.assertTrue(level.getBlockState(stone).is(ModBlocks.FOUNDATION_STONE), "the stone isn't at the turned, mirrored front corner " + stone);
		WonderSite site = WonderManager.get(level).in(v.hall()).get(0);
		Leftovers.after(helper, () -> forget(level, site.id()));
		BlockPos built = helper.absolutePos(new BlockPos(9, 2, 9));
		level.setBlockAndUpdate(built, Blocks.STONE_BRICKS.defaultBlockState()); // a block "already built"

		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		stranger.setShiftKeyDown(true);
		helper.assertTrue(!WonderStages.breakStone(level, stranger, stone) && WonderManager.get(level).site(site.id()).isPresent(),
			"a stranger broke the stone");
		v.owner().setShiftKeyDown(false);
		helper.assertTrue(!WonderStages.breakStone(level, v.owner(), stone) && WonderManager.get(level).site(site.id()).isPresent(),
			"a plain break ended the Wonder");
		v.owner().setShiftKeyDown(true);
		helper.assertTrue(WonderStages.breakStone(level, v.owner(), stone), "the owner's sneak-break was refused");
		v.owner().setShiftKeyDown(false);
		helper.assertTrue(WonderManager.get(level).site(site.id()).isEmpty(), "the Wonder goes on");
		helper.assertTrue(site.parts().stream().allMatch(id -> BuildSiteManager.get(level).get(id) == null), "its open parts stay");
		helper.assertTrue(v.owner().getInventory().countItem(ModItems.WONDER_PLAN) == 1, "the plan didn't come back");
		helper.assertTrue(level.getBlockState(built).is(Blocks.STONE_BRICKS), "what's built was taken down");
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(v.hall());
		helper.assertTrue(hall.chronicle().stream().anyMatch(e -> key(e.text()).equals("chronicle.aliveworkplace.wonder.abandoned")), "not in the chronicle");
		// a stray stone (no Wonder) breaks like any block
		helper.assertTrue(WonderStages.breakStone(level, stranger, stone), "a stray stone can't be broken");
		helper.succeed();
	}

	// ---------------------------------------------------------------- a real builder raises the folly

	/** Fills chests round the stone with all the folly takes, a few spare of each. */
	private static void stock(GameTestHelper helper, ServerLevel level, BlockPos stone, BlueprintData.Placement at) {
		BlueprintSupplies.Checklist list = BlueprintSupplies.checklist(level, new BlueprintData(WHOLE,
			BlueprintLibrary.get(level, WHOLE).map(Blueprint::size), Optional.empty())).orElseThrow();
		int dx = Integer.signum(stone.getX() - helper.absolutePos(MIDDLE).getX());
		int dz = Integer.signum(stone.getZ() - helper.absolutePos(MIDDLE).getZ());
		List<BlockPos> chests = List.of(stone.offset(2 * dx, 0, 0), stone.offset(0, 0, 2 * dz), stone.offset(2 * dx, 0, 2 * dz), stone.offset(3 * dx, 0, 0));
		chests.forEach(p -> level.setBlockAndUpdate(p, Blocks.CHEST.defaultBlockState()));
		int c = 0;
		int slot = 0;
		for (BlueprintSupplies.Line line : list.lines()) {
			Item item = line.item();
			for (int left = line.need() + 4; left > 0; left -= item.getDefaultMaxStackSize()) {
				if (slot == 27) {
					c++;
					slot = 0;
				}
				helper.assertTrue(c < chests.size(), "the folly needs more than " + chests.size() + " chests");
				((Container) level.getBlockEntity(chests.get(c))).setItem(slot++, new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
			}
		}
	}

	/** The template blocks of stage II (rel. y 7 to 12 over the 9 x 9 footprint), in the world. */
	private static boolean stageTwoEmpty(ServerLevel level, BlueprintData.Placement at) {
		for (int x = 0; x < 9; x++) {
			for (int y = 7; y <= 12; y++) {
				for (int z = 0; z < 9; z++) {
					BlockPos p = at.origin().offset(StructureTemplate.transform(new BlockPos(x, y, z), at.mirror(), at.rotation(), BlockPos.ZERO));
					if (!level.getBlockState(p).isAir()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** Blocks the folly as drawn still lacks at {@code at}. */
	private static List<BlockPos> unlike(ServerLevel level, ResourceLocation drawing, BlueprintData.Placement at) {
		Blueprint drawn = BlueprintLibrary.get(level, drawing).orElseThrow();
		return BuildPlan.create(drawn, at, level, 0).unfinished(level);
	}

	/**
	 * Blocks the finished folly lacks, but for the lantern on its finial (template 4, 18, 4): five blocks over the highest
	 * spot a builder can stand inside the closed dome, it waits for scaffolding (ROADMAP 35.6; see the Notes) and is the
	 * one step a lone builder skips today. Tighten this when 35.6 lands.
	 */
	private static List<BlockPos> unlikeButTheFinial(ServerLevel level, BlueprintData.Placement at) {
		BlockPos finial = at.origin().offset(StructureTemplate.transform(new BlockPos(4, 18, 4), at.mirror(), at.rotation(), BlockPos.ZERO));
		return unlike(level, DRAWN, at).stream().filter(p -> !p.equals(finial)).toList();
	}

	/** One builder raises the folly in {@code turn} and {@code mirror}: stage by stage, ending as drawn. */
	private static void raise(GameTestHelper helper, Rotation turn, Mirror mirror) {
		Village v = village(helper, new BlockPos(2, 2, 27));
		ServerLevel level = v.level();
		ItemStack plan = plan(helper, v, helper.absolutePos(MIDDLE), turn, mirror);
		BlueprintData.Placement at = placement(plan);
		v.owner().setItemInHand(InteractionHand.MAIN_HAND, plan);
		WonderGoodsKit.stockAll(level, FOLLY, at); // 35.7: every stage's goods in the stockpile
		helper.assertTrue(WonderStages.handOver(v.owner(), v.builder(), plan) == InteractionResult.SUCCESS, "not handed over " + turn + "/" + mirror);
		WonderSite begun = WonderManager.get(level).in(v.hall()).get(0);
		BlockPos stone = begun.stone().orElseThrow();
		stock(helper, level, stone, at);
		UUID id = begun.id();
		Leftovers.after(helper, () -> forget(level, id));
		helper.assertTrue(begun.builder().equals(Optional.of(v.builder().getUUID())), "the Wonder remembers its builder");
		List<BlueprintData.Placement> stageOne = List.of(at);
		AtomicInteger stage = new AtomicInteger(1);
		helper.onEachTick(() -> {
			if (level.getGameTime() % 200 == 0) {
				helper.setDayTime(2000); // a long build: keep the day going
			}
			WonderSite now = WonderManager.get(level).site(id).orElse(null);
			if (now == null || now.done()) {
				return;
			}
			if (now.stage() == 1) {
				helper.assertTrue(stageTwoEmpty(level, at), "a stage II block went up during stage I " + turn + "/" + mirror);
			} else if (stage.get() == 1) {
				stage.set(now.stage());
				// stage II has just begun: every block of stage I stands
				List<BlockPos> left = unlike(level, ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "wonder/test_folly/stage_1_0"), stageOne.get(0));
				helper.assertTrue(left.isEmpty(), "stage II began with " + left.size() + " blocks of stage I missing, e.g. " + left.stream().limit(3).toList());
			}
		});
		helper.succeedWhen(() -> {
			WonderSite now = WonderManager.get(level).site(id).orElseThrow();
			helper.assertTrue(now.done(), "still at stage " + now.stage() + " " + turn + "/" + mirror + ", "
				+ Math.round(WonderStages.stageProgress(level, now) * 100) + "%, parts " + now.parts().stream()
				.map(p -> BuildSiteManager.get(level).get(p)).map(s -> s == null ? "done" : s.stage() + "/" + s.status() + " missing " + s.missing()).toList()
				+ "; builder alive=" + v.builder().isAlive() + " at " + helper.relativePos(v.builder().blockPosition()) + " activity="
				+ v.builder().getBrain().getActiveNonCoreActivity() + " job=" + io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_JOB.get(v.builder())
				+ " running=" + v.builder().getBrain().getRunningBehaviors().stream().map(b -> b.getClass().getSimpleName()).toList());
			List<BlockPos> left = unlikeButTheFinial(level, at);
			helper.assertTrue(left.isEmpty(), left.size() + " blocks unlike the drawing " + turn + "/" + mirror + ", e.g. "
				+ left.stream().limit(3).map(p -> p.toShortString() + "=" + level.getBlockState(p) + " below " + level.getBlockState(p.below())
				+ " items " + level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(p).inflate(6)).stream()
				.map(e -> e.getItem().toString()).toList() + " builder at " + v.builder().blockPosition().toShortString()).toList());
			helper.assertTrue(level.getBlockState(stone).is(ModBlocks.FOUNDATION_STONE), "the stone was cleared");
		});
	}

	//$ gametest_ticks_batch TALL '24000' '"aBuilderRaisesTheFolly"'
	@GameTest(template = TALL, timeoutTicks = 24000, batch = "aBuilderRaisesTheFolly")
	public void aBuilderRaisesTheFolly(GameTestHelper helper) {
		raise(helper, Rotation.NONE, Mirror.NONE);
	}

	//$ gametest_ticks_batch TALL '24000' '"aBuilderRaisesTheFollyTurnedOnce"'
	@GameTest(template = TALL, timeoutTicks = 24000, batch = "aBuilderRaisesTheFollyTurnedOnce")
	public void aBuilderRaisesTheFollyTurnedOnce(GameTestHelper helper) {
		raise(helper, Rotation.CLOCKWISE_90, Mirror.NONE);
	}

	//$ gametest_ticks_batch TALL '24000' '"aBuilderRaisesTheFollyTurnedTwice"'
	@GameTest(template = TALL, timeoutTicks = 24000, batch = "aBuilderRaisesTheFollyTurnedTwice")
	public void aBuilderRaisesTheFollyTurnedTwice(GameTestHelper helper) {
		raise(helper, Rotation.CLOCKWISE_180, Mirror.NONE);
	}

	//$ gametest_ticks_batch TALL '24000' '"aBuilderRaisesTheFollyTurnedBack"'
	@GameTest(template = TALL, timeoutTicks = 24000, batch = "aBuilderRaisesTheFollyTurnedBack")
	public void aBuilderRaisesTheFollyTurnedBack(GameTestHelper helper) {
		raise(helper, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
	}

	//$ gametest_ticks_batch TALL '24000' '"aBuilderRaisesTheFollyMirrored"'
	@GameTest(template = TALL, timeoutTicks = 24000, batch = "aBuilderRaisesTheFollyMirrored")
	public void aBuilderRaisesTheFollyMirrored(GameTestHelper helper) {
		raise(helper, Rotation.NONE, Mirror.FRONT_BACK);
	}

	//$ gametest_ticks_batch TALL '24000' '"aBuilderRaisesTheFollyMirroredTurnedOnce"'
	@GameTest(template = TALL, timeoutTicks = 24000, batch = "aBuilderRaisesTheFollyMirroredTurnedOnce")
	public void aBuilderRaisesTheFollyMirroredTurnedOnce(GameTestHelper helper) {
		raise(helper, Rotation.CLOCKWISE_90, Mirror.FRONT_BACK);
	}

	//$ gametest_ticks_batch TALL '24000' '"aBuilderRaisesTheFollyMirroredTurnedTwice"'
	@GameTest(template = TALL, timeoutTicks = 24000, batch = "aBuilderRaisesTheFollyMirroredTurnedTwice")
	public void aBuilderRaisesTheFollyMirroredTurnedTwice(GameTestHelper helper) {
		raise(helper, Rotation.CLOCKWISE_180, Mirror.FRONT_BACK);
	}

	//$ gametest_ticks_batch TALL '24000' '"aBuilderRaisesTheFollyMirroredTurnedBack"'
	@GameTest(template = TALL, timeoutTicks = 24000, batch = "aBuilderRaisesTheFollyMirroredTurnedBack")
	public void aBuilderRaisesTheFollyMirroredTurnedBack(GameTestHelper helper) {
		raise(helper, Rotation.COUNTERCLOCKWISE_90, Mirror.FRONT_BACK);
	}

	/**
	 * Saved and loaded mid-stage II (the Wonders, every build site and the builder, as a restart does), the builder
	 * carries on and the folly is finished as drawn.
	 */
	//$ gametest_ticks_batch TALL '24000' '"wonderReload"'
	@GameTest(template = TALL, timeoutTicks = 24000, batch = "wonderReload")
	public void aReloadMidStageTwoLosesNothing(GameTestHelper helper) {
		Village v = village(helper, new BlockPos(2, 2, 27));
		ServerLevel level = v.level();
		var registries = level.registryAccess();
		ItemStack plan = plan(helper, v, helper.absolutePos(MIDDLE), Rotation.CLOCKWISE_90, Mirror.NONE);
		BlueprintData.Placement at = placement(plan);
		WonderGoodsKit.stockAll(level, FOLLY, at); // 35.7: every stage's goods in the stockpile
		helper.assertTrue(WonderStages.handOver(v.owner(), v.builder(), plan) == InteractionResult.SUCCESS, "not handed over");
		WonderSite begun = WonderManager.get(level).in(v.hall()).get(0);
		stock(helper, level, begun.stone().orElseThrow(), at);
		UUID id = begun.id();
		Leftovers.after(helper, () -> forget(level, id));
		AtomicBoolean reloaded = new AtomicBoolean(false);
		CompoundTag[] builderTag = new CompoundTag[1];
		helper.onEachTick(() -> {
			if (level.getGameTime() % 200 == 0) {
				helper.setDayTime(2000);
			}
			if (builderTag[0] != null) { // the builder comes back the tick after leaving, as the world loads them
				Villager back = EntityType.VILLAGER.create(level);
				back.load(builderTag[0]);
				level.addFreshEntity(back);
				builderTag[0] = null;
				return;
			}
			WonderSite now = WonderManager.get(level).site(id).orElse(null);
			if (reloaded.get() || now == null || now.stage() != 2) {
				return;
			}
			BuildSite part = BuildSiteManager.get(level).get(now.parts().get(0));
			if (part == null || part.isQueued() || part.placed() < 20) {
				return;
			}
			// the restart: the Wonders and the build sites read back from their saves, the builder saved and loaded again
			WonderManager wonders = WonderManager.get(level);
			level.getDataStorage().set(WonderManager.NAME, WonderManager.load(wonders.save(new CompoundTag(), registries), registries));
			BuildSiteManager sites = BuildSiteManager.get(level);
			level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(sites.save(new CompoundTag(), registries), registries));
			Villager builder = (Villager) level.getEntity(v.builder().getUUID());
			builderTag[0] = builder.saveWithoutId(new CompoundTag());
			builder.discard();
			reloaded.set(true);
			WonderSite read = WonderManager.get(level).site(id).orElseThrow();
			helper.assertTrue(read.stage() == 2 && read.parts().equals(now.parts()) && BuildSiteManager.get(level).get(now.parts().get(0)) != null
				&& BuildSiteManager.get(level).get(now.parts().get(0)) != part, "the Wonder and its stage read back: " + read);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded.get(), "never reloaded mid-stage II");
			WonderSite now = WonderManager.get(level).site(id).orElseThrow();
			helper.assertTrue(now.done(), "still at stage " + now.stage() + ", " + Math.round(WonderStages.stageProgress(level, now) * 100) + "%");
			List<BlockPos> left = unlikeButTheFinial(level, at);
			helper.assertTrue(left.isEmpty(), left.size() + " blocks unlike the drawing after the reload, e.g. " + left.stream().limit(3).toList());
		});
	}
}
