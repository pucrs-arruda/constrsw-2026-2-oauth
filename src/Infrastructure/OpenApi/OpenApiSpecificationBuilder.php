<?php

declare(strict_types=1);

namespace App\Infrastructure\OpenApi;

use App\Infrastructure\OpenApi\Attribute\OpenApiOperation;
use ReflectionClass;
use ReflectionMethod;
use Symfony\Component\Routing\Route;
use Symfony\Component\Routing\RouterInterface;

final class OpenApiSpecificationBuilder
{
    public function __construct(
        private readonly RouterInterface $router
    ) {
    }

    /**
     * Constrói a especificação OpenAPI 3.0.3 completa, unindo endpoints pré-configurados
     * com auto-descoberta dinâmica de quaisquer novas rotas registradas no Symfony.
     *
     * @return array<string, mixed>
     */
    public function build(string $serverUrl = 'http://localhost:8181'): array
    {
        $spec = [
            'openapi' => '3.0.3',
            'info' => [
                'title' => 'OAuth & OIDC Microservice API',
                'version' => '1.0.0',
                'description' => "Microserviço de Autenticação e Autorização institucional com Keycloak, Symfony 6.4 e Arquitetura Hexagonal.\n\n" .
                    "Desenvolvido para a disciplina de Construção de Software (PUCRS).\n" .
                    "Fornece autenticação OIDC, renovação de tokens, gerenciamento administrativo de usuários no Keycloak e autorização baseada em matriz de permissões institucionais.",
                'contact' => [
                    'name' => 'Equipe Grupo 01 - Construção de Software',
                    'url' => 'https://github.com/pucrs-constrsw-2026-2/oauth',
                ],
            ],
            'servers' => [
                [
                    'url' => $serverUrl,
                    'description' => 'Servidor Principal (Docker Local)',
                ],
                [
                    'url' => '/',
                    'description' => 'Servidor Atual (Relativo)',
                ],
            ],
            'tags' => [
                [
                    'name' => 'Autenticação',
                    'description' => 'Endpoints de emissão e renovação de tokens JWT/OIDC e consulta de perfil.',
                ],
                [
                    'name' => 'Usuários',
                    'description' => 'Endpoints de CRUD e administração do ciclo de vida de usuários no Keycloak.',
                ],
                [
                    'name' => 'Roles',
                    'description' => 'Endpoints de CRUD e administração de papéis institucionais no Keycloak.',
                ],
                [
                    'name' => 'Autorização',
                    'description' => 'Endpoint de validação de políticas e controle de acesso por recurso (FR-10).',
                ],
                [
                    'name' => 'Sistema',
                    'description' => 'Endpoints operacionais de diagnóstico e saúde do microserviço.',
                ],
            ],
            'paths' => [],
            'components' => [
                'securitySchemes' => [
                    'bearerAuth' => [
                        'type' => 'http',
                        'scheme' => 'bearer',
                        'bearerFormat' => 'JWT',
                        'description' => "Insira o token JWT retornado pelo endpoint `/login`.\nExemplo: `eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...`",
                    ],
                ],
                'schemas' => $this->getComponentSchemas(),
            ],
        ];

        // 1. Carrega os metadados dos endpoints nativos do microserviço
        $predefinedPaths = $this->getPredefinedPaths();

        // 2. Executa a auto-descoberta dinâmica de rotas registradas no roteador do Symfony
        $discoveredPaths = $this->discoverDynamicRoutes();

        // 3. Mescla mantendo a riqueza dos metadados pré-definidos e incorporando novas rotas
        $allPaths = $predefinedPaths;
        foreach ($discoveredPaths as $path => $methods) {
            foreach ($methods as $method => $operation) {
                if (!isset($allPaths[$path][$method])) {
                    $allPaths[$path][$method] = $operation;
                    
                    // Adiciona tag desconhecida à lista de tags se necessário
                    foreach ($operation['tags'] ?? [] as $tag) {
                        $existingTags = array_column($spec['tags'], 'name');
                        if (!in_array($tag, $existingTags, true)) {
                            $spec['tags'][] = [
                                'name' => $tag,
                                'description' => "Operações do módulo {$tag}",
                            ];
                        }
                    }
                }
            }
        }

        // Ordena caminhos alfabeticamente para consistência visual
        ksort($allPaths);
        $spec['paths'] = $allPaths;

        return $spec;
    }

    /**
     * Realiza a varredura dinâmica no Symfony RouterInterface para identificar
     * quaisquer rotas que venham a ser adicionadas na aplicação.
     *
     * @return array<string, array<string, mixed>>
     */
    private function discoverDynamicRoutes(): array
    {
        $discovered = [];
        $routes = $this->router->getRouteCollection()->all();

        foreach ($routes as $name => $route) {
            $path = $route->getPath();

            // Ignora rotas internas do framework, profiler e de documentação
            if ($this->shouldIgnoreRoute($name, $path)) {
                continue;
            }

            $methods = $route->getMethods();
            if (empty($methods)) {
                $methods = ['GET'];
            }

            $controllerString = $route->getDefault('_controller') ?? '';
            $operationMetadata = $this->inspectController($controllerString, $path, $route);

            foreach ($methods as $method) {
                $methodLower = strtolower($method);
                if (in_array($methodLower, ['get', 'post', 'put', 'patch', 'delete', 'options', 'head'], true)) {
                    $discovered[$path][$methodLower] = $operationMetadata;
                }
            }
        }

        return $discovered;
    }

