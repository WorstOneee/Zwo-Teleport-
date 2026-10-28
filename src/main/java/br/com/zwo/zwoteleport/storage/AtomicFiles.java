package br.com.zwo.zwoteleport.storage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Gravação de arquivos que nunca deixa o destino pela metade: escreve em um temporário na mesma
 * pasta, força a gravação em disco, guarda a versão anterior em {@code .bak} e só então substitui.
 */
public final class AtomicFiles {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private AtomicFiles() {
    }

    public static void write(Path target, String content, boolean keepBackup) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        if (keepBackup && Files.exists(target)) {
            Files.copy(target, backupOf(target), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static Path backupOf(Path target) {
        return target.resolveSibling(target.getFileName() + ".bak");
    }

    /** Copia um arquivo problemático para análise, sem removê-lo. */
    public static Path preserveCopy(Path source, String suffix) throws IOException {
        Path copy = source.resolveSibling(source.getFileName() + "." + suffix + "-" + LocalDateTime.now().format(STAMP));
        Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
        return copy;
    }

    public static String timestamp() {
        return LocalDateTime.now().format(STAMP);
    }
}
