package com.an.llm.connector.gateway.service.ai;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;

@Service
public class ImagePreprocessorService {

    private static final int MAX_SIZE = 768;
    private static final float QUALITY = 0.75f;

    public byte[] preprocess(MultipartFile file) throws Exception {

        BufferedImage original = readImage(file);

        BufferedImage resized = resizeAndNormalize(original, MAX_SIZE);

        return compressToJpeg(resized, QUALITY);
    }

    private BufferedImage readImage(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream()) {
            return ImageIO.read(is);
        }
    }

    private BufferedImage resizeAndNormalize(BufferedImage original, int maxSize) {

        int width = original.getWidth();
        int height = original.getHeight();

        double scale = Math.min((double) maxSize / width, (double) maxSize / height);

        // no upscale
        if (scale >= 1.0) {
            return normalizeToRGB(original);
        }

        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        Image tmp = original.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);

        BufferedImage output = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = output.createGraphics();

        // 🔥 important for PNG transparency
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, newWidth, newHeight);

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        return output;
    }

    private BufferedImage normalizeToRGB(BufferedImage img) {

        if (img.getType() == BufferedImage.TYPE_INT_RGB) {
            return img;
        }

        BufferedImage rgb = new BufferedImage(
                img.getWidth(),
                img.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g2d = rgb.createGraphics();

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, img.getWidth(), img.getHeight());

        g2d.drawImage(img, 0, 0, null);
        g2d.dispose();

        return rgb;
    }

    private byte[] compressToJpeg(BufferedImage image, float quality) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter writer = writers.next();

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {

            writer.setOutput(ios);

            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality); // 0.7–0.85 sweet spot

            writer.write(null, new IIOImage(image, null, null), param);

        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }
}