    /**
     * Inspeciona a classe e método do Controller via Reflection procurando por atributos PHP 8
     * ou inferindo documentação padrão a partir da assinatura e docblocks.
     *
     * @return array<string, mixed>
     */
    private function inspectController(string $controllerString, string $path, Route $route): array
    {
        $tag = 'Outros';
        $summary = 'Operação de ' . $path;
        $description = 'Endpoint descoberto automaticamente pelo roteador Symfony.';
        $parameters = $this->extractPathParameters($path);
        $requestBody = null;
        $responses = [
            '200' => [
                'description' => 'Operação executada com sucesso.',
                'content' => [
                    'application/json' => [
                        'schema' => [
                            'type' => 'object',
                            'example' => ['success' => true],
                        ],
                    ],
                ],
            ],
        ];
        $security = null;

        if (str_contains($controllerString, '::')) {
            [$class, $methodName] = explode('::', $controllerString, 2);
            $parts = explode('\\', $class);
            $shortName = end($parts);
            $cleanTag = preg_replace('/Controller$/', '', $shortName) ?: $shortName;
            $tag = match ($cleanTag) {
                'Auth' => 'Autenticação',
                'User' => 'Usuários',
                'Role' => 'Roles',
                'Authorization' => 'Autorização',
                'Health' => 'Sistema',
                default => $cleanTag,
            };

            if (class_exists($class) && method_exists($class, $methodName)) {
                $reflectionClass = new ReflectionClass($class);
                $reflectionMethod = new ReflectionMethod($class, $methodName);

                // Verifica se há o atributo PHP 8 #[OpenApiOperation]
                $attributes = $reflectionMethod->getAttributes(OpenApiOperation::class);
                if (!empty($attributes)) {
                    /** @var OpenApiOperation $attrInstance */
                    $attrInstance = $attributes[0]->newInstance();
                    if ($attrInstance->summary !== null) {
                        $summary = $attrInstance->summary;
                    }
                    if ($attrInstance->description !== null) {
                        $description = $attrInstance->description;
                    }
                    if (!empty($attrInstance->tags)) {
                        $tag = $attrInstance->tags[0];
                    }
                    if ($attrInstance->requestBody !== null) {
                        $requestBody = $attrInstance->requestBody;
                    }
                    if (!empty($attrInstance->responses)) {
                        $responses = $attrInstance->responses;
                    }
                    if (!empty($attrInstance->parameters)) {
                        $parameters = array_merge($parameters, $attrInstance->parameters);
                    }
                    if ($attrInstance->security !== null) {
                        $security = $attrInstance->security;
                    }
                } else {
                    // Tenta ler o sumário do PHPDoc do método
                    $docComment = $reflectionMethod->getDocComment();
                    if ($docComment !== false) {
                        $cleanedDoc = trim(preg_replace('/(^\s*\/\*\*|\s*\*\/\s*$|^\s*\*\s?)/m', '', $docComment) ?? '');
                        $firstLine = strtok($cleanedDoc, "\n");
                        if ($firstLine !== false && !str_starts_with($firstLine, '@') && trim($firstLine) !== '') {
                            $summary = trim($firstLine);
                        }
                    }
                }
            }
        }

        $operation = [
            'tags' => [$tag],
            'summary' => $summary,
            'description' => $description,
            'parameters' => $parameters,
            'responses' => $responses,
        ];

        if ($requestBody !== null) {
            $operation['requestBody'] = $requestBody;
        }

        if ($security !== null) {
            $operation['security'] = $security;
        }

        return $operation;
    }

    /**
     * Extrai parâmetros de rota como `{id}` e gera a especificação OpenAPI correspondente.
     *
     * @return array<int, array<string, mixed>>
     */
    private function extractPathParameters(string $path): array
    {
        $parameters = [];
        if (preg_match_all('/\{([a-zA-Z0-9_]+)\}/', $path, $matches)) {
            foreach ($matches[1] as $paramName) {
                $parameters[] = [
                    'name' => $paramName,
                    'in' => 'path',
                    'required' => true,
                    'description' => "Identificador do parâmetro {$paramName}",
                    'schema' => [
                        'type' => 'string',
                    ],
                ];
            }
        }

        return $parameters;
    }

    private function shouldIgnoreRoute(string $name, string $path): bool
    {
        if (str_starts_with($name, '_')) {
            return true;
        }

        $ignoredPrefixes = [
            '/docs',
            '/api/docs',
            '/swagger',
            '/docs.json',
            '/openapi.json',
            '/_wdt',
            '/_profiler',
            '/_error',
        ];

        foreach ($ignoredPrefixes as $prefix) {
            if ($path === $prefix || str_starts_with($path, $prefix . '/') || str_starts_with($path, $prefix . '.')) {
                return true;
            }
        }

        return false;
    }

