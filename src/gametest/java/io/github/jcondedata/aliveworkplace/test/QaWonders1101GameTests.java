package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlans;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.wonder.WondersPage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (qa-lane-b-1101-0338) for the Wonders page and Wonder Plan (ROADMAP 35.3) with the real Wonder designs (35.9,
 * 35.11, 35.13, 35.15/35.15a, 35.17), the corners earlier suites leave (they use the test folly for the plan):
 * <ul>
 * <li>each real Wonder's whole blueprint ({@code wonder/<id>}, the one its plan carries and previews) holds exactly the
 * blocks its stages leave standing, of the same kinds, at its full size, and its plan carries that size;</li>
 * <li>each real Wonder's plan goes down with its centre on the hall in all four turns, and mirrored, and is refused once
 * its centre is past the hall's reach;</li>
 * <li>each data file asks what its roadmap item asks: a Town, its Legend with the named stand-in, and its extra
 * condition (3 Chapels, coastal, 20 research levels, a Gatehouse), in four stages titled as the item titles them;</li>
 * <li>Draw up the plans at the boundary: 31 emeralds take nothing and say so in English, 32 exactly buy the plan.</li>
 * </ul>
 */
public class QaWonders1101GameTests implements FabricGameTest {
	private static final String SMALL = "aliveworkplace_test:build_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final BlockPos HALL = new BlockPos(8, 2, 8);

	static final List<String> WONDERS = List.of("great_cathedral", "lighthouse", "great_library", "great_wall", "observatory");

	@GameTestGenerator
	public Collection<TestFunction> qaWonders1101() {
		List<TestFunction> out = new ArrayList<>();
		for (String id : WONDERS) {
			add(out, "qaWholePlanIsTheStagesStanding1101_" + id, EMPTY_STRUCTURE, id, QaWonders1101GameTests::wholeIsTheStages);
			add(out, "qaRequirementsAsTheItemAsks1101_" + id, EMPTY_STRUCTURE, id, QaWonders1101GameTests::requirements);
			add(out, "qaPlanGoesDownCentredOnTheHall1101_" + id, SMALL, id, QaWonders1101GameTests::centredOnTheHall);
		}
		return out;
	}

	private static void add(List<TestFunction> out, String name, String template, String id, BiConsumer<GameTestHelper, String> check) {
		out.add(new TestFunction(name, name, template, 200, 0, true, helper -> {
			check.accept(helper, id);
			helper.succeed();
		}));
	}

