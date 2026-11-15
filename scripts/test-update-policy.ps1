$ErrorActionPreference = 'Stop'
$taskProject = Split-Path -Parent $PSScriptRoot
$taskClasses = Join-Path $taskProject 'build/policy-tests'
New-Item -ItemType Directory -Force -Path $taskClasses | Out-Null
& javac -encoding UTF-8 -d $taskClasses (Join-Path $taskProject 'app/src/main/java/br/com/espectro/UpdatePolicy.java') (Join-Path $taskProject 'tests/UpdatePolicyTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar os testes' }
& java -cp $taskClasses br.com.espectro.UpdatePolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Falha nos testes da política de atualização' }