    /**
     * Dicionário completo de schemas de request, response e erros do microserviço.
     *
     * @return array<string, array<string, mixed>>
     */
    private function getComponentSchemas(): array
    {
        return [
            'LoginRequest' => [
                'type' => 'object',
                'required' => ['username', 'password'],
                'properties' => [
                    'username' => [
                        'type' => 'string',
                        'format' => 'email',
                        'description' => 'E-mail ou nome de usuário cadastrado no Keycloak.',
                        'example' => 'professor@pucrs.br',
                    ],
                    'password' => [
                        'type' => 'string',
                        'format' => 'password',
                        'description' => 'Senha da conta de usuário.',
                        'example' => 'a12345678',
                    ],
                    'client_id' => [
                        'type' => 'string',
                        'description' => 'Identificador do client OIDC (padrão: oauth).',
                        'example' => 'oauth',
                    ],
                    'grant_type' => [
                        'type' => 'string',
                        'description' => 'Tipo de concessão OAuth2 (padrão: password).',
                        'example' => 'password',
                    ],
                ],
            ],
            'TokenResponse' => [
                'type' => 'object',
                'properties' => [
                    'access_token' => [
                        'type' => 'string',
                        'description' => 'Token JWT de acesso (Bearer token).',
                        'example' => 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...',
                    ],
                    'token_type' => [
                        'type' => 'string',
                        'description' => 'Tipo do token retornado.',
                        'example' => 'Bearer',
                    ],
                    'expires_in' => [
                        'type' => 'integer',
                        'description' => 'Tempo de vida do access token em segundos.',
                        'example' => 600,
                    ],
                    'refresh_token' => [
                        'type' => 'string',
                        'description' => 'Token opaco para renovação de sessão.',
                        'example' => 'eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9...',
                    ],
                    'refresh_expires_in' => [
                        'type' => 'integer',
                        'description' => 'Tempo de vida do refresh token em segundos.',
                        'example' => 1800,
                    ],
                ],
            ],
            'RefreshTokenRequest' => [
                'type' => 'object',
                'required' => ['refresh_token'],
                'properties' => [
                    'refresh_token' => [
                        'type' => 'string',
                        'description' => 'Refresh token emitido na autenticação.',
                        'example' => 'eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9...',
                    ],
                    'client_id' => [
                        'type' => 'string',
                        'description' => 'Identificador do client OIDC (opcional).',
                        'example' => 'oauth',
                    ],
                ],
            ],
            'UserProfileResponse' => [
                'type' => 'object',
                'properties' => [
                    'sub' => [
                        'type' => 'string',
                        'format' => 'uuid',
                        'description' => 'Identificador único do usuário no Keycloak.',
                        'example' => 'b2d1c002-0000-4000-b000-000000000003',
                    ],
                    'name' => [
                        'type' => 'string',
                        'description' => 'Nome completo do usuário.',
                        'example' => 'Professor PUCRS',
                    ],
                    'email' => [
                        'type' => 'string',
                        'format' => 'email',
                        'description' => 'Endereço de e-mail institucional.',
                        'example' => 'professor@pucrs.br',
                    ],
                    'preferred_username' => [
                        'type' => 'string',
                        'description' => 'Nome de usuário preferencial.',
                        'example' => 'professor@pucrs.br',
                    ],
                    'email_verified' => [
                        'type' => 'boolean',
                        'description' => 'Indica se o e-mail foi verificado.',
                        'example' => true,
                    ],
                    'roles' => [
                        'type' => 'array',
                        'items' => ['type' => 'string'],
                        'description' => 'Lista de papéis institucionais associados.',
                        'example' => ['professor'],
                    ],
                ],
            ],
            'CreateUserRequest' => [
                'type' => 'object',
                'required' => ['email', 'password'],
                'properties' => [
                    'email' => [
                        'type' => 'string',
                        'format' => 'email',
                        'description' => 'E-mail institucional obrigatório (utilizado também como username).',
                        'example' => 'novo.usuario@pucrs.br',
                    ],
                    'firstName' => [
                        'type' => 'string',
                        'description' => 'Primeiro nome do usuário.',
                        'example' => 'Carlos',
                    ],
                    'lastName' => [
                        'type' => 'string',
                        'description' => 'Sobrenome do usuário.',
                        'example' => 'Silva',
                    ],
                    'password' => [
                        'type' => 'string',
                        'format' => 'password',
                        'description' => 'Senha permanente do usuário.',
                        'example' => 'senhaSegura123!',
                    ],
                    'roles' => [
                        'type' => 'array',
                        'items' => ['type' => 'string'],
                        'description' => 'Papéis institucionais a vincular (administrator, coordinator, professor, student).',
                        'example' => ['student'],
                    ],
                ],
            ],
            'UpdateUserRequest' => [
                'type' => 'object',
                'properties' => [
                    'firstName' => [
                        'type' => 'string',
                        'description' => 'Novo primeiro nome.',
                        'example' => 'Carlos Atualizado',
                    ],
                    'lastName' => [
                        'type' => 'string',
                        'description' => 'Novo sobrenome.',
                        'example' => 'Silva Santos',
                    ],
                    'email' => [
                        'type' => 'string',
                        'format' => 'email',
                        'description' => 'Novo e-mail institucional.',
                        'example' => 'carlos.santos@pucrs.br',
                    ],
                    'enabled' => [
                        'type' => 'boolean',
                        'description' => 'Status de ativação da conta.',
                        'example' => true,
                    ],
                ],
            ],
            'UpdatePasswordRequest' => [
                'type' => 'object',
                'required' => ['password'],
                'properties' => [
                    'password' => [
                        'type' => 'string',
                        'format' => 'password',
                        'description' => 'Nova senha permanente do usuário.',
                        'example' => 'novaSenhaForte456#',
                    ],
                ],
            ],
            'UserResponse' => [
                'type' => 'object',
                'properties' => [
                    'id' => [
                        'type' => 'string',
                        'format' => 'uuid',
                        'description' => 'UUID do usuário no Keycloak.',
                        'example' => 'b2d1c002-0000-4000-b000-000000000003',
                    ],
                    'email' => [
                        'type' => 'string',
                        'format' => 'email',
                        'description' => 'E-mail institucional.',
                        'example' => 'professor@pucrs.br',
                    ],
                    'firstName' => [
                        'type' => 'string',
                        'description' => 'Primeiro nome.',
                        'example' => 'Professor',
                    ],
                    'lastName' => [
                        'type' => 'string',
                        'description' => 'Sobrenome.',
                        'example' => 'PUCRS',
                    ],
                    'enabled' => [
                        'type' => 'boolean',
                        'description' => 'Indica se a conta está ativa.',
                        'example' => true,
                    ],
                    'roles' => [
                        'type' => 'array',
                        'items' => ['type' => 'string'],
                        'description' => 'Papéis institucionais atribuídos.',
                        'example' => ['professor'],
                    ],
                ],
            ],
            'CreateRoleRequest' => [
                'type' => 'object',
                'required' => ['name'],
                'properties' => [
                    'name' => [
                        'type' => 'string',
                        'description' => 'Nome único do papel institucional no Keycloak.',
                        'example' => 'gestor',
                    ],
                    'description' => [
                        'type' => 'string',
                        'description' => 'Descrição das atribuições do papel.',
                        'example' => 'Gestor do sistema acadêmico',
                    ],
                ],
            ],
            'RoleResponse' => [
                'type' => 'object',
                'properties' => [
                    'id' => [
                        'type' => 'string',
                        'description' => 'Identificador único ou nome da role no Keycloak.',
                        'example' => 'gestor',
                    ],
                    'name' => [
                        'type' => 'string',
                        'description' => 'Nome do papel institucional.',
                        'example' => 'gestor',
                    ],
                    'description' => [
                        'type' => 'string',
                        'description' => 'Descrição do papel institucional.',
                        'example' => 'Gestor do sistema acadêmico',
                    ],
                    'composite' => [
                        'type' => 'boolean',
                        'description' => 'Indica se o papel é composto por outros sub-papéis.',
                        'example' => false,
                    ],
                    'clientRole' => [
                        'type' => 'boolean',
                        'description' => 'Indica se é um papel específico de client.',
                        'example' => false,
                    ],
                    'containerId' => [
                        'type' => 'string',
                        'description' => 'ID do container ou realm no Keycloak.',
                        'example' => 'constrsw',
                    ],
                ],
            ],
            'UpdateRoleRequest' => [
                'type' => 'object',
                'required' => ['name'],
                'properties' => [
                    'name' => [
                        'type' => 'string',
                        'description' => 'Novo nome do papel institucional.',
                        'example' => 'gestor-academico',
                    ],
                    'description' => [
                        'type' => 'string',
                        'description' => 'Nova descrição do papel institucional.',
                        'example' => 'Gestor do sistema acadêmico',
                    ],
                ],
            ],
            'PatchRoleRequest' => [
                'type' => 'object',
                'properties' => [
                    'name' => [
                        'type' => 'string',
                        'description' => 'Novo nome do papel institucional (opcional).',
                        'example' => 'gestor-academico',
                    ],
                    'description' => [
                        'type' => 'string',
                        'description' => 'Nova descrição do papel institucional (opcional).',
                        'example' => 'Gestor com atribuições atualizadas',
                    ],
                ],
            ],
            'AssignRoleRequest' => [
                'type' => 'object',
                'description' => 'Payload para atribuição de papel a usuário no Keycloak.',
                'properties' => [
                    'roleId' => [
                        'type' => 'string',
                        'description' => 'Identificador único (UUID) da role a ser associada.',
                        'example' => 'a1c0b001-0000-4000-a000-000000000003',
                    ],
                    'name' => [
                        'type' => 'string',
                        'description' => 'Nome da role a ser associada (caso não utilize roleId).',
                        'example' => 'professor',
                    ],
                ],
            ],
            'AssignRoleResponse' => [
                'type' => 'object',
                'properties' => [
                    'success' => [
                        'type' => 'boolean',
                        'example' => true,
                    ],
                    'userId' => [
                        'type' => 'string',
                        'format' => 'uuid',
                        'example' => 'b2d1c002-0000-4000-b000-000000000003',
                    ],
                    'roleId' => [
                        'type' => 'string',
                        'example' => 'a1c0b001-0000-4000-a000-000000000003',
                    ],
                    'roleName' => [
                        'type' => 'string',
                        'example' => 'professor',
                    ],
                    'roles' => [
                        'type' => 'array',
                        'description' => 'Lista atualizada de papéis associados ao usuário.',
                        'items' => ['type' => 'string'],
                        'example' => ['default-roles-construcao-sw', 'professor'],
                    ],
                ],
            ],
            'AuthorizeRequest' => [
                'type' => 'object',
                'required' => ['resource'],
                'properties' => [
                    'resource' => [
                        'type' => 'string',
                        'description' => "Recurso protegido da aplicação a ser acessado.\n" .
                            "Valores válidos: `resources`, `rooms`, `professors`, `students`, `courses`, `classes`, `lessons`, `reservations`.",
                        'example' => 'lessons',
                    ],
                ],
            ],
            'AuthorizeResponse' => [
                'type' => 'object',
                'properties' => [
                    'authorized' => [
                        'type' => 'boolean',
                        'description' => 'Resultado da validação de acesso.',
                        'example' => true,
                    ],
                    'resource' => [
                        'type' => 'string',
                        'description' => 'Recurso consultado.',
                        'example' => 'lessons',
                    ],
                    'matching_roles' => [
                        'type' => 'array',
                        'items' => ['type' => 'string'],
                        'description' => 'Papéis do usuário que concedem permissão ao recurso.',
                        'example' => ['professor'],
                    ],
                    'reason' => [
                        'type' => 'string',
                        'description' => 'Justificativa da concessão de acesso.',
                        'example' => 'Acesso autorizado com base nos papéis institucionais.',
                    ],
                ],
            ],
            'HealthResponse' => [
                'type' => 'object',
                'properties' => [
                    'status' => [
                        'type' => 'string',
                        'description' => 'Status operacional do serviço.',
                        'example' => 'healthy',
                    ],
                    'service' => [
                        'type' => 'string',
                        'description' => 'Nome do microserviço.',
                        'example' => 'oauth',
                    ],
                    'architecture' => [
                        'type' => 'string',
                        'description' => 'Padrão arquitetural implementado.',
                        'example' => 'hexagonal',
                    ],
                    'framework' => [
                        'type' => 'string',
                        'description' => 'Framework PHP utilizado.',
                        'example' => 'symfony',
                    ],
                    'time' => [
                        'type' => 'string',
                        'format' => 'date-time',
                        'description' => 'Data e hora atual do servidor em UTC.',
                        'example' => '2026-09-09T06:00:00+00:00',
                    ],
                    'keycloak_realm' => [
                        'type' => 'string',
                        'description' => 'Realm do Keycloak configurado.',
                        'example' => 'constrsw',
                    ],
                ],
            ],
            'ErrorResponse' => [
                'type' => 'object',
                'properties' => [
                    'error' => [
                        'type' => 'object',
                        'properties' => [
                            'code' => [
                                'type' => 'string',
                                'description' => 'Código semântico de erro padronizado.',
                                'example' => 'INVALID_CREDENTIALS',
                            ],
                            'message' => [
                                'type' => 'string',
                                'description' => 'Mensagem legível descrevendo o erro.',
                                'example' => 'Credenciais de autenticação inválidas.',
                            ],
                            'details' => [
                                'description' => 'Detalhes adicionais de validação ou campos com inconsistência.',
                                'oneOf' => [
                                    ['type' => 'object'],
                                    ['type' => 'array', 'items' => ['type' => 'string']],
                                ],
                                'example' => [],
                            ],
                        ],
                    ],
                ],
            ],
        ];
    }

