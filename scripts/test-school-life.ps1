param([switch]$Coverage)

$ErrorActionPreference = 'Stop'
$lifeContainer = 'fasoecole-life-test-' + [guid]::NewGuid().ToString('N').Substring(0,12)
try {
    docker run -d --name $lifeContainer --tmpfs /var/lib/postgresql/data:rw,size=512m -p '127.0.0.1::5432' -e POSTGRES_PASSWORD=disposable-test-only -e POSTGRES_DB=school_life_test postgres:16-alpine | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start PostgreSQL' }
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        docker exec $lifeContainer pg_isready -h 127.0.0.1 -U postgres -d school_life_test 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'PostgreSQL did not become ready' }
    $lifePort = (docker port $lifeContainer 5432/tcp).Trim().Split(':')[-1]
    Push-Location (Split-Path -Parent $PSScriptRoot)
    try {
        $lifeMavenArgs = @('-q', "-DschoolLifeTestUrl=jdbc:postgresql://127.0.0.1:$lifePort/school_life_test")
        if ($Coverage) {
            Remove-Item -LiteralPath 'target/jacoco.exec' -ErrorAction SilentlyContinue
        } else {
            $lifeMavenArgs += '-Dtest=SchoolLifeIntegrationTest,SchoolLifeServiceTest,MessagingHttpIntegrationTest,PayablesHttpIntegrationTest,SchoolPayableServiceTest'
        }
        & mvn @lifeMavenArgs test
        if ($LASTEXITCODE -ne 0) { throw 'School life integration checks failed' }
    } finally { Pop-Location }
} finally {
    # Only remove the uniquely named temporary container created by this script.
    docker rm -f $lifeContainer 2>$null | Out-Null
}
