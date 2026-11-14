package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderWork;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * B1148: a builder asks for a blueprint's item frames like its blocks. A blueprint of one plank with an item frame on it:
 * the frame is on the builder's list from the start; with leather and sticks in his chest and a Leatherworker in the
 * village, the frame is made and hung before the build ends (also with the builder and the build saved and loaded while
 * he waits for it); with nobody to make it, the finished build waits a while with "1× Item Frame" on its waiting line,
 * then ends without it.
 */
public class BuilderAsksForFramesGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);
	/** Where the frame hangs: on the plank's south face. */
	private static final BlockPos FRAME = ROW.south();

	/** With leather and sticks in the chest and a Leatherworker by, the blueprint's frame is made and hung. */
	//$ gametest_ticks_batch AREA '4000' '"b1148Made"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "b1148Made")
	public void theLeatherworkerMakesTheBlueprintsItemFrameAndItIsHung(GameTestHelper helper) {
		prepare(helper);
		ResourceLocation id = framed(helper);
		Container chest = chest(helper, new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.LEATHER, 4), new ItemStack(Items.STICK, 64));
		Villager builder = builder(helper);
		BuildSite site = start(helper, builder, id);
		Villager leatherworker = leatherworker(helper);
		UUID siteId = site.id();
		helper.succeedWhen(() -> {
			BuildSite open = BuildSiteManager.get(helper.getLevel()).get(siteId);
			helper.assertTrue(open == null, "still building: " + (open == null ? "" : state(open)));
			helper.assertTrue(frames(helper) == 1, frames(helper) + " item frames hang on the plank");
			helper.assertTrue(helper.getBlockState(ROW).is(Blocks.OAK_PLANKS), "the plank isn't there");
			helper.assertTrue(chest.countItem(Items.LEATHER) < 4, "the frame wasn't made from the chest's leather: " + chest.countItem(Items.LEATHER) + " left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(leatherworker, 0) >= 1,
				"the Leatherworker made " + ModAttachments.ITEMS_CRAFTED.getOrElse(leatherworker, 0));
		});
	}

	/**
	 * Nobody makes item frames: the frame is on the builder's list from the first tick (Builders.remainingNeed), and the
	 * finished build waits for it with "1× Item Frame" on its waiting line and the requests board, then (its wait over,
	 * shortened here) ends without it rather than for ever.
	 */
	//$ gametest_ticks_batch AREA '2000' '"b1148Nobody"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "b1148Nobody")
	public void aFrameNobodyMakesIsNamedOnTheWaitingLineThenLeftOut(GameTestHelper helper) {
		prepare(helper);
		int wait = BuilderWork.DECORATION_WAIT;
		BuilderWork.DECORATION_WAIT = 400;
		Leftovers.after(helper, () -> BuilderWork.DECORATION_WAIT = wait);
		ResourceLocation id = framed(helper);
		chest(helper, new ItemStack(Items.OAK_PLANKS, 4));
		Villager builder = builder(helper);
		BuildSite site = start(helper, builder, id);
		ServerLevel level = helper.getLevel();
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null && plan.entities().size() == 1, "the blueprint's frame isn't in the plan");
		Integer frames = Builders.remainingNeed(level, site, plan).get(Items.ITEM_FRAME);
		helper.assertTrue(frames != null && frames == 1, "the builder's list doesn't count the frame: " + Builders.remainingNeed(level, site, plan));
		UUID siteId = site.id();
		AtomicReference<String> line = new AtomicReference<>();
		AtomicReference<String> board = new AtomicReference<>();
		AtomicBoolean waited = new AtomicBoolean();
		helper.onEachTick(() -> {
			BuildSite open = BuildSiteManager.get(level).get(siteId);
			if (open != null && open.isGettingDecorations() && open.status() == BuildSite.Status.WAITING_FOR_MATERIALS && line.get() == null) {
				waited.set(true);
				line.set(Builders.formatMissing(open, 5).getString());
				board.set(io.github.jcondedata.aliveworkplace.work.Requests.of(level, builder).stream()
					.map(r -> BuiltInRegistries.ITEM.getKey(r.icon().getItem()).toString()).toList().toString());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(waited.get(), "the finished build never waited for its frame");
			helper.assertTrue("1× Item Frame".equals(line.get()), "the waiting line: " + line.get());
			helper.assertTrue(board.get() != null && board.get().contains("item_frame"), "the requests board: " + board.get());
			BuildSite open = BuildSiteManager.get(level).get(siteId);
			helper.assertTrue(open == null, "still building: " + (open == null ? "" : state(open)));
			helper.assertTrue(helper.getBlockState(ROW).is(Blocks.OAK_PLANKS), "the plank isn't there");
			helper.assertTrue(frames(helper) == 0, frames(helper) + " item frames hang with none to be had");
		});
	}

	/**
	 * Saved and loaded while the finished build waits for its frame (the builder and the build, B1148's wait with them):
	 * it keeps waiting from where it was, and once leather and sticks come the Leatherworker makes the frame and it is hung.
	 */
	//$ gametest_ticks_batch AREA '5000' '"b1148Reload"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "b1148Reload")
	public void aBuilderLoadedWhileWaitingForTheFrameStillHangsIt(GameTestHelper helper) {
		prepare(helper);
		int wait = BuilderWork.DECORATION_WAIT;
		BuilderWork.DECORATION_WAIT = 4000;
		Leftovers.after(helper, () -> BuilderWork.DECORATION_WAIT = wait);
		ResourceLocation id = framed(helper);
		Container chest = chest(helper, new ItemStack(Items.OAK_PLANKS, 4));
		Villager[] builder = {builder(helper)};
		BuildSite site = start(helper, builder[0], id);
		Villager leatherworker = leatherworker(helper);
		ServerLevel level = helper.getLevel();
		UUID siteId = site.id();
		AtomicBoolean reloaded = new AtomicBoolean();
		helper.onEachTick(() -> {
			BuildSite open = BuildSiteManager.get(level).get(siteId);
			if (reloaded.get() || open == null || !open.isGettingDecorations() || open.status() != BuildSite.Status.WAITING_FOR_MATERIALS) {
				return;
			}
			reloaded.set(true);
			long since = open.decorationWaitSince();
			builder[0] = QaBuilderTerrain1102GameTests.reload(helper, level, builder[0]);
			BuildSite back = BuildSiteManager.get(level).get(siteId);
			helper.assertTrue(back != null && back.isDone() && back.decorationWaitSince() == since,
				"the wait wasn't kept over the reload: " + (back == null ? "site gone" : back.stage() + " since " + back.decorationWaitSince() + ", was " + since));
			chest.setItem(1, new ItemStack(Items.LEATHER, 4));
			chest.setItem(2, new ItemStack(Items.STICK, 64));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded.get(), "never reloaded while waiting for the frame");
			BuildSite open = BuildSiteManager.get(level).get(siteId);
			helper.assertTrue(open == null, "still building: " + (open == null ? "" : state(open)));
			helper.assertTrue(frames(helper) == 1, frames(helper) + " item frames hang on the plank");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(leatherworker, 0) >= 1,
				"the Leatherworker made " + ModAttachments.ITEMS_CRAFTED.getOrElse(leatherworker, 0));
		});
	}

	// ---- helpers ----

	private static void prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
	}

	/** A blueprint of one oak plank with an empty item frame on its south face, standing on the floor. */
	private static ResourceLocation framed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(ROW, Blocks.OAK_PLANKS);
		ItemFrame frame = new ItemFrame(level, helper.absolutePos(FRAME), Direction.SOUTH);
		helper.assertTrue(frame.survives(), "setup: the frame can't hang on the plank");
		level.addFreshEntity(frame);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"b1148_frame_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(1, 1, 2), true, Blocks.STRUCTURE_VOID);
		frame.discard();
		helper.setBlock(ROW, Blocks.AIR);
		return id;
	}

	private static Container chest(GameTestHelper helper, ItemStack... items) {
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container container = helper.getBlockEntity(CHEST);
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
		return container;
	}

	private static Villager builder(GameTestHelper helper) {
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(helper.getLevel(), builder, helper.absolutePos(BENCH));
		return builder;
	}

	private static BuildSite start(GameTestHelper helper, Villager builder, ResourceLocation id) {
		ServerLevel level = helper.getLevel();
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		if (site == null || site.plan(level) == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		return site;
	}

	/** A Leatherworker at his cauldron, with a chest by it. */
	private static Villager leatherworker(GameTestHelper helper) {
		helper.setBlock(STATION, Blocks.CAULDRON);
		helper.setBlock(STATION.east(), Blocks.CHEST);
		Villager v = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), PoiTypes.LEATHERWORKER, VillagerProfession.LEATHERWORKER);
		return v;
	}

	private static int frames(GameTestHelper helper) {
		List<ItemFrame> hanging = helper.getLevel().getEntitiesOfClass(ItemFrame.class, new AABB(helper.absolutePos(FRAME)).inflate(0.5), ItemFrame::isAlive);
		return hanging.size();
	}

	private static String state(BuildSite site) {
		return "stage=" + site.stage() + " status=" + site.status() + " missing=" + site.missing();
	}
}
