package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * QA (lane D, 2026-11-14), verifying B1244's fix from its spec: on a hall's Map (the waystone network, 41.2) a far
 * (unloaded) village whose saved known-village name is blank is listed by its made-up name, as B1229 does elsewhere.
 * The other side of each boundary too: a far village with a real saved name keeps it (the fix must not make up a name
 * for every far village), a loaded village is named by its own hall, and a blank name survives a save and reload of the
 * realm list only as the made-up name on the Map.
 */
public class QaWaystoneBlankNameVerify1114dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String LINE = "screen.aliveworkplace.hall.waystone";

	private static RealmData.Known known(ServerLevel level, BlockPos hall, Component name, UUID owner) {
		return new RealmData.Known(GlobalPos.of(level.dimension(), hall), name, Optional.of(owner), "Jesse", "hamlet", 0, 0, level.getGameTime());
	}

	/** A world with a hall of the player's here and far villages at {@code halls} (unloaded) with stones at {@code stones}. */
	private record World(ServerLevel level, ServerPlayer player, BlockPos home, List<BlockPos> halls, Runnable forget) {
	}

	private static World world(GameTestHelper helper, List<BlockPos> offsets) {
		RaidHelperCompatTests.clearAround(helper);
		VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos home = helper.absolutePos(WaystonesCompatTests.HALL);
		List<BlockPos> stones = offsets.stream().map(home::offset).toList();
		List<BlockPos> halls = stones.stream().map(s -> s.offset(2, 0, 2)).toList();
		RealmData realms = RealmData.get(level.getServer());
		Runnable forget = () -> {
			for (BlockPos h : halls) {
				VillageWaystones.Data.get(level).forget(h);
				realms.forgetKnown(GlobalPos.of(level.dimension(), h));
			}
			VillageWaystones.Data.get(level).forget(home);
			realms.forgetKnown(GlobalPos.of(level.dimension(), home));
			level.removeBlock(home, false);
		};
		forget.run();
		level.setBlock(home, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		if (!(level.getBlockEntity(home) instanceof VillageHallBlockEntity entity)) {
			throw new GameTestAssertException("setup: no hall at " + home);
		}
		entity.setCustomName(Component.literal("Home"));
		entity.setOwner(player.getUUID(), "Jesse");
		for (BlockPos h : halls) {
			helper.assertFalse(level.isLoaded(h), "setup: a far village's hall is loaded at " + h);
		}
		// as if their halls raised these stones on an earlier day
		CompoundTag tag = VillageWaystones.Data.get(level).save(new CompoundTag(), level.registryAccess());
		long[] oldHalls = tag.getLongArray("halls");
		long[] oldStones = tag.getLongArray("stones");
		long[] h = java.util.Arrays.copyOf(oldHalls, oldHalls.length + halls.size());
		long[] s = java.util.Arrays.copyOf(oldStones, oldStones.length + halls.size());
		for (int i = 0; i < halls.size(); i++) {
			h[oldHalls.length + i] = halls.get(i).asLong();
			s[oldStones.length + i] = stones.get(i).asLong();
		}
		tag.putLongArray("halls", h);
		tag.putLongArray("stones", s);
		level.getDataStorage().set("aliveworkplace_village_waystones", VillageWaystones.Data.load(tag, level.registryAccess()));
		return new World(level, player, home, halls, forget);
	}

	private static String nameOn(List<VillageWaystones.Village> network, BlockPos hall) {
		return network.stream().filter(v -> v.hall().equals(hall)).findFirst().map(v -> v.name().getString()).orElse("(not listed)");
	}

	/**
	 * Two far villages side by side: Cedarford, saved by its name, and one whose saved name is blank. Cedarford is listed
	 * as Cedarford (not a made-up name) and the blank one by its made-up name, each on its own Map line, nearest first.
	 */
	//$ gametest_batch AREA '"qaWaystoneBlankVerify1114dReal"'
	@GameTest(template = AREA, batch = "qaWaystoneBlankVerify1114dReal")
	public void aFarVillageWithARealSavedNameKeepsItBesideABlankOne(GameTestHelper helper) {
		World w = world(helper, List.of(new BlockPos(1500, 0, 0), new BlockPos(-2500, 0, 0)));
		try {
			RealmData realms = RealmData.get(w.level().getServer());
			realms.putKnown(known(w.level(), w.halls().get(0), Component.literal("Cedarford"), w.player().getUUID()));
			realms.putKnown(known(w.level(), w.halls().get(1), Component.empty(), w.player().getUUID()));
			List<VillageWaystones.Village> network = VillageWaystones.network(w.level(), w.home(), w.player());
			String made = VillageHalls.madeUpName(w.halls().get(1)).getString();
			helper.assertTrue(nameOn(network, w.halls().get(0)).equals("Cedarford"),
				"a far village saved as Cedarford is listed as '" + nameOn(network, w.halls().get(0)) + "'");
			helper.assertTrue(nameOn(network, w.halls().get(1)).equals(made),
				"a far village with a blank saved name is listed as '" + nameOn(network, w.halls().get(1)) + "', not " + made);
			List<String> lines = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(w.player(), w.home()), LINE);
			List<String> want = List.of("Cedarford, 1500 blocks east: its waystone is broken", made + ", 2500 blocks west: its waystone is broken");
			int a = lines.indexOf(want.get(0));
			int b = lines.indexOf(want.get(1));
			helper.assertTrue(a >= 0 && b > a, "the Map should list " + want + " in that order (nearest first); it reads " + lines);
		} finally {
			w.forget().run();
		}
		helper.succeed();
	}

	/**
	 * Blank in every way a saved name can be: a tab, a newline, and an empty name with only a style (bold, gold). Each is
	 * listed by the village's made-up name; a styled real name keeps its text.
	 */
	//$ gametest_batch AREA '"qaWaystoneBlankVerify1114dKinds"'
	@GameTest(template = AREA, batch = "qaWaystoneBlankVerify1114dKinds")
	public void tabsNewlinesAndStyledEmptyNamesAllGetTheMadeUpName(GameTestHelper helper) {
		World w = world(helper, List.of(new BlockPos(1800, 0, 0), new BlockPos(-1800, 0, 0), new BlockPos(0, 0, 1800), new BlockPos(0, 0, -1800)));
		try {
			RealmData realms = RealmData.get(w.level().getServer());
			List<Component> names = List.of(Component.literal("\t"), Component.literal("\n "),
				Component.empty().withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD), Component.literal("Ashdale").withStyle(ChatFormatting.GOLD));
			String[] why = {"a tab", "a newline and a space", "an empty bold gold name", "a gold Ashdale"};
			for (int i = 0; i < names.size(); i++) {
				realms.putKnown(known(w.level(), w.halls().get(i), names.get(i), w.player().getUUID()));
			}
			List<VillageWaystones.Village> network = VillageWaystones.network(w.level(), w.home(), w.player());
			List<String> wrong = new ArrayList<>();
			for (int i = 0; i < names.size(); i++) {
				String want = i == 3 ? "Ashdale" : VillageHalls.madeUpName(w.halls().get(i)).getString();
				String got = nameOn(network, w.halls().get(i));
				if (!got.equals(want)) {
					wrong.add(why[i] + ": '" + got + "', not '" + want + "'");
				}
			}
			helper.assertTrue(wrong.isEmpty(), "far villages on the network named wrong: " + String.join("; ", wrong));
		} finally {
			w.forget().run();
		}
		helper.succeed();
	}

	/**
	 * The realm list saved with a blank far-village name and loaded again (a server restart): the Map still names that
	 * village by its made-up name, and the same made-up name every time (it is tied to the hall's spot).
	 */
	//$ gametest_batch AREA '"qaWaystoneBlankVerify1114dReload"'
	@GameTest(template = AREA, batch = "qaWaystoneBlankVerify1114dReload")
	public void aBlankSavedNameStaysTheSameMadeUpNameAfterASaveAndReload(GameTestHelper helper) {
		World w = world(helper, List.of(new BlockPos(2200, 0, 0)));
		try {
			RealmData realms = RealmData.get(w.level().getServer());
			BlockPos far = w.halls().get(0);
			realms.putKnown(known(w.level(), far, Component.literal(" "), w.player().getUUID()));
			String before = nameOn(VillageWaystones.network(w.level(), w.home(), w.player()), far);
			CompoundTag saved = realms.save(new CompoundTag(), w.level().registryAccess());
			RealmData loaded = RealmData.load(saved, w.level().registryAccess());
			RealmData.Known back = loaded.known(GlobalPos.of(w.level().dimension(), far));
			helper.assertTrue(back != null, "the far village is gone from the realm list after a save and load");
			realms.forgetKnown(GlobalPos.of(w.level().dimension(), far));
			realms.putKnown(back);
			String after = nameOn(VillageWaystones.network(w.level(), w.home(), w.player()), far);
			String made = VillageHalls.madeUpName(far).getString();
			helper.assertTrue(before.equals(made) && after.equals(made),
				"a far village with a blank saved name reads '" + before + "' before a reload and '" + after + "' after, not " + made + " both times");
		} finally {
			w.forget().run();
		}
		helper.succeed();
	}

	/**
	 * The village whose Map is open (loaded, its hall named Home) is listed by its hall's own name even when the realm
	 * list saved a blank one for it: the made-up name is only for villages too far away to read.
	 */
	//$ gametest_batch AREA '"qaWaystoneBlankVerify1114dHere"'
	@GameTest(template = AREA, batch = "qaWaystoneBlankVerify1114dHere")
	public void aLoadedVillageIsNamedByItsHallNotByABlankSavedName(GameTestHelper helper) {
		World w = world(helper, List.of(new BlockPos(1600, 0, 0)));
		try {
			RealmData realms = RealmData.get(w.level().getServer());
			realms.putKnown(known(w.level(), w.home(), Component.empty(), w.player().getUUID()));
			realms.putKnown(known(w.level(), w.halls().get(0), Component.literal("Birchton"), w.player().getUUID()));
			// the home hall needs its own stone to be listed: give it one where the far ones keep theirs
			CompoundTag tag = VillageWaystones.Data.get(w.level()).save(new CompoundTag(), w.level().registryAccess());
			long[] h = tag.getLongArray("halls");
			long[] s = tag.getLongArray("stones");
			long[] h2 = java.util.Arrays.copyOf(h, h.length + 1);
			long[] s2 = java.util.Arrays.copyOf(s, s.length + 1);
			h2[h.length] = w.home().asLong();
			s2[s.length] = w.home().offset(-1, 0, -1).asLong();
			tag.putLongArray("halls", h2);
			tag.putLongArray("stones", s2);
			w.level().getDataStorage().set("aliveworkplace_village_waystones", VillageWaystones.Data.load(tag, w.level().registryAccess()));
			List<VillageWaystones.Village> network = VillageWaystones.network(w.level(), w.home(), w.player());
			helper.assertTrue(!network.isEmpty() && network.get(0).here(), "the village whose Map is open should come first: " + network);
			helper.assertTrue(nameOn(network, w.home()).equals("Home"), "the loaded village is listed as '" + nameOn(network, w.home()) + "', not Home");
			helper.assertTrue(nameOn(network, w.halls().get(0)).equals("Birchton"), "the far village is listed as '" + nameOn(network, w.halls().get(0)) + "', not Birchton");
		} finally {
			w.forget().run();
		}
		helper.succeed();
	}
}
