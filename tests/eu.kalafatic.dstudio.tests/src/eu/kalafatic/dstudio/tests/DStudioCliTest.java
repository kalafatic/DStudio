package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.application.cli.DStudioCli;
import eu.kalafatic.dstudio.application.cli.DStudioCliCommandTable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Headless CLI contract tests (no coordinator required): the help output is generated
 * from the same table used for dispatch, suggestions use case-insensitive prefixes and
 * small edit distances, and stop/exit terminate the session.
 */
class DStudioCliTest {

    private static DStudioCli cli(StringWriter buffer) {
        return new DStudioCli(null, new PrintWriter(buffer, true));
    }

    @Test
    void commandTableIsConsistent() {
        Set<String> names = new HashSet<>();
        for (DStudioCliCommandTable.Command command : DStudioCliCommandTable.COMMANDS) {
            assertFalse(command.name().isBlank(), "command name must not be blank");
            assertFalse(command.syntax().isBlank(), "syntax must not be blank");
            assertFalse(command.shortHelp().isBlank(), "short help must not be blank");
            assertFalse(command.topic().isBlank(), "topic must not be blank");
            assertTrue(command.syntax().startsWith(command.name()),
                    "syntax must start with the command name: " + command.name());
            assertTrue(names.add(command.name()), "duplicate command name: "
                    + command.name());
            assertTrue(DStudioCliCommandTable.TOPIC_HELP.containsKey(command.topic()),
                    "topic help missing for topic: " + command.topic());
        }
        // stop/exit are the only terminating commands
        for (DStudioCliCommandTable.Command command : DStudioCliCommandTable.COMMANDS) {
            if (!"stop".equals(command.name()) && !"exit".equals(command.name())) {
                assertFalse(command.terminatesRepl(), command.name()
                        + " must not terminate the REPL");
            } else {
                assertTrue(command.terminatesRepl(), command.name()
                        + " must terminate the REPL");
            }
        }
    }

    @Test
    void helpListsEveryCommandFromTheDispatchTable() {
        StringWriter buffer = new StringWriter();
        cli(buffer).dispatch("help");
        String output = buffer.toString();
        for (DStudioCliCommandTable.Command command : DStudioCliCommandTable.COMMANDS) {
            assertTrue(output.contains(command.name()),
                    "help output missing command: " + command.name());
            assertTrue(output.contains(command.syntax()),
                    "help output missing syntax for: " + command.name());
        }
    }

    @Test
    void mandatoryFamilyVocabularyIsRegistered() {
        Set<String> names = new HashSet<>();
        for (DStudioCliCommandTable.Command command : DStudioCliCommandTable.COMMANDS) {
            names.add(command.name());
        }
        for (String required : new String[] {"help", "status", "start", "stop",
                "restart", "web", "terminal", "models", "sessions", "providers",
                "agents", "config", "logs", "exit"}) {
            assertTrue(names.contains(required),
                    "mandatory CLI command missing: " + required);
        }
    }

    @Test
    void slashIsOptional() {
        StringWriter plain = new StringWriter();
        cli(plain).dispatch("help");
        StringWriter slashed = new StringWriter();
        cli(slashed).dispatch("/help");
        assertEquals(plain.toString(), slashed.toString());
    }

    @Test
    void dispatchIsCaseInsensitive() {
        StringWriter buffer = new StringWriter();
        assertTrue(cli(buffer).dispatch("STATUS"));
        assertFalse(buffer.toString().isBlank());
    }

    @Test
    void helpTopicPrintsDetailedHelp() {
        StringWriter buffer = new StringWriter();
        cli(buffer).dispatch("help sessions");
        String output = buffer.toString();
        assertTrue(output.contains("sessions list"), output);
        assertTrue(output.contains("sessions show"), output);
        assertTrue(output.contains("sessions cancel"), output);
    }

    @Test
    void helpCliTopicPrintsCliOverview() {
        StringWriter buffer = new StringWriter();
        cli(buffer).dispatch("help cli");
        String output = buffer.toString();
        assertTrue(output.contains("headless CLI"), output);
        assertFalse(output.contains("Unknown topic"), output);
    }

    @Test
    void unknownTopicSuggestsNearestMatch() {
        StringWriter buffer = new StringWriter();
        cli(buffer).dispatch("help sessionz");
        String output = buffer.toString();
        assertTrue(output.contains("Unknown topic 'sessionz'."), output);
        assertTrue(output.contains("Did you mean:"), output);
        assertTrue(output.contains("sessions"), output);
        assertTrue(output.contains("Type 'help' for the list of commands."), output);
    }

    @Test
    void unknownCommandSuggestsNearestMatchAndPrintsSummaryLine() {
        StringWriter buffer = new StringWriter();
        cli(buffer).dispatch("statuz");
        String output = buffer.toString();
        assertTrue(output.contains("Unknown command 'statuz'."), output);
        assertTrue(output.contains("Did you mean:"), output);
        assertTrue(output.contains("status"), output);
        assertTrue(output.contains("Type 'help' for the list of commands."), output);
    }

    @Test
    void unknownCommandSuggestsByPrefix() {
        StringWriter buffer = new StringWriter();
        cli(buffer).dispatch("pro");
        String output = buffer.toString();
        assertTrue(output.contains("Unknown command 'pro'."), output);
        assertTrue(output.contains("Did you mean:"), output);
        assertTrue(output.contains("providers"), output);
    }

    @Test
    void exitAndStopTerminateTheSession() {
        assertFalse(cli(new StringWriter()).dispatch("exit"));
        assertFalse(cli(new StringWriter()).dispatch("/exit"));
        assertFalse(cli(new StringWriter()).dispatch("stop"));
        assertFalse(cli(new StringWriter()).dispatch("/stop"));
    }

    @Test
    void regularCommandsKeepTheSessionRunning() {
        StringWriter buffer = new StringWriter();
        DStudioCli cli = cli(buffer);
        for (String command : new String[] {"status", "web", "help", "config show",
                "logs show", "agents list", "sessions list", "models list",
                "providers list", "start", "restart"}) {
            assertTrue(cli.dispatch(command), "command should keep the session: "
                    + command);
        }
        assertNotEquals(0, buffer.toString().length());
    }

    @Test
    void startAndRestartReportUnavailableWithoutCoordinator() {
        StringWriter buffer = new StringWriter();
        DStudioCli cli = cli(buffer);
        assertTrue(cli.dispatch("start"));
        assertTrue(cli.dispatch("restart"));
        String output = buffer.toString();
        assertTrue(output.contains("unavailable"), output);
    }

    @Test
    void blankLinesAreIgnored() {
        StringWriter buffer = new StringWriter();
        DStudioCli cli = cli(buffer);
        assertTrue(cli.dispatch(""));
        assertTrue(cli.dispatch("   "));
        assertTrue(cli.dispatch("/"));
        assertEquals("", buffer.toString());
    }
}
