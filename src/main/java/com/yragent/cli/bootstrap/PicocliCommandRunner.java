package com.yragent.cli.bootstrap;

import com.yragent.cli.command.RunTaskCommand;
import com.yragent.cli.command.YrAgentRootCommand;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
public class PicocliCommandRunner implements CommandLineRunner, ExitCodeGenerator {

    private final YrAgentRootCommand rootCommand;
    private final RunTaskCommand runTaskCommand;
    private int exitCode = 0;

    public PicocliCommandRunner(YrAgentRootCommand rootCommand, RunTaskCommand runTaskCommand) {
        this.rootCommand = rootCommand;
        this.runTaskCommand = runTaskCommand;
    }

    @Override
    public void run(String... args) {
        // 启动时把 Spring Bean 形式的命令对象接入 Picocli，保证 CLI 能真实执行。
        CommandLine commandLine = new CommandLine(rootCommand);
        commandLine.addSubcommand(runTaskCommand);
        exitCode = commandLine.execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
