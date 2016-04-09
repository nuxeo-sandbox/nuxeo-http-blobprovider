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
import org.apache.commons.codec.digest.DigestUtils;
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
 * Handle a blob living on a remote HTTP server, in read-only (no write to the server, no synchronization)
 * <p>
 * Nuxeo will handle the blob as if it was living in its own blob store: Thumbnail, full text, video storyboard, ...
 * <p>
 * First implementation: Support unauthenticated URLs or BASIC authentication
 * <p>
 * Because we don't redirect the URL (we don't override <code>getURI()</code>), any download will fetch the file on the
 * remote server => We should have some cache mechanism for optimization, instead of downloading the file from the
 * distant url. This cache should be an option though, because in some application, the distant server wants to keep
 * track of all the downloads, etc.
 * <p>
 * There is one default blob provider, named "http", contributed by the plug-in. It is ready to use configuraiton
 * parameters stored in nuxeo.conf file:
 * <ul>
 * <li>http.blobprovider.origin<br/>
 * Notice this parameter must also contains the protocol</li>
 * <li>http.blobprovider.auth.type</li>
 * <li>http.blobprovider.auth.login</li>
 * <li>http.blobprovider.auth.password</li>
 * <li>http.blobprovider.auth.moreHeadersJson</li>
 * </ul>
 * So you can just put these parameters in your configuration and it will work as expected.
 * <p>
 * To setup another http-blob provider, contribute the same extension and change the name ("my-http") and the
 * properties. You can use the same mechanism as in the default provider, and set up a property with a configuration
 * parameter using the following expression:
 * 
 * <pre>
 * <property name="origin">${my.other.provider.origin:=}</property>
 * </pre>
 * 
 * Just stating the obvious: You can't add a property that is not used here. The current implementation supports the
 * properties listed above (origin, authentication type, ...). Well, you cn add it, it will just be ignored :->
 * <p>
 * Also, notice that by default a blob provider allows connection to domains that are not the one set in the "origin"
 * property. In this case, the provider assumes the call is always unauthenticated. So for example, if your
 * contributions has the <code><property name="origin">http://my.site.com</property></code> property and you use an url
 * like "http://somethingelse.com/thefile.pdf", then the provider will try to get the file with no authentication.
 * <p>
 * In this example, if you need to access "thefile.pdf" and the site requires authentication, you must declare another
 * http blob provider
 * 
 * @since 8.1
 */
public class HttpBlobProvider extends AbstractBlobProvider {

    @SuppressWarnings("unused")
    private static final Log log = LogFactory.getLog(HttpBlobProvider.class);

    // <-------------------- Configuration Parameters -------------------->
    // Names (keys) of the default parameters, as used in the default xml contribution.
    // as a user of the blob provider, you are supposed to:
    // -> Setup the correct XML contribution to BlobProvider
    // -> And either hard code the values or use you own configuration parameters
    public static final String KEY_ORIGIN = "http.blobprovider.origin";

    public static final String KEY_AUTHENTICATION_TYPE = "http.blobprovider.auth.type";

    public static final String KEY_AUTHENTICATION_LOGIN = "http.blobprovider.auth.login";

    public static final String KEY_AUTHENTICATION_PWD = "http.blobprovider.auth.password";

    public static final String KEY_AUTHENTICATION_MORE_HEADERS = "http.blobprovider.moreheaders";

    // <-------------------- Names of properties in the XML -------------------->
    public static final String PROPERTY_ORIGIN = "origin";

    public static final String PROPERTY_AUTHENTICATION_TYPE = "authenticationType";

    public static final String PROPERTY_LOGIN = "login";

    public static final String PROPERTY_PWD = "password";

    public static final String PROPERTY_MORE_HEADERS = "moreHeadersJson";

    // <-------------------- Other constants -------------------->
    protected static final String AUTH_NONE = "None";

    protected static final String AUTH_BASIC = "Basic";

    public static final String[] SUPPORTED_AUTHENTICATION_METHODS = { AUTH_NONE, AUTH_BASIC };

    public static final String DEFAULT_PROVIDER = "http";

    // <-------------------- Implementation -------------------->
    protected String origin;

    protected String authenticationType;

    protected String authenticationLogin;

