package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 21.1a with Cobblemon installed: the plan's Pokémon jobs start at their shared block with their item (a Ball Smith
 * at a smithing table with an apricorn, a Pokémon Trader at a Shop Counter with a Poké Ball, a Fossil Scientist at a
 * Training Post with a fossil), and the Training Post's own job, the Trainer, takes a jobless villager by itself.
 */
public class StationsCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	/** Helper y = 1 is the floor here too. */
	private static final BlockPos STATION = new BlockPos(3, 2, 3);
	private static final BlockPos STANDING = new BlockPos(4, 2, 4);

	private static Item cobblemon(String path) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", path));
		if (item == net.minecraft.world.item.Items.AIR) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("no cobblemon:" + path);
		}
		return item;
	}

	/** A client's sneak-right-click on {@code villager} holding {@code stack}, through the server's packet handler. */
	private static void sneakClick(ServerPlayer player, Villager villager, ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, true, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		player.setShiftKeyDown(false);
	}

	private static Optional<BlockPos> site(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
	}

	private static String name(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).toString();
	}

	private record Row(Block block, Item item, VillagerProfession job) {
	}

	/** Each Pokémon job of the plan: a jobless villager by the block, sneak-right-clicked with the item, takes it there. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void thePokemonJobsStartFromTheirBlockAndItem(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		List<Row> rows = List.of(
			new Row(Blocks.SMITHING_TABLE, cobblemon("red_apricorn"), ModVillagers.BALL_SMITH),
			new Row(ModBlocks.SHOP_COUNTER, cobblemon("poke_ball"), ModVillagers.POKEMON_TRADER),
			new Row(ModBlocks.TRAINING_POST, cobblemon("dome_fossil"), ModVillagers.FOSSIL_SCIENTIST),
			new Row(ModBlocks.TRAINING_POST, net.minecraft.world.item.Items.GOLD_BLOCK, ModVillagers.TRAINER_LEADER),
			new Row(ModBlocks.TRAINING_POST, net.minecraft.world.item.Items.BOOK, ModVillagers.TUTOR));
		for (Row row : rows) {
			helper.setBlock(STATION, row.block());
			Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
			sneakClick(player, villager, new ItemStack(row.item()));
			String what = BuiltInRegistries.ITEM.getKey(row.item()) + " at " + BuiltInRegistries.BLOCK.getKey(row.block());
			helper.assertTrue(villager.getVillagerData().getProfession() == row.job(),
				what + " gave " + name(villager.getVillagerData().getProfession()) + ", not " + name(row.job()));
			helper.assertTrue(site(villager).equals(Optional.of(helper.absolutePos(STATION))), what + ": works at " + site(villager));
			villager.discard();
			helper.setBlock(STATION, Blocks.AIR);
		}
		helper.succeed();
	}

	/**
	 * A Poké Ball picks two jobs (Trainer at a Training Post, Pokémon Trader at a Shop Counter): the block the villager
	 * works at decides, so a Move Tutor becomes a Trainer at their post, and back with a book.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aPokeBallMakesATutorATrainerAtTheirPost(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		helper.setBlock(STATION, ModBlocks.TRAINING_POST);
		helper.setBlock(new BlockPos(6, 2, 5), ModBlocks.SHOP_COUNTER);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		sneakClick(player, villager, new ItemStack(net.minecraft.world.item.Items.BOOK));
		helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.TUTOR, "a book gave " + name(villager.getVillagerData().getProfession()));
		sneakClick(player, villager, new ItemStack(cobblemon("poke_ball")));
		helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.TRAINER, "a Poké Ball gave the tutor " + name(villager.getVillagerData().getProfession()));
		helper.assertTrue(site(villager).equals(Optional.of(helper.absolutePos(STATION))), "the trainer works at " + site(villager));
		helper.succeed();
	}

	/** The Training Post's own job takes a jobless villager by itself: a Trainer, not the Leader, Tutor or Scientist. */
	//$ gametest_ticks_batch AREA '1200' '"stationsCompatTrainer"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "stationsCompatTrainer")
	public void aTrainingPostTakesATrainerByItself(GameTestHelper helper) {
		helper.setDayTime(2000);
		for (var e : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(48))) {
			e.discard();
		}
		helper.setBlock(STATION, ModBlocks.TRAINING_POST);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.TRAINER, "by the Training Post: " + name(villager.getVillagerData().getProfession()));
			helper.assertTrue(site(villager).equals(Optional.of(helper.absolutePos(STATION))), "the trainer works at " + site(villager));
		});
	}
}
