package com.an.llm.connector.gateway.util;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DebugImageSaver {

    private static final String ROOT = "debug-images";

    private DebugImageSaver() {}

    public static void save(BufferedImage image, String stage) {
        try {
            File dir = new File(ROOT);

            if (!dir.exists()) {
                dir.mkdirs();
            }

            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));

            File output = new File(
                    dir,
                    timestamp + "_" + stage + ".png"
            );

            ImageIO.write(image, "png", output);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}