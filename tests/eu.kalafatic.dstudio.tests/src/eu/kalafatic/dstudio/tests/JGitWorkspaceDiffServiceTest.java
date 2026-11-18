package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.service.WorkspaceDiffService;
import eu.kalafatic.dstudio.infrastructure.git.JGitWorkspaceDiffService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JGitWorkspaceDiffServiceTest {

    @TempDir
    Path tempDir;

    private final WorkspaceDiffService diffService = new JGitWorkspaceDiffService();

    @Test
    void nonGitDirectoryIsNotARepository() throws Exception {
        Path dir = tempDir.resolve("plain");
        Files.createDirectories(dir);
        assertFalse(diffService.isGitRepository(dir));
        assertEquals(null, diffService.takeSnapshot(dir));
    }

    @Test
    void modifiedFileIsCollectedAfterSnapshot() throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            Path file = repo.resolve("src.txt");
            Files.writeString(file, "initial content\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial commit").call();
        }
        assertTrue(diffService.isGitRepository(repo));
        Object snapshot = diffService.takeSnapshot(repo);
        assertNotNull(snapshot);
        assertTrue(diffService.collectChangedFiles(snapshot, repo).isEmpty());
        Files.writeString(repo.resolve("src.txt"), "changed content\n");
        List<String> changed = diffService.collectChangedFiles(snapshot, repo);
        assertEquals(1, changed.size());
        assertEquals("src.txt", changed.get(0));
        String diff = diffService.diffText(snapshot, repo, "src.txt");
        assertTrue(diff.contains("diff --git"));
        assertTrue(diff.contains("changed content"));
    }

    @Test
    void newUntrackedFileIsCollectedWithSyntheticDiff() throws Exception {
        Path repo = tempDir.resolve("repo-new");
        Files.createDirectories(repo);
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            Path file = repo.resolve("existing.txt");
            Files.writeString(file, "existing\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial commit").call();
        }
        Object snapshot = diffService.takeSnapshot(repo);
        Files.writeString(repo.resolve("new-file.txt"), "brand new\n");
        List<String> changed = diffService.collectChangedFiles(snapshot, repo);
        assertEquals(1, changed.size());
        assertEquals("new-file.txt", changed.get(0));
        String diff = diffService.diffText(snapshot, repo, "new-file.txt");
        assertTrue(diff.contains("new file mode"));
        assertTrue(diff.contains("brand new"));
    }

    @Test
    void preExistingDirtyFilesAreNotReportedAsNew() throws Exception {
        Path repo = tempDir.resolve("repo-dirty");
        Files.createDirectories(repo);
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            Path file = repo.resolve("dirty.txt");
            Files.writeString(file, "initial\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial commit").call();
        }
        Files.writeString(repo.resolve("dirty.txt"), "dirty before snapshot\n");
        Object snapshot = diffService.takeSnapshot(repo);
        Files.writeString(repo.resolve("dirty.txt"), "dirty after snapshot\n");
        List<String> changed = diffService.collectChangedFiles(snapshot, repo);
        assertTrue(changed.isEmpty());
    }
}
