package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Butchers look after the herd around their smoker. */
public class HerderGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos SMOKER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/** Empty buckets in the chest come back full of milk, and an egg lying about ends up in the chest too. */
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "herder_milk")
	public void butcherMilksTheCowAndPicksUpEggs(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		butcher(helper);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.BUCKET, 2));
		Cow cow = helper.spawn(EntityType.COW, new BlockPos(9, 2, 9));
		cow.setNoAi(true);
		BlockPos egg = helper.absolutePos(new BlockPos(6, 2, 10));
		helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(), egg.getX() + 0.5, egg.getY(), egg.getZ() + 0.5, new ItemStack(Items.EGG)));
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(Items.MILK_BUCKET) == 2, "milk: " + chest.countItem(Items.MILK_BUCKET));
			helper.assertTrue(chest.countItem(Items.EGG) == 1, "eggs: " + chest.countItem(Items.EGG));
		});
	}

	/** A hired butcher keeps the flock at ten grown chickens: the rest go for meat (never the named one). */
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "herder_cull")
	public void hiredButcherKeepsTheHerdInCheck(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Villager butcher = butcher(helper);
		ModAttachments.BUILDER_EMPLOYER.set(butcher, new Employer(UUID.randomUUID(), "Al"));
		List<Chicken> chickens = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			Chicken chicken = helper.spawn(EntityType.CHICKEN, new BlockPos(7 + i % 4, 2, 7 + i / 4));
			chicken.setNoAi(true);
			chickens.add(chicken);
		}
		chickens.get(0).setCustomName(Component.literal("Clucky"));
		Container chest = helper.getBlockEntity(CHEST);
		helper.succeedWhen(() -> {
			long alive = chickens.stream().filter(Chicken::isAlive).count();
			helper.assertTrue(alive == 10, "chickens left: " + alive);
			helper.assertTrue(chickens.get(0).isAlive(), "the named chicken went");
			helper.assertTrue(chest.countItem(Items.CHICKEN) + chest.countItem(Items.COOKED_CHICKEN) == 2, "chicken in the chest: " + chest.countItem(Items.CHICKEN));
		});
	}

	private static Villager butcher(GameTestHelper helper) {
		helper.setBlock(SMOKER, Blocks.SMOKER);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager butcher = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), butcher, helper.absolutePos(SMOKER), PoiTypes.BUTCHER, VillagerProfession.BUTCHER);
		return butcher;
	}
}
