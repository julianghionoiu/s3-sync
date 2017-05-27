package tdl.s3.upload;

import java.io.File;
import tdl.s3.sync.destination.DestinationOperationException;

/**
 * General interface to upload any file to S3 storage
 *
 */
public interface FileUploader {

    void upload(File file);

    void upload(File file, String path);

    boolean exists(String path) throws DestinationOperationException;
}
