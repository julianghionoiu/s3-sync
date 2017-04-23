package tdl.s3;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import com.amazonaws.services.s3.model.MultipartUpload;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PartSummary;
import org.apache.commons.codec.binary.Hex;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import sun.misc.IOUtils;
import tdl.s3.rules.RemoteTestBucketRule;
import tdl.s3.rules.TempFileRule;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class C_OnDemand_IncompleteFileUpload_AccTest {

    @Rule
    public TempFileRule tempFileRule = new TempFileRule();

    @Rule
    public RemoteTestBucketRule remoteTestBucket = new RemoteTestBucketRule();


    @Before
    public void setUp() throws Exception {
        //Create file to upload and lock file
        File filePrototype = new File("src/test/resources/unfinished_writing_file.bin");
        Files.write(tempFileRule.getLockFile().toPath(), new byte[]{0});
        Files.copy(filePrototype.toPath(), tempFileRule.getFileToUpload().toPath());
    }

    /**
     * NOTES
     * <p>
     * This is where it gets interesting. :)
     * We want to start uploading the video recording as soon as it starts being generated.
     * <p>
     * I am using https://github.com/julianghionoiu/dev-screen-record to generate the recording and
     * it is configured such that information is always appended to the end of the video and never changed.
     * Internally, the video recording is composed of small independent parts and can be played even if the
     * recording has not stopped.
     * This way we can upload chunks of video as it is being generated.
     * <p>
     * The tricky bit is to detect when the recording has completed and the upload can be finalised.
     */
    @Test
    public void should_upload_incomplete_file() throws Exception {
        //Start uploading the file
        String[] uploadingArgs = ("upload -f " + tempFileRule.getFileToUpload()).split(" ");
        SyncFileApp.main(uploadingArgs);

        //create parts hashes
        Map<Integer, String> hashes = calcHashes(tempFileRule.getFileToUpload());

        //Check that the file still not exists on the server
        String fileName = tempFileRule.getFileToUpload().getName();
        ObjectMetadata objectMetadata = remoteTestBucket.getObjectMetadata(fileName);
        assertNull(objectMetadata);

        //Check multipart upload exists
        MultipartUpload multipartUpload = remoteTestBucket.getMultipartUploadFor(fileName)
                .orElseThrow(() -> new AssertionError("Found no multipart upload for: "+fileName));
        //and the parts have the expected ETag


        remoteTestBucket.getPartsFor(multipartUpload).forEach(partSummary -> comparePart(partSummary, hashes));
    }

    private void comparePart(PartSummary partSummary, Map<Integer, String> hashes) {
        int partNumber = partSummary.getPartNumber();
        assertEquals(hashes.get(partNumber), partSummary.getETag());
    }


    @Test
    public void should_be_able_to_continue_incomplete_file_and_finalise() throws Exception {
        //Start uploading the file
        String[] startingUploadingArgs = ("upload -f " + tempFileRule.getFileToUpload()).split(" ");
        SyncFileApp.main(startingUploadingArgs);

        //write additional data and delete lock file
        File fileToUpload = tempFileRule.getFileToUpload();
        tempFileRule.writeDataToFile(fileToUpload);
        Files.delete(tempFileRule.getLockFile().toPath());

        //Start uploading the rest of the file
        String[] uploadingArgs = ("upload -f " + fileToUpload).split(" ");
        SyncFileApp.main(uploadingArgs);

        //Check that the file exists on the server
        String fileName = fileToUpload.getName();
        assertThat(remoteTestBucket.doesObjectExists(fileName), is(true));

        //Check that multipart upload completed and not exists anymore
        assertThat(remoteTestBucket.getMultipartUploadFor(fileName), is(Optional.empty()));

        //check complete file hash. ETag of complete file consists from complete file MD5 hash and some part after "-" sign(probably file version number)
        assertTrue(remoteTestBucket.getObjectMetadata(fileName)
                .getETag().startsWith(getCompleteFileMD5(fileToUpload)));
    }

    /*
     * Complete file hash in S3 ic calculated as MD5 hash of concatenated MD5 hashes of all parts
     */
    private String getCompleteFileMD5(File fileToUpload) throws IOException, NoSuchAlgorithmException {
        Map<Integer, String> partHashes = calcHashes(fileToUpload);
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] hashesData = partHashes.keySet().stream()
                .sorted()
                .map(partHashes::get)
                .map(ByteArrayUtil::hexStringToByteArray)
                .reduce(new byte[0], this::merge);
        return ByteArrayUtil.toHexString(digest.digest(hashesData));
    }

    private byte[] merge(byte[] acc, byte[] hash) {
        int size = acc.length + hash.length;
        byte[] result = new byte[size];
        System.arraycopy(acc, 0, result, 0, acc.length);
        System.arraycopy(hash, 0, result, acc.length, hash.length);
        return result;
    }

    private Map<Integer, String> calcHashes(File fileToUpload) throws IOException, NoSuchAlgorithmException {
        long fileSize = Files.size(fileToUpload.toPath());
        MessageDigest digest = MessageDigest.getInstance("MD5");
        Map<Integer, String> result = new HashMap<>(3, 2);
        try (FileInputStream fileInputStream = new FileInputStream(fileToUpload)) {
            long read = 0;
            for (int i = 1; read < fileSize; i++) {
                int DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024;
                int chunkSize = fileSize - read > DEFAULT_CHUNK_SIZE ? DEFAULT_CHUNK_SIZE : (int) (fileSize - read);
                byte[] chunk = IOUtils.readFully(fileInputStream, chunkSize, true);
                String hash = Hex.encodeHexString(digest.digest(chunk));
                result.put(i, hash);
                read += chunk.length;
            }
        }
        return result;
    }
}