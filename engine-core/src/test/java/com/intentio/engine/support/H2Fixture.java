package com.intentio.engine.support;

import com.intentio.engine.SchemaEngine;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * engine-core 的最小 H2 内存测试夹具：每次 {@link #create()} 建一个全新内存库、
 * 执行 test-ddl-ext.sql、从 test-schema-ext 装载 SchemaEngine。
 */
public final class H2Fixture {

    private static final AtomicInteger SEQ = new AtomicInteger();

    public final DataSource dataSource;
    public final SchemaEngine engine;

    private H2Fixture(DataSource dataSource, SchemaEngine engine) {
        this.dataSource = dataSource;
        this.engine = engine;
    }

    public static H2Fixture create() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:ext_" + SEQ.incrementAndGet()
            + ";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        ds.setPassword("");
        runDdl(ds);
        SchemaEngine engine = SchemaEngine.load(Paths.get("src/test/resources/test-schema-ext"), ds);
        return new H2Fixture(ds, engine);
    }

    private static void runDdl(DataSource ds) {
        String ddl = readResource("/test-ddl-ext.sql");
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            for (String stmt : ddl.split(";")) {
                String s = stmt.trim();
                if (!s.isEmpty()) st.execute(s);
            }
        } catch (Exception e) {
            throw new RuntimeException("DDL failed", e);
        }
    }

    private static String readResource(String path) {
        try (InputStream in = H2Fixture.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("resource not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("read resource failed: " + path, e);
        }
    }
}
