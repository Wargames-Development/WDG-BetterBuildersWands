package apis.wgc;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import com.wdg.wgcore.integration.model.ActionAttribution;
import com.wdg.wgcore.integration.model.ActionSourceType;
import com.wdg.wgcore.integration.model.IntegrationDecision;
import com.wdg.wgcore.integration.model.PlacementActionContext;

import java.util.UUID;


import static com.wdg.wgcore.integration.api.WGCoreIntegrationAccess.*;

public class Integrations {
    public static boolean canPlaceBlockWGC(UUID party, World world, Block block, int blockX, int blockY, int blockZ) {
        if (party == null || world == null || block == null) return false;
        // Client highlights are geometric estimates; only the server owns claim data.
        if (world.isRemote) return true;
        IntegrationDecision decision = evaluatePlacement(new PlacementActionContext(
                world, blockX, blockY, blockZ,
                ActionAttribution.directPlayer(party, null, "betterbuilderswands", ActionSourceType.PLAYER),
                Block.blockRegistry.getNameForObject(block)));
        return decision != null && decision.isAllowed();
    }
}
