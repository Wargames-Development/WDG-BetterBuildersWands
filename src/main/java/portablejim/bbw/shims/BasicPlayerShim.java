package portablejim.bbw.shims;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import portablejim.bbw.basics.Point3d;
import vazkii.botania.api.item.IBlockProvider;


/**
 * Wrap a player to provide basic functions.
 */
public class BasicPlayerShim implements IPlayerShim {
    private EntityPlayer player;
    private boolean providersEnabled;

    public BasicPlayerShim(EntityPlayer player) {
        this.player = player;
        this.providersEnabled = areProvidersEnabled();
    }

    private static Block getBlock(ItemStack stack) {
        return Block.getBlockFromItem(stack.getItem());
    }

    private static int getBlockMeta(ItemStack stack) {
        return stack.getHasSubtypes() ? stack.getItemDamage() : 0;
    }

    private static boolean areProvidersEnabled() {
        try {
            boolean disable = new Object() instanceof IBlockProvider;
            return true;
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    public double getReach() {
        if(player instanceof EntityPlayerMP) {
            return ((EntityPlayerMP)player).theItemInWorldManager.getBlockReachDistance();
        }
        return 5F;
    }

    private boolean matches(ItemStack required, ItemStack available) {
        return available != null && required.isItemEqual(available)
                && ItemStack.areItemStackTagsEqual(required, available);
    }

    @Override
    public int countItems(ItemStack required) {
        if (required == null || required.stackSize <= 0 || player.inventory == null) return 0;
        long total = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (matches(required, stack)) {
                total += Math.max(0, stack.stackSize);
            } else if (required.stackSize == 1 && providersEnabled && stack != null
                    && stack.getItem() instanceof IBlockProvider) {
                int count = ((IBlockProvider) stack.getItem()).getBlockCount(
                        player, required, stack, getBlock(required), getBlockMeta(required));
                if (count == -1) return Integer.MAX_VALUE;
                total += Math.max(0, count);
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total / required.stackSize);
    }

    @Override
    public boolean useItem(ItemStack required) {
        if (required == null || required.stackSize <= 0 || player.inventory == null) return false;
        long available = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (matches(required, stack)) available += Math.max(0, stack.stackSize);
        }
        if (available >= required.stackSize) {
            int remaining = required.stackSize;
            for (int i = player.inventory.mainInventory.length - 1; i >= 0 && remaining > 0; i--) {
                ItemStack stack = player.inventory.mainInventory[i];
                if (!matches(required, stack)) continue;
                int consumed = Math.min(remaining, Math.max(0, stack.stackSize));
                remaining -= consumed;
                stack.stackSize -= consumed;
                if (stack.stackSize == 0) player.inventory.setInventorySlotContents(i, null);
            }
            player.inventory.markDirty();
            return true;
        }
        // Providers have no multi-item rollback contract. Never partially debit inventory.
        if (required.stackSize == 1 && providersEnabled) {
            for (ItemStack stack : player.inventory.mainInventory) {
                if (stack != null && stack.getItem() instanceof IBlockProvider
                        && ((IBlockProvider) stack.getItem()).provideBlock(
                        player, required, stack, getBlock(required), getBlockMeta(required), true)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public ItemStack getNextItem(Block block, int meta) {
        for(int i = player.inventory.mainInventory.length - 1; i >= 0; i--) {
            ItemStack inventoryStack = player.inventory.mainInventory[i];

        }

        return null;
    }

    @Override
    public Point3d getPlayerPosition() {
        return new Point3d((int)player.posX, (int)player.posY, (int)player.posZ);
    }

    @Override
    public EntityPlayer getPlayer() {
        return player;
    }

    @Override
    public boolean isCreative() {
        return player.capabilities.isCreativeMode;
    }
}
