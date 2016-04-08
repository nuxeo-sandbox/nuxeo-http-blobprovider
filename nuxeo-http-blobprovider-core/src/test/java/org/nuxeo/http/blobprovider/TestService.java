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
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.blob.BlobManager.BlobInfo;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.transaction.TransactionHelper;

import javax.inject.Inject;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.Serializable;


@RunWith(FeaturesRunner.class)
@Features({ AutomationFeature.class, SimpleFeatureCustom.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy({ "org.nuxeo.ecm.platform.commandline.executor", "org.nuxeo.ecm.platform.video.core",
        "org.nuxeo.ecm.platform.video.convert", "org.nuxeo.ecm.platform.picture.core",
        "org.nuxeo.http.blobprovider.nuxeo-http-blobprovider-core" })
public class TestService {

    @Inject
    CoreSession session;

    @Test
    public void testHttpBlobProvider_noAuthentication() throws Exception {

        // Using such distant file maybe is not the best idea, because who knows, maybe it will be removed in the
        // future, etc.
        // String URL_TEST = "http://doc.nuxeo.com/download/attachments/8684602/Nuxeo_IDE_documentation.pdf?api=v2";
        String URL_TEST = "https://doc.nuxeo.com/download/attachments/8684602/Nuxeo_IDE_documentation.pdf?api=v2";
        String mimeType = "application/pdf";
        String fileName = "Nuxeo_IDE_documentation.pdf";

        // Instantiate the blobProvider now
        BlobManager blobManager = Framework.getService(BlobManager.class);
        HttpBlobProvider bp = (HttpBlobProvider) blobManager.getBlobProvider("http");
        
        // So we just quickly test if the URL really exist
        boolean canTestFile = bp.urlLooksValid(URL_TEST);
        Assume.assumeTrue("Remote file not available, cannot run the test", canTestFile);

        DocumentModel doc = session.createDocumentModel("/", "File-NoAuth", "File");
        doc = session.createDocument(doc);

        BlobInfo blobInfo = new BlobInfo();
        blobInfo.key = URL_TEST;
        blobInfo.mimeType = mimeType;
        blobInfo.filename = fileName;
        blobInfo.length = 0L;
        // newInfo.encoding = encoding;
        // newInfo.digest = digest;

        Blob blob = bp.createBlob(blobInfo);
        doc.setPropertyValue("file:content", (Serializable) blob);
        doc = session.saveDocument(doc);

        // Make sure it can be found later
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        // Wait for async. worker (full text index) to finish their job
        Thread.sleep(500);
        Framework.getService(EventService.class).waitForAsyncCompletion();

        String nxql = "SELECT * FROM Document WHERE ecm:fulltext = 'installing nuxeo ide'";
        DocumentModelList docs = session.query(nxql);
        assertEquals(1, docs.size());

        doc = docs.get(0);

        // ===================== Test we can get the blob
        blob = (Blob) doc.getPropertyValue("file:content");
        InputStream is = bp.getStream((ManagedBlob) blob);

        Blob tmp = Blobs.createBlobWithExtension(".pdf");
        FileOutputStream outputStream = new FileOutputStream(tmp.getFile());
        int bytesRead = -1;
        byte[] buffer = new byte[10240];
        while ((bytesRead = is.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.close();
        is.close();

        assertTrue(tmp.getFile().exists());
        // Should test more...
    }
    
    protected boolean areNotBlanks(String...values) {
        
        for(String str : values) {
            if(StringUtils.isBlank(str)) {
                return false;
            }
        }
        
        return true;
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
        HttpBlobProvider bp = (HttpBlobProvider) blobManager.getBlobProvider("http");
        
        boolean canTestFile = bp.urlLooksValid(url);
        Assume.assumeTrue("Remote file not available, cannot run the test", canTestFile);

        DocumentModel doc = session.createDocumentModel("/", "File-Auth", "File");
        doc = session.createDocument(doc);

        BlobInfo blobInfo = new BlobInfo();
        blobInfo.key = url;
        blobInfo.mimeType = mimeType;
        blobInfo.filename = fileName;
        blobInfo.length = fileSize;Blob blob = bp.createBlob(blobInfo);
        doc.setPropertyValue("file:content", (Serializable) blob);
        doc = session.saveDocument(doc);

        // Make sure it can be found later
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        // Wait for async. worker (full text index) to finish their job
        Thread.sleep(500);
        Framework.getService(EventService.class).waitForAsyncCompletion();

        String nxql = "SELECT * FROM Document WHERE ecm:fulltext = '" + fullTextTSearch + "'";
        DocumentModelList docs = session.query(nxql);
        assertEquals(1, docs.size());

        doc = docs.get(0);
        
    }

}
