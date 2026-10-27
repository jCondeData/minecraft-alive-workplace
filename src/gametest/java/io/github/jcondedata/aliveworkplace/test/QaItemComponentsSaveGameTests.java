package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Shapes;
import io.github.jcondedata.aliveworkplace.blueprint.SupplyReport;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.colony.ColonyCharterItem;
import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.guard.PatrolMapItem;
import io.github.jcondedata.aliveworkplace.guard.RallyBannerItem;
import io.github.jcondedata.aliveworkplace.hall.VillageBannerItem;
import io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem;
import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.story.Gifts;
import io.github.jcondedata.aliveworkplace.travel.TicketData;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;

/**
 * QA (area: saves): every item data component the mod registers is kept on items in chests and inventories of the
 * owner's live world, so each one must come back exactly from a chest's save (ItemStack.save, then parse, also through
 * the NBT bytes written to disk) and from the trip to a player (ItemStack.STREAM_CODEC). Samples are realistic, not
 * defaults: every optional filled, lists non-empty, positions far from the origin, another dimension. A guard fails when
 * a new component is registered without a sample here. For the five components no other test covered (village_banner,
 * patrol, city_plan_hall, colony_charter, made_day) the reloaded item's own behaviour is checked to read it the same.
 */
public class QaItemComponentsSaveGameTests implements FabricGameTest {

	/** One component's sample: the item that carries it, and a realistic value. */
	private record Sample<T>(Item item, int count, DataComponentType<T> type, T value) {
	}

	private static ResourceLocation mc(String path) {
		return ResourceLocation.withDefaultNamespace(path);
	}

	private static final GlobalPos HALL = GlobalPos.of(Level.OVERWORLD, new BlockPos(812, 64, -3301));

