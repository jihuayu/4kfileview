package cn.keking.service;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.model.FileType;
import cn.keking.utils.RarUtils;
import cn.keking.web.filter.BaseUrlFilter;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.simple.ISimpleInArchive;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * @author yudian-it
 * create 2017/11/27
 */
@Component
public class CompressFileReader {
    private static final Logger logger = LoggerFactory.getLogger(CompressFileReader.class);
    private static final String[] SEVEN_ZIP_COMMANDS = {"7z", "7zz", "7za"};
    private static final String[] SEVEN_ZIP_COMMON_PATHS = {
            "/opt/homebrew/bin/7z",
            "/opt/homebrew/bin/7zz",
            "/opt/homebrew/bin/7za",
            "/usr/local/bin/7z",
            "/usr/local/bin/7zz",
            "/usr/local/bin/7za"
    };
    private static final long LOCAL_7Z_TIMEOUT_MINUTES = 30;

    private final FileHandlerService fileHandlerService;

    public CompressFileReader(FileHandlerService fileHandlerService) {
        this.fileHandlerService = fileHandlerService;
    }

    public String unRar(String filePath, String filePassword, String fileName, FileAttribute fileAttribute) throws Exception {
        String fileDir = ConfigConstants.getFileDir();
        String packagePath = "_";
        String folderName = filePath.replace(fileDir, ""); //修复压缩包 多重目录获取路径错误
        if (fileAttribute.isCompressFile()) {
            folderName = "_decompression" + folderName;
        }
        Path folderPath = Paths.get(fileDir, folderName + packagePath);
        Files.createDirectories(folderPath);

        if (isAppleSiliconMac() && findLocalSevenZipCommand().isPresent()) {
            extractWithLocalSevenZip(filePath, filePassword, folderPath);
            cacheImageUrls(fileName, packagePath, folderPath);
            return folderName + packagePath;
        }

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(filePath, "r");
             IInArchive inArchive = SevenZip.openInArchive(null, new RandomAccessFileInStream(randomAccessFile))) {

            extractWithSevenZipJBinding(simpleInArchive(inArchive), filePassword, folderPath);
            cacheImageUrls(fileName, packagePath, folderPath);
        } catch (Exception | LinkageError e) {
            if (isSevenZipNativeUnavailable(e) && findLocalSevenZipCommand().isPresent()) {
                logger.warn("SevenZipJBinding native library is unavailable, falling back to local 7z: {}", e.getMessage());
                extractWithLocalSevenZip(filePath, filePassword, folderPath);
                cacheImageUrls(fileName, packagePath, folderPath);
            } else {
                throw new Exception("Error processing RAR file: " + e.getMessage(), e);
            }
        }
        return folderName + packagePath;
    }

    private ISimpleInArchive simpleInArchive(IInArchive inArchive) throws SevenZipException {
        return inArchive.getSimpleInterface();
    }

    private void extractWithSevenZipJBinding(ISimpleInArchive simpleInArchive, String filePassword, Path folderPath) throws Exception {
        for (final ISimpleInArchiveItem item : simpleInArchive.getArchiveItems()) {
            if (!item.isFolder()) {
                final Path filePathInsideArchive = getFilePathInsideArchive(item, folderPath);
                Files.deleteIfExists(filePathInsideArchive);
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(filePathInsideArchive.toFile(), false))) {
                    ExtractOperationResult result = item.extractSlow(data -> {
                        try {
                            out.write(data);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        return data.length;
                    }, filePassword);
                    if (result != ExtractOperationResult.OK) {
                        ExtractOperationResult result1 = ExtractOperationResult.valueOf("WRONG_PASSWORD");
                        if (result1.equals(result)) {
                            throw new Exception("Password");
                        } else {
                            throw new Exception("Failed to extract RAR file.");
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void extractWithLocalSevenZip(String filePath, String filePassword, Path folderPath) throws Exception {
        String sevenZipCommand = findLocalSevenZipCommand()
                .orElseThrow(() -> new Exception("Local 7z executable was not found."));
        Path tempFolder = Files.createTempDirectory(folderPath.getParent(), folderPath.getFileName() + "-7z-");
        try {
            List<String> command = new ArrayList<>();
            command.add(sevenZipCommand);
            command.add("x");
            command.add("-bd");
            command.add("-y");
            command.add("-o" + tempFolder);
            if (StringUtils.hasText(filePassword)) {
                command.add("-p" + filePassword);
            } else {
                command.add("-p");
            }
            command.add(filePath);

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readProcessOutput(process));
            boolean finished = process.waitFor(LOCAL_7Z_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new Exception("Local 7z extraction timed out.");
            }
            String output = outputFuture.get(10, TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                if (output.toLowerCase(Locale.ROOT).contains("password")) {
                    throw new Exception("Password");
                }
                throw new Exception("Local 7z extraction failed with exit code " + exitCode + ": " + output);
            }
            copyExtractedFilesSafely(tempFolder, folderPath);
        } finally {
            deleteRecursively(tempFolder);
        }
    }

    private String readProcessOutput(Process process) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            process.getInputStream().transferTo(output);
            return output.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    private void copyExtractedFilesSafely(Path sourceRoot, Path folderPath) throws IOException {
        try (Stream<Path> extractedPaths = Files.walk(sourceRoot)) {
            Iterator<Path> iterator = extractedPaths.filter(Files::isRegularFile).iterator();
            while (iterator.hasNext()) {
                Path extractedPath = iterator.next();
                Path relativePath = sourceRoot.relativize(extractedPath);
                Path destination = folderPath.resolve(relativePath).normalize();
                if (!destination.startsWith(folderPath)) {
                    throw new SecurityException("Unsafe path detected: " + relativePath);
                }
                Files.createDirectories(destination.getParent());
                Files.copy(extractedPath, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void cacheImageUrls(String fileName, String packagePath, Path folderPath) throws IOException {
        List<String> imgUrls = new ArrayList<>();
        String baseUrl = BaseUrlFilter.getBaseUrl();
        try (Stream<Path> extractedPaths = Files.walk(folderPath)) {
            Iterator<Path> iterator = extractedPaths.filter(Files::isRegularFile).iterator();
            while (iterator.hasNext()) {
                Path filePathInsideArchive = iterator.next();
                FileType type = FileType.typeFromUrl(filePathInsideArchive.toString());
                if (type.equals(FileType.PICTURE)) {  //图片缓存到集合，为了特殊符号需要进行编码
                    imgUrls.add(baseUrl + URLEncoder.encode(fileName + packagePath + "/" + folderPath.relativize(filePathInsideArchive).toString().replace("\\", "/"), StandardCharsets.UTF_8).replaceAll("%2F", "/"));
                }
            }
        }
        fileHandlerService.putImgCache(fileName + packagePath, imgUrls);
    }

    private Optional<String> findLocalSevenZipCommand() {
        String configuredPath = System.getenv("KK_SEVENZIP_PATH");
        if (StringUtils.hasText(configuredPath) && Files.isExecutable(Paths.get(configuredPath))) {
            return Optional.of(configuredPath);
        }
        return Stream.concat(Arrays.stream(SEVEN_ZIP_COMMON_PATHS), findExecutablesInPath())
                .filter(command -> Files.isExecutable(Paths.get(command)))
                .findFirst();
    }

    private Stream<String> findExecutablesInPath() {
        String path = System.getenv("PATH");
        if (!StringUtils.hasText(path)) {
            return Stream.empty();
        }
        return Arrays.stream(path.split(File.pathSeparator))
                .flatMap(dir -> Arrays.stream(SEVEN_ZIP_COMMANDS)
                        .map(command -> Paths.get(dir, command).toString()));
    }

    private boolean isAppleSiliconMac() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String osArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return osName.contains("mac") && (osArch.equals("aarch64") || osArch.equals("arm64"));
    }

    private boolean isSevenZipNativeUnavailable(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if (current instanceof UnsatisfiedLinkError || current instanceof NoClassDefFoundError
                    || className.contains("SevenZipNativeInitializationException")
                    || (message != null && message.toLowerCase(Locale.ROOT).contains("native"))) {
                return true;
            }
        }
        return false;
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path item : paths.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    private Path getFilePathInsideArchive(ISimpleInArchiveItem item, Path folderPath) throws SevenZipException, UnsupportedEncodingException {
        String insideFileName = RarUtils.getUtf8String(item.getPath());
        if (RarUtils.isMessyCode(insideFileName)) {
            insideFileName = new String(item.getPath().getBytes(StandardCharsets.ISO_8859_1), "gbk");
        }

        // 正规化路径并验证是否安全
        Path normalizedPath = folderPath.resolve(insideFileName).normalize();
        if (!normalizedPath.startsWith(folderPath)) {
            throw new SecurityException("Unsafe path detected: " + insideFileName);
        }

        try {
            Files.createDirectories(normalizedPath.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + normalizedPath.getParent(), e);
        }
        return normalizedPath;
    }


}
