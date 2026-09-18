package dev.oumuv.budlog.whitenoise;

import dev.oumuv.budlog.common.BusinessException;
import dev.oumuv.budlog.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class WhiteNoiseService {

    private static final String DEFAULT_PLAYLIST_NAME = "默认";
    private static final String STREAM_PREFIX = "/api/v1/white-noise/tracks/";
    private static final String STREAM_SUFFIX = "/stream";
    private static final LinkOption[] NO_FOLLOW = new LinkOption[]{LinkOption.NOFOLLOW_LINKS};
    private static final Comparator<Path> PATH_NAME_COMPARATOR = Comparator
            .comparing((Path path) -> path.getFileName().toString().toLowerCase(Locale.ROOT))
            .thenComparing(path -> path.getFileName().toString());

    private final AppProperties properties;

    public WhiteNoiseService(AppProperties properties) {
        this.properties = properties;
    }

    public List<WhiteNoisePlaylistResponse> listPlaylists() {
        List<ScannedPlaylist> scanned = scanLibrary();
        List<WhiteNoisePlaylistResponse> result = new ArrayList<WhiteNoisePlaylistResponse>();
        for (ScannedPlaylist playlist : scanned) {
            List<WhiteNoiseTrackResponse> tracks = new ArrayList<WhiteNoiseTrackResponse>();
            for (WhiteNoiseTrackResource track : playlist.tracks) {
                tracks.add(WhiteNoiseTrackResponse.builder()
                        .id(track.getId())
                        .name(track.getName())
                        .format("MP3")
                        .sizeBytes(track.getSizeBytes())
                        .streamUrl(STREAM_PREFIX + track.getId() + STREAM_SUFFIX)
                        .build());
            }
            result.add(WhiteNoisePlaylistResponse.builder()
                    .id(playlist.id)
                    .name(playlist.name)
                    .tracks(tracks)
                    .build());
        }
        return result;
    }

    public WhiteNoiseTrackResource requireTrack(String id) {
        if (!StringUtils.hasText(id) || !id.matches("[a-f0-9]{64}")) {
            throw BusinessException.notFound("白噪音文件不存在");
        }
        for (ScannedPlaylist playlist : scanLibrary()) {
            for (WhiteNoiseTrackResource track : playlist.tracks) {
                if (track.getId().equals(id)) {
                    return track;
                }
            }
        }
        throw BusinessException.notFound("白噪音文件不存在或已移除");
    }

    private List<ScannedPlaylist> scanLibrary() {
        Path root = configuredRoot();
        BasicFileAttributes pathAttributes;
        try {
            pathAttributes = Files.readAttributes(root, BasicFileAttributes.class, NO_FOLLOW);
        } catch (IOException exception) {
            throw BusinessException.serviceUnavailable("白噪音目录不存在或不可读");
        }
        requireStableDirectory(pathAttributes);

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(root)) {
            if (!(directoryStream instanceof SecureDirectoryStream)) {
                throw BusinessException.serviceUnavailable("当前文件系统不支持安全读取白噪音目录");
            }
            @SuppressWarnings("unchecked")
            SecureDirectoryStream<Path> secureRoot = (SecureDirectoryStream<Path>) directoryStream;
            BasicFileAttributes openedAttributes = readDirectoryAttributes(secureRoot);
            if (!sameDirectory(pathAttributes, openedAttributes)) {
                throw BusinessException.serviceUnavailable("白噪音目录在扫描时发生变化");
            }
            return scanRoot(root, secureRoot, openedAttributes.fileKey());
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | DirectoryIteratorException exception) {
            throw BusinessException.serviceUnavailable("白噪音目录暂时不可用");
        }
    }

    private List<ScannedPlaylist> scanRoot(
            Path root,
            SecureDirectoryStream<Path> secureRoot,
            Object rootKey) throws IOException {
        List<Path> rootFiles = new ArrayList<Path>();
        List<Path> directories = new ArrayList<Path>();
        for (Path entry : secureRoot) {
            Path name = entry.getFileName();
            if (!isSafeVisibleName(name)) {
                continue;
            }
            BasicFileAttributes attributes = tryReadEntryAttributes(secureRoot, name);
            if (attributes == null) {
                continue;
            }
            if (attributes.isRegularFile() && isMp3(name)) {
                rootFiles.add(name);
            } else if (attributes.isDirectory()) {
                directories.add(name);
            }
        }
        rootFiles.sort(PATH_NAME_COMPARATOR);
        directories.sort(PATH_NAME_COMPARATOR);

        List<ScannedPlaylist> playlists = new ArrayList<ScannedPlaylist>();
        List<WhiteNoiseTrackResource> defaultTracks = scanTracks(
                root,
                secureRoot,
                rootKey,
                rootKey,
                null,
                rootFiles);
        if (!defaultTracks.isEmpty()) {
            playlists.add(new ScannedPlaylist(hash("playlist:default"), DEFAULT_PLAYLIST_NAME, defaultTracks));
        }

        for (Path directoryName : directories) {
            ScannedPlaylist playlist = scanPlaylistDirectory(root, secureRoot, rootKey, directoryName);
            if (playlist != null) {
                playlists.add(playlist);
            }
        }
        return playlists;
    }

    private ScannedPlaylist scanPlaylistDirectory(
            Path root,
            SecureDirectoryStream<Path> secureRoot,
            Object rootKey,
            Path directoryName) {
        try {
            BasicFileAttributes expected = readEntryAttributes(secureRoot, directoryName);
            if (!expected.isDirectory()) {
                return null;
            }
            Object directoryKey = requireStableIdentity(expected);
            try (SecureDirectoryStream<Path> directory = secureRoot.newDirectoryStream(directoryName, NO_FOLLOW)) {
                BasicFileAttributes opened = readDirectoryAttributes(directory);
                if (!sameDirectory(expected, opened)) {
                    return null;
                }

                List<Path> files = new ArrayList<Path>();
                for (Path entry : directory) {
                    Path name = entry.getFileName();
                    if (!isSafeVisibleName(name) || !isMp3(name)) {
                        continue;
                    }
                    BasicFileAttributes attributes = tryReadEntryAttributes(directory, name);
                    if (attributes != null && attributes.isRegularFile()) {
                        files.add(name);
                    }
                }
                files.sort(PATH_NAME_COMPARATOR);
                List<WhiteNoiseTrackResource> tracks = scanTracks(
                        root,
                        directory,
                        rootKey,
                        directoryKey,
                        directoryName,
                        files);
                if (tracks.isEmpty()) {
                    return null;
                }
                String name = directoryName.toString();
                return new ScannedPlaylist(hash("playlist:" + name), name, tracks);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | DirectoryIteratorException exception) {
            return null;
        }
    }

    private List<WhiteNoiseTrackResource> scanTracks(
            Path root,
            SecureDirectoryStream<Path> directory,
            Object rootKey,
            Object directoryKey,
            Path directoryName,
            List<Path> filenames) throws IOException {
        List<WhiteNoiseTrackResource> tracks = new ArrayList<WhiteNoiseTrackResource>();
        for (Path filename : filenames) {
            WhiteNoiseTrackResource track = scanTrack(
                    root,
                    directory,
                    rootKey,
                    directoryKey,
                    directoryName,
                    filename);
            if (track != null) {
                tracks.add(track);
            }
        }
        return tracks;
    }

    private WhiteNoiseTrackResource scanTrack(
            Path root,
            SecureDirectoryStream<Path> directory,
            Object rootKey,
            Object directoryKey,
            Path directoryName,
            Path filename) throws IOException {
        BasicFileAttributes before = tryReadEntryAttributes(directory, filename);
        if (before == null || !before.isRegularFile()) {
            return null;
        }
        Object fileKey = requireStableIdentity(before);
        try (SeekableByteChannel channel = directory.newByteChannel(filename, readOptions())) {
            BasicFileAttributes after = tryReadEntryAttributes(directory, filename);
            if (after == null
                    || !sameFile(before, after)
                    || channel.size() != after.size()) {
                return null;
            }
        } catch (IOException exception) {
            return null;
        }

        Path relative = directoryName == null ? filename : directoryName.resolve(filename);
        String portableRelative = relative.toString().replace(relative.getFileSystem().getSeparator(), "/");
        String originalName = filename.toString();
        String displayName = originalName.substring(0, originalName.length() - 4);
        return new WhiteNoiseTrackResource(
                hash(portableRelative),
                displayName,
                root,
                relative,
                rootKey,
                directoryKey,
                fileKey,
                before.lastModifiedTime().toMillis(),
                before.size());
    }

    private BasicFileAttributes readDirectoryAttributes(SecureDirectoryStream<Path> directory) throws IOException {
        BasicFileAttributeView view = directory.getFileAttributeView(BasicFileAttributeView.class);
        if (view == null) {
            throw BusinessException.serviceUnavailable("当前文件系统无法读取白噪音目录属性");
        }
        return view.readAttributes();
    }

    private BasicFileAttributes readEntryAttributes(SecureDirectoryStream<Path> directory, Path name)
            throws IOException {
        BasicFileAttributeView view = directory.getFileAttributeView(
                name,
                BasicFileAttributeView.class,
                NO_FOLLOW);
        if (view == null) {
            throw BusinessException.serviceUnavailable("当前文件系统无法安全读取白噪音文件属性");
        }
        return view.readAttributes();
    }

    private BasicFileAttributes tryReadEntryAttributes(SecureDirectoryStream<Path> directory, Path name) {
        try {
            return readEntryAttributes(directory, name);
        } catch (IOException exception) {
            return null;
        }
    }

    private void requireStableDirectory(BasicFileAttributes attributes) {
        if (!attributes.isDirectory()) {
            throw BusinessException.serviceUnavailable("白噪音目录不存在或不可读");
        }
        requireStableIdentity(attributes);
    }

    private Object requireStableIdentity(BasicFileAttributes attributes) {
        if (attributes.fileKey() == null) {
            throw BusinessException.serviceUnavailable("当前文件系统无法安全识别白噪音文件");
        }
        return attributes.fileKey();
    }

    private boolean sameDirectory(BasicFileAttributes expected, BasicFileAttributes actual) {
        return actual.isDirectory()
                && expected.fileKey() != null
                && Objects.equals(expected.fileKey(), actual.fileKey());
    }

    private boolean sameFile(BasicFileAttributes expected, BasicFileAttributes actual) {
        return actual.isRegularFile()
                && Objects.equals(expected.fileKey(), actual.fileKey())
                && expected.size() == actual.size()
                && expected.lastModifiedTime().equals(actual.lastModifiedTime());
    }

    private boolean isSafeVisibleName(Path name) {
        if (name == null || name.getNameCount() != 1) {
            return false;
        }
        String value = name.toString();
        return !value.isEmpty() && !value.startsWith(".");
    }

    private boolean isMp3(Path name) {
        return name.toString().toLowerCase(Locale.ROOT).endsWith(".mp3");
    }

    private Set<OpenOption> readOptions() {
        Set<OpenOption> options = new HashSet<OpenOption>();
        options.add(StandardOpenOption.READ);
        options.add(LinkOption.NOFOLLOW_LINKS);
        return options;
    }

    private Path configuredRoot() {
        String configured = properties.getWhiteNoise().getRootDirectory();
        if (!StringUtils.hasText(configured)) {
            throw BusinessException.serviceUnavailable("白噪音目录未配置");
        }
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static class ScannedPlaylist {
        private final String id;
        private final String name;
        private final List<WhiteNoiseTrackResource> tracks;

        private ScannedPlaylist(String id, String name, List<WhiteNoiseTrackResource> tracks) {
            this.id = id;
            this.name = name;
            this.tracks = tracks;
        }
    }
}
