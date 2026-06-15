package cn.keking.service.impl;

import cn.keking.model.FileAttribute;
import cn.keking.service.FilePreview;
import cn.keking.utils.HttpRequestUtils;
import cn.keking.utils.KkFileUtils;
import cn.keking.utils.WebUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Created by kl on 2018/1/17.
 * Content :图片文件处理
 */
@Service
public class Online3DFilePreviewImpl implements FilePreview {

    private static final Logger logger = LoggerFactory.getLogger(Online3DFilePreviewImpl.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final CommonPreviewImpl commonPreview;

    public Online3DFilePreviewImpl(CommonPreviewImpl commonPreview) {
        this.commonPreview = commonPreview;
    }

    @Override
    public String filePreviewHandle(String url, Model model, FileAttribute fileAttribute) {
        commonPreview.filePreviewHandle(url,model,fileAttribute);
        List<String> resourceUrls = findGltfExternalResourceUrls(url, fileAttribute);
        if (!resourceUrls.isEmpty()) {
            model.addAttribute("online3DResourceUrls", resourceUrls);
        }
        return ONLINE3D_PREVIEW_PAGE;
    }

    List<String> findGltfExternalResourceUrls(String url, FileAttribute fileAttribute) {
        if (fileAttribute == null || !"gltf".equalsIgnoreCase(fileAttribute.getSuffix())) {
            return List.of();
        }
        try {
            URL sourceUrl = WebUtils.normalizedURL(url);
            JsonNode rootNode = objectMapper.readTree(readUrlContent(sourceUrl, fileAttribute));
            Set<String> resourceUrls = new LinkedHashSet<>();
            collectGltfUris(rootNode.path("buffers"), sourceUrl, resourceUrls);
            collectGltfUris(rootNode.path("images"), sourceUrl, resourceUrls);
            return new ArrayList<>(resourceUrls);
        } catch (Exception e) {
            logger.debug("Failed to resolve glTF external resources, url: {}", url, e);
            return List.of();
        }
    }

    private byte[] readUrlContent(URL sourceUrl, FileAttribute fileAttribute) throws Exception {
        if (KkFileUtils.isHttpUrl(sourceUrl)) {
            CloseableHttpClient httpClient = HttpRequestUtils.createConfiguredHttpClient();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            HttpRequestUtils.executeHttpRequest(sourceUrl, httpClient, fileAttribute,
                    responseWrapper -> responseWrapper.getInputStream().transferTo(outputStream));
            return outputStream.toByteArray();
        }
        try (InputStream inputStream = sourceUrl.openStream()) {
            return inputStream.readAllBytes();
        }
    }

    private void collectGltfUris(JsonNode nodes, URL sourceUrl, Set<String> resourceUrls) {
        if (!nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            String uri = node.path("uri").asText(null);
            if (isExternalGltfResource(uri)) {
                resolveResourceUrl(sourceUrl, uri).ifPresent(resourceUrls::add);
            }
        }
    }

    private Optional<String> resolveResourceUrl(URL sourceUrl, String resourceUri) {
        try {
            URI uri = URI.create(resourceUri.replace(" ", "%20"));
            URI resolvedUri = uri.isAbsolute() ? uri : sourceUrl.toURI().resolve(uri);
            return Optional.of(resolvedUri.toString());
        } catch (Exception e) {
            logger.debug("Failed to resolve glTF resource uri: {}", resourceUri, e);
            return Optional.empty();
        }
    }

    private boolean isExternalGltfResource(String uri) {
        return uri != null && !uri.isBlank() && !uri.startsWith("data:");
    }
}
