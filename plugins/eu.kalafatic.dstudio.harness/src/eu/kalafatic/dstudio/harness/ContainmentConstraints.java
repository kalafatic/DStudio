package eu.kalafatic.dstudio.harness;

import java.nio.file.Path;

public final class ContainmentConstraints {

    private ContainmentConstraints() {
    }

    public static String appendConstraints(String prompt, Path workingDirectory) {
        return prompt + "\n\n[EXECUTION CONSTRAINTS]\n"
                + "Work only inside the directory " + workingDirectory
                + ". Create and modify files using paths relative to that directory only. "
                + "Never use absolute paths outside this directory.";
    }
}
