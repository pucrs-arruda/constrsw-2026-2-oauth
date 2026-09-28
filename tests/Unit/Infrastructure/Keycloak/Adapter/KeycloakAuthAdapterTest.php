<?php

declare(strict_types=1);

namespace App\Tests\Unit\Infrastructure\Keycloak\Adapter;

use App\Application\DTO\Auth\LoginRequestDTO;
use App\Application\DTO\Auth\RefreshTokenRequestDTO;
use App\Domain\Exception\InvalidCredentialsException;
use App\Domain\Exception\InvalidTokenException;
use App\Infrastructure\Keycloak\Adapter\KeycloakAuthAdapter;
use App\Infrastructure\Keycloak\Client\KeycloakHttpClient;
use PHPUnit\Framework\TestCase;
use RuntimeException;

final class KeycloakAuthAdapterTest extends TestCase
{
    private KeycloakHttpClient $httpClient;
    private KeycloakAuthAdapter $adapter;

    protected function setUp(): void
    {
        $this->httpClient = $this->createMock(KeycloakHttpClient::class);
        $this->httpClient->method('getRealm')->willReturn('constrsw');
        $this->httpClient->method('getClientId')->willReturn('oauth');
        $this->httpClient->method('getClientSecret')->willReturn('secret-123');

        $this->adapter = new KeycloakAuthAdapter($this->httpClient);
    }

    public function testAuthenticateSuccessReturnsTokens(): void
    {
        $dto = new LoginRequestDTO(
            username: 'professor@pucrs.br',
            password: 'secretPassword'
        );

        $this->httpClient
            ->expects($this->once())
            ->method('request')
            ->with(
                'POST',
                'realms/constrsw/protocol/openid-connect/token',
                ['Content-Type' => 'application/x-www-form-urlencoded'],
                $this->callback(function (string $body) {
                    parse_str($body, $parsed);
                    return $parsed['grant_type'] === 'password'
                        && $parsed['username'] === 'professor@pucrs.br'
                        && $parsed['password'] === 'secretPassword'
                        && $parsed['client_id'] === 'oauth'
                        && $parsed['client_secret'] === 'secret-123';
                })
            )
            ->willReturn([
                'status' => 200,
                'data' => [
                    'token_type' => 'Bearer',
                    'access_token' => 'access.token.jwt',
                    'expires_in' => 300,
                    'refresh_token' => 'refresh.token.jwt',
                    'refresh_expires_in' => 1800,
                ],
            ]);

        $tokens = $this->adapter->authenticate($dto);

        $this->assertSame('Bearer', $tokens->tokenType);
        $this->assertSame('access.token.jwt', $tokens->accessToken);
        $this->assertSame(300, $tokens->expiresIn);
        $this->assertSame('refresh.token.jwt', $tokens->refreshToken);
        $this->assertSame(1800, $tokens->refreshExpiresIn);
    }

    public function testAuthenticateThrowsInvalidCredentialsOn400(): void
    {
        $dto = new LoginRequestDTO(
            username: 'wrong@pucrs.br',
            password: 'wrongPassword'
        );

        $this->httpClient
            ->method('request')
            ->willReturn([
                'status' => 401,
                'data' => [
                    'error' => 'invalid_grant',
                    'error_description' => 'Invalid user credentials',
                ],
            ]);

        $this->expectException(InvalidCredentialsException::class);
        $this->expectExceptionMessage('Invalid user credentials');

        $this->adapter->authenticate($dto);
    }

    public function testAuthenticateThrowsRuntimeExceptionOnServerError(): void
    {
        $dto = new LoginRequestDTO(
            username: 'user@pucrs.br',
            password: 'password'
        );

        $this->httpClient
            ->method('request')
            ->willReturn([
                'status' => 500,
                'data' => ['error' => 'server_error'],
            ]);

        $this->expectException(RuntimeException::class);
        $this->expectExceptionMessage('Erro inesperado ao autenticar no Keycloak. Status: 500');

        $this->adapter->authenticate($dto);
    }

