package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane E, 2026-11-21): the rebuilt Create builds (Milestones 50 and 51 redrew every one of them) in a world that was
 * played with Create and opened again after Create was taken out of the pack. {@link QaCreateGoneGameTests} proves it
 * for the Watermill only; this does the same for each of the others and the Train Station. From the spec: "the mod must
 * run without them" and "don't break existing saves" (CLAUDE.md), and 50.13's "the mod absent, nothing changes": a
 * builder whose site was begun with Create (the site saved by id, read back here through a real save and load of the
 * site) finishes it from the store with the Create parts left out, and is never left standing on it; nothing it asks
 * for is a Create item.
 *
 * <p>All of them run side by side in one batch: the build-speed game rules are set by the first to start and put back
 * by the last to end, and each clears only its own area (never {@code Leftovers.clear}, which sweeps 48 blocks round).
 */
public class QaCreateGoneRebuilt1121eGameTests implements FabricGameTest {
	private static final String TALL_AREA = "aliveworkplace_test:tall_area";
	private static final String BATCH = "qa_create_gone_rebuilt_1121e";
	/** How far round a build (blocks) the test area must reach: a builder stands up to 4 blocks out (B1390). */
	private static final int ROOM_AROUND = 4;
	private static final AtomicInteger RUNNING = new AtomicInteger();
	private static int delayWas;
	private static boolean helpsWas;

	static List<StarterBlueprints.Entry> builds() {
		return Stream.concat(StarterBlueprints.CREATE_ONLY.stream(), StarterBlueprints.TRAINS_ONLY.stream())
			.filter(e -> e != StarterBlueprints.WATERMILL).toList();
	}

	/**
	 * One per build, all in one batch. 36,000 ticks: with one builder the rebuilt Brass Foundry and Train Station (about
	 * 1,500 blocks each) take about 25,000 at build delay 1; the rest finish within 12,000.
	 */
	@GameTestGenerator
	public Collection<TestFunction> startedWithCreateFinishedWithout() {
		List<TestFunction> out = new ArrayList<>();
		for (StarterBlueprints.Entry entry : builds()) {
			String name = "qaCreateGoneRebuilt_" + entry.id().getPath();
			out.add(new TestFunction(BATCH, name, TALL_AREA, 36000, 0, true, helper -> finishedWithoutCreate(helper, entry)));
		}
		return out;
	}

	private static void finishedWithoutCreate(GameTestHelper helper, StarterBlueprints.Entry entry) {
		// Not Leftovers.clear: its 48-block sweep would take the neighboring tests' builders. Only this area's own leftovers go.
		helper.getLevel().getEntitiesOfClass(Entity.class, helper.getBounds(), e -> !(e instanceof net.minecraft.world.entity.player.Player))
			.forEach(Entity::discard);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("create"), "Create is loaded in the plain suite");
		if (RUNNING.getAndIncrement() == 0) {
			delayWas = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
			helpsWas = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		}
		helper.setDayTime(2000);
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		// The build in the middle of the area, so the builder never has to stand past its edge
		int sizeX = entry.size().getX();
		int sizeZ = entry.size().getZ();
		int x0 = Math.max(ROOM_AROUND + 1, (30 - sizeX) / 2);
		int z0 = Math.max(ROOM_AROUND + 2, (30 - sizeZ) / 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(x0, 2, z0)),
			Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> {
			builder.discard();
			BuildSiteManager.get(level).forgetFinished(placement);
			level.getEntitiesOfClass(Entity.class, helper.getBounds(), e -> e instanceof ItemEntity || e instanceof Villager)
				.forEach(Entity::discard);
			if (RUNNING.decrementAndGet() == 0) {
				level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delayWas, level.getServer());
				level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(helpsWas, level.getServer());
			}
		});
		BuildSite started = Builders.start(level, builder, null, entry.id(), placement);
		helper.assertTrue(started != null, "setup: " + entry.id() + " couldn't be started by id");
		// The world saved (with Create) and opened again (without): the site as the save holds it
		CompoundTag saved = started.save();
		BuildSite site = BuildSite.load(saved);
		helper.assertTrue(site != null && site.id().equals(started.id()) && site.structure().equals(entry.id()),
			"the saved site of " + entry.id() + " didn't load back: " + saved);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			// A blueprint that can't be read any more must not leave a site behind for the builder to stand on
			helper.succeedWhen(() -> helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				entry.id() + " can't be planned without Create, yet its site stays: " + site.stage() + " " + site.status()));
			return;
		}
		BoundingBox box = plan.bounds();
		AABB area = helper.getBounds();
		helper.assertTrue(box.minX() - ROOM_AROUND >= area.minX && box.maxX() + 1 + ROOM_AROUND <= area.maxX
			&& box.minZ() - ROOM_AROUND >= area.minZ && box.maxZ() + 1 + ROOM_AROUND <= area.maxZ && box.maxY() < area.maxY,
			"setup: the test area doesn't reach " + ROOM_AROUND + " blocks round " + entry.id() + " (" + box + " in " + area + ")");
		List<String> createItems = new ArrayList<>();
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			if ("create".equals(BuiltInRegistries.ITEM.getKey(e.getKey()).getNamespace())) {
				createItems.add(e.getKey().toString());
			}
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(createItems.isEmpty(), entry.id() + " wants Create items without Create: " + createItems);
		// The store: a wall of barrels along the front edge, each filled with what the build takes
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i % 12, 2 + i / 12, 0);
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		// The bigger builds take more than one working day (2000 to 9000) at this speed: the day is held in working hours
		// (the whole batch is these builds, so no other test sees the clock)
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 >= 8500) {
				level.setDayTime(level.getDayTime() - level.getDayTime() % 24000 + 2100);
			}
		});
		helper.succeedWhen(() -> {
			BuildSite live = BuildSiteManager.get(level).get(site.id());
			helper.assertTrue(live == null, live == null ? "" : entry.id() + " is still being built without Create: " + live.stage() + " "
				+ live.status() + " missing " + live.missing() + " skipped " + live.skipped());
		});
	}
}
