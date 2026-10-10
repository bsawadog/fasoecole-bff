# Local container integration check. Uses a disposable database, never AWS data.
$ErrorActionPreference = 'Stop'
$testSuffix = [guid]::NewGuid().ToString('N').Substring(0, 12)
$testNetwork = "fasoecole-check-$testSuffix"
$testDatabase = "fasoecole-check-db-$testSuffix"
$testApi = "fasoecole-check-api-$testSuffix"
$testImage = "fasoecole-dev-check:$testSuffix"

function Assert-NativeSuccess {
    if ($LASTEXITCODE -ne 0) { throw "External command failed with exit code $LASTEXITCODE" }
}

try {
    docker build --platform linux/amd64 -t $testImage .
    Assert-NativeSuccess
    docker network create $testNetwork | Out-Null
    Assert-NativeSuccess
    docker run -d --name $testDatabase --network $testNetwork --tmpfs /var/lib/postgresql/data:rw,size=512m -e POSTGRES_PASSWORD=disposable-test-only -e POSTGRES_DB=fasoecoleBD postgres:16-alpine | Out-Null
    Assert-NativeSuccess
    $databaseReady = $false
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        docker exec $testDatabase pg_isready -h 127.0.0.1 -U postgres -d fasoecoleBD 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $databaseReady = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $databaseReady) { throw 'PostgreSQL did not start' }
    docker run -d --name $testApi --network $testNetwork -p '127.0.0.1::8080' -e SPRING_PROFILES_ACTIVE=dev -e "SPRING_DATASOURCE_URL=jdbc:postgresql://${testDatabase}:5432/fasoecoleBD" -e DB_USER=postgres -e DB_PASSWORD=disposable-test-only -e JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA -e JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=65.0 --memory=2g $testImage | Out-Null
    Assert-NativeSuccess
    $testPort = (docker port $testApi 8080/tcp).Trim().Split(':')[-1]
    Assert-NativeSuccess
    $apiReady = $false
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        $containerRunning = docker inspect --format '{{.State.Running}}' $testApi
        if ($containerRunning -ne 'true') { break }
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$testPort/actuator/health" -TimeoutSec 3
            if ($health.status -eq 'UP') { $apiReady = $true; break }
        } catch {}
        Start-Sleep -Seconds 2
    }
    if (-not $apiReady) { docker logs $testApi; throw 'API health check did not pass' }
    try {
        Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$testPort/api/users/me" -TimeoutSec 5 | Out-Null
        throw 'Protected API unexpectedly accepted an anonymous request'
    } catch {
        if (-not $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 401) { throw }
    }
    $baselineCount = docker exec $testDatabase psql -U postgres -d fasoecoleBD -tAc "SELECT count(*) FROM flyway_schema_history WHERE version='39' AND success"
    Assert-NativeSuccess
    if ($baselineCount.Trim() -ne '1') { throw 'B39 migration was not applied' }
    $bootstrapCount = docker exec $testDatabase psql -U postgres -d fasoecoleBD -tAc "SELECT count(*) FROM flyway_schema_history WHERE version='40' AND success"
    Assert-NativeSuccess
    if ($bootstrapCount.Trim() -ne '1') { throw 'V40 migration was not applied' }
    Write-Output 'PASS: fresh PostgreSQL, Flyway B39/V40, anonymous health UP, protected API 401'
} finally {
    # Only remove the uniquely named resources created by this invocation.
    docker rm -f $testApi $testDatabase 2>$null | Out-Null
    docker network rm $testNetwork 2>$null | Out-Null
    docker image rm $testImage 2>$null | Out-Null
}
