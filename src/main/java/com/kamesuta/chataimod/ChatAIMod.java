package com.kamesuta.chataimod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("chataimod")
public class ChatAIMod {
    // ロガー
    private static final Logger LOGGER = LogManager.getLogger();
    // チャットの正規表現
    public static final Pattern REGEX = Pattern.compile("^<(.+?)> (.+)$");

    // スレッドプール
    private static final ExecutorService executor = Executors.newFixedThreadPool(8);

    // 途中経過を表示するための任意のチャットID
    private static final int CHAT_ID = 378346;

    public ChatAIMod() {
        // クライアント初期化時イベント
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);

        // イベント登録
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        // コンフィグの読み込み
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_CONFIG);
        Config.loadConfig(FMLPaths.CONFIGDIR.get().resolve("chataimod.toml").toString());
    }

    // 同期通信でAIを呼び出す
    private static String requestAI(String text) throws Exception {
        String hostname = Config.BACKEND_IP.get();
        //String hostname = "localhost";// Pythonサーバーのホスト名
        int port = Config.BACKEND_PORT.get();
        ; // Pythonサーバーのポート番号

        Socket socket = new Socket(hostname, port);

        // データの送信
        OutputStream output = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
        writer.println(text);

        // データの受信
        InputStream input = socket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        byte[] response = reader.readLine().getBytes();
        String result = new String(response, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("Pythonからの応答：" + result);
        // ソケットのクローズ
        input.close();
        output.close();
        socket.close();
        return result;
    }

    // 別スレッドでAIを呼び出す
    private static void requestAIAsync(String text, Consumer<String> callback, Consumer<Exception> error) {
        executor.submit(() -> {
            try {
                // AIを呼び出す
                String result = requestAI(text);
                // コールバック
                callback.accept(result);
            } catch (Exception e) {
                // エラー
                error.accept(e);
            }
        });
    }

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
                    new StringTextComponent(" -> AIリクエスト中..."), CHAT_ID);

            // AIを呼び出す
            requestAIAsync(inputText.substring(1), (result) -> {
                // チャットコンポーネント生成
                StringTextComponent component = new StringTextComponent(" -> " + result);

                // リクエスト中を削除
                Minecraft.getInstance().ingameGUI.getChatGUI().deleteChatLine(CHAT_ID);

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
                Minecraft.getInstance().ingameGUI.getChatGUI().deleteChatLine(CHAT_ID);

                // エラーを表示
                LOGGER.error("AI呼び出しエラー", e);
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
        Matcher matcher = REGEX.matcher(text);
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
