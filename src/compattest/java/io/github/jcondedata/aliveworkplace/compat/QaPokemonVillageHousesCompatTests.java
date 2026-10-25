package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane d, 2026-10-25) for ROADMAP 28.15 across all 25 templates (the placing tests cover one style of each house):
 * each of the five Pokémon jobs' houses in each of the five village styles has exactly one job block, and it is a
 * block that job works at; exactly one villager, already in that job; no structure void (B5) and no lava.
 */
public class QaPokemonVillageHousesCompatTests implements FabricGameTest {
	static final List<String> STYLES = List.of("plains", "desert", "savanna", "snowy", "taiga");
	static final Map<String, String> JOBS = Map.of("pokemon_center", "aliveworkplace:nurse", "camp_kitchen", "aliveworkplace:camp_cook",
		"berry_nursery", "aliveworkplace:berry_breeder", "daycare", "aliveworkplace:daycare_keeper", "gem_grotto", "aliveworkplace:gem_grower");

	//$ gametest 'CompatGameTests.AREA'
	@GameTest(template = CompatGameTests.AREA)
	public void everyStyleOfEveryPokemonHouseHasOneJobBlockAndItsWorker(GameTestHelper helper) {
		var level = helper.getLevel();
		var resources = level.getServer().getResourceManager();
		var blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
		List<String> wrong = new ArrayList<>();
		for (String style : STYLES) {
			for (var house : JOBS.entrySet()) {
				String name = style + "_" + house.getKey();
				ResourceLocation file = AliveWorkplace.id("structure/village/" + name + ".nbt");
				CompoundTag nbt;
				try (var in = resources.getResourceOrThrow(file).open()) {
					nbt = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
				} catch (java.io.IOException e) {
					wrong.add(name + ": no template (" + e.getMessage() + ")");
					continue;
				}
				ResourceLocation job = ResourceLocation.parse(house.getValue());
				var profession = BuiltInRegistries.VILLAGER_PROFESSION.get(job);
				ListTag palette = nbt.getList("palette", Tag.TAG_COMPOUND);
				List<BlockState> states = new ArrayList<>();
				for (int i = 0; i < palette.size(); i++) {
					states.add(NbtUtils.readBlockState(blocks, palette.getCompound(i)));
				}
				int sites = 0;
				int others = 0;
				ListTag placed = nbt.getList("blocks", Tag.TAG_COMPOUND);
				for (int i = 0; i < placed.size(); i++) {
					BlockState state = states.get(placed.getCompound(i).getInt("state"));
					if (state.is(Blocks.STRUCTURE_VOID)) {
						wrong.add(name + ": a structure void");
					}
					if (state.is(Blocks.LAVA)) {
						wrong.add(name + ": lava");
					}
					var poi = PoiTypes.forState(state);
					if (poi.isPresent() && BuiltInRegistries.VILLAGER_PROFESSION.stream().anyMatch(p -> p.heldJobSite().test(poi.get()))) {
						if (profession.heldJobSite().test(poi.get())) {
							sites++;
						} else {
							others++;
						}
					}
				}
				if (sites != 1 || others != 0) {
					wrong.add(name + ": " + sites + " of its job's blocks and " + others + " other job blocks");
				}
				int villagers = 0;
				int inJob = 0;
				ListTag entities = nbt.getList("entities", Tag.TAG_COMPOUND);
				for (int i = 0; i < entities.size(); i++) {
					CompoundTag e = entities.getCompound(i).getCompound("nbt");
					if (e.getString("id").equals("minecraft:villager")) {
						villagers++;
						if (e.getCompound("VillagerData").getString("profession").equals(job.toString())) {
							inJob++;
						}
					}
				}
				if (villagers != 1 || inJob != 1) {
					wrong.add(name + ": " + villagers + " villagers, " + inJob + " of them " + job);
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "Pokémon village houses that aren't as 28.15 says: " + wrong);
		helper.succeed();
	}
}
