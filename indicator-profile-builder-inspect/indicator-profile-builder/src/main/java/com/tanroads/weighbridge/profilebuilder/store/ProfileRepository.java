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
import com.tanroads.weighbridge.profilebuilder.model.IndicatorProfile;

/**
 * File-per-profile JSON store: {@code <dataDir>/profiles/<id>.json}. Each file IS the
 * profile in the exact shape the main system imports, so a technician can also just copy
 * a file out of this folder. Writes are atomic (temp file + move) so a crash mid-write
 * never leaves a half-written profile behind.
 */
public class ProfileRepository {

    private final Path directory;
    private final ObjectMapper mapper;
    private final Map<Long, IndicatorProfile> byId = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public ProfileRepository(Path directory, ObjectMapper mapper) throws IOException {
        this.directory = directory;
        this.mapper = mapper;
        Files.createDirectories(directory);
        load();
    }

    private void load() throws IOException {
        long maxId = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : stream) {
                IndicatorProfile profile = mapper.readValue(file.toFile(), IndicatorProfile.class);
                if (profile.getId() == null) {
                    continue;
                }
                byId.put(profile.getId(), profile);
                maxId = Math.max(maxId, profile.getId());
            }
        }
        nextId.set(maxId + 1);
    }

    public Path directory() {
        return directory;
    }

    public List<IndicatorProfile> findAll() {
        List<IndicatorProfile> all = new ArrayList<>(byId.values());
        all.sort(Comparator.comparing(IndicatorProfile::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(IndicatorProfile::getProfileVersion, Comparator.nullsLast(Comparator.naturalOrder())));
        return all;
    }

    public Optional<IndicatorProfile> findById(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    public synchronized IndicatorProfile save(IndicatorProfile profile) throws IOException {
        if (profile.getId() == null) {
            profile.setId(nextId.getAndIncrement());
        } else {
            nextId.accumulateAndGet(profile.getId() + 1, Math::max);
        }
        Path target = fileFor(profile.getId());
        Path temp = directory.resolve(profile.getId() + ".json.tmp");
        mapper.writeValue(temp.toFile(), profile);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        byId.put(profile.getId(), profile);
        return profile;
    }

    public synchronized boolean delete(Long id) throws IOException {
        IndicatorProfile removed = byId.remove(id);
        if (removed == null) {
            return false;
        }
        Files.deleteIfExists(fileFor(id));
        return true;
    }

    public Path fileFor(Long id) {
        return directory.resolve(id + ".json");
    }
}
