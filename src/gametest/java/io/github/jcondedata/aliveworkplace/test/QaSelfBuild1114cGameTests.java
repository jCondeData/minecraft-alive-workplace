package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.MaterialLedger;
import io.github.jcondedata.aliveworkplace.city.CaravanRoads;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.city.Renewals;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.city.Walls;
import io.github.jcondedata.aliveworkplace.command.CitySoak;
import io.github.jcondedata.aliveworkplace.command.Soak;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1114, 2026-11-14) for ROADMAP 27.1 (the design note: appointing a Steward with the City Plan, the
 * {@code steward} switch that leaves the City Plan painting, the build site's {@code steward} flag and {@code hall} with
 * their defaults, the guide page's words) and 27.22 (the city yardstick: {@code /workplace city} only on benchmark
 * servers, a short city run's village and its clean end, meals eaten from the store counted only while a soak counts,
 * items counted before and after). Written from the spec; the existing CitySoak/Steward/QaSteward1031 tests cover the
 * rest (Journeyman's boundary, the recipe, the plan's save).
 */
public class QaSelfBuild1114cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);

	private static ItemStack planFor(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		ItemStack stack = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(helper.getLevel(), player, stack, helper.absolutePos(hall));
		return stack;
	}

	/** A right-click on {@code villager} as the client sends it, sneaking or not, with {@code held} in the main hand. */
	private static net.minecraft.world.InteractionResult click(ServerPlayer player, Villager villager, ItemStack held, boolean sneaking) {
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.setShiftKeyDown(sneaking);
		return UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, villager, null);
	}

	private static boolean stewardOf(GameTestHelper helper, Villager villager, BlockPos hall) {
		return villager.getVillagerData().getProfession() == ModVillagers.STEWARD
			&& Stewards.hallOf(helper.getLevel(), villager).equals(Optional.of(helper.absolutePos(hall)));
	}

	private static List<String> words(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	private static void setup(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.stewardSites(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
	}

	// ---- 27.1: appointing through the player's own click -------------------------------------------------------------

	/**
	 * The design note's entry point: sneak-right-click a seasoned Builder by the hall with its City Plan and he is that
	 * hall's Steward, told in the words "X is now the Steward of Y"; a plain right-click (not sneaking) with the plan in
	 * hand appoints nobody.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1114cSneak"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1114cSneak")
	public void aSneakClickWithTheCityPlanAppointsAndAPlainClickDoesNot(GameTestHelper helper) {
		setup(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		Villager plain = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.east(2)));
		click(player, plain, planFor(helper, player, HALL), false);
		helper.assertTrue(plain.getVillagerData().getProfession() == ModVillagers.BUILDER, "a plain right-click appointed: " + plain.getVillagerData());

		Villager builder = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
		String who = builder.getDisplayName().getString(); // his name as the player clicks him (a Builder)
		net.minecraft.world.InteractionResult r = click(player, builder, planFor(helper, player, HALL), true);
		helper.assertTrue(r.consumesAction(), "the sneak-click passed: " + r);
		helper.assertTrue(stewardOf(helper, builder, HALL), "the sneak-click didn't make him the hall's Steward: " + builder.getVillagerData()
			+ " hall " + Stewards.hallOf(helper.getLevel(), builder));
		String expected = who + " is now the Steward of "
			+ VillageHalls.name(helper.getLevel(), helper.absolutePos(HALL)).getString();
		helper.assertTrue(words(seen).contains(expected), "no \"" + expected + "\": " + words(seen));
		helper.succeed();
	}

	/**
	 * Config {@code steward} off (design note: "no Steward appointments (the City Plan still paints)"): even a seasoned
	 * Builder's sneak-click is refused, in words, he stays a Builder and the plan is kept; and the plan still paints a zone
	 * through the plan screen's packet.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1114cOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1114cOff")
	public void withStewardsOffASeasonedBuilderIsRefusedButThePlanStillPaints(GameTestHelper helper) {
		setup(helper);
		boolean was = Stewards.ENABLED;
		Leftovers.after(helper, () -> Stewards.ENABLED = was);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		entity.setOwner(player.getUUID(), player.getGameProfile().getName());
		Villager builder = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
		helper.assertTrue(Stewards.qualifies(builder), "setup: the builder doesn't qualify");
		ItemStack plan = planFor(helper, player, HALL);
		Stewards.ENABLED = false;
		click(player, builder, plan, true);
		helper.assertTrue(builder.getVillagerData().getProfession() == ModVillagers.BUILDER, "appointed with steward off: " + builder.getVillagerData());
		helper.assertTrue(words(seen).contains("Stewards are switched off on this server"), "the refusal's words: " + words(seen));
		helper.assertTrue(player.getMainHandItem().is(ModItems.CITY_PLAN), "the refusal took the City Plan: " + player.getMainHandItem());

		BlockPos hall = helper.absolutePos(HALL);
		player.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(CityPlans.Edit.addZone(hall, "homes", "Homes 1", "")));
		java.util.BitSet cells = new java.util.BitSet();
		cells.set(CityPlan.cellAt(hall, hall.offset(20, 0, 0)));
		player.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(CityPlans.Edit.paint(hall, 0, cells)));
		CityPlan now = entity.plan();
		helper.assertTrue(now.zones().size() == 1 && !now.zones().get(0).cells().isEmpty(), "the plan didn't paint with steward off: " + now.zones());
		// switched back on, the same click appoints him: the switch was the only reason
		Stewards.ENABLED = true;
		click(player, builder, player.getMainHandItem(), true);
		helper.assertTrue(stewardOf(helper, builder, HALL), "back on, still not appointed: " + builder.getVillagerData());
		helper.succeed();
	}

	/**
	 * "Appointed beside his own hall": a builder 5 blocks from the hall is in reach; at 6 he is refused with "Bring the
	 * villager to ...'s Village Hall" naming the hall, stays a Builder, and walked back in he is appointed.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1114cReach"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1114cReach")
	public void aBuilderSixBlocksFromTheHallIsRefusedAndFiveIsAppointed(GameTestHelper helper) {
		setup(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		BlockPos hall = helper.absolutePos(HALL);
		Villager builder = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.east(6)));
		builder.setNoAi(true);
		builder.teleportTo(hall.getX() + 6.5, hall.getY(), hall.getZ() + 0.5);
		click(player, builder, planFor(helper, player, HALL), true);
		helper.assertTrue(builder.getVillagerData().getProfession() == ModVillagers.BUILDER, "appointed 6 blocks from the hall");
		String far = "Bring the villager to " + VillageHalls.name(helper.getLevel(), hall).getString()
			+ "'s Village Hall: a Steward is appointed beside his own hall";
		helper.assertTrue(words(seen).contains(far), "no \"" + far + "\": " + words(seen));
		builder.teleportTo(hall.getX() + 5.5, hall.getY(), hall.getZ() + 0.5);
		click(player, builder, player.getMainHandItem(), true);
		helper.assertTrue(stewardOf(helper, builder, HALL), "not appointed 5 blocks from the hall: " + builder.getVillagerData());
		helper.succeed();
	}

	/**
	 * One Steward per hall, and only a grown villager: a baby Builder and a nitwit are refused with "Only a grown villager
	 * can be a Steward"; with a Steward already at the hall, a second seasoned Builder is refused with "Y already has a
	 * Steward: X" naming the first, stays a Builder, and the first keeps the hall.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1114cOnePerHall"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1114cOnePerHall")
	public void aBabyANitwitAndASecondStewardAreRefusedInWords(GameTestHelper helper) {
		setup(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		BlockPos hall = helper.absolutePos(HALL);
		String grown = "Only a grown villager can be a Steward";
		Villager baby = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.east(2)));
		baby.setBaby(true);
		click(player, baby, planFor(helper, player, HALL), true);
		helper.assertTrue(baby.getVillagerData().getProfession() != ModVillagers.STEWARD, "a baby was appointed");
		helper.assertTrue(words(seen).contains(grown), "the baby's refusal: " + words(seen));
		seen.clear();
		Villager nitwit = helper.spawn(EntityType.VILLAGER, HALL.west(2));
		nitwit.setVillagerData(nitwit.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.NITWIT));
		click(player, nitwit, planFor(helper, player, HALL), true);
		helper.assertTrue(nitwit.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.NITWIT, "a nitwit was appointed");
		helper.assertTrue(words(seen).contains(grown), "the nitwit's refusal: " + words(seen));

		Villager first = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
		click(player, first, planFor(helper, player, HALL), true);
		helper.assertTrue(stewardOf(helper, first, HALL), "setup: the first wasn't appointed");
		seen.clear();
		Villager second = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.north(2)));
		click(player, second, planFor(helper, player, HALL), true);
		helper.assertTrue(second.getVillagerData().getProfession() == ModVillagers.BUILDER, "a second Steward at one hall: " + second.getVillagerData());
		helper.assertTrue(stewardOf(helper, first, HALL), "the first lost the hall");
		String taken = VillageHalls.name(helper.getLevel(), hall).getString() + " already has a Steward: " + first.getDisplayName().getString();
		helper.assertTrue(words(seen).contains(taken), "no \"" + taken + "\": " + words(seen));
		helper.succeed();
	}

	/** A build site's {@code steward} flag and {@code hall} survive save and reload; a site saved without them (a player's own, or from 1.0) loads as a player's own. */
	//$ gametest_ticks_batch AREA '20' '"qa1114cSiteSave"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qa1114cSiteSave")
	public void aStewardsSiteKeepsItsHallThroughSaveAndAnOldSiteIsAPlayersOwn(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(10, 2, 10)),
			Rotation.NONE, Mirror.NONE);
		BuildSiteManager manager = BuildSiteManager.get(level);
		BuildSite site = manager.create(java.util.UUID.randomUUID(), "", AliveWorkplace.id("stone_house"), placement);
		try {
			site.setStewardHall(hall);
			CompoundTag tag = site.save();
			BuildSite copy = BuildSite.load(tag);
			helper.assertTrue(copy.isSteward() && hall.equals(copy.stewardHall()), "the Steward's hall after reload: " + copy.stewardHall());
			CompoundTag old = tag.copy();
			old.remove("steward");
			old.remove("hall");
			BuildSite fromOld = BuildSite.load(old);
			helper.assertTrue(!fromOld.isSteward() && fromOld.stewardHall() == null, "a site saved without the flag is the Steward's: " + fromOld.stewardHall());
			CompoundTag half = tag.copy();
			half.remove("hall"); // a flag with no hall is no Steward's site
			helper.assertTrue(!BuildSite.load(half).isSteward(), "a flag without a hall loaded as the Steward's");
			site.setStewardHall(null);
			helper.assertTrue(!BuildSite.load(site.save()).isSteward(), "a player's own site saved as the Steward's");
		} finally {
			manager.remove(site.id());
		}
		helper.succeed();
	}

	/** The Guide Book's City Plan page names the real ingredients and the real level a Steward needs (27.1a), by the game's own names. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theGuidePageNamesTheRealRecipeAndLevel(GameTestHelper helper) {
		String title = Component.translatable("guide.aliveworkplace.page.city_plan.title").getString();
		String text = Component.translatable("guide.aliveworkplace.page.city_plan.text").getString();
		helper.assertTrue(title.equals("A village from a plan"), "title: " + title);
		helper.assertTrue(!text.startsWith("guide.aliveworkplace"), "the page's text is a raw key");
		String lower = text.toLowerCase(java.util.Locale.ROOT);
		// the recipe's map is vanilla's Empty Map; the page calls it "a map"
		helper.assertTrue(lower.contains("a map"), "the page doesn't name the map: " + text);
		for (Item item : List.of(ModItems.BLANK_BLUEPRINT, Items.HEART_OF_THE_SEA, ModItems.CITY_PLAN)) {
			String name = item.getDescription().getString().toLowerCase(java.util.Locale.ROOT);
			helper.assertTrue(lower.contains(name), "the page doesn't name \"" + name + "\": " + text);
		}
		String level = BuilderLevels.levelName(Stewards.MIN_BUILDER_LEVEL).getString();
		helper.assertTrue(text.contains(level + " Builder"), "the page doesn't say a " + level + " Builder: " + text);
		helper.assertTrue(text.toLowerCase(java.util.Locale.ROOT).contains("sneak-right-click"), "the page doesn't say how to appoint: " + text);
		helper.succeed();
	}

	// ---- 27.22: the city yardstick --------------------------------------------------------------------------------------

	/** {@code /workplace city} is for benchmark servers only: on an ordinary server (this one) the command isn't there, while other workplace commands are. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theCityCommandIsOnlyOnBenchmarkServers(GameTestHelper helper) {
		helper.assertTrue(!Boolean.getBoolean("aliveworkplace.benchmark"), "setup: this test server is a benchmark server");
		var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
		var workplace = root.getChild("workplace");
		helper.assertTrue(workplace != null && !workplace.getChildren().isEmpty(), "no /workplace command at all");
		helper.assertTrue(workplace.getChild("city") == null, "/workplace city is on an ordinary server");
		helper.succeed();
	}

	/**
	 * A short city run, started where the scene starts it: the village it lays out (a Steward of the hall, 3 builders, 12
	 * more villagers, a stocked store, the six-zone plan in Run the village), its result line with nothing out of its zone,
	 * no item off and no stall, and every switch it turned on put back as it was; a second finish has nothing to end.
	 */
	//$ gametest_ticks_batch EMPTY_STRUCTURE '400' '"qa1114cCityRun"'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "qa1114cCityRun")
	public void aShortCityRunLaysOutTheVillageAndPutsEverySwitchBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GameRules rules = level.getGameRules();
		boolean daylight = rules.getBoolean(GameRules.RULE_DAYLIGHT);
		boolean mobs = rules.getBoolean(GameRules.RULE_DOMOBSPAWNING);
		boolean free = rules.getBoolean(ModGameRules.FREE_MATERIALS);
		long time = level.getDayTime();
		boolean[] before = flags();
		BlockPos hall = helper.absolutePos(new BlockPos(0, 2, 0)).offset(2500, 0, 2500);
		AABB box = new AABB(hall).inflate(CitySoak.REACH + 8, 40, CitySoak.REACH + 8);
		Leftovers.after(helper, () -> {
			if (CitySoak.progress()[2] == 1) {
				CitySoak.finish();
			}
			level.getEntitiesOfClass(Villager.class, box).forEach(v -> v.discard());
			BuildSiteManager manager = BuildSiteManager.get(level);
			for (BuildSite s : new ArrayList<>(manager.all())) {
				if (box.contains(net.minecraft.world.phys.Vec3.atCenterOf(s.placement().origin()))) {
					manager.remove(s.id());
				}
			}
			level.setBlockAndUpdate(hall, Blocks.AIR.defaultBlockState());
			VillageHalls.forgetNearest();
			rules.getRule(GameRules.RULE_DAYLIGHT).set(daylight, level.getServer());
			rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(mobs, level.getServer());
			rules.getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.setDayTime(time);
		});
		helper.assertTrue(CitySoak.progress()[2] == 0, "setup: a city run is already going");

		String line = CitySoak.begin(level, hall, 1);
		helper.assertTrue(line.equals("City: a Steward, " + CitySoak.BUILDERS + " builders, " + CitySoak.VILLAGERS
			+ " villagers, 6 zones, 2 streets and a wall line; 1 days"), "the start line: " + line);
		helper.assertTrue(CitySoak.progress()[2] == 1, "not running after begin");
		helper.assertTrue(MaterialLedger.isOn(), "the ledger isn't counting");
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null && entity.plan().mode() == CityPlan.Mode.RUN && entity.plan().zones().size() == 6,
			"the hall's plan: " + (entity == null ? null : entity.plan()));
		List<Villager> all = level.getEntitiesOfClass(Villager.class, box);
		long stewards = all.stream().filter(v -> v.getVillagerData().getProfession() == ModVillagers.STEWARD).count();
		long builders = all.stream().filter(v -> v.getVillagerData().getProfession() == ModVillagers.BUILDER).count();
		helper.assertTrue(stewards == 1, "Stewards: " + stewards);
		Villager steward = all.stream().filter(v -> v.getVillagerData().getProfession() == ModVillagers.STEWARD).findFirst().get();
		helper.assertTrue(Stewards.hallOf(level, steward).equals(Optional.of(hall)), "the Steward isn't the hall's: " + Stewards.hallOf(level, steward));
		helper.assertTrue(builders == CitySoak.BUILDERS, "builders: " + builders);
		helper.assertTrue(all.size() >= 1 + CitySoak.BUILDERS + CitySoak.VILLAGERS, "villagers: " + all.size());
		Map<Item, Integer> stored = stored(level, hall);
		helper.assertTrue(stored.getOrDefault(Items.COBBLESTONE, 0) >= 256 && stored.getOrDefault(Items.BREAD, 0) >= 128,
			"the store isn't stocked: " + stored);

		helper.runAfterDelay(40, () -> {
			String result = CitySoak.finish();
			helper.assertTrue(result != null && result.startsWith(CitySoak.RESULT + " "), "the result line: " + result);
			helper.assertTrue(result.contains("outside their zone or in Keep Clear: none"), "a build out of its zone: " + result);
			helper.assertTrue(result.contains("; 0 stalls;"), "stalls in a 40-tick run: " + result);
			helper.assertTrue(result.contains("items off: none;"), "items off in a 40-tick run: " + result);
			helper.assertTrue(result.equals(CitySoak.lastResult), "lastResult isn't the result line");
			helper.assertTrue(!MaterialLedger.isOn(), "the ledger still counts after the run");
			boolean[] after = flags();
			helper.assertTrue(java.util.Arrays.equals(before, after), "the switches weren't put back: before " + java.util.Arrays.toString(before)
				+ " after " + java.util.Arrays.toString(after));
			helper.assertTrue(CitySoak.finish() == null, "a second finish ended something");
			helper.assertTrue(java.util.Arrays.equals(CitySoak.progress(), new int[] {0, 0, 0}), "progress after the run: "
				+ java.util.Arrays.toString(CitySoak.progress()));
			helper.succeed();
		});
	}

	private static boolean[] flags() {
		return new boolean[] {Stewards.ENABLED, StewardDesk.SELF_RUN, Roads.ENABLED, Walls.ENABLED, Renewals.ENABLED, CaravanRoads.ENABLED,
			VillageRaids.ENABLED, BanditCamps.ENABLED};
	}

	private static Map<Item, Integer> stored(ServerLevel level, BlockPos hall) {
		Map<Item, Integer> out = new TreeMap<>((a, b) -> BuiltInRegistries.ITEM.getKey(a).compareTo(BuiltInRegistries.ITEM.getKey(b)));
		BlockPos from = hall.offset(-CitySoak.REACH, -8, -CitySoak.REACH);
		BlockPos to = hall.offset(CitySoak.REACH, 40, CitySoak.REACH);
		BoundingBox area = BoundingBox.fromCorners(from, to);
		for (int cx = from.getX() >> 4; cx <= to.getX() >> 4; cx++) {
			for (int cz = from.getZ() >> 4; cz <= to.getZ() >> 4; cz++) {
				for (var be : level.getChunk(cx, cz).getBlockEntities().values()) {
					if (be instanceof net.minecraft.world.Container c && area.isInside(be.getBlockPos())) {
						for (int i = 0; i < c.getContainerSize(); i++) {
							ItemStack s = c.getItem(i);
							if (!s.isEmpty()) {
								out.merge(s.getItem(), s.getCount(), Integer::sum);
							}
						}
					}
				}
			}
		}
		return out;
	}

	/**
	 * "No item duplicated or lost (counted before and after)", with meals: a meal a villager eats from the store is
	 * counted while a soak counts (the item leaves the chest and the ledger has it), and not at all once it stops.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1114cMeals"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1114cMeals")
	public void aMealFromTheStoreIsCountedOnlyWhileASoakCounts(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.assertTrue(!MaterialLedger.isOn(), "setup: the ledger is already counting");
		Leftovers.after(helper, MaterialLedger::stop);
		ServerLevel level = helper.getLevel();
		BlockPos chestAt = new BlockPos(3, 2, 3);
		helper.setBlock(chestAt, Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(chestAt));
		chest.setItem(0, new ItemStack(Items.BREAD, 3));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 3));
		List<BlockPos> store = List.of(helper.absolutePos(chestAt));

		MaterialLedger.start();
		helper.assertTrue(VillageNeeds.eat(level, villager, store), "he didn't eat from a store with bread");
		helper.assertTrue(chest.getItem(0).getCount() == 2, "the chest after one meal: " + chest.getItem(0));
		helper.assertTrue(MaterialLedger.eaten().equals(Map.of(Items.BREAD, 1)), "eaten while counting: " + MaterialLedger.eaten());
		// the books balance: 3 stocked, 1 eaten, 2 left
		String off = Soak.itemsOff(Map.of(Items.BREAD, 3), MaterialLedger.gained(), MaterialLedger.eaten(), MaterialLedger.dropped(),
			Map.of(Items.BREAD, chest.getItem(0).getCount()));
		helper.assertTrue(off.equals("none"), "items off with the meal counted: " + off);
		MaterialLedger.stop();

		helper.assertTrue(VillageNeeds.eat(level, villager, store), "he didn't eat the second time");
		helper.assertTrue(chest.getItem(0).getCount() == 1, "the chest after two meals: " + chest.getItem(0));
		helper.assertTrue(MaterialLedger.eaten().equals(Map.of(Items.BREAD, 1)), "a meal counted after the soak stopped: " + MaterialLedger.eaten());
		MaterialLedger.start();
		helper.assertTrue(MaterialLedger.eaten().isEmpty(), "a new soak starts with old meals: " + MaterialLedger.eaten());
		chest.setItem(0, ItemStack.EMPTY);
		helper.assertTrue(!VillageNeeds.eat(level, villager, store), "he ate from an empty store");
		helper.assertTrue(MaterialLedger.eaten().isEmpty(), "an empty store counted a meal: " + MaterialLedger.eaten());
		helper.succeed();
	}

	/** The before-and-after count names exactly the items that don't add up, by how many, either way; a balanced count reads "none". */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void itemsOffNamesOnlyWhatDoesntAddUp(GameTestHelper helper) {
		Map<Item, Integer> stocked = Map.of(Items.COBBLESTONE, 100, Items.BREAD, 10);
		Map<Item, Integer> gained = Map.of(Items.OAK_LOG, 4);
		Map<Item, Integer> used = Map.of(Items.COBBLESTONE, 30, Items.BREAD, 3);
		Map<Item, Integer> dropped = Map.of(Items.COBBLESTONE, 2);
		Map<Item, Integer> balanced = Map.of(Items.COBBLESTONE, 68, Items.BREAD, 7, Items.OAK_LOG, 4);
		String none = Soak.itemsOff(stocked, gained, used, dropped, balanced);
		helper.assertTrue(none.equals("none"), "a balanced count: " + none);
		String dup = Soak.itemsOff(stocked, gained, used, dropped, Map.of(Items.COBBLESTONE, 69, Items.BREAD, 7, Items.OAK_LOG, 4));
		helper.assertTrue(dup.equals("cobblestone +1"), "one duplicated: " + dup);
		String lost = Soak.itemsOff(stocked, gained, used, dropped, Map.of(Items.COBBLESTONE, 68, Items.OAK_LOG, 4));
		helper.assertTrue(lost.equals("bread -7"), "all the bread lost: " + lost);
		String fromNowhere = Soak.itemsOff(stocked, gained, used, dropped, Map.of(Items.COBBLESTONE, 68, Items.BREAD, 7, Items.OAK_LOG, 4, Items.DIAMOND, 1));
		helper.assertTrue(fromNowhere.equals("diamond +1"), "an item from nowhere: " + fromNowhere);
		String both = Soak.itemsOff(stocked, gained, used, dropped, Map.of(Items.COBBLESTONE, 67, Items.BREAD, 8, Items.OAK_LOG, 4));
		helper.assertTrue(both.equals("bread +1, cobblestone -1"), "one each way: " + both);
		helper.succeed();
	}
}
