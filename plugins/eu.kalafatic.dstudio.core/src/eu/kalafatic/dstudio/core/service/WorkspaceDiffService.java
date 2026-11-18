package eu.kalafatic.dstudio.core.service;

import java.nio.file.Path;
import java.util.List;

public interface WorkspaceDiffService {

    boolean isGitRepository(Path directory);

    Object takeSnapshot(Path directory);

    List<String> collectChangedFiles(Object snapshot, Path directory);

    String diffText(Object snapshot, Path directory, String relativePath);

    String baseContent(Object snapshot, Path directory, String relativePath);
}
