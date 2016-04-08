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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.AbstractBlobProvider;
import org.nuxeo.ecm.core.blob.BlobManager.BlobInfo;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.blob.SimpleManagedBlob;
import org.nuxeo.ecm.core.model.Document;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Handle a blob living on a remote HTTP server.
 * <p>
 * Nuxeo will handle the blob as if it was living in its own blob store: Thumbnail, full text, ...
 * <p>
 * First implementation: Support unauthenticated URLs or BASIC authentication
 * <p>
 * Because we don't redirect the URL (we don't override <code>getURI()</code>), any download will fetch the file on the
 * remote server => We should have some cache mechanism for optimization, instead of downloading the file from the
 * distant url. This cache should be an option though, because in some application, the distant server wants to keep
 * track of all the downloads, etc.
 * 
 * @since 8.1
 */
public class HttpBlobProvider extends AbstractBlobProvider {

    @SuppressWarnings("unused")
    private static final Log log = LogFactory.getLog(HttpBlobProvider.class);

    // <-------------------- Configuration Parameters -------------------->
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

    public static final String PROPERTY_EXTRA_HEADERS = "moreHeadersJson";

    // <-------------------- Other constants -------------------->
    protected static final String AUTH_NONE = "None";

    protected static final String AUTH_BASIC = "Basic";

    public static final String[] SUPPORTED_AUTHENTICATION_METHODS = { AUTH_NONE, AUTH_BASIC };

    public static final String DEFAULT_PROVIDER = "http";

    // <-------------------- Implementation -------------------->
    protected String domain;

    protected String authenticationType;

    protected String authenticationLogin;

    protected String authenticationPwd;

    protected String basicAuthentication;

    protected HashMap<String, String> moreHeaders;

    protected String extractUrl(ManagedBlob blob) {

        String key = blob.getKey();
        // strip prefix
        int colon = key.indexOf(':');
        if (colon >= 0 && key.substring(0, colon).equals(blobProviderId)) {
            key = key.substring(colon + 1);
        }

        return key;
    }

    /*
     * Here, we just make sure every variable is not null, so we can easily use theValue.equals() everywhere for
     * example.We also realign values to get rid of case-sensitive comparison errors and be cool with the users of the
     * extension point ;-)
     */
    protected void setupProperties() throws JSONException {

        // <-------------------- Load from the configuration -------------------->
        authenticationType = properties.get(PROPERTY_AUTHENTICATION_TYPE);
        authenticationType = StringUtils.isBlank(authenticationType) ? "" : authenticationType;

        domain = properties.get(PROPERTY_DOMAIN);
        domain = StringUtils.isBlank(domain) ? "" : domain;

        authenticationLogin = properties.get(PROPERTY_LOGIN);
        authenticationLogin = StringUtils.isBlank(authenticationLogin) ? "" : authenticationLogin;

        authenticationPwd = properties.get(PROPERTY_PWD);
        authenticationPwd = StringUtils.isBlank(authenticationPwd) ? "" : authenticationPwd;

        String moreHeadersJson = properties.get(PROPERTY_EXTRA_HEADERS);
        moreHeadersJson = StringUtils.isBlank(moreHeadersJson) ? "" : moreHeadersJson;

        // <-------------------- Realign etc. -------------------->
        if (authenticationType.toLowerCase().equals(AUTH_NONE.toLowerCase())) {
            authenticationType = AUTH_NONE;
        } else if (authenticationType.toLowerCase().equals(AUTH_BASIC.toLowerCase())) {
            authenticationType = AUTH_BASIC;

            String authString = authenticationLogin + ":" + authenticationPwd;
            basicAuthentication = "Basic " + new String(Base64.encodeBase64(authString.getBytes()));
        }

        moreHeaders = new HashMap<String, String>();
        if (!moreHeadersJson.isEmpty()) {
            JSONArray array = new JSONArray(moreHeadersJson);
            int max = array.length();
            JSONObject obj;
            for (int i = 0; i < max; ++i) {
                obj = array.getJSONObject(i);
                moreHeaders.put(obj.getString("key"), obj.getString("value"));
            }
        }
        if (moreHeaders != null) {
            moreHeadersJson = "zou";
        }

    }

