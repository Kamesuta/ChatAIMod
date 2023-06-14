package com.kamesuta.chataimod;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.*;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.regex.Matcher;

import static com.kamesuta.chataimod.ChatAI.requestAIAsync;

public class ChatAIListener {
    // チャットを送信する際に呼び出される
    @SubscribeEvent
    public void onChatSend(ClientChatEvent event) {
        String inputText = event.getMessage();
        // 「#」で始まるチャットはAIを呼び出す
        if (inputText.startsWith("#")) {
            // チャットをキャンセル
            event.setCanceled(true);

            onRequestAI(inputText);
        }
    }

    /**
     * チャットを消す
     */
    private void deleteMessage() {
        ChatComponent chat = Minecraft.getInstance().gui.getChat();
        chat.allMessages.removeIf(message -> ChatAI.CHAT_ID.equals(message.signature()));
        chat.refreshTrimmedMessage();
    }

    /**
     * AIを呼び出す
     *
     * @param inputText チャットのテキスト
     */
    private void onRequestAI(String inputText) {
        // リクエスト中を表示
        deleteMessage();
        Minecraft.getInstance().gui.getChat().addMessage(
                Component.literal(" -> AIリクエスト中..."), ChatAI.CHAT_ID, GuiMessageTag.system());

        // AIを呼び出す
        requestAIAsync(inputText.substring(1), (result) -> {
            // チャットコンポーネント生成
            MutableComponent component = Component.literal(" -> " + result);

            // リクエスト中を削除
            deleteMessage();

            // スタイルを設定
            component.setStyle(component.getStyle()
                    // クリックイベントを設定
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, result))
                    // ホバーイベントを設定
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("クリックでチャット欄に挿入")))
            );

            // AIからの応答をチャットに表示する
            Minecraft.getInstance().gui.getChat().addMessage(component, null, GuiMessageTag.system());
        }, (e) -> {
            // リクエスト中を削除
            deleteMessage();

            // エラーを表示
            ChatAIMod.LOGGER.error("AI呼び出しエラー", e);
            Minecraft.getInstance().gui.getChat().addMessage(Component.literal(" -> AI呼び出しエラー"), null, GuiMessageTag.system());
        });
    }

    // クライアントコマンドを登録する
    @SubscribeEvent
    public void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        // aiコマンドを登録
        LiteralArgumentBuilder<CommandSourceStack> aiCommand = Commands.literal("ai")
                .requires((commandSource) -> commandSource.hasPermission(0))
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes((commandSource) -> {
                            // aiコマンドを実行
                            String inputText = StringArgumentType.getString(commandSource, "text");
                            onRequestAI(inputText);
                            return 0;
                        })
                );
        event.getDispatcher().register(aiCommand);
    }

    // チャットを受信する際に呼び出される
    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        // チャットが来たらAIを呼び出す
        // チャットは「<Kamesuta> aaa」のような形式なので、正規表現で抽出する
        String text = event.getMessage().getString();
        Matcher matcher = ChatAI.REGEX.matcher(text);
        if (matcher.matches()) {
            // 正規表現でグループ抽出
            String inputName = matcher.group(1);
            String inputText = matcher.group(2);

            // 自分は除外
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && inputName.equals(player.getName().getString())) {
                return;
            }

            // メッセージを書き換え
            Component message = event.getMessage();
            // ボタンを作成
            MutableComponent button = Component.literal("[+AI]")
                    .setStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ai " + text))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("AIを呼び出す")))
                    );
            // メッセージの最後にボタンを追加したメッセージを作成
            Component newMessage = Component.literal("")
                    .append(message)
                    .append(Component.literal(" "))
                    .append(button);
            // メッセージを書き換え
            event.setMessage(newMessage);
        }
    }
}
