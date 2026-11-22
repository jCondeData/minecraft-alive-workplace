package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA lane E (config extremes and saves, 2026-11-22): BuildSiteManager's saved {@code refresh_asked} (ROADMAP 36.21, an
 * owner's request on the Village Hall's Repairs page to bring an old building up to its latest look, waiting for a free
 * builder). From the spec: the request is "saved with the building", so a world saved and reloaded while it waits still
 * gets the refresh, once; and a save from before 36.21 (no key) loads with nothing waiting. The staging is the one
 * {@link QaRefreshOldHouseGameTests} uses (an older drawing standing, the latest one saved as a structure).
 */
public class QaRefreshAskedSave1122eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	private static final BlockPos ROW = new BlockPos(4, 2, 7);
	private static final BlockPos STORE = new BlockPos(2, 2, 4);
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockState PLANKS = Blocks.OAK_PLANKS.defaultBlockState();
	private static final BlockState TORCH = Blocks.TORCH.defaultBlockState();

	private record Stage(ServerPlayer owner, BlueprintData.Placement placement, ResourceLocation id, ServerLevel level) {
	}

	private static List<Blueprint.Entry> older() {
		List<Blueprint.Entry> blocks = new ArrayList<>();
		blocks.add(new Blueprint.Entry(new BlockPos(0, 0, 0), PLANKS, null));
		blocks.add(new Blueprint.Entry(new BlockPos(1, 0, 0), Blocks.STONE_BRICKS.defaultBlockState(), null));
		blocks.add(new Blueprint.Entry(new BlockPos(1, 1, 0), TORCH, null));
		blocks.add(new Blueprint.Entry(new BlockPos(2, 0, 0), Blocks.COBBLESTONE.defaultBlockState(), null));
		blocks.add(new Blueprint.Entry(new BlockPos(3, 0, 0), Blocks.CHEST.defaultBlockState(), null));
		return blocks;
	}

	private static void put(GameTestHelper helper, BlockPos rel, BlockState state) {
		helper.getLevel().setBlock(helper.absolutePos(rel), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	/** The hall, the owner's build standing as its older drawing drew it, a stocked store by the bench, the finished record. */
	private static Stage stage(GameTestHelper helper, String tag) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		for (int x = 0; x < 7; x++) {
			for (int y = 1; y >= 0; y--) {
				for (int z = 0; z < 2; z++) {
					put(helper, ROW.offset(x, y, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
		put(helper, ROW, PLANKS);
		put(helper, ROW.offset(0, 1, 0), TORCH);
		put(helper, ROW.offset(6, 0, 0), PLANKS);
		put(helper, ROW.offset(6, 1, 0), TORCH);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_refresh_asked_" + tag + "_"
			+ Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX() + "_" + helper.absolutePos(ROW).getZ());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(7, 2, 2), false, Blocks.STRUCTURE_VOID);
		Refreshes.addHistory(id, "older", new Blueprint(id, new Vec3i(7, 2, 2), older()));
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Refreshes.removeHistory(id);
			level.getServer().getPlayerList().remove(owner);
		});
		for (int x = 0; x < 7; x++) {
			put(helper, ROW.offset(x, 1, 0), Blocks.AIR.defaultBlockState());
			put(helper, ROW.offset(x, 0, 0), Blocks.AIR.defaultBlockState());
		}
		for (Blueprint.Entry e : older()) {
			put(helper, ROW.offset(e.pos()), e.state());
		}
		Container inside = helper.getBlockEntity(ROW.offset(3, 0, 0));
		inside.setItem(0, new ItemStack(Items.EMERALD, 7));
		helper.setBlock(STORE, Blocks.CHEST);
		Container store = helper.getBlockEntity(STORE);
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
		store.setItem(1, new ItemStack(Items.TORCH, 4));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, owner.getUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return new Stage(owner, placement, id, level);
	}

	private static Villager builder(GameTestHelper helper, ServerPlayer owner, BlockPos at) {
		Villager builder = helper.spawn(EntityType.VILLAGER, at);
		Builders.employ(helper.getLevel(), builder, helper.absolutePos(BENCH));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		return builder;
	}

	/** The owner opens the hall's Repairs page, clicks the build's entry and presses Refresh (the same server handler a click reaches). */
	private static void askRefresh(GameTestHelper helper, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
		menu.press(HallPages.slot(RepairsPage.PAGE), player);
		helper.assertTrue(menu.icon(4).is(Items.ANVIL), "not on the Repairs page: " + menu.icon(4));
		int slot = -1;
		for (int i = VillageHallScreen.FIRST_ROW; i < ChoiceMenu.SIZE && slot < 0; i++) {
			if (menu.icon(i).is(Items.PAINTING) || menu.icon(i).is(Items.SCAFFOLDING)) {
				slot = i;
			}
		}
		helper.assertTrue(slot >= 0, "no build to refresh on the Repairs page");
		menu.press(slot, player);
		helper.assertTrue(menu.icon(RepairsPage.REFRESH).is(Items.BRUSH), "no Refresh button: " + menu.icon(RepairsPage.REFRESH));
		menu.press(RepairsPage.REFRESH, player);
	}

	private static long sites(Stage s) {
		return BuildSiteManager.get(s.level()).all().stream().filter(site -> site.placement().equals(s.placement())).count();
	}

	/** This build's entry in a saved BuildSiteManager tag (by its structure id), or null. */
	private static CompoundTag entryOf(CompoundTag saved, ResourceLocation id) {
		ListTag done = saved.getList("finished", Tag.TAG_COMPOUND);
		for (int i = 0; i < done.size(); i++) {
			if (id.toString().equals(done.getCompound(i).getString("structure"))) {
				return done.getCompound(i);
			}
		}
		return null;
	}

	/** Saves the level's BuildSiteManager, lets {@code edit} change the tag (an older save), loads it back as after a restart. */
	private static BuildSiteManager reload(Stage s, java.util.function.Consumer<CompoundTag> edit) {
		BuildSiteManager manager = BuildSiteManager.get(s.level());
		CompoundTag saved = manager.save(new CompoundTag(), s.level().registryAccess());
		edit.accept(saved);
		BuildSiteManager loaded = BuildSiteManager.load(saved, s.level().registryAccess());
		s.level().getDataStorage().set(BuildSiteManager.NAME, loaded);
		return loaded;
	}

	private static void assertRefreshed(GameTestHelper helper, Stage s) {
		helper.assertTrue(Refreshes.siteOf(s.level(), s.placement()) == null && sites(s) == 0, "still refreshing");
		helper.assertBlockPresent(Blocks.OAK_PLANKS, ROW);
		helper.assertBlockPresent(Blocks.TORCH, ROW.offset(0, 1, 0));
		helper.assertBlockPresent(Blocks.AIR, ROW.offset(1, 0, 0));
		helper.assertBlockPresent(Blocks.AIR, ROW.offset(2, 0, 0));
		helper.assertBlockPresent(Blocks.TORCH, ROW.offset(6, 1, 0));
		helper.assertBlockPresent(Blocks.CHEST, ROW.offset(3, 0, 0));
		Container inside = helper.getBlockEntity(ROW.offset(3, 0, 0));
		helper.assertTrue(inside.countItem(Items.EMERALD) == 7, "the build's chest lost its emeralds: " + inside.countItem(Items.EMERALD));
	}

	/**
	 * The owner asks for a refresh with no builder free; the world is saved (the key "refresh_asked" in the build's entry)
	 * and loaded again. The request still waits, and a builder employed after the reload takes it up on its own rounds
	 * (the real tick loop) and does it once: one refresh site in all, nothing waiting afterwards, and no second refresh
	 * started in the 100 ticks after it finished.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qa_refresh_asked_reload"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_refresh_asked_reload")
	public void aWaitingRefreshSurvivesASaveAndHappensOnce(GameTestHelper helper) {
		Stage s = stage(helper, "reload");
		Villager[] builder = {null};
		Set<UUID> refreshSites = new HashSet<>();
		long[] doneAt = {-1};
		helper.runAfterDelay(2, () -> {
			askRefresh(helper, s.owner());
			helper.assertTrue(sites(s) == 0 && BuildSiteManager.get(s.level()).refreshAsked(s.placement()), "not waiting with no builder");
			CompoundTag saved = BuildSiteManager.get(s.level()).save(new CompoundTag(), s.level().registryAccess());
			CompoundTag entry = entryOf(saved, s.id());
			helper.assertTrue(entry != null && entry.getBoolean("refresh_asked"), "the request isn't in the save: " + entry);
			BuildSiteManager loaded = reload(s, t -> { });
			helper.assertTrue(loaded.refreshAsked(s.placement()) && BuildSiteManager.get(s.level()) == loaded,
				"the request didn't come back from the save");
			builder[0] = builder(helper, s.owner(), new BlockPos(3, 2, 3));
		});
		helper.onEachTick(() -> {
			BuildSite site = Refreshes.siteOf(s.level(), s.placement());
			if (site != null) {
				refreshSites.add(site.id());
			}
			if (doneAt[0] < 0 && builder[0] != null && site == null && !refreshSites.isEmpty()
				&& !BuildSiteManager.get(s.level()).refreshAsked(s.placement())) {
				doneAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(builder[0] != null, "no builder yet");
			helper.assertTrue(doneAt[0] >= 0, "the refresh asked before the save never finished");
			helper.assertTrue(helper.getTick() >= doneAt[0] + 100, "watching for a second refresh");
			helper.assertTrue(refreshSites.size() == 1, "refresh sites: " + refreshSites.size() + ", wanted exactly one");
			helper.assertFalse(BuildSiteManager.get(s.level()).refreshAsked(s.placement()), "still waiting after the refresh");
			assertRefreshed(helper, s);
			AliveWorkplace.LOG.info("QA refresh_asked: saved {}, reloaded, refreshed once by the real tick loop ({} site), nothing waits after",
				true, refreshSites.size());
		});
	}

	/**
	 * A save from before 36.21: the build's entry has no "refresh_asked" (taken out of a save made while a request waited).
	 * It loads with nothing waiting and the build still listed; a free builder on its rounds starts no refresh on it; and
	 * a build nobody asked about is saved without the key (as an older save would be read), false read as false.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_refresh_asked_old_save"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_refresh_asked_old_save")
	public void anOldSaveWithoutTheKeyLoadsWithNothingWaiting(GameTestHelper helper) {
		Stage s = stage(helper, "old");
		Villager[] builder = {null};
		long[] loadedAt = {-1};
		helper.runAfterDelay(2, () -> {
			CompoundTag plain = entryOf(BuildSiteManager.get(s.level()).save(new CompoundTag(), s.level().registryAccess()), s.id());
			helper.assertTrue(plain != null && !plain.contains("refresh_asked"), "a build nobody asked about saved the key: " + plain);
			askRefresh(helper, s.owner());
			helper.assertTrue(BuildSiteManager.get(s.level()).refreshAsked(s.placement()), "not waiting with no builder");
			boolean[] edited = {false};
			BuildSiteManager loaded = reload(s, t -> {
				CompoundTag entry = entryOf(t, s.id());
				if (entry != null && entry.contains("refresh_asked")) {
					entry.remove("refresh_asked");
					edited[0] = true;
				}
			});
			helper.assertTrue(edited[0], "the save to make old had no key to take out");
			helper.assertFalse(loaded.refreshAsked(s.placement()), "an old save without the key loaded a request");
			helper.assertFalse(loaded.anyRefreshAsked(), "an old save without the key loaded some request");
			helper.assertTrue(loaded.finishedIn(s.level()).stream().anyMatch(f -> f.placement().equals(s.placement())), "the build was lost from the old save");
			BuildSiteManager falseOne = BuildSiteManager.load(withKey(loaded.save(new CompoundTag(), s.level().registryAccess()), s.id()),
				s.level().registryAccess());
			helper.assertFalse(falseOne.refreshAsked(s.placement()), "refresh_asked: false read as a request");
			builder[0] = builder(helper, s.owner(), new BlockPos(3, 2, 3));
			loadedAt[0] = helper.getTick();
		});
		helper.onEachTick(() -> {
			if (builder[0] != null) {
				helper.assertTrue(sites(s) == 0, "a refresh started on a build nobody asked about after loading an old save");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(loadedAt[0] >= 0 && helper.getTick() >= loadedAt[0] + 200, "watching the free builder");
			helper.assertTrue(sites(s) == 0 && !BuildSiteManager.get(s.level()).refreshAsked(s.placement()), "something waits or runs");
			helper.assertBlockPresent(Blocks.STONE_BRICKS, ROW.offset(1, 0, 0));
			AliveWorkplace.LOG.info("QA refresh_asked: old save without the key loads with nothing waiting, false reads false, no refresh in 200 ticks");
		});
	}

	/** {@code saved} with "refresh_asked": false written into this build's entry. */
	private static CompoundTag withKey(CompoundTag saved, ResourceLocation id) {
		CompoundTag entry = entryOf(saved, id);
		if (entry != null) {
			entry.putBoolean("refresh_asked", false);
		}
		return saved;
	}
}
