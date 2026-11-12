$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectDirectory
try {
    $staleEmbeddedTemplate = Join-Path $projectDirectory 'target/classes/templates/acceptance-template.docx'
    $staleIssueTemplate = Join-Path $projectDirectory 'target/classes/templates/issue-template.docx'
    if (Test-Path -LiteralPath $staleEmbeddedTemplate -PathType Leaf) {
        Remove-Item -LiteralPath $staleEmbeddedTemplate
    }
    if (Test-Path -LiteralPath $staleIssueTemplate -PathType Leaf) {
        Remove-Item -LiteralPath $staleIssueTemplate
    }
    & mvn '-DskipTests' package
    if ($LASTEXITCODE -ne 0) { throw 'Не удалось собрать приложение.' }
    & (Join-Path $PSScriptRoot 'generate-app-icon.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Не удалось создать значок приложения.' }
    $inputDirectory = Join-Path $projectDirectory 'target/app-input'
    $libraryDirectory = Join-Path $inputDirectory 'lib'
    $templateDirectory = Join-Path $inputDirectory 'templates'
    $acceptanceTemplate = Join-Path $projectDirectory 'templates/acceptance-template.docx'
    $issueTemplate = Join-Path $projectDirectory 'templates/issue-template.docx'
    if (-not (Test-Path -LiteralPath $acceptanceTemplate -PathType Leaf)) {
        throw 'Не найден внешний шаблон templates/acceptance-template.docx.'
    }
    if (-not (Test-Path -LiteralPath $issueTemplate -PathType Leaf)) {
        throw 'Не найден внешний шаблон templates/issue-template.docx.'
    }
    New-Item -ItemType Directory -Path $libraryDirectory -Force | Out-Null
    New-Item -ItemType Directory -Path $templateDirectory -Force | Out-Null
    Copy-Item -LiteralPath 'target/craft_biker-1.0-SNAPSHOT.jar' -Destination $inputDirectory
    Copy-Item -LiteralPath $acceptanceTemplate -Destination $templateDirectory -Force
    Copy-Item -LiteralPath $issueTemplate -Destination $templateDirectory -Force
    Get-ChildItem -LiteralPath 'target/lib' -Filter '*.jar' | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $libraryDirectory
    }
    $modules = @(Get-ChildItem -LiteralPath 'target/lib' -Filter 'javafx-*-win.jar' | Select-Object -ExpandProperty FullName)
    if ($modules.Count -eq 0) { throw 'Не найдены Windows-модули JavaFX.' }
    $jpackageCommand = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($jpackageCommand) {
        $jpackagePath = $jpackageCommand.Source
    } else {
        $properties = & java '-XshowSettings:properties' '-version' 2>&1
        $javaHomeLine = $properties | Select-String '^\s*java.home\s*=\s*(.+)$' | Select-Object -First 1
        if (-not $javaHomeLine) { throw 'Не удалось определить каталог JDK.' }
        $javaDirectory = $javaHomeLine.Matches[0].Groups[1].Value.Trim()
        $jpackagePath = Join-Path $javaDirectory 'bin/jpackage.exe'
        if (-not (Test-Path -LiteralPath $jpackagePath)) { throw 'Для упаковки нужен полный JDK 21 с jpackage.' }
    }
    # A separate output folder makes repeated builds safe without deleting previous builds.
    $outputDirectory = Join-Path $projectDirectory ('target/package-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    & $jpackagePath '--type' 'app-image' '--name' 'CraftBiker' '--dest' $outputDirectory `
        '--input' $inputDirectory '--main-jar' 'craft_biker-1.0-SNAPSHOT.jar' '--main-class' 'org.bike.Main' `
        '--module-path' ($modules -join ';') `
        '--add-modules' 'java.se,javafx.controls,javafx.fxml,jdk.crypto.ec,jdk.unsupported' `
        '--java-options' '--add-modules=javafx.controls,javafx.fxml' `
        '--icon' (Join-Path $projectDirectory 'tools/assets/craft-biker-app-icon.ico') `
        '--app-version' '1.0' '--vendor' 'Craft Bike'
    if ($LASTEXITCODE -ne 0) { throw 'Не удалось создать приложение с Java runtime.' }
    Write-Output (Join-Path $outputDirectory 'CraftBiker/CraftBiker.exe')
} finally {
    Pop-Location
}



