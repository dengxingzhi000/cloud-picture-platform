package com.cn.cloudpictureplatform.domain.storage;

import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 感知哈希服务 - 计算图片的各种哈希值用于重复检测
 * 支持的哈希算法：
 * 1. SHA-256: 精确内容哈希，用于检测完全相同的文件
 * 2. pHash (Perceptual Hash): 基于DCT的感知哈希，对压缩、亮度变化鲁棒
 * 3. dHash (Difference Hash): 基于梯度差异的哈希，计算快速
 * 4. aHash (Average Hash): 基于平均灰度的哈希，最简单快速
 * 参考: http://www.hackerfactor.com/blog/index.php?/archives/432-Looks-Like-It.html
 */
@Service
public class PerceptualHashService {

    // 哈希图片的标准尺寸
    private static final int HASH_SIZE = 8;
    private static final int RESIZED_WIDTH = 32;
    private static final int RESIZED_HEIGHT = 32;

    /**
     * 计算文件的完整哈希信息
     *
     * @param file 上传的文件
     * @return 包含所有哈希值的计算结果
     */
    public ImageHashResult computeHashes(MultipartFile file) throws IOException {
        byte[] fileBytes = file.getBytes();

        // 计算 SHA-256
        String sha256Hash = computeSha256(fileBytes);

        // 尝试解析图片并计算感知哈希
        Integer width = null;
        Integer height = null;
        String pHash = null;
        String dHash = null;
        String aHash = null;

        try (InputStream imageStream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(imageStream);
            if (image != null) {
                width = image.getWidth();
                height = image.getHeight();

                // 转换为灰度图用于哈希计算
                BufferedImage grayImage = toGrayscale(image);

                // 计算各种感知哈希
                pHash = computePHash(grayImage);
                dHash = computeDHash(grayImage);
                aHash = computeAHash(grayImage);
            }
        } catch (Exception e) {
            // 图片解析失败，只返回 SHA-256
        }

        return ImageHashResult.builder()
                .sha256Hash(sha256Hash)
                .perceptualHash(pHash)
                .diffHash(dHash)
                .avgHash(aHash)
                .width(width)
                .height(height)
                .sizeBytes(file.getSize())
                .contentType(file.getContentType())
                .build();
    }

    /**
     * 计算 SHA-256 哈希
     */
    public String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * 计算 pHash (Perceptual Hash)
     * 基于 DCT (离散余弦变换) 的感知哈希
     * 对图片压缩、亮度变化、色调变化比较鲁棒
     */
    public String computePHash(BufferedImage image) {
        // 1. 缩放到 32x32
        BufferedImage resized = resize(image, RESIZED_WIDTH, RESIZED_HEIGHT);

        // 2. 转换为灰度（如果还不是）
        BufferedImage gray = toGrayscale(resized);

        // 3. 计算 DCT
        double[][] dct = computeDCT(gray);

        // 4. 取左上角 8x8 低频系数（去除 DC 分量）
        double[] lowFreq = new double[HASH_SIZE * HASH_SIZE];
        int idx = 0;
        for (int i = 0; i < HASH_SIZE; i++) {
            for (int j = 0; j < HASH_SIZE; j++) {
                // 跳过 DC 分量 (0,0)
                if (i == 0 && j == 0) continue;
                lowFreq[idx++] = dct[i][j];
            }
        }

        // 5. 计算中值并生成哈希
        double median = median(lowFreq);
        long hash = 0;
        for (int i = 0; i < HASH_SIZE * HASH_SIZE - 1; i++) {
            if (lowFreq[i] >= median) {
                hash |= (1L << i);
            }
        }

        return String.format("%016x", hash);
    }

    /**
     * 计算 dHash (Difference Hash)
     * 基于相邻像素差异的哈希
     * 计算快速，对图片缩放、宽高比变化鲁棒
     */
    public String computeDHash(BufferedImage image) {
        // 1. 缩放到 9x8（宽度比高度多1，用于计算水平差异）
        BufferedImage resized = resize(image, HASH_SIZE + 1, HASH_SIZE);

        // 2. 转换为灰度
        BufferedImage gray = toGrayscale(resized);

        // 3. 计算水平差异并生成哈希
        long hash = 0;
        int bitPos = 0;
        for (int y = 0; y < HASH_SIZE; y++) {
            int prevGray = getGray(gray, 0, y);
            for (int x = 1; x <= HASH_SIZE; x++) {
                int grayVal = getGray(gray, x, y);
                if (grayVal > prevGray) {
                    hash |= (1L << bitPos);
                }
                prevGray = grayVal;
                bitPos++;
            }
        }

        return String.format("%016x", hash);
    }

