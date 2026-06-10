package com.intentio.engine.example;

import com.intentio.engine.SchemaEngine;
import com.intentio.engine.intent.IntentGroup;
import com.intentio.engine.intent.Op;
import com.intentio.engine.intent.QueryIntent;
import com.intentio.engine.result.IntentResult;
import com.intentio.engine.result.QueryResult;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class PrescriptionIntegrationTest {

    private static DataSource dataSource;
    private static SchemaEngine engine;

    @BeforeAll
    static void setUpAll() throws Exception {
        dataSource = buildDataSource();
        Path schemaDir = Paths.get("src/main/resources/schema");
        engine = SchemaEngine.load(schemaDir, dataSource);
    }

    @BeforeEach
    void resetDb() throws Exception {
        String ddl = readResource("/schema.sql");
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            for (String stmt : ddl.split(";")) {
                String s = stmt.trim();
                if (!s.isEmpty()) st.execute(s);
            }
            st.execute("INSERT INTO drug (id, name, unit, category) VALUES (5, '黄连', 'g', 'chinese')");
            st.execute("INSERT INTO drug (id, name, unit, category) VALUES (8, '板蓝根', 'g', 'chinese')");
            st.execute("INSERT INTO drug_stock (drug_id, quantity) VALUES (5, 20.000)");
            st.execute("INSERT INTO drug_stock (drug_id, quantity) VALUES (8, 5.000)");
        }
    }

    @Test
    void createsPrescriptionWithItems() throws Exception {
        IntentResult result = engine.execute(IntentGroup.of(
            Op.insert("Prescription", Map.of(
                "clinic_id", 1,
                "patient",   "张三",
                "issued_at", "2026-06-10"
            )).as("rx"),
            Op.insert("PrescriptionItem", Map.of(
                "prescription_id", "@rx",
                "drug_id",         5,
                "weight",          "15.000",
                "unit_price",      "2.50"
            )),
            Op.insert("PrescriptionItem", Map.of(
                "prescription_id", "@rx",
                "drug_id",         8,
                "weight",          "4.000",
                "unit_price",      "1.80"
            ))
        ));

        assertTrue(result.ok(), () -> "errors: " + result.errors());
        assertNotNull(result.generatedId("rx"));

        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            var rs1 = st.executeQuery("SELECT COUNT(*) FROM prescription");
            rs1.next(); assertEquals(1, rs1.getInt(1));
            var rs2 = st.executeQuery("SELECT COUNT(*) FROM prescription_item");
            rs2.next(); assertEquals(2, rs2.getInt(1));
        }
    }

    @Test
    void rejectsPrescriptionWithoutItems() throws Exception {
        IntentResult result = engine.execute(IntentGroup.of(
            Op.insert("Prescription", Map.of(
                "clinic_id", 1,
                "patient",   "李四",
                "issued_at", "2026-06-10"
            ))
        ));

        assertFalse(result.ok());
        assertEquals(1, result.errors().size());
        assertEquals("require_has", result.errors().get(0).rule());

        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            var rs = st.executeQuery("SELECT COUNT(*) FROM prescription");
            rs.next(); assertEquals(0, rs.getInt(1), "事务应回滚");
        }
    }

    @Test
    void rejectsWhenStockInsufficient() throws Exception {
        IntentResult result = engine.execute(IntentGroup.of(
            Op.insert("Prescription", Map.of(
                "clinic_id", 1,
                "patient",   "王五",
                "issued_at", "2026-06-10"
            )).as("rx"),
            Op.insert("PrescriptionItem", Map.of(
                "prescription_id", "@rx",
                "drug_id",         8,
                "weight",          "10.000",
                "unit_price",      "1.80"
            ))
        ));

        assertFalse(result.ok());
        assertEquals(1, result.errors().size());
        var err = result.errors().get(0);
        assertEquals("stock_check", err.rule());
        assertTrue(err.message().contains("库存不足"), "实际消息: " + err.message());

        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            var rs = st.executeQuery("SELECT COUNT(*) FROM prescription");
            rs.next(); assertEquals(0, rs.getInt(1), "事务应回滚");
        }
    }

    @Test
    void queryWithNestedInclude() throws Exception {
        engine.execute(IntentGroup.of(
            Op.insert("Prescription", Map.of(
                "clinic_id", 1, "patient", "赵六", "issued_at", "2026-06-10"
            )).as("rx"),
            Op.insert("PrescriptionItem", Map.of(
                "prescription_id", "@rx",
                "drug_id", 5, "weight", "5.000", "unit_price", "2.50"
            )),
            Op.insert("PrescriptionItem", Map.of(
                "prescription_id", "@rx",
                "drug_id", 8, "weight", "3.000", "unit_price", "1.80"
            ))
        ));

        QueryResult q = engine.query(
            QueryIntent.from("Prescription").include("items.drug").limit(10)
        );

        assertEquals(1, q.size());
        Map<String, Object> rx = q.first();
        assertEquals("赵六", rx.get("patient"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) rx.get("items");
        assertNotNull(items);
        assertEquals(2, items.size());
        Map<String, Object> firstDrug = (Map<String, Object>) items.get(0).get("drug");
        assertNotNull(firstDrug);
        assertNotNull(firstDrug.get("name"));
    }

    private static DataSource buildDataSource() throws IOException {
        Properties props = new Properties();
        try (InputStream in = PrescriptionIntegrationTest.class.getResourceAsStream("/application.properties")) {
            if (in != null) props.load(in);
        }
        String url = props.getProperty("jdbc.url", "");
        HikariConfig cfg = new HikariConfig();
        if (url == null || url.isBlank()) {
            cfg.setJdbcUrl("jdbc:h2:mem:intentio;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            cfg.setUsername("sa");
            cfg.setPassword("");
            cfg.setDriverClassName("org.h2.Driver");
        } else {
            cfg.setJdbcUrl(url);
            cfg.setUsername(props.getProperty("jdbc.user", ""));
            cfg.setPassword(props.getProperty("jdbc.password", ""));
        }
        cfg.setMaximumPoolSize(4);
        return new HikariDataSource(cfg);
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = PrescriptionIntegrationTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
