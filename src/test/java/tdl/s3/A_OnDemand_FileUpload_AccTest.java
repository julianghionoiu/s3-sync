package tdl.s3;

import com.amazonaws.services.s3.model.ObjectMetadata;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import org.junit.ClassRule;
import tdl.s3.rules.LocalS3Server;
import tdl.s3.rules.LocalTestBucket;
import tdl.s3.sync.RemoteSync;
import tdl.s3.sync.Filters;
import tdl.s3.sync.Source;

public class A_OnDemand_FileUpload_AccTest {

    @ClassRule
    public static LocalS3Server localS3Server = new LocalS3Server();
    
    @Rule
    public LocalTestBucket localTestBucket = new LocalTestBucket();

    @Test
    public void should_not_upload_file_if_already_present() throws Exception {
        Path path = Paths.get("src/test/resources/test_a_1/");
        Filters filters = Filters.getBuilder().include(Filters.endsWith("txt")).create();
        Source source = Source.getBuilder(path)
                .setFilters(filters)
                .create();

        //Upload first file just to check in test that it will not be uploaded twice
        RemoteSync sync = new RemoteSync(source, localTestBucket.asDestination());
        sync.run();

        // Sleep 2 seconds to distinguish that file uploaded_once.txt on aws was not uploaded by next call
        Thread.sleep(2000);
        Instant uploadingTime = Instant.now();
        sync.run();

        ObjectMetadata objectMetadata = localTestBucket.getObjectMetadata("already_uploaded.txt");
        Instant actualLastModifiedDate = objectMetadata.getLastModified().toInstant();

        //Check that file is older than last uploading start
        assertTrue(actualLastModifiedDate.isBefore(uploadingTime));
    }

    @Test
    public void should_upload_simple_file_to_bucket() throws Exception {
        Path path = Paths.get("src/test/resources/test_a_2/");
        Filters filters = Filters.getBuilder().include(Filters.endsWith("txt")).create();
        Source source = Source.getBuilder(path)
                .setFilters(filters)
                .create();

        RemoteSync sync = new RemoteSync(source, localTestBucket.asDestination());
        sync.run();

        assertThat(localTestBucket.doesObjectExists("sample_small_file_to_upload.txt"), is(true));
    }

    @Test
    public void should_upload_large_file_to_bucket_using_multipart_upload() throws Exception {
        Path path = Paths.get("src/test/resources/test_a_3/");
        Filters filters = Filters.getBuilder().include(Filters.endsWith("bin")).create();
        Source source = Source.getBuilder(path)
                .setFilters(filters)
                .create();

        RemoteSync sync = new RemoteSync(source, localTestBucket.asDestination());
        sync.run();

        assertThat(localTestBucket.doesObjectExists("large_file.bin"), is(true));
    }

}
