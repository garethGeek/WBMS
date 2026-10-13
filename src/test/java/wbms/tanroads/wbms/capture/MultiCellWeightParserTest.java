package wbms.tanroads.wbms.capture;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiCellWeightParserTest {

    @Test
    void readsLatestFourCellsFromIndicatorStream() {
        assertEquals(List.of(0d, 0d, 0d, 100d),
                MultiCellWeightParser.parseLatest("00, 00, 00, 100", 4));
    }

    @Test
    void usesLatestGroupWhenSeveralGroupsArriveTogether() {
        assertEquals(List.of(0d, 100d, 0d, 0d),
                MultiCellWeightParser.parseLatest("00, 00, 00, 100, 0, 100, 0, 0", 4));
    }

    @Test
    void readsLabeledCellsWithoutTreatingLabelsAsValues() {
        assertEquals(List.of(565d, 300d, 600d, 100d),
                MultiCellWeightParser.parseLatest("C1: 565, C2: 300, C3: 600, C4: 100", 4));
    }

    @Test
    void readsTanroadsKgDelimitedAxleGroups() {
        assertEquals(List.of(565d, 300d, 600d, 100d),
                MultiCellWeightParser.parseLatest("565kg 300kg 600kg 100kg", 4));
    }

    @Test
    void readsFourWeightsSeparatedByLiteral049() {
        assertEquals(List.of(566d, 300d, 601d, 100d),
                MultiCellWeightParser.parseLatest("566049300049601049100", 4));
    }

    @Test
    void readsFourWeightsSeparatedBySpaces() {
        assertEquals(List.of(140d, 90d, 70d, 15d), MultiCellWeightParser.parseLatest("140 90 70 15", 4, "SPACE"));
    }

    @Test
    void treatsFifthTanroadsValueAsIndicatorGvm() {
        var reading = MultiCellWeightParser.parseWithGvm("00, 1500, 22550, 16650, 40500", 4, ",", false);
        assertEquals(List.of(0d, 1500d, 22550d, 16650d), reading.cellWeightsKg());
        assertEquals(40500d, reading.indicatorGvmKg());
    }

    @Test
    void calculatesGvmWhenIndicatorGvmIsZero() {
        var reading = MultiCellWeightParser.parseWithGvm("00|1500|22550|16650|00", 4, "|", true);
        assertEquals(List.of(0d, 1500d, 22550d, 16650d), reading.cellWeightsKg());
        assertEquals(0d, reading.indicatorGvmKg());
    }
}
