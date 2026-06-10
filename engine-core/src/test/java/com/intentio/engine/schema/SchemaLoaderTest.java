package com.intentio.engine.schema;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class SchemaLoaderTest {

    private static Path schemaDir() {
        return Paths.get("src/test/resources/test-schema");
    }

    @Test
    void loadsAllEntities() {
        SchemaRegistry reg = SchemaLoader.loadDirectory(schemaDir());
        assertNotNull(reg.find("Prescription").orElse(null));
        assertNotNull(reg.find("PrescriptionItem").orElse(null));
        assertNotNull(reg.find("Drug").orElse(null));
        assertNotNull(reg.find("DrugStock").orElse(null));
    }

    @Test
    void parsesFieldsCorrectly() {
        SchemaRegistry reg = SchemaLoader.loadDirectory(schemaDir());
        EntityDef rx = reg.require("Prescription");
        assertEquals("prescription", rx.table());

        FieldDef id = rx.fields().get("id");
        assertTrue(id.pk());
        assertTrue(id.autoIncrement());
        assertEquals(FieldType.LONG, id.type());

        FieldDef patient = rx.fields().get("patient");
        assertTrue(patient.required());
        assertEquals(Integer.valueOf(64), patient.length());

        FieldDef status = rx.fields().get("status");
        assertEquals(FieldType.ENUM, status.type());
        assertEquals(java.util.List.of("draft", "issued", "cancelled"), status.enumValues());
        assertEquals("draft", status.defaultValue());

        FieldDef weight = reg.require("PrescriptionItem").fields().get("weight");
        assertEquals(FieldType.DECIMAL, weight.type());
        assertEquals(Integer.valueOf(8), weight.precision());
        assertEquals(Integer.valueOf(3), weight.scale());
    }

    @Test
    void parsesRelations() {
        SchemaRegistry reg = SchemaLoader.loadDirectory(schemaDir());
        RelationDef items = reg.require("Prescription").relation("items").orElseThrow();
        assertEquals(RelationDef.Kind.HAS_MANY, items.kind());
        assertEquals("PrescriptionItem", items.targetEntity());
        assertEquals("prescription_id", items.fk());

        RelationDef drug = reg.require("PrescriptionItem").relation("drug").orElseThrow();
        assertEquals(RelationDef.Kind.BELONGS_TO, drug.kind());
        assertEquals("Drug", drug.targetEntity());
    }

    @Test
    void parsesIntegrityRules() {
        SchemaRegistry reg = SchemaLoader.loadDirectory(schemaDir());
        var rxIntegrity = reg.require("Prescription").integrity();
        assertEquals(1, rxIntegrity.onCreate().size());
        Rule reqHas = rxIntegrity.onCreate().get(0);
        assertEquals(Rule.Type.REQUIRE_HAS, reqHas.type());
        assertEquals("items", reqHas.param("relation"));
        assertEquals(1, reqHas.param("min"));

        var itemIntegrity = reg.require("PrescriptionItem").integrity();
        Rule stock = itemIntegrity.onCreate().get(0);
        assertEquals(Rule.Type.STOCK_CHECK, stock.type());
        assertEquals("DrugStock", stock.param("via"));
        assertEquals("库存不足", stock.message());

        var stockField = reg.require("DrugStock").integrity().fieldRules().get("quantity");
        assertNotNull(stockField);
        assertEquals("quantity >= 0", stockField.param("rule"));
    }

    @Test
    void buildsRelationGraph() {
        SchemaRegistry reg = SchemaLoader.loadDirectory(schemaDir());
        var graph = reg.relationGraph();
        var path = graph.path("Prescription", "items.drug");
        assertEquals(2, path.size());
        assertEquals("PrescriptionItem", path.get(0).to());
        assertEquals("Drug", path.get(1).to());
    }
}
