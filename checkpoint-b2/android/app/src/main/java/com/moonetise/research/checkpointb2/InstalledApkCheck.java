package com.moonetise.research.checkpointb2;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Counts actual class_def entries in installed base/split APKs, without loading the adapter. */
public final class InstalledApkCheck {
    private static final String TARGET =
            "Lcom/applovin/mediation/adapters/PrebidMaxMediationAdapter;";
    private static final int MAX_DEX_BYTES = 64 * 1024 * 1024;

    private InstalledApkCheck() {}

    public static int count(Context context) throws IOException {
        List<String> paths = new ArrayList<>();
        paths.add(context.getApplicationInfo().sourceDir);
        String[] splits = context.getApplicationInfo().splitSourceDirs;
        if (splits != null) for (String split : splits) paths.add(split);
        int count = 0;
        for (String path : paths) {
            try (ZipFile zip = new ZipFile(path)) {
                java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.getName().matches("classes([0-9]+)?\\.dex")) continue;
                    try (InputStream input = zip.getInputStream(entry);
                            ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                        byte[] chunk = new byte[8192];
                        int read;
                        while ((read = input.read(chunk)) != -1) {
                            if (output.size() + read > MAX_DEX_BYTES) throw new IOException("DEX_SIZE_LIMIT");
                            output.write(chunk, 0, read);
                        }
                        count += countDex(output.toByteArray());
                    }
                }
            }
        }
        return count;
    }

    private static int countDex(byte[] dex) throws IOException {
        try {
            if (dex.length < 112 || dex[0] != 'd' || dex[1] != 'e' || dex[2] != 'x')
                throw new IOException("INVALID_DEX");
            ByteBuffer buffer = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN);
            int strings = buffer.getInt(0x3c);
            int types = buffer.getInt(0x44);
            int classCount = buffer.getInt(0x60);
            int classes = buffer.getInt(0x64);
            if (classCount < 0 || classCount > dex.length / 32) throw new IOException("INVALID_DEX");
            int total = 0;
            for (int i = 0; i < classCount; i++) {
                int type = buffer.getInt(classes + i * 32);
                int string = buffer.getInt(types + type * 4);
                int offset = buffer.getInt(strings + string * 4);
                int lengthBytes = 0;
                while ((dex[offset++] & 0x80) != 0) {
                    if (++lengthBytes > 4) throw new IOException("INVALID_DEX");
                }
                int start = offset;
                while (dex[offset] != 0) offset++;
                if (TARGET.equals(new String(dex, start, offset - start, StandardCharsets.UTF_8))) total++;
            }
            return total;
        } catch (RuntimeException invalid) {
            throw new IOException("INVALID_DEX");
        }
    }
}