    protected String authenticationPwd;

    protected String basicAuthentication;

    protected HashMap<String, String> moreHeaders;

    // <============================================================================>
    // <============================ NON PUBLIC METHODS ============================>
    // <============================================================================>
    /*
     * Centralize handling of the key, that mixes the blobprovider Id and the URL
     */
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
    protected void setupFromProperties() throws JSONException {

        // <-------------------- Load from the configuration -------------------->
        authenticationType = properties.get(PROPERTY_AUTHENTICATION_TYPE);
        authenticationType = StringUtils.isBlank(authenticationType) ? "" : authenticationType;

        origin = properties.get(PROPERTY_ORIGIN);
        origin = StringUtils.isBlank(origin) ? "" : origin;

        authenticationLogin = properties.get(PROPERTY_LOGIN);
        authenticationLogin = StringUtils.isBlank(authenticationLogin) ? "" : authenticationLogin;

        authenticationPwd = properties.get(PROPERTY_PWD);
        authenticationPwd = StringUtils.isBlank(authenticationPwd) ? "" : authenticationPwd;

        String moreHeadersJson = properties.get(PROPERTY_MORE_HEADERS);
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

    /*
     * Just a centralization of adding the headers if needed.
     */
    protected void addHeaders(HttpURLConnection connection, String urlStr) {

        // No authentication type or not the original domain => Assume the url does not require authentication.
        // Else => authentication
        if (StringUtils.isNotBlank(origin) && urlStr.toLowerCase().startsWith(origin)) {

            switch (authenticationType) {
            case AUTH_BASIC:
                connection.setRequestProperty("Authorization", basicAuthentication);
                break;

            // . . . Other cases . . .
            }
        }

        if (moreHeaders.size() > 0) {
            for (Entry<String, String> entry : moreHeaders.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    protected boolean isBasicAuthentication() {
        return StringUtils.isNotBlank(authenticationType) && authenticationType.equals(AUTH_BASIC);
    }

    protected boolean isNoAuthentication() {
        return StringUtils.isBlank(authenticationType) || authenticationType.equals(AUTH_NONE);
    }

    // <============================================================================>
    // <============================== PUBLIC METHODS ==============================>
    // <============================================================================>

    @Override
    public void initialize(String blobProviderId, Map<String, String> properties) throws IOException {
        super.initialize(blobProviderId, properties);

        try {
            setupFromProperties();
        } catch (JSONException e) {
            throw new IOException("Failed to load extra headers from the configuration", e);
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

        // log.warn("===========> GET STREAM - Length: " + blob.getLength());

        String urlStr = extractUrl(blob);

        InputStream stream = null;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            addHeaders(connection, urlStr);

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

    /**
     * Downloads the remote data, returns a temp. blob, with ".tmp" as file extension
     * <p>
     * (used by unit tests so far)
     * 
     * @param blob
     * @return the downloaded blob
     * @throws IOException
     * @since 8.1
     */
    public Blob downloadFile(ManagedBlob blob) throws IOException {

        Blob result = null;

        String urlStr = extractUrl(blob);

        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        addHeaders(connection, urlStr);

        String fileName = blob.getFilename();
        String mimeType = blob.getMimeType();

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

        result.setFilename(fileName);
        result.setMimeType(mimeType);

        return result;
    }

    /**
     * This class does no support user updates, whatever the value of the "preventUserUpdate" property.
     */
    @Override
    public boolean supportsUserUpdate() {
        return false; // supportsUserUpdateDefaultFalse();
    }

    /**
     * This class does not support writing a blob and always throws an exception
     */
    @Override
    public String writeBlob(Blob blob, Document doc) throws IOException {
        throw new UnsupportedOperationException("Writing a blob is not supported");
    }

    /**
     * Creates a blob whose key is the remote URL
     * <p>
     * <b>IMPORTANT</b>:
     * <ul>
     * <li>The <code>blobInfo.key</code> field will be replaced by the provider's own key scheme</li>
     * <li>blobInfo <i>must</i> contain the mime type and the filename. If they don't, the code tries to guess the
     * values by sending a HEAD request. If this fails, an error is thrown.</li>
     * </ul>
     * <p>
     * The passed {@link BlobInfo} contains information about the blob
     * <p>
     * <p>
     * A future improvement would be to allow just a URL and using HEAD request maybe to fetch the infos (mime type,
     * lenght, file name, ...)
     *
     * @param blobInfo the blob info where the key is the URL
     * @return the blob
     */
    public ManagedBlob createBlob(BlobInfo blobInfo) throws IOException {

        String url = blobInfo.key;

        BlobInfo newInfo = new BlobInfo(blobInfo);
        newInfo.key = blobProviderId + ":" + url;

        if (StringUtils.isBlank(newInfo.mimeType) || StringUtils.isBlank(newInfo.filename)) {

            BlobInfo guessedInfo = guessInfosFromURL(url);
            
            if(guessedInfo == null || newInfo.mimeType == null || newInfo.filename == null) {
                throw new NuxeoException("BlobInfo with no mime type or no file name, and could not guess them.");
            }
            
            newInfo.mimeType = guessedInfo.mimeType == null ? newInfo.mimeType : guessedInfo.mimeType;
            newInfo.filename = guessedInfo.filename == null ? newInfo.filename : guessedInfo.filename;
            newInfo.encoding = guessedInfo.encoding == null ? newInfo.encoding : guessedInfo.encoding;
        }

        if (newInfo.length == null) {
            // Default widgets in the UI activate a link if the length is >= 0 (see extended_file_widget.xhtml)
            newInfo.length = 0L;
        }

        if (StringUtils.isBlank(newInfo.digest)) {
            newInfo.digest = DigestUtils.md5Hex(url);
        }
        if (StringUtils.isBlank(newInfo.encoding)) {
            newInfo.encoding = null;
        }

        return new SimpleManagedBlob(newInfo);
    }

    /**
     * Tests the URL (stored in the blob key) using a HEAD http verb, and adding authentication if needed.
     * 
     * @param blob
     * @return true if the URL can be reached with no error
     * @since 8.1
     */
    public boolean urlLooksValid(ManagedBlob blob) {

        String urlStr = extractUrl(blob);

        return urlLooksValid(urlStr);
    }

    /**
     * Tests the URL using a HEAD http verb, and adding authentication if needed.
     * 
     * @param urlStr
     * @return true if the URL can be reached with no error
     * @since 8.1
     */
    public boolean urlLooksValid(String urlStr) {

        boolean looksOk = false;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();

            addHeaders(huc, urlStr);

            huc.setRequestMethod("HEAD");
            int responseCode = huc.getResponseCode();
            looksOk = responseCode == HttpURLConnection.HTTP_OK;

        } catch (Exception e) { // Whatever the error, we fail. No need to be granular here.
            looksOk = false;
        }

        return looksOk;
    }

    /**
     * Sends a HEAD request to get the info without downloading the file.
     * <p>
     * If an error occurs, returns null.
     * 
     * @param urlStr
     * @return the BlobInfo
     * @since 8.1
     */
    public BlobInfo guessInfosFromURL(String urlStr) {

        BlobInfo bi = null;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            addHeaders(connection, urlStr);

            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {

                bi = new BlobInfo();
                
                bi.mimeType = connection.getContentType();
                // Remove possible ...;charset="something"
                int idx = bi.mimeType.indexOf(";");
                if(idx >= 0) {
                    bi.mimeType = bi.mimeType.substring(0, idx);
                }
                
                bi.encoding = connection.getContentEncoding();
                bi.length = connection.getContentLengthLong();
                if (bi.length < 0) {
                    bi.length = 0L;
                }

                String disposition = connection.getHeaderField("Content-Disposition");
                String[] attributes = disposition.split(";");

                for (String attr : attributes) {
                    if (attr.toLowerCase().contains("filename=")) {
                        attr = attr.trim();
                        // Remove filename=
                        String fileName = attr.substring(9);
                        idx = fileName.indexOf("\"");
                        if (idx > -1) {
                            fileName = fileName.substring(idx + 1, fileName.lastIndexOf("\""));
                        }
                        bi.filename = fileName;

                        break;
                    }
                }
            }

        } catch (Exception e) { // Whatever the error, we fail. No need to be granular here.
            bi = null;
        }

        return bi;
    }

}
