package com.kamesuta.chataimod;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class ChatAI {
    // チャットの正規表現
    public static final Pattern REGEX = Pattern.compile("^<(.+?)> (.+)$");

    // 途中経過を表示するための任意のチャットID
    public static final int CHAT_ID = 378346;

    // スレッドプール
    private static final ExecutorService executor = Executors.newFixedThreadPool(8);

    // 同期通信でAIを呼び出す
    public static String requestAI(String text) throws Exception {
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
    public static void requestAIAsync(String text, Consumer<String> callback, Consumer<Exception> error) {
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
}
