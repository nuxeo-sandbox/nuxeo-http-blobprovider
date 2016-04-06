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

import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.blob.AbstractBlobProvider;
import org.nuxeo.ecm.core.blob.BlobManager.BlobInfo;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.blob.SimpleManagedBlob;
import org.nuxeo.ecm.core.model.Document;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

public class HttpBlobProvider extends AbstractBlobProvider {

    @Override
    public void initialize(String blobProviderId, Map<String, String> properties) throws IOException {
        super.initialize(blobProviderId, properties);
    }

    @Override
    public void close() {
    }

    @Override
    public Blob readBlob(BlobInfo blobInfo) throws IOException {
        return new SimpleManagedBlob(blobInfo);
    }

    @Override
    public InputStream getStream(ManagedBlob blob) throws IOException {
        
        /*
        System.out.println("============================================");
        System.out.println("getStream()");
        System.out.println("============================================");
        */
        
        String key = blob.getKey();
        // strip prefix
        int colon = key.indexOf(':');
        if (colon >= 0 && key.substring(0, colon).equals(blobProviderId)) {
            key = key.substring(colon + 1);
        }

        URL url = new URL(key);
        URLConnection connection = url.openConnection();
        return connection.getInputStream();
    }

    @Override
    public boolean supportsUserUpdate() {
        return supportsUserUpdateDefaultFalse();
    }

    @Override
    public String writeBlob(Blob blob, Document doc) throws IOException {
        throw new UnsupportedOperationException("Writing a blob is not supported");
    }

    /**
     * Creates a filesystem blob with the given information.
     * <p>
     * The passed {@link BlobInfo} contains information about the blob, and the key is a file path.
     *
     * @param blobInfo the blob info where the key is a file path
     * @return the blob
     */
    public ManagedBlob createBlob(BlobInfo blobInfo) throws IOException {
        String url = blobInfo.key;
        blobInfo = new BlobInfo(blobInfo); // copy
        blobInfo.key = blobProviderId + ":" + url;
        if (StringUtils.isBlank(blobInfo.filename)) {
            blobInfo.filename = url;
        }
        if (StringUtils.isBlank(blobInfo.digest)) {
            blobInfo.digest = url;
        }
        return new SimpleManagedBlob(blobInfo);
    }
    
    public ManagedBlob createBlobFromUrl(String url, String mimeType) throws IOException {
        
        BlobInfo blobInfo = new BlobInfo();
        
        // . . .
        blobInfo.key = url;
        blobInfo.mimeType = mimeType; //"application/pdf"; // HARD CODED TEMPORARILY
        
        return createBlob(blobInfo);
    }

}
