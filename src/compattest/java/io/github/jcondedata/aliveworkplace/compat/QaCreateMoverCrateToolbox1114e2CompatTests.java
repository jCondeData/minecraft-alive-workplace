package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.SPOT;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.held;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setup;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverCrateLastResort1114eBugCompatTests.notAStorageRoom;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverCrateLastResort1114eBugCompatTests.round;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverCrateLastResort1114eBugCompatTests.storageRoom;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.moved;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane e, 2026-11-14 evening), the fix for B1246-B1250/B1252 (748f0f194) with Create: a packed block whose new spot
 * holds a Mover's Crate drops as itself with everything it held, and nothing is ever counted twice. The fix's last resort
 * (no spot round the crate) drops the block's Silk Touch loot and then every item saved anywhere in its data "unless the
 * block's item keeps them", which it judges by vanilla's {@code container} component only. Create's Toolbox keeps its
 * items in its own {@code create:toolbox_inventory} component (its loot table copies it), so these tests count every item
 * that exists after the move, loose or inside a dropped block's item: exactly as many as were packed (B1172: nothing packed
 * is deleted; nothing is made from nothing either).
 */
public class QaCreateMoverCrateToolbox1114e2CompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";

	/**
	 * How many {@code item} are saved anywhere in {@code tag} (an item's id with a count), the item data itself included,
	 * leaving out Create's filters (a Toolbox's {@code filters}, a FilteringBehaviour's {@code Filter}): those are ghost
	 * copies a player sets, never items he has (a toolbox compartment's filter is a 1-count copy of what it holds).
	 */
	static long countIn(Tag tag, String id) {
		long n = 0;
		if (tag instanceof CompoundTag c) {
			if (id.equals(c.getString("id")) && (c.contains("count", Tag.TAG_ANY_NUMERIC) || c.contains("Count", Tag.TAG_ANY_NUMERIC))) {
				n += c.contains("count", Tag.TAG_ANY_NUMERIC) ? c.getInt("count") : c.getInt("Count");
			}
			for (String key : c.getAllKeys()) {
				if (key.equals("filters") || key.equals("Filter")) {
					continue;
				}
				n += countIn(c.get(key), id);
			}
		} else if (tag instanceof ListTag list) {
			for (Tag t : list) {
				n += countIn(t, id);
			}
		}
		return n;
	}

	/** Every {@code item} round the test: loose, and inside every loose item's own data (a dropped toolbox's inventory). */
	static long everywhereLoose(GameTestHelper helper, Item item) {
		ServerLevel level = helper.getLevel();
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		long n = 0;
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(34))) {
			ItemStack stack = e.getItem();
			if (!stack.isEmpty()) {
				n += countIn(stack.save(level.registryAccess()), id);
			}
		}
		return n;
	}

	/** Every loose item round the test, by id and count, with what each one holds counted too. */
	static Map<String, Long> looseWithInside(GameTestHelper helper, Item inside) {
		ServerLevel level = helper.getLevel();
		Map<String, Long> out = new LinkedHashMap<>();
		String id = BuiltInRegistries.ITEM.getKey(inside).toString();
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(34))) {
			ItemStack stack = e.getItem();
			String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			long held = stack.is(inside) ? 0 : countIn(stack.save(level.registryAccess()), id);
			out.merge(held > 0 ? key + "(holding " + held + ")" : key, (long) stack.getCount(), Long::sum);
		}
		return out;
	}

	/**
	 * Moves {@code state} holding {@code count} {@code item} onto a Mover's Crate, the crate in a storage room when
	 * {@code storeroom}, else in the open; checks the block itself drops and the items exist exactly {@code count} times.
	 */
	static void onACrate(GameTestHelper helper, String name, BlockState state, String blockItem, Item item, int count, boolean storeroom) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		Setup s = setup(helper, name, state, item, count);
		helper.runAfterDelay(2, () -> {
			BlockPos landing = moved(s, SPOT);
			String end;
			String room = null;
			Map<String, Long> looseNow;
			long inOld;
			long inNew;
			long inBarrels = 0;
			long lying;
			long blocks;
			try {
				set(level, landing, ModBlocks.MOVERS_CRATE.defaultBlockState());
				MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.STRUCTURE || x.skipped() > 0);
				helper.assertTrue(site.stage() == MoveSite.Stage.STRUCTURE && site.skipped() == 0,
					"setup: the put-up didn't get to STRUCTURE before the drop: " + site.stage() + ", skipped " + site.skipped());
				String[] roomAtDrop = {null};
				site = work(level, s, x -> {
					if (x.skipped() > 0) {
						return true;
					}
					if (storeroom) {
						storageRoom(level, landing);
						roomAtDrop[0] = notAStorageRoom(level, landing);
					}
					return false;
				});
				room = roomAtDrop[0];
				helper.assertTrue(site.skipped() == 1, "the " + name + " on the crate was never dropped: " + site.stage() + ", skipped " + site.skipped());
				helper.assertTrue(room == null, "setup: the crate wasn't in a storage room right before the drop: " + room);
				helper.assertTrue(level.getBlockState(landing).is(ModBlocks.MOVERS_CRATE), "setup: the crate is gone: " + level.getBlockState(landing));
				site = work(level, s, x -> false);
				end = site.stage() + ", skipped " + site.skipped();
				looseNow = looseWithInside(helper, item);
				lying = everywhereLoose(helper, item);
				inOld = held(level, helper.absolutePos(SPOT), item);
				inNew = held(level, landing, item);
				for (BlockPos p : round(landing)) {
					if (level.getBlockEntity(p) instanceof Container c) {
						for (int i = 0; i < c.getContainerSize(); i++) {
							if (c.getItem(i).is(item)) {
								inBarrels += c.getItem(i).getCount();
							}
						}
					}
				}
				blocks = looseNow.entrySet().stream().filter(e -> e.getKey().startsWith(blockItem)).mapToLong(Map.Entry::getValue).sum();
			} finally {
				tidy(helper, s);
			}
			String where = storeroom ? "in a storage room" : "in the open";
			helper.assertTrue(end.startsWith("DONE") && end.contains("skipped 1"), "the move: " + end);
			helper.assertTrue(blocks == 1, "the " + name + " itself: " + blocks + " dropped, not 1; loose " + looseNow);
			long all = inOld + inNew + inBarrels + lying;
			helper.assertTrue(all == count, "a " + name + " holding " + count + " " + BuiltInRegistries.ITEM.getKey(item).getPath()
				+ " went up over a Mover's Crate " + where + " (move " + end + "): " + all + " exist after, not " + count + " (" + inOld
				+ " at the old spot, " + inNew + " at the new, " + inBarrels + " in the barrels, " + lying + " loose or in a dropped item); loose "
				+ looseNow + (all > count ? " (made from nothing: counted twice)" : " (lost)"));
			helper.succeed();
		});
	}

	/** A Toolbox holding 8 iron ingots, its crate with room round it (the borrowed spot): the toolbox and 8 ingots, not 16. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1114b_crate_toolbox_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1114b_crate_toolbox_open")
	public void aToolboxOnACrateInTheOpenIsDroppedWithItsPartsOnce(GameTestHelper helper) {
		onACrate(helper, "toolbox_open", block("create:brown_toolbox").defaultBlockState(), "create:brown_toolbox", Items.IRON_INGOT, 8, false);
	}

	/** A Depot holding 16 copper ingots, its crate in a storage room: the depot and its 16, once. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1114b_crate_depot_storeroom"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1114b_crate_depot_storeroom")
	public void aDepotOnACrateAmongContainersIsDroppedWithItsItemOnce(GameTestHelper helper) {
		onACrate(helper, "depot_storeroom", block("create:depot").defaultBlockState(), "create:depot", Items.COPPER_INGOT, 16, true);
	}

	/** An Item Vault holding 48 emeralds, its crate in the open: the vault and its 48, once (B1252's open-room side). */
	//$ gametest_ticks_batch AREA '400' '"qa_e1114b_crate_vault_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1114b_crate_vault_open")
	public void aVaultOnACrateInTheOpenIsDroppedWithItsEmeraldsOnce(GameTestHelper helper) {
		onACrate(helper, "vault_open", block("create:item_vault").defaultBlockState(), "create:item_vault", Items.EMERALD, 48, false);
	}
}