    public function testRefreshTokenSuccess(): void
    {
        $dto = new RefreshTokenRequestDTO(
            refreshToken: 'valid-refresh-token'
        );

        $this->httpClient
            ->expects($this->once())
            ->method('request')
            ->with(
                'POST',
                'realms/constrsw/protocol/openid-connect/token',
                ['Content-Type' => 'application/x-www-form-urlencoded'],
                $this->callback(function (string $body) {
                    parse_str($body, $parsed);
                    return $parsed['grant_type'] === 'refresh_token'
                        && $parsed['refresh_token'] === 'valid-refresh-token'
                        && $parsed['client_id'] === 'oauth'
                        && $parsed['client_secret'] === 'secret-123';
                })
            )
            ->willReturn([
                'status' => 200,
                'data' => [
                    'token_type' => 'Bearer',
                    'access_token' => 'new.access.token',
                    'expires_in' => 300,
                    'refresh_token' => 'new.refresh.token',
                    'refresh_expires_in' => 1800,
                ],
            ]);

        $tokens = $this->adapter->refreshToken($dto);

        $this->assertSame('Bearer', $tokens->tokenType);
        $this->assertSame('new.access.token', $tokens->accessToken);
        $this->assertSame('new.refresh.token', $tokens->refreshToken);
    }

    public function testRefreshTokenThrowsInvalidTokenOnFailure(): void
    {
        $dto = new RefreshTokenRequestDTO(
            refreshToken: 'expired-token'
        );

        $this->httpClient
            ->method('request')
            ->willReturn([
                'status' => 400,
                'data' => ['error' => 'invalid_grant'],
            ]);

        $this->expectException(InvalidTokenException::class);
        $this->expectExceptionMessage('Refresh token expirado, inválido ou revogado.');

        $this->adapter->refreshToken($dto);
    }

    public function testRefreshTokenThrowsRuntimeExceptionOnServerError(): void
    {
        $dto = new RefreshTokenRequestDTO(
            refreshToken: 'valid-token'
        );

        $this->httpClient
            ->method('request')
            ->willReturn([
                'status' => 503,
                'data' => [],
            ]);

        $this->expectException(RuntimeException::class);
        $this->expectExceptionMessage('Erro inesperado ao renovar token no Keycloak. Status: 503');

        $this->adapter->refreshToken($dto);
    }

    public function testGetUserInfoSuccess(): void
    {
        $this->httpClient
            ->expects($this->once())
            ->method('request')
            ->with(
                'GET',
                'realms/constrsw/protocol/openid-connect/userinfo',
                ['Authorization' => 'Bearer valid-jwt-token']
            )
            ->willReturn([
                'status' => 200,
                'data' => [
                    'sub' => 'user-uuid-123',
                    'name' => 'John Doe',
                    'preferred_username' => 'johndoe',
                    'email' => 'john@pucrs.br',
                    'email_verified' => true,
                ],
            ]);

        $profile = $this->adapter->getUserInfo('valid-jwt-token');

        $this->assertSame('user-uuid-123', $profile->sub);
        $this->assertSame('John Doe', $profile->name);
        $this->assertSame('johndoe', $profile->preferredUsername);
        $this->assertSame('john@pucrs.br', $profile->email);
        $this->assertTrue($profile->emailVerified);
    }

    public function testGetUserInfoThrowsInvalidTokenOn401(): void
    {
        $this->httpClient
            ->method('request')
            ->willReturn([
                'status' => 401,
                'data' => ['error' => 'invalid_token'],
            ]);

        $this->expectException(InvalidTokenException::class);
        $this->expectExceptionMessage('Token de acesso ausente, inválido ou expirado.');

        $this->adapter->getUserInfo('expired-jwt-token');
    }

    public function testGetUserInfoThrowsRuntimeExceptionOnServerError(): void
    {
        $this->httpClient
            ->method('request')
            ->willReturn([
                'status' => 500,
                'data' => [],
            ]);

        $this->expectException(RuntimeException::class);
        $this->expectExceptionMessage('Erro inesperado ao obter dados do usuário no Keycloak. Status: 500');

        $this->adapter->getUserInfo('token');
    }
}
