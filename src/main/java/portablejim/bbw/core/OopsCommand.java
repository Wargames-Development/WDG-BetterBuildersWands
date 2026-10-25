package portablejim.bbw.core;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;

/** Legacy item-NBT history cannot prove block ownership or material recovery. */
public class OopsCommand extends CommandBase {
    @Override
    public String getCommandName() { return "wandOops"; }

    @Override
    public String getCommandUsage(ICommandSender sender) { return "/wandOops"; }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) {
        // Old item receipts are never authority to delete blocks or mint refunds.
        throw new WrongUsageException("bbw.chat.error.undodisabled");
    }
}
