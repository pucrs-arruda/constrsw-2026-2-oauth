param(
    [string]$Network = "constrsw_constrsw",
    [string]$ApiUrl = "http://oauth:3001",
    [string]$KeycloakUrl = "http://keycloak:8080",
    [string]$MavenImage = "maven:3.9.9-eclipse-temurin-21"
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$containerName = "oauth-e2e-$PID-$([Guid]::NewGuid().ToString('N').Substring(0, 8))"

try {
    $createArgs = @(
        "create",
        "--name", $containerName,
        "--network", $Network,
        "-v", "constrsw-maven-cache:/root/.m2",
        "-w", "/tmp",
        "-e", "E2E_BASE_URL=$ApiUrl",
        "-e", "E2E_KEYCLOAK_URL=$KeycloakUrl"
    )

    foreach ($variable in @("E2E_REALM", "E2E_ADMIN_USERNAME", "E2E_ADMIN_PASSWORD")) {
        if (Test-Path "Env:$variable") {
            $createArgs += @("-e", $variable)
        }
    }

    $createArgs += @($MavenImage, "mvn", "-q", "verify", "-Pe2e")
    & docker @createArgs | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Não foi possível criar o container Maven para o E2E."
    }

    & docker cp (Join-Path $projectRoot "pom.xml") "${containerName}:/tmp/pom.xml"
    if ($LASTEXITCODE -ne 0) {
        throw "Não foi possível copiar o pom.xml para o container E2E."
    }

    & docker cp (Join-Path $projectRoot "src") "${containerName}:/tmp/src"
    if ($LASTEXITCODE -ne 0) {
        throw "Não foi possível copiar os fontes para o container E2E."
    }

    & docker start -a $containerName
    if ($LASTEXITCODE -ne 0) {
        throw "A suíte E2E falhou."
    }

    Write-Host "E2E concluído com sucesso." -ForegroundColor Green
}
finally {
    & docker rm -f $containerName 2>$null | Out-Null
}
