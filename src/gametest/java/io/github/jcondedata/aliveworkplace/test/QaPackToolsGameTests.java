package io.github.jcondedata.aliveworkplace.test;

import com.mojang.brigadier.tree.CommandNode;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (lane E, the pack's test tools): {@code /workplace benchmark}, {@code rehearsal} and {@code soak} fill the world
 * with workers, free materials and copies of saves, so they exist only when the server runs with
 * {@code -Daliveworkplace.benchmark=true} (the pack test's modes), never on a normal server like the owner's, not even
 * for an operator.
 */
public class QaPackToolsGameTests implements FabricGameTest {
	private static final List<String> TOOLS = List.of("benchmark", "rehearsal", "soak");

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePackTestsToolsAreNotOnANormalServer(GameTestHelper helper) {
		helper.assertFalse(Boolean.getBoolean("aliveworkplace.benchmark"), "this test server runs in the pack test's benchmark mode");
		var server = helper.getLevel().getServer();
		CommandNode<CommandSourceStack> workplace = server.getCommands().getDispatcher().getRoot().getChild("workplace");
		for (String tool : TOOLS) {
			helper.assertTrue(workplace == null || workplace.getChild(tool) == null, "/workplace " + tool + " is registered on a normal server");
		}
		CommandSourceStack op = server.createCommandSourceStack().withPermission(4).withSuppressedOutput()
			.withPosition(Vec3.atCenterOf(helper.absolutePos(net.minecraft.core.BlockPos.ZERO)));
		for (String tool : TOOLS) {
			var parsed = server.getCommands().getDispatcher().parse("workplace " + tool + " 1", op);
			helper.assertTrue(parsed.getReader().canRead() || parsed.getContext().getCommand() == null,
				"/workplace " + tool + " 1 parses to a command for an operator");
		}
		helper.succeed();
	}
}
