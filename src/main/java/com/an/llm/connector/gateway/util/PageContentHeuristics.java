package com.an.llm.connector.gateway.util;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

@Slf4j
public class PageContentHeuristics {
    private static final double LOW_CONTENT_THRESHOLD = 0.03;

    public static boolean isLikelyBlankOrStampOnly(byte[] pageBytes) {
        log.info("Preparing page ink level check.");
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(pageBytes));

            if (image == null) {
                return false;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            long darkPixels = 0L;
            long totalPixels = (long) width * height;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = image.getRGB(x, y);

                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;

                    int gray = (r + g + b) / 3;

                    if (gray < 245) {
                        darkPixels++;
                    }
                }
            }

            double inkRatio = (double) darkPixels / totalPixels;
            return inkRatio < LOW_CONTENT_THRESHOLD;
        } catch (Exception e) {
            log.error("Error while detecting page ink level.",e);
            return false;
        }
    }
}