    @Override
    public void initialize(String blobProviderId, Map<String, String> properties) throws IOException {
        super.initialize(blobProviderId, properties);

        try {
            setupProperties();
        } catch (JSONException e) {
            throw new IOException("Failed to load extar headers from the configuration", e);
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

        //log.warn("===========> GET STREAM - Length: " + blob.getLength());

        String urlStr = extractUrl(blob);

        InputStream stream = null;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // No authentication type or not the original domain => Assume the url does not require authentication.
            // Else => authentication
            if (StringUtils.isNotBlank(domain) && urlStr.toLowerCase().startsWith(domain)) {

                switch (authenticationType) {
                case AUTH_BASIC:
                    connection.setRequestProperty("Authorization", basicAuthentication);
                    if (moreHeaders.size() > 0) {
                        for (Entry<String, String> entry : moreHeaders.entrySet()) {
                            connection.setRequestProperty(entry.getKey(), entry.getValue());
                        }
                    }
                    break;

                // . . . Other cases . . .
                }
            }
            stream = connection.getInputStream();

        } catch (MalformedURLException e) {
            throw new NuxeoException("Fatal protocol violation", e);
        } catch (IOException e) {
            throw new IOException("Fatal transport error", e);
        } finally {
        }

        // log.warn("===========> GET STREAM => " + (stream == null ? "NULL" : "Not null"));

        return stream;

    }

    /*
     * This looks like dead code but it not. Not fully at least. Used for tests while implementing
     */
    protected Blob downloadFile(ManagedBlob blob) throws IOException {

        Blob result = null;

        String urlStr = extractUrl(blob);

        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // No authentication type or not the original domain => Assume the url does not require authentication.
        // Else => authentication
        if (StringUtils.isNotBlank(domain) && urlStr.toLowerCase().startsWith(domain)) {

            switch (authenticationType) {
            case AUTH_BASIC:
                connection.setRequestProperty("Authorization", basicAuthentication);
                if (moreHeaders.size() > 0) {
                    for (Entry<String, String> entry : moreHeaders.entrySet()) {
                        connection.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }
                break;

            // . . . Other cases . . .
            }
        }
        result = Blobs.createBlobWithExtension(".tmp");
        FileOutputStream outputStream = new FileOutputStream(result.getFile());
        InputStream inputStream = connection.getInputStream();
        int bytesRead = -1;
        byte[] buffer = new byte[10240];
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.close();
        inputStream.close();

        result.setFilename(blob.getFilename());
        result.setMimeType(blob.getMimeType());

        return result;
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

        BlobInfo newInfo = new BlobInfo(blobInfo);
        newInfo.key = blobProviderId + ":" + url;

        // ==================================================
        if (newInfo.length == null || newInfo.length < 0 || StringUtils.isBlank(newInfo.mimeType)) {

            // Here, get info from the server using HEAD maybe
            if (newInfo.length == null) {
                // Default widgets in the UI activate a link if the length is >= 0 (see extended_file_widget.xhtml)
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
        if (StringUtils.isBlank(newInfo.encoding)) {
            newInfo.encoding = null;
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
    

    public boolean urlLooksValid(ManagedBlob blob) {
    
        String urlStr = extractUrl(blob);
        
        return urlLooksValid(urlStr);
    }
    
    public boolean urlLooksValid(String urlStr) {
        
        boolean looksOk = false;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            
            if (StringUtils.isNotBlank(domain) && urlStr.toLowerCase().startsWith(domain)) {

                switch (authenticationType) {
                case AUTH_BASIC:
                    huc.setRequestProperty("Authorization", basicAuthentication);
                    if (moreHeaders.size() > 0) {
                        for (Entry<String, String> entry : moreHeaders.entrySet()) {
                            huc.setRequestProperty(entry.getKey(), entry.getValue());
                        }
                    }
                    break;

                // . . . Other cases . . .
                }
            }
            
            huc.setRequestMethod("HEAD");
            int responseCode = huc.getResponseCode();
            looksOk = responseCode == HttpURLConnection.HTTP_OK;
            
        } catch (Exception e) {
            looksOk = false;
        }

        return looksOk;
    }

}
