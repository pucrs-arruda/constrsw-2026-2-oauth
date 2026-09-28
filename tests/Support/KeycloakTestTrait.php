<?php

declare(strict_types=1);

namespace App\Tests\Support;

trait KeycloakTestTrait
{
    private static ?bool $keycloakAvailable = null;

    /**
     * Verifica conectividade com o Keycloak antes de executar testes que dependem do serviço ativo.
     * Caso o serviço não esteja acessível, o teste é marcado como skipped com mensagem instrutiva.
     */
    protected function requireKeycloak(): void
    {
        if (self::$keycloakAvailable === true) {
            return;
        }

        if (self::$keycloakAvailable === false) {
            $this->skipBecauseKeycloakUnavailable();
        }

        $serverUrl = $_ENV['KEYCLOAK_SERVER_URL'] ?? getenv('KEYCLOAK_SERVER_URL') ?: 'http://keycloak:8080';
        $parts = parse_url($serverUrl);
        $host = $parts['host'] ?? 'keycloak';
        $port = isset($parts['port']) ? (int) $parts['port'] : ($parts['scheme'] === 'https' ? 443 : 80);

        $fp = @fsockopen($host, $port, $errno, $errstr, 0.4);

        if ($fp === false) {
            self::$keycloakAvailable = false;
            $this->skipBecauseKeycloakUnavailable($host, $port);
        }

        fclose($fp);
        self::$keycloakAvailable = true;
    }

    private function skipBecauseKeycloakUnavailable(?string $host = null, ?int $port = null): void
    {
        $endpoint = ($host && $port) ? "{$host}:{$port}" : 'Keycloak';
        $this->markTestSkipped(
            "O serviço Keycloak não está respondendo em {$endpoint}. " .
            "Para executar os testes contra a stack real, inicie o ambiente com: docker compose up -d"
        );
    }
}
