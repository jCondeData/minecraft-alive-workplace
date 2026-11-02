package io.github.jcondedata.aliveworkplace.test;

import com.mojang.serialization.Codec;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.PieceLooks;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.Services;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane E, saves, 2026-11-02; B763's kind, for the Village Hall): one damaged entry in one of a hall's saved lists
 * mustn't cost the village the rest of that list. The hall reads its edicts, guilds, reforms, found services and the
 * houses' furniture looks each with a list codec and {@code .result()}, which gives nothing back when any one entry
 * fails, so a single bad entry (a hand-edited file, one a crash cut short) silently repeals every edict, disbands every
 * guild and forgets every reform's progress. Each list is saved with two good entries and, in the damaged copy, an empty
 * entry put first; the damaged load must give back the same good entries the clean one does.
 */
public class QaHallListsOneBadEntry1102BugTests implements FabricGameTest {

	private record Case(String key, Tag good, Function<VillageHallBlockEntity, List<?>> read) {
	}

	private static <T> Tag encode(Codec<T> codec, List<T> values) {
		return codec.listOf().encodeStart(NbtOps.INSTANCE, values).getOrThrow();
	}

	private static List<Case> cases() {
		UUID master = new UUID(0x1234L, 0x5678L);
		return List.of(
			new Case("edicts", encode(Edicts.InForce.CODEC, List.of(new Edicts.InForce(AliveWorkplace.id("long_shifts").toString(), 3),
				new Edicts.InForce(AliveWorkplace.id("curfew").toString(), 4))), VillageHallBlockEntity::edicts),
			new Case("guilds", encode(Guilds.Charter.CODEC, List.of(new Guilds.Charter(AliveWorkplace.id("masons"), master, "Bram", 5, Optional.empty()),
				new Guilds.Charter(AliveWorkplace.id("farmers"), master, "Tom", 6, Optional.of(new BlockPos(1, 2, 3))))), VillageHallBlockEntity::guilds),
			new Case("reforms", encode(Reforms.Progress.CODEC, List.of(new Reforms.Progress(AliveWorkplace.id("long_shifts").toString(), 2, false, 9),
				new Reforms.Progress(AliveWorkplace.id("curfew").toString(), 1, true, 0))), VillageHallBlockEntity::reforms),
			new Case("services", encode(Services.Found.CODEC, List.of(new Services.Found(AliveWorkplace.id("inn"), new BlockPos(10, 64, 10), 48),
				new Services.Found(AliveWorkplace.id("clinic"), new BlockPos(-10, 64, 10), 32))), VillageHallBlockEntity::services),
			new Case("piece_looks", encode(PieceLooks.Choice.CODEC, List.of(
				new PieceLooks.Choice(new BlockPos(5, 64, 5), Rotation.NONE, "aliveworkplace:stone_house", "plains", "oak"),
				new PieceLooks.Choice(new BlockPos(20, 64, 5), Rotation.CLOCKWISE_90, "aliveworkplace:inn", "taiga", "spruce"))), VillageHallBlockEntity::pieceLooks));
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void oneDamagedEntryInAHallsListDoesntLoseTheRestOfIt(GameTestHelper helper) {
		var level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		var state = ModBlocks.VILLAGE_HALL.defaultBlockState();
		CompoundTag base = new VillageHallBlockEntity(pos, state).saveWithoutMetadata(level.registryAccess());
		List<String> lost = new ArrayList<>();
		for (Case c : cases()) {
			CompoundTag clean = base.copy();
			clean.put(c.key(), c.good().copy());
			VillageHallBlockEntity a = new VillageHallBlockEntity(pos, state);
			a.loadWithComponents(clean, level.registryAccess());
			List<?> want = c.read().apply(a);
			if (want.size() != 2) {
				lost.add(c.key() + ": the clean load gave " + want.size() + " of 2 entries back (the test's entries are wrong): " + want);
				continue;
			}
			CompoundTag hurt = base.copy();
			ListTag list = (ListTag) c.good().copy();
			list.add(0, new CompoundTag());
			hurt.put(c.key(), list);
			VillageHallBlockEntity b = new VillageHallBlockEntity(pos, state);
			b.loadWithComponents(hurt, level.registryAccess());
			List<?> got = c.read().apply(b);
			if (!got.containsAll(want)) {
				lost.add(c.key() + ": " + got.size() + " of " + want.size() + " good entries back with one empty entry first (" + got + ")");
			}
		}
		helper.assertTrue(lost.isEmpty(), "one damaged entry loses a hall's whole list: " + lost);
		helper.succeed();
	}
}
