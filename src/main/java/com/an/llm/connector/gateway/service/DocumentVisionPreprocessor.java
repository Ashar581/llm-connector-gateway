package com.an.llm.connector.gateway.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

@Service
public class DocumentVisionPreprocessor {
    private static final int IMAGE_MAX_SIZE = 768;   // for images (Qwen optimal)
    private static final int PDF_MAX_SIZE = 1024;    // for PDF pages (higher fidelity)
    private static final int PDF_DPI = 220;          // PDF rendering quality
    private static final float JPEG_QUALITY = 0.90f; // compression balance


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

            int maxPages = Math.min(totalPages, 5);

            for (int i = 0; i < maxPages; i++) {
                BufferedImage rendered = renderer.renderImageWithDPI(i, PDF_DPI, ImageType.RGB);
                BufferedImage resized = resizeAndNormalize(rendered, PDF_MAX_SIZE);
                pages.add(compressToJpeg(resized, JPEG_QUALITY));
                rendered.flush();
            }
        }

        return pages;
    }

    private byte[] processImage(BufferedImage original) throws Exception {
        BufferedImage resized = resizeAndNormalize(original, IMAGE_MAX_SIZE);
        return compressToJpeg(resized, JPEG_QUALITY);
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

        if (scale >= 1.0) {
            return normalizeToRGB(original);
        }

        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        BufferedImage output = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = output.createGraphics();

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, newWidth, newHeight);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        return output;
    }


    private BufferedImage normalizeToRGB(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_RGB) {
            return img;
        }

        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
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
            param.setCompressionQuality(quality);

            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }
}