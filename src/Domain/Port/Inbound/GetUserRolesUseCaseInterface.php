<?php

declare(strict_types=1);

namespace App\Domain\Port\Inbound;

interface GetUserRolesUseCaseInterface
{
    /**
     * @return string[]
     */
    public function execute(string $userId): array;
}