	private static String key(Component c) {
		return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	private static Wonder wonder(GameTestHelper helper, String id) {
		Wonder w = Wonders.loaded().get(AliveWorkplace.id(id));
		helper.assertTrue(w != null, id + " isn't loaded: " + Wonders.problems());
		return w;
	}

	// ---------------------------------------------------------------- the whole blueprint the plan carries

	/** wonder/<id> holds what the stages leave standing: same count, same kinds, same size; the plan carries that size. */
	static void wholeIsTheStages(GameTestHelper helper, String id) {
		wonder(helper, id);
		var server = helper.getLevel().getServer();
		Map<BlockPos, BlockState> standing = QaWonderDesignsGameTests.whole(helper, id);
		ResourceLocation wholeId = WonderBlueprints.idOf(AliveWorkplace.id(id));
		Optional<Blueprint> whole = BlueprintLibrary.get(server, wholeId);
		helper.assertTrue(whole.isPresent(), id + ": no whole blueprint " + wholeId);
		Blueprint b = whole.get();

		Map<String, Integer> want = new HashMap<>();
		standing.values().forEach(s -> want.merge(QaWonderDesignsGameTests.name(s), 1, Integer::sum));
		Map<String, Integer> got = new HashMap<>();
		int maxX = 0;
		int maxY = 0;
		int maxZ = 0;
		for (Blueprint.Entry e : b.blocks()) {
			if (!e.state().isAir()) {
				got.merge(QaWonderDesignsGameTests.name(e.state()), 1, Integer::sum);
				helper.assertTrue(standing.containsKey(e.pos()) && standing.get(e.pos()).equals(e.state()),
					id + ": the whole has " + e.state() + " at " + e.pos().toShortString() + " where the stages leave " + standing.get(e.pos()));
			}
		}
		for (BlockPos p : standing.keySet()) {
			maxX = Math.max(maxX, p.getX() + 1);
			maxY = Math.max(maxY, p.getY() + 1);
			maxZ = Math.max(maxZ, p.getZ() + 1);
		}
		helper.assertTrue(b.solidBlockCount() == standing.size(), id + ": the whole has " + b.solidBlockCount() + " blocks, the stages leave " + standing.size());
		helper.assertTrue(got.equals(want), id + ": the kinds differ");
		Vec3i size = b.size();
		helper.assertTrue(size.getX() >= maxX && size.getY() >= maxY && size.getZ() >= maxZ,
			id + ": the whole is " + size + " but its blocks reach " + maxX + " x " + maxY + " x " + maxZ);
		helper.assertTrue(BlueprintLibrary.list(server, false).stream().noneMatch(wholeId::equals), id + "'s whole is in the Blueprint Table");

		// its plan carries that size (the preview and the placement checks read it)
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ItemStack plan = WonderPlanItem.of(helper.getLevel(), helper.absolutePos(HALL), wonder(helper, id), Component.literal("Testville"));
		Optional<Vec3i> carried = BlueprintItem.data(plan).flatMap(BlueprintData::size);
		helper.assertTrue(carried.isPresent() && carried.get().equals(size), id + ": the plan carries " + carried + ", the whole is " + size);
		helper.assertTrue(BlueprintItem.data(plan).get().structure().equals(wholeId), id + ": the plan is of " + BlueprintItem.data(plan).get().structure());
		helper.assertTrue(plan.getHoverName().getString().startsWith("Wonder Plan: ") && !plan.getHoverName().getString().contains("wonder."),
			id + ": the plan's name " + plan.getHoverName().getString());
		AliveWorkplace.LOG.info("QA 1101: {} whole {} with {} blocks, the stages' {}", id, size, b.solidBlockCount(), standing.size());
	}

	// ---------------------------------------------------------------- placing a real Wonder's plan

	/** The placement of {@code size} turned and mirrored so its footprint's centre is on {@code hall}. */
	private static BlueprintData.Placement centred(ServerLevel level, Vec3i size, BlockPos hall, Rotation turn, Mirror mirror, int shiftX) {
		BlueprintData.Placement p = BlueprintItem.placementAt(level.dimension().location(), size, hall, turn, mirror);
		BoundingBox box = BlueprintOutline.bounds(p, size);
		int dx = hall.getX() - (box.minX() + box.maxX() + 1) / 2 + shiftX;
		int dz = hall.getZ() - (box.minZ() + box.maxZ() + 1) / 2;
		return new BlueprintData.Placement(p.dimension(), p.origin().offset(dx, 0, dz), turn, mirror);
	}

	/** Centred on the hall it goes down, in all four turns and mirrored; its centre past the hall's reach, it's refused. */
	static void centredOnTheHall(GameTestHelper helper, String id) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		// alone in its batch (the batch is the test's name): an earlier batch's finished building or Wonder site within
		// the plan's reach refused it ('It would stand over the finished Qahfu ...', local build 2026-11-01), as in B125
		Leftovers.wonderGround(helper, hall);
		ItemStack stack = WonderPlanItem.of(level, hall, wonder(helper, id), Component.literal("Testville"));
		WonderPlanItem.Plan plan = WonderPlanItem.plan(stack).orElseThrow();
		Vec3i size = BlueprintItem.data(stack).flatMap(BlueprintData::size).orElseThrow();
		List<String> wrong = new ArrayList<>();
		for (Rotation turn : Rotation.values()) {
			for (Mirror mirror : new Mirror[] {Mirror.NONE, Mirror.FRONT_BACK}) {
				Optional<Component> why = WonderPlans.placeRefusal(level, plan, centred(level, size, hall, turn, mirror, 0), size);
				why.ifPresent(c -> wrong.add(turn + "/" + mirror + ": " + c.getString()));
			}
		}
		helper.assertTrue(wrong.isEmpty(), id + " (" + size + ") centred on the hall is refused: " + wrong);
		// its centre a few blocks past the hall's reach: refused, and the line says why in English
		Optional<Component> far = WonderPlans.placeRefusal(level, plan, centred(level, size, hall, Rotation.NONE, Mirror.NONE, VillageHalls.RADIUS + 3), size);
		helper.assertTrue(far.isPresent() && key(far.get()).equals("message.aliveworkplace.wonder_plan.outside")
			&& far.get().getString().startsWith("Its center must be in Testville, within " + VillageHalls.RADIUS + " blocks of the hall")
			&& !far.get().getString().contains("%"), id + " past the reach: " + far.map(Component::getString).orElse("none"));
	}

	// ---------------------------------------------------------------- what each Wonder asks

	/** Each item's "Data:" line: a Town, the Legend and its stand-in, the extra condition; four stages titled as written. */
	static void requirements(GameTestHelper helper, String id) {
		JsonObject file = WonderBlueprintGameTests.file(helper.getLevel().getServer(), id);
		JsonArray reqs = file.getAsJsonArray("requirements");
		List<String> got = new ArrayList<>();
		for (JsonElement e : reqs) {
			got.add(describe(e.getAsJsonObject()));
		}
		List<String> want = switch (id) {
			case "great_cathedral" -> List.of("rank town", "legend seer [job cleric 5 x1]", "finished chapel x3");
			case "lighthouse" -> List.of("rank town", "legend merchant_prince [caravans 3]", "coastal");
			case "great_library" -> List.of("rank town", "legend old_sage [job scholar 5 x1]", "research 20");
			case "great_wall" -> List.of("rank town", "legend golem_smith [golems 4, job tinkerer 5 x1]", "finished gatehouse x1");
			case "observatory" -> List.of("rank town", "legend seer [job cleric 5 x1]");
			default -> throw new IllegalArgumentException(id);
		};
		helper.assertTrue(got.size() == want.size() && got.containsAll(want), id + " asks " + got + ", its item " + want);
		Wonder w = wonder(helper, id);
		// the loader keeps every one of them and adds the Master Architect every Wonder needs (35.2)
		helper.assertTrue(w.requirements().size() == reqs.size() + 1, id + ": the loader reads " + w.requirements().size() + " for the file's " + reqs.size() + " and the Master Architect");
		List<String> titles = switch (id) {
			case "great_cathedral" -> List.of("The Foundations and the West Front", "The Nave", "The Transepts and the Choir", "The Towers and the Rose Window");
			case "lighthouse" -> List.of("The Mole and the Quay", "The Tower", "The Gallery", "The Lantern and the Keeper's House");
			case "great_library" -> List.of("The Stacks", "The Galleries", "The Rotunda", "The Dome and the Portico");
			case "great_wall" -> List.of("The Great Gate", "The West Run", "The East Run", "The Walk and the Towers' Crowns");
			case "observatory" -> List.of("The Terrace and the Courtyard", "The Tower", "The Dome", "The Great Telescope and the Orrery");
			default -> throw new IllegalArgumentException(id);
		};
		List<String> read = w.stages().stream().map(s -> Component.translatable(s.name()).getString()).toList();
		helper.assertTrue(read.equals(titles), id + "'s stages read " + read);
		AliveWorkplace.LOG.info("QA 1101: {} asks {} in stages {}", id, got, read);
	}

