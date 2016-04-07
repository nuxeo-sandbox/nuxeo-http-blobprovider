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

import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.SimpleFeature;

/**
 * This class allows to setup the http blobprovider properties using a file stored locally for the tests, because it may
 * use loginb/pwd that can't be hard coded on GitHub
 * <p>
 * The testconf.conf file is ignored in .gitignore. So, to test with your custom URLs and authentication (Basic only as
 * of "today", April, 2016), you must have this file
 * 
 * @since 8.1
 */
public class SimpleFeatureCustom extends SimpleFeature {

    public static final String TEST_CONF_FILE = "testconf.conf";
    
    public static final String CONF_KEY_HAS_LOCAL_CONF = "TEST_HTTP_BLOBPROVIDER_HAS_LOCALCONF";

    @Override
    public void initialize(FeaturesRunner runner) throws Exception {
        
        Properties localProps = null;
        File file = null;
        FileInputStream fileInput = null;
        try {
            file = FileUtils.getResourceFileFromContext(TEST_CONF_FILE);
            fileInput = new FileInputStream(file);
            localProps = new Properties();
            localProps.load(fileInput);
        } catch (Exception e) {
            localProps = null;
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
        
        Properties p = System.getProperties();
        p.put(CONF_KEY_HAS_LOCAL_CONF, localProps != null);
        if(localProps != null) {
            p.put(HttpBlobProvider.KEY_DOMAIN, localProps.get(HttpBlobProvider.KEY_DOMAIN));
            p.put(HttpBlobProvider.KEY_AUTHENTICATION_TYPE, localProps.get(HttpBlobProvider.KEY_AUTHENTICATION_TYPE));
            p.put(HttpBlobProvider.KEY_AUTHENTICATION_LOGIN, localProps.get(HttpBlobProvider.KEY_AUTHENTICATION_LOGIN));
            p.put(HttpBlobProvider.KEY_AUTHENTICATION_PWD, localProps.get(HttpBlobProvider.KEY_AUTHENTICATION_PWD));
        }
    }
    
    @Override
    public void stop(FeaturesRunner runner) throws Exception {
        
        Properties p = System.getProperties();
        p.remove(CONF_KEY_HAS_LOCAL_CONF);
        p.remove(HttpBlobProvider.KEY_DOMAIN);
        p.remove(HttpBlobProvider.KEY_AUTHENTICATION_TYPE);
        p.remove(HttpBlobProvider.KEY_AUTHENTICATION_LOGIN);
        p.remove(HttpBlobProvider.KEY_AUTHENTICATION_PWD);
    }

}