    /**
     * 计算 aHash (Average Hash)
     * 基于平均灰度值的哈希
     * 最简单快速，但对变化较敏感
     */
    public String computeAHash(BufferedImage image) {
        // 1. 缩放到 8x8
        BufferedImage resized = resize(image, HASH_SIZE, HASH_SIZE);

        // 2. 转换为灰度
        BufferedImage gray = toGrayscale(resized);

        // 3. 计算平均灰度
        int totalGray = 0;
        int[] grays = new int[HASH_SIZE * HASH_SIZE];
        int idx = 0;
        for (int y = 0; y < HASH_SIZE; y++) {
            for (int x = 0; x < HASH_SIZE; x++) {
                int g = getGray(gray, x, y);
                grays[idx++] = g;
                totalGray += g;
            }
        }
        int avgGray = totalGray / (HASH_SIZE * HASH_SIZE);

        // 4. 生成哈希
        long hash = 0;
        for (int i = 0; i < grays.length; i++) {
            if (grays[i] >= avgGray) {
                hash |= (1L << i);
            }
        }

        return String.format("%016x", hash);
    }

    /**
     * 计算两个哈希的汉明距离
     * 距离越小，图片越相似
     */
    public int hammingDistance(String hash1, String hash2) {
        return ImageHashResult.hammingDistance(hash1, hash2);
    }

    /**
     * 判断两张图片是否相似（基于 pHash）
     *
     * @param hash1 第一张图片的 pHash
     * @param hash2 第二张图片的 pHash
     * @param threshold 相似度阈值（汉明距离 <= threshold 认为相似）
     * @return 是否相似
     */
    public boolean isSimilar(String hash1, String hash2, int threshold) {
        return ImageHashResult.isSimilar(hash1, hash2, threshold);
    }

    /**
     * 判断两张图片是否相似（使用默认阈值 10）
     */
    public boolean isSimilar(String hash1, String hash2) {
        return isSimilar(hash1, hash2, 10);
    }

    // ============== 私有辅助方法 ==============

    private BufferedImage toGrayscale(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            return image;
        }
        BufferedImage gray = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        ColorConvertOp op = new ColorConvertOp(
                ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        op.filter(image, gray);
        return gray;
    }

    private BufferedImage resize(BufferedImage image, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(
                java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    private int getGray(BufferedImage image, int x, int y) {
        return image.getRaster().getSample(x, y, 0);
    }

    private double[][] computeDCT(BufferedImage image) {
        int size = image.getWidth();
        double[][] matrix = new double[size][size];

        // 读取像素值
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrix[i][j] = getGray(image, j, i) - 128; // 中心化
            }
        }

        // 简化版 DCT 计算
        return simplifiedDCT(matrix);
    }

    private double[][] simplifiedDCT(double[][] input) {
        int n = input.length;
        double[][] output = new double[n][n];

        // 使用简化版 DCT-II
        for (int u = 0; u < n; u++) {
            for (int v = 0; v < n; v++) {
                double sum = 0;
                for (int x = 0; x < n; x++) {
                    for (int y = 0; y < n; y++) {
                        sum += input[x][y] *
                                Math.cos((2 * x + 1) * u * Math.PI / (2 * n)) *
                                Math.cos((2 * y + 1) * v * Math.PI / (2 * n));
                    }
                }
                double cu = (u == 0) ? 1 / Math.sqrt(2) : 1;
                double cv = (v == 0) ? 1 / Math.sqrt(2) : 1;
                output[u][v] = 2.0 / n * cu * cv * sum;
            }
        }
        return output;
    }

    private double median(double[] values) {
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return (sorted.length % 2 == 0)
                ? (sorted[mid - 1] + sorted[mid]) / 2.0
                : sorted[mid];
    }
}
