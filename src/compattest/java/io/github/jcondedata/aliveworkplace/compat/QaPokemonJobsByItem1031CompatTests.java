package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-d-1031-0408) for B688's other side: B688 keeps the Pokémon jobs from being picked by item without Cobblemon
 * (B688GameTests). With Cobblemon installed (this run) the same sneak-right-clicks by a Training Post must still hire
 * them, each at the post: a book makes a Move Tutor, a gold block a Trainer Leader, a Poké Ball a Trainer.
 */
public class QaPokemonJobsByItem1031CompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos POST = new BlockPos(10, 2, 4);

	private static String job(Villager v) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).toString();
	}

	//$ gametest_ticks_batch AREA '200' '"qa_b688_cobblemon"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_b688_cobblemon")
	public void withCobblemonABookAGoldBlockAndAPokeBallHireAtATrainingPost(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.assertTrue(Stations.available(ModVillagers.TUTOR) && Stations.available(ModVillagers.TRAINER_LEADER),
			"with Cobblemon installed the Move Tutor or the Trainer Leader isn't available");
		Item ball = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball"));
		helper.assertTrue(ball != Items.AIR, "no cobblemon:poke_ball");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<String> wrong = new ArrayList<>();
		try {
			record Row(Item item, VillagerProfession job) {}
			List<Row> rows = List.of(new Row(Items.BOOK, ModVillagers.TUTOR), new Row(Items.GOLD_BLOCK, ModVillagers.TRAINER_LEADER), new Row(ball, ModVillagers.TRAINER));
			for (int i = 0; i < rows.size(); i++) {
				Row row = rows.get(i);
				BlockPos at = POST.offset(0, 0, i * 6); // a post of its own each, far enough apart that only it is in reach
				helper.setBlock(at, ModBlocks.TRAINING_POST);
				BlockPos post = helper.absolutePos(at);
				Villager v = helper.spawn(EntityType.VILLAGER, at.east());
				v.setNoAi(true);
				ItemStack held = new ItemStack(row.item(), 2);
				player.setItemInHand(InteractionHand.MAIN_HAND, held);
				player.setShiftKeyDown(true);
				Stations.choose(player, v, held);
				boolean atPost = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(post::equals).isPresent();
				if (v.getVillagerData().getProfession() != row.job() || !atPost) {
					wrong.add(BuiltInRegistries.ITEM.getKey(row.item()) + " made a " + job(v) + (atPost ? "" : ", not at the Training Post"));
				}
				v.discard();
			}
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}
}
