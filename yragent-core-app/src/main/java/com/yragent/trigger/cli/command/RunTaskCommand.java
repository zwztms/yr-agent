package com.yragent.trigger.cli.command;

import com.yragent.service.TaskApplicationService;
import com.yragent.trigger.cli.formatter.CliOutputFormatter;
import com.yragent.domain.gate.PendingDecision;
import com.yragent.domain.gate.PendingDecisionType;
import com.yragent.domain.stage.TaskExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@CommandLine.Command(name = "run-task", mixinStandardHelpOptions = true, description = "Run a task through stage-gated workflow.")
public class RunTaskCommand implements Runnable {

    @CommandLine.Parameters(index = "0", description = "The task description")
    private String input;

    @CommandLine.Option(names = "--gate-input-file", description = "UTF-8 file used to provide gate inputs line by line")
    private Path gateInputFile;

    private final TaskApplicationService taskApplicationService;
    private final CliOutputFormatter cliOutputFormatter;
    private final BufferedReader consoleReader;
    private BufferedReader gateInputReader;

    @Autowired
    public RunTaskCommand(TaskApplicationService taskApplicationService, CliOutputFormatter cliOutputFormatter) {
        this(taskApplicationService, cliOutputFormatter, new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)));
    }

    RunTaskCommand(TaskApplicationService taskApplicationService,
                   CliOutputFormatter cliOutputFormatter,
                   BufferedReader consoleReader) {
        this.taskApplicationService = taskApplicationService;
        this.cliOutputFormatter = cliOutputFormatter;
        this.consoleReader = consoleReader;
    }

    @Override
    public void run() {
        TaskExecutionContext context = taskApplicationService.startTask(input);
        System.out.println(cliOutputFormatter.formatTaskSummary(context));
        while (!context.getPendingDecisions().isEmpty()) {
            String understandingSummary = null;
            String riskSummary = null;
            if (requiresInput(context, PendingDecisionType.UNDERSTANDING_INPUT)) {
                understandingSummary = readLine("请复述你对当前阶段设计的理解: ");
                if (understandingSummary == null) {
                    return;
                }
            }
            if (requiresInput(context, PendingDecisionType.RISK_INPUT)) {
                riskSummary = readLine("请补充你识别到的主要风险、误解点或授权建议: ");
                if (riskSummary == null) {
                    return;
                }
            }

            List<String> confirmedDecisionCodes = List.of();
            if (requiresInput(context, PendingDecisionType.CONFIRMATION)) {
                String confirmationInput = readConfirmationInput();
                if (confirmationInput == null) {
                    return;
                }
                String normalizedInput = confirmationInput.trim();
                if (normalizedInput.isEmpty()) {
                    System.out.println("未输入任何确认项，请重新输入。");
                    continue;
                }
                if ("exit".equalsIgnoreCase(normalizedInput)) {
                    System.out.println("任务已在门禁阶段停止，等待后续确认。");
                    return;
                }
                confirmedDecisionCodes = resolveConfirmedDecisionCodes(normalizedInput, context.getPendingDecisions());
                if (confirmedDecisionCodes.isEmpty()) {
                    System.out.println("输入的确认项无效，请根据 pendingDecisions 里的 code 重新输入。");
                    continue;
                }
            }

            context = taskApplicationService.submitGateInputAndContinue(
                    context,
                    understandingSummary,
                    riskSummary,
                    confirmedDecisionCodes
            );
            System.out.println(cliOutputFormatter.formatTaskSummary(context));
        }
    }

    private String readConfirmationInput() {
        return readLine("请输入要确认的 code，多个用逗号分隔；输入 all 表示全部确认；输入 exit 结束: ");
    }

    private String readLine(String prompt) {
        try {
            System.out.print(prompt);
            return getGateInputReader().readLine();
        } catch (IOException exception) {
            throw new IllegalStateException("读取开发者确认输入失败", exception);
        }
    }

    private BufferedReader getGateInputReader() throws IOException {
        if (gateInputFile == null) {
            return consoleReader;
        }
        if (gateInputReader == null) {
            // 文件输入模式用于稳定复现门禁验证，避免控制台编码干扰。
            gateInputReader = Files.newBufferedReader(gateInputFile, StandardCharsets.UTF_8);
        }
        return gateInputReader;
    }

    private boolean requiresInput(TaskExecutionContext context, PendingDecisionType decisionType) {
        return context.getPendingDecisions().stream()
                .anyMatch(decision -> decision.getType() == decisionType);
    }

    // 把开发者输入解析为本轮允许确认的决策编码，避免把无关 code 写进上下文。
    List<String> resolveConfirmedDecisionCodes(String rawInput, List<PendingDecision> pendingDecisions) {
        Set<String> availableCodes = pendingDecisions.stream()
                .filter(decision -> decision.getType() == PendingDecisionType.CONFIRMATION)
                .map(PendingDecision::getCode)
                .collect(Collectors.toSet());
        if ("all".equalsIgnoreCase(rawInput)) {
            return List.copyOf(availableCodes);
        }

        List<String> requestedCodes = Arrays.stream(rawInput.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .map(code -> code.toLowerCase(Locale.ROOT))
                .toList();
        if (requestedCodes.isEmpty()) {
            return List.of();
        }
        if (!availableCodes.containsAll(requestedCodes)) {
            return List.of();
        }
        return requestedCodes;
    }
}
