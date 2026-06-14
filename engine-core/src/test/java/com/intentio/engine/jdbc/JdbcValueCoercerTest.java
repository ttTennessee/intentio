package com.intentio.engine.jdbc;

import com.intentio.engine.intent.IntentGroup;
import com.intentio.engine.intent.Op;
import com.intentio.engine.intent.QueryIntent;
import com.intentio.engine.result.IntentResult;
import com.intentio.engine.result.QueryResult;
import com.intentio.engine.support.H2Fixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JdbcValueCoercerTest {

    private H2Fixture fx;

    @BeforeEach
    void setUp() {
        fx = H2Fixture.create();
    }

    @Test
    void unitCoercesJdbcDateTypes() {
        LocalDate d = LocalDate.of(2026, 6, 14);
        LocalDateTime dt = LocalDateTime.of(2026, 6, 14, 15, 30, 0);

        assertEquals(d, JdbcValueCoercer.coerceDate(Date.valueOf(d)));
        assertEquals(d, JdbcValueCoercer.coerceDate(Timestamp.valueOf(dt)));
        assertEquals(d, JdbcValueCoercer.coerceDate("2026-06-14 00:00:00"));
        assertEquals(dt, JdbcValueCoercer.coerceDateTime(Timestamp.valueOf(dt)));
        assertEquals(dt, JdbcValueCoercer.coerceDateTime("2026-06-14 15:30:00"));
    }

    @Test
    void queryReturnsLocalDateAndLocalDateTime() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("username", "eve");
        fields.put("joined_on", LocalDate.of(2026, 6, 14));
        fields.put("last_seen", LocalDateTime.of(2026, 6, 14, 15, 30, 0));
        IntentResult seed = fx.engine.execute(IntentGroup.of(Op.insert("Account", fields)));
        assertTrue(seed.ok(), () -> "seed failed: " + seed.errors());

        QueryResult result = fx.engine.query(
                QueryIntent.from("Account").filter("username", "eve"));
        Map<String, Object> row = result.first();

        assertInstanceOf(LocalDate.class, row.get("joined_on"));
        assertEquals(LocalDate.of(2026, 6, 14), row.get("joined_on"));
        assertInstanceOf(LocalDateTime.class, row.get("last_seen"));
        assertEquals(LocalDateTime.of(2026, 6, 14, 15, 30, 0), row.get("last_seen"));
    }
}
