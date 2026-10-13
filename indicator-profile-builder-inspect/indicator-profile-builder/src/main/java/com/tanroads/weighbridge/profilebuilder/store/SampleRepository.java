package com.tanroads.weighbridge.profilebuilder.store;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanroads.weighbridge.profilebuilder.model.CaptureSample;

/** File-per-sample JSON store: {@code <dataDir>/samples/<profileId>/<sampleId>.json}. */
public class SampleRepository {

    private final Path directory;
    private final ObjectMapper mapper;
    private final Map<Long, CaptureSample> byId = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public SampleRepository(Path directory, ObjectMapper mapper) throws IOException {
        this.directory = directory;
        this.mapper = mapper;
        Files.createDirectories(directory);
        load();
    }

    private void load() throws IOException {
        long maxId = 0;
        try (DirectoryStream<Path> profileDirs = Files.newDirectoryStream(directory, Files::isDirectory)) {
            for (Path profileDir : profileDirs) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(profileDir, "*.json")) {
                    for (Path file : files) {
                        CaptureSample sample = mapper.readValue(file.toFile(), CaptureSample.class);
                        if (sample.getId() == null) {
                            continue;
                        }
                        byId.put(sample.getId(), sample);
                        maxId = Math.max(maxId, sample.getId());
                    }
                }
            }
        }
        nextId.set(maxId + 1);
    }

    public List<CaptureSample> findByProfile(Long profileId) {
        List<CaptureSample> result = new ArrayList<>();
        for (CaptureSample sample : byId.values()) {
            if (profileId != null && profileId.equals(sample.getIndicatorProfileNo())) {
                result.add(sample);
            }
        }
        result.sort(Comparator.comparing(CaptureSample::getCapturedOn, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return result;
    }

    public Optional<CaptureSample> findById(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    public synchronized CaptureSample save(CaptureSample sample) throws IOException {
        if (sample.getId() == null) {
            sample.setId(nextId.getAndIncrement());
        } else {
            nextId.accumulateAndGet(sample.getId() + 1, Math::max);
        }
        Path profileDir = directory.resolve(String.valueOf(sample.getIndicatorProfileNo()));
        Files.createDirectories(profileDir);
        Path target = profileDir.resolve(sample.getId() + ".json");
        Path temp = profileDir.resolve(sample.getId() + ".json.tmp");
        mapper.writeValue(temp.toFile(), sample);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        byId.put(sample.getId(), sample);
        return sample;
    }

    public synchronized boolean delete(Long id) throws IOException {
        CaptureSample removed = byId.remove(id);
        if (removed == null) {
            return false;
        }
        Files.deleteIfExists(directory.resolve(String.valueOf(removed.getIndicatorProfileNo())).resolve(id + ".json"));
        return true;
    }

    public synchronized void deleteByProfile(Long profileId) throws IOException {
        for (CaptureSample sample : findByProfile(profileId)) {
            delete(sample.getId());
        }
    }
}
