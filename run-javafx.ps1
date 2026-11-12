$ErrorActionPreference = 'Stop'
$projectDirectory = $PSScriptRoot
Push-Location -LiteralPath $projectDirectory
try {
    $application = Join-Path $projectDirectory 'target/craft_biker-1.0-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $application)) {
        & mvn '-DskipTests' package
        if ($LASTEXITCODE -ne 0) { throw 'Не удалось собрать приложение.' }
    }
    $modules = @(Get-ChildItem -LiteralPath (Join-Path $projectDirectory 'target/lib') -Filter 'javafx-*-win.jar' | Select-Object -ExpandProperty FullName)
    if ($modules.Count -eq 0) { throw 'Не найдены библиотеки JavaFX. Выполните mvn package.' }
    & java '--module-path' ($modules -join ';') '--add-modules' 'javafx.controls,javafx.fxml' '-jar' $application
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
