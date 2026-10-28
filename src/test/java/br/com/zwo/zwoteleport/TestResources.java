package br.com.zwo.zwoteleport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class TestResources {

    private TestResources() {
    }

    public static String read(String path) {
        try (InputStream in = TestResources.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("recurso ausente: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
