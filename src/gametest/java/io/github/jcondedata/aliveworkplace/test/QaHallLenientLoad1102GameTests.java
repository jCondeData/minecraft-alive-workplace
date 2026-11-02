package io.github.jcondedata.aliveworkplace.test;

import com.mojang.serialization.Codec;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.PieceLooks;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.Services;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.research.Research;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-c-1102-0953) of B796's fix, from its expected behaviour ("the bad entry is skipped ... and the rest load")
 * and its commit's promise ("encoding is unchanged, so saves read the same both ways"), on the sides its own tests
 * don't reach: a clean hall saved, loaded and saved again writes the same tags; a hall loaded with one damaged entry in
 * each list writes back only the good ones, and that save loads the same again (the damage doesn't come back or grow);
 * and a list saved as the wrong kind of tag altogether (a string, a number, a compound) loads empty without costing the
 * hall anything else.
 */
public class QaHallLenientLoad1102GameTests implements FabricGameTest {
	private static final List<String> LISTS = List.of("edicts", "guilds", "reforms", "services", "piece_looks");

	private static <T> Tag encode(Codec<T> codec, List<T> values) {
		return codec.listOf().encodeStart(NbtOps.INSTANCE, values).getOrThrow();
	}

	/** A hall's save with two good entries in each of the five lists, two plan zones and two research levels. */
	private static CompoundTag full(GameTestHelper helper, BlockPos pos, BlockState state) {
		var level = helper.getLevel();
		BitSet cells = new BitSet();
		cells.set(3);
		cells.set(9);
		VillageHallBlockEntity a = new VillageHallBlockEntity(pos, state);
		a.setOwner(new UUID(0xabcL, 0xdefL), "Ana");
		a.setPlan(new CityPlan(List.of(new CityPlan.Zone("homes", "North Homes", "", false, cells),
			new CityPlan.Zone("market", "Market", "", true, cells)), List.of(), Optional.empty(), CityPlan.Mode.ASK));
		a.setResearch(new Research.State(Map.of("scholars/farming", 2, "scholars/masonry", 1), Optional.empty(), 0, false));
		CompoundTag tag = a.saveWithoutMetadata(level.registryAccess());
		UUID master = new UUID(0x1234L, 0x5678L);
		tag.put("edicts", encode(Edicts.InForce.CODEC, List.of(new Edicts.InForce(AliveWorkplace.id("long_shifts").toString(), 3),
			new Edicts.InForce(AliveWorkplace.id("curfew").toString(), 4))));
		tag.put("guilds", encode(Guilds.Charter.CODEC, List.of(new Guilds.Charter(AliveWorkplace.id("masons"), master, "Bram", 5, Optional.empty()),
			new Guilds.Charter(AliveWorkplace.id("farmers"), master, "Tom", 6, Optional.of(new BlockPos(1, 2, 3))))));
		tag.put("reforms", encode(Reforms.Progress.CODEC, List.of(new Reforms.Progress(AliveWorkplace.id("long_shifts").toString(), 2, false, 9),
			new Reforms.Progress(AliveWorkplace.id("curfew").toString(), 1, true, 0))));
		tag.put("services", encode(Services.Found.CODEC, List.of(new Services.Found(AliveWorkplace.id("inn"), new BlockPos(10, 64, 10), 48),
			new Services.Found(AliveWorkplace.id("clinic"), new BlockPos(-10, 64, 10), 32))));
		tag.putLong("servicesDay", 5); // the hall writes its found services back only once it has looked for them
		tag.put("piece_looks", encode(PieceLooks.Choice.CODEC, List.of(
			new PieceLooks.Choice(new BlockPos(5, 64, 5), Rotation.NONE, "aliveworkplace:stone_house", "plains", "oak"),
			new PieceLooks.Choice(new BlockPos(20, 64, 5), Rotation.CLOCKWISE_90, "aliveworkplace:inn", "taiga", "spruce"))));
		return tag;
	}

	private static VillageHallBlockEntity load(GameTestHelper helper, BlockPos pos, BlockState state, CompoundTag tag) {
		VillageHallBlockEntity e = new VillageHallBlockEntity(pos, state);
		e.loadWithComponents(tag, helper.getLevel().registryAccess());
		return e;
	}

	private static List<String> differences(VillageHallBlockEntity want, VillageHallBlockEntity got) {
		List<String> out = new ArrayList<>();
		if (!got.edicts().equals(want.edicts())) out.add("edicts " + got.edicts());
		if (!got.guilds().equals(want.guilds())) out.add("guilds " + got.guilds());
		if (!got.reforms().equals(want.reforms())) out.add("reforms " + got.reforms());
		if (!got.services().equals(want.services())) out.add("services " + got.services());
		if (!got.pieceLooks().equals(want.pieceLooks())) out.add("piece_looks " + got.pieceLooks());
		if (!got.plan().zones().equals(want.plan().zones())) out.add("plan zones " + got.plan().zones());
		if (!got.research().levels().equals(want.research().levels())) out.add("research " + got.research().levels());
		return out;
	}

	/** A clean hall saved, loaded and saved again writes the same seven tags, and they load the same. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaACleanHallSavesTheSameTagsAfterALoad(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		BlockState state = ModBlocks.VILLAGE_HALL.defaultBlockState();
		CompoundTag first = full(helper, pos, state);
		VillageHallBlockEntity a = load(helper, pos, state, first);
		helper.assertTrue(a.edicts().size() == 2 && a.guilds().size() == 2 && a.reforms().size() == 2 && a.services().size() == 2
			&& a.pieceLooks().size() == 2 && a.plan().zones().size() == 2 && a.research().levels().size() == 2, "set-up: the clean load lost something");
		CompoundTag second = a.saveWithoutMetadata(helper.getLevel().registryAccess());
		List<String> changed = new ArrayList<>();
		for (String key : List.of("edicts", "guilds", "reforms", "services", "piece_looks", "plan", "research")) {
			if (!java.util.Objects.equals(first.get(key), second.get(key))) {
				changed.add(key + ": " + first.get(key) + " -> " + second.get(key));
			}
		}
		helper.assertTrue(changed.isEmpty(), "a clean hall writes different tags after a load: " + changed);
		List<String> diff = differences(a, load(helper, pos, state, second));
		helper.assertTrue(diff.isEmpty(), "the second save loads differently: " + diff);
		helper.succeed();
	}

	/**
	 * One empty entry first in each of the five lists and the plan's zones, and one research level that isn't a number:
	 * the hall loads the good entries; saved again it writes exactly 2 entries in each list (the damaged one is gone, not
	 * written back), and that save loads the same good entries again.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaAHallLoadedWithDamageWritesBackOnlyTheGoodEntries(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		BlockState state = ModBlocks.VILLAGE_HALL.defaultBlockState();
		CompoundTag clean = full(helper, pos, state);
		VillageHallBlockEntity want = load(helper, pos, state, clean);
		CompoundTag hurt = clean.copy();
		for (String key : LISTS) {
			((ListTag) hurt.get(key)).add(0, new CompoundTag());
		}
		((ListTag) hurt.getCompound("plan").get("zones")).add(0, new CompoundTag());
		hurt.getCompound("research").getCompound("levels").put("scholars/broken", StringTag.valueOf("not a number"));
		VillageHallBlockEntity got = load(helper, pos, state, hurt);
		List<String> diff = differences(want, got);
		helper.assertTrue(diff.isEmpty(), "with one damaged entry in each list the hall loaded: " + diff);
		CompoundTag again = got.saveWithoutMetadata(helper.getLevel().registryAccess());
		List<String> sizes = new ArrayList<>();
		for (String key : LISTS) {
			int n = again.get(key) instanceof ListTag l ? l.size() : -1;
			if (n != 2) {
				sizes.add(key + " " + n);
			}
		}
		int zones = again.getCompound("plan").get("zones") instanceof ListTag l ? l.size() : -1;
		if (zones != 2) {
			sizes.add("plan zones " + zones);
		}
		int levels = again.getCompound("research").getCompound("levels").size();
		if (levels != 2) {
			sizes.add("research levels " + levels);
		}
		helper.assertTrue(sizes.isEmpty(), "saved again after the damaged load, the hall wrote other than the 2 good entries: " + sizes);
		List<String> reload = differences(want, load(helper, pos, state, again));
		helper.assertTrue(reload.isEmpty(), "that save loads differently: " + reload);
		helper.succeed();
	}

	/**
	 * Each list saved as the wrong kind of tag (a string, a number, a compound, a list of strings), the plan as a string
	 * and the research as a number: the hall loads with those empty and keeps its owner.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaAListSavedAsTheWrongKindOfTagLoadsEmptyAndTheHallKeepsTheRest(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		BlockState state = ModBlocks.VILLAGE_HALL.defaultBlockState();
		CompoundTag tag = full(helper, pos, state);
		tag.put("edicts", StringTag.valueOf("long_shifts"));
		tag.put("guilds", IntTag.valueOf(7));
		tag.put("reforms", new CompoundTag());
		ListTag strings = new ListTag();
		strings.add(StringTag.valueOf("inn"));
		tag.put("services", strings);
		tag.put("piece_looks", StringTag.valueOf(""));
		tag.put("plan", StringTag.valueOf("plan"));
		tag.put("research", IntTag.valueOf(3));
		VillageHallBlockEntity got = load(helper, pos, state, tag);
		helper.assertTrue(got.edicts().isEmpty() && got.guilds().isEmpty() && got.reforms().isEmpty() && got.services().isEmpty()
			&& got.pieceLooks().isEmpty() && got.plan().zones().isEmpty() && got.research().levels().isEmpty(),
			"wrong kinds of tag loaded as something: " + List.of(got.edicts(), got.guilds(), got.reforms(), got.services(), got.pieceLooks(),
				got.plan().zones(), got.research().levels()));
		helper.assertTrue(new UUID(0xabcL, 0xdefL).equals(got.owner()) && "Ana".equals(got.ownerName()),
			"the hall lost its owner over its damaged lists: " + got.owner() + " " + got.ownerName());
		helper.succeed();
	}
}
