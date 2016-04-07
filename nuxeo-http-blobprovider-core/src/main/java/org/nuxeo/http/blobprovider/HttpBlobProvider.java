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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.blob.AbstractBlobProvider;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobManager.BlobInfo;
import org.nuxeo.ecm.core.blob.BlobManager.UsageHint;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.blob.SimpleManagedBlob;
import org.nuxeo.ecm.core.model.Document;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * Handle a blob living on a remote HTTP server.
 * <p>
 * Nuxeo will handle the blob as if it was living in its own blob store: Thumbnail, fill text, ...
 * <p>
 * Firs implementation: Support unauthenticated URLs or BASIC authentication
 * 
 * @since 8.1
 */
public class HttpBlobProvider extends AbstractBlobProvider {

    private static final Log log = LogFactory.getLog(HttpBlobProvider.class);

    // <-------------------- COnfiguration Parameters -------------------->
    // Default configuration parameters. Mainly used in test, since,
    // as a user of the blob provider, you are supposed to:
    // -> Setup the correct XML contribution to BlobProvider
    // -> And either hard code the values or use you own configuration parameters
    public static final String KEY_DOMAIN = "http.blobprovider.domain";

    public static final String KEY_AUTHENTICATION_TYPE = "http.blobprovider.auth.type";

    public static final String KEY_AUTHENTICATION_LOGIN = "http.blobprovider.auth.login";

    public static final String KEY_AUTHENTICATION_PWD = "http.blobprovider.auth.password";

    // <-------------------- Names of properties in the XML -------------------->
    public static final String PROPERTY_DOMAIN = "domain";

    public static final String PROPERTY_AUTHENTICATION_TYPE = "authenticationType";

    public static final String PROPERTY_LOGIN = "login";

    public static final String PROPERTY_PWD = "password";

    // <-------------------- Other constants -------------------->
    protected static final String AUTH_NONE = "None";

    protected static final String AUTH_BASIC = "Basic";

    public static final String[] SUPPORTED_AUTHENTICATION_METHODS = { AUTH_NONE, AUTH_BASIC };

    // <-------------------- Implementation -------------------->
    protected String domain;

    protected String authenticationType;

    protected String authenticationLogin;

    protected String authenticationPwd;

    protected String extractUrl(ManagedBlob blob) {

        String key = blob.getKey();
        // strip prefix
        int colon = key.indexOf(':');
        if (colon >= 0 && key.substring(0, colon).equals(blobProviderId)) {
            key = key.substring(colon + 1);
        }

        return key;
    }

    protected void loadAuthenticationType() {

        authenticationType = properties.get(PROPERTY_AUTHENTICATION_TYPE);
        if (StringUtils.isNotBlank(authenticationType)) {
            if (authenticationType.toLowerCase().equals(AUTH_NONE.toLowerCase())) {
                authenticationType = AUTH_NONE;
            } else if (authenticationType.toLowerCase().equals(AUTH_BASIC.toLowerCase())) {
                authenticationType = AUTH_BASIC;
            }
        }
    }

    @Override
    public void initialize(String blobProviderId, Map<String, String> properties) throws IOException {
        super.initialize(blobProviderId, properties);

        domain = properties.get(PROPERTY_DOMAIN);

        loadAuthenticationType();
        if (!authenticationType.equals(AUTH_NONE)) {
            authenticationLogin = properties.get(PROPERTY_LOGIN);
            authenticationPwd = properties.get(PROPERTY_PWD);
        }
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

        String urlStr = extractUrl(blob);

        URL url = new URL(urlStr);

        if (!isNoAuthentication() && StringUtils.isNotBlank(domain) && urlStr.toLowerCase().startsWith(domain)) {
            // . . .
        } else {
            // No authentication type or not the original domain => Assume the url does not require authentication
        }
        URLConnection connection = url.openConnection();
        return connection.getInputStream();
    }

    @Override
    public URI getURI(ManagedBlob blob, UsageHint hint, HttpServletRequest servletRequest) throws IOException {

        if (hint != BlobManager.UsageHint.DOWNLOAD) {
            return null;
        }

        String urlStr = extractUrl(blob);

        URI uri = null;
        try {
            uri = new URI(urlStr);
        } catch (URISyntaxException e) {
            log.error("Invalid URI: " + urlStr, e);
        }

        return uri;
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
     * Creates a blob whose key is the remote URL
     * <p>
     * <b>IMPORTANT</b>: The <code>blobInfo.key</code> field will be replaced by the provider's own key scheme
     * <p>
     * The passed {@link BlobInfo} contains information about the blob
     * <p>
     * A future improvement would be to allow just a URL and using HEAD request maybe to fetch the infos (mime type,
     * lengh, file name, ...)
     *
     * @param blobInfo the blob info where the key is the URL
     * @return the blob
     */
    public ManagedBlob createBlob(BlobInfo blobInfo) throws IOException {

        String url = blobInfo.key;

        BlobInfo newInfo = new BlobInfo(blobInfo); // copy
        newInfo.key = blobProviderId + ":" + url;

        // ==================================================
        if (newInfo.length == null || newInfo.length < 0 || StringUtils.isBlank(newInfo.mimeType)) {
            // Here, get info from the server using HEAD maybe
            if (newInfo.length == null) {
                // Default widgets iun the UI activate a link if the length is >= 0 (see extended_file_widget.xhtml)
                newInfo.length = 0L;
            }
        }
        // ==================================================

        if (StringUtils.isBlank(newInfo.filename)) {
            newInfo.filename = url;
        }
        if (StringUtils.isBlank(newInfo.digest)) {
            newInfo.digest = url;
        }

        // newInfo.encoding

        return new SimpleManagedBlob(newInfo);
    }

    protected boolean isBasicAuthentication() {
        return StringUtils.isNotBlank(authenticationType) && authenticationType.equals(AUTH_BASIC);
    }

    protected boolean isNoAuthentication() {
        return StringUtils.isBlank(authenticationType) || authenticationType.equals(AUTH_NONE);
    }

}
