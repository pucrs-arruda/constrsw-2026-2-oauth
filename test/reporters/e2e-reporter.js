/**
 * Reporter de terminal para a suíte e2e: imprime, por teste, o nome completo e
 * se passou ou falhou (com duração). É somado ao reporter padrão em
 * `test/jest-e2e.json`, então o detalhe completo de uma falha continua no
 * relatório normal do Jest.
 */
const GREEN = "\u001b[32m";
const RED = "\u001b[31m";
const YELLOW = "\u001b[33m";
const DIM = "\u001b[2m";
const RESET = "\u001b[0m";

const LABEL = {
  passed: `${GREEN}PASSOU${RESET}`,
  failed: `${RED}FALHOU${RESET}`,
  skipped: `${YELLOW}PULADO${RESET}`,
  pending: `${YELLOW}PENDENTE${RESET}`,
  todo: `${YELLOW}TODO${RESET}`,
  disabled: `${YELLOW}DESABILITADO${RESET}`,
};

class E2EReporter {
  onTestStart(test) {
    process.stdout.write(`\n${DIM}[E2E] Arquivo: ${test.path}${RESET}\n`);
  }

  onTestCaseResult(_test, result) {
    const icon =
      result.status === "passed"
        ? `${GREEN}✔${RESET}`
        : result.status === "failed"
          ? `${RED}✘${RESET}`
          : `${YELLOW}•${RESET}`;
    const label = LABEL[result.status] ?? result.status.toUpperCase();
    const duration = result.duration == null ? "" : ` (${result.duration} ms)`;

    process.stdout.write(
      `[E2E] ${icon} ${label} ${result.fullName}${duration}\n`,
    );

    if (result.status === "failed" && result.failureMessages?.length) {
      const firstLine = result.failureMessages[0].split("\n")[0];
      process.stdout.write(`${RED}      ${firstLine}${RESET}\n`);
    }
  }
}

module.exports = E2EReporter;
