package com.fengsheng.handler;

import com.fengsheng.*;
import com.fengsheng.network.ProtoServerChannelHandler;
import com.fengsheng.protos.Errcode;
import com.fengsheng.protos.Fengsheng;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

public class join_room_tos implements ProtoHandler {
    private static final Logger log = Logger.getLogger(join_room_tos.class);

    @Override
    public void handle(HumanPlayer player, GeneratedMessageV3 message) {
        if (player.getGame() != null || player.isLoadingRecord()) {
            log.error("player is already in a room");
            return;
        }
        var pb = (Fengsheng.join_room_tos) message;
        // 客户端版本号不对，直接返回错误码
        if (pb.getVersion() < Config.ClientVersion) {
            player.send(Errcode.error_code_toc.newBuilder()
                    .setCode(Errcode.error_code.client_version_not_match)
                    .addIntParams(Config.ClientVersion).build());
            return;
        }
        String device = pb.getDevice();
        HumanPlayer oldPlayer = Game.deviceCache.get(device);
        if (oldPlayer != null && !oldPlayer.isActive() && oldPlayer.getGame() != null && oldPlayer.getGame().isStarted() && !oldPlayer.getGame().isEnd()) { // 断线重连
            CountDownLatch cd = new CountDownLatch(1);
            GameExecutor.post(oldPlayer.getGame(), () -> {
                ProtoServerChannelHandler.exchangePlayer(oldPlayer, player);
                cd.countDown();
                oldPlayer.reconnect();
            });
            try {
                cd.await();
            } catch (InterruptedException e) {
                log.error("thread " + Thread.currentThread().getId() + " interrupted");
                Thread.currentThread().interrupt();
            }
            return;
        }
        if (pb.getName().getBytes(StandardCharsets.UTF_8).length > 24) {
            player.send(Errcode.error_code_toc.newBuilder()
                    .setCode(Errcode.error_code.name_too_long).build());
            return;
        }
        GeneratedMessageV3 reply;
        synchronized (Game.class) {
            if (Game.GameCache.size() > Config.MaxRoomCount) {
                reply = Errcode.error_code_toc.newBuilder().setCode(Errcode.error_code.no_more_room).build();
            } else {
                player.setDevice(pb.getDevice());
                Game.deviceCache.putIfAbsent(pb.getDevice(), player);
                String playerName = pb.getName();
                player.setPlayerName(playerName.isBlank() ? Player.randPlayerName() : playerName);
                player.setGame(Game.getInstance());
                player.getGame().onPlayerJoinRoom(player);
                var builder = Fengsheng.get_room_info_toc.newBuilder().setMyPosition(player.location());
                for (Player p : player.getGame().getPlayers()) {
                    builder.addNames(p != null ? p.getPlayerName() : "");
                }
                reply = builder.build();
            }
        }
        player.send(reply);
    }
}
