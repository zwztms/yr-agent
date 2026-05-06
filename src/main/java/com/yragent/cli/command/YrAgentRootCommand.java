package com.yragent.cli.command;

import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(
        name = "yragent",
        mixinStandardHelpOptions = true,
        description = "YRAgent CLI entry."
)
public class YrAgentRootCommand implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
