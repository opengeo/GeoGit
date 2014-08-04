/* Copyright (c) 2013 OpenPlans. All rights reserved.
 * This code is licensed under the BSD New License, available at the root
 * application directory.
 */
package org.geogit.geotools.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.geogit.api.NodeRef;
import org.geogit.api.ObjectId;
import org.geogit.api.Ref;
import org.geogit.api.RevCommit;
import org.geogit.api.plumbing.LsTreeOp;
import org.geogit.api.plumbing.LsTreeOp.Strategy;
import org.geogit.api.porcelain.BranchCreateOp;
import org.geogit.api.porcelain.CommitOp;
import org.geogit.geotools.data.GeoGitDataStore.ChangeType;
import org.geogit.test.integration.RepositoryTestCase;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureSource;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.NameImpl;
import org.geotools.geometry.jts.GeometryBuilder;
import org.junit.Test;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;

import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;

public class GeoGitDataStoreTest extends RepositoryTestCase {

    private GeoGitDataStore dataStore;

    @Override
    protected void setUpInternal() throws Exception {
        dataStore = new GeoGitDataStore(geogit);
    }

    @Override
    protected void tearDownInternal() throws Exception {
        dataStore.dispose();
        dataStore = null;
    }

    @Test
    public void testDispose() {
        assertTrue(geogit.isOpen());
        dataStore.dispose();
        assertFalse(geogit.isOpen());
    }

    private List<String> getTypeNames(String head) {
        Iterator<NodeRef> typeTrees = geogit.command(LsTreeOp.class)
                .setStrategy(Strategy.TREES_ONLY).setReference(head).call();
        List<String> typeNames = Lists.newArrayList(Iterators.transform(typeTrees,
                new Function<NodeRef, String>() {

                    @Override
                    public String apply(NodeRef input) {
                        return input.name();
                    }
                }));
        return typeNames;
    }

