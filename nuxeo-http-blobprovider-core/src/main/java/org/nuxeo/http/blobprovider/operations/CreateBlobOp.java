/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thibaud Arguillere
 */
package org.nuxeo.http.blobprovider.operations;

import java.io.IOException;
import java.io.Serializable;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobManager.BlobInfo;
import org.nuxeo.http.blobprovider.HttpBlobProvider;
import org.nuxeo.runtime.api.Framework;

/**
 *
 * @since 8.1
 */
@Operation(id = CreateBlobOp.ID, category = Constants.CAT_SERVICES, label = "HTTP BlobProvider: Create Blob", description = "")
public class CreateBlobOp {

    public static final String ID = "HTTPBlobProvider.CreateBlob";

    @Context
    protected CoreSession session;

    @Param(name = "provider", required = false, values = { HttpBlobProvider.DEFAULT_PROVIDER })
    String provider;

    @Param(name = "urlXPath", required = false)
    String urlXPath;

    @Param(name = "blobXPath", required = false, values = { "file:content" } )
    String blobXPath;

    @Param(name = "mimeType", required = true)
    String mimeType;

    @Param(name = "fileName", required = true)
    String fileName;

    @Param(name = "fileSize", required = false)
    Long fileSize;

    @Param(name = "encoding", required = false)
    String encoding;

    @Param(name = "digest", required = false)
    String digest;

    @Param(name = "save", required = false)
    boolean save = false;

    @OperationMethod
    public Blob run(String url) throws IOException {

        BlobInfo newInfo = new BlobInfo();
        newInfo.key = url;
        newInfo.mimeType = mimeType;
        newInfo.filename = fileName;
        newInfo.length = fileSize;
        newInfo.encoding = encoding;
        newInfo.digest = digest;

        if(StringUtils.isBlank(provider)) {
            provider = HttpBlobProvider.DEFAULT_PROVIDER;
        }

        BlobManager blobManager = Framework.getService(BlobManager.class);
        HttpBlobProvider bp = (HttpBlobProvider) blobManager.getBlobProvider(provider);
        Blob blob = bp.createBlob(newInfo);

        return blob;
    }

    @OperationMethod
    public DocumentModel run(DocumentModel input) throws IOException {
        String url = (String) input.getPropertyValue(urlXPath);
        Blob blob = run(url);

        if(StringUtils.isBlank(blobXPath)) {
            blobXPath = "file:content";
        }
        input.setPropertyValue(blobXPath, (Serializable) blob);
        if(save) {
            input = session.saveDocument(input);
        }

        return input;
    }

}
