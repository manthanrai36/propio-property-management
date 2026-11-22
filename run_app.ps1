Set-Location "C:\Users\Manthan\.gemini\antigravity\scratch\propio"
$env:DB_PASSWORD = "root"
$jars = (Get-ChildItem -Path "C:\Users\Manthan\.m2\repository" -Recurse -Filter "*.jar" | Where-Object { $_.FullName -notmatch "1\.7\.36" } | Select-Object -ExpandProperty FullName) -join ";"
& "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe" `
  -cp "target\classes;$jars" `
  "-Dspring.profiles.active=local" `
  "-Dspring.devtools.restart.enabled=false" `
  "-Dspring.devtools.livereload.enabled=false" `
  com.propio.PropioApplication
