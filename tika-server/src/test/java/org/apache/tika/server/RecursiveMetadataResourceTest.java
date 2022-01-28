/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.server;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.ws.rs.core.Response;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.server.resource.RecursiveMetadataResource;
import org.apache.tika.server.writer.MetadataListMessageBodyWriter;
import org.junit.Test;

public class RecursiveMetadataResourceTest extends CXFTestBase {

    private static final String FORM_PATH = "/form";
    private static final String META_PATH = "/rmeta";
    private static final String TEXT_PATH = "/text";
    private static final String IGNORE_PATH = "/ignore";
    private static final String XML_PATH = "/xml";
    private static final String UNPARSEABLE_PATH = "/somethingOrOther";
    private static final String SLASH = "/";

    private static final String TEST_RECURSIVE_DOC = "test_recursive_embedded.docx";
    private static final String TEST_NULL_POINTER = "mock/null_pointer.xml";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(RecursiveMetadataResource.class);
        sf.setResourceProvider(RecursiveMetadataResource.class,
                new SingletonResourceProvider(new RecursiveMetadataResource()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new MetadataListMessageBodyWriter());
        sf.setProviders(providers);
    }

    @Test
    public void testGZOut() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .acceptEncoding("gzip")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));

        Reader reader = new InputStreamReader(new GzipCompressorInputStream((InputStream) response.getEntity()), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        assertEquals("Microsoft Office Word", metadataList.get(0).get(OfficeOpenXMLExtended.APPLICATION));
        assertContains("plundered our seas", metadataList.get(6).get("X-TIKA:content"));

        assertEquals("a38e6c7b38541af87148dee9634cb811", metadataList.get(10).get("X-TIKA:digest:MD5"));
    }

    @Test
    public void testGZIn() throws Exception {

        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .encoding("gzip")
                .put(gzip(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC)));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        assertEquals("Microsoft Office Word", metadataList.get(0).get(OfficeOpenXMLExtended.APPLICATION));
        assertContains("plundered our seas", metadataList.get(6).get("X-TIKA:content"));

        assertEquals("a38e6c7b38541af87148dee9634cb811", metadataList.get(10).get("X-TIKA:digest:MD5"));

    }

    @Test
    public void testSimpleWord() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TEST_RECURSIVE_DOC));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);

        assertEquals(12, metadataList.size());
        assertEquals("Microsoft Office Word", metadataList.get(0).get("Application-Name"));
        assertContains("plundered our seas", metadataList.get(6).get("X-TIKA:content"));

        assertEquals("a38e6c7b38541af87148dee9634cb811", metadataList.get(10).get("X-TIKA:digest:MD5"));
    }

    @Test
    public void testPasswordProtected() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .type("application/vnd.ms-excel")
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TikaResourceTest.TEST_PASSWORD_PROTECTED));

        // Won't work, no password given
        assertEquals(200, response.getStatus());
        // Check results
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);

        assertNotNull(metadataList.get(0).get(TikaCoreProperties.CREATOR));
        assertContains("org.apache.tika.exception.EncryptedDocumentException", metadataList.get(0).get(RecursiveParserWrapperHandler.CONTAINER_EXCEPTION));
        // Try again, this time with the password
        response = WebClient
                .create(endPoint + META_PATH)
                .type("application/vnd.ms-excel")
                .accept("application/json")
                .header("Password", "password")
                .put(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_PASSWORD_PROTECTED));

        // Will work
        assertEquals(200, response.getStatus());

        // Check results
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertNotNull(metadataList.get(0).get(TikaCoreProperties.CREATOR));
        assertEquals("pavel", metadataList.get(0).get(TikaCoreProperties.CREATOR));
    }

    @Test
    public void testHandlerType() throws Exception {
        //default unspecified
        Response response = WebClient
                .create(endPoint+META_PATH)
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TEST_RECURSIVE_DOC));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        String content = metadataList.get(6).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT).trim();
        assertTrue(content.startsWith("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));

        //extra slash
        response = WebClient
                .create(endPoint + META_PATH + SLASH)
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        content = metadataList.get(6).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT).trim();
        assertTrue(content.startsWith("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));

        //unparseable
        response = WebClient
                .create(endPoint + META_PATH + UNPARSEABLE_PATH)
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        content = metadataList.get(6).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT).trim();
        assertTrue(content.startsWith("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));

        //xml
        response = WebClient
                .create(endPoint + META_PATH + XML_PATH)
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        content = metadataList.get(6).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT).trim();
        assertTrue(content.startsWith("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));

        //text
        response = WebClient
                .create(endPoint + META_PATH + TEXT_PATH)
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        content = metadataList.get(6).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT).trim();
        assertTrue(content.startsWith("embed_3"));

        //ignore
        response = WebClient
                .create(endPoint + META_PATH + IGNORE_PATH)
                .accept("application/json")
                .put(ClassLoader
                        .getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        assertNull(metadataList.get(6).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));

    }

    @Test
    public void testHandlerTypeInMultipartXML() throws Exception {
        //default unspecified
        Attachment attachmentPart =
                new Attachment("myworddocx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        WebClient webClient = WebClient.create(endPoint + META_PATH + FORM_PATH);

        Response response = webClient.type("multipart/form-data")
                .accept("application/json")
                .post(attachmentPart);
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        String content = metadataList.get(6).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT).trim();
        assertTrue(content.startsWith("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));

        //unparseable
        attachmentPart =
                new Attachment("myworddocx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        webClient = WebClient.create(endPoint + META_PATH + FORM_PATH + UNPARSEABLE_PATH);

        response = webClient.type("multipart/form-data")
                .accept("application/json")
                .post(attachmentPart);
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        content = metadataList.get(6).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT).trim();
        assertTrue(content.startsWith("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));

        //xml
        attachmentPart =
                new Attachment("myworddocx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        webClient = WebClient.create(endPoint + META_PATH + FORM_PATH + XML_PATH);

        response = webClient.type("multipart/form-data")
                .accept("application/json")
                .post(attachmentPart);
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        content = metadataList.get(6).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT).trim();
        assertTrue(content.startsWith("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));

        //text
        attachmentPart =
                new Attachment("myworddocx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        webClient = WebClient.create(endPoint + META_PATH + FORM_PATH+TEXT_PATH);

        response = webClient.type("multipart/form-data")
                .accept("application/json")
                .post(attachmentPart);
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        content = metadataList.get(6).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT).trim();
        assertTrue(content.startsWith("embed_3"));

        //ignore -- no content
        attachmentPart =
                new Attachment("myworddocx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        webClient = WebClient.create(endPoint + META_PATH +FORM_PATH+IGNORE_PATH);

        response = webClient.type("multipart/form-data")
                .accept("application/json").query("handler", "ignore")
                .post(attachmentPart);
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(12, metadataList.size());
        assertNull(metadataList.get(6).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));
    }

    @Test
    public void testEmbeddedResourceLimit() throws Exception {
        for (int i : new int[]{0,1,5}) {
            Response response = WebClient
                    .create(endPoint + META_PATH)
                    .accept("application/json")
                    .header("maxEmbeddedResources", Integer.toString(i))
                    .put(ClassLoader
                            .getSystemResourceAsStream(TEST_RECURSIVE_DOC));

            assertEquals(200, response.getStatus());
            // Check results
            Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
            List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
            assertEquals(i+1, metadataList.size());
        }
    }

    @Test
    public void testJsonWriteLimitEmbedded() throws Exception {
        for (int i = 500; i < 8500; i += 500) {
            Response response = WebClient.create(endPoint + META_PATH + "/text").accept("application/json")
                    .header("writeLimit",
                            Integer.toString(i)).put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
            List<Metadata> metadata = JsonMetadataList.fromJson(
                    new InputStreamReader(((InputStream) response.getEntity()), StandardCharsets.UTF_8));
            int len = 0;
            for (Metadata m : metadata) {
                len += m.get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT).length();
            }
            assertEquals(i, len);
        }
    }

    @Test
    public void testWriteLimit() throws Exception {
        int writeLimit = 10;
        Response response = WebClient.create(endPoint + META_PATH).accept("application/json")
                .header("writeLimit", Integer.toString(writeLimit))
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));

        assertEquals(200, response.getStatus());
        // Check results
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(1, metadataList.size());
        assertEquals("true", metadataList.get(0).get(AbstractRecursiveParserWrapperHandler.WRITE_LIMIT_REACHED));

        //now try with a write limit of 1000
        writeLimit = 500;
        response = WebClient.create(endPoint + META_PATH).accept("application/json")
                .header("writeLimit", Integer.toString(writeLimit))
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));

        assertEquals(200, response.getStatus());
        // Check results
        reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(10, metadataList.size());

        assertEquals("true", metadataList.get(6).get(AbstractRecursiveParserWrapperHandler.WRITE_LIMIT_REACHED));
        assertContains("When in the Course of human events it becomes necessary",
                metadataList.get(6).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));
        TikaTest.assertNotContained("We hold these truths",
                metadataList.get(6).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));

    }

    @Test
    public void testNPE() throws Exception {
        Response response = WebClient.create(endPoint + META_PATH).accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_NULL_POINTER));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        Metadata metadata = metadataList.get(0);
        assertEquals("Nikolai Lobachevsky", metadata.get("author"));
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertContains("some content", metadata.get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));
        assertContains("null pointer message",
                metadata.get(AbstractRecursiveParserWrapperHandler.CONTAINER_EXCEPTION));

    }

    @Test
    public void testWriteLimitInPDF() throws Exception {
        int writeLimit = 10;
        Response response = WebClient.create(endPoint + META_PATH).accept("application/json")
                .header("writeLimit", Integer.toString(writeLimit))
                .put(ClassLoader.getSystemResourceAsStream("testPDFTwoTextBoxes.pdf"));

        assertEquals(200, response.getStatus());
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        Metadata metadata = metadataList.get(0);
        assertEquals("true",
                metadata.get(AbstractRecursiveParserWrapperHandler.WRITE_LIMIT_REACHED));
    }
