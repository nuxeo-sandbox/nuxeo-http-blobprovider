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
package org.nuxeo.http.blobprovider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RunnerFeature;
import org.nuxeo.runtime.test.runner.SimpleFeature;

/**
 * This class allows to setup the http blobprovider properties using a file
 * stored locally for the tests
 * <p>
 * The testconf.conf file is ignored in .gitignore. So, to test with your
 * custom URLs and authentication (Basic only as of "today", April, 2016,
 * and still the same in 2024), you must have this file available. The following
 * values are expected (with an example for each)
 * <p>
 * <code>
 *     http.blobprovider.origin=https://your.test.server.your.company.com
 *     http.blobprovider.auth.type=Basic
 * 
 *     AUTH_FILE_URL=https://your.test.server.your.company.com/nuxeo/nxfile/default/edf6f643-3756-4179-8915-2df223cec0a2/file:content
 *     AUTH_FILE_MIME_TYPE=image/jpeg
 *     AUTH_FILE_FILE_NAME=cold-snow-landscape-259522.jpeg
 *     AUTH_FILE_SIZE=3801662
 *     AUTH_FILE_FULL_TEXT_SEARCH=mountain outdoors nature person
 * </code>
 * <p>
 * That said, for authentication to the distant site, we don't store credentials
 * in this file (in case someone makes a mistake and changes .gitignore for example).
 * So, in order for the tests to work, we expect the following environment
 * variables:
 * <ul>
 * <li>NX_HTTP_BLOBPROVIDER_TEST_BASIC_LOGIN</li>
 * <li>NX_HTTP_BLOBPROVIDER_TEST_BASIC_PWD</li>
 * </ul>
 * These must be set and will fill the http.blobprovider.auth.login and http.blobprovider.auth.password
 * parameters expected by the plugin.
 *
 * @since 8.1
 */
public class SimpleFeatureCustom implements RunnerFeature {

    public static final String ENV_VAR_TEST_KEY_LOGIN = "NX_HTTP_BLOBPROVIDER_TEST_BASIC_LOGIN";

    public static final String ENV_VAR_TEST_KEY_PWD = "NX_HTTP_BLOBPROVIDER_TEST_BASIC_PWD";

    public static final String TEST_CONF_FILE = "testconf.conf";

    public static final String CONF_KEY_HAS_LOCAL_CONF = "TEST_HTTP_BLOBPROVIDER_HAS_LOCALCONF";

    // <-------------------- These values are expected when testing authentication -------------------->
    public static final String CONF_KEY_AUTH_FILE_URL = "AUTH_FILE_URL";

    public static final String CONF_KEY_AUTH_FILE_MIME_TYPE = "AUTH_FILE_MIME_TYPE";

    public static final String CONF_KEY_AUTH_FILE_FILE_NAME = "AUTH_FILE_FILE_NAME";

    public static final String CONF_KEY_AUTH_FILE_SIZE = "AUTH_FILE_SIZE";

    public static final String AUTH_FILE_FULL_TEXT_SEARCH = "AUTH_FILE_FULL_TEXT_SEARCH";

    // These are the properties that are tested. You must declare/use the same in your testconf.conf file
    protected static boolean testConfigurationOk = false;

    protected static Properties localProperties = null;

    public static String getLocalProperty(String key) {

        if (localProperties != null) {
            return localProperties.getProperty(key);
        }

        return null;
    }

    public static boolean hasValidTestConfiguration() {
        return testConfigurationOk;
    }

    @Override
    public void initialize(FeaturesRunner runner) throws Exception {

        localProperties = null;
        File file = null;
        FileInputStream fileInput = null;
        try {
            file = FileUtils.getResourceFileFromContext(TEST_CONF_FILE);
            fileInput = new FileInputStream(file);
            localProperties = new Properties();
            localProperties.load(fileInput);

        } catch (Exception e) {
            localProperties = null;
        } finally {
            if (fileInput != null) {
                try {
                    fileInput.close();
                } catch (IOException e) {
                    // Ignore
                }
                fileInput = null;
            }
        }

        if(!addEnvironmentVariable(ENV_VAR_TEST_KEY_LOGIN)) {
            System.out.println("No login defined in " + ENV_VAR_TEST_KEY_LOGIN + " => No test with authentication.");
            return;
        }
        if(!addEnvironmentVariable(ENV_VAR_TEST_KEY_PWD)) {
            System.out.println("No login defined in " + ENV_VAR_TEST_KEY_PWD + " => No test with authentication.");
            return;
        }

        if (localProperties != null) {

            Properties systemProps = System.getProperties();

            systemProps.put(HttpBlobProvider.KEY_AUTHENTICATION_LOGIN, localProperties.get(ENV_VAR_TEST_KEY_LOGIN));
            systemProps.put(HttpBlobProvider.KEY_AUTHENTICATION_PWD, localProperties.get(ENV_VAR_TEST_KEY_PWD));

            systemProps.put(HttpBlobProvider.KEY_ORIGIN, localProperties.get(HttpBlobProvider.KEY_ORIGIN));
            systemProps.put(HttpBlobProvider.KEY_AUTHENTICATION_TYPE,
                    localProperties.get(HttpBlobProvider.KEY_AUTHENTICATION_TYPE));

            String type = systemProps.getProperty(HttpBlobProvider.KEY_AUTHENTICATION_TYPE);
            String login = systemProps.getProperty(HttpBlobProvider.KEY_AUTHENTICATION_LOGIN);
            String pwd = systemProps.getProperty(HttpBlobProvider.KEY_AUTHENTICATION_PWD);
            String origin = systemProps.getProperty(HttpBlobProvider.KEY_ORIGIN);

            if (StringUtils.isNoneBlank(type, login, pwd, origin)) {
                testConfigurationOk = true;
            } else {
                testConfigurationOk = false;
                System.out.println(String.format(
                        "**TEST WARNING**\nMissing configuration parameter in authType (%s), login (%s), pwd (%s) and/or origin (%s)",
                        type, login, pwd, origin));
            }
        }
    }

    protected boolean addEnvironmentVariable(String key) {
        String value = System.getenv(key);
        if (value != null) {
            if (localProperties == null) {
                localProperties = new Properties();
            }
            localProperties.put(key, value);
            return true;
        }
        
        return false;
    }

    @Override
    public void stop(FeaturesRunner runner) throws Exception {

        Properties p = System.getProperties();
        p.remove(HttpBlobProvider.KEY_ORIGIN);
        p.remove(HttpBlobProvider.KEY_AUTHENTICATION_TYPE);
        p.remove(HttpBlobProvider.KEY_AUTHENTICATION_LOGIN);
        p.remove(HttpBlobProvider.KEY_AUTHENTICATION_PWD);
    }

}
