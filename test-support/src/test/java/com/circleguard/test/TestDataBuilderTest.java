package com.circleguard.test;

import org.junit.jupiter.api.RepeatedTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestDataBuilderTest {

    @RepeatedTest(5)
    void randomAnonymousId_returnsUuid() {
        var id = TestDataBuilder.randomAnonymousId();
        assertNotNull(id);
        assertEquals(4, id.version());
    }
}
