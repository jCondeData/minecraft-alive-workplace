package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonBlocks;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.block.PastureBlock;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.Pastures;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Cobblemon Pasture Blocks (and Cobbleworkers, which is installed here): Pokémon partners and courier routes. */
public class PastureCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	static BlockPos pasture(GameTestHelper helper, BlockPos pos) {
		var part = PastureBlock.Companion.getPART();
		var dry = CobblemonBlocks.PASTURE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false);
		helper.setBlock(pos, dry.setValue(part, PastureBlock.PasturePart.BOTTOM));
		helper.setBlock(pos.above(), dry.setValue(part, PastureBlock.PasturePart.TOP));
		return helper.absolutePos(pos);
	}

	static Pokemon pastured(GameTestHelper helper, BlockPos pasture, ServerPlayer player, String species, Direction side) {
		Pokemon pokemon = PokemonProperties.Companion.parse(species + " level=10", " ", "=").create();
		PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
		pc.add(pokemon);
		PokemonPastureBlockEntity block = (PokemonPastureBlockEntity) helper.getLevel().getBlockEntity(pasture);
		helper.assertTrue(block != null && block.tether(player, pokemon, side), "could not put " + species + " in the pasture");
		return pokemon;
	}

	/** A Machop in a pasture near the Builder's Bench speeds the builder up; a Pikachu there doesn't. */
	@GameTest(template = AREA, timeoutTicks = 200)
	public void pasturedPokemonHelpTheVillagerNearby(GameTestHelper helper) {
		helper.assertTrue(FabricLoader.getInstance().isModLoaded("cobbleworkers"), "Cobbleworkers should be installed for this test");
		ServerLevel level = helper.getLevel();
		BlockPos bench = new BlockPos(2, 2, 2); // y = 1 is this area's floor
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, builder, helper.absolutePos(bench), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		helper.assertTrue(BuilderLevels.delay(100, builder) == 100, "a novice without help takes the full time");

		BlockPos pasture = pasture(helper, new BlockPos(10, 2, 10));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		pastured(helper, pasture, player, "machop", Direction.NORTH);
		pastured(helper, pasture, player, "pikachu", Direction.WEST);

		helper.runAfterDelay(10, () -> {
			Partners.forget(builder);
			var helpers = Partners.helpers(builder);
			helper.assertTrue(helpers.size() == 1 && helpers.get(0).getString().equals("Machop"), "helpers: " + helpers);
			helper.assertTrue(BuilderLevels.delay(100, builder) == 85, "with Machop: " + BuilderLevels.delay(100, builder));
			helper.assertTrue(BuilderLevels.describe(builder).getString().contains("Machop"), "status: " + BuilderLevels.describe(builder).getString());
			helper.succeed();
		});
	}

	/** A courier route from a pasture empties the chests around it (where Cobbleworkers' Pokémon put their finds). */
	@GameTest(template = AREA, timeoutTicks = 1600)
	public void courierHaulsFromAPasture(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos pasture = pasture(helper, new BlockPos(11, 2, 11));
		BlockPos berries = new BlockPos(13, 2, 11);
		BlockPos apricorns = new BlockPos(11, 2, 14);
		BlockPos drop = new BlockPos(2, 2, 6);
		for (BlockPos p : List.of(berries, apricorns, drop)) {
			helper.setBlock(p, Blocks.CHEST);
		}
		((Container) helper.getBlockEntity(berries)).setItem(0, new ItemStack(CobblemonItems.ORAN_BERRY, 20));
		((Container) helper.getBlockEntity(apricorns)).setItem(3, new ItemStack(CobblemonItems.RED_APRICORN, 6));
		helper.assertTrue(Pastures.containers(level, pasture).size() == 2, "containers around the pasture: " + Pastures.containers(level, pasture));

		BlockPos desk = new BlockPos(2, 2, 2);
		helper.setBlock(desk, ModBlocks.POSTAL_DESK);
		Villager postman = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, postman, helper.absolutePos(desk), ModVillagers.POSTAL_DESK_POI, ModVillagers.POSTMAN);
		postman.setAttached(ModAttachments.COURIER_ROUTES, List.of(new RouteData(Optional.of(pasture), Optional.of(helper.absolutePos(drop)), List.of())));
		Container target = helper.getBlockEntity(drop);
		helper.succeedWhen(() -> {
			helper.assertTrue(target.countItem(CobblemonItems.ORAN_BERRY) == 20, target.countItem(CobblemonItems.ORAN_BERRY) + " berries delivered");
			helper.assertTrue(target.countItem(CobblemonItems.RED_APRICORN) == 6, target.countItem(CobblemonItems.RED_APRICORN) + " apricorns delivered");
		});
	}
}
