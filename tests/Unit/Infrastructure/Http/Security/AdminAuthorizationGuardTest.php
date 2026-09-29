<?php

declare(strict_types=1);

namespace App\Tests\Unit\Infrastructure\Http\Security;

use App\Domain\Exception\AccessDeniedException;
use App\Domain\Exception\InvalidTokenException;
use App\Infrastructure\Http\Security\AdminAuthorizationGuard;
use PHPUnit\Framework\TestCase;
use Symfony\Component\HttpFoundation\Request;

final class AdminAuthorizationGuardTest extends TestCase
{
    private function createMockJwt(array $payload): string
    {
        $header = rtrim(strtr(base64_encode(json_encode(['alg' => 'RS256', 'typ' => 'JWT'])), '+/', '-_'), '=');
        $body = rtrim(strtr(base64_encode(json_encode($payload)), '+/', '-_'), '=');
        $signature = 'mockSignature';

        return "{$header}.{$body}.{$signature}";
    }

    public function testExtractTokenFromAuthorizationHeader(): void
    {
        $guard = new AdminAuthorizationGuard('dev');
        $request = new Request(server: ['HTTP_AUTHORIZATION' => 'Bearer token-abc-123']);

        $token = $guard->extractToken($request);

        $this->assertSame('token-abc-123', $token);
    }

    public function testExtractTokenFromXAccessTokenHeader(): void
    {
        $guard = new AdminAuthorizationGuard('dev');
        $request = new Request();
        $request->headers->set('x-access-token', 'custom-token-xyz');

        $token = $guard->extractToken($request);

        $this->assertSame('custom-token-xyz', $token);
    }

    public function testExtractTokenReturnsNullWhenMissing(): void
    {
        $guard = new AdminAuthorizationGuard('dev');
        $request = new Request();

        $token = $guard->extractToken($request);

        $this->assertNull($token);
    }

    public function testRequireAdminAllowsAdminInRealmAccess(): void
    {
        $guard = new AdminAuthorizationGuard('dev');
        $jwt = $this->createMockJwt([
            'sub' => 'admin-uuid',
            'exp' => time() + 3600,
            'realm_access' => [
                'roles' => ['default-roles-realm', 'administrator', 'USER'],
            ],
        ]);

        $request = new Request(server: ['HTTP_AUTHORIZATION' => "Bearer {$jwt}"]);

        // Should not throw
        $guard->requireAdmin($request);
        $this->assertTrue(true);
    }

    public function testRequireAdminAllowsAdminInResourceAccess(): void
    {
        $guard = new AdminAuthorizationGuard('dev');
        $jwt = $this->createMockJwt([
            'sub' => 'admin-uuid',
            'exp' => time() + 3600,
            'resource_access' => [
                'realm-management' => [
                    'roles' => ['admin'],
                ],
            ],
        ]);

        $request = new Request(server: ['HTTP_AUTHORIZATION' => "Bearer {$jwt}"]);

        // Should not throw
        $guard->requireAdmin($request);
        $this->assertTrue(true);
    }

    public function testRequireAdminRejectsNonAdminUser(): void
    {
        $guard = new AdminAuthorizationGuard('dev');
        $jwt = $this->createMockJwt([
            'sub' => 'student-uuid',
            'exp' => time() + 3600,
            'realm_access' => [
                'roles' => ['student', 'USER'],
            ],
        ]);

        $request = new Request(server: ['HTTP_AUTHORIZATION' => "Bearer {$jwt}"]);

        $this->expectException(AccessDeniedException::class);
        $this->expectExceptionMessage('Acesso negado. Apenas administradores podem executar esta operação.');

        $guard->requireAdmin($request);
    }

    public function testRequireAdminRejectsMissingTokenInDevEnv(): void
    {
        $guard = new AdminAuthorizationGuard('dev');
        $request = new Request();

        $this->expectException(AccessDeniedException::class);
        $this->expectExceptionMessage('Acesso negado. Token de autorização ausente.');

        $guard->requireAdmin($request);
    }

    public function testRequireAdminBypassesMissingTokenInTestEnv(): void
    {
        $guard = new AdminAuthorizationGuard('test');
        $request = new Request();

        // Em ambiente de teste sem header explícito, não deve lançar exceção para testes legados
        $guard->requireAdmin($request);
        $this->assertTrue(true);
    }

