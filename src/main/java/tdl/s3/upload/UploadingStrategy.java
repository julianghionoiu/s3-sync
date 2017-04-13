package tdl.s3.upload;

import com.amazonaws.services.s3.AmazonS3;

import java.io.File;

/**
 * @author vdanyliuk
 * @version 12.04.17.
 */
public interface UploadingStrategy {

    void upload(AmazonS3 s3, String bucket, File file, String newName) throws Exception;
}