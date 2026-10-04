package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** ROADMAP 28.10 with the real Cobblemon: the Habitat Keeper's job at a Pasture Block, and her Saccharine logs. */
public class HabitatKeeperCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos PASTURE = new BlockPos(2, 2, 2);

	private static ResourceLocation c(String path) {
		return ResourceLocation.fromNamespaceAndPath("cobblemon", path);
	}

	/** One half of Cobblemon's Pasture Block ({@code part} bottom or top). */
	private static BlockState half(String part) {
		Block pasture = BuiltInRegistries.BLOCK.get(c("pasture"));
		BlockState state = pasture.defaultBlockState();
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals("part")) {
				return with(state, property, part);
			}
		}
		throw new IllegalStateException("cobblemon:pasture has no part property");
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
		return state.setValue(property, property.getValue(value).orElseThrow());
	}

	/**
	 * A honey bottle makes a villager by a Pasture Block a Habitat Keeper (not with the switch off); only the pasture's
	 * lower half is a workstation, and a jobless villager never takes it by itself.
	 */
	//$ gametest_ticks AREA '200'
	@GameTest(template = AREA, timeoutTicks = 200)
	public void aHoneyBottleMakesAHabitatKeeper(GameTestHelper helper) {
		helper.assertTrue(PoiTypes.forState(half("bottom")).map(t -> t.is(ModVillagers.PASTURE_POI)).orElse(false),
			"the pasture's lower half is no workstation: " + PoiTypes.forState(half("bottom")));
		helper.assertTrue(PoiTypes.forState(half("top")).isEmpty(), "the pasture's upper half is a workstation too");
		helper.setBlock(PASTURE, half("bottom"));
		helper.setBlock(PASTURE.above(), half("top"));
		helper.assertTrue(Stations.at(BuiltInRegistries.BLOCK.get(c("pasture"))).map(s -> !s.byItself() && s.has(ModVillagers.HABITAT_KEEPER))
			.orElse(false), "a jobless villager could take the pasture by itself");
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2, 3.5)));
		try {
			HabitatKeepers.ENABLED = false;
			helper.assertTrue(!HabitatKeepers.isHoney(new ItemStack(Items.HONEY_BOTTLE)), "honey still picks a job with the switch off");
			// (honey still names the Nurse's job, which needs a Healing Machine: she gets no job here)
			Stations.choose(player, villager, new ItemStack(Items.HONEY_BOTTLE));
			helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE,
				"switched off, honey still gave a job: " + villager.getVillagerData().getProfession());
		} finally {
			HabitatKeepers.ENABLED = true;
		}
		Stations.choose(player, villager, new ItemStack(Items.HONEY_BOTTLE));
		helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.HABITAT_KEEPER,
			"a honey bottle made a " + villager.getVillagerData().getProfession());
		helper.succeed();
	}

	/** The log scan finds a Saccharine log within 32 blocks, a few thousand blocks a tick, and honey slathers it as Cobblemon does. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSaccharineLogIsFoundAndSlathered(GameTestHelper helper) {
		BlockPos center = helper.absolutePos(PASTURE);
		BlockPos log = helper.absolutePos(new BlockPos(4, 2, 1));
		Block saccharine = BuiltInRegistries.BLOCK.get(c("saccharine_log"));
		helper.getLevel().setBlockAndUpdate(log, saccharine.defaultBlockState());
		List<BlockPos> found = new ArrayList<>();
		int cursor = 0;
		int ticks = 0;
		do {
			cursor = HabitatKeepers.scan(helper.getLevel(), center, cursor, Integer.MAX_VALUE, found);
			ticks++;
		} while (cursor != 0 && ticks < 1000);
		helper.assertTrue(ticks == (HabitatKeepers.scanSize() + HabitatKeepers.SCAN_PER_TICK - 1) / HabitatKeepers.SCAN_PER_TICK,
			"the scan took " + ticks + " ticks, not at most " + HabitatKeepers.SCAN_PER_TICK + " blocks a tick");
		helper.assertTrue(found.equals(List.of(log)), "the scan found " + found + ", not the log at " + log);
		Direction face = HabitatKeepers.faceToward(log, center);
		helper.assertTrue(face == Direction.WEST, "the honey faces " + face + ", not the pasture to the west");
		helper.assertTrue(HabitatKeepers.slather(helper.getLevel(), log, face), "the log wasn't slathered");
		BlockState state = helper.getLevel().getBlockState(log);
		helper.assertTrue(BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(c("saccharine_log_slathered")), "the log is " + state);
		helper.assertTrue(HabitatKeepers.isSlathered(state) && !HabitatKeepers.slather(helper.getLevel(), log, face),
			"a slathered log was slathered again");
		// A second scan remembers it once, slathered or not.
		do {
			cursor = HabitatKeepers.scan(helper.getLevel(), center, cursor, Integer.MAX_VALUE, found);
		} while (cursor != 0);
		helper.assertTrue(found.size() == 1, "the log was remembered " + found.size() + " times");
		helper.succeed();
	}
}