    public function testRequireAdminEnforcesMissingTokenInTestEnvWithEnforceHeader(): void
    {
        $guard = new AdminAuthorizationGuard('test');
        $request = new Request();
        $request->headers->set('X-Enforce-Auth', '1');

        $this->expectException(AccessDeniedException::class);
        $this->expectExceptionMessage('Acesso negado. Token de autorização ausente.');

        $guard->requireAdmin($request);
    }

    public function testRequireAdminRejectsMalformedToken(): void
    {
        $guard = new AdminAuthorizationGuard('dev');
        $request = new Request(server: ['HTTP_AUTHORIZATION' => 'Bearer malformed-token-string']);

        $this->expectException(InvalidTokenException::class);

        $guard->requireAdmin($request);
    }

    public function testRequireAdminRejectsExpiredToken(): void
    {
        $guard = new AdminAuthorizationGuard('dev');
        $jwt = $this->createMockJwt([
            'sub' => 'admin-uuid',
            'exp' => time() - 3600, // expirado
            'realm_access' => ['roles' => ['administrator']],
        ]);

        $request = new Request(server: ['HTTP_AUTHORIZATION' => "Bearer {$jwt}"]);

        $this->expectException(InvalidTokenException::class);
        $this->expectExceptionMessage('Token de acesso expirado.');

        $guard->requireAdmin($request);
    }

    public function testRequireAdminOrSelfAllowsAdminForOtherUser(): void
    {
        $guard = new AdminAuthorizationGuard('dev');
        $jwt = $this->createMockJwt([
            'sub' => 'admin-uuid',
            'exp' => time() + 3600,
            'realm_access' => ['roles' => ['administrator']],
        ]);

        $request = new Request(server: ['HTTP_AUTHORIZATION' => "Bearer {$jwt}"]);

        $guard->requireAdminOrSelf($request, 'other-user-uuid');
        $this->assertTrue(true);
    }

    public function testRequireAdminOrSelfAllowsSelfBySub(): void
    {
        $guard = new AdminAuthorizationGuard('dev');
        $jwt = $this->createMockJwt([
            'sub' => 'student-uuid-123',
            'exp' => time() + 3600,
            'realm_access' => ['roles' => ['student']],
        ]);

        $request = new Request(server: ['HTTP_AUTHORIZATION' => "Bearer {$jwt}"]);

        $guard->requireAdminOrSelf($request, 'student-uuid-123');
        $this->assertTrue(true);
    }

    public function testRequireAdminOrSelfAllowsSelfByUsername(): void
    {
        $guard = new AdminAuthorizationGuard('dev');
        $jwt = $this->createMockJwt([
            'sub' => 'uuid-456',
            'preferred_username' => 'student@pucrs.br',
            'exp' => time() + 3600,
            'realm_access' => ['roles' => ['student']],
        ]);

        $request = new Request(server: ['HTTP_AUTHORIZATION' => "Bearer {$jwt}"]);

        $guard->requireAdminOrSelf($request, 'student@pucrs.br');
        $this->assertTrue(true);
    }

    public function testRequireAdminOrSelfAllowsSelfByEmail(): void
    {
        $guard = new AdminAuthorizationGuard('dev');
        $jwt = $this->createMockJwt([
            'sub' => 'uuid-456',
            'email' => 'carlos@pucrs.br',
            'exp' => time() + 3600,
            'realm_access' => ['roles' => ['student']],
        ]);

        $request = new Request(server: ['HTTP_AUTHORIZATION' => "Bearer {$jwt}"]);

        $guard->requireAdminOrSelf($request, 'carlos@pucrs.br');
        $this->assertTrue(true);
    }

    public function testRequireAdminOrSelfRejectsNonAdminForOtherUser(): void
    {
        $guard = new AdminAuthorizationGuard('dev');
        $jwt = $this->createMockJwt([
            'sub' => 'student-uuid-123',
            'preferred_username' => 'student@pucrs.br',
            'email' => 'student@pucrs.br',
            'exp' => time() + 3600,
            'realm_access' => ['roles' => ['student']],
        ]);

        $request = new Request(server: ['HTTP_AUTHORIZATION' => "Bearer {$jwt}"]);

        $this->expectException(AccessDeniedException::class);
        $this->expectExceptionMessage('Acesso negado. Usuários não administradores não podem modificar dados de outros usuários.');

        $guard->requireAdminOrSelf($request, 'other-student-uuid-999');
    }
}
