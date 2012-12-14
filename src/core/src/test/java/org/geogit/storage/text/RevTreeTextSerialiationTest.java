package org.geogit.storage.text;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;

import org.geogit.api.ObjectId;
import org.geogit.api.RevObject.TYPE;
import org.geogit.api.RevTree;
import org.geogit.storage.ObjectReader;
import org.geogit.storage.ObjectSerialisingFactory;
import org.geogit.storage.RevTreeSerializationTest;
import org.junit.Test;

public class RevTreeTextSerialiationTest extends RevTreeSerializationTest {

    ObjectSerialisingFactory factory = new TextSerializationFactory();

    @Override
    protected ObjectSerialisingFactory getFactory() {
        return factory;
    }

    @Test
    public void testMalformedSerializedObject() throws Exception {

        // TODO: add more cases here

        // a wrong type
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8");
        writer.write(TYPE.FEATURE.name() + "\n");
        writer.flush();
        ObjectReader<RevTree> reader = factory.createRevTreeReader();
        try {
            reader.read(ObjectId.forString("ID_STRING"),
                    new ByteArrayInputStream(out.toByteArray()));
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().equals("Wrong type: FEATURE"));
        }

    }

}