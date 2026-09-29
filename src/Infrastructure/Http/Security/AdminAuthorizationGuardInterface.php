<?php

declare(strict_types=1);

namespace App\Infrastructure\Http\Security;

use Symfony\Component\HttpFoundation\Request;

interface AdminAuthorizationGuardInterface
{
    /**
     * Exige que o requisitante possua papel de administrador institucional ('administrator' ou 'admin').
     *
     * @throws \App\Domain\Exception\AccessDeniedException Quando o usuário não possui permissão administrativa.
     * @throws \App\Domain\Exception\InvalidTokenException Quando o token é inválido ou expirado.
     */
    public function requireAdmin(Request $request): void;

    /**
     * Exige que o requisitante seja administrador OU seja o próprio usuário alvo da operação.
     *
     * @param Request $request
     * @param string $targetUserId UUID, username ou email do usuário alvo
     * @throws \App\Domain\Exception\AccessDeniedException Quando o usuário não é administrador nem o próprio usuário.
     * @throws \App\Domain\Exception\InvalidTokenException Quando o token é inválido ou expirado.
     */
    public function requireAdminOrSelf(Request $request, string $targetUserId): void;

    /**
     * Extrai o token Bearer do cabeçalho da requisição.
     */
    public function extractToken(Request $request): ?string;

    /**
     * Decodifica as claims do JWT (sem validação criptográfica).
     *
     * @return array<string, mixed>|null
     */
    public function decodeJwtPayload(string $jwt): ?array;

    /**
     * Retorna a lista de papéis contidos no token JWT.
     *
     * @return array<string>
     */
    public function extractRolesFromToken(string $jwt): array;

    /**
     * Determina se o token pertence a um administrador.
     */
    public function isAdmin(string $jwt): bool;

    /**
     * Determina se o token pertence ao usuário alvo especificado.
     */
    public function isSelf(string $jwt, string $targetUserId): bool;
}
