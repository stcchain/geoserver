/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2020, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.dggs.rhealpix;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Iterators;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import jep.JepException;
import jep.SharedInterpreter;
import org.geotools.dggs.DGGSFactory;
import org.geotools.dggs.DGGSFactoryFinder;
import org.geotools.dggs.DGGSInstance;
import org.geotools.dggs.Zone;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

public class RHealPixInstanceTest {

    private static final GeometryFactory GF = new GeometryFactory();
    private static final ReferencedEnvelope WORLD =
            new ReferencedEnvelope(-180, 180, -90, 90, DefaultGeographicCRS.WGS84);
    private DGGSInstance rpix;

    @Before
    public void setup() throws IOException {
        Optional<DGGSFactory> factory =
                DGGSFactoryFinder.getExtensionFactories()
                        .filter(d -> "rHEALPix".equalsIgnoreCase(d.getId()))
                        .findFirst();
        Assume.assumeTrue(factory.isPresent());
        rpix = factory.get().createInstance(null);
    }

    @After
    public void cleanup() throws JepException {
        JEPWebRuntime.closeThreadIntepreter();
    }

    @Test
    public void getZone() throws JepException {
        Zone zone = rpix.getZone("P");
        assertNotNull(zone);
        assertEquals(0, zone.getResolution());

        // TODO: check boundary
    }

    @Test
    public void zonesFromEnvelopeWorldResZero() {
        Iterator<Zone> zonesIterator = rpix.zonesFromEnvelope(WORLD, 0);
        Set<Zone> zones = new HashSet<>();
        zonesIterator.forEachRemaining(zones::add);
        assertEquals(6, zones.size());
    }

    @Test
    public void zonesFromEnvelopeAcrossDateline() throws IOException {
        Iterator<Zone> zonesIterator =
                rpix.zonesFromEnvelope(
                        new ReferencedEnvelope(-181, -179, -10, 10, DefaultGeographicCRS.WGS84), 0);
        Set<Zone> zones = new HashSet<>();
        zonesIterator.forEachRemaining(zones::add);
        assertEquals(1, zones.size());
        assertEquals("P", zones.iterator().next().getId());
    }

    @Test
    public void testZoneAndGeometry() throws IOException, ParseException {
        Zone zone = rpix.getZone("N");
        assertEquals(90, zone.getCenter().getY(), 0d);
        Polygon polygon = zone.getBoundary();
        Polygon expected =
                (Polygon)
                        new WKTReader()
                                .read(
                                        "POLYGON ((-180 41.937853904844985, -180 90, 180 90, 180 41.937853904844985, -180"
                                                + " 41.937853904844985))");
        assertTrue(polygon.equalsExact(expected, 1e-6));
    }

    @Test
    public void countZonesFromEnvelopeWorldResZero() {
        ReferencedEnvelope envelope = WORLD;
        assertZoneCount(envelope, 0);
    }

    @Test
    public void countZonesFromEnvelopeWorldResOne() {
        ReferencedEnvelope envelope = WORLD;
        assertZoneCount(envelope, 1);
    }

    @Test
    public void countZonesFromEnvelopeWorldResTwo() {
        ReferencedEnvelope envelope =
                new ReferencedEnvelope(-1, 1, -1, 1, DefaultGeographicCRS.WGS84);
        assertZoneCount(envelope, 2);
    }

    @Test
    public void countZonesCloseToPole() {
        ReferencedEnvelope envelope =
                new ReferencedEnvelope(102, 103, 88.9, 90, DefaultGeographicCRS.WGS84);
        assertZoneCount(envelope, 4);
    }

    @Test
    public void countZonesCloseToDateline() {
        ReferencedEnvelope envelope =
                new ReferencedEnvelope(175, 176, 81.4, 81.7, DefaultGeographicCRS.WGS84);
        assertZoneCount(envelope, 1);
    }

    @Test
    @Ignore // untested yet
    public void testRandomized() {
        Random random = new Random();
        final int LOOPS = 1000;
        final int MAX_RESOLUTION = 4;
        for (int i = 0; i < LOOPS; i++) {
            double lon1 = random.nextDouble() * 360 - 180;
            double lon2 = lon1 + (random.nextDouble() * (180 - lon1));
            double lat1 = random.nextDouble() * 180 - 90;
            double lat2 = lat1 + (random.nextDouble() * (90 - lat1));
            ReferencedEnvelope envelope =
                    new ReferencedEnvelope(lon1, lon2, lat1, lat2, DefaultGeographicCRS.WGS84);
            int resolution = (int) (random.nextDouble() * MAX_RESOLUTION);
            try {
                assertZoneCount(envelope, resolution);
            } catch (AssertionError e) {
                System.out.println(envelope + " / " + resolution + " -> " + e.getMessage());
            }
        }
    }

