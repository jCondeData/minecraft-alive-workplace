package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.platform.ItemStores;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.Porters;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 37.8: Sophisticated Storage's chests, barrels and limited barrels of every tier as village storage, read
 * only through Fabric's transfer API and block ids (never the mod's classes): supply chests, porters' chests and
 * Storehouse chests; builders leave them standing and never copy what's in them; a stack upgrade's room is shown on
 * the Storehouse board, counted in items.
 */
public class SophisticatedStorageCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String MOD = "sophisticatedstorage";
	private static final List<String> TIERS = List.of("", "copper_", "iron_", "gold_", "diamond_", "netherite_");

	/** Every chest, barrel and limited barrel of the mod, six tiers of each: 36 blocks. */
	static List<String> storageIds() {
		List<String> ids = new ArrayList<>();
		for (String tier : TIERS) {
			ids.add(tier + "chest");
			ids.add(tier + "barrel");
			for (int slots = 1; slots <= 4; slots++) {
				ids.add("limited_" + tier + "barrel_" + slots);
			}
		}
		return ids;
	}

	/**
	 * Each tier, side by side: listed as storage, filled the way a porter fills a store, emptied the way a builder takes
	 * materials and a porter takes goods, protected from a builder clearing the ground, and (chests and barrels) in the
	 * common {@code c:chests} / {@code c:barrels} tags. Limited barrels hold more than a stack a slot.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void everyTierStoresAndGivesItems(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TagKey<Block> chests = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "chests"));
		TagKey<Block> barrels = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "barrels"));
		List<String> ids = storageIds();
		helper.assertTrue(ids.size() == 36, "tiers: " + ids);
		List<String> problems = new ArrayList<>();
		for (int i = 0; i < ids.size(); i++) {
			String id = ids.get(i);
			Block block = block(id);
			BlockPos rel = new BlockPos(1 + (i % 8) * 2, 2, 1 + (i / 8) * 2); // 8 a row, 5 rows
			helper.setBlock(rel, block);
			BlockPos pos = helper.absolutePos(rel);
			List<BlockPos> one = List.of(pos);
			BlockState state = level.getBlockState(pos);
			if (!SupplyContainers.find(level, pos, null).contains(pos)) {
				problems.add(id + ": not listed as storage");
				continue;
			}
			ItemStack rest = SupplyContainers.insert(level, one, new ItemStack(Items.COBBLESTONE, 100));
			long held = SupplyContainers.count(level, one, Items.COBBLESTONE);
			if (!rest.isEmpty() || held != 100) {
				problems.add(id + ": stored " + held + " of 100 (left " + rest.getCount() + ")");
				continue;
			}
			if (SupplyContainers.freeStacks(level, one) <= 0) {
				problems.add(id + ": no room for a porter after 100 cobblestone");
			}
			ItemStores.Room room = SupplyContainers.room(level, one);
			if (id.startsWith("limited_") && !room.bigSlots()) {
				problems.add(id + ": a limited barrel's slot holds only a stack: " + room);
			}
			int taken = SupplyContainers.extract(level, one, Items.COBBLESTONE, 30);
			int carried = 0;
			for (int tries = 0; tries < 3; tries++) {
				for (ItemStack stack : SupplyContainers.takeMatching(level, pos, s -> s.is(Items.COBBLESTONE), 9)) {
					carried += stack.getCount();
				}
			}
			if (taken != 30 || carried != 70 || SupplyContainers.count(level, one, Items.COBBLESTONE) != 0) {
				problems.add(id + ": a builder took " + taken + " of 30, a porter " + carried + " of 70");
			}
			if (!MaterialRules.isProtected(state, state.getDestroySpeed(level, pos))) {
				problems.add(id + ": a builder clearing the ground would break it");
			}
			if (id.endsWith("chest") && !state.is(chests) || id.endsWith("barrel") && !id.startsWith("limited_") && !state.is(barrels)) {
				problems.add(id + ": not in c:chests / c:barrels");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * A porter carries a miner's goods out of a Sophisticated iron barrel into the Storehouse's limited barrel, whose one
	 * slot already holds some raw iron: no empty slot, but room for many stacks.
	 */
	//$ gametest_ticks_batch AREA '1600' '"sophisticated_porter"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "sophisticated_porter")
	public void porterFillsALimitedBarrelFromASophisticatedBarrel(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = 0);
		helper.setDayTime(2000); // work hours
		ServerLevel level = helper.getLevel();
		BlockPos minersBench = new BlockPos(2, 2, 2);
		BlockPos minersBarrel = new BlockPos(2, 2, 4);
		helper.setBlock(minersBench, ModBlocks.MINERS_BENCH);
		helper.setBlock(minersBarrel, block("iron_barrel"));
		List<BlockPos> source = List.of(helper.absolutePos(minersBarrel));
		helper.assertTrue(SupplyContainers.insert(level, source, new ItemStack(Items.RAW_IRON, 64)).isEmpty()
			&& SupplyContainers.insert(level, source, new ItemStack(Items.RAW_IRON, 36)).isEmpty(), "the miner's barrel took no raw iron");
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		io.github.jcondedata.aliveworkplace.mine.Miners.employ(level, miner, helper.absolutePos(minersBench));

		BlockPos storehouse = new BlockPos(13, 2, 13);
		BlockPos storeBarrel = new BlockPos(13, 2, 11);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(storeBarrel, block("limited_barrel_1"));
		List<BlockPos> store = List.of(helper.absolutePos(storeBarrel));
		helper.assertTrue(SupplyContainers.insert(level, store, new ItemStack(Items.RAW_IRON, 10)).isEmpty(), "the store barrel took no raw iron");
		helper.assertTrue(SupplyContainers.freeSlots(level, store) == 0 && SupplyContainers.freeStacks(level, store) > 1,
			"the limited barrel: " + SupplyContainers.room(level, store));
		Villager porter = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		Porters.employ(level, porter, helper.absolutePos(storehouse));
		helper.succeedWhen(() -> {
			long stored = SupplyContainers.count(level, store, Items.RAW_IRON);
			long left = SupplyContainers.count(level, source, Items.RAW_IRON);
			helper.assertTrue(stored == 110 && left == 0, "raw iron: " + stored + " in the store, " + left + " left with the miner");
			helper.assertTrue(miner.isAlive() && porter.isAlive(), "a worker is gone");
		});
	}

	/**
	 * A builder takes its planks from a Sophisticated gold chest by the bench; the Sophisticated chest in the blueprint is
	 * built empty (its contents in the blueprint are never copied); a Sophisticated barrel with goods standing on the
	 * site is left in place with all of them.
	 */
	//$ gametest_ticks_batch AREA '2400' '"sophisticated_builder"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "sophisticated_builder")
	public void builderUsesAndLeavesSophisticatedStorage(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		// The blueprint: a plank wall with a Sophisticated chest in it, full of diamonds when it was saved.
		BlockPos chestInDesign = CompatGameTests.ORIGIN.offset(1, 0, 0);
		helper.setBlock(chestInDesign, block("chest"));
		helper.assertTrue(SupplyContainers.insert(level, List.of(helper.absolutePos(chestInDesign)), new ItemStack(Items.DIAMOND, 20)).isEmpty(),
			"no diamonds in the blueprint's chest");
		Map<BlockPos, BlockState> design = new HashMap<>();
		for (BlockPos p : List.of(new BlockPos(0, 0, 0), new BlockPos(2, 0, 0), new BlockPos(0, 1, 0), new BlockPos(2, 1, 0))) {
			design.put(p, Blocks.OAK_PLANKS.defaultBlockState());
		}
		design.put(new BlockPos(1, 0, 0), level.getBlockState(helper.absolutePos(chestInDesign)));
		ResourceLocation id = CompatGameTests.blueprintFrom(helper, "sophisticated_wall", design, Map.of());
		helper.assertTrue(level.getBlockState(helper.absolutePos(chestInDesign)).isAir(), "the design wasn't cleared");

		// On the site, where the blueprint wants air: a Sophisticated barrel with someone's goods.
		BlockPos barrelOnSite = CompatGameTests.ORIGIN.offset(1, 1, 0);
		helper.setBlock(barrelOnSite, block("diamond_barrel"));
		List<BlockPos> onSite = List.of(helper.absolutePos(barrelOnSite));
		helper.assertTrue(SupplyContainers.insert(level, onSite, new ItemStack(Items.EMERALD, 12)).isEmpty(), "the barrel on site took nothing");
		// The supplies: the Sophisticated chest item in the bench's vanilla chest, the planks in a Sophisticated gold chest.
		CompatGameTests.Setup s = CompatGameTests.setup(helper, id, new ItemStack(item("chest")));
		BlockPos supply = new BlockPos(4, 2, 2);
		helper.setBlock(supply, block("gold_chest"));
		List<BlockPos> supplies = List.of(helper.absolutePos(supply));
		helper.assertTrue(SupplyContainers.insert(level, supplies, new ItemStack(Items.OAK_PLANKS, 10)).isEmpty(), "the gold chest took no planks");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(s.site().id()) == null,
				"still building: stage=" + s.site().stage() + " status=" + s.site().status() + " missing=" + s.site().missing());
			for (BlockPos p : design.keySet()) {
				BlockPos at = s.at(p);
				helper.assertTrue(level.getBlockState(at).getBlock() == design.get(p).getBlock(), "at " + p + ": " + level.getBlockState(at));
			}
			long planks = SupplyContainers.count(level, supplies, Items.OAK_PLANKS);
			helper.assertTrue(planks <= 6, planks + " planks left in the gold chest: the builder didn't take its 4 from it");
			List<BlockPos> built = List.of(s.at(new BlockPos(1, 0, 0)));
			helper.assertTrue(SupplyContainers.count(level, built, Items.DIAMOND) == 0, "the blueprint's diamonds were copied into the built chest");
			helper.assertTrue(level.getBlockState(helper.absolutePos(barrelOnSite)).is(block("diamond_barrel")), "the barrel on the site was taken down");
			long emeralds = SupplyContainers.count(level, onSite, Items.EMERALD);
			helper.assertTrue(emeralds == 12, "the barrel on the site holds " + emeralds + " of its 12 emeralds");
		});
	}

	/**
	 * A Sophisticated barrel by the Storehouse: 27 slots of a stack, so the board shows no extra line. A player puts a
	 * stack upgrade in it (sneak-clicking its front with the upgrade, the mod's own way); each slot now holds several
	 * stacks, and the board shows the real room in items, which 100 cobblestone lower by 100; that much more fills it.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aStackUpgradeShowsItsRealRoomOnTheBoard(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(2, 2, 2);
		BlockPos barrel = new BlockPos(2, 2, 4);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(barrel, block("barrel"));
		List<BlockPos> store = SupplyContainers.find(level, helper.absolutePos(storehouse), null);
		helper.assertTrue(store.equals(List.of(helper.absolutePos(barrel))), "the store: " + store);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos at = helper.absolutePos(barrel);
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5);

		ItemStores.Room plain = SupplyContainers.room(level, store);
		helper.assertTrue(!plain.bigSlots() && plain.freeSlots() == 27 && plain.freeItems() == 27L * 64, "a plain barrel: " + plain);
		helper.assertTrue(roomLine(info(player, storehouse, helper)) == null, "a room line without an upgrade");

		ItemStack upgrade = new ItemStack(item("stack_upgrade_tier_2"));
		player.setItemInHand(InteractionHand.MAIN_HAND, upgrade);
		player.setShiftKeyDown(true);
		Direction front = front(level.getBlockState(at));
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at).relative(front, 0.5), front, at, false);
		player.gameMode.useItemOn(player, level, upgrade, InteractionHand.MAIN_HAND, hit);
		player.setShiftKeyDown(false);

		ItemStores.Room up = SupplyContainers.room(level, store);
		helper.assertTrue(up.bigSlots() && up.freeSlots() == 27 && up.freeItems() > 27L * 64 && up.freeItems() % 27 == 0,
			"the stack upgrade isn't in its slot (front " + front + "): " + up);
		helper.assertTrue(up.freeStacks() == up.freeItems() / 64, "free stacks " + up.freeStacks() + " for " + up.freeItems() + " items");
		Component line = roomLine(info(player, storehouse, helper));
		helper.assertTrue(line != null && arg(line).equals(String.valueOf(up.freeItems())), "the board's room line: " + line + ", wanted " + up.freeItems());

		helper.assertTrue(SupplyContainers.insert(level, store, new ItemStack(Items.COBBLESTONE, 100)).isEmpty(), "the barrel took no cobblestone");
		ItemStores.Room after = SupplyContainers.room(level, store);
		helper.assertTrue(after.freeItems() == up.freeItems() - 100 && after.freeSlots() == 26, "after 100 cobblestone: " + after);
		Component again = roomLine(info(player, storehouse, helper));
		helper.assertTrue(again != null && arg(again).equals(String.valueOf(after.freeItems())), "the board after 100 cobblestone: " + again);
		// The room is real: that much more cobblestone goes in, and then not one more.
		ItemStack rest = SupplyContainers.insert(level, store, new ItemStack(Items.COBBLESTONE, (int) after.freeItems()));
		ItemStores.Room full = SupplyContainers.room(level, store);
		helper.assertTrue(rest.isEmpty() && full.freeItems() == 0 && full.freeSlots() == 0, "filled to the room shown: " + rest.getCount() + " left over, " + full);
		helper.assertTrue(SupplyContainers.insert(level, store, new ItemStack(Items.COBBLESTONE, 1)).getCount() == 1, "a full barrel took one more");
		helper.succeed();
	}

	static ItemLore info(ServerPlayer player, BlockPos storehouse, GameTestHelper helper) {
		ItemLore lore = StorehouseBoard.boardForTest(player, helper.absolutePos(storehouse)).icon(StorehouseBoard.INFO_SLOT).get(DataComponents.LORE);
		helper.assertTrue(lore != null, "the board's info has no text");
		return lore;
	}

	static Component roomLine(ItemLore lore) {
		return lore.lines().stream().filter(l -> l.getContents() instanceof TranslatableContents t
			&& t.getKey().equals("screen.aliveworkplace.storehouse.room")).findFirst().orElse(null);
	}

	private static String arg(Component line) {
		return String.valueOf(((TranslatableContents) line.getContents()).getArgs()[0]);
	}

	/** The side a storage block faces (its {@code facing} or {@code horizontal_facing} state), read by name. */
	static Direction front(BlockState state) {
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals("facing") && state.getValue(property) instanceof Direction d) {
				return d;
			}
		}
		throw new GameTestAssertException("no facing on " + state);
	}

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(MOD, id))
			.orElseThrow(() -> new GameTestAssertException("no block " + MOD + ":" + id));
	}

	static Item item(String id) {
		return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(MOD, id))
			.orElseThrow(() -> new GameTestAssertException("no item " + MOD + ":" + id));
	}
}
