param(
    [string]$WorkDir = (Join-Path (Get-Location) 'holdfast-gate0')
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$forgeVersion = '1.20.1-47.4.8'
$forgeUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$forgeVersion/forge-$forgeVersion-installer.jar"
$forgeSha256 = '08dce804d25c12726cb8fb65c3041de1be10fb31534dd8cbd500fe1d199a9d57'
$mods = @(
    @{
        Name = 'geckolib-forge-1.20.1-4.8.4.jar'
        Url = 'https://cdn.modrinth.com/data/8BmcQJ2H/versions/aC5KMoNg/geckolib-forge-1.20.1-4.8.4.jar'
        Sha512 = '443a321a293e3cb92fbbc3c97a38b179a45d1db703cd7bdb123270a9e04e7b7d67d5b06cd8e77a6d4d9139e033e901b9660cccf53ad5f9beaae6d06eae68febf'
    },
    @{
        Name = 'player-animation-lib-forge-1.0.2-rc1+1.20.jar'
        Url = 'https://cdn.modrinth.com/data/gedNE4y2/versions/xe2EVE6q/player-animation-lib-forge-1.0.2-rc1%2B1.20.jar'
        Sha512 = 'cb9f6a3aaa943823a85fdd716ddb3c7623af2b3520926c3a98d170c52d31908894808fb61c52bea39bae622dbab59d50b4bad56f7ca0533a9c3350d412c33729'
    }
)

function Assert-Hash([string]$Path, [string]$Algorithm, [string]$Expected) {
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm $Algorithm).Hash.ToLowerInvariant()
    if ($actual -ne $Expected) { throw "Hash mismatch for $Path ($Algorithm): $actual" }
    Write-Host "Verified $Algorithm : $Path"
}

function Assert-Zip([string]$Path) {
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        if ($archive.Entries.Count -eq 0) { throw "Empty archive: $Path" }
        Write-Host "Verified ZIP : $Path ($($archive.Entries.Count) entries)"
    } finally { $archive.Dispose() }
}

function Download-Verified([string]$Url, [string]$Path, [string]$Algorithm, [string]$Expected) {
    Invoke-WebRequest -Uri $Url -OutFile $Path
    Assert-Hash $Path $Algorithm $Expected
    Assert-Zip $Path
}

$java = Get-Command java -ErrorAction Stop
$version = (& $java.Source -version 2>&1 | Out-String)
Write-Host "Java: $version"
if ($version -notmatch 'version "17\.') { throw 'Use a Java 17 runtime on PATH for this Forge 1.20.1 check.' }

New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
$WorkDir = (Resolve-Path $WorkDir).Path
$server = Join-Path $WorkDir 'server'
$modsDir = Join-Path $server 'mods'
New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
$installer = Join-Path $WorkDir "forge-$forgeVersion-installer.jar"
Download-Verified $forgeUrl $installer 'SHA256' $forgeSha256

foreach ($mod in $mods) {
    Download-Verified $mod.Url (Join-Path $modsDir $mod.Name) 'SHA512' $mod.Sha512
}

Push-Location $server
try {
    $installLog = Join-Path $server 'forge-install.log'
    $installProc = Start-Process -FilePath $java.Source -ArgumentList @('-jar', "`"$installer`"", '--installServer') -NoNewWindow -Wait -PassThru -RedirectStandardOutput $installLog -RedirectStandardError (Join-Path $server 'forge-install-error.log')
    if ($installProc.ExitCode -ne 0) { throw "Forge installer exit $($installProc.ExitCode). See $installLog" }

    $generated = Join-Path $server "libraries\net\minecraftforge\forge\$forgeVersion\forge-$forgeVersion-server.jar"
    Assert-Zip $generated
    $run = Join-Path $server 'run.bat'
    if (-not (Test-Path $run)) { throw "Installer did not create $run" }

    Write-Host ''
    Write-Host 'Server files are ready. The script has NOT accepted the Minecraft EULA or started the server.'
    Write-Host "Server folder: $server"
    Write-Host "Mod jars: $modsDir"
    Write-Host 'Copy these two exact mod jars into a clean Prism instance with Minecraft 1.20.1 + Forge 47.4.8.'
    Write-Host 'Launch the client and save the Prism log. If you accept the Minecraft EULA, run run.bat nogui from the server folder and save logs/latest.log.'
} finally { Pop-Location }