	/** A realistic, non-default value for every component under our namespace. */
	private static List<Sample<?>> samples() {
		List<Sample<?>> out = new ArrayList<>();
		out.add(new Sample<>(ModItems.BLUEPRINT, 1, ModComponents.BLUEPRINT, new BlueprintData(AliveWorkplace.id("cottage"),
			Optional.of(new Vec3i(9, 7, 11)),
			Optional.of(new BlueprintData.Placement(Level.NETHER.location(), new BlockPos(-1203, 71, 4410), Rotation.CLOCKWISE_90, Mirror.FRONT_BACK)),
			false, true, true)));
		out.add(new Sample<>(ModItems.VILLAGE_BANNER, 5, ModComponents.VILLAGE_BANNER, DyeColor.PURPLE));
		out.add(new Sample<>(ModItems.QUARRY_MARKER, 1, ModComponents.QUARRY, new QuarryData(Optional.of(Level.OVERWORLD.location()),
			Optional.of(new BlockPos(100, 40, -20)), Optional.of(new BlockPos(131, 40, -5)), QuarryData.STRIP_MINE, Optional.of(-53))));
		out.add(new Sample<>(ModItems.FIELD_MARKER, 1, ModComponents.FIELD, new FieldData(Optional.of(Level.NETHER.location()),
			Optional.of(new BlockPos(-40, 63, 17)), Optional.of(new BlockPos(-25, 63, 30)))));
		out.add(new Sample<>(ModItems.SCAN_TOOL, 1, ModComponents.SCAN, new FieldData(Optional.of(Level.END.location()),
			Optional.of(new BlockPos(2001, 80, -999)), Optional.of(new BlockPos(2012, 95, -985)))));
		out.add(new Sample<>(ModItems.PATROL_MAP, 1, ModComponents.PATROL, new PatrolMapItem.Route(Optional.of(Level.NETHER.location()),
			List.of(new BlockPos(10, 70, 10), new BlockPos(40, 71, 12), new BlockPos(44, 69, -30), new BlockPos(5, 64, -33), new BlockPos(-20, 65, 0)))));
		out.add(new Sample<>(ModItems.SHAPE_PLANNER, 1, ModComponents.SHAPE,
			new Shapes.Settings(Shapes.Kind.DOME.id(), 15, 9, 13, false, Optional.of(mc("stone_bricks")))));
		out.add(new Sample<>(ModItems.VILLAGE_LEDGER, 1, ModComponents.LEDGER, new VillageLedgerItem.Ledger(HALL, Component.literal("Oakhollow"))));
		out.add(new Sample<>(ModItems.CITY_PLAN, 1, ModComponents.CITY_PLAN_HALL,
			new VillageLedgerItem.Ledger(GlobalPos.of(Level.NETHER, new BlockPos(-77, 33, 120)), Component.literal("Ember Hollow"))));
		out.add(new Sample<>(ModItems.COLONY_CHARTER, 1, ModComponents.COLONY_CHARTER,
			new ColonyCharterItem.Charter(HALL, Component.literal("Millbrook"), Optional.of(new BlockPos(1100, 70, -3050)))));
		out.add(new Sample<>(ModItems.WONDER_PLAN, 1, ModComponents.WONDER_PLAN,
			new WonderPlanItem.Plan(AliveWorkplace.id("great_library"), HALL, Component.literal("Riverside"))));
		out.add(new Sample<>(ModBlocks.VILLAGE_HALL.asItem(), 1, ModComponents.CITY_PLAN, cityPlan()));
		out.add(new Sample<>(ModItems.RALLY_BANNER, 1, ModComponents.RALLY, new RallyBannerItem.Rally(
			List.of(new UUID(0x1234L, 0x5678L), UUID.fromString("0b9a4a3c-9f0e-4d4b-8c11-3f2a7e6d5c4b"), new UUID(-1L, 42L)), true)));
		out.add(new Sample<>(ModItems.TRAVEL_TICKET, 7, ModComponents.TICKET, new TicketData(UUID.fromString("5d0c7a5e-2b1f-4f39-a7c1-9e2d3b4c5a6f"),
			GlobalPos.of(Level.OVERWORLD, new BlockPos(-5000, 66, 7200)), "Harbor Post")));
		out.add(new Sample<>(ModItems.DELIVERY_NOTE, 1, ModComponents.ROUTE, new RouteData(Optional.of(new BlockPos(15, 64, -8)),
			Optional.of(new BlockPos(88, 70, 41)), List.of(Items.WHEAT, Items.IRON_INGOT, Items.OAK_LOG))));
		out.add(new Sample<>(ModItems.BLUEPRINT, 1, ModComponents.SUPPLY_REPORT, new SupplyReport(Optional.of(new BlockPos(301, 64, -77)), 3,
			List.of(new SupplyReport.Missing(Items.OAK_PLANKS, 64), new SupplyReport.Missing(Items.GLASS_PANE, 12)))));
		out.add(new Sample<>(ModItems.BERRY_WINE, 3, ModComponents.MADE_DAY, new LuxuryRecipes.MadeDay(412L, 3)));
		ItemStack wrapped = new ItemStack(Items.DIAMOND_SWORD);
		wrapped.set(DataComponents.CUSTOM_NAME, Component.literal("Heirloom"));
		wrapped.setDamageValue(17);
		out.add(new Sample<>(ModItems.GIFT, 1, ModComponents.GIFT, new Gifts.Wrapped(wrapped, "Alex")));
		return out;
	}

