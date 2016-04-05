package org.nuxeo.http.blobprovider;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobManager.BlobInfo;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import javax.inject.Inject;
import java.io.Serializable;

/**
 * Created by Michaël on 28/05/2015.
 */

@RunWith(FeaturesRunner.class)
@Features({AutomationFeature.class})
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy({"org.nuxeo.http.blobprovider.nuxeo-http-blobprovider-core"})
public class TestService {

    @Inject
    CoreSession session;

    @Ignore
    @Test
    public void testHttpBlobProvider() throws Exception {
        DocumentModel doc = session.createDocumentModel("/","File","File");
        doc = session.createDocument(doc);
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.key = "http://doc.nuxeo.com/download/attachments/8684602/Nuxeo_IDE_documentation.pdf?api=v2";
        blobInfo.mimeType = "application/pdf";
        BlobManager blobManager = Framework.getService(BlobManager.class);
        Blob blob = ((HttpBlobProvider) blobManager.getBlobProvider("http")).createBlob(blobInfo);
        doc.setPropertyValue("file:content", (Serializable) blob);
        session.saveDocument(doc);
    }

}
