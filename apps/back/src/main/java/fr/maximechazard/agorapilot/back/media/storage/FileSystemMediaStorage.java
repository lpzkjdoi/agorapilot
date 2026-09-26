package fr.maximechazard.agorapilot.back.media.storage;

import fr.maximechazard.agorapilot.back.media.MediaFileType;
import fr.maximechazard.agorapilot.back.media.MediaProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Stockage des médias sur un volume disque.
 * <p>
 * Les fichiers sont répartis en {@code aaaa/MM/} : un répertoire unique
 * deviendrait impraticable (listing, sauvegarde) au bout de quelques milliers
 * d'affiches.
 * <p>
 * L'écriture passe par un fichier temporaire puis un déplacement atomique : sans
 * ça, une interruption au milieu du transfert laisserait un fichier tronqué que
 * rien ne distinguerait d'un fichier valide.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileSystemMediaStorage implements MediaStorageService {

    private static final DateTimeFormatter SHARD = DateTimeFormatter.ofPattern("yyyy/MM");

    private final MediaProperties properties;

    private Path root;

    @PostConstruct
    void initialize() {
        this.root = Path.of(properties.root()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new MediaStorageException("Racine de stockage des médias inutilisable : " + root, e);
        }
        if (!Files.isWritable(root)) {
            // Cas typique en conteneur : volume monté appartenant à root alors que
            // le processus tourne sous un utilisateur non privilégié.
            throw new MediaStorageException("Racine de stockage des médias non accessible en écriture : " + root);
        }
        log.info("Médiathèque : stockage sur {}", root);
    }

    @Override
    public StoredFile store(InputStream content, MediaFileType fileType) {
        String storageKey = LocalDate.now().format(SHARD) + "/" + UUID.randomUUID() + "." + fileType.getExtension();
        Path target = resolve(storageKey);
        Path temporary = null;

        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), "upload-", ".part");

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (OutputStream out = Files.newOutputStream(temporary);
                 DigestOutputStream digesting = new DigestOutputStream(out, digest)) {
                size = content.transferTo(digesting);
            }

            move(temporary, target);
            return new StoredFile(storageKey, size, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException e) {
            deleteQuietly(temporary);
            throw new MediaStorageException("Échec de l'écriture du média " + storageKey, e);
        }
    }

    @Override
    public Resource load(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isReadable(path)) {
            throw new MediaStorageException("Fichier introuvable pour la clé " + storageKey);
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            // La ligne en base est déjà supprimée : on ne peut plus revenir en
            // arrière, on trace pour un nettoyage ultérieur.
            log.warn("Média {} : suppression du fichier impossible", storageKey, e);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Fichier temporaire {} non supprimé", path, e);
        }
    }

    private void move(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Certains systèmes de fichiers réseau ne le supportent pas ; le
            // déplacement simple reste très largement préférable à une copie.
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Traduit une clé en chemin absolu, en refusant tout ce qui sortirait de la
     * racine. Les clés sont générées par ce service, mais elles transitent par la
     * base : une clé altérée ne doit pas pouvoir servir à lire {@code /etc/passwd}.
     */
    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new MediaStorageException("Clé de stockage vide");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new MediaStorageException("Clé de stockage hors de la racine : " + storageKey);
        }
        return resolved;
    }
}
