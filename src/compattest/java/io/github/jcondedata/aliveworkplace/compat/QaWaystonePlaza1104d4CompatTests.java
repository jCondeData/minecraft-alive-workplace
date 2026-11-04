package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA from the player guide's Waystone Plaza page (docs/guide/waystone-plaza.md, ROADMAP 41.1 and 41.2) with the real
 * Waystones mod, lane-d-1104-2209: what the page promises that no test checked yet. The plaza is 7 by 5 by 7 in every
 * village style; the Steward proposes it once (not again once one stands); the Map's lines are green for a discovered
 * stone, yellow for one not discovered yet and red for a broken one, this village first; a broken stone isn't put back
 * from another kind of waystone, and is once the builder has one of the same kind.
 */
public class QaWaystonePlaza1104d4CompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String HUGE = "aliveworkplace_compat:huge_area";
	private static final ResourceLocation RULE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "waystone_plaza");
	private static final BlockPos HALL = new BlockPos(1, 2, 12);
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 3);
	/** The stone's spot in the plaza (lower half), as tools/blueprints/waystones.py draws it. */
	private static final BlockPos STONE = new BlockPos(3, 2, 3);
	private static final BlockPos HALL_A = new BlockPos(1, 2, 1);
	private static final BlockPos HALL_B = new BlockPos(28, 2, 28);
	private static final BlockPos PLAZA_A = new BlockPos(3, 2, 3);
	private static final BlockPos PLAZA_B = new BlockPos(19, 2, 19);

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at, String name) {
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(at);
		hall.setCustomName(Component.literal(name));
		return hall;
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	/** Sets every block of the plaza at {@code origin} as a finished one stands, and records it finished (owned by {@code owner}). */
	private static void paste(GameTestHelper helper, BlockPos origin, UUID owner) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint"));
		BuildPlan plan = BuildPlan.create(blueprint, placement(helper, origin));
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
				}
			}
		}
		BuildSiteManager.get(level).recordFinished(VillageWaystones.PLAZA, placement(helper, origin), owner);
	}

	private static StewardWishes.Status verdict(GameTestHelper helper) {
		StewardRules.Rule rule = StewardRules.all().stream().filter(r -> r.id().equals(RULE)).findFirst()
			.orElseThrow(() -> new GameTestAssertException("no Steward rule " + RULE));
		StewardConditions.Facts facts = StewardConditions.Facts.of(helper.getLevel(), helper.absolutePos(HALL));
		return StewardWishes.judge(rule, facts, StewardWishes.State.EMPTY, 0).status();
	}

	// ---------------------------------------------------------------- the build

	/** "Size: 7 by 5 by 7": the plaza of every village style (plains, desert, savanna, snowy, taiga), each with one waystone. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyStylesPlazaIsSevenByFiveBySevenWithOneWaystone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (String culture : List.of("plains", "desert", "savanna", "snowy", "taiga")) {
			ResourceLocation id = VillageWaystones.plazaFor(culture);
			Blueprint b = BlueprintLibrary.get(level, id).orElseThrow(() -> new GameTestAssertException("no plaza for " + culture + " (" + id + ")"));
			helper.assertTrue(b.size().equals(new Vec3i(7, 5, 7)), "the " + culture + " plaza is " + b.size());
			long stones = b.blocks().stream().filter(e -> VillageWaystones.isStone(e.state())
				&& BuiltInRegistries.BLOCK.getKey(e.state().getBlock()).getNamespace().equals("waystones")
				&& e.state().getValues().entrySet().stream().noneMatch(p -> p.getKey().getName().equals("half") && p.getValue().toString().equals("upper"))).count();
			helper.assertTrue(stones == 1, "the " + culture + " plaza has " + stones + " waystones");
		}
		helper.succeed();
	}

	/**
	 * "The village's Steward proposes a Waystone Plaza near the hall once": with a hall and no plaza, the rule holds; once
	 * a finished plaza stands by the hall, it doesn't, so a second one is never proposed.
	 */
	//$ gametest_batch AREA '"qa1104d4PlazaOnce"'
	@GameTest(template = AREA, batch = "qa1104d4PlazaOnce")
	public void theStewardProposesThePlazaOnlyUntilOneStands(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		BuildSiteManager.get(level).forgetFinished(placement(helper, ORIGIN));
		hall(helper, HALL, "Onceford");
		try {
			helper.assertTrue(verdict(helper) == StewardWishes.Status.HELD, "with a hall and no plaza: " + verdict(helper));
			paste(helper, ORIGIN, UUID.randomUUID());
			helper.assertTrue(verdict(helper) != StewardWishes.Status.HELD, "with a plaza standing, the Steward still proposes one: " + verdict(helper));
			StewardConditions.Facts facts = StewardConditions.Facts.of(level, helper.absolutePos(HALL));
			boolean wished = StewardWishes.rank(StewardRules.all(), facts, StewardWishes.State.EMPTY, 0).stream()
				.anyMatch(w -> w.effect().blueprint().map(VillageWaystones::isPlaza).orElse(false));
			helper.assertFalse(wished, "with a plaza standing, the Steward wishes for a second");
		} finally {
			BuildSiteManager.get(level).forgetFinished(placement(helper, ORIGIN));
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Map

	private static void known(GameTestHelper helper, BlockPos at, String name, ServerPlayer owner) {
		VillageHallBlockEntity hall = hall(helper, at, name);
		hall.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		ServerLevel level = helper.getLevel();
		RealmData.get(level.getServer()).putKnown(new RealmData.Known(GlobalPos.of(level.dimension(), helper.absolutePos(at)), Component.literal(name),
			Optional.of(owner.getUUID()), owner.getGameProfile().getName(), "hamlet", 0, 0, level.getGameTime()));
	}

	private static List<Component> mapLore(ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		return menu.icon(VillageHallScreen.MAP).getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
	}

	/** The village lines of the Map's lore (after the network heading), as "text | color". */
	private static List<String> villageLines(List<Component> lore) {
		return lore.stream().filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().startsWith("screen.aliveworkplace.hall.waystone."))
			.map(c -> c.getString() + " | " + (c.getStyle().getColor() == null ? "none" : c.getStyle().getColor().serialize())).toList();
	}

	private static String color(ChatFormatting f) {
		return TextColor.fromLegacyFormat(f).serialize();
	}

	/**
	 * "Each says whether you have discovered its stone (green), not yet (yellow ...), or that its waystone is broken
	 * (red ...)", this one first: the player discovered the far village's stone and not this one's: this village's line
	 * is first, yellow; the far one's second, green; the far stone broken, its line is red.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa1104d4MapColors"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1104d4MapColors")
	public void theMapsLinesAreGreenYellowAndRedThisVillageFirst(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		try {
			known(helper, HALL_A, "Ashford", player);
			known(helper, HALL_B, "Brookmere", player);
			paste(helper, PLAZA_A, UUID.randomUUID());
			paste(helper, PLAZA_B, UUID.randomUUID());
			VillageWaystones.round(level, a);
			VillageWaystones.round(level, b);
			BlockPos stoneB = helper.absolutePos(PLAZA_B).offset(STONE);
			Waystone brookmere = WaystonesAPI.getWaystoneAt(level, stoneB).orElseThrow(() -> new GameTestAssertException("Brookmere has no waystone"));
			WaystonesAPI.activateWaystone(player, brookmere); // the far one discovered, this one not
			String where = VillageHallScreen.where(a, stoneB).getString();
			List<String> lines = villageLines(mapLore(player, a));
			helper.assertTrue(lines.equals(List.of("Ashford, this village: not discovered yet | " + color(ChatFormatting.YELLOW),
				"Brookmere, " + where + ": discovered | " + color(ChatFormatting.GREEN))), "the Map's lines: " + lines);
			level.setBlock(stoneB.above(), Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(stoneB, Blocks.AIR.defaultBlockState(), 3);
			VillageWaystones.round(level, b);
			List<String> broken = villageLines(mapLore(player, a));
			helper.assertTrue(broken.equals(List.of("Ashford, this village: not discovered yet | " + color(ChatFormatting.YELLOW),
				"Brookmere, " + where + ": its waystone is broken | " + color(ChatFormatting.RED))), "the Map's lines with Brookmere's stone broken: " + broken);
		} finally {
			for (BlockPos at : List.of(HALL_A, HALL_B)) {
				RealmData.get(level.getServer()).forgetKnown(GlobalPos.of(level.dimension(), helper.absolutePos(at)));
				VillageWaystones.Data.get(level).forget(helper.absolutePos(at));
			}
			for (BlockPos plaza : List.of(PLAZA_A, PLAZA_B)) {
				BuildSiteManager.get(level).forgetFinished(placement(helper, plaza));
			}
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the stone broken

	private static Optional<String> nameAt(GameTestHelper helper, BlockPos stone) {
		return VillageWaystones.EXTENSION.call(s -> s.nameAt(helper.getLevel(), stone), Optional.<String>empty());
	}

	/**
	 * "They need a waystone of the same kind in their chest to do it": the plaza's plain waystone broken and only a mossy
	 * waystone in the builder's chest, the repair starts but no stone goes back (and never a mossy one) for 600 ticks; a
	 * plain waystone put in the chest, the stone is back, named after the village again.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qa1104d4PlazaKind"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa1104d4PlazaKind")
	public void aBrokenStoneIsntPutBackFromAnotherKindOfWaystoneButIsFromItsOwn(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		hall(helper, HALL, "Mossbury");
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		paste(helper, ORIGIN, builder.getUUID());
		VillageWaystones.round(level, helper.absolutePos(HALL));
		BlockPos stone = helper.absolutePos(ORIGIN).offset(STONE);
		helper.assertTrue(nameAt(helper, stone).equals(Optional.of("Mossbury")), "the stone before it was broken: " + nameAt(helper, stone));
		BlockState lower = level.getBlockState(stone);
		ResourceLocation kind = BuiltInRegistries.BLOCK.getKey(lower.getBlock());
		helper.assertTrue(kind.equals(ResourceLocation.fromNamespaceAndPath("waystones", "waystone")), "the plains plaza's stone is " + kind);
		level.setBlock(stone.above(), Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(stone, Blocks.AIR.defaultBlockState(), 3);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("waystones", "mossy_waystone"))));
		helper.assertFalse(chest.getItem(0).isEmpty(), "no mossy waystone item");
		boolean was = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		int every = Upkeep.EVERY;
		Upkeep.ENABLED = true;
		Upkeep.ASK = false;
		Upkeep.EVERY = 100; // the builder looks over the village every 100 ticks, so a repair that gave up is looked at again
		Runnable restore = () -> {
			Upkeep.ENABLED = was;
			Upkeep.ASK = asks;
			Upkeep.EVERY = every;
			BuildSiteManager.get(level).forgetFinished(placement(helper, ORIGIN));
		};
		helper.startSequence()
			.thenExecuteAfter(2, () -> {
				BuildSite site = ModAttachments.BUILDER_JOB.has(builder) ? Builders.activeSite(level, builder) : Upkeep.look(level, builder);
				helper.assertTrue(site != null && site.isRepair(), "no repair started");
			})
			.thenExecuteFor(600, () -> {
				BlockState there = level.getBlockState(stone);
				helper.assertFalse(VillageWaystones.isStone(there), "a stone went back with only a mossy waystone in the chest: " + there);
			})
			.thenExecute(() -> chest.setItem(1, new ItemStack(lower.getBlock().asItem())))
			.thenWaitUntil(() -> {
				BlockState there = level.getBlockState(stone);
				helper.assertTrue(there.is(lower.getBlock()), "no plain waystone back with one in the chest: " + there + "; builder job " + ModAttachments.BUILDER_JOB.has(builder)
					+ ", gave up " + BuildSiteManager.get(level).gaveUp(placement(helper, ORIGIN)).contains(stone));
				helper.assertTrue(nameAt(helper, stone).equals(Optional.of("Mossbury")), "the repaired stone: " + nameAt(helper, stone));
			})
			.thenExecute(restore)
			.thenSucceed();
	}
}
