package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
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
 * QA for 36.21 ("Refresh to the latest look") after B642, on the owner's furnished pre-B260 Stone House (turned), through
 * the hall's Repairs page: the stores short of one of the new pieces' materials (the Builder waits and names it, nothing
 * doubled meanwhile, then finishes as a fresh build), and the Builder killed mid-refresh (offered again on the page, a new
 * Builder finishes it as a fresh build). Stage and helpers as in {@link QaRefreshFurnishedGameTests}.
 */
public class QaRefreshFurnishedShortGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** The house's origin: the middle of the area, so it fits however it's turned. */
	private static final BlockPos HOUSE = new BlockPos(15, 2, 15);

	private record Stage(ServerLevel level, ServerPlayer owner, ResourceLocation structure, BlueprintData.Placement placement, Blueprint asBuilt,
						 BlockPos hall, List<BlockPos> stores, Villager builder) {
	}

	/** The spots file of {@code structure} as it was at {@code key}^ (the day the older drawing was current). */
	static FurnitureChoice oldFurniture(String structure, String key) {
		String file = "/qa_b642/" + structure + "_" + key + ".json";
		try (InputStream in = QaRefreshFurnishedShortGameTests.class.getResourceAsStream(file)) {
			if (in == null) {
				throw new IllegalStateException("missing " + file);
			}
			var spots = BlueprintFurniture.read(com.google.gson.JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
			FurnitureChoice chosen = FurnitureChoice.choose(spots, List.of(FurnishingTableGameTests.TEST_TABLE), true);
			return FurnitureChoice.load(chosen.save()); // (as an old save has it)
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * {@code structure} standing as its older drawing {@code key} drew it, furnished with {@code furniture}; a hall, a
	 * Builder, his bench and four stores in the corner the house leaves free.
	 */
	private static Stage stage(GameTestHelper helper, ResourceLocation structure, String key, Rotation rotation, Mirror mirror, FurnitureChoice furniture) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 40;
		helper.setDayTime(2000);
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 9000) {
				helper.setDayTime(2000);
			}
		});
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.getServer().getPlayerList().remove(owner);
		});
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), rotation, mirror);
		Blueprint old = Refreshes.olderDrawing(level.getServer(), structure, key)
			.orElseThrow(() -> new IllegalStateException(structure + "'s drawing " + key + " isn't shipped"));
		Blueprint asBuilt = furniture.applyAsBuilt(old);
		Map<BlockPos, BlockState> built = BuildPlan.create(asBuilt, placement).targets();
		for (Map.Entry<BlockPos, BlockState> e : built.entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		// the corner the house leaves free (it stands in one quarter round HOUSE, as turned and mirrored)
		BlockPos o = helper.absolutePos(HOUSE);
		double sx = built.keySet().stream().mapToInt(p -> p.getX() - o.getX()).average().orElse(0);
		double sz = built.keySet().stream().mapToInt(p -> p.getZ() - o.getZ()).average().orElse(0);
		int x0 = sx > 0 ? 1 : 22;
		int z0 = sz > 0 ? 1 : 22;
		BlockPos hall = new BlockPos(x0, 2, z0);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		List<BlockPos> stores = List.of(new BlockPos(x0, 2, z0 + 4), new BlockPos(x0, 2, z0 + 6), new BlockPos(x0 + 4, 2, z0 + 4), new BlockPos(x0 + 4, 2, z0 + 6));
		stores.forEach(c -> helper.setBlock(c, Blocks.CHEST));
		BlockPos bench = new BlockPos(x0 + 2, 2, z0 + 4);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(x0 + 2, 2, z0 + 2));
		Builders.employ(level, builder, helper.absolutePos(bench));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		BuildSiteManager manager = BuildSiteManager.get(level);
		manager.recordFinished(structure, placement, owner.getUUID());
		if (!furniture.isVanilla()) {
			manager.setFurniture(placement, furniture);
		}
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return new Stage(level, owner, structure, placement, asBuilt, helper.absolutePos(hall), stores, builder);
	}

	/** The owner presses Refresh on the hall's Repairs page (the build's entry, then the button). */
	private static BuildSite pressRefresh(GameTestHelper helper, Stage s) {
		ChoiceMenu menu = VillageHallScreen.forTest(s.owner(), s.hall());
		menu.press(HallPages.slot(RepairsPage.PAGE), s.owner());
		int slot = -1;
		for (int i = VillageHallScreen.FIRST_ROW; i < ChoiceMenu.SIZE && slot < 0; i++) {
			if (menu.icon(i).is(Items.PAINTING) || menu.icon(i).is(Items.SCAFFOLDING)) {
				slot = i;
			}
		}
		helper.assertTrue(slot >= 0, "the old " + s.structure() + " has no Refresh on the Repairs page");
		menu.press(slot, s.owner());
		helper.assertTrue(menu.icon(RepairsPage.REFRESH).is(Items.BRUSH), "no Refresh button: " + menu.icon(RepairsPage.REFRESH));
		menu.press(RepairsPage.REFRESH, s.owner());
		BuildSite site = Refreshes.siteOf(s.level(), s.placement());
		helper.assertTrue(site != null, "no Builder took the refresh up");
		return site;
	}

	/** The current drawing as this build would be built now (its furniture brought to today's spots). */
	private static Blueprint currentFor(Stage s) {
		Blueprint base = BlueprintLibrary.get(s.level(), s.structure()).orElseThrow();
		return Refreshes.refreshedFurniture(s.level(), s.structure(), BuildSiteManager.get(s.level()).furniture(s.placement())).apply(base, s.structure());
	}

	/** Every block in the build (old or new drawing's spots) that can't stand where it is: a torch, lantern, banner, plate... floating. */
	private static List<String> floating(Stage s, Blueprint current) {
		Set<BlockPos> spots = new HashSet<>(BuildPlan.create(s.asBuilt(), s.placement()).targets().keySet());
		spots.addAll(BuildPlan.create(current, s.placement()).targets().keySet());
		List<String> out = new ArrayList<>();
		for (BlockPos pos : spots) {
			BlockState world = s.level().getBlockState(pos);
			if (!world.isAir() && !world.canSurvive(s.level(), pos)) {
				out.add(pos.toShortString() + " " + world.getBlock().getDescriptionId());
			}
		}
		return out;
	}

	/** Every material the refresh needs, {@code extra} each over, into the stores, leaving {@code withheld} out. */
	private static void stock(GameTestHelper helper, Stage s, Map<net.minecraft.world.item.Item, Integer> needs, int times, net.minecraft.world.item.Item withheld) {
		int at = 0;
		for (Map.Entry<net.minecraft.world.item.Item, Integer> need : needs.entrySet()) {
			if (need.getKey() == withheld) {
				continue;
			}
			for (int left = need.getValue() * times + 2; left > 0 && at < 27 * s.stores().size(); at++) {
				int n = Math.min(left, need.getKey().getDefaultMaxStackSize());
				Container store = helper.getBlockEntity(s.stores().get(at / 27));
				store.setItem(at % 27, new ItemStack(need.getKey(), n));
				left -= n;
			}
		}
	}

	/** Every spot of the old or new drawing holds the old drawing's block, the new one's, or air: nothing a third thing, nothing doubled. */
	private static List<String> strays(Stage s, Map<BlockPos, BlockState> was, Map<BlockPos, BlockState> now) {
		Set<BlockPos> spots = new HashSet<>(was.keySet());
		spots.addAll(now.keySet());
		List<String> out = new ArrayList<>();
		for (BlockPos pos : spots) {
			Block world = s.level().getBlockState(pos).getBlock();
			Block old = was.getOrDefault(pos, Blocks.AIR.defaultBlockState()).getBlock();
			Block fresh = now.getOrDefault(pos, Blocks.AIR.defaultBlockState()).getBlock();
			if (world != Blocks.AIR && world != old && world != fresh) {
				out.add(pos.toShortString() + " " + world.getDescriptionId());
			}
		}
		return out;
	}

	/** How many of {@code block} stand on the build's spots. */
	private static long count(Stage s, Set<BlockPos> spots, Block block) {
		return spots.stream().filter(p -> s.level().getBlockState(p).is(block)).count();
	}

	/** The house afterwards: a fresh build of today's drawing (beds a villager claimed aside), nothing floating, no second Refresh. */
	private static void assertFresh(GameTestHelper helper, Stage s, Map<BlockPos, BlockState> was, Map<BlockPos, BlockState> now) {
		helper.assertTrue(Refreshes.siteOf(s.level(), s.placement()) == null, "still refreshing");
		helper.assertTrue(BuildSiteManager.get(s.level()).all().stream().noneMatch(b -> b.placement().equals(s.placement())), "a site left on the house");
		List<String> left = new ArrayList<>(RefreshMatchesFreshBuildGameTests.differences(s.level(), s.placement(), s.asBuilt(), currentFor(s)));
		Set<String> claimed = new HashSet<>();
		for (BlockPos pos : was.keySet()) {
			BlockState world = s.level().getBlockState(pos);
			if (world.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
				BlockPos head = world.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD
					? pos : pos.relative(net.minecraft.world.level.block.BedBlock.getConnectedDirection(world));
				if (s.level().getPoiManager().getFreeTickets(head) == 0) {
					claimed.add(pos.toShortString());
					BlockState other = now.get(pos);
					if (other != null && other.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
						claimed.add(pos.relative(net.minecraft.world.level.block.BedBlock.getConnectedDirection(other)).toShortString());
					}
				}
			}
		}
		helper.assertTrue(left.stream().noneMatch(d -> claimed.stream().noneMatch(c -> d.startsWith(c + " "))),
			left.size() + " spots differ from a fresh build: " + left.subList(0, Math.min(10, left.size())) + "; claimed beds: " + claimed);
		List<String> loose = floating(s, currentFor(s));
		helper.assertTrue(loose.isEmpty(), "floating after the refresh: " + loose.subList(0, Math.min(10, loose.size())));
		Set<BlockPos> spots = new HashSet<>(was.keySet());
		spots.addAll(now.keySet());
		helper.assertTrue(count(s, spots, Blocks.CRAFTING_TABLE) <= now.values().stream().filter(b -> b.is(Blocks.CRAFTING_TABLE)).count(), "a table doubled");
		helper.assertTrue(Refreshes.entries(s.level(), s.hall()).stream().noneMatch(e -> e.building().placement().equals(s.placement())),
			"the refreshed house still offers a refresh on the Repairs page");
	}

	/**
	 * 36.21 "materials short (asks like a repair)" on the owner's furnished pre-B260 Stone House, turned: the stores lack
	 * every piece of one material the new drawing needs. The Builder waits and names it, with nothing placed doubled or
	 * foreign meanwhile; once the material is in the store, he finishes and the house is a fresh build of today's drawing.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa_refresh_furnished_short"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa_refresh_furnished_short")
	public void aFurnishedOldStoneHouseShortOfAMaterialWaitsThenFinishesAsAFreshBuild(GameTestHelper helper) {
		ResourceLocation stoneHouse = AliveWorkplace.id("stone_house");
		Stage s = stage(helper, stoneHouse, RefreshMatchesFreshBuildGameTests.BEFORE_B260, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE,
			RefreshMatchesFreshBuildGameTests.oldFurniture());
		Map<BlockPos, BlockState> was = BuildPlan.create(s.asBuilt(), s.placement()).targets();
		Map<BlockPos, BlockState> now = BuildPlan.create(currentFor(s), s.placement()).targets();
		net.minecraft.world.item.Item[] withheld = {null};
		int[] need = {0};
		int[] waited = {0};
		boolean[] added = {false};
		helper.runAfterDelay(2, () -> {
			BuildSite site = pressRefresh(helper, s);
			Map<net.minecraft.world.item.Item, Integer> materials = site.plan(s.level()).materials();
			helper.assertTrue(!materials.isEmpty(), "the refresh needs no materials");
			// the material needed most (a whole kind of new piece missing), by id so the run is the same each time
			Map.Entry<net.minecraft.world.item.Item, Integer> most = materials.entrySet().stream()
				.max(java.util.Comparator.<Map.Entry<net.minecraft.world.item.Item, Integer>>comparingInt(Map.Entry::getValue)
					.thenComparing(e -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getKey()).toString()))
				.orElseThrow();
			withheld[0] = most.getKey();
			need[0] = most.getValue();
			stock(helper, s, materials, 1, withheld[0]);
			AliveWorkplace.LOG.info("QA refresh short: withholding {} x{} of {}", need[0], withheld[0], materials);
		});
		helper.onEachTick(() -> {
			if (added[0] || withheld[0] == null) {
				return;
			}
			BuildSite site = Refreshes.siteOf(s.level(), s.placement());
			helper.assertTrue(site != null, "the refresh ended without " + withheld[0]);
			if (site.status() != BuildSite.Status.WAITING_FOR_MATERIALS || !site.missing().containsKey(withheld[0])) {
				return;
			}
			List<String> odd = strays(s, was, now);
			helper.assertTrue(odd.isEmpty(), "while waiting, spots hold neither the old nor the new drawing's block: " + odd.subList(0, Math.min(10, odd.size())));
			if (++waited[0] < 100) {
				return;
			}
			helper.assertTrue(Refreshes.siteOf(s.level(), s.placement()) != null, "the waiting refresh was dropped");
			added[0] = true;
			int left = need[0] + 2;
			for (BlockPos store : s.stores()) {
				Container c = helper.getBlockEntity(store);
				for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
					if (c.getItem(i).isEmpty()) {
						int n = Math.min(left, withheld[0].getDefaultMaxStackSize());
						c.setItem(i, new ItemStack(withheld[0], n));
						left -= n;
					}
				}
			}
			helper.assertTrue(left <= 0, "no room in the stores for " + withheld[0]);
			AliveWorkplace.LOG.info("QA refresh short: waited {} ticks for {} ({}), then put it in", waited[0], withheld[0], site.missing());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(added[0], "the Builder never waited for " + withheld[0]);
			assertFresh(helper, s, was, now);
			AliveWorkplace.LOG.info("QA refresh short: finished as a fresh build once {} arrived", withheld[0]);
		});
	}

	/**
	 * 36.21 with B642: the Builder is killed mid-refresh of the owner's furnished pre-B260 Stone House. As the wiki and
	 * the simple-house test have it, the refresh is no longer set to the dead Builder and the Repairs page offers it
	 * again; a new free Builder takes it up through the button and finishes: a fresh build of today's drawing, no old
	 * piece left beside its new one, nothing doubled, nothing floating.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa_refresh_furnished_killed"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa_refresh_furnished_killed")
	public void theBuilderKilledMidRefreshOfAFurnishedOldStoneHouseLeavesItToANewBuilder(GameTestHelper helper) {
		ResourceLocation stoneHouse = AliveWorkplace.id("stone_house");
		Stage s = stage(helper, stoneHouse, RefreshMatchesFreshBuildGameTests.BEFORE_B260, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE,
			RefreshMatchesFreshBuildGameTests.oldFurniture());
		Map<BlockPos, BlockState> was = BuildPlan.create(s.asBuilt(), s.placement()).targets();
		Map<BlockPos, BlockState> now = BuildPlan.create(currentFor(s), s.placement()).targets();
		Villager[] second = {null};
		boolean[] killed = {false};
		boolean[] repressed = {false};
		int[] ticks = {0};
		helper.runAfterDelay(2, () -> {
			BuildSite site = pressRefresh(helper, s);
			stock(helper, s, site.plan(s.level()).materials(), 2, null);
		});
		helper.onEachTick(() -> {
			if (killed[0]) {
				if (!repressed[0] && second[0] != null && Refreshes.siteOf(s.level(), s.placement()) == null
					&& !BuildSiteManager.get(s.level()).refreshAsked(s.placement())) {
					repressed[0] = true;
					ChoiceMenu menu = VillageHallScreen.forTest(s.owner(), s.hall());
					menu.press(HallPages.slot(RepairsPage.PAGE), s.owner());
					int slot = -1;
					for (int i = VillageHallScreen.FIRST_ROW; i < ChoiceMenu.SIZE && slot < 0; i++) {
						if (menu.icon(i).is(Items.PAINTING) || menu.icon(i).is(Items.SCAFFOLDING)) {
							slot = i;
						}
					}
					helper.assertTrue(slot >= 0, "the half-refreshed house isn't on the Repairs page any more");
					menu.press(slot, s.owner());
					helper.assertTrue(menu.icon(RepairsPage.REFRESH).is(Items.BRUSH), "no Refresh button the second time: " + menu.icon(RepairsPage.REFRESH));
					menu.press(RepairsPage.REFRESH, s.owner());
					helper.assertTrue(Refreshes.siteOf(s.level(), s.placement()) != null, "the new Builder didn't take the refresh up");
				}
				return;
			}
			BuildSite site = Refreshes.siteOf(s.level(), s.placement());
			if (site == null || ++ticks[0] < 200) {
				return;
			}
			killed[0] = true;
			List<String> odd = strays(s, was, now);
			helper.assertTrue(odd.isEmpty(), "mid-refresh, spots hold neither the old nor the new drawing's block: " + odd.subList(0, Math.min(10, odd.size())));
			s.builder().kill();
			BuildSite after = Refreshes.siteOf(s.level(), s.placement());
			helper.assertTrue(after == null || !s.builder().getUUID().equals(after.builder()), "the refresh is still set to the dead Builder");
			ChoiceMenu menu = VillageHallScreen.forTest(s.owner(), s.hall());
			menu.press(HallPages.slot(RepairsPage.PAGE), s.owner());
			boolean offered = false;
			for (int i = VillageHallScreen.FIRST_ROW; i < ChoiceMenu.SIZE; i++) {
				offered |= menu.icon(i).is(Items.PAINTING) || menu.icon(i).is(Items.SCAFFOLDING);
			}
			helper.assertTrue(offered || after != null || BuildSiteManager.get(s.level()).refreshAsked(s.placement()),
				"the half-refreshed house is neither offered again nor kept for a Builder");
			// a new free Builder at the same bench
			BlockPos bench = s.stores().get(0).offset(2, 0, 0);
			Villager next = helper.spawn(EntityType.VILLAGER, helper.relativePos(s.hall()).offset(2, 0, 0));
			Builders.employ(s.level(), next, helper.absolutePos(bench));
			ModAttachments.BUILDER_EMPLOYER.set(next, new Employer(s.owner().getUUID(), s.owner().getGameProfile().getName()));
			second[0] = next;
			AliveWorkplace.LOG.info("QA refresh killed: Builder killed {} ticks in (site {}, offered again {})", ticks[0], after == null ? "gone" : "kept", offered);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(killed[0] && second[0] != null, "the Builder was never killed mid-way");
			assertFresh(helper, s, was, now);
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(second[0]), "the new Builder is still at it");
			AliveWorkplace.LOG.info("QA refresh killed: the new Builder finished it as a fresh build (asked again through the page: {})", repressed[0]);
		});
	}
}
