package com.kamesuta.chataimod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
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

            // リクエスト中を表示
            Minecraft.getInstance().ingameGUI.getChatGUI().printChatMessageWithOptionalDeletion(
                    new StringTextComponent(" -> AIリクエスト中..."), ChatAI.CHAT_ID);

            // AIを呼び出す
            requestAIAsync(inputText.substring(1), (result) -> {
                // チャットコンポーネント生成
                StringTextComponent component = new StringTextComponent(" -> " + result);

                // リクエスト中を削除
                Minecraft.getInstance().ingameGUI.getChatGUI().deleteChatLine(ChatAI.CHAT_ID);

                // スタイルを設定
                component.setStyle(component.getStyle()
                        // クリックイベントを設定
                        .setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, result))
                        // ホバーイベントを設定
                        .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("クリックでチャット欄に挿入")))
                );

                // AIからの応答をチャットに表示する
                Minecraft.getInstance().ingameGUI.getChatGUI().printChatMessage(component);
            }, (e) -> {
                // リクエスト中を削除
                Minecraft.getInstance().ingameGUI.getChatGUI().deleteChatLine(ChatAI.CHAT_ID);

                // エラーを表示
                ChatAIMod.LOGGER.error("AI呼び出しエラー", e);
                Minecraft.getInstance().ingameGUI.getChatGUI().printChatMessage(new StringTextComponent(" -> AI呼び出しエラー"));
            });
        }
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
            ClientPlayerEntity player = Minecraft.getInstance().player;
            if (player != null && inputName.equals(player.getName().getString())) {
                return;
            }

            // メッセージを書き換え
            ITextComponent message = event.getMessage();
            // ボタンを作成
            StringTextComponent button = new StringTextComponent("[+AI]");
            button.setStyle(button.getStyle()
                    .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "#" + text))
                    .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("AIを呼び出す")))
            );
            // メッセージの最後にボタンを追加したメッセージを作成
            ITextComponent newMessage = new StringTextComponent("")
                    .appendSibling(message)
                    .appendSibling(new StringTextComponent(" "))
                    .appendSibling(button);
            // メッセージを書き換え
            event.setMessage(newMessage);
        }
    }
}
