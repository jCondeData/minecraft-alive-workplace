package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane E, 2026-10-25), with Cobblemon, from the builder's promise (wiki builder.md, "Builders never copy what is
 * inside containers"; BlockEntityData: no free mobs are copied), not from the code. A player's
 * Pasture Block with their Machop let out in it is scanned into a blueprint, and a real builder builds that blueprint
 * from a chest holding exactly what its material list asks. The pasture built is an empty one: none of the player's
 * Pokémon tethered to it, and no second Machop let out by it.
 */
public class QaPastureCopyCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos SRC = new BlockPos(20, 2, 20);
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);

	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_pasture_copy"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_pasture_copy")
	public void aScannedPastureWithAPokemonInItIsBuiltEmpty(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos pasture = PastureCompatTests.pasture(helper, SRC);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PastureCompatTests.pastured(helper, pasture, player, "machop", Direction.NORTH);
		PokemonPastureBlockEntity scanned = (PokemonPastureBlockEntity) level.getBlockEntity(pasture);
		helper.assertTrue(scanned.getTetheredPokemon().size() == 1, "the scanned pasture holds " + scanned.getTetheredPokemon().size() + " Pokémon, not 1");
		net.minecraft.nbt.CompoundTag saved = scanned.saveWithoutMetadata(level.registryAccess());
		helper.assertTrue(saved.getList("TetherPokemon", net.minecraft.nbt.Tag.TAG_COMPOUND).size() == 1,
			"the scanned pasture's data doesn't hold its Machop (so the scan would test nothing): " + saved.getAllKeys());
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat", "qa_pasture_copy_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, pasture, new Vec3i(1, 2, 1), false, Blocks.STRUCTURE_VOID);
		int machopsBefore = machops(level, helper);

		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		BuildPlan plan = site.plan(level);
		Map<Item, Integer> asked = plan.materials();
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : asked.entrySet()) {
			chest.setItem(slot++, new ItemStack(e.getKey(), e.getValue()));
		}
		String list = asked.entrySet().stream().map(e -> e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).toList().toString();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			BlockPos at = helper.absolutePos(ORIGIN);
			helper.assertTrue(level.getBlockEntity(at) instanceof PokemonPastureBlockEntity, "no pasture built from " + list + ": " + level.getBlockState(at));
			PokemonPastureBlockEntity built = (PokemonPastureBlockEntity) level.getBlockEntity(at);
			helper.assertTrue(built.getTetheredPokemon().isEmpty(), "the built pasture holds the scanned pasture's " + built.getTetheredPokemon().size() + " Pokémon");
			helper.assertTrue(machops(level, helper) <= machopsBefore, "the built pasture let out another Machop: " + machops(level, helper) + " now, " + machopsBefore + " before");
		});
	}

	private static int machops(ServerLevel level, GameTestHelper helper) {
		AABB area = helper.getBounds().inflate(8);
		return level.getEntitiesOfClass(PokemonEntity.class, area, e -> e.isAlive() && e.getPokemon().getSpecies().getName().equalsIgnoreCase("machop")).size();
	}
}