	private static CityPlan cityPlan() {
		BitSet cells = new BitSet();
		cells.set(3);
		cells.set(4);
		cells.set(35);
		cells.set(1023);
		CityPlan.Zone homes = new CityPlan.Zone("homes", "Homes", "oak", true, cells);
		BitSet farmCells = new BitSet();
		farmCells.set(100, 110);
		CityPlan.Zone farms = new CityPlan.Zone("farms", "North Farms", "", false, farmCells);
		CityPlan.Road road = new CityPlan.Road(List.of(new BlockPos(0, 0, 0), new BlockPos(32, 0, 0), new BlockPos(32, 0, 64)), CityPlan.Road.AVENUE,
			"cobble", true, List.of(new BlockPos(1, 64, 0), new BlockPos(2, 64, 0), new BlockPos(3, 65, 0)), true, List.of(0, 2), false, 4, 6,
			Optional.of(new BlockPos(400, 0, -96)));
		CityPlan.Road lane = new CityPlan.Road(List.of(new BlockPos(-5, 0, 3), new BlockPos(-5, 0, 9)), CityPlan.Road.LANE, "", false,
			List.of(), false, List.of(), true);
		CityPlan.Wall wall = new CityPlan.Wall(List.of(new BlockPos(-40, 0, -40), new BlockPos(40, 0, -40), new BlockPos(40, 0, 40)), false, "stone", true, true);
		return new CityPlan(List.of(homes, farms), List.of(road, lane), Optional.of(wall), CityPlan.Mode.RUN);
	}

	private static RegistryAccess access(GameTestHelper helper) {
		return helper.getLevel().registryAccess();
	}

