package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.UpgradeOffers;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA (2026-11-07, second round): the grander homes built for real and their upgrades in a style (ROADMAP 34.15, 34.16;
 * wiki grander-homes.md: "That blueprint, in any style, is that grade, and each upgrade one more"; 28.7a: "an upgrade
 * keeps its look"). A Townhouse I and a Manor I each built by a builder across a save and reload mid-STRUCTURE end
 * finished with exactly their materials, and the finished build itself gives the builder the next tier's sale; a styled
 * Artisan's House built for real gives the styled II; and for every style the library has for each grander home, the
 * builder who finishes a styled tier sells the next tier in that same style, never the plain one.
 */
public class QaGranderHomes1107bGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** 30 x 30 x 30: the Townhouse is 20 tall, over the huge area's height once it stands on y = 2 (as BuilderGameTests). */
	private static final String TALL = "aliveworkplace_test:tall_area";

	/** As a save and a load of the world would: the build sites from their save (the manager replaced) and the builder from its save. */
	private static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load back: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}

	/**
	 * A builder puts {@code structure} up from bare ground at {@code origin}, from barrels holding exactly what its plan
	 * asks for; the world is saved and loaded back (site and builder) once 40 blocks of its STRUCTURE stage are in. It
	 * must end finished, every block as drawn, nothing of its materials left anywhere, each of its beds a home of
	 * {@code grade}, and the builder selling {@code upgrade} (and not the tier after it).
	 */
	private static void buildAcrossAReload(GameTestHelper helper, ResourceLocation structure, BlockPos origin, ResourceLocation upgrade, int grade) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.BUILDERS_BENCH);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(new BlockPos(2, 2, 2)));
		BuildSite site = Builders.start(level, builder, null, structure, placement);
		helper.assertTrue(site != null && site.plan(level) != null, structure + " didn't start");
		helper.assertTrue(site.structure().equals(structure), "the site builds " + site.structure() + ", not " + structure);
		BuildPlan plan = site.plan(level);
		Map<Item, Integer> needs = new LinkedHashMap<>(plan.materials());
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : needs.entrySet()) {
			for (int left = e.getValue(); left > 0; ) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				stock.add(new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		BlockPos[] barrels = {new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4), new BlockPos(6, 2, 4)};
		helper.assertTrue(stock.size() <= barrels.length * 27, "the test needs more barrels: " + stock.size() + " stacks");
		for (int i = 0; i < barrels.length; i++) {
			helper.setBlock(barrels[i], Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(barrels[i]);
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		UUID id = site.id();
		Villager[] who = {builder};
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			BuildSite s = BuildSiteManager.get(level).get(id);
			if (reloaded[0] || s == null) {
				return;
			}
			if (s.stage() == BuildPlan.Stage.STRUCTURE && s.placed() >= 40) {
				reloaded[0] = true;
				who[0] = reload(helper, level, who[0]);
				helper.assertTrue(BuildSiteManager.get(level).get(id) != null, "the site didn't come back from its save");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never saved and loaded while the walls were going up");
			BuildSite s = BuildSiteManager.get(level).get(id);
			helper.assertTrue(s == null, s == null ? "" : "still building " + structure + ": " + s.stage() + " " + s.status() + ", " + s.placed()
				+ " placed, missing " + s.missing() + ", builder at " + helper.relativePos(who[0].blockPosition()) + (who[0].isAlive() ? "" : " (dead)"));
			List<BlockPos> wrong = plan.unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of " + structure + " wrong after the build, e.g. "
				+ wrong.stream().limit(5).map(p -> helper.relativePos(p) + "=" + level.getBlockState(p)).toList());
			Map<Item, Integer> left = new LinkedHashMap<>();
			List<Container> containers = new ArrayList<>();
			for (BlockPos b : barrels) {
				containers.add(helper.getBlockEntity(b));
			}
			containers.add(helper.getBlockEntity(new BlockPos(2, 2, 4)));
			containers.add(who[0].getInventory());
			for (Container c : containers) {
				for (int slot = 0; slot < c.getContainerSize(); slot++) {
					ItemStack st = c.getItem(slot);
					if (!st.isEmpty() && needs.containsKey(st.getItem())) {
						left.merge(st.getItem(), st.getCount(), Integer::sum);
					}
				}
			}
			helper.assertTrue(left.isEmpty(), "materials left over after " + structure + " (it should use exactly what it needs): " + left);
			helper.assertTrue(UpgradeOffers.sells(who[0], upgrade), "the builder who finished " + structure + " doesn't sell " + upgrade + ": " + sold(who[0]));
			ResourceLocation after = io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades.upgradeOf(upgrade);
			helper.assertFalse(UpgradeOffers.sells(who[0], after), "the builder sells " + after + " already");
			int beds = 0;
			for (Map.Entry<BlockPos, BlockState> e : plan.targets().entrySet()) {
				if (e.getValue().getBlock() instanceof BedBlock && e.getValue().getValue(BedBlock.PART) == BedPart.HEAD) {
					beds++;
					helper.assertTrue(level.getBlockState(e.getKey()).getBlock() instanceof BedBlock, "no bed built at " + helper.relativePos(e.getKey()));
					helper.assertTrue(Homes.at(level, e.getKey()).map(Homes.Home::tier).orElse(0) == grade, "a bed in the built " + structure + ": "
						+ Homes.at(level, e.getKey()) + ", not grade " + grade);
				}
			}
			helper.assertTrue(beds >= 2, structure + " has " + beds + " beds");
		});
	}

	private static List<ResourceLocation> sold(Villager v) {
		return v.getOffers().stream().flatMap(o -> BlueprintItem.data(o.getResult()).map(d -> d.structure()).stream()).toList();
	}

	/** A Townhouse I built across a save and reload: finished, exactly its materials, its beds grade II, its builder selling the Townhouse II. */
	//$ gametest_ticks_batch TALL '70000' '"qaGrand1107bTownhouseReload"'
	@GameTest(template = TALL, timeoutTicks = 70000, batch = "qaGrand1107bTownhouseReload")
	public void aTownhouseBuiltAcrossASaveAndReloadEndsFinishedAndSellsItsII(GameTestHelper helper) {
		buildAcrossAReload(helper, StarterBlueprints.TOWNHOUSE.id(), new BlockPos(9, 2, 8), StarterBlueprints.TOWNHOUSE_2.id(), 2);
	}

	/** A Manor I built across a save and reload: finished, exactly its materials, its beds grade III, its builder selling the Manor II. */
	//$ gametest_ticks_batch HUGE '70000' '"qaGrand1107bManorReload"'
	@GameTest(template = HUGE, timeoutTicks = 70000, batch = "qaGrand1107bManorReload")
	public void aManorBuiltAcrossASaveAndReloadEndsFinishedAndSellsItsII(GameTestHelper helper) {
		buildAcrossAReload(helper, StarterBlueprints.MANOR.id(), new BlockPos(1, 2, 5), StarterBlueprints.MANOR_2.id(), 3);
	}

	/**
	 * A cherry Artisan's House (a style the Table offers for it) built for real across a save and reload: finished with
	 * exactly the cherry materials, and its builder sells the Artisan's House II in cherry ("an upgrade keeps its look").
	 */
	//$ gametest_ticks_batch HUGE '50000' '"qaGrand1107bStyledArtisanReload"'
	@GameTest(template = HUGE, timeoutTicks = 50000, batch = "qaGrand1107bStyledArtisanReload")
	public void aStyledArtisansHouseBuiltForRealSellsItsIIInTheSameStyle(GameTestHelper helper) {
		String style = "cherry";
		List<String> styles = TableServer.styles(helper.getLevel().getServer(), StarterBlueprints.ARTISANS_HOUSE.id()).stream().map(TablePayloads.StyleChoice::name).toList();
		helper.assertTrue(styles.contains(style), "the Artisan's House doesn't take the " + style + " style: " + styles);
		buildAcrossAReload(helper, BlueprintStyles.styled(StarterBlueprints.ARTISANS_HOUSE.id(), style), new BlockPos(9, 2, 8),
			BlueprintStyles.styled(StarterBlueprints.ARTISANS_HOUSE_2.id(), style), 1);
	}

	/**
	 * For every style the library has for each grander home's tier I and II (the Table's style buttons), the builder who
	 * finishes that styled tier is offered the next tier in the same style (a blueprint that loads), and never the plain
	 * one; a plain finish gives the plain next tier. Each sale is added once.
	 */
	//$ gametest_batch AREA '"qaGrand1107bStyledUpgrades"'
	@GameTest(template = AREA, batch = "qaGrand1107bStyledUpgrades")
	public void everyStyledTierLeadsToTheNextTierInItsOwnStyle(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<StarterBlueprints.Entry[]> steps = List.of(
			new StarterBlueprints.Entry[] {StarterBlueprints.ARTISANS_HOUSE, StarterBlueprints.ARTISANS_HOUSE_2},
			new StarterBlueprints.Entry[] {StarterBlueprints.ARTISANS_HOUSE_2, StarterBlueprints.ARTISANS_HOUSE_3},
			new StarterBlueprints.Entry[] {StarterBlueprints.TOWNHOUSE, StarterBlueprints.TOWNHOUSE_2},
			new StarterBlueprints.Entry[] {StarterBlueprints.TOWNHOUSE_2, StarterBlueprints.TOWNHOUSE_3},
			new StarterBlueprints.Entry[] {StarterBlueprints.MANOR, StarterBlueprints.MANOR_2},
			new StarterBlueprints.Entry[] {StarterBlueprints.MANOR_2, StarterBlueprints.MANOR_3});
		int checked = 0;
		for (StarterBlueprints.Entry[] step : steps) {
			List<String> styles = new ArrayList<>(TableServer.styles(level.getServer(), step[0].id()).stream().map(TablePayloads.StyleChoice::name).toList());
			helper.assertTrue(!styles.isEmpty(), step[0].id() + " takes no style at all");
			styles.add("");
			for (String style : styles) {
				ResourceLocation built = BlueprintStyles.styled(step[0].id(), style);
				ResourceLocation next = BlueprintStyles.styled(step[1].id(), style);
				Villager b = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
				b.setNoAi(true);
				b.setVillagerData(new VillagerData(b.getVillagerData().getType(), ModVillagers.BUILDER, 3));
				MerchantOffers offers = new MerchantOffers();
				offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.STICK, 4), 12, 1, 0.05f));
				b.setOffers(offers);
				Optional<ResourceLocation> added = UpgradeOffers.offer(level, b, built);
				helper.assertTrue(added.equals(Optional.of(next)), "finishing " + built + " offers " + added + ", not " + next);
				helper.assertTrue(BlueprintLibrary.get(level, next).isPresent(), next + " doesn't load");
				if (!style.isEmpty()) {
					helper.assertFalse(UpgradeOffers.sells(b, step[1].id()), "finishing " + built + " sells the plain " + step[1].id());
				}
				helper.assertTrue(UpgradeOffers.offer(level, b, built).isEmpty() && sold(b).size() == 1, "finishing " + built + " twice: " + sold(b));
				b.discard();
				checked++;
			}
		}
		io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("qaGrand1107b: {} styled and plain upgrade steps checked", checked);
		helper.succeed();
	}
}
