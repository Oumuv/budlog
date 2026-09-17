package dev.oumuv.budlog.whitenoise;

import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class WhiteNoiseTrackResource {

    private static final LinkOption[] NO_FOLLOW = new LinkOption[]{LinkOption.NOFOLLOW_LINKS};

    private final String id;
    private final String name;
    private final Path rootDirectory;
    private final Path relativePath;
    private final Object rootKey;
    private final Object parentKey;
    private final Object fileKey;
    private final long lastModified;
    private final long sizeBytes;

    public WhiteNoiseTrackResource(
            String id,
            String name,
            Path rootDirectory,
            Path relativePath,
            Object rootKey,
            Object parentKey,
            Object fileKey,
            long lastModified,
            long sizeBytes) {
        this.id = id;
        this.name = name;
        this.rootDirectory = rootDirectory;
        this.relativePath = relativePath;
        this.rootKey = rootKey;
        this.parentKey = parentKey;
        this.fileKey = fileKey;
        this.lastModified = lastModified;
        this.sizeBytes = sizeBytes;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Resource asResource() {
        return new VerifiedPathResource();
    }

    public long getLastModified() {
        return lastModified;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getEtag() {
        return "\"" + id + "-" + sizeBytes + "-" + lastModified + "\"";
    }

    private class VerifiedPathResource extends AbstractResource {

        @Override
        public String getDescription() {
            return "white-noise track " + id;
        }

        @Override
        public String getFilename() {
            return name + ".mp3";
        }

        @Override
        public long contentLength() {
            return sizeBytes;
        }

        @Override
        public long lastModified() {
            return WhiteNoiseTrackResource.this.lastModified;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            DirectoryStream<Path> rootStream = null;
            SecureDirectoryStream<Path> childStream = null;
            SeekableByteChannel channel = null;
            try {
                BasicFileAttributes rootPathAttributes = Files.readAttributes(
                        rootDirectory,
                        BasicFileAttributes.class,
                        NO_FOLLOW);
                requireDirectoryIdentity(rootPathAttributes, rootKey);

                rootStream = Files.newDirectoryStream(rootDirectory);
                if (!(rootStream instanceof SecureDirectoryStream)) {
                    throw changed("Secure directory streams are unavailable");
                }
                @SuppressWarnings("unchecked")
                SecureDirectoryStream<Path> secureRoot = (SecureDirectoryStream<Path>) rootStream;
                requireDirectoryIdentity(readDirectoryAttributes(secureRoot), rootKey);

                SecureDirectoryStream<Path> parentStream = secureRoot;
                if (relativePath.getNameCount() == 2) {
                    Path directoryName = relativePath.getName(0);
                    requireDirectoryIdentity(readEntryAttributes(secureRoot, directoryName), parentKey);
                    childStream = secureRoot.newDirectoryStream(directoryName, NO_FOLLOW);
                    requireDirectoryIdentity(readDirectoryAttributes(childStream), parentKey);
                    parentStream = childStream;
                } else if (relativePath.getNameCount() != 1) {
                    throw changed("Invalid track path");
                }

                Path filename = relativePath.getFileName();
                requireFileIdentity(readEntryAttributes(parentStream, filename));
                channel = parentStream.newByteChannel(filename, readOptions());
                requireFileIdentity(readEntryAttributes(parentStream, filename));
                if (channel.size() != sizeBytes) {
                    throw changed("Track size changed while opening");
                }

                InputStream input = Channels.newInputStream(channel);
                channel = null;
                DirectoryStream<Path> managedRoot = rootStream;
                rootStream = null;
                SecureDirectoryStream<Path> managedChild = childStream;
                childStream = null;
                return new ManagedInputStream(input, managedChild, managedRoot);
            } catch (IOException exception) {
                closeQuietly(channel);
                closeQuietly(childStream);
                closeQuietly(rootStream);
                throw exception;
            }
        }

        private void requireFileIdentity(BasicFileAttributes attributes) throws FileNotFoundException {
            if (!attributes.isRegularFile()
                    || attributes.size() != sizeBytes
                    || attributes.lastModifiedTime().toMillis() != lastModified
                    || !Objects.equals(attributes.fileKey(), fileKey)) {
                throw changed("Track changed while opening");
            }
        }
    }

    private BasicFileAttributes readDirectoryAttributes(SecureDirectoryStream<Path> directory) throws IOException {
        BasicFileAttributeView view = directory.getFileAttributeView(BasicFileAttributeView.class);
        if (view == null) {
            throw changed("Basic file attributes are unavailable");
        }
        return view.readAttributes();
    }

    private BasicFileAttributes readEntryAttributes(SecureDirectoryStream<Path> directory, Path name) throws IOException {
        BasicFileAttributeView view = directory.getFileAttributeView(
                name,
                BasicFileAttributeView.class,
                NO_FOLLOW);
        if (view == null) {
            throw changed("Basic file attributes are unavailable");
        }
        return view.readAttributes();
    }

    private void requireDirectoryIdentity(BasicFileAttributes attributes, Object expectedKey)
            throws FileNotFoundException {
        if (!attributes.isDirectory() || !Objects.equals(attributes.fileKey(), expectedKey)) {
            throw changed("Track directory changed while opening");
        }
    }

    private FileNotFoundException changed(String reason) {
        return new FileNotFoundException(reason + ": " + id);
    }

    private Set<OpenOption> readOptions() {
        Set<OpenOption> options = new HashSet<OpenOption>();
        options.add(StandardOpenOption.READ);
        options.add(LinkOption.NOFOLLOW_LINKS);
        return options;
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Preserve the original failure from opening the track.
        }
    }

    private static class ManagedInputStream extends FilterInputStream {

        private final Closeable[] resources;
        private boolean closed;

        private ManagedInputStream(InputStream input, Closeable... resources) {
            super(input);
            this.resources = resources;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            IOException failure = null;
            try {
                super.close();
            } catch (IOException exception) {
                failure = exception;
            }
            for (Closeable resource : resources) {
                if (resource == null) {
                    continue;
                }
                try {
                    resource.close();
                } catch (IOException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
