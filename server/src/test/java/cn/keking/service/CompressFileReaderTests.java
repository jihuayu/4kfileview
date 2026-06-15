package cn.keking.service;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.web.filter.BaseUrlFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CompressFileReaderTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldUseLocalSevenZipOnAppleSiliconMac() throws Exception {
        Optional<String> sevenZipCommand = findLocalSevenZipCommand();
        assumeTrue(sevenZipCommand.isPresent(), "local 7z/7zz/7za is required for this test");
        Path fileDir = tempDir.resolve("files");
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(fileDir);
        Files.createDirectories(sourceDir.resolve("nested"));
        Files.writeString(sourceDir.resolve("inner.txt"), "hello", StandardCharsets.UTF_8);
        Files.write(sourceDir.resolve("nested/pic.png"), new byte[]{1, 2, 3});
        Path archive = fileDir.resolve("sample.7z");
        createArchive(sevenZipCommand.get(), sourceDir, archive);

        String oldOsName = System.getProperty("os.name");
        String oldOsArch = System.getProperty("os.arch");
        String oldFileDir = ConfigConstants.getFileDir();
        ConfigConstants.setFileDirValue(fileDir.toString());
        setBaseUrl();
        FileHandlerService fileHandlerService = mock(FileHandlerService.class);
        CompressFileReader reader = new CompressFileReader(fileHandlerService);
        FileAttribute fileAttribute = new FileAttribute();

        try {
            System.setProperty("os.name", "Mac OS X");
            System.setProperty("os.arch", "aarch64");

            String folderName = reader.unRar(archive.toString(), null, "sample.7z", fileAttribute);

            assertEquals("sample.7z_", folderName);
            assertTrue(Files.exists(fileDir.resolve("sample.7z_/inner.txt")));
            assertTrue(Files.exists(fileDir.resolve("sample.7z_/nested/pic.png")));
            ArgumentCaptor<List<String>> imagesCaptor = ArgumentCaptor.forClass(List.class);
            verify(fileHandlerService).putImgCache(eq("sample.7z_"), imagesCaptor.capture());
            assertEquals(List.of("http://127.0.0.1:8012/sample.7z_/nested/pic.png"), imagesCaptor.getValue());
        } finally {
            System.setProperty("os.name", oldOsName);
            System.setProperty("os.arch", oldOsArch);
            ConfigConstants.setFileDirValue(oldFileDir);
        }
    }

    private Optional<String> findLocalSevenZipCommand() {
        String path = System.getenv("PATH");
        Stream<String> pathCandidates = path == null ? Stream.empty() : Arrays.stream(path.split(File.pathSeparator))
                .flatMap(dir -> Stream.of("7z", "7zz", "7za").map(command -> Path.of(dir, command).toString()));
        return Stream.concat(Stream.of(
                        "/opt/homebrew/bin/7z",
                        "/opt/homebrew/bin/7zz",
                        "/opt/homebrew/bin/7za",
                        "/usr/local/bin/7z",
                        "/usr/local/bin/7zz",
                        "/usr/local/bin/7za"),
                pathCandidates)
                .filter(command -> Files.isExecutable(Path.of(command)))
                .findFirst();
    }

    private void createArchive(String sevenZipCommand, Path sourceDir, Path archive) throws Exception {
        Process process = new ProcessBuilder(sevenZipCommand, "a", "-bd", "-y",
                archive.toString(), "inner.txt", "nested/pic.png")
                .directory(sourceDir.toFile())
                .redirectErrorStream(true)
                .start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        process.getInputStream().transferTo(output);
        int exitCode = process.waitFor();
        assertEquals(0, exitCode, output.toString(StandardCharsets.UTF_8));
    }

    private void setBaseUrl() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("127.0.0.1");
        request.setServerPort(8012);
        new BaseUrlFilter().doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
        });
    }
}
