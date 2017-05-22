[![Java Version](http://img.shields.io/badge/Java-1.8-blue.svg)](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
[![Download](https://api.bintray.com/packages/julianghionoiu/maven/s3-sync-stream/images/download.svg)](https://bintray.com/julianghionoiu/maven/s3-sync-stream/_latestVersion)
[![Codeship Status for julianghionoiu/s3-sync-stream](https://img.shields.io/codeship/b617e390-006f-0135-fe1b-4ee982914aba/master.svg)](https://codeship.com/projects/212588)
[![Coverage Status](https://coveralls.io/repos/github/julianghionoiu/s3-sync-stream/badge.svg?branch=master)](https://coveralls.io/github/julianghionoiu/s3-sync?branch=master)

`s3-sync-stream` is library that continuously syncs the contents of a folder to an S3 bucket. Optimised for streaming file formats (video, logs).

The library will aggresively upload content as it is being generated:
* Each chunk will be uploaded as a part in a multipart upload
* The multipart upload in kept open while the file is being generated 
* It is the responsibility of the generator to create a `.lock` with the same name.
* Once the file generation is completed and the `.lock` file removed, the multipart upload will be finalised.


## To use as a library

### Add as Maven dependency

Add a dependency to `tdl:s3-sync-stream` in `compile` scope. See `bintray` shield for latest release number.
```xml
<dependency>
  <groupId>ro.ghionoiu</groupId>
  <artifactId>s3-sync-stream</artifactId>
  <version>X.Y.Z</version>
</dependency>
```

### Configure AWS user with minimal permissions

**WIP** - TODO Add detailed IAM instructions

### Define sync source and destination

Configure the local folder as a `source` and define AWS S3 as the `destination`
```java
Source source = Source.getBuilder(/* Path */ pathToFolder)
  .traverseDirectories(true)
  .include(endsWith(".mp4")
  .exclude(startsWith(".")
  .exclude(matches("tmp.log"))
  .create();

Destination destination = Destination.getBuilder()
  .loadFromPath(/* Path */ pathToFile)
  .create();
```

Construct the `RemoteSync` and run. The `run` method can be invoked multiple times.
```java
remoteSync = new RemoteSync(source, destination);
remoteSync.run();
```

### Example source definitions

The source will be a set of filters that can be applied to a folder to obtain a list of files to be synced

**Default values** will not include .lock files and hidden files (. files)
```java
Source source = Source.getBuilder(/* Path */ pathToFolder)
  .includeAll()
  .create();
```

**Single file** can be selected using a matcher
```java
Filter filter = Filter.getBuilder().matches("file.txt");

Source source = Source.getBuilder(/* Path */ pathToFolder)
  .include(filter)
  .create();
```

**Multiple files** can be included if they match one of the matchers.
The list of included files can be further filtered via exclude matchers
```java
Filter includeFilter = Filter.getBuilder()
                        .endsWith(".mp4")
                        .endsWith(".log")
                        .create();

Filter excludeFilter = Filter.getBuilder()
                        .matches("tmp.log")
                        .create();

Source source = Source.getBuilder(/* Path */ pathToFolder)
  .include(includeFilter)
  .exclude(excludeFilter)
  .create();
```

By default the library will not **traverse directories**, if you need this behaviour than you can set the `traverseDirectories` flag to true
```java
Source source = Source.getBuilder(/* Path */ pathToFolder)
  .traverseDirectories(true)
  .includeAll()
  .create();
```

If no include matcher is specified then an **IllegalArgumentException** will be raised upon creation:
```java
Source source = Source.getBuilder(/* Path */ pathToFolder)
  .create();
```

## Development

### Prepare environment

Configuration for running this service should be placed in file `.private/aws-test-secrets` in Java Properties file format. For examples.

```properties
aws_access_key_id=ABCDEFGHIJKLM
aws_secret_access_key=ABCDEFGHIJKLM
s3_region=ap-southeast-1
s3_bucket=bucketname
s3_prefix=prefix/
```

The values are:
* `aws_access_key_id` - access key to the AWS account.
* `aws_secret_access_key` - secret key to the AWS account.
* `s3_region` - this contains the region that holds the S3 bucket.
* `s3_bucket` the bucket that will store the uploaded files.
* `s3_prefix` S3 prefix that will be added before all files

### Build and run as command-line app
```bash
./gradlew shadowJar
java -Dlogback.configurationFile=`pwd`/logback.xml \
    -jar ./build/libs/s3-sync-stream-0.0.6-SNAPSHOT-all.jar \
    -c ./.private/aws-test-secrets \
    -d ./src/test/resources/test_a_1 \
    --filter "^[0-9a-zA-Z\\_]+\\.txt$"
```

### Install to mavenLocal

If you want to build the SNAPSHOT version locally you can install to the local Maven cache
```
./gradlew -x test clean install
```

### Release to jcenter and mavenCentral

The CI server is configured to pushs release branches to Bintray.
You trigger the process by running the `release` command locally. 

The command will increment the release number and create and annotated tag:
```bash
./gradlew release
git push --tags
```

### Inspect traffic with Charles Proxy

- Install Charles Proxy: https://www.charlesproxy.com/
- Enable SSL Proxying: https://www.charlesproxy.com/documentation/proxying/ssl-proxying/
- Add SSL host: `*.amazonaws.com`
- Export Charles certificate, `SSL Proxying > Save Charles Root Certificate`
- Import into Java Keystore
```
sudo keytool -import -alias charles \
  -file "${CERT_SAVE_LOCATION}/charles-ssl-proxying-certificate.pem" \
  -keystore "${JAVA_HOME}/jre/lib/security/cacerts" \
  -storepass changeit
```
- Traffic should appear in Charles