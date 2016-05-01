# nuxeo-http-blobprovider

QA build status: ![Build Status](https://qa.nuxeo.org/jenkins/buildStatus/icon?job=Sandbox/sandbox_nuxeo-http-blobprovider-master)

This plug-in provides a [`BlobProvider`](https://doc.nuxeo.com/x/fYYZAQ) class allowing to handle a remote binary, referenced via its URL. The  original binary is not stored in the default BinaryStore of Nuxeo (typically, the File System), it stays on its server. Still, the default features apply: Full text extraction, image metadata, video transcoding, ... (and the result of these computation are hold by Nuxeo: Thumbnail, images of the storyboard, picture conversions, ...).

When the [download service](https://doc.nuxeo.com/display/NXDOC/File+Storage#FileStorage-DownloadService) is involved (a user clicking a "Download" button for example), the blob is read on its remote server.

As of "today" (April 2016), the plug-in handles urls requiring no authentication or basic authentication

To create a blob using this provider, you will use the [`HTTP BlobProvider: Create Blob`](#creating-a-blob-handled)by-the-provider) operation, and pass at least a mime-type and a fileName (if not passed, the plug-in will try to guess the values by sending a `HEAD` request)

### Quality Assurance
[QA Last Build Page](http://qa.nuxeo.org/jenkins/job/Sandbox/job/sandbox_nuxeo-http-blobprovider-master/lastBuild/org.nuxeo.http.blobprovider$nuxeo-http-blobprovider-mp/) of the Nuxeo Package, to get the .zip package and install it on your server (no need to build it).

# Configuration
As any `BlobProvider`, the HTTP BlobProvider is configured via an _extension point_. This lets you configure different providers (each one with a unique name), handling different servers, and different authentications.

### Extension Point and Properties

To configure a HTTP BlobProvider, you must:

* Contribute the "configuration" point of the ""BlobManager" extension
* Give it a unique "name"
* Set its class to `.nuxeo.http.blobprovider.HttpBlobProvider` (this is a requirement)
* Setup the properties used by the class:
  * A property is defined by its `"name"` (and you add the value, possibly empty)

Here is an example od configuration with all the properties:

```
<extension target="org.nuxeo.ecm.core.blob.BlobManager" point="configuration">
  <blobprovider name="my-http">
    <class>org.nuxeo.http.blobprovider.HttpBlobProvider</class>
    
    <property name="origin">http://my.site.com</property>
    
    <property name="authenticationType">Basic</property>
    <property name="login">johndoe</property>
    <property name="password">123456</property>
    
    <property name="moreHeadersJson"></property>
    
    <property name="useCache">true</property>
    <!-- Letf empty, so will use default values -->
    <property name="cacheMaxFileSize"></property>
    <property name="cacheMaxCount"></property>
    <property name="cacheMinAge"></property>
  </blobprovider>
</extension>
```

**Important**: The `<class>` _must_ be set to `org.nuxeo.http.blobprovider.HttpBlobProvider`

**Good to Know**: To avoid hard-coding the values in the XML and to use parameters set in the configuration file (nuxeo.conf), see [XML Properties and nuxeo.conf](xml-properties-and-nuxeo-conf).

The properties expected by the plug-in are the following. They can be optional, depending on other properties (fFor example, if you don't use a cache, the cache size property is irrelevant), and some have default values if they are not explicitly set (or are set but empty).

The plug-in can use the following properties:

* `"origin"`:
  * The "prefix" of every URL to be handled by the plug-in. For example "http://my.site.com"
  * _Must_ start with the protocol ("http://", "https:/")
  * When the URL to a file stored by the provider starts with this origin, authentication is applied (if properties about authentication are set).
  * Example (with `"origin"` set to `http://my.site.com`)
    * If the url is "http://my.site.com/the/file" => Authentication headers will be added (if set in the other properties)
    * If the url is "http://other.site.com/some/file" => The plug-in will try to get the file without any authentication (direct download)
  * _Notice_: The plug-in checks the value in a case insensitive way.


* `"authenticationType"`: The protocol used for authentication when getting the remote file.
  * As of "today" (April, 2016), the plug-in only supports Basic authentication or no authentication.
  * The possible values are `Basic` or `None` (case insensitive).
  * An empty value, or no `"authenticationType"` property is handled as `None`

* `"login"` and `"password"` are the values to be used when `"authenticationType"` is `Basic`

* `"moreHeadersJson"`: Let you set some headers expected by the server. There is, however, a restriction to this: These headers are basically statics, not updated depending on dynamic values at the time the plug-in gets the file from the remote server.
  * For example, maybe the remote server has a requirement and can respond only if the `Accept` header is set.
  * The format is a JSON string of an array of objects, each object having the `key` and the `value` fields. For example: `[{key: "Accept", value: "*/*"}, {key: "MyHeader", value: "v1"}]`

* `"useCache"`: Tell the plug-in to use a local File System cache to cache the remote file when it is downloaded, avoiding requesting it to the remote server again and again (10 users downloading the same file in 2 minutes for example).
  * To use the cache, set the value to `true` (case insensitive). Any value that, once converted to lowercase, is not equals to `true` is considered as `false`
  * See [Using a Local Cache](using-a-local-cache) for details about the `"cacheMaxFileSize"`, `"cacheMaxCount"` and `"cacheMinAge"` properties


### XML Properties and nuxeo.conf
A recommended way of configuring an extension with credentials (which typically must be stored server-side) is to do the following:

1. Have configuration parameters declared in nuxeo.conf, giving the information (login, password, ...)
2. Reference them in the extension point, using the syntax: `#{the.key.name:=}` (notice the terminating `:=`).

This allows to deploy the same XML configuration on different servers, so you don't need to build one application/server just because a login or using the cache or not is different from a server to another.

This is what does the default configuration, which declares a `"http"` blob provider with default names for the parameters (see `blobprovider-contrib.xml`):

```
<extension target="org.nuxeo.ecm.core.blob.BlobManager" point="configuration">
  <blobprovider name="http">
    <class>org.nuxeo.http.blobprovider.HttpBlobProvider</class>
    <property name="preventUserUpdate">true</property>
    
    <property name="origin">${http.blobprovider.origin:=}</property>
    <property name="authenticationType">${http.blobprovider.auth.type:=}</property>
    <property name="login">${http.blobprovider.auth.login:=}</property>
    <property name="password">${http.blobprovider.auth.password:=}
    <property name="moreHeadersJson">${http.blobprovider.auth.moreheaders:=}
  </blobprovider>
</extension>
```

So, in nuxeo.conf, you can just set the parameters accordingly:

```
http.blobprovider.origin=http://the.remote.server.example.com
http.blobprovider.auth.type=Basic
http.blobprovider.auth.login=johdoe
http.blobprovider.auth.password=123456
```
  
### Adding Another Provider
You can have basically as many http BlobProvider as you wish. Typically, you will have one provider/server requesting authentication (see [About Authentication](about-authentication)).

Contributing your provider is quite easy. Using the recommended approach, where you store credentials in nuxeo.conf, you would do the following

1. Add the parameters to nuxeo.conf, making sure to use your own keys. For example:

  ```
  other.http.bp.origin=https://some.distant.server
  # In this example, we hard-code the authentication type in the XML
  other.http.bp.login=jdoe
  other.http.bp.pwd=the_password
 ```

2. And the XML would be:

  ```
  <extension target="org.nuxeo.ecm.core.blob.BlobManager" point="configuration">
    <blobprovider name="httpOther">
      <class>org.nuxeo.http.blobprovider.HttpBlobProvider</class>
      <property name="preventUserUpdate">true</property>
      
      <property name="origin">${other.http.bp.origin:=}</property>
      <property name="authenticationType">Basic</property>
      <property name="login">${other.http.bp.login:=}</property>
      <property name="password">${other.http.bp.pwd:=}
    </blobprovider>
  </extension>
  ```

  Notice:

  * The name, "httpOther"
  * Authentication type is hard coded
  * The is no "more headers" property
  * Not using a local cache

# About Authentication
Current implementation supports no authentication or Basic authentication (a login and password must then be set in the configuration).

When the configuration declares an `"origin"` _and_ basic authentication parameters, the following rule applies:

* If the URL to the blob starts with the same origin, the authentication headers are added to the request
* Else, the request is considered as not authenticated.

So, for example, say you set the parameters so that `"origin"` is `"http://my.server.com"`, with "Basic" `"authenticationType"` and values in `"login"` and `"password"`:

* When the URL to get the blob is `"http://my.server.com/something/getthefile.pdf"`, then the request will be authenticated with the login/password set in the configuration
* But if the URL to get the blob is `"http://another.server.org/get/other.pdf"`, then the code will get the distant file without authentication

To summarize: If the URL does not fit the `"origin"` property, no error is trigger, the URL is not rejected by the plug-in, which will still try to get the file with no authentication (and this could fail, of course).

# Creating a Blob Handled by the Provider

To create a blob handled by the provider, you will use the `HTTP BlobProvider: Create Blob` operation. Typically, you will store the URL to use in a field and then call the operation from the `About to Create` and/or `Before modification`.

The `mimeType` and `fileName` parameters should be passed. If they are not passed, the plug-in will try to guess the values by sending a HEAD request to the remote server, which can be costly.

The `HTTP BlobProvider: Create Blob` operation (ID: `HTTPBlobProvider.CreateBlob`):

*  Accepts
  * `Document` and returns `Document` with the blob set to the `blobXPath` field,
  * Or `String` and returns `Blob`
* Parameters:
  * `provider`: Optional. Default value is the default provider of the plug-in, "http".
  * If the input is `Document`, the following parameters must/can be used:
    * `urlXPath`: The field where to read the URL from. Required if the input is `Document`
    * `blobXPath`: Optional. The field where to store the blob (`file:content` by default)
    * `save`: Optional. If `true` the input document will be saved after setting the blob.
  * `mimeType`: Optional, but recommended. The mime type of the distant file (application/pdf, image/png, ...).
  * `fileName`: Optional, but recommended. The name of the distant file
  * `fileSize`: Optional. The exact size of the distant file
  * `encoding`: Optional. The encoding of the distant file
  * `digest`: Optional. The digest of the distant file. If not passed, the URL is used as digest.

# Using a Local Cache

The XML contribution can ask the plug-in to use a local (File System) cache to cache the binaries when they are downloaded. To use is, set the `"useCache"` property to `true` (case insensitive): `<property name="useCache">true</property>`.

_Note_: Internally, it uses the [`LRUFileCache`](https://github.com/nuxeo-archives/nuxeo-common/blob/master/src/main/java/org/nuxeo/common/file/LRUFileCache.java) class.

When the cache is used, you can also setup more properties, that come with default values (so if you don't use them or let them empty, the default values will apply):

* `"cacheMaxSize"`:
  * The maximum size (in bytes) of the cache.
  * Default value is 52428800 (500 MB)
  * Beyond this limit, older files are removed from the cache
* `"cacheMaxCount"`:
  * The maximum number of files to store in the cache
  * Default value is 10000
* `"cacheMinAge"`:
  * The maximum duration for a file to be in the cache
    * The file is not automatically removed from the cache after this time. It will we removed only if either the max. size or the max count is reached. Then, the code looks for files older than the minimum age and remove them.
  * Value is set in _seconds_
  * Default value is 3600


# Build and Install

Assuming [maven](http://maven.apache.org/) (3.2.5) is installed on your system, after downloading the whole repository, execute the following:

  ```
  cd /path/to/nuxeo-http-blobprovider
  mvn clean install
  ```


## License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

## About Nuxeo

Nuxeo provides a modular, extensible Java-based [open source software platform for enterprise content management](http://www.nuxeo.com) and packaged applications for Document Management, Digital Asset Management and Case Management. Designed by developers for developers, the Nuxeo platform offers a modern architecture, a powerful plug-in model and extensive packaging capabilities for building content applications.