	/** A chest's save: the stack to NBT, the NBT to bytes as on disk, back from the bytes, then parsed. */
	private static ItemStack throughDisk(GameTestHelper helper, ItemStack stack, String what) {
		Tag tag = stack.save(access(helper));
		if (!(tag instanceof CompoundTag compound)) {
			throw new AssertionError(what + ": a saved stack isn't a compound: " + tag);
		}
		CompoundTag reread;
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			NbtIo.write(compound, new DataOutputStream(bytes));
			reread = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
		} catch (IOException e) {
			throw new AssertionError(what + ": the saved stack's bytes couldn't be written or read: " + e, e);
		}
		helper.assertTrue(reread.equals(compound), what + ": the NBT read back from bytes differs: " + compound + " -> " + reread);
		return ItemStack.parse(access(helper), reread).orElseThrow(() -> new AssertionError(what + ": the saved stack didn't load: " + reread));
	}

	/** The trip to a player: the stack through the item stream codec with registries. */
	private static ItemStack overTheNetwork(GameTestHelper helper, ItemStack stack) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access(helper));
		try {
			ItemStack.STREAM_CODEC.encode(buf, stack);
			ItemStack back = ItemStack.STREAM_CODEC.decode(buf);
			if (buf.readableBytes() != 0) {
				throw new AssertionError(stack + ": " + buf.readableBytes() + " bytes left unread after the network trip");
			}
			return back;
		} finally {
			buf.release();
		}
	}

	private static <T> void checkSame(GameTestHelper helper, Sample<T> s, ItemStack back, String over) {
		String what = s.type() + " on " + BuiltInRegistries.ITEM.getKey(s.item());
		helper.assertTrue(back.is(s.item()), what + ": the item changed over " + over + ": " + back.getItem());
		helper.assertValueEqual(back.getCount(), s.count(), what + ": the count over " + over);
		T got = back.get(s.type());
		helper.assertTrue(s.value().equals(got), what + " changed over " + over + ": expected " + s.value() + ", got " + got);
	}

	private static <T> ItemStack stackOf(Sample<T> s) {
		ItemStack stack = new ItemStack(s.item(), s.count());
		stack.set(s.type(), s.value());
		return stack;
	}

	/** Every saved component comes back equal from a chest's save (also as bytes) and from the network; the item and count too. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyComponentComesBackFromAChestAndTheNetwork(GameTestHelper helper) {
		int saved = 0;
		for (Sample<?> s : samples()) {
			ItemStack stack = stackOf(s);
			ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(s.type());
			if (s.type().codec() != null) {
				CompoundTag tag = (CompoundTag) stack.save(access(helper));
				helper.assertTrue(tag.getCompound("components").contains(id.toString()),
					id + " isn't written into the saved stack at all: " + tag);
				checkSame(helper, s, throughDisk(helper, stack, id.toString()), "a chest's save");
				saved++;
			}
			checkSame(helper, s, overTheNetwork(helper, stack), "the network");
		}
		helper.assertTrue(saved >= 17, "only " + saved + " saved components were round-tripped");
		helper.succeed();
	}

	/** The supply report is sent to the player but never saved: a chest's save drops it and leaves the rest. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theSupplyReportIsSentButNotSaved(GameTestHelper helper) {
		helper.assertTrue(ModComponents.SUPPLY_REPORT.codec() == null, "supply_report became persistent; give it a save sample");
		SupplyReport report = new SupplyReport(Optional.of(new BlockPos(301, 64, -77)), 3, List.of(new SupplyReport.Missing(Items.OAK_PLANKS, 64)));
		BlueprintData blueprint = new BlueprintData(AliveWorkplace.id("cottage"), Optional.of(new Vec3i(9, 7, 11)), Optional.empty());
		ItemStack stack = new ItemStack(ModItems.BLUEPRINT);
		stack.set(ModComponents.BLUEPRINT, blueprint);
		stack.set(ModComponents.SUPPLY_REPORT, report);
		ItemStack loaded = throughDisk(helper, stack, "a blueprint with a supply report");
		helper.assertTrue(!loaded.has(ModComponents.SUPPLY_REPORT), "the supply report was saved: " + loaded.get(ModComponents.SUPPLY_REPORT));
		helper.assertTrue(blueprint.equals(loaded.get(ModComponents.BLUEPRINT)), "the blueprint beside the report was lost: " + loaded.get(ModComponents.BLUEPRINT));
		helper.assertTrue(report.equals(overTheNetwork(helper, stack).get(ModComponents.SUPPLY_REPORT)), "the supply report didn't reach the player");
		helper.succeed();
	}

	/** Guard: every component under our namespace has a sample above, and that sample isn't the item's default. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyOneOfOurComponentsHasARealisticSample(GameTestHelper helper) {
		Map<DataComponentType<?>, Sample<?>> byType = new LinkedHashMap<>();
		for (Sample<?> s : samples()) {
			byType.put(s.type(), s);
		}
		List<String> missing = new ArrayList<>();
		int ours = 0;
		for (var entry : BuiltInRegistries.DATA_COMPONENT_TYPE.entrySet()) {
			ResourceLocation id = entry.getKey().location();
			if (!id.getNamespace().equals(AliveWorkplace.MOD_ID)) {
				continue;
			}
			ours++;
			Sample<?> s = byType.get(entry.getValue());
			if (s == null) {
				missing.add(id.toString());
				continue;
			}
			Object fallback = new ItemStack(s.item()).get(s.type());
			helper.assertTrue(!s.value().equals(fallback), id + "'s sample is the item's default value: " + fallback);
		}
		helper.assertTrue(missing.isEmpty(), "components with no save round trip in QaItemComponentsSaveGameTests: " + missing);
		helper.assertValueEqual(byType.size(), ours, "samples for components not registered under " + AliveWorkplace.MOD_ID);
		helper.succeed();
	}

	// --- The five untested components, read back by the item's own behaviour -------------------------------------

	private static List<Component> tooltip(GameTestHelper helper, ItemStack stack) {
		List<Component> lines = new ArrayList<>();
		stack.getItem().appendHoverText(stack, Item.TooltipContext.of(helper.getLevel()), lines, TooltipFlag.NORMAL);
		return lines;
	}

	private static TranslatableContents translatable(GameTestHelper helper, Component line, String key) {
		helper.assertTrue(line.getContents() instanceof TranslatableContents, "not a translated line: " + line);
		TranslatableContents contents = (TranslatableContents) line.getContents();
		helper.assertValueEqual(contents.getKey(), key, "the tooltip line's key");
		return contents;
	}

	/** A reloaded Village Banner keeps its base color (tooltip and the vanilla banner it hangs as) and its patterns. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aReloadedVillageBannerKeepsItsDesign(GameTestHelper helper) {
		BannerPatternLayers patterns = new BannerPatternLayers.Builder()
			.add(access(helper).registryOrThrow(Registries.BANNER_PATTERN).getHolderOrThrow(BannerPatterns.STRIPE_TOP), DyeColor.YELLOW)
			.add(access(helper).registryOrThrow(Registries.BANNER_PATTERN).getHolderOrThrow(BannerPatterns.CROSS), DyeColor.BLACK)
			.build();
		ItemStack banner = VillageBannerItem.of(DyeColor.CYAN, patterns);
		for (ItemStack back : List.of(throughDisk(helper, banner, "a village banner"), overTheNetwork(helper, banner))) {
			helper.assertValueEqual(VillageBannerItem.base(back), DyeColor.CYAN, "the reloaded banner's base color");
			helper.assertTrue(patterns.equals(VillageBannerItem.patterns(back)), "the reloaded banner's patterns: " + VillageBannerItem.patterns(back));
			ItemStack vanilla = VillageBannerItem.asVanilla(VillageBannerItem.base(back), VillageBannerItem.patterns(back));
			helper.assertTrue(vanilla.is(Items.CYAN_BANNER), "the reloaded banner hangs as " + vanilla.getItem() + ", not a cyan banner");
			TranslatableContents base = translatable(helper, tooltip(helper, back).get(0), "tooltip.aliveworkplace.village_banner.base");
			Object color = base.getArgs()[0];
			helper.assertTrue(color instanceof Component c && c.getContents() instanceof TranslatableContents t && t.getKey().equals("color.minecraft.cyan"),
				"the reloaded banner's tooltip names " + color + ", not cyan");
		}
		helper.succeed();
	}

	/** A reloaded Patrol Map still has its route (dimension and every point, in order) and its tooltip counts them. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aReloadedPatrolMapKeepsItsRoute(GameTestHelper helper) {
		List<BlockPos> points = List.of(new BlockPos(10, 70, 10), new BlockPos(40, 71, 12), new BlockPos(44, 69, -30), new BlockPos(5, 64, -33));
		PatrolMapItem.Route route = new PatrolMapItem.Route(Optional.of(Level.OVERWORLD.location()), points);
		ItemStack map = new ItemStack(ModItems.PATROL_MAP);
		map.set(ModComponents.PATROL, route);
		for (ItemStack back : List.of(throughDisk(helper, map, "a patrol map"), overTheNetwork(helper, map))) {
			PatrolMapItem.Route got = PatrolMapItem.route(back);
			helper.assertTrue(got.points().equals(points), "the reloaded map's points: " + got.points());
			helper.assertTrue(got.dimension().equals(route.dimension()), "the reloaded map's dimension: " + got.dimension());
			TranslatableContents count = translatable(helper, tooltip(helper, back).get(0), "tooltip.aliveworkplace.patrol.points");
			helper.assertValueEqual(count.getArgs()[0], points.size(), "the reloaded map's tooltip point count");
		}
		helper.succeed();
	}

	/** A reloaded City Plan still names the village it's bound to. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aReloadedCityPlanStillNamesItsVillage(GameTestHelper helper) {
		Component name = Component.literal("Ember Hollow").withStyle(ChatFormatting.GOLD);
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		plan.set(ModComponents.CITY_PLAN_HALL, new VillageLedgerItem.Ledger(GlobalPos.of(Level.NETHER, new BlockPos(-77, 33, 120)), name));
		for (ItemStack back : List.of(throughDisk(helper, plan, "a city plan"), overTheNetwork(helper, plan))) {
			VillageLedgerItem.Ledger bound = back.get(ModComponents.CITY_PLAN_HALL);
			helper.assertTrue(bound != null && bound.hall().equals(GlobalPos.of(Level.NETHER, new BlockPos(-77, 33, 120))), "the reloaded plan's hall: " + bound);
			TranslatableContents line = translatable(helper, tooltip(helper, back).get(0), "tooltip.aliveworkplace.city_plan.bound");
			helper.assertTrue(name.equals(line.getArgs()[0]), "the reloaded plan's tooltip names " + line.getArgs()[0] + ", not " + name);
		}
		helper.succeed();
	}

	/** A reloaded Colony Charter still names its mother village, keeps its chosen spot and the colony name from the anvil. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aReloadedColonyCharterStillNamesItsMotherVillage(GameTestHelper helper) {
		BlockPos spot = new BlockPos(1100, 70, -3050);
		ItemStack charter = new ItemStack(ModItems.COLONY_CHARTER);
		charter.set(ModComponents.COLONY_CHARTER, new ColonyCharterItem.Charter(HALL, Component.literal("Millbrook"), Optional.empty()).withSpot(spot));
		charter.set(DataComponents.CUSTOM_NAME, Component.literal("New Millbrook"));
		for (ItemStack back : List.of(throughDisk(helper, charter, "a colony charter"), overTheNetwork(helper, charter))) {
			ColonyCharterItem.Charter got = back.get(ModComponents.COLONY_CHARTER);
			helper.assertTrue(got != null && got.hall().equals(HALL) && got.spot().equals(Optional.of(spot)), "the reloaded charter: " + got);
			List<Component> lines = ColonyCharterItem.lines(back);
			TranslatableContents bound = translatable(helper, lines.get(0), "tooltip.aliveworkplace.colony_charter.bound");
			helper.assertTrue(Component.literal("Millbrook").equals(bound.getArgs()[0]), "the reloaded charter's mother village: " + bound.getArgs()[0]);
			translatable(helper, lines.get(1), "tooltip.aliveworkplace.colony_charter.spot");
			Component named = ColonyCharterItem.colonyName(back);
			helper.assertTrue(named != null && named.getString().equals("New Millbrook"), "the reloaded charter's colony name: " + named);
		}
		helper.succeed();
	}

	/** A reloaded made day decides "vintage" exactly as before, keeps the stack a plain making, and isn't re-stamped. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aReloadedMadeDayAgesTheSame(GameTestHelper helper) {
		for (LuxuryRecipes.MadeDay made : List.of(new LuxuryRecipes.MadeDay(412L, 3), new LuxuryRecipes.MadeDay(5_000_000_123L, 7))) {
			ItemStack wine = new ItemStack(ModItems.BERRY_WINE, 2);
			wine.set(ModComponents.MADE_DAY, made);
			for (ItemStack back : List.of(throughDisk(helper, wine, "berry wine"), overTheNetwork(helper, wine))) {
				helper.assertTrue(made.equals(back.get(ModComponents.MADE_DAY)), "the reloaded made day: " + back.get(ModComponents.MADE_DAY) + ", expected " + made);
				helper.assertTrue(LuxuryRecipes.plainButMadeDay(back), "the reloaded wine isn't plain apart from its made day: " + back.getComponentsPatch());
				for (long today : new long[] {made.day(), made.day() + made.vintageDays() - 1, made.day() + made.vintageDays(), made.day() + 100}) {
					helper.assertValueEqual(LuxuryRecipes.agedFor(back, made.vintageDays(), today), LuxuryRecipes.agedFor(wine, made.vintageDays(), today),
						"aged on day " + today + " after the reload");
					helper.assertTrue(LuxuryRecipes.tooltip(back.get(ModComponents.MADE_DAY), today).equals(LuxuryRecipes.tooltip(made, today)),
						"the reloaded wine's tooltip on day " + today);
				}
				helper.assertTrue(!LuxuryRecipes.agedFor(back, made.vintageDays(), made.day() + made.vintageDays() - 1), "vintage a day early after the reload");
				helper.assertTrue(LuxuryRecipes.agedFor(back, made.vintageDays(), made.day() + made.vintageDays()), "not vintage on its day after the reload");
				LuxuryRecipes.stamp(back, made.day() + 50);
				helper.assertTrue(made.equals(back.get(ModComponents.MADE_DAY)), "a reloaded wine was re-stamped: " + back.get(ModComponents.MADE_DAY));
			}
		}
		helper.succeed();
	}
}
