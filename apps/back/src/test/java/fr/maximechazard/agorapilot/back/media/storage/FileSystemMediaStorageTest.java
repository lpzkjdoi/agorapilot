package fr.maximechazard.agorapilot.back.media.storage;

import fr.maximechazard.agorapilot.back.media.MediaFileType;
import fr.maximechazard.agorapilot.back.media.MediaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemMediaStorageTest {

    private static final byte[] CONTENT = "une affiche".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path root;

    private FileSystemMediaStorage storage;

    @BeforeEach
    void setUp() {
        storage = new FileSystemMediaStorage(new MediaProperties(root.toString(), List.of("image/jpeg")));
        storage.initialize();
    }

    @Test
    void writes_the_file_under_a_year_month_shard_and_reports_its_size() throws IOException {
        StoredFile stored = storage.store(new ByteArrayInputStream(CONTENT), MediaFileType.JPEG);

        assertThat(stored.storageKey()).matches("\\d{4}/\\d{2}/[0-9a-f-]{36}\\.jpg");
        assertThat(stored.sizeBytes()).isEqualTo(CONTENT.length);
        assertThat(Files.readAllBytes(root.resolve(stored.storageKey()))).isEqualTo(CONTENT);
    }

    @Test
    void computes_a_stable_sha256_of_the_content() {
        StoredFile first = storage.store(new ByteArrayInputStream(CONTENT), MediaFileType.JPEG);
        StoredFile second = storage.store(new ByteArrayInputStream(CONTENT), MediaFileType.JPEG);

        assertThat(first.checksum()).hasSize(64).isEqualTo(second.checksum());
        assertThat(first.storageKey()).isNotEqualTo(second.storageKey());
    }

    @Test
    void leaves_no_temporary_file_behind() throws IOException {
        StoredFile stored = storage.store(new ByteArrayInputStream(CONTENT), MediaFileType.JPEG);

        try (var files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile))
                    .containsExactly(root.resolve(stored.storageKey()));
        }
    }

    @Test
    void reads_back_what_it_wrote() throws IOException {
        StoredFile stored = storage.store(new ByteArrayInputStream(CONTENT), MediaFileType.JPEG);

        assertThat(storage.load(stored.storageKey()).getInputStream().readAllBytes()).isEqualTo(CONTENT);
    }

    @Test
    void refuses_a_key_that_escapes_the_root() {
        // Une clé transite par la base : altérée, elle ne doit pas servir à lire
        // n'importe quel fichier de l'hôte.
        assertThatThrownBy(() -> storage.load("../../etc/passwd"))
                .isInstanceOf(MediaStorageException.class)
                .hasMessageContaining("hors de la racine");
    }

    @Test
    void refuses_a_blank_key() {
        assertThatThrownBy(() -> storage.load("  ")).isInstanceOf(MediaStorageException.class);
    }

    @Test
    void fails_when_asked_for_a_file_that_is_not_there() {
        assertThatThrownBy(() -> storage.load("2026/08/absent.jpg"))
                .isInstanceOf(MediaStorageException.class)
                .hasMessageContaining("introuvable");
    }

    @Test
    void deletes_the_file_and_stays_silent_on_a_second_pass() {
        StoredFile stored = storage.store(new ByteArrayInputStream(CONTENT), MediaFileType.JPEG);

        storage.delete(stored.storageKey());
        assertThat(root.resolve(stored.storageKey())).doesNotExist();

        // Rejouable : la suppression est appelée après commit, potentiellement
        // après un premier passage partiel.
        storage.delete(stored.storageKey());
    }

    @Test
    void creates_the_root_when_it_does_not_exist_yet() {
        Path missing = root.resolve("sous/dossier/absent");
        FileSystemMediaStorage fresh =
                new FileSystemMediaStorage(new MediaProperties(missing.toString(), List.of()));

        fresh.initialize();

        assertThat(missing).isDirectory();
    }
}
