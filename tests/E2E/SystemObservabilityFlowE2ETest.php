<?php

declare(strict_types=1);

namespace App\Tests\E2E;

use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\HttpFoundation\Response;

/**
 * Teste End-to-End para Verificação de Saúde, Métricas Prometheus e Documentação OpenAPI/Swagger.
 *
 * Cenário E2E:
 * 1. Healthcheck em todas as rotas suportadas (/, /health, /api/health) -> 200 OK.
 * 2. Emissão de requisições de teste para incrementar contadores.
 * 3. Coleta de métricas em /metrics -> Formato Prometheus contendo 'http_requests_total'.
 * 4. Acesso à documentação Swagger UI em /docs -> 200 OK HTML.
 * 5. Download da especificação OpenAPI em /docs/openapi.json -> 200 OK com todas as rotas e schemas.
 */
final class SystemObservabilityFlowE2ETest extends WebTestCase
{
    private KernelBrowser $client;

    protected function setUp(): void
    {
        $this->client = static::createClient();
    }

    public function testObservabilityAndDocumentationE2E(): void
    {
        // 1. Healthchecks
        $healthEndpoints = ['/', '/health', '/api/health'];
        foreach ($healthEndpoints as $endpoint) {
            $this->client->request('GET', $endpoint);
            $response = $this->client->getResponse();
            $this->assertSame(Response::HTTP_OK, $response->getStatusCode(), "Endpoint {$endpoint} deve retornar 200 OK");

            $data = json_decode((string) $response->getContent(), true);
            $this->assertIsArray($data);
            $this->assertSame('healthy', $data['status']);
            $this->assertSame('oauth', $data['service']);
        }

        // 2. Disparar uma requisição qualquer para incrementar métricas
        $this->client->request('GET', '/health');

        // 3. Coleta de métricas Prometheus (/metrics)
        $this->client->request('GET', '/metrics');
        $metricsResponse = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $metricsResponse->getStatusCode(), 'GET /metrics deve retornar 200 OK');
        $this->assertStringContainsString('text/plain', (string) $metricsResponse->headers->get('Content-Type'));

        $metricsContent = (string) $metricsResponse->getContent();
        $this->assertStringContainsString('http_requests_total', $metricsContent);
        $this->assertStringContainsString('php_memory_bytes', $metricsContent);

        // 4. Swagger UI HTML (/docs)
        $this->client->request('GET', '/docs');
        $docsResponse = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $docsResponse->getStatusCode(), 'GET /docs deve retornar 200 OK');
        $this->assertStringContainsString('text/html', (string) $docsResponse->headers->get('Content-Type'));
        $this->assertStringContainsString('SwaggerUIBundle', (string) $docsResponse->getContent());

        // 5. OpenAPI JSON Schema (/docs/openapi.json)
        $this->client->request('GET', '/docs/openapi.json');
        $openapiResponse = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $openapiResponse->getStatusCode(), 'GET /docs/openapi.json deve retornar 200 OK');
        $this->assertStringContainsString('application/json', (string) $openapiResponse->headers->get('Content-Type'));

        $spec = json_decode((string) $openapiResponse->getContent(), true);
        $this->assertIsArray($spec);
        $this->assertSame('3.0.3', $spec['openapi']);
        $this->assertSame('OAuth & OIDC Microservice API', $spec['info']['title']);

        // Valida presença de todos os endpoints essenciais na especificação
        $requiredPaths = [
            '/login',
            '/refresh',
            '/me',
            '/users',
            '/users/{id}',
            '/roles',
            '/roles/{id}',
            '/users/{userId}/roles',
            '/authorize',
            '/metrics',
            '/health',
        ];

        foreach ($requiredPaths as $path) {
            $this->assertArrayHasKey($path, $spec['paths'], "O caminho {$path} deve constar no OpenAPI schema");
        }
    }
}
