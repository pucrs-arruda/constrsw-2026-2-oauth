<?php

declare(strict_types=1);

namespace App\Tests\Integration\Infrastructure\Http\Controller;

use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\HttpFoundation\Response;

final class MetricsControllerTest extends WebTestCase
{
    private KernelBrowser $client;

    protected function setUp(): void
    {
        $this->client = static::createClient();
    }

    public function testMetricsEndpointReturnsPrometheusTextFormat(): void
    {
        $this->client->request('GET', '/metrics');

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());
        $this->assertStringContainsString('text/plain', (string) $response->headers->get('Content-Type'));
        $this->assertStringContainsString('version=0.0.4', (string) $response->headers->get('Content-Type'));

        $content = (string) $response->getContent();
        $this->assertStringContainsString('# HELP php_info', $content);
        $this->assertStringContainsString('# TYPE php_info gauge', $content);
        $this->assertStringContainsString('# HELP php_memory_bytes', $content);
        $this->assertStringContainsString('# HELP oauth_service_info', $content);
        $this->assertStringContainsString('service="oauth"', $content);
    }

    public function testHttpRequestIncrementsMetricsCounter(): void
    {
        // 1. Faz uma requisição no endpoint de health
        $this->client->request('GET', '/health');
        $this->assertSame(Response::HTTP_OK, $this->client->getResponse()->getStatusCode());

        // 2. Consulta o endpoint de métricas
        $this->client->request('GET', '/metrics');
        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());

        $content = (string) $response->getContent();
        $this->assertStringContainsString('http_requests_total', $content);
        $this->assertStringContainsString('status="200"', $content);
    }

    public function testBusinessMetricsRecordedOnAuthEndpoints(): void
    {
        // 1. Dispara requisição com falha de validação em login (POST /login vazio -> 400)
        $this->client->request(
            method: 'POST',
            uri: '/login',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([], JSON_THROW_ON_ERROR)
        );
        $this->assertSame(Response::HTTP_BAD_REQUEST, $this->client->getResponse()->getStatusCode());

        // 2. Dispara requisição com falha de validação em autorização (POST /authorize sem header -> 401)
        $this->client->request(
            method: 'POST',
            uri: '/authorize',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([], JSON_THROW_ON_ERROR)
        );
        $this->assertSame(Response::HTTP_UNAUTHORIZED, $this->client->getResponse()->getStatusCode());

        // 3. Consulta endpoint de métricas e valida presença das métricas de negócio
        $this->client->request('GET', '/metrics');
        $content = (string) $this->client->getResponse()->getContent();

        $this->assertStringContainsString('oauth_logins_total', $content);
        $this->assertStringContainsString('status="failure"', $content);
        $this->assertStringContainsString('oauth_authorizations_total', $content);
    }
}
