<?php

declare(strict_types=1);

namespace App\Tests\E2E;

use App\Tests\Support\KeycloakTestTrait;
use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\HttpFoundation\Response;

/**
 * Teste End-to-End do ciclo de vida completo de Gestão de Usuários.
 *
 * Cenário E2E:
 * 1. Admin autentica para obter credencial de gerenciamento.
 * 2. Criação de um novo usuário via POST /users -> 201 Created com ID e dados.
 * 3. Listagem de usuários ativos via GET /users -> 200 OK contendo o usuário recém-criado.
 * 4. Consulta detalhada por ID via GET /users/{id} -> 200 OK.
 * 5. Atualização de dados cadastrais via PUT /users/{id} -> 200 OK.
 * 6. Atualização de senha via PATCH /users/{id} -> 200 OK.
 * 7. Autenticação ponta a ponta do usuário criado usando a nova senha via POST /login -> 200 OK.
 * 8. Exclusão lógica / desabilitação via DELETE /users/{id} -> 204 No Content.
 * 9. Validação pós-desativação: usuário não consegue mais logar via POST /login -> 401.
 */
final class UserManagementFlowE2ETest extends WebTestCase
{
    use KeycloakTestTrait;

    private KernelBrowser $client;

    protected function setUp(): void
    {
        $this->client = static::createClient();
    }

    public function testCompleteUserLifecycleE2E(): void
    {
        $this->requireKeycloak();

        $suffix = uniqid();
        $email = "e2e-student-{$suffix}@constrsw.pucrs.br";
        $initialPassword = 'initialPass123!';
        $newPassword = 'updatedSecret456!';

        // 1. Criação do usuário
        $this->client->request(
            method: 'POST',
            uri: '/users',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'email' => $email,
                'firstName' => 'E2E',
                'lastName' => "User-{$suffix}",
                'password' => $initialPassword,
            ], JSON_THROW_ON_ERROR)
        );

        $createResponse = $this->client->getResponse();
        $this->assertSame(Response::HTTP_CREATED, $createResponse->getStatusCode(), 'POST /users deve retornar 201 Created');

        $createdData = json_decode((string) $createResponse->getContent(), true);
        $this->assertNotEmpty($createdData['id']);
        $this->assertSame($email, $createdData['email']);
        $this->assertSame('E2E', $createdData['firstName']);
        $userId = $createdData['id'];

        // 2. Consulta por ID
        $this->client->request('GET', "/users/{$userId}");
        $getResponse = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $getResponse->getStatusCode());

        $fetchedUser = json_decode((string) $getResponse->getContent(), true);
        $this->assertSame($userId, $fetchedUser['id']);
        $this->assertSame($email, $fetchedUser['email']);
        $this->assertTrue($fetchedUser['enabled']);

        // 3. Listagem de ativos
        $this->client->request('GET', '/users');
        $listResponse = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $listResponse->getStatusCode());

        $userList = json_decode((string) $listResponse->getContent(), true);
        $this->assertIsArray($userList);
        $found = false;
        foreach ($userList as $u) {
            if ($u['id'] === $userId) {
                $found = true;
                break;
            }
        }
        $this->assertTrue($found, 'O usuário recém-criado deve constar na listagem de usuários ativos');

        // 4. Atualização de atributos cadastrais (PUT)
        $this->client->request(
            method: 'PUT',
            uri: "/users/{$userId}",
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'firstName' => 'E2EUpdated',
                'lastName' => 'LastNameUpdated',
                'email' => $email,
            ], JSON_THROW_ON_ERROR)
        );

        $putResponse = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $putResponse->getStatusCode(), 'PUT /users/{id} deve retornar 200 OK');

        // 5. Atualização de senha (PATCH)
        $this->client->request(
            method: 'PATCH',
            uri: "/users/{$userId}",
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'password' => $newPassword,
            ], JSON_THROW_ON_ERROR)
        );

        $patchResponse = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $patchResponse->getStatusCode(), 'PATCH /users/{id} deve retornar 200 OK');

        // 6. Login ponta a ponta com a nova senha
        $this->client->request(
            method: 'POST',
            uri: '/login',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'username' => $email,
                'password' => $newPassword,
            ], JSON_THROW_ON_ERROR)
        );

        $loginResponse = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $loginResponse->getStatusCode(), 'Login com a nova senha do usuário deve ter sucesso');

        // 7. Exclusão lógica (DELETE)
        $this->client->request('DELETE', "/users/{$userId}");
        $deleteResponse = $this->client->getResponse();
        $this->assertSame(Response::HTTP_NO_CONTENT, $deleteResponse->getStatusCode(), 'DELETE /users/{id} deve retornar 204 No Content');

        // 8. Validação: login rejeitado após desativação
        $this->client->request(
            method: 'POST',
            uri: '/login',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'username' => $email,
                'password' => $newPassword,
            ], JSON_THROW_ON_ERROR)
        );

        $blockedLogin = $this->client->getResponse();
        $this->assertSame(Response::HTTP_UNAUTHORIZED, $blockedLogin->getStatusCode(), 'Usuário desativado não pode mais efetuar login');
    }
}
