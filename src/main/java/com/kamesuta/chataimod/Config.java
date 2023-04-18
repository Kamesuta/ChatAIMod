package com.kamesuta.chataimod;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;
import net.minecraftforge.fml.common.Mod;

import java.io.File;

@Mod("chataimod")
public class Config {
    public static ForgeConfigSpec CLIENT_CONFIG;

    // バックエンドのIPアドレス
    public static ForgeConfigSpec.ConfigValue<String> BACKEND_IP;
    // バックエンドのポート番号
    public static IntValue BACKEND_PORT;

    static {
        Builder builder = new ForgeConfigSpec.Builder();

        BACKEND_IP = builder.comment("バックエンドIP").define("ip", "localhost");
        BACKEND_PORT = builder.comment("バックエンドPort").defineInRange("port", 12345, 0, 65535);

        CLIENT_CONFIG = builder.build();
    }

    // コンフィグの読み込み
    public static void loadConfig(String path) {
        CommentedFileConfig file = CommentedFileConfig.builder(new File(path)).sync().autosave()
                .preserveInsertionOrder().writingMode(WritingMode.REPLACE).build();
        file.load();
        CLIENT_CONFIG.setConfig(file);
    }
}