    /**
     * Catálogo completo de rotas e operações oficiais pré-configuradas.
     *
     * @return array<string, array<string, mixed>>
     */
    private function getPredefinedPaths(): array
    {
        return [
            '/login' => [
                'post' => [
                    'tags' => ['Autenticação'],
                    'summary' => 'Autenticar usuário e obter tokens OIDC',
                    'description' => "Autentica um usuário institucional contra o Keycloak via Direct Grant (Resource Owner Password Credentials) e retorna access_token (JWT) e refresh_token.\n\nAceita tanto `application/json` quanto `application/x-www-form-urlencoded`.",
                    'requestBody' => [
                        'required' => true,
                        'description' => 'Credenciais de acesso do usuário.',
                        'content' => [
                            'application/json' => [
                                'schema' => ['$ref' => '#/components/schemas/LoginRequest'],
                            ],
                            'application/x-www-form-urlencoded' => [
                                'schema' => ['$ref' => '#/components/schemas/LoginRequest'],
                            ],
                        ],
                    ],
                    'responses' => [
                        '200' => [
                            'description' => 'Autenticação bem-sucedida. Retorna tokens de acesso e renovação.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/TokenResponse'],
                                ],
                            ],
                        ],
                        '400' => [
                            'description' => 'Parâmetros obrigatórios ausentes ou inválidos.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                        '401' => [
                            'description' => 'Credenciais de usuário inválidas ou conta desabilitada.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                    ],
                ],
            ],
            '/refresh' => [
                'post' => [
                    'tags' => ['Autenticação'],
                    'summary' => 'Renovar sessão via Refresh Token',
                    'description' => 'Emite um novo conjunto de access_token e refresh_token sem a necessidade de retransmitir a senha do usuário.',
                    'requestBody' => [
                        'required' => true,
                        'description' => 'Payload contendo o refresh token ativo.',
                        'content' => [
                            'application/json' => [
                                'schema' => ['$ref' => '#/components/schemas/RefreshTokenRequest'],
                            ],
                            'application/x-www-form-urlencoded' => [
                                'schema' => ['$ref' => '#/components/schemas/RefreshTokenRequest'],
                            ],
                        ],
                    ],
                    'responses' => [
                        '200' => [
                            'description' => 'Sessão renovada com sucesso.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/TokenResponse'],
                                ],
                            ],
                        ],
                        '400' => [
                            'description' => 'Token de refresh não informado ou vazio.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                        '401' => [
                            'description' => 'Refresh token expirado, revogado ou inválido.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                    ],
                ],
            ],
            '/me' => [
                'get' => [
                    'tags' => ['Autenticação'],
                    'summary' => 'Consultar perfil do usuário autenticado',
                    'description' => 'Valida o Bearer token no endpoint UserInfo do Keycloak e extrai os dados de identidade e papéis institucionais.',
                    'security' => [
                        ['bearerAuth' => []],
                    ],
                    'responses' => [
                        '200' => [
                            'description' => 'Dados de identidade do usuário autenticado.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/UserProfileResponse'],
                                ],
                            ],
                        ],
                        '401' => [
                            'description' => 'Bearer token ausente, expirado ou inválido.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                    ],
                ],
            ],
            '/users' => [
                'post' => [
                    'tags' => ['Usuários'],
                    'summary' => 'Cadastrar novo usuário no Keycloak',
                    'description' => 'Cria uma nova conta de usuário no Keycloak com credenciais permanentes e papéis associados via Admin REST API.',
                    'security' => [
                        ['bearerAuth' => []],
                    ],
                    'requestBody' => [
                        'required' => true,
                        'description' => 'Dados do novo usuário a ser registrado.',
                        'content' => [
                            'application/json' => [
                                'schema' => ['$ref' => '#/components/schemas/CreateUserRequest'],
                            ],
                        ],
                    ],
                    'responses' => [
                        '201' => [
                            'description' => 'Usuário criado com sucesso no Keycloak.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/UserResponse'],
                                ],
                            ],
                        ],
                        '400' => [
                            'description' => 'Dados de entrada inválidos (e-mail malformado ou campos vazios).',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                        '409' => [
                            'description' => 'Conflito: já existe usuário cadastrado com o mesmo e-mail.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                    ],
                ],
                'get' => [
                    'tags' => ['Usuários'],
                    'summary' => 'Listar todos os usuários ativos',
                    'description' => 'Retorna a coleção de usuários ativos (habilitados) no realm do Keycloak.',
                    'security' => [
                        ['bearerAuth' => []],
                    ],
                    'responses' => [
                        '200' => [
                            'description' => 'Lista de usuários ativos retornada com sucesso.',
                            'content' => [
                                'application/json' => [
                                    'schema' => [
                                        'type' => 'array',
                                        'items' => ['$ref' => '#/components/schemas/UserResponse'],
                                    ],
                                ],
                            ],
                        ],
                    ],
                ],
            ],
            '/users/{id}' => [
                'get' => [
                    'tags' => ['Usuários'],
                    'summary' => 'Buscar usuário por ID',
                    'description' => 'Recupera os dados cadastrais completos de um usuário específico a partir de seu UUID no Keycloak.',
                    'security' => [
                        ['bearerAuth' => []],
                    ],
                    'parameters' => [
                        [
                            'name' => 'id',
                            'in' => 'path',
                            'required' => true,
                            'description' => 'UUID do usuário no Keycloak.',
                            'schema' => [
                                'type' => 'string',
                                'format' => 'uuid',
                                'example' => 'b2d1c002-0000-4000-b000-000000000003',
                            ],
                        ],
                    ],
                    'responses' => [
                        '200' => [
                            'description' => 'Usuário localizado.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/UserResponse'],
                                ],
                            ],
                        ],
                        '404' => [
                            'description' => 'Usuário não encontrado.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                    ],
                ],
                'put' => [
                    'tags' => ['Usuários'],
                    'summary' => 'Atualizar atributos cadastrais de usuário',
                    'description' => 'Atualiza nome, sobrenome e outros dados cadastrais de um usuário no Keycloak.',
                    'security' => [
                        ['bearerAuth' => []],
                    ],
                    'parameters' => [
                        [
                            'name' => 'id',
                            'in' => 'path',
                            'required' => true,
                            'description' => 'UUID do usuário.',
                            'schema' => ['type' => 'string', 'format' => 'uuid'],
                        ],
                    ],
                    'requestBody' => [
                        'required' => true,
                        'description' => 'Campos a atualizar.',
                        'content' => [
                            'application/json' => [
                                'schema' => ['$ref' => '#/components/schemas/UpdateUserRequest'],
                            ],
                        ],
                    ],
                    'responses' => [
                        '200' => [
                            'description' => 'Usuário atualizado com sucesso.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/UserResponse'],
                                ],
                            ],
                        ],
                        '400' => [
                            'description' => 'Dados de atualização inválidos.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                        '404' => [
                            'description' => 'Usuário não encontrado.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                    ],
                ],
                'patch' => [
                    'tags' => ['Usuários'],
                    'summary' => 'Alterar senha do usuário',
                    'description' => 'Redefine a credencial de senha de um usuário no Keycloak de forma definitiva.',
                    'security' => [
                        ['bearerAuth' => []],
                    ],
                    'parameters' => [
                        [
                            'name' => 'id',
                            'in' => 'path',
                            'required' => true,
                            'description' => 'UUID do usuário.',
                            'schema' => ['type' => 'string', 'format' => 'uuid'],
                        ],
                    ],
                    'requestBody' => [
                        'required' => true,
                        'description' => 'Nova senha a ser atribuída.',
                        'content' => [
                            'application/json' => [
                                'schema' => ['$ref' => '#/components/schemas/UpdatePasswordRequest'],
                            ],
                        ],
                    ],
                    'responses' => [
                        '200' => [
                            'description' => 'Senha alterada com sucesso.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/UserResponse'],
                                ],
                            ],
                        ],
                        '400' => [
                            'description' => 'Senha ausente ou não atende aos requisitos mínimos.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                        '404' => [
                            'description' => 'Usuário não encontrado.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                    ],
                ],
                'delete' => [
                    'tags' => ['Usuários'],
                    'summary' => 'Desativar usuário (Exclusão Lógica)',
                    'description' => 'Realiza a exclusão lógica do usuário desabilitando-o (enabled=false) no Keycloak, preservando integridade referencial.',
                    'security' => [
                        ['bearerAuth' => []],
                    ],
                    'parameters' => [
                        [
                            'name' => 'id',
                            'in' => 'path',
                            'required' => true,
                            'description' => 'UUID do usuário.',
                            'schema' => ['type' => 'string', 'format' => 'uuid'],
                        ],
                    ],
                    'responses' => [
                        '204' => [
                            'description' => 'Usuário desativado com sucesso (sem conteúdo).',
                        ],
                        '404' => [
                            'description' => 'Usuário não encontrado.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                    ],
                ],
            ],
            '/users/{userId}/roles' => [
                'post' => [
                    'tags' => ['Usuários'],
                    'summary' => 'Atribuir papel a usuário (FR-17)',
                    'description' => 'Associa uma Realm Role ao usuário especificado através da Role Mapping API do Keycloak.',
                    'security' => [
                        ['bearerAuth' => []],
                    ],
                    'parameters' => [
                        [
                            'name' => 'userId',
                            'in' => 'path',
                            'required' => true,
                            'description' => 'UUID do usuário no Keycloak.',
                            'schema' => ['type' => 'string', 'format' => 'uuid'],
                        ],
                    ],
                    'requestBody' => [
                        'required' => true,
                        'description' => 'Identificador ou nome da role a ser atribuída.',
                        'content' => [
                            'application/json' => [
                                'schema' => ['$ref' => '#/components/schemas/AssignRoleRequest'],
                            ],
                        ],
                    ],
                    'responses' => [
                        '200' => [
                            'description' => 'Papel associado ao usuário com sucesso.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/AssignRoleResponse'],
                                ],
                            ],
                        ],
                        '400' => [
                            'description' => 'Identificador do papel ausente ou inválido.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                        '404' => [
                            'description' => 'Usuário ou papel não encontrado.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                    ],
                ],
            ],
            '/users/{userId}/roles/{roleId}' => [
                'delete' => [
                    'tags' => ['Usuários'],
                    'summary' => 'Desassociar papel de usuário (FR-18)',
                    'description' => 'Revoga a atribuição de um papel do usuário especificado através da Role Mapping API do Keycloak.',
                    'security' => [
                        ['bearerAuth' => []],
                    ],
                    'parameters' => [
                        [
                            'name' => 'userId',
                            'in' => 'path',
                            'required' => true,
                            'description' => 'UUID do usuário no Keycloak.',
                            'schema' => ['type' => 'string', 'format' => 'uuid'],
                        ],
                        [
                            'name' => 'roleId',
                            'in' => 'path',
                            'required' => true,
                            'description' => 'UUID ou nome da role a ser revogada.',
                            'schema' => ['type' => 'string'],
                        ],
                    ],
                    'responses' => [
                        '204' => [
                            'description' => 'Atribuição revogada com sucesso (sem conteúdo).',
                        ],
                        '404' => [
                            'description' => 'Usuário ou papel não encontrado.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                    ],
                ],
            ],
            '/roles' => [
                'post' => [
                    'tags' => ['Roles'],
                    'summary' => 'Criar novo papel (Role)',
                    'description' => 'Cria uma nova role institucional no realm do Keycloak.',
                    'security' => [
                        ['bearerAuth' => []],
                    ],
                    'requestBody' => [
                        'required' => true,
                        'description' => 'Dados da role a ser criada.',
                        'content' => [
                            'application/json' => [
                                'schema' => ['$ref' => '#/components/schemas/CreateRoleRequest'],
                            ],
                        ],
                    ],
                    'responses' => [
                        '201' => [
                            'description' => 'Papel criado com sucesso.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/RoleResponse'],
                                ],
                            ],
                        ],
                        '400' => [
                            'description' => 'Nome da role ausente ou formato inválido.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                        '409' => [
                            'description' => 'Papel já existente no Keycloak.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                    ],
                ],
                'get' => [
                    'tags' => ['Roles'],
                    'summary' => 'Listar todos os papéis (Roles)',
                    'description' => 'Recupera os dados e descrições de todos os papéis institucionais cadastrados no Keycloak.',
                    'security' => [
                        ['bearerAuth' => []],
                    ],
                    'responses' => [
                        '200' => [
                            'description' => 'Lista de roles retornada com sucesso.',
                            'content' => [
                                'application/json' => [
                                    'schema' => [
                                        'type' => 'array',
                                        'items' => ['$ref' => '#/components/schemas/RoleResponse'],
                                    ],
                                ],
                            ],
                        ],
                    ],
                ],
            ],
            '/roles/{id}' => [
                'get' => [
                    'tags' => ['Roles'],
                    'summary' => 'Buscar papel por identificador',
                    'description' => 'Recupera os dados de um papel específico a partir do seu ID ou nome.',
                    'security' => [
                        ['bearerAuth' => []],
                    ],
                    'parameters' => [
                        [
                            'name' => 'id',
                            'in' => 'path',
                            'required' => true,
                            'description' => 'Identificador único ou nome do papel no Keycloak.',
                            'schema' => ['type' => 'string'],
                        ],
                    ],
                    'responses' => [
                        '200' => [
                            'description' => 'Papel encontrado e retornado com sucesso.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/RoleResponse'],
                                ],
                            ],
                        ],
                        '404' => [
                            'description' => 'Papel não encontrado no Keycloak.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                    ],
                ],
                'put' => [
                    'tags' => ['Roles'],
                    'summary' => 'Atualizar papel integralmente (Role)',
                    'description' => 'Atualiza todos os dados de um papel no Keycloak pelo identificador.',
                    'security' => [
                        ['bearerAuth' => []],
                    ],
                    'parameters' => [
                        [
                            'name' => 'id',
                            'in' => 'path',
                            'required' => true,
                            'description' => 'Identificador único ou nome do papel no Keycloak.',
                            'schema' => ['type' => 'string'],
                        ],
                    ],
                    'requestBody' => [
                        'required' => true,
                        'description' => 'Dados completos para atualização do papel.',
                        'content' => [
                            'application/json' => [
                                'schema' => ['$ref' => '#/components/schemas/UpdateRoleRequest'],
                            ],
                        ],
                    ],
                    'responses' => [
                        '200' => [
                            'description' => 'Papel atualizado com sucesso.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/RoleResponse'],
                                ],
                            ],
                        ],
                        '400' => [
                            'description' => 'Dados inválidos ou nome não fornecido.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                        '404' => [
                            'description' => 'Papel não encontrado no Keycloak.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                        '409' => [
                            'description' => 'Já existe outro papel com o nome informado.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                    ],
                ],
                'patch' => [
                    'tags' => ['Roles'],
                    'summary' => 'Atualizar papel parcialmente (Role)',
                    'description' => 'Atualiza apenas os campos especificados do papel no Keycloak mantendo os demais inalterados.',
                    'security' => [
                        ['bearerAuth' => []],
                    ],
                    'parameters' => [
                        [
                            'name' => 'id',
                            'in' => 'path',
                            'required' => true,
                            'description' => 'Identificador único ou nome do papel no Keycloak.',
                            'schema' => ['type' => 'string'],
                        ],
                    ],
                    'requestBody' => [
                        'required' => true,
                        'description' => 'Campos parciais para alteração do papel.',
                        'content' => [
                            'application/json' => [
                                'schema' => ['$ref' => '#/components/schemas/PatchRoleRequest'],
                            ],
                        ],
                    ],
                    'responses' => [
                        '200' => [
                            'description' => 'Papel atualizado com sucesso.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/RoleResponse'],
                                ],
                            ],
                        ],
                        '400' => [
                            'description' => 'Nenhum campo informado para alteração ou dados inválidos.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                        '404' => [
                            'description' => 'Papel não encontrado no Keycloak.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                        '409' => [
                            'description' => 'Já existe outro papel com o nome informado.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                    ],
                ],
                'delete' => [
                    'tags' => ['Roles'],
                    'summary' => 'Inativar papel (Exclusão Lógica)',
                    'description' => 'Realiza a exclusão lógica do papel institucional desabilitando-o (attributes.enabled = false), preservando histórico e integridade referencial.',
                    'security' => [
                        ['bearerAuth' => []],
                    ],
                    'parameters' => [
                        [
                            'name' => 'id',
                            'in' => 'path',
                            'required' => true,
                            'description' => 'Identificador único ou nome do papel no Keycloak.',
                            'schema' => ['type' => 'string'],
                        ],
                    ],
                    'responses' => [
                        '204' => [
                            'description' => 'Papel inativado com sucesso (sem conteúdo).',
                        ],
                        '404' => [
                            'description' => 'Papel não encontrado no Keycloak.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                    ],
                ],
            ],
            '/authorize' => [
                'post' => [
                    'tags' => ['Autorização'],
                    'summary' => 'Validar autorização e permissões por recurso (FR-10)',
                    'description' => "Valida o Bearer token JWT no Keycloak e avalia a política de controle de acesso por recurso.\n\n" .
                        "**Matriz de Permissões da Disciplina (FR-10):**\n" .
                        "- `administrator`: `resources`, `rooms`, `professors`, `students`\n" .
                        "- `coordinator`: `courses`, `classes`\n" .
                        "- `professor`: `lessons`, `reservations`\n" .
                        "- Qualquer outro papel (ex: `student`): Acesso restrito a recursos públicos.",
                    'security' => [
                        ['bearerAuth' => []],
                    ],
                    'requestBody' => [
                        'required' => true,
                        'description' => 'Recurso da aplicação que se deseja acessar.',
                        'content' => [
                            'application/json' => [
                                'schema' => ['$ref' => '#/components/schemas/AuthorizeRequest'],
                            ],
                        ],
                    ],
                    'responses' => [
                        '200' => [
                            'description' => 'Acesso autorizado para o recurso especificado.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/AuthorizeResponse'],
                                ],
                            ],
                        ],
                        '400' => [
                            'description' => 'Campo resource obrigatório ausente ou vazio.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                        '401' => [
                            'description' => 'Bearer token ausente, expirado ou inválido.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                        '403' => [
                            'description' => 'Acesso negado: o usuário autenticado não possui papel que conceda acesso ao recurso.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/ErrorResponse'],
                                ],
                            ],
                        ],
                    ],
                ],
            ],
            '/health' => [
                'get' => [
                    'tags' => ['Sistema'],
                    'summary' => 'Verificar saúde operacional do microserviço',
                    'description' => 'Retorna o status de integridade da API, informações de arquitetura hexagonal, framework e realm conectado.',
                    'responses' => [
                        '200' => [
                            'description' => 'Microserviço saudável e operacional.',
                            'content' => [
                                'application/json' => [
                                    'schema' => ['$ref' => '#/components/schemas/HealthResponse'],
                                ],
                            ],
                        ],
                    ],
                ],
            ],
            '/metrics' => [
                'get' => [
                    'tags' => ['Sistema'],
                    'summary' => 'Expor telemetria e métricas operacionais para Prometheus',
                    'description' => 'Exporta métricas de runtime PHP, tráfego HTTP, contadores e latência no formato OpenMetrics / Prometheus.',
                    'responses' => [
                        '200' => [
                            'description' => 'Métricas exportadas com sucesso no formato texto do Prometheus.',
                            'content' => [
                                'text/plain' => [
                                    'schema' => [
                                        'type' => 'string',
                                        'example' => "# HELP php_memory_bytes Uso atual de memória\n# TYPE php_memory_bytes gauge\nphp_memory_bytes 1234567\n",
                                    ],
                                ],
                            ],
                        ],
                    ],
                ],
            ],
        ];
    }
}
