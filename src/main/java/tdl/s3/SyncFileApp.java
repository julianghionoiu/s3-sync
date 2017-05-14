package tdl.s3;

import com.beust.jcommander.JCommander;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.beust.jcommander.Parameters;
import com.beust.jcommander.Parameter;
import tdl.s3.cli.ProgressStatus;
import tdl.s3.sync.RemoteSync;
import tdl.s3.sync.RemoteSyncException;
import tdl.s3.sync.destination.Destination;
import tdl.s3.sync.Filters;
import tdl.s3.sync.Source;
import tdl.s3.sync.destination.S3BucketDestination;

@Parameters
public class SyncFileApp {

    @Parameter(names = {"--config", "-c"})
    private String configPath;

    @Parameter(names = {"--dir", "-d"}, required = true)
    private String dirPath;

    @Parameter(names = {"--recursive", "-R"})
    private boolean recursive = false;

    @Parameter(names = {"--filter"})
    private String regex = "^[0-9a-zA-Z\\_]+\\.mp4";

    public static void main(String[] args) throws RemoteSyncException {
        SyncFileApp app = new SyncFileApp();
        JCommander jCommander = new JCommander(app);
        jCommander.parse(args);

        app.run();
    }

    private void run() throws RemoteSyncException {
        Source source = buildSource();
        Destination destination = buildDestination();
        RemoteSync sync = new RemoteSync(source, destination);

        ProgressStatus progressBar = new ProgressStatus();
        sync.setListener(progressBar);
        sync.run();
    }

    private Source buildSource() {
        Filters filters = Filters.getBuilder()
                .include(Filters.matches(regex))
                .create();
        return Source.getBuilder(Paths.get(dirPath))
                .setFilters(filters)
                .setRecursive(recursive)
                .create();
    }

    private Destination buildDestination() {
        if (configPath == null) {
            return S3BucketDestination.createDefaultDestination();
        }
        Path path = Paths.get(configPath);
        return S3BucketDestination.getBuilder()
                .loadFromPath(path)
                .create();
    }
}
