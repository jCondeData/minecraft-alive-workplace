package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bug tests (QA, lane D, 2026-11-14), B1229's expectation ("a blank saved name counts as missing and falls back to the
 * made-up name") on the waystone network (41.2): for a far village whose chunks aren't loaded,
 * {@link VillageWaystones#network} takes the name straight from the realm's known-village list, and a blank one (an
 * older save that kept no name, or a hand-edited one) reaches the hall's Map as an empty name. Not registered on main:
 * fails until the mod is fixed.
 */
public class QaWaystoneBlankName1114dBugTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String LINE = "screen.aliveworkplace.hall.waystone";

	private static List<String> mapLines(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		return WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, hall), LINE);
	}

	// --- far villages (their chunks unloaded): the Map reads the name their last round saved

	private static RealmData.Known known(ServerLevel level, BlockPos hall, Component name, UUID owner) {
		return new RealmData.Known(GlobalPos.of(level.dimension(), hall), name, Optional.of(owner), "Jesse", "hamlet", 0, 0, level.getGameTime());
	}

	/** The village's known entry as a save written without a name reads it back (the codec's default). */
	private static RealmData.Known nameless(ServerLevel level, BlockPos hall, UUID owner) {
		var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag tag = RealmData.Known.CODEC.encodeStart(ops, known(level, hall, Component.literal("x"), owner)).getOrThrow();
		((CompoundTag) tag).remove("name");
		return RealmData.Known.CODEC.parse(ops, tag).getOrThrow();
	}

	/**
	 * Three far villages of the player's, unloaded, each with a waystone: one whose saved name is empty, one of spaces, one
	 * from a save that kept none. The Map (and the network behind it) names each by its made-up name, never a blank.
	 */
	//$ gametest_batch AREA '"qaWaystoneBlankName1114dFar"'
	@GameTest(template = AREA, batch = "qaWaystoneBlankName1114dFar")
	public void aFarVillageWithABlankSavedNameIsListedByItsMadeUpName(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		UUID owner = player.getUUID();
		BlockPos home = helper.absolutePos(WaystonesCompatTests.HALL);
		List<BlockPos> stones = List.of(home.offset(2000, 0, 0), home.offset(-2000, 0, 0), home.offset(0, 0, 2000));
		List<BlockPos> halls = stones.stream().map(s -> s.offset(2, 0, 2)).toList();
		List<String> reads = List.of("2000 blocks east", "2000 blocks west", "2000 blocks south");
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
		try {
			level.setBlock(home, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
			if (!(level.getBlockEntity(home) instanceof VillageHallBlockEntity entity)) {
				throw new GameTestAssertException("setup: no hall at " + home);
			}
			entity.setCustomName(Component.literal("Home"));
			entity.setOwner(owner, "Jesse");
			for (BlockPos h : halls) {
				helper.assertFalse(level.isLoaded(h), "setup: a far village's hall is loaded at " + h);
			}
			realms.putKnown(known(level, halls.get(0), Component.empty(), owner));
			realms.putKnown(known(level, halls.get(1), Component.literal("   "), owner));
			RealmData.Known old = nameless(level, halls.get(2), owner);
			helper.assertTrue(old.name().getString().isEmpty(), "setup: a known village saved without a name reads back as '" + old.name().getString() + "'");
			realms.putKnown(old);
			// as if their halls raised these stones on an earlier day
			CompoundTag tag = VillageWaystones.Data.get(level).save(new CompoundTag(), level.registryAccess());
			long[] oldHalls = tag.getLongArray("halls");
			long[] oldStones = tag.getLongArray("stones");
			long[] h = java.util.Arrays.copyOf(oldHalls, oldHalls.length + 3);
			long[] s = java.util.Arrays.copyOf(oldStones, oldStones.length + 3);
			for (int i = 0; i < 3; i++) {
				h[oldHalls.length + i] = halls.get(i).asLong();
				s[oldStones.length + i] = stones.get(i).asLong();
			}
			tag.putLongArray("halls", h);
			tag.putLongArray("stones", s);
			level.getDataStorage().set("aliveworkplace_village_waystones", VillageWaystones.Data.load(tag, level.registryAccess()));

			List<VillageWaystones.Village> network = VillageWaystones.network(level, home, player);
			List<String> lines = mapLines(helper, player, home);
			String[] why = {"an empty saved name", "a saved name of spaces", "no saved name (an older save)"};
			List<String> wrong = new java.util.ArrayList<>();
			for (int i = 0; i < 3; i++) {
				BlockPos at = halls.get(i);
				String made = VillageHalls.madeUpName(at).getString();
				String name = network.stream().filter(v -> v.hall().equals(at)).findFirst().map(v -> v.name().getString()).orElse("(not listed)");
				String want = made + ", " + reads.get(i) + ": its waystone is broken";
				if (!name.equals(made) || !lines.contains(want)) {
					wrong.add(why[i] + ": named '" + name + "', not " + made);
				}
			}
			helper.assertTrue(wrong.isEmpty(), "far villages on the waystone network without a readable name (" + String.join("; ", wrong) + "); the Map reads " + lines);
		} finally {
			forget.run();
		}
		helper.succeed();
	}
}
