package fr.maximechazard.agorapilot.back.media;

import fr.maximechazard.agorapilot.back.media.dtos.MediaContent;
import fr.maximechazard.agorapilot.back.media.dtos.MediaDTO;
import fr.maximechazard.agorapilot.back.media.exceptions.MediaNotFoundException;
import fr.maximechazard.agorapilot.back.media.exceptions.UnsupportedMediaFileTypeException;
import fr.maximechazard.agorapilot.back.media.requests.UpdateMediaRequest;
import fr.maximechazard.agorapilot.back.media.storage.MediaStorageService;
import fr.maximechazard.agorapilot.back.media.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

    private final MediaRepository mediaRepository;
    private final MediaStorageService storage;
    private final MediaTypeDetector typeDetector;
    private final MediaMapper mapper;
    private final MediaProperties properties;

    // ------------------------------- Lecture -------------------------------

    public List<MediaDTO> getAll(Boolean archived) {
        List<Media> medias = archived == null
                ? mediaRepository.findAllByOrderByCreatedAtDesc()
                : mediaRepository.findAllByArchivedOrderByCreatedAtDesc(archived);

        return medias.stream().map(mapper::toDTO).toList();
    }

    public MediaDTO get(Long id) {
        return mapper.toDTO(findOrThrow(id));
    }

    public MediaContent loadContent(Long id) {
        Media media = findOrThrow(id);
        Resource resource = storage.load(media.getStorageKey());
        return new MediaContent(mapper.toDTO(media), resource);
    }

    // ------------------------------- Écriture ------------------------------

    /**
     * Dépose un fichier et enregistre son média.
     * <p>
     * Le binaire est écrit avant le commit : si la transaction échoue ensuite, le
     * fichier resterait orphelin sur le disque. Une synchronisation de transaction
     * le rattrape — c'est le seul point où les deux supports peuvent diverger.
     */
    @Transactional
    public MediaDTO upload(MultipartFile file, String title, String altText) {
        MediaFileType fileType = detectType(file);

        StoredFile stored;
        try (InputStream content = file.getInputStream()) {
            stored = storage.store(content, fileType);
        } catch (IOException e) {
            throw new UnsupportedMediaFileTypeException("Le fichier déposé n'a pas pu être lu.");
        }

        deleteFileIfRollback(stored.storageKey());

        Media media = new Media(
                stored.storageKey(),
                sanitizeFilename(file.getOriginalFilename(), fileType),
                fileType,
                stored.sizeBytes(),
                stored.checksum()
        );
        media.setTitle(StringUtils.hasText(title) ? title.trim() : null);
        media.setAltText(StringUtils.hasText(altText) ? altText.trim() : null);

        if (fileType.isRaster()) {
            readDimensions(stored.storageKey()).ifPresent(dimensions -> {
                media.setWidth(dimensions.width());
                media.setHeight(dimensions.height());
            });
        }

        return mapper.toDTO(mediaRepository.save(media));
    }

    @Transactional
    public MediaDTO update(Long id, UpdateMediaRequest request) {
        Media media = findOrThrow(id);

        if (request.getTitle() != null) {
            media.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle().trim() : null);
        }
        if (request.getAltText() != null) {
            media.setAltText(StringUtils.hasText(request.getAltText()) ? request.getAltText().trim() : null);
        }
        if (request.getArchived() != null) {
            media.setArchived(request.getArchived());
        }

        return mapper.toDTO(mediaRepository.save(media));
    }

    /**
     * Supprime le média, ligne et fichier.
     * <p>
     * Le fichier n'est effacé qu'après le commit : l'inverse détruirait le binaire
     * d'un média encore présent en base si la transaction venait à échouer.
     */
    @Transactional
    public void delete(Long id) {
        Media media = findOrThrow(id);
        String storageKey = media.getStorageKey();

        mediaRepository.delete(media);
        deleteFileAfterCommit(storageKey);
    }

    // ------------------------------- Interne -------------------------------

    private Media findOrThrow(Long id) {
        return mediaRepository.findById(id)
                .orElseThrow(() -> new MediaNotFoundException("Média " + id + " introuvable"));
    }

    /**
     * Détermine le format sur le contenu réel. Le {@code Content-Type} annoncé par
     * le navigateur et l'extension du nom sont fournis par le client : un exécutable
     * renommé en {@code .jpg} passerait tout contrôle fondé sur eux.
     */
    private MediaFileType detectType(MultipartFile file) {
        byte[] header = new byte[MediaTypeDetector.HEADER_LENGTH];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            throw new UnsupportedMediaFileTypeException("Le fichier déposé n'a pas pu être lu.");
        }

        if (read < header.length) {
            throw new UnsupportedMediaFileTypeException("Le fichier déposé est vide ou trop court pour être identifié.");
        }

        MediaFileType fileType = typeDetector.detect(header)
                .orElseThrow(() -> new UnsupportedMediaFileTypeException(
                        "Format non reconnu. Formats acceptés : " + acceptedLabel() + "."));

        if (!properties.allowedContentTypes().contains(fileType.getContentType())) {
            throw new UnsupportedMediaFileTypeException(
                    "Format " + fileType.getContentType() + " non accepté. Formats acceptés : " + acceptedLabel() + ".");
        }

        return fileType;
    }

    private String acceptedLabel() {
        return String.join(", ", properties.allowedContentTypes());
    }

    /**
     * Neutralise le nom fourni par le client : il ne sert qu'à l'affichage et au
     * téléchargement, jamais à composer un chemin, mais il finit dans un en-tête
     * HTTP et dans le DOM du front.
     */
    private String sanitizeFilename(String originalFilename, MediaFileType fileType) {
        String cleaned = StringUtils.getFilename(originalFilename);
        if (!StringUtils.hasText(cleaned)) {
            return "media." + fileType.getExtension();
        }
        cleaned = cleaned.replaceAll("[\\p{Cntrl}\"\\\\/]", "").trim();
        if (cleaned.isEmpty()) {
            return "media." + fileType.getExtension();
        }
        return cleaned.length() > 255 ? cleaned.substring(0, 255) : cleaned;
    }

    private Optional<Dimensions> readDimensions(String storageKey) {
        try (InputStream in = storage.load(storageKey).getInputStream();
             ImageInputStream imageStream = ImageIO.createImageInputStream(in)) {

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
            if (!readers.hasNext()) {
                return Optional.empty();
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageStream);
                return Optional.of(new Dimensions(reader.getWidth(0), reader.getHeight(0)));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            // Les dimensions sont un confort d'affichage : leur absence ne doit pas
            // faire échouer un dépôt par ailleurs valide.
            log.warn("Média {} : dimensions illisibles", storageKey, e);
            return Optional.empty();
        }
    }

    private void deleteFileIfRollback(String storageKey) {
        registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    log.warn("Transaction non validée : suppression du fichier orphelin {}", storageKey);
                    storage.delete(storageKey);
                }
            }
        });
    }

    private void deleteFileAfterCommit(String storageKey) {
        registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(storageKey);
            }
        });
    }

    private void registerSynchronization(TransactionSynchronization synchronization) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(synchronization);
        } else {
            // Hors transaction (appel direct en test unitaire) : rien à différer.
            synchronization.afterCommit();
        }
    }

    private record Dimensions(int width, int height) {
    }
}
