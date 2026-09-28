<?php

declare(strict_types=1);

namespace App\Tests\Integration\Infrastructure\Http\Controller;

use App\Tests\Support\KeycloakTestTrait;
use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\HttpFoundation\Response;

final class UserControllerTest extends WebTestCase
{
    use KeycloakTestTrait;

    private KernelBrowser $client;

    protected function setUp(): void
    {
        $this->client = static::createClient();
    }

    /**
     * POST /users: Valida erro de validação quando campos obrigatórios são omitidos (400 VALIDATION_ERROR).
     */
    public function testCreateUserWithMissingFieldsReturns400(): void
    {
        $this->client->request(
            method: 'POST',
            uri: '/users',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_BAD_REQUEST, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertArrayHasKey('error', $data);
        $this->assertSame('VALIDATION_ERROR', $data['error']['code']);
        $this->assertArrayHasKey('email', $data['error']['details']);
        $this->assertArrayHasKey('firstName', $data['error']['details']);
        $this->assertArrayHasKey('lastName', $data['error']['details']);
        $this->assertArrayHasKey('password', $data['error']['details']);
    }

    /**
     * POST /users: Valida erro quando o e-mail possui formato inválido (400 VALIDATION_ERROR).
     */
    public function testCreateUserWithInvalidEmailReturns400(): void
    {
        $this->client->request(
            method: 'POST',
            uri: '/users',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'email' => 'invalid-email-string',
                'firstName' => 'Joao',
                'lastName' => 'Silva',
                'password' => 'secret123',
            ], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_BAD_REQUEST, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('VALIDATION_ERROR', $data['error']['code']);
        $this->assertArrayHasKey('email', $data['error']['details']);
    }

    /**
     * PUT /users/{id}: Valida erro quando nenhum campo é fornecido para atualização (400 VALIDATION_ERROR).
     */
    public function testUpdateUserWithNoFieldsReturns400(): void
    {
        $this->client->request(
            method: 'PUT',
            uri: '/users/sample-user-id',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_BAD_REQUEST, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('VALIDATION_ERROR', $data['error']['code']);
    }

    /**
     * PUT /users/{id}: Valida erro quando e-mail inválido é fornecido para atualização (400 VALIDATION_ERROR).
     */
    public function testUpdateUserWithInvalidEmailReturns400(): void
    {
        $this->client->request(
            method: 'PUT',
            uri: '/users/sample-user-id',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'email' => 'not-an-email',
            ], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_BAD_REQUEST, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('VALIDATION_ERROR', $data['error']['code']);
        $this->assertArrayHasKey('email', $data['error']['details']);
    }

    /**
     * PATCH /users/{id}: Valida erro quando senha não é informada ou é vazia (400 VALIDATION_ERROR).
     */
    public function testUpdatePasswordWithEmptyPasswordReturns400(): void
    {
        $this->client->request(
            method: 'PATCH',
            uri: '/users/sample-user-id',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'password' => '   ',
            ], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_BAD_REQUEST, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('VALIDATION_ERROR', $data['error']['code']);
        $this->assertArrayHasKey('password', $data['error']['details']);
    }

    /**
     * Ciclo de vida completo do usuário no Keycloak:
     * 1. Criação (POST /users -> 201)
     * 2. Rejeição de duplicado (POST /users -> 409)
     * 3. Listagem de ativos (GET /users -> 200)
     * 4. Consulta por ID (GET /users/{id} -> 200)
     * 5. Consulta ID inexistente (GET /users/inexistente -> 404)
     * 6. Atualização de atributos (PUT /users/{id} -> 200)
     * 7. Atualização de senha (PATCH /users/{id} -> 200) e login com nova senha (POST /login -> 200)
     * 8. Exclusão lógica (DELETE /users/{id} -> 204)
     * 9. Confirmação de desativação (GET /users não lista, POST /login rejeita com 401)
     */
    public function testUserFullLifecycleInKeycloak(): void
    {
        $this->requireKeycloak();

        $uniqueSuffix = uniqid();
        $email = "e2e-user-{$uniqueSuffix}@constrsw.pucrs.br";
        $initialPassword = 'initialPassword123!';
        $newPassword = 'updatedPassword456!';

        // 1. Criação de usuário
        $this->client->request(
            method: 'POST',
            uri: '/users',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'email' => $email,
                'firstName' => 'Ciclo',
                'lastName' => 'Vida',
                'password' => $initialPassword,
            ], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_CREATED, $response->getStatusCode());
        $createdUser = json_decode((string) $response->getContent(), true);
        $this->assertNotEmpty($createdUser['id']);
        $this->assertSame($email, $createdUser['email']);
        $userId = $createdUser['id'];

        // 2. Conflito em e-mail duplicado (409)
        $this->client->request(
            method: 'POST',
            uri: '/users',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'email' => $email,
                'firstName' => 'Outro',
                'lastName' => 'Nome',
                'password' => 'outraSenha123',
            ], JSON_THROW_ON_ERROR)
        );
        $this->assertSame(Response::HTTP_CONFLICT, $this->client->getResponse()->getStatusCode());