    @Test
    public void testCreateSchema() throws IOException {
        final SimpleFeatureType featureType = super.linesType;
        dataStore.createSchema(featureType);

        List<String> typeNames;
        typeNames = getTypeNames(Ref.HEAD);
        assertEquals(1, typeNames.size());
        assertEquals(linesName, typeNames.get(0));

        dataStore.createSchema(super.pointsType);

        typeNames = getTypeNames(Ref.HEAD);
        assertEquals(2, typeNames.size());
        assertTrue(typeNames.contains(linesName));
        assertTrue(typeNames.contains(pointsName));

        try {
            dataStore.createSchema(super.pointsType);
            fail("Expected IOException on existing type");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("already exists"));
        }
    }

    @Test
    public void testCreateSchemaOnBranch() throws IOException {
        final String branchName = "testBranch";
        geogit.command(BranchCreateOp.class).setName(branchName).setOrphan(true).call();

        dataStore.setHead(branchName);
        final SimpleFeatureType featureType = super.linesType;
        dataStore.createSchema(featureType);

        List<String> typeNames;
        typeNames = getTypeNames(Ref.HEAD);
        assertTrue(typeNames.isEmpty());

        typeNames = getTypeNames(branchName);
        assertEquals(1, typeNames.size());
        assertEquals(linesName, typeNames.get(0));

        dataStore.createSchema(super.pointsType);

        typeNames = getTypeNames(Ref.HEAD);
        assertTrue(typeNames.isEmpty());

        typeNames = getTypeNames(branchName);
        assertEquals(2, typeNames.size());
        assertTrue(typeNames.contains(linesName));
        assertTrue(typeNames.contains(pointsName));
    }

    @Test
    public void testGetNames() throws Exception {

        assertEquals(0, dataStore.getNames().size());

        insertAndAdd(points1);
        assertEquals(0, dataStore.getNames().size());
        commit();

        assertEquals(1, dataStore.getNames().size());

        insertAndAdd(lines1);
        assertEquals(1, dataStore.getNames().size());
        commit();

        assertEquals(2, dataStore.getNames().size());

        List<Name> names = dataStore.getNames();
        // ContentDataStore doesn't support native namespaces
        // assertTrue(names.contains(RepositoryTestCase.linesTypeName));
        // assertTrue(names.contains(RepositoryTestCase.pointsTypeName));
        assertTrue(names.contains(new NameImpl(pointsName)));
        assertTrue(names.contains(new NameImpl(linesName)));
    }

    @Test
    public void testGetTypeNames() throws Exception {

        assertEquals(0, dataStore.getTypeNames().length);

        insertAndAdd(lines1);
        assertEquals(0, dataStore.getTypeNames().length);
        commit();

        assertEquals(1, dataStore.getTypeNames().length);

        insertAndAdd(points1);
        assertEquals(1, dataStore.getTypeNames().length);
        commit();

        assertEquals(2, dataStore.getTypeNames().length);

        List<String> simpleNames = Arrays.asList(dataStore.getTypeNames());

        assertTrue(simpleNames.contains(linesName));
        assertTrue(simpleNames.contains(pointsName));
    }

    @Test
    public void testGetSchemaName() throws Exception {
        try {
            dataStore.getSchema(RepositoryTestCase.linesTypeName);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("does not exist"));
        }

        insertAndAdd(lines1);
        try {
            dataStore.getSchema(RepositoryTestCase.linesTypeName);
            fail("Expected IOE as type hasn't been committed");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("does not exist"));
        }
        commit();
        SimpleFeatureType lines = dataStore.getSchema(RepositoryTestCase.linesTypeName);
        assertEquals(super.linesType, lines);

        try {
            dataStore.getSchema(RepositoryTestCase.pointsTypeName);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(true);
        }

        insertAndAdd(points1);
        commit();
        SimpleFeatureType points = dataStore.getSchema(RepositoryTestCase.pointsTypeName);
        assertEquals(super.pointsType, points);
    }

    private ObjectId commit() {
        RevCommit c = geogit.command(CommitOp.class).call();
        return c.getId();
    }

    @Test
    public void testGetSchemaProvidedNamespace() throws Exception {
        String namespace = "http://www.geogit.org/test";
        dataStore.setNamespaceURI(namespace);
        insertAndAdd(lines1);
        commit();
        SimpleFeatureType lines = dataStore.getSchema(RepositoryTestCase.linesTypeName);
        Name expectedName = new NameImpl(namespace, linesName);
        assertEquals(expectedName, lines.getName());
        assertEquals(super.linesType.getAttributeDescriptors(), lines.getAttributeDescriptors());

        insertAndAdd(points1);
        commit();
        SimpleFeatureType points = dataStore.getSchema(RepositoryTestCase.pointsTypeName);
        assertEquals(new NameImpl(namespace, pointsName), points.getName());
        assertEquals(super.pointsType.getAttributeDescriptors(), points.getAttributeDescriptors());
    }

    @Test
    public void testGetSchemaString() throws Exception {
        try {
            dataStore.getSchema(RepositoryTestCase.linesName);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(true);
        }

        insertAndAdd(lines1);
        commit();
        SimpleFeatureType lines = dataStore.getSchema(RepositoryTestCase.linesName);
        assertEquals(super.linesType.getAttributeDescriptors(), lines.getAttributeDescriptors());

        try {
            dataStore.getSchema(RepositoryTestCase.pointsName);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(true);
        }

        insertAndAdd(points1);
        commit();
        SimpleFeatureType points = dataStore.getSchema(RepositoryTestCase.pointsName);
        assertEquals(super.pointsType, points);
    }

    @Test
    public void testGetFeatureSourceName() throws Exception {
        try {
            dataStore.getFeatureSource(RepositoryTestCase.linesTypeName);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(true);
        }

        SimpleFeatureSource source;

        insertAndAdd(lines1);
        try {
            dataStore.getFeatureSource(RepositoryTestCase.linesTypeName);
            fail("Expected IOE as feature typ is not committed yet");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("does not exist"));
        }
        commit();
        source = dataStore.getFeatureSource(RepositoryTestCase.linesTypeName);
        assertTrue(source instanceof GeogitFeatureStore);

        try {
            dataStore.getFeatureSource(RepositoryTestCase.pointsTypeName);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(true);
        }

        insertAndAdd(points1);
        commit();
        source = dataStore.getFeatureSource(RepositoryTestCase.pointsTypeName);
        assertTrue(source instanceof GeogitFeatureStore);
    }

    @Test
    public void testGetFeatureSourceString() throws Exception {
        try {
            dataStore.getFeatureSource(RepositoryTestCase.linesName);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(true);
        }

        SimpleFeatureSource source;

        insertAndAdd(lines1);
        commit();
        source = dataStore.getFeatureSource(RepositoryTestCase.linesName);
        assertTrue(source instanceof GeogitFeatureStore);

        try {
            dataStore.getFeatureSource(RepositoryTestCase.pointsName);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(true);
        }

        insertAndAdd(points1);
        commit();
        source = dataStore.getFeatureSource(RepositoryTestCase.pointsName);
        assertTrue(source instanceof GeogitFeatureStore);
    }

    @Test
    public void testFeatureWriterAppend() throws Exception {
        dataStore.createSchema(linesType);

        Transaction tx = new DefaultTransaction();
        FeatureWriter<SimpleFeatureType, SimpleFeature> fw = dataStore.getFeatureWriterAppend(
                linesTypeName.getLocalPart(), tx);

        LineString line = new GeometryBuilder().lineString(0, 0, 1, 1);
        SimpleFeature f = (SimpleFeature) fw.next();
        f.setAttribute("sp", "foo");
        f.setAttribute("ip", 10);
        f.setAttribute("pp", line);

        fw.write();
        fw.close();

        tx.commit();

        FeatureSource<SimpleFeatureType, SimpleFeature> source = dataStore
                .getFeatureSource(linesTypeName);
        assertEquals(1, source.getCount(null));

        FeatureReader<SimpleFeatureType, SimpleFeature> r = dataStore.getFeatureReader(new Query(
                linesTypeName.getLocalPart()), Transaction.AUTO_COMMIT);
        assertTrue(r.hasNext());

        f = r.next();
        assertEquals("foo", f.getAttribute("sp"));
        assertEquals(10, f.getAttribute("ip"));
        assertTrue(line.equals((Geometry) f.getAttribute("pp")));
    }

    @Test
    public void testGetDiffFeatureSource() throws Exception {
        insertAndAdd(points1);
        insertAndAdd(lines1, lines2);
        final ObjectId c1 = commit();
        insertAndAdd(points2);
        final ObjectId c2 = commit();
        deleteAndAdd(points2);
        final ObjectId c3 = commit();
        insertAndAdd(points1_modified);
        final ObjectId c4 = commit();

        testDiffFeatures(ObjectId.NULL, c1, 1/* added */, 0/* removed */, 0/* modified */);
        testDiffFeatures(c1, c2, 1, 0, 0);
        testDiffFeatures(c2, c1, 0, 1, 0);
        testDiffFeatures(ObjectId.NULL, c2, 2, 0, 0);

        testDiffFeatures(c3, c4, 0, 0, 1);
        testDiffFeatures(c4, c3, 0, 0, 1);

        testDiffFeatures(c2, c4, 0, 1, 1);
        testDiffFeatures(c4, c2, 1, 0, 1);
    }

    private void testDiffFeatures(ObjectId oldRoot, ObjectId newRoot, int expectedAdded,
            int expectedRemoved, int expectedChanged) throws IOException {

        dataStore.setHead(newRoot.toString());
        List<String> fids;

        ChangeType changeType = ChangeType.ADDED;
        fids = toIdList(dataStore.getDiffFeatureSource(pointsName, oldRoot.toString(), changeType)
                .getFeatures());
        assertEquals(changeType + fids.toString(), expectedAdded, fids.size());

        changeType = ChangeType.REMOVED;
        fids = toIdList(dataStore.getDiffFeatureSource(pointsName, oldRoot.toString(), changeType)
                .getFeatures());
        assertEquals(changeType + fids.toString(), expectedRemoved, fids.size());

        changeType = ChangeType.CHANGED_NEW;
        fids = toIdList(dataStore.getDiffFeatureSource(pointsName, oldRoot.toString(), changeType)
                .getFeatures());
        assertEquals(changeType + fids.toString(), expectedChanged, fids.size());

        changeType = ChangeType.CHANGED_OLD;
        fids = toIdList(dataStore.getDiffFeatureSource(pointsName, oldRoot.toString(), changeType)
                .getFeatures());
        assertEquals(changeType + fids.toString(), expectedChanged, fids.size());
    }

    private List<String> toIdList(SimpleFeatureCollection features) {
        List<SimpleFeature> list = toList(features);
        return Lists.transform(list, new Function<SimpleFeature, String>() {

            @Override
            public String apply(SimpleFeature f) {
                return f.getID();
            }
        });
    }

    private List<SimpleFeature> toList(SimpleFeatureCollection features) {
        List<SimpleFeature> list = new ArrayList<>();
        try (SimpleFeatureIterator it = features.features()) {
            while (it.hasNext()) {
                list.add(it.next());
            }
        }
        return list;
    }
}
