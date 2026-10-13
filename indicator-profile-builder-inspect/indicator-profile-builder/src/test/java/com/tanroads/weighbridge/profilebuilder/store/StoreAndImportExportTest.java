package com.tanroads.weighbridge.profilebuilder.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanroads.weighbridge.profilebuilder.model.CaptureSample;
import com.tanroads.weighbridge.profilebuilder.model.CaptureSampleLabel;
import com.tanroads.weighbridge.profilebuilder.model.ConnectionType;
import com.tanroads.weighbridge.profilebuilder.model.ExtractionRule;
import com.tanroads.weighbridge.profilebuilder.model.FramingMode;
import com.tanroads.weighbridge.profilebuilder.model.IndicatorProfile;
import com.tanroads.weighbridge.profilebuilder.model.ProfileApprovalStatus;
import com.tanroads.weighbridge.profilebuilder.model.ReadMode;
import com.tanroads.weighbridge.profilebuilder.service.ProfileImportExportService;

class StoreAndImportExportTest {

    private static final String CRLF = "" + (char) 13 + (char) 10;

    private static IndicatorProfile sample() {
        return new IndicatorProfile().setName("Avery ZM301 v1").setManufacturer("Avery Weigh-Tronix").setModel("ZM301")
                .setConnectionType(ConnectionType.SERIAL).setSerialPort("COM3").setBaudRate(9600)
                .setReadMode(ReadMode.CONTINUOUS).setFramingMode(FramingMode.DELIMITER).setMessageDelimiter(CRLF)
                .setExtractionRule(ExtractionRule.FIRST_NUMBER).setWeightUnit("kg")
                .setApprovalStatus(ProfileApprovalStatus.APPROVED).setCreatedOn(java.time.Instant.now());
    }

    @Test
    void repositoryPersistsAndReloadsFromDisk(@TempDir Path dir) throws Exception {
        ObjectMapper mapper = JsonMapperFactory.create();
        ProfileRepository repo = new ProfileRepository(dir.resolve("profiles"), mapper);
        IndicatorProfile saved = repo.save(sample());
        assertNotNull(saved.getId());
        assertTrue(Files.exists(repo.fileFor(saved.getId())));

        ProfileRepository reloaded = new ProfileRepository(dir.resolve("profiles"), mapper);
        assertEquals(1, reloaded.findAll().size());
        assertEquals(CRLF, reloaded.findById(saved.getId()).get().getMessageDelimiter());
        IndicatorProfile second = reloaded.save(sample().setName("B"));
        assertEquals(saved.getId() + 1, second.getId());
        assertTrue(reloaded.delete(saved.getId()));
        assertFalse(Files.exists(repo.fileFor(saved.getId())));
    }

    @Test
    void sampleRepositoryGroupsByProfile(@TempDir Path dir) throws Exception {
        ObjectMapper mapper = JsonMapperFactory.create();
        SampleRepository repo = new SampleRepository(dir.resolve("samples"), mapper);
        repo.save(new CaptureSample().setIndicatorProfileNo(1L).setLabel(CaptureSampleLabel.STABLE_SAMPLE).setRawAscii("a").setCapturedOn(java.time.Instant.now()));
        repo.save(new CaptureSample().setIndicatorProfileNo(2L).setLabel(CaptureSampleLabel.UNKNOWN_SAMPLE).setRawAscii("b").setCapturedOn(java.time.Instant.now()));
        assertEquals(1, repo.findByProfile(1L).size());
        SampleRepository reloaded = new SampleRepository(dir.resolve("samples"), mapper);
        assertEquals(1, reloaded.findByProfile(2L).size());
        reloaded.deleteByProfile(2L);
        assertEquals(0, reloaded.findByProfile(2L).size());
    }

    @Test
    void exportUsesMainSystemPropertyNamesAndImportLandsAsDraft() throws Exception {
        ObjectMapper mapper = JsonMapperFactory.create();
        ProfileImportExportService service = new ProfileImportExportService(mapper);
        IndicatorProfile original = sample().setId(99L);
        CaptureSample evidence = new CaptureSample().setId(5L).setIndicatorProfileNo(99L).setLabel(CaptureSampleLabel.STABLE_SAMPLE).setRawAscii("ST,GS, 12340 kg" + CRLF);
        String json = service.toJson(original, List.of(evidence), "test");

        JsonNode tree = mapper.readTree(json);
        for (String field : new String[]{"name", "manufacturer", "model", "connectionType", "serialPort", "baudRate", "readMode",
                "framingMode", "messageDelimiter", "extractionRule", "weightUnit", "stabilityRule", "approvalStatus", "profileVersion"}) {
            assertTrue(tree.has(field), "missing main-system field " + field);
        }
        assertEquals("SERIAL", tree.get("connectionType").asText());
        assertEquals(CRLF, tree.get("messageDelimiter").asText());
        assertNull(tree.get("createdBy").isNull() ? null : tree.get("createdBy"), "createdBy must stay null (main system expects a Long)");

        ProfileImportExportService.ImportResult result = service.importAsDraft(json);
        assertNull(result.profile().getId());
        assertEquals(ProfileApprovalStatus.DRAFT, result.profile().getApprovalStatus());
        assertNull(result.profile().getCreatedOn());
        assertEquals("COM3", result.profile().getSerialPort());
        assertEquals(1, result.samples().size());
        assertNull(result.samples().get(0).getId());
    }

    @Test
    void importAcceptsMainSystemStyleJsonWithEpochDates() throws Exception {
        ObjectMapper mapper = JsonMapperFactory.create();
        ProfileImportExportService service = new ProfileImportExportService(mapper);
        String mainSystemJson = "{\"id\":3,\"name\":\"From HQ\",\"connectionType\":\"TCP\",\"tcpHost\":\"10.0.0.5\",\"tcpPort\":4001,"
                + "\"readMode\":\"CONTINUOUS\",\"framingMode\":\"DELIMITER\",\"messageDelimiter\":\"\\r\\n\",\"extractionRule\":\"FIRST_NUMBER\","
                + "\"weightUnit\":\"kg\",\"stabilityRule\":\"NONE\",\"approvalStatus\":\"DEPLOYED\",\"createdOn\":1756800000000,"
                + "\"validatedOn\":1756800000000,\"validatedBy\":12,\"active\":true}";
        ProfileImportExportService.ImportResult result = service.importAsDraft(mainSystemJson);
        assertEquals("From HQ", result.profile().getName());
        assertEquals(CRLF, result.profile().getMessageDelimiter());
        assertEquals(ProfileApprovalStatus.DRAFT, result.profile().getApprovalStatus());
        assertNull(result.profile().getValidatedBy());
        Map<String, Object> bundle = service.exportBundle(result.profile(), List.of(), "t");
        assertEquals(ProfileImportExportService.EXPORT_FORMAT, bundle.get("exportFormat"));
    }
}
