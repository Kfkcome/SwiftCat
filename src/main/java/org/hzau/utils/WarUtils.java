package org.hzau.utils;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class WarUtils {
    public static void extractWars() throws IOException {
        String parentsPath = "./webapps";
        File warFile = new File(parentsPath);
        System.out.println(warFile.getAbsolutePath());
        for (File file : warFile.listFiles()) {
            if(!file.isDirectory()&&file.getName().endsWith(".war")){
                extractWar(
                        file.getAbsolutePath(),
                        file.getAbsolutePath().substring(0, file.getAbsolutePath().length() - 4)
                );
            }
        }

    }

    public static void extractWar(String warFilePath, String outputDirectory)  {
        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(warFilePath))) {
            File outputDir = new File(outputDirectory);

            // 创建输出目录
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                File entryFile = new File(outputDir, entryName);

                if (entry.isDirectory()) {
                    // 如果是目录，创建目录
                    entryFile.mkdirs();
                } else {
                    // 如果是文件，写入文件
                    try (FileOutputStream fos = new FileOutputStream(entryFile)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zipInputStream.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }catch (FileNotFoundException e) {

                    }
                }
                zipInputStream.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
