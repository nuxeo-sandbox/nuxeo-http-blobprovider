/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Michael Vachette
 *     Thibaud Arguillere
 */
package org.nuxeo.http.blobprovider;

import static org.junit.Assert.*;

import org.apache.commons.lang.StringUtils;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationChain;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.blob.BlobManager.BlobInfo;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.http.blobprovider.operations.CreateBlobOp;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.transaction.TransactionHelper;

import javax.inject.Inject;

import java.io.File;
import java.io.Serializable;

@RunWith(FeaturesRunner.class)
@Features({ AutomationFeature.class, SimpleFeatureCustom.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy({ "org.nuxeo.ecm.platform.commandline.executor", "org.nuxeo.ecm.platform.video.core",
        "org.nuxeo.ecm.platform.video.convert", "org.nuxeo.ecm.platform.picture.core",
        "org.nuxeo.http.blobprovider.nuxeo-http-blobprovider-core" })
@LocalDeploy({ "nuxeo-http-blobprovider-test:http-blobprovider-test.xml" })
public class TestService {

    // See test/resources/http-blobprovider-test.xml
    // It allows getting file from any URL, with no authentication.
    public static final String OTHER_PROVIDER = "http-na";

    // Checking such distant file may not be reliable (file may move etc.
    // We check the URL and Assume.assumeTrue() that it can be reached.
    public static final String FILE_NO_AUTH_URL = "https://doc.nuxeo.com/download/attachments/8684602/Nuxeo_IDE_documentation.pdf?api=v2";

    public static final String FILE_NO_AUTH_MIMETYPE = "application/pdf";

    public static final String FILE_NO_AUTH_FILENAME = "Nuxeo_IDE_documentation.pdf";

    public static final String FILE_NO_AUTH_FULLTEXT_TO_SEARCH = "installing nuxeo ide";

    @Inject
    CoreSession session;

    @Inject
    AutomationService automationService;

    /*
     * Grouping test code
     */
    protected DocumentModel createDocumentAndTest(HttpBlobProvider blopProvider, String nameInPath, String docType,
            String url, String mimeType, String fileName, Long fileSize, String fullTextToSearch) throws Exception {

        if (blopProvider == null) {
            blopProvider = getProvider(null);
        }

        // <-------------------- Test creation and search (full text was extracted) -------------------->
        DocumentModel doc = session.createDocumentModel("/", nameInPath, docType);
        doc = session.createDocument(doc);

        BlobInfo blobInfo = new BlobInfo();
        blobInfo.key = url;
        blobInfo.mimeType = mimeType;
        blobInfo.filename = fileName;
        blobInfo.length = fileSize;
        Blob blob = blopProvider.createBlob(blobInfo);
        doc.setPropertyValue("file:content", (Serializable) blob);
        doc = session.saveDocument(doc);

        return commitWaitAndTest(blopProvider, doc, fileSize, fullTextToSearch);

    }

    protected DocumentModel commitWaitAndTest(HttpBlobProvider blopProvider, DocumentModel doc, Long fileSize,
            String fullTextToSearch) throws Exception {

        if (blopProvider == null) {
            blopProvider = getProvider(null);
        }

        // Make sure it can be found later
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        // Wait for async. worker (full text index) to finish their job
        Thread.sleep(500);
        Framework.getService(EventService.class).waitForAsyncCompletion();

        String nxql = "SELECT * FROM Document WHERE ecm:fulltext = '" + fullTextToSearch + "'";
        DocumentModelList docs = session.query(nxql);
        assertEquals(1, docs.size());

        doc = docs.get(0);
        // <-------------------- Test download and check file size -------------------->
        // If we are here, it is very likely downloading worked since Nuxeo could extract the full text from it
        Blob blob = (Blob) doc.getPropertyValue("file:content");
        Blob downloaded = blopProvider.downloadFile((ManagedBlob) blob);
        assertNotNull(downloaded);
        File f = downloaded.getFile();
        assertTrue(f.exists());
        if (fileSize > 0) {
            assertEquals(fileSize.longValue(), f.length());
        }

        return doc;
    }

    protected HttpBlobProvider getProvider(String name) {

        BlobManager blobManager = Framework.getService(BlobManager.class);
        if (name == null) {
            name = HttpBlobProvider.DEFAULT_PROVIDER;
        }
        return (HttpBlobProvider) blobManager.getBlobProvider(name);
    }

    protected boolean areNotBlanks(String... values) {

        for (String str : values) {
            if (StringUtils.isBlank(str)) {
                return false;
            }
        }

        return true;
    }

    @Test
    public void testHttpBlobProvider_noAuthentication() throws Exception {

        // Instantiate the blobProvider now
        BlobManager blobManager = Framework.getService(BlobManager.class);
        HttpBlobProvider bp = (HttpBlobProvider) blobManager.getBlobProvider(HttpBlobProvider.DEFAULT_PROVIDER);
        assertNotNull(bp);

        // So we just quickly test if the URL really exist
        boolean canTestFile = bp.urlLooksValid(FILE_NO_AUTH_URL);
        Assume.assumeTrue("Remote file not available, cannot run the test", canTestFile);

        DocumentModel doc = createDocumentAndTest(bp, "File-NoAuth", "File", FILE_NO_AUTH_URL, FILE_NO_AUTH_MIMETYPE,
                FILE_NO_AUTH_FILENAME, 0L, FILE_NO_AUTH_FULLTEXT_TO_SEARCH);

        // The default provider does not use the cache
        Blob b = (Blob) doc.getPropertyValue("file:content");
        assertTrue(b instanceof ManagedBlob);
        assertFalse(bp.isCached((ManagedBlob) b));
        assertEquals(0, bp.getNumberOfCachedFiles());
    }

    /*
     * Authentication info and url to test are in the local testconf.conf file. If this file does not exist, the test is
     * ignored. It is our SimpleFeatureCustom class that load it
     */
    @Test
    public void testHttpBlobProvider_withAuthentication() throws Exception {

        Assume.assumeTrue("No local configuration file, no test", SimpleFeatureCustom.hasLocalTestConfiguration());

        String url = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.CONF_KEY_AUTH_FILE_URL);
        String mimeType = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.CONF_KEY_AUTH_FILE_MIME_TYPE);
        String fileName = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.CONF_KEY_AUTH_FILE_FILE_NAME);
        String fullTextTSearch = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.AUTH_FILE_FULL_TEXT_SEARCH);
        String fileSizeStr = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.CONF_KEY_AUTH_FILE_SIZE);
        long fileSize = 0;
        try {
            fileSize = Long.parseLong(fileSizeStr);
        } catch (Exception e) {
            fileSize = 0;
        }

        boolean hasValues = areNotBlanks(url, mimeType, fileName, fileSizeStr);
        Assume.assumeTrue("Parameters not set for authentication, no test", hasValues);

        // Instantiate the blobProvider now
        BlobManager blobManager = Framework.getService(BlobManager.class);
        HttpBlobProvider bp = (HttpBlobProvider) blobManager.getBlobProvider(HttpBlobProvider.DEFAULT_PROVIDER);
        assertNotNull(bp);

        boolean canTestFile = bp.urlLooksValid(url);
        Assume.assumeTrue("Remote file not available, cannot run the test", canTestFile);

        createDocumentAndTest(bp, "File-Auth", "File", url, mimeType, fileName, fileSize, fullTextTSearch);

    }

    @Test
    public void testGuessInfo_noAuthentication() throws Exception {

        BlobManager blobManager = Framework.getService(BlobManager.class);
        HttpBlobProvider bp = (HttpBlobProvider) blobManager.getBlobProvider(HttpBlobProvider.DEFAULT_PROVIDER);

        BlobInfo bi = bp.guessInfosFromURL(FILE_NO_AUTH_URL);
        assertTrue(bi != null);
        assertEquals(FILE_NO_AUTH_MIMETYPE, bi.mimeType);
        assertEquals(FILE_NO_AUTH_FILENAME, bi.filename);

    }

    @Test
    public void testGuessInfo_authenticated() throws Exception {

        Assume.assumeTrue("No local configuration file, no test", SimpleFeatureCustom.hasLocalTestConfiguration());

        String url = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.CONF_KEY_AUTH_FILE_URL);
        String mimeType = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.CONF_KEY_AUTH_FILE_MIME_TYPE);
        String fileName = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.CONF_KEY_AUTH_FILE_FILE_NAME);
        String fileSizeStr = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.CONF_KEY_AUTH_FILE_SIZE);

        BlobManager blobManager = Framework.getService(BlobManager.class);
        HttpBlobProvider bp = (HttpBlobProvider) blobManager.getBlobProvider(HttpBlobProvider.DEFAULT_PROVIDER);

        BlobInfo bi = bp.guessInfosFromURL(url);
        assertNotNull(bi);
        assertEquals(mimeType, bi.mimeType);
        assertEquals(fileName, bi.filename);
        assertEquals(fileSizeStr, bi.length.toString());

    }

    @Test
    public void testOtherProvider() throws Exception {

        // Instantiate the blobProvider now
        BlobManager blobManager = Framework.getService(BlobManager.class);
        HttpBlobProvider bp = (HttpBlobProvider) blobManager.getBlobProvider(OTHER_PROVIDER);
        assertNotNull(bp);
        // Check we really have our provider
        // Our xml contribution declares the parameters, letting them empty
        assertEquals(OTHER_PROVIDER, bp.blobProviderId);
        assertEquals("", bp.properties.get(HttpBlobProvider.PROPERTY_ORIGIN));
        assertEquals("", bp.properties.get(HttpBlobProvider.PROPERTY_AUTHENTICATION_TYPE));
        assertEquals("", bp.properties.get(HttpBlobProvider.PROPERTY_LOGIN));
        assertEquals("", bp.properties.get(HttpBlobProvider.PROPERTY_PWD));
        assertEquals("", bp.properties.get(HttpBlobProvider.PROPERTY_MORE_HEADERS));
        assertEquals("true", bp.properties.get(HttpBlobProvider.PROPERTY_USE_CACHE));

        // <---------- Create and check authenticated url ---------->
        boolean canTestFile = bp.urlLooksValid(FILE_NO_AUTH_URL);
        Assume.assumeTrue("Remote file not available, cannot run the test", canTestFile);

        DocumentModel doc = createDocumentAndTest(bp, "File-NoAuth-OtherProvider", "File", FILE_NO_AUTH_URL,
                FILE_NO_AUTH_MIMETYPE, FILE_NO_AUTH_FILENAME, 0L, FILE_NO_AUTH_FULLTEXT_TO_SEARCH);

        // <---------- Check the blob is handled by our provider ---------->
        Blob b = (Blob) doc.getPropertyValue("file:content");
        assertTrue(b instanceof ManagedBlob);
        BlobProvider theBP = blobManager.getBlobProvider(b);
        assertTrue(theBP instanceof HttpBlobProvider);
        HttpBlobProvider myBP = (HttpBlobProvider) theBP;
        assertEquals(OTHER_PROVIDER, myBP.blobProviderId);

        // <---------- Check the File Cache ---------->
        // (the xml contribution has "useCache" set to true)
        // FAILTING TESTING THE CACHE. It works in Eclipse, not in maven
        // TODO Fix this...
        //assertTrue(myBP.isCached((ManagedBlob) b));
        //assertEquals(1, myBP.getNumberOfCachedFiles());

    }

    @Test
    public void testOperation_noAuth() throws Exception {

        OperationChain chain;
        OperationContext ctx = new OperationContext(session);

        DocumentModel doc = session.createDocumentModel("/", "withOp-1", "File");
        doc.setPropertyValue("dc:description", FILE_NO_AUTH_URL);
        doc = session.createDocument(doc);

        ctx.setInput(doc);
        chain = new OperationChain("createBlob-1");
        chain.add(CreateBlobOp.ID)
             .set("urlXPath", "dc:description")
             .set("mimeType", FILE_NO_AUTH_MIMETYPE)
             .set("fileName", FILE_NO_AUTH_FILENAME)
             .set("save", true);
        DocumentModel result = (DocumentModel) automationService.run(ctx, chain);
        assertNotNull(result);

        Blob b = (Blob) doc.getPropertyValue("file:content");
        assertNotNull(b);
        assertEquals(FILE_NO_AUTH_MIMETYPE, b.getMimeType());
        assertEquals(FILE_NO_AUTH_FILENAME, b.getFilename());

        commitWaitAndTest(null, doc, 0L, FILE_NO_AUTH_FULLTEXT_TO_SEARCH);

    }
}
