package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.card.StatusCardMenu;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA lane B, 2026-11-09, ROADMAP 42.5 (the status card: "what they need"), the builder's side, from the spec and the
 * wiki (job-status-cards.md: at most three needs, then "and N more"; nothing short reads Needs: Nothing). The landing
 * tests only check "and N more" when the hut happens to be short of more than three. These fill the builder's chests
 * with the whole stone house's materials and take away whole kinds: none, exactly three and exactly four short.
 */
public class QaBuilderCardNeeds1109bGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos STATION = new BlockPos(3, 2, 3);
	private static final BlockPos AT = new BlockPos(4, 2, 4);

	/** A builder (no AI) at his bench, a Village Hall, six chests by the bench and a player. */
	private record Scene(Villager builder, ServerPlayer player, List<Container> chests) {
	}

	private static Scene scene(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(new BlockPos(1, 2, 8), ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		helper.setBlock(STATION, ModBlocks.BUILDERS_BENCH);
		Villager v = helper.spawn(EntityType.VILLAGER, AT);
		v.setNoAi(true);
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		v.setCustomName(Component.literal("Cole"));
		List<Container> chests = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			BlockPos at = new BlockPos(1 + i, 2, 1);
			helper.setBlock(at, Blocks.CHEST);
			chests.add(helper.getBlockEntity(at));
		}
		ServerPlayer player = QaStageFourGameTests.player(helper, new ArrayList<>(), GameType.SURVIVAL);
		return new Scene(v, player, chests);
	}

	/** The stone house's materials, the {@code leaveOut} kinds with the most needed taken away. */
	private static Map<Item, Integer> houseMaterials(BuildPlan plan, int leaveOut, List<Item> left) {
		Map<Item, Integer> all = new LinkedHashMap<>(plan.materials());
		all.entrySet().stream().sorted(Map.Entry.<Item, Integer>comparingByValue().reversed()).limit(leaveOut).forEach(e -> left.add(e.getKey()));
		left.forEach(all::remove);
		return all;
	}

	private static void fill(GameTestHelper helper, List<Container> chests, Map<Item, Integer> materials) {
		int chest = 0, slot = 0;
		for (Map.Entry<Item, Integer> e : materials.entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				helper.assertTrue(chest < chests.size(), "setup: the house's materials don't fit in " + chests.size() + " chests");
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				chests.get(chest).setItem(slot, new ItemStack(e.getKey(), n));
				left -= n;
				if (++slot == chests.get(chest).getContainerSize()) {
					slot = 0;
					chest++;
				}
			}
		}
	}

	private static BuildSite start(GameTestHelper helper, Scene s) {
		ServerLevel level = helper.getLevel();
		BuildSite site = Builders.start(level, s.builder(), s.player(), StarterBlueprints.STONE_HOUSE.id(),
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(14, 1, 14)), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "setup: the builder didn't take the stone house on");
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		return site;
	}

	/** His chests get the house's materials but the {@code leaveOut} kinds; he looks at them again, as he does at work. */
	private static void stock(GameTestHelper helper, Scene s, BuildSite site, int leaveOut, List<Item> left) {
		BuildPlan plan = site.plan(helper.getLevel());
		helper.assertTrue(plan != null && plan.materials().size() > 4, "setup: the stone house's plan has too few kinds");
		fill(helper, s.chests(), houseMaterials(plan, leaveOut, left));
		Builders.refreshMissing(helper.getLevel(), site, s.builder());
	}

	private static List<StatusCard.Line> needs(StatusCard card) {
		return card.lines().stream().filter(l -> l.label().getString().startsWith("Needs")).toList();
	}

	private static List<StatusCard.Line> more(StatusCard card) {
		return card.lines().stream().filter(l -> l.label().getString().startsWith("and ")).toList();
	}

	/** Every material in his chests: the card says Needs: Nothing, in green, and no "and N more". */
	//$ gametest_ticks_batch AREA '200' '"qaBuilderCardNeeds1109bNone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBuilderCardNeeds1109bNone")
	public void aBuilderWithEverythingInHisChestsNeedsNothing(GameTestHelper helper) {
		Scene s = scene(helper);
		BuildSite site = start(helper, s);
		stock(helper, s, site, 0, new ArrayList<>());
		StatusCard card = JobCardGameTests.open(helper, new JobCardGameTests.Setup(s.builder(), s.player(), new ArrayList<>())).card();
		List<StatusCard.Line> needs = needs(card);
		helper.assertTrue(needs.size() == 1, "with every material in his chests the card lists " + JobCardGameTests.lines(card) + " (short: " + site.missing() + ")");
		helper.assertValueEqual(needs.get(0).label().getString() + ": " + needs.get(0).value().getString(), "Needs: Nothing", "the needs line");
		helper.assertTrue(needs.get(0).color() == StatusCard.GREEN, "Needs: Nothing isn't green");
		helper.assertTrue(more(card).isEmpty(), "an 'and N more' line with nothing short: " + JobCardGameTests.lines(card));
		s.player().closeContainer();
		helper.succeed();
	}

	/** Exactly three kinds short: three Needs lines, each the kind and how many, and no "and N more". */
	//$ gametest_ticks_batch AREA '200' '"qaBuilderCardNeeds1109bThree"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBuilderCardNeeds1109bThree")
	public void exactlyThreeKindsShortShowThreeLinesAndNoMore(GameTestHelper helper) {
		Scene s = scene(helper);
		List<Item> left = new ArrayList<>();
		stock(helper, s, start(helper, s), 3, left);
		StatusCard card = JobCardGameTests.open(helper, new JobCardGameTests.Setup(s.builder(), s.player(), new ArrayList<>())).card();
		List<StatusCard.Line> needs = needs(card);
		helper.assertTrue(needs.size() == 3 && more(card).isEmpty(), "three kinds short " + left + ", the card: " + JobCardGameTests.lines(card));
		for (StatusCard.Line l : needs) {
			helper.assertTrue(left.stream().anyMatch(i -> l.icon().is(i)) && l.color() == StatusCard.ORANGE && l.value().getString().matches("×\\d+"),
				"a needs line that isn't one of the kinds short " + left + ": " + l.label().getString() + " " + l.value().getString());
		}
		s.player().closeContainer();
		helper.succeed();
	}

	/** Exactly four kinds short: three Needs lines and "and 1 more". */
	//$ gametest_ticks_batch AREA '200' '"qaBuilderCardNeeds1109bFour"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBuilderCardNeeds1109bFour")
	public void fourKindsShortShowThreeLinesAndOneMore(GameTestHelper helper) {
		Scene s = scene(helper);
		List<Item> left = new ArrayList<>();
		stock(helper, s, start(helper, s), 4, left);
		StatusCard card = JobCardGameTests.open(helper, new JobCardGameTests.Setup(s.builder(), s.player(), new ArrayList<>())).card();
		helper.assertTrue(needs(card).size() == 3, "four kinds short " + left + ", the card: " + JobCardGameTests.lines(card));
		helper.assertTrue(more(card).size() == 1 && more(card).get(0).label().getString().equals("and 1 more"),
			"four kinds short, the last line isn't 'and 1 more': " + JobCardGameTests.lines(card));
		s.player().closeContainer();
		helper.succeed();
	}
}