        // 3. Listagem de usuários ativos
        $this->client->request('GET', '/users');
        $this->assertSame(Response::HTTP_OK, $this->client->getResponse()->getStatusCode());
        $usersList = json_decode((string) $this->client->getResponse()->getContent(), true);
        $found = false;
        foreach ($usersList as $u) {
            if ($u['id'] === $userId) {
                $found = true;
                $this->assertTrue($u['enabled']);
                break;
            }
        }
        $this->assertTrue($found, 'Usuário recém-criado deve estar presente na listagem ativa.');

        // 4. Busca por ID
        $this->client->request('GET', "/users/{$userId}");
        $this->assertSame(Response::HTTP_OK, $this->client->getResponse()->getStatusCode());
        $fetchedUser = json_decode((string) $this->client->getResponse()->getContent(), true);
        $this->assertSame($userId, $fetchedUser['id']);
        $this->assertSame('Ciclo', $fetchedUser['firstName']);

        // 5. Busca por ID inexistente (404)
        $this->client->request('GET', '/users/00000000-0000-0000-0000-000000000000');
        $this->assertSame(Response::HTTP_NOT_FOUND, $this->client->getResponse()->getStatusCode());

        // 6. Atualização de atributos (PUT /users/{id})
        $this->client->request(
            method: 'PUT',
            uri: "/users/{$userId}",
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'firstName' => 'NomeAtualizado',
                'lastName' => 'SobrenomeAtualizado',
            ], JSON_THROW_ON_ERROR)
        );
        $this->assertSame(Response::HTTP_OK, $this->client->getResponse()->getStatusCode());

        $this->client->request('GET', "/users/{$userId}");
        $updatedUser = json_decode((string) $this->client->getResponse()->getContent(), true);
        $this->assertSame('NomeAtualizado', $updatedUser['firstName']);
        $this->assertSame('SobrenomeAtualizado', $updatedUser['lastName']);

        // 7. Atualização de senha (PATCH /users/{id})
        $this->client->request(
            method: 'PATCH',
            uri: "/users/{$userId}",
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'password' => $newPassword,
            ], JSON_THROW_ON_ERROR)
        );
        $this->assertSame(Response::HTTP_OK, $this->client->getResponse()->getStatusCode());

        // Valida login com a nova senha
        $this->client->request(
            method: 'POST',
            uri: '/login',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'username' => $email,
                'password' => $newPassword,
            ], JSON_THROW_ON_ERROR)
        );
        $this->assertSame(Response::HTTP_OK, $this->client->getResponse()->getStatusCode());

        // 8. Exclusão lógica (DELETE /users/{id})
        $this->client->request('DELETE', "/users/{$userId}");
        $this->assertSame(Response::HTTP_NO_CONTENT, $this->client->getResponse()->getStatusCode());

        // 9. Confirmação de desativação lógica
        // 9a. Não deve aparecer na listagem de usuários ativos
        $this->client->request('GET', '/users');
        $activeUsersAfterDelete = json_decode((string) $this->client->getResponse()->getContent(), true);
        foreach ($activeUsersAfterDelete as $u) {
            $this->assertNotSame($userId, $u['id'], 'Usuário desativado não deve aparecer em GET /users.');
        }

        // 9b. Não deve conseguir fazer login
        $this->client->request(
            method: 'POST',
            uri: '/login',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'username' => $email,
                'password' => $newPassword,
            ], JSON_THROW_ON_ERROR)
        );
        $this->assertSame(Response::HTTP_UNAUTHORIZED, $this->client->getResponse()->getStatusCode());
    }
}
