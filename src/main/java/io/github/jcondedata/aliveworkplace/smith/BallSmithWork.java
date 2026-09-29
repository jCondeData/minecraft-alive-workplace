package io.github.jcondedata.aliveworkplace.smith;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * The Ball Smith's shift: at the Ball Workbench, turn the apricorns and ball metals in the chests nearby
 * into Poké Balls with Cobblemon's own recipes (see {@link BallRecipes}), a batch at a time, and put the
 * balls back in the chests. They take turns between the kinds they can make and stop making a kind once
 * the chests hold {@link #KEEP} of it, so a stock of apricorns isn't all turned into one ball.
 */
public class BallSmithWork extends Behavior<Villager> {
	/** Base ticks one batch takes (faster with levels and Pokémon partners). */
	public static final int CRAFT_TICKS = 120;
	/** A smith stops making a kind of ball when the chests hold this many. */
	public static final int KEEP = 64;
	private static final float SPEED = 0.5f;
	private static final int LOOK_EVERY = 100;

	private enum Phase { WAITING, CRAFTING, STOCKED }

	private final Walker walker = new Walker(SPEED);
	@Nullable
	private BallRecipes.BallRecipe making;
	private int timer;
	private int lookTimer;
	private int next;
	private Phase phase = Phase.WAITING;

	public BallSmithWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return !villager.isSleeping() && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		making = null;
		lookTimer = 0;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos bench = Builders.benchPos(villager).orElse(null);
		if (bench == null) {
			return;
		}
		if (making == null) {
			status(villager, phase, null);
			if (--lookTimer > 0) {
				return;
			}
			lookTimer = LOOK_EVERY;
			making = choose(level, villager, bench);
			if (making == null) {
				return;
			}
			timer = Math.max(20, BuilderLevels.delay(CRAFT_TICKS, villager));
		}
		status(villager, Phase.CRAFTING, making);
		if (!walker.walkTo(level, villager, bench, 2.5)) {
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(bench));
		if (--timer % 15 == 0) {
			villager.swing(InteractionHand.MAIN_HAND);
			level.playSound(null, bench, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.4f, 1.3f + level.random.nextFloat() * 0.2f);
		}
		if (timer > 0) {
			return;
		}
		craft(level, villager, bench, making);
		making = null;
		lookTimer = 0;
	}

	/** The next kind of ball to make (taking turns), or null if nothing can be made now. */
	@Nullable
	private BallRecipes.BallRecipe choose(ServerLevel level, Villager villager, BlockPos bench) {
		List<BlockPos> chests = SupplyContainers.find(level, bench, null);
		Map<Item, Long> stock = SupplyContainers.contents(level, chests);
		List<BallRecipes.BallRecipe> recipes = BallRecipes.all(level);
		int tier = BuilderLevels.level(villager);
		boolean anyStocked = false;
		for (int i = 0; i < recipes.size(); i++) {
			BallRecipes.BallRecipe recipe = recipes.get((next + i) % recipes.size());
			if (recipe.tier() > tier || !BallSmiths.wants(villager, recipe.result()) || BallRecipes.plan(stock, recipe) == null) {
				continue;
			}
			if (stock.getOrDefault(recipe.result().getItem(), 0L) >= KEEP) {
				anyStocked = true;
				continue;
			}
			next = (next + i + 1) % recipes.size();
			phase = Phase.WAITING;
			return recipe;
		}
		phase = anyStocked ? Phase.STOCKED : Phase.WAITING;
		return null;
	}

	/** Takes the ingredients (if they're still there) and puts the balls in the chests. */
	static boolean craft(ServerLevel level, Villager villager, BlockPos bench, BallRecipes.BallRecipe recipe) {
		List<BlockPos> chests = SupplyContainers.find(level, bench, null);
		Map<Item, Integer> take = BallRecipes.plan(SupplyContainers.contents(level, chests), recipe);
		if (take == null) {
			return false;
		}
		take.forEach((item, count) -> SupplyContainers.extract(level, chests, item, count));
		ItemStack balls = recipe.result().copy();
		int made = balls.getCount();
		ItemStack rest = SupplyContainers.insert(level, chests, balls);
		if (!rest.isEmpty()) {
			Block.popResource(level, bench.above(), rest);
		}
		level.playSound(null, bench, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.3f, 1.6f);
		ModAttachments.BALLS_MADE.set(villager, ModAttachments.BALLS_MADE.getOrElse(villager, 0) + made);
		BuilderLevels.addXp(level, villager, recipe.tier(), null);
		return true;
	}

	private static void status(Villager villager, Phase phase, @Nullable BallRecipes.BallRecipe making) {
		Component title = Component.translatable("message.aliveworkplace.ball_smith.title", ModAttachments.BALLS_MADE.getOrElse(villager, 0));
		Component line = making != null
			? Component.translatable("message.aliveworkplace.ball_smith.state.crafting", making.result().getHoverName()).withStyle(ChatFormatting.GRAY)
			: Component.translatable("message.aliveworkplace.ball_smith.state." + phase.name().toLowerCase())
				.withStyle(phase == Phase.WAITING ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
		WorkerStatus.set(villager, title, -1f, line);
	}
}
