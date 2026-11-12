$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectDirectory
try {
    & mvn '-q' 'test-compile' 'dependency:build-classpath' '-Dmdep.outputFile=target/fx-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Не удалось собрать классы проверок.' }
    $dependencies = (Get-Content -LiteralPath 'target/fx-classpath.txt' -Raw).Trim()
    $modules = @($dependencies.Split(';') | Where-Object { (Split-Path -Leaf $_) -like 'javafx-*-win.jar' })
    $classpath = 'target/classes;target/test-classes;' + $dependencies
    foreach ($check in @('FxFormsSmokeCheck', 'FxWorkflowCheck', 'FxApplicationCheck')) {
        & java '--module-path' ($modules -join ';') '--add-modules' 'javafx.controls,javafx.fxml' `
            '-cp' $classpath ('org.bike.fx.' + $check)
        if ($LASTEXITCODE -ne 0) { throw "Не прошла проверка $check" }
    }
} finally {
    Pop-Location
}
