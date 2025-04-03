package net.phyrobyte.commands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "homeland", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TPACommand {

    private static final Map<UUID, UUID> pendingTpa = new HashMap<>();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tpa")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer requester = source.getPlayer();
                                    ServerPlayer target = EntityArgument.getPlayer(context, "target");

                                    if (requester.getUUID().equals(target.getUUID())) {
                                        requester.sendSystemMessage(Component.literal("You can't /tpa to yourself."));
                                        return 0;
                                    }

                                    pendingTpa.put(target.getUUID(), requester.getUUID());

                                    requester.sendSystemMessage(Component.literal("TPA request sent to " + target.getName().getString()).withStyle(s -> s.withColor(0x55FF55)));
                                    target.sendSystemMessage(Component.literal(requester.getName().getString() + " wants to teleport to you. Use /tpaccept to allow.").withStyle(s -> s.withColor(0xAAAAFF)));

                                    return 1;
                                })
                        )
        );

        event.getDispatcher().register(
                Commands.literal("tpaccept")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerPlayer accepter = source.getPlayer();
                            UUID targetId = accepter.getUUID();

                            if (!pendingTpa.containsKey(targetId)) {
                                accepter.sendSystemMessage(Component.literal("You have no TPA requests.").withStyle(s -> s.withColor(0xFF5555)));
                                return 0;
                            }

                            UUID requesterId = pendingTpa.remove(targetId);
                            ServerPlayer requester = accepter.getServer().getPlayerList().getPlayer(requesterId);

                            if (requester == null) {
                                accepter.sendSystemMessage(Component.literal("The player who requested TPA is no longer online."));
                                return 0;
                            }

                            requester.teleportTo(
                                    accepter.serverLevel(),
                                    accepter.getX(),
                                    accepter.getY(),
                                    accepter.getZ(),
                                    accepter.getYRot(),
                                    accepter.getXRot()
                            );

                            accepter.sendSystemMessage(Component.literal("You accepted the teleport request from " + requester.getName().getString()).withStyle(s -> s.withColor(0x55FF55)));
                            requester.sendSystemMessage(Component.literal("Teleporting to " + accepter.getName().getString()).withStyle(s -> s.withColor(0x55FF55)));

                            return 1;
                        })
        );

        event.getDispatcher().register(
                Commands.literal("tpdeny")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerPlayer denier = source.getPlayer();
                            UUID targetId = denier.getUUID();

                            if (!pendingTpa.containsKey(targetId)) {
                                denier.sendSystemMessage(Component.literal("You have no TPA requests to deny.").withStyle(s -> s.withColor(0xFF5555)));
                                return 0;
                            }

                            UUID requesterId = pendingTpa.remove(targetId);
                            ServerPlayer requester = denier.getServer().getPlayerList().getPlayer(requesterId);

                            if (requester != null) {
                                requester.sendSystemMessage(Component.literal(denier.getName().getString() + " denied your teleport request.").withStyle(s -> s.withColor(0xFF5555)));
                            }

                            denier.sendSystemMessage(Component.literal("Teleport request denied.").withStyle(s -> s.withColor(0xFF5555)));
                            return 1;
                        })
        );

    }
}
