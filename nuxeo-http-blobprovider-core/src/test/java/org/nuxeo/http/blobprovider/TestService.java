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

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobManager.BlobInfo;
import org.nuxeo.ecm.core.blob.ManagedBlob;
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

/**
 * Created by Michaël on 28/05/2015.
 */

@RunWith(FeaturesRunner.class)
@Features({AutomationFeature.class})
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy({ "org.nuxeo.ecm.platform.commandline.executor",
    "org.nuxeo.ecm.platform.video.core",
    "org.nuxeo.ecm.platform.video.convert",
    "org.nuxeo.ecm.platform.picture.core",
    "org.nuxeo.http.blobprovider.nuxeo-http-blobprovider-core"})
public class TestService {

    @Inject
    CoreSession session;

    //@Ignore
    @Test
    public void testHttpBlobProvider() throws Exception {
        DocumentModel doc = session.createDocumentModel("/","File","File");
        doc = session.createDocument(doc);
        
        //String URL_TEST = "http://doc.nuxeo.com/download/attachments/8684602/Nuxeo_IDE_documentation.pdf?api=v2";
        String URL_TEST = "https://doc.nuxeo.com/download/attachments/8684602/Nuxeo_IDE_documentation.pdf?api=v2";
        String mimeType = "application/pdf";
        
        BlobManager blobManager = Framework.getService(BlobManager.class);
        HttpBlobProvider bp = (HttpBlobProvider) blobManager.getBlobProvider("http");
        
        Blob blob = bp.createBlobFromUrl(URL_TEST, mimeType);
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
        
        String z = "stop";
    }

}
