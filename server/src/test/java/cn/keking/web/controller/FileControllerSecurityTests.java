package cn.keking.web.controller;

import cn.keking.config.ConfigConstants;
import cn.keking.model.ReturnResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileControllerSecurityTests {

    @TempDir
    Path tempDir;

    private String originalFileDir;
    private Boolean originalFileUploadDisable;
    private String[] originalProhibit;

    @BeforeEach
    void setUp() {
        originalFileDir = ConfigConstants.getFileDir();
        originalFileUploadDisable = ConfigConstants.getFileUploadDisable();
        originalProhibit = ConfigConstants.getProhibit();

        ConfigConstants.setFileDirValue(tempDir.toString());
        ConfigConstants.setFileUploadDisableValue(false);
        ConfigConstants.setProhibitValue(new String[]{"exe", "dll"});
    }

    @AfterEach
    void tearDown() {
        ConfigConstants.setFileDirValue(originalFileDir);
        ConfigConstants.setFileUploadDisableValue(originalFileUploadDisable);
        ConfigConstants.setProhibitValue(originalProhibit);
    }

    @Test
    void shouldListFilesInNestedDemoDirectory() throws IOException {
        Files.createDirectories(tempDir.resolve("demo").resolve("nested"));
        Files.writeString(tempDir.resolve("demo").resolve("nested").resolve("sample.txt"), "ok");

        FileController controller = new FileController();
        Map<String, Object> result = controller.getFiles("nested", "", 0, 20, null, null);

        assertEquals(1, result.get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
        assertEquals("sample.txt", data.get(0).get("name"));
    }

    @Test
    void shouldRejectDirectoryTraversalWhenListingFiles() throws IOException {
        Files.createDirectories(tempDir.resolve("demo"));
        Files.writeString(tempDir.resolve("outside.txt"), "outside");

        FileController controller = new FileController();
        Map<String, Object> result = controller.getFiles("../", "", 0, 20, null, null);

        assertEquals(0, result.get("total"));
        assertTrue(result.containsKey("error"));
    }

    @Test
    void shouldCreateFolderInsideNestedDemoDirectory() throws IOException {
        Files.createDirectories(tempDir.resolve("demo").resolve("parent"));

        FileController controller = new FileController();
        ReturnResponse<Object> response = controller.createFolder("parent", "child");

        assertTrue(response.isSuccess());
        assertTrue(Files.isDirectory(tempDir.resolve("demo").resolve("parent").resolve("child")));
    }

    @Test
    void shouldRejectDirectoryTraversalWhenCreatingFolder() throws IOException {
        Files.createDirectories(tempDir.resolve("demo"));

        FileController controller = new FileController();
        ReturnResponse<Object> response = controller.createFolder("../", "evil");

        assertTrue(response.isFailure());
        assertFalse(Files.exists(tempDir.resolve("evil")));
    }

    @Test
    void shouldUploadFileInsideNestedDemoDirectory() throws IOException {
        Files.createDirectories(tempDir.resolve("demo").resolve("uploads"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        FileController controller = new FileController();
        ReturnResponse<Object> response = controller.fileUpload(file, "uploads");

        assertTrue(response.isSuccess());
        assertTrue(Files.exists(tempDir.resolve("demo").resolve("uploads").resolve("sample.png")));
    }

    @Test
    void shouldRejectDirectoryTraversalWhenUploadingFile() throws IOException {
        Files.createDirectories(tempDir.resolve("demo"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        FileController controller = new FileController();
        ReturnResponse<Object> response = controller.fileUpload(file, "../");

        assertTrue(response.isFailure());
        assertFalse(Files.exists(tempDir.resolve("sample.png")));
    }
}
