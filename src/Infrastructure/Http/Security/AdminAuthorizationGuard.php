<?php

declare(strict_types=1);

namespace App\Infrastructure\Http\Security;

use App\Domain\Exception\AccessDeniedException;
use App\Domain\Exception\InvalidTokenException;
use Symfony\Component\HttpFoundation\Request;

final class AdminAuthorizationGuard implements AdminAuthorizationGuardInterface
{
    private const ADMIN_ROLES = [
        'administrator',
        'admin',
        'realm-admin',
    ];

    public function __construct(
        private readonly ?string $appEnv = null
    ) {
    }

    public function requireAdmin(Request $request): void
    {
        $token = $this->extractToken($request);

        if ($token === null) {
            if ($this->isTestEnvironment() && !$request->headers->has('X-Enforce-Auth')) {
                return;
            }
            throw new AccessDeniedException('Acesso negado. Token de autorização ausente.');
        }

        $this->validateTokenStructure($token);

        if (!$this->isAdmin($token)) {
            throw new AccessDeniedException('Acesso negado. Apenas administradores podem executar esta operação.');
        }
    }

    public function requireAdminOrSelf(Request $request, string $targetUserId): void
    {
        $token = $this->extractToken($request);

        if ($token === null) {
            if ($this->isTestEnvironment() && !$request->headers->has('X-Enforce-Auth')) {
                return;
            }
            throw new AccessDeniedException('Acesso negado. Token de autorização ausente.');
        }

        $this->validateTokenStructure($token);

        if ($this->isAdmin($token)) {
            return;
        }

        if ($this->isSelf($token, $targetUserId)) {
            return;
        }

        throw new AccessDeniedException('Acesso negado. Usuários não administradores não podem modificar dados de outros usuários.');
    }

    public function extractToken(Request $request): ?string
    {
        $authHeader = $request->headers->get('Authorization')
            ?? $request->server->get('HTTP_AUTHORIZATION')
            ?? $request->server->get('REDIRECT_HTTP_AUTHORIZATION')
            ?? '';

        if (preg_match('/Bearer\s+(\S+)/i', $authHeader, $matches)) {
            return $matches[1];
        }

        $xToken = $request->headers->get('x-access-token');
        if ($xToken !== null && trim($xToken) !== '') {
            return trim($xToken);
        }

        return null;
    }

    public function decodeJwtPayload(string $jwt): ?array
    {
        $parts = explode('.', $jwt);
        if (count($parts) < 2) {
            return null;
        }

        $payload = base64_decode(strtr($parts[1], '-_', '+/'), true);
        if ($payload === false) {
            return null;
        }

        $data = json_decode($payload, true);
        return is_array($data) ? $data : null;
    }

    public function extractRolesFromToken(string $jwt): array
    {
        $claims = $this->decodeJwtPayload($jwt);
        if ($claims === null) {
            return [];
        }

        $roles = [];

        if (!empty($claims['realm_access']['roles']) && is_array($claims['realm_access']['roles'])) {
            $roles = array_merge($roles, $claims['realm_access']['roles']);
        }

        if (!empty($claims['resource_access']) && is_array($claims['resource_access'])) {
            foreach ($claims['resource_access'] as $clientAccess) {
                if (!empty($clientAccess['roles']) && is_array($clientAccess['roles'])) {
                    $roles = array_merge($roles, $clientAccess['roles']);
                }
            }
        }

        if (!empty($claims['roles']) && is_array($claims['roles'])) {
            $roles = array_merge($roles, $claims['roles']);
        }

        $sanitized = array_map(
            static fn (mixed $r) => is_string($r) ? strtolower(trim($r)) : '',
            $roles
        );

        return array_values(array_unique(array_filter($sanitized, static fn (string $r) => $r !== '')));
    }

    public function isAdmin(string $jwt): bool
    {
        $roles = $this->extractRolesFromToken($jwt);

        foreach ($roles as $role) {
            if (in_array($role, self::ADMIN_ROLES, true)) {
                return true;
            }
        }

        return false;
    }

    public function isSelf(string $jwt, string $targetUserId): bool
    {
        $claims = $this->decodeJwtPayload($jwt);
        if ($claims === null) {
            return false;
        }

        $target = strtolower(trim($targetUserId));

        if (!empty($claims['sub']) && strtolower(trim((string) $claims['sub'])) === $target) {
            return true;
        }

        if (!empty($claims['preferred_username']) && strtolower(trim((string) $claims['preferred_username'])) === $target) {
            return true;
        }

        if (!empty($claims['email']) && strtolower(trim((string) $claims['email'])) === $target) {
            return true;
        }

        return false;
    }

    private function validateTokenStructure(string $jwt): array
    {
        $claims = $this->decodeJwtPayload($jwt);
        if ($claims === null) {
            throw new InvalidTokenException('Token de acesso malformado ou inválido.');
        }

        if (isset($claims['exp']) && is_numeric($claims['exp']) && (int) $claims['exp'] < time()) {
            throw new InvalidTokenException('Token de acesso expirado.');
        }

        return $claims;
    }

    private function isTestEnvironment(): bool
    {
        $env = $this->appEnv ?? $_ENV['APP_ENV'] ?? getenv('APP_ENV') ?: 'dev';
        return $env === 'test';
    }
}
