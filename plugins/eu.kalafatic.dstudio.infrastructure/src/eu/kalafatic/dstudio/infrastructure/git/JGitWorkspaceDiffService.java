package eu.kalafatic.dstudio.infrastructure.git;

import eu.kalafatic.dstudio.core.service.WorkspaceDiffService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.filter.PathFilter;

public class JGitWorkspaceDiffService implements WorkspaceDiffService {

    @Override
    public boolean isGitRepository(Path directory) {
        try (Repository repo = open(directory)) {
            return repo != null;
        } catch (Exception e) {
            return false;
        }
    }

    private Repository open(Path directory) throws IOException {
        Path gitDir = directory.resolve(".git");
        if (!Files.exists(gitDir)) {
            return null;
        }
        return new FileRepositoryBuilder().setGitDir(gitDir.toFile()).build();
    }

    @Override
    public Object takeSnapshot(Path directory) {
        Map<String, String> snapshot = new HashMap<>();
        try (Repository repo = open(directory)) {
            if (repo == null) {
                return null;
            }
            try (Git git = new Git(repo)) {
                Status status = git.status().call();
                for (String file : status.getUncommittedChanges()) {
                    snapshot.put(file, "");
                }
                for (String file : status.getUntracked()) {
                    snapshot.put(file, "");
                }
            }
            return snapshot;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> collectChangedFiles(Object snapshot, Path directory) {
        List<String> result = new ArrayList<>();
        if (snapshot == null) {
            return result;
        }
        Map<String, String> before = (Map<String, String>) snapshot;
        try (Repository repo = open(directory)) {
            if (repo == null) {
                return result;
            }
            try (Git git = new Git(repo)) {
                Status status = git.status().call();
                for (String file : status.getUncommittedChanges()) {
                    if (!before.containsKey(file)) {
                        result.add(file);
                    }
                }
                for (String file : status.getUntracked()) {
                    if (!before.containsKey(file)) {
                        result.add(file);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public String diffText(Object snapshot, Path directory, String relativePath) {
        try (Repository repo = open(directory)) {
            if (repo == null) {
                return "";
            }
            try (Git git = new Git(repo)) {
                if (snapshot instanceof Map<?, ?> before
                        && !before.containsKey(relativePath)) {
                    Path file = directory.resolve(relativePath).normalize();
                    if (Files.isRegularFile(file)) {
                        return newFileDiff(relativePath, file);
                    }
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                git.diff().setOutputStream(out)
                        .setPathFilter(PathFilter.create(relativePath)).call();
                return out.toString(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public String baseContent(Object snapshot, Path directory, String relativePath) {
        if (snapshot instanceof Map<?, ?> before
                && !before.containsKey(relativePath)) {
            return "";
        }
        try (Repository repo = open(directory)) {
            if (repo == null) {
                return "";
            }
            org.eclipse.jgit.lib.ObjectId headTree = repo.resolve("HEAD^{tree}");
            if (headTree == null) {
                return "";
            }
            try (org.eclipse.jgit.revwalk.RevWalk walk =
                    new org.eclipse.jgit.revwalk.RevWalk(repo)) {
                org.eclipse.jgit.revwalk.RevTree tree = walk.parseTree(headTree);
                try (org.eclipse.jgit.treewalk.TreeWalk treeWalk =
                        org.eclipse.jgit.treewalk.TreeWalk.forPath(repo,
                                relativePath, tree)) {
                    if (treeWalk == null) {
                        return "";
                    }
                    org.eclipse.jgit.lib.ObjectId blobId = treeWalk.getObjectId(0);
                    org.eclipse.jgit.lib.ObjectLoader loader =
                            repo.open(blobId, org.eclipse.jgit.lib.Constants.OBJ_BLOB);
                    return new String(loader.getBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            return "";
        }
    }

    private String newFileDiff(String relativePath, Path file) throws IOException {
        StringBuilder diff = new StringBuilder();
        diff.append("diff --git a/").append(relativePath).append(" b/")
                .append(relativePath).append('\n');
        diff.append("new file mode 100644\n");
        diff.append("--- /dev/null\n");
        diff.append("+++ b/").append(relativePath).append('\n');
        List<String> lines = Files.readAllLines(file);
        if (lines.isEmpty()) {
            diff.append("@@ -0,0 +1,0 @@\n");
        } else {
            diff.append("@@ -0,0 +1,").append(lines.size()).append(" @@\n");
            for (String line : lines) {
                diff.append('+').append(line).append('\n');
            }
        }
        return diff.toString();
    }
}