    public void assertZoneCount(ReferencedEnvelope envelope, int resolution) {
        long count = rpix.countZonesFromEnvelope(envelope, resolution);
        int expected = Iterators.size(rpix.zonesFromEnvelope(envelope, resolution));
        List<String> expectedZones = new ArrayList<>();
        rpix.zonesFromEnvelope(envelope, resolution)
                .forEachRemaining(z -> expectedZones.add(z.getId()));
        assertEquals(
                "Expected " + expected + " " + expectedZones + " but got " + count,
                expected,
                count);
    }

    @Test
    public void testNeighborsNorthPole() {
        Iterator<Zone> iterator = rpix.neighbors("N4", 1);
        List<String> neighbors = new ArrayList<>();
        iterator.forEachRemaining(z -> neighbors.add(z.getId()));
        // round the pole we go, dart cells are not considered neighbords, only share a corner
        assertThat(neighbors, hasItems("N1", "N3", "N5", "N7"));
    }

    @Test
    public void testNeighborsDateline() {
        // pentagon on the dateline
        Iterator<Zone> iterator = rpix.neighbors("P3", 1);
        List<String> neighbors = new ArrayList<>();
        iterator.forEachRemaining(z -> neighbors.add(z.getId()));
        // dateline check, remember full side connectivity
        assertThat(neighbors, hasItems("P0", "O5", "P6", "P4"));
        assertThat(neighbors, CoreMatchers.not(hasItems("P3")));
        assertEquals(4, neighbors.size());
    }

    @Test
    public void testNeighborsDatelineRadius2() {
        // pentagon on the dateline
        Iterator<Zone> iterator = rpix.neighbors("P3", 2);
        List<String> neighbors = new ArrayList<>();
        iterator.forEachRemaining(z -> neighbors.add(z.getId()));
        // dateline check, remember full side connectivity
        assertThat(
                neighbors,
                hasItems("P0", "O2", "O5", "O8", "P6", "P1", "P4", "P7", "O4", "N8", "P5", "S2"));
        assertThat(neighbors, CoreMatchers.not(hasItems("P3")));
        assertEquals(12, neighbors.size());
    }

    @Test
    public void testNeighborsAll() {
        // pentagon at the equator/greenwitch (close to), with this distance should catch them all
        Iterator<Zone> iterator = rpix.neighbors("R3", 10);
        List<String> neighbors = new ArrayList<>();
        iterator.forEachRemaining(z -> neighbors.add(z.getId()));
        // get all
        Iterator<Zone> zonesIterator = rpix.zonesFromEnvelope(WORLD, 1);
        Set<String> expected = new HashSet<>();
        zonesIterator.forEachRemaining(z -> expected.add(z.getId()));
        expected.remove("R3"); // don't expect R3 itself
        // check same size and contents
        assertEquals(expected.size(), neighbors.size());
        assertTrue(expected.containsAll(neighbors));
    }

    @Test
    public void testChildren() throws Exception {
        String parent = "R";
        SharedInterpreter interpreter = JEPWebRuntime.INTERPRETER.get();
        // keep the resolution small, the list can grow veeeeeery fast
        for (int r = 1; r < 4; r++) {
            Set<String> actual = new HashSet<>();
            rpix.children(parent, r).forEachRemaining(z -> actual.add(z.getId()));
            RHealPixUtils.setCellId(interpreter, "id", parent);
            interpreter.exec("c = Cell(dggs, id)");
            interpreter.set("resolution", Integer.valueOf(r));
            Set<String> expected =
                    new HashSet<>(interpreter.getValue("list(c.subcells(resolution))", List.class));
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testMapPoint() throws Exception {
        Point northPole = GF.createPoint(new Coordinate(0, 90));
        // test few different resolutions
        String[] expectedIds = new String[] {"N", "N4", "N44"};
        for (int r = 0; r < expectedIds.length; r++) {
            Zone zone = rpix.point(northPole, r);
            assertEquals(expectedIds[r], zone.getId());
        }
    }

    @Test
    public void testMapPolygon() throws Exception {
        Polygon polygon =
                (Polygon) new WKTReader().read("POLYGON((-1 -1, -1 1, 1 1, 1 -1, -1 -1))");

        Set<String> actual = new HashSet<>();
        rpix.polygon(polygon, 4).forEachRemaining(z -> actual.add(z.getId()));
        // visually verified
        Set<String> expected =
                new HashSet<>(Arrays.asList("R4430", "R4434", "R4433", "R4431", "R4437", "R4436"));
        assertEquals(expected, actual);
    }
}
