package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * 50.12 and saves: the Pokémon Center (default look) and its Mountain Lodge look were rebuilt from scratch, and in each
 * Level II the Pasture Block and the Shop Counter moved (default II from (6, 1, 22) and (6, 1, 14) to (9, 1, 19) and
 * (15, 1, 7); Lodge II from (6, 1, 15) and (16, 1, 6) to (10, 1, 19) and (15, 1, 7)). A finished building records no
 * drawing version, and the owner's live server can have a II standing in the old drawing (blueprint_history). Nothing in
 * the mod reads those two blocks at a fixed spot: a villager takes them as job sites by the block itself (Stations). So
 * an old-drawing II, placed from blueprint_history and recorded as finished, still has its Pasture Block and Shop Counter
 * where the old drawing put them, and a villager by each, sneak-clicked with its item, takes that very block: a Poké Ball
 * makes a Pokémon Trader at the old Shop Counter, a honey bottle a Habitat Keeper at the old Pasture Block.
 */
public class OldPokemonCenterDrawingCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos ORIGIN = new BlockPos(2, 2, 2);

	/** The drawing of {@code id} saved as {@code version} in blueprint_history, as a structure template. */
	private static StructureTemplate oldTemplate(ServerLevel level, ResourceLocation id, String version) throws Exception {
		ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "blueprint_history/" + id.getPath() + "/" + version + ".nbt");
		try (InputStream in = level.getServer().getResourceManager().getResource(file)
				.orElseThrow(() -> new GameTestAssertException("no " + file + " shipped")).open()) {
			CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
			StructureTemplate template = new StructureTemplate();
			template.load(Lookup.lookup(BuiltInRegistries.BLOCK), tag);
			return template;
		}
	}

	/** The Pokémon Center II drawn before 50.12 (every release to v0.158.0): Pasture Block (6, 1, 22), Shop Counter (6, 1, 14). */
	//$ gametest_ticks_batch AREA '200' '"oldPokemonCenter2"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "oldPokemonCenter2")
	public void anOldDrawingPokemonCenterIIStillHasItsPastureBlockAndShopCounter(GameTestHelper helper) throws Exception {
		oldDrawing(helper, StarterBlueprints.POKEMON_CENTER_2.id(), "v0.145.0", new BlockPos(6, 1, 22), new BlockPos(6, 1, 14));
	}

	/** The Mountain Lodge II drawn before 50.12: Pasture Block (6, 1, 15), Shop Counter (16, 1, 6). */
	//$ gametest_ticks_batch AREA '200' '"oldPokemonCenterLodge2"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "oldPokemonCenterLodge2")
	public void anOldDrawingLodgeIIStillHasItsPastureBlockAndShopCounter(GameTestHelper helper) throws Exception {
		oldDrawing(helper, StarterBlueprints.POKEMON_CENTER_LODGE_2.id(), "v0.150.0", new BlockPos(6, 1, 15), new BlockPos(16, 1, 6));
	}

	private static void oldDrawing(GameTestHelper helper, ResourceLocation id, String version, BlockPos pasture, BlockPos counter) throws Exception {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(ORIGIN);
		oldTemplate(level, id, version).placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(50_12L), 2);
		BuildSiteManager.get(level).recordFinished(id, new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE),
			UUID.randomUUID());
		Block pastureBlock = BuiltInRegistries.BLOCK.get(ModVillagers.PASTURE_BLOCK);
		helper.assertTrue(pastureBlock != Blocks.AIR, "no " + ModVillagers.PASTURE_BLOCK);
		helper.assertTrue(level.getBlockState(origin.offset(pasture)).is(pastureBlock),
			id + " (" + version + "): no Pasture Block at " + pasture.toShortString() + ": " + level.getBlockState(origin.offset(pasture)));
		helper.assertTrue(level.getBlockState(origin.offset(counter)).is(ModBlocks.SHOP_COUNTER),
			id + " (" + version + "): no Shop Counter at " + counter.toShortString() + ": " + level.getBlockState(origin.offset(counter)));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		takes(helper, player, origin.offset(counter), cobblemon("poke_ball"), ModVillagers.POKEMON_TRADER, id + "'s old Shop Counter");
		takes(helper, player, origin.offset(pasture), Items.HONEY_BOTTLE, ModVillagers.HABITAT_KEEPER, id + "'s old Pasture Block");
		helper.succeed();
	}

	/** A jobless villager standing by {@code station} (absolute), sneak-clicked with {@code item}, takes {@code job} at that very block. */
	private static void takes(GameTestHelper helper, ServerPlayer player, BlockPos station, Item item, VillagerProfession job, String what) {
		ServerLevel level = helper.getLevel();
		BlockPos stand = null;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos p = station.relative(d);
			if (level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
				&& !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) {
				stand = p;
				break;
			}
		}
		helper.assertTrue(stand != null, what + ": no open cell beside it at " + station.toShortString());
		Villager villager = EntityType.VILLAGER.create(level);
		villager.moveTo(Vec3.atBottomCenterOf(stand));
		level.addFreshEntity(villager);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, true, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		player.setShiftKeyDown(false);
		VillagerProfession got = villager.getVillagerData().getProfession();
		helper.assertTrue(got == job, what + ": " + BuiltInRegistries.ITEM.getKey(item) + " gave " + BuiltInRegistries.VILLAGER_PROFESSION.getKey(got)
			+ ", not " + BuiltInRegistries.VILLAGER_PROFESSION.getKey(job));
		Optional<BlockPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
		helper.assertTrue(site.equals(Optional.of(station)), what + ": works at " + site + ", not " + station.toShortString());
	}

	private static Item cobblemon(String path) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", path));
		if (item == Items.AIR) {
			throw new GameTestAssertException("no cobblemon:" + path);
		}
		return item;
	}
}
