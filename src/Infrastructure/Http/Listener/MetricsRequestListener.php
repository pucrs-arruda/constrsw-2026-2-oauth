<?php

declare(strict_types=1);

namespace App\Infrastructure\Http\Listener;

use App\Infrastructure\Metrics\MetricsRegistry;
use Symfony\Component\EventDispatcher\EventSubscriberInterface;
use Symfony\Component\HttpKernel\Event\RequestEvent;
use Symfony\Component\HttpKernel\Event\ResponseEvent;
use Symfony\Component\HttpKernel\KernelEvents;

/**
 * Event Subscriber que intercepta requisições HTTP para registrar métricas Prometheus:
 * - Total de requisições por método, rota e status code.
 * - Duração da execução das requisições em segundos.
 */
class MetricsRequestListener implements EventSubscriberInterface
{
    private const START_TIME_ATTRIBUTE = '_metrics_start_time';

    public function __construct(
        private readonly MetricsRegistry $metricsRegistry
    ) {
    }

    public static function getSubscribedEvents(): array
    {
        return [
            KernelEvents::REQUEST => ['onKernelRequest', 1024],
            KernelEvents::RESPONSE => ['onKernelResponse', -1024],
        ];
    }

    public function onKernelRequest(RequestEvent $event): void
    {
        if (!$event->isMainRequest()) {
            return;
        }

        $request = $event->getRequest();
        $request->attributes->set(self::START_TIME_ATTRIBUTE, microtime(true));
    }

    public function onKernelResponse(ResponseEvent $event): void
    {
        if (!$event->isMainRequest()) {
            return;
        }

        $request = $event->getRequest();
        $path = $request->getPathInfo();

        // Não contabiliza requisições do próprio scraper do Prometheus para não inflar métricas
        if ($path === '/metrics') {
            return;
        }

        $method = $request->getMethod();
        $statusCode = (string) $event->getResponse()->getStatusCode();
        $route = (string) ($request->attributes->get('_route') ?? $path);

        $labels = [
            'method' => $method,
            'route' => $route,
            'status' => $statusCode,
        ];

        $this->metricsRegistry->incrementCounter('http_requests_total', $labels);

        // Métricas especializadas de negócio e segurança do domínio OAuth
        if ($route === 'auth_login') {
            $statusLabel = ((int) $statusCode >= 200 && (int) $statusCode < 300) ? 'success' : 'failure';
            $this->metricsRegistry->incrementCounter('oauth_logins_total', ['status' => $statusLabel]);
        } elseif ($route === 'auth_refresh') {
            $statusLabel = ((int) $statusCode >= 200 && (int) $statusCode < 300) ? 'success' : 'failure';
            $this->metricsRegistry->incrementCounter('oauth_token_refreshes_total', ['status' => $statusLabel]);
        } elseif ($route === 'authz_authorize') {
            $resultLabel = match ((int) $statusCode) {
                200 => 'granted',
                403 => 'denied',
                default => 'error',
            };
            $this->metricsRegistry->incrementCounter('oauth_authorizations_total', ['result' => $resultLabel]);
        }

        $startTime = $request->attributes->get(self::START_TIME_ATTRIBUTE);
        if (is_float($startTime)) {
            $duration = microtime(true) - $startTime;
            $this->metricsRegistry->recordDuration('http_request_duration_seconds', $duration, [
                'method' => $method,
                'route' => $route,
            ]);
        }
    }
}
