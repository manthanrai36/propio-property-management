$ErrorActionPreference = "Stop"
Set-Location "C:\Users\Manthan\.gemini\antigravity\scratch\propio"

$jars = (Get-ChildItem -Path "C:\Users\Manthan\.m2\repository" -Recurse -Filter "*.jar" | Where-Object { $_.FullName -notmatch "1\.7\.36" } | Select-Object -ExpandProperty FullName) -join ";"
$sources = (Get-ChildItem -Path "src\main\java" -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName)

Write-Host "Compiling $($sources.Count) Java files..."
& "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\javac.exe" -parameters -cp $jars -d "target\classes" $sources

Write-Host "Copying resources to target\classes..."
Copy-Item -Recurse -Force "src\main\resources\*" "target\classes\"

Write-Host "Build complete!"
