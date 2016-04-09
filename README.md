# nuxeo-http-blobprovider



QA build status: [![Build Status](https://qa.nuxeo.org/jenkins/buildStatus/icon?job=Sandbox/sandbox_nuxeo-http-blobprovider-master)](https://qa.nuxeo.org/jenkins/buildStatus/icon?job=Sandbox/sandbox_nuxeo-http-blobprovider-master)

This plug-in provides a [`BlobProvider`](https://doc.nuxeo.com/x/fYYZAQ) class allowing to handle a remote binary, referenced via its URL. The  original binary is not stored in the default BinaryStore of Nuxeo (typically, the File System), it stays on its server. Still, the default features apply: Full text extraction, image metadata, video transcoding, ... (and the result of these computation are hold by Nuxeo: Thumbnail, images of the storyboard, picture conversions, ...).

When the [download service](https://doc.nuxeo.com/display/NXDOC/File+Storage#FileStorage-DownloadService) is involved (a user clicking a "Download" button for example), the blob is read on its remote server.

As of "today" (April 2016), the plug-in handles urls requiring no authentication or basic authentication

To create a blob using this provider, you will use the [`HTTP BlobProvider: Create Blob`](#creating-a-blob-handled)by-the-provider) operation, and pass at least a mime-type and a fileName (if not passed, the plug-in will try to guess the values by sending a `HEAD` request)

# Configuration
As any `BlobProvider`, the HTTP BlobProvider is configured via an _extension point_. This lets you configure different providers (each one with a unique name), handling different servers, and different authentications.

### Credentials, nuxeo.conf and XML Properties
A recommended way of configuring an extension with credentials (which typically must be stored server-side) is to do the following:

1. Have configuration parameters declared in nuxeo.conf, giving the information (login, password, ...)
2. Reference them in the extension point, using the syntax: `#{the.key.name:=}` (notice the terminating `:=`).

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

### Configuration Rules and Important Points

* The `<class>` _must_ be `org.nuxeo.http.blobprovider.HttpBlobProvider`
* `"origin"`: _Must_ start with the protocol ()"http://", "https:/")
* `"authenticationType"`: As of "today" (April, 2016), the plug-in only supports Basic authentication (or no authentication)
* `"moreHeadersJson"`:
  * Let you set some headers expected by the server. There is, however, a restriction to this: These headers are basically statics, not updated depending on dynamic values at the time the plug-in gets the file from the remote server.
  * For example, maybe the remote server has a requirement and can respond only if the `Accept` header is set.
  * The format is a JSON string of an array of objects, each object having the `key` and the `value` fields. For example: `[{key: "Accept", value: "*/*"}, {key: "MyHeader", value: "v1"}]`
  
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
