package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** TEMPORARY (B24): repeats of the butcher/Pidgey test with a trace. */
public class ZRepeatTests {
	@GameTestGenerator
	public Collection<TestFunction> repeats() {
		List<TestFunction> out = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			int n = i;
			out.add(new TestFunction("rep_" + n, "rep_" + n + "_butcher", CompatGameTests.AREA, 2400, 0L, true, h -> run(h, n)));
		}
		return out;
	}

	static void run(GameTestHelper helper, int n) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		io.github.jcondedata.aliveworkplace.ranch.PokemonChores.forget();
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.SMOKER);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		chest.setItem(0, new ItemStack(Items.BUCKET));
		chest.setItem(1, new ItemStack(Items.BRUSH));
		for (int i = 0; i < 4; i++) {
			chest.setItem(2 + i, new ItemStack(Items.MILK_BUCKET));
		}
		Villager butcher = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, butcher, helper.absolutePos(new BlockPos(2, 2, 2)), net.minecraft.world.entity.ai.village.poi.PoiTypes.BUTCHER,
			net.minecraft.world.entity.npc.VillagerProfession.BUTCHER);
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(10, 2, 10));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Pokemon miltank = PastureCompatTests.pastured(helper, pasture, player, "miltank", Direction.NORTH);
		Pokemon pidgey = PastureCompatTests.pastured(helper, pasture, player, "pidgey", Direction.WEST);
		long[] t = {0};
		helper.onEachTick(() -> {
			if (t[0]++ % 100 != 0) {
				return;
			}
			var p = pidgey.getEntity();
			var m = miltank.getEntity();
			AliveWorkplace.LOG.info("[B24] rep {} t={} butcher={} pidgey={} (d={}) miltank={} tended={} bag={} status={}", n, t[0],
				butcher.position(), p == null ? null : p.position(), p == null ? -1 : Math.sqrt(butcher.distanceToSqr(p)),
				m == null ? null : m.position(), ModAttachments.POKEMON_TENDED.getOrElse(butcher, 0),
				ModAttachments.BUILDER_BAG.getOrCreate(butcher).stacks(), io.github.jcondedata.aliveworkplace.work.WorkerStatus.class.getSimpleName());
		});
		helper.succeedWhen(() -> {
			boolean feathers = false;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				feathers |= chest.getItem(i).is(Items.FEATHER);
			}
			helper.assertTrue(feathers, "no feathers from the Pidgey");
			AliveWorkplace.LOG.info("[B24] rep {} passed at t={}", n, t[0]);
		});
	}
}
