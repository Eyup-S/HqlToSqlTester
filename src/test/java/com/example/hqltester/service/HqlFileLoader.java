package com.example.hqltester.service;

import com.example.hqltester.config.HqlTesterProperties;
import com.example.hqltester.model.HqlFileInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class HqlFileLoader {

    private final HqlTesterProperties properties;
    private final ObjectMapper objectMapper;

    public HqlFileLoader(HqlTesterProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Lists all .hql files in the configured folder.
     * Re-reads the directory on every call, so no restart is needed
     * when new files are added.
     */
    public List<HqlFileInfo> listFiles() throws IOException {
        Path folder = resolveFolder();
        if (!Files.exists(folder) || !Files.isDirectory(folder)) {
            return Collections.emptyList();
        }

        try (Stream<Path> stream = Files.list(folder)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".hql"))
                    .sorted()
                    .map(this::toFileInfo)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Reads the raw HQL content of the given file.
     * File is re-read every call, no caching.
     */
    public String loadHql(String filename) throws IOException {
        return Files.readString(resolveHqlPath(filename));
    }

    /**
     * Reads the optional .json sidecar that holds bind parameter values.
     * If the sidecar doesn't exist, returns an empty map.
     *
     * Sidecar format:
     * {
     *   "paramName": "value",
     *   "numericParam": 42,
     *   "listParam": [1, 2, 3]
     * }
     */
    public Map<String, Object> loadParams(String filename) throws IOException {
        String jsonFilename = filename.replaceAll("\\.hql$", ".json");
        Path paramsFile = resolveFolder().resolve(jsonFilename);

        if (!Files.exists(paramsFile)) {
            return Collections.emptyMap();
        }

        return objectMapper.readValue(
                paramsFile.toFile(),
                new TypeReference<Map<String, Object>>() {}
        );
    }

    public Path resolveFolder() {
        return Paths.get(properties.getQueryFolder()).toAbsolutePath().normalize();
    }

    public Path resolveHqlPath(String filename) {
        return resolveFolder().resolve(filename).normalize();
    }

    private HqlFileInfo toFileInfo(Path path) {
        HqlFileInfo info = new HqlFileInfo();
        String filename = path.getFileName().toString();
        info.setFilename(filename);

        String paramsJson = filename.replaceAll("\\.hql$", ".json");
        info.setHasParams(Files.exists(path.getParent().resolve(paramsJson)));

        try {
            String content = Files.readString(path);
            // First non-empty line that starts with "-- " is treated as the description.
            content.lines()
                    .filter(l -> !l.isBlank())
                    .findFirst()
                    .filter(l -> l.startsWith("-- "))
                    .ifPresent(l -> info.setDescription(l.substring(3).trim()));

            String preview = content.strip();
            info.setHqlPreview(preview.length() > 250 ? preview.substring(0, 250) + "..." : preview);
        } catch (IOException e) {
            info.setDescription("(error reading file: " + e.getMessage() + ")");
        }

        return info;
    }
}