	private static String path(JsonObject o, String field) {
		return ResourceLocation.parse(o.get(field).getAsString()).getPath();
	}

	private static String describe(JsonObject o) {
		String type = o.get("type").getAsString();
		return switch (type) {
			case "rank_at_least" -> "rank " + o.get("rank").getAsString();
			case "coastal" -> "coastal";
			case "research_levels" -> "research " + o.get("count").getAsInt();
			case "finished" -> "finished " + path(o, "blueprint") + " x" + o.get("count").getAsInt();
			case "caravan_routes" -> "caravans " + o.get("count").getAsInt();
			case "iron_golems" -> "golems " + o.get("count").getAsInt();
			case "job_level" -> "job " + path(o, "job") + " " + o.get("level").getAsInt() + " x" + o.get("count").getAsInt();
			case "legend" -> {
				List<String> stand = new ArrayList<>();
				o.getAsJsonArray("stand_in").forEach(s -> stand.add(describe(s.getAsJsonObject())));
				yield "legend " + path(o, "id") + " " + stand;
			}
			default -> type;
		};
	}

	// ---------------------------------------------------------------- Draw up the plans at its price

	/** Through the hall's page: 31 emeralds and an empty treasury take nothing and say why; with 32 exactly the plan is theirs. */
	//$ gametest_batch SMALL '"qaWonderPrice1101"'
	@GameTest(template = SMALL, batch = "qaWonderPrice1101")
	public void qaThirtyOneEmeraldsBuyNoPlanThirtyTwoDo(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean legends = Wonders.LEGENDS;
		Wonders.LEGENDS = false;
		Leftovers.after(helper, () -> Wonders.LEGENDS = legends);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setOwner(player.getUUID(), "Owner");
		entity.setRank(VillageRanks.Rank.TOWN);
		entity.setTreasury(0);
		for (BlockPos at : List.of(new BlockPos(3, 2, 3), new BlockPos(13, 2, 3), new BlockPos(8, 2, 13))) {
			Villager v = helper.spawn(EntityType.VILLAGER, at);
			v.setNoAi(true);
			v.setVillagerData(v.getVillagerData().setProfession(at.getX() == 3 ? ModVillagers.BUILDER : VillagerProfession.FARMER).setLevel(at.getX() == 3 ? 5 : 1));
		}
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded");
		player.getInventory().add(new ItemStack(Items.EMERALD, 31));

		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(HallPages.slot(WondersPage.PAGE), player);
		int index = new ArrayList<>(Wonders.all()).indexOf(folly);
		menu.press(WondersPage.slot(index), player);
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 31 && player.getInventory().countItem(ModItems.WONDER_PLAN) == 0,
			"31 emeralds: " + player.getInventory().countItem(Items.EMERALD) + " left, " + player.getInventory().countItem(ModItems.WONDER_PLAN) + " plans");
		WonderPlans.Drawn short1 = WonderPlans.draw(level, hall, player, FOLLY);
		String said = short1.refusal() == null ? "none" : short1.refusal().getString();
		helper.assertTrue(!short1.drawn() && key(short1.refusal()).equals("message.aliveworkplace.wonder_plan.money")
			&& said.startsWith("The plans cost 32 emeralds, and neither the treasury (") && !said.contains("%")
			&& player.getInventory().countItem(Items.EMERALD) == 31, "one short: " + said);

		player.getInventory().add(new ItemStack(Items.EMERALD, 1));
		menu.press(WondersPage.slot(index), player);
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0 && player.getInventory().countItem(ModItems.WONDER_PLAN) == 1,
			"32 exactly: " + player.getInventory().countItem(Items.EMERALD) + " left, " + player.getInventory().countItem(ModItems.WONDER_PLAN) + " plans");
		AliveWorkplace.LOG.info("QA 1101: one short said \"{}\"", said);
		helper.succeed();
	}
}
