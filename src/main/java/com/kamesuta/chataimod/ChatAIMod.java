package com.kamesuta.chataimod;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
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
    private static String requestAI(String name, String text) throws Exception {
        String hostname = Config.BACKEND_IP.get();
        //String hostname = "localhost";// Pythonサーバーのホスト名
        int port = Config.BACKEND_PORT.get();
        ; // Pythonサーバーのポート番号

        Socket socket = new Socket(hostname, port);

        // データの送信
        OutputStream output = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
        writer.println(name + "\0" + text);

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
    private static void requestAIAsync(String name, String text, Consumer<String> callback) {
        executor.submit(() -> {
            try {
                // AIを呼び出す
                String result = requestAI(name, text);
                // コールバック
                callback.accept(result);
            } catch (Exception e) {
                LOGGER.error("AI通信エラー", e);
            }
        });
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        // do something when the server starts
        LOGGER.info(event.getMessage().getString());

        // チャットが来たらAIを呼び出す
        // チャットは「<Kamesuta> aaa」のような形式なので、正規表現で抽出する
        String text = event.getMessage().getString();
        Matcher matcher = REGEX.matcher(text);
        if (matcher.matches()) {
            // 正規表現でグループ抽出
            String name = matcher.group(1);
            String message = matcher.group(2);

            // AIを呼び出す
            requestAIAsync(name, message, (result) -> {
                // チャットコンポーネント生成
                StringTextComponent component = new StringTextComponent(" -> " + result);

                // スタイルを設定
                component.setStyle(component.getStyle()
                        // クリックイベントを設定
                        .setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, result))
                        // ホバーイベントを設定
                        .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("クリックでチャット欄に挿入")))
                );

                // AIからの応答をチャットに表示する
                Minecraft.getInstance().ingameGUI.getChatGUI().printChatMessage(component);
            });
        }
    }
}
