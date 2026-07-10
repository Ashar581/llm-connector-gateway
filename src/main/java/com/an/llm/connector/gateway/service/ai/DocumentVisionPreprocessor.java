package com.an.llm.connector.gateway.service.ai;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class DocumentVisionPreprocessor {
    private static final int IMAGE_MAX_SIZE = 1800;
    private static final int PDF_MAX_SIZE = 2400;

    private static final int PDF_DPI = 600;

    private static final float CONTRAST_SCALE = 1.05f;
    private static final float BRIGHTNESS_OFFSET = 5f;

    public List<byte[]> preprocess(MultipartFile file) throws Exception {
        String filename = Objects.requireNonNull(file.getOriginalFilename()).toLowerCase();

        if (isImage(filename)) {
            return List.of(processImage(readImage(file)));
        }

        if (filename.endsWith(".pdf")) {
            return processPdf(file);
        }

        throw new IllegalArgumentException("Unsupported file type");
    }

    private boolean isImage(String filename) {
        return filename.endsWith(".jpg")
                || filename.endsWith(".jpeg")
                || filename.endsWith(".png")
                || filename.endsWith(".webp")
                || filename.endsWith(".bmp");
    }

    private List<byte[]> processPdf(MultipartFile file) throws Exception {

        List<byte[]> pages = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {

            PDFRenderer renderer = new PDFRenderer(document);

            int totalPages = document.getNumberOfPages();
            int maxPages = Math.min(totalPages, 15);

            for (int i = 0; i < maxPages; i++) {

                BufferedImage rendered = renderer.renderImageWithDPI(
                        i,
                        PDF_DPI,
                        ImageType.RGB
                );

                BufferedImage normalized = normalizeToRGB(rendered);

                pages.add(
                        compressToPng(normalized)
                );

                rendered.flush();
                normalized.flush();
            }
        }

        return pages;
    }

    private byte[] processImage(BufferedImage original) throws Exception {
        BufferedImage resized = resizeAndNormalize(original, IMAGE_MAX_SIZE);
        BufferedImage enhanced = normalizeContrast(resized);

        byte[] result = compressToPng(enhanced);

        resized.flush();
        enhanced.flush();

        return result;
    }

    private BufferedImage readImage(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream()) {
            BufferedImage image = ImageIO.read(is);
            if (image == null) {
                throw new IllegalArgumentException("Invalid image file");
            }
            return image;
        }
    }

    /**
     * Resize ONLY when necessary.
     * Never upscale.
     * Use high-quality bicubic interpolation.
     */
    private BufferedImage resizeAndNormalize(BufferedImage original, int maxSize) {
        int width = original.getWidth();
        int height = original.getHeight();

        double scale = Math.min((double) maxSize / width, (double) maxSize / height);

        if (scale >= 1.0) {
            return normalizeToRGB(original);
        }

        int newWidth = (int) Math.round(width * scale);
        int newHeight = (int) Math.round(height * scale);

        BufferedImage output = new BufferedImage(
                newWidth,
                newHeight,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g2d = output.createGraphics();

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, newWidth, newHeight);

        g2d.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
        );

        g2d.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY
        );

        g2d.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        g2d.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        g2d.setRenderingHint(
                RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY
        );

        g2d.setRenderingHint(
                RenderingHints.KEY_COLOR_RENDERING,
                RenderingHints.VALUE_COLOR_RENDER_QUALITY
        );

        g2d.drawImage(
                original,
                0,
                0,
                newWidth,
                newHeight,
                null
        );

        g2d.dispose();

        return output;
    }

    /**
     * Ensure RGB format.
     * Preserve natural appearance for VLMs.
     */
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

    /**
     * Very mild enhancement only.
     * Avoid aggressive OCR-style preprocessing.
     */
    private BufferedImage normalizeContrast(BufferedImage image) {
        BufferedImage output = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        RescaleOp op = new RescaleOp(
                CONTRAST_SCALE,
                BRIGHTNESS_OFFSET,
                null
        );

        op.filter(image, output);

        return output;
    }

    /**
     * PNG preserves text edges much better than JPEG.
     */
    private byte[] compressToPng(BufferedImage image) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);

        return baos.toByteArray();
    }
}