package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.guard.GuardPatrol;
import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageBannerItem;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Village Banner (ROADMAP 30.13): crafting keeps the design, the hall takes it as its colours (they survive a
 * reload), and the colours show over finished buildings' doors, on guards' and mercenaries' plain shields, on the
 * routes page and the Book of Edicts, and at festivals.
 */
public class VillageBannerGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 2);
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	private static BannerPatternLayers design(GameTestHelper helper) {
		var patterns = helper.getLevel().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		return new BannerPatternLayers.Builder().add(patterns.getOrThrow(BannerPatterns.STRIPE_TOP), DyeColor.RED).build();
	}

	private static VillageBanners.Colours colours(GameTestHelper helper) {
		return new VillageBanners.Colours(DyeColor.BLUE, design(helper));
	}

	/** A Village Hall at {@code rel} in the test's colours, cleared again after the test (other tests may look for a hall near them). */
	private static VillageHallBlockEntity hallWithColours(GameTestHelper helper, BlockPos rel) {
		helper.setBlock(rel, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
		entity.setColours(colours(helper));
		Leftovers.after(helper, () -> entity.setColours(null));
		return entity;
	}

	/** Turns Village Banners off until the test ends. */
	private static void switchOff(GameTestHelper helper) {
		boolean was = VillageBanners.ENABLED;
		VillageBanners.ENABLED = false;
		Leftovers.after(helper, () -> VillageBanners.ENABLED = was);
	}

	private static boolean shows(ItemStack stack, VillageBanners.Colours colours) {
		return stack.is(BannerBlock.byColor(colours.base()).asItem()) && colours.patterns().equals(stack.get(DataComponents.BANNER_PATTERNS));
	}

	/** A red-striped blue banner and a gold ingot make a Village Banner of that design, which sets the hall's colours and survives a reload. */
	//$ gametest_ticks_batch AREA '100' '"villageBanner"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "villageBanner")
	public void villageBannerSetsTheHallsColoursAndSurvivesAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ItemStack banner = new ItemStack(Items.BLUE_BANNER);
			banner.set(DataComponents.BANNER_PATTERNS, design(helper));
			VillageBannerItem.Recipe recipe = new VillageBannerItem.Recipe(CraftingBookCategory.MISC);
			CraftingInput input = CraftingInput.of(2, 1, List.of(banner, new ItemStack(Items.GOLD_INGOT)));
			helper.assertTrue(recipe.matches(input, level), "a banner and a gold ingot match");
			helper.assertFalse(recipe.matches(CraftingInput.of(2, 1, List.of(banner, new ItemStack(Items.IRON_INGOT))), level), "iron doesn't");
			ItemStack village = recipe.assemble(input, level.registryAccess());
			helper.assertTrue(VillageBannerItem.base(village) == DyeColor.BLUE && VillageBannerItem.patterns(village).equals(design(helper)), "keeps the design");

			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			Leftovers.after(helper, () -> entity.setColours(null));
			helper.assertTrue(entity.colours() == null, "a new hall has no colours");
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			VillageBannerItem.setColours(level, player, village, hall);
			helper.assertTrue(village.getCount() == 1, "the banner isn't used up");
			VillageBanners.Colours colours = entity.colours();
			helper.assertTrue(colours != null && colours.base() == DyeColor.BLUE && colours.patterns().equals(design(helper)), "set: " + colours);

			CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
			VillageHallBlockEntity copy = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(copy != null && colours.equals(copy.colours()), "reloaded: " + (copy == null ? null : copy.colours()));
			tag.remove("bannerBase");
			tag.remove("bannerPatterns");
			VillageHallBlockEntity old = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(old != null && old.colours() == null, "a hall saved before banners has none");
			helper.assertTrue(WorkplaceConfig.parse("{}").villageBanners, "villageBanners defaults on");
			level.getServer().getPlayerList().remove(player);
			helper.succeed();
		});
	}

	/**
	 * A player right-clicks the hall with a Village Banner: its design becomes the colours, the banner is kept, the
	 * chronicle notes it. With the switch off the hall keeps its colours, none show and none can be crafted. Used on the
	 * ground, it hangs the design like a banner.
	 */
	//$ gametest_ticks_batch AREA '100' '"villageBannerHall"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "villageBannerHall")
	public void aPlayerSetsTheColoursAtTheHallAndPlacesTheBanner(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			Leftovers.after(helper, () -> entity.setColours(null));
			try {
				ItemStack held = VillageBannerItem.of(DyeColor.BLUE, design(helper));
				use(player, level, hall, Direction.UP, held);
				helper.assertTrue(colours(helper).equals(entity.colours()), "the hall's colours: " + entity.colours());
				helper.assertTrue(player.getMainHandItem().is(ModItems.VILLAGE_BANNER) && player.getMainHandItem().getCount() == 1, "the banner was used up");
				helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.BANNER
					&& e.text().getString().contains("raised new colours")), "not in the chronicle: " + entity.chronicle());

				switchOff(helper);
				use(player, level, hall, Direction.UP, VillageBannerItem.of(DyeColor.RED, BannerPatternLayers.EMPTY));
				helper.assertTrue(colours(helper).equals(entity.colours()), "changed while off: " + entity.colours());
				helper.assertTrue(VillageBanners.of(entity) == null, "the colours show while off");
				CraftingInput input = CraftingInput.of(2, 1, List.of(new ItemStack(Items.RED_BANNER), new ItemStack(Items.GOLD_INGOT)));
				helper.assertFalse(new VillageBannerItem.Recipe(CraftingBookCategory.MISC).matches(input, level), "crafted while off");
				VillageBanners.ENABLED = true;
				helper.assertTrue(colours(helper).equals(VillageBanners.of(entity)), "the colours didn't come back");

				// On the ground it hangs the design, as a banner does.
				BlockPos floor = helper.absolutePos(new BlockPos(6, 1, 6));
				use(player, level, floor, Direction.UP, VillageBannerItem.of(DyeColor.BLUE, design(helper)));
				BlockState placed = level.getBlockState(floor.above());
				helper.assertTrue(placed.is(Blocks.BLUE_BANNER), "placed: " + placed);
				helper.assertTrue(level.getBlockEntity(floor.above()) instanceof BannerBlockEntity b && colours(helper).on(b), "the placed banner lost the design");
				helper.assertTrue(player.getMainHandItem().isEmpty(), "placing didn't use it up");
				helper.assertTrue(Component.translatable("screen.aliveworkplace.edicts.banner_how").getString().contains("Village Banner"), "untranslated hint");
			} finally {
				level.getServer().getPlayerList().remove(player);
			}
			helper.succeed();
		});
	}

	private static void use(ServerPlayer player, ServerLevel level, BlockPos at, Direction face, ItemStack held) {
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at).relative(face, 0.5), face, at, false));
	}

	/**
	 * A builder finishes the test hut in a village with colours and a blue banner in the chest: it hangs over the hut's
	 * front door, in the colours, facing out.
	 */
	//$ gametest_ticks_batch AREA '2400' '"villageBannerDoor"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "villageBannerDoor")
	public void aBuilderHangsTheColoursOverTheFrontDoor(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		hallWithColours(helper, new BlockPos(14, 2, 14));
		BlockPos bench = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		ItemStack[] materials = {new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH), new ItemStack(Items.BLUE_BANNER)};
		for (int i = 0; i < materials.length; i++) {
			chest.setItem(i, materials[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(bench));
		BuildSite site = Builders.start(level, villager, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "no site");
		// test_hut's door is at (2, 1, 0) of the blueprint, on its front (north) side; the wall over the door's top
		// is the roof's edge, so the banner hangs right over the door, outside.
		BlockPos expected = new BlockPos(8, 5, 5);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage());
			BlockState state = helper.getBlockState(expected);
			helper.assertTrue(state.getBlock() instanceof WallBannerBlock && state.getValue(WallBannerBlock.FACING) == Direction.NORTH,
				"no wall banner over the door: " + state);
			helper.assertTrue(helper.getBlockState(new BlockPos(8, 4, 6)).getBlock() instanceof DoorBlock, "not over the door");
			helper.assertTrue(level.getBlockEntity(helper.absolutePos(expected)) instanceof BannerBlockEntity b && colours(helper).on(b), "not in the colours");
			helper.assertTrue(chest.countItem(Items.BLUE_BANNER) == 0, "the banner wasn't taken from the chest");
		});
	}

	/**
	 * Hanging the colours over a door: only with a banner of the base colour in the chests (a red one won't do, and none
	 * is asked for), only with the switch on, only in a village with colours; then over the door, facing out.
	 */
	//$ gametest_ticks_batch AREA '100' '"villageBannerDoorRules"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "villageBannerDoorRules")
	public void theColoursGoOverTheDoorOnlyWithABannerOfTheirBaseColour(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		// A plank box 5 wide, 4 tall, with a door in its north wall.
		for (BlockPos p : BlockPos.betweenClosed(4, 2, 4, 8, 5, 8)) {
			boolean shell = p.getX() == 4 || p.getX() == 8 || p.getZ() == 4 || p.getZ() == 8 || p.getY() == 5;
			helper.setBlock(p, shell ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
		}
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
		helper.setBlock(new BlockPos(6, 2, 4), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(new BlockPos(6, 3, 4), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		BlockPos chestPos = new BlockPos(1, 2, 1);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		List<BlockPos> supplies = List.of(helper.absolutePos(chestPos));
		BlockPos lo = helper.absolutePos(new BlockPos(4, 2, 4));
		BlockPos hi = helper.absolutePos(new BlockPos(8, 5, 8));
		BoundingBox box = BoundingBox.fromCorners(lo, hi);
		// The banner goes a block over the door's top, on the wall's top course; its cloth falls over the door.
		BlockPos expected = helper.absolutePos(new BlockPos(6, 5, 3));

		helper.assertTrue(VillageBanners.hangOverDoor(level, box, supplies) == null, "hung without colours");
		hallWithColours(helper, new BlockPos(14, 2, 14));
		helper.assertTrue(VillageBanners.hangOverDoor(level, box, supplies) == null, "hung without a banner");
		chest.setItem(0, new ItemStack(Items.RED_BANNER));
		helper.assertTrue(VillageBanners.hangOverDoor(level, box, supplies) == null, "hung a red banner for blue colours");
		helper.assertTrue(chest.countItem(Items.RED_BANNER) == 1, "took the red banner");
		chest.setItem(1, new ItemStack(Items.BLUE_BANNER));
		switchOff(helper);
		helper.assertTrue(VillageBanners.hangOverDoor(level, box, supplies) == null, "hung with the switch off");
		VillageBanners.ENABLED = true;
		BlockPos hung = VillageBanners.hangOverDoor(level, box, supplies);
		helper.assertTrue(expected.equals(hung), "hung at " + hung + ", not " + expected);
		BlockState state = level.getBlockState(expected);
		helper.assertTrue(state.getBlock() instanceof WallBannerBlock && state.getValue(WallBannerBlock.FACING) == Direction.NORTH, "hangs: " + state);
		helper.assertTrue(level.getBlockEntity(expected) instanceof BannerBlockEntity b && colours(helper).on(b), "not in the colours");
		helper.assertTrue(chest.countItem(Items.BLUE_BANNER) == 0 && chest.countItem(Items.RED_BANNER) == 1, "took the wrong banner");
		// A second time: the spot is taken, nothing more is hung.
		chest.setItem(1, new ItemStack(Items.BLUE_BANNER));
		BlockPos again = VillageBanners.hangOverDoor(level, box, supplies);
		helper.assertTrue(again == null || !again.equals(expected), "hung twice in one spot");
		helper.succeed();
	}

	/**
	 * A guard gearing up in a village with colours paints a plain shield in them; a shield a player painted is kept as it
	 * is; with the switch off nothing is painted. A mercenary band hired at the hall brings a shield in its colours.
	 */
	//$ gametest_ticks_batch AREA '100' '"villageBannerShields"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "villageBannerShields")
	public void guardsAndMercenariesCarryTheColoursOnPlainShields(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		VillageBanners.Colours colours = colours(helper);
		hallWithColours(helper, HALL);
		BlockPos post = new BlockPos(6, 2, 2);
		BlockPos chestPos = new BlockPos(6, 2, 4);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		List<BlockPos> chests = List.of(helper.absolutePos(chestPos));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		player.getInventory().add(new ItemStack(Items.EMERALD, Mercenaries.PRICE_EMERALDS));
		helper.runAfterDelay(2, () -> {
			List<Villager> spawned = new ArrayList<>();
			try {
				Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 3));
				spawned.add(guard);
				Jobs.employ(level, guard, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
				chest.setItem(0, new ItemStack(Items.SHIELD));
				GuardPatrol.equipBestForTest(level, guard, chests);
				ItemStack shield = guard.getItemBySlot(EquipmentSlot.OFFHAND);
				helper.assertTrue(shield.is(Items.SHIELD) && shield.get(DataComponents.BASE_COLOR) == DyeColor.BLUE
					&& colours.patterns().equals(shield.get(DataComponents.BANNER_PATTERNS)), "the plain shield wasn't painted: " + shield.getComponents());

				// A shield a player painted (red, plain) stays red.
				Villager knight = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 3));
				spawned.add(knight);
				Jobs.employ(level, knight, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
				ItemStack red = new ItemStack(Items.SHIELD);
				red.set(DataComponents.BASE_COLOR, DyeColor.RED);
				knight.setItemSlot(EquipmentSlot.OFFHAND, red);
				GuardPatrol.equipBestForTest(level, knight, chests);
				ItemStack kept = knight.getItemBySlot(EquipmentSlot.OFFHAND);
				helper.assertTrue(kept.get(DataComponents.BASE_COLOR) == DyeColor.RED
					&& kept.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().isEmpty(), "a player's shield was repainted");

				// Switched off: a plain shield stays plain.
				switchOff(helper);
				Villager plain = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 3));
				spawned.add(plain);
				Jobs.employ(level, plain, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
				chest.setItem(0, new ItemStack(Items.SHIELD));
				GuardPatrol.equipBestForTest(level, plain, chests);
				helper.assertTrue(plain.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD) && !plain.getItemBySlot(EquipmentSlot.OFFHAND).has(DataComponents.BASE_COLOR),
					"painted with the switch off");
				VillageBanners.ENABLED = true;

				BlockPos hall = helper.absolutePos(HALL);
				Mercenaries.hire(level, hall, player);
				List<Villager> band = Mercenaries.near(level, hall);
				spawned.addAll(band);
				helper.assertTrue(band.size() == Mercenaries.BAND, "band of " + band.size());
				List<ItemStack> shields = band.stream().map(v -> v.getItemBySlot(EquipmentSlot.OFFHAND)).filter(s -> s.is(Items.SHIELD)).toList();
				helper.assertTrue(!shields.isEmpty() && shields.stream().allMatch(s -> s.get(DataComponents.BASE_COLOR) == DyeColor.BLUE
					&& colours.patterns().equals(s.get(DataComponents.BANNER_PATTERNS))), "the mercenaries' shield isn't in the colours: " + shields);
			} finally {
				spawned.forEach(net.minecraft.world.entity.Entity::discard);
				level.getServer().getPlayerList().remove(player);
			}
			helper.succeed();
		});
	}

	/**
	 * The hall's trade routes page shows a village with colours by its banner (one without by a cart), and the Book of
	 * Edicts shows the village by its own banner, with its colours on the last row.
	 */
	//$ gametest_ticks_batch AREA '100' '"villageBannerRoutes"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "villageBannerRoutes")
	public void theRoutesPageAndTheBookShowEachVillageByItsBanner(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		VillageBanners.Colours colours = colours(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		hallWithColours(helper, new BlockPos(14, 2, 14));
		helper.setBlock(new BlockPos(14, 2, 2), ModBlocks.VILLAGE_HALL);
		BlockPos a = helper.absolutePos(HALL);
		BlockPos b = helper.absolutePos(new BlockPos(14, 2, 14));
		BlockPos c = helper.absolutePos(new BlockPos(14, 2, 2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			data.remove(a);
			data.remove(b);
			data.remove(c);
		});
		helper.runAfterDelay(2, () -> {
			try {
				data.setWants(a, Component.literal("Ashford"), List.of());
				data.setWants(b, Component.literal("Bluebrook"), List.of());
				data.setWants(c, Component.literal("Cobbleby"), List.of());
				ChoiceMenu menu = VillageHallScreen.forTest(player, a);
				menu.press(VillageHallScreen.ROUTES, player);
				helper.assertTrue(menu.icon(4).is(Items.CHEST_MINECART), "not the routes page: " + menu.icon(4));
				ItemStack bluebrook = named(menu, "Bluebrook");
				ItemStack cobbleby = named(menu, "Cobbleby");
				helper.assertTrue(bluebrook != null && shows(bluebrook, colours), "Bluebrook isn't shown by its banner: " + bluebrook);
				helper.assertTrue(cobbleby != null && cobbleby.is(Items.MINECART), "Cobbleby (no colours) isn't shown by a cart: " + cobbleby);

				ChoiceMenu own = EdictBook.forTest(player, b);
				helper.assertTrue(shows(own.icon(EdictBook.HEADER), colours), "the Book doesn't show the village's banner: " + own.icon(EdictBook.HEADER));
				helper.assertTrue(shows(own.icon(EdictBook.BANNER), colours), "the last row doesn't show the colours: " + own.icon(EdictBook.BANNER));
				ChoiceMenu none = EdictBook.forTest(player, a);
				helper.assertTrue(none.icon(EdictBook.HEADER).is(Items.LECTERN), "a village without colours: " + none.icon(EdictBook.HEADER));
				helper.assertTrue(none.icon(EdictBook.BANNER).is(ModItems.VILLAGE_BANNER), "no hint how to set colours: " + none.icon(EdictBook.BANNER));
				helper.assertTrue(Component.translatable("screen.aliveworkplace.edicts.banner_shown").getString().contains("shields"), "untranslated");
			} finally {
				level.getServer().getPlayerList().remove(player);
			}
			helper.succeed();
		});
	}

	private static ItemStack named(ChoiceMenu menu, String name) {
		for (int i = 0; i < ChoiceMenu.SIZE; i++) {
			ItemStack icon = menu.icon(i);
			Component custom = icon.get(DataComponents.CUSTOM_NAME);
			if (custom != null && custom.getString().equals(name)) {
				return icon;
			}
		}
		return null;
	}

	/**
	 * A festival under a banner in the colours (within 16 blocks of the square) lifts the mood to 20 for 3 days; one
	 * without lifts it by 15 for 2. A banner of another design, or too far, doesn't count.
	 */
	//$ gametest_ticks_batch AREA '200' '"villageBannerFestival"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "villageBannerFestival")
	public void aFestivalUnderTheBannerIsBetterAndLonger(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Festivals.forget();
		});
		ServerLevel level = helper.getLevel();
		// A design of this test's own: banners other tests left in the world (in the shared design) mustn't count.
		var patterns = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		VillageBanners.Colours colours = new VillageBanners.Colours(DyeColor.BLUE, new BannerPatternLayers.Builder()
			.add(patterns.getOrThrow(BannerPatterns.CROSS), DyeColor.YELLOW).add(patterns.getOrThrow(BannerPatterns.BORDER), DyeColor.WHITE).build());
		helper.setDayTime(1000);
		VillageHallBlockEntity entity = hallWithColours(helper, HALL);
		entity.setColours(colours);
		BlockPos bell = new BlockPos(4, 2, 4);
		helper.setBlock(bell, Blocks.BELL);
		BlockPos flag = new BlockPos(10, 2, 4);
		helper.setBlock(flag, Blocks.BLUE_BANNER);
		List<Villager> villagers = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			villagers.add(helper.spawn(EntityType.VILLAGER, new BlockPos(12 + i, 2, 12)));
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos hall = helper.absolutePos(HALL);
		helper.runAfterDelay(5, () -> {
			BannerBlockEntity banner = (BannerBlockEntity) level.getBlockEntity(helper.absolutePos(flag));
			helper.assertFalse(Festivals.underBanner(level, hall), "a plain blue banner counted as the colours");
			banner.fromItem(colours.banner(), colours.base());
			helper.assertTrue(Festivals.underBanner(level, hall), "the banner in the colours by the square doesn't count");
			helper.assertTrue(VillageBanners.fliesNear(level, helper.absolutePos(flag).offset(-16, 0, 0), colours), "16 blocks away is near");
			helper.assertFalse(VillageBanners.fliesNear(level, helper.absolutePos(flag).offset(-17, 0, 0), colours), "17 blocks away isn't");

			Festivals.call(level, hall, player);
			helper.setDayTime(9500);
			Festivals.round(level, hall, entity, villagers.size());
			long today = Chronicle.day(level);
			for (Villager v : villagers) {
				helper.assertTrue(Festivals.mood(level, v) == Festivals.BANNER_MOOD && Festivals.BANNER_MOOD == 20, "mood under the banner: " + Festivals.mood(level, v));
				helper.assertTrue(Moods.work(level, v).good().stream().anyMatch(r -> r.getString().contains("village's colours")), "reason: " + Moods.work(level, v).good());
			}
			Villager v = villagers.get(0);
			// Three days on it still counts, four it doesn't.
			ModAttachments.FESTIVAL_DAY.set(v, today - 3);
			ModAttachments.FESTIVAL_BANNER_DAY.set(v, today - 3);
			helper.assertTrue(Festivals.enjoyedLately(level, v) && Festivals.mood(level, v) == 20, "3 days after a bannered festival");
			ModAttachments.FESTIVAL_DAY.set(v, today - 4);
			ModAttachments.FESTIVAL_BANNER_DAY.set(v, today - 4);
			helper.assertFalse(Festivals.enjoyedLately(level, v), "4 days after a bannered festival");
			// Without the banner: 15, for 2 days.
			ModAttachments.FESTIVAL_DAY.set(v, today - 2);
			ModAttachments.FESTIVAL_BANNER_DAY.remove(v);
			helper.assertTrue(Festivals.mood(level, v) == Festivals.MOOD && Festivals.MOOD == 15, "2 days after a plain festival: " + Festivals.mood(level, v));
			ModAttachments.FESTIVAL_DAY.set(v, today - 3);
			helper.assertFalse(Festivals.enjoyedLately(level, v), "3 days after a plain festival");
			// With the switch off the banner doesn't count.
			switchOff(helper);
			helper.assertFalse(Festivals.underBanner(level, hall), "the banner counted with the switch off");
			VillageBanners.ENABLED = true;
			helper.succeed();
		});
	}
}