/**
    @Test
    public void testWriteLimitInAll() throws Exception {
        //specify your file directory here
        Path testDocs = Paths.get("..../tika-parsers/src/test/resources/test-documents");
        for (File f : testDocs.toFile().listFiles()) {
            if (f.isDirectory()) {
                continue;
            }
                System.out.println(f.getName());
                testWriteLimit(f);
        }
    }

    private void testWriteLimit(File f) throws Exception {
        Response response = WebClient.create(endPoint + META_PATH+"/text").accept(
                "application/json")
                .put(f);

        assertEquals(200, response.getStatus());
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        int totalLen = 0;
        StringBuilder sb = new StringBuilder();
        for (Metadata m : metadataList) {
            String txt = m.get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT);
            sb.append(txt);
            totalLen += (txt == null) ? 0 : txt.length();
        }
        String fullText = sb.toString();
        Random r = new Random();
        for (int i = 0; i < 20; i++) {
            int writeLimit = r.nextInt(totalLen+100);
            response = WebClient.create(endPoint + META_PATH+"/text").accept(
                    "application/json")
                    .header("writeLimit", Integer.toString(writeLimit)).put(f);

            assertEquals(200, response.getStatus());
            reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
            List<Metadata> writeLimitMetadataList = JsonMetadataList.fromJson(reader);
            int len = 0;
            StringBuilder extracted = new StringBuilder();
            for (Metadata m : writeLimitMetadataList) {
                String txt = m.get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT);
                len += (txt == null) ? 0 : txt.length();
                extracted.append(txt);
            }
            if (totalLen > len) {
                boolean wlr = false;
                for (Metadata m : writeLimitMetadataList) {
                    if ("true".equals(m.get(AbstractRecursiveParserWrapperHandler.WRITE_LIMIT_REACHED))) {
                        wlr = true;
                    }
                }
                System.out.println(f.getName() + " actualLen:" + len + " : writeLimit: "
                        + writeLimit + " : totalLen: "+totalLen);
                assertTrue(f.getName() + ": writelimit: " + writeLimit + " len: "+len,
                        len <= writeLimit);
                assertEquals(f.getName() +" writeLimit: " + writeLimit +
                                " : fullLen:" + totalLen + " limitedLen: " +len,
                        true, wlr);
            } else if (len > totalLen) {
                fail("len should never be > totalLen "+len + "  : "+ totalLen);
            }
        }
    }*/
}