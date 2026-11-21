package io.github.jcondedata.aliveworkplace.compat.toms;

import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * B1025: each inventory once in a Tom's Simple Storage network. Tom's (read from its 2.3.0 jar) merges a network's
 * inventories in {@code MultiInventoryAccess.build} and tells them apart by the object that reads them, one per block
 * face: its inventory connector makes one for the face of each inventory it touches, and each cable connector has its
 * own for the block it faces. So a chest that touches the connector and is also faced by a cable connector on the same
 * network was merged twice, and the connector's item storage showed every slot of it twice: the Storehouse counted 20
 * of its 10 cobblestone and two chests' room (Tom's own terminal with it).
 *
 * <p>Here the merged list loses every later reader of a block that shows exactly the slots an earlier one shows (the
 * same slot objects in the same order, as Fabric hands them out for one container whichever face is asked). A block
 * whose faces show different slots (a furnace), a reader behind one of Tom's filters, and storage that makes its slots
 * anew each time are left as they are. With config {@code moddedStorageNetworks} off, nothing.
 */
public final class TomsMerge {
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace");
	private static boolean failed;

	private TomsMerge() {
	}

	/** Takes out of {@code connected} (Tom's merged inventories, in its order) the readers showing an earlier one's slots. */
	public static void once(List<Object> connected) {
		if (failed || !StorageNetworks.ENABLED || connected == null || connected.size() < 2) {
			return;
		}
		try {
			List<List<Object>> kept = new ArrayList<>();
			for (Iterator<Object> it = connected.iterator(); it.hasNext();) {
				if (!(it.next() instanceof TomsBlockAccess block)) {
					continue;
				}
				Storage<ItemVariant> storage = block.aliveworkplace$storage();
				if (storage == null) {
					continue;
				}
				List<Object> slots = slots(storage);
				if (slots.isEmpty()) {
					continue;
				}
				if (kept.stream().anyMatch(earlier -> same(earlier, slots))) {
					it.remove();
				} else {
					kept.add(slots);
				}
			}
		} catch (RuntimeException | LinkageError e) {
			failed = true; // Tom's changed: leave its networks as it builds them
			LOGGER.warn("Tom's Simple Storage: can't tell a network's inventories apart, leaving them as they are", e);
		}
	}

	/** The slots of {@code storage}, each as the view under any wrappers. */
	private static List<Object> slots(Storage<ItemVariant> storage) {
		List<Object> out = new ArrayList<>();
		for (StorageView<ItemVariant> view : storage) {
			StorageView<ItemVariant> under = view;
			for (int depth = 0; depth < 8; depth++) {
				StorageView<ItemVariant> next = under.getUnderlyingView();
				if (next == null || next == under) {
					break;
				}
				under = next;
			}
			out.add(under);
		}
		return out;
	}

	private static boolean same(List<Object> a, List<Object> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (a.get(i) != b.get(i)) {
				return false;
			}
		}
		return true;
	}
}
